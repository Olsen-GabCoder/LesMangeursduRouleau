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

    // Nouveau champ pour gérer l'état de saisie des participants.
    // La clé est l'ID de l'utilisateur, la valeur est `true` s'il écrit.
    val typingStatus: Map<String, Boolean> = emptyMap(),

    // CORRECTION: Ajout de l'annotation @PropertyName pour résoudre l'ambiguïté du mapping Firestore
    // pour les champs booléens préfixés par "is".
    @get:PropertyName("isFavorite")
    val isFavorite: Boolean = false
) {
    // Constructeur sans argument requis par Firestore
    constructor() : this(
        id = null,
        participantIds = emptyList(),
        participantNames = emptyMap(),
        participantPhotoUrls = emptyMap(),
        lastMessage = null,
        lastMessageTimestamp = null,
        unreadCount = emptyMap(),
        typingStatus = emptyMap(),
        isFavorite = false
    )
}