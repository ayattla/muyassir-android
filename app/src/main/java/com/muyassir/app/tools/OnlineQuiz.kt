package com.muyassir.app.tools

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Cloud
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.engine.Office
import com.muyassir.app.ui.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Publishing the quiz as an online activity (link + QR, no student accounts) and reading its results. */
@Composable
fun OnlineQuizPanel(title: String, qs: List<JSONObject>, minutes: String) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(2) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf<JSONObject?>(null) }; var note by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf(false) }
    var published by remember { mutableStateOf(Store.list("published")) }
    val dayOptions = listOf(1, 3, 7, 14)

    fun publish() {
        val m = Cloud.toActivity(title, qs, minutes.num().toInt(), dayOptions[days])
        if ((m.activity.optJSONArray("questions")?.length() ?: 0) == 0) { error = "لا توجد أسئلة تُصحح آليًا. أضف سؤال اختيار أو صح وخطأ أو إكمال أو توصيل مكتملًا."; return }
        busy = true; error = null
        scope.launch {
            runCatching { Cloud.publish(m.activity) }
                .onSuccess { last = it; published = Store.list("published"); note = if (m.skipped > 0) "لم يُنشر ${m.skipped} سؤال: الأسئلة المقالية أو غير المكتملة لا تُصحح آليًا." else null }
                .onFailure { error = it.message }
            busy = false
        }
    }

    Panel("اختبار إلكتروني برابط ورمز QR") {
        Hint("يفتحه الطلاب من الجوال أو الحاسب دون حساب، ويُصحح تلقائيًا وتصلك النتائج هنا. تُرسل الأسئلة وإجاباتها فقط إلى خادم مُيسّر، ولا يُطلب من الطالب إلا اسمه.")
        Text("يبقى مفتوحًا", style = MaterialTheme.typography.labelLarge)
        Segmented(listOf("يوم", "3 أيام", "أسبوع", "أسبوعان"), days) { days = it }
        PrimaryButton(if (busy) "جارٍ النشر…" else "انشر برابط ورمز QR", Icons.Rounded.Public, enabled = qs.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().testTag("publishOnline")) {
            if (Cloud.consented) publish() else ask = true
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("onlineError")) }
        last?.let { item ->
            val url = item.optString("url")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val bmp = remember(url) { qrBitmap(url, 600) }
                Image(bmp.asImageBitmap(), "رمز QR للاختبار", Modifier.size(200.dp))
                Text(url, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.testTag("onlineUrl"))
                Text("رمز الاختبار: ${item.optString("code")}", style = MaterialTheme.typography.titleSmall)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("نسخ الرابط", Icons.Rounded.ContentCopy) { copy(ctx, url) }
                    SecondaryButton("مشاركة", Icons.Rounded.Share) {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${item.optString("title")}\n$url"), "مشاركة رابط الاختبار").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                var qrFile by remember(url) { mutableStateOf<List<File>>(emptyList()) }
                TextButton(onClick = {
                    val f = File(Files.outDir(ctx), "رمز-${item.optString("code")}.png")
                    f.outputStream().use { qrBitmap(url, 900).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; qrFile = listOf(f)
                }) { Text("حفظ رمز QR صورة للطباعة") }
                if (qrFile.isNotEmpty()) ResultFiles(qrFile)
            }
        }
    }
    if (published.isNotEmpty()) PublishedList(published) { published = Store.list("published") }

    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("نشر الاختبار على الإنترنت") },
        text = { Text("سيُرسل عنوان الاختبار وأسئلته وإجاباتها الصحيحة ومدته إلى خادم مُيسّر (ayattla.com) لإنشاء صفحة للطلاب. لا تُرسل ملفاتك ولا أي بيانات شخصية عنك. يُغلق الاختبار في الموعد الذي اخترته، وتُحذف نتائجه بعد 90 يومًا من إغلاقه، ويمكنك حذفه متى شئت.") },
        confirmButton = { TextButton(onClick = { ask = false; Cloud.consent(); publish() }, modifier = Modifier.testTag("consentOk")) { Text("موافق، انشر") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("إلغاء") } },
    )
}

