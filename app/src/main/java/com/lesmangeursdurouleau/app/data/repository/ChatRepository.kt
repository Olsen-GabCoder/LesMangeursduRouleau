package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface ChatRepository {
    fun getGeneralChatMessages(): Flow<Resource<List<Message>>>
    suspend fun sendGeneralChatMessage(message: Message): Resource<Unit>
    suspend fun deleteChatMessage(messageId: String): Resource<Unit>
    // Méthode pour la pagination
    fun getPreviousChatMessages(oldestMessageTimestamp: Date, limit: Long): Flow<Resource<List<Message>>>

    // MODIFIÉ: Méthode pour basculer (ajouter/retirer) une réaction à un message
    suspend fun toggleMessageReaction(messageId: String, reactionEmoji: String, userId: String): Resource<Unit>
}