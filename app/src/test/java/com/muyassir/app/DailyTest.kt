package com.muyassir.app

import com.muyassir.app.engine.Cities
import com.muyassir.app.engine.PayDays
import com.muyassir.app.engine.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class DailyTest {
    private fun at(y: Int, m: Int, d: Int) = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh")).apply { clear(); set(y, m - 1, d) }

    @Test fun riyadhMatchesUmmAlQura() {
        // Published Umm al-Qura times for Riyadh, 19 Nov 2026: Dhuhr 11:40, Maghrib 17:07, Isha 18:37.
        val r = Cities.byId("riyadh")
        val t = PrayerTimes.minutes(2026, 11, 19, r.lat, r.lng)
        assertTrue("dhuhr ${t[2]}", kotlin.math.abs(t[2] - (11 * 60 + 40)) <= 1)
        assertTrue("maghrib ${t[4]}", kotlin.math.abs(t[4] - (17 * 60 + 7)) <= 1)
        assertTrue("isha ${t[5]}", kotlin.math.abs(t[5] - (18 * 60 + 37)) <= 1)
        assertTrue(t[0] < t[1] && t[1] < t[2] && t[2] < t[3] && t[3] < t[4] && t[4] < t[5])
        // Ramadan: Isha two hours after Maghrib.
        val ram = PrayerTimes.minutes(2026, 11, 19, r.lat, r.lng, ramadan = true)
        assertEquals(120, ram[5] - ram[4])
    }

    @Test fun nextPrayerWrapsToTomorrowsFajr() {
        val t = listOf(300, 375, 700, 885, 1027, 1117)
        assertEquals(4 to 27, PrayerTimes.next(t, 1000, 301))
        assertEquals(0 to (1440 - 1200 + 301), PrayerTimes.next(t, 1200, 301))
        assertEquals("6:39", PrayerTimes.clock(1119)); assertEquals("38 دقيقة", PrayerTimes.left(38)); assertEquals("1:05", PrayerTimes.left(65))
    }

    @Test fun governmentSalaryFollowsTheMinistryTable() {
        // Ministry of Finance 2026: 26 Mar (27 is Friday), 27 Apr, 24 May (holiday), 28 Jun (27 is Saturday), 26 Nov, 27 Dec.
        fun day(m: Int) = PayDays.inMonth(PayDays.gov, 2026, m).get(Calendar.DAY_OF_MONTH)
        assertEquals(26, day(3)); assertEquals(27, day(4)); assertEquals(24, day(5)); assertEquals(28, day(6)); assertEquals(26, day(11)); assertEquals(27, day(12))
    }

    @Test fun citizenAccountAndCountdown() {
        // 10 Oct 2026 is a Saturday, so the deposit moves to Sunday 11 Oct.
        assertEquals(11, PayDays.inMonth(PayDays.citizen, 2026, 10).get(Calendar.DAY_OF_MONTH))
        assertEquals(10, PayDays.inMonth(PayDays.citizen, 2026, 12).get(Calendar.DAY_OF_MONTH))
        val (d, c) = PayDays.next(PayDays.gov, at(2026, 11, 23)); assertEquals(3, d); assertEquals(26, c.get(Calendar.DAY_OF_MONTH))
        val (d2, c2) = PayDays.next(PayDays.gov, at(2026, 11, 27)); assertEquals(Calendar.DECEMBER, c2.get(Calendar.MONTH)); assertEquals(30, d2)
        assertEquals(0, PayDays.next(PayDays.gov, at(2026, 11, 26)).first)
        val (d3, c3) = PayDays.next(PayDays.custom(31), at(2026, 2, 10)); assertEquals(28, c3.get(Calendar.DAY_OF_MONTH)); assertEquals(18, d3)
    }
}
