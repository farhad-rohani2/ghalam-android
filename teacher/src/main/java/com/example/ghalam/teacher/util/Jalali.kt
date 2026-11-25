package com.example.ghalam.teacher.util

import java.time.LocalDateTime

/**
 * Jalali <-> Gregorian conversion (33-year-cycle arithmetic). Verified against the jdatetime library
 * for every day from 2001 to 2052 and against the dates the server itself returns.
 */
object Jalali {
    data class Date(val year: Int, val month: Int, val day: Int)

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /** Saturday first, like the Iranian calendar. */
    val weekdayLetters = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    private val cumulativeDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun fromGregorian(gy: Int, gm: Int, gd: Int): Date {
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + cumulativeDays[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Date(jy, jm, jd)
    }

    /** Returns (year, month, day) of the Gregorian date. */
    fun toGregorian(jy0: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy = jy0 + 1595
        var days = -355668 + (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + jd +
            (if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days += 1
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val monthLengths = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > monthLengths[gm]) {
            gd -= monthLengths[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }

    fun daysInMonth(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> {
            // Esfand has 30 days only in a leap year: day 30 survives a round trip
            val (gy, gm, gd) = toGregorian(jy, 12, 30)
            if (fromGregorian(gy, gm, gd) == Date(jy, 12, 30)) 30 else 29
        }
    }

    fun fromDateTime(dt: LocalDateTime): Date = fromGregorian(dt.year, dt.monthValue, dt.dayOfMonth)

    fun toDateTime(jy: Int, jm: Int, jd: Int, hour: Int, minute: Int): LocalDateTime {
        val (gy, gm, gd) = toGregorian(jy, jm, jd)
        return LocalDateTime.of(gy, gm, gd, hour, minute)
    }

    /** 0 = Saturday ... 6 = Friday, for the first day of a Jalali month. */
    fun firstWeekdayIndex(jy: Int, jm: Int): Int {
        val (gy, gm, gd) = toGregorian(jy, jm, 1)
        return (java.time.LocalDate.of(gy, gm, gd).dayOfWeek.value + 1) % 7
    }

    /** "۱۴۰۵/۰۹/۰۲ ۱۷:۰۰" */
    fun format(dt: LocalDateTime): String {
        val d = fromDateTime(dt)
        return "%04d/%02d/%02d %02d:%02d".format(d.year, d.month, d.day, dt.hour, dt.minute).fa()
    }
}
