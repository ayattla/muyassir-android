@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.muyassir.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

class Progress { var value by mutableFloatStateOf(-1f); var label by mutableStateOf("") }

/**
 * Generic "pick files → set options → run → results" screen used by the conversion, PDF, image and ZIP tools.
 * Files never leave the device: they are processed in the app's private storage.
 */
@Composable
fun FileTool(
    tool: Tool,
    onBack: () -> Unit,
    mimes: Array<String>,
    multiple: Boolean,
    pickLabel: String,
    actionLabel: String,
    reorderable: Boolean = false,
    options: @Composable ColumnScope.(inputs: List<Uri>) -> Unit = {},
    validate: (List<Uri>) -> String? = { if (it.isEmpty()) "اختر ملفًا أولًا" else null },
    after: @Composable ColumnScope.() -> Unit = {},
    run: suspend (inputs: List<Uri>, out: File, progress: Progress) -> List<File>,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputs = remember { mutableStateListOf<Uri>() }
    var results by remember { mutableStateOf<List<File>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress = remember { Progress() }
    LaunchedEffect(Unit) {
        (com.muyassir.app.core.TestHooks.inputs ?: com.muyassir.app.core.Incoming.take())?.let { if (inputs.isEmpty()) inputs.addAll(if (multiple) it else it.take(1)) }
    }

    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) { inputs.addAll(it.filter { u -> u !in inputs }); results = emptyList() } }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) { inputs.clear(); inputs.add(it); results = emptyList() } }

    // Tablets and split screen: files can also be dragged in from another app.
    val drop = remember {
        object : androidx.compose.ui.draganddrop.DragAndDropTarget {
            override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
                val e = event.toAndroidDragEvent()
                ctx.activity()?.requestDragAndDropPermissions(e)
                val clip = e.clipData ?: return false
                val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                if (uris.isEmpty()) return false
                if (multiple) inputs.addAll(uris.filter { it !in inputs }) else { inputs.clear(); inputs.add(uris.first()) }
                results = emptyList(); return true
            }
        }
    }
    ToolScreen(tool, onBack) {
        Panel {
            val accent = tokens.accent
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(tokens.accentSoft.copy(alpha = if (tokens.dark) 0.6f else 0.55f))
                    .drawBehind {
                        drawRoundRect(accent.copy(alpha = 0.45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                    }
                    .pressable { if (multiple) pickMany.launch(mimes) else pickOne.launch(mimes) }
                    .dragAndDropTarget(shouldStartDragAndDrop = { ev -> ev.mimeTypes().isNotEmpty() }, target = drop)
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tokens.surface), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.UploadFile, null, tint = accent, modifier = Modifier.size(28.dp))
                    }
                    Text(if (inputs.isEmpty()) pickLabel else if (multiple) "إضافة ملفات أخرى" else "تغيير الملف", style = MaterialTheme.typography.titleSmall, color = accent)
                    Text(if (multiple) "يمكنك اختيار عدة ملفات دفعة واحدة" else "اختر ملفًا من جهازك أو من خدمات التخزين", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = tokens.textTertiary, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp))
                        Text("تتم المعالجة على جهازك ولا تُرفع ملفاتك", style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary)
                    }
                }
            }
            if (com.muyassir.app.core.Plus.isMetered(tool.id) && !com.muyassir.app.core.Plus.unlocked) {
                val left = com.muyassir.app.core.Plus.opsLeft
                Row(Modifier.fillMaxWidth().testTag("opsLeft"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (left > 0) "متبقٍ لك اليوم $left من ${com.muyassir.app.core.Plus.FREE_OPS_PER_DAY} عمليات مجانية" else "انتهت عملياتك المجانية لليوم", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { com.muyassir.app.core.Plus.paywall = com.muyassir.app.core.Plus.Reason.Menu }) { Text("بلا حدود مع بلس") }
                }
            }
            if (inputs.isNotEmpty()) Text("${inputs.size} ${if (inputs.size == 1) "ملف" else "ملفات"} جاهزة", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
            inputs.forEachIndexed { i, u ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(tokens.surfaceSunken).padding(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(u)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(Files.name(ctx, u), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, maxLines = 1)
                        Text(Files.human(Files.size(ctx, u)), style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
                    }
                    if (reorderable && inputs.size > 1) {
                        IconButton(enabled = i > 0, onClick = { inputs.add(i - 1, inputs.removeAt(i)) }) { Icon(Icons.Rounded.KeyboardArrowUp, "أعلى") }
                        IconButton(enabled = i < inputs.lastIndex, onClick = { inputs.add(i + 1, inputs.removeAt(i)) }) { Icon(Icons.Rounded.KeyboardArrowDown, "أسفل") }
                    }
                    IconButton(onClick = { inputs.removeAt(i); results = emptyList() }) { Icon(Icons.Rounded.Close, "إزالة", tint = tokens.textSecondary) }
                }
            }
        }

        if (inputs.isNotEmpty()) options(inputs)

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("error")) }

        if (busy) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = tokens.accent); Spacer(Modifier.width(Space.s))
                    Text(progress.label.ifBlank { "جارٍ المعالجة على جهازك…" }, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                }
                if (progress.value >= 0) {
                    val v by androidx.compose.animation.core.animateFloatAsState(progress.value, label = "p")
                    LinearProgressIndicator(progress = { v }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), trackColor = tokens.surfaceSunken)
                    Text("${(progress.value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = tokens.textTertiary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), trackColor = tokens.surfaceSunken)
            }
        } else PrimaryButton(actionLabel, Icons.Rounded.Bolt, enabled = inputs.isNotEmpty(), modifier = Modifier.testTag("action")) {
            error = validate(inputs)
            com.muyassir.app.core.Plus.check(tool.id, inputs.map { Files.size(ctx, it) })?.let { com.muyassir.app.core.Plus.paywall = it; return@PrimaryButton }
            if (error == null) scope.launch {
                busy = true; progress.value = -1f; progress.label = ""
                val started = System.currentTimeMillis()
                try {
                    val out = Files.outDir(ctx)
                    results = withContext(Dispatchers.IO) { run(inputs.toList(), out, progress) }
                    Store.used(tool.id, results.firstOrNull()?.name ?: "")
                    com.muyassir.app.core.Plus.counted(tool.id)
                    maybeAskForReview(ctx)
                    if (System.currentTimeMillis() - started > 6000) notifyDone(ctx, tool.title, results.size)
                } catch (t: Throwable) {
                    android.util.Log.e("Muyassir", "tool ${tool.id} failed", t)
                    error = friendly(t)
                } finally {
                    busy = false
                    withContext(Dispatchers.IO) { java.io.File(ctx.cacheDir, "in").deleteRecursively() }
                }
            }
        }

        if (results.isNotEmpty()) {
            val single = inputs.singleOrNull()
            if (single != null && results.size == 1 && results[0].extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") && Files.name(ctx, single).substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic"))
                BeforeAfter(single, results[0])
            ResultFiles(results)
        }
        after()
    }
}

