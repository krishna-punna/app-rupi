package com.dailyrupi.app.data

import com.dailyrupi.app.di.AppScope
import com.dailyrupi.app.sync.SyncRunner
import com.dailyrupi.core.auth.OfflineLogin
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val sync: SyncRunner,
    private val offlineLogin: OfflineLogin,
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
            // Offline at launch: keep the saved session; expenses work from the phone.
            AuthState.LoggedIn(CurrentUser(prefs.username().orEmpty()))
        }
        if (_state.value is AuthState.LoggedIn) expenses.requestSync()
    }

    suspend fun setServer(url: HttpUrl) {
        prefs.setServerUrl(url.toString())
        if (url == server.url && _state.value != AuthState.NeedsServer) return
        // Expenses on the phone belong to the old server.
        if (server.url != null) clearLocalData()
        offlineLogin.forget()
        cookies.clear()
        server.url = url
        _state.value = AuthState.LoggedOut()
    }

    /**
     * Logs in on the server. When the server cannot be reached, checks the password against the
     * hash kept from the last online login instead, and expenses are saved on the phone until a
     * later online login lets them sync. Throws [ApiException] with the message to show when refused.
     */
    suspend fun login(username: String, password: String) {
        val user = try {
            apiCall { api.login(username.trim(), password) }
        } catch (e: IOException) {
            loginOffline(username, password, e)
            return
        } catch (e: ApiException) {
            // Refused by the server (wrong password, locked, or changed on the web): offline login goes too.
            if (e.status == UNAUTHORIZED) offlineLogin.forget()
            throw e
        }
        val previous = prefs.username()
        // Someone else logging in on this phone does not get the last user's unsynced changes.
        if (previous != null && !previous.equals(user.username, ignoreCase = true)) clearLocalData()
        prefs.setUsername(user.username)
        // A temporary password is not kept: it has to be changed online first.
        if (user.passwordChangeRequired) {
            offlineLogin.forget()
        } else {
            withContext(Dispatchers.Default) { offlineLogin.remember(user.username, password) }
        }
        _state.value = stateFor(user)
        if (_state.value is AuthState.LoggedIn) expenses.requestSync()
    }

    private suspend fun loginOffline(username: String, password: String, offline: IOException) {
        val result = withContext(Dispatchers.Default) { offlineLogin.check(username, password) }
        when (result) {
            is OfflineLogin.Result.Success -> _state.value = AuthState.LoggedIn(CurrentUser(result.username))
            is OfflineLogin.Result.WrongPassword -> throw ApiException(
                UNAUTHORIZED,
                "LOGIN_FAILED",
                if (result.attemptsLeft == 0) {
                    "Wrong password. Offline login is now off until you log in with the server reachable."
                } else {
                    "Wrong password. ${result.attemptsLeft} more tries while offline."
                },
            )
            OfflineLogin.Result.Unavailable -> throw offline
        }
    }

    /** From the Sync screen when the session has ended: back to Log in, keeping unsynced changes. */
    fun logInAgain() {
        cookies.clear()
        _state.value = AuthState.LoggedOut("Log in to send the changes saved on this phone.")
    }

    suspend fun changePassword(current: String, new: String) {
        apiCall { api.changePassword(ChangePasswordRequest(current, new)) }
        // The server ends the session after a password change.
        cookies.clear()
        offlineLogin.forget()
        _state.value = AuthState.LoggedOut("Password changed. Log in with your new password.")
    }

    /** Ends the server session if it can, and always clears everything on the phone. */
    suspend fun logout() {
        runCatching { apiCall { api.logout() } }
        cookies.clear()
        offlineLogin.forget()
        clearLocalData()
        prefs.clearUserData()
        _state.value = AuthState.LoggedOut()
    }

    private suspend fun clearLocalData() {
        expenses.clearAll()
        referenceData.clear()
        prefs.setSyncSince(null)
        sync.reset()
    }

    private fun onSessionExpired() {
        val current = _state.value
        if (current is AuthState.LoggedIn || current is AuthState.MustChangePassword) {
            cookies.clear()
            _state.value = AuthState.LoggedOut("Your session has ended. Please log in again.")
        }
    }

    private companion object {
        const val UNAUTHORIZED = 401
    }

    private fun stateFor(user: CurrentUser): AuthState =
        if (user.passwordChangeRequired) AuthState.MustChangePassword(user.username) else AuthState.LoggedIn(user)
}
