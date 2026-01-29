package ar.timbr.app.data.repository

import ar.timbr.app.domain.model.LocationInput
import ar.timbr.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.ZoneId
import java.util.UUID

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {
    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override fun authState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signUp(
        fullName: String,
        email: String,
        password: String,
        locationInput: LocationInput,
    ) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("No se pudo crear el usuario")
        user.updateProfile(userProfileChangeRequest {
            displayName = fullName
        }).await()

        val homeId = UUID.randomUUID().toString()
        val qrIdHash = hashSha256(homeId)
        val timeZone = ZoneId.systemDefault().id
        val homeRef = firestore.collection("homes").document(homeId)
        val userRef = firestore.collection("users").document(user.uid)

        firestore.runTransaction { transaction ->
            val homeSnapshot = transaction.get(homeRef)
            val role = if (homeSnapshot.exists()) "member" else "owner"
            if (!homeSnapshot.exists()) {
                transaction.set(
                    homeRef,
                    mapOf(
                        "homeId" to homeId,
                        "publicQrId" to qrIdHash,
                        "address" to locationInput.address,
                        "addressName" to locationInput.addressName,
                        "locationType" to locationInput.locationType.name,
                        "placeId" to locationInput.placeId,
                        "latitude" to locationInput.latitude,
                        "longitude" to locationInput.longitude,
                        "isDoorbellEnabled" to true,
                        "scheduleStartMinutes" to 480,
                        "scheduleEndMinutes" to 1200,
                        "timeZone" to timeZone,
                        "rateLimitBaseMinutes" to 5,
                        "rateLimitMaxMinutes" to 120,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            transaction.set(
                userRef,
                mapOf(
                    "uid" to user.uid,
                    "fullName" to fullName,
                    "email" to email,
                    "homeId" to homeId,
                    "role" to role,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
        }.await()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private fun hashSha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
