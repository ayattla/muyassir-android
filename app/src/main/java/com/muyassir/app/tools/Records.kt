package com.muyassir.app.tools

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.ReminderReceiver
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.Office
import com.muyassir.app.ui.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class FType { TEXT, MULTI, NUM, DATE, DATETIME, CHOICE, BOOL }
data class FieldSpec(val key: String, val label: String, val type: FType = FType.TEXT, val options: List<String> = emptyList(), val required: Boolean = false)

val dayFmt = SimpleDateFormat("EEEE d/M/yyyy", Locale("ar"))
val dtFmt = SimpleDateFormat("EEEE d/M/yyyy  HH:mm", Locale("ar"))
fun daysUntil(t: Long): Long {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return Math.floorDiv(t - c.timeInMillis, 86_400_000L)
}
fun whenText(t: Long): String = when (val d = daysUntil(t)) { 0L -> "اليوم"; 1L -> "غدًا"; -1L -> "أمس"; else -> if (d > 0) "بعد $d يوم" else "منذ ${-d} يوم" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: Long, withTime: Boolean, onChange: (Long) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var tmpDay by remember { mutableLongStateOf(0L) }
    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Event, null); Spacer(Modifier.width(8.dp))
        Text(if (value == 0L) label else (if (withTime) dtFmt else dayFmt).format(Date(value)))
    }
    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = if (value == 0L) System.currentTimeMillis() else value)
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(onClick = {
                pickDate = false
                val utc = st.selectedDateMillis ?: return@TextButton
                val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                val local = Calendar.getInstance().apply { clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 9, 0) }
                if (withTime) { tmpDay = local.timeInMillis; pickTime = true } else onChange(local.timeInMillis)
            }) { Text("تم") }
        }, dismissButton = { TextButton(onClick = { pickDate = false }) { Text("إلغاء") } }) { DatePicker(st) }
    }
    if (pickTime) {
        val c = Calendar.getInstance().apply { timeInMillis = if (value == 0L) System.currentTimeMillis() + 3600_000 else value }
        val ts = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)
        AlertDialog(onDismissRequest = { pickTime = false }, confirmButton = {
            TextButton(onClick = {
                pickTime = false
                onChange(Calendar.getInstance().apply { timeInMillis = tmpDay; set(Calendar.HOUR_OF_DAY, ts.hour); set(Calendar.MINUTE, ts.minute) }.timeInMillis)
            }) { Text("تم") }
        }, text = { TimePicker(ts) })
    }
}

