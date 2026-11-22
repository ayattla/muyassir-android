package com.muyassir.app.engine

import java.util.Calendar
import java.util.TimeZone

/**
 * Versioned rules behind every Saudi calculator: the formula in words, the article, the official source,
 * and the date the rule was last checked against it. Countries other than Saudi Arabia are declared but not
 * enabled until their rules are reviewed (see [Country.enabled]).
 */
object SaudiRules {
    enum class Country(val label: String, val enabled: Boolean) { SA("السعودية", true), AE("الإمارات", false), KW("الكويت", false), QA("قطر", false), BH("البحرين", false), OM("عُمان", false) }

    data class Rule(val id: String, val title: String, val formula: String, val articles: String, val source: String, val sourceUrl: String, val reviewed: String)

    private const val LABOR = "https://www.hrsd.gov.sa/knowledge-centre/نظام-العمل"
    private const val GOSI = "https://awareness.gosi.gov.sa/pdf/Contributions.pdf"
    const val REVIEWED = "22 نوفمبر 2026"

    val rules = listOf(
        Rule("eos", "مكافأة نهاية الخدمة", "نصف أجر شهر عن كل سنة من الخمس الأولى، وأجر شهر عن كل سنة بعدها، وتُحسب كسور السنة بنسبتها. عند الاستقالة: لا شيء قبل سنتين، ثلث المكافأة من 2 إلى أقل من 5، ثلثاها من 5 إلى أقل من 10، وكاملة بعد 10 سنوات.", "المادتان 84 و85 من نظام العمل", "وزارة الموارد البشرية — نظام العمل", LABOR, REVIEWED),
        Rule("leave", "رصيد الإجازة السنوية", "21 يومًا سنويًا، و30 يومًا إذا أمضى العامل خمس سنوات متصلة لدى صاحب العمل. قيمة الرصيد = الأيام المتبقية × (الأجر الشهري ÷ 30)، ويستحق العامل أجر ما لم يتمتع به عند انتهاء العقد.", "المادتان 109 و111 من نظام العمل", "وزارة الموارد البشرية — نظام العمل", LABOR, REVIEWED),
        Rule("salary", "اشتراكات التأمينات الاجتماعية", "الأجر الخاضع = الأساسي + السكن (بحد أعلى 45,000 ريال). السعودي في النظام السابق: 9% معاشات + 0.75% ساند على الموظف. النظام الجديد (المسجلون لأول مرة من 3 يوليو 2024): المعاشات 9.5% من 1 يوليو 2025، و10% من 1 يوليو 2026، و10.5% من 1 يوليو 2027، و11% من 1 يوليو 2028، على كل من الموظف وصاحب العمل، إضافة إلى ساند 0.75%. غير السعودي: 2% أخطار مهنية على صاحب العمل فقط.", "نظام التأمينات الاجتماعية (م/273)", "المؤسسة العامة للتأمينات الاجتماعية — نسب الاشتراك", GOSI, REVIEWED),
        Rule("overtime", "أجر العمل الإضافي", "أجر الساعة الإضافية = أجر الساعة الفعلي + 50% من أجر الساعة الأساسي. أجر الساعة = الأجر الشهري ÷ 30 ÷ ساعات العمل اليومية.", "المادة 107 من نظام العمل", "وزارة الموارد البشرية — نظام العمل", LABOR, REVIEWED),
        Rule("service", "مدة الخدمة", "الفرق بين تاريخ المباشرة وتاريخ انتهاء العلاقة بالسنوات والأشهر والأيام، وهو أساس المكافأة والإجازة والتعويض.", "المادة 84 (حساب المدة)", "وزارة الموارد البشرية — نظام العمل", LABOR, REVIEWED),
        Rule("notice", "التعويض عن مهلة الإشعار", "في العقد غير محدد المدة وأجره شهري: مهلة 30 يومًا إن أنهاه العامل، و60 يومًا إن أنهاه صاحب العمل؛ ولغير الشهري 30 يومًا. من لم يلتزم بالمهلة يدفع للطرف الآخر أجر العامل عن المهلة أو ما تبقى منها. العقد محدد المدة لا مهلة إشعار فيه.", "المادتان 75 و76 من نظام العمل (بعد تعديل م/44 النافذ في 19 فبراير 2025)", "وزارة الموارد البشرية — نظام العمل", LABOR, REVIEWED),
        Rule("art77", "تعويض الإنهاء لسبب غير مشروع", "ما لم يحدد العقد تعويضًا: أجر 15 يومًا عن كل سنة خدمة في العقد غير محدد المدة، وأجر المدة الباقية في العقد محدد المدة، وبحد أدنى أجر شهرين في الحالتين. يُضاف إلى المكافأة ولا يحل محلها.", "المادة 77 من نظام العمل", "وزارة الموارد البشرية — نظام العمل", LABOR, REVIEWED),
        Rule("deduct", "الاستقطاع من الأجر", "لا يجوز أن يزيد ما يُستقطع من أجر العامل وفاءً للقروض والجزاءات ونحوها على نصف الأجر المستحق. للنظام استثناءات محددة (مثل دين النفقة) تُراجع فيها المادة نفسها.", "المادة 92 من نظام العمل", "وزارة الموارد البشرية — نظام العمل", LABOR, REVIEWED),
        Rule("vat", "ضريبة القيمة المضافة", "النسبة الأساسية 15%. السعر شامل الضريبة = السعر × 1.15، والضريبة من سعر شامل = السعر × 15 ÷ 115.", "نظام ضريبة القيمة المضافة", "هيئة الزكاة والضريبة والجمارك", "https://zatca.gov.sa", REVIEWED),
    ).associateBy { it.id }

