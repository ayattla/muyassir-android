package com.muyassir.app.engine

import kotlin.math.pow

/**
 * Pure calculation rules used by the Saudi, student and finance tools.
 * Kept free of Android types so they are covered by plain unit tests.
 */
object Calc {
    const val VAT_RATE = 15.0 // ZATCA standard rate

    data class Vat(val net: Double, val vat: Double, val total: Double)

    fun vat(amount: Double, rate: Double = VAT_RATE, inclusive: Boolean): Vat =
        if (inclusive) { val net = amount / (1 + rate / 100); Vat(net, amount - net, amount) }
        else { val v = amount * rate / 100; Vat(amount, v, amount + v) }

    // ---- GOSI (social insurance) ----
    enum class GosiSystem { LEGACY, NEW }

    data class Gosi(val base: Double, val employee: Double, val employer: Double, val employeeRate: Double, val employerRate: Double)

    /**
     * Contribution base = basic + housing, capped at 45,000 SAR (floor 1,500 for Saudis).
     * Saudi, legacy system (registered before 3 Jul 2024): employee 9% pension + 0.75% SANED; employer 9% + 0.75% + 2% hazards.
     * Saudi, new system (law M/273): pension share rises 0.5% every July — `newPension` is the current share (9.5% in 2026).
     * Non-Saudi: employer pays 2% occupational hazards only.
     */
    fun gosi(basic: Double, housing: Double, saudi: Boolean, system: GosiSystem = GosiSystem.LEGACY, newPension: Double = 9.5): Gosi {
        var base = (basic + housing).coerceAtMost(45000.0)
        if (saudi) base = base.coerceAtLeast(1500.0)
        if (!saudi) return Gosi(base, 0.0, base * 0.02, 0.0, 2.0)
        val pension = if (system == GosiSystem.LEGACY) 9.0 else newPension
        val emp = pension + 0.75
        val er = pension + 0.75 + 2.0
        return Gosi(base, base * emp / 100, base * er / 100, emp, er)
    }

    data class Salary(val gross: Double, val gosi: Double, val net: Double)

    fun salary(basic: Double, housing: Double, transport: Double, other: Double, saudi: Boolean, system: GosiSystem, otherDeductions: Double = 0.0): Salary {
        val gross = basic + housing + transport + other
        val g = gosi(basic, housing, saudi, system).employee
        return Salary(gross, g, gross - g - otherDeductions)
    }

    // ---- End of service award (Saudi Labor Law art. 84 & 85) ----
    enum class Leaving { TERMINATION, RESIGNATION }

    data class Eos(val years: Double, val fullAward: Double, val payable: Double, val share: Double)

    fun endOfService(monthlyWage: Double, years: Int, months: Int, days: Int, how: Leaving): Eos {
        val total = years + months / 12.0 + days / 365.0
        val first = total.coerceAtMost(5.0)
        val rest = (total - 5.0).coerceAtLeast(0.0)
        val full = first * monthlyWage / 2 + rest * monthlyWage
        val share = when {
            how == Leaving.TERMINATION -> 1.0
            total < 2 -> 0.0
            total < 5 -> 1.0 / 3
            total < 10 -> 2.0 / 3
            else -> 1.0
        }
        return Eos(total, full, full * share, share)
    }

    // ---- Annual leave (art. 109): 21 days, 30 days after five years of service ----
    data class Leave(val entitlement: Int, val accrued: Double, val remaining: Double, val value: Double)

    fun leave(serviceYears: Double, monthsThisYear: Double, usedDays: Double, monthlyWage: Double): Leave {
        val ent = if (serviceYears >= 5) 30 else 21
        val accrued = ent * (monthsThisYear.coerceIn(0.0, 12.0) / 12.0)
        val remaining = (accrued - usedDays).coerceAtLeast(0.0)
        return Leave(ent, accrued, remaining, remaining * monthlyWage / 30)
    }

    // ---- Financing ----
    data class Loan(val monthly: Double, val totalPaid: Double, val profit: Double, val apr: Double)

    /** Flat rate as quoted by most Saudi personal-finance offers: profit = principal × rate × years. */
    fun flatLoan(principal: Double, annualFlat: Double, months: Int): Loan {
        val profit = principal * annualFlat / 100 * months / 12.0
        val total = principal + profit
        return Loan(total / months, total, profit, aprFromPayment(principal, total / months, months))
    }

    /** Reducing balance (amortised) loan. */
    fun reducingLoan(principal: Double, annualRate: Double, months: Int): Loan {
        val i = annualRate / 100 / 12
        val pay = if (i == 0.0) principal / months else principal * i / (1 - (1 + i).pow(-months))
        return Loan(pay, pay * months, pay * months - principal, annualRate)
    }

    /** Effective annual rate that makes `payment` amortise `principal` over `months` (bisection). */
    fun aprFromPayment(principal: Double, payment: Double, months: Int): Double {
        if (payment * months <= principal) return 0.0
        var lo = 0.0; var hi = 1.0
        repeat(100) {
            val mid = (lo + hi) / 2
            val p = principal * mid / (1 - (1 + mid).pow(-months))
            if (p > payment) hi = mid else lo = mid
        }
        return lo * 12 * 100
    }

