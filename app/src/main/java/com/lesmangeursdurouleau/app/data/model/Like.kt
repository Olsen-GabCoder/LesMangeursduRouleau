package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Représente un "Like" sur la lecture active d'un utilisateur ou sur un commentaire spécifique.
data class Like(
    @DocumentId
    val likeId: String = "", // L'ID du document Firestore (sera l'UID de l'utilisateur qui like)
    val userId: String = "", // L'UID de l'utilisateur qui a donné le "Like"
    val targetUserId: String = "", // L'UID de l'utilisateur dont la lecture/commentaire a été likée (le propriétaire du profil public)
    val readingId: String = "activeReading", // L'ID de la lecture likée (pour l'instant, toujours 'activeReading')
    val commentId: String? = null, // NOUVEAU: L'ID du commentaire si le like concerne un commentaire, null sinon.
    val timestamp: Timestamp = Timestamp.now() // Date et heure du "Like"
)