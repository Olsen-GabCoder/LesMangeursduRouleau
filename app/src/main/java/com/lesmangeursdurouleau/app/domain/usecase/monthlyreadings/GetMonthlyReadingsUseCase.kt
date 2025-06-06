package com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings

import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf // TRÈS IMPORTANT : assurez-vous que cet import est là
import javax.inject.Inject

class GetMonthlyReadingsUseCase @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository
) {
    // 1. Récupérer les lectures pour un mois/année spécifique
    operator fun invoke(year: Int, month: Int): Flow<Resource<List<MonthlyReading>>> {
        return monthlyReadingRepository.getMonthlyReadings(year, month)
    }

    // 2. Récupérer toutes les lectures (pour les filtres globaux)
    operator fun invoke(): Flow<Resource<List<MonthlyReading>>> {
        return monthlyReadingRepository.getAllMonthlyReadings()
    }

    // 3. NOUVELLE SURCHARGE : Récupérer une seule lecture par son ID (peut être null)
    operator fun invoke(readingId: String): Flow<Resource<MonthlyReading?>> {
        if (readingId.isBlank()) {
            return flowOf(Resource.Error("L'ID de la lecture mensuelle ne peut pas être vide."))
        }
        // MAINTAINED: Utilise la méthode mise à jour du repository qui renvoie MonthlyReading?
        return monthlyReadingRepository.getMonthlyReadingById(readingId)
    }
}