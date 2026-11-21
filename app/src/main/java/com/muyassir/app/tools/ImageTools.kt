package com.muyassir.app.tools

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.ImageEngine
import com.muyassir.app.engine.ImageEngine.Fmt
import com.muyassir.app.engine.Ocr
import com.muyassir.app.engine.Office
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun FormatPicker(fmt: MutableState<Int>) = Segmented(Fmt.entries.map { it.label }, fmt.value) { fmt.value = it }

@Composable
private fun Preview(uri: Uri?, overlay: (androidx.compose.ui.graphics.drawscope.DrawScope.(Size) -> Unit)? = null) {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) { bmp = uri?.let { withContext(Dispatchers.IO) { runCatching { ImageEngine.load(ctx, it, 1200) }.getOrNull() } } }
    bmp?.let { b ->
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B2140)), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height).heightIn(max = 420.dp)) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                overlay?.let { o -> Canvas(Modifier.fillMaxSize()) { o(this, size) } }
            }
        }
        Text("${b.width}×${b.height} (معاينة)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun outName(ctx: android.content.Context, u: Uri, suffix: String, fmt: Fmt) = Files.base(Files.name(ctx, u)) + suffix + "." + fmt.ext

@Composable
fun ImgConvert(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val fmt = remember { mutableStateOf(0) }
    var q by remember { mutableFloatStateOf(90f) }
    FileTool(t, back, IMAGES, true, "اختر صورة أو أكثر", "تحويل", options = {
        Panel { FormatPicker(fmt); if (Fmt.entries[fmt.value] != Fmt.PNG) { Text("الجودة ${q.toInt()}%"); Slider(q, { q = it }, valueRange = 40f..100f) } }
    }) { ins, out, p ->
        val f = Fmt.entries[fmt.value]
        ins.mapIndexed { i, u -> p.value = i / ins.size.toFloat(); ImageEngine.save(ImageEngine.load(ctx, u), f, q.toInt(), File(out, outName(ctx, u, "", f))) }
    }
}

@Composable
fun ImgCompress(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }
    var q by remember { mutableFloatStateOf(70f) }
    var kb by remember { mutableStateOf("200") }
    FileTool(t, back, IMAGES, true, "اختر الصور", "ضغط", options = {
        Panel {
            Segmented(listOf("حسب الجودة", "حجم مستهدف"), mode) { mode = it }
            if (mode == 0) { Text("الجودة ${q.toInt()}%"); Slider(q, { q = it }, valueRange = 20f..95f) }
            else NumField("الحجم الأقصى لكل صورة", kb, { kb = it }, suffix = "ك.ب")
            Hint("مفيد لرفع الصور على المنصات التي تحدد حجمًا أقصى للمرفقات.")
        }
    }) { ins, out, p ->
        ins.mapIndexed { i, u ->
            p.value = i / ins.size.toFloat()
            val b = ImageEngine.load(ctx, u); val o = File(out, outName(ctx, u, "-مضغوط", Fmt.JPG))
            if (mode == 0) ImageEngine.save(b, Fmt.JPG, q.toInt(), o) else ImageEngine.compressToSize(b, kb.num().toInt().coerceAtLeast(10), o)
        }
    }
}

@Composable
fun ImgResize(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }
    var w by remember { mutableStateOf("1080") }
    var h by remember { mutableStateOf("1080") }
    var pct by remember { mutableFloatStateOf(50f) }
    var keep by remember { mutableStateOf(true) }
    val fmt = remember { mutableStateOf(0) }
    FileTool(t, back, IMAGES, true, "اختر الصور", "تغيير الأبعاد", options = { ins ->
        Preview(ins.firstOrNull())
        Panel {
            Segmented(listOf("بالبكسل", "بالنسبة"), mode) { mode = it }
            if (mode == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumField("العرض", w, { w = it }, Modifier.weight(1f)); NumField("الارتفاع", h, { h = it }, Modifier.weight(1f)) }
                SwitchRow("الحفاظ على النسبة", keep) { keep = it }
            } else { Text("${pct.toInt()}%"); Slider(pct, { pct = it }, valueRange = 5f..200f) }
            FormatPicker(fmt)
        }
    }) { ins, out, p ->
        val f = Fmt.entries[fmt.value]
        ins.mapIndexed { i, u ->
            p.value = i / ins.size.toFloat()
            val b = ImageEngine.load(ctx, u, 8000)
            val r = if (mode == 0) ImageEngine.resize(b, w.num().toInt(), h.num().toInt(), keep)
            else ImageEngine.resize(b, (b.width * pct / 100).toInt(), (b.height * pct / 100).toInt(), false)
            ImageEngine.save(r, f, 92, File(out, outName(ctx, u, "-${r.width}x${r.height}", f)))
        }
    }
}

