package com.muyassir.app.tools

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.Calc
import com.muyassir.app.engine.DocBuilder
import com.muyassir.app.engine.DocBuilder.Block
import com.muyassir.app.ui.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Date

@Composable
fun GpaTool(t: Tool, back: () -> Unit) {
    var scale5 by remember { mutableIntStateOf(0) }
    val rows = remember { mutableStateListOf(Triple("", "A", "3"), Triple("", "B+", "3"), Triple("", "A+", "2")) }
    var prevGpa by remember { mutableStateOf("") }; var prevHours by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        Panel { Segmented(listOf("من 5", "من 4"), scale5) { scale5 = it } }
        Panel("مواد الفصل") {
            rows.forEachIndexed { i, (name, grade, hours) ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField2("المادة", name, { rows[i] = Triple(it, grade, hours) }, Modifier.weight(1.4f))
                    Box(Modifier.weight(1f)) { Picker("التقدير", Calc.letters, grade, { it }) { rows[i] = Triple(name, it, hours) } }
                    NumField("ساعات", hours, { rows[i] = Triple(name, grade, it) }, Modifier.weight(0.8f))
                    IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Rounded.Close, "حذف") }
                }
            }
            SecondaryButton("إضافة مادة", Icons.Rounded.Add) { rows.add(Triple("", "A", "3")) }
        }
        Panel("المعدل السابق (للتراكمي)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("المعدل التراكمي", prevGpa, { prevGpa = it }, Modifier.weight(1f)); NumField("الساعات المجتازة", prevHours, { prevHours = it }, Modifier.weight(1f))
            }
        }
        val (sem, cum) = Calc.gpa(rows.map { Calc.Course(Calc.points(it.second, scale5 == 0), it.third.num()) }, prevGpa.num(), prevHours.num())
        val max = if (scale5 == 0) 5.0 else 4.0
        fun word(g: Double) = when { g >= max * 0.9 -> "ممتاز مرتفع"; g >= max * 0.75 -> "ممتاز"; g >= max * 0.7 -> "جيد جدًا مرتفع"; g >= max * 0.55 -> "جيد جدًا"; g >= max * 0.5 -> "جيد"; else -> "مقبول" }
        ResultCard("المعدل الفصلي" to "${fmt(sem)} من ${fmt(max, 0)}", listOf("التقدير" to word(sem), "المعدل التراكمي" to fmt(cum), "ساعات الفصل" to fmt(rows.sumOf { it.third.num() })))
        Hint("سُلّم النقاط المستخدم هو الشائع في الجامعات السعودية (A+ = 5، A = 4.75 …). راجع لائحة جامعتك فقد يختلف.")
    }
}

@Composable
fun PercentTool(t: Tool, back: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        Panel {
            Segmented(listOf("الدرجة", "س% من ص", "التغير"), mode) { mode = it }
            when (mode) {
                0 -> { NumField("درجتك", a, { a = it }); NumField("الدرجة الكاملة", b, { b = it }) }
                1 -> { NumField("النسبة %", a, { a = it }); NumField("من العدد", b, { b = it }) }
                else -> { NumField("القيمة القديمة", a, { a = it }); NumField("القيمة الجديدة", b, { b = it }) }
            }
        }
        val x = a.num(); val y = b.num()
        when (mode) {
            0 -> if (y > 0) { val p = x / y * 100; ResultCard("النسبة" to "${fmt(p)}%", listOf("التقدير" to Calc.gradeWord(p), "من 5" to fmt(p / 20), "من 4" to fmt(p / 25))) }
            1 -> if (b.isNotBlank()) ResultCard("الناتج" to fmt(x / 100 * y), listOf("المتبقي" to fmt(y - x / 100 * y)))
            else -> if (x != 0.0) { val c = (y - x) / x * 100; ResultCard(if (c >= 0) "زيادة" to "${fmt(c)}%" else "انخفاض" to "${fmt(-c)}%", listOf("الفرق" to fmt(y - x))) }
        }
    }
}

