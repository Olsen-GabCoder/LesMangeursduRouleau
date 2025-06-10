package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.User
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

    /**
     * Permet à l'utilisateur courant de suivre un autre utilisateur.
     * @param currentUserId L'ID de l'utilisateur qui effectue l'action de suivre.
     * @param targetUserId L'ID de l'utilisateur à suivre.
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit>

    /**
     * Permet à l'utilisateur courant de ne plus suivre un autre utilisateur.
     * @param currentUserId L'ID de l'utilisateur qui effectue l'action de ne plus suivre.
     * @param targetUserId L'ID de l'utilisateur à ne plus suivre.
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit>

    /**
     * Vérifie si l'utilisateur courant suit un utilisateur cible.
     * @param currentUserId L'ID de l'utilisateur qui vérifie le suivi.
     * @param targetUserId L'ID de l'utilisateur dont on veut savoir s'il est suivi.
     * @return Un Flow de Resource<Boolean> indiquant l'état de suivi.
     */
    fun isFollowing(currentUserId: String, targetUserId: String): Flow<Resource<Boolean>>

    /**
     * Récupère la liste des utilisateurs suivis par un utilisateur donné.
     * @param userId L'ID de l'utilisateur dont on veut la liste des suivis.
     * @return Un Flow de Resource<List<User>> contenant les profils des utilisateurs suivis.
     */
    fun getFollowingUsers(userId: String): Flow<Resource<List<User>>>

    /**
     * NOUVEAU: Récupère la liste des utilisateurs qui suivent un utilisateur donné (ses "followers").
     * @param userId L'ID de l'utilisateur dont on veut la liste des followers.
     * @return Un Flow de Resource<List<User>> contenant les profils des followers.
     */
    fun getFollowersUsers(userId: String): Flow<Resource<List<User>>> // <-- NOUVELLE DÉCLARATION
}