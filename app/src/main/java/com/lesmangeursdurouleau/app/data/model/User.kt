package com.lesmangeursdurouleau.app.data.model

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,
    val createdAt: Long? = null,
    val bio: String? = null,
    val city: String? = null, // NOUVEAU CHAMP POUR LA VILLE
    val canEditReadings: Boolean = false, // NOUVEAU : Permission d'édition des lectures
    val lastPermissionGrantedTimestamp: Long? = null // NOUVEAU CHAMP : Timestamp de la dernière permission accordée
)