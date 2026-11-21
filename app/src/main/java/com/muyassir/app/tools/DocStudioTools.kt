package com.muyassir.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.DocBuilder
import com.muyassir.app.engine.ExamParser
import com.muyassir.app.engine.ImageEngine
import com.muyassir.app.engine.Layout
import com.muyassir.app.engine.Ocr
import com.muyassir.app.engine.Office
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Pages (images or PDF pages) → OCR words → reconstructed layout. Pages are separated by a page break in Word. */
fun ocrPages(ctx: Context, inputs: List<Uri>, p: Progress, maxPages: Int): Pair<List<Layout.Result>, Int> {
    val pages = ArrayList<Layout.Result>(); var skipped = 0
    fun one(bmp: Bitmap) { pages += Layout.analyze(Ocr.recognizeWords(ctx, bmp, Ocr.Lang.AR)); bmp.recycle() }
    inputs.forEachIndexed { i, u ->
        val f = Files.toCache(ctx, u)
        if (Files.name(ctx, u).lowercase().endsWith(".pdf")) {
            val n = PdfEngine.pageCount(f)
            for (pg in 0 until n) {
                if (pages.size >= maxPages) { skipped++; continue }
                p.label = "التعرف على الصفحة ${pg + 1} من $n"; p.value = pg / n.toFloat()
                one(PdfEngine.render(f, pg, 220))
            }
        } else {
            if (pages.size >= maxPages) { skipped++; return@forEachIndexed }
            p.label = "التعرف على الصورة ${i + 1} من ${inputs.size}"; p.value = i / inputs.size.toFloat()
            one(ImageEngine.load(ctx, u, 3000))
        }
    }
    return pages to skipped
}

fun docxFrom(pages: List<Layout.Result>): List<Office.Para> = pages.flatMapIndexed { i, r -> (if (i > 0) listOf(Office.Para.PageBreak) else emptyList()) + Layout.toDocx(r) }

/**
 * Review before saving: the recognised text is editable, unsure words are listed so the user checks them,
 * and "save" rebuilds a real Word file from the edited text. Accuracy is never claimed to be 100%.
 */
