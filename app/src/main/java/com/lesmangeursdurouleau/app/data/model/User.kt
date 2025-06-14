package com.lesmangeursdurouleau.app.data.model

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
    var booksReadCount: Int = 0
)