fun friendly(t: Throwable): String {
    val m = t.message ?: ""
    return when {
        t is OutOfMemoryError -> "الملف كبير جدًا على ذاكرة الجهاز. جرّب دقة أقل أو عددًا أقل من الصفحات."
        m.contains("password", true) || t.javaClass.simpleName.contains("InvalidPassword") -> "الملف محمي بكلمة مرور. استخدم أداة «إزالة حماية PDF» أولًا."
        t is SecurityException -> "لا يمكن فتح هذا الملف. اختره مرة أخرى."
        m.isNotBlank() && m.any { it in '؀'..'ۿ' } -> m
        t.javaClass.name.contains("MlKit") -> "تعذّر تشغيل نموذج Google على هذا الجهاز. تأكد من تحديث خدمات Google Play والاتصال بالإنترنت لأول استخدام."
        else -> "تعذّرت المعالجة: تأكد أن الملف سليم ومن النوع الصحيح."
    }
}

@Composable
fun ResultFiles(files: List<File>) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { u ->
        val f = pending; if (u != null && f != null) { Files.writeTo(ctx, f, u); toast(ctx, "تم الحفظ") }
    }
    Panel("الناتج (${files.size})", Modifier.testTag("results")) {
        files.take(60).forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Brand.Mint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).clickable { Files.open(ctx, f) }) {
                    Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(Files.human(f.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Files.open(ctx, f) }) { Icon(Icons.Rounded.OpenInNew, "فتح") }
                IconButton(onClick = { pending = f; saveAs.launch(f.name) }) { Icon(Icons.Rounded.Save, "حفظ باسم") }
            }
        }
        if (files.size > 60) Text("و${files.size - 60} ملفًا آخر", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("مشاركة", Icons.Rounded.Share, Modifier.weight(1f)) { Files.share(ctx, files) }
            SecondaryButton("حفظ في التنزيلات", Icons.Rounded.Download, Modifier.weight(1f)) {
                val ok = files.all { Files.saveToDownloads(ctx, it) }
                if (ok) toast(ctx, "حُفظ في مجلد التنزيلات/Muyassir") else { pending = files.first(); saveAs.launch(files.first().name) }
            }
        }
        if (files.size == 1 && files[0].extension == "pdf") SecondaryButton("طباعة", Icons.Rounded.Print, Modifier.fillMaxWidth()) { Files.printPdf(ctx, files[0]) }
    }
}


