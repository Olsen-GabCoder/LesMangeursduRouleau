package com.lesmangeursdurouleau.app.data.model // Package correct selon votre chemin

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverImageUrl: String? = null,
    val synopsis: String? = null
)