package com.dailyrupi.app

import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.sync.SyncRunner
import com.dailyrupi.app.ui.expenses.ExpensesViewModel
import com.dailyrupi.core.masterdata.ItemChoice
import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.model.PaymentMethod
import com.dailyrupi.core.net.InMemoryCookieStorage
import com.dailyrupi.core.net.PersistentCookieJar
import com.dailyrupi.core.sync.InMemoryLocalExpenseStore
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncEngine
import com.dailyrupi.core.sync.SyncState
import com.dailyrupi.app.sync.PreferencesSyncCursor
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeApi()
    private val prefs = FakePreferences()
    private val store = InMemoryLocalExpenseStore()
    private val repository = ExpenseRepository(store, prefs, FakeScheduler(), TestScope(UnconfinedTestDispatcher()))
    private val cookies = PersistentCookieJar(InMemoryCookieStorage())
    private val sync = SyncRunner(SyncEngine(api, store, PreferencesSyncCursor(prefs)) { TODAY }, prefs, cookies)
    private val now = LocalDateTime.of(2026, 10, 5, 20, 0)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.ofHoursMinutes(5, 30)), ZoneId.of("Asia/Kolkata"))
    private val milk = ItemChoice(100, "Milk", 10, "Groceries", 1, "Food")

    private fun loggedIn() {
        val server = "https://rupi.example.com/".toHttpUrl()
        cookies.saveFromResponse(server, listOf(Cookie.parse(server, "JSESSIONID=abc; Path=/; HttpOnly")!!))
    }

    private fun onPhone(key: String, spentAt: LocalDateTime, state: SyncState) = LocalExpense(
        key = key, serverId = if (state == SyncState.CREATE) null else key.length.toLong() + 900, clientId = key,
        amount = BigDecimal("1"), spentAt = spentAt, note = null, categoryId = 1, categoryName = "Food",
        subCategoryId = 10, subCategoryName = "Groceries", itemId = 100, itemName = "Milk",
        paymentMethodId = 1, paymentMethodName = "UPI", state = state,
    )

    @Test
    fun withoutTheServerShowsThePhonesExpensesAndNoTotals() = runTest {
        repository.save(null, ExpenseRequest(100, 1, BigDecimal("10"), now.minusHours(1)), milk, PaymentMethod(1, "UPI"))
        repository.save(null, ExpenseRequest(100, 1, BigDecimal("5.50"), now.minusDays(1)), milk, PaymentMethod(1, "UPI"))

        val vm = ExpensesViewModel(repository, sync, api, clock)
        advanceUntilIdle()
        val state = vm.state.value

        assertFalse(state.loading)
        assertEquals(2, state.days.size)
        assertNull(state.summary)
        assertFalse(state.online)
        assertNull(state.message)
        assertEquals(2, state.unsynced)
    }

    @Test
    fun pullToRefreshSyncsAndLoadsTheServersTotals() = runTest {
        loggedIn()
        api.addExpense(1, "20", now.minusHours(2))
        repository.save(null, ExpenseRequest(200, 2, BigDecimal("5"), now.minusHours(1)), milk, PaymentMethod(2, "Cash"))
        val vm = ExpensesViewModel(repository, sync, api, clock)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.days.single().expenses.size)
        assertEquals(0, state.unsynced)
        assertFalse(state.refreshing)
        assertTrue(state.online)
        assertEquals(BigDecimal("25"), state.summary?.today)
    }

    @Test
    fun refreshWithoutTheServerSaysSoAndKeepsTheList() = runTest {
        loggedIn()
        api.unreachable = true
        repository.save(null, ExpenseRequest(100, 1, BigDecimal("10"), now.minusHours(1)), milk, PaymentMethod(1, "UPI"))
        val vm = ExpensesViewModel(repository, sync, api, clock)

        vm.refresh()
        advanceUntilIdle()

        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.online)
        assertEquals(1, vm.state.value.unsynced)
        assertEquals(1, vm.state.value.days.single().expenses.size)
    }

    @Test
    fun onlyTheLastWeekStaysOnThePhoneUnlessNotSynced() = runTest {
        loggedIn()
        api.addExpense(1, "20", now.minusDays(10))
        api.addExpense(2, "30", now.minusDays(6))
        store.upsert(onPhone("old-synced", now.minusDays(8), SyncState.SYNCED))
        // Refused by the server, so it waits on the phone for the user.
        store.upsert(onPhone("old-unsynced", now.minusDays(9), SyncState.CREATE).copy(error = "Item is inactive"))

        val vm = ExpensesViewModel(repository, sync, api, clock)
        advanceUntilIdle()

        // The first fetch asks for the last 7 days only.
        assertEquals("2026-09-29", api.fromAsked.first())
        assertEquals(setOf(2L, null), store.all.map { it.serverId }.toSet())
        assertNull(store.byKey("old-synced"))
        assertNotNull(store.byKey("old-unsynced"))
        // Shown: the week, and the change still to sync.
        assertEquals(2, vm.state.value.days.size)
    }

    @Test
    fun pickingADayLoadsItFromTheServerAndDropsItAfterwards() = runTest {
        loggedIn()
        api.addExpense(1, "20", now.minusDays(10))
        api.addExpense(2, "30", now.minusHours(1))
        val vm = ExpensesViewModel(repository, sync, api, clock)
        advanceUntilIdle()
        assertTrue(vm.state.value.online)

        val day = LocalDate.of(2026, 9, 25)
        vm.showDay(day)
        advanceUntilIdle()

        assertEquals("2026-09-25" to "2026-09-25", api.daysAsked.last())
        assertEquals(day, vm.state.value.day)
        assertEquals(BigDecimal("20"), vm.state.value.days.single().expenses.single().amount)

        // A sync while the day is shown keeps it.
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.days.single().expenses.size)

        vm.showDay(null)
        advanceUntilIdle()
        assertNull(vm.state.value.day)
        assertEquals(BigDecimal("30"), vm.state.value.days.single().expenses.single().amount)
        assertEquals(listOf(2L), store.all.map { it.serverId })
    }

    @Test
    fun pickingADayWithoutTheServerSaysSo() = runTest {
        loggedIn()
        val vm = ExpensesViewModel(repository, sync, api, clock)
        advanceUntilIdle()
        api.unreachable = true

        vm.showDay(LocalDate.of(2026, 9, 25))
        advanceUntilIdle()

        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.online)
        assertTrue(vm.state.value.days.isEmpty())
    }
}

private val TODAY: LocalDate = LocalDate.of(2026, 10, 5)
