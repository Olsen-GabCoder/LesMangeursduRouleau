package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.Like
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
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
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import java.util.Date

class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService
) : UserRepository {

    companion object {
        private const val TAG = "UserRepositoryImpl"
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
            createdAt = document.getTimestamp("createdAt")?.toDate()?.time,
            canEditReadings = document.getBoolean("canEditReadings") ?: false,
            lastPermissionGrantedTimestamp = document.getLong("lastPermissionGrantedTimestamp"),
            followersCount = document.getLong("followersCount")?.toInt() ?: 0,
            followingCount = document.getLong("followingCount")?.toInt() ?: 0,
            booksReadCount = document.getLong("booksReadCount")?.toInt() ?: 0
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
                val targetUserDocRef = usersCollection.document(targetUserId)
                val followingDocRef = usersCollection.document(currentUserId).collection("following").document(targetUserId)
                val followingDoc = transaction.get(followingDocRef) // Lit pour vérifier l'existence

                val currentUserDocRef = usersCollection.document(currentUserId)

                if (followingDoc.exists()) {
                    Log.w(TAG, "followUser: Transaction: Utilisateur $currentUserId suit déjà $targetUserId. Opération ignorée.")
                    throw FirebaseFirestoreException("Vous suivez déjà cet utilisateur.", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                }

                // Vérification de l'existence de l'utilisateur cible pour une meilleure gestion des erreurs client.
                val targetUserDoc = transaction.get(targetUserDocRef)
                if (!targetUserDoc.exists()) {
                    Log.e(TAG, "followUser: Transaction: L'utilisateur cible $targetUserId n'existe pas dans Firestore.")
                    throw FirebaseFirestoreException("L'utilisateur à suivre n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                Log.d(TAG, "followUser: Transaction: Ajout du document de suivi: $followingDocRef")
                transaction.set(followingDocRef, mapOf("timestamp" to Timestamp.now()))

                val followersDocRef = usersCollection.document(targetUserId).collection("followers").document(currentUserId)
                Log.d(TAG, "followUser: Transaction: Ajout du document de follower: $followersDocRef")
                transaction.set(followersDocRef, mapOf("timestamp" to Timestamp.now()))

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
                val targetUserDocRef = usersCollection.document(targetUserId)
                val followingDocRef = usersCollection.document(currentUserId).collection("following").document(targetUserId)
                val followingDoc = transaction.get(followingDocRef) // Lit pour vérifier l'existence

                val currentUserDocRef = usersCollection.document(currentUserId)

                if (!followingDoc.exists()) {
                    Log.w(TAG, "unfollowUser: Transaction: Utilisateur $currentUserId ne suit pas $targetUserId. Opération ignorée.")
                    throw FirebaseFirestoreException("Vous ne suivez pas cet utilisateur.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                // Vérification de l'existence de l'utilisateur cible pour une meilleure gestion des erreurs client.
                val targetUserDoc = transaction.get(targetUserDocRef)
                if (!targetUserDoc.exists()) {
                    Log.e(TAG, "unfollowUser: Transaction: L'utilisateur cible $targetUserId n'existe pas dans Firestore.")
                    throw FirebaseFirestoreException("L'utilisateur à désabonner n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                Log.d(TAG, "unfollowUser: Transaction: Suppression du document de suivi: $followingDocRef")
                transaction.delete(followingDocRef)

                val followersDocRef = usersCollection.document(targetUserId).collection("followers").document(currentUserId)
                Log.d(TAG, "unfollowUser: Transaction: Suppression du document de follower: $followersDocRef")
                transaction.delete(followersDocRef)

                // Les règles Firestore gèrent déjà la non-négativité des compteurs.
                Log.d(TAG, "unfollowUser: Transaction: Décrémentation de followingCount pour $currentUserId.")
                transaction.update(currentUserDocRef, "followingCount", FieldValue.increment(-1))

                Log.d(TAG, "unfollowUser: Transaction: Décrémentation de followersCount pour $targetUserId.")
                transaction.update(targetUserDocRef, "followersCount", FieldValue.increment(-1))

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
                    // Utilisation d'un AtomicInteger pour suivre les tâches asynchrones en toute sécurité
                    val completedTasks = java.util.concurrent.atomic.AtomicInteger(0)

                    chunks.forEach { chunk ->
                        usersCollection.whereIn(FieldPath.documentId(), chunk)
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val chunkUsers = usersSnapshot.documents.mapNotNull { userDoc ->
                                    try {
                                        createUserFromSnapshot(userDoc)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "getFollowingUsers: Erreur de conversion du document utilisateur ${userDoc.id}: ${e.message}", e)
                                        null
                                    }
                                }
                                synchronized(allFollowedUsers) { // Synchronisation pour l'ajout concurrent
                                    allFollowedUsers.addAll(chunkUsers)
                                }

                                if (completedTasks.incrementAndGet() == chunks.size) {
                                    // Quand toutes les chunks sont traitées
                                    Log.i(TAG, "getFollowingUsers: ${allFollowedUsers.size} utilisateurs suivis récupérés pour $userId (après traitement des chunks).")
                                    trySend(Resource.Success(allFollowedUsers.distinctBy { it.uid }))
                                }
                            }
                            .addOnFailureListener { e ->
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
                    // Utilisation d'un AtomicInteger pour suivre les tâches asynchrones en toute sécurité
                    val completedTasks = java.util.concurrent.atomic.AtomicInteger(0)

                    chunks.forEach { chunk ->
                        usersCollection.whereIn(FieldPath.documentId(), chunk)
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val chunkUsers = usersSnapshot.documents.mapNotNull { userDoc ->
                                    try {
                                        createUserFromSnapshot(userDoc)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "getFollowersUsers: Erreur de conversion du document utilisateur ${userDoc.id}: ${e.message}", e)
                                        null
                                    }
                                }
                                synchronized(allFollowerUsers) { // Synchronisation pour l'ajout concurrent
                                    allFollowerUsers.addAll(chunkUsers)
                                }

                                if (completedTasks.incrementAndGet() == chunks.size) {
                                    // Quand toutes les chunks sont traitées
                                    Log.i(TAG, "getFollowersUsers: ${allFollowerUsers.size} followers récupérés pour $userId (après traitement des chunks).")
                                    trySend(Resource.Success(allFollowerUsers.distinctBy { it.uid }))
                                }
                            }
                            .addOnFailureListener { e ->
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
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)

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
                trySend(Resource.Success(null))
            }
        }

        awaitClose {
            Log.d(TAG, "getCurrentReading: Fermeture du listener de lecture en cours pour ID '$userId'.")
            listenerRegistration.remove()
        }
    }

    override suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit> {
        if (userId.isBlank()) {
            Log.w(TAG, "updateCurrentReading: Tentative de mise à jour avec un userId vide.")
            return Resource.Error("L'ID utilisateur ne peut pas être vide.")
        }

        val currentReadingDocRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)

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

    override suspend fun addCommentOnActiveReading(targetUserId: String, comment: Comment): Resource<Unit> {
        if (targetUserId.isBlank()) {
            Log.w(TAG, "addCommentOnActiveReading: targetUserId est vide.")
            return Resource.Error("L'ID de l'utilisateur cible ne peut pas être vide.")
        }
        if (comment.commentText.isBlank()) {
            Log.w(TAG, "addCommentOnActiveReading: Le commentaire est vide.")
            return Resource.Error("Le commentaire ne peut pas être vide.")
        }
        if (comment.userId.isBlank()) {
            Log.w(TAG, "addCommentOnActiveReading: L'ID de l'auteur du commentaire est vide.")
            return Resource.Error("L'ID de l'auteur du commentaire est manquant.")
        }

        val commentsCollectionRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)

        return try {
            commentsCollectionRef.add(comment).await()
            Log.i(TAG, "addCommentOnActiveReading: Commentaire ajouté avec succès sur la lecture de '$targetUserId' par '${comment.userName}'.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addCommentOnActiveReading: Erreur lors de l'ajout du commentaire sur la lecture de '$targetUserId': ${e.message}", e)
            Resource.Error("Erreur lors de l'ajout du commentaire: ${e.localizedMessage}")
        }
    }

    override fun getCommentsOnActiveReading(targetUserId: String): Flow<Resource<List<Comment>>> = callbackFlow {
        if (targetUserId.isBlank()) {
            Log.w(TAG, "getCommentsOnActiveReading: targetUserId est vide.")
            trySend(Resource.Error("L'ID de l'utilisateur cible ne peut pas être vide."))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading())
        Log.i(TAG, "getCommentsOnActiveReading: Tentative de récupération des commentaires pour la lecture de l'utilisateur ID: '$targetUserId'.")

        val commentsCollectionRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)

        val listenerRegistration = commentsCollectionRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getCommentsOnActiveReading: Erreur Firestore lors de l'écoute des commentaires pour ID '$targetUserId': ${error.message}", error)
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val commentsList = mutableListOf<Comment>()
                    for (document in snapshot.documents) {
                        try {
                            val comment = document.toObject(Comment::class.java)?.copy(commentId = document.id)
                            comment?.let { commentsList.add(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "getCommentsOnActiveReading: Erreur de conversion du document de commentaire ${document.id} pour ID '$targetUserId': ${e.message}", e)
                        }
                    }
                    Log.d(TAG, "getCommentsOnActiveReading: ${commentsList.size} commentaires récupérés pour ID '$targetUserId'.")
                    trySend(Resource.Success(commentsList))
                } else {
                    Log.d(TAG, "getCommentsOnActiveReading: Snapshot de commentaires est null pour ID '$targetUserId'.")
                    trySend(Resource.Success(emptyList()))
                }
            }

        awaitClose {
            Log.d(TAG, "getCommentsOnActiveReading: Fermeture du listener de commentaires pour ID '$targetUserId'.")
            listenerRegistration.remove()
        }
    }

    override suspend fun deleteCommentOnActiveReading(targetUserId: String, commentId: String): Resource<Unit> {
        if (targetUserId.isBlank()) {
            Log.w(TAG, "deleteCommentOnActiveReading: targetUserId est vide.")
            return Resource.Error("L'ID de l'utilisateur cible ne peut pas être vide.")
        }
        if (commentId.isBlank()) {
            Log.w(TAG, "deleteCommentOnActiveReading: commentId est vide.")
            return Resource.Error("L'ID du commentaire ne peut pas être vide.")
        }

        val commentDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
            .document(commentId)

        return try {
            Log.d(TAG, "deleteCommentOnActiveReading: Tentative de suppression du commentaire '$commentId' de la lecture de '$targetUserId'.")
            commentDocRef.delete().await()
            Log.i(TAG, "deleteCommentOnActiveReading: Commentaire '$commentId' supprimé avec succès.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteCommentOnActiveReading: Erreur lors de la suppression du commentaire '$commentId' pour '$targetUserId': ${e.message}", e)
            Resource.Error("Erreur lors de la suppression du commentaire: ${e.localizedMessage}")
        }
    }

    override suspend fun toggleLikeOnActiveReading(targetUserId: String, currentUserId: String): Resource<Unit> {
        if (targetUserId.isBlank() || currentUserId.isBlank()) {
            Log.w(TAG, "toggleLikeOnActiveReading: targetUserId ou currentUserId est vide.")
            return Resource.Error("L'ID de l'utilisateur cible ou courant ne peut pas être vide.")
        }

        val likeDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES)
            .document(currentUserId) // L'ID du document like est l'UID de l'utilisateur qui like

        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(likeDocRef)
                if (snapshot.exists()) {
                    // Le like existe, on le supprime
                    transaction.delete(likeDocRef)
                    Log.i(TAG, "toggleLikeOnActiveReading: Like de '$currentUserId' sur '$targetUserId' supprimé.")
                } else {
                    // Le like n'existe pas, on l'ajoute
                    val newLike = Like(
                        likeId = currentUserId,
                        userId = currentUserId,
                        targetUserId = targetUserId,
                        readingId = FirebaseConstants.DOCUMENT_ACTIVE_READING,
                        timestamp = Timestamp.now(),
                        commentId = null // Ce like concerne la lecture, pas un commentaire
                    )
                    transaction.set(likeDocRef, newLike)
                    Log.i(TAG, "toggleLikeOnActiveReading: Like de '$currentUserId' sur '$targetUserId' ajouté.")
                }
                null // Transaction réussie
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleLikeOnActiveReading: Erreur lors de la bascule du like pour '$targetUserId' par '$currentUserId': ${e.message}", e)
            Resource.Error("Erreur lors de la bascule du like: ${e.localizedMessage}")
        }
    }

    override fun isLikedByCurrentUser(targetUserId: String, currentUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        if (targetUserId.isBlank() || currentUserId.isBlank()) {
            Log.w(TAG, "isLikedByCurrentUser: targetUserId ou currentUserId est vide.")
            trySend(Resource.Error("L'ID utilisateur cible ou courant ne peut pas être vide."))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading())
        Log.i(TAG, "isLikedByCurrentUser: Vérification si '$currentUserId' a liké la lecture de '$targetUserId'.")

        val likeDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES)
            .document(currentUserId)

        val listenerRegistration = likeDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "isLikedByCurrentUser: Erreur Firestore lors de l'écoute du like pour '$currentUserId' sur '$targetUserId': ${error.message}", error)
                trySend(Resource.Error("Erreur lors de la vérification du like: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }

            val isLiked = snapshot?.exists() == true
            Log.d(TAG, "isLikedByCurrentUser: Statut de like pour '$currentUserId' sur '$targetUserId': $isLiked")
            trySend(Resource.Success(isLiked))
        }

        awaitClose {
            Log.d(TAG, "isLikedByCurrentUser: Fermeture du listener de like pour '$currentUserId' sur '$targetUserId'.")
            listenerRegistration.remove()
        }
    }

    override fun getActiveReadingLikesCount(targetUserId: String): Flow<Resource<Int>> = callbackFlow {
        if (targetUserId.isBlank()) {
            Log.w(TAG, "getActiveReadingLikesCount: targetUserId est vide.")
            trySend(Resource.Error("L'ID de l'utilisateur cible ne peut pas être vide."))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading())
        Log.i(TAG, "getActiveReadingLikesCount: Tentative de récupération du nombre de likes pour la lecture de l'utilisateur ID: '$targetUserId'.")

        val likesCollectionRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES)

        val listenerRegistration = likesCollectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "getActiveReadingLikesCount: Erreur Firestore lors de l'écoute du nombre de likes pour ID '$targetUserId': ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }

            val likesCount = snapshot?.size() ?: 0
            Log.d(TAG, "getActiveReadingLikesCount: Nombre de likes pour ID '$targetUserId': $likesCount")
            trySend(Resource.Success(likesCount))
        }

        awaitClose {
            Log.d(TAG, "getActiveReadingLikesCount: Fermeture du listener du nombre de likes pour ID '$targetUserId'.")
            listenerRegistration.remove()
        }
    }

    // =====================================================================================
    // IMPLÉMENTATIONS DES NOUVELLES MÉTHODES POUR LA GESTION DES LIKES SUR LES COMMENTAIRES
    // =====================================================================================

    override suspend fun toggleLikeOnComment(targetUserId: String, commentId: String, currentUserId: String): Resource<Unit> {
        if (targetUserId.isBlank() || commentId.isBlank() || currentUserId.isBlank()) {
            Log.w(TAG, "toggleLikeOnComment: IDs manquants. TargetUser: $targetUserId, CommentID: $commentId, CurrentUser: $currentUserId")
            return Resource.Error("Informations manquantes pour liker/déliker le commentaire.")
        }

        // Référence au document du like de l'utilisateur courant pour ce commentaire
        val likeDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
            .document(commentId)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES)
            .document(currentUserId) // Le document du like est nommé d'après l'UID de l'utilisateur qui like

        // Référence au document du commentaire lui-même (pour mettre à jour le compteur)
        val commentDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
            .document(commentId)

        return try {
            firestore.runTransaction { transaction ->
                val likeSnapshot = transaction.get(likeDocRef)
                val commentSnapshot = transaction.get(commentDocRef)

                if (!commentSnapshot.exists()) {
                    Log.e(TAG, "toggleLikeOnComment: Le commentaire '$commentId' n'existe pas sur le profil de '$targetUserId'.")
                    throw FirebaseFirestoreException("Le commentaire n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                if (likeSnapshot.exists()) {
                    // Le like existe : on le supprime et on décrémente le compteur
                    transaction.delete(likeDocRef)
                    transaction.update(commentDocRef, "likesCount", FieldValue.increment(-1))
                    // On ne touche pas à lastLikeTimestamp lors d'une décrémentation, car un like plus ancien pourrait être le dernier restant
                    Log.i(TAG, "toggleLikeOnComment: Like de '$currentUserId' sur commentaire '$commentId' supprimé. Compteur décrémenté.")
                } else {
                    // Le like n'existe pas : on le crée et on incrémente le compteur
                    val newLike = Like(
                        likeId = currentUserId,
                        userId = currentUserId,
                        targetUserId = targetUserId,
                        readingId = FirebaseConstants.DOCUMENT_ACTIVE_READING, // Toujours activeReading pour ce contexte
                        commentId = commentId, // C'est un like de commentaire
                        timestamp = Timestamp.now()
                    )
                    transaction.set(likeDocRef, newLike)
                    transaction.update(commentDocRef, "likesCount", FieldValue.increment(1))
                    transaction.update(commentDocRef, "lastLikeTimestamp", Timestamp.now()) // Met à jour le timestamp du dernier like
                    Log.i(TAG, "toggleLikeOnComment: Like de '$currentUserId' sur commentaire '$commentId' ajouté. Compteur incrémenté.")
                }
                null // Transaction réussie
            }.await()
            Resource.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "toggleLikeOnComment: Erreur Firestore lors de la bascule du like sur commentaire '$commentId' par '$currentUserId': Code=${e.code}, Message=${e.message}", e)
            Resource.Error("Erreur Firestore: ${e.localizedMessage}")
        } catch (e: Exception) {
            Log.e(TAG, "toggleLikeOnComment: Erreur inattendue lors de la bascule du like sur commentaire '$commentId' par '$currentUserId': ${e.message}", e)
            Resource.Error("Erreur inattendue: ${e.localizedMessage}")
        }
    }

    override fun isCommentLikedByCurrentUser(targetUserId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        if (targetUserId.isBlank() || commentId.isBlank() || currentUserId.isBlank()) {
            Log.w(TAG, "isCommentLikedByCurrentUser: IDs manquants.")
            trySend(Resource.Error("Informations manquantes pour vérifier le statut de like du commentaire."))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading())
        Log.i(TAG, "isCommentLikedByCurrentUser: Vérification si '$currentUserId' a liké le commentaire '$commentId' de '$targetUserId'.")

        val likeDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
            .document(commentId)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES)
            .document(currentUserId)

        val listenerRegistration = likeDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "isCommentLikedByCurrentUser: Erreur Firestore lors de l'écoute du like pour '$currentUserId' sur commentaire '$commentId': ${error.message}", error)
                trySend(Resource.Error("Erreur lors de la vérification du like du commentaire: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }

            val isLiked = snapshot?.exists() == true
            Log.d(TAG, "isCommentLikedByCurrentUser: Statut de like pour '$currentUserId' sur commentaire '$commentId': $isLiked")
            trySend(Resource.Success(isLiked))
        }

        awaitClose {
            Log.d(TAG, "isCommentLikedByCurrentUser: Fermeture du listener de like pour commentaire '$commentId' par '$currentUserId'.")
            listenerRegistration.remove()
        }
    }

    // =====================================================================================
    // IMPLÉMENTATIONS DES NOUVELLES MÉTHODES POUR LA GESTION DES LECTURES TERMINÉES
    // =====================================================================================

    override suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit> {
        if (userId.isBlank()) {
            Log.w(TAG, "markActiveReadingAsCompleted: userId est vide.")
            return Resource.Error("L'ID utilisateur ne peut pas être vide.")
        }
        if (activeReadingDetails.bookId.isBlank()) {
            Log.w(TAG, "markActiveReadingAsCompleted: L'ID du livre dans activeReadingDetails est vide.")
            return Resource.Error("Les détails de la lecture active sont incomplets (ID du livre manquant).")
        }

        val activeReadingDocRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)

        val completedReadingDocRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(activeReadingDetails.bookId) // Utilise l'ID du livre comme ID du document pour la lecture terminée

        val userDocRef = usersCollection.document(userId)

        Log.d(TAG, "markActiveReadingAsCompleted: Début de la transaction pour marquer la lecture comme terminée pour l'utilisateur '$userId'.")

        return try {
            firestore.runTransaction { transaction ->
                Log.d(TAG, "markActiveReadingAsCompleted: [Transaction Started] Checking active reading existence.")
                // 1. Vérifier si la lecture active existe avant de tenter de la supprimer
                val activeReadingSnapshot = transaction.get(activeReadingDocRef)
                if (!activeReadingSnapshot.exists()) {
                    Log.e(TAG, "markActiveReadingAsCompleted: [Transaction Fail] Aucune lecture active trouvée pour l'utilisateur '$userId'. Impossible de marquer comme terminée. Rolling back.")
                    // Le message d'erreur est déjà défini ici dans la transaction
                    throw FirebaseFirestoreException("Aucune lecture en cours à marquer comme terminée.", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                Log.d(TAG, "markActiveReadingAsCompleted: [Transaction Progress] Active reading found: ${activeReadingSnapshot.id}.")

                // 2. Préparer l'objet CompletedReading
                val completedReading = CompletedReading(
                    bookId = activeReadingDetails.bookId,
                    userId = userId,
                    title = activeReadingDetails.title,
                    author = activeReadingDetails.author,
                    coverImageUrl = activeReadingDetails.coverImageUrl,
                    totalPages = activeReadingDetails.totalPages,
                    completionDate = Date()
                )

                // 3. Ajouter la lecture aux completed_readings
                Log.d(TAG, "markActiveReadingAsCompleted: [Transaction Progress] Attempting to set completed reading for bookId: '${activeReadingDetails.bookId}'.")
                transaction.set(completedReadingDocRef, completedReading)
                Log.d(TAG, "markActiveReadingAsCompleted: [Transaction Progress] Set on completed_readings document done.")

                // 4. Supprimer la lecture active
                Log.d(TAG, "markActiveReadingAsCompleted: [Transaction Progress] Attempting to delete active reading document.")
                transaction.delete(activeReadingDocRef)
                Log.d(TAG, "markActiveReadingAsCompleted: [Transaction Progress] Delete on activeReading document done.")

                // 5. Incrémenter booksReadCount sur le document utilisateur
                Log.d(TAG, "markActiveReadingAsCompleted: [Transaction Progress] Attempting to increment booksReadCount for user: '$userId'.")
                transaction.update(userDocRef, "booksReadCount", FieldValue.increment(1))
                Log.d(TAG, "markActiveReadingAsCompleted: [Transaction Progress] Update on booksReadCount done.")

                Log.i(TAG, "markActiveReadingAsCompleted: [Transaction Success] All operations registered. Committing transaction for user '$userId'.")
                null // La transaction a réussi
            }.await()
            Log.i(TAG, "markActiveReadingAsCompleted: [Repository Success] Firestore transaction completed successfully for user '$userId'.")
            Resource.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "markActiveReadingAsCompleted: [Repository Error] Firestore transaction failed for '$userId'. Code=${e.code}, Message=${e.message}, Cause=${e.cause?.message}", e)
            // MODIFICATION ICI : Gérer spécifiquement FirebaseFirestoreException.Code.NOT_FOUND
            return when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> Resource.Error(e.message ?: "Lecture active non trouvée.")
                else -> Resource.Error("Erreur Firestore: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "markActiveReadingAsCompleted: [Repository Error] Unexpected error during transaction for '$userId': ${e.message}", e)
            Resource.Error("Erreur inattendue: ${e.localizedMessage}")
        }
    }

    override suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit> {
        if (userId.isBlank() || bookId.isBlank()) {
            Log.w(TAG, "removeCompletedReading: userId ou bookId est vide.")
            return Resource.Error("L'ID utilisateur ou l'ID du livre ne peut pas être vide.")
        }

        val completedReadingDocRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)

        val userDocRef = usersCollection.document(userId)

        Log.d(TAG, "removeCompletedReading: Début de la transaction pour supprimer la lecture terminée '$bookId' pour l'utilisateur '$userId'.")

        return try {
            firestore.runTransaction { transaction ->
                Log.d(TAG, "removeCompletedReading: [Transaction Started] Checking completed reading existence for bookId: '$bookId'.")
                // 1. Vérifier si la lecture terminée existe avant de tenter de la supprimer
                val completedReadingSnapshot = transaction.get(completedReadingDocRef)
                if (!completedReadingSnapshot.exists()) {
                    Log.w(TAG, "removeCompletedReading: [Transaction Fail] La lecture terminée '$bookId' n'existe pas pour l'utilisateur '$userId'. Pas de suppression. Rolling back.")
                    // Le message d'erreur est déjà défini ici dans la transaction
                    throw FirebaseFirestoreException("La lecture terminée n'a pas été trouvée.", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                Log.d(TAG, "removeCompletedReading: [Transaction Progress] Completed reading found: ${completedReadingSnapshot.id}.")

                // 2. Supprimer la lecture terminée
                Log.d(TAG, "removeCompletedReading: [Transaction Progress] Attempting to delete completed reading for bookId: '$bookId'.")
                transaction.delete(completedReadingDocRef)
                Log.d(TAG, "removeCompletedReading: [Transaction Progress] Delete on completed_readings done.")

                // 3. Décrémenter booksReadCount sur le document utilisateur
                Log.d(TAG, "removeCompletedReading: [Transaction Progress] Attempting to decrement 'booksReadCount' for user: '$userId'.")
                transaction.update(userDocRef, "booksReadCount", FieldValue.increment(-1))
                Log.d(TAG, "removeCompletedReading: [Transaction Progress] Update on booksReadCount done.")

                Log.i(TAG, "removeCompletedReading: [Transaction Success] All operations registered. Committing transaction for user '$userId'.")
                null // La transaction a réussi
            }.await()
            Log.i(TAG, "removeCompletedReading: [Repository Success] Firestore transaction completed successfully for user '$userId'.")
            Resource.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "removeCompletedReading: [Repository Error] Firestore transaction failed for '$userId'. Code=${e.code}, Message=${e.message}, Cause=${e.cause?.message}", e)
            // MODIFICATION ICI : Gérer spécifiquement FirebaseFirestoreException.Code.NOT_FOUND
            return when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> Resource.Error(e.message ?: "Lecture terminée non trouvée.")
                else -> Resource.Error("Erreur Firestore: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "removeCompletedReading: [Repository Error] Unexpected error during transaction for '$userId': ${e.message}", e)
            Resource.Error("Erreur inattendue: ${e.localizedMessage}")
        }
    }

    override fun getCompletedReadings(userId: String): Flow<Resource<List<CompletedReading>>> = callbackFlow {
        if (userId.isBlank()) {
            Log.w(TAG, "getCompletedReadings: userId est vide.")
            trySend(Resource.Error("L'ID utilisateur ne peut pas être vide."))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading())
        Log.i(TAG, "getCompletedReadings: Tentative de récupération des lectures terminées pour l'utilisateur ID: '$userId'.")

        val completedReadingsCollectionRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)

        val listenerRegistration = completedReadingsCollectionRef
            .orderBy("completionDate", Query.Direction.DESCENDING) // Tri par date de complétion
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getCompletedReadings: Erreur Firestore lors de l'écoute des lectures terminées pour ID '$userId': ${error.message}", error)
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val completedReadingsList = mutableListOf<CompletedReading>()
                    for (document in snapshot.documents) {
                        try {
                            val completedReading = document.toObject(CompletedReading::class.java)
                            completedReading?.let { completedReadingsList.add(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "getCompletedReadings: Erreur de conversion du document de lecture terminée ${document.id} pour ID '$userId': ${e.message}", e)
                        }
                    }
                    Log.d(TAG, "getCompletedReadings: ${completedReadingsList.size} lectures terminées récupérées pour ID '$userId'.")
                    trySend(Resource.Success(completedReadingsList))
                } else {
                    Log.d(TAG, "getCompletedReadings: Snapshot de lectures terminées est null pour ID '$userId'.")
                    trySend(Resource.Success(emptyList()))
                }
            }

        awaitClose {
            Log.d(TAG, "getCompletedReadings: Fermeture du listener de lectures terminées pour ID '$userId'.")
            listenerRegistration.remove()
        }
    }
}