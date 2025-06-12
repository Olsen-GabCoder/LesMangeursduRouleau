package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Phase(
    val date: Date? = null,
    val status: String = "PLANIFIED", 
    val meetingLink: String? = null
) {
    // Constants for phase statuses
    companion object {
        const val STATUS_PLANIFIED = "PLANIFIED"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}