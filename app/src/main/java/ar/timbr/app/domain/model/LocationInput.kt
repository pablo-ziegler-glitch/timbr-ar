package ar.timbr.app.domain.model

data class LocationInput(
    val address: String,
    val placeId: String,
    val addressName: String,
    val locationType: LocationType,
    val latitude: Double?,
    val longitude: Double?,
)
