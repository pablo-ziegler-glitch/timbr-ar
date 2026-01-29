package ar.timbr.app.ui.auth

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val homeId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
