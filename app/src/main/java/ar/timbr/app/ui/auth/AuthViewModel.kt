package ar.timbr.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.timbr.app.domain.usecase.AuthUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCases: AuthUseCases,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun updateFullName(value: String) {
        _uiState.update { it.copy(fullName = value) }
    }

    fun updateHomeId(value: String) {
        _uiState.update { it.copy(homeId = value) }
    }

    fun signIn() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching {
            authUseCases.signIn(uiState.value.email.trim(), uiState.value.password)
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message) }
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun signUp() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching {
            authUseCases.signUp(
                fullName = uiState.value.fullName.trim(),
                email = uiState.value.email.trim(),
                password = uiState.value.password,
                homeId = uiState.value.homeId.trim(),
            )
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message) }
        }
        _uiState.update { it.copy(isLoading = false) }
    }
}
