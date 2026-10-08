package com.dailyrupi.app.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyrupi.app.ui.components.CenteredMessage
import com.dailyrupi.app.ui.components.SyncLabel
import com.dailyrupi.app.ui.components.FullScreenLoading
import com.dailyrupi.core.format.Formats
import com.dailyrupi.core.model.ExpenseSummary
import com.dailyrupi.core.sync.LocalExpense

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSync: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Undo for 5 seconds: the repository deletes when the time is up, which ends this effect.
    LaunchedEffect(state.pendingDelete?.key) {
        val pending = state.pendingDelete ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "Deleted ${pending.itemName} ${Formats.rupees(pending.amount)}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.messageShown()
        snackbar.showSnackbar(message)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = { SyncAction(state.unsynced, state.needsAttention, onSync) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add expense")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SummaryBar(state.summary)
            if (state.loading) {
                FullScreenLoading()
            } else {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ExpenseList(state, onOpen)
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(summary: ExpenseSummary?) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryCell("Today", summary?.today?.let(Formats::rupees), Modifier.weight(1f))
            SummaryCell("This week", summary?.week?.let(Formats::rupees), Modifier.weight(1f))
            SummaryCell("This month", summary?.month?.let(Formats::rupees), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String?, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            value ?: "–",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Opens the Sync screen; the badge counts changes not on the server yet, red when one was refused. */
@Composable
private fun SyncAction(unsynced: Int, needsAttention: Int, onSync: () -> Unit) {
    val description = when {
        needsAttention > 0 -> "Sync, $needsAttention need attention"
        unsynced > 0 -> "Sync, $unsynced not synced"
        else -> "Sync"
    }
    IconButton(onClick = onSync) {
        BadgedBox(
            badge = {
                if (unsynced > 0) {
                    Badge(
                        containerColor = if (needsAttention > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    ) { Text(unsynced.toString()) }
                }
            },
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = description)
        }
    }
}

@Composable
private fun ExpenseList(state: ExpensesUiState, onOpen: (String) -> Unit) {
    if (state.days.isEmpty()) {
        // Still scrollable, so pull to refresh works on an empty list.
        LazyColumn(Modifier.fillMaxSize()) {
            item { CenteredMessage("No expenses yet. Tap + to add one.", Modifier.padding(top = 48.dp)) }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        .semantics(mergeDescendants = true) { heading() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        Formats.dayLabel(day.date, state.today),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(Formats.rupees(day.total), style = MaterialTheme.typography.titleSmall)
                }
            }
            items(day.expenses, key = { it.key }) { expense ->
                ExpenseRow(expense, onClick = { onOpen(expense.key) })
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: LocalExpense, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(expense.itemName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                Formats.time(expense.spentAt),
                expense.paymentMethodName,
                expense.subCategoryName,
            ).joinToString(" · ")
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            expense.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SyncLabel(expense)
        }
        Text(
            Formats.rupees(expense.amount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