    data class Discount(val saving: Double, val final: Double, val effectivePct: Double)

    fun discount(price: Double, vararg pcts: Double): Discount {
        var p = price
        pcts.forEach { p *= (1 - it / 100) }
        return Discount(price - p, p, if (price == 0.0) 0.0 else (price - p) / price * 100)
    }

    // ---- Grades ----
    data class Course(val points: Double, val hours: Double)

    fun gpa(courses: List<Course>, prevGpa: Double = 0.0, prevHours: Double = 0.0): Pair<Double, Double> {
        val h = courses.sumOf { it.hours }
        val sem = if (h == 0.0) 0.0 else courses.sumOf { it.points * it.hours } / h
        val totalH = h + prevHours
        val cum = if (totalH == 0.0) 0.0 else (sem * h + prevGpa * prevHours) / totalH
        return sem to cum
    }

    /** Letter grade → points on the 5-point (most Saudi universities) or 4-point scale. */
    val letters = listOf("A+", "A", "B+", "B", "C+", "C", "D+", "D", "F")
    fun points(letter: String, scale5: Boolean): Double {
        val five = mapOf("A+" to 5.0, "A" to 4.75, "B+" to 4.5, "B" to 4.0, "C+" to 3.5, "C" to 3.0, "D+" to 2.5, "D" to 2.0, "F" to 1.0)
        val four = mapOf("A+" to 4.0, "A" to 3.75, "B+" to 3.5, "B" to 3.0, "C+" to 2.5, "C" to 2.0, "D+" to 1.5, "D" to 1.0, "F" to 0.0)
        return (if (scale5) five else four)[letter] ?: 0.0
    }

    fun gradeWord(pct: Double): String = when {
        pct >= 90 -> "ممتاز"; pct >= 80 -> "جيد جدًا"; pct >= 70 -> "جيد"; pct >= 60 -> "مقبول"; else -> "راسب"
    }

    // ---- Zakat: 2.5% of zakatable wealth held a full lunar year, when it reaches the nisab ----
    data class Zakat(val wealth: Double, val nisab: Double, val due: Double, val reached: Boolean)

    /** [nisab] = value of 85 g of gold (or 595 g of silver) at today's price, entered by the user. */
    fun zakat(cash: Double, gold: Double, silver: Double, trade: Double, receivables: Double, debts: Double, nisab: Double): Zakat {
        val w = (cash + gold + silver + trade + receivables - debts).coerceAtLeast(0.0)
        val reached = nisab > 0 && w >= nisab
        return Zakat(w, nisab, if (reached) w * 0.025 else 0.0, reached)
    }

    // ---- Overtime, Saudi Labor Law article 107: hourly wage + 50% of the basic hourly wage ----
    data class Overtime(val hourly: Double, val basicHourly: Double, val perHour: Double, val total: Double)

    fun overtime(basic: Double, allowances: Double, dailyHours: Double, hours: Double): Overtime {
        val dh = if (dailyHours > 0) dailyHours else 8.0
        val hourly = (basic + allowances) / 30.0 / dh
        val basicHourly = basic / 30.0 / dh
        val per = hourly + basicHourly * 0.5
        return Overtime(hourly, basicHourly, per, per * hours)
    }

    // ---- Residential electricity bill estimate (Saudi Electricity, two tiers at 6,000 kWh) ----
    data class Power(val tier1: Double, val tier2: Double, val fixed: Double, val vat: Double, val total: Double)

    /** [rate1]/[rate2] in halalas per kWh; [fixed] = the monthly fixed fee from the bill, in riyals. */
    fun electricity(kwh: Double, rate1: Double, rate2: Double, fixed: Double = 0.0, vatRate: Double = VAT_RATE): Power {
        val t1 = minOf(kwh, 6000.0).coerceAtLeast(0.0) * rate1 / 100.0
        val t2 = (kwh - 6000.0).coerceAtLeast(0.0) * rate2 / 100.0
        val net = t1 + t2 + fixed
        val v = net * vatRate / 100.0
        return Power(t1, t2, fixed, v, net + v)
    }

    // ---- Difference between two dates ----
    data class Span(val years: Int, val months: Int, val days: Int, val totalDays: Long)

    fun span(fromMillis: Long, toMillis: Long): Span {
        val a = java.util.Calendar.getInstance().apply { timeInMillis = minOf(fromMillis, toMillis) }
        val b = java.util.Calendar.getInstance().apply { timeInMillis = maxOf(fromMillis, toMillis) }
        var y = b.get(java.util.Calendar.YEAR) - a.get(java.util.Calendar.YEAR)
        var m = b.get(java.util.Calendar.MONTH) - a.get(java.util.Calendar.MONTH)
        var d = b.get(java.util.Calendar.DAY_OF_MONTH) - a.get(java.util.Calendar.DAY_OF_MONTH)
        if (d < 0) { m -= 1; val prev = b.clone() as java.util.Calendar; prev.add(java.util.Calendar.MONTH, -1); d += prev.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) }
        if (m < 0) { y -= 1; m += 12 }
        val total = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(b.timeInMillis - a.timeInMillis)
        return Span(y, m, d, total)
    }
}
