package com.muyassir.app.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Templates
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.DocBuilder
import com.muyassir.app.engine.DocBuilder.Block
import com.muyassir.app.engine.Office
import com.muyassir.app.ui.*
import org.json.JSONObject
import java.io.File

/** Shared vocabulary of the education studio. */
object Edu {
    val types = listOf("اختيار من متعدد", "صح وخطأ", "مقالي", "إكمال الفراغ", "توصيل")
    val stages = listOf("ابتدائي", "متوسط", "ثانوي", "جامعي")
    val levels = listOf("سهل", "متوسط", "صعب")
    const val BLANK = "……"

    /** Marks: blanks in the text are written as ___ or …… */
    fun blanked(s: String) = s.replace(Regex("_{2,}|…{1,}|\\.{4,}"), "..................")

    /** Matching pairs: one "left = right" per line. */
    fun pairs(q: JSONObject) = q.optString("pairs").lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.filter { it.first.isNotEmpty() }

    /** A stable shuffle (same order in the student copy and the key). */
    fun <T> shuffled(l: List<T>, seed: Int): List<T> = l.shuffled(java.util.Random(seed.toLong()))

    /** Reads a question bank from Excel/CSV: السؤال، النوع، أ، ب، ج، د، الإجابة، الدرجة، المادة، الصف، الدرس، الصعوبة. */
    fun importRows(rows: List<List<String>>): List<JSONObject> {
        if (rows.isEmpty()) return emptyList()
        val head = rows.first().map { it.trim() }
        val hasHead = head.any { it in setOf("السؤال", "question", "Question") }
        fun col(r: List<String>, name: String, idx: Int) = (if (hasHead) head.indexOf(name).takeIf { it >= 0 } else idx)?.let { r.getOrNull(it) }?.trim().orEmpty()
        return (if (hasHead) rows.drop(1) else rows).mapNotNull { r ->
            val q = col(r, "السؤال", 0); if (q.isBlank()) return@mapNotNull null
            val opts = listOf("أ", "ب", "ج", "د").mapIndexed { k, n -> col(r, n, 2 + k) }
            val typeName = col(r, "النوع", 1)
            val type = Edu.types.indexOf(typeName).takeIf { it >= 0 } ?: if (opts.count { it.isNotBlank() } >= 2) 0 else if (q.contains("__") || q.contains("…")) 3 else 2
            val ansRaw = col(r, "الإجابة", 6)
            val o = JSONObject().put("q", q).put("type", type).put("mark", col(r, "الدرجة", 7).ifBlank { "1" }).put("lines", "3")
                .put("subject", col(r, "المادة", 8)).put("grade", col(r, "الصف", 9)).put("lesson", col(r, "الدرس", 10)).put("level", col(r, "الصعوبة", 11))
            opts.forEachIndexed { k, s -> if (s.isNotBlank()) o.put("o$k", s) }
            when (type) {
                0 -> o.put("ans", listOf("أ", "ب", "ج", "د").indexOf(ansRaw).takeIf { it >= 0 } ?: opts.indexOf(ansRaw).coerceAtLeast(0))
                1 -> o.put("ans", if (ansRaw.startsWith("خ") || ansRaw.equals("false", true)) 1 else 0)
                else -> o.put("answer", ansRaw)
            }
            o
        }
    }
}

