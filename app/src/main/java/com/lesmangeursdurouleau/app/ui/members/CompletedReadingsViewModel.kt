package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// NOUVELLE data class pour représenter l'état du tri
data class SortOptions(
    val orderBy: String = "completionDate",
    val direction: Query.Direction = Query.Direction.DESCENDING
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CompletedReadingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "CompletedReadingsViewModel"
    }

    private val userId: String = savedStateHandle.get<String>("userId")
        ?: throw IllegalArgumentException("userId est manquant pour CompletedReadingsViewModel")

    // NOUVEAU: StateFlow pour gérer l'état actuel du tri
    private val _sortOptions = MutableStateFlow(SortOptions())
    val sortOptions: StateFlow<SortOptions> = _sortOptions.asStateFlow()

    // MODIFIÉ: Ce StateFlow réagit maintenant aux changements de _sortOptions
    val completedReadings: StateFlow<Resource<List<CompletedReading>>> = _sortOptions
        .flatMapLatest { options ->
            userRepository.getCompletedReadings(
                userId = userId,
                orderBy = options.orderBy,
                direction = options.direction
            ).catch { e ->
                Log.e(TAG, "Erreur dans le flow de getCompletedReadings: ${e.message}", e)
                emit(Resource.Error("Erreur de chargement: ${e.localizedMessage}"))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading()
        )

    private val _deleteStatus = MutableSharedFlow<Resource<Unit>>()
    val deleteStatus: SharedFlow<Resource<Unit>> = _deleteStatus.asSharedFlow()

    init {
        Log.d(TAG, "CompletedReadingsViewModel initialisé pour userId: $userId")
    }

    // NOUVELLE FONCTION: Pour que l'UI mette à jour les options de tri
    fun setSortOption(orderBy: String, direction: Query.Direction) {
        _sortOptions.value = SortOptions(orderBy, direction)
    }

    fun deleteCompletedReading(bookId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Tentative de suppression de la lecture (bookId: $bookId) pour l'utilisateur $userId")
            val result = userRepository.removeCompletedReading(userId, bookId)
            _deleteStatus.emit(result)
        }
    }
}