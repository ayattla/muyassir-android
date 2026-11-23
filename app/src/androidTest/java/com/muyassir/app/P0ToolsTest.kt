package com.muyassir.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.muyassir.app.core.Store
import com.muyassir.app.core.TestHooks
import com.muyassir.app.engine.DocBuilder
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * P0: proves the result of every tool that had only a "screen opens" check before.
 * Each test drives the real screen and waits for the tool's own output (a number, a saved record or a file).
 */
@RunWith(AndroidJUnit4::class)
class P0ToolsTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setup() { PDFBoxResourceLoader.init(ctx); dir = File(ctx.cacheDir, "p0-${System.nanoTime()}").apply { mkdirs() } }
    @After fun done() { TestHooks.inputs = null; scenario?.close() }

    private fun launch(tool: String) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("tool", tool)
        scenario = ActivityScenario.launch(i); rule.waitForIdle()
    }
    private fun waitText(s: String, ms: Long = 10_000) = try {
        rule.waitUntil(ms) { rule.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) { fail("«$s» did not appear within ${ms / 1000}s") }
    private fun SemanticsNodeInteraction.scrolled() = apply { runCatching { performScrollTo() } } // dialogs have no scroll parent
    private fun input(label: String, value: String) { rule.onNode(hasSetTextAction() and hasText(label, substring = true)).scrolled().performTextReplacement(value) }
    private fun click(label: String) { rule.onAllNodes(hasText(label, substring = true) and hasClickAction()).onFirst().scrolled().performClick(); rule.waitForIdle() }
    private fun pick(label: String, option: String) {
        rule.onNode(hasContentDescription(label) and hasClickAction()).scrolled().performClick(); rule.waitForIdle()
        rule.onAllNodes(hasText(option) and hasClickAction()).onLast().performClick(); rule.waitForIdle()
    }
    private fun clickTag(tag: String) { rule.onNodeWithTag(tag).scrolled().performClick(); rule.waitForIdle() }
    private fun waitResults(ms: Long = 60_000) {
        try { rule.waitUntil(ms) { rule.onAllNodesWithTag("results").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("error").fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { fail("no result within ${ms / 1000}s") }
        rule.onAllNodesWithTag("error").fetchSemanticsNodes().firstOrNull()?.let { fail("failed: " + it.config.getOrNull(SemanticsProperties.Text)?.joinToString()) }
    }
    /** Picks today in the date dialog, and the default time when the field has one. */
    private fun pickDate(label: String, withTime: Boolean) {
        click(label); click("تم"); if (withTime) click("تم")
    }
    private fun pdf(name: String, pages: Int): File {
        val b = DocBuilder(ctx)
        repeat(pages) { i -> if (i > 0) b.add(DocBuilder.Block.PageBreak); b.add(DocBuilder.Block.Title("صفحة ${i + 1}")); b.add(DocBuilder.Block.Para("نص تجريبي عربي")) }
        return b.save(File(dir, name))
    }

    private val lesson = "الخلية هي الوحدة الأساسية في بناء جميع الكائنات الحية. تحتوي الخلية على النواة التي تتحكم في أنشطتها الحيوية. " +
        "يحيط بالخلية غشاء خلوي ينظم دخول المواد وخروجها. تقوم الميتوكوندريا بإنتاج الطاقة اللازمة للخلية. " +
        "تحتوي الخلية النباتية على جدار خلوي وبلاستيدات خضراء تقوم بعملية البناء الضوئي. البناء الضوئي يحول ضوء الشمس إلى طاقة كيميائية. " +
        "الرايبوسومات مسؤولة عن تصنيع البروتينات داخل الخلية. تختلف الخلايا في أشكالها وأحجامها حسب وظائفها في الجسم."

    // ---------- Saudi services and calculators ----------
    @Test fun leaveBalance() { launch("leave"); input("سنوات الخدمة", "6"); waitText("30 يومًا") }
    @Test fun zakatDue() { launch("zakat"); input("النقد والأرصدة البنكية", "100000"); input("سعر غرام الذهب", "300"); waitText("2,500.00") }
    @Test fun overtimePay() { launch("overtime"); input("الراتب الأساسي", "6000"); input("عدد الساعات الإضافية", "10"); waitText("375.00") }
    @Test fun electricityBill() { launch("electricity"); click("حتى 31 ديسمبر 2026"); input("الاستهلاك الشهري", "1000"); waitText("207.00") }
    @Test fun dateSpan() { launch("date_diff"); waitText("إجمالي الأيام"); waitText("هـ") }
    @Test fun govDirectoryLinks() { launch("gov_dir"); waitText("أبشر"); waitText("توكلنا") }
    @Test fun emergencyNumbers() { launch("emergency"); waitText("911"); waitText("997") }
    @Test fun documentsSaveAndRemind() {
        launch("documents"); click("إضافة"); input("اسم الوثيقة أو الموعد", "جواز سفر اختبار")
        pickDate("تاريخ الانتهاء أو الموعد", false); click("حفظ"); waitText("جواز سفر اختبار")
    }

    // ---------- productivity ----------
    @Test fun areaOfRectangle() { launch("area_volume"); input("الطول", "4"); input("العرض", "5"); waitText("20 وحدة²") }
    @Test fun textCompareScores() { launch("text_compare"); input("النص الأول", "مرحبا بك في ميسر"); input("النص الثاني", "مرحبا بكم في ميسر"); waitText("نسبة التطابق") }
    @Test fun remindersSave() { launch("reminders"); click("إضافة"); input("التذكير", "تذكير اختبار"); pickDate("الوقت", true); click("حفظ"); waitText("تذكير اختبار") }
    @Test fun expensesTotal() {
        launch("expenses"); click("إضافة"); input("البند", "قهوة"); input("المبلغ", "25"); pickDate("التاريخ", false); click("حفظ")
        waitText("قهوة"); waitText("مصروف هذا الشهر")
    }
    @Test fun budgetRemaining() { launch("budget"); input("الدخل الشهري", "5000"); waitText("المتبقي هذا الشهر") }
    @Test fun calendarShowsSavedItems() {
        Store.saveList("tasks", Store.list("tasks") + JSONObject().put("id", 1L).put("title", "مهمة التقويم").put("due", System.currentTimeMillis()))
        launch("calendar"); waitText("مهمة التقويم")
    }

    // ---------- study and teacher pack ----------
    @Test fun timetableCell() { launch("timetable"); clickTag("cell-0-0"); input("المادة", "رياضيات"); click("حفظ"); waitText("رياضيات") }
    @Test fun examsSave() { launch("exams"); click("إضافة"); input("المادة", "فيزياء"); pickDate("موعد الاختبار", true); click("حفظ"); waitText("فيزياء") }
    @Test fun homeworkSave() { launch("homework"); click("إضافة"); input("المادة", "لغتي"); input("الواجب", "تمرين 3"); click("حفظ"); waitText("تمرين 3") }
    @Test fun flashcardsReview() {
        launch("flashcards"); input("الوجه", "عاصمة السعودية"); input("الظهر", "الرياض"); click("إضافة البطاقة")
        click("ابدأ المراجعة"); waitText("عاصمة السعودية")
    }
    @Test fun studyTimerCounts() { launch("study_timer"); click("ابدأ"); waitText("24:5", 5_000) }
    @Test fun quizMakerPdf() {
        launch("quiz_maker"); click("إضافة سؤال"); input("نص السؤال", "كم عدد أيام الأسبوع؟"); input("الخيار أ", "سبعة"); input("الخيار ب", "خمسة")
        click("إنشاء PDF"); waitResults()
    }
    @Test fun worksheetPdf() { launch("worksheet"); input("الأنشطة/الأسئلة", "1) عرّف الخلية\n2) اذكر أجزاءها"); click("إنشاء PDF"); waitResults() }
    @Test fun certificateSingle() { launch("certificate"); input("اسم المكرَّم", "سارة أحمد"); clickTag("action"); waitResults() }
    @Test fun certificateBulk() {
        launch("certificate"); click("شهادات جماعية من قائمة أسماء"); input("الأسماء", "سارة\nمحمد\nنورة"); clickTag("action"); waitResults(); waitText("شهادات-")
    }
    @Test fun attendanceSession() { launch("attendance"); input("اسم الطالب", "خالد"); rule.onAllNodesWithContentDescription("إضافة").onFirst().performClick(); rule.waitForIdle(); click("حفظ الحصة"); waitText("حضور") }
    @Test fun gradesTotals() {
        Store.saveList("students", emptyList()); Store.saveList("grade_items", emptyList()); Store.saveObj("grades", JSONObject())
        launch("grades"); input("اسم الطالب أو عدة أسماء", "ريم"); clickTag("addStudent")
        input("اسم التقييم", "اختبار 1"); input("الدرجة العظمى", "50"); clickTag("addItem")
        input("من 50", "45"); waitText("45 / 50"); waitText("90%")
    }
    @Test fun quizGenerator() { launch("quiz_gen"); rule.onNodeWithTag("sourceText").performScrollTo().performTextReplacement(lesson); clickTag("action"); waitResults() }
    @Test fun summarizer() { launch("summarize"); rule.onNodeWithTag("sourceText").performScrollTo().performTextReplacement(lesson); clickTag("action"); waitResults() }

    // ---------- documents ----------
    @Test fun quotePdf() { launch("quote"); input("الوصف", "تصميم شعار"); input("الكمية", "1"); input("السعر", "500"); click("إنشاء عرض السعر"); waitResults() }
    @Test fun letterPdf() { launch("letter"); input("الموضوع", "طلب إجازة"); input("نص الخطاب", "أرجو الموافقة على إجازتي."); click("إنشاء PDF"); waitResults() }
    @Test fun textToPdf() { launch("text_pdf"); input("النص", "فقرة عربية للاختبار"); click("إنشاء PDF"); waitResults() }
    @Test fun signPdf() {
        TestHooks.inputs = listOf(Uri.fromFile(pdf("s.pdf", 1)))
        launch("sign_pdf"); rule.onNodeWithTag("signPad").performScrollTo().performTouchInput { swipe(centerLeft, centerRight, 300) }
        rule.waitUntil(10_000) { rule.onNodeWithTag("action").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null }
        clickTag("action"); waitResults()
    }
    @Test fun pdfEditAddsText() {
        TestHooks.inputs = listOf(Uri.fromFile(pdf("e.pdf", 2)))
        launch("pdf_edit"); rule.waitUntil(15_000) { rule.onAllNodesWithTag("pagePreview").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("editText").performScrollTo().performTextReplacement("تمت المراجعة")
        rule.onNodeWithTag("pagePreview").performScrollTo().performTouchInput { click(center) }
        clickTag("action"); waitResults()
    }

    // ---------- smart tools ----------
    @Test fun qrReadFromImage() {
        val m = QRCodeWriter().encode("https://ayattla.com/muyassir", BarcodeFormat.QR_CODE, 600, 600)
        val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        for (x in 0 until 600) for (y in 0 until 600) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        val f = File(dir, "qr.png"); FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        TestHooks.inputs = listOf(Uri.fromFile(f)); launch("qr_read"); waitText("ayattla.com/muyassir")
    }
    @Test fun translateOnDevice() {
        // The language model downloads once (about 30 MB); afterwards it works offline.
        launch("translate"); rule.onNodeWithTag("sourceText").performScrollTo().performTextReplacement("مرحبا بكم في التطبيق"); clickTag("action"); waitResults(240_000)
    }

    // ---------- broken and large inputs: a clear message, never a crash or a blank screen ----------
    private fun expectError(tool: String, file: File) {
        TestHooks.inputs = listOf(Uri.fromFile(file)); launch(tool); clickTag("action")
        try { rule.waitUntil(60_000) { rule.onAllNodesWithTag("error").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("results").fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { fail("$tool: no message for a broken file") }
        if (rule.onAllNodesWithTag("error").fetchSemanticsNodes().isEmpty()) fail("$tool accepted a broken file without a warning")
    }
    private fun junk(name: String) = File(dir, name).apply { writeBytes(ByteArray(4096) { (it * 37 % 251).toByte() }) }
    @Test fun brokenPdfIsRejected() = expectError("pdf_word", junk("bad.pdf"))
    @Test fun brokenWordIsRejected() = expectError("word_pdf", junk("bad.docx"))
    @Test fun brokenExcelIsRejected() = expectError("excel_pdf", junk("bad.xlsx"))
    @Test fun brokenZipIsRejected() = expectError("unzip", junk("bad.zip"))
    @Test fun brokenImageIsRejected() = expectError("img_compress", junk("bad.png"))

    /** A 60-page document goes through merge and compression, and the time is reported in the test log. */
    @Test fun largePdfMergeAndCompress() {
        val big = pdf("big.pdf", 60)
        val t0 = System.currentTimeMillis()
        TestHooks.inputs = listOf(Uri.fromFile(big), Uri.fromFile(pdf("small.pdf", 2))); launch("pdf_merge"); clickTag("action"); waitResults(180_000)
        val merge = System.currentTimeMillis() - t0; scenario?.close()
        val t1 = System.currentTimeMillis()
        TestHooks.inputs = listOf(Uri.fromFile(big)); launch("pdf_compress"); clickTag("action"); waitResults(300_000)
        android.util.Log.i("MuyassirTiming", "merge 62 pages: ${merge} ms, compress 60 pages: ${System.currentTimeMillis() - t1} ms")
    }

    // ---------- P1: document studio and education studio ----------
    private fun textImage(name: String, lines: List<String>): File {
        // Bold 80 px text, like a clear phone photo of a printed page.
        val bmp = Bitmap.createBitmap(1600, 160 + lines.size * 130, Bitmap.Config.ARGB_8888); val c = android.graphics.Canvas(bmp); c.drawColor(Color.WHITE)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 80f; typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.RIGHT }
        lines.forEachIndexed { i, l -> c.drawText(l, 1520f, 140f + i * 130, p) }
        val f = File(dir, name); FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; return f
    }
    @Test fun imagesToWordWithReview() {
        TestHooks.inputs = listOf(Uri.fromFile(textImage("doc.png", listOf("تقرير المبيعات 2026", "الفرع الأول: الرياض", "Muyassir sales report", "هذا نص عربي واضح للاختبار"))))
        launch("img_word"); clickTag("action"); waitResults(180_000); rule.onNodeWithTag("reviewText").assertExists()
        clickTag("saveReviewed"); waitText("مراجع.docx")
    }
    @Test fun examPhotoBecomesQuestions() {
        TestHooks.inputs = listOf(Uri.fromFile(textImage("exam.png", listOf("1) ما عاصمة السعودية؟", "أ) جدة", "ب) الرياض", "2) الشمس نجم ( )"))))
        launch("exam_doc"); clickTag("action"); waitResults(180_000); rule.onNodeWithTag("examQuestions").assertExists()
    }
    @Test fun pdfHeaderFooterNumbers() {
        TestHooks.inputs = listOf(Uri.fromFile(pdf("n.pdf", 3))); launch("pdf_pages"); input("الترويسة", "مدرسة الاختبار"); clickTag("action"); waitResults()
    }
    @Test fun quizMakerAllTypesAndWord() {
        launch("quiz_maker"); input("الصف", "الخامس"); input("المادة", "علوم")
        click("إضافة سؤال"); input("نص السؤال", "كم عدد أيام الأسبوع؟"); input("الخيار أ", "سبعة")
        pick("نوع السؤال الجديد", "إكمال الفراغ"); click("إضافة سؤال"); input("اكتب ___", "الماء يغلي عند ___ درجة"); input("الإجابة الصحيحة", "100")
        pick("نوع السؤال الجديد", "توصيل"); click("إضافة سؤال"); input("الأزواج", "الرياض = السعودية\nالقاهرة = مصر")
        clickTag("action"); waitResults(); waitText(".docx")
    }
    @Test fun worksheetBloomTemplate() { launch("worksheet"); pick("القالب", "مستويات بلوم"); clickTag("action"); waitResults(); waitText(".docx") }
    @Test fun worksheetExitTickets() { launch("worksheet"); pick("القالب", "بطاقات الخروج (3-2-1)"); clickTag("action"); waitResults() }

    // ---------- P2: Saudi services with formula, source and review date ----------
    @Test fun noticeCompensation() {
        launch("notice"); input("أيام الإشعار التي أُعطيت", "20"); input("الأجر الشهري", "9000"); waitText("12,000.00")
        click("عرض"); waitText("المادتان 75 و76"); waitText("آخر مراجعة")
    }
    @Test fun finalSettlementShowsBreakdown() {
        launch("settlement"); pickDate("تاريخ المباشرة", false); input("آخر أجر شهري", "10000"); input("أيام عمل لم يُدفع أجرها", "10")
        waitText("إجمالي التسوية التقديرية"); waitText("3,333.33"); waitText("تفاصيل الحساب")
    }
    @Test fun governmentSectorIsExplained() { launch("settlement"); click("حكومي"); waitText("نظام الخدمة المدنية") }
    @Test fun wageDeductionCap() { launch("wage_deduct"); input("الأجر المستحق", "8000"); input("المبلغ المطلوب", "6000"); waitText("4,000.00") }
    @Test fun entitlementsTotal() { launch("entitlements"); input("الأجر الشهري الكامل", "9000"); input("أيام عمل لم يُدفع أجرها", "10"); waitText("3,000.00") }
    @Test fun serviceLength() { launch("service_len"); pickDate("تاريخ المباشرة", false); waitText("مدة الخدمة"); waitText("بالأيام") }
    @Test fun salaryShowsSource() { launch("salary"); click("عرض"); waitText("نسب الاشتراك"); waitText("آخر مراجعة") }

    // ---------- P3: the assistant runs the real tools; projects ----------
    @Test fun assistantUnderstandsTheBriefExamples() {
        fun first(q: String) = com.muyassir.app.core.Assistant.plan(q).steps.map { it.tool.id }
        val cases = mapOf(
            "أريد تحويل عشر صور إلى Word" to "img_word", "أنشئ اختباراً من هذا الملف" to "quiz_gen", "أريد حساب مستحقات نهاية الخدمة" to "settlement",
            "اجمع هذه المستندات في PDF" to "pdf_merge", "استخرج الجداول من هذا التقرير" to "pdf_excel", "أنشئ ورقة عمل للصف السادس" to "worksheet",
        )
        val wrong = cases.filter { (q, id) -> first(q).firstOrNull() != id }.map { (q, id) -> "$q → ${first(q)} (expected $id)" }
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
        org.junit.Assert.assertEquals("الصف السادس", com.muyassir.app.core.Assistant.details("أنشئ ورقة عمل للصف السادس").optString("grade"))
    }
    @Test fun assistantOpensWorksheetWithGrade() {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        scenario = ActivityScenario.launch(i); rule.waitForIdle()
        rule.onAllNodes(hasText("المساعد") and hasClickAction()).onFirst().performClick(); rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("أنشئ ورقة عمل للصف السادس")
        click("اقترح الأدوات"); clickTag("assistOpen-worksheet"); waitText("الصف السادس")
    }
    @Test fun projectKeepsFiles() {
        launch("text_pdf"); input("النص", "مستند المشروع"); click("إنشاء PDF"); waitResults(); scenario?.close()
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        scenario = ActivityScenario.launch(i); rule.waitForIdle()
        rule.onAllNodes(hasText("ملفاتي") and hasClickAction()).onFirst().performClick(); click("الملفات")
        rule.onAllNodesWithContentDescription("إلى مشروع").onFirst().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("newProject").performTextReplacement("مشروع الاختبار"); clickTag("createProject")
        click("مشاريعي"); waitText("مشروع الاختبار"); click("فتح"); waitText(".pdf")
    }

    // ---------- P4: online quiz on the live server (published, answered, results read, then deleted) ----------
    @Test fun onlineQuizEndToEnd() {
        Store.saveObj("cloud", JSONObject()); Store.saveList("published", emptyList())
        launch("quiz_maker"); input("عنوان الاختبار", "اختبار آلي من CI")
        click("إضافة سؤال"); input("نص السؤال", "كم عدد أيام الأسبوع؟"); input("الخيار أ", "سبعة"); input("الخيار ب", "خمسة")
        clickTag("publishOnline"); clickTag("consentOk")
        try { rule.waitUntil(30_000) { rule.onAllNodesWithTag("onlineUrl").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("onlineError").fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { fail("publish did not finish") }
        rule.onAllNodesWithTag("onlineError").fetchSemanticsNodes().firstOrNull()?.let { fail("publish failed: " + it.config.getOrNull(SemanticsProperties.Text)?.joinToString()) }
        waitText("ayattla.com/muyassir/q/")
        val code = Store.list("published").first().optString("code")
        try {
            // A student answers from the web page (same form a browser sends).
            val c = java.net.URL("https://ayattla.com/muyassir/q/$code").openConnection() as java.net.HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(("name=" + java.net.URLEncoder.encode("طالب الاختبار", "UTF-8") + "&q0=" + java.net.URLEncoder.encode("سبعة", "UTF-8")).toByteArray()) }
            val page = c.inputStream.bufferedReader().readText(); c.disconnect()
            if (!page.contains("1 من 1")) fail("student page did not grade the answer")
            clickTag("results-$code"); waitText("طالب الاختبار", 20_000); waitText("عدد المتقدمين")
        } finally {
            kotlinx.coroutines.runBlocking { com.muyassir.app.core.Cloud.forgetMe() }
        }
        val gone = java.net.URL("https://ayattla.com/muyassir/q/$code").openConnection() as java.net.HttpURLConnection
        if (gone.responseCode != 404) fail("quiz still on the server after deletion: ${gone.responseCode}")
    }

    // ---------- P5: ordering and classification, printed and played online ----------
    @Test fun orderAndClassifyOnline() {
        Store.saveObj("cloud", JSONObject()); Store.saveList("published", emptyList())
        launch("quiz_maker"); input("عنوان الاختبار", "ترتيب وتصنيف من CI")
        pick("نوع السؤال الجديد", "ترتيب"); click("إضافة سؤال"); input("العناصر بترتيبها", "واحد\nاثنان\nثلاثة")
        pick("نوع السؤال الجديد", "تصنيف"); click("إضافة سؤال"); input("سطر لكل عنصر", "أسد = ثدييات\nصقر = طيور\nحوت = ثدييات")
        clickTag("action"); waitResults()
        clickTag("publishOnline"); clickTag("consentOk")
        try { rule.waitUntil(30_000) { rule.onAllNodesWithTag("onlineUrl").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("onlineError").fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { fail("publish did not finish") }
        rule.onAllNodesWithTag("onlineError").fetchSemanticsNodes().firstOrNull()?.let { fail("publish failed: " + it.config.getOrNull(SemanticsProperties.Text)?.joinToString()) }
        val code = Store.list("published").first().optString("code")
        try {
            val enc = { v: String -> java.net.URLEncoder.encode(v, "UTF-8") }
            val form = "name=${enc("طالبة")}&q0_0=${enc("واحد")}&q0_1=${enc("اثنان")}&q0_2=${enc("ثلاثة")}&q1_0=${enc("ثدييات")}&q1_1=${enc("طيور")}&q1_2=${enc("ثدييات")}"
            val c = java.net.URL("https://ayattla.com/muyassir/q/$code").openConnection() as java.net.HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(form.toByteArray()) }
            val page = c.inputStream.bufferedReader().readText(); c.disconnect()
            if (!page.contains("2 من 2")) fail("ordering/classification were not graded: " + page.takeLast(300))
        } finally { kotlinx.coroutines.runBlocking { com.muyassir.app.core.Cloud.forgetMe() } }
    }
}
