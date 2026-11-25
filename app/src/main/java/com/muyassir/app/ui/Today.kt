package com.muyassir.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.muyassir.app.tools.thisMonth
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
import com.muyassir.app.engine.Occasions
import com.muyassir.app.core.Tools
import com.muyassir.app.engine.PayDays
import com.muyassir.app.engine.PrayerTimes
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** «يومي»: what matters today at a glance. Every card shows only when it has something to say. */
@Composable
fun TodayScreen(open: (String) -> Unit, openAssistant: () -> Unit, openTools: () -> Unit, openCat: (String) -> Unit = {}, openMe: () -> Unit = {}) {
    Daily.load()
    var now by remember { mutableStateOf(GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh"))) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh")) } }
    var editing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }   // bumps after a task is ticked off or added

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.l).padding(bottom = Space.xxxl).testTag("today"),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            TopBar(now, openMe) { editing = !editing }
            CommandBar(now, openAssistant, open)
            if (Daily.showPrayer) PrayerCard(now)
            QuickGrid(open, openTools)
            if (!Daily.setupDone || editing) SetupCard(first = !Daily.setupDone) { editing = false }
            WeekStrip(now, open)
            DatesRow(now, open)
            key(tick) { TasksCard(now, open) { tick++ } }
            if (now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) FridayCard()
            BudgetCard(now, open)
            VatCard()
            key(tick) { NoteCard(open) { tick++ } }
            if (Daily.setupDone && Daily.roles.isEmpty()) RolePrompt()
            Daily.roles.forEach { RoleCard(it, now, open) }
            ContinueCard(open)
            if (Daily.showAdhkar && Daily.showPrayer) AdhkarCard(now)
            SpacesGrid(openCat)
            ToolOfDay(now, open)
        }
    }
}

private fun greeting(c: Calendar) = when (c.get(Calendar.HOUR_OF_DAY)) { in 4..11 -> "صباح الخير"; in 12..16 -> "طاب يومك"; else -> "مساء الخير" } +
    Prefs.name.takeIf { it.isNotBlank() }?.let { "، $it" }.orEmpty()

private fun minuteOfDay(c: Calendar) = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)