@Composable
fun ImgCrop(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val ratios = listOf("حر" to 0f, "1:1" to 1f, "4:3" to 4f / 3, "3:4" to 3f / 4, "16:9" to 16f / 9, "9:16" to 9f / 16)
    var ratio by remember { mutableIntStateOf(0) }
    var l by remember { mutableFloatStateOf(0.1f) }; var tp by remember { mutableFloatStateOf(0.1f) }
    var r by remember { mutableFloatStateOf(0.9f) }; var b by remember { mutableFloatStateOf(0.9f) }
    FileTool(t, back, IMAGES, false, "اختر صورة", "قص", options = { ins ->
        Panel {
            ChipRow { ratios.forEachIndexed { i, (n, _) -> Chip(n, i == ratio) { ratio = i } } }
        }
        if (ratio == 0) {
            Preview(ins.firstOrNull()) { s -> drawRect(Color(0xFFFFC23D), Offset(l * s.width, tp * s.height), Size((r - l) * s.width, (b - tp) * s.height), style = Stroke(6f)) }
            Panel {
                Text("اليمين/اليسار"); RangeSlider(l..r, { l = it.start; r = it.endInclusive }, valueRange = 0f..1f)
                Text("الأعلى/الأسفل"); RangeSlider(tp..b, { tp = it.start; b = it.endInclusive }, valueRange = 0f..1f)
            }
        } else Preview(ins.firstOrNull())
    }) { ins, out, _ ->
        val bmp = ImageEngine.load(ctx, ins[0], 8000)
        val res = if (ratio == 0) ImageEngine.cropRect(bmp, l, tp, r, b) else { val rr = ratios[ratio].second; ImageEngine.cropRatio(bmp, rr, 1f) }
        listOf(ImageEngine.save(res, Fmt.PNG, 100, File(out, outName(ctx, ins[0], "-مقصوص", Fmt.PNG))))
    }
}

@Composable
fun ImgRotate(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var deg by remember { mutableIntStateOf(0) }
    var fh by remember { mutableStateOf(false) }; var fv by remember { mutableStateOf(false) }
    FileTool(t, back, IMAGES, true, "اختر الصور", "تطبيق", options = { ins ->
        Preview(ins.firstOrNull())
        Panel {
            Segmented(listOf("بدون", "90°", "180°", "270°"), deg) { deg = it }
            SwitchRow("قلب أفقي", fh) { fh = it }; SwitchRow("قلب عمودي", fv) { fv = it }
        }
    }) { ins, out, _ ->
        ins.map { u -> ImageEngine.save(ImageEngine.rotate(ImageEngine.load(ctx, u, 8000), deg * 90f, fh, fv), Fmt.JPG, 95, File(out, outName(ctx, u, "-مدور", Fmt.JPG))) }
    }
}

@Composable
fun ImgEnhance(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var auto by remember { mutableStateOf(true) }
    var br by remember { mutableFloatStateOf(0f) }; var ct by remember { mutableFloatStateOf(1f) }
    var sat by remember { mutableFloatStateOf(1f) }; var sharp by remember { mutableStateOf(true) }; var gray by remember { mutableStateOf(false) }
    FileTool(t, back, IMAGES, true, "اختر الصور", "تحسين", options = { ins ->
        Preview(ins.firstOrNull())
        Panel {
            SwitchRow("تحسين تلقائي", auto) { auto = it }
            if (!auto) {
                Text("السطوع ${br.toInt()}"); Slider(br, { br = it }, valueRange = -80f..80f)
                Text("التباين ${fmt(ct.toDouble())}"); Slider(ct, { ct = it }, valueRange = 0.5f..2f)
                Text("التشبع ${fmt(sat.toDouble())}"); Slider(sat, { sat = it }, valueRange = 0f..2f)
                SwitchRow("زيادة الحدّة", sharp) { sharp = it }; SwitchRow("أبيض وأسود", gray) { gray = it }
            }
        }
    }) { ins, out, p ->
        ins.mapIndexed { i, u ->
            p.value = i / ins.size.toFloat()
            val b = ImageEngine.load(ctx, u, 3000)
            val r = if (auto) ImageEngine.autoEnhance(b) else ImageEngine.adjust(b, br, ct, sat, sharp, gray)
            ImageEngine.save(r, Fmt.JPG, 94, File(out, outName(ctx, u, "-محسنة", Fmt.JPG)))
        }
    }
}

