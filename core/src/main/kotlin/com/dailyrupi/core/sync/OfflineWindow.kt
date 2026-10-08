package com.dailyrupi.core.sync

import java.time.LocalDate

/** The phone keeps the last week of expenses (today and the six days before) for offline use. */
object OfflineWindow {
    const val DAYS = 7L

    fun start(today: LocalDate): LocalDate = today.minusDays(DAYS - 1)

    fun contains(day: LocalDate, today: LocalDate) = !day.isBefore(start(today)) && !day.isAfter(today)
}
