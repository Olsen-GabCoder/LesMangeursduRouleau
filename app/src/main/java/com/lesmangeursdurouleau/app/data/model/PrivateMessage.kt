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

    // MODIFIÉ: Le texte est maintenant optionnel pour permettre les messages avec image seule.
    val text: String? = null,

    // AJOUT: URL de l'image, optionnelle.
    val imageUrl: String? = null,

    @ServerTimestamp
    val timestamp: Date? = null,

    val reactions: Map<String, String> = emptyMap(),

    val isEdited: Boolean = false,

    val status: String = MessageStatus.SENT.name
) {
    // Constructeur sans argument requis par Firestore pour la désérialisation
    // MODIFIÉ: Mise à jour pour inclure les nouveaux champs et la nullabilité.
    constructor() : this(id = null, senderId = "", text = null, imageUrl = null, timestamp = null, reactions = emptyMap(), isEdited = false, status = MessageStatus.SENT.name)
}