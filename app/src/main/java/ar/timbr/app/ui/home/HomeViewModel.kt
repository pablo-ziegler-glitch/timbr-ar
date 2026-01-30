package ar.timbr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.timbr.app.domain.model.HomeConfig
import ar.timbr.app.domain.model.LocationInput
import ar.timbr.app.domain.model.LocationType
import ar.timbr.app.domain.repository.PlacesRepository
import ar.timbr.app.domain.usecase.AuthUseCases
import ar.timbr.app.domain.usecase.DoorbellUseCases
import com.google.android.gms.common.api.ApiException
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authUseCases: AuthUseCases,
    private val doorbellUseCases: DoorbellUseCases,
    private val messaging: FirebaseMessaging,
    private val placesRepository: PlacesRepository,
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var addressJob: Job? = null
    private var hasActivePlacesSession = false

    init {
        observeProfileAndEvents()
        syncFcmToken()
    }

    private fun observeProfileAndEvents() {
        viewModelScope.launch {
            authUseCases.authState().flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(null)
                } else {
                    doorbellUseCases.observeProfile(userId)
                }
            }.collectLatest { profile ->
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            }
        }

        viewModelScope.launch {
            authUseCases.authState().flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    doorbellUseCases.observeProfile(userId).flatMapLatest { profile ->
                        if (profile == null || profile.homeId.isBlank()) {
                            flowOf(emptyList())
                        } else {
                            doorbellUseCases.observeRingEvents(profile.homeId)
                        }
                    }
                }
            }.collectLatest { events ->
                _uiState.update { it.copy(ringEvents = events) }
            }
        }

        viewModelScope.launch {
            authUseCases.authState().flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(null)
                } else {
                    doorbellUseCases.observeProfile(userId).flatMapLatest { profile ->
                        if (profile == null || profile.homeId.isBlank()) {
                            flowOf(null)
                        } else {
                            doorbellUseCases.observeHomeConfig(profile.homeId)
                        }
                    }
                }
            }.collectLatest { config ->
                _uiState.update {
                    val updatedHomeSetup = if (config != null && !it.homeSetupDraft.isDirty) {
                        it.homeSetupDraft.fromConfig(config)
                    } else {
                        it.homeSetupDraft
                    }
                    it.copy(
                        homeConfig = config,
                        scheduleDraft = config?.toDraft() ?: it.scheduleDraft,
                        homeSetupDraft = updatedHomeSetup,
                    )
                }
            }
        }
    }

    private fun syncFcmToken() {
        viewModelScope.launch {
            val userId = authUseCases.currentUserId ?: return@launch
            messaging.token.addOnSuccessListener { token ->
                viewModelScope.launch {
                    runCatching { doorbellUseCases.updateFcmToken(userId, token) }
                }
            }
        }
    }

    fun signOut() = viewModelScope.launch {
        authUseCases.signOut()
    }

    fun updateDoorbellEnabled(value: Boolean) {
        _uiState.update {
            it.copy(scheduleDraft = it.scheduleDraft.copy(isDoorbellEnabled = value, errorMessage = null))
        }
    }

    fun updateStartTime(value: String) {
        _uiState.update {
            it.copy(scheduleDraft = it.scheduleDraft.copy(startTime = value, errorMessage = null))
        }
    }

    fun updateEndTime(value: String) {
        _uiState.update {
            it.copy(scheduleDraft = it.scheduleDraft.copy(endTime = value, errorMessage = null))
        }
    }

    fun updateTimeZone(value: String) {
        _uiState.update {
            it.copy(scheduleDraft = it.scheduleDraft.copy(timeZone = value, errorMessage = null))
        }
    }

    fun updateRateLimitBase(value: String) {
        _uiState.update {
            it.copy(scheduleDraft = it.scheduleDraft.copy(rateLimitBaseMinutes = value, errorMessage = null))
        }
    }

    fun updateRateLimitMax(value: String) {
        _uiState.update {
            it.copy(scheduleDraft = it.scheduleDraft.copy(rateLimitMaxMinutes = value, errorMessage = null))
        }
    }

    fun updateBlockedPhone(value: String) {
        _uiState.update {
            it.copy(blockDraft = it.blockDraft.copy(phone = value, errorMessage = null))
        }
    }

    fun updateHomeAddressQuery(value: String) {
        _uiState.update {
            it.copy(
                homeSetupDraft = it.homeSetupDraft.copy(
                    addressQuery = value,
                    selectedAddress = null,
                    errorMessage = null,
                    successMessage = null,
                    isDirty = true,
                )
            )
        }
        addressJob?.cancel()
        if (value.trim().length < 3) {
            hasActivePlacesSession = false
            _uiState.update {
                it.copy(homeSetupDraft = it.homeSetupDraft.copy(addressSuggestions = emptyList(), isAddressLoading = false))
            }
            return
        }
        if (!hasActivePlacesSession) {
            placesRepository.startNewSession()
            hasActivePlacesSession = true
        }
        addressJob = viewModelScope.launch {
            delay(300)
            _uiState.update {
                it.copy(homeSetupDraft = it.homeSetupDraft.copy(isAddressLoading = true))
            }
            runCatching { placesRepository.autocomplete(value.trim()) }
                .onSuccess { suggestions ->
                    _uiState.update {
                        it.copy(
                            homeSetupDraft = it.homeSetupDraft.copy(
                                addressSuggestions = suggestions,
                                isAddressLoading = false,
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            homeSetupDraft = it.homeSetupDraft.copy(
                                addressSuggestions = emptyList(),
                                isAddressLoading = false,
                                errorMessage = mapPlacesError(error),
                            )
                        )
                    }
                }
        }
    }

    fun selectHomeAddress(placeId: String) {
        _uiState.update {
            it.copy(homeSetupDraft = it.homeSetupDraft.copy(isAddressLoading = true, addressSuggestions = emptyList()))
        }
        hasActivePlacesSession = false
        viewModelScope.launch {
            runCatching { placesRepository.fetchAddress(placeId) }
                .onSuccess { address ->
                    _uiState.update {
                        it.copy(
                            homeSetupDraft = it.homeSetupDraft.copy(
                                selectedAddress = address,
                                addressQuery = address.address,
                                isAddressLoading = false,
                                isDirty = true,
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            homeSetupDraft = it.homeSetupDraft.copy(
                                isAddressLoading = false,
                                errorMessage = mapPlacesError(error, defaultMessage = "No se pudo obtener la dirección."),
                            )
                        )
                    }
                }
        }
    }

    fun updateHomeAddressName(value: String) {
        _uiState.update {
            it.copy(
                homeSetupDraft = it.homeSetupDraft.copy(
                    addressName = value,
                    errorMessage = null,
                    successMessage = null,
                    isDirty = true,
                )
            )
        }
    }

    fun updateHomeLocationType(value: LocationType) {
        _uiState.update {
            it.copy(
                homeSetupDraft = it.homeSetupDraft.copy(
                    locationType = value,
                    errorMessage = null,
                    successMessage = null,
                    isDirty = true,
                )
            )
        }
    }

    fun clearHomeAddressSuggestions() {
        _uiState.update {
            it.copy(homeSetupDraft = it.homeSetupDraft.copy(addressSuggestions = emptyList()))
        }
    }

    fun saveHomeLocation() = viewModelScope.launch {
        val profile = uiState.value.profile ?: return@launch
        if (profile.homeId.isNotBlank() && profile.role != "owner") {
            _uiState.update {
                it.copy(homeSetupDraft = it.homeSetupDraft.copy(errorMessage = "Solo el propietario puede editar la ubicación."))
            }
            return@launch
        }
        val draft = uiState.value.homeSetupDraft
        val selectedAddress = draft.selectedAddress
        if (selectedAddress == null || selectedAddress.address.isBlank()) {
            _uiState.update {
                it.copy(homeSetupDraft = draft.copy(errorMessage = "Seleccioná una dirección válida."))
            }
            return@launch
        }
        if (draft.addressName.isBlank()) {
            _uiState.update {
                it.copy(homeSetupDraft = draft.copy(errorMessage = "Ingresá un nombre para la ubicación."))
            }
            return@launch
        }
        _uiState.update {
            it.copy(homeSetupDraft = draft.copy(isSaving = true, errorMessage = null, successMessage = null))
        }
        runCatching {
            doorbellUseCases.upsertHomeLocation(
                userId = profile.uid,
                currentHomeId = profile.homeId,
                locationInput = LocationInput(
                    address = selectedAddress.address,
                    placeId = selectedAddress.placeId,
                    addressName = draft.addressName.trim(),
                    locationType = draft.locationType,
                    latitude = selectedAddress.latitude,
                    longitude = selectedAddress.longitude,
                ),
            )
        }.onSuccess {
            _uiState.update {
                it.copy(
                    homeSetupDraft = it.homeSetupDraft.copy(
                        isSaving = false,
                        isDirty = false,
                        successMessage = "Ubicación guardada. El QR se actualizó.",
                    )
                )
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    homeSetupDraft = it.homeSetupDraft.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "No se pudo guardar la ubicación.",
                    )
                )
            }
        }
    }

    fun updateBlockReason(value: String) {
        _uiState.update {
            it.copy(blockDraft = it.blockDraft.copy(reason = value, errorMessage = null))
        }
    }

    fun blockPhone() = viewModelScope.launch {
        val profile = uiState.value.profile ?: return@launch
        val draft = uiState.value.blockDraft
        val phoneValue = draft.phone.trim()
        if (phoneValue.isBlank()) {
            _uiState.update {
                it.copy(blockDraft = draft.copy(errorMessage = "Ingresá un teléfono válido."))
            }
            return@launch
        }
        _uiState.update { it.copy(blockDraft = draft.copy(isSaving = true, errorMessage = null)) }
        runCatching { doorbellUseCases.blockPhone(profile.homeId, phoneValue, draft.reason.trim()) }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        blockDraft = draft.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudo bloquear.",
                        )
                    )
                }
            }
        _uiState.update { it.copy(blockDraft = uiState.value.blockDraft.copy(isSaving = false, phone = "", reason = "")) }
    }

    fun saveSchedule() = viewModelScope.launch {
        val profile = uiState.value.profile ?: return@launch
        val draft = uiState.value.scheduleDraft
        if (uiState.value.homeConfig == null) {
            _uiState.update {
                it.copy(
                    scheduleDraft = draft.copy(
                        errorMessage = "Configurá el hogar antes de editar el horario.",
                    )
                )
            }
            return@launch
        }
        val parsed = parseDraft(draft, uiState.value.homeConfig)
        if (parsed == null) {
            _uiState.update {
                it.copy(
                    scheduleDraft = draft.copy(
                        errorMessage = "Formato de horario inválido. Usá HH:mm (ej. 08:00).",
                    )
                )
            }
            return@launch
        }

        _uiState.update { it.copy(scheduleDraft = draft.copy(isSaving = true, errorMessage = null)) }
        runCatching { doorbellUseCases.updateHomeSchedule(profile.homeId, parsed) }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        scheduleDraft = draft.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudo guardar.",
                        )
                    )
                }
            }
        _uiState.update { it.copy(scheduleDraft = uiState.value.scheduleDraft.copy(isSaving = false)) }
    }

    private fun parseDraft(draft: ScheduleDraft, config: HomeConfig?): HomeConfig? {
        if (config == null) return null
        return runCatching {
            val start = LocalTime.parse(draft.startTime, timeFormatter)
            val end = LocalTime.parse(draft.endTime, timeFormatter)
            val startMinutes = start.hour * 60 + start.minute
            val endMinutes = end.hour * 60 + end.minute
            val baseMinutes = draft.rateLimitBaseMinutes.trim().toInt()
            val maxMinutes = draft.rateLimitMaxMinutes.trim().toInt()
            require(baseMinutes > 0)
            require(maxMinutes >= baseMinutes)
            HomeConfig(
                homeId = config.homeId,
                publicQrId = config.publicQrId,
                address = config.address,
                addressName = config.addressName,
                locationType = config.locationType,
                placeId = config.placeId,
                latitude = config.latitude,
                longitude = config.longitude,
                isDoorbellEnabled = draft.isDoorbellEnabled,
                scheduleStartMinutes = startMinutes,
                scheduleEndMinutes = endMinutes,
                timeZone = draft.timeZone.trim(),
                rateLimitBaseMinutes = baseMinutes,
                rateLimitMaxMinutes = maxMinutes,
            )
        }.getOrNull()
    }

    private fun HomeSetupDraft.fromConfig(config: HomeConfig): HomeSetupDraft {
        val addressDetails = ar.timbr.app.domain.model.AddressDetails(
            placeId = config.placeId,
            address = config.address,
            latitude = config.latitude,
            longitude = config.longitude,
        )
        return copy(
            addressQuery = config.address,
            selectedAddress = addressDetails,
            addressName = config.addressName,
            locationType = config.locationType,
            isAddressLoading = false,
            addressSuggestions = emptyList(),
            errorMessage = null,
            successMessage = null,
            isDirty = false,
        )
    }

    private fun mapPlacesError(
        error: Throwable,
        defaultMessage: String = "No se pudo buscar la dirección.",
    ): String {
        val apiException = error as? ApiException
        return when (apiException?.statusCode) {
            9011 -> "La API key de Google Maps/Places es inválida (9011). " +
                "Configurá google_maps_key en strings.xml, habilitá Places API " +
                "y verificá la facturación en Google Cloud."
            else -> error.message ?: defaultMessage
        }
    }
}

private fun HomeConfig.toDraft(): ScheduleDraft {
    val start = minutesToTime(scheduleStartMinutes)
    val end = minutesToTime(scheduleEndMinutes)
    return ScheduleDraft(
        isDoorbellEnabled = isDoorbellEnabled,
        startTime = start,
        endTime = end,
        timeZone = timeZone,
        rateLimitBaseMinutes = rateLimitBaseMinutes.toString(),
        rateLimitMaxMinutes = rateLimitMaxMinutes.toString(),
    )
}

private fun minutesToTime(totalMinutes: Int): String {
    val hours = (totalMinutes.coerceIn(0, 1439)) / 60
    val minutes = totalMinutes % 60
    return "%02d:%02d".format(hours, minutes)
}
