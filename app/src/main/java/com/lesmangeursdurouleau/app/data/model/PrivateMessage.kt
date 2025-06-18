package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Énumération des statuts possibles pour un message.
 * SENT: Le message a été envoyé avec succès au serveur.
 * READ: Le message a été lu par le destinataire.
 */
enum class MessageStatus {
    SENT,
    READ
}

/**
 * Représente un message unique au sein d'une conversation privée.
 */
data class PrivateMessage(
    @DocumentId
    val id: String? = null,

    val senderId: String = "",
    val text: String = "",

    @ServerTimestamp
    val timestamp: Date? = null,

    val reactions: Map<String, String> = emptyMap(),

    val isEdited: Boolean = false,

    // AJOUT: Statut du message (envoyé, lu).
    val status: String = MessageStatus.SENT.name
) {
    // Constructeur sans argument requis par Firestore pour la désérialisation
    constructor() : this(null, "", "", null, emptyMap(), false, MessageStatus.SENT.name)
}