package com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.repository.BookRepository // AJOUTÉ : Import du BookRepository
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import java.util.Date
import javax.inject.Inject

class AddMonthlyReadingUseCase @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository,
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(
        bookId: String, // ID du livre déjà géré par le ViewModel
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisMeetingLink: String?,
        debateDate: Date,
        debateMeetingLink: String?,
        customDescription: String?
        // Note: Le livre (titre, auteur, etc.) est géré DIRECTEMENT par le ViewModel avant d'appeler ce Use Case,
        // donc nous n'avons plus besoin de le passer ici. Ce Use Case reçoit juste le bookId final.
        // Si la logique de création/mise à jour de livre devait être ici, alors l'objet Book serait passé.
        // Puisque le ViewModel gère la persistance du livre, ce Use Case n'a besoin que du bookId.
    ): Resource<Unit> {
        if (bookId.isBlank()) {
            return Resource.Error("L'ID du livre est obligatoire pour la lecture mensuelle.")
        }
        if (year <= 0 || month !in 1..12) {
            return Resource.Error("La date (année/mois) est invalide.")
        }
        // MODIFIED: analysisDate and debateDate can be null in model, but not in use-case parameters
        if (analysisDate == null || debateDate == null) {
            return Resource.Error("Les dates d'analyse et de débat sont obligatoires.")
        }

        val monthlyReading = MonthlyReading(
            bookId = bookId, // Utilise l'ID du livre qui a été créé/mis à jour par le ViewModel
            year = year,
            month = month,
            analysisPhase = Phase(
                date = analysisDate,
                status = Phase.STATUS_PLANIFIED,
                meetingLink = analysisMeetingLink
            ),
            debatePhase = Phase(
                date = debateDate,
                status = Phase.STATUS_PLANIFIED,
                meetingLink = debateMeetingLink
            ),
            customDescription = customDescription
        )
        return monthlyReadingRepository.addMonthlyReading(monthlyReading)
    }
}