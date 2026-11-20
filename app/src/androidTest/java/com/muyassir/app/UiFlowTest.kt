package com.muyassir.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muyassir.app.core.TestHooks
import com.muyassir.app.core.Tools
import com.muyassir.app.engine.DocBuilder
import com.muyassir.app.engine.Office
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.engine.ZipEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Drives the real app UI: every category, every tool screen, back navigation, and the actual function of the tools
 * (calculations, records, and every file tool run end-to-end on real input files).
 */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setup() { PDFBoxResourceLoader.init(ctx); dir = File(ctx.cacheDir, "ui-${System.nanoTime()}").apply { mkdirs() } }
    @After fun done() { TestHooks.inputs = null; scenario?.close() }

    private fun launch(tool: String? = null) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (tool != null) i.putExtra("tool", tool)
        scenario = ActivityScenario.launch(i); rule.waitForIdle()
    }
    private fun text(s: String) = rule.onNode(hasText(s, substring = true) and !hasSetTextAction(), useUnmergedTree = true)
    private fun waitText(s: String, ms: Long = 10_000) = rule.waitUntil(ms) { rule.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun input(label: String, value: String, exact: Boolean = false) { rule.onNode(hasSetTextAction() and hasText(label, substring = !exact)).performScrollTo().performTextReplacement(value) }

    // ---------- navigation: every category and every tool ----------
    @Test fun everyCategoryShowsItsToolsAndEveryToolOpens() {
        launch()
        val problems = mutableListOf<String>()
        for (c in Tools.categories) {
            rule.onNodeWithTag("homeGrid").performScrollToNode(hasText(c.title) and hasClickAction())
            rule.onNode(hasText(c.title) and hasClickAction()).performClick(); rule.waitForIdle()
            rule.onNodeWithTag("catGrid").assertExists("category ${c.id} has no grid")
            val tools = Tools.inCat(c.id)
            if (tools.isEmpty()) problems += "category ${c.id} empty"
            for (t in tools) {
                try {
                    rule.onNodeWithTag("catGrid").performScrollToNode(hasText(t.title) and hasClickAction())
                    rule.onNode(hasText(t.title) and hasClickAction()).performClick(); rule.waitForIdle()
                    // A tool screen shows its subtitle and the favourite action in the top bar.
                    waitText(t.sub, 5_000)
                    rule.onNode(hasContentDescription("المفضلة", substring = true)).assertExists()
                    rule.onNodeWithContentDescription("رجوع").performClick(); rule.waitForIdle()
                    rule.onNodeWithTag("catGrid").assertExists()
                } catch (e: Throwable) { problems += "${c.id}/${t.id}: ${e.message?.lineSequence()?.firstOrNull()}"; rule.waitForIdle() }
            }
            rule.onNodeWithContentDescription("رجوع").performClick(); rule.waitForIdle()
            rule.onNodeWithTag("homeGrid").assertExists()
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test fun searchFindsTools() {
        launch()
        rule.onNode(hasSetTextAction()).performTextInput("ضريبة")
        rule.onNode(hasText("ضريبة القيمة المضافة") and hasClickAction()).performClick()
        waitText("15% إضافة أو استخراج")
    }

    // ---------- calculators ----------
    @Test fun vatCalculates() { launch("vat"); input("المبلغ قبل الضريبة", "100"); waitText("115.00 ريال") }
    @Test fun salaryCalculates() { launch("salary"); input("الراتب الأساسي", "8000"); input("بدل السكن", "2000"); waitText("975") }
    @Test fun endOfServiceCalculates() { launch("eos"); input("آخر أجر شهري", "10000"); input("سنوات", "10"); waitText("75,000") }
    @Test fun discountCalculates() { launch("discount"); input("السعر الأصلي", "200"); input("نسبة الخصم", "10"); waitText("180") }
    @Test fun financeCalculates() { launch("finance"); input("مبلغ التمويل", "100000"); waitText("القسط الشهري") }
    @Test fun gpaCalculates() { launch("gpa"); waitText("المعدل") }
    @Test fun percentCalculates() { launch("percent"); input("درجتك", "45"); input("الدرجة الكاملة", "50"); waitText("90%") }
    @Test fun unitsConvert() { launch("units"); input("القيمة", "2"); waitText("0.2 سنتيمتر") }
    @Test fun scientificCalculates() {
        launch("scientific")
        listOf("2", "+", "3", "×", "4").forEach { rule.onNode(hasText(it) and hasClickAction()).performClick() }
        rule.onNode(hasText("=") and hasClickAction()).performClick(); waitText("14")
    }
    @Test fun hijriConverts() { launch("hijri"); waitText("هـ") }
    @Test fun wordCountCounts() { launch("word_count"); input("الصق النص هنا", "مرحبا بك في ميسر"); waitText("4") }
    @Test fun dedupeRemoves() { launch("dedupe"); input("سطر", "أ\nب\nأ"); waitText("1") }
    @Test fun qrMakes() { launch("qr_make"); input("الرابط أو النص", "https://example.com"); rule.onNode(hasText("حفظ الصورة") and hasClickAction()).performScrollTo().performClick(); waitText("الناتج") }

    // ---------- records (saved on device) ----------
    @Test fun notesAddAndPersist() {
        launch("notes")
        rule.onNode(hasText("إضافة") and hasClickAction()).performClick()
        input("العنوان", "ملاحظة اختبار")
        rule.onNode(hasText("حفظ") and hasClickAction()).performClick(); waitText("ملاحظة اختبار")
        scenario?.close(); launch("notes"); waitText("ملاحظة اختبار")
    }
    @Test fun tasksAdd() {
        launch("tasks"); rule.onNode(hasText("إضافة") and hasClickAction()).performClick()
        input("المهمة", "مهمة اختبار"); rule.onNode(hasText("حفظ") and hasClickAction()).performClick(); waitText("مهمة اختبار")
    }

    // ---------- every file tool, end to end on real files ----------
    private fun pdf(name: String, pages: Int): File {
        val b = DocBuilder(ctx)
        repeat(pages) { i -> if (i > 0) b.add(DocBuilder.Block.PageBreak); b.add(DocBuilder.Block.Title("صفحة ${i + 1}")); b.add(DocBuilder.Block.Para("نص تجريبي عربي Test ${i + 1}")) }
        return b.save(File(dir, name))
    }
    private fun png(name: String, w: Int = 900, h: Int = 700): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(47, 75, 255); textSize = 90f }
        c.drawCircle(w / 2f, h / 2f, h / 4f, p); p.color = Color.BLACK; c.drawText("Muyassir 2026", 60f, 120f, p)
        val f = File(dir, name); FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; return f
    }
    /** A simple portrait: a figure on a light studio background, for background removal and ID photos. */
    private fun person(name: String): File {
        val bmp = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888); val c = Canvas(bmp); c.drawColor(Color.rgb(232, 232, 236))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(217, 166, 128) }
        c.drawOval(290f, 180f, 510f, 450f, p); p.color = Color.rgb(38, 51, 115); c.drawRoundRect(160f, 470f, 640f, 1070f, 200f, 200f, p)
        val f = File(dir, name); FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; return f
    }
    private fun runFileTool(id: String, files: List<File>, fill: () -> Unit = {}, waitMs: Long = 60_000) {
        TestHooks.inputs = files.map { Uri.fromFile(it) }
        launch(id)
        fill()
        rule.onNodeWithTag("action").performScrollTo().performClick()
        try {
            rule.waitUntil(waitMs) {
                rule.onAllNodesWithTag("results").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("error").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) { fail("$id: no result within ${waitMs / 1000}s") }
        val err = rule.onAllNodesWithTag("error").fetchSemanticsNodes().firstOrNull()
        if (err != null) fail("$id failed: " + err.config.getOrNull(SemanticsProperties.Text)?.joinToString())
    }

    @Test fun pdfToWord() = runFileTool("pdf_word", listOf(pdf("a.pdf", 2)))
    @Test fun wordToPdf() { val d = File(dir, "a.docx"); Office.writeDocx(d, listOf(Office.Para.Text("عنوان", 1), Office.Para.Text("فقرة عربية"))); runFileTool("word_pdf", listOf(d)) }
    @Test fun pdfToExcel() = runFileTool("pdf_excel", listOf(pdf("a.pdf", 1)))
    @Test fun excelToPdf() { val x = File(dir, "a.xlsx"); Office.writeXlsx(x, listOf("ورقة" to listOf(listOf("الاسم", "المبلغ"), listOf("سارة", "10")))); runFileTool("excel_pdf", listOf(x)) }
    @Test fun pdfToPpt() = runFileTool("pdf_ppt", listOf(pdf("a.pdf", 2)))
    @Test fun pptToPdf() { val p = File(dir, "a.pptx"); Office.writePptx(p, listOf(Office.Slide("شريحة", "نقطة 1\nنقطة 2"))); runFileTool("ppt_pdf", listOf(p)) }
    @Test fun pdfToImages() = runFileTool("pdf_img", listOf(pdf("a.pdf", 2)))
    @Test fun imagesToPdf() = runFileTool("img_pdf", listOf(png("1.png"), png("2.png")))
    @Test fun pdfMerge() = runFileTool("pdf_merge", listOf(pdf("a.pdf", 2), pdf("b.pdf", 1)))
    @Test fun pdfSplit() = runFileTool("pdf_split", listOf(pdf("a.pdf", 3)))
    @Test fun pdfCompress() = runFileTool("pdf_compress", listOf(pdf("a.pdf", 2)))
    @Test fun pdfReorder() = runFileTool("pdf_reorder", listOf(pdf("a.pdf", 3)), { input("الترتيب الجديد", "3, 1, 2") })
    @Test fun pdfDelete() = runFileTool("pdf_delete", listOf(pdf("a.pdf", 3)), { input("الصفحات المطلوب حذفها", "2") })
    @Test fun pdfExtract() = runFileTool("pdf_extract", listOf(pdf("a.pdf", 3)), { input("الصفحات المطلوبة", "1, 3") })
    @Test fun pdfRotate() = runFileTool("pdf_rotate", listOf(pdf("a.pdf", 2)))
    @Test fun pdfProtect() = runFileTool("pdf_protect", listOf(pdf("a.pdf", 1)), { input("كلمة المرور", "1234", exact = true); input("تأكيد كلمة المرور", "1234") })
    @Test fun pdfUnlock() {
        val locked = PdfEngine.protect(pdf("a.pdf", 1), "1234", "", true, false, File(dir, "locked.pdf"))
        runFileTool("pdf_unlock", listOf(locked), { input("كلمة مرور الملف", "1234") })
    }
    @Test fun ocrImage() = runFileTool("ocr", listOf(png("t.png")), waitMs = 120_000)
    @Test fun zip() = runFileTool("zip", listOf(pdf("a.pdf", 1), png("b.png")))
    @Test fun unzip() { val z = ZipEngine.zip(listOf("أ.pdf" to pdf("a.pdf", 1)), File(dir, "z.zip")); runFileTool("unzip", listOf(z)) }
    @Test fun imgConvert() = runFileTool("img_convert", listOf(png("a.png")))
    @Test fun imgCompress() = runFileTool("img_compress", listOf(png("a.png")))
    @Test fun imgResize() = runFileTool("img_resize", listOf(png("a.png")))
    @Test fun imgCrop() = runFileTool("img_crop", listOf(png("a.png")))
    @Test fun imgRotate() = runFileTool("img_rotate", listOf(png("a.png")))
    @Test fun imgEnhance() = runFileTool("img_enhance", listOf(png("a.png")))
    @Test fun imgBatch() = runFileTool("img_batch", listOf(png("a.png"), png("b.png"), png("c.png")))
    @Test fun idPhoto() = runFileTool("id_photo", listOf(person("p.png")))
    @Test fun imgBackground() = runFileTool("img_bg", listOf(person("p.png")), waitMs = 300_000)
}
