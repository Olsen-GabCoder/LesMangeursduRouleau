package com.lesmangeursdurouleau.app.data.model

import java.util.Date // Ou un type de date plus moderne comme java.time.LocalDateTime

data class ClubEvent(
    val id: String,
    val title: String,
    val date: Date,
    val location: String,
    val description: String? = null,
    val bookToDiscussId: String? = null // Optionnel : ID du livre associé
)