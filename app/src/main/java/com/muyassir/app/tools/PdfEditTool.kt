package com.muyassir.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.muyassir.app.R
import com.muyassir.app.core.Files
import com.muyassir.app.core.Plus
import com.muyassir.app.core.Store
import com.muyassir.app.core.TestHooks
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Renders text (Arabic shaped by Android) into a transparent bitmap sized for a box of [wPt]×[hPt] points. */
fun textBitmap(ctx: Context, text: String, sizePt: Float, color: Int, wPt: Float, hPt: Float = 0f, scale: Float = 3f): Bitmap {
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(ctx, R.font.plex_medium); textSize = sizePt * scale; this.color = color
    }
    val w = (wPt * scale).toInt().coerceAtLeast(10)
    val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, w).setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL).setLineSpacing(0f, 1.1f).setIncludePad(false).build()
    val h = maxOf(l.height, (hPt * scale).toInt()).coerceAtLeast(10)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b -> val c = ACanvas(b); c.translate(0f, ((h - l.height) / 2f).coerceAtLeast(0f)); l.draw(c) }
}

private data class Mark(val id: Long, val page: Int, var rx: Float, var ry: Float, var rw: Float, var rh: Float, val kind: Int, val text: String, val size: Float, val color: Int)

@Composable
fun PdfEditTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<File?>(null) }
    var pages by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var pageSize by remember { mutableStateOf(595f to 842f) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var kind by remember { mutableIntStateOf(0) } // 0 text, 1 highlight, 2 white-out
    var draft by remember { mutableStateOf("") }
    var fontPt by remember { mutableFloatStateOf(12f) }
    var color by remember { mutableIntStateOf(0) }
    val colors = listOf(0xFF141A2E.toInt(), 0xFF3346D3.toInt(), 0xFFC62F3A.toInt(), 0xFF23814A.toInt())
    val marks = remember { mutableStateListOf<Mark>() }
    var selected by remember { mutableStateOf<Long?>(null) }
    var fields by remember { mutableStateOf<List<PdfEngine.FormField>>(emptyList()) }
    val values = remember { mutableStateMapOf<String, String>() }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun load(u: Uri) = scope.launch {
        error = null; result = emptyList(); marks.clear(); values.clear()
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val f = Files.toCache(ctx, u); val n = PdfEngine.pageCount(f)
                val b = PdfEngine.render(f, 0, 72); val sz = b.width.toFloat() to b.height.toFloat(); b.recycle()
                Triple(f, n, sz) to runCatching { PdfEngine.formFields(f) }.getOrDefault(emptyList())
            }
        }
        r.onSuccess { (a, ff) -> pdf = a.first; pages = a.second; pageSize = a.third; page = 0; fields = ff; ff.forEach { values[it.name] = it.value }; if (ff.isNotEmpty()) tab = 1 }
            .onFailure { error = "تعذّر فتح الملف. إن كان محميًا بكلمة مرور فأزل الحماية أولًا." }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { load(it) } }
    LaunchedEffect(Unit) { (TestHooks.inputs ?: com.muyassir.app.core.Incoming.take())?.firstOrNull()?.let { load(it) } }
    LaunchedEffect(pdf, page) { pdf?.let { f -> preview = withContext(Dispatchers.IO) { PdfEngine.render(f, page, 110) } } }

    fun overlayBitmap(m: Mark, scale: Float): Bitmap {
        val (pw, ph) = pageSize
        return when (m.kind) {
            0 -> textBitmap(ctx, m.text, m.size, m.color, pw * m.rw, ph * m.rh, scale)
            else -> Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(if (m.kind == 1) 0x66FFE14D else android.graphics.Color.WHITE) }
        }
    }

    ToolScreen(t, back) {
        Panel {
            SecondaryButton(if (pdf == null) "اختر ملف PDF" else "تغيير الملف", Icons.Rounded.UploadFile, Modifier.fillMaxWidth()) { pick.launch(arrayOf("application/pdf")) }
            if (pdf != null) Segmented(listOf("كتابة وتظليل", "تعبئة النموذج (${fields.size})"), tab) { tab = it }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
        if (pdf != null && tab == 0) {
            Panel {
                Segmented(listOf("نص", "تظليل", "إخفاء بالأبيض"), kind) { kind = it }
                if (kind == 0) {
                    TextField2("النص الذي تريد إضافته", draft, { draft = it }, Modifier.testTag("editText"), lines = 2)
                    Text("الحجم ${fontPt.toInt()}", style = MaterialTheme.typography.labelMedium); Slider(fontPt, { fontPt = it }, valueRange = 8f..28f)
                    ChipRow { listOf("أسود", "أزرق", "أحمر", "أخضر").forEachIndexed { i, n -> Chip(n, i == color) { color = i } } }
                }
                Hint(if (kind == 0) "اكتب النص ثم المس مكانه على الصفحة." else "المس المكان على الصفحة، ثم عدّل الحجم من الأسفل.")
            }
            if (pages > 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(enabled = page > 0, onClick = { page-- }) { Icon(Icons.Rounded.ChevronRight, "الصفحة السابقة") }
                Text("الصفحة ${page + 1} من $pages")
                IconButton(enabled = page < pages - 1, onClick = { page++ }) { Icon(Icons.Rounded.ChevronLeft, "الصفحة التالية") }
            }
            preview?.let { pv ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxWidth().aspectRatio(pv.width.toFloat() / pv.height).testTag("pagePreview")
                        .pointerInput(kind, draft, fontPt, color, page) {
                            detectTapGestures { o ->
                                val rx = o.x / this.size.width; val ry = o.y / this.size.height
                                val (pw, ph) = pageSize
                                val m = when (kind) {
                                    0 -> if (draft.isBlank()) null else {
                                        val rw = 0.45f; val bmp = textBitmap(ctx, draft, fontPt, colors[color], pw * rw, 0f, 1f)
                                        Mark(System.nanoTime(), page, (rx - rw / 2).coerceIn(0f, 1f - rw), ry.coerceIn(0f, 0.97f), rw, bmp.height / ph, 0, draft, fontPt, colors[color]).also { bmp.recycle() }
                                    }
                                    else -> Mark(System.nanoTime(), page, (rx - 0.15f).coerceIn(0f, 0.7f), (ry - 0.012f).coerceIn(0f, 0.97f), 0.3f, 0.028f, kind, "", 0f, 0)
                                }
                                if (m != null) { marks += m; selected = m.id; if (kind == 0) draft = "" }
                            }
                        }) {
                        Image(pv.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.fillMaxSize()) {
                            marks.filter { it.page == page }.forEach { m ->
                                val tl = Offset(m.rx * size.width, m.ry * size.height); val sz = Size(m.rw * size.width, m.rh * size.height)
                                when (m.kind) {
                                    1 -> drawRect(Color(0x66FFE14D), tl, sz)
                                    2 -> drawRect(Color.White, tl, sz)
                                    else -> drawIntoCanvas(this, m, tl, sz, ctx, pageSize)
                                }
                                if (m.id == selected) drawRect(Color(0xFF3346D3), tl, sz, style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
            marks.firstOrNull { it.id == selected }?.let { m ->
                Panel("العنصر المحدد") {
                    Text("الموضع الأفقي"); Slider(m.rx, { v -> val i = marks.indexOf(m); marks[i] = m.copy(rx = v.coerceIn(0f, 1f - m.rw)) }, valueRange = 0f..1f)
                    Text("الموضع العمودي"); Slider(m.ry, { v -> val i = marks.indexOf(m); marks[i] = m.copy(ry = v.coerceIn(0f, 1f - m.rh)) }, valueRange = 0f..1f)
                    if (m.kind != 0) { Text("العرض"); Slider(m.rw, { v -> val i = marks.indexOf(m); marks[i] = m.copy(rw = v) }, valueRange = 0.05f..1f); Text("الارتفاع"); Slider(m.rh, { v -> val i = marks.indexOf(m); marks[i] = m.copy(rh = v) }, valueRange = 0.01f..0.3f) }
                    SecondaryButton("حذف العنصر", Icons.Rounded.Delete, Modifier.fillMaxWidth()) { marks.remove(m); selected = null }
                }
            }
            if (marks.isNotEmpty()) Text("${marks.size} إضافة على ${marks.map { it.page }.distinct().size} صفحة", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
            PrimaryButton(if (busy) "جارٍ الحفظ…" else "حفظ الملف", Icons.Rounded.Save, enabled = marks.isNotEmpty() && !busy, modifier = Modifier.testTag("action")) {
                Plus.check(t.id, listOf(pdf!!.length()))?.let { Plus.paywall = it; return@PrimaryButton }
                scope.launch {
                    busy = true
                    val r = withContext(Dispatchers.IO) {
                        runCatching { listOf(PdfEngine.annotate(pdf!!, marks.map { m -> PdfEngine.Overlay(m.page, m.rx, m.ry, m.rw, m.rh, overlayBitmap(m, 3f)) }, File(Files.outDir(ctx), "مستند-معدّل.pdf"))) }
                    }
                    busy = false
                    r.onSuccess { result = it; Store.used(t.id); Plus.counted(t.id); maybeAskForReview(ctx) }.onFailure { error = "تعذّر حفظ التعديلات على هذا الملف." }
                }
            }
        }
        if (pdf != null && tab == 1) {
            if (fields.isEmpty()) Panel { Text("هذا الملف ليس نموذجًا قابلًا للتعبئة. استخدم «كتابة وتظليل» لإضافة النص في أي مكان.", style = MaterialTheme.typography.bodyMedium) }
            else {
                Panel("حقول النموذج") { fields.forEach { f -> TextField2(f.label, values[f.name] ?: "", { values[f.name] = it }) } }
                PrimaryButton(if (busy) "جارٍ الحفظ…" else "تعبئة وحفظ", Icons.Rounded.EditNote, enabled = !busy, modifier = Modifier.testTag("fillForm")) {
                    Plus.check(t.id, listOf(pdf!!.length()))?.let { Plus.paywall = it; return@PrimaryButton }
                    scope.launch {
                        busy = true
                        val r = withContext(Dispatchers.IO) {
                            runCatching { listOf(PdfEngine.fillForm(pdf!!, values.toMap(), File(Files.outDir(ctx), "نموذج-معبأ.pdf")) { v, w, h -> textBitmap(ctx, v, (h * 0.6f).coerceIn(7f, 14f), 0xFF141A2E.toInt(), w, h) }) }
                        }
                        busy = false
                        r.onSuccess { result = it; Store.used(t.id); Plus.counted(t.id) }.onFailure { error = "تعذّرت تعبئة هذا النموذج." }
                    }
                }
                Hint("النص الإنجليزي يُكتب داخل الحقل نفسه، والعربي يُرسم مكان الحقل ليظهر صحيحًا في كل البرامج.")
            }
        }
        if (result.isNotEmpty()) ResultFiles(result)
        if (pdf == null) Hint("أضف نصًا عربيًا أو إنجليزيًا في أي مكان، وظلّل، وأخفِ ما لا تريد ظهوره، أو عبّئ النماذج الرسمية. كل ذلك على جهازك.")
    }
}

/** Draws a text mark in the on-screen preview. */
private fun drawIntoCanvas(scope: androidx.compose.ui.graphics.drawscope.DrawScope, m: Mark, tl: Offset, sz: Size, ctx: Context, page: Pair<Float, Float>) {
    val k = sz.width / (page.first * m.rw)
    val b = textBitmap(ctx, m.text, m.size, m.color, page.first * m.rw, page.second * m.rh, k.coerceAtLeast(0.5f))
    scope.drawImage(b.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(tl.x.toInt(), tl.y.toInt()), dstSize = androidx.compose.ui.unit.IntSize(sz.width.toInt().coerceAtLeast(1), sz.height.toInt().coerceAtLeast(1)))
}
