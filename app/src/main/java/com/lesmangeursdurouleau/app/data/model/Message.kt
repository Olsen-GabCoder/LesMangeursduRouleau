// Message.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Message(
    @DocumentId
    val messageId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val text: String = "",
    val timestamp: Date? = null,
    // *** NOUVEAU/RÉINTRODUIT : POUR L'AFFICHAGE DES RÉACTIONS TOTALES ***
    val reactions: Map<String, Int> = emptyMap(), // Map d'emoji à leur compte total (pour l'affichage)
    val userReaction: String? = null // La réaction de l'utilisateur actuel (pour l'affichage)
)