package com.dailyrupi.core.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Indian-style rupee amounts and the app's date formats (5 Oct 2026). */
object Formats {

    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val dayHeader = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** ₹12,34,567.50: the last three digits, then groups of two. */
    fun rupees(amount: BigDecimal): String {
        val scaled = amount.setScale(2, RoundingMode.HALF_UP)
        val sign = if (scaled.signum() < 0) "-" else ""
        val plain = scaled.abs().toPlainString()
        val whole = plain.substringBefore('.')
        val fraction = plain.substringAfter('.')
        return "$sign₹${groupIndian(whole)}.$fraction"
    }

    internal fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        val groups = head.reversed().chunked(2).map { it.reversed() }.reversed()
        return groups.joinToString(",") + "," + tail
    }

    fun date(value: LocalDate): String = value.format(date)

    fun time(value: LocalDateTime): String = value.format(time).replace("AM", "am").replace("PM", "pm")

    fun month(value: YearMonth): String = value.format(month)

    /** "Today", "Yesterday", or the full date with weekday, for the expense list's day headers. */
    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(dayHeader)
    }
}
