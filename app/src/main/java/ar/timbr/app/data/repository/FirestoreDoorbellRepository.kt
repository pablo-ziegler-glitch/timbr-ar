package ar.timbr.app.data.repository

import ar.timbr.app.domain.model.HomeConfig
import ar.timbr.app.domain.model.RingEvent
import ar.timbr.app.domain.model.UserProfile
import ar.timbr.app.domain.repository.DoorbellRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

class FirestoreDoorbellRepository(
    private val firestore: FirebaseFirestore,
) : DoorbellRepository {
    override fun observeProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data
                val profile = data?.let {
                    UserProfile(
                        uid = it["uid"] as? String ?: userId,
                        fullName = it["fullName"] as? String ?: "",
                        email = it["email"] as? String ?: "",
                        homeId = it["homeId"] as? String ?: "",
                        phone = it["phone"] as? String,
                        role = it["role"] as? String ?: "member",
                    )
                }
                trySend(profile)
            }
        awaitClose { registration.remove() }
    }

    override fun observeHomeConfig(homeId: String): Flow<HomeConfig?> = callbackFlow {
        val registration = firestore.collection("homes")
            .document(homeId)
            .addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data
                val config = data?.let {
                    HomeConfig(
                        homeId = homeId,
                        isDoorbellEnabled = it["isDoorbellEnabled"] as? Boolean ?: true,
                        scheduleStartMinutes = (it["scheduleStartMinutes"] as? Number)?.toInt() ?: 480,
                        scheduleEndMinutes = (it["scheduleEndMinutes"] as? Number)?.toInt() ?: 1200,
                        timeZone = it["timeZone"] as? String ?: "America/Argentina/Buenos_Aires",
                        rateLimitBaseMinutes = (it["rateLimitBaseMinutes"] as? Number)?.toInt() ?: 5,
                        rateLimitMaxMinutes = (it["rateLimitMaxMinutes"] as? Number)?.toInt() ?: 120,
                    )
                }
                trySend(config)
            }
        awaitClose { registration.remove() }
    }

    override fun observeRingEvents(homeId: String): Flow<List<RingEvent>> = callbackFlow {
        val registration = firestore.collection("homes")
            .document(homeId)
            .collection("ringEvents")
            .orderBy("createdAt")
            .limitToLast(20)
            .addSnapshotListener { snapshot, _ ->
                val events = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    RingEvent(
                        id = doc.id,
                        homeId = homeId,
                        visitorPhone = data["visitorPhone"] as? String ?: "",
                        createdAt = Instant.ofEpochMilli((data["createdAt"] as? Number)?.toLong() ?: 0L),
                        status = data["status"] as? String ?: "pending",
                    )
                } ?: emptyList()
                trySend(events)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun updateFcmToken(userId: String, token: String) {
        firestore.collection("users")
            .document(userId)
            .update(mapOf("fcmToken" to token))
            .await()
    }

    override suspend fun updateHomeSchedule(homeId: String, config: HomeConfig) {
        firestore.collection("homes")
            .document(homeId)
            .update(
                mapOf(
                    "isDoorbellEnabled" to config.isDoorbellEnabled,
                    "scheduleStartMinutes" to config.scheduleStartMinutes,
                    "scheduleEndMinutes" to config.scheduleEndMinutes,
                    "timeZone" to config.timeZone,
                    "rateLimitBaseMinutes" to config.rateLimitBaseMinutes,
                    "rateLimitMaxMinutes" to config.rateLimitMaxMinutes,
                )
            )
            .await()
    }

    override suspend fun blockPhone(homeId: String, phone: String, reason: String?) {
        val normalized = phone.filter(Char::isDigit)
        require(normalized.length >= 8)
        firestore.collection("homes")
            .document(homeId)
            .collection("blockedPhones")
            .document(normalized)
            .set(
                mapOf(
                    "phone" to normalized,
                    "reason" to reason,
                    "createdAt" to System.currentTimeMillis(),
                )
            )
            .await()
    }
}
