package ar.timbr.app.domain.repository

import ar.timbr.app.domain.model.AddressDetails
import ar.timbr.app.domain.model.AddressSuggestion

interface PlacesRepository {
    fun startNewSession()
    suspend fun autocomplete(query: String): List<AddressSuggestion>
    suspend fun fetchAddress(placeId: String): AddressDetails
}
