package ar.timbr.app.domain.model

import java.time.Instant

data class RingEvent(
    val id: String,
    val homeId: String,
    val visitorPhone: String,
    val createdAt: Instant,
    val status: String,
)
