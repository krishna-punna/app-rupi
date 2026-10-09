package com.dailyrupi.app

import com.dailyrupi.app.data.AuthState
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.data.ReferenceDataRepository
import com.dailyrupi.app.data.SessionEvents
import com.dailyrupi.app.data.SessionManager
import com.dailyrupi.app.sync.PreferencesSyncCursor
import com.dailyrupi.app.sync.SyncRunner
import com.dailyrupi.core.auth.OfflineLogin
import com.dailyrupi.core.sync.InMemoryLocalExpenseStore
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncEngine
import com.dailyrupi.core.sync.SyncState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import com.dailyrupi.core.net.InMemoryCookieStorage
import com.dailyrupi.core.net.PersistentCookieJar
import com.dailyrupi.core.net.ServerAddress
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private val api = FakeApi()
    private val prefs = FakePreferences()
    private val cookies = PersistentCookieJar(InMemoryCookieStorage())
    private val events = SessionEvents()
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val server = "https://rupi.example.com/".toHttpUrl()

    private val store = InMemoryLocalExpenseStore()
    private val scheduler = FakeScheduler()
    private val offlineLogin = OfflineLogin(InMemoryCookieStorage(), iterations = 1_000)

    private fun manager() = SessionManager(
        api, cookies, ServerAddress(), prefs,
        ExpenseRepository(store, prefs, scheduler, scope), ReferenceDataRepository(api, FakeReferenceCache()),
        SyncRunner(SyncEngine(api, store, PreferencesSyncCursor(prefs)) { TODAY }, prefs, cookies),
        offlineLogin, events, scope,
    )

    private suspend fun unsyncedExpense() = store.upsert(
        LocalExpense(
            key = "k", serverId = null, clientId = "k", amount = BigDecimal("10"), spentAt = LocalDateTime.of(2026, 10, 5, 9, 0),
            note = null, categoryId = 1, categoryName = "Food", subCategoryId = 10, subCategoryName = "Groceries",
            itemId = 100, itemName = "Milk", paymentMethodId = 1, paymentMethodName = "UPI", state = SyncState.CREATE,
        ),
    )

    private fun storeSession() =
        cookies.saveFromResponse(server, listOf(Cookie.parse(server, "JSESSIONID=abc; Path=/; HttpOnly")!!))

    @Test
    fun firstLaunchAsksForTheServer() = runTest {
        val session = manager()
        session.start()
        assertEquals(AuthState.NeedsServer, session.state.value)

        session.setServer(server)
        assertEquals(AuthState.LoggedOut(), session.state.value)
        assertEquals(server.toString(), prefs.server)
    }

    @Test
    fun savedSessionIsKeptAcrossRestarts() = runTest {
        prefs.server = server.toString()
        storeSession()
        val session = manager()
        session.start()
        assertEquals(AuthState.LoggedIn(api.user), session.state.value)
    }

    @Test
    fun temporaryPasswordGoesStraightToChangePassword() = runTest {
        prefs.server = server.toString()
        api.user = api.user.copy(passwordChangeRequired = true)
        val session = manager()
        session.start()
        session.login("krishna", "temporary")
        assertEquals(AuthState.MustChangePassword("krishna"), session.state.value)

        storeSession()
        session.changePassword("temporary", "a much longer password")
        assertEquals(AuthState.LoggedOut("Password changed. Log in with your new password."), session.state.value)
        assertFalse(cookies.hasSession())
    }

    @Test
    fun expiredSessionReturnsToLogin() = runTest {
        prefs.server = server.toString()
        val session = manager()
        session.start()
        session.login("krishna", "pw")
        storeSession()

        events.sessionExpired()

        val state = session.state.value
        assertTrue(state is AuthState.LoggedOut && state.notice != null)
        assertFalse(cookies.hasSession())
    }

    @Test
    fun logoutClearsEverythingOnThePhone() = runTest {
        prefs.server = server.toString()
        prefs.recent = listOf(100L)
        prefs.lastMethod = 2L
        val session = manager()
        session.start()
        session.login("krishna", "pw")
        storeSession()

        unsyncedExpense()
        session.logout()

        assertTrue(api.loggedOut)
        assertTrue(store.all.isEmpty())
        assertFalse(offlineLogin.isAvailable("krishna"))
        assertFalse(cookies.hasSession())
        assertEquals(emptyList<Long>(), prefs.recent)
        assertNull(prefs.lastMethod)
        assertNull(prefs.user)
        assertEquals(server.toString(), prefs.server)
        assertEquals(AuthState.LoggedOut(), session.state.value)
    }

    @Test
    fun withoutTheServerTheLastUserLogsInWithTheirPasswordAndChangesStayOnThePhone() = runTest {
        prefs.server = server.toString()
        val session = manager()
        session.start()
        session.login("krishna", "the right password")
        storeSession()
        events.sessionExpired()
        unsyncedExpense()

        api.unreachable = true
        val wrong = runCatching { session.login("krishna", "wrong") }.exceptionOrNull()
        assertEquals("Wrong password. 4 more tries while offline.", wrong?.message)

        session.login("Krishna", "the right password")
        assertEquals("krishna", (session.state.value as AuthState.LoggedIn).user.username)
        assertEquals(1, store.all.size)
    }

    @Test
    fun offlineLoginStopsAfterFiveWrongPasswordsAndForUnknownUsers() = runTest {
        prefs.server = server.toString()
        val session = manager()
        session.start()
        session.login("krishna", "the right password")
        api.unreachable = true

        assertTrue(runCatching { session.login("someone", "the right password") }.exceptionOrNull() is java.io.IOException)
        repeat(5) { runCatching { session.login("krishna", "wrong") } }
        assertTrue(runCatching { session.login("krishna", "the right password") }.exceptionOrNull() is java.io.IOException)
    }

    @Test
    fun aPasswordTheServerRefusesIsNotUsableOffline() = runTest {
        prefs.server = server.toString()
        val session = manager()
        session.start()
        session.login("krishna", "old password")
        api.loginFails = true
        runCatching { session.login("krishna", "old password") }
        assertFalse(offlineLogin.isAvailable("krishna"))
    }

    @Test
    fun aTemporaryPasswordIsNotKeptForOfflineLogin() = runTest {
        prefs.server = server.toString()
        api.user = api.user.copy(passwordChangeRequired = true)
        val session = manager()
        session.start()
        session.login("krishna", "temporary")
        assertFalse(offlineLogin.isAvailable("krishna"))
    }
}

private val TODAY: LocalDate = LocalDate.of(2026, 10, 5)