@Composable
fun ImgBackground(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var bg by remember { mutableIntStateOf(0) }
    var hq by remember { mutableStateOf(false) }
    val colors = listOf("شفاف" to 0, "أبيض" to AColor.WHITE, "أزرق" to 0xFF2F4BFF.toInt(), "رمادي" to 0xFFE5E7EB.toInt())
    // Plus: custom background colours
    val custom = listOf("أسود" to 0xFF111111.toInt(), "كحلي" to 0xFF1C2453.toInt(), "أحمر" to 0xFFC62F3A.toInt(), "أخضر" to 0xFF23814A.toInt(),
        "زهري" to 0xFFF6C9D5.toInt(), "سماوي" to 0xFFCFE8F7.toInt(), "بيج" to 0xFFF1E6D2.toInt(), "ذهبي" to 0xFFF2B01E.toInt())
    val all = colors + custom
    FileTool(t, back, IMAGES, true, "اختر صورة لشخص أو منتج", "إزالة الخلفية", options = { ins ->
        Preview(ins.firstOrNull())
        Panel {
            Text("الخلفية الجديدة", style = MaterialTheme.typography.labelLarge)
            ChipRow { colors.forEachIndexed { i, (n, _) -> Chip(n, i == bg) { bg = i } } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("ألوان مخصصة", style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(6.dp)); com.muyassir.app.ui.PlusBadge() }
            ChipRow { custom.forEachIndexed { j, (n, _) -> val i = colors.size + j; Chip(n, i == bg) { if (com.muyassir.app.core.Plus.allow(com.muyassir.app.core.Plus.Reason.Quality)) bg = i } } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SwitchRow("دقة عالية (حتى 4096 بكسل)", hq) { if (!it || com.muyassir.app.core.Plus.allow(com.muyassir.app.core.Plus.Reason.Quality)) hq = it } }
                com.muyassir.app.ui.PlusBadge()
            }
            Hint("يعمل على جهازك دون رفع الصورة. يدعم الأشخاص فورًا، ويدعم أي عنصر بعد أن تنزّل خدمات Google Play نموذجها الإضافي تلقائيًا.")
        }
    }) { ins, out, p ->
        val side = if (hq && com.muyassir.app.core.Plus.unlocked) 4096 else 2048
        ins.mapIndexed { i, u ->
            p.value = i / ins.size.toFloat(); p.label = "صورة ${i + 1} من ${ins.size}"
            val fg = ImageEngine.removeBackground(ctx, ImageEngine.load(ctx, u, side)) { p.label = it }
            if (bg == 0) ImageEngine.save(fg, Fmt.PNG, 100, File(out, outName(ctx, u, "-بدون-خلفية", Fmt.PNG)))
            else ImageEngine.save(ImageEngine.flatten(fg, all[bg].second), Fmt.JPG, 95, File(out, outName(ctx, u, "-خلفية", Fmt.JPG)))
        }
    }
}

@Composable
fun ImgBatch(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var maxSide by remember { mutableStateOf("1600") }
    var q by remember { mutableFloatStateOf(80f) }
    val fmt = remember { mutableStateOf(0) }
    var enhance by remember { mutableStateOf(false) }
    var gray by remember { mutableStateOf(false) }
    FileTool(t, back, IMAGES, true, "اختر عشرات الصور دفعة واحدة", "معالجة الكل", options = { ins ->
        Panel("الإعدادات (${ins.size} صورة)") {
            NumField("أقصى طول للضلع (0 = بدون تغيير)", maxSide, { maxSide = it }, suffix = "بكسل")
            Text("الجودة ${q.toInt()}%"); Slider(q, { q = it }, valueRange = 30f..100f)
            FormatPicker(fmt)
            SwitchRow("تحسين تلقائي", enhance) { enhance = it }; SwitchRow("أبيض وأسود", gray) { gray = it }
        }
    }) { ins, out, p ->
        val f = Fmt.entries[fmt.value]; val m = maxSide.num().toInt()
        ins.mapIndexed { i, u ->
            p.value = i / ins.size.toFloat(); p.label = "صورة ${i + 1} من ${ins.size}"
            var b = ImageEngine.load(ctx, u, 6000)
            if (m > 0 && maxOf(b.width, b.height) > m) b = ImageEngine.resize(b, m, m, true)
            if (enhance) b = ImageEngine.autoEnhance(b)
            if (gray) b = ImageEngine.adjust(b, 0f, 1f, 0f, false, true)
            ImageEngine.save(b, f, q.toInt(), File(out, outName(ctx, u, "", f))).also { b.recycle() }
        }
    }
}

