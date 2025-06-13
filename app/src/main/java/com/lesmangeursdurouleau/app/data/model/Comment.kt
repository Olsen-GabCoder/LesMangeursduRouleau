// app/src/main/java/com/lesmangeursdurouleau/app/data/model/Comment.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Représente un commentaire sur une lecture.
data class Comment(
    @DocumentId
    val commentId: String = "", // L'ID du document Firestore (généré automatiquement ou par UUID)
    val userId: String = "",    // L'UID de l'utilisateur qui a posté le commentaire
    val userName: String = "",  // Le pseudo de l'utilisateur qui a posté le commentaire (dénormalisé pour affichage)
    val userPhotoUrl: String? = null, // L'URL de la photo de l'utilisateur (dénormalisé)
    val targetUserId: String = "", // L'UID de l'utilisateur dont la lecture est commentée
    val commentText: String = "", // Le contenu du commentaire
    val timestamp: Timestamp = Timestamp.now() // Date et heure de publication
)