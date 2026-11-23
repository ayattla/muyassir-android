package com.muyassir.app.engine

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Saudi cities for prayer times, offline (no location permission needed). */
object Cities {
    data class City(val id: String, val name: String, val lat: Double, val lng: Double)
    val all = listOf(
        City("riyadh", "الرياض", 24.7136, 46.6753), City("jeddah", "جدة", 21.4858, 39.1925), City("makkah", "مكة المكرمة", 21.4225, 39.8262),
        City("madinah", "المدينة المنورة", 24.4686, 39.6142), City("dammam", "الدمام", 26.4207, 50.0888), City("khobar", "الخبر", 26.2172, 50.1971),
        City("dhahran", "الظهران", 26.2361, 50.0393), City("qatif", "القطيف", 26.5196, 50.0115), City("jubail", "الجبيل", 27.0046, 49.6460),
        City("ahsa", "الأحساء", 25.3833, 49.5864), City("taif", "الطائف", 21.2703, 40.4158), City("buraidah", "بريدة", 26.3592, 43.9818),
        City("unaizah", "عنيزة", 26.0843, 43.9935), City("hail", "حائل", 27.5114, 41.7208), City("tabuk", "تبوك", 28.3835, 36.5662),
        City("abha", "أبها", 18.2164, 42.5053), City("khamis", "خميس مشيط", 18.3000, 42.7333), City("jazan", "جازان", 16.8892, 42.5511),
        City("najran", "نجران", 17.5656, 44.2289), City("baha", "الباحة", 20.0129, 41.4677), City("yanbu", "ينبع", 24.0895, 38.0618),
        City("arar", "عرعر", 30.9753, 41.0381), City("sakaka", "سكاكا", 29.9697, 40.2064), City("kharj", "الخرج", 24.1556, 47.3120),
        City("hafar", "حفر الباطن", 28.4328, 45.9708), City("qurayyat", "القريات", 31.3318, 37.3428), City("bisha", "بيشة", 20.0005, 42.6052),
        City("majmaah", "المجمعة", 25.9039, 45.3456), City("dawadmi", "الدوادمي", 24.5077, 44.3924), City("rabigh", "رابغ", 22.7986, 39.0349),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

/** Prayer times by the Umm al-Qura method used in Saudi Arabia: Fajr at 18.5°, Isha 90 minutes after Maghrib
 *  (120 in Ramadan), Asr at one shadow length, with the calendar's 2-minute precaution on Maghrib. Accurate to ±2 minutes. */
object PrayerTimes {
    val names = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
    private fun rad(d: Double) = d * Math.PI / 180
    private fun deg(r: Double) = r * 180 / Math.PI

    /** Minutes after local midnight for [fajr, sunrise, dhuhr, asr, maghrib, isha]. */
    fun minutes(y: Int, m: Int, d: Int, lat: Double, lng: Double, tz: Double = 3.0, ramadan: Boolean = false): List<Int> {
        val (y2, m2) = if (m <= 2) (y - 1) to (m + 12) else y to m
        val a = y2 / 100; val b = 2 - a + a / 4
        val jd = (365.25 * (y2 + 4716)).toInt() + (30.6001 * (m2 + 1)).toInt() + d + b - 1524.5 - lng / 360
        val n = jd - 2451545.0
        val g = (357.529 + 0.98560028 * n).mod(360.0); val q = (280.459 + 0.98564736 * n).mod(360.0)
        val l = (q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g))).mod(360.0)
        val e = 23.439 - 0.00000036 * n
        val ra = (deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15).mod(24.0)
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        val eqt = (q / 15 - ra + 12).mod(24.0) - 12
        val noon = 12 + tz - lng / 15 - eqt
        fun span(angle: Double) = deg(acos(((-sin(rad(angle)) - sin(rad(lat)) * sin(rad(decl))) / (cos(rad(lat)) * cos(rad(decl)))).coerceIn(-1.0, 1.0))) / 15
        val asrAngle = -deg(atan(1 / (1 + tan(rad(abs(lat - decl))))))
        val maghrib = noon + span(0.833) + 2.0 / 60
        val hours = listOf(noon - span(18.5), noon - span(0.833), noon + 1.0 / 60, noon + span(asrAngle), maghrib, maghrib + if (ramadan) 2.0 else 1.5)
        return hours.map { (it * 60).let { v -> Math.round(v).toInt() } }
    }