@Composable
fun IdPhoto(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    // The first two sizes are free; the rest and the print sheet are Plus (open to everyone until Plus launches).
    val sizes = listOf("جواز/هوية 4×6 سم" to (40f to 60f), "تأشيرة 3.5×4.5 سم" to (35f to 45f), "2×2 بوصة" to (50.8f to 50.8f), "3×4 سم" to (30f to 40f),
        "5×7 سم" to (50f to 70f), "3.5×4 سم" to (35f to 40f), "4.5×3.5 سم (عرضي)" to (45f to 35f), "2.5×3 سم" to (25f to 30f))
    var size by remember { mutableIntStateOf(0) }
    var removeBg by remember { mutableStateOf(true) }
    var bg by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(false) }
    val bgs = listOf("أبيض" to AColor.WHITE, "أزرق فاتح" to 0xFFDCE7F7.toInt(), "رمادي فاتح" to 0xFFEFEFEF.toInt())
    FileTool(t, back, IMAGES, false, "اختر صورة شخصية واضحة", "تجهيز الصورة", options = { ins ->
        Preview(ins.firstOrNull())
        Panel {
            Text("المقاس", style = MaterialTheme.typography.labelLarge)
            ChipRow {
                sizes.forEachIndexed { i, (n, _) ->
                    val locked = i >= 2 && !com.muyassir.app.core.Plus.unlocked
                    Chip(if (locked) "$n • بلس" else n, i == size) { if (i < 2 || com.muyassir.app.core.Plus.allow(com.muyassir.app.core.Plus.Reason.Sizes)) size = i }
                }
            }
            SwitchRow("استبدال الخلفية", removeBg) { removeBg = it }
            if (removeBg) ChipRow { bgs.forEachIndexed { i, (n, _) -> Chip(n, i == bg) { bg = i } } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SwitchRow("ورقة طباعة 10×15 سم بعدة نسخ", sheet) { if (!it || com.muyassir.app.core.Plus.allow(com.muyassir.app.core.Plus.Reason.Sizes)) sheet = it } }
                com.muyassir.app.ui.PlusBadge()
            }
            Hint("تأكد من متطلبات الجهة (لون الخلفية والمقاس) قبل الطباعة. الدقة 300 نقطة لكل بوصة.")
        }
    }) { ins, out, p ->
        var b = ImageEngine.load(ctx, ins[0], 2400)
        if (removeBg) {
            val cut = runCatching { ImageEngine.removeBackground(ctx, b) { p.label = it } }
            if (cut.isSuccess) b = ImageEngine.flatten(cut.getOrThrow(), bgs[bg].second)
            else p.label = "تعذّر استبدال الخلفية (${cut.exceptionOrNull()?.message ?: "لا يوجد شخص واضح"})، فحُفظت بخلفيتها الأصلية"
        }
        val pick = if (size >= 2 && !com.muyassir.app.core.Plus.unlocked) 0 else size
        val (w, h) = sizes[pick].second
        val photo = ImageEngine.idPhoto(b, w, h, bgs[bg].second)
        val list = mutableListOf(ImageEngine.save(photo, Fmt.JPG, 97, File(out, "صورة-وثيقة-${w.toInt()}x${h.toInt()}.jpg")))
        if (sheet && com.muyassir.app.core.Plus.unlocked) list += ImageEngine.save(ImageEngine.printSheet(photo), Fmt.JPG, 97, File(out, "ورقة-طباعة-10x15.jpg"))
        list
    }
}

