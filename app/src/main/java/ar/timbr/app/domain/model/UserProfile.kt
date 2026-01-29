package ar.timbr.app.domain.model

data class UserProfile(
    val uid: String,
    val fullName: String,
    val email: String,
    val homeId: String,
    val phone: String? = null,
    val role: String = "member",
)