/** Small preview of a picked file: the image itself, or the first page of a PDF, otherwise a file icon. */
@Composable
fun Thumb(u: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, u) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val n = Files.name(ctx, u).lowercase()
                when {
                    n.endsWith(".pdf") -> ctx.contentResolver.openFileDescriptor(u, "r")?.use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { r -> r.openPage(0).use { p ->
                            val b = android.graphics.Bitmap.createBitmap(96, (96f * p.height / p.width).toInt().coerceIn(40, 160), android.graphics.Bitmap.Config.ARGB_8888)
                            b.eraseColor(android.graphics.Color.WHITE); p.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); b } } }
                    n.substringAfterLast('.') in setOf("jpg", "jpeg", "png", "webp", "heic", "bmp", "gif") -> ctx.contentResolver.openInputStream(u)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) }
                    else -> null
                }
            }.getOrNull()
        }
    }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(tokens.surface), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Icon(Icons.Rounded.Description, null, tint = tokens.textTertiary, modifier = Modifier.size(22.dp))
    }
}

/** Before/after comparison: drag the handle to reveal the result over the original. */
@Composable
fun BeforeAfter(before: Uri, after: File) {
    val ctx = LocalContext.current
    val pair by produceState<Pair<androidx.compose.ui.graphics.ImageBitmap, androidx.compose.ui.graphics.ImageBitmap>?>(null, before, after) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                val a = ctx.contentResolver.openInputStream(before)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }!!
                val b = android.graphics.BitmapFactory.decodeFile(after.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })!!
                a.asImageBitmap() to b.asImageBitmap()
            }.getOrNull()
        }
    }
    val p = pair ?: return
    var split by remember { mutableFloatStateOf(0.5f) }
    Panel("قبل وبعد") {
        val ratio = p.first.width.toFloat() / p.first.height.coerceAtLeast(1)
        // Physical left = before, right = after, whatever the text direction.
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) { BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2f)).clip(RoundedCornerShape(Radius.m)).background(tokens.surfaceSunken)
            .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> split = (ch.position.x / size.width).coerceIn(0f, 1f) } }
            .pointerInput(Unit) { detectTapGestures { split = (it.x / size.width).coerceIn(0f, 1f) } }) {
            androidx.compose.foundation.Image(p.second, "بعد", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            androidx.compose.foundation.Image(p.first, "قبل", Modifier.fillMaxSize().drawWithContent {
                clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
            }, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            Box(Modifier.fillMaxHeight().width(3.dp).offset(x = maxWidth * split - 1.5.dp).background(androidx.compose.ui.graphics.Color.White))
            Text("قبل", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
            Text("بعد", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
        } }
        Text("اسحب الخط للمقارنة", style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Long operations post a notification when they finish (only if notifications are allowed). */
fun notifyDone(ctx: android.content.Context, title: String, n: Int) {
    if (android.os.Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
    val i = android.app.PendingIntent.getActivity(ctx, 0, android.content.Intent(ctx, com.muyassir.app.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP), android.app.PendingIntent.FLAG_IMMUTABLE)
    val note = androidx.core.app.NotificationCompat.Builder(ctx, com.muyassir.app.MuyassirApp.CHANNEL)
        .setSmallIcon(com.muyassir.app.R.drawable.ic_launcher_foreground).setContentTitle("اكتملت العملية: $title")
        .setContentText("$n ${if (n == 1) "ملف جاهز" else "ملفات جاهزة"} في مُيسّر").setContentIntent(i).setAutoCancel(true).build()
    runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(7001, note) }
}
