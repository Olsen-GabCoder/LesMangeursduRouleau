// Placez ce fichier dans le répertoire: app/src/main/java/com/lesmangeursdurouleau/app/ui/members/

package com.lesmangeursdurouleau.app.ui.members // CHANGEMENT ICI: Utilisation du package spécifié

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
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

    // L'ID de l'utilisateur dont nous voulons afficher l'historique de lecture
    private val userId: String = savedStateHandle.get<String>("userId")
        ?: throw IllegalArgumentException("userId est manquant pour CompletedReadingsViewModel")

    // StateFlow pour exposer la liste des lectures terminées à l'UI
    private val _completedReadings = MutableStateFlow<Resource<List<CompletedReading>>>(Resource.Loading())
    val completedReadings: StateFlow<Resource<List<CompletedReading>>> = _completedReadings.asStateFlow()

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
}