    // ---- GOSI: the new-system pension share by date (official schedule) ----
    private fun date(y: Int, m: Int, d: Int) = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh")).apply { clear(); set(y, m - 1, d) }.timeInMillis
    fun newPensionRate(at: Long = System.currentTimeMillis()): Double = when {
        at < date(2025, 7, 1) -> 9.0
        at < date(2026, 7, 1) -> 9.5
        at < date(2027, 7, 1) -> 10.0
        at < date(2028, 7, 1) -> 10.5
        else -> 11.0
    }

    // ---- service span (30-day months, as used for labour entitlements) ----
    data class Span(val years: Int, val months: Int, val days: Int, val totalDays: Long) { val asYears get() = totalDays / 365.0 }
    fun service(start: Long, end: Long): Span {
        val a = Calendar.getInstance().apply { timeInMillis = minOf(start, end) }; val b = Calendar.getInstance().apply { timeInMillis = maxOf(start, end) }
        var y = b.get(Calendar.YEAR) - a.get(Calendar.YEAR); var m = b.get(Calendar.MONTH) - a.get(Calendar.MONTH); var d = b.get(Calendar.DAY_OF_MONTH) - a.get(Calendar.DAY_OF_MONTH)
        if (d < 0) { m -= 1; d += a.getActualMaximum(Calendar.DAY_OF_MONTH) }
        if (m < 0) { y -= 1; m += 12 }
        return Span(y, m, d, (b.timeInMillis - a.timeInMillis) / 86_400_000L)
    }

    // ---- notice (art. 75/76) ----
    enum class Contract { OPEN, FIXED }
    enum class By { EMPLOYER, WORKER }
    data class Notice(val requiredDays: Int, val missingDays: Int, val amount: Double)
    fun notice(contract: Contract, by: By, monthlyPaid: Boolean, givenDays: Int, monthlyWage: Double): Notice {
        if (contract == Contract.FIXED) return Notice(0, 0, 0.0)
        val req = if (!monthlyPaid) 30 else if (by == By.EMPLOYER) 60 else 30
        val miss = (req - givenDays).coerceAtLeast(0)
        return Notice(req, miss, miss * monthlyWage / 30)
    }

    // ---- art. 77 ----
    fun art77(contract: Contract, serviceYears: Double, remainingMonths: Double, monthlyWage: Double): Double {
        val raw = if (contract == Contract.OPEN) serviceYears * monthlyWage / 2 else remainingMonths * monthlyWage
        return maxOf(raw, monthlyWage * 2)
    }

    // ---- art. 92 ----
    data class Deduct(val requested: Double, val allowed: Double, val cap: Double)
    fun deduction(monthlyWage: Double, requested: Double): Deduct {
        val cap = monthlyWage / 2
        return Deduct(requested, minOf(requested, cap), cap)
    }

    // ---- final settlement ----
    enum class Reason(val label: String) {
        CONTRACT_END("انتهاء مدة العقد"), EMPLOYER_LAWFUL("إنهاء من صاحب العمل بسبب مشروع (مادة 75)"),
        EMPLOYER_UNLAWFUL("إنهاء من صاحب العمل دون سبب مشروع"), RESIGNATION("استقالة العامل"),
        WORKER_ART81("ترك العامل العمل وفق المادة 81"), ART80("فسخ صاحب العمل وفق المادة 80"),
    }
    data class Line(val label: String, val amount: Double, val note: String)
    data class Settlement(val lines: List<Line>) { val total get() = lines.sumOf { it.amount } }

    fun settlement(
        wage: Double, start: Long, end: Long, reason: Reason, contract: Contract,
        unpaidDays: Double, leaveDays: Double, noticeGivenDays: Int, remainingMonths: Double, monthlyPaid: Boolean = true,
    ): Settlement {
        val s = service(start, end); val years = s.asYears
        val out = ArrayList<Line>()
        if (unpaidDays > 0) out += Line("أجر الأيام غير المدفوعة", unpaidDays * wage / 30, "${fmtN(unpaidDays)} يوم × الأجر ÷ 30")
        if (leaveDays > 0) out += Line("بدل رصيد الإجازة", leaveDays * wage / 30, "المادة 111: ${fmtN(leaveDays)} يوم")
        val full = minOf(years, 5.0) * wage / 2 + maxOf(years - 5, 0.0) * wage
        val share = when (reason) {
            Reason.ART80 -> 0.0
            Reason.RESIGNATION -> when { years < 2 -> 0.0; years < 5 -> 1.0 / 3; years < 10 -> 2.0 / 3; else -> 1.0 }
            else -> 1.0
        }
        out += Line("مكافأة نهاية الخدمة", full * share, if (share < 1) "المادة 85: ${if (share == 0.0) "لا تستحق" else if (share < 0.5) "الثلث" else "الثلثان"}" else "المادة 84: كاملة")
        when (reason) {
            Reason.EMPLOYER_LAWFUL -> notice(contract, By.EMPLOYER, monthlyPaid, noticeGivenDays, wage).takeIf { it.amount > 0 }?.let { out += Line("تعويض مهلة الإشعار", it.amount, "المادة 76: ${it.missingDays} يومًا من ${it.requiredDays}") }
            Reason.EMPLOYER_UNLAWFUL -> {
                out += Line("تعويض الإنهاء غير المشروع", art77(contract, years, remainingMonths, wage), "المادة 77، بحد أدنى أجر شهرين")
                notice(contract, By.EMPLOYER, monthlyPaid, noticeGivenDays, wage).takeIf { it.amount > 0 }?.let { out += Line("تعويض مهلة الإشعار", it.amount, "المادة 76") }
            }
            else -> {}
        }
        return Settlement(out)
    }

    private fun fmtN(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)
}
