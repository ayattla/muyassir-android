package com.muyassir.app.core

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.rounded.AutoMode
import com.muyassir.app.engine.ImageEngine
import com.muyassir.app.engine.Ocr
import com.muyassir.app.engine.Office
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.engine.ZipEngine
import com.muyassir.app.ui.Progress
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * «الوصفات»: several tools chained into one tap (e.g. scan photos → enhance → PDF → compress).
 * Built-in recipes are free; making your own is a Plus feature. Everything runs on the device.
 */
object Recipes {
    data class Step(val op: String, val arg: Int = 0)
    data class Recipe(val id: String, val title: String, val sub: String, val input: String, val steps: List<Step>, val custom: Boolean = false)

    /** Operations a recipe can use, with a label and the default argument. */
    val ops = linkedMapOf(
        "enhance" to "تحسين الصور", "gray" to "أبيض وأسود", "resize" to "تصغير الأبعاد", "compress_img" to "ضغط الصور",
        "to_pdf" to "الصور إلى PDF", "merge" to "دمج ملفات PDF", "compress_pdf" to "ضغط PDF", "to_images" to "PDF إلى صور",
        "ocr" to "استخراج النص", "zip" to "ضغط ZIP",
    )
    val defaultArg = mapOf("resize" to 1600, "compress_img" to 75, "compress_pdf" to 1)
    val inputs = linkedMapOf("image" to "صور", "pdf" to "ملفات PDF", "both" to "صور أو PDF")

    val builtIn = listOf(
        Recipe("doc", "مستند من صور", "تحسين ← PDF واحد ← ضغط", "image", listOf(Step("enhance"), Step("to_pdf"), Step("compress_pdf", 1))),
        Recipe("light", "صور خفيفة للإرسال", "تصغير إلى 1600 بكسل ← ضغط 75%", "image", listOf(Step("resize", 1600), Step("compress_img", 75))),
        Recipe("text", "استخراج النص", "من صور أو PDF إلى نص وWord", "both", listOf(Step("ocr"))),
        Recipe("email", "PDF للبريد", "ضغط متوسط يناسب المرفقات", "pdf", listOf(Step("compress_pdf", 1))),
        Recipe("merge", "دمج وضغط", "عدة ملفات PDF ← ملف واحد مضغوط", "pdf", listOf(Step("merge"), Step("compress_pdf", 1))),
        Recipe("archive", "أرشفة المستندات", "تحسين ← أبيض وأسود ← PDF ← ZIP", "image", listOf(Step("enhance"), Step("gray"), Step("to_pdf"), Step("zip"))),
    )
    val custom = mutableStateListOf<Recipe>()
    val all: List<Recipe> get() = builtIn + custom

    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun asTool(r: Recipe) = Tool("recipe:${r.id}", listOf("files"), r.title, r.sub, androidx.compose.material.icons.Icons.Rounded.AutoMode)

    fun mimes(r: Recipe): Array<String> = when (r.input) { "image" -> arrayOf("image/*"); "pdf" -> arrayOf("application/pdf"); else -> arrayOf("image/*", "application/pdf") }

    /** Recipes whose input fits these kinds (from Incoming.kind). */
    fun forKinds(kinds: Set<String>) = all.filter { r ->
        kinds.isNotEmpty() && kinds.all { k -> when (r.input) { "image" -> k == "image"; "pdf" -> k == "pdf"; else -> k == "image" || k == "pdf" } }
    }

    fun describe(steps: List<Step>) = steps.joinToString(" ← ") { s ->
        (ops[s.op] ?: s.op) + when (s.op) { "resize" -> " ${s.arg}"; "compress_img" -> " ${s.arg}%"; "compress_pdf" -> listOf(" قوي", " متوسط", " خفيف")[s.arg.coerceIn(0, 2)]; else -> "" }
    }

    // ---- custom recipes (stored on the device) ----
    private var loaded = false
    fun load() {
        if (loaded) return; loaded = true
        custom.clear(); Store.list("recipes").forEach { o -> runCatching { custom += fromJson(o) } }
    }
    private fun toJson(r: Recipe) = JSONObject().put("id", r.id).put("title", r.title).put("input", r.input)
        .put("steps", JSONArray(r.steps.map { JSONObject().put("op", it.op).put("arg", it.arg) }))
    private fun fromJson(o: JSONObject): Recipe {
        val a = o.getJSONArray("steps"); val steps = (0 until a.length()).map { a.getJSONObject(it).let { s -> Step(s.getString("op"), s.optInt("arg")) } }
        return Recipe(o.getString("id"), o.getString("title"), describe(steps), o.optString("input", "both"), steps, custom = true)
    }
    fun save(r: Recipe) { custom.removeAll { it.id == r.id }; custom += r.copy(sub = describe(r.steps), custom = true); Store.saveList("recipes", custom.map { toJson(it) }) }
    fun delete(id: String) { custom.removeAll { it.id == id }; Store.saveList("recipes", custom.map { toJson(it) }) }

