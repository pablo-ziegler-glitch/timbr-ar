package ar.timbr.app.ui.home

import ar.timbr.app.domain.model.AddressDetails
import ar.timbr.app.domain.model.AddressSuggestion
import ar.timbr.app.domain.model.HomeConfig
import ar.timbr.app.domain.model.LocationType
import ar.timbr.app.domain.model.RingEvent
import ar.timbr.app.domain.model.UserProfile

data class HomeUiState(
    val profile: UserProfile? = null,
    val homeConfig: HomeConfig? = null,
    val homeSetupDraft: HomeSetupDraft = HomeSetupDraft(),
    val scheduleDraft: ScheduleDraft = ScheduleDraft(),
    val blockDraft: BlockDraft = BlockDraft(),
    val ringEvents: List<RingEvent> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

data class HomeSetupDraft(
    val addressQuery: String = "",
    val selectedAddress: AddressDetails? = null,
    val addressSuggestions: List<AddressSuggestion> = emptyList(),
    val addressName: String = "",
    val locationType: LocationType = LocationType.CASA,
    val isAddressLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

data class ScheduleDraft(
    val isDoorbellEnabled: Boolean = true,
    val startTime: String = "08:00",
    val endTime: String = "20:00",
    val timeZone: String = "America/Argentina/Buenos_Aires",
    val rateLimitBaseMinutes: String = "5",
    val rateLimitMaxMinutes: String = "120",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

data class BlockDraft(
    val phone: String = "",
    val reason: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
