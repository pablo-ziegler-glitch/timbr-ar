package ar.timbr.app.domain.usecase

import ar.timbr.app.domain.model.HomeConfig
import ar.timbr.app.domain.model.LocationInput
import ar.timbr.app.domain.repository.DoorbellRepository

class DoorbellUseCases(private val repository: DoorbellRepository) {
    fun observeProfile(userId: String) = repository.observeProfile(userId)
    fun observeHomeConfig(homeId: String) = repository.observeHomeConfig(homeId)
    fun observeRingEvents(homeId: String) = repository.observeRingEvents(homeId)
    suspend fun updateFcmToken(userId: String, token: String) = repository.updateFcmToken(userId, token)
    suspend fun updateHomeSchedule(homeId: String, config: HomeConfig) =
        repository.updateHomeSchedule(homeId, config)
    suspend fun upsertHomeLocation(userId: String, currentHomeId: String?, locationInput: LocationInput) =
        repository.upsertHomeLocation(userId, currentHomeId, locationInput)
    suspend fun blockPhone(homeId: String, phone: String, reason: String?) =
        repository.blockPhone(homeId, phone, reason)
}
