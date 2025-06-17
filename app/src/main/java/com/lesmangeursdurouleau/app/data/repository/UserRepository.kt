package com.lesmangeursdurouleau.app.data.repository

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
     * Récupère la liste des utilisateurs qui suivent un utilisateur donné (ses "followers").
     * @param userId L'ID de l'utilisateur dont on veut la liste des followers.
     * @return Un Flow de Resource<List<User>> contenant les profils des followers.
     */
    fun getFollowersUsers(userId: String): Flow<Resource<List<User>>>

    /**
     * Récupère la lecture en cours d'un utilisateur.
     * @param userId L'ID de l'utilisateur dont on veut récupérer la lecture en cours.
     * @return Un Flow de Resource<UserBookReading?>. Retourne null si aucune lecture en cours n'est définie.
     */
    fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>>

    /**
     * Met à jour la lecture en cours d'un utilisateur.
     * Si userBookReading est null, cela supprimera la lecture en cours.
     * @param userId L'ID de l'utilisateur dont la lecture en cours est mise à jour.
     * @param userBookReading L'objet UserBookReading à enregistrer/mettre à jour, ou null pour supprimer.
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit>

    /**
     * Ajoute un commentaire sur la lecture active d'un utilisateur cible.
     * @param targetUserId L'ID de l'utilisateur dont la lecture active est commentée.
     * @param bookId L'ID du livre associé à la lecture.
     * @param comment L'objet Comment à ajouter.
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun addCommentOnActiveReading(targetUserId: String, bookId: String, comment: Comment): Resource<Unit>

    /**
     * Récupère un flux de commentaires pour la lecture active d'un utilisateur cible.
     * Les commentaires sont triés par horodatage (les plus récents en premier).
     * @param targetUserId L'ID de l'utilisateur dont les commentaires de la lecture active sont à récupérer.
     * @param bookId L'ID du livre associé à la lecture.
     * @return Un Flow de Resource<List<Comment>>.
     */
    fun getCommentsOnActiveReading(targetUserId: String, bookId: String): Flow<Resource<List<Comment>>>

    /**
     * Permet de supprimer un commentaire sur la lecture active d'un utilisateur cible.
     * Cette action n'est permise que si l'utilisateur courant est l'auteur du commentaire.
     * @param targetUserId L'ID de l'utilisateur dont la lecture active contient le commentaire.
     * @param bookId L'ID du livre associé à la lecture.
     * @param commentId L'ID unique du commentaire à supprimer.
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun deleteCommentOnActiveReading(targetUserId: String, bookId: String, commentId: String): Resource<Unit>

    /**
     * Bascule le statut de "like" pour la lecture active d'un utilisateur cible par l'utilisateur courant.
     * Si un like existe, il est supprimé ; sinon, un nouveau like est créé.
     * @param targetUserId L'ID de l'utilisateur dont la lecture est likée.
     * @param bookId L'ID du livre associé à la lecture.
     * @param currentUserId L'ID de l'utilisateur qui effectue l'action de "like".
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun toggleLikeOnActiveReading(targetUserId: String, bookId: String, currentUserId: String): Resource<Unit>

    /**
     * Vérifie si la lecture active d'un utilisateur cible a été "likée" par l'utilisateur courant.
     * @param targetUserId L'ID de l'utilisateur dont la lecture est vérifiée.
     * @param bookId L'ID du livre associé à la lecture.
     * @param currentUserId L'ID de l'utilisateur dont on veut savoir s'il a liké.
     * @return Un Flow de Resource<Boolean> indiquant si la lecture est likée par l'utilisateur courant.
     */
    fun isLikedByCurrentUser(targetUserId: String, bookId: String, currentUserId: String): Flow<Resource<Boolean>>

    /**
     * Récupère le nombre total de "likes" pour la lecture active d'un utilisateur cible.
     * @param targetUserId L'ID de l'utilisateur dont la lecture active est concernée.
     * @param bookId L'ID du livre associé à la lecture.
     * @return Un Flow de Resource<Int> contenant le nombre de likes.
     */
    fun getActiveReadingLikesCount(targetUserId: String, bookId: String): Flow<Resource<Int>>

    // =====================================================================================
    // NOUVELLES MÉTHODES POUR LA GESTION DES LIKES SUR LES COMMENTAIRES
    // =====================================================================================

    /**
     * Bascule le statut de "like" pour un commentaire spécifique.
     * Si un like existe pour ce commentaire par l'utilisateur courant, il est supprimé ;
     * sinon, un nouveau like est créé.
     * Met également à jour le compteur de likes du commentaire.
     * @param targetUserId L'ID de l'utilisateur dont le profil contient la lecture et le commentaire.
     * @param bookId L'ID du livre associé à la lecture.
     * @param commentId L'ID du commentaire concerné.
     * @param currentUserId L'ID de l'utilisateur qui effectue l'action de "like".
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun toggleLikeOnComment(targetUserId: String, bookId: String, commentId: String, currentUserId: String): Resource<Unit>

    /**
     * Vérifie si un commentaire spécifique a été "liké" par l'utilisateur courant.
     * @param targetUserId L'ID de l'utilisateur dont le profil contient le commentaire.
     * @param bookId L'ID du livre associé à la lecture.
     * @param commentId L'ID du commentaire concerné.
     * @param currentUserId L'ID de l'utilisateur dont on veut savoir s'il a liké.
     * @return Un Flow de Resource<Boolean> indiquant si le commentaire est liké par l'utilisateur courant.
     */
    fun isCommentLikedByCurrentUser(targetUserId: String, bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>>


    // =====================================================================================
    // NOUVELLES MÉTHODES POUR LA GESTION DES LECTURES TERMINÉES (Phase 3.2)
    // =====================================================================================

    /**
     * Marque la lecture en cours d'un utilisateur comme terminée.
     * Cette opération est atomique : elle supprime la lecture active, ajoute le livre aux lectures terminées
     * et incrémente le compteur `booksReadCount` de l'utilisateur, le tout via une transaction Firestore.
     * @param userId L'ID de l'utilisateur concerné.
     * @param activeReadingDetails Les détails de la lecture active qui va être marquée comme terminée.
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit>

    /**
     * Supprime une lecture terminée de la liste d'un utilisateur et décrémente `booksReadCount`.
     * @param userId L'ID de l'utilisateur concerné.
     * @param bookId L'ID du livre terminé à supprimer.
     * @return Un Resource.Success(Unit) en cas de succès, ou Resource.Error en cas d'échec.
     */
    suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit>

    /**
     * Récupère un flux de lectures terminées pour un utilisateur donné.
     * Les lectures sont triées par date de complétion (les plus récentes en premier).
     * @param userId L'ID de l'utilisateur dont les lectures terminées sont à récupérer.
     * @return Un Flow de Resource<List<CompletedReading>>.
     */
    fun getCompletedReadings(userId: String): Flow<Resource<List<CompletedReading>>>

    /**
     * NOUVELLE MÉTHODE : Récupère les détails d'une seule lecture terminée.
     * @param userId L'ID de l'utilisateur propriétaire de la lecture.
     * @param bookId L'ID du livre (et du document CompletedReading).
     * @return Un Flow de Resource<CompletedReading?> contenant les détails de la lecture, ou null si elle n'existe pas.
     */
    fun getCompletedReadingDetail(userId: String, bookId: String): Flow<Resource<CompletedReading?>>
}