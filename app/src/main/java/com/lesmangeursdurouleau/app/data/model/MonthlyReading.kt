package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class MonthlyReading(
    @get:Exclude val id: String = "", // Firestore document ID
    val bookId: String = "", // Reference to the Book ID
    val year: Int = 0,
    val month: Int = 0, // 1 for January, 12 for December
    val analysisPhase: Phase = Phase(),
    val debatePhase: Phase = Phase(),
    val customDescription: String? = null,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)