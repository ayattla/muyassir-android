package com.muyassir.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Muyassir server (ayattla.com/muyassir): online quizzes students open by link or QR without an account.
 * Only what the teacher publishes is sent (title, questions, answers, time). No files and no personal data;
 * the device gets an anonymous token on first publish.
 */
object Cloud {
    const val BASE = "https://ayattla.com/muyassir"
    class Failure(msg: String) : Exception(msg)

    /** The app's quiz types → the server's: 0 choice, 1 true/false, 3 blank, 4 matching (as "sort"), 5 order, 6 classify. Essays are left out. */
    data class Mapped(val activity: JSONObject, val skipped: Int)

    fun toActivity(title: String, qs: List<JSONObject>, minutes: Int, days: Int = 7): Mapped {
        val out = JSONArray(); var skipped = 0
        for (q in qs) {
            val text = q.optString("q").trim()
            val mark = q.optString("mark", "1").toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 1.0
            when (q.optInt("type")) {
                0 -> {
                    val opts = (0 until 4).map { q.optString("o$it").trim() }
                    val ans = opts.getOrNull(q.optInt("ans")).orEmpty()
                    val list = opts.filter { it.isNotEmpty() }
                    if (text.isEmpty() || list.size < 2 || ans.isEmpty()) { skipped++; continue }
                    out.put(JSONObject().put("text", text).put("type", "choice").put("options", JSONArray(list)).put("answer", ans).put("mark", mark))
                }
                1 -> if (text.isEmpty()) skipped++ else out.put(JSONObject().put("text", text).put("type", "truefalse").put("answer", if (q.optInt("ans") == 0) "صح" else "خطأ").put("mark", mark))
                3 -> {
                    val ans = q.optString("answer").trim()
                    if (text.isEmpty() || ans.isEmpty()) { skipped++; continue }
                    out.put(JSONObject().put("text", text.replace(Regex("_{2,}|…+|\\.{4,}"), "......")).put("type", "blank").put("answer", ans).put("mark", mark))
                }
                4 -> {
                    val ps = q.optString("pairs").lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.take(8)
                    if (ps.size < 2) { skipped++; continue }
                    val head = (text.ifEmpty { "صل كل عنصر بما يناسبه" }) + "\n" + ps.mapIndexed { i, p -> "${i + 1}) ${p.first}" }.joinToString("\n")
                    val shown = ps.map { it.second }.sorted()
                    out.put(JSONObject().put("text", head.take(600)).put("type", "sort").put("options", JSONArray(shown)).put("answer", JSONArray(ps.map { it.second })).put("mark", mark))
                }
                5 -> {
                    val its = q.optString("items").lines().map { it.trim() }.filter { it.isNotEmpty() }.take(8)
                    if (its.size < 2) { skipped++; continue }
                    out.put(JSONObject().put("text", text.ifEmpty { "رتّب العناصر ترتيبًا صحيحًا" }).put("type", "order").put("options", JSONArray(its.sorted())).put("answer", JSONArray(its)).put("mark", mark))
                }
                6 -> {
                    val ps = q.optString("pairs").lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.take(20)
                    val cats = ps.map { it.second }.distinct()
                    if (ps.size < 2 || cats.size < 2 || cats.size > 8) { skipped++; continue }
                    out.put(JSONObject().put("text", text.ifEmpty { "صنّف العناصر" }).put("type", "classify").put("items", JSONArray(ps.map { it.first }))
                        .put("options", JSONArray(cats)).put("answer", JSONArray(ps.map { it.second })).put("mark", mark))
                }
                else -> skipped++
            }
        }
        val a = JSONObject().put("title", title.ifBlank { "اختبار" }.take(120)).put("questions", out)
            .put("minutes", minutes.coerceIn(0, 300)).put("closes_in_days", days.coerceIn(1, 60)).put("show_answers", true)
        return Mapped(a, skipped)
    }

    /** One quiet retry: mobile networks often drop the first connection. */
    private fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject =
        try { callOnce(method, path, body, auth) } catch (e: Failure) {
            if (e.message?.startsWith("لا يوجد اتصال") != true) throw e
            Thread.sleep(1500); callOnce(method, path, body, auth)
        }

    private fun callOnce(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 12000; c.readTimeout = 20000
            c.setRequestProperty("Accept", "application/json")
            if (auth) c.setRequestProperty("Authorization", "Bearer " + token())
            if (body != null) {
                c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).opt("detail") }.getOrNull()
                throw Failure(when {
                    code == 429 -> "طلبات كثيرة خلال وقت قصير، حاول بعد قليل"
                    code == 404 -> "الاختبار غير موجود على الخادم (ربما حُذف)"
                    detail is String -> detail
                    else -> "تعذر الاتصال بالخادم ($code)"
                })
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: Failure) { throw e
        } catch (e: java.io.IOException) { throw Failure("لا يوجد اتصال بالإنترنت أو الخادم لا يستجيب")
        } finally { c.disconnect() }
    }

    private fun token(): String {
        Store.obj("cloud").optString("token").takeIf { it.isNotEmpty() }?.let { return it }
        val t = call("POST", "/api/v1/devices", JSONObject().put("platform", "android"), auth = false).getString("token")
        Store.saveObj("cloud", Store.obj("cloud").put("token", t)); return t
    }

    val consented: Boolean get() = Store.obj("cloud").optBoolean("consent")
    fun consent() = Store.saveObj("cloud", Store.obj("cloud").put("consent", true))

    /** Publishes and remembers the quiz in "published". */
    suspend fun publish(a: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val r = call("POST", "/api/v1/activities", a)
        val item = JSONObject().put("code", r.getString("code")).put("url", r.getString("url")).put("title", a.optString("title"))
            .put("count", a.optJSONArray("questions")?.length() ?: 0).put("at", System.currentTimeMillis())
        Store.saveList("published", listOf(item) + Store.list("published")); item
    }

    suspend fun results(code: String): JSONObject = withContext(Dispatchers.IO) { call("GET", "/api/v1/activities/$code/results") }
    suspend fun close(code: String) = withContext(Dispatchers.IO) { call("POST", "/api/v1/activities/$code/close") }
    suspend fun delete(code: String) = withContext(Dispatchers.IO) {
        runCatching { call("DELETE", "/api/v1/activities/$code") }.onFailure { if (it.message?.contains("غير موجود") != true) throw it }
        Store.saveList("published", Store.list("published").filter { it.optString("code") != code })
    }

    /** Deletes the device and everything it published from the server, then forgets the token. */
    suspend fun forgetMe() = withContext(Dispatchers.IO) {
        if (Store.obj("cloud").optString("token").isNotEmpty()) call("DELETE", "/api/v1/devices/me")
        Store.saveObj("cloud", JSONObject()); Store.saveList("published", emptyList())
    }
}
