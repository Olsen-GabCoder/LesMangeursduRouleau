package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Représente un "Like" sur une lecture spécifique (active ou archivée) ou sur un commentaire.
data class Like(
    @DocumentId
    val likeId: String = "", // L'ID du document Firestore (sera l'UID de l'utilisateur qui like)
    val userId: String = "", // L'UID de l'utilisateur qui a donné le "Like"
    val targetUserId: String = "", // L'UID de l'utilisateur dont la lecture/commentaire a été likée (le propriétaire du profil public)
    val bookId: String = "", // NOUVEAU: L'ID du livre auquel ce like est associé. Remplace 'readingId'.
    val commentId: String? = null, // L'ID du commentaire si le like concerne un commentaire, null sinon.
    val timestamp: Timestamp = Timestamp.now() // Date et heure du "Like"
)