package com.example.util

import java.util.Calendar
import java.util.Locale

data class EthiopianDate(
    val day: Int,
    val month: Int,
    val year: Int,
) {
    fun formatNumeric(): String = "%02d/%02d/%04d".format(Locale.US, day, month, year)

    fun formatAmharicWithMonth(): String {
        val monthName = EthiopianCalendarUtil.amharicMonthName(month)
        return "$monthName $day/$year ዓ.ም"
    }
}

/**
 * Pure Kotlin mathematical Ethiopian <-> Gregorian calendar converter using Julian Day Number (JDN).
 * Accurate across Pagume leap years (Ethiopian year % 4 == 3).
 */
object EthiopianCalendarUtil {
    private const val JD_EPOCH_OFFSET_ETHIOPIC = 1723856

    private val amharicMonths = listOf(
        "መስከረም", "ጥቅምት", "ኅዳር", "ታኅሣሥ",
        "ጥር", "የካቲት", "መጋቢት", "ሚያዝያ",
        "ግንቦት", "ሰኔ", "ሐምሌ", "ነሐሴ", "ጳጉሜ"
    )

    private val gregorianMonths = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun amharicMonthName(month: Int): String =
        amharicMonths.getOrElse(month - 1) { "መስከረም" }

    fun daysInEthiopianMonth(year: Int, month: Int): Int = when {
        month in 1..12 -> 30
        year % 4 == 3 -> 6
        else -> 5
    }

    fun todayEthiopian(): EthiopianDate {
        val cal = Calendar.getInstance()
        return gregorianToEthiopian(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun gregorianToEthiopian(gYear: Int, gMonth: Int, gDay: Int): EthiopianDate {
        val jdn = gregorianToJdn(gYear, gMonth, gDay)
        return jdnToEthiopian(jdn)
    }

    fun ethiopianToGregorianString(ethiopianDateStr: String): String {
        val parsed = parseEthiopianDate(ethiopianDateStr) ?: return ""
        val (gy, gm, gd) = ethiopianToGregorian(parsed.year, parsed.month, parsed.day)
        val monthShort = gregorianMonths.getOrElse(gm - 1) { "" }
        return "$gd $monthShort $gy"
    }

    fun parseEthiopianDate(input: String): EthiopianDate? {
        val parts = input.trim().replace("-", "/").split("/")
        if (parts.size != 3) return null
        val d = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        if (m !in 1..13 || d !in 1..daysInEthiopianMonth(y, m)) return null
        return EthiopianDate(d, m, y)
    }

    fun ethiopianToGregorian(eYear: Int, eMonth: Int, eDay: Int): Triple<Int, Int, Int> {
        val jdn = ethiopianToJdn(eYear, eMonth, eDay)
        return jdnToGregorian(jdn)
    }

    fun addDaysToEthiopian(dateStr: String, days: Int): Pair<String, String> {
        val parsed = parseEthiopianDate(dateStr) ?: todayEthiopian()
        val targetJdn = ethiopianToJdn(parsed.year, parsed.month, parsed.day) + days
        val et = jdnToEthiopian(targetJdn)
        val etStr = et.formatNumeric()
        return etStr to ethiopianToGregorianString(etStr)
    }

    private fun gregorianToJdn(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    private fun jdnToGregorian(jdn: Int): Triple<Int, Int, Int> {
        val f = jdn + 1401 + (((4 * jdn + 274277) / 146097) * 3) / 4 - 38
        val e = 4 * f + 3
        val g = (e % 1461) / 4
        val h = 5 * g + 2
        val day = (h % 153) / 5 + 1
        val month = (h / 153 + 2) % 12 + 1
        val year = e / 1461 - 4716 + (12 + 2 - month) / 12
        return Triple(year, month, day)
    }

    private fun ethiopianToJdn(year: Int, month: Int, day: Int): Int {
        return (JD_EPOCH_OFFSET_ETHIOPIC + 365) +
            365 * (year - 1) +
            (year / 4) +
            30 * (month - 1) +
            day - 1
    }

    private fun jdnToEthiopian(jdn: Int): EthiopianDate {
        val r = (jdn - JD_EPOCH_OFFSET_ETHIOPIC) % 1461
        val n = (r % 365) + 365 * (r / 1460)
        val year = 4 * ((jdn - JD_EPOCH_OFFSET_ETHIOPIC) / 1461) + (r / 365) - (r / 1460)
        val month = (n / 30) + 1
        val day = (n % 30) + 1
        return EthiopianDate(day = day, month = month, year = year)
    }
}
