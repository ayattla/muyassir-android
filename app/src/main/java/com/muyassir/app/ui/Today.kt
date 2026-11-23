package com.muyassir.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyassir.app.core.Daily
import com.muyassir.app.core.Prefs
import com.muyassir.app.core.Store
import com.muyassir.app.engine.Cities
import com.muyassir.app.engine.Hijri
import com.muyassir.app.engine.PayDays
import com.muyassir.app.engine.PrayerTimes
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** «يومي»: what matters today at a glance. Every card shows only when it has something to say. */
@Composable
fun TodayScreen(open: (String) -> Unit, openAssistant: () -> Unit, openTools: () -> Unit) {
    Daily.load()
    var now by remember { mutableStateOf(GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh"))) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh")) } }
    var editing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }   // bumps after a task is ticked off

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.l).padding(bottom = Space.xxxl).testTag("today"),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = Space.l), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(now), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    Text("يومي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { editing = !editing }, modifier = Modifier.testTag("todaySettings").semantics { contentDescription = "تخصيص يومي" }) {
                    Icon(Icons.Rounded.Tune, null, tint = tokens.textPrimary)
                }
            }
            if (!Daily.setupDone || editing) SetupCard(first = !Daily.setupDone) { editing = false }
            if (Daily.showPrayer) PrayerCard(now)
            DatesRow(now, open)
            key(tick) { TasksCard(now, open) { tick++ } }
            Daily.roles.forEach { RoleCard(it, now, open) }
            if (Daily.showAdhkar && Daily.showPrayer) AdhkarCard(now)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(tokens.surface).border(1.dp, tokens.borderStrong, RoundedCornerShape(Radius.m))
                    .clickable(onClick = openAssistant).heightIn(min = 52.dp).padding(horizontal = Space.l).testTag("todayAssistant"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = tokens.accent, modifier = Modifier.size(20.dp))
                Text("اكتب ما تريد… مثل: حوّل هذه الصور إلى PDF", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
            }
            TextButton(onClick = openTools, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("كل الأدوات") }
        }
    }
}

private fun greeting(c: Calendar) = when (c.get(Calendar.HOUR_OF_DAY)) { in 4..11 -> "صباح الخير"; in 12..16 -> "طاب يومك"; else -> "مساء الخير" } +
    Prefs.name.takeIf { it.isNotBlank() }?.let { "، $it" }.orEmpty()

private fun minuteOfDay(c: Calendar) = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)

@Composable
private fun SetupCard(first: Boolean, done: () -> Unit) {
    Column(Modifier.fillMaxWidth().cardSurface().padding(Space.l).testTag("todaySetup"), verticalArrangement = Arrangement.spacedBy(Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (first) "نرتّب لك يومك" else "تخصيص يومي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("اختر ما يصفك، ويمكنك اختيار أكثر من دور. كل ما تختاره يبقى على جهازك.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center)
        val roles = listOf("student" to "طالب", "teacher" to "معلم", "employee" to "موظف", "business" to "صاحب منشأة")
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            roles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    row.forEach { (id, label) ->
                        val on = id in Daily.roles
                        Box(
                            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(Radius.m))
                                .background(if (on) tokens.accentSoft else tokens.surface)
                                .border(if (on) 2.dp else 1.dp, if (on) tokens.accent else tokens.border, RoundedCornerShape(Radius.m))
                                .clickable { Daily.toggleRole(id) }.testTag("role-$id"),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = MaterialTheme.typography.titleSmall, color = if (on) tokens.accent else tokens.textPrimary) }
                    }
                }
            }
        }
        Picker("المدينة لمواقيت الصلاة", Cities.all, Cities.byId(Daily.city), { it.name }) { Daily.chooseCity(it.id) }
        Picker("موعد راتبك", listOf("gov", "custom", "none"), Daily.salary, { mapOf("gov" to "راتب الدولة (يوم 27)", "custom" to "يوم أحدده", "none" to "لا أريد")[it]!! }) { Daily.setSalary(it) }
        if (Daily.salary == "custom") {
            var day by remember { mutableStateOf(Daily.salaryDay.toString()) }
            NumField("يوم الراتب من الشهر (1 إلى 31)", day, { day = it; it.toIntOrNull()?.let { d -> Daily.setSalary("custom", d) } })
        }
        SwitchRow("مواقيت الصلاة", Daily.showPrayer) { Daily.setPrayer(it) }
        SwitchRow("حساب المواطن (يوم 10)", Daily.showCitizen) { Daily.setCitizen(it) }
        SwitchRow("التقاعد والضمان (يوم 1)", Daily.showPension) { Daily.setPension(it) }
        SwitchRow("انتهاء الوثائق", Daily.showDocs) { Daily.setDocs(it) }
        SwitchRow("تذكير الأذكار من آيات تتلى", Daily.showAdhkar) { Daily.setAdhkar(it) }
        PrimaryButton(if (first) "ابدأ يومي" else "تم", modifier = Modifier.testTag("todayStart")) { Daily.finishSetup(); done() }
    }
}

