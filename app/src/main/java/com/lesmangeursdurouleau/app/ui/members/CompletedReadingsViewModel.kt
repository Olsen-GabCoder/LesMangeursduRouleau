package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompletedReadingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "CompletedReadingsViewModel"
    }

    private val userId: String = savedStateHandle.get<String>("userId")
        ?: throw IllegalArgumentException("userId est manquant pour CompletedReadingsViewModel")

    private val _completedReadings = MutableStateFlow<Resource<List<CompletedReading>>>(Resource.Loading())
    val completedReadings: StateFlow<Resource<List<CompletedReading>>> = _completedReadings.asStateFlow()

    // NOUVEAU: SharedFlow pour gérer les événements ponctuels comme un Toast de succès/erreur
    private val _deleteStatus = MutableSharedFlow<Resource<Unit>>()
    val deleteStatus: SharedFlow<Resource<Unit>> = _deleteStatus.asSharedFlow()

    init {
        Log.d(TAG, "CompletedReadingsViewModel initialisé pour userId: $userId")
        fetchCompletedReadings()
    }

    private fun fetchCompletedReadings() {
        viewModelScope.launch {
            userRepository.getCompletedReadings(userId)
                .catch { e ->
                    Log.e(TAG, "Erreur lors de la récupération des lectures terminées pour $userId: ${e.message}", e)
                    _completedReadings.value = Resource.Error("Erreur lors du chargement de l'historique: ${e.localizedMessage}")
                }
                .collectLatest { resource ->
                    _completedReadings.value = resource
                    if (resource is Resource.Success) {
                        Log.d(TAG, "Lectures terminées pour $userId chargées avec succès: ${resource.data?.size ?: 0} livres.")
                    }
                }
        }
    }

    // NOUVELLE FONCTION: Pour supprimer une lecture terminée
    fun deleteCompletedReading(bookId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Tentative de suppression de la lecture (bookId: $bookId) pour l'utilisateur $userId")
            val result = userRepository.removeCompletedReading(userId, bookId)
            _deleteStatus.emit(result) // Emet le résultat (succès ou erreur)
        }
    }
}