package com.lesmangeursdurouleau.app.data.model

import java.util.Date

sealed interface ChatItem {
    val id: String
    val timestamp: Date?
}

// Enveloppe pour un message, implémentant ChatItem
data class MessageItem(
    val message: PrivateMessage
) : ChatItem {
    override val id: String
        get() = message.id ?: "temp_${message.timestamp?.time}" // Assure un ID non-nul
    override val timestamp: Date?
        get() = message.timestamp
}

// Classe pour un séparateur de date
data class DateSeparatorItem(
    override val timestamp: Date
) : ChatItem {
    // L'ID est basé sur le jour pour être unique par jour
    override val id: String = "separator_${timestamp.time / (1000 * 60 * 60 * 24)}"
}