package ar.timbr.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: String?
    fun authState(): Flow<String?>
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(fullName: String, email: String, password: String, homeId: String)
    suspend fun signOut()
}
