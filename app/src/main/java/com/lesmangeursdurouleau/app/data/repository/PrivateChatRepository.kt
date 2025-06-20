package com.lesmangeursdurouleau.app.data.repository

import android.net.Uri
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié à la gestion de la messagerie privée (conversations entre utilisateurs).
 */
interface PrivateChatRepository {

    // ... (toutes les méthodes existantes restent inchangées)

    /**
     * Récupère en temps réel la liste des conversations pour un utilisateur donné.
     * @param userId L'ID de l'utilisateur.
     * @return Un Flow de Resource contenant la liste des conversations.
     */
    fun getUserConversations(userId: String): Flow<Resource<List<Conversation>>>

    /**
     * Crée une nouvelle conversation entre deux utilisateurs si elle n'existe pas,
     * ou retourne l'ID de la conversation existante.
     * @param currentUserId L'ID de l'utilisateur courant.
     * @param targetUserId L'ID de l'autre participant.
     * @return Une Resource contenant l'ID de la conversation.
     */
    suspend fun createOrGetConversation(currentUserId: String, targetUserId: String): Resource<String>

    /**
     * Récupère en temps réel la liste des messages d'une conversation spécifique.
     * @param conversationId L'ID de la conversation.
     * @return Un Flow de Resource contenant la liste des messages privés.
     */
    fun getConversationMessages(conversationId: String): Flow<Resource<List<PrivateMessage>>>

    /**
     * Envoie un message texte dans une conversation.
     * @param conversationId L'ID de la conversation.
     * @param message L'objet PrivateMessage à envoyer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun sendPrivateMessage(conversationId: String, message: PrivateMessage): Resource<Unit>

    /**
     * Uploade une image sur Firebase Storage, puis envoie un message contenant l'URL de l'image.
     * @param conversationId L'ID de la conversation.
     * @param imageUri L'URI locale de l'image à uploader.
     * @param text Le texte optionnel à joindre à l'image.
     * @return Une Resource indiquant le succès ou l'échec de l'opération complète.
     */
    suspend fun sendImageMessage(conversationId: String, imageUri: Uri, text: String? = null): Resource<Unit>

    /**
     * Supprime un message privé d'une conversation.
     * @param conversationId L'ID de la conversation.
     * @param messageId L'ID du message à supprimer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun deletePrivateMessage(conversationId: String, messageId: String): Resource<Unit>

    /**
     * Marque tous les messages d'une conversation comme lus pour un utilisateur spécifique.
     * @param conversationId L'ID de la conversation.
     * @param userId L'ID de l'utilisateur qui lit la conversation.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun markConversationAsRead(conversationId: String, userId: String): Resource<Unit>

    /**
     * Ajoute, modifie ou supprime une réaction emoji sur un message.
     * @param conversationId L'ID de la conversation.
     * @param messageId L'ID du message.
     * @param userId L'ID de l'utilisateur qui réagit.
     * @param emoji L'emoji de la réaction.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun addOrUpdateReaction(conversationId: String, messageId: String, userId: String, emoji: String): Resource<Unit>

    /**
     * Modifie le texte d'un message privé existant.
     * @param conversationId L'ID de la conversation.
     * @param messageId L'ID du message à modifier.
     * @param newText Le nouveau texte du message.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun editPrivateMessage(conversationId: String, messageId: String, newText: String): Resource<Unit>

    /**
     * Met à jour le statut de plusieurs messages à "LU".
     * @param conversationId L'ID de la conversation.
     * @param messageIds La liste des ID de messages à mettre à jour.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateMessagesStatusToRead(conversationId: String, messageIds: List<String>): Resource<Unit>

    // --- NOUVELLE MÉTHODE ---
    /**
     * Met à jour le statut "favori" d'une conversation.
     * @param conversationId L'ID de la conversation à mettre à jour.
     * @param isFavorite Le nouveau statut (true ou false).
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateFavoriteStatus(conversationId: String, isFavorite: Boolean): Resource<Unit>
}