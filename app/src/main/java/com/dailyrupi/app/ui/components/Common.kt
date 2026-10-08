package com.dailyrupi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyrupi.core.sync.LocalExpense
import com.dailyrupi.core.sync.SyncState

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A message in the middle of the screen, with an optional retry. */
@Composable
fun CenteredMessage(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(
            message,
            modifier = modifier,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** "Not synced" or "Needs attention" under an expense that has not reached the server; nothing once synced. */
@Composable
fun SyncLabel(expense: LocalExpense, modifier: Modifier = Modifier) {
    val (text, color) = when {
        expense.needsAttention -> "Needs attention" to MaterialTheme.colorScheme.error
        expense.state == SyncState.SYNCED -> return
        expense.state == SyncState.DELETE -> "Deleting" to MaterialTheme.colorScheme.tertiary
        else -> "Not synced" to MaterialTheme.colorScheme.tertiary
    }
    Text(text, modifier = modifier, color = color, style = MaterialTheme.typography.labelSmall)
}
