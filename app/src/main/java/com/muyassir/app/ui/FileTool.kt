package com.muyassir.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) { inputs.addAll(it.filter { u -> u !in inputs }); results = emptyList() } }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) { inputs.clear(); inputs.add(it); results = emptyList() } }

    ToolScreen(tool, onBack) {
        Panel {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                    .clickable { if (multiple) pickMany.launch(mimes) else pickOne.launch(mimes) }.padding(vertical = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.UploadFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Text(pickLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("تتم المعالجة على جهازك ولا تُرفع ملفاتك", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            inputs.forEachIndexed { i, u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Files.name(ctx, u), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(Files.human(Files.size(ctx, u)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (reorderable && inputs.size > 1) {
                        IconButton(enabled = i > 0, onClick = { inputs.add(i - 1, inputs.removeAt(i)) }) { Icon(Icons.Rounded.KeyboardArrowUp, "أعلى") }
                        IconButton(enabled = i < inputs.lastIndex, onClick = { inputs.add(i + 1, inputs.removeAt(i)) }) { Icon(Icons.Rounded.KeyboardArrowDown, "أسفل") }
                    }
                    IconButton(onClick = { inputs.removeAt(i); results = emptyList() }) { Icon(Icons.Rounded.Close, "إزالة") }
                }
            }
        }

        if (inputs.isNotEmpty()) options(inputs)

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        if (busy) {
            Panel {
                Text(progress.label.ifBlank { "جارٍ المعالجة…" }, style = MaterialTheme.typography.bodyMedium)
                if (progress.value >= 0) LinearProgressIndicator(progress = { progress.value }, Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else PrimaryButton(actionLabel, Icons.Rounded.Bolt, enabled = inputs.isNotEmpty()) {
            error = validate(inputs)
            if (error == null) scope.launch {
                busy = true; progress.value = -1f; progress.label = ""
                try {
                    val out = Files.outDir(ctx)
                    results = withContext(Dispatchers.IO) { run(inputs.toList(), out, progress) }
                    Store.used(tool.id, results.firstOrNull()?.name ?: "")
                } catch (t: Throwable) {
                    error = friendly(t)
                } finally {
                    busy = false
                    withContext(Dispatchers.IO) { java.io.File(ctx.cacheDir, "in").deleteRecursively() }
                }
            }
        }

        if (results.isNotEmpty()) ResultFiles(results)
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
    Panel("الناتج (${files.size})") {
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
