package com.dailyrupi.core.expense

import com.dailyrupi.core.sync.LocalExpense
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** The backend's expense rules, checked on the phone first so mistakes show next to the field. */
object ExpenseRules {

    const val NOTE_MAX = 255
    private const val MAX_WHOLE_DIGITS = 10
    private val AMOUNT = Regex("""^\d+(\.\d{0,2})?$""")

    sealed interface Amount {
        data class Valid(val value: BigDecimal) : Amount
        data class Invalid(val reason: String) : Amount
    }

    fun parseAmount(text: String): Amount {
        val cleaned = text.trim().replace(",", "").removePrefix("₹").trim()
        if (cleaned.isEmpty()) return Amount.Invalid("Enter an amount")
        if (!AMOUNT.matches(cleaned)) return Amount.Invalid("Use numbers with up to 2 decimals")
        val value = BigDecimal(cleaned.trimEnd('.'))
        if (value.signum() <= 0) return Amount.Invalid("Amount must be more than zero")
        if (value.toBigInteger().toString().length > MAX_WHOLE_DIGITS) return Amount.Invalid("Amount is too large")
        return Amount.Valid(value)
    }

    /** The server allows a minute of clock difference; the phone refuses anything later than now. */
    fun isFuture(spentAt: LocalDateTime, now: LocalDateTime): Boolean = spentAt.isAfter(now.plusMinutes(1))

    fun noteError(note: String): String? =
        if (note.trim().length > NOTE_MAX) "Note can be at most $NOTE_MAX characters" else null
}

/** One day in the expense list: its header, its expenses newest first, and their total. */
data class ExpenseDay(val date: LocalDate, val total: BigDecimal, val expenses: List<LocalExpense>)

/** Groups expenses (already newest first) by the day they were spent. */
fun groupByDay(expenses: List<LocalExpense>): List<ExpenseDay> =
    expenses.groupBy { it.spentAt.toLocalDate() }
        .map { (date, items) -> ExpenseDay(date, items.fold(BigDecimal.ZERO) { sum, e -> sum + e.amount }, items) }
        .sortedByDescending { it.date }

/** Most recently used first, without duplicates, at most [max] entries. */
fun <T> pushRecent(recent: List<T>, used: T, max: Int = 5): List<T> =
    (listOf(used) + recent.filter { it != used }).take(max)