@Composable
private fun PrayerCard(now: Calendar) {
    val city = Cities.byId(Daily.city)
    val times = remember(now.get(Calendar.DAY_OF_YEAR), city.id) { PrayerTimes.today(city, now) }
    val tomorrow = remember(now.get(Calendar.DAY_OF_YEAR), city.id) {
        val t = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }; PrayerTimes.today(city, t)
    }
    val (idx, left) = PrayerTimes.next(times, minuteOfDay(now), tomorrow[0])
    val h = Hijri.toHijri(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    val accent = tokens.accent; val on = tokens.onAccent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(accent).padding(Space.l).testTag("prayerCard"),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("${Hijri.days[now.get(Calendar.DAY_OF_WEEK) - 1]} ${h.d} ${Hijri.months[h.m - 1]} ${h.y}", color = on.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall)
                Text("${now.get(Calendar.DAY_OF_MONTH)} ${Hijri.gMonths[now.get(Calendar.MONTH)]} ${now.get(Calendar.YEAR)}", color = on.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall)
            }
            Text(city.name, color = on, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(on.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("صلاة ${PrayerTimes.names[idx]} بعد", color = on.copy(alpha = .92f), style = MaterialTheme.typography.bodyMedium)
            Text(PrayerTimes.left(left), color = on, fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("prayerLeft"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0, 2, 3, 4, 5).forEach { i ->
                val sel = i == idx
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) on.copy(alpha = .2f) else Color.Transparent).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(PrayerTimes.names[i], color = on.copy(alpha = if (sel) 1f else .75f), style = MaterialTheme.typography.labelSmall)
                    Text(PrayerTimes.clock(times[i]), color = on.copy(alpha = if (sel) 1f else .75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private data class Due(val days: Long, val title: String, val tone: Int, val tool: String?)

@Composable
private fun DatesRow(now: Calendar, open: (String) -> Unit) {
    val dues = buildList {
        Daily.payRules().forEach { r -> val (d, _) = PayDays.next(r, now); add(Due(d.toLong(), r.title, 0, if (r.id == "gov" || r.id == "custom") "salary" else null)) }
        if (Daily.showDocs) {
            AppLock.load()
            Store.list("documents").filter { it.optLong("expiry") > 0 }.map { it to com.muyassir.app.tools.daysUntil(it.optLong("expiry")) }
                .filter { it.second in 0..60 }.minByOrNull { it.second }?.let { (o, d) ->
                    val name = if (AppLock.enabled) "وثيقة" else o.optString("name"); add(Due(d, name, if (d <= 30) 2 else 1, "documents"))
                }
        }
        Store.list("exams").filter { it.optLong("at") > System.currentTimeMillis() }.minByOrNull { it.optLong("at") }?.let {
            val d = com.muyassir.app.tools.daysUntil(it.optLong("at")); if (d <= 14) add(Due(d, "اختبار ${it.optString("subject")}", 1, "exams"))
        }
    }.sortedBy { it.days }.take(3)
    if (dues.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text("مواعيد قريبة", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.testTag("dates")) {
            dues.forEach { d ->
                val warn = d.tone == 2
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (warn) toneSoft(Color(0xFFB0600A)) else tokens.surface)
                        .border(1.dp, if (warn) Color(0xFFB0600A).copy(alpha = .35f) else tokens.border, RoundedCornerShape(18.dp))
                        .clickable(enabled = d.tool != null) { d.tool?.let(open) }.padding(vertical = Space.m, horizontal = Space.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (d.days == 0L) "اليوم" else "${d.days}", fontSize = if (d.days == 0L) 18.sp else 24.sp, fontWeight = FontWeight.Bold,
                        color = if (warn) Color(0xFFB0600A).let { c -> if (tokens.dark) Color(0xFFF2B01E) else c } else tokens.accent)
                    if (d.days > 0) Text(if (d.days in 3..10) "أيام" else "يومًا", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                    Text(d.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun TasksCard(now: Calendar, open: (String) -> Unit, changed: () -> Unit) {
    val endOfDay = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
    val tomorrowEnd = endOfDay + 86_400_000L
    val tasks = Store.list("tasks").filter { val due = it.optLong("due"); due in 1..endOfDay || (due == 0L && !it.optBoolean("done")) }.take(5)
    val homework = Store.list("homework").filter { val due = it.optLong("due"); due in 1..tomorrowEnd && !it.optBoolean("done") }.take(3)
    val doneCount = tasks.count { it.optBoolean("done") }
    Column(Modifier.fillMaxWidth().cardSurface().padding(Space.l).testTag("todayTasks"), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("مهامك اليوم", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (tasks.isNotEmpty()) Text("$doneCount من ${tasks.size}", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
        }
        if (tasks.isEmpty() && homework.isEmpty()) {
            Text("لا مهام لليوم. أضف ما تريد إنجازه، ونعرضه هنا ونذكّرك به.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
        }
        tasks.forEach { t -> TaskLine(t.optString("title"), t.optBoolean("done")) { toggle("tasks", t); changed() } }
        homework.forEach { t -> TaskLine("${t.optString("subject")}: ${t.optString("title")}", false) { toggle("homework", t); changed() } }
        TextButton(onClick = { open("tasks") }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (tasks.isEmpty()) "أضف مهمة" else "كل المهام") }
    }
}

private fun toggle(store: String, o: JSONObject) {
    Store.saveList(store, Store.list(store).map { if (it.optLong("id") == o.optLong("id")) JSONObject(it.toString()).put("done", !o.optBoolean("done")) else it })
}

@Composable
private fun TaskLine(text: String, done: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(done, { onToggle() })
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (done) tokens.textTertiary else tokens.textPrimary,
            textDecoration = if (done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RoleCard(role: String, now: Calendar, open: (String) -> Unit) {
    val (title, line, actions) = when (role) {
        "student" -> {
            val t = Store.obj("timetable"); val dow = now.get(Calendar.DAY_OF_WEEK)
            // The timetable runs Sunday (0) to Thursday (4); after school, tomorrow's lessons are what matters.
            val target = if (now.get(Calendar.HOUR_OF_DAY) >= 13) (dow % 7) else dow - 1
            val lessons = if (target in 0..4) (0 until 7).mapNotNull { p -> t.optString("$target-$p").takeIf { it.isNotBlank() } } else emptyList()
            Triple(if (now.get(Calendar.HOUR_OF_DAY) >= 13) "حصص الغد" else "حصص اليوم", lessons.joinToString("، ").ifBlank { "أضف جدولك الدراسي لتظهر حصصك هنا" },
                listOf("timetable" to "الجدول", "flashcards" to "المراجعة"))
        }
        "teacher" -> {
            val q = Store.list("published").firstOrNull()
            Triple("اختباراتك", q?.let { "آخر اختبار منشور: ${it.optString("title")}" } ?: "أنشئ اختبارًا وانشره برابط، وتصلك النتائج هنا",
                listOf("quiz_maker" to "اختبار جديد", "attendance" to "الحضور"))
        }
        "employee" -> Triple("عملك", "احسب راتبك الصافي أو مكافأتك أو إجازاتك بالمواد النظامية", listOf("salary" to "الراتب", "leave" to "الإجازات"))
        else -> Triple("منشأتك", "فاتورة أو عرض سعر في دقيقة، بصيغة PDF", listOf("invoice" to "فاتورة", "quote" to "عرض سعر"))
    }
    Column(Modifier.fillMaxWidth().cardSurface().padding(Space.l).testTag("roleCard-$role"), verticalArrangement = Arrangement.spacedBy(Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(line, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) { actions.forEach { (id, label) -> SecondaryButton(label, modifier = Modifier.weight(1f)) { open(id) } } }
    }
}

@Composable
private fun AdhkarCard(now: Calendar) {
    val ctx = LocalContext.current
    val city = Cities.byId(Daily.city)
    val t = remember(now.get(Calendar.DAY_OF_YEAR), city.id) { PrayerTimes.today(city, now) }
    val m = minuteOfDay(now)
    val (label, path) = when {
        m >= t[0] && m < t[3] -> "أذكار الصباح" to "azkar-sabah"
        m >= t[3] && m < t[5] + 120 -> "أذكار المساء" to "azkar-masaa"
        else -> "أذكار النوم" to "azkar-nawm"
    }
    val green = if (tokens.dark) Color(0xFF5FD3AE) else Color(0xFF0E7A57)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(toneSoft(Color(0xFF14936B))).border(1.dp, Color(0xFF14936B).copy(alpha = .25f), RoundedCornerShape(Radius.l))
            .clickable { openUrl(ctx, "https://ayattla.com/$path") }.padding(Space.l).testTag("adhkarCard"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = green, fontWeight = FontWeight.SemiBold)
        Text("حان وقت $label", style = MaterialTheme.typography.bodyMedium)
        Text("افتحها في آيات تتلى", style = MaterialTheme.typography.labelLarge, color = green)
    }
}
