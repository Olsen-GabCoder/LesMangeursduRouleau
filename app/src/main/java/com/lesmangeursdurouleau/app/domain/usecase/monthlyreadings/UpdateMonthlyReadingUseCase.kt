package com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import java.util.Date
import javax.inject.Inject

class UpdateMonthlyReadingUseCase @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository
    // Note: Pas besoin du BookRepository ici non plus, le ViewModel gère la persistance du livre
) {
    suspend operator fun invoke(
        id: String, // MonthlyReading ID
        bookId: String, // ID du livre déjà géré par le ViewModel
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisStatus: String,
        analysisMeetingLink: String?,
        debateDate: Date,
        debateStatus: String,
        debateMeetingLink: String?,
        customDescription: String?
        // Note: Le livre (titre, auteur, etc.) est géré DIRECTEMENT par le ViewModel avant d'appeler ce Use Case,
        // donc nous n'avons plus besoin de le passer ici. Ce Use Case reçoit juste le bookId final.
    ): Resource<Unit> {
        if (id.isBlank() || bookId.isBlank()) {
            return Resource.Error("L'ID de la lecture et le livre sont obligatoires pour la mise à jour.")
        }
        if (year <= 0 || month !in 1..12) {
            return Resource.Error("La date (année/mois) est invalide.")
        }
        // MODIFIED: analysisDate and debateDate can be null in model, but not in use-case parameters
        if (analysisDate == null || debateDate == null) {
            return Resource.Error("Les dates d'analyse et de débat sont obligatoires.")
        }

        val updatedMonthlyReading = MonthlyReading(
            id = id,
            bookId = bookId, // Utilise l'ID du livre qui a été créé/mis à jour par le ViewModel
            year = year,
            month = month,
            analysisPhase = Phase(
                date = analysisDate,
                status = analysisStatus,
                meetingLink = analysisMeetingLink
            ),
            debatePhase = Phase(
                date = debateDate,
                status = debateStatus,
                meetingLink = debateMeetingLink
            ),
            customDescription = customDescription
        )
        return monthlyReadingRepository.updateMonthlyReading(updatedMonthlyReading)
    }
}