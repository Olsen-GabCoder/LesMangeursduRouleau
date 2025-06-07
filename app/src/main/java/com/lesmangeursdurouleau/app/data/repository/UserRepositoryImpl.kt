package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
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

class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService
) : UserRepository {

    companion object {
        private const val TAG = "UserRepositoryImpl"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    override suspend fun updateUserProfile(userId: String, username: String): Resource<Unit> {
        if (username.isBlank()) {
            return Resource.Error("Le pseudo ne peut pas être vide.")
        }
        try {
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) {
                Log.e(TAG, "updateUserProfile: Utilisateur non authentifié ou ID ne correspond pas. UserID: $userId, AuthUID: ${user?.uid}")
                return Resource.Error("Erreur d'authentification pour la mise à jour du profil.")
            }
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(username).build()
            user.updateProfile(profileUpdates).await()
            Log.d(TAG, "updateUserProfile: Firebase Auth displayName mis à jour pour $userId.")
            val userDocRef = usersCollection.document(userId)
            userDocRef.update("username", username).await()
            Log.d(TAG, "updateUserProfile: Champ 'username' dans Firestore mis à jour pour $userId.")
            return Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserProfile: Erreur lors de la mise à jour du profil pour $userId: ${e.message}", e)
            return Resource.Error("Erreur lors de la mise à jour du profil: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserBio(userId: String, bio: String): Resource<Unit> {
        Log.d(TAG, "updateUserBio: Tentative de mise à jour de la bio pour UserID: $userId")
        try {
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null || currentUser.uid != userId) {
                Log.e(TAG, "updateUserBio: Utilisateur non authentifié ou ID ne correspond pas. UserID: $userId, AuthUID: ${currentUser?.uid}")
                return Resource.Error("Erreur d'authentification pour la mise à jour de la biographie.")
            }
            val userDocRef = usersCollection.document(userId)
            userDocRef.update("bio", bio).await()
            Log.i(TAG, "updateUserBio: Champ 'bio' dans Firestore mis à jour pour $userId.")
            return Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserBio: Erreur lors de la mise à jour de la bio pour $userId: ${e.message}", e)
            return Resource.Error("Erreur lors de la mise à jour de la biographie: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserCity(userId: String, city: String): Resource<Unit> {
        Log.d(TAG, "updateUserCity: Tentative de mise à jour de la ville vers '$city' pour UserID: $userId")
        try {
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null || currentUser.uid != userId) {
                Log.e(TAG, "updateUserCity: Utilisateur non authentifié ou ID ne correspond pas. UserID: $userId, AuthUID: ${currentUser?.uid}")
                return Resource.Error("Erreur d'authentification pour la mise à jour de la ville.")
            }
            val userDocRef = usersCollection.document(userId)
            userDocRef.update("city", city).await()
            Log.i(TAG, "updateUserCity: Champ 'city' dans Firestore mis à jour pour $userId.")
            return Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserCity: Erreur lors de la mise à jour de la ville pour $userId: ${e.message}", e)
            return Resource.Error("Erreur lors de la mise à jour de la ville: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String> {
        Log.i(TAG, "updateUserProfilePicture: Début de la fonction pour UserID: $userId")
        try {
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) {
                Log.e(TAG, "updateUserProfilePicture: Utilisateur non authentifié ou ID ne correspond pas. UserID: $userId, AuthUID: ${user?.uid}")
                return Resource.Error("Erreur d'authentification.")
            }
            Log.d(TAG, "updateUserProfilePicture: Utilisateur authentifié. UID: ${user.uid}. Lancement de l'upload sur Storage.")

            val uploadResult = firebaseStorageService.uploadProfilePicture(userId, imageData)
            Log.d(TAG, "updateUserProfilePicture: Résultat de l'upload Storage: $uploadResult")

            if (uploadResult is Resource.Success) {
                val photoUrl = uploadResult.data
                if (photoUrl.isNullOrBlank()) {
                    Log.e(TAG, "updateUserProfilePicture: L'URL de Storage est null ou vide après un upload réussi. C'est inattendu.")
                    return Resource.Error("Erreur interne: L'URL de la photo est vide après l'upload.")
                }
                Log.i(TAG, "updateUserProfilePicture: Upload Storage réussi. Nouvelle URL: $photoUrl")

                Log.d(TAG, "updateUserProfilePicture: Tentative de mise à jour de 'profilePictureUrl' dans Firestore avec: $photoUrl")
                val userDocRef = usersCollection.document(userId)
                userDocRef.update("profilePictureUrl", photoUrl).await()
                Log.i(TAG, "updateUserProfilePicture: 'profilePictureUrl' dans Firestore MIS À JOUR pour $userId avec $photoUrl.")

                Log.d(TAG, "updateUserProfilePicture: Tentative de mise à jour de Firebase Auth photoUri avec: $photoUrl")
                val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(android.net.Uri.parse(photoUrl)).build()
                user.updateProfile(profileUpdates).await()
                val updatedAuthPhotoUrl = firebaseAuth.currentUser?.photoUrl?.toString()
                Log.i(TAG, "updateUserProfilePicture: Firebase Auth photoUri MIS À JOUR pour $userId. Nouvelle URL dans Auth: $updatedAuthPhotoUrl")

                if (updatedAuthPhotoUrl != photoUrl) {
                    Log.w(TAG, "updateUserProfilePicture: DISCORDANCE! URL de Storage ($photoUrl) vs URL dans Auth après MAJ ($updatedAuthPhotoUrl)")
                }
                Log.i(TAG, "updateUserProfilePicture: Retour de Resource.Success avec URL: $photoUrl")
                return Resource.Success(photoUrl)
            } else {
                Log.e(TAG, "updateUserProfilePicture: Échec de l'upload de la photo sur Storage pour $userId: ${uploadResult.message}")
                return Resource.Error(uploadResult.message ?: "Erreur lors de l'upload de la photo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateUserProfilePicture: Exception générale lors de la mise à jour de la photo de profil pour $userId: ${e.message}", e)
            return Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override fun getAllUsers(): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "getAllUsers: Tentative de récupération de tous les utilisateurs.")
        val listenerRegistration = usersCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getAllUsers: Erreur Firestore - ${error.message}", error)
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val usersList = mutableListOf<User>()
                    for (document in snapshot.documents) {
                        try {
                            val user = User(
                                uid = document.id,
                                username = document.getString("username") ?: "",
                                email = document.getString("email") ?: "",
                                profilePictureUrl = document.getString("profilePictureUrl"),
                                bio = document.getString("bio"),
                                city = document.getString("city"),
                                createdAt = document.getTimestamp("createdAt")?.toDate()?.time,
                                canEditReadings = document.getBoolean("canEditReadings") ?: false,
                                lastPermissionGrantedTimestamp = document.getLong("lastPermissionGrantedTimestamp")
                            )
                            usersList.add(user)
                        } catch (e: Exception) {
                            Log.e(TAG, "getAllUsers: Erreur de conversion du document ${document.id}", e)
                        }
                    }
                    Log.d(TAG, "getAllUsers: ${usersList.size} utilisateurs récupérés.")
                    trySend(Resource.Success(usersList))
                } else {
                    Log.d(TAG, "getAllUsers: Snapshot est null.")
                    trySend(Resource.Success(emptyList()))
                }
            }
        awaitClose { Log.d(TAG, "getAllUsers: Fermeture du listener."); listenerRegistration.remove() }
    }

    override fun getUserById(userId: String): Flow<Resource<User>> = callbackFlow {
        if (userId.isBlank()) {
            Log.w(TAG, "getUserById: Tentative de récupération avec un userId vide.")
            trySend(Resource.Error("L'ID utilisateur ne peut pas être vide."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        Log.i(TAG, "getUserById: Tentative de récupération de l'utilisateur ID: '$userId' depuis la collection '${FirebaseConstants.COLLECTION_USERS}'.")
        val docRef = usersCollection.document(userId)
        val listenerRegistration = docRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.e(TAG, "getUserById: Erreur Firestore pour ID '$userId': ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                Log.d(TAG, "getUserById: Document trouvé pour ID '$userId'. Tentative de conversion.")
                try {
                    val user = User(
                        uid = documentSnapshot.id,
                        username = documentSnapshot.getString("username") ?: "",
                        email = documentSnapshot.getString("email") ?: "",
                        profilePictureUrl = documentSnapshot.getString("profilePictureUrl"),
                        bio = documentSnapshot.getString("bio"),
                        city = documentSnapshot.getString("city"),
                        createdAt = documentSnapshot.getTimestamp("createdAt")?.toDate()?.time,
                        canEditReadings = documentSnapshot.getBoolean("canEditReadings") ?: false,
                        lastPermissionGrantedTimestamp = documentSnapshot.getLong("lastPermissionGrantedTimestamp")
                    )
                    Log.i(TAG, "getUserById: Utilisateur converti avec succès pour ID '$userId': $user")
                    trySend(Resource.Success(user))
                } catch (e: Exception) {
                    Log.e(TAG, "getUserById: Erreur de conversion du document pour ID '$userId': ${e.message}", e)
                    trySend(Resource.Error("Erreur de conversion des données utilisateur."))
                }
            } else {
                Log.w(TAG, "getUserById: Aucun document trouvé pour l'utilisateur ID '$userId'. DocumentSnapshot: $documentSnapshot")
                trySend(Resource.Error("Utilisateur non trouvé."))
            }
        }
        awaitClose { Log.d(TAG, "getUserById: Fermeture du listener pour ID '$userId'."); listenerRegistration.remove() }
    }

    override suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit> {
        if (userId.isBlank()) {
            Log.e(TAG, "updateUserTypingStatus: userId est vide.")
            return Resource.Error("ID utilisateur invalide pour la mise à jour du statut de frappe.")
        }
        Log.d(TAG, "updateUserTypingStatus: Pour userID '$userId', statut isTyping: $isTyping")

        return try {
            val typingUpdate = mapOf("isTypingInGeneralChat" to isTyping)
            usersCollection
                .document(userId)
                .set(typingUpdate, SetOptions.merge())
                .await()
            Log.d(TAG, "updateUserTypingStatus: Statut de frappe pour '$userId' mis à jour à '$isTyping' dans Firestore.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserTypingStatus: Erreur lors de la mise à jour du statut de frappe pour '$userId': ${e.message}", e)
            return Resource.Error("Erreur technique lors de la mise à jour du statut: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit> {
        return try {
            usersCollection.document(userId).update("canEditReadings", canEdit).await()
            Log.d(TAG, "updateUserEditPermission: Permission 'canEditReadings' de l'utilisateur $userId mise à jour à $canEdit.")
            return Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserEditPermission: Erreur lors de la mise à jour de la permission d'édition pour $userId: ${e.message}", e)
            return Resource.Error(e.localizedMessage ?: "Erreur lors de la mise à jour de la permission d'édition.")
        }
    }

    override suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit> {
        return try {
            val updateData = if (timestamp != null) {
                mapOf("lastPermissionGrantedTimestamp" to timestamp)
            } else {
                // Pour supprimer le champ, utilisez FieldValue.delete() si timestamp est null
                mapOf("lastPermissionGrantedTimestamp" to com.google.firebase.firestore.FieldValue.delete())
            }
            usersCollection.document(userId).set(updateData, SetOptions.merge()).await()
            Log.d(TAG, "updateUserLastPermissionTimestamp: Timestamp de permission de l'utilisateur $userId mis à jour à $timestamp.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserLastPermissionTimestamp: Erreur lors de la mise à jour du timestamp de permission pour $userId: ${e.message}", e)
            return Resource.Error(e.localizedMessage ?: "Erreur lors de la mise à jour du timestamp de permission.")
        }
    }

    // NOUVELLE MÉTHODE IMPLÉMENTÉE : Pour mettre à jour le jeton FCM de l'utilisateur
    override suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit> {
        if (userId.isBlank()) {
            Log.e(TAG, "updateUserFCMToken: userId est vide.")
            return Resource.Error("L'ID utilisateur ne peut pas être vide pour la mise à jour du jeton FCM.")
        }
        if (token.isBlank()) {
            Log.e(TAG, "updateUserFCMToken: Le jeton FCM est vide pour l'utilisateur $userId.")
            return Resource.Error("Le jeton FCM ne peut pas être vide.")
        }

        return try {
            val tokenUpdate = mapOf("fcmToken" to token)
            // Utilise SetOptions.merge() pour ajouter/mettre à jour uniquement le champ 'fcmToken' sans affecter les autres
            usersCollection
                .document(userId)
                .set(tokenUpdate, SetOptions.merge())
                .await()
            Log.d(TAG, "updateUserFCMToken: Jeton FCM mis à jour avec succès pour l'utilisateur $userId.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserFCMToken: Erreur lors de la mise à jour du jeton FCM pour l'utilisateur $userId: ${e.message}", e)
            return Resource.Error("Erreur lors de la mise à jour du jeton FCM: ${e.localizedMessage}")
        }
    }
}