@Composable
fun QuizMaker(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val pre = remember { com.muyassir.app.core.Assistant.takePrefill() }
    val meta = remember { Store.obj("quiz_meta").apply { listOf("grade", "subject").forEach { k -> pre.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) } } } }
    var title by remember { mutableStateOf(meta.optString("title", "اختبار قصير")) }; var school by remember { mutableStateOf(meta.optString("school")) }
    var stage by remember { mutableIntStateOf(meta.optInt("stage", 0)) }; var grade by remember { mutableStateOf(meta.optString("grade")) }
    var subject by remember { mutableStateOf(meta.optString("subject")) }; var lesson by remember { mutableStateOf(meta.optString("lesson")) }
    var level by remember { mutableIntStateOf(meta.optInt("level", 1)) }; var minutes by remember { mutableStateOf(meta.optString("minutes")) }
    var english by remember { mutableStateOf(false) }
    val qs = remember { mutableStateListOf<JSONObject>().apply { addAll(Store.list("quiz_draft")); Store.saveList("quiz_draft", emptyList()) } }
    var withKey by remember { mutableStateOf(true) }; var word by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    fun saveMeta() = Store.saveObj("quiz_meta", JSONObject().put("title", title).put("school", school).put("stage", stage).put("grade", grade).put("subject", subject).put("lesson", lesson).put("level", level).put("minutes", minutes))
    ToolScreen(t, back) {
        Panel("إعدادات الاختبار") {
            TextField2("عنوان الاختبار", title, { title = it }); TextField2("المدرسة/الجهة", school, { school = it })
            Segmented(Edu.stages, stage) { stage = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { TextField2("الصف", grade, { grade = it }) }
                Box(Modifier.weight(1f)) { TextField2("المادة", subject, { subject = it }) }
            }
            TextField2("الدرس أو الوحدة", lesson, { lesson = it })
            Text("مستوى الصعوبة", style = MaterialTheme.typography.labelLarge); Segmented(Edu.levels, level) { level = it }
            NumField("مدة الاختبار", minutes, { minutes = it }, suffix = "دقيقة")
            SwitchRow("اختبار باللغة الإنجليزية (من اليسار لليمين)", english) { english = it }
        }
        qs.forEachIndexed { i, q ->
            var tick by remember { mutableIntStateOf(0) }
            key(tick, q) {
                Panel("السؤال ${i + 1}") {
                    Picker("نوع السؤال", Edu.types.indices.toList(), q.optInt("type"), { Edu.types[it] }) { q.put("type", it); tick++ }
                    TextField2(if (q.optInt("type") == 3) "نص السؤال (اكتب ___ مكان الفراغ)" else "نص السؤال", q.optString("q"), { q.put("q", it); tick++ }, lines = 2)
                    when (q.optInt("type")) {
                        0 -> (0 until 4).forEach { k ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(q.optInt("ans") == k, { q.put("ans", k); tick++ })
                                TextField2("الخيار ${"أبجد"[k]}", q.optString("o$k"), { q.put("o$k", it); tick++ })
                            }
                        }
                        1 -> Segmented(listOf("صح", "خطأ"), q.optInt("ans")) { q.put("ans", it); tick++ }
                        2 -> { NumField("عدد أسطر الإجابة", q.optString("lines", "3"), { q.put("lines", it); tick++ }); TextField2("معيار التصحيح (يظهر في نموذج الإجابة)", q.optString("answer"), { q.put("answer", it); tick++ }, lines = 2) }
                        3 -> TextField2("الإجابة الصحيحة", q.optString("answer"), { q.put("answer", it); tick++ })
                        4 -> TextField2("الأزواج: سطر لكل زوج بصيغة «العمود الأول = ما يقابله»", q.optString("pairs"), { q.put("pairs", it); tick++ }, lines = 4)
                    }
                    NumField("الدرجة", q.optString("mark", "1"), { q.put("mark", it); tick++ })
                    TextButton(onClick = { qs.removeAt(i) }) { Text("حذف السؤال") }
                }
            }
        }
        var addType by remember { mutableIntStateOf(0) }
        Panel {
            Picker("نوع السؤال الجديد", Edu.types.indices.toList(), addType, { Edu.types[it] }) { addType = it }
            SecondaryButton("إضافة سؤال", Icons.Rounded.Add, Modifier.fillMaxWidth()) { qs.add(JSONObject().put("type", addType).put("mark", "1")) }
        }
        QuestionBank(qs, subject, grade, lesson, Edu.levels[level])
        SwitchRow("إنشاء نموذج إجابة منفصل مع معايير التقويم", withKey) { withKey = it }
        SwitchRow("ملف Word قابل للتعديل أيضًا", word) { word = it }
        TemplatePanel()
        PrimaryButton("إنشاء PDF", Icons.Rounded.PictureAsPdf, enabled = qs.isNotEmpty(), modifier = Modifier.testTag("action")) {
            saveMeta()
            val out = Files.outDir(ctx)
            val total = fmt(qs.sumOf { it.optString("mark", "1").num() })
            val L = if (english) mapOf("name" to "Student name", "score" to "Score", "key" to " — Answer key", "t" to "True", "f" to "False", "ans" to "Answer", "mark" to "mark", "match" to "Match each item with its pair", "rubric" to "Marking guide", "info" to "")
                else mapOf("name" to "اسم الطالب", "score" to "الدرجة", "key" to " — نموذج الإجابة", "t" to "صح", "f" to "خطأ", "ans" to "الإجابة", "mark" to "درجة", "match" to "صل كل عنصر بما يناسبه", "rubric" to "معيار التصحيح", "info" to "")
            val letters = if (english) "ABCD" else "أبجد"
            val info = listOfNotNull(
                (if (english) "Level" else "المرحلة") to Edu.stages[stage], grade.takeIf { it.isNotBlank() }?.let { (if (english) "Grade" else "الصف") to it },
                lesson.takeIf { it.isNotBlank() }?.let { (if (english) "Lesson" else "الدرس") to it },
                minutes.takeIf { it.isNotBlank() }?.let { (if (english) "Time" else "الزمن") to (if (english) "$it min" else "$it دقيقة") },
                (if (english) "Difficulty" else "الصعوبة") to Edu.levels[level],
            )
            fun build(key: Boolean): File {
                val b = Templates.apply(ctx, DocBuilder(ctx)).apply { ltr = english }
                b.add(Block.Title(title + if (key) L["key"] else "", listOf(school, subject).filter { it.isNotBlank() }.joinToString(" • ")))
                b.add(Block.KeyValues(info + if (!key) listOf(L["name"]!! to "........................................", L["score"]!! to "......... / $total") else emptyList()))
                qs.forEachIndexed { i, q ->
                    val text = if (q.optInt("type") == 3) Edu.blanked(q.optString("q")) else q.optString("q")
                    b.add(Block.Para("${i + 1}) $text   (${fmt(q.optString("mark", "1").num())} ${L["mark"]})", 12f, bold = true))
                    when (q.optInt("type")) {
                        0 -> (0 until 4).forEach { k -> val o = q.optString("o$k"); if (o.isNotBlank()) b.add(Block.Para("   ${if (key && q.optInt("ans") == k) "✔" else "○"} ${letters[k]}) $o")) }
                        1 -> b.add(Block.Para(if (key) "   ${L["ans"]}: ${if (q.optInt("ans") == 0) L["t"] else L["f"]}" else "   (     ) ${L["t"]}          (     ) ${L["f"]}"))
                        2 -> if (!key) b.add(Block.Lines(q.optString("lines", "3").num().toInt().coerceIn(1, 15))) else q.optString("answer").takeIf { it.isNotBlank() }?.let { b.add(Block.Para("   ${L["rubric"]}: $it")) }
                        3 -> if (key) b.add(Block.Para("   ${L["ans"]}: ${q.optString("answer")}"))
                        4 -> {
                            val ps = Edu.pairs(q); val right = Edu.shuffled(ps.map { it.second }, i + 7)
                            b.add(Block.Table(emptyList(), ps.indices.map { k ->
                                listOf("${k + 1}) ${ps[k].first}", if (key) "${letters.getOrElse(right.indexOf(ps[k].second)) { '-' }}" else "(     )", "${letters.getOrElse(k) { '-' }}) ${right[k]}")
                            }))
                        }
                    }
                    b.add(Block.Space(6f))
                }
                return b.save(File(out, if (key) "$title-الإجابة.pdf" else "$title.pdf"))
            }
            fun docx(): File {
                val p = ArrayList<Office.Para>()
                p += Office.Para.Text(title, 1)
                p += Office.Para.Text((listOf(school, subject) + info.map { "${it.first}: ${it.second}" }).filter { it.isNotBlank() }.joinToString(" • "))
                p += Office.Para.Text("${L["name"]}: ........................    ${L["score"]}: ...... / $total")
                qs.forEachIndexed { i, q ->
                    p += Office.Para.Text("${i + 1}) ${if (q.optInt("type") == 3) Edu.blanked(q.optString("q")) else q.optString("q")}", bold = true)
                    when (q.optInt("type")) {
                        0 -> (0 until 4).forEach { k -> q.optString("o$k").takeIf { it.isNotBlank() }?.let { p += Office.Para.Text("    ${letters[k]}) $it") } }
                        1 -> p += Office.Para.Text("    (     ) ${L["t"]}        (     ) ${L["f"]}")
                        2 -> repeat(q.optString("lines", "3").num().toInt().coerceIn(1, 15)) { p += Office.Para.Text("    ..............................................................") }
                        4 -> { val ps = Edu.pairs(q); val right = Edu.shuffled(ps.map { it.second }, i + 7); p += Office.Para.Table(ps.indices.map { k -> listOf("${k + 1}) ${ps[k].first}", "(     )", "${letters.getOrElse(k) { '-' }}) ${right[k]}") }) }
                    }
                }
                return File(out, "$title.docx").also { Office.writeDocx(it, p) }
            }
            result = listOfNotNull(build(false), if (withKey) build(true) else null, if (word) docx() else null); Store.used(t.id, title)
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

/** The question bank: classified by subject, grade, lesson and difficulty; imported from Excel/CSV; tests built from it. */
@Composable
fun QuestionBank(qs: MutableList<JSONObject>, subject: String, grade: String, lesson: String, level: String) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u ?: return@rememberLauncherForActivityResult
        runCatching {
            val f = Files.toCache(ctx, u)
            val rows = if (Files.name(ctx, u).lowercase().endsWith(".xlsx")) Office.readXlsx(f).firstOrNull()?.second.orEmpty()
                else f.readLines().map { l -> l.split(',', ';', '\t').map { it.trim().trim('"') } }
            val add = Edu.importRows(rows)
            val bank = Store.list("qbank"); val have = bank.map { it.optString("q") }.toSet()
            val fresh = add.filter { it.optString("q") !in have }
            Store.saveList("qbank", bank + fresh); msg = "استُورد ${fresh.size} سؤالًا إلى البنك"
        }.onFailure { msg = "تعذّرت قراءة الملف. استخدم Excel أو CSV بالأعمدة: السؤال، النوع، أ، ب، ج، د، الإجابة، الدرجة، المادة، الصف، الدرس، الصعوبة." }
    }
    Panel("بنك الأسئلة") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("حفظ في البنك", Icons.Rounded.Inventory2, Modifier.weight(1f).testTag("bankSave")) {
                val bank = Store.list("qbank"); val have = bank.map { it.optString("q") }.toSet()
                val add = qs.filter { it.optString("q").isNotBlank() && it.optString("q") !in have }
                    .map { JSONObject(it.toString()).put("subject", subject).put("grade", grade).put("lesson", lesson).put("level", level) }
                Store.saveList("qbank", bank + add); msg = "أُضيف ${add.size} سؤالًا إلى البنك (${bank.size + add.size} إجمالًا)"
            }
            SecondaryButton("من البنك", Icons.Rounded.LibraryAdd, Modifier.weight(1f).testTag("bankOpen")) { show = true }
        }
        SecondaryButton("استيراد أسئلة من Excel أو CSV", Icons.Rounded.UploadFile, Modifier.fillMaxWidth()) {
            import.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "text/plain"))
        }
        msg?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Brand.Mint) }
    }
    if (show) {
        val bank = remember { Store.list("qbank") }
        var fSubject by remember { mutableStateOf("الكل") }; var fGrade by remember { mutableStateOf("الكل") }; var fLevel by remember { mutableStateOf("الكل") }
        var search by remember { mutableStateOf("") }; var random by remember { mutableStateOf("") }
        val picked = remember { mutableStateListOf<Int>() }
        fun opts(k: String) = listOf("الكل") + bank.map { it.optString(k) }.filter { it.isNotBlank() }.distinct()
        val shown = bank.indices.filter { i -> val b = bank[i]
            (fSubject == "الكل" || b.optString("subject") == fSubject) && (fGrade == "الكل" || b.optString("grade") == fGrade) &&
                (fLevel == "الكل" || b.optString("level") == fLevel) && (search.isBlank() || b.optString("q").contains(search)) }
        AlertDialog(onDismissRequest = { show = false }, title = { Text("بنك الأسئلة (${shown.size} من ${bank.size})") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (bank.isEmpty()) Text("البنك فارغ. احفظ أسئلة اختبار أو استوردها من Excel لتعيد استخدامها لاحقًا.")
                    else {
                        TextField2("بحث في الأسئلة", search, { search = it })
                        Picker("المادة", opts("subject"), fSubject, { it }) { fSubject = it }
                        Picker("الصف", opts("grade"), fGrade, { it }) { fGrade = it }
                        Picker("الصعوبة", listOf("الكل") + Edu.levels, fLevel, { it }) { fLevel = it }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { NumField("عدد عشوائي", random, { random = it }) }
                            TextButton(onClick = { picked.clear(); picked.addAll(shown.shuffled().take(random.num().toInt().coerceAtLeast(0))) }) { Text("اختر") }
                        }
                        shown.forEach { i -> val b = bank[i]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(i in picked, { on -> if (on) picked.add(i) else picked.remove(i) })
                                Text(b.optString("q") + listOf(b.optString("subject"), b.optString("grade"), b.optString("level")).filter { it.isNotBlank() }.joinToString(" • ").let { if (it.isBlank()) "" else " — $it" },
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picked.sorted().forEach { qs.add(JSONObject(bank[it].toString())) }; show = false }) { Text("إضافة ${picked.size}") } },
            dismissButton = { TextButton(onClick = { show = false }) { Text("إغلاق") } })
    }
}

