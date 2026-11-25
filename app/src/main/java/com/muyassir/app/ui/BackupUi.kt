package com.muyassir.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Backup
import com.muyassir.app.core.Plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** Encrypted backup: the user picks where the file goes; nothing is uploaded by the app. */
@Composable
fun BackupPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<String?>(null) }   // "export" | "restore"
    var pass by remember { mutableStateOf("") }; var pass2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        u ?: return@rememberLauncherForActivityResult
        val p = pass; busy = true
        scope.launch {
            msg = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(u)!!.use { Backup.export(ctx, p, it) }; "حفظت النسخة المشفرة. احتفظ بكلمة المرور، لا يمكن استرجاعها." }.getOrElse { "تعذر الحفظ: ${it.message}" }
            }
            busy = false; pass = ""; pass2 = ""
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) { pending = u; mode = "restore" } }
    Panel("النسخ الاحتياطي المشفر") {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("احفظ ملاحظاتك ووثائقك ومصاريفك وجداولك في ملف مشفر بكلمة مرور، وضعه حيث تريد: ذاكرة الجهاز أو Drive أو غيره. التطبيق لا يرفع شيئا بنفسه.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            PlusBadge()
        }
        SecondaryButton("إنشاء نسخة احتياطية", Icons.Rounded.Backup, Modifier.fillMaxWidth().testTag("backupExport")) { if (Plus.allow(Plus.Reason.Backup)) { msg = null; mode = "export" } }
        SecondaryButton("استعادة من نسخة", Icons.Rounded.Restore, Modifier.fillMaxWidth().testTag("backupRestore")) { if (Plus.allow(Plus.Reason.Backup)) { msg = null; open.launch(arrayOf("*/*")) } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        msg?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
    if (mode != null) AlertDialog(
        onDismissRequest = { mode = null; pass = ""; pass2 = "" },
        title = { Text(if (mode == "export") "كلمة مرور النسخة" else "كلمة مرور النسخة المحفوظة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == "export") Text("8 أحرف على الأقل. لا نستطيع استعادتها إن نسيتها.", style = MaterialTheme.typography.bodySmall)
                else Text("ستستبدل بياناتك الحالية ببيانات النسخة، ثم يعاد تشغيل التطبيق.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(pass, { pass = it }, label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("backupPass"))
                if (mode == "export") OutlinedTextField(pass2, { pass2 = it }, label = { Text("تأكيد كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("backupPass2"))
            }
        },
        confirmButton = {
            TextButton(enabled = if (mode == "export") pass.length >= 8 && pass == pass2 else pass.isNotEmpty(), onClick = {
                if (mode == "export") {
                    mode = null; create.launch("ميسر-نسخة-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.mysb")
                } else {
                    val u = pending ?: return@TextButton; val p = pass; mode = null; busy = true
                    scope.launch {
                        val err = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)!!.use { Backup.restore(ctx, p, it) } }.exceptionOrNull() }
                        busy = false; pass = ""
                        if (err != null) msg = err.message ?: "تعذرت الاستعادة"
                        else {
                            val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            ctx.startActivity(i); Runtime.getRuntime().exit(0)
                        }
                    }
                }
            }, modifier = Modifier.testTag("backupConfirm")) { Text(if (mode == "export") "متابعة" else "استعادة") }
        },
        dismissButton = { TextButton(onClick = { mode = null; pass = ""; pass2 = "" }) { Text("إلغاء") } }
    )
}
