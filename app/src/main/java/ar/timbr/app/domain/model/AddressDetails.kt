package ar.timbr.app.domain.model

data class AddressDetails(
    val placeId: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
)