/** Teaching-strategy templates: each section is a heading, a prompt for the student, and answer space (lines or a table). */
object Strategies {
    data class Sec(val head: String, val prompt: String = "", val lines: Int = 3, val table: List<String> = emptyList(), val rows: Int = 0)
    data class T(val name: String, val about: String, val sections: List<Sec>, val perPage: Int = 1)
    val all = listOf(
        T("ورقة عمل حرة", "اكتب أنشطتك سطرًا سطرًا", emptyList()),
        T("مستويات بلوم", "أسئلة متدرجة من التذكر إلى الإبداع", listOf(
            Sec("تذكّر", "عرّف أو اذكر…"), Sec("افهم", "اشرح بأسلوبك…"), Sec("طبّق", "استخدم ما تعلمته في…"),
            Sec("حلّل", "قارن أو صنّف…"), Sec("قيّم", "ما رأيك ولماذا؟"), Sec("ابتكر", "صمّم أو اقترح…"))),
        T("فكّر، زاوج، شارك", "تفكير فردي ثم نقاش ثنائي ثم مشاركة", listOf(
            Sec("فكّر بمفردك (دقيقتان)", "اكتب إجابتك الأولية"), Sec("زاوج مع زميلك (3 دقائق)", "ما الذي أضافه زميلك؟"), Sec("شارك الصف", "الفكرة التي سنعرضها", 2))),
        T("الخبراء (Jigsaw)", "كل مجموعة تتقن جزءًا ثم تعلّمه للآخرين", listOf(
            Sec("جزئي في مجموعة الخبراء", "الجزء: ............  أهم ثلاث أفكار", 4),
            Sec("ما تعلمته من زملائي في المجموعة الأم", table = listOf("الجزء", "الفكرة الرئيسة", "مثال"), rows = 4))),
        T("القبعات الست", "التفكير في الموضوع من ست زوايا", listOf(
            Sec("القبعة البيضاء: الحقائق", "ما المعلومات المتوفرة؟", 2), Sec("القبعة الحمراء: المشاعر", "بماذا تشعر تجاه الموضوع؟", 2),
            Sec("القبعة السوداء: المخاطر", "ما السلبيات أو العقبات؟", 2), Sec("القبعة الصفراء: الإيجابيات", "ما الفوائد؟", 2),
            Sec("القبعة الخضراء: الأفكار الجديدة", "ما البدائل والحلول المبتكرة؟", 2), Sec("القبعة الزرقاء: التنظيم", "ما الخلاصة والخطوة التالية؟", 2))),
        T("التعلم التعاوني", "أدوار واضحة لكل عضو ومهمة مشتركة", listOf(
            Sec("أدوار المجموعة", table = listOf("الدور", "الاسم"), rows = 0),
            Sec("المهمة", "ما المطلوب من المجموعة؟", 3), Sec("ناتج المجموعة", "", 5), Sec("تقييم العمل الجماعي", "ما الذي نجح؟ وما الذي نحسّنه؟", 2))),
        T("التعلم القائم على حل المشكلات", "من تحديد المشكلة إلى تقييم الحل", listOf(
            Sec("المشكلة", "صف المشكلة بجملة واحدة", 2), Sec("ماذا نعرف؟", "", 3), Sec("ماذا نحتاج أن نعرف؟", "", 3),
            Sec("الحلول المقترحة", table = listOf("الحل", "المزايا", "العيوب"), rows = 3), Sec("الحل المختار ولماذا", "", 3), Sec("كيف سنعرف أنه نجح؟", "", 2))),
        T("المحطات التعليمية", "مهام قصيرة يتنقل الطلاب بينها", listOf(
            Sec("المحطة 1", "المهمة: ............  الزمن: ..... دقائق", 3), Sec("المحطة 2", "المهمة: ............  الزمن: ..... دقائق", 3),
            Sec("المحطة 3", "المهمة: ............  الزمن: ..... دقائق", 3), Sec("المحطة 4", "المهمة: ............  الزمن: ..... دقائق", 3))),
        T("بطاقات الخروج (3-2-1)", "ثلاث بطاقات قصيرة في الصفحة تُجمع آخر الحصة", listOf(
            Sec("3 أشياء تعلمتها اليوم", "", 3), Sec("2 سؤالان ما زالا لديّ", "", 2), Sec("1 كيف سأطبّق ما تعلمته", "", 1)), perPage = 3),
        T("الاستبيان القبلي والبعدي", "قبل الدرس وبعده لقياس التعلم", listOf(
            Sec("قبل الدرس: ماذا أعرف؟", "", 3), Sec("ماذا أريد أن أعرف؟", "", 2), Sec("بعد الدرس: ماذا تعلمت؟", "", 3),
            Sec("قيّم فهمك", table = listOf("العبارة", "قبل (1-5)", "بعد (1-5)"), rows = 4))),
        T("خطة علاجية", "لطلاب يحتاجون دعمًا في مهارة محددة", listOf(
            Sec("المهارة المستهدفة", "", 1), Sec("خطة الدعم", table = listOf("الطالب", "مستواه الحالي", "الإجراء العلاجي", "الموعد", "النتيجة"), rows = 6))),
        T("خطة إثرائية", "لطلاب متقدمين يحتاجون تحديًا أكبر", listOf(
            Sec("الهدف الإثرائي", "", 2), Sec("الأنشطة", table = listOf("الطالب", "النشاط الإثرائي", "المصدر", "موعد التسليم"), rows = 5))),
        T("خطة دراسية أسبوعية", "توزيع المذاكرة على أيام الأسبوع", listOf(
            Sec("الأهداف هذا الأسبوع", "", 3),
            Sec("الجدول", table = listOf("اليوم", "المادة", "ما سأذاكره", "المدة", "تم"), rows = 7))),
    )
}

