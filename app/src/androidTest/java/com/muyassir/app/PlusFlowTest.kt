package com.muyassir.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muyassir.app.core.Plus
import com.muyassir.app.core.Templates
import com.muyassir.app.core.TestHooks
import com.muyassir.app.engine.DocBuilder
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** «مُيسّر بلس»: the paywall, the free limits and the Plus gates, driven through the real UI. */
@RunWith(AndroidJUnit4::class)
class PlusFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setup() { PDFBoxResourceLoader.init(ctx); dir = File(ctx.cacheDir, "plus-${System.nanoTime()}").apply { mkdirs() } }
    @After fun done() { TestHooks.inputs = null; scenario?.close(); Plus.paywall = null; Plus.testState("off") }

    private fun launch(plus: String, tool: String? = null, paywall: String? = null, tab: String? = null) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("plus", plus)
        tool?.let { i.putExtra("tool", it) }; paywall?.let { i.putExtra("paywall", it) }; tab?.let { i.putExtra("tab", it) }
        scenario = ActivityScenario.launch(i); rule.waitForIdle()
    }
    private fun waitText(s: String, ms: Long = 10_000) = rule.waitUntil(ms) { rule.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitTag(t: String, ms: Long = 10_000) = rule.waitUntil(ms) { rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun png(name: String): File {
        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888); Canvas(bmp).drawColor(Color.rgb(47, 75, 255))
        return File(dir, name).also { f -> FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    private fun pdf(name: String, pages: Int): File {
        val b = DocBuilder(ctx)
        repeat(pages) { i -> if (i > 0) b.add(DocBuilder.Block.PageBreak); b.add(DocBuilder.Block.Title("Page ${i + 1}")); b.add(DocBuilder.Block.Para("Muyassir test text ${i + 1}")) }
        return b.save(File(dir, name))
    }

    @Test fun paywallShowsPlansTermsRestoreAndCloses() {
        launch("free", paywall = "Menu")
        waitTag("paywall")
        rule.onNodeWithTag("plan-yearly", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("plan-monthly", useUnmergedTree = true).assertExists()
        waitText("وفّر"); waitText("استعادة المشتريات"); waitText("شروط الاستخدام"); waitText("سياسة الخصوصية")
        waitText("ابدأ تجربة مجانية 7 أيام")
        waitText("مرور يوم")
        rule.onNodeWithTag("plan-monthly", useUnmergedTree = true).performClick(); rule.waitForIdle()
        waitText("اشترك الآن")
        rule.onNodeWithTag("paywallClose").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("paywall").assertCountEquals(0)
    }

    @Test fun beforeLaunchEverythingIsFreeAndPaywallSaysSoon() {
        launch("off", paywall = "Menu")
        waitTag("paywall"); waitText("قريبًا"); waitText("نبّهني عند إطلاق بلس")
        rule.onAllNodesWithTag("subscribe").assertCountEquals(0)
    }

    @Test fun dailyLimitOpensPaywall() {
        TestHooks.inputs = listOf(Uri.fromFile(pdf("a.pdf", 1)))
        launch("free-used", tool = "pdf_compress")
        waitTag("opsLeft"); waitText("انتهت عملياتك المجانية")
        rule.onNodeWithTag("action").performScrollTo().performClick()
        waitTag("paywall"); waitText("استخدمت عملياتك المجانية لليوم")
    }

    @Test fun freeRunIsCountedThenWorks() {
        TestHooks.inputs = listOf(Uri.fromFile(pdf("a.pdf", 1)))
        launch("free", tool = "pdf_compress")
        waitText("متبقٍ لك اليوم 5 من 5")
        rule.onNodeWithTag("action").performScrollTo().performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("results").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, Plus.usedToday)
    }

    @Test fun moreThanFiveFilesNeedsPlus() {
        TestHooks.inputs = (1..6).map { Uri.fromFile(png("$it.png")) }
        launch("free", tool = "img_compress")
        rule.onNodeWithTag("action").performScrollTo().performClick()
        waitTag("paywall"); waitText("المعالجة الدفعية")
    }

    @Test fun plusUserHasNoLimits() {
        TestHooks.inputs = (1..6).map { Uri.fromFile(png("$it.png")) }
        launch("plus", tool = "img_compress")
        rule.onAllNodesWithTag("opsLeft").assertCountEquals(0)
        rule.onNodeWithTag("action").performScrollTo().performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("results").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("paywall").assertCountEquals(0)
    }

    @Test fun ocrFreeStopsAtThreePages() {
        TestHooks.inputs = listOf(Uri.fromFile(pdf("five.pdf", 5)))
        launch("free", tool = "ocr")
        rule.onNodeWithTag("action").performScrollTo().performClick()
        rule.waitUntil(240_000) { rule.onAllNodesWithTag("results").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ocrLimited", useUnmergedTree = true).assertExists()
    }

    @Test fun templatesLockedForFreeOpenForPlus() {
        launch("free", tool = "invoice")
        waitTag("templates")
        rule.onNodeWithContentDescription("قالب ترويسة ملوّنة").performScrollTo().performClick()
        waitTag("paywall"); waitText("القوالب الاحترافية")
        scenario?.close(); Plus.paywall = null
        launch("plus", tool = "invoice")
        waitTag("templates")
        rule.onNodeWithContentDescription("قالب ترويسة ملوّنة").performScrollTo().performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("paywall").assertCountEquals(0)
        assertEquals(1, Templates.all[Templates.selected].layout)
        Templates.choose(0)
    }

    @Test fun footerOnlyForFreeDocuments() {
        Plus.testState("free")
        assertEquals(Plus.FOOTER, Templates.apply(ctx, DocBuilder(ctx)).footer)
        Plus.testState("plus")
        assertNull(Templates.apply(ctx, DocBuilder(ctx)).footer)
        // every template renders a valid PDF
        Templates.all.forEach { st ->
            Templates.choose(st.index)
            val b = Templates.apply(ctx, DocBuilder(ctx))
            b.add(DocBuilder.Block.Title("فاتورة", "منشأة تجريبية")); b.add(DocBuilder.Block.Heading("البنود"))
            b.add(DocBuilder.Block.Table(listOf("#", "الوصف"), listOf(listOf("1", "خدمة"))))
            val f = b.save(File(dir, "t${st.index}.pdf"))
            assertTrue(f.length() > 500)
        }
        Templates.choose(0)
    }

    @Test fun settingsShowsPlusCard() {
        launch("free", tab = "settings")
        waitTag("plusCard")
        rule.onNodeWithTag("plusCard").performClick()
        waitTag("paywall")
    }

    @Test fun schoolCodeIsCheckedOnTheServerAndOpensPlus() {
        com.muyassir.app.core.Store.saveObj("cloud", org.json.JSONObject())
        launch("off", paywall = "Menu")
        waitTag("paywall")
        rule.onNodeWithTag("schoolCode").apply { runCatching { performScrollTo() } }.performClick(); waitTag("schoolCodeField")
        rule.onNodeWithTag("schoolCodeField").performTextReplacement("MSR-AAAA-AAAA")
        rule.onNodeWithTag("redeemCode").performClick()
        try { waitText("الكود غير صحيح", 30_000) } finally { kotlinx.coroutines.runBlocking { runCatching { com.muyassir.app.core.Cloud.forgetMe() } } }
        assert(!Plus.active)
        // A licence the server confirmed: Plus opens and the paywall names the school.
        rule.runOnIdle { Plus.school = "مدارس الاختبار"; Plus.schoolUntil = System.currentTimeMillis() + 86_400_000L }
        waitTag("schoolActive"); waitText("مدارس الاختبار")
        assert(Plus.active)
        rule.runOnIdle { Plus.school = ""; Plus.schoolUntil = 0 }
    }
}