    fun today(city: Cities.City, now: Calendar = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh"))): List<Int> {
        val h = Hijri.toHijri(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        return minutes(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH), city.lat, city.lng, ramadan = h.m == 9)
    }

    /** The next of the five prayers (sunrise skipped): index into [names] and minutes left. After Isha, tomorrow's Fajr. */
    fun next(times: List<Int>, nowMin: Int, tomorrowFajr: Int): Pair<Int, Int> {
        for (i in listOf(0, 2, 3, 4, 5)) if (times[i] > nowMin) return i to (times[i] - nowMin)
        return 0 to (24 * 60 - nowMin + tomorrowFajr)
    }

    fun clock(min: Int): String { val h = (min / 60) % 24; return "${if (h % 12 == 0) 12 else h % 12}:${String.format(java.util.Locale.US, "%02d", min % 60)}" }
    fun left(min: Int): String = if (min < 60) "$min دقيقة" else "${min / 60}:${String.format(java.util.Locale.US, "%02d", min % 60)}"
}

/** Official monthly payment days (Gregorian), with the published weekend rules. */
object PayDays {
    data class Rule(val id: String, val title: String, val day: Int, val friday: Int, val saturday: Int, val source: String)
    // Government salaries: day 27; Friday → Thursday before, Saturday → Sunday after (Ministry of Finance).
    // Citizen Account: day 10; Friday → 9, Saturday → 11. Developed social security and pensions: day 1; Friday → Thursday before, Saturday → Sunday 2.
    val gov = Rule("gov", "راتب الدولة", 27, -1, +1, "وزارة المالية")
    val citizen = Rule("citizen", "حساب المواطن", 10, -1, +1, "برنامج حساب المواطن")
    val pension = Rule("pension", "التقاعد والضمان", 1, -1, +1, "المؤسسة العامة للتأمينات والضمان الاجتماعي")
    /** Dates the Ministry of Finance moved away from the rule (holidays), yyyy-mm → day. */
    private val overrides = mapOf("gov:2026-05" to 24)

    fun custom(day: Int) = Rule("custom", "راتبي", day.coerceIn(1, 31), 0, 0, "الموعد الذي حددته")

    /** The payment day for that month, as a calendar. */
    fun inMonth(r: Rule, y: Int, m: Int): GregorianCalendar {
        val c = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh")).apply { clear(); set(y, m - 1, 1) }
        overrides["${r.id}:$y-${String.format(java.util.Locale.US, "%02d", m)}"]?.let { c.set(Calendar.DAY_OF_MONTH, it); return c }
        c.set(Calendar.DAY_OF_MONTH, minOf(r.day, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        when (c.get(Calendar.DAY_OF_WEEK)) {
            Calendar.FRIDAY -> c.add(Calendar.DAY_OF_MONTH, r.friday)
            Calendar.SATURDAY -> c.add(Calendar.DAY_OF_MONTH, r.saturday)
        }
        return c
    }

    /** Days from [today] (midnight) to the next payment, and its date (today counts as 0). */
    fun next(r: Rule, today: Calendar = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh"))): Pair<Int, GregorianCalendar> {
        val t0 = midnight(today)
        var y = today.get(Calendar.YEAR); var m = today.get(Calendar.MONTH) + 1
        repeat(3) {
            val c = inMonth(r, y, m)
            val days = ((c.timeInMillis - t0) / 86_400_000L).toInt()
            if (days >= 0) return days to c
            m++; if (m == 13) { m = 1; y++ }
        }
        val c = inMonth(r, y, m); return ((c.timeInMillis - t0) / 86_400_000L).toInt() to c
    }

    private fun midnight(c: Calendar): Long = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh")).apply {
        clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
