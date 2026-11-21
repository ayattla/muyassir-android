package com.muyassir.app.engine

import android.icu.util.Calendar
import android.icu.util.GregorianCalendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import android.icu.util.ULocale

/** Umm al-Qura conversions using the system ICU calendar (the official Saudi calendar). */
object Hijri {
    val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    val gMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    val days = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    private fun islamic() = IslamicCalendar(TimeZone.getTimeZone("Asia/Riyadh"), ULocale("ar_SA")).apply {
        calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
    }

    data class D(val y: Int, val m: Int, val d: Int, val weekday: Int)

    fun toHijri(y: Int, m: Int, d: Int): D {
        val g = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh")); g.clear(); g.set(y, m - 1, d, 12, 0)
        val h = islamic(); h.timeInMillis = g.timeInMillis
        return D(h.get(Calendar.YEAR), h.get(Calendar.MONTH) + 1, h.get(Calendar.DAY_OF_MONTH), g.get(Calendar.DAY_OF_WEEK))
    }

    fun toGregorian(y: Int, m: Int, d: Int): D {
        val h = islamic(); h.clear(); h.set(y, m - 1, d, 12, 0)
        val g = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh")); g.timeInMillis = h.timeInMillis
        return D(g.get(Calendar.YEAR), g.get(Calendar.MONTH) + 1, g.get(Calendar.DAY_OF_MONTH), g.get(Calendar.DAY_OF_WEEK))
    }

    fun fromMillis(ms: Long): D { val g = GregorianCalendar().apply { timeInMillis = ms }; return toHijri(g.get(Calendar.YEAR), g.get(Calendar.MONTH) + 1, g.get(Calendar.DAY_OF_MONTH)) }
    fun today(): D { val g = GregorianCalendar(); return toHijri(g.get(Calendar.YEAR), g.get(Calendar.MONTH) + 1, g.get(Calendar.DAY_OF_MONTH)) }
    fun monthLength(y: Int, m: Int): Int { val h = islamic(); h.clear(); h.set(y, m - 1, 1); return h.getActualMaximum(Calendar.DAY_OF_MONTH) }
}
