package ar.timbr.app.data.repository

import ar.timbr.app.domain.model.HomeConfig
import ar.timbr.app.domain.model.LocationInput
import ar.timbr.app.domain.model.LocationType
import ar.timbr.app.domain.model.RingEvent
import ar.timbr.app.domain.model.UserProfile
import ar.timbr.app.domain.repository.DoorbellRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

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
                    val locationTypeRaw = it["locationType"] as? String ?: LocationType.CASA.name
                    HomeConfig(
                        homeId = homeId,
                        publicQrId = it["publicQrId"] as? String ?: hashSha256(homeId),
                        address = it["address"] as? String ?: "",
                        addressName = it["addressName"] as? String ?: "",
                        locationType = runCatching { LocationType.valueOf(locationTypeRaw) }
                            .getOrElse { LocationType.CASA },
                        placeId = it["placeId"] as? String ?: "",
                        latitude = (it["latitude"] as? Number)?.toDouble(),
                        longitude = (it["longitude"] as? Number)?.toDouble(),
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

    override suspend fun upsertHomeLocation(
        userId: String,
        currentHomeId: String?,
        locationInput: LocationInput,
    ): String {
        val resolvedHomeId = currentHomeId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val homeRef = firestore.collection("homes").document(resolvedHomeId)
        val userRef = firestore.collection("users").document(userId)
        val qrIdHash = hashSha256(resolvedHomeId)
        val timeZone = ZoneId.systemDefault().id

        firestore.runTransaction { transaction ->
            val existingHome = transaction.get(homeRef)
            if (!existingHome.exists()) {
                transaction.set(
                    homeRef,
                    mapOf(
                        "homeId" to resolvedHomeId,
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
            } else {
                transaction.update(
                    homeRef,
                    mapOf(
                        "publicQrId" to qrIdHash,
                        "address" to locationInput.address,
                        "addressName" to locationInput.addressName,
                        "locationType" to locationInput.locationType.name,
                        "placeId" to locationInput.placeId,
                        "latitude" to locationInput.latitude,
                        "longitude" to locationInput.longitude,
                    )
                )
            }
            val role = if (currentHomeId.isNullOrBlank()) "owner" else null
            val userUpdate = buildMap<String, Any> {
                put("homeId", resolvedHomeId)
                if (role != null) {
                    put("role", role)
                }
            }
            transaction.set(userRef, userUpdate, SetOptions.merge())
        }.await()
        return resolvedHomeId
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

    private fun hashSha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
