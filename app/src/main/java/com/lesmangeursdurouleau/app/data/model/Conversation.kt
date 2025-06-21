package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Représente une conversation privée entre deux utilisateurs.
 * Conçu avec une dénormalisation des données des participants (nom, photo)
 * pour optimiser les performances d'affichage de la liste des conversations.
 */
data class Conversation(
    @DocumentId
    val id: String? = null,

    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotoUrls: Map<String, String> = emptyMap(),

    val lastMessage: String? = null,

    @ServerTimestamp
    val lastMessageTimestamp: Date? = null,

    val unreadCount: Map<String, Int> = emptyMap(),

    val typingStatus: Map<String, Boolean> = emptyMap(),

    @get:PropertyName("isFavorite")
    var isFavorite: Boolean = false
) {

}