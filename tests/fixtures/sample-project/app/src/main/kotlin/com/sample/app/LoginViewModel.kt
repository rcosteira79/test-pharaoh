package com.sample.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sample.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    data object Idle : LoginUiState()

    data object Loading : LoginUiState()

    data object Success : LoginUiState()

    data class Error(
        val message: String,
    ) : LoginUiState()
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val repo: AuthRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
        val state: StateFlow<LoginUiState> = _state

        fun submit(
            email: String,
            password: String,
        ) {
            _state.value = LoginUiState.Loading
            viewModelScope.launch {
                _state.value =
                    repo.login(email, password).fold(
                        onSuccess = { LoginUiState.Success },
                        onFailure = { LoginUiState.Error(it.message ?: "unknown") },
                    )
            }
        }
    }
