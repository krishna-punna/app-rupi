package com.dailyrupi.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyrupi.app.data.AuthState
import com.dailyrupi.app.data.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(private val session: SessionManager) : ViewModel() {

    val state: StateFlow<AuthState> = session.state

    init {
        viewModelScope.launch { session.start() }
    }
}
