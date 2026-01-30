package ar.timbr.app.domain.repository

import ar.timbr.app.domain.model.HomeConfig
import ar.timbr.app.domain.model.LocationInput
import ar.timbr.app.domain.model.RingEvent
import ar.timbr.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface DoorbellRepository {
    fun observeProfile(userId: String): Flow<UserProfile?>
    fun observeHomeConfig(homeId: String): Flow<HomeConfig?>
    fun observeRingEvents(homeId: String): Flow<List<RingEvent>>
    suspend fun updateFcmToken(userId: String, token: String)
    suspend fun updateHomeSchedule(homeId: String, config: HomeConfig)
    suspend fun upsertHomeLocation(userId: String, currentHomeId: String?, locationInput: LocationInput): String
    suspend fun blockPhone(homeId: String, phone: String, reason: String?)
}
