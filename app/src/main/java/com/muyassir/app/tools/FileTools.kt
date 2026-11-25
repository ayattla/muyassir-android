package com.muyassir.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.DocBuilder
import com.muyassir.app.engine.ImageEngine
import com.muyassir.app.engine.Ocr
import com.muyassir.app.engine.Office
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.engine.ZipEngine
import com.muyassir.app.ui.*
import java.io.ByteArrayOutputStream
import java.io.File

private val PDF = arrayOf("application/pdf")
private val DOCX = arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
private val XLSX = arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
private val PPTX = arrayOf("application/vnd.openxmlformats-officedocument.presentationml.presentation")
val IMAGES = arrayOf("image/*")

private fun cache(ctx: Context, u: Uri) = Files.toCache(ctx, u)
private fun base(ctx: Context, u: Uri) = Files.base(Files.name(ctx, u))

/** Lines of a PDF, either from its text layer or (for scans and better Arabic order) through OCR. */
private fun pdfLines(ctx: Context, f: File, useOcr: Boolean, p: Progress): List<List<String>> {
    if (!useOcr) return PdfEngine.text(f).map { it.lines() }
    val n = PdfEngine.pageCount(f)
    return (0 until n).map { i ->
        p.label = "التعرف على الصفحة ${i + 1} من $n"; p.value = i / n.toFloat()
        val bmp = PdfEngine.render(f, i, 200)
        Ocr.recognize(ctx, bmp, Ocr.Lang.AR).lines().also { bmp.recycle() }
    }
}

@Composable
private fun OcrSwitch(state: MutableState<Boolean>) {
    SwitchRow("التعرف الضوئي على الصفحات (للمسح الضوئي وأدق للعربية)", state.value) { state.value = it }
}

@Composable
fun PdfToWord(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val ocr = remember { mutableStateOf(false) }
    val review = remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "تحويل إلى Word", options = {
        Panel {
            SwitchRow("إعادة بناء العناوين والجداول بالتعرف الضوئي (للمسح الضوئي والجداول، أبطأ)", ocr.value) { ocr.value = it }
            Hint("بدونه يستخرج النص والفقرات من طبقة النص مع اتجاه عربي صحيح. معه تقرأ كل صفحة كصورة ويعاد بناء الجداول والعناوين، وتظهر الكلمات غير المؤكدة لمراجعتها قبل الحفظ.")
        }
    }, after = { ReviewPanel(review, "مستند") }) { ins, out, p ->
        if (ocr.value) {
            val (pages, _) = ocrPages(ctx, ins, p, com.muyassir.app.core.Plus.ocrPages.coerceAtMost(80))
            val o = File(out, base(ctx, ins[0]) + ".docx"); Office.writeDocx(o, docxFrom(pages))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { review.value = pages.joinToString("\n\n") { it.plain() } to pages.flatMap { it.unsure }.distinct() }
            return@FileTool listOf(o)
        }
        val f = cache(ctx, ins[0])
        val pages = pdfLines(ctx, f, ocr.value, p)
        val paras = ArrayList<Office.Para>()
        pages.forEachIndexed { i, lines ->
            if (i > 0) paras += Office.Para.PageBreak
            // Join wrapped lines back into paragraphs at blank lines.
            val sb = StringBuilder()
            for (l in lines) { if (l.isBlank()) { if (sb.isNotEmpty()) { paras += Office.Para.Text(sb.toString().trim()); sb.clear() } } else sb.append(l.trim()).append(' ') }
            if (sb.isNotEmpty()) paras += Office.Para.Text(sb.toString().trim())
        }
        val o = File(out, base(ctx, ins[0]) + ".docx"); Office.writeDocx(o, paras); listOf(o)
    }
}