@Composable
fun TimetableTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val days = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس")
    val periods = 7
    val data = remember { Store.obj("timetable") }
    var tick by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    ToolScreen(t, back) {
        key(tick) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.width(72.dp))
                        (1..periods).forEach { Box(Modifier.width(92.dp), contentAlignment = Alignment.Center) { Text("الحصة $it", style = MaterialTheme.typography.labelMedium) } }
                    }
                    days.forEachIndexed { d, day ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(day, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                            (0 until periods).forEach { p ->
                                val v = data.optString("$d-$p")
                                Box(
                                    Modifier.width(92.dp).height(54.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (v.isBlank()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)).clickable { edit = d to p },
                                    contentAlignment = Alignment.Center,
                                ) { Text(v, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2) }
                            }
                        }
                    }
                }
            }
        }
        SecondaryButton("تصدير PDF للطباعة", Icons.Rounded.PictureAsPdf, Modifier.fillMaxWidth()) {
            val b = DocBuilder(ctx, landscape = true)
            b.add(Block.Title("الجدول الدراسي"))
            b.add(Block.Table(listOf("اليوم") + (1..periods).map { "الحصة $it" }, days.mapIndexed { d, day -> listOf(day) + (0 until periods).map { data.optString("$d-$it") } }))
            Files.share(ctx, listOf(b.save(File(Files.outDir(ctx), "الجدول-الدراسي.pdf"))))
        }
    }
    edit?.let { (d, p) ->
        var v by remember(d, p) { mutableStateOf(data.optString("$d-$p")) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("${days[d]} — الحصة ${p + 1}") },
            text = { TextField2("المادة (وأي ملاحظة)", v, { v = it }) },
            confirmButton = { TextButton(onClick = { data.put("$d-$p", v); Store.saveObj("timetable", data); tick++; edit = null; Store.used(t.id) }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("إلغاء") } })
    }
}

@Composable
fun StudyTimer(t: Tool, back: () -> Unit) {
    var focus by remember { mutableStateOf("25") }; var rest by remember { mutableStateOf("5") }
    var running by remember { mutableStateOf(false) }
    var onBreak by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(25 * 60) }
    var sessions by remember { mutableIntStateOf(Store.obj("timer").optInt("today")) }
    val total = ((if (onBreak) rest else focus).num().coerceAtLeast(1.0) * 60).toInt()
    LaunchedEffect(running, onBreak) {
        while (running && left > 0) { delay(1000); left-- }
        if (running && left == 0) {
            runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90).startTone(ToneGenerator.TONE_PROP_BEEP2, 600) }
            if (!onBreak) { sessions++; Store.saveObj("timer", JSONObject().put("today", sessions)); Store.used(t.id) }
            onBreak = !onBreak; left = ((if (onBreak) rest else focus).num().coerceAtLeast(1.0) * 60).toInt()
        }
    }
    val prog by animateFloatAsState(1f - left / total.toFloat(), label = "p")
    ToolScreen(t, back) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.outlineVariant; val col = if (onBreak) Brand.Mint else MaterialTheme.colorScheme.primary
            Canvas(Modifier.size(230.dp)) {
                drawArc(track, 0f, 360f, false, style = Stroke(22f), size = Size(size.width, size.height))
                drawArc(col, -90f, 360f * prog, false, style = Stroke(22f, cap = StrokeCap.Round), topLeft = Offset.Zero, size = Size(size.width, size.height))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(java.util.Locale.US, "%02d:%02d", left / 60, left % 60), style = MaterialTheme.typography.displaySmall)
                Text(if (onBreak) "استراحة" else "تركيز", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (running) "إيقاف مؤقت" else "ابدأ", if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f)) { running = !running }
            SecondaryButton("إعادة", Icons.Rounded.Refresh) { running = false; onBreak = false; left = (focus.num().coerceAtLeast(1.0) * 60).toInt() }
        }
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("التركيز", focus, { focus = it; if (!running && !onBreak) left = (it.num().coerceAtLeast(1.0) * 60).toInt() }, Modifier.weight(1f), "دقيقة")
                NumField("الاستراحة", rest, { rest = it }, Modifier.weight(1f), "دقيقة")
            }
            LabelValue("جلسات مكتملة", "$sessions", true)
        }
        Hint("أبقِ التطبيق مفتوحًا أثناء الجلسة؛ يصدر تنبيه صوتي عند انتهاء كل فترة.")
    }
}

