package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class SocialRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : SocialRepository {

    companion object {
        private const val TAG = "SocialRepositoryImpl"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    // Helper function to create User from snapshot, may be needed if UserProfileRepository is not available
    private fun createUserFromSnapshot(document: com.google.firebase.firestore.DocumentSnapshot): User {
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
                val followingDoc = transaction.get(followingDocRef)

                val currentUserDocRef = usersCollection.document(currentUserId)

                if (followingDoc.exists()) {
                    throw FirebaseFirestoreException("Vous suivez déjà cet utilisateur.", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                }

                val targetUserDoc = transaction.get(targetUserDocRef)
                if (!targetUserDoc.exists()) {
                    throw FirebaseFirestoreException("L'utilisateur à suivre n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                transaction.set(followingDocRef, mapOf("timestamp" to Timestamp.now()))
                transaction.set(usersCollection.document(targetUserId).collection("followers").document(currentUserId), mapOf("timestamp" to Timestamp.now()))
                transaction.update(currentUserDocRef, "followingCount", FieldValue.increment(1))
                transaction.update(targetUserDocRef, "followersCount", FieldValue.increment(1))
                null
            }.await()

            Resource.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "followUser: Erreur Firestore lors du suivi: Code=${e.code}, Message=${e.message}", e)
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> Resource.Error("L'utilisateur à suivre n'existe pas.")
                FirebaseFirestoreException.Code.ALREADY_EXISTS -> Resource.Error("Vous suivez déjà cet utilisateur.")
                else -> Resource.Error("Erreur Firestore lors du suivi: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "followUser: Erreur générale lors du suivi: ${e.message}", e)
            Resource.Error("Erreur inattendue lors du suivi : ${e.localizedMessage}")
        }
    }

    override suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        Log.d(TAG, "unfollowUser: Utilisateur $currentUserId tente de ne plus suivre $targetUserId")
        return try {
            firestore.runTransaction { transaction ->
                val targetUserDocRef = usersCollection.document(targetUserId)
                val followingDocRef = usersCollection.document(currentUserId).collection("following").document(targetUserId)
                val followingDoc = transaction.get(followingDocRef)

                val currentUserDocRef = usersCollection.document(currentUserId)

                if (!followingDoc.exists()) {
                    throw FirebaseFirestoreException("Vous ne suivez pas cet utilisateur.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                val targetUserDoc = transaction.get(targetUserDocRef)
                if (!targetUserDoc.exists()) {
                    throw FirebaseFirestoreException("L'utilisateur à désabonner n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                transaction.delete(followingDocRef)
                transaction.delete(usersCollection.document(targetUserId).collection("followers").document(currentUserId))
                transaction.update(currentUserDocRef, "followingCount", FieldValue.increment(-1))
                transaction.update(targetUserDocRef, "followersCount", FieldValue.increment(-1))
                null
            }.await()

            Resource.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "unfollowUser: Erreur Firestore: Code=${e.code}, Message=${e.message}", e)
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> Resource.Error("L'utilisateur à désabonner n'existe pas ou vous ne le suivez pas.")
                else -> Resource.Error("Erreur Firestore: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "unfollowUser: Erreur générale: ${e.message}", e)
            Resource.Error("Erreur inattendue : ${e.localizedMessage}")
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
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            trySend(Resource.Success(snapshot?.exists() == true))
        }
        awaitClose { listenerRegistration.remove() }
    }

    override fun getFollowingUsers(userId: String): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        val followingCollectionRef = usersCollection.document(userId).collection("following")

        val listenerRegistration = followingCollectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val followedUserIds = snapshot.documents.map { it.id }
                if (followedUserIds.isNotEmpty()) {
                    // Fetch user details in chunks of 10
                    val chunks = followedUserIds.chunked(10)
                    val allFollowedUsers = mutableListOf<User>()
                    val completedTasks = AtomicInteger(0)

                    chunks.forEach { chunk ->
                        usersCollection.whereIn(FieldPath.documentId(), chunk).get()
                            .addOnSuccessListener { usersSnapshot ->
                                val chunkUsers = usersSnapshot.documents.mapNotNull { userDoc ->
                                    try { createUserFromSnapshot(userDoc) } catch (e: Exception) { null }
                                }
                                synchronized(allFollowedUsers) { allFollowedUsers.addAll(chunkUsers) }
                                if (completedTasks.incrementAndGet() == chunks.size) {
                                    trySend(Resource.Success(allFollowedUsers.distinctBy { it.uid }))
                                }
                            }
                            .addOnFailureListener { e ->
                                trySend(Resource.Error("Erreur: ${e.localizedMessage}"))
                                close(e)
                            }
                    }
                } else {
                    trySend(Resource.Success(emptyList()))
                }
            } else {
                trySend(Resource.Success(emptyList()))
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    override fun getFollowersUsers(userId: String): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        val followersCollectionRef = usersCollection.document(userId).collection("followers")

        val listenerRegistration = followersCollectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.isEmpty) {
                val followerUserIds = snapshot.documents.map { it.id }
                if (followerUserIds.isNotEmpty()) {
                    val chunks = followerUserIds.chunked(10)
                    val allFollowerUsers = mutableListOf<User>()
                    val completedTasks = AtomicInteger(0)

                    chunks.forEach { chunk ->
                        usersCollection.whereIn(FieldPath.documentId(), chunk).get()
                            .addOnSuccessListener { usersSnapshot ->
                                val chunkUsers = usersSnapshot.documents.mapNotNull { userDoc ->
                                    try { createUserFromSnapshot(userDoc) } catch (e: Exception) { null }
                                }
                                synchronized(allFollowerUsers) { allFollowerUsers.addAll(chunkUsers) }
                                if (completedTasks.incrementAndGet() == chunks.size) {
                                    trySend(Resource.Success(allFollowerUsers.distinctBy { it.uid }))
                                }
                            }
                            .addOnFailureListener { e ->
                                trySend(Resource.Error("Erreur: ${e.localizedMessage}"))
                                close(e)
                            }
                    }
                } else {
                    trySend(Resource.Success(emptyList()))
                }
            } else {
                trySend(Resource.Success(emptyList()))
            }
        }
        awaitClose { listenerRegistration.remove() }
    }
}