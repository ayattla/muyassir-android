package com.muyassir.app.core

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything the user saves stays on the device, in small JSON files inside the app's private folder. */
object Store {
    private lateinit var dir: File
    val favorites = mutableStateListOf<String>()
    val usage = mutableStateMapOf<String, Int>()
    val recents = mutableStateListOf<Recent>()

    data class Recent(val tool: String, val time: Long, val detail: String)

    fun init(ctx: Context) {
        if (::dir.isInitialized) return
        dir = File(ctx.filesDir, "data").apply { mkdirs() }
        favorites.clear(); favorites.addAll(readArr("favorites").strings())
        usage.clear(); readObj("usage").let { o -> o.keys().forEach { usage[it] = o.optInt(it) } }
        recents.clear()
        readArr("recents").objects().forEach { recents.add(Recent(it.optString("t"), it.optLong("at"), it.optString("d"))) }
    }

    fun toggleFavorite(id: String) {
        if (id in favorites) favorites.remove(id) else favorites.add(0, id)
        write("favorites", JSONArray(favorites.toList()))
    }

    fun used(id: String, detail: String = "") {
        usage[id] = (usage[id] ?: 0) + 1
        write("usage", JSONObject(usage.toMap() as Map<*, *>))
        recents.removeAll { it.tool == id && it.detail == detail }
        recents.add(0, Recent(id, System.currentTimeMillis(), detail))
        while (recents.size > 30) recents.removeAt(recents.lastIndex)
        write("recents", JSONArray(recents.map { JSONObject().put("t", it.tool).put("at", it.time).put("d", it.detail) }))
    }

    fun clearRecents() { recents.clear(); write("recents", JSONArray()) }

    // ---- generic record lists (notes, tasks, expenses, exams, flashcards …) ----
    fun list(name: String): MutableList<JSONObject> = readArr("list_$name").objects().toMutableList()
    fun saveList(name: String, items: List<JSONObject>) = write("list_$name", JSONArray(items))
    fun obj(name: String): JSONObject = readObj("obj_$name")
    fun saveObj(name: String, o: JSONObject) = write("obj_$name", o)

    private fun readArr(n: String) = runCatching { JSONArray(File(dir, "$n.json").readText()) }.getOrElse { JSONArray() }
    private fun readObj(n: String) = runCatching { JSONObject(File(dir, "$n.json").readText()) }.getOrElse { JSONObject() }
    private fun write(n: String, v: Any) {
        val f = File(dir, "$n.json"); val tmp = File(dir, "$n.tmp")
        tmp.writeText(v.toString()); tmp.renameTo(f)
    }
    private fun JSONArray.strings() = (0 until length()).map { optString(it) }
    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
}
