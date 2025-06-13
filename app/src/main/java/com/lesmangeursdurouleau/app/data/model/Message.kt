// Message.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Message(
    @DocumentId
    val messageId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val text: String = "",
    val timestamp: Date? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val userReaction: String? = null
)