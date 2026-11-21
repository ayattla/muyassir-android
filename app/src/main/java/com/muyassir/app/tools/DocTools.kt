package com.muyassir.app.tools

import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint
import android.graphics.Path as APath
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.Calc
import com.muyassir.app.engine.DocBuilder
import com.muyassir.app.core.Templates
import com.muyassir.app.ui.TemplatePanel
import com.muyassir.app.engine.DocBuilder.Block
import com.muyassir.app.engine.ImageEngine
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Date

/** Remembers the last values of a form so the user does not retype their company or personal details. */
@Composable
private fun rememberForm(name: String): JSONObject = remember { Store.obj("form_$name") }

@Composable
private fun F(form: JSONObject, key: String, label: String, lines: Int = 1, tick: MutableIntState) {
    TextField2(label, form.optString(key), { form.put(key, it); tick.intValue++ }, lines = lines)
}

@Composable
fun CvTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val f = rememberForm("cv"); val tick = remember { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var cvLang by remember { mutableIntStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { photo = runCatching { ImageEngine.cropRatio(ImageEngine.load(ctx, it, 800), 1f, 1f) }.getOrNull() } }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        key(tick.intValue) {
            Panel("البيانات الأساسية") {
                F(f, "name", "الاسم الكامل", tick = tick); F(f, "title", "المسمى أو التخصص", tick = tick)
                F(f, "phone", "الجوال", tick = tick); F(f, "email", "البريد الإلكتروني", tick = tick); F(f, "city", "المدينة", tick = tick)
                SecondaryButton(if (photo == null) "إضافة صورة (اختياري)" else "تغيير الصورة", Icons.Rounded.AddAPhoto) { pick.launch("image/*") }
            }
            Panel("المحتوى") {
                F(f, "summary", "نبذة مختصرة", 3, tick)
                F(f, "exp", "الخبرات (سطر لكل خبرة: المسمى — الجهة — المدة)", 5, tick)
                F(f, "edu", "التعليم (سطر لكل مؤهل)", 3, tick)
                F(f, "skills", "المهارات (مفصولة بفاصلة)", 2, tick)
                F(f, "langs", "اللغات", tick = tick); F(f, "courses", "الدورات والشهادات", 3, tick)
            }
        }
        Panel { Segmented(listOf("سيرة عربية", "English CV"), cvLang) { cvLang = it }; if (cvLang == 1) Hint("اكتب البيانات بالإنجليزية؛ تُكتب العناوين بالإنجليزية ويُحاذى النص لليسار.") }
        TemplatePanel(useLogo = false)
        PrimaryButton("إنشاء السيرة الذاتية", Icons.Rounded.PictureAsPdf, enabled = f.optString("name").isNotBlank()) {
            Store.saveObj("form_cv", f)
            val en = cvLang == 1
            val h = if (en) mapOf("نبذة" to "Summary", "الخبرات العملية" to "Experience", "التعليم" to "Education", "المهارات" to "Skills", "اللغات" to "Languages", "الدورات والشهادات" to "Courses & certificates") else emptyMap()
            val b = Templates.apply(ctx, DocBuilder(ctx), useLogo = false).apply { ltr = en }
            photo?.let { b.add(Block.Image(it, 110f, center = false)) }
            b.add(Block.Title(f.optString("name"), f.optString("title")))
            b.add(Block.Para(listOf(f.optString("phone"), f.optString("email"), f.optString("city")).filter { it.isNotBlank() }.joinToString("   |   "), 10f, color = DocBuilder.MUTED))
            fun sec(title: String, k: String, bullets: Boolean = true) { val v = f.optString(k); if (v.isNotBlank()) { b.add(Block.Heading(h[title] ?: title)); v.lines().filter { it.isNotBlank() }.forEach { b.add(Block.Para(if (bullets) "• $it" else it)) } } }
            sec("نبذة", "summary", false); sec("الخبرات العملية", "exp"); sec("التعليم", "edu")
            f.optString("skills").takeIf { it.isNotBlank() }?.let { b.add(Block.Heading(h["المهارات"] ?: "المهارات")); b.add(Block.Para(it.split(',', '،').joinToString("  •  ") { s -> s.trim() })) }
            sec("اللغات", "langs"); sec("الدورات والشهادات", "courses")
            result = listOf(b.save(File(Files.outDir(ctx), (if (en) "CV-" else "السيرة-الذاتية-") + "${f.optString("name")}.pdf"))); Store.used(t.id)
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

/** Invoice and quotation share one form: seller, buyer, line items, VAT. */
@Composable
private fun BillingDoc(t: Tool, back: () -> Unit, quote: Boolean, open: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val f = rememberForm(if (quote) "quote" else "invoice"); val tick = remember { mutableIntStateOf(0) }
    val items = remember { mutableStateListOf(Triple("", "1", "")) }
    // A quote turned into an invoice arrives with its items.
    LaunchedEffect(Unit) { if (!quote) BillingDraft.take()?.let { (form, list) -> form.keys().forEach { k -> f.put(k, form.get(k)) }; items.clear(); items.addAll(list); tick.intValue++ } }
    val clients = remember { mutableStateListOf<JSONObject>().apply { addAll(Store.list("clients")) } }
    var withVat by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        key(tick.intValue) {
            Panel("البائع") { F(f, "seller", "اسم المنشأة", tick = tick); F(f, "vatNo", "الرقم الضريبي", tick = tick); F(f, "sellerInfo", "العنوان والتواصل", 2, tick) }
            Panel("العميل") {
                if (clients.isNotEmpty()) {
                    Text("عملاء سابقون", style = MaterialTheme.typography.labelLarge)
                    ChipRow { clients.take(12).forEach { c -> Chip(c.optString("buyer")) { f.put("buyer", c.optString("buyer")); f.put("buyerInfo", c.optString("buyerInfo")); tick.intValue++ } } }
                }
                F(f, "buyer", "اسم العميل", tick = tick); F(f, "buyerInfo", "بيانات العميل (اختياري)", 2, tick)
            }
            Panel { F(f, "no", if (quote) "رقم العرض (فارغ = ترقيم تلقائي)" else "رقم الفاتورة (فارغ = ترقيم تلقائي)", tick = tick); F(f, "notes", if (quote) "الشروط ومدة صلاحية العرض" else "ملاحظات وطريقة الدفع", 3, tick) }
        }
        Panel("البنود") {
            items.forEachIndexed { i, (d, q, p) ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField2("الوصف", d, { items[i] = Triple(it, q, p) }, Modifier.weight(1.6f))
                    NumField("الكمية", q, { items[i] = Triple(d, it, p) }, Modifier.weight(0.7f))
                    NumField("السعر", p, { items[i] = Triple(d, q, it) }, Modifier.weight(0.9f))
                    IconButton(onClick = { if (items.size > 1) items.removeAt(i) }) { Icon(Icons.Rounded.Close, "حذف") }
                }
            }
            SecondaryButton("إضافة بند", Icons.Rounded.Add) { items.add(Triple("", "1", "")) }
            SwitchRow("إضافة ضريبة القيمة المضافة 15%", withVat) { withVat = it }
        }
        val sub = items.sumOf { it.second.num() * it.third.num() }
        val v = if (withVat) Calc.vat(sub, inclusive = false) else Calc.Vat(sub, 0.0, sub)
        ResultCard("الإجمالي" to money(v.total), listOf("المجموع قبل الضريبة" to money(v.net), "الضريبة" to money(v.vat)))
        TemplatePanel()
        PrimaryButton(if (quote) "إنشاء عرض السعر" else "إنشاء الفاتورة", Icons.Rounded.PictureAsPdf, enabled = sub > 0) {
            // automatic numbering and the saved clients list
            val counter = Store.obj("billing_counter"); val key = if (quote) "q" else "i"
            val number = f.optString("no").ifBlank { (if (quote) "Q-" else "INV-") + "%04d".format(counter.optInt(key, 0) + 1) }
            if (f.optString("no").isBlank()) Store.saveObj("billing_counter", counter.put(key, counter.optInt(key, 0) + 1))
            if (f.optString("buyer").isNotBlank()) {
                clients.removeAll { it.optString("buyer") == f.optString("buyer") }
                clients.add(0, JSONObject().put("buyer", f.optString("buyer")).put("buyerInfo", f.optString("buyerInfo")))
                while (clients.size > 30) clients.removeAt(clients.lastIndex)
                Store.saveList("clients", clients.toList())
            }
            Store.saveObj(if (quote) "form_quote" else "form_invoice", JSONObject(f.toString()).put("no", ""))
            val b = Templates.apply(ctx, DocBuilder(ctx))
            b.add(Block.Title(if (quote) "عرض سعر" else if (withVat) "فاتورة ضريبية مبسطة" else "فاتورة", "${f.optString("seller")}${f.optString("vatNo").takeIf { it.isNotBlank() }?.let { "  •  الرقم الضريبي: $it" } ?: ""}"))
            b.add(Block.KeyValues(listOfNotNull(
                (if (quote) "رقم العرض" else "رقم الفاتورة") to number,
                "التاريخ" to dayFmt.format(Date()), "العميل" to f.optString("buyer"),
                f.optString("buyerInfo").takeIf { it.isNotBlank() }?.let { "بيانات العميل" to it },
                f.optString("sellerInfo").takeIf { it.isNotBlank() }?.let { "بيانات البائع" to it },
            )))
            b.add(Block.Table(listOf("#", "الوصف", "الكمية", "السعر", "المجموع"),
                items.filter { it.first.isNotBlank() || it.third.num() > 0 }.mapIndexed { i, (d, q, p) -> listOf("${i + 1}", d, fmt(q.num()), fmt(p.num()), fmt(q.num() * p.num())) },
                listOf(0.4f, 3f, 0.8f, 1f, 1.2f)))
            b.add(Block.KeyValues(listOf("المجموع قبل الضريبة" to money(v.net)) + (if (withVat) listOf("ضريبة القيمة المضافة 15%" to money(v.vat)) else emptyList()) + listOf("الإجمالي المستحق" to money(v.total))))
            f.optString("notes").takeIf { it.isNotBlank() }?.let { b.add(Block.Heading(if (quote) "الشروط" else "ملاحظات")); b.add(Block.Para(it)) }
            if (!quote && withVat) b.add(Block.Para("تنبيه: هذا مستند مبسط. الفوترة الإلكترونية الإلزامية (فاتورة) تتطلب نظامًا معتمدًا من هيئة الزكاة والضريبة والجمارك.", 8.5f, color = DocBuilder.MUTED))
            result = listOf(b.save(File(Files.outDir(ctx), "${if (quote) "عرض-سعر" else "فاتورة"}-${f.optString("buyer").ifBlank { "عميل" }}.pdf"))); Store.used(t.id, f.optString("buyer"))
        }
        if (result.isNotEmpty()) {
            ResultFiles(result)
            if (quote) SecondaryButton("تحويل العرض إلى فاتورة", Icons.Rounded.Receipt, Modifier.fillMaxWidth().testTag("quoteToInvoice")) {
                BillingDraft.put(JSONObject().put("seller", f.optString("seller")).put("vatNo", f.optString("vatNo")).put("sellerInfo", f.optString("sellerInfo"))
                    .put("buyer", f.optString("buyer")).put("buyerInfo", f.optString("buyerInfo")), items.toList())
                open("invoice")
            }
        }
    }
}

