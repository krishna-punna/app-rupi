package com.dailyrupi.app.sync

import com.dailyrupi.app.data.AppPreferences
import com.dailyrupi.core.net.ApiException
import com.dailyrupi.core.net.PersistentCookieJar
import com.dailyrupi.core.net.userMessage
import com.dailyrupi.core.sync.SyncCursor
import com.dailyrupi.core.sync.SyncEngine
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Why the last sync did not finish. */
sealed interface SyncProblem {
    val message: String

    /** The server could not be reached; changes wait on the phone. */
    data object Offline : SyncProblem {
        override val message = "Can't reach the server. Changes are saved on this phone and will be sent later."
    }

    /** No server session: the user unlocked offline, or the session ended. */
    data object NeedsLogin : SyncProblem {
        override val message = "Log in again to sync."
    }

    data class Failed(override val message: String) : SyncProblem
}

data class SyncStatus(
    val running: Boolean = false,
    val lastSyncAt: Long? = null,
    val problem: SyncProblem? = null,
)

enum class SyncOutcome { DONE, RETRY, NEEDS_LOGIN }

/** Runs [SyncEngine] for the background worker and the Sync screen, and reports how it went. */
class SyncRunner(
    private val engine: SyncEngine,
    private val prefs: AppPreferences,
    private val cookies: PersistentCookieJar,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    suspend fun run(): SyncOutcome {
        if (_status.value.lastSyncAt == null) {
            prefs.lastSyncAt()?.let { last -> _status.update { it.copy(lastSyncAt = last) } }
        }
        // Without a session every call would be refused; don't send any until the user logs in.
        if (!cookies.hasSession()) {
            _status.update { it.copy(running = false, problem = SyncProblem.NeedsLogin) }
            return SyncOutcome.NEEDS_LOGIN
        }
        _status.update { it.copy(running = true) }
        return try {
            engine.sync()
            val now = clock()
            prefs.setLastSyncAt(now)
            _status.update { SyncStatus(running = false, lastSyncAt = now, problem = null) }
            SyncOutcome.DONE
        } catch (e: CancellationException) {
            _status.update { it.copy(running = false) }
            throw e
        } catch (e: ApiException) {
            if (e.status == UNAUTHORIZED) {
                _status.update { it.copy(running = false, problem = SyncProblem.NeedsLogin) }
                SyncOutcome.NEEDS_LOGIN
            } else {
                _status.update { it.copy(running = false, problem = SyncProblem.Failed(e.userMessage())) }
                SyncOutcome.RETRY
            }
        } catch (e: IOException) {
            _status.update { it.copy(running = false, problem = SyncProblem.Offline) }
            SyncOutcome.RETRY
        } catch (e: Exception) {
            _status.update { it.copy(running = false, problem = SyncProblem.Failed(e.userMessage())) }
            SyncOutcome.RETRY
        }
    }

    /** After Log out or a new server: forget the last sync. */
    fun reset() {
        _status.value = SyncStatus()
    }

    private companion object {
        const val UNAUTHORIZED = 401
    }
}

/** Keeps the sync's place in the preferences. */
class PreferencesSyncCursor(private val prefs: AppPreferences) : SyncCursor {
    override suspend fun since(): LocalDateTime? = prefs.syncSince()?.let(LocalDateTime::parse)
    override suspend fun setSince(value: LocalDateTime?) = prefs.setSyncSince(value?.toString())
}
