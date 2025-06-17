package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.Like
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun updateUserProfile(userId: String, username: String): Resource<Unit>
    suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String>
    fun getAllUsers(): Flow<Resource<List<User>>>
    fun getUserById(userId: String): Flow<Resource<User>>
    suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit>
    suspend fun updateUserBio(userId: String, bio: String): Resource<Unit>
    suspend fun updateUserCity(userId: String, city: String): Resource<Unit>
    suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit>
    suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit>
    suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit>

    suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit>

    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit>

    fun isFollowing(currentUserId: String, targetUserId: String): Flow<Resource<Boolean>>

    fun getFollowingUsers(userId: String): Flow<Resource<List<User>>>

    fun getFollowersUsers(userId: String): Flow<Resource<List<User>>>

    fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>>

    suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit>

    suspend fun addCommentOnActiveReading(targetUserId: String, bookId: String, comment: Comment): Resource<Unit>

    fun getCommentsOnActiveReading(targetUserId: String, bookId: String): Flow<Resource<List<Comment>>>

    suspend fun deleteCommentOnActiveReading(targetUserId: String, bookId: String, commentId: String): Resource<Unit>

    suspend fun toggleLikeOnActiveReading(targetUserId: String, bookId: String, currentUserId: String): Resource<Unit>

    fun isLikedByCurrentUser(targetUserId: String, bookId: String, currentUserId: String): Flow<Resource<Boolean>>

    fun getActiveReadingLikesCount(targetUserId: String, bookId: String): Flow<Resource<Int>>

    suspend fun toggleLikeOnComment(targetUserId: String, bookId: String, commentId: String, currentUserId: String): Resource<Unit>

    fun isCommentLikedByCurrentUser(targetUserId: String, bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>>

    suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit>

    suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit>

    /**
     * MODIFIÉ: Récupère un flux de lectures terminées pour un utilisateur, avec options de tri.
     * @param userId L'ID de l'utilisateur.
     * @param orderBy Le champ sur lequel trier (ex: "completionDate", "title").
     * @param direction La direction du tri (ASCENDING ou DESCENDING).
     * @return Un Flow de Resource<List<CompletedReading>>.
     */
    fun getCompletedReadings(
        userId: String,
        orderBy: String,
        direction: Query.Direction
    ): Flow<Resource<List<CompletedReading>>>

    fun getCompletedReadingDetail(userId: String, bookId: String): Flow<Resource<CompletedReading?>>
}