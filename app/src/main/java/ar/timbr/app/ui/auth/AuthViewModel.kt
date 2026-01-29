package ar.timbr.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.timbr.app.domain.model.LocationInput
import ar.timbr.app.domain.model.LocationType
import ar.timbr.app.domain.repository.PlacesRepository
import ar.timbr.app.domain.usecase.AuthUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCases: AuthUseCases,
    private val placesRepository: PlacesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private var addressJob: Job? = null
    private var hasActivePlacesSession = false

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun updateFullName(value: String) {
        _uiState.update { it.copy(fullName = value) }
    }

    fun updateAddressQuery(value: String) {
        _uiState.update {
            it.copy(
                addressQuery = value,
                selectedAddress = null,
                errorMessage = null,
            )
        }
        addressJob?.cancel()
        if (value.trim().length < 3) {
            hasActivePlacesSession = false
            _uiState.update { it.copy(addressSuggestions = emptyList(), isAddressLoading = false) }
            return
        }
        if (!hasActivePlacesSession) {
            placesRepository.startNewSession()
            hasActivePlacesSession = true
        }
        addressJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isAddressLoading = true) }
            runCatching { placesRepository.autocomplete(value.trim()) }
                .onSuccess { suggestions ->
                    _uiState.update { it.copy(addressSuggestions = suggestions, isAddressLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            addressSuggestions = emptyList(),
                            isAddressLoading = false,
                            errorMessage = error.message ?: "No se pudo buscar la dirección.",
                        )
                    }
                }
        }
    }

    fun selectAddress(placeId: String) {
        _uiState.update { it.copy(isAddressLoading = true, addressSuggestions = emptyList()) }
        hasActivePlacesSession = false
        viewModelScope.launch {
            runCatching { placesRepository.fetchAddress(placeId) }
                .onSuccess { address ->
                    _uiState.update {
                        it.copy(
                            selectedAddress = address,
                            addressQuery = address.address,
                            isAddressLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAddressLoading = false,
                            errorMessage = error.message ?: "No se pudo obtener la dirección.",
                        )
                    }
                }
        }
    }

    fun updateAddressName(value: String) {
        _uiState.update { it.copy(addressName = value) }
    }

    fun updateLocationType(value: LocationType) {
        _uiState.update { it.copy(locationType = value) }
    }

    fun clearAddressSuggestions() {
        _uiState.update { it.copy(addressSuggestions = emptyList()) }
    }

    fun toggleMode() {
        _uiState.update {
            val newMode = if (it.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn
            val baseState = it.copy(
                mode = newMode,
                errorMessage = null,
                addressSuggestions = emptyList(),
                isAddressLoading = false,
            )
            if (newMode == AuthMode.SignIn) {
                hasActivePlacesSession = false
                baseState.copy(
                    fullName = "",
                    addressQuery = "",
                    selectedAddress = null,
                    addressName = "",
                    locationType = LocationType.CASA,
                )
            } else {
                baseState
            }
        }
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
        val state = uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresá email y contraseña.") }
            return@launch
        }
        if (state.fullName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresá tu nombre completo.") }
            return@launch
        }
        val selectedAddress = state.selectedAddress
        if (selectedAddress == null || selectedAddress.address.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Seleccioná una dirección válida.") }
            return@launch
        }
        if (state.addressName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresá un nombre para la ubicación.") }
            return@launch
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching {
            authUseCases.signUp(
                fullName = state.fullName.trim(),
                email = state.email.trim(),
                password = state.password,
                locationInput = LocationInput(
                    address = selectedAddress.address,
                    placeId = selectedAddress.placeId,
                    addressName = state.addressName.trim(),
                    locationType = state.locationType,
                    latitude = selectedAddress.latitude,
                    longitude = selectedAddress.longitude,
                ),
            )
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message) }
        }
        _uiState.update { it.copy(isLoading = false) }
    }
}