@Composable
fun FlashcardsTool(t: Tool, back: () -> Unit) {
    var cards by remember { mutableStateOf(Store.list("flashcards")) }
    var front by remember { mutableStateOf("") }; var backText by remember { mutableStateOf("") }
    var studying by remember { mutableStateOf(false) }
    var flipped by remember { mutableStateOf(false) }
    fun save(l: List<JSONObject>) { cards = l.toMutableList(); Store.saveList("flashcards", l) }
    // Leitner: box 1 every session, box 2 every 2nd, box 3 every 4th … — due = lowest box first.
    val due = cards.sortedWith(compareBy({ it.optInt("box", 1) }, { it.optLong("seen") }))
    ToolScreen(t, back) {
        if (!studying) {
            Panel("بطاقة جديدة") {
                TextField2("الوجه (سؤال أو كلمة)", front, { front = it })
                TextField2("الظهر (الإجابة)", backText, { backText = it }, lines = 2)
                PrimaryButton("إضافة البطاقة", Icons.Rounded.Add, enabled = front.isNotBlank() && backText.isNotBlank()) {
                    save(cards + JSONObject().put("id", System.currentTimeMillis()).put("f", front).put("b", backText).put("box", 1)); front = ""; backText = ""
                }
            }
            if (cards.isNotEmpty()) {
                PrimaryButton("ابدأ المراجعة (${cards.size})", Icons.Rounded.PlayArrow) { studying = true; flipped = false }
                Panel("البطاقات") {
                    cards.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(c.optString("f"), style = MaterialTheme.typography.titleSmall); Text("الصندوق ${c.optInt("box", 1)} من 5", style = MaterialTheme.typography.bodySmall) }
                            IconButton(onClick = { save(cards.filter { it.optLong("id") != c.optLong("id") }) }) { Icon(Icons.Rounded.DeleteOutline, "حذف") }
                        }
                    }
                }
            } else EmptyState(t.icon, "لا توجد بطاقات", "أضف كلمات أو أسئلة لتراجعها بطريقة الصناديق المتباعدة.")
        } else {
            val c = due.firstOrNull()
            if (c == null) { studying = false } else {
                Box(
                    Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (flipped) Brand.Ink else MaterialTheme.colorScheme.primary).clickable { flipped = !flipped }.padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (flipped) c.optString("b") else c.optString("f"), style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center)
                }
                Text(if (flipped) "هل عرفت الإجابة؟" else "اضغط البطاقة لرؤية الإجابة", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                if (flipped) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("لم أعرفها", Icons.Rounded.Close, Modifier.weight(1f)) {
                        save(cards.map { if (it.optLong("id") == c.optLong("id")) JSONObject(it.toString()).put("box", 1).put("seen", System.currentTimeMillis()) else it }); flipped = false
                    }
                    PrimaryButton("عرفتها", Icons.Rounded.Check, modifier = Modifier.weight(1f)) {
                        save(cards.map { if (it.optLong("id") == c.optLong("id")) JSONObject(it.toString()).put("box", (it.optInt("box", 1) + 1).coerceAtMost(5)).put("seen", System.currentTimeMillis()) else it })
                        flipped = false; Store.used(t.id)
                    }
                }
                TextButton(onClick = { studying = false }) { Text("إنهاء المراجعة") }
            }
        }
    }
}

// ------------------------------------------------------------- printable documents (quiz, worksheet, certificate)