@Composable
fun WordToPdf(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    FileTool(t, back, DOCX, false, "اختر ملف Word (DOCX)", "تحويل إلى PDF", options = {
        Hint("يدعم ملفات DOCX. ملفات DOC القديمة احفظها بصيغة DOCX أولا من Word أو Google Docs.")
    }) { ins, out, _ ->
        val paras = Office.readDocx(cache(ctx, ins[0]))
        val b = DocBuilder(ctx)
        paras.forEach { line ->
            if ('\t' in line) b.add(DocBuilder.Block.Table(emptyList(), listOf(line.split('\t'))))
            else if (line.isBlank()) b.add(DocBuilder.Block.Space(8f)) else b.add(DocBuilder.Block.Para(line))
        }
        listOf(b.save(File(out, base(ctx, ins[0]) + ".pdf")))
    }
}

@Composable
fun PdfToExcel(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val ocr = remember { mutableStateOf(false) }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "تحويل إلى Excel", options = {
        Panel { OcrSwitch(ocr); Hint("كل صفحة ورقة عمل، وكل سطر صف. الأعمدة تفصل حيث توجد مسافات واسعة أو علامات جدولة.") }
    }) { ins, out, p ->
        val pages = pdfLines(ctx, cache(ctx, ins[0]), ocr.value, p)
        val sheets = pages.mapIndexed { i, lines ->
            "صفحة ${i + 1}" to lines.filter { it.isNotBlank() }.map { l -> l.trim().split(Regex("\\t|\\s{3,}")).map { it.trim() } }
        }
        val o = File(out, base(ctx, ins[0]) + ".xlsx"); Office.writeXlsx(o, sheets); listOf(o)
    }
}

@Composable
fun ExcelToPdf(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var landscape by remember { mutableStateOf(true) }
    var headerRow by remember { mutableStateOf(true) }
    FileTool(t, back, XLSX, false, "اختر ملف Excel (XLSX)", "تحويل إلى PDF", options = {
        Panel { SwitchRow("صفحة أفقية", landscape) { landscape = it }; SwitchRow("الصف الأول عناوين", headerRow) { headerRow = it } }
    }) { ins, out, _ ->
        val sheets = Office.readXlsx(cache(ctx, ins[0]))
        require(sheets.isNotEmpty()) { "لم يعثر على أوراق عمل في الملف" }
        val b = DocBuilder(ctx, landscape)
        sheets.forEachIndexed { i, (name, rows) ->
            if (i > 0) b.add(DocBuilder.Block.PageBreak)
            b.add(DocBuilder.Block.Heading(name))
            val clean = rows.filter { r -> r.any { it.isNotBlank() } }
            val w = clean.maxOfOrNull { it.size } ?: 0
            val padded = clean.map { r -> r + List(w - r.size) { "" } }
            if (padded.isNotEmpty()) b.add(DocBuilder.Block.Table(if (headerRow) padded.first() else emptyList(), if (headerRow) padded.drop(1) else padded))
        }
        listOf(b.save(File(out, base(ctx, ins[0]) + ".pdf")))
    }
}

@Composable
fun PdfToPpt(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    FileTool(t, back, PDF, false, "اختر ملف PDF", "تحويل إلى PowerPoint", options = {
        Hint("كل صفحة تصبح شريحة بشكلها الأصلي تماما (صورة عالية الدقة)، فتحافظ على الخطوط والتصميم.")
    }) { ins, out, p ->
        val f = cache(ctx, ins[0]); val n = PdfEngine.pageCount(f)
        val slides = (0 until n).map { i ->
            p.label = "الصفحة ${i + 1} من $n"; p.value = i / n.toFloat()
            val bmp = PdfEngine.render(f, i, 150)
            val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            Office.Slide("", imagePng = png, imgW = bmp.width, imgH = bmp.height).also { bmp.recycle() }
        }
        val o = File(out, base(ctx, ins[0]) + ".pptx"); Office.writePptx(o, slides); listOf(o)
    }
}

