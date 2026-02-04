package ar.timbr.app.domain.repository

import ar.timbr.app.domain.model.LocationInput
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: String?
    fun authState(): Flow<String?>
    suspend fun signIn(email: String, password: String)
    suspend fun signUpPublic(fullName: String, email: String, password: String)
    suspend fun signInWithGoogle(idToken: String)
    suspend fun signUp(
        fullName: String,
        email: String,
        password: String,
        locationInput: LocationInput,
    )
    suspend fun signOut()
}
