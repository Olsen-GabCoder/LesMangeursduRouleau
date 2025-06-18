package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

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

    // AJOUT: Indicateur pour savoir si le message a été modifié.
    val isEdited: Boolean = false
) {
    // Constructeur sans argument requis par Firestore pour la désérialisation
    constructor() : this(null, "", "", null, emptyMap(), false)
}