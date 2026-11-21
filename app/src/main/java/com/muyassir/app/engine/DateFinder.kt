package com.muyassir.app.engine

import java.util.Calendar

/**
 * Finds dates in OCR text from IDs, passports, licences and appointment cards: yyyy/mm/dd, dd/mm/yyyy (or with - .),
 * Gregorian or Hijri (years 1350–1500 are read as Hijri, Umm al-Qura). Arabic-Indic digits are accepted.
 */
object DateFinder {
    data class Found(val millis: Long, val text: String, val hijri: Boolean)

    private fun digits(s: String) = s.map { c -> when (c) { in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰'); else -> c } }.joinToString("")

    fun find(raw: String): List<Found> {
        val s = digits(raw)
        val out = ArrayList<Found>()
        val ymd = Regex("""\b(1[34]\d\d|19\d\d|20\d\d)[/\-.](\d{1,2})[/\-.](\d{1,2})\b""")
        val dmy = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](1[34]\d\d|19\d\d|20\d\d)\b""")
        ymd.findAll(s).forEach { m -> make(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.value)?.let { out += it } }
        dmy.findAll(s).forEach { m -> make(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt(), m.value)?.let { out += it } }
        return out.distinctBy { it.millis }.sortedBy { it.millis }
    }

    private fun make(y: Int, m: Int, d: Int, text: String): Found? {
        if (m !in 1..12 || d !in 1..31) return null
        val hijri = y in 1350..1500
        val (gy, gm, gd) = if (hijri) Hijri.toGregorian(y, m, d).let { Triple(it.y, it.m, it.d) } else Triple(y, m, d)
        val c = Calendar.getInstance().apply { clear(); set(gy, gm - 1, gd, 9, 0) }
        return Found(c.timeInMillis, text, hijri)
    }

    /** The most likely expiry: the latest date found (issue and birth dates come earlier). */
    fun expiry(raw: String): Found? = find(raw).maxByOrNull { it.millis }
}
