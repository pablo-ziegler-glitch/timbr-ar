package ar.timbr.app.domain.model

data class AddressSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String,
)