@Composable
fun PptToPdf(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    FileTool(t, back, PPTX, false, "اختر عرض PowerPoint (PPTX)", "تحويل إلى PDF", options = {
        Hint("تنقل نصوص كل شريحة إلى صفحة أفقية. الصور والأشكال لا تنقل في هذا الإصدار.")
    }) { ins, out, _ ->
        val slides = Office.readPptx(cache(ctx, ins[0]))
        val b = DocBuilder(ctx, landscape = true)
        slides.forEachIndexed { i, s ->
            if (i > 0) b.add(DocBuilder.Block.PageBreak)
            val lines = s.lines()
            b.add(DocBuilder.Block.Title(lines.firstOrNull() ?: "شريحة ${i + 1}", "شريحة ${i + 1}"))
            lines.drop(1).forEach { b.add(DocBuilder.Block.Para("• $it", 14f)) }
        }
        listOf(b.save(File(out, base(ctx, ins[0]) + ".pdf")))
    }
}

@Composable
fun PdfToImages(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var png by remember { mutableIntStateOf(0) }
    var dpi by remember { mutableIntStateOf(1) }
    var range by remember { mutableStateOf("") }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "تحويل إلى صور", options = {
        Panel {
            Segmented(listOf("JPG", "PNG"), png) { png = it }
            Text("الدقة", style = MaterialTheme.typography.labelLarge)
            Segmented(listOf("عادية 100", "عالية 150", "طباعة 300"), dpi) { dpi = it }
            TextField2("الصفحات (اتركه فارغا للكل) مثل 1-3, 7", range, { range = it })
        }
    }) { ins, out, p ->
        val f = cache(ctx, ins[0]); val n = PdfEngine.pageCount(f)
        val pages = if (range.isBlank()) (0 until n).toList() else PdfEngine.parseRanges(range, n)
        PdfEngine.toImages(f, pages, listOf(100, 150, 300)[dpi], png == 1, out, base(ctx, ins[0])) { i, total -> p.value = i / total.toFloat(); p.label = "صفحة ${i + 1} من $total" }
    }
}

@Composable
fun ImagesToPdf(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var a4 by remember { mutableStateOf(true) }
    FileTool(t, back, IMAGES, true, "اختر الصور", "إنشاء PDF", reorderable = true, options = {
        Panel { SwitchRow("ضبط على صفحات A4", a4) { a4 = it }; Text("رتب الصور بالأسهم قبل الإنشاء.", style = MaterialTheme.typography.bodySmall) }
    }) { ins, out, p ->
        val seq = ins.asSequence().mapIndexed { i, u -> p.value = i / ins.size.toFloat(); ImageEngine.load(ctx, u, 2400) }
        listOf(PdfEngine.fromImages(seq, a4, File(out, "صور-${ins.size}.pdf")))
    }
}

@Composable
fun PdfMerge(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    FileTool(t, back, PDF, true, "اختر ملفين أو أكثر", "دمج الملفات", reorderable = true,
        validate = { if (it.size < 2) "اختر ملفين على الأقل" else null }) { ins, out, p ->
        p.label = "جار الدمج…"
        listOf(PdfEngine.merge(ins.map { cache(ctx, it) }, File(out, "مدمج-${ins.size}.pdf")))
    }
}

@Composable
fun PdfSplit(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }
    var spec by remember { mutableStateOf("") }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "تقسيم", options = { ins ->
        val n = remember(ins) { runCatching { PdfEngine.pageCount(Files.toCache(ctx, ins[0])) }.getOrDefault(0) }
        Panel {
            Text("عدد الصفحات: $n", style = MaterialTheme.typography.bodyMedium)
            Segmented(listOf("كل صفحة ملف", "حسب النطاقات"), mode) { mode = it }
            if (mode == 1) TextField2("النطاقات مفصولة بفاصلة، كل نطاق ملف: 1-3, 4-6, 7-", spec, { spec = it })
        }
    }, validate = { if (it.isEmpty()) "اختر ملفا" else if (mode == 1 && spec.isBlank()) "اكتب النطاقات" else null }) { ins, out, _ ->
        PdfEngine.split(cache(ctx, ins[0]), spec, mode == 0, out, base(ctx, ins[0]))
    }
}

