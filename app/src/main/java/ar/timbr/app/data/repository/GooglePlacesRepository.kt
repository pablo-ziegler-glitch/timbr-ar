package ar.timbr.app.data.repository

import ar.timbr.app.domain.model.AddressDetails
import ar.timbr.app.domain.model.AddressSuggestion
import ar.timbr.app.domain.repository.PlacesRepository
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await

class GooglePlacesRepository(
    private val placesClient: PlacesClient,
) : PlacesRepository {
    private var sessionToken: AutocompleteSessionToken = AutocompleteSessionToken.newInstance()

    override fun startNewSession() {
        sessionToken = AutocompleteSessionToken.newInstance()
    }

    override suspend fun autocomplete(query: String): List<AddressSuggestion> {
        val request = FindAutocompletePredictionsRequest.builder()
            .setTypeFilter(TypeFilter.ADDRESS)
            .setQuery(query)
            .setSessionToken(sessionToken)
            .build()
        val response = placesClient.findAutocompletePredictions(request).await()
        return response.autocompletePredictions.map { prediction ->
            AddressSuggestion(
                placeId = prediction.placeId,
                primaryText = prediction.getPrimaryText(null).toString(),
                secondaryText = prediction.getSecondaryText(null).toString(),
                fullText = prediction.getFullText(null).toString(),
            )
        }
    }

    override suspend fun fetchAddress(placeId: String): AddressDetails {
        val request = FetchPlaceRequest.builder(
            placeId,
            listOf(Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.ID),
        ).setSessionToken(sessionToken).build()
        val response = placesClient.fetchPlace(request).await()
        val place = response.place
        val latLng = place.latLng
        return AddressDetails(
            placeId = place.id ?: placeId,
            address = place.address.orEmpty(),
            latitude = latLng?.latitude,
            longitude = latLng?.longitude,
        )
    }
}