/** Generic on-device record list: add / edit / delete / tick, optional reminders and Excel export. */
@Composable
fun RecordList(
    t: Tool, back: () -> Unit, store: String, fields: List<FieldSpec>,
    title: (JSONObject) -> String, subtitle: (JSONObject) -> String = { "" },
    doneKey: String? = null, sort: (List<JSONObject>) -> List<JSONObject> = { it },
    reminder: ((JSONObject) -> Pair<Long, String>?)? = null,
    header: @Composable ColumnScope.(List<JSONObject>) -> Unit = {},
    emptyText: String = "أضف أول عنصر بالزر أدناه",
) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(Store.list(store)) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun save(list: List<JSONObject>) { items = list.toMutableList(); Store.saveList(store, list) }

    ToolScreen(t, back) {
        header(items)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("إضافة", Icons.Rounded.Add, modifier = Modifier.weight(1f)) { editing = JSONObject().put("id", System.currentTimeMillis()) }
            if (items.isNotEmpty()) SecondaryButton("تصدير Excel", Icons.Rounded.TableChart) {
                val out = File(Files.outDir(ctx), "${t.title}.xlsx")
                val rows = listOf(fields.map { it.label }) + items.map { o -> fields.map { f -> cell(o, f) } }
                Office.writeXlsx(out, listOf(t.title to rows)); Files.share(ctx, listOf(out))
            }
        }
        if (items.isEmpty()) EmptyState(t.icon, "لا توجد عناصر بعد", emptyText)
        sort(items).forEach { o ->
            val done = doneKey?.let { o.optBoolean(it) } ?: false
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (doneKey != null) Checkbox(done, { v -> save(items.map { if (it.optLong("id") == o.optLong("id")) JSONObject(it.toString()).put(doneKey, v) else it }) })
                    Column(Modifier.weight(1f).clickable { editing = JSONObject(o.toString()) }) {
                        Text(title(o), style = MaterialTheme.typography.titleSmall, textDecoration = if (done) TextDecoration.LineThrough else null)
                        val s = subtitle(o); if (s.isNotBlank()) Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        ReminderReceiver.cancel(ctx, (o.optLong("id") % Int.MAX_VALUE).toInt())
                        save(items.filter { it.optLong("id") != o.optLong("id") })
                    }) { Icon(Icons.Rounded.DeleteOutline, "حذف") }
                }
            }
        }
    }

    editing?.let { e ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (items.any { it.optLong("id") == e.optLong("id") }) "تعديل" else "إضافة") },
            text = {
                Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var tick by remember { mutableIntStateOf(0) }
                    key(tick) {
                        fields.forEach { f ->
                            when (f.type) {
                                FType.TEXT -> TextField2(f.label, e.optString(f.key), { e.put(f.key, it); tick++ })
                                FType.MULTI -> TextField2(f.label, e.optString(f.key), { e.put(f.key, it); tick++ }, lines = 4)
                                FType.NUM -> NumField(f.label, e.optString(f.key), { e.put(f.key, it); tick++ })
                                FType.DATE -> DateField(f.label, e.optLong(f.key), false) { e.put(f.key, it); tick++ }
                                FType.DATETIME -> DateField(f.label, e.optLong(f.key), true) { e.put(f.key, it); tick++ }
                                FType.CHOICE -> Picker(f.label, f.options, e.optString(f.key, f.options.first()), { it }) { e.put(f.key, it); tick++ }
                                FType.BOOL -> SwitchRow(f.label, e.optBoolean(f.key)) { e.put(f.key, it); tick++ }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fields.any { it.required && e.optString(it.key).isBlank() }) { toast(ctx, "أكمل الحقول المطلوبة"); return@TextButton }
                    fields.filter { it.type == FType.CHOICE && !e.has(it.key) }.forEach { e.put(it.key, it.options.first()) }
                    val list = items.filter { it.optLong("id") != e.optLong("id") } + e
                    save(list)
                    reminder?.invoke(e)?.let { (at, text) ->
                        if (at > System.currentTimeMillis()) {
                            if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                            ReminderReceiver.schedule(ctx, (e.optLong("id") % Int.MAX_VALUE).toInt(), at, title(e), text)
                        }
                    }
                    Store.used(t.id, title(e)); editing = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun Modifier.verticalScrollCompat() = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

private fun cell(o: JSONObject, f: FieldSpec): String = when (f.type) {
    FType.DATE -> o.optLong(f.key).takeIf { it > 0 }?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: ""
    FType.DATETIME -> o.optLong(f.key).takeIf { it > 0 }?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it)) } ?: ""
    FType.BOOL -> if (o.optBoolean(f.key)) "نعم" else "لا"
    else -> o.optString(f.key)
}

// ------------------------------------------------------------------ concrete lists

@Composable fun NotesTool(t: Tool, back: () -> Unit) = RecordList(t, back, "notes",
    listOf(FieldSpec("title", "العنوان", required = true), FieldSpec("body", "الملاحظة", FType.MULTI)),
    title = { it.optString("title") }, subtitle = { it.optString("body").take(120) }, sort = { l -> l.sortedByDescending { it.optLong("id") } })