@Composable
fun OcrTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var lang by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var limited by remember { mutableStateOf(0) }
    var searchable by remember { mutableStateOf(true) }
    FileTool(t, back, arrayOf("image/*", "application/pdf"), true, "اختر صورًا أو ملف PDF", "استخراج النص", options = {
        Panel {
            Segmented(Ocr.Lang.entries.map { it.label }, lang) { lang = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SwitchRow("إنشاء PDF قابل للبحث أيضًا", searchable && com.muyassir.app.core.Plus.unlocked) { if (com.muyassir.app.core.Plus.allow(com.muyassir.app.core.Plus.Reason.Ocr)) searchable = it } }
                com.muyassir.app.ui.PlusBadge()
            }
            Hint(if (com.muyassir.app.core.Plus.unlocked) "يعمل دون إنترنت. للحصول على أفضل نتيجة استخدم صورة واضحة ومستقيمة."
                 else "يعمل دون إنترنت. المجاني يستخرج النص من ${com.muyassir.app.core.Plus.FREE_OCR_PAGES} صفحات في العملية، ومع بلس من الملف كاملًا.")
        }
    }, after = {
        if (limited > 0) Panel {
            Text("استُخرج النص من أول ${com.muyassir.app.core.Plus.FREE_OCR_PAGES} صفحات، وبقيت $limited ${if (limited == 1) "صفحة" else "صفحات"}.", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("ocrLimited"))
            SecondaryButton("استخرج الملف كاملًا مع بلس", Icons.Rounded.WorkspacePremium, Modifier.fillMaxWidth()) { com.muyassir.app.core.Plus.paywall = com.muyassir.app.core.Plus.Reason.Ocr }
        }
        if (text.isNotBlank()) TextResult(text, "النص المستخرج")
    }) { ins, out, p ->
        val sb = StringBuilder()
        val budget = com.muyassir.app.core.Plus.ocrPages
        val makePdf = searchable && com.muyassir.app.core.Plus.unlocked
        var done = 0; var skipped = 0
        val pages = ArrayList<Pair<android.graphics.Bitmap, List<Ocr.Line>>>()
        fun one(bmp: android.graphics.Bitmap) {
            if (makePdf) { val (s, lines) = Ocr.recognizeLines(ctx, bmp, Ocr.Lang.entries[lang]); sb.append(s).append("\n\n"); val small = if (maxOf(bmp.width, bmp.height) > 1700) ImageEngine.resize(bmp, 1700, 1700, true) else bmp
                val f = small.width / bmp.width.toFloat()
                pages += small to lines.map { l -> Ocr.Line(l.text, android.graphics.Rect((l.box.left * f).toInt(), (l.box.top * f).toInt(), (l.box.right * f).toInt(), (l.box.bottom * f).toInt())) }
                if (small !== bmp) bmp.recycle() }
            else { sb.append(Ocr.recognize(ctx, bmp, Ocr.Lang.entries[lang])).append("\n\n"); bmp.recycle() }
        }
        for ((i, u) in ins.withIndex()) {
            val name = Files.name(ctx, u)
            if (name.endsWith(".pdf", true) || ctx.contentResolver.getType(u) == "application/pdf") {
                val f = Files.toCache(ctx, u); val n = PdfEngine.pageCount(f)
                for (pg in 0 until n) {
                    if (done >= budget) { skipped++; continue }
                    p.label = "$name: صفحة ${pg + 1} من $n"; p.value = pg / n.toFloat()
                    one(PdfEngine.render(f, pg, 220)); done++
                }
            } else {
                if (done >= budget) { skipped++; continue }
                p.label = "صورة ${i + 1} من ${ins.size}"; p.value = i / ins.size.toFloat()
                one(ImageEngine.load(ctx, u, 3000)); done++
            }
        }
        limited = skipped
        text = sb.toString().trim()
        val txt = File(out, "نص-مستخرج.txt").apply { writeText(text) }
        val docx = File(out, "نص-مستخرج.docx").also { Office.writeDocx(it, text.split("\n\n").map { para -> Office.Para.Text(para) }) }
        val list = mutableListOf(txt, docx)
        if (makePdf && pages.isNotEmpty()) { p.label = "إنشاء PDF قابل للبحث…"; list += Ocr.searchablePdf(pages, File(out, "ملف-قابل-للبحث.pdf")); pages.forEach { it.first.recycle() } }
        list
    }
}

@Composable
fun ScanTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<File>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data) ?: return@rememberLauncherForActivityResult
        scope.launch {
            results = withContext(Dispatchers.IO) {
                val out = Files.outDir(ctx); val list = ArrayList<File>()
                res.pdf?.let { pdf -> File(out, "مستند-ممسوح.pdf").also { f -> ctx.contentResolver.openInputStream(pdf.uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }; list += f } }
                res.pages?.forEachIndexed { i, pg -> File(out, "صفحة-${i + 1}.jpg").also { f -> ctx.contentResolver.openInputStream(pg.imageUri)!!.use { s -> f.outputStream().use { s.copyTo(it) } }; list += f } }
                list
            }
            Store.used(t.id, results.firstOrNull()?.name ?: "")
        }
    }
    ToolScreen(t, back) {
        Panel {
            Text("صوّر المستند وسيُقص ويُحسَّن تلقائيًا، ويمكنك إضافة عدة صفحات.", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("بدء المسح", Icons.Rounded.DocumentScanner) {
                val opts = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(50)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF, GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
                GmsDocumentScanning.getClient(opts).getStartScanIntent(act)
                    .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                    .addOnFailureListener { error = "الماسح غير متاح على هذا الجهاز. تأكد من تحديث خدمات Google Play." }
            }
            Hint("الماسح من خدمات Google Play ويعمل على الجهاز دون رفع الصور.")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (results.isNotEmpty()) ResultFiles(results)
    }
}
