package com.dailyrupi.core

import com.dailyrupi.core.format.Formats
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {

    @Test
    fun rupeesUseIndianGrouping() {
        assertEquals("₹0.00", Formats.rupees(BigDecimal.ZERO))
        assertEquals("₹999.00", Formats.rupees(BigDecimal("999")))
        assertEquals("₹1,000.00", Formats.rupees(BigDecimal("1000")))
        assertEquals("₹12,345.60", Formats.rupees(BigDecimal("12345.6")))
        assertEquals("₹1,23,456.00", Formats.rupees(BigDecimal("123456")))
        assertEquals("₹12,34,567.50", Formats.rupees(BigDecimal("1234567.5")))
        assertEquals("₹1,00,00,00,000.00", Formats.rupees(BigDecimal("1000000000")))
        assertEquals("-₹2,500.75", Formats.rupees(BigDecimal("-2500.75")))
    }

    @Test
    fun datesAndTimes() {
        assertEquals("5 Oct 2026", Formats.date(LocalDate.of(2026, 10, 5)))
        assertEquals("6:30 pm", Formats.time(LocalDateTime.of(2026, 10, 5, 18, 30)))
        assertEquals("9:05 am", Formats.time(LocalDateTime.of(2026, 10, 5, 9, 5)))
        assertEquals("October 2026", Formats.month(YearMonth.of(2026, 10)))
    }

    @Test
    fun dayLabels() {
        val today = LocalDate.of(2026, 10, 5)
        assertEquals("Today", Formats.dayLabel(today, today))
        assertEquals("Yesterday", Formats.dayLabel(today.minusDays(1), today))
        assertEquals("Sat, 3 Oct 2026", Formats.dayLabel(today.minusDays(2), today))
    }
}
