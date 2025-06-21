package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    var uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,
    val createdAt: Long? = null,
    val bio: String? = null,
    val city: String? = null,
    val canEditReadings: Boolean = false,
    val lastPermissionGrantedTimestamp: Long? = null,
    var followersCount: Int = 0,
    var followingCount: Int = 0,
    var booksReadCount: Int = 0,
    // NOUVEAU : Champs pour le statut de présence.
    // L'annotation est essentielle pour que Firestore reconnaisse "isOnline" malgré le préfixe "is".
    @get:PropertyName("isOnline")
    val isOnline: Boolean = false,
    @ServerTimestamp
    val lastSeen: Date? = null
)