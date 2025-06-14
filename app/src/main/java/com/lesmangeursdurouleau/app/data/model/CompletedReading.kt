package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Modèle de données pour représenter une lecture terminée par un utilisateur.
 * Stocké dans une sous-collection 'completed_readings' sous le document de l'utilisateur.
 *
 * @param bookId L'ID unique du livre (correspondant à un document dans la collection 'books').
 * @param userId L'ID de l'utilisateur qui a terminé ce livre.
 * @param title Le titre du livre.
 * @param author L'auteur du livre.
 * @param coverImageUrl L'URL de l'image de couverture du livre.
 * @param totalPages Le nombre total de pages du livre (nécessaire pour les stats ou si l'on veut savoir le "nombre de pages lues" pour ce livre).
 * @param completionDate La date à laquelle la lecture a été marquée comme terminée. Sera générée par le serveur.
 */
data class CompletedReading(
    var bookId: String = "",
    var userId: String = "",
    var title: String = "",
    var author: String = "",
    var coverImageUrl: String? = null,
    var totalPages: Int = 0,
    @ServerTimestamp
    var completionDate: Date? = null
)