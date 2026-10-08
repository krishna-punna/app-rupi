package com.dailyrupi.core

import com.dailyrupi.core.auth.PasswordRules
import com.dailyrupi.core.expense.ExpenseRules
import com.dailyrupi.core.expense.ExpenseRules.Amount
import com.dailyrupi.core.expense.groupByDay
import com.dailyrupi.core.expense.pushRecent
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun amounts() {
        assertEquals(Amount.Valid(BigDecimal("250")), ExpenseRules.parseAmount(" 250 "))
        assertEquals(Amount.Valid(BigDecimal("1234.5")), ExpenseRules.parseAmount("1,234.5"))
        assertEquals(Amount.Valid(BigDecimal("99.99")), ExpenseRules.parseAmount("₹99.99"))
        assertEquals(Amount.Valid(BigDecimal("12")), ExpenseRules.parseAmount("12."))
        assertTrue(ExpenseRules.parseAmount("") is Amount.Invalid)
        assertTrue(ExpenseRules.parseAmount("0") is Amount.Invalid)
        assertTrue(ExpenseRules.parseAmount("0.00") is Amount.Invalid)
        assertTrue(ExpenseRules.parseAmount("1.234") is Amount.Invalid)
        assertTrue(ExpenseRules.parseAmount("-5") is Amount.Invalid)
        assertTrue(ExpenseRules.parseAmount("abc") is Amount.Invalid)
        assertTrue(ExpenseRules.parseAmount("12345678901") is Amount.Invalid)
        assertTrue(ExpenseRules.parseAmount("1234567890.99") is Amount.Valid)
    }

    @Test
    fun futureDatesAndNotes() {
        val now = LocalDateTime.of(2026, 10, 5, 18, 0)
        assertFalse(ExpenseRules.isFuture(now, now))
        assertFalse(ExpenseRules.isFuture(now.plusSeconds(30), now))
        assertTrue(ExpenseRules.isFuture(now.plusMinutes(2), now))
        assertNull(ExpenseRules.noteError("a".repeat(255)))
        assertEquals("Note can be at most 255 characters", ExpenseRules.noteError("a".repeat(256)))
    }

    @Test
    fun groupsByDayNewestFirstWithTotals() {
        val days = groupByDay(
            listOf(
                expense(3, "100.50", LocalDateTime.of(2026, 10, 5, 20, 0)),
                expense(2, "49.50", LocalDateTime.of(2026, 10, 5, 9, 0)),
                expense(1, "10", LocalDateTime.of(2026, 10, 3, 9, 0)),
            ),
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3)), days.map { it.date })
        assertEquals(BigDecimal("150.00"), days[0].total)
        assertEquals(listOf(3L, 2L), days[0].expenses.map { it.serverId })
    }

    @Test
    fun recentListKeepsFiveMostRecentWithoutDuplicates() {
        assertEquals(listOf(7L), pushRecent(emptyList(), 7L))
        assertEquals(listOf(3L, 1L, 2L), pushRecent(listOf(1L, 2L, 3L), 3L))
        assertEquals(listOf(6L, 1L, 2L, 3L, 4L), pushRecent(listOf(1L, 2L, 3L, 4L, 5L), 6L))
    }

    @Test
    fun passwordRules() {
        assertEquals("Enter your current password", PasswordRules.problem("admin", "", "x", "x"))
        assertEquals("New password must be at least 12 characters", PasswordRules.problem("admin", "old", "short", "short"))
        assertEquals(
            "New password must not contain your username",
            PasswordRules.problem("admin", "old", "myADMINpassword1", "myADMINpassword1"),
        )
        assertEquals("The two new passwords do not match", PasswordRules.problem("admin", "old", "long enough pass", "other"))
        assertNull(PasswordRules.problem("admin", "old", "long enough pass", "long enough pass"))
    }

    private fun expense(id: Long, amount: String, at: LocalDateTime) = LocalExpense(
        key = "k$id", serverId = id, clientId = null, amount = BigDecimal(amount), spentAt = at, note = null,
        categoryId = 1, categoryName = "Food", subCategoryId = 2, subCategoryName = "Groceries",
        itemId = 3, itemName = "Milk", paymentMethodId = 1, paymentMethodName = "UPI", state = SyncState.SYNCED,
    )
}
