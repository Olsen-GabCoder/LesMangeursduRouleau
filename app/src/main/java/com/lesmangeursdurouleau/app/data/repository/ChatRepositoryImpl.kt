package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepositoryImpl"
        const val MESSAGES_INITIAL_LIMIT = 50L
        private const val MESSAGES_PAGINATION_LIMIT = 20L // Nombre de messages à charger par page d'historique
        private const val COLLECTION_REACTIONS = "reactions" // Constante pour la sous-collection des réactions
    }

    override fun getGeneralChatMessages(): Flow<Resource<List<Message>>> = callbackFlow {
        Log.d(TAG, "getGeneralChatMessages (Firestore) appelé.")
        trySend(Resource.Loading())

        val query = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(MESSAGES_INITIAL_LIMIT) // Limite initiale

        Log.d(TAG, "getGeneralChatMessages (Firestore): Requête créée pour la collection ${FirebaseConstants.COLLECTION_GENERAL_CHAT}")

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "getGeneralChatMessages (Firestore): Erreur d'écoute - ${error.localizedMessage}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                Log.d(TAG, "getGeneralChatMessages (Firestore): Snapshot reçu avec ${snapshot.documents.size} documents.")
                val currentUserUid = firebaseAuth.currentUser?.uid // Récupère l'UID de l'utilisateur actuel

                val tempMessagesWithReactions = mutableListOf<Message>()
                val documentsToProcess = snapshot.documents.size

                // Si le snapshot est vide, émettre une liste vide immédiatement
                if (documentsToProcess == 0) {
                    trySend(Resource.Success(emptyList()))
                    return@addSnapshotListener
                }

                var processedCount = 0

                for (document in snapshot.documents) {
                    try {
                        val msg = document.toObject(Message::class.java)
                        if (msg != null) {
                            val messageId = document.id
                            val reactionCollectionRef = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
                                .document(messageId)
                                .collection(COLLECTION_REACTIONS)

                            reactionCollectionRef.get().addOnSuccessListener { reactionsSnapshot ->
                                // Note: La classe Message n'a plus le champ 'reactions' directement
                                // Mais nous pouvons toujours calculer ces valeurs pour un usage côté client
                                val allReactions: MutableMap<String, Int> = mutableMapOf() // Map: emoji -> count
                                var currentUserEmoji: String? = null

                                for (reactionDoc in reactionsSnapshot.documents) {
                                    val emoji = reactionDoc.getString("emoji")
                                    val reactorUserId = reactionDoc.getString("userId")

                                    if (emoji != null) {
                                        allReactions[emoji] = (allReactions[emoji] ?: 0) + 1
                                    }

                                    if (reactorUserId == currentUserUid && emoji != null) {
                                        currentUserEmoji = emoji // Store the current user's reaction
                                    }
                                }
                                // Créez une nouvelle instance de Message en copiant les champs existants
                                // et en ajoutant les champs calculés pour les réactions.
                                val messageWithReactions = msg.copy(
                                    messageId = messageId,
                                    reactions = allReactions,
                                    userReaction = currentUserEmoji // Le champ 'reactions' n'est plus dans le modèle Message
                                )
                                // Si vous avez besoin d'accéder à toutes les réactions dans l'UI/VM,
                                // vous devrez peut-être ajouter une classe de wrapper ou une autre approche.
                                // Pour l'instant, `userReaction` est suffisant selon le modèle.
                                tempMessagesWithReactions.add(messageWithReactions)
                                processedCount++

                                // Vérifier si tous les messages ont été traités
                                if (processedCount == documentsToProcess) {
                                    Log.i(TAG, "getGeneralChatMessages (Firestore): ${tempMessagesWithReactions.size} messages avec réactions traités et prêts à être émis.")
                                    trySend(Resource.Success(tempMessagesWithReactions.sortedBy { it.timestamp }))
                                }

                            }.addOnFailureListener { e ->
                                Log.e(TAG, "getGeneralChatMessages (Firestore): Erreur lors de la récupération des réactions pour message $messageId", e)
                                // En cas d'erreur, ajoutez le message sans les réactions (ou avec null pour userReaction)
                                tempMessagesWithReactions.add(msg.copy(messageId = messageId, userReaction = null))
                                processedCount++

                                if (processedCount == documentsToProcess) {
                                    Log.i(TAG, "getGeneralChatMessages (Firestore): ${tempMessagesWithReactions.size} messages (certains sans réactions) traités et prêts à être émis.")
                                    trySend(Resource.Success(tempMessagesWithReactions.sortedBy { it.timestamp }))
                                }
                            }
                        } else {
                            Log.w(TAG, "getGeneralChatMessages (Firestore): Document ${document.id} converti en Message null.")
                            processedCount++ // Compter aussi les documents nuls pour ne pas bloquer l'émission
                            if (processedCount == documentsToProcess) {
                                Log.i(TAG, "getGeneralChatMessages (Firestore): ${tempMessagesWithReactions.size} messages (certains nuls) traités et prêts à être émis.")
                                trySend(Resource.Success(tempMessagesWithReactions.sortedBy { it.timestamp }))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "getGeneralChatMessages (Firestore): Erreur de conversion du document ${document.id}", e)
                        processedCount++ // Compter aussi les erreurs de conversion
                        if (processedCount == documentsToProcess) {
                            Log.i(TAG, "getGeneralChatMessages (Firestore): ${tempMessagesWithReactions.size} messages (certains en erreur) traités et prêts à être émis.")
                            trySend(Resource.Success(tempMessagesWithReactions.sortedBy { it.timestamp }))
                        }
                    }
                }
            } else {
                Log.d(TAG, "getGeneralChatMessages (Firestore): Snapshot est null, émission d'une liste vide.")
                trySend(Resource.Success(emptyList()))
            }
        }
        awaitClose {
            Log.d(TAG, "getGeneralChatMessages (Firestore): Fermeture du listener Snapshot.")
            listenerRegistration.remove()
        }
    }

    override fun getPreviousChatMessages(oldestMessageTimestamp: Date, limit: Long): Flow<Resource<List<Message>>> = callbackFlow {
        Log.d(TAG, "getPreviousChatMessages (Firestore) appelé. Timestamp pivot: $oldestMessageTimestamp, Limite: $limit")
        trySend(Resource.Loading())

        val query = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .startAfter(oldestMessageTimestamp)
            .limit(limit)

        Log.d(TAG, "getPreviousChatMessages (Firestore): Requête créée.")

        query.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    Log.d(TAG, "getPreviousChatMessages (Firestore): Snapshot reçu avec ${snapshot.documents.size} documents d'historique.")
                    val currentUserUid = firebaseAuth.currentUser?.uid

                    val tempMessagesWithReactions = mutableListOf<Message>()
                    val documentsToProcess = snapshot.documents.size

                    if (documentsToProcess == 0) {
                        trySend(Resource.Success(emptyList()))
                        close()
                        return@addOnSuccessListener
                    }

                    var processedCount = 0

                    for (document in snapshot.documents) {
                        try {
                            val msg = document.toObject(Message::class.java)
                            if (msg != null) {
                                val messageId = document.id
                                val reactionCollectionRef = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
                                    .document(messageId)
                                    .collection(COLLECTION_REACTIONS)

                                reactionCollectionRef.get().addOnSuccessListener { reactionsSnapshot ->
                                    val allReactions: MutableMap<String, Int> = mutableMapOf()
                                    var currentUserEmoji: String? = null

                                    for (reactionDoc in reactionsSnapshot.documents) {
                                        val emoji = reactionDoc.getString("emoji")
                                        val reactorUserId = reactionDoc.getString("userId")

                                        if (emoji != null) {
                                            allReactions[emoji] = (allReactions[emoji] ?: 0) + 1
                                        }

                                        if (reactorUserId == currentUserUid && emoji != null) {
                                            currentUserEmoji = emoji
                                        }
                                    }
                                    val messageWithReactions = msg.copy(
                                        messageId = messageId,
                                        userReaction = currentUserEmoji
                                    )
                                    tempMessagesWithReactions.add(messageWithReactions)
                                    processedCount++

                                    if (processedCount == documentsToProcess) {
                                        Log.i(TAG, "getPreviousChatMessages (Firestore): ${tempMessagesWithReactions.size} messages d'historique avec réactions traités.")
                                        trySend(Resource.Success(tempMessagesWithReactions.sortedBy { it.timestamp }.reversed()))
                                        close()
                                    }
                                }.addOnFailureListener { e ->
                                    Log.e(TAG, "getPreviousChatMessages (Firestore): Erreur lors de la récupération des réactions pour message ${document.id}", e)
                                    tempMessagesWithReactions.add(msg.copy(messageId = messageId, userReaction = null))
                                    processedCount++
                                    if (processedCount == documentsToProcess) {
                                        Log.i(TAG, "getPreviousChatMessages (Firestore): ${tempMessagesWithReactions.size} messages d'historique (certains sans réactions) traités.")
                                        trySend(Resource.Success(tempMessagesWithReactions.sortedBy { it.timestamp }.reversed()))
                                        close()
                                    }
                                }
                            } else {
                                Log.w(TAG, "getPreviousChatMessages (Firestore): Document ${document.id} converti en Message null.")
                                processedCount++
                                if (processedCount == documentsToProcess) {
                                    Log.i(TAG, "getPreviousChatMessages (Firestore): ${tempMessagesWithReactions.size} messages d'historique (certains nuls) traités.")
                                    trySend(Resource.Success(tempMessagesWithReactions.sortedBy { it.timestamp }.reversed()))
                                    close()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "getPreviousChatMessages (Firestore): Erreur de conversion du document ${document.id}", e)
                            processedCount++
                            if (processedCount == documentsToProcess) {
                                Log.i(TAG, "getPreviousChatMessages (Firestore): ${tempMessagesWithReactions.size} messages d'historique (certains en erreur) traités.")
                                trySend(Resource.Success(tempMessagesWithReactions.sortedBy { it.timestamp }.reversed()))
                                close()
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "getPreviousChatMessages (Firestore): Snapshot est null, émission d'une liste vide.")
                    trySend(Resource.Success(emptyList()))
                    close()
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "getPreviousChatMessages (Firestore): Erreur de récupération - ${error.localizedMessage}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
            }
        awaitClose { Log.d(TAG, "getPreviousChatMessages (Firestore): Flow fermé.") }
    }


    override suspend fun sendGeneralChatMessage(message: Message): Resource<Unit> {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "sendGeneralChatMessage (Firestore): Utilisateur non authentifié.")
            return Resource.Error("Utilisateur non authentifié.")
        }
        if (message.text.isBlank()) {
            Log.e(TAG, "sendGeneralChatMessage (Firestore): Le message est vide.")
            return Resource.Error("Le message ne peut pas être vide.")
        }
        // Pour les nouveaux messages, le timestamp est généré par Firestore si on met 'null'
        // et le messageId est aussi généré par Firestore lors de l'appel à .add()
        val messageToSend = message.copy(
            messageId = "", // Firestore générera l'ID
            senderId = currentUser.uid,
            senderUsername = currentUser.displayName ?: "Utilisateur Anonyme",
            timestamp = Date(), // Utiliser la date actuelle pour l'envoi
            userReaction = null // Assurez-vous que les réactions ne sont pas envoyées avec le message principal
        )
        Log.d(TAG, "sendGeneralChatMessage (Firestore): Tentative d'envoi du message: $messageToSend")
        return try {
            firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
                .add(messageToSend)
                .await()
            Log.i(TAG, "sendGeneralChatMessage (Firestore): Message envoyé avec succès à Firestore.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendGeneralChatMessage (Firestore): Erreur d'envoi - ${e.localizedMessage}", e)
            Resource.Error("Erreur lors de l'envoi du message: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteChatMessage(messageId: String): Resource<Unit> {
        if (messageId.isBlank()) {
            Log.e(TAG, "deleteChatMessage: messageId est vide.")
            return Resource.Error("ID de message invalide.")
        }
        Log.d(TAG, "deleteChatMessage: Tentative de suppression du message ID: $messageId")
        return try {
            // Suppression du document message principal
            firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
                .document(messageId)
                .delete()
                .await()

            // Optionnel: Supprimer les réactions associées.
            // Note: Firestore n'a pas de suppression en cascade automatique.
            // Si vous avez de nombreuses réactions, cela pourrait être coûteux en lectures/écritures.
            // Une fonction Cloud Functions pourrait être plus efficace pour gérer cela en arrière-plan.
            val reactionsRef = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
                .document(messageId)
                .collection(COLLECTION_REACTIONS)

            // Récupérer toutes les réactions et les supprimer une par une
            val reactionsSnapshot = reactionsRef.get().await()
            val batch = firestore.batch() // Utiliser un batch pour des suppressions plus efficaces

            if (!reactionsSnapshot.isEmpty) {
                Log.d(TAG, "deleteChatMessage: Suppression de ${reactionsSnapshot.documents.size} réactions pour le message $messageId.")
                for (doc in reactionsSnapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            } else {
                Log.d(TAG, "deleteChatMessage: Aucune réaction à supprimer pour le message $messageId.")
            }


            Log.i(TAG, "deleteChatMessage: Message ID: $messageId et ses réactions (si existantes) supprimés avec succès de Firestore.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteChatMessage: Erreur lors de la suppression du message ID: $messageId - ${e.localizedMessage}", e)
            Resource.Error("Erreur lors de la suppression du message: ${e.localizedMessage}")
        }
    }

    // RENOMMÉ ET CORRIGÉ: Implémentation de toggleMessageReaction
    override suspend fun toggleMessageReaction(messageId: String, reactionEmoji: String, userId: String): Resource<Unit> {
        if (messageId.isBlank() || userId.isBlank() || reactionEmoji.isBlank()) {
            Log.e(TAG, "toggleMessageReaction: Paramètres invalides (messageId, userId ou reactionEmoji vide).")
            return Resource.Error("Paramètres de réaction invalides.")
        }

        val messageRef = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT).document(messageId)
        // Le document de réaction est identifié par l'ID de l'utilisateur
        val reactionDocRef = messageRef.collection(COLLECTION_REACTIONS).document(userId)

        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reactionDocRef)

                if (snapshot.exists() && snapshot.getString("emoji") == reactionEmoji) {
                    // L'utilisateur a déjà réagi avec cet emoji -> retirer la réaction
                    Log.d(TAG, "toggleMessageReaction: Utilisateur $userId a retiré la réaction '$reactionEmoji' sur message $messageId.")
                    transaction.delete(reactionDocRef)
                } else {
                    // L'utilisateur n'a pas encore réagi avec cet emoji ou veut changer sa réaction
                    Log.d(TAG, "toggleMessageReaction: Utilisateur $userId a ajouté/modifié la réaction à '$reactionEmoji' sur message $messageId.")
                    // Le document de réaction contient l'UID de l'utilisateur et l'emoji
                    transaction.set(reactionDocRef, mapOf("userId" to userId, "emoji" to reactionEmoji))
                }
                null // La transaction réussit
            }.await() // Attendre que la transaction soit complète
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleMessageReaction: Erreur lors de la mise à jour de la réaction sur message $messageId - ${e.localizedMessage}", e)
            Resource.Error("Erreur lors de la mise à jour de la réaction: ${e.localizedMessage}")
        }
    }
}