package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Représente un commentaire sur la lecture terminée ou active d'un utilisateur.
data class Comment(
    @DocumentId
    val commentId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val targetUserId: String = "",
    val bookId: String = "", // NOUVEAU: Identifiant du livre associé au commentaire
    val commentText: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val likesCount: Int = 0,
    val lastLikeTimestamp: Timestamp? = null
)