@Composable
fun WorksheetTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var kind by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("ورقة عمل") }; var subject by remember { mutableStateOf("") }; var goal by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("1) \n2) \n3) ") }; var lines by remember { mutableStateOf("3") }
    var topic by remember { mutableStateOf("") }; var word by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { val p = com.muyassir.app.core.Assistant.takePrefill(); val sg = listOf(p.optString("subject"), p.optString("grade")).filter { it.isNotBlank() }.joinToString(" — "); if (sg.isNotBlank()) subject = sg }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    val tpl = Strategies.all[kind]
    ToolScreen(t, back) {
        Panel("الاستراتيجية") {
            Picker("القالب", Strategies.all.indices.toList(), kind, { Strategies.all[it].name }) { kind = it; if (title == "ورقة عمل" || Strategies.all.any { s -> s.name == title }) title = if (it == 0) "ورقة عمل" else Strategies.all[it].name }
            Hint(tpl.about)
        }
        Panel {
            TextField2("العنوان", title, { title = it }); TextField2("المادة والصف", subject, { subject = it }); TextField2("الهدف من الدرس", goal, { goal = it })
            if (kind != 0) TextField2("الموضوع أو السؤال المحوري", topic, { topic = it })
            NumField("مدة النشاط", minutes, { minutes = it }, suffix = "دقيقة")
            if (kind == 0) { TextField2("الأنشطة/الأسئلة (سطر لكل سؤال)", body, { body = it }, lines = 6); NumField("أسطر إجابة بعد كل سؤال", lines, { lines = it }) }
            else tpl.sections.forEach { s -> Text("• ${s.head}" + if (s.prompt.isNotBlank()) ": ${s.prompt}" else "", style = MaterialTheme.typography.bodySmall) }
        }
        SwitchRow("ملف Word قابل للتعديل أيضًا", word) { word = it }
        TemplatePanel()
        PrimaryButton("إنشاء PDF", Icons.Rounded.PictureAsPdf, modifier = Modifier.testTag("action")) {
            val b = Templates.apply(ctx, DocBuilder(ctx)); val doc = ArrayList<Office.Para>()
            val sub = listOf(subject, minutes.takeIf { it.isNotBlank() }?.let { "$it دقيقة" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" • ")
            repeat(tpl.perPage) { copy ->
                if (copy > 0) b.add(Block.Space(18f))
                b.add(Block.Title(title, sub)); if (copy == 0) { doc += Office.Para.Text(title, 1); if (sub.isNotBlank()) doc += Office.Para.Text(sub) }
                b.add(Block.KeyValues(listOf("الاسم" to ".................................", "التاريخ" to "....../....../........")))
                if (copy == 0) doc += Office.Para.Text("الاسم: .......................     التاريخ: ....../....../........")
                if (goal.isNotBlank() && copy == 0) { b.add(Block.Heading("الهدف")); b.add(Block.Para(goal)); doc += Office.Para.Text("الهدف", 2); doc += Office.Para.Text(goal) }
                if (topic.isNotBlank()) { b.add(Block.Para(topic, 13f, bold = true)); if (copy == 0) doc += Office.Para.Text(topic, bold = true) }
                if (kind == 0) {
                    b.add(Block.Heading("الأنشطة")); if (copy == 0) doc += Office.Para.Text("الأنشطة", 2)
                    body.lines().filter { it.isNotBlank() }.forEach { l -> b.add(Block.Para(l, 12f)); b.add(Block.Lines(lines.num().toInt().coerceIn(0, 12))); b.add(Block.Space(6f))
                        if (copy == 0) { doc += Office.Para.Text(l); repeat(lines.num().toInt().coerceIn(0, 12)) { doc += Office.Para.Text("..............................................................") } } }
                } else tpl.sections.forEach { s ->
                    b.add(Block.Heading(s.head)); if (copy == 0) doc += Office.Para.Text(s.head, 2)
                    if (s.prompt.isNotBlank()) { b.add(Block.Para(s.prompt, 11f, color = DocBuilder.MUTED)); if (copy == 0) doc += Office.Para.Text(s.prompt) }
                    if (s.table.isNotEmpty()) {
                        val rows = if (s.head == "أدوار المجموعة") listOf("القائد", "المقرر", "الموقّت", "المتحدث").map { listOf(it, "") } else List(s.rows) { List(s.table.size) { "" } }
                        b.add(Block.Table(s.table, rows)); if (copy == 0) doc += Office.Para.Table(listOf(s.table) + rows.map { r -> r.map { it.ifBlank { " " } } })
                    } else { b.add(Block.Lines(s.lines)); if (copy == 0) repeat(s.lines) { doc += Office.Para.Text("..............................................................") } }
                }
            }
            val out = Files.outDir(ctx)
            result = listOfNotNull(b.save(File(out, "$title.pdf")), if (word) File(out, "$title.docx").also { Office.writeDocx(it, doc) } else null); Store.used(t.id, title)
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}
