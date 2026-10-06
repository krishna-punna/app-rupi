package com.dailyrupi.app

import com.dailyrupi.app.data.AuthState
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.data.ReferenceDataRepository
import com.dailyrupi.app.data.SessionEvents
import com.dailyrupi.app.data.SessionManager
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

    private fun manager() = SessionManager(
        api, cookies, ServerAddress(), prefs,
        ExpenseRepository(api, prefs, scope), ReferenceDataRepository(api), events, scope,
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

        session.logout()

        assertTrue(api.loggedOut)
        assertFalse(cookies.hasSession())
        assertEquals(emptyList<Long>(), prefs.recent)
        assertNull(prefs.lastMethod)
        assertNull(prefs.user)
        assertEquals(server.toString(), prefs.server)
        assertEquals(AuthState.LoggedOut(), session.state.value)
    }
}
