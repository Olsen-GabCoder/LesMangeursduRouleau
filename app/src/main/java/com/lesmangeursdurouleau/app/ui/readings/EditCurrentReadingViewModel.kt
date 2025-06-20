package com.lesmangeursdurouleau.app.ui.readings

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.repository.BookRepository
// AJOUT: Import du nouveau repository de lecture
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
// SUPPRESSION: L'ancien import n'est plus nécessaire
// import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Événements ponctuels pour l'interface utilisateur
sealed class EditReadingEvent {
    data class ShowToast(val message: String) : EditReadingEvent()
    data object NavigateBack : EditReadingEvent()
    data object ShowDeleteConfirmationDialog : EditReadingEvent()
}

// État de l'interface utilisateur pour l'écran de modification de la lecture en cours
data class EditReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookReading: UserBookReading? = null, // La lecture EXISTANTE de l'utilisateur (telle que chargée initialement)
    val selectedBook: Book? = null,       // Le livre NOUVELLEMENT SÉLECTIONNÉ par l'utilisateur (avant sauvegarde)
    val bookDetails: Book? = null,        // Les détails complets du livre si une lecture existe (associés à bookReading)
    val isSavedSuccessfully: Boolean = false,
    val isRemoveConfirmed: Boolean = false
)

@HiltViewModel
class EditCurrentReadingViewModel @Inject constructor(
    // MODIFIÉ: Remplacement de UserRepository par ReadingRepository
    private val readingRepository: ReadingRepository,
    private val bookRepository: BookRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditReadingUiState(isLoading = true))
    val uiState: StateFlow<EditReadingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditReadingEvent>()
    val events: SharedFlow<EditReadingEvent> = _events.asSharedFlow()

    private val currentUserId: String? = firebaseAuth.currentUser?.uid

    init {
        Log.d(TAG, "EditCurrentReadingViewModel initialisé.")
        loadExistingReading()
    }

    private fun loadExistingReading() {
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Utilisateur non connecté.") }
            sendEvent(EditReadingEvent.ShowToast("Erreur: Utilisateur non connecté."))
            Log.e(TAG, "loadExistingReading: Utilisateur non connecté.")
            return
        }

        viewModelScope.launch {
            // MODIFIÉ: Appel sur readingRepository
            readingRepository.getCurrentReading(currentUserId)
                .flatMapLatest { readingResource ->
                    when (readingResource) {
                        is Resource.Loading -> {
                            flowOf(Pair(readingResource, Resource.Loading(null)))
                        }
                        is Resource.Error -> {
                            flowOf(Pair(readingResource, Resource.Error(readingResource.message ?: "Erreur inconnue", null)))
                        }
                        is Resource.Success -> {
                            val userBookReading = readingResource.data
                            if (userBookReading != null && userBookReading.bookId.isNotBlank()) {
                                Log.d(TAG, "loadExistingReading: Lecture existante trouvée. Book ID: ${userBookReading.bookId}")
                                val bookDetailsFromReading = Book(
                                    id = userBookReading.bookId,
                                    title = userBookReading.title,
                                    author = userBookReading.author,
                                    coverImageUrl = userBookReading.coverImageUrl,
                                    totalPages = userBookReading.totalPages
                                )
                                flowOf(Pair(readingResource, Resource.Success(bookDetailsFromReading)))
                            } else {
                                Log.d(TAG, "loadExistingReading: Aucune lecture en cours trouvée ou bookId vide.")
                                flowOf(Pair(readingResource, Resource.Success(null)))
                            }
                        }
                    }
                }
                .catch { e ->
                    Log.e(TAG, "loadExistingReading: Exception générale du flow de lecture en cours: ${e.message}", e)
                    _uiState.update { it.copy(isLoading = false, error = "Erreur générale: ${e.localizedMessage}") }
                }
                .collectLatest { (readingResource, bookResource) ->
                    _uiState.update { currentState ->
                        when (readingResource) {
                            is Resource.Loading -> currentState.copy(isLoading = true, error = null)
                            is Resource.Error -> currentState.copy(isLoading = false, error = readingResource.message ?: "Erreur inconnue de lecture")
                            is Resource.Success -> {
                                val userBookReading = readingResource.data
                                currentState.copy(isLoading = false, error = null, bookReading = userBookReading, bookDetails = bookResource.data, selectedBook = null)
                            }
                        }
                    }
                    Log.d(TAG, "loadExistingReading: UI State mis à jour: ${_uiState.value}")
                }
        }
    }

    fun setSelectedBook(book: Book?) {
        _uiState.update { currentState ->
            if (book == null) {
                currentState.copy(selectedBook = null)
            } else {
                val updatedBookReading = if (currentState.bookDetails?.id == book.id) {
                    currentState.bookReading
                } else {
                    null
                }
                currentState.copy(selectedBook = book, bookReading = updatedBookReading)
            }
        }
        Log.d(TAG, "setSelectedBook: Livre sélectionné mis à jour: ${book?.title ?: "aucun"}")
    }

    fun saveCurrentReading(currentPage: Int, totalPages: Int, favoriteQuote: String?, personalReflection: String?) {
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Utilisateur non connecté.") }
            sendEvent(EditReadingEvent.ShowToast("Erreur: Utilisateur non connecté."))
            return
        }

        val bookToSave = _uiState.value.selectedBook ?: _uiState.value.bookDetails
        if (bookToSave == null || bookToSave.id.isBlank()) {
            _uiState.update { it.copy(error = "Veuillez sélectionner un livre.") }
            sendEvent(EditReadingEvent.ShowToast("Veuillez sélectionner un livre."))
            return
        }

        val finalTotalPages = if (totalPages > 0) totalPages else bookToSave.totalPages
        if (finalTotalPages <= 0) {
            _uiState.update { it.copy(error = "Le total des pages doit être supérieur à zéro.") }
            sendEvent(EditReadingEvent.ShowToast("Le total des pages doit être supérieur à zéro."))
            return
        }

        if (currentPage < 0) {
            _uiState.update { it.copy(error = "La page actuelle ne peut pas être négative.") }
            sendEvent(EditReadingEvent.ShowToast("La page actuelle ne peut pas être négative."))
            return
        }
        if (currentPage > finalTotalPages) {
            _uiState.update { it.copy(error = "La page actuelle ne peut pas dépasser le total des pages.") }
            sendEvent(EditReadingEvent.ShowToast("La page actuelle ne peut pas dépasser le total des pages."))
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, isSavedSuccessfully = false) }

        viewModelScope.launch {
            val previousActiveReading = _uiState.value.bookReading
            val isNowCompleted = currentPage == finalTotalPages
            val wasPreviousReadingCompletedStatus = previousActiveReading?.status == "completed"

            val newActiveReadingData = UserBookReading(
                bookId = bookToSave.id,
                title = bookToSave.title,
                author = bookToSave.author,
                coverImageUrl = bookToSave.coverImageUrl,
                currentPage = currentPage,
                totalPages = finalTotalPages,
                favoriteQuote = favoriteQuote?.takeIf { it.isNotBlank() },
                personalReflection = personalReflection?.takeIf { it.isNotBlank() },
                startedReadingAt = previousActiveReading?.startedReadingAt ?: System.currentTimeMillis(),
                lastPageUpdateAt = System.currentTimeMillis(),
                status = "in_progress",
                finishedReadingAt = null
            )

            val result: Resource<Unit>

            when {
                isNowCompleted && !wasPreviousReadingCompletedStatus -> {
                    Log.d(TAG, "saveCurrentReading: CAS 1 - Marquage de la lecture comme terminée.")
                    val completedReadingData = newActiveReadingData.copy(status = "completed", finishedReadingAt = System.currentTimeMillis())
                    // MODIFIÉ: Appel sur readingRepository
                    result = readingRepository.markActiveReadingAsCompleted(currentUserId, completedReadingData)
                }
                !isNowCompleted && wasPreviousReadingCompletedStatus -> {
                    Log.d(TAG, "saveCurrentReading: CAS 2 - Remise en cours d'une lecture précédemment terminée.")
                    // MODIFIÉ: Appel sur readingRepository
                    val removeResult = readingRepository.removeCompletedReading(currentUserId, bookToSave.id)
                    if (removeResult is Resource.Success || (removeResult is Resource.Error && removeResult.message?.contains("not found", true) == true)) {
                        Log.i(TAG, "saveCurrentReading: Livre retiré (ou non trouvé) des lectures terminées. Re-enregistrement comme lecture active.")
                        // MODIFIÉ: Appel sur readingRepository
                        result = readingRepository.updateCurrentReading(currentUserId, newActiveReadingData)
                    } else {
                        result = Resource.Error(removeResult.message ?: "Erreur lors de la suppression de la lecture terminée.")
                        Log.e(TAG, "saveCurrentReading: Erreur lors de la suppression de la lecture terminée: ${removeResult.message}")
                    }
                }
                else -> {
                    Log.d(TAG, "saveCurrentReading: CAS 3 - Nouvelle lecture ou mise à jour standard.")
                    // MODIFIÉ: Appel sur readingRepository
                    result = readingRepository.updateCurrentReading(currentUserId, newActiveReadingData)
                }
            }

            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSavedSuccessfully = true, selectedBook = null) }
                    sendEvent(EditReadingEvent.ShowToast("Lecture enregistrée avec succès !"))
                    sendEvent(EditReadingEvent.NavigateBack)
                    Log.i(TAG, "saveCurrentReading: Opération de lecture réussie.")
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message, isSavedSuccessfully = false) }
                    sendEvent(EditReadingEvent.ShowToast("Erreur: ${result.message ?: "Erreur inconnue"}"))
                    Log.e(TAG, "saveCurrentReading: Erreur lors de l'opération de lecture: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun confirmRemoveCurrentReading() {
        _uiState.update { it.copy(isRemoveConfirmed = true) }
        sendEvent(EditReadingEvent.ShowDeleteConfirmationDialog)
        Log.d(TAG, "confirmRemoveCurrentReading: Demande de confirmation de suppression.")
    }

    fun removeCurrentReading() {
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Utilisateur non connecté.") }
            sendEvent(EditReadingEvent.ShowToast("Erreur: Utilisateur non connecté."))
            return
        }

        if (_uiState.value.selectedBook != null && _uiState.value.bookReading == null) {
            setSelectedBook(null)
            sendEvent(EditReadingEvent.ShowToast("Sélection annulée."))
            _uiState.update { it.copy(isLoading = false, isRemoveConfirmed = false) }
            Log.d(TAG, "removeCurrentReading: Sélection non sauvegardée annulée (pas d'appel Firestore).")
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, isSavedSuccessfully = false, isRemoveConfirmed = false) }

        viewModelScope.launch {
            // MODIFIÉ: Appel sur readingRepository
            val result = readingRepository.updateCurrentReading(currentUserId, null)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSavedSuccessfully = true, bookReading = null, selectedBook = null, bookDetails = null) }
                    sendEvent(EditReadingEvent.ShowToast("Lecture en cours retirée avec succès."))
                    sendEvent(EditReadingEvent.NavigateBack)
                    Log.i(TAG, "removeCurrentReading: Lecture en cours retirée avec succès (Firestore).")
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message, isSavedSuccessfully = false) }
                    sendEvent(EditReadingEvent.ShowToast("Erreur lors du retrait: ${result.message ?: "Erreur inconnue"}"))
                    Log.e(TAG, "removeCurrentReading: Erreur lors du retrait: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun cancelRemoveConfirmation() {
        _uiState.update { it.copy(isRemoveConfirmed = false) }
        Log.d(TAG, "cancelRemoveConfirmation: Confirmation de suppression annulée.")
    }

    private fun sendEvent(event: EditReadingEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    companion object {
        private const val TAG = "EditReadingViewModel"
    }
}