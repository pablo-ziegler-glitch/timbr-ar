package ar.timbr.app.domain.model

data class HomeConfig(
    val homeId: String,
    val publicQrId: String,
    val address: String,
    val addressName: String,
    val locationType: LocationType,
    val placeId: String,
    val latitude: Double?,
    val longitude: Double?,
    val isDoorbellEnabled: Boolean,
    val scheduleStartMinutes: Int,
    val scheduleEndMinutes: Int,
    val timeZone: String,
    val rateLimitBaseMinutes: Int,
    val rateLimitMaxMinutes: Int,
)