@Composable
private fun PublishedList(items: List<JSONObject>, changed: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<String?>(null) }
    var res by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }; var forget by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    val day = SimpleDateFormat("d/M/yyyy", Locale.US)

    fun load(code: String) {
        busy = true; error = null; open = code; res = null; files = emptyList()
        scope.launch { runCatching { Cloud.results(code) }.onSuccess { res = it }.onFailure { error = it.message }; busy = false }
    }

    Panel("اختباراتي المنشورة") {
        items.forEach { p ->
            val code = p.optString("code")
            Column(Modifier.fillMaxWidth().cardSurface().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.optString("title"), style = MaterialTheme.typography.titleSmall)
                Text("الرمز $code • ${p.optInt("count")} سؤال • ${day.format(Date(p.optLong("at")))}", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("النتائج", Icons.Rounded.BarChart, Modifier.testTag("results-$code")) { load(code) }
                    TextButton(onClick = { copy(ctx, p.optString("url")) }) { Text("نسخ الرابط") }
                    TextButton(onClick = { confirm = code }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                }
                if (open == code) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { e -> Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    res?.let { r ->
                        val att = r.optJSONArray("attempts"); val n = att?.length() ?: 0
                        val total = if (n > 0) att!!.getJSONObject(0).optDouble("total") else 0.0
                        ResultCard(main = "عدد المتقدمين" to "$n", rows = listOf(
                            "متوسط الدرجة" to (if (n > 0) "${fmt(r.optDouble("average"))} من ${fmt(total)}" else "—"),
                            "الحالة" to (if (r.optBoolean("closed")) "مغلق" else "مفتوح"),
                        ))
                        if (n == 0) Text("لم يُرسل أي طالب إجابته بعد.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, modifier = Modifier.testTag("noAttempts"))
                        for (k in 0 until n) { val a = att!!.getJSONObject(k); LabelValue(a.optString("name"), "${fmt(a.optDouble("score"))} / ${fmt(a.optDouble("total"))}") }
                        val pq = r.optJSONArray("per_question")
                        if (n > 0 && pq != null) {
                            Text("الإجابات الصحيحة لكل سؤال", style = MaterialTheme.typography.labelLarge)
                            for (k in 0 until pq.length()) { val q = pq.getJSONObject(k); LabelValue("${k + 1}) ${q.optString("text").lineSequence().first().take(40)}", "${q.optInt("correct")} من ${q.optInt("of")}") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (n > 0) SecondaryButton("تصدير إلى Excel", Icons.Rounded.TableChart) {
                                val f = File(Files.outDir(ctx), "نتائج-${r.optString("title")}.xlsx")
                                val rows = listOf(listOf("الاسم", "الدرجة", "من", "وقت الإرسال")) + (0 until n).map { k -> val a = att!!.getJSONObject(k)
                                    listOf(a.optString("name"), fmt(a.optDouble("score")), fmt(a.optDouble("total")), SimpleDateFormat("d/M/yyyy HH:mm", Locale.US).format(Date((a.optDouble("submitted") * 1000).toLong()))) }
                                Office.writeXlsx(f, listOf("النتائج" to rows)); files = listOf(f)
                            }
                            if (!r.optBoolean("closed")) TextButton(onClick = { scope.launch { runCatching { Cloud.close(code) }.onFailure { e -> error = e.message }; load(code) } }) { Text("إغلاق الاختبار الآن") }
                        }
                        if (files.isNotEmpty()) ResultFiles(files)
                    }
                }
            }
        }
        TextButton(onClick = { forget = true }, modifier = Modifier.fillMaxWidth()) { Text("حذف كل اختباراتي ونتائجها من الخادم", color = MaterialTheme.colorScheme.error) }
    }

    confirm?.let { code -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("حذف الاختبار $code؟") },
        text = { Text("سيُحذف الاختبار ونتائج الطلاب من الخادم نهائيًا، ولن يفتح رابطه بعد الآن.") },
        confirmButton = { TextButton(onClick = { confirm = null; scope.launch { runCatching { Cloud.delete(code) }.onSuccess { if (open == code) open = null; changed() }.onFailure { error = it.message; open = code } } }) { Text("حذف نهائي", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء") } }) }
    if (forget) AlertDialog(onDismissRequest = { forget = false }, title = { Text("حذف بياناتك من الخادم؟") },
        text = { Text("ستُحذف كل اختباراتك المنشورة ونتائج طلابها ومعرّف جهازك من خادم مُيسّر نهائيًا. ملفاتك على الجهاز لا تتأثر.") },
        confirmButton = { TextButton(onClick = { forget = false; scope.launch { runCatching { Cloud.forgetMe() }.onSuccess { open = null; changed(); toast(ctx, "حُذفت بياناتك من الخادم") }.onFailure { toast(ctx, it.message ?: "تعذر الحذف") } } }) { Text("حذف نهائي", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { forget = false }) { Text("إلغاء") } })
}