@Composable fun TasksTool(t: Tool, back: () -> Unit) = RecordList(t, back, "tasks",
    listOf(FieldSpec("title", "المهمة", required = true), FieldSpec("priority", "الأولوية", FType.CHOICE, listOf("عادية", "مهمة", "عاجلة")), FieldSpec("due", "الموعد", FType.DATE), FieldSpec("done", "منجزة", FType.BOOL)),
    title = { it.optString("title") }, doneKey = "done",
    subtitle = { o -> listOfNotNull(o.optString("priority").takeIf { it.isNotBlank() }, o.optLong("due").takeIf { it > 0 }?.let { whenText(it) }).joinToString(" • ") },
    sort = { l -> l.sortedWith(compareBy<JSONObject>({ it.optBoolean("done") }, { -listOf("عادية", "مهمة", "عاجلة").indexOf(it.optString("priority")) }, { it.optLong("due").takeIf { d -> d > 0 } ?: Long.MAX_VALUE })) })

@Composable fun RemindersTool(t: Tool, back: () -> Unit) = RecordList(t, back, "reminders",
    listOf(FieldSpec("title", "التذكير", required = true), FieldSpec("at", "الوقت", FType.DATETIME, required = true), FieldSpec("note", "تفاصيل")),
    title = { it.optString("title") }, subtitle = { o -> o.optLong("at").takeIf { it > 0 }?.let { dtFmt.format(Date(it)) } ?: "" },
    sort = { l -> l.sortedBy { it.optLong("at") } }, reminder = { o -> o.optLong("at") to o.optString("note") })

@Composable fun ExamsTool(t: Tool, back: () -> Unit) = RecordList(t, back, "exams",
    listOf(FieldSpec("subject", "المادة", required = true), FieldSpec("at", "موعد الاختبار", FType.DATETIME, required = true), FieldSpec("room", "القاعة/الملاحظات"), FieldSpec("remind", "تذكير قبلها بيوم", FType.BOOL)),
    title = { it.optString("subject") }, subtitle = { o -> o.optLong("at").takeIf { it > 0 }?.let { "${dtFmt.format(Date(it))} • ${whenText(it)}" } ?: "" },
    sort = { l -> l.sortedBy { it.optLong("at") } },
    reminder = { o -> if (o.optBoolean("remind")) (o.optLong("at") - 86_400_000L) to "اختبارك غدًا" else null },
    header = { list ->
        list.filter { it.optLong("at") > System.currentTimeMillis() }.minByOrNull { it.optLong("at") }?.let {
            ResultCard("أقرب اختبار" to it.optString("subject"), listOf("الموعد" to whenText(it.optLong("at"))))
        }
    })

@Composable fun HomeworkTool(t: Tool, back: () -> Unit) = RecordList(t, back, "homework",
    listOf(FieldSpec("subject", "المادة", required = true), FieldSpec("title", "الواجب", required = true), FieldSpec("due", "موعد التسليم", FType.DATE), FieldSpec("done", "سُلّم", FType.BOOL)),
    title = { "${it.optString("subject")}: ${it.optString("title")}" }, doneKey = "done",
    subtitle = { o -> o.optLong("due").takeIf { it > 0 }?.let { "التسليم ${whenText(it)}" } ?: "" },
    sort = { l -> l.sortedWith(compareBy({ it.optBoolean("done") }, { it.optLong("due").takeIf { d -> d > 0 } ?: Long.MAX_VALUE })) })