@Composable
fun PdfCompress(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(1) }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "ضغط الملف", options = {
        Panel {
            Segmented(listOf("خفيف", "متوازن", "قوي"), level) { level = it }
            Hint("يعاد رسم الصفحات كصور مضغوطة، فيصغر الحجم كثيرا خاصة للملفات الممسوحة ضوئيا. النص يصبح غير قابل للتحديد.")
        }
    }) { ins, out, p ->
        val f = cache(ctx, ins[0])
        val (dpi, q) = listOf(150 to 80, 110 to 65, 80 to 45)[level]
        val o = PdfEngine.compress(f, dpi, q, File(out, base(ctx, ins[0]) + "-مضغوط.pdf")) { i, n -> p.value = i / n.toFloat(); p.label = "صفحة ${i + 1} من $n" }
        p.label = "من ${Files.human(f.length())} إلى ${Files.human(o.length())}"
        listOf(o)
    }
}

@Composable
private fun PageCount(ctx: Context, ins: List<Uri>) {
    val n = remember(ins) { runCatching { PdfEngine.pageCount(Files.toCache(ctx, ins[0])) }.getOrDefault(0) }
    Text("عدد الصفحات: $n", style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun PdfReorder(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }
    var order by remember { mutableStateOf("") }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "إعادة الترتيب", options = { ins ->
        Panel {
            PageCount(ctx, ins)
            Segmented(listOf("ترتيب مخصص", "عكس الترتيب"), mode) { mode = it }
            if (mode == 0) TextField2("الترتيب الجديد مثل: 3, 1, 2, 4-10", order, { order = it })
        }
    }, validate = { if (it.isEmpty()) "اختر ملفا" else if (mode == 0 && order.isBlank()) "اكتب الترتيب" else null }) { ins, out, _ ->
        val f = cache(ctx, ins[0]); val n = PdfEngine.pageCount(f)
        val pages = if (mode == 1) (n - 1 downTo 0).toList() else PdfEngine.parseRanges(order, n)
        listOf(PdfEngine.select(f, pages, File(out, base(ctx, ins[0]) + "-مرتب.pdf")))
    }
}

@Composable
fun PdfDelete(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var spec by remember { mutableStateOf("") }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "حذف الصفحات", options = { ins ->
        Panel { PageCount(ctx, ins); TextField2("الصفحات المطلوب حذفها مثل: 2, 5-7", spec, { spec = it }) }
    }, validate = { if (it.isEmpty()) "اختر ملفا" else if (spec.isBlank()) "حدد الصفحات" else null }) { ins, out, _ ->
        val f = cache(ctx, ins[0])
        listOf(PdfEngine.delete(f, PdfEngine.parseRanges(spec, PdfEngine.pageCount(f)).toSet(), File(out, base(ctx, ins[0]) + "-معدل.pdf")))
    }
}

@Composable
fun PdfExtract(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var spec by remember { mutableStateOf("") }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "استخراج", options = { ins ->
        Panel { PageCount(ctx, ins); TextField2("الصفحات المطلوبة مثل: 1, 4-6", spec, { spec = it }) }
    }, validate = { if (it.isEmpty()) "اختر ملفا" else if (spec.isBlank()) "حدد الصفحات" else null }) { ins, out, _ ->
        val f = cache(ctx, ins[0])
        listOf(PdfEngine.select(f, PdfEngine.parseRanges(spec, PdfEngine.pageCount(f)), File(out, base(ctx, ins[0]) + "-مستخرج.pdf")))
    }
}

@Composable
fun PdfRotate(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var deg by remember { mutableIntStateOf(0) }
    var spec by remember { mutableStateOf("") }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "تدوير", options = { ins ->
        Panel {
            PageCount(ctx, ins)
            Segmented(listOf("90° يمين", "180°", "90° يسار"), deg) { deg = it }
            TextField2("الصفحات (فارغ = الكل)", spec, { spec = it })
        }
    }) { ins, out, _ ->
        val f = cache(ctx, ins[0])
        val pages = if (spec.isBlank()) null else PdfEngine.parseRanges(spec, PdfEngine.pageCount(f))
        listOf(PdfEngine.rotate(f, pages, listOf(90, 180, 270)[deg], File(out, base(ctx, ins[0]) + "-مدور.pdf")))
    }
}

