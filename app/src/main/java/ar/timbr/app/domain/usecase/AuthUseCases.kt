package ar.timbr.app.domain.usecase

import ar.timbr.app.domain.model.LocationInput
import ar.timbr.app.domain.repository.AuthRepository

class AuthUseCases(private val repository: AuthRepository) {
    val authState = repository::authState
    val currentUserId = repository::currentUserId

    suspend fun signIn(email: String, password: String) = repository.signIn(email, password)
    suspend fun signUpPublic(fullName: String, email: String, password: String) =
        repository.signUpPublic(fullName, email, password)
    suspend fun signInWithGoogle(idToken: String) = repository.signInWithGoogle(idToken)
    suspend fun signUp(fullName: String, email: String, password: String, locationInput: LocationInput) =
        repository.signUp(fullName, email, password, locationInput)

    suspend fun signOut() = repository.signOut()
}
