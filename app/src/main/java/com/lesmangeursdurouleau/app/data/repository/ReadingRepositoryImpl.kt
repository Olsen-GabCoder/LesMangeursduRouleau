package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implémentation de ReadingRepository pour la gestion des activités de lecture avec Firebase.
 */
class ReadingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ReadingRepository {

    override fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun addCommentOnActiveReading(targetUserId: String, bookId: String, comment: Comment): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override fun getCommentsOnActiveReading(targetUserId: String, bookId: String): Flow<Resource<List<Comment>>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun deleteCommentOnActiveReading(targetUserId: String, bookId: String, commentId: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun toggleLikeOnActiveReading(targetUserId: String, bookId: String, currentUserId: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override fun isLikedByCurrentUser(targetUserId: String, bookId: String, currentUserId: String): Flow<Resource<Boolean>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override fun getActiveReadingLikesCount(targetUserId: String, bookId: String): Flow<Resource<Int>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun toggleLikeOnComment(
        targetUserId: String,
        bookId: String,
        commentId: String,
        currentUserId: String
    ): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override fun isCommentLikedByCurrentUser(
        targetUserId: String,
        bookId: String,
        commentId: String,
        currentUserId: String
    ): Flow<Resource<Boolean>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override fun getCompletedReadings(
        userId: String,
        orderBy: String,
        direction: Query.Direction
    ): Flow<Resource<List<CompletedReading>>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override fun getCompletedReadingDetail(userId: String, bookId: String): Flow<Resource<CompletedReading?>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }
}