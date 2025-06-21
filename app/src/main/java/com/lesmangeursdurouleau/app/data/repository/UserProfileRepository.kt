package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié à la gestion du profil et des données de base des utilisateurs.
 */
interface UserProfileRepository {

    /**
     * Met à jour le nom d'utilisateur (pseudo) d'un utilisateur.
     * @param userId L'ID de l'utilisateur à modifier.
     * @param username Le nouveau nom d'utilisateur.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserProfile(userId: String, username: String): Resource<Unit>

    /**
     * Met à jour la photo de profil d'un utilisateur.
     * @param userId L'ID de l'utilisateur à modifier.
     * @param imageData Les données binaires de la nouvelle image.
     * @return Une Resource contenant l'URL de la nouvelle photo de profil.
     */
    suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String>

    /**
     * Récupère en temps réel la liste de tous les utilisateurs de l'application.
     * @return Un Flow de Resource contenant la liste de tous les utilisateurs.
     */
    fun getAllUsers(): Flow<Resource<List<User>>>

    /**
     * Récupère en temps réel les informations d'un utilisateur spécifique par son ID.
     * @param userId L'ID de l'utilisateur à récupérer.
     * @return Un Flow de Resource contenant l'objet User.
     */
    fun getUserById(userId: String): Flow<Resource<User>>

    /**
     * Met à jour le statut "est en train d'écrire" d'un utilisateur (pour le chat général).
     * @param userId L'ID de l'utilisateur.
     * @param isTyping Le statut de frappe (true/false).
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit>

    // NOUVELLE FONCTION
    /**
     * Met à jour le statut de présence (en ligne/hors ligne) et l'horodatage de la dernière vue.
     * @param userId L'ID de l'utilisateur concerné.
     * @param isOnline Le nouveau statut de présence.
     */
    suspend fun updateUserPresence(userId: String, isOnline: Boolean)


    /**
     * Met à jour la biographie d'un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param bio La nouvelle biographie.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserBio(userId: String, bio: String): Resource<Unit>

    /**
     * Met à jour la ville d'un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param city La nouvelle ville.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserCity(userId: String, city: String): Resource<Unit>

    /**
     * Met à jour la permission d'édition des lectures pour un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param canEdit La nouvelle valeur de la permission.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit>

    /**
     * Met à jour le timestamp de la dernière fois que la permission a été accordée.
     * @param userId L'ID de l'utilisateur.
     * @param timestamp Le timestamp en millisecondes, ou null pour le supprimer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit>

    /**
     * Met à jour le jeton FCM (Firebase Cloud Messaging) pour les notifications push.
     * @param userId L'ID de l'utilisateur.
     * @param token Le nouveau jeton FCM.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit>
}