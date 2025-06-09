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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldPath

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
                    // L'erreur "Unreachable code" pourrait être ici si 'close(error)' est suivi par 'return@addSnapshotListener'
                    // On ne peut pas avoir de code après 'close()' dans un callbackFlow
                    // Le 'return' est suffisant pour sortir du lambda et le 'close' termine le flow
                    close(error)
                    return@addSnapshotListener // Cette ligne est atteignable si 'close(error)' ne jette pas d'exception immédiatement
                }
                if (snapshot != null) {
                    val usersList = mutableListOf<User>()
                    for (document in snapshot.documents) { // 'document' est bien résolu ici
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
                                lastPermissionGrantedTimestamp = document.getLong("lastPermissionGrantedTimestamp"),
                                followersCount = document.getLong("followersCount")?.toInt() ?: 0,
                                followingCount = document.getLong("followingCount")?.toInt() ?: 0
                            )
                            usersList.add(user)
                        } catch (e: Exception) {
                            Log.e(TAG, "getAllUsers: Erreur de conversion du document ${document.id}", e)
                            // En cas d'erreur de conversion d'un document, on logue et on continue les autres
                            // Plutôt que de fermer le flow entier pour une seule mauvaise conversion.
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
            close() // Ferme le flow et le lambda se termine
            return@callbackFlow // S'assure de quitter le callbackFlow pour éviter "Unreachable code" si la logique après le close change
        }
        trySend(Resource.Loading())
        Log.i(TAG, "getUserById: Tentative de récupération de l'utilisateur ID: '$userId' depuis la collection '${FirebaseConstants.COLLECTION_USERS}'.")
        val docRef = usersCollection.document(userId)
        val listenerRegistration = docRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.e(TAG, "getUserById: Erreur Firestore pour ID '$userId': ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error) // Ferme le flow
                return@addSnapshotListener // S'assure de quitter le lambda du listener
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
                        lastPermissionGrantedTimestamp = documentSnapshot.getLong("lastPermissionGrantedTimestamp"),
                        followersCount = documentSnapshot.getLong("followersCount")?.toInt() ?: 0,
                        followingCount = documentSnapshot.getLong("followingCount")?.toInt() ?: 0
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

    // --- NOUVELLES IMPLÉMENTATIONS DES MÉTHODES DE SUIVI AVEC TRANSACTIONS ---

    override suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        Log.d(TAG, "followUser: Utilisateur $currentUserId tente de suivre $targetUserId")
        return try {
            if (currentUserId == targetUserId) {
                Log.w(TAG, "followUser: Un utilisateur ne peut pas se suivre lui-même. ID: $currentUserId")
                return Resource.Error("Vous ne pouvez pas vous suivre vous-même.")
            }

            // Exécuter toutes les opérations de suivi dans une transaction
            firestore.runTransaction { transaction ->
                // 1. Vérifier si l'utilisateur cible existe réellement
                val targetUserDocRef = usersCollection.document(targetUserId)
                val targetUserDoc = transaction.get(targetUserDocRef)

                if (!targetUserDoc.exists()) {
                    Log.e(TAG, "followUser: Transaction: L'utilisateur cible $targetUserId n'existe pas dans Firestore.")
                    throw FirebaseFirestoreException("L'utilisateur à suivre n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                // 2. Vérifier si l'utilisateur courant ne suit pas déjà la cible
                val followingDocRef = usersCollection.document(currentUserId).collection("following").document(targetUserId)
                val followingDoc = transaction.get(followingDocRef)
                if (followingDoc.exists()) {
                    Log.w(TAG, "followUser: Transaction: Utilisateur $currentUserId suit déjà $targetUserId. Opération ignorée.")
                    throw FirebaseFirestoreException("Vous suivez déjà cet utilisateur.", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                }

                // 3. Ajouter le document dans la sous-collection 'following' de l'utilisateur courant
                Log.d(TAG, "followUser: Transaction: Ajout du document de suivi: $followingDocRef")
                transaction.set(followingDocRef, mapOf("timestamp" to System.currentTimeMillis()))

                // 4. Ajouter le document dans la sous-collection 'followers' de l'utilisateur cible
                val followersDocRef = usersCollection.document(targetUserId).collection("followers").document(currentUserId)
                Log.d(TAG, "followUser: Transaction: Ajout du document de follower: $followersDocRef")
                transaction.set(followersDocRef, mapOf("timestamp" to System.currentTimeMillis()))

                // 5. Incrémenter les compteurs
                val currentUserDocRef = usersCollection.document(currentUserId)
                Log.d(TAG, "followUser: Transaction: Incrémentation de followingCount pour $currentUserId.")
                transaction.update(currentUserDocRef, "followingCount", FieldValue.increment(1))

                Log.d(TAG, "followUser: Transaction: Incrémentation de followersCount pour $targetUserId.")
                transaction.update(targetUserDocRef, "followersCount", FieldValue.increment(1))

                Log.i(TAG, "followUser: Transaction complétée pour $currentUserId suivant $targetUserId.")
                null // La transaction réussit si aucun throw n'est déclenché
            }.await() // Attendre la fin de la transaction

            Resource.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "followUser: Erreur Firestore lors du suivi de l'utilisateur $targetUserId par $currentUserId: Code=${e.code}, Message=${e.message}", e)
            // Gérer les erreurs spécifiques que nous avons lancées
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
            // Exécuter toutes les opérations de désabonnement dans une transaction
            firestore.runTransaction { transaction ->
                // 1. Vérifier si l'utilisateur cible existe réellement
                val targetUserDocRef = usersCollection.document(targetUserId)
                val targetUserDoc = transaction.get(targetUserDocRef)

                if (!targetUserDoc.exists()) {
                    Log.e(TAG, "unfollowUser: Transaction: L'utilisateur cible $targetUserId n'existe pas dans Firestore.")
                    throw FirebaseFirestoreException("L'utilisateur à désabonner n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                // 2. Vérifier si l'utilisateur courant suit réellement la cible
                val followingDocRef = usersCollection.document(currentUserId).collection("following").document(targetUserId)
                val followingDoc = transaction.get(followingDocRef)
                if (!followingDoc.exists()) {
                    Log.w(TAG, "unfollowUser: Transaction: Utilisateur $currentUserId ne suit pas $targetUserId. Opération ignorée.")
                    throw FirebaseFirestoreException("Vous ne suivez pas cet utilisateur.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                // 3. Supprimer le document de la sous-collection 'following' de l'utilisateur courant
                Log.d(TAG, "unfollowUser: Transaction: Suppression du document de suivi: $followingDocRef")
                transaction.delete(followingDocRef)

                // 4. Supprimer le document de la sous-collection 'followers' de l'utilisateur cible
                val followersDocRef = usersCollection.document(targetUserId).collection("followers").document(currentUserId)
                Log.d(TAG, "unfollowUser: Transaction: Suppression du document de follower: $followersDocRef")
                transaction.delete(followersDocRef)

                // 5. Décrémenter les compteurs
                val currentUserDocRef = usersCollection.document(currentUserId)
                val currentUserDoc = transaction.get(currentUserDocRef) // Lire le document pour vérifier le compteur actuel

                // Vérifier que le compteur ne deviendra pas négatif
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
                null // La transaction réussit
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
        trySend(Resource.Loading()) // Indiquer que le chargement est en cours
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

            // Si le snapshot existe, cela signifie que currentUserId suit targetUserId
            val isFollowed = snapshot?.exists() == true
            Log.d(TAG, "isFollowing: Statut de suivi pour $currentUserId -> $targetUserId: $isFollowed")
            trySend(Resource.Success(isFollowed))
        }

        // Le bloc awaitClose est appelé lorsque le Flow est annulé ou terminé
        awaitClose {
            Log.d(TAG, "isFollowing: Fermeture du listener de statut de suivi pour $currentUserId -> $targetUserId")
            listenerRegistration.remove()
        }
    }

    override fun getFollowingUsers(userId: String): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading()) // Indiquer que le chargement est en cours
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
                // Étape 1: Récupérer les IDs des utilisateurs suivis
                val followedUserIds = snapshot.documents.map { it.id }
                Log.d(TAG, "getFollowingUsers: IDs des utilisateurs suivis par $userId: $followedUserIds")

                // Étape 2: Récupérer les informations complètes de ces utilisateurs
                if (followedUserIds.isNotEmpty()) {
                    val chunks = followedUserIds.chunked(10)
                    val allFollowedUsers = mutableListOf<User>()
                    var fetchErrors = false

                    chunks.forEachIndexed { index, chunk ->
                        usersCollection.whereIn(FieldPath.documentId(), chunk)
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val chunkUsers = usersSnapshot.documents.mapNotNull { userDoc -> // Renommé 'doc' en 'userDoc' pour plus de clarté
                                    try {
                                        userDoc.toObject(User::class.java)?.apply {
                                            this.uid = userDoc.id // S'assurer que l'UID est correctement mappé
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
                                    trySend(Resource.Success(allFollowedUsers))
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
}