@Composable fun DocumentsTool(t: Tool, back: () -> Unit) = RecordList(t, back, "documents",
    listOf(
        FieldSpec("name", "اسم الوثيقة أو الموعد", required = true),
        FieldSpec("kind", "النوع", FType.CHOICE, listOf("هوية وطنية", "جواز سفر", "إقامة", "رخصة قيادة", "استمارة مركبة", "تأمين", "عقد إيجار", "موعد", "أخرى")),
        FieldSpec("expiry", "تاريخ الانتهاء أو الموعد", FType.DATE, required = true),
        FieldSpec("days", "التذكير قبل (أيام)", FType.NUM), FieldSpec("note", "ملاحظة"),
    ),
    title = { it.optString("name") },
    subtitle = { o -> "${o.optString("kind")} • ${o.optLong("expiry").takeIf { it > 0 }?.let { whenText(it) } ?: ""}" },
    sort = { l -> l.sortedBy { it.optLong("expiry") } },
    reminder = { o -> (o.optLong("expiry") - (o.optString("days").num().takeIf { it > 0 } ?: 30.0).toLong() * 86_400_000L) to "تنتهي ${o.optString("kind")} قريبًا — جدّدها عبر المنصة الرسمية" },
    header = { list ->
        val soon = list.count { val d = daysUntil(it.optLong("expiry")); d in 0..30 }
        val expired = list.count { daysUntil(it.optLong("expiry")) < 0 }
        if (list.isNotEmpty()) ResultCard(null, listOf("تنتهي خلال 30 يومًا" to "$soon", "منتهية" to "$expired"))
        Hint("لا تكتب أرقام الوثائق الكاملة. البيانات تبقى على جهازك فقط. التجديد يتم عبر أبشر أو المنصة المختصة.")
    })

private val expenseCats = listOf("طعام", "مواصلات", "سكن", "فواتير", "تسوق", "صحة", "تعليم", "ترفيه", "أخرى")

@Composable fun ExpensesTool(t: Tool, back: () -> Unit) = RecordList(t, back, "expenses",
    listOf(FieldSpec("title", "البند", required = true), FieldSpec("amount", "المبلغ", FType.NUM, required = true), FieldSpec("cat", "الفئة", FType.CHOICE, expenseCats), FieldSpec("date", "التاريخ", FType.DATE)),
    title = { "${it.optString("title")} — ${money(it.optString("amount").num())}" },
    subtitle = { o -> "${o.optString("cat")} • ${o.optLong("date").takeIf { it > 0 }?.let { dayFmt.format(Date(it)) } ?: ""}" },
    sort = { l -> l.sortedByDescending { it.optLong("date").takeIf { d -> d > 0 } ?: it.optLong("id") } },
    header = { list ->
        val month = thisMonth(list)
        if (list.isNotEmpty()) ResultCard("مصروف هذا الشهر" to money(month.sumOf { it.optString("amount").num() }),
            month.groupBy { it.optString("cat") }.map { (k, v) -> k to money(v.sumOf { it.optString("amount").num() }) }.sortedByDescending { it.second })
    })

fun thisMonth(list: List<JSONObject>): List<JSONObject> {
    val c = Calendar.getInstance(); val m = c.get(Calendar.MONTH); val y = c.get(Calendar.YEAR)
    return list.filter { val d = Calendar.getInstance().apply { timeInMillis = it.optLong("date").takeIf { v -> v > 0 } ?: it.optLong("id") }; d.get(Calendar.MONTH) == m && d.get(Calendar.YEAR) == y }
}

