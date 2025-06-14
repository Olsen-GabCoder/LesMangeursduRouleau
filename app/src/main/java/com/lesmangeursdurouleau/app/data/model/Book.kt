// app/src/main/java/com/lesmangeursdurouleau.app/data/model/Book.kt
package com.lesmangeursdurouleau.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Book(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val coverImageUrl: String? = null,
    val synopsis: String? = null,
    val totalPages: Int = 0
) : Parcelable