package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié à la gestion des activités de lecture des utilisateurs.
 */
interface ReadingRepository {

    /**
     * Récupère en temps réel la lecture en cours d'un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @return Un Flow de Resource contenant les détails de la lecture en cours, ou null si aucune.
     */
    fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>>

    /**
     * Met à jour ou supprime la lecture en cours d'un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param userBookReading L'objet de lecture à enregistrer, ou null pour la supprimer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit>

    /**
     * Ajoute un commentaire sur la lecture d'un autre utilisateur.
     * NOTE: La logique actuelle pointe vers "completed_readings".
     * @param targetUserId L'ID de l'utilisateur dont la lecture est commentée.
     * @param bookId L'ID du livre.
     * @param comment L'objet Commentaire à ajouter.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun addCommentOnActiveReading(targetUserId: String, bookId: String, comment: Comment): Resource<Unit>

    /**
     * Récupère en temps réel les commentaires sur la lecture d'un utilisateur.
     * @param targetUserId L'ID de l'utilisateur.
     * @param bookId L'ID du livre.
     * @return Un Flow de Resource contenant la liste des commentaires.
     */
    fun getCommentsOnActiveReading(targetUserId: String, bookId: String): Flow<Resource<List<Comment>>>

    /**
     * Supprime un commentaire sur la lecture d'un utilisateur.
     * @param targetUserId L'ID de l'utilisateur.
     * @param bookId L'ID du livre.
     * @param commentId L'ID du commentaire à supprimer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun deleteCommentOnActiveReading(targetUserId: String, bookId: String, commentId: String): Resource<Unit>

    /**
     * Ajoute ou supprime un "like" sur la lecture d'un utilisateur.
     * @param targetUserId L'ID de l'utilisateur dont la lecture est likée.
     * @param bookId L'ID du livre.
     * @param currentUserId L'ID de l'utilisateur qui effectue l'action.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun toggleLikeOnActiveReading(targetUserId: String, bookId: String, currentUserId: String): Resource<Unit>

    /**
     * Vérifie en temps réel si l'utilisateur courant a liké une lecture spécifique.
     * @param targetUserId L'ID de l'utilisateur propriétaire de la lecture.
     * @param bookId L'ID du livre.
     * @param currentUserId L'ID de l'utilisateur courant.
     * @return Un Flow de Resource contenant un booléen.
     */
    fun isLikedByCurrentUser(targetUserId: String, bookId: String, currentUserId: String): Flow<Resource<Boolean>>

    /**
     * Récupère en temps réel le nombre de likes sur une lecture.
     * @param targetUserId L'ID de l'utilisateur.
     * @param bookId L'ID du livre.
     * @return Un Flow de Resource contenant le nombre de likes.
     */
    fun getActiveReadingLikesCount(targetUserId: String, bookId: String): Flow<Resource<Int>>

    /**
     * Ajoute ou supprime un "like" sur un commentaire d'une lecture.
     * @param targetUserId L'ID du propriétaire de la lecture.
     * @param bookId L'ID du livre.
     * @param commentId L'ID du commentaire.
     * @param currentUserId L'ID de l'utilisateur qui like.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun toggleLikeOnComment(targetUserId: String, bookId: String, commentId: String, currentUserId: String): Resource<Unit>

    /**
     * Vérifie en temps réel si un commentaire est liké par l'utilisateur courant.
     * @param targetUserId L'ID du propriétaire de la lecture.
     * @param bookId L'ID du livre.
     * @param commentId L'ID du commentaire.
     * @param currentUserId L'ID de l'utilisateur courant.
     * @return Un Flow de Resource contenant un booléen.
     */
    fun isCommentLikedByCurrentUser(targetUserId: String, bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>>

    /**
     * Marque une lecture active comme terminée, la déplaçant dans la collection appropriée.
     * @param userId L'ID de l'utilisateur.
     * @param activeReadingDetails Les détails de la lecture à marquer comme terminée.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit>

    /**
     * Supprime une lecture de la liste des lectures terminées.
     * @param userId L'ID de l'utilisateur.
     * @param bookId L'ID du livre à supprimer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit>

    /**
     * Récupère en temps réel la liste des lectures terminées d'un utilisateur, avec tri.
     * @param userId L'ID de l'utilisateur.
     * @param orderBy Le champ sur lequel trier.
     * @param direction La direction du tri (ascendant/descendant).
     * @return Un Flow de Resource contenant la liste des lectures terminées.
     */
    fun getCompletedReadings(
        userId: String,
        orderBy: String,
        direction: Query.Direction
    ): Flow<Resource<List<CompletedReading>>>

    /**
     * Récupère les détails d'une lecture terminée spécifique.
     * @param userId L'ID de l'utilisateur.
     * @param bookId L'ID du livre.
     * @return Un Flow de Resource contenant les détails de la lecture terminée, ou null.
     */
    fun getCompletedReadingDetail(userId: String, bookId: String): Flow<Resource<CompletedReading?>>
}