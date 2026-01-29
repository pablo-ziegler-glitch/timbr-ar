package ar.timbr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.timbr.app.domain.model.HomeConfig
import ar.timbr.app.domain.usecase.AuthUseCases
import ar.timbr.app.domain.usecase.DoorbellUseCases
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
                    it.copy(
                        homeConfig = config,
                        scheduleDraft = config?.toDraft() ?: it.scheduleDraft,
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
        val parsed = parseDraft(draft)
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

    private fun parseDraft(draft: ScheduleDraft): HomeConfig? {
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
                homeId = uiState.value.profile?.homeId.orEmpty(),
                isDoorbellEnabled = draft.isDoorbellEnabled,
                scheduleStartMinutes = startMinutes,
                scheduleEndMinutes = endMinutes,
                timeZone = draft.timeZone.trim(),
                rateLimitBaseMinutes = baseMinutes,
                rateLimitMaxMinutes = maxMinutes,
            )
        }.getOrNull()
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