@Composable
fun QuizMaker(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("اختبار قصير") }; var school by remember { mutableStateOf("") }; var subject by remember { mutableStateOf("") }
    val qs = remember { mutableStateListOf<JSONObject>() }
    var withKey by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        Panel { TextField2("عنوان الاختبار", title, { title = it }); TextField2("المدرسة/الجهة", school, { school = it }); TextField2("المادة والصف", subject, { subject = it }) }
        qs.forEachIndexed { i, q ->
            var tick by remember { mutableIntStateOf(0) }
            key(tick) {
                Panel("السؤال ${i + 1}") {
                    Segmented(listOf("اختيار من متعدد", "صح وخطأ", "مقالي"), q.optInt("type")) { q.put("type", it); tick++ }
                    TextField2("نص السؤال", q.optString("q"), { q.put("q", it); tick++ }, lines = 2)
                    when (q.optInt("type")) {
                        0 -> (0 until 4).forEach { k ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(q.optInt("ans") == k, { q.put("ans", k); tick++ })
                                TextField2("الخيار ${"أبجد"[k]}", q.optString("o$k"), { q.put("o$k", it); tick++ })
                            }
                        }
                        1 -> Segmented(listOf("صح", "خطأ"), q.optInt("ans")) { q.put("ans", it); tick++ }
                        else -> NumField("عدد أسطر الإجابة", q.optString("lines", "3"), { q.put("lines", it); tick++ })
                    }
                    NumField("الدرجة", q.optString("mark", "1"), { q.put("mark", it); tick++ })
                    TextButton(onClick = { qs.removeAt(i) }) { Text("حذف السؤال") }
                }
            }
        }
        SecondaryButton("إضافة سؤال", Icons.Rounded.Add, Modifier.fillMaxWidth()) { qs.add(JSONObject().put("type", 0).put("mark", "1")) }
        SwitchRow("إنشاء نموذج إجابة منفصل", withKey) { withKey = it }
        PrimaryButton("إنشاء PDF", Icons.Rounded.PictureAsPdf, enabled = qs.isNotEmpty()) {
            val out = Files.outDir(ctx)
            fun build(key: Boolean): File {
                val b = DocBuilder(ctx)
                b.add(Block.Title(title + if (key) " — نموذج الإجابة" else "", listOf(school, subject).filter { it.isNotBlank() }.joinToString(" • ")))
                if (!key) b.add(Block.KeyValues(listOf("اسم الطالب" to "........................................", "الدرجة" to "......... / ${fmt(qs.sumOf { it.optString("mark", "1").num() })}")))
                qs.forEachIndexed { i, q ->
                    b.add(Block.Para("${i + 1}) ${q.optString("q")}   (${fmt(q.optString("mark", "1").num())} درجة)", 12f, bold = true))
                    when (q.optInt("type")) {
                        0 -> (0 until 4).forEach { k -> val o = q.optString("o$k"); if (o.isNotBlank()) b.add(Block.Para("   ${if (key && q.optInt("ans") == k) "✔" else "○"} ${"أبجد"[k]}) $o")) }
                        1 -> b.add(Block.Para(if (key) "   الإجابة: ${if (q.optInt("ans") == 0) "صح" else "خطأ"}" else "   (     ) صح          (     ) خطأ"))
                        else -> if (!key) b.add(Block.Lines(q.optString("lines", "3").num().toInt().coerceIn(1, 15)))
                    }
                    b.add(Block.Space(6f))
                }
                return b.save(File(out, if (key) "$title-الإجابة.pdf" else "$title.pdf"))
            }
            result = listOfNotNull(build(false), if (withKey) build(true) else null); Store.used(t.id, title)
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

@Composable
fun WorksheetTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("ورقة عمل") }; var subject by remember { mutableStateOf("") }; var goal by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("1) \n2) \n3) ") }; var lines by remember { mutableStateOf("3") }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        Panel {
            TextField2("العنوان", title, { title = it }); TextField2("المادة والصف", subject, { subject = it }); TextField2("الهدف من الدرس", goal, { goal = it })
            TextField2("الأنشطة/الأسئلة (سطر لكل سؤال)", body, { body = it }, lines = 6)
            NumField("أسطر إجابة بعد كل سؤال", lines, { lines = it })
        }
        PrimaryButton("إنشاء PDF", Icons.Rounded.PictureAsPdf) {
            val b = DocBuilder(ctx)
            b.add(Block.Title(title, subject)); b.add(Block.KeyValues(listOf("الاسم" to ".................................", "التاريخ" to "....../....../........")))
            if (goal.isNotBlank()) { b.add(Block.Heading("الهدف")); b.add(Block.Para(goal)) }
            b.add(Block.Heading("الأنشطة"))
            body.lines().filter { it.isNotBlank() }.forEach { b.add(Block.Para(it, 12f)); b.add(Block.Lines(lines.num().toInt().coerceIn(0, 12))); b.add(Block.Space(6f)) }
            result = listOf(b.save(File(Files.outDir(ctx), "$title.pdf"))); Store.used(t.id, title)
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

@Composable
fun CertificateTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val kinds = listOf("شهادة شكر وتقدير", "شهادة تفوق", "شهادة إتمام", "شهادة حضور")
    var kind by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }; var signer by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var color by remember { mutableIntStateOf(0) }
    val colors = listOf("أزرق" to 0xFF2F4BFF.toInt(), "ذهبي" to 0xFFB8860B.toInt(), "أخضر" to 0xFF15803D.toInt(), "عنابي" to 0xFF7F1D3A.toInt())
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        Panel {
            Picker("النوع", kinds.indices.toList(), kind, { kinds[it] }) { kind = it }
            TextField2("اسم المكرَّم", name, { name = it })
            TextField2("السبب أو الإنجاز", reason, { reason = it }, lines = 2)
            TextField2("الجهة المانحة", org, { org = it }); TextField2("اسم الموقّع وصفته", signer, { signer = it })
            DateField("التاريخ", date, false) { date = it }
            ChipRow { colors.forEachIndexed { i, (n, _) -> Chip(n, i == color) { color = i } } }
        }
        PrimaryButton("إنشاء الشهادة", Icons.Rounded.WorkspacePremium, enabled = name.isNotBlank()) {
            val b = DocBuilder(ctx, landscape = true).apply { border = colors[color].second; footer = null }
            b.add(Block.Space(30f))
            b.add(Block.Para(org, 14f, center = true, color = DocBuilder.MUTED))
            b.add(Block.Para(kinds[kind], 34f, bold = true, center = true, color = colors[color].second))
            b.add(Block.Space(10f))
            b.add(Block.Para("يسرّنا أن نقدّم هذه الشهادة إلى", 14f, center = true))
            b.add(Block.Para(name, 28f, bold = true, center = true))
            if (reason.isNotBlank()) b.add(Block.Para(reason, 14f, center = true))
            b.add(Block.Space(30f))
            b.add(Block.Para("${dayFmt.format(Date(date))}\n$signer\n...............................", 12f, center = true, color = DocBuilder.MUTED))
            result = listOf(b.save(File(Files.outDir(ctx), "شهادة-$name.pdf"))); Store.used(t.id, name)
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

@Composable
fun CalendarTool(t: Tool, back: () -> Unit) {
    val cal = remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    var selected by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }
    data class Ev(val title: String, val at: Long, val src: String)
    val events = remember {
        Store.list("tasks").filter { it.optLong("due") > 0 }.map { Ev(it.optString("title"), it.optLong("due"), "مهمة") } +
            Store.list("exams").map { Ev(it.optString("subject"), it.optLong("at"), "اختبار") } +
            Store.list("homework").filter { it.optLong("due") > 0 }.map { Ev(it.optString("title"), it.optLong("due"), "واجب") } +
            Store.list("documents").map { Ev(it.optString("name"), it.optLong("expiry"), "وثيقة") } +
            Store.list("reminders").map { Ev(it.optString("title"), it.optLong("at"), "تذكير") }
    }
    fun sameDay(a: Long, y: Int, m: Int, d: Int) = Calendar.getInstance().apply { timeInMillis = a }.let { it.get(Calendar.YEAR) == y && it.get(Calendar.MONTH) == m && it.get(Calendar.DAY_OF_MONTH) == d }
    ToolScreen(t, back) {
        val c = cal.value; val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH)
        val first = c.get(Calendar.DAY_OF_WEEK) - 1; val len = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { cal.value = (c.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }) { Icon(Icons.Rounded.ChevronRight, "السابق") }
            Text("${com.muyassir.app.engine.Hijri.gMonths[m]} $y", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { cal.value = (c.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }) { Icon(Icons.Rounded.ChevronLeft, "التالي") }
        }
        Row { listOf("أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
        val cells = first + len
        (0 until (cells + 6) / 7).forEach { w ->
            Row {
                (0 until 7).forEach { k ->
                    val d = w * 7 + k - first + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (d in 1..len) {
                            val has = events.any { sameDay(it.at, y, m, d) }
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                    .background(if (d == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface).clickable { selected = d },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$d", color = if (d == selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface)
                                if (has) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(RoundedCornerShape(50)).background(Brand.Saffron))
                            }
                        }
                    }
                }
            }
        }
        val dayEvents = events.filter { sameDay(it.at, y, m, selected) }
        Panel("مواعيد $selected ${com.muyassir.app.engine.Hijri.gMonths[m]}") {
            if (dayEvents.isEmpty()) Text("لا شيء في هذا اليوم. أضف من أدوات المهام والتذكيرات والاختبارات والوثائق.", style = MaterialTheme.typography.bodySmall)
            dayEvents.forEach { LabelValue(it.title, it.src) }
        }
    }
}
