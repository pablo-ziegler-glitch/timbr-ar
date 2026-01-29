package ar.timbr.app.domain.model

data class HomeConfig(
    val homeId: String,
    val isDoorbellEnabled: Boolean,
    val scheduleStartMinutes: Int,
    val scheduleEndMinutes: Int,
    val timeZone: String,
    val rateLimitBaseMinutes: Int,
    val rateLimitMaxMinutes: Int,
)