@Composable
private fun SetupCard(first: Boolean, done: () -> Unit) {
    Column(Modifier.fillMaxWidth().cardSurface().padding(Space.l).testTag("todaySetup"), verticalArrangement = Arrangement.spacedBy(Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (first) "نرتب لك يومك" else "تخصيص يومي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        Occasions.upcoming(now).forEach { add(Due(it.days, it.title, 3, "hijri")) }
    }.sortedBy { it.days }.take(8)
    if (dues.isEmpty()) return
    Section {
        SectionTitle("مواعيدك القادمة", "رواتب ومناسبات ووثائق", icon = Icons.Rounded.Event, color = Color(0xFF14936B))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("dates")) {
            dues.forEach { d ->
                val warn = d.tone == 2
                Column(
                    Modifier.width(112.dp).clip(RoundedCornerShape(18.dp)).background(if (warn) toneSoft(Color(0xFFB0600A)) else tokens.surface)
                        .border(1.dp, if (warn) Color(0xFFB0600A).copy(alpha = .35f) else tokens.border, RoundedCornerShape(18.dp))
                        .clickable(enabled = d.tool != null) { d.tool?.let(open) }.padding(vertical = Space.m, horizontal = Space.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (d.days == 0L) "اليوم" else "${d.days}", fontSize = if (d.days == 0L) 18.sp else 24.sp, fontWeight = FontWeight.Bold,
                        color = when { warn -> if (tokens.dark) Color(0xFFF2B01E) else Color(0xFFB0600A); d.tone == 3 -> if (tokens.dark) Color(0xFF5FD3AE) else Color(0xFF0E7A57); else -> tokens.accent })
                    if (d.days > 0) Text(if (d.days in 3..10) "أيام" else "يوما", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
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
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(toneSoft(Color(0xFF0E7490))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.TaskAlt, null, tint = tone(Color(0xFF0E7490)), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.m))
            Text("مهامك اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (tasks.isNotEmpty()) Text("$doneCount من ${tasks.size}", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
        }
        if (tasks.isEmpty() && homework.isEmpty()) {
            Text("ما عندك مهام اليوم. اكتب أول مهمة واضغط إضافة.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
        }
        var draft by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            OutlinedTextField(draft, { draft = it }, placeholder = { Text("أضف مهمة لليوم") }, singleLine = true, shape = RoundedCornerShape(Radius.m),
                modifier = Modifier.weight(1f).testTag("quickTask"))
            FilledIconButton(onClick = {
                val title = draft.trim(); if (title.isEmpty()) return@FilledIconButton
                Store.saveList("tasks", listOf(JSONObject().put("id", System.currentTimeMillis()).put("title", title).put("due", endOfDay).put("done", false)) + Store.list("tasks"))
                draft = ""; changed()
            }, modifier = Modifier.size(52.dp).testTag("quickTaskAdd").semantics { contentDescription = "إضافة المهمة" }) { Icon(Icons.Rounded.Add, null) }
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
            Triple("اختباراتك", q?.let { "آخر اختبار منشور: ${it.optString("title")}" } ?: "أنشئ اختبارا وانشره برابط، وتصلك النتائج هنا",
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

// ---- Home layout pieces -------------------------------------------------------------------------

/** Section header in the card style: a coloured icon chip, the title and an optional pill action. */
@Composable
private fun SectionTitle(title: String, sub: String? = null, action: Pair<String, () -> Unit>? = null, icon: ImageVector = Icons.Rounded.Dashboard, color: Color = Color(0xFF3346D3)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(toneSoft(color)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tone(color), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 1)
        }
        if (action != null) Text(action.first, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tokens.accent,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(tokens.accentSoft).clickable(onClick = action.second).heightIn(min = 36.dp).padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

/** A home section: one white card with its header inside. */
@Composable
private fun Section(tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().cardSurface().padding(Space.l).let { if (tag != null) it.testTag(tag) else it }, verticalArrangement = Arrangement.spacedBy(Space.m), content = content)
}

@Composable
private fun TopBar(now: Calendar, openMe: () -> Unit, customise: () -> Unit) {
    val h = Hijri.toHijri(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    Row(Modifier.fillMaxWidth().padding(top = Space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(tokens.accentSoft).clickable(onClick = openMe).testTag("todayMe").semantics { contentDescription = "حسابي" },
            contentAlignment = Alignment.Center,
        ) {
            val n = Prefs.name.trim()
            if (n.isNotEmpty()) Text(n.take(1), color = tokens.accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            else Icon(Icons.Rounded.Person, null, tint = tokens.accent)
        }
        Column(Modifier.weight(1f)) {
            Text(greeting(now), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${Hijri.days[now.get(Calendar.DAY_OF_WEEK) - 1]} ${h.d} ${Hijri.months[h.m - 1]}، ${now.get(Calendar.DAY_OF_MONTH)} ${Hijri.gMonths[now.get(Calendar.MONTH)]}",
                style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 1)
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(tokens.surface).border(1.dp, tokens.border, CircleShape).clickable(onClick = customise)
                .testTag("todaySettings").semantics { contentDescription = "تخصيص يومي" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Tune, null, tint = tokens.accent, modifier = Modifier.size(22.dp)) }
    }
}

/** One place to ask for anything, with three suggestions that change with the time of day. */
@Composable
private fun CommandBar(now: Calendar, openAssistant: () -> Unit, open: (String) -> Unit) {
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val ideas = when {
        hour in 5..11 -> listOf("scan", "img_pdf", "translate")
        hour in 12..17 -> listOf("pdf_merge", "pdf_compress", "invoice")
        else -> listOf("tasks", "budget", "notes")
    }.mapNotNull { Tools.byId[it] }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(tokens.surface).border(1.5.dp, tokens.accent.copy(alpha = .35f), RoundedCornerShape(Radius.l))
                .clickable(onClick = openAssistant).heightIn(min = 58.dp).padding(horizontal = Space.l).testTag("todayAssistant"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tokens.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = tokens.onAccent, modifier = Modifier.size(18.dp))
            }
            Text("وش تبي تسوي اليوم؟", style = MaterialTheme.typography.bodyLarge, color = tokens.textSecondary, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            ideas.forEach { t ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(toneSoft(Tools.colorOf(t))).clickable { open(t.id) }.heightIn(min = 40.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(t.icon, null, tint = tone(Tools.colorOf(t)), modifier = Modifier.size(16.dp))
                    Text(t.title, style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
                }
            }
        }
    }
}

/** Eight shortcuts: favourites first, then what fits the person's role. */
@Composable
private fun QuickGrid(open: (String) -> Unit, openTools: () -> Unit) {
    val base = Daily.roles.flatMap { Tools.byRole[it].orEmpty() }
    val ids = (Store.favorites.toList() + base + listOf("scan", "img_pdf", "pdf_merge", "pdf_compress", "translate", "salary", "invoice", "qr_make")).distinct()
    val tools = ids.mapNotNull { Tools.byId[it] }.take(8)
    Section {
        SectionTitle("اختصاراتك", if (Store.favorites.isEmpty()) "ثبت أدواتك بالنجمة لتظهر هنا" else null, "كل الأدوات" to openTools, Icons.Rounded.Bolt, Color(0xFF3346D3))
        Column(Modifier.fillMaxWidth().testTag("quickGrid"), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            tools.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { t ->
                        val c = Tools.colorOf(t)
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { open(t.id) }.padding(vertical = 6.dp).testTag("quick-${t.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(toneSoft(c)), contentAlignment = Alignment.Center) {
                                Icon(t.icon, null, tint = tone(c), modifier = Modifier.size(26.dp))
                            }
                            Text(t.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minLines = 2, color = tokens.textPrimary)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RolePrompt() {
    Column(Modifier.fillMaxWidth().cardSurface().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("وش يصفك؟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("نعرض لك أدوات دورك ومواعيده هنا", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            listOf("student" to "طالب", "teacher" to "معلم", "employee" to "موظف", "business" to "صاحب منشأة").forEach { (id, label) ->
                Text(label, style = MaterialTheme.typography.labelLarge, color = tokens.accent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, tokens.accent.copy(alpha = .4f), RoundedCornerShape(50)).clickable { Daily.toggleRole(id) }
                        .heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 10.dp).testTag("pick-$id"))
            }
        }
    }
}

/** Pick up where you left off: the last three things done in the app. */
@Composable
private fun ContinueCard(open: (String) -> Unit) {
    val items = Store.recents.take(3).mapNotNull { r -> Tools.byId[r.tool]?.let { it to r } }
    if (items.isEmpty()) return
    Section("continue") {
        SectionTitle("أكمل من حيث توقفت", icon = Icons.Rounded.History, color = Color(0xFF7A4FD0))
        items.forEach { (t, r) -> ActivityRow(t.icon, Tools.colorOf(t), t.title, r.detail, ago(r.time), "فتح") { open(t.id) } }
    }
}

/** Every space of the app, one tap away: the categories as coloured tiles. */
@Composable
private fun SpacesGrid(openCat: (String) -> Unit) {
    Section {
        SectionTitle("مساحاتك", "${Tools.all.size} أداة في ${Tools.categories.size} مساحات", icon = Icons.Rounded.GridView, color = Color(0xFFC2410C))
        Tools.categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { c ->
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(Radius.m)).background(toneSoft(c.color)).clickable { openCat(c.id) }
                            .heightIn(min = 64.dp).padding(horizontal = Space.m, vertical = Space.s).testTag("space-${c.id}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(tokens.surface), contentAlignment = Alignment.Center) {
                            Icon(c.icon, null, tint = tone(c.color), modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, color = tokens.textPrimary)
                            Text("${Tools.inCat(c.id).size} أداة", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One tool you haven't tried, a different one each day. */
@Composable
private fun ToolOfDay(now: Calendar, open: (String) -> Unit) {
    val pool = Tools.all.filter { it.id !in Store.favorites && (Store.usage[it.id] ?: 0) == 0 }.ifEmpty { Tools.all }
    val t = pool[now.get(Calendar.DAY_OF_YEAR) % pool.size]
    val c = Tools.colorOf(t)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).border(1.dp, tokens.border, RoundedCornerShape(Radius.l)).clickable { open(t.id) }.padding(Space.l).testTag("toolOfDay"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(toneSoft(c)), contentAlignment = Alignment.Center) { Icon(t.icon, null, tint = tone(c)) }
        Column(Modifier.weight(1f)) {
            Text("جرب اليوم", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
            Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(t.sub, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2)
        }
    }
}

// ---- Daily widgets ------------------------------------------------------------------------------

/** The coming seven days at a glance; a dot marks a day with a task, exam, pay day or occasion. */
@Composable
private fun WeekStrip(now: Calendar, open: (String) -> Unit) {
    val zone = TimeZone.getTimeZone("Asia/Riyadh")
    fun dayStart(off: Int) = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, off); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val tasks = Store.list("tasks").filter { !it.optBoolean("done") }.map { it.optLong("due") }
    val exams = Store.list("exams").map { it.optLong("at") }
    val pays = Daily.payRules().map { PayDays.next(it, now).first }
    val occ = Occasions.upcoming(now, 7).map { it.days }
    var sel by remember { mutableIntStateOf(0) }
    Section("week") {
        SectionTitle("أسبوعك", "اضغط على يوم لتشوف ما فيه", icon = Icons.Rounded.CalendarMonth, color = Color(0xFF0E7490))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..6).forEach { i ->
                val d = dayStart(i); val end = d.timeInMillis + 86_400_000L
                val busy = tasks.any { it in d.timeInMillis until end } || exams.any { it in d.timeInMillis until end } || i in pays || i.toLong() in occ
                val on = i == sel
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) tokens.accent else Color.Transparent).clickable { sel = i }.padding(vertical = 8.dp).testTag("day-$i"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(Hijri.days[d.get(Calendar.DAY_OF_WEEK) - 1].take(3), style = MaterialTheme.typography.labelSmall, color = if (on) tokens.onAccent else tokens.textSecondary, maxLines = 1)
                    Text("${d.get(Calendar.DAY_OF_MONTH)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (on) tokens.onAccent else tokens.textPrimary)
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (busy) (if (on) tokens.onAccent else tokens.accent) else Color.Transparent))
                }
            }
        }
        val d = dayStart(sel); val end = d.timeInMillis + 86_400_000L
        val lines = buildList {
            Store.list("tasks").filter { !it.optBoolean("done") && it.optLong("due") in d.timeInMillis until end }.forEach { add("مهمة: " + it.optString("title")) }
            Store.list("exams").filter { it.optLong("at") in d.timeInMillis until end }.forEach { add("اختبار " + it.optString("subject")) }
            Daily.payRules().filter { PayDays.next(it, now).first == sel }.forEach { add(it.title) }
            Occasions.upcoming(now, 7).filter { it.days == sel.toLong() }.forEach { add(it.title) }
        }
        Text(if (lines.isEmpty()) "يوم فاضي، ما فيه مواعيد" else lines.take(4).joinToString("\n"), style = MaterialTheme.typography.bodyMedium,
            color = if (lines.isEmpty()) tokens.textSecondary else tokens.textPrimary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

/** This month's spending against income, and how much is left per day until the next salary. */
@Composable
private fun BudgetCard(now: Calendar, open: (String) -> Unit) {
    val income = Store.obj("budget").optString("income").toDoubleOrNull() ?: 0.0
    val spent = thisMonth(Store.list("expenses")).sumOf { it.optString("amount").toDoubleOrNull() ?: 0.0 }
    Section("budgetCard") {
        SectionTitle("مصروف الشهر", if (income > 0) "من دخل ${fmt(income)} ريال" else "تابع مصروفك قبل يخلص الراتب", "سجل مصروف" to { open("expenses") }, Icons.Rounded.AccountBalanceWallet, Color(0xFFB45309))
        if (income <= 0) {
            Text("حدد دخلك الشهري مرة وحدة، ونحسب لك كم يبقى لك كل يوم حتى الراتب.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { open("budget") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("حدد دخلك") }
        } else {
            val left = income - spent
            val days = Daily.payRules().firstOrNull()?.let { PayDays.next(it, now).first.coerceAtLeast(1) } ?: 30
            LinearProgressIndicator(progress = { (spent / income).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                color = if (left < 0) Color(0xFFC0392B) else Color(0xFFB45309), trackColor = toneSoft(Color(0xFFB45309)))
            Row(Modifier.fillMaxWidth()) {
                Stat("صرفت", "${fmt(spent)} ريال", Modifier.weight(1f)); Stat("باقي", "${fmt(left)} ريال", Modifier.weight(1f))
                Stat("لكل يوم", if (left > 0) "${fmt(left / days)} ريال" else "0", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, m: Modifier) {
    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
    }
}

private fun fmt(v: Double) = if (v == Math.floor(v) && kotlin.math.abs(v) < 1e9) "%,d".format(java.util.Locale.US, v.toLong()) else "%,.2f".format(java.util.Locale.US, v)

/** Saudi VAT (15%) both ways, right on the home screen. */
@Composable
private fun VatCard() {
    var amount by remember { mutableStateOf("") }
    val v = amount.toDoubleOrNull()
    Section("vatCard") {
        SectionTitle("حاسبة الضريبة", "ضريبة القيمة المضافة 15%", icon = Icons.Rounded.Calculate, color = Color(0xFF3346D3))
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, placeholder = { Text("اكتب المبلغ") }, singleLine = true, shape = RoundedCornerShape(Radius.m),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text("ريال") }, modifier = Modifier.fillMaxWidth().testTag("vatAmount"))
        if (v != null && v > 0) Row(Modifier.fillMaxWidth()) {
            Stat("مع الضريبة", fmt(v * 1.15), Modifier.weight(1f)); Stat("الضريبة", fmt(v * .15), Modifier.weight(1f)); Stat("قبل الضريبة لو شامل", fmt(v / 1.15), Modifier.weight(1f))
        }
    }
}

/** Friday: a reminder to read Surat al-Kahf, opening it in آيات تتلى. */
@Composable
private fun FridayCard() {
    val uri = LocalUriHandler.current
    Section("fridayCard") {
        SectionTitle("جمعة مباركة", "سورة الكهف والصلاة على النبي ﷺ", icon = Icons.Rounded.AutoStories, color = Color(0xFF14936B))
        Button(onClick = { uri.openUri("https://ayattla.com/surah/18-al-kahf") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("اقرأ سورة الكهف في آيات تتلى") }
    }
}

/** The latest note and a quick way to jot a new one. */
@Composable
private fun NoteCard(open: (String) -> Unit, changed: () -> Unit) {
    val last = Store.list("notes").maxByOrNull { it.optLong("id") }
    var draft by remember { mutableStateOf("") }
    Section("noteCard") {
        SectionTitle("ملاحظة سريعة", last?.let { "آخر ملاحظة: " + it.optString("title") }, "كل الملاحظات" to { open("notes") }, Icons.Rounded.EditNote, Color(0xFF7A4FD0))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            OutlinedTextField(draft, { draft = it }, placeholder = { Text("اكتب فكرة قبل تنساها") }, shape = RoundedCornerShape(Radius.m), maxLines = 3, modifier = Modifier.weight(1f).testTag("quickNote"))
            FilledIconButton(onClick = {
                val text = draft.trim(); if (text.isEmpty()) return@FilledIconButton
                Store.saveList("notes", Store.list("notes") + JSONObject().put("id", System.currentTimeMillis()).put("title", text.lineSequence().first().take(40)).put("body", text))
                draft = ""; changed()
            }, modifier = Modifier.size(52.dp).testTag("quickNoteAdd").semantics { contentDescription = "حفظ الملاحظة" }) { Icon(Icons.Rounded.Check, null) }
        }
    }
}
