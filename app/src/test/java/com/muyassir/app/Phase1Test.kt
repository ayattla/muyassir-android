package com.muyassir.app

import com.muyassir.app.core.Assistant
import com.muyassir.app.core.Recipes
import com.muyassir.app.engine.Calc
import com.muyassir.app.engine.DateFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class Phase1Test {
    @Test fun zakatAboveAndBelowNisab() {
        val z = Calc.zakat(100_000.0, 0.0, 0.0, 0.0, 0.0, 0.0, nisab = 85 * 400.0)
        assertTrue(z.reached); assertEquals(2500.0, z.due, 0.001)
        val low = Calc.zakat(10_000.0, 0.0, 0.0, 0.0, 0.0, 0.0, nisab = 85 * 400.0)
        assertFalse(low.reached); assertEquals(0.0, low.due, 0.0)
        assertEquals(0.0, Calc.zakat(50_000.0, 0.0, 0.0, 0.0, 0.0, 30_000.0, nisab = 34_000.0).due, 0.0)
    }

    @Test fun overtimeArticle107() {
        // basic 2,400 + allowances 1,800, 8 h/day, 3 hours: 17.50 + 5 = 22.50 per hour, 67.50 total
        val o = Calc.overtime(2400.0, 1800.0, 8.0, 3.0)
        assertEquals(17.5, o.hourly, 0.001); assertEquals(22.5, o.perHour, 0.001); assertEquals(67.5, o.total, 0.001)
    }

    @Test fun electricityTiers() {
        val p = Calc.electricity(7000.0, 18.0, 30.0)
        assertEquals(1080.0, p.tier1, 0.001); assertEquals(300.0, p.tier2, 0.001); assertEquals(1380.0 * 1.15, p.total, 0.01)
        val n = Calc.electricity(1000.0, 21.0, 33.0, fixed = 10.0)
        assertEquals((210.0 + 10.0) * 1.15, n.total, 0.01)
    }

    @Test fun dateSpan() {
        val a = Calendar.getInstance().apply { clear(); set(2000, 0, 31) }.timeInMillis
        val b = Calendar.getInstance().apply { clear(); set(2026, 10, 21) }.timeInMillis
        val s = Calc.span(a, b)
        assertEquals(26, s.years); assertEquals(9, s.months); assertEquals(21, s.days)
    }

    @Test fun findsGregorianDates() {
        val f = DateFinder.find("تاريخ الإصدار 2021/03/15 تاريخ الانتهاء 15-03-2031")
        assertEquals(2, f.size)
        val e = DateFinder.expiry("Issue 01.02.2020 Expiry ٢٠٣٠/٠٢/٠١")!!
        val c = Calendar.getInstance().apply { timeInMillis = e.millis }
        assertEquals(2030, c.get(Calendar.YEAR)); assertEquals(1, c.get(Calendar.MONTH))
    }

    @Test fun recipesDescribeAndMatch() {
        assertEquals(6, Recipes.builtIn.size)
        assertTrue(Recipes.forKinds(setOf("image")).any { it.id == "doc" })
        assertTrue(Recipes.forKinds(setOf("pdf")).none { it.input == "image" })
        assertNotNull(Recipes.describe(Recipes.builtIn.first().steps))
        assertEquals("doc", Assistant.recipeFor(listOf("img_pdf", "pdf_compress")))
        assertEquals("text", Assistant.recipeFor(listOf("ocr")))
    }
}
