package com.dailyrupi.core.expense

import com.dailyrupi.core.model.ExpenseSummary
import com.dailyrupi.core.sync.LocalExpense
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Today, this week (from Monday) and this month, worked out on the phone the way the server does,
 * so totals include expenses not synced yet.
 */
fun summarize(expenses: List<LocalExpense>, today: LocalDate): ExpenseSummary {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monthStart = today.withDayOfMonth(1)
    fun total(from: LocalDate) = expenses
        .filter { val day = it.spentAt.toLocalDate(); !day.isBefore(from) && !day.isAfter(today) }
        .fold(BigDecimal.ZERO) { sum, e -> sum + e.amount }
    return ExpenseSummary(today, weekStart, monthStart, total(today), total(weekStart), total(monthStart))
}
