package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.remote.FirebaseConstants // Import mis à jour
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.DocumentSnapshot

class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService
) : UserRepository {

    companion object {
        private const val TAG = "UserRepositoryImpl"
        // SUPPRIMÉ : Constantes déplacées vers FirebaseConstants.kt
        // private const val SUBCOLLECTION_USER_READINGS = "user_readings"
        // private const val DOCUMENT_ACTIVE_READING = "activeReading"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    // Helper function pour créer un objet User à partir d'un DocumentSnapshot, gérant la conversion de createdAt
    private fun createUserFromSnapshot(document: DocumentSnapshot): User {
        return User(
            uid = document.id,
            username = document.getString("username") ?: "",
            email = document.getString("email") ?: "",
            profilePictureUrl = document.getString("profilePictureUrl"),
            bio = document.getString("bio"),
            city = document.getString("city"),
            // CORRECTION: Conversion manuelle de Timestamp en Long pour createdAt
            createdAt = document.getTimestamp("createdAt")?.toDate()?.time,
            canEditReadings = document.getBoolean("canEditReadings") ?: false,
            lastPermissionGrantedTimestamp = document.getLong("lastPermissionGrantedTimestamp"),
            followersCount = document.getLong("followersCount")?.toInt() ?: 0,
            followingCount = document.getLong("followingCount")?.toInt() ?: 0
        )
    }

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

                try {
                    val updateData = mapOf("profilePictureUrl" to photoUrl)
                    Log.d(TAG, "updateUserProfilePicture: Firestore - Appel de set(..., SetOptions.merge()) avec URL: $photoUrl")
                    userDocRef.set(updateData, SetOptions.merge()).await()
                    Log.i(TAG, "updateUserProfilePicture: Firestore - Écriture de 'profilePictureUrl' RÉUSSIE pour $userId avec $photoUrl.")
                } catch (e: FirebaseFirestoreException) {
                    Log.e(TAG, "updateUserProfilePicture: Firestore - ERREUR SPÉCIFIQUE FIRESTORE lors de la mise à jour de la photo pour $userId. Code: ${e.code}, Message: ${e.message}", e)
                    return Resource.Error("Erreur Firestore (${e.code.name}): ${e.localizedMessage}")
                } catch (e: Exception) {
                    Log.e(TAG, "updateUserProfilePicture: Firestore - ERREUR GÉNÉRIQUE lors de la mise à jour de la photo pour $userId: ${e.message}", e)
                    return Resource.Error("Erreur inattendue lors de la mise à jour Firestore: ${e.localizedMessage}")
                }

                Log.d(TAG, "updateUserProfilePicture: Tentative de mise à jour de Firebase Auth photoUri avec: $photoUrl")
                val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(android.net.Uri.parse(photoUrl)).build()
                user.updateProfile(profileUpdates).await()

                user.reload().await()
                Log.d(TAG, "updateUserProfilePicture: Firebase Auth user object rechargé.")

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
                            // Utilisation de la fonction helper pour la création de l'objet User
                            val user = createUserFromSnapshot(document)
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
                    // Utilisation de la fonction helper pour la création de l'objet User
                    val user = createUserFromSnapshot(documentSnapshot)
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
                mapOf("lastPermissionGrantedTimestamp" to FieldValue.delete())
            }
            usersCollection.document(userId).set(updateData, SetOptions.merge()).await()
            Log.d(TAG, "updateUserLastPermissionTimestamp: Timestamp de permission de l'utilisateur $userId mis à jour à $timestamp.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserLastPermissionTimestamp: Erreur lors de la mise à jour du timestamp de permission pour $userId: ${e.message}", e)
            return Resource.Error(e.localizedMessage ?: "Erreur lors de la mise à jour du timestamp de permission.")
        }
    }

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

    override suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        Log.d(TAG, "followUser: Utilisateur $currentUserId tente de suivre $targetUserId")
        return try {
            if (currentUserId == targetUserId) {
                Log.w(TAG, "followUser: Un utilisateur ne peut pas se suivre lui-même. ID: $currentUserId")
                return Resource.Error("Vous ne pouvez pas vous suivre vous-même.")
            }

            firestore.runTransaction { transaction ->
                // CORRECTION 1: Toutes les lectures AU DÉBUT de la transaction
                val targetUserDocRef = usersCollection.document(targetUserId)
                val targetUserDoc = transaction.get(targetUserDocRef) // Lecture 1

                val followingDocRef = usersCollection.document(currentUserId).collection("following").document(targetUserId)
                val followingDoc = transaction.get(followingDocRef) // Lecture 2

                val currentUserDocRef = usersCollection.document(currentUserId)
                val currentUserDoc = transaction.get(currentUserDocRef) // Lecture 3 (Déplacée ici)

                // Validation basée sur les lectures
                if (!targetUserDoc.exists()) {
                    Log.e(TAG, "followUser: Transaction: L'utilisateur cible $targetUserId n'existe pas dans Firestore.")
                    throw FirebaseFirestoreException("L'utilisateur à suivre n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                if (followingDoc.exists()) {
                    Log.w(TAG, "followUser: Transaction: Utilisateur $currentUserId suit déjà $targetUserId. Opération ignorée.")
                    throw FirebaseFirestoreException("Vous suivez déjà cet utilisateur.", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                }

                // Toutes les écritures APRÈS toutes les lectures
                Log.d(TAG, "followUser: Transaction: Ajout du document de suivi: $followingDocRef")
                transaction.set(followingDocRef, mapOf("timestamp" to System.currentTimeMillis()))

                val followersDocRef = usersCollection.document(targetUserId).collection("followers").document(currentUserId)
                Log.d(TAG, "followUser: Transaction: Ajout du document de follower: $followersDocRef")
                transaction.set(followersDocRef, mapOf("timestamp" to System.currentTimeMillis()))

                Log.d(TAG, "followUser: Transaction: Incrémentation de followingCount pour $currentUserId.")
                transaction.update(currentUserDocRef, "followingCount", FieldValue.increment(1))

                Log.d(TAG, "followUser: Transaction: Incrémentation de followersCount pour $targetUserId.")
                transaction.update(targetUserDocRef, "followersCount", FieldValue.increment(1))

                Log.i(TAG, "followUser: Transaction complétée pour $currentUserId suivant $targetUserId.")
                null
            }.await()

            Resource.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "followUser: Erreur Firestore lors du suivi de l'utilisateur $targetUserId par $currentUserId: Code=${e.code}, Message=${e.message}", e)
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> Resource.Error("L'utilisateur à suivre n'existe pas.")
                FirebaseFirestoreException.Code.ALREADY_EXISTS -> Resource.Error("Vous suivez déjà cet utilisateur.")
                else -> Resource.Error("Erreur Firestore lors du suivi: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "followUser: Erreur générale lors du suivi de l'utilisateur $targetUserId par $currentUserId: ${e.message}", e)
            Resource.Error("Erreur inattendue lors du suivi de l'utilisateur : ${e.localizedMessage}")
        }
    }

    override suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        Log.d(TAG, "unfollowUser: Utilisateur $currentUserId tente de ne plus suivre $targetUserId")
        return try {
            firestore.runTransaction { transaction ->
                // CORRECTION 1: Toutes les lectures AU DÉBUT de la transaction
                val targetUserDocRef = usersCollection.document(targetUserId)
                val targetUserDoc = transaction.get(targetUserDocRef) // Lecture 1

                val followingDocRef = usersCollection.document(currentUserId).collection("following").document(targetUserId)
                val followingDoc = transaction.get(followingDocRef) // Lecture 2

                val currentUserDocRef = usersCollection.document(currentUserId)
                val currentUserDoc = transaction.get(currentUserDocRef) // Lecture 3 (Déplacée ici)


                // Validation et logique basées sur les lectures
                if (!targetUserDoc.exists()) {
                    Log.e(TAG, "unfollowUser: Transaction: L'utilisateur cible $targetUserId n'existe pas dans Firestore.")
                    throw FirebaseFirestoreException("L'utilisateur à désabonner n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                if (!followingDoc.exists()) {
                    Log.w(TAG, "unfollowUser: Transaction: Utilisateur $currentUserId ne suit pas $targetUserId. Opération ignorée.")
                    throw FirebaseFirestoreException("Vous ne suivez pas cet utilisateur.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                // Toutes les écritures APRÈS toutes les lectures
                Log.d(TAG, "unfollowUser: Transaction: Suppression du document de suivi: $followingDocRef")
                transaction.delete(followingDocRef)

                val followersDocRef = usersCollection.document(targetUserId).collection("followers").document(currentUserId)
                Log.d(TAG, "unfollowUser: Transaction: Suppression du document de follower: $followersDocRef")
                transaction.delete(followersDocRef)

                val currentFollowingCount = currentUserDoc.getLong("followingCount")?.toInt() ?: 0
                if (currentFollowingCount > 0) {
                    Log.d(TAG, "unfollowUser: Transaction: Décrémentation de followingCount pour $currentUserId.")
                    transaction.update(currentUserDocRef, "followingCount", FieldValue.increment(-1))
                } else {
                    Log.w(TAG, "unfollowUser: Transaction: followingCount de $currentUserId est déjà à 0 ou négatif. Pas de décrémentation.")
                }

                val targetFollowersCount = targetUserDoc.getLong("followersCount")?.toInt() ?: 0
                if (targetFollowersCount > 0) {
                    Log.d(TAG, "unfollowUser: Transaction: Décrémentation de followersCount pour $targetUserId.")
                    transaction.update(targetUserDocRef, "followersCount", FieldValue.increment(-1))
                } else {
                    Log.w(TAG, "unfollowUser: Transaction: followersCount de $targetUserId est déjà à 0 ou négatif. Pas de décrémentation.")
                }

                Log.i(TAG, "unfollowUser: Transaction complétée pour $currentUserId arrêtant de suivre $targetUserId.")
                null
            }.await()

            Resource.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "unfollowUser: Erreur Firestore lors du désabonnement de l'utilisateur $targetUserId par $currentUserId: Code=${e.code}, Message=${e.message}", e)
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> Resource.Error("L'utilisateur à désabonner n'existe pas ou vous ne le suivez pas.")
                else -> Resource.Error("Erreur Firestore lors du désabonnement: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "unfollowUser: Erreur générale lors du désabonnement de l'utilisateur $targetUserId par $currentUserId: ${e.message}", e)
            Resource.Error("Erreur inattendue lors du désabonnement de l'utilisateur : ${e.localizedMessage}")
        }
    }

    override fun isFollowing(currentUserId: String, targetUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "isFollowing: Vérification du suivi de $targetUserId par $currentUserId")

        val followingDocRef = usersCollection.document(currentUserId)
            .collection("following")
            .document(targetUserId)

        val listenerRegistration = followingDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "isFollowing: Erreur lors de l'écoute du statut de suivi pour $currentUserId -> $targetUserId: ${error.message}", error)
                trySend(Resource.Error("Erreur lors de la vérification du suivi: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }

            val isFollowed = snapshot?.exists() == true
            Log.d(TAG, "isFollowing: Statut de suivi pour $currentUserId -> $targetUserId: $isFollowed")
            trySend(Resource.Success(isFollowed))
        }

        awaitClose {
            Log.d(TAG, "isFollowing: Fermeture du listener de statut de suivi pour $currentUserId -> $targetUserId")
            listenerRegistration.remove()
        }
    }

    override fun getFollowingUsers(userId: String): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "getFollowingUsers: Récupération des utilisateurs suivis par $userId")

        val followingCollectionRef = usersCollection.document(userId).collection("following")

        val listenerRegistration = followingCollectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "getFollowingUsers: Erreur lors de l'écoute de la sous-collection 'following' pour $userId: ${error.message}", error)
                trySend(Resource.Error("Erreur lors de la récupération des abonnements: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val followedUserIds = snapshot.documents.map { it.id }
                Log.d(TAG, "getFollowingUsers: IDs des utilisateurs suivis par $userId: $followedUserIds")

                if (followedUserIds.isNotEmpty()) {
                    val chunks = followedUserIds.chunked(10)
                    val allFollowedUsers = mutableListOf<User>()
                    var fetchErrors = false

                    chunks.forEachIndexed { index, chunk ->
                        usersCollection.whereIn(FieldPath.documentId(), chunk)
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val chunkUsers = usersSnapshot.documents.mapNotNull { userDoc ->
                                    try {
                                        // Utilisation de la fonction helper pour la création de l'objet User
                                        createUserFromSnapshot(userDoc).apply {
                                            this.followersCount = userDoc.getLong("followersCount")?.toInt() ?: 0
                                            this.followingCount = userDoc.getLong("followingCount")?.toInt() ?: 0
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "getFollowingUsers: Erreur de conversion du document utilisateur ${userDoc.id}: ${e.message}", e)
                                        null
                                    }
                                }
                                allFollowedUsers.addAll(chunkUsers)

                                if (index == chunks.lastIndex && !fetchErrors) {
                                    Log.i(TAG, "getFollowingUsers: ${allFollowedUsers.size} utilisateurs suivis récupérés pour $userId (après traitement des chunks).")
                                    trySend(Resource.Success(allFollowedUsers.distinctBy { it.uid }))
                                }
                            }
                            .addOnFailureListener { e ->
                                fetchErrors = true
                                Log.e(TAG, "getFollowingUsers: Erreur lors de la récupération des détails des utilisateurs suivis (chunk) pour $userId: ${e.message}", e)
                                trySend(Resource.Error("Erreur lors de la récupération des détails des utilisateurs suivis: ${e.localizedMessage}"))
                                close(e)
                            }
                    }
                } else {
                    Log.d(TAG, "getFollowingUsers: Aucune personne suivie par $userId.")
                    trySend(Resource.Success(emptyList()))
                }
            } else {
                Log.d(TAG, "getFollowingUsers: Aucun snapshot ou snapshot vide pour 'following' de $userId.")
                trySend(Resource.Success(emptyList()))
            }
        }

        awaitClose {
            Log.d(TAG, "getFollowingUsers: Fermeture du listener de la liste des suivis pour $userId")
            listenerRegistration.remove()
        }
    }

    override fun getFollowersUsers(userId: String): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "getFollowersUsers: Récupération des followers pour $userId")

        val followersCollectionRef = usersCollection.document(userId).collection("followers")

        val listenerRegistration = followersCollectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "getFollowersUsers: Erreur lors de l'écoute de la sous-collection 'followers' pour $userId: ${error.message}", error)
                trySend(Resource.Error("Erreur lors de la récupération des followers: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val followerUserIds = snapshot.documents.map { it.id }
                Log.d(TAG, "getFollowersUsers: IDs des followers pour $userId: $followerUserIds")

                if (followerUserIds.isNotEmpty()) {
                    val chunks = followerUserIds.chunked(10)
                    val allFollowerUsers = mutableListOf<User>()
                    var fetchErrors = false

                    chunks.forEachIndexed { index, chunk ->
                        usersCollection.whereIn(FieldPath.documentId(), chunk)
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val chunkUsers = usersSnapshot.documents.mapNotNull { userDoc ->
                                    try {
                                        // Utilisation de la fonction helper pour la création de l'objet User
                                        createUserFromSnapshot(userDoc).apply {
                                            this.followersCount = userDoc.getLong("followersCount")?.toInt() ?: 0
                                            this.followingCount = userDoc.getLong("followingCount")?.toInt() ?: 0
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "getFollowersUsers: Erreur de conversion du document utilisateur ${userDoc.id}: ${e.message}", e)
                                        null
                                    }
                                }
                                allFollowerUsers.addAll(chunkUsers)

                                if (index == chunks.lastIndex && !fetchErrors) {
                                    Log.i(TAG, "getFollowersUsers: ${allFollowerUsers.size} followers récupérés pour $userId (après traitement des chunks).")
                                    trySend(Resource.Success(allFollowerUsers.distinctBy { it.uid }))
                                }
                            }
                            .addOnFailureListener { e ->
                                fetchErrors = true
                                Log.e(TAG, "getFollowersUsers: Erreur lors de la récupération des détails des followers (chunk) pour $userId: ${e.message}", e)
                                trySend(Resource.Error("Erreur lors de la récupération des détails des followers: ${e.localizedMessage}"))
                                close(e)
                            }
                    }
                } else {
                    Log.d(TAG, "getFollowersUsers: Aucun follower pour $userId.")
                    trySend(Resource.Success(emptyList()))
                }
            } else {
                Log.d(TAG, "getFollowersUsers: Aucun snapshot ou snapshot vide pour 'followers' de $userId.")
                trySend(Resource.Success(emptyList()))
            }
        }

        awaitClose {
            Log.d(TAG, "getFollowersUsers: Fermeture du listener de la liste des followers pour $userId")
            listenerRegistration.remove()
        }
    }

    // IMPLÉMENTATION MISE À JOUR : Récupération de la lecture en cours (utilisation de FirebaseConstants)
    override fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>> = callbackFlow {
        if (userId.isBlank()) {
            Log.w(TAG, "getCurrentReading: Tentative de récupération avec un userId vide.")
            trySend(Resource.Error("L'ID utilisateur ne peut pas être vide."))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading())
        Log.i(TAG, "getCurrentReading: Tentative de récupération de la lecture en cours pour l'utilisateur ID: '$userId'.")

        val currentReadingDocRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS) // Utilisation de la constante
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING) // Utilisation de la constante

        val listenerRegistration = currentReadingDocRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.e(TAG, "getCurrentReading: Erreur Firestore pour lecture en cours de ID '$userId': ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }

            if (documentSnapshot != null && documentSnapshot.exists()) {
                Log.d(TAG, "getCurrentReading: Document de lecture en cours trouvé pour ID '$userId'. Tentative de conversion.")
                try {
                    val userBookReading = documentSnapshot.toObject(UserBookReading::class.java)
                    Log.i(TAG, "getCurrentReading: Lecture en cours convertie avec succès pour ID '$userId': $userBookReading")
                    trySend(Resource.Success(userBookReading))
                } catch (e: Exception) {
                    Log.e(TAG, "getCurrentReading: Erreur de conversion du document de lecture en cours pour ID '$userId': ${e.message}", e)
                    trySend(Resource.Error("Erreur de conversion des données de lecture en cours."))
                }
            } else {
                Log.d(TAG, "getCurrentReading: Aucun document de lecture en cours trouvé pour l'utilisateur ID '$userId'.")
                trySend(Resource.Success(null)) // Aucun livre en cours de lecture
            }
        }

        awaitClose {
            Log.d(TAG, "getCurrentReading: Fermeture du listener de lecture en cours pour ID '$userId'.")
            listenerRegistration.remove()
        }
    }

    // IMPLÉMENTATION MISE À JOUR : Mise à jour de la lecture en cours (utilisation de FirebaseConstants)
    override suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit> {
        if (userId.isBlank()) {
            Log.w(TAG, "updateCurrentReading: Tentative de mise à jour avec un userId vide.")
            return Resource.Error("L'ID utilisateur ne peut pas être vide.")
        }

        val currentReadingDocRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS) // Utilisation de la constante
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING) // Utilisation de la constante

        return try {
            if (userBookReading != null) {
                Log.d(TAG, "updateCurrentReading: Mise à jour/création de la lecture en cours pour l'utilisateur '$userId'. Données: $userBookReading")
                currentReadingDocRef.set(userBookReading, SetOptions.merge()).await()
                Log.i(TAG, "updateCurrentReading: Lecture en cours mise à jour/créée avec succès pour l'utilisateur '$userId'.")
            } else {
                Log.d(TAG, "updateCurrentReading: Suppression de la lecture en cours pour l'utilisateur '$userId'.")
                currentReadingDocRef.delete().await()
                Log.i(TAG, "updateCurrentReading: Lecture en cours supprimée avec succès pour l'utilisateur '$userId'.")
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateCurrentReading: Erreur lors de la mise à jour de la lecture en cours pour l'utilisateur '$userId': ${e.message}", e)
            Resource.Error("Erreur lors de la mise à jour de la lecture en cours: ${e.localizedMessage}")
        }
    }
}