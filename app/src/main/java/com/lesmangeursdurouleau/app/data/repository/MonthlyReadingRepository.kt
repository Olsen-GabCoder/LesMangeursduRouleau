package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface MonthlyReadingRepository {
    fun getMonthlyReadings(year: Int, month: Int): Flow<Resource<List<MonthlyReading>>>
    fun getAllMonthlyReadings(): Flow<Resource<List<MonthlyReading>>> // Pour les filtres globaux
    // MAINTAINED: Le MonthlyReading peut être null dans le Resource.Success si non trouvé
    fun getMonthlyReadingById(readingId: String): Flow<Resource<MonthlyReading?>>
    suspend fun addMonthlyReading(reading: MonthlyReading): Resource<Unit>
    suspend fun updateMonthlyReading(reading: MonthlyReading): Resource<Unit>
    // suspend fun deleteMonthlyReading(readingId: String): Resource<Unit> // À ajouter si nécessaire
}