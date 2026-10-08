package com.dailyrupi.app.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.data.SessionManager
import com.dailyrupi.app.sync.SyncProblem
import com.dailyrupi.app.sync.SyncRunner
import com.dailyrupi.app.sync.SyncStatus
import com.dailyrupi.app.ui.components.SyncLabel
import com.dailyrupi.core.format.Formats
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SyncViewModel @Inject constructor(
    expenses: ExpenseRepository,
    private val sync: SyncRunner,
    private val session: SessionManager,
) : ViewModel() {

    val unsynced: StateFlow<List<LocalExpense>> =
        expenses.unsynced.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val status: StateFlow<SyncStatus> = sync.status

    /** Sends every change now, instead of waiting for the background sync. */
    fun pushNow() {
        if (status.value.running) return
        viewModelScope.launch { sync.run() }
    }

    fun logInAgain() = session.logInAgain()
}

/** What has not reached the server yet, how the last sync went, and Push now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val unsynced by viewModel.unsynced.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "status") {
                StatusCard(status, unsynced, onPushNow = viewModel::pushNow, onLogIn = viewModel::logInAgain)
            }
            if (unsynced.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Everything on this phone is on the server.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                item(key = "header") {
                    Text(
                        "Waiting to sync",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(unsynced, key = { it.key }) { expense ->
                    UnsyncedRow(
                        expense,
                        // A delete waiting to be sent has nothing left to edit.
                        onClick = if (expense.state == SyncState.DELETE) null else ({ onOpen(expense.key) }),
                    )
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: SyncStatus,
    unsynced: List<LocalExpense>,
    onPushNow: () -> Unit,
    onLogIn: () -> Unit,
) {
    val attention = unsynced.count { it.needsAttention }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    unsynced.isEmpty() -> "All synced"
                    attention > 0 -> "${unsynced.size} not synced, $attention need attention"
                    else -> "${unsynced.size} not synced"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Last synced: " + (status.lastSyncAt?.let(::formatTime) ?: "not yet"),
                style = MaterialTheme.typography.bodyMedium,
            )
            status.problem?.let {
                Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (attention > 0) {
                Text(
                    "The server refused some changes. Open each one to fix it, or delete it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPushNow, enabled = !status.running) { Text("Push now") }
                if (status.running) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (status.problem == SyncProblem.NeedsLogin) {
                OutlinedButton(onClick = onLogIn) { Text("Log in again") }
            }
        }
    }
}

@Composable
private fun UnsyncedRow(expense: LocalExpense, onClick: (() -> Unit)?) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClickLabel = "Edit", onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(expense.itemName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formats.date(expense.spentAt.toLocalDate())} · ${Formats.time(expense.spentAt)} · ${action(expense)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SyncLabel(expense)
            expense.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(Formats.rupees(expense.amount), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun action(expense: LocalExpense) = when (expense.state) {
    SyncState.CREATE -> "New"
    SyncState.UPDATE -> "Edited"
    SyncState.DELETE -> "Deleted"
    SyncState.SYNCED -> "Synced"
}

private fun formatTime(epochMillis: Long): String {
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    return "${Formats.dayLabel(at.toLocalDate(), java.time.LocalDate.now())}, ${Formats.time(at)}"
}
