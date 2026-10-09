package com.dailyrupi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyrupi.app.data.AuthState
import com.dailyrupi.app.ui.auth.ChangePasswordScreen
import com.dailyrupi.app.ui.auth.LoginScreen
import com.dailyrupi.app.ui.auth.ServerSetupScreen
import com.dailyrupi.app.ui.components.FullScreenLoading

/** Log in, Change password and Server setup sit outside the bottom bar; everything else is [MainScreen]. */
@Composable
fun DailyRupiRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        AuthState.Loading -> FullScreenLoading()
        AuthState.NeedsServer -> ServerSetupScreen(onDone = {}, onBack = null)
        is AuthState.LoggedOut -> {
            var editingServer by rememberSaveable { mutableStateOf(false) }
            if (editingServer) {
                BackHandler { editingServer = false }
                ServerSetupScreen(onDone = { editingServer = false }, onBack = { editingServer = false })
            } else {
                LoginScreen(notice = current.notice, onChangeServer = { editingServer = true })
            }
        }
        is AuthState.MustChangePassword -> ChangePasswordScreen(forced = true, onBack = null)
        is AuthState.LoggedIn -> MainScreen()
    }
}
