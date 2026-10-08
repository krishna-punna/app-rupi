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
import com.dailyrupi.core.sync.SyncEngine
import com.dailyrupi.app.sync.PreferencesSyncCursor
import java.math.BigDecimal
import java.time.Clock
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
    private val sync = SyncRunner(SyncEngine(api, store, PreferencesSyncCursor(prefs)), prefs, cookies)
    private val now = LocalDateTime.of(2026, 10, 5, 20, 0)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.ofHoursMinutes(5, 30)), ZoneId.of("Asia/Kolkata"))
    private val milk = ItemChoice(100, "Milk", 10, "Groceries", 1, "Food")

    private fun loggedIn() {
        val server = "https://rupi.example.com/".toHttpUrl()
        cookies.saveFromResponse(server, listOf(Cookie.parse(server, "JSESSIONID=abc; Path=/; HttpOnly")!!))
    }

    @Test
    fun showsExpensesSavedOnThePhoneWithTotalsBeforeTheyAreSynced() = runTest {
        repository.save(null, ExpenseRequest(100, 1, BigDecimal("10"), now.minusHours(1)), milk, PaymentMethod(1, "UPI"))
        repository.save(null, ExpenseRequest(100, 1, BigDecimal("5.50"), now.minusDays(1)), milk, PaymentMethod(1, "UPI"))

        val vm = ExpensesViewModel(repository, sync, clock)
        advanceUntilIdle()
        val state = vm.state.value

        assertFalse(state.loading)
        assertEquals(2, state.days.size)
        assertEquals(BigDecimal("10"), state.summary?.today)
        assertEquals(BigDecimal("15.50"), state.summary?.month)
        assertEquals(2, state.unsynced)
    }

    @Test
    fun pullToRefreshSyncsAndFetchesWebExpenses() = runTest {
        loggedIn()
        api.addExpense(1, "20", now.minusHours(2))
        repository.save(null, ExpenseRequest(200, 2, BigDecimal("5"), now.minusHours(1)), milk, PaymentMethod(2, "Cash"))
        val vm = ExpensesViewModel(repository, sync, clock)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.days.single().expenses.size)
        assertEquals(0, state.unsynced)
        assertFalse(state.refreshing)
    }

    @Test
    fun refreshWithoutTheServerSaysSoAndKeepsTheList() = runTest {
        loggedIn()
        api.unreachable = true
        repository.save(null, ExpenseRequest(100, 1, BigDecimal("10"), now.minusHours(1)), milk, PaymentMethod(1, "UPI"))
        val vm = ExpensesViewModel(repository, sync, clock)

        vm.refresh()
        advanceUntilIdle()

        assertNotNull(vm.state.value.message)
        assertEquals(1, vm.state.value.unsynced)
        assertEquals(1, vm.state.value.days.single().expenses.size)
    }
}