@Composable
fun BudgetTool(t: Tool, back: () -> Unit) {
    val cfg = remember { Store.obj("budget") }
    var income by remember { mutableStateOf(cfg.optString("income")) }
    val limits = remember { mutableStateMapOf<String, String>().apply { expenseCats.forEach { put(it, cfg.optString("l_$it")) } } }
    val spent = remember { thisMonth(Store.list("expenses")).groupBy { it.optString("cat") }.mapValues { (_, v) -> v.sumOf { it.optString("amount").num() } } }
    fun persist() { val o = JSONObject().put("income", income); limits.forEach { (k, v) -> o.put("l_$k", v) }; Store.saveObj("budget", o) }
    ToolScreen(t, back) {
        Panel { NumField("الدخل الشهري", income, { income = it; persist() }, suffix = "ريال") }
        val total = spent.values.sum()
        ResultCard("المتبقي هذا الشهر" to money(income.num() - total), listOf("المصروف" to money(total), "الحدود المخططة" to money(limits.values.sumOf { it.num() })),
            note = "المصروفات تُسجَّل من أداة «إدارة المصروفات».")
        Panel("الحد الشهري لكل فئة") {
            expenseCats.forEach { c ->
                val s = spent[c] ?: 0.0; val lim = limits[c]?.num() ?: 0.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c, style = MaterialTheme.typography.titleSmall)
                        if (lim > 0) { LinearProgressIndicator(progress = { (s / lim).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 4.dp), color = if (s > lim) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                        Text("صُرف ${money(s)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    NumField("الحد", limits[c] ?: "", { limits[c] = it; persist() }, Modifier.width(130.dp))
                }
            }
        }
    }
}

@Composable
fun AttendanceTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var students by remember { mutableStateOf(Store.list("students").map { it.optString("name") }) }
    var sessions by remember { mutableStateOf<List<JSONObject>>(Store.list("attendance")) }
    var newName by remember { mutableStateOf("") }
    val absentToday = remember { mutableStateListOf<String>() }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun saveStudents(l: List<String>) { students = l; Store.saveList("students", l.map { JSONObject().put("name", it) }) }
    ToolScreen(t, back) {
        Panel("الطلاب (${students.size})") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField2("اسم الطالب", newName, { newName = it }, Modifier.weight(1f))
                IconButton(onClick = { if (newName.isNotBlank()) { saveStudents(students + newName.trim()); newName = "" } }) { Icon(Icons.Rounded.PersonAdd, "إضافة") }
            }
            Hint("أضف عدة أسماء دفعة واحدة بفصلها بفاصلة.")
            if (newName.contains(',') || newName.contains('،')) SecondaryButton("إضافة الكل", Icons.Rounded.GroupAdd) {
                saveStudents(students + newName.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }); newName = ""
            }
        }
        if (students.isNotEmpty()) Panel("تسجيل حصة") {
            DateField("التاريخ", date, false) { date = it }
            Text("علّم الغائبين:", style = MaterialTheme.typography.labelLarge)
            students.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (s in absentToday) absentToday.remove(s) else absentToday.add(s) }) {
                    Checkbox(s in absentToday, { if (it) absentToday.add(s) else absentToday.remove(s) })
                    Text(s, Modifier.weight(1f))
                    IconButton(onClick = { saveStudents(students - s) }) { Icon(Icons.Rounded.Close, "حذف الطالب", Modifier.size(18.dp)) }
                }
            }
            PrimaryButton("حفظ الحصة (${students.size - absentToday.size} حاضر)", Icons.Rounded.Check) {
                val o = JSONObject().put("id", System.currentTimeMillis()).put("date", date).put("absent", org.json.JSONArray(absentToday.toList()))
                sessions = sessions + o; Store.saveList("attendance", sessions); absentToday.clear(); Store.used(t.id); toast(ctx, "حُفظت الحصة")
            }
        }
        if (sessions.isNotEmpty()) Panel("ملخص الغياب (${sessions.size} حصة)") {
            val counts = students.associateWith { s -> sessions.count { o -> (0 until (o.optJSONArray("absent")?.length() ?: 0)).any { o.getJSONArray("absent").getString(it) == s } } }
            counts.entries.sortedByDescending { it.value }.forEach { (s, c) -> LabelValue(s, "$c غياب • ${fmt(100.0 * (sessions.size - c) / sessions.size, 0)}% حضور") }
            SecondaryButton("تصدير Excel", Icons.Rounded.TableChart, Modifier.fillMaxWidth()) {
                val dates = sessions.sortedBy { it.optLong("date") }
                val header = listOf("الطالب") + dates.map { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.optLong("date"))) } + listOf("الغياب")
                val rows = students.map { s ->
                    val marks = dates.map { o -> if ((0 until (o.optJSONArray("absent")?.length() ?: 0)).any { o.getJSONArray("absent").getString(it) == s }) "غائب" else "حاضر" }
                    listOf(s) + marks + listOf(marks.count { it == "غائب" }.toString())
                }
                val out = File(Files.outDir(ctx), "سجل-الحضور.xlsx"); Office.writeXlsx(out, listOf("الحضور" to (listOf(header) + rows))); Files.share(ctx, listOf(out))
            }
        }
    }
}