    // ---- running ----
    private fun isImage(f: File) = f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
    private fun isPdf(f: File) = f.extension.lowercase() == "pdf"

    /** Runs the steps in order; each step takes the files the previous one produced. */
    fun run(ctx: Context, r: Recipe, inputs: List<Uri>, out: File, p: Progress): List<File> {
        val work = File(ctx.cacheDir, "in/recipe-${System.nanoTime()}").apply { mkdirs() }
        var files = inputs.map { Files.toCache(ctx, it) }
        r.steps.forEachIndexed { si, s ->
            p.label = "الخطوة ${si + 1} من ${r.steps.size}: ${ops[s.op] ?: s.op}"; p.value = si / r.steps.size.toFloat()
            val dir = File(work, "s$si").apply { mkdirs() }
            files = step(ctx, s, files, dir)
            check(files.isNotEmpty()) { "لم تنتج الخطوة «${ops[s.op]}» أي ملف. تأكد من نوع الملفات المختارة." }
        }
        p.value = 1f
        return files.map { f -> File(out, f.name.substringAfter('_', f.name)).also { f.copyTo(it, overwrite = true) } }
    }

    private fun base(f: File) = f.nameWithoutExtension.substringAfter('_', f.nameWithoutExtension)

    private fun step(ctx: Context, s: Step, files: List<File>, dir: File): List<File> = when (s.op) {
        "enhance", "gray", "resize", "compress_img" -> files.map { f ->
            if (!isImage(f)) f else {
                var b = ImageEngine.load(ctx, Uri.fromFile(f), 4096)
                when (s.op) {
                    "enhance" -> b = ImageEngine.autoEnhance(b)
                    "gray" -> b = ImageEngine.adjust(b, 0f, 1f, 0f, false, true)
                    "resize" -> { val m = s.arg.takeIf { it > 0 } ?: 1600; if (maxOf(b.width, b.height) > m) b = ImageEngine.resize(b, m, m, true) }
                }
                val q = if (s.op == "compress_img") (s.arg.takeIf { it in 10..100 } ?: 75) else 92
                ImageEngine.save(b, ImageEngine.Fmt.JPG, q, File(dir, base(f) + ".jpg")).also { b.recycle() }
            }
        }
        "to_pdf" -> {
            val imgs = files.filter { isImage(it) }
            val rest = files.filterNot { isImage(it) }
            if (imgs.isEmpty()) rest
            else rest + PdfEngine.fromImages(imgs.asSequence().map { BitmapFactory.decodeFile(it.absolutePath) }, true, File(dir, "مستند.pdf"))
        }
        "merge" -> {
            val pdfs = files.filter { isPdf(it) }
            if (pdfs.size < 2) files else files.filterNot { isPdf(it) } + PdfEngine.merge(pdfs, File(dir, "ملف-مدمج.pdf"))
        }
        "compress_pdf" -> files.map { f ->
            if (!isPdf(f)) f else {
                val (dpi, q) = when (s.arg) { 0 -> 72 to 45; 2 -> 150 to 75; else -> 110 to 60 }
                PdfEngine.compress(f, dpi, q, File(dir, base(f) + "-مضغوط.pdf")) { _, _ -> }
            }
        }
        "to_images" -> files.flatMap { f -> if (!isPdf(f)) listOf(f) else PdfEngine.toImages(f, (0 until PdfEngine.pageCount(f)).toList(), 150, false, dir, base(f)) { _, _ -> } }
        "ocr" -> {
            val sb = StringBuilder()
            files.forEach { f ->
                when {
                    isPdf(f) -> repeat(PdfEngine.pageCount(f)) { pg -> val b = PdfEngine.render(f, pg, 220); sb.append(Ocr.recognize(ctx, b, Ocr.Lang.AR)).append("\n\n"); b.recycle() }
                    isImage(f) -> sb.append(Ocr.recognize(ctx, ImageEngine.load(ctx, Uri.fromFile(f), 3000), Ocr.Lang.AR)).append("\n\n")
                }
            }
            val text = sb.toString().trim()
            val txt = File(dir, "نص-مستخرج.txt").apply { writeText(text) }
            val docx = File(dir, "نص-مستخرج.docx").also { Office.writeDocx(it, text.split("\n\n").map { para -> Office.Para.Text(para) }) }
            listOf(txt, docx)
        }
        "zip" -> listOf(ZipEngine.zip(files.map { base(it) + "." + it.extension to it }, File(dir, "أرشيف.zip")))
        else -> files
    }
}
