package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.model.Like
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

class ReadingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ReadingRepository {

    companion object {
        private const val TAG = "ReadingRepositoryImpl"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    override fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("L'ID utilisateur ne peut pas être vide."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val docRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                try {
                    val reading = snapshot.toObject(UserBookReading::class.java)
                    trySend(Resource.Success(reading))
                } catch (e: Exception) {
                    trySend(Resource.Error("Erreur de conversion des données de lecture."))
                }
            } else {
                trySend(Resource.Success(null))
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit> {
        if (userId.isBlank()) return Resource.Error("L'ID utilisateur ne peut pas être vide.")
        val docRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
        return try {
            if (userBookReading != null) {
                docRef.set(userBookReading, SetOptions.merge()).await()
            } else {
                docRef.delete().await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun addCommentOnActiveReading(targetUserId: String, bookId: String, comment: Comment): Resource<Unit> {
        if (targetUserId.isBlank() || bookId.isBlank() || comment.commentText.isBlank() || comment.userId.isBlank()) {
            return Resource.Error("Informations manquantes pour ajouter un commentaire.")
        }
        val collectionRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
        return try {
            collectionRef.add(comment).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur lors de l'ajout du commentaire: ${e.localizedMessage}")
        }
    }

    override fun getCommentsOnActiveReading(targetUserId: String, bookId: String): Flow<Resource<List<Comment>>> = callbackFlow {
        if (targetUserId.isBlank() || bookId.isBlank()) {
            trySend(Resource.Error("ID utilisateur ou livre manquant."))
            close(); return@callbackFlow
        }
        trySend(Resource.Loading())
        val collectionRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            if (snapshot != null) {
                val comments = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Comment::class.java)?.copy(commentId = doc.id)
                }
                trySend(Resource.Success(comments))
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun deleteCommentOnActiveReading(targetUserId: String, bookId: String, commentId: String): Resource<Unit> {
        if (targetUserId.isBlank() || bookId.isBlank() || commentId.isBlank()) {
            return Resource.Error("Informations manquantes pour supprimer le commentaire.")
        }
        val docRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
            .document(commentId)
        return try {
            docRef.delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun toggleLikeOnActiveReading(targetUserId: String, bookId: String, currentUserId: String): Resource<Unit> {
        if (targetUserId.isBlank() || bookId.isBlank() || currentUserId.isBlank()) {
            return Resource.Error("Informations manquantes pour liker.")
        }
        val likeDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES)
            .document(currentUserId)
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(likeDocRef)
                if (snapshot.exists()) {
                    transaction.delete(likeDocRef)
                } else {
                    val newLike = Like(likeId = currentUserId, userId = currentUserId, targetUserId = targetUserId, bookId = bookId, timestamp = Timestamp.now(), commentId = null)
                    transaction.set(likeDocRef, newLike)
                }
                null
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override fun isLikedByCurrentUser(targetUserId: String, bookId: String, currentUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        if (targetUserId.isBlank() || bookId.isBlank() || currentUserId.isBlank()) {
            trySend(Resource.Error("Informations manquantes."))
            close(); return@callbackFlow
        }
        trySend(Resource.Loading())
        val likeDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES)
            .document(currentUserId)
        val listener = likeDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            trySend(Resource.Success(snapshot?.exists() == true))
        }
        awaitClose { listener.remove() }
    }

    override fun getActiveReadingLikesCount(targetUserId: String, bookId: String): Flow<Resource<Int>> = callbackFlow {
        if (targetUserId.isBlank() || bookId.isBlank()) {
            trySend(Resource.Error("Informations manquantes."))
            close(); return@callbackFlow
        }
        trySend(Resource.Loading())
        val likesCollectionRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES)
        val listener = likesCollectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            trySend(Resource.Success(snapshot?.size() ?: 0))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleLikeOnComment(targetUserId: String, bookId: String, commentId: String, currentUserId: String): Resource<Unit> {
        if (targetUserId.isBlank() || bookId.isBlank() || commentId.isBlank() || currentUserId.isBlank()) {
            return Resource.Error("Informations manquantes.")
        }
        val likeDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS).document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES).document(currentUserId)
        val commentDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS).document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId)
        return try {
            firestore.runTransaction { transaction ->
                val likeSnapshot = transaction.get(likeDocRef)
                if (!transaction.get(commentDocRef).exists()) throw FirebaseFirestoreException("Le commentaire n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                if (likeSnapshot.exists()) {
                    transaction.delete(likeDocRef)
                    transaction.update(commentDocRef, "likesCount", FieldValue.increment(-1))
                } else {
                    val newLike = Like(likeId = currentUserId, userId = currentUserId, targetUserId = targetUserId, bookId = bookId, commentId = commentId, timestamp = Timestamp.now())
                    transaction.set(likeDocRef, newLike)
                    transaction.update(commentDocRef, "likesCount", FieldValue.increment(1))
                    transaction.update(commentDocRef, "lastLikeTimestamp", Timestamp.now())
                }
                null
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override fun isCommentLikedByCurrentUser(targetUserId: String, bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        if (targetUserId.isBlank() || bookId.isBlank() || commentId.isBlank() || currentUserId.isBlank()) {
            trySend(Resource.Error("Informations manquantes."))
            close(); return@callbackFlow
        }
        trySend(Resource.Loading())
        val likeDocRef = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS).document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES).document(currentUserId)
        val listener = likeDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            trySend(Resource.Success(snapshot?.exists() == true))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit> {
        if (userId.isBlank() || activeReadingDetails.bookId.isBlank()) {
            return Resource.Error("Informations de lecture invalides.")
        }
        val activeReadingDocRef = usersCollection.document(userId).collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS).document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
        val completedReadingDocRef = usersCollection.document(userId).collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS).document(activeReadingDetails.bookId)
        val userDocRef = usersCollection.document(userId)
        return try {
            firestore.runTransaction { transaction ->
                val completedReading = CompletedReading(bookId = activeReadingDetails.bookId, userId = userId, title = activeReadingDetails.title, author = activeReadingDetails.author, coverImageUrl = activeReadingDetails.coverImageUrl, totalPages = activeReadingDetails.totalPages, completionDate = Date())
                transaction.set(completedReadingDocRef, completedReading)
                transaction.delete(activeReadingDocRef)
                transaction.update(userDocRef, "booksReadCount", FieldValue.increment(1))
                null
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit> {
        if (userId.isBlank() || bookId.isBlank()) return Resource.Error("Informations manquantes.")
        val docRef = usersCollection.document(userId).collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS).document(bookId)
        val userDocRef = usersCollection.document(userId)
        return try {
            firestore.runTransaction { transaction ->
                if (!transaction.get(docRef).exists()) throw FirebaseFirestoreException("Lecture non trouvée.", FirebaseFirestoreException.Code.NOT_FOUND)
                transaction.delete(docRef)
                transaction.update(userDocRef, "booksReadCount", FieldValue.increment(-1))
                null
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override fun getCompletedReadings(userId: String, orderBy: String, direction: Query.Direction): Flow<Resource<List<CompletedReading>>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("ID utilisateur manquant."))
            close(); return@callbackFlow
        }
        trySend(Resource.Loading())
        val collectionRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .orderBy(orderBy, direction)
        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            if (snapshot != null) {
                val readings = snapshot.toObjects(CompletedReading::class.java)
                trySend(Resource.Success(readings))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getCompletedReadingDetail(userId: String, bookId: String): Flow<Resource<CompletedReading?>> = callbackFlow {
        if (userId.isBlank() || bookId.isBlank()) {
            trySend(Resource.Error("Informations manquantes."))
            close(); return@callbackFlow
        }
        trySend(Resource.Loading())
        val docRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                try {
                    val reading = snapshot.toObject(CompletedReading::class.java)
                    trySend(Resource.Success(reading))
                } catch (e: Exception) {
                    trySend(Resource.Error("Erreur de conversion."))
                }
            } else {
                trySend(Resource.Success(null))
            }
        }
        awaitClose { listener.remove() }
    }
}