@Composable
fun ReviewPanel(review: MutableState<Pair<String, List<String>>?>, name: String) {
    val ctx = LocalContext.current
    val r = review.value ?: return
    var text by remember(r) { mutableStateOf(r.first) }
    var saved by remember(r) { mutableStateOf<List<File>>(emptyList()) }
    Panel("راجع النص قبل الحفظ") {
        if (r.second.isNotEmpty()) {
            Text("كلمات غير مؤكدة (${r.second.size}): تحقّق منها في النص", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            ChipRow { r.second.take(40).forEach { w -> Chip(w, false) {} } }
        } else Hint("لم يجد المحرك كلمات منخفضة الثقة، لكن راجع الأسماء والأرقام دائمًا.")
        TextField2("النص المستخرج (الجداول: أعمدة مفصولة بـ |)", text, { text = it }, Modifier.testTag("reviewText"), lines = 12)
        PrimaryButton("حفظ Word بعد المراجعة", Icons.Rounded.Description, modifier = Modifier.testTag("saveReviewed")) {
            val o = File(Files.outDir(ctx), "$name-مراجع.docx")
            Office.writeDocx(o, Layout.toDocx(Layout.fromText(text))); saved = listOf(o)
        }
    }
    if (saved.isNotEmpty()) ResultFiles(saved)
}

@Composable
fun ImagesToWord(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val review = remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var name by remember { mutableStateOf("مستند") }
    FileTool(t, back, IMAGES + arrayOf("application/pdf"), true, "اختر صورًا أو ملف PDF مصوّر", "تحويل إلى Word", reorderable = true, options = {
        Hint("يتعرّف على النص العربي والإنجليزي على جهازك ويعيد بناء العناوين والفقرات والجداول في ملف Word قابل للتعديل. الدقة تعتمد على وضوح الصورة، وستظهر لك الكلمات غير المؤكدة لمراجعتها.")
    }, after = { ReviewPanel(review, name) }) { ins, out, p ->
        val (pages, skipped) = ocrPages(ctx, ins, p, com.muyassir.app.core.Plus.ocrPages.coerceAtMost(60))
        if (pages.all { it.blocks.isEmpty() }) error("لم يُعثر على نص. جرّب صورة أوضح أو أقرب.")
        val n = Files.base(Files.name(ctx, ins[0])); name = n
        val o = File(out, "$n.docx"); Office.writeDocx(o, docxFrom(pages))
        withContext(Dispatchers.Main) { review.value = pages.joinToString("\n\n") { it.plain() } to pages.flatMap { it.unsure }.distinct() }
        if (skipped > 0) withContext(Dispatchers.Main) { com.muyassir.app.core.Plus.paywall = com.muyassir.app.core.Plus.Reason.Ocr }
        Store.used(t.id, n); listOf(o)
    }
}

/** A photographed or scanned test becomes an editable Word file, and its questions go to the quiz maker and the question bank. */
@Composable
fun ExamToDoc(t: Tool, back: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    var qs by remember { mutableStateOf<List<ExamParser.Q>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }
    FileTool(t, back, IMAGES + arrayOf("application/pdf"), true, "صوّر الاختبار أو اختر صوره", "استخراج الأسئلة", reorderable = true, options = {
        Hint("يتعرّف على الأسئلة المرقمة واختياراتها (أ، ب، ج، د) وأسئلة الصح والخطأ، ثم يبني منها ملف Word قابلًا للتعديل. راجع النتيجة قبل الطباعة.")
    }, after = {
        if (qs.isNotEmpty()) Panel("${qs.size} سؤالًا", Modifier.testTag("examQuestions")) {
            qs.take(30).forEachIndexed { i, q ->
                Text("${i + 1}) ${q.text}", style = MaterialTheme.typography.bodyMedium)
                q.choices.forEach { c -> Text("   ○ $c", style = MaterialTheme.typography.bodySmall) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("افتحها في إنشاء الاختبارات", Icons.Rounded.Quiz, Modifier.weight(1f)) {
                    Store.saveList("quiz_draft", qs.map { toQuiz(it) }); open("quiz_maker")
                }
                SecondaryButton("أضفها لبنك الأسئلة", Icons.Rounded.Inventory2, Modifier.weight(1f)) {
                    val bank = Store.list("qbank"); val have = bank.map { it.optString("q") }.toSet()
                    val add = qs.filter { it.text !in have }.map { toQuiz(it).put("subject", "مستخرج من صورة") }
                    Store.saveList("qbank", bank + add); msg = "أُضيف ${add.size} سؤالًا إلى البنك"
                }
            }
            msg?.let { Text(it, color = Brand.Mint) }
        }
    }) { ins, out, p ->
        val (pages, _) = ocrPages(ctx, ins, p, com.muyassir.app.core.Plus.ocrPages.coerceAtMost(30))
        val text = pages.joinToString("\n") { r -> r.blocks.joinToString("\n") { b -> when (b) { is Layout.Block.Heading -> b.text; is Layout.Block.Para -> b.text; is Layout.Block.Table -> b.rows.joinToString("\n") { it.joinToString(" ") } } } }
        val found = ExamParser.parse(text)
        if (found.isEmpty()) error("لم أجد أسئلة مرقمة. تأكد أن الصورة واضحة وأن الأسئلة تبدأ بأرقام.")
        withContext(Dispatchers.Main) { qs = found; msg = null }
        val paras = ArrayList<Office.Para>()
        found.forEachIndexed { i, q ->
            paras += Office.Para.Text("${i + 1}) ${q.text}", bold = true)
            q.choices.forEachIndexed { k, c -> paras += Office.Para.Text("    ${"أبجدهو".getOrElse(k) { '-' }}) $c") }
            if (q.kind == ExamParser.Kind.TRUE_FALSE) paras += Office.Para.Text("    (     ) صح        (     ) خطأ")
            if (q.kind == ExamParser.Kind.OPEN) paras += Office.Para.Text("    ..............................................................")
        }
        val o = File(out, "اختبار-قابل-للتعديل.docx"); Office.writeDocx(o, paras); Store.used(t.id); listOf(o)
    }
}

/** Quiz maker record (type 0 choice, 1 true/false, 2 open), matching QuizMaker's fields. */
fun toQuiz(q: ExamParser.Q): JSONObject {
    val o = JSONObject().put("q", q.text).put("type", when (q.kind) { ExamParser.Kind.CHOICE -> 0; ExamParser.Kind.TRUE_FALSE -> 1; ExamParser.Kind.OPEN -> 2 }).put("ans", 0).put("mark", "1").put("lines", "3")
    q.choices.take(4).forEachIndexed { k, c -> o.put("o$k", c) }
    return o
}

/** Header, footer and page numbers on every page of a PDF. */
@Composable
fun PdfHeaderFooter(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var header by remember { mutableStateOf("") }; var footer by remember { mutableStateOf("") }
    var numbers by remember { mutableStateOf(true) }; var style by remember { mutableIntStateOf(0) }; var skipFirst by remember { mutableStateOf(false) }
    FileTool(t, back, arrayOf("application/pdf"), false, "اختر ملف PDF", "إضافة إلى الصفحات", options = {
        Panel {
            TextField2("الترويسة (أعلى كل صفحة)", header, { header = it })
            TextField2("التذييل (أسفل كل صفحة)", footer, { footer = it })
            SwitchRow("ترقيم الصفحات", numbers) { numbers = it }
            if (numbers) Segmented(listOf("1", "صفحة 1 من 10", "- 1 -"), style) { style = it }
            SwitchRow("بدون ترويسة ورقم على الصفحة الأولى (الغلاف)", skipFirst) { skipFirst = it }
        }
    }, validate = { if (it.isEmpty()) "اختر ملفًا أولًا" else if (header.isBlank() && footer.isBlank() && !numbers) "أضف ترويسة أو تذييلًا أو فعّل الترقيم" else null }) { ins, out, p ->
        val f = Files.toCache(ctx, ins[0]); val n = PdfEngine.pageCount(f)
        val items = ArrayList<PdfEngine.Overlay>()
        val bmp0 = PdfEngine.render(f, 0, 30); val ratio = bmp0.height / bmp0.width.toFloat(); bmp0.recycle()
        val wPt = 595f; val hPt = wPt * ratio
        for (pg in 0 until n) {
            if (skipFirst && pg == 0) continue
            p.value = pg / n.toFloat(); p.label = "الصفحة ${pg + 1} من $n"
            fun put(text: String, top: Boolean, rw: Float) {
                val b = textBitmap(ctx, text, 9f, 0xFF4A5068.toInt(), wPt * rw, 14f)
                items += PdfEngine.Overlay(pg, (1f - rw) / 2f, if (top) 18f / hPt else 1f - 32f / hPt, rw, b.height / 3f / hPt, b)
            }
            if (header.isNotBlank()) put(header, true, 0.8f)
            val num = if (!numbers) "" else when (style) { 1 -> "صفحة ${pg + 1} من $n"; 2 -> "- ${pg + 1} -"; else -> "${pg + 1}" }
            val bottom = listOf(footer.trim(), num).filter { it.isNotEmpty() }.joinToString("    •    ")
            if (bottom.isNotBlank()) put(bottom, false, 0.8f)
        }
        val o = File(out, Files.base(Files.name(ctx, ins[0])) + "-مرقّم.pdf"); PdfEngine.annotate(f, items, o); Store.used(t.id); listOf(o)
    }
}


