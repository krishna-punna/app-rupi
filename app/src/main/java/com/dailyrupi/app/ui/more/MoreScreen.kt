package com.dailyrupi.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyrupi.app.BuildConfig
import com.dailyrupi.app.data.AuthState
import com.dailyrupi.app.data.ExpenseRepository
import com.dailyrupi.app.data.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val session: SessionManager,
    expenses: ExpenseRepository,
) : ViewModel() {

    /** Changes not on the server yet; Log out warns that they will be lost. */
    val unsynced: StateFlow<Int> = expenses.unsynced.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val username: String
        get() = (session.state.value as? AuthState.LoggedIn)?.user?.username.orEmpty()

    val serverUrl: String get() = session.serverUrl?.toString().orEmpty()

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    fun logout() {
        if (_loggingOut.value) return
        _loggingOut.value = true
        viewModelScope.launch { session.logout() }
    }
}

/** Settings: account, sync, server address, log out and app version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onSync: () -> Unit,
    onChangePassword: () -> Unit,
    onChangeServer: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val loggingOut by viewModel.loggingOut.collectAsStateWithLifecycle()
    val unsynced by viewModel.unsynced.collectAsStateWithLifecycle()
    var confirmingLogout by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("More") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Logged in as") },
                supportingContent = { Text(viewModel.username.ifEmpty { "–" }) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Sync") },
                supportingContent = { Text(if (unsynced == 0) "All synced" else "$unsynced not synced") },
                modifier = Modifier.clickable(onClick = onSync),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Change password") },
                modifier = Modifier.clickable(onClick = onChangePassword),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Server address") },
                supportingContent = { Text(viewModel.serverUrl) },
                modifier = Modifier.clickable(onClick = onChangeServer),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Ends the session and clears this phone's data") },
                modifier = Modifier.clickable(enabled = !loggingOut) { confirmingLogout = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("App version") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
        }
    }

    if (confirmingLogout) {
        AlertDialog(
            onDismissRequest = { confirmingLogout = false },
            title = { Text("Log out?") },
            text = {
                Text(
                    when (unsynced) {
                        0 -> "Ends the session and clears this phone's data."
                        1 -> "1 change has not reached the server and will be lost."
                        else -> "$unsynced changes have not reached the server and will be lost."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingLogout = false
                    viewModel.logout()
                }) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingLogout = false }) { Text("Cancel") }
            },
        )
    }
}