@Composable
fun PdfProtect(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var print by remember { mutableStateOf(true) }
    var copyOk by remember { mutableStateOf(false) }
    var mark by remember { mutableStateOf("") }
    FileTool(t, back, PDF, false, "اختر ملف PDF", "حماية الملف", options = {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Text("علامة مائية (اختياري)", style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(6.dp)); com.muyassir.app.ui.PlusBadge()
            }
            TextField2("نص العلامة، مثل: سري أو نسخة للمراجعة", mark, { if (it.isEmpty() || com.muyassir.app.core.Plus.allow(com.muyassir.app.core.Plus.Reason.Sign)) mark = it })
        }
        Panel {
            TextField2("كلمة المرور", pass, { pass = it }, keyboard = androidx.compose.ui.text.input.KeyboardType.Password)
            TextField2("تأكيد كلمة المرور", pass2, { pass2 = it }, keyboard = androidx.compose.ui.text.input.KeyboardType.Password)
            SwitchRow("السماح بالطباعة", print) { print = it }
            SwitchRow("السماح بنسخ النص", copyOk) { copyOk = it }
            Hint("احتفظ بكلمة المرور؛ لا يمكن استرجاعها إن نسيت. التشفير AES بطول 128 بت.")
        }
    }, validate = { when { it.isEmpty() -> "اختر ملفا"; pass.length < 4 -> "كلمة المرور 4 أحرف على الأقل"; pass != pass2 -> "كلمتا المرور غير متطابقتين"; else -> null } }) { ins, out, _ ->
        var src = cache(ctx, ins[0])
        if (mark.isNotBlank() && com.muyassir.app.core.Plus.unlocked) src = PdfEngine.watermark(src, mark.trim(), File(ctx.cacheDir, "in/wm.pdf").apply { parentFile?.mkdirs() })
        listOf(PdfEngine.protect(src, pass, "", print, copyOk, File(out, base(ctx, ins[0]) + "-محمي.pdf")))
    }
}

@Composable
fun PdfUnlock(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var pass by remember { mutableStateOf("") }
    FileTool(t, back, PDF, false, "اختر ملف PDF المحمي", "إزالة الحماية", options = {
        Panel { TextField2("كلمة مرور الملف", pass, { pass = it }); Hint("استخدمها فقط لملفات تملكها أو مصرح لك بها.") }
    }) { ins, out, _ ->
        try { listOf(PdfEngine.unlock(cache(ctx, ins[0]), pass, File(out, base(ctx, ins[0]) + "-مفتوح.pdf"))) }
        catch (e: Exception) { throw IllegalStateException("كلمة المرور غير صحيحة") }
    }
}

@Composable
fun ZipTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("أرشيف") }
    var level by remember { mutableIntStateOf(1) }
    FileTool(t, back, arrayOf("*/*"), true, "اختر الملفات", "إنشاء ZIP", options = {
        Panel { TextField2("اسم الأرشيف", name, { name = it }); Segmented(listOf("سريع", "متوازن", "أقصى ضغط"), level) { level = it } }
    }) { ins, out, p ->
        val files = ins.mapIndexed { i, u -> p.value = i / ins.size.toFloat(); Files.name(ctx, u) to cache(ctx, u) }
        listOf(ZipEngine.zip(files, File(out, "${name.ifBlank { "أرشيف" }}.zip"), listOf(1, 6, 9)[level]))
    }
}

@Composable
fun UnzipTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    FileTool(t, back, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"), false, "اختر ملف ZIP", "فك الضغط") { ins, out, _ ->
        ZipEngine.unzip(cache(ctx, ins[0]), File(out, base(ctx, ins[0]))).ifEmpty { error("الأرشيف فارغ أو تالف") }
    }
}
