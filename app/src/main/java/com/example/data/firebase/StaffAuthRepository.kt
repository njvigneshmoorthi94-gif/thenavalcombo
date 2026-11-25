package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.Locale

class StaffAuthRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Please sign in with Google to connect to Firestore.")
    }

    suspend fun ensureUserProfile(firebaseUser: FirebaseUser): Result<UserProfile> {
        val docPath = "users/${firebaseUser.uid}"
        return try {
            val uid = requireUserId()
            val email = firebaseUser.email.orEmpty().trim()
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()

            if (snap.exists()) {
                val profile = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Failed to read user profile.")
                val isBootstrappedOwner = firebaseUser.isEmailVerified &&
                    ShopRepository.BOOTSTRAPPED_OWNER_EMAILS.contains(email.lowercase(Locale.ROOT))
                if (isBootstrappedOwner && (profile.userRole != UserRole.OWNER || !profile.active)) {
                    docRef.update(
                        mapOf(
                            "role" to UserRole.OWNER.wireValue,
                            "active" to true,
                            "email" to email.take(120),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    val refreshed = docRef.get().await()
                        .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: profile.copy(role = UserRole.OWNER.wireValue, active = true, email = email)
                    return Result.success(refreshed)
                }
                return Result.success(profile)
            }

            val isBootstrappedOwner = firebaseUser.isEmailVerified &&
                ShopRepository.BOOTSTRAPPED_OWNER_EMAILS.contains(email.lowercase(Locale.ROOT))
            val assignedRole = if (isBootstrappedOwner) UserRole.OWNER else UserRole.LABOUR
            val active = isBootstrappedOwner
            val displayName = firebaseUser.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "Staff Member" }
            val safeUsername = if (isBootstrappedOwner) {
                "admin"
            } else {
                sanitizeUsername(email.substringBefore("@").ifBlank { "labour" })
            }

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "username" to safeUsername,
                "role" to assignedRole.wireValue,
                "name" to displayName.take(80),
                "active" to active,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (email.isNotBlank()) {
                payload["email"] = email.take(120)
            }

            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    username = safeUsername,
                    role = assignedRole.wireValue,
                    name = displayName,
                    email = email,
                    active = active
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun validateStaffCredentials(
        firebaseUser: FirebaseUser,
        rawUsername: String,
        rawPassword: String
    ): Result<UserProfile> {
        var docPath = "users/${firebaseUser.uid}"
        return try {
            val uid = requireUserId()
            val cleanUsername = sanitizeUsername(rawUsername)
            require(rawUsername.trim().length in 2..50) { "Username must be 2–50 characters." }
            require(rawPassword.length >= 4) { "Password must be at least 4 characters." }

            val myProfile = ensureUserProfile(firebaseUser).getOrThrow()

            if (myProfile.userRole == UserRole.OWNER && myProfile.active) {
                docPath = "users"
                val querySnap = db.collection("users")
                    .whereEqualTo("username", cleanUsername)
                    .limit(1)
                    .get()
                    .await()

                val matched = querySnap.documents.firstOrNull()
                    ?.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

                if (matched != null) {
                    val targetId = matched.userId.ifBlank { matched.id }
                    docPath = "users/$targetId"
                    if (matched.passcodeHash.isNotBlank()) {
                        val valid = StaffCredentialHasher.verifyPassword(
                            password = rawPassword,
                            saltHex = matched.passcodeSalt,
                            expectedHashHex = matched.passcodeHash
                        )
                        if (!valid) {
                            return Result.failure(IllegalArgumentException("Invalid username or password."))
                        }
                        return Result.success(matched)
                    } else if (targetId == uid) {
                        val salt = StaffCredentialHasher.generateSalt()
                        val hash = StaffCredentialHasher.hashPassword(rawPassword, salt)
                        db.collection("users").document(uid).update(
                            mapOf(
                                "passcodeSalt" to salt,
                                "passcodeHash" to hash,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        return Result.success(matched.copy(passcodeSalt = salt, passcodeHash = hash))
                    } else {
                        return Result.failure(
                            IllegalStateException("Staff @$cleanUsername does not have a password set yet. Set it in the Users tab.")
                        )
                    }
                }

                // Owner's own account hasn't claimed a username & password yet
                if (myProfile.passcodeHash.isBlank()) {
                    docPath = "users/$uid"
                    val salt = StaffCredentialHasher.generateSalt()
                    val hash = StaffCredentialHasher.hashPassword(rawPassword, salt)
                    db.collection("users").document(uid).update(
                        mapOf(
                            "username" to cleanUsername,
                            "passcodeSalt" to salt,
                            "passcodeHash" to hash,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return Result.success(
                        myProfile.copy(username = cleanUsername, passcodeSalt = salt, passcodeHash = hash)
                    )
                }

                return Result.failure(IllegalArgumentException("No staff account found for @$cleanUsername."))
            } else {
                // Non-owner device: verify against the signed-in user's own Firestore document
                docPath = "users/$uid"
                if (myProfile.passcodeHash.isBlank()) {
                    val salt = StaffCredentialHasher.generateSalt()
                    val hash = StaffCredentialHasher.hashPassword(rawPassword, salt)
                    db.collection("users").document(uid).update(
                        mapOf(
                            "username" to cleanUsername,
                            "passcodeSalt" to salt,
                            "passcodeHash" to hash,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return Result.success(
                        myProfile.copy(username = cleanUsername, passcodeSalt = salt, passcodeHash = hash)
                    )
                }

                if (!myProfile.username.equals(cleanUsername, ignoreCase = true) ||
                    !StaffCredentialHasher.verifyPassword(rawPassword, myProfile.passcodeSalt, myProfile.passcodeHash)
                ) {
                    return Result.failure(IllegalArgumentException("Invalid username or password."))
                }

                return Result.success(myProfile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserCredentials(
        targetUserId: String,
        username: String,
        newPassword: String?
    ): Result<Unit> {
        val docPath = "users/$targetUserId"
        return try {
            requireUserId()
            require(targetUserId.isNotBlank()) { "User ID missing." }
            require(username.trim().length in 2..50) { "Username must be 2–50 characters." }
            val safeUsername = sanitizeUsername(username)

            val updates = mutableMapOf<String, Any>(
                "username" to safeUsername,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newPassword.isNullOrBlank()) {
                require(newPassword.length >= 4) { "Password must be at least 4 characters." }
                val salt = StaffCredentialHasher.generateSalt()
                val hash = StaffCredentialHasher.hashPassword(newPassword, salt)
                updates["passcodeSalt"] = salt
                updates["passcodeHash"] = hash
            }

            db.collection("users").document(targetUserId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        fun sanitizeUsername(raw: String): String {
            val cleaned = raw.trim().lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9._-]"), "")
                .ifBlank { "staff" }
                .take(50)
            return if (cleaned.length >= 2) cleaned else "u_$cleaned"
        }
    }
}
