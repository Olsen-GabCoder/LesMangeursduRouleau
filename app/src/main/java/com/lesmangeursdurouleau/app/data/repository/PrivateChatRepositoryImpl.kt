package com.lesmangeursdurouleau.app.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.model.MessageStatus
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class PrivateChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService
) : PrivateChatRepository {

    companion object {
        private const val TAG = "PrivateChatRepository"
    }

    private val conversationsCollection = firestore.collection(FirebaseConstants.COLLECTION_CONVERSATIONS)
    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS) // Nécessaire pour createOrGetConversation

    override fun getUserConversations(userId: String): Flow<Resource<List<Conversation>>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "getUserConversations: Récupération des conversations pour l'utilisateur $userId.")

        val listenerRegistration = conversationsCollection
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getUserConversations: Erreur Firestore: ${error.message}", error)
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val conversations = snapshot.toObjects(Conversation::class.java)
                    Log.d(TAG, "getUserConversations: ${conversations.size} conversations trouvées pour $userId.")
                    trySend(Resource.Success(conversations))
                }
            }
        awaitClose {
            Log.d(TAG, "getUserConversations: Fermeture du listener pour $userId.")
            listenerRegistration.remove()
        }
    }

    override suspend fun createOrGetConversation(currentUserId: String, targetUserId: String): Resource<String> {
        return try {
            val participants = listOf(currentUserId, targetUserId).sorted()
            val conversationId = "${participants[0]}_${participants[1]}"
            Log.d(TAG, "createOrGetConversation: ID canonique généré: $conversationId")

            val conversationDocRef = conversationsCollection.document(conversationId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(conversationDocRef)
                if (snapshot.exists()) {
                    Log.d(TAG, "createOrGetConversation: La conversation $conversationId existe déjà.")
                    return@runTransaction
                }

                Log.d(TAG, "createOrGetConversation: La conversation $conversationId n'existe pas. Création en cours.")
                val currentUserDoc = transaction.get(usersCollection.document(currentUserId))
                val targetUserDoc = transaction.get(usersCollection.document(targetUserId))

                val newConversation = Conversation(
                    participantIds = participants,
                    participantNames = mapOf(
                        currentUserId to (currentUserDoc.getString("username") ?: ""),
                        targetUserId to (targetUserDoc.getString("username") ?: "")
                    ),
                    participantPhotoUrls = mapOf(
                        currentUserId to (currentUserDoc.getString("profilePictureUrl") ?: ""),
                        targetUserId to (targetUserDoc.getString("profilePictureUrl") ?: "")
                    ),
                    unreadCount = mapOf(
                        currentUserId to 0,
                        targetUserId to 0
                    )
                )
                transaction.set(conversationDocRef, newConversation)
                Log.i(TAG, "createOrGetConversation: Nouvelle conversation $conversationId créée avec succès.")
            }.await()

            Resource.Success(conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "createOrGetConversation: Erreur lors de la création/récupération de la conversation: ${e.message}", e)
            Resource.Error("Erreur lors du démarrage de la conversation : ${e.localizedMessage}")
        }
    }

    override fun getConversationMessages(conversationId: String): Flow<Resource<List<PrivateMessage>>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "getConversationMessages: Récupération des messages pour la conversation $conversationId.")

        val messagesCollectionRef = conversationsCollection.document(conversationId)
            .collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)

        val listenerRegistration = messagesCollectionRef
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getConversationMessages: Erreur Firestore: ${error.message}", error)
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(PrivateMessage::class.java)?.copy(id = doc.id)
                    }
                    Log.d(TAG, "getConversationMessages: ${messages.size} messages trouvés pour $conversationId.")
                    trySend(Resource.Success(messages))
                }
            }

        awaitClose {
            Log.d(TAG, "getConversationMessages: Fermeture du listener pour $conversationId.")
            listenerRegistration.remove()
        }
    }

    override suspend fun sendPrivateMessage(conversationId: String, message: PrivateMessage): Resource<Unit> {
        return try {
            val conversationDocRef = conversationsCollection.document(conversationId)
            val newMessageDocRef = conversationDocRef.collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).document()

            val conversationSnapshot = conversationDocRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val participants = conversationSnapshot.get("participantIds") as? List<String>
            if (participants == null) {
                Log.e(TAG, "sendPrivateMessage: Impossible de récupérer les participants de la conversation $conversationId.")
                return Resource.Error("Erreur interne: participants non trouvés.")
            }
            val receiverId = participants.firstOrNull { it != message.senderId }
            if (receiverId == null) {
                Log.e(TAG, "sendPrivateMessage: Impossible d'identifier le destinataire dans la conversation $conversationId.")
                return Resource.Error("Erreur interne: destinataire non trouvé.")
            }

            Log.d(TAG, "sendPrivateMessage: Préparation du batch pour envoyer le message dans $conversationId. Destinataire: $receiverId")

            firestore.runBatch { batch ->
                batch.set(newMessageDocRef, message)
                val conversationUpdate = mapOf(
                    "lastMessage" to message.text,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "unreadCount.$receiverId" to FieldValue.increment(1)
                )
                batch.update(conversationDocRef, conversationUpdate)
            }.await()

            Log.i(TAG, "sendPrivateMessage: Message envoyé et conversation mise à jour avec succès via batch.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendPrivateMessage: Erreur lors de l'envoi du message: ${e.message}", e)
            Resource.Error("Erreur lors de l'envoi du message: ${e.localizedMessage}")
        }
    }

    override suspend fun sendImageMessage(conversationId: String, imageUri: Uri, text: String?): Resource<Unit> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return Resource.Error("Utilisateur non authentifié.")

        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val uploadResult = firebaseStorageService.uploadChatMessageImage(conversationId, fileName, imageUri)

            if (uploadResult is Resource.Error<*>) {
                Log.e(TAG, "sendImageMessage: Échec de l'upload de l'image sur Storage. ${uploadResult.message}")
                return Resource.Error(uploadResult.message ?: "Erreur lors de l'upload de l'image.")
            }

            val imageUrl = (uploadResult as Resource.Success<*>).data as? String
            if (imageUrl.isNullOrBlank()) {
                return Resource.Error("Erreur interne: L'URL de l'image est vide après l'upload.")
            }

            val conversationDocRef = conversationsCollection.document(conversationId)
            val newMessageDocRef = conversationDocRef.collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).document()

            val conversationSnapshot = conversationDocRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val participants = conversationSnapshot.get("participantIds") as? List<String>
                ?: return Resource.Error("Participants non trouvés.")
            val receiverId = participants.firstOrNull { it != currentUserId }
                ?: return Resource.Error("Destinataire non trouvé.")

            val messageData = mapOf(
                "senderId" to currentUserId,
                "text" to text,
                "imageUrl" to imageUrl,
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to MessageStatus.SENT.name,
                "reactions" to emptyMap<String, String>(),
                "isEdited" to false
            )

            val lastMessageSummary = if (!text.isNullOrBlank()) "📷 $text" else "📷 Photo"

            Log.d(TAG, "sendImageMessage: Préparation du batch pour envoyer l'image dans $conversationId.")
            firestore.runBatch { batch ->
                batch.set(newMessageDocRef, messageData)
                val conversationUpdate = mapOf(
                    "lastMessage" to lastMessageSummary,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "unreadCount.$receiverId" to FieldValue.increment(1)
                )
                batch.update(conversationDocRef, conversationUpdate)
            }.await()

            Log.i(TAG, "sendImageMessage: Message image envoyé et conversation mise à jour avec succès.")
            Resource.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "sendImageMessage: Erreur lors de l'envoi du message image: ${e.message}", e)
            Resource.Error("Erreur lors de l'envoi de l'image: ${e.localizedMessage}")
        }
    }

    override suspend fun deletePrivateMessage(conversationId: String, messageId: String): Resource<Unit> {
        return try {
            val conversationDocRef = conversationsCollection.document(conversationId)
            val messagesCollectionRef = conversationDocRef.collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)
            val messageToDeleteDocRef = messagesCollectionRef.document(messageId)

            val lastTwoMessagesQuery = messagesCollectionRef
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(2)

            val lastTwoMessagesSnapshot = lastTwoMessagesQuery.get().await()
            val lastMessagesDocs = lastTwoMessagesSnapshot.documents

            firestore.runTransaction { transaction ->
                Log.d(TAG, "deletePrivateMessage [Transac]: Début pour message $messageId.")

                transaction.delete(messageToDeleteDocRef)

                val latestMessageDoc = lastMessagesDocs.firstOrNull()
                if (latestMessageDoc != null && latestMessageDoc.id == messageId) {
                    val newLatestMessageDoc = if (lastMessagesDocs.size > 1) lastMessagesDocs[1] else null

                    val conversationUpdate: Map<String, Any?>
                    if (newLatestMessageDoc != null) {
                        val newLatestMessage = newLatestMessageDoc.toObject(PrivateMessage::class.java)
                        Log.d(TAG, "deletePrivateMessage [Transac]: Le nouveau dernier message est: ${newLatestMessage?.text ?: newLatestMessage?.imageUrl}")
                        val lastMessageText = when {
                            !newLatestMessage?.text.isNullOrBlank() -> newLatestMessage?.text
                            !newLatestMessage?.imageUrl.isNullOrBlank() -> "📷 Photo"
                            else -> ""
                        }
                        conversationUpdate = mapOf(
                            "lastMessage" to lastMessageText,
                            "lastMessageTimestamp" to newLatestMessage?.timestamp
                        )
                    } else {
                        Log.d(TAG, "deletePrivateMessage [Transac]: La conversation est maintenant vide. Réinitialisation du résumé.")
                        conversationUpdate = mapOf(
                            "lastMessage" to "",
                            "lastMessageTimestamp" to null
                        )
                    }
                    transaction.update(conversationDocRef, conversationUpdate)
                } else {
                    Log.d(TAG, "deletePrivateMessage [Transac]: Le message supprimé n'était pas le dernier. Aucune mise à jour du résumé nécessaire.")
                }
            }.await()

            Log.i(TAG, "deletePrivateMessage: Transaction de suppression de message terminée avec succès.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deletePrivateMessage: Erreur lors de la transaction de suppression du message $messageId: ${e.message}", e)
            Resource.Error("Erreur lors de la suppression du message: ${e.localizedMessage}")
        }
    }

    override suspend fun markConversationAsRead(conversationId: String, userId: String): Resource<Unit> {
        return try {
            Log.d(TAG, "markConversationAsRead: Réinitialisation du compteur pour l'utilisateur $userId dans la conv $conversationId")
            val conversationDocRef = conversationsCollection.document(conversationId)
            val fieldPathToUpdate = "unreadCount.$userId"

            conversationDocRef.update(fieldPathToUpdate, 0).await()

            Log.i(TAG, "markConversationAsRead: Compteur de non-lus réinitialisé avec succès pour $userId.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markConversationAsRead: Erreur lors de la réinitialisation du compteur pour $userId: ${e.message}", e)
            Resource.Error("Erreur lors de la mise à jour du statut de lecture: ${e.localizedMessage}")
        }
    }

    override suspend fun addOrUpdateReaction(conversationId: String, messageId: String, userId: String, emoji: String): Resource<Unit> {
        if (conversationId.isBlank() || messageId.isBlank() || userId.isBlank() || emoji.isBlank()) {
            return Resource.Error("Arguments invalides pour la réaction.")
        }

        return try {
            val messageRef = conversationsCollection.document(conversationId)
                .collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).document(messageId)

            firestore.runTransaction { transaction ->
                val messageSnapshot = transaction.get(messageRef)
                if (!messageSnapshot.exists()) {
                    throw FirebaseFirestoreException("Message non trouvé.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                @Suppress("UNCHECKED_CAST")
                val currentReactions = messageSnapshot.get("reactions") as? Map<String, String> ?: emptyMap()
                val newReactions = currentReactions.toMutableMap()

                if (newReactions[userId] == emoji) {
                    newReactions.remove(userId)
                    Log.d(TAG, "addOrUpdateReaction [Transac]: Réaction retirée pour l'utilisateur $userId.")
                } else {
                    newReactions[userId] = emoji
                    Log.d(TAG, "addOrUpdateReaction [Transac]: Réaction ajoutée/mise à jour pour l'utilisateur $userId avec $emoji.")
                }

                transaction.update(messageRef, "reactions", newReactions)
            }.await()

            Log.i(TAG, "addOrUpdateReaction: Transaction de réaction terminée avec succès pour le message $messageId.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addOrUpdateReaction: Erreur lors de la transaction de réaction pour le message $messageId: ${e.message}", e)
            Resource.Error("Erreur lors de la mise à jour de la réaction: ${e.localizedMessage}")
        }
    }

    override suspend fun editPrivateMessage(conversationId: String, messageId: String, newText: String): Resource<Unit> {
        if (conversationId.isBlank() || messageId.isBlank() || newText.isBlank()) {
            return Resource.Error("Arguments invalides pour la modification du message.")
        }

        return try {
            val conversationDocRef = conversationsCollection.document(conversationId)
            val messagesCollectionRef = conversationDocRef.collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)
            val messageRef = messagesCollectionRef.document(messageId)

            val lastMessageSnapshot = messagesCollectionRef
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            firestore.runBatch { batch ->
                val updates = mapOf(
                    "text" to newText,
                    "isEdited" to true
                )
                batch.update(messageRef, updates)

                if (lastMessageSnapshot.documents.firstOrNull()?.id == messageId) {
                    batch.update(conversationDocRef, "lastMessage", newText)
                    Log.i(TAG, "editPrivateMessage: Le résumé de la conversation sera mis à jour.")
                }
            }.await()

            Log.i(TAG, "editPrivateMessage: Message $messageId modifié avec succès.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "editPrivateMessage: Erreur lors de la modification du message $messageId: ${e.message}", e)
            Resource.Error("Erreur lors de la modification du message: ${e.localizedMessage}")
        }
    }

    override suspend fun updateMessagesStatusToRead(conversationId: String, messageIds: List<String>): Resource<Unit> {
        if (conversationId.isBlank() || messageIds.isEmpty()) {
            Log.w(TAG, "updateMessagesStatusToRead: conversationId ou messageIds est vide. Rien à faire.")
            return Resource.Success(Unit)
        }

        return try {
            Log.d(TAG, "updateMessagesStatusToRead: Démarrage d'un batch pour mettre à jour ${messageIds.size} messages en 'lu' dans la conv $conversationId.")
            val messagesRef = conversationsCollection.document(conversationId)
                .collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)

            val batch = firestore.batch()

            for (messageId in messageIds) {
                val docRef = messagesRef.document(messageId)
                batch.update(docRef, "status", MessageStatus.READ.name)
            }

            batch.commit().await()

            Log.i(TAG, "updateMessagesStatusToRead: Batch de mise à jour des statuts terminé avec succès.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateMessagesStatusToRead: Erreur lors du batch de mise à jour des statuts pour la conv $conversationId: ${e.message}", e)
            Resource.Error("Erreur lors de la mise à jour du statut des messages: ${e.localizedMessage}")
        }
    }

    override suspend fun updateFavoriteStatus(conversationId: String, isFavorite: Boolean): Resource<Unit> {
        return try {
            Log.d(TAG, "updateFavoriteStatus: Mise à jour du statut favori à '$isFavorite' pour la conv $conversationId.")
            conversationsCollection.document(conversationId)
                .update("isFavorite", isFavorite)
                .await()
            Log.i(TAG, "updateFavoriteStatus: Statut favori mis à jour avec succès.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateFavoriteStatus: Erreur lors de la mise à jour du statut favori: ${e.message}", e)
            Resource.Error("Erreur lors de la mise à jour du favori: ${e.localizedMessage}")
        }
    }
}