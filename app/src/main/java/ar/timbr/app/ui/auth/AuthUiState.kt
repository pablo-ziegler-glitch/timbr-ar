package ar.timbr.app.ui.auth

import ar.timbr.app.domain.model.AddressDetails
import ar.timbr.app.domain.model.AddressSuggestion
import ar.timbr.app.domain.model.LocationType

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val addressQuery: String = "",
    val addressSuggestions: List<AddressSuggestion> = emptyList(),
    val selectedAddress: AddressDetails? = null,
    val addressName: String = "",
    val locationType: LocationType = LocationType.CASA,
    val isLoading: Boolean = false,
    val isAddressLoading: Boolean = false,
    val errorMessage: String? = null,
)

enum class AuthMode {
    SignIn,
    SignUp,
}
