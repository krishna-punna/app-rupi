package com.dailyrupi.app.data

import com.dailyrupi.app.di.AppScope
import com.dailyrupi.core.model.ChangePasswordRequest
import com.dailyrupi.core.model.CurrentUser
import com.dailyrupi.core.net.ApiException
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.PersistentCookieJar
import com.dailyrupi.core.net.ServerAddress
import com.dailyrupi.core.net.apiCall
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

sealed interface AuthState {
    data object Loading : AuthState
    data object NeedsServer : AuthState
    data class LoggedOut(val notice: String? = null) : AuthState
    data class MustChangePassword(val username: String) : AuthState
    data class LoggedIn(val user: CurrentUser) : AuthState
}

/** Decides which part of the app shows: server setup, log in, password change, or the app itself. */
@Singleton
class SessionManager @Inject constructor(
    private val api: DailyRupiApi,
    private val cookies: PersistentCookieJar,
    private val server: ServerAddress,
    private val prefs: AppPreferences,
    private val expenses: ExpenseRepository,
    private val referenceData: ReferenceDataRepository,
    events: SessionEvents,
    @AppScope scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val serverUrl: HttpUrl? get() = server.url

    init {
        scope.launch { events.expired.collect { onSessionExpired() } }
    }

    /** Called once at launch: picks up the saved server and session. */
    suspend fun start() {
        if (_state.value != AuthState.Loading) return
        val url = prefs.serverUrl()?.let(ServerAddress::parse)
        server.url = url
        if (url == null) {
            _state.value = AuthState.NeedsServer
            return
        }
        if (!cookies.hasSession()) {
            _state.value = AuthState.LoggedOut()
            return
        }
        _state.value = try {
            stateFor(apiCall { api.me() })
        } catch (e: ApiException) {
            AuthState.LoggedOut()
        } catch (e: IOException) {
            // Offline at launch: keep the saved session; screens show their own errors.
            AuthState.LoggedIn(CurrentUser(prefs.username().orEmpty()))
        }
    }

    suspend fun setServer(url: HttpUrl) {
        prefs.setServerUrl(url.toString())
        if (url == server.url && _state.value != AuthState.NeedsServer) return
        cookies.clear()
        server.url = url
        _state.value = AuthState.LoggedOut()
    }

    /** Throws [ApiException] with the server's message when login is refused. */
    suspend fun login(username: String, password: String) {
        val user = apiCall { api.login(username.trim(), password) }
        prefs.setUsername(user.username)
        _state.value = stateFor(user)
    }

    suspend fun changePassword(current: String, new: String) {
        apiCall { api.changePassword(ChangePasswordRequest(current, new)) }
        // The server ends the session after a password change.
        cookies.clear()
        _state.value = AuthState.LoggedOut("Password changed. Log in with your new password.")
    }

    /** Ends the server session if it can, and always clears everything on the phone. */
    suspend fun logout() {
        runCatching { apiCall { api.logout() } }
        cookies.clear()
        prefs.clearUserData()
        expenses.clear()
        referenceData.clear()
        _state.value = AuthState.LoggedOut()
    }

    private fun onSessionExpired() {
        val current = _state.value
        if (current is AuthState.LoggedIn || current is AuthState.MustChangePassword) {
            cookies.clear()
            _state.value = AuthState.LoggedOut("Your session has ended. Please log in again.")
        }
    }

    private fun stateFor(user: CurrentUser): AuthState =
        if (user.passwordChangeRequired) AuthState.MustChangePassword(user.username) else AuthState.LoggedIn(user)
}