/** Hands a quote's details to the invoice screen. */
object BillingDraft {
    private var pending: Pair<JSONObject, List<Triple<String, String, String>>>? = null
    fun put(form: JSONObject, items: List<Triple<String, String, String>>) { pending = form to items }
    fun take() = pending.also { pending = null }
}

@Composable fun InvoiceTool(t: Tool, back: () -> Unit) = BillingDoc(t, back, false)
@Composable fun QuoteTool(t: Tool, back: () -> Unit, open: (String) -> Unit = {}) = BillingDoc(t, back, true, open)

@Composable
fun LetterTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val f = rememberForm("letter"); val tick = remember { mutableIntStateOf(0) }
    var kind by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        Panel { Segmented(listOf("خطاب رسمي", "تقرير"), kind) { kind = it } }
        key(tick.intValue) {
            Panel {
                F(f, "from", "الجهة أو المرسل", tick = tick)
                if (kind == 0) { F(f, "to", "المرسل إليه (سعادة/ مدير …)", tick = tick); F(f, "subject", "الموضوع", tick = tick) }
                else { F(f, "rtitle", "عنوان التقرير", tick = tick); F(f, "author", "إعداد", tick = tick) }
                F(f, "body", if (kind == 0) "نص الخطاب" else "محتوى التقرير (العناوين تبدأ بـ #)", 10, tick)
                F(f, "sign", "الاسم والصفة للتوقيع", tick = tick)
            }
        }
        TemplatePanel()
        PrimaryButton("إنشاء PDF", Icons.Rounded.PictureAsPdf) {
            Store.saveObj("form_letter", f)
            val b = Templates.apply(ctx, DocBuilder(ctx))
            if (kind == 0) {
                b.add(Block.Para(f.optString("from"), 13f, bold = true))
                b.add(Block.Para("التاريخ: ${dayFmt.format(Date())}", 10f, color = DocBuilder.MUTED)); b.add(Block.Rule)
                b.add(Block.Para(f.optString("to"), 12f, bold = true)); b.add(Block.Para("السلام عليكم ورحمة الله وبركاته،"))
                if (f.optString("subject").isNotBlank()) b.add(Block.Para("الموضوع: ${f.optString("subject")}", 12f, bold = true))
                f.optString("body").split("\n").forEach { b.add(Block.Para(it, 12f)) }
                b.add(Block.Space(20f)); b.add(Block.Para("وتفضلوا بقبول فائق الاحترام والتقدير،")); b.add(Block.Space(16f))
                b.add(Block.Para(f.optString("sign"), 12f, bold = true)); b.add(Block.Para("التوقيع: ..............................."))
            } else {
                b.add(Block.Title(f.optString("rtitle").ifBlank { "تقرير" }, listOf(f.optString("from"), f.optString("author").takeIf { it.isNotBlank() }?.let { "إعداد: $it" }, dayFmt.format(Date())).filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")))
                f.optString("body").lines().forEach { l -> if (l.startsWith("#")) b.add(Block.Heading(l.trimStart('#').trim())) else if (l.isNotBlank()) b.add(Block.Para(l)) }
                if (f.optString("sign").isNotBlank()) { b.add(Block.Space(16f)); b.add(Block.Para(f.optString("sign"), bold = true)) }
            }
            result = listOf(b.save(File(Files.outDir(ctx), "${if (kind == 0) "خطاب" else "تقرير"}.pdf"))); Store.used(t.id)
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

@Composable
fun TextPdfTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var size by remember { mutableIntStateOf(1) }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        Panel {
            TextField2("العنوان (اختياري)", title, { title = it }); TextField2("النص", body, { body = it }, lines = 12)
            Segmented(listOf("صغير", "متوسط", "كبير"), size) { size = it }
        }
        TemplatePanel()
        PrimaryButton("إنشاء PDF", Icons.Rounded.PictureAsPdf, enabled = body.isNotBlank()) {
            val b = Templates.apply(ctx, DocBuilder(ctx))
            if (title.isNotBlank()) b.add(Block.Title(title))
            body.split("\n").forEach { b.add(Block.Para(it, listOf(10f, 12f, 15f)[size])) }
            result = listOf(b.save(File(Files.outDir(ctx), "${title.ifBlank { "مستند" }}.pdf"))); Store.used(t.id, title)
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

@Composable
fun SignPdfTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var pdf by remember { mutableStateOf<File?>(null) }
    var pages by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var x by remember { mutableFloatStateOf(0.6f) }; var y by remember { mutableFloatStateOf(0.8f) }; var w by remember { mutableFloatStateOf(0.28f) }
    var padW by remember { mutableIntStateOf(1) }; var padH by remember { mutableIntStateOf(1) }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    // Plus: a saved signature and stamps (any image, e.g. a company stamp)
    val sigFile = remember { File(ctx.filesDir, "data/signature.png") }
    var source by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf<Bitmap?>(null) }
    var stampImg by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) { saved = withContext(Dispatchers.IO) { if (sigFile.exists()) android.graphics.BitmapFactory.decodeFile(sigFile.absolutePath) else null } }
    val pickStamp = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { stampImg = runCatching { ImageEngine.load(ctx, it, 900) }.getOrNull() } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        u?.let { scope.launch { withContext(Dispatchers.IO) { val f = Files.toCache(ctx, it); pdf = f; pages = PdfEngine.pageCount(f); page = pages; preview = PdfEngine.render(f, page - 1, 60) } } }
    }
    LaunchedEffect(page, pdf) { pdf?.let { f -> preview = withContext(Dispatchers.IO) { PdfEngine.render(f, page - 1, 60) } } }

    fun signatureBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(padW, padH, Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B1F6B.toInt(); strokeWidth = 7f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        strokes.forEach { s -> if (s.size > 1) { val path = APath(); path.moveTo(s[0].x, s[0].y); s.drop(1).forEach { path.lineTo(it.x, it.y) }; c.drawPath(path, p) } }
        return bmp
    }

    fun chosen(): Bitmap? = when (source) { 1 -> saved; 2 -> stampImg; else -> if (strokes.isEmpty()) null else signatureBitmap() }
    val ratio = when (source) { 1 -> saved?.let { it.height / it.width.toFloat() }; 2 -> stampImg?.let { it.height / it.width.toFloat() }; else -> null } ?: (padH / padW.coerceAtLeast(1).toFloat())

    ToolScreen(t, back) {
        Panel {
            Segmented(listOf("ارسم توقيعًا", "توقيعي المحفوظ", "ختم أو صورة"), source) { if (it == 0 || com.muyassir.app.core.Plus.allow(com.muyassir.app.core.Plus.Reason.Sign)) source = it }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) { PlusBadge() }
        }
        if (source == 1) Panel("توقيعي المحفوظ") {
            saved?.let { Image(it.asImageBitmap(), "التوقيع المحفوظ", Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)) }
                ?: Hint("لا يوجد توقيع محفوظ بعد. ارسم توقيعك في «ارسم توقيعًا» ثم اضغط «حفظ هذا التوقيع».")
        }
        if (source == 2) Panel("الختم") {
            stampImg?.let { Image(it.asImageBitmap(), "الختم", Modifier.fillMaxWidth().height(140.dp)) }
            SecondaryButton(if (stampImg == null) "اختر صورة الختم (PNG شفاف أفضل)" else "تغيير الصورة", Icons.Rounded.Approval, Modifier.fillMaxWidth()) { pickStamp.launch("image/*") }
        }
        if (source == 0) Panel("ارسم توقيعك") {
            Box(
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        padW = size.width; padH = size.height
                        detectDragGestures(onDragStart = { current = listOf(it) }, onDragEnd = { strokes.add(current); current = emptyList() }) { ch, _ -> current = current + ch.position }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    (strokes + listOf(current)).forEach { s -> for (i in 1 until s.size) drawLine(Color(0xFF0B1F6B), s[i - 1], s[i], 7f, cap = StrokeCap.Round) }
                }
                if (strokes.isEmpty() && current.isEmpty()) Text("وقّع هنا بإصبعك", Modifier.align(Alignment.Center), color = Color.Gray)
            }
            Row {
                TextButton(onClick = { strokes.clear() }) { Text("مسح التوقيع") }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = strokes.isNotEmpty(), onClick = {
                    if (com.muyassir.app.core.Plus.allow(com.muyassir.app.core.Plus.Reason.Sign)) {
                        val b = signatureBitmap(); sigFile.parentFile?.mkdirs(); sigFile.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        saved = b; toast(ctx, "حُفظ توقيعك على جهازك")
                    }
                }) { Text("حفظ هذا التوقيع") }
            }
        }
        Panel("المستند") {
            SecondaryButton(if (pdf == null) "اختر ملف PDF" else "تغيير الملف", Icons.Rounded.UploadFile, Modifier.fillMaxWidth()) { pick.launch(arrayOf("application/pdf")) }
            if (pdf != null) {
                Text("الصفحة $page من $pages"); if (pages > 1) Slider(page.toFloat(), { page = it.toInt() }, valueRange = 1f..pages.toFloat(), steps = (pages - 2).coerceAtLeast(0))
                preview?.let { pv ->
                    Box(Modifier.fillMaxWidth().aspectRatio(pv.width.toFloat() / pv.height)) {
                        Image(pv.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.fillMaxSize()) { drawRect(Color(0x552F4BFF), Offset(x * size.width, y * size.height), androidx.compose.ui.geometry.Size(w * size.width, w * size.width * ratio)) }
                    }
                }
                Text("الموضع الأفقي"); Slider(x, { x = it }, valueRange = 0f..(1f - w))
                Text("الموضع العمودي"); Slider(y, { y = it }, valueRange = 0f..0.95f)
                Text("الحجم"); Slider(w, { w = it; x = x.coerceAtMost(1f - it) }, valueRange = 0.1f..0.6f)
            }
        }
        val img = if (source == 0 && strokes.isEmpty()) null else if (source == 0) Unit else chosen()
        PrimaryButton("توقيع المستند", Icons.Rounded.Draw, enabled = pdf != null && img != null) {
            val bmp = chosen() ?: return@PrimaryButton
            scope.launch {
                result = withContext(Dispatchers.IO) { listOf(PdfEngine.stamp(pdf!!, page - 1, bmp, x, y, w, File(Files.outDir(ctx), "مستند-موقع.pdf"))) }
                Store.used(t.id)
            }
        }
        Hint("التوقيع المرسوم مناسب للمستندات الداخلية. التوقيع الإلكتروني الموثّق نظاميًا يتم عبر منصات معتمدة مثل النفاذ الوطني.")
        if (result.isNotEmpty()) ResultFiles(result)
    }
}
