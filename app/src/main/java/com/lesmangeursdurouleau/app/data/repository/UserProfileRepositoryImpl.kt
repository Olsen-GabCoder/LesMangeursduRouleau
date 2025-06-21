package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserProfileRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService
) : UserProfileRepository {

    companion object {
        private const val TAG = "UserProfileRepository"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    private fun createUserFromSnapshot(document: DocumentSnapshot): User {
        return User(
            uid = document.id,
            username = document.getString("username") ?: "",
            email = document.getString("email") ?: "",
            profilePictureUrl = document.getString("profilePictureUrl"),
            bio = document.getString("bio"),
            city = document.getString("city"),
            createdAt = document.getTimestamp("createdAt")?.toDate()?.time,
            canEditReadings = document.getBoolean("canEditReadings") ?: false,
            lastPermissionGrantedTimestamp = document.getLong("lastPermissionGrantedTimestamp"),
            followersCount = document.getLong("followersCount")?.toInt() ?: 0,
            followingCount = document.getLong("followingCount")?.toInt() ?: 0,
            booksReadCount = document.getLong("booksReadCount")?.toInt() ?: 0,
            // MODIFICATION : Ajout de la lecture des nouveaux champs de présence.
            isOnline = document.getBoolean("isOnline") ?: false,
            lastSeen = document.getTimestamp("lastSeen")?.toDate()
        )
    }

    override suspend fun updateUserProfile(userId: String, username: String): Resource<Unit> {
        if (username.isBlank()) return Resource.Error("Le pseudo ne peut pas être vide.")
        return try {
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) return Resource.Error("Erreur d'authentification.")
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(username).build()
            user.updateProfile(profileUpdates).await()
            usersCollection.document(userId).update("username", username).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur lors de la mise à jour du profil: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String> {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) return Resource.Error("Erreur d'authentification.")

            val uploadResult = firebaseStorageService.uploadProfilePicture(userId, imageData)
            if (uploadResult is Resource.Success) {
                val photoUrl = uploadResult.data
                if (photoUrl.isNullOrBlank()) return Resource.Error("L'URL de la photo est vide.")

                usersCollection.document(userId).set(mapOf("profilePictureUrl" to photoUrl), SetOptions.merge()).await()
                val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(photoUrl.toUri()).build()
                user.updateProfile(profileUpdates).await()

                Resource.Success(photoUrl)
            } else {
                Resource.Error(uploadResult.message ?: "Erreur lors de l'upload de la photo.")
            }
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override fun getAllUsers(): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            if (snapshot != null) {
                val users = snapshot.documents.mapNotNull { doc ->
                    try { createUserFromSnapshot(doc) } catch (e: Exception) { null }
                }
                trySend(Resource.Success(users))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getUserById(userId: String): Flow<Resource<User>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("ID utilisateur manquant.")); close(); return@callbackFlow
        }
        trySend(Resource.Loading())
        val listener = usersCollection.document(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                try {
                    val user = createUserFromSnapshot(snapshot)
                    trySend(Resource.Success(user))
                } catch (e: Exception) {
                    trySend(Resource.Error("Erreur de conversion des données."))
                }
            } else {
                trySend(Resource.Error("Utilisateur non trouvé."))
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit> {
        if (userId.isBlank()) return Resource.Error("ID utilisateur invalide.")
        return try {
            val typingUpdate = mapOf("isTypingInGeneralChat" to isTyping)
            usersCollection.document(userId).set(typingUpdate, SetOptions.merge()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    // NOUVELLE FONCTION IMPLÉMENTÉE
    override suspend fun updateUserPresence(userId: String, isOnline: Boolean) {
        if (userId.isBlank()) return

        try {
            val presenceUpdate = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to FieldValue.serverTimestamp() // Met à jour l'heure à chaque changement
            )
            usersCollection.document(userId).update(presenceUpdate).await()
            Log.d(TAG, "Statut de présence mis à jour pour $userId : isOnline = $isOnline")
        } catch (e: FirebaseFirestoreException) {
            // Loguer spécifiquement les erreurs Firestore (ex: PERMISSION_DENIED)
            Log.e(TAG, "Erreur Firestore lors de la mise à jour de la présence pour $userId: ${e.code}", e)
        } catch (e: Exception) {
            // Loguer les autres erreurs (ex: pas de réseau)
            Log.e(TAG, "Erreur générale lors de la mise à jour de la présence pour $userId", e)
        }
    }


    override suspend fun updateUserBio(userId: String, bio: String): Resource<Unit> {
        return try {
            if (firebaseAuth.currentUser?.uid != userId) return Resource.Error("Erreur d'authentification.")
            usersCollection.document(userId).update("bio", bio).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserCity(userId: String, city: String): Resource<Unit> {
        return try {
            if (firebaseAuth.currentUser?.uid != userId) return Resource.Error("Erreur d'authentification.")
            usersCollection.document(userId).update("city", city).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit> {
        return try {
            usersCollection.document(userId).update("canEditReadings", canEdit).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit> {
        return try {
            val updateData = if (timestamp != null) {
                mapOf("lastPermissionGrantedTimestamp" to timestamp)
            } else {
                mapOf("lastPermissionGrantedTimestamp" to FieldValue.delete())
            }
            usersCollection.document(userId).set(updateData, SetOptions.merge()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit> {
        if (userId.isBlank() || token.isBlank()) return Resource.Error("Informations manquantes.")
        return try {
            val tokenUpdate = mapOf("fcmToken" to token)
            usersCollection.document(userId).set(tokenUpdate, SetOptions.merge()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }
}