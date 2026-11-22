package com.muyassir.app

import com.muyassir.app.engine.SaudiRules
import com.muyassir.app.engine.SaudiRules.Contract
import com.muyassir.app.engine.SaudiRules.Reason
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Hand-worked examples for every Saudi calculator rule (Labor Law arts. 75–77, 84–85, 92, 107, 109–111; GOSI M/273). */
class SaudiRulesTest {
    private fun d(y: Int, m: Int, day: Int) = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh")).apply { clear(); set(y, m - 1, day, 12, 0) }.timeInMillis
    private fun near(e: Double, a: Double) = assertEquals(e, a, 0.01)

    @Test fun gosiScheduleFollowsOfficialDates() {
        assertEquals(9.0, SaudiRules.newPensionRate(d(2025, 6, 30)), 0.0)
        assertEquals(9.5, SaudiRules.newPensionRate(d(2025, 7, 1)), 0.0)
        assertEquals(10.0, SaudiRules.newPensionRate(d(2026, 11, 22)), 0.0)
        assertEquals(10.5, SaudiRules.newPensionRate(d(2027, 7, 2)), 0.0)
        assertEquals(11.0, SaudiRules.newPensionRate(d(2030, 1, 1)), 0.0)
    }

    @Test fun serviceSpan() {
        val s = SaudiRules.service(d(2020, 1, 15), d(2026, 3, 10))
        assertEquals(6, s.years); assertEquals(1, s.months); assertEquals(24, s.days)
    }

    @Test fun noticeArticles75and76() {
        // Employer ends an open monthly contract with 20 days' notice: 40 of 60 days are owed.
        val n = SaudiRules.notice(Contract.OPEN, SaudiRules.By.EMPLOYER, true, 20, 9000.0)
        assertEquals(60, n.requiredDays); assertEquals(40, n.missingDays); near(12000.0, n.amount)
        assertEquals(30, SaudiRules.notice(Contract.OPEN, SaudiRules.By.WORKER, true, 0, 9000.0).requiredDays)
        assertEquals(0.0, SaudiRules.notice(Contract.FIXED, SaudiRules.By.EMPLOYER, true, 0, 9000.0).amount, 0.0)
    }

    @Test fun article77MinimumTwoMonths() {
        near(20000.0, SaudiRules.art77(Contract.OPEN, 1.0, 0.0, 10000.0))       // 15 days = 5,000 → minimum 2 months
        near(50000.0, SaudiRules.art77(Contract.OPEN, 10.0, 0.0, 10000.0))      // 10 years × 15 days
        near(70000.0, SaudiRules.art77(Contract.FIXED, 3.0, 7.0, 10000.0))      // 7 remaining months
    }

    @Test fun deductionCapIsHalf() { val r = SaudiRules.deduction(8000.0, 6000.0); near(4000.0, r.allowed) }

    @Test fun finalSettlementResignationAfterSixYears() {
        val s = SaudiRules.settlement(10000.0, d(2020, 1, 1), d(2026, 1, 1), Reason.RESIGNATION, Contract.OPEN, 10.0, 15.0, 30, 0.0)
        val eos = s.lines.first { it.label == "مكافأة نهاية الخدمة" }.amount
        // 6 years: 5 × 5,000 + 1 × 10,000 = 35,000 (≈, by days); resignation 5–10 years → two thirds.
        assertEquals(35000.0 * 2 / 3, eos, 60.0)
        near(10 * 10000 / 30.0, s.lines.first { it.label.startsWith("أجر الأيام") }.amount)
        near(15 * 10000 / 30.0, s.lines.first { it.label == "بدل رصيد الإجازة" }.amount)
    }

    @Test fun unlawfulTerminationAddsArticle77AndNotice() {
        val s = SaudiRules.settlement(6000.0, d(2024, 1, 1), d(2026, 1, 1), Reason.EMPLOYER_UNLAWFUL, Contract.OPEN, 0.0, 0.0, 0, 0.0)
        assertTrue(s.lines.any { it.label == "تعويض الإنهاء غير المشروع" && it.amount >= 12000.0 })
        assertTrue(s.lines.any { it.label == "تعويض مهلة الإشعار" && kotlin.math.abs(it.amount - 12000.0) < 0.01 })
    }

    @Test fun article80Forfeits() {
        val s = SaudiRules.settlement(6000.0, d(2015, 1, 1), d(2026, 1, 1), Reason.ART80, Contract.OPEN, 0.0, 0.0, 0, 0.0)
        assertEquals(0.0, s.lines.first { it.label == "مكافأة نهاية الخدمة" }.amount, 0.0)
    }

    @Test fun everyRuleHasSourceAndDate() {
        SaudiRules.rules.values.forEach { r -> assertTrue(r.id, r.sourceUrl.startsWith("https://") && r.reviewed.isNotBlank() && r.formula.isNotBlank()) }
        assertTrue(SaudiRules.Country.values().filter { it.enabled } == listOf(SaudiRules.Country.SA))
    }
}
