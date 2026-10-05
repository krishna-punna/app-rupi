package com.dailyrupi.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyrupi.app.data.AuthState
import com.dailyrupi.app.data.SessionManager
import com.dailyrupi.core.auth.PasswordRules
import com.dailyrupi.core.net.ServerAddress
import com.dailyrupi.core.net.ServerCheck
import com.dailyrupi.core.net.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerSetupUiState(
    val address: String = "",
    val checking: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val session: SessionManager,
    private val check: ServerCheck,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerSetupUiState(address = session.serverUrl?.toString().orEmpty()))
    val state: StateFlow<ServerSetupUiState> = _state.asStateFlow()

    fun onAddressChange(value: String) = _state.update { it.copy(address = value, error = null, done = false) }

    fun doneHandled() = _state.update { it.copy(done = false) }

    /** Tests the connection and, if the server answers like Daily Rupi, saves it. */
    fun save() {
        val url = ServerAddress.parse(_state.value.address)
        if (url == null) {
            _state.update { it.copy(error = "Enter an address like https://rupi.example.com") }
            return
        }
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            val problem = check.problem(url)
            if (problem == null) session.setServer(url)
            _state.update { it.copy(checking = false, error = problem, done = problem == null) }
        }
    }
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    val serverUrl: String get() = session.serverUrl?.toString().orEmpty()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun login() {
        val current = _state.value
        if (current.busy) return
        if (current.username.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(error = "Enter your username and password") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                session.login(current.username, current.password)
                _state.update { it.copy(busy = false, password = "") }
            } catch (e: Exception) {
                // The server sends one message for every failure, lockout included.
                _state.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }
}

data class ChangePasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    private val username: String
        get() = when (val auth = session.state.value) {
            is AuthState.MustChangePassword -> auth.username
            is AuthState.LoggedIn -> auth.user.username
            else -> ""
        }

    fun onCurrentChange(value: String) = _state.update { it.copy(current = value, error = null) }
    fun onNewChange(value: String) = _state.update { it.copy(new = value, error = null) }
    fun onConfirmChange(value: String) = _state.update { it.copy(confirm = value, error = null) }

    /** On success the server ends the session and the app returns to Log in. */
    fun submit() {
        val form = _state.value
        if (form.busy) return
        val problem = PasswordRules.problem(username, form.current, form.new, form.confirm)
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                session.changePassword(form.current, form.new)
                _state.value = ChangePasswordUiState()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }
}
