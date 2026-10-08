package com.dailyrupi.core.sync

import com.dailyrupi.core.model.Expense
import com.dailyrupi.core.net.ApiException
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.apiCall
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Remembers how far the last pull got, so the next one asks only for what changed since. */
interface SyncCursor {
    suspend fun since(): LocalDateTime?
    suspend fun setSince(value: LocalDateTime?)
}

/** What one sync did. */
data class SyncResult(val sent: Int, val refused: Int, val received: Int)

/**
 * Sends the phone's changes to the server oldest first, then fetches what changed there.
 *
 * - A new expense carries its client id, so a create retried after a dropped connection is saved once.
 * - A change the server refuses (for example the item was made inactive on the web) stays on the
 *   phone with the server's reason until the user fixes or deletes it; later changes still go out.
 * - A change waiting on the phone is not overwritten by the server's copy; once sent, the last
 *   one saved on the server wins.
 *
 * Network failures and an expired session are thrown, leaving everything unsent for the next try.
 */
class SyncEngine(
    private val api: DailyRupiApi,
    private val store: LocalExpenseStore,
    private val cursor: SyncCursor,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncResult = mutex.withLock {
        var sent = 0
        var refused = 0
        // Changes made while a pass was running go out in the next pass.
        repeat(MAX_PASSES) {
            val (passSent, passRefused) = push()
            sent += passSent
            refused += passRefused
            if (passSent + passRefused == 0 || store.pending().isEmpty()) return@withLock SyncResult(sent, refused, pull())
        }
        SyncResult(sent, refused, pull())
    }

    private suspend fun push(): Pair<Int, Int> {
        var sent = 0
        var refused = 0
        for (change in store.pending()) {
            // The user may have changed or deleted it since the list was read.
            val current = store.byKey(change.key) ?: continue
            if (current != change) continue
            try {
                send(current)
                sent++
            } catch (e: ApiException) {
                if (!isRefusal(e)) throw e
                // Keep any edit the user made while this one was on its way.
                val latest = store.byKey(current.key)
                if (latest == current) store.upsert(current.copy(error = e.message))
                refused++
            }
        }
        return sent to refused
    }

    private suspend fun send(change: LocalExpense) {
        val id = change.serverId
        when (change.state) {
            SyncState.SYNCED -> Unit
            SyncState.CREATE -> try {
                saved(change, apiCall { api.createExpense(change.toRequest()) })
            } catch (e: ApiException) {
                // Deleted on the web after an earlier attempt reached the server.
                if (e.status == GONE) store.delete(change.key) else throw e
            }
            SyncState.UPDATE -> try {
                saved(change, apiCall { api.updateExpense(id!!, change.toRequest()) })
            } catch (e: ApiException) {
                if (e.status == NOT_FOUND) store.delete(change.key) else throw e
            }
            SyncState.DELETE -> {
                if (id != null) {
                    try {
                        apiCall { api.deleteExpense(id) }
                    } catch (e: ApiException) {
                        if (e.status != NOT_FOUND) throw e
                    }
                }
                store.delete(change.key)
            }
        }
    }

    private suspend fun saved(sent: LocalExpense, expense: Expense) {
        val latest = store.byKey(sent.key)
        when {
            latest == sent -> store.upsert(LocalExpense.fromServer(expense, sent.key))
            // Deleted on the phone while the create was on its way: delete it on the server too.
            latest == null -> store.upsert(
                LocalExpense.fromServer(expense, sent.key).copy(state = SyncState.DELETE, changedAt = sent.changedAt),
            )
            // Changed again while this one was on its way: keep that change, now as an update.
            else -> {
                val state = if (latest.state == SyncState.DELETE) SyncState.DELETE else SyncState.UPDATE
                store.upsert(latest.copy(serverId = expense.id, state = state))
            }
        }
    }

    private suspend fun pull(): Int {
        val since = cursor.since()
        val changes = apiCall { api.expenseChanges(since?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)) }
        val incoming = mutableListOf<LocalExpense>()
        for (expense in changes.expenses) {
            val local = store.byServerId(expense.id) ?: expense.clientId?.let { store.byKey(it) }
            when {
                local == null || local.state == SyncState.SYNCED ->
                    incoming += LocalExpense.fromServer(expense, local?.key)
                // A create that reached the server but whose answer was lost: it is saved now.
                local.state == SyncState.CREATE && local.clientId == expense.clientId && local.error == null ->
                    incoming += LocalExpense.fromServer(expense, local.key)
                // Otherwise the phone's change is still to be sent and wins.
            }
        }
        store.upsertAll(incoming)
        for (deleted in changes.deleted) {
            val local = store.byServerId(deleted.id) ?: deleted.clientId?.let { store.byKey(it) } ?: continue
            store.delete(local.key)
        }
        cursor.setSince(changes.nextSince)
        return incoming.size + changes.deleted.size
    }

    private companion object {
        const val MAX_PASSES = 3
        const val NOT_FOUND = 404
        const val GONE = 410

        /** The server looked at the change and said no; sending it again would not help. */
        fun isRefusal(e: ApiException) = e.status in 400..499 && e.status != 401 && e.status != 403 && e.status != 408 && e.status != 429
    }
}
