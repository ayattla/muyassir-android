package com.muyassir.app

import com.muyassir.app.core.Plus
import com.muyassir.app.core.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The free / Plus rules from the monetization plan. */
class PlusTest {
    private val mb = 1024L * 1024L

    @Before fun reset() = Plus.resetForTest()

    @Test fun beforeLaunchEverythingIsOpen() {
        Plus.testState("off")
        assertTrue(Plus.unlocked)
        assertNull(Plus.check("pdf_merge", List(60) { 300 * mb }))
        repeat(20) { Plus.counted("pdf_merge") }
        assertNull(Plus.check("pdf_merge", listOf(mb)))
        assertEquals(Int.MAX_VALUE, Plus.ocrPages)
    }

    @Test fun freeLimits() {
        Plus.testState("free")
        assertFalse(Plus.unlocked)
        assertNull(Plus.check("pdf_merge", List(5) { 20 * mb }))
        assertEquals(Plus.Reason.Files, Plus.check("pdf_merge", List(6) { mb }))
        assertEquals(Plus.Reason.Size, Plus.check("img_compress", listOf(21 * mb)))
        assertEquals(3, Plus.ocrPages)
        assertEquals(0, Templates.current.index)
    }

    @Test fun fiveOperationsPerDay() {
        Plus.testState("free")
        repeat(Plus.FREE_OPS_PER_DAY) { assertNull(Plus.check("pdf_compress", listOf(mb))); Plus.counted("pdf_compress") }
        assertEquals(Plus.Reason.Daily, Plus.check("pdf_compress", listOf(mb)))
        assertEquals(0, Plus.opsLeft)
    }

    @Test fun unmeteredToolsAreAlwaysFree() {
        Plus.testState("free-used")
        for (id in listOf("vat", "salary", "gpa", "notes", "qr_make", "cv", "invoice", "scientific")) {
            assertNull(Plus.check(id, listOf(900 * mb)))
            assertFalse(id in Plus.metered)
        }
    }

    @Test fun plusLimits() {
        Plus.testState("plus")
        assertTrue(Plus.unlocked)
        repeat(50) { Plus.counted("pdf_merge") }
        assertNull(Plus.check("pdf_merge", List(100) { 400 * mb }))
        assertEquals(Plus.Reason.Files, Plus.check("pdf_merge", List(101) { mb }))
        assertEquals(Plus.Reason.Size, Plus.check("pdf_merge", listOf(501 * mb)))
    }

    @Test fun dayPassLasts24Hours() {
        Plus.testState("free")
        val now = System.currentTimeMillis()
        Plus.grantDayPass(now)
        assertTrue(Plus.active)
        Plus.resetForTest(); Plus.testState("free")
        Plus.grantDayPass(now - 25 * 3600_000L)
        assertFalse(Plus.active)
    }

    @Test fun gatesShowPaywallOnlyForFree() {
        Plus.testState("free")
        assertFalse(Plus.allow(Plus.Reason.Template)); assertEquals(Plus.Reason.Template, Plus.paywall)
        Plus.paywall = null; Plus.testState("plus")
        assertTrue(Plus.allow(Plus.Reason.Template)); assertNull(Plus.paywall)
    }

    @Test fun twentyFourTemplates() {
        assertEquals(24, Templates.all.size)
        assertEquals(24, Templates.all.map { it.layout to it.accent }.toSet().size)
    }
}
