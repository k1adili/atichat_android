package com.k1adili.atichat.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Gregorian <-> Jalali (Shamsi) conversion. Date math stays on java.time.LocalDate;
 * Jalali is only produced at the display layer.
 */
object Jalali {
    data class Date(val year: Int, val month: Int, val day: Int)

    private val GREGORIAN_DAYS_BEFORE_MONTH = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun fromGregorian(date: LocalDate): Date = fromGregorian(date.year, date.monthValue, date.dayOfMonth)

    fun fromGregorian(gregorianYear: Int, gm: Int, gd: Int): Date {
        var jy = if (gregorianYear <= 1600) 0 else 979
        val gy = gregorianYear - if (gregorianYear <= 1600) 621 else 1600
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd +
            GREGORIAN_DAYS_BEFORE_MONTH[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        jy += (days - 1) / 365
        if (days > 365) days = (days - 1) % 365
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return Date(jy, jm, jd)
    }

    fun toGregorian(jalaliYear: Int, jm: Int, jd: Int): LocalDate {
        var gy = if (jalaliYear <= 979) 621 else 1600
        val jy = jalaliYear - if (jalaliYear <= 979) 0 else 979
        var days = 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + 78 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        gy += 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        gy += (days - 1) / 365
        if (days > 365) days = (days - 1) % 365
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val monthLengths = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13) {
            val length = monthLengths[gm]
            if (gd <= length) break
            gd -= length
            gm++
        }
        return LocalDate.of(gy, gm, gd)
    }

    fun toPersianDigits(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') ('۰' + (c - '0')) else c)
    }

    /** "۱۴۰۵/۰۸/۲۳ ۱۴:۰۵" in the device time zone. */
    fun formatDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = instant.atZone(zone)
        val j = fromGregorian(local.toLocalDate())
        val text = String.format(Locale.ROOT, "%04d/%02d/%02d %02d:%02d", j.year, j.month, j.day, local.hour, local.minute)
        return toPersianDigits(text)
    }

    fun formatTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = instant.atZone(zone)
        return toPersianDigits(String.format(Locale.ROOT, "%02d:%02d", local.hour, local.minute))
    }

    fun formatDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val j = fromGregorian(instant.atZone(zone).toLocalDate())
        return toPersianDigits(String.format(Locale.ROOT, "%04d/%02d/%02d", j.year, j.month, j.day))
    }
}
