package ar.timbr.app.domain.usecase

import ar.timbr.app.domain.repository.AuthRepository

class AuthUseCases(private val repository: AuthRepository) {
    val authState = repository::authState
    val currentUserId = repository::currentUserId

    suspend fun signIn(email: String, password: String) = repository.signIn(email, password)
    suspend fun signUp(fullName: String, email: String, password: String, homeId: String) =
        repository.signUp(fullName, email, password, homeId)

    suspend fun signOut() = repository.signOut()
}
