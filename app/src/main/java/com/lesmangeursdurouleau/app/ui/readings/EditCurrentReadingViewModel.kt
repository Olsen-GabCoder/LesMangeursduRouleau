package com.lesmangeursdurouleau.app.ui.readings

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource

import dagger.hilt.android.lifecycle.HiltViewModel
// import kotlinx.coroutines.FlowPreview // Supprimé si non utilisé
// import kotlinx.coroutines.ExperimentalCoroutinesApi // Supprimé si non utilisé
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
    val bookReading: UserBookReading? = null, // La lecture EXISTANTE de l'utilisateur
    val selectedBook: Book? = null,       // Le livre NOUVELLEMENT SÉLECTIONNÉ par l'utilisateur (avant sauvegarde)
    val bookDetails: Book? = null,        // Les détails complets du livre si une lecture existe (associés à bookReading)
    val isSavedSuccessfully: Boolean = false,
    val isRemoveConfirmed: Boolean = false
)

@HiltViewModel
class EditCurrentReadingViewModel @Inject constructor(
    private val userRepository: UserRepository,
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

    /**
     * Charge la lecture en cours existante de l'utilisateur pour pré-remplir le formulaire.
     * Cette méthode récupère aussi les détails complets du livre associé.
     */
    private fun loadExistingReading() {
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Utilisateur non connecté.") }
            sendEvent(EditReadingEvent.ShowToast("Erreur: Utilisateur non connecté."))
            Log.e(TAG, "loadExistingReading: Utilisateur non connecté.")
            return
        }

        viewModelScope.launch {
            userRepository.getCurrentReading(currentUserId)
                .flatMapLatest { readingResource ->
                    when (readingResource) {
                        is Resource.Loading -> {
                            flowOf(Pair(readingResource, Resource.Loading(null))) // Continue le chargement pour les deux
                        }
                        is Resource.Error -> {
                            flowOf(Pair(readingResource, Resource.Error(readingResource.message ?: "Erreur inconnue", null))) // Propage l'erreur pour les deux
                        }
                        is Resource.Success -> {
                            val userBookReading = readingResource.data
                            if (userBookReading != null && userBookReading.bookId.isNotBlank()) {
                                Log.d(TAG, "loadExistingReading: Lecture existante trouvée. Book ID: ${userBookReading.bookId}")
                                bookRepository.getBookById(userBookReading.bookId)
                                    .map { bookResource ->
                                        Pair(readingResource, bookResource) // Retourne la lecture ET le livre
                                    }
                                    .catch { e ->
                                        Log.e(TAG, "loadExistingReading: Erreur lors de la récupération des détails du livre: ${e.message}", e)
                                        emit(Pair(readingResource, Resource.Error("Erreur chargement détails livre: ${e.localizedMessage}", null)))
                                    }
                            } else {
                                Log.d(TAG, "loadExistingReading: Aucune lecture en cours trouvée ou bookId vide.")
                                flowOf(Pair(readingResource, Resource.Success(null))) // Pas de livre à charger
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
                                when (bookResource) {
                                    is Resource.Loading -> currentState.copy(isLoading = true, error = null, bookReading = userBookReading, bookDetails = null, selectedBook = null)
                                    is Resource.Error -> currentState.copy(isLoading = false, error = bookResource.message ?: "Erreur inconnue de livre", bookReading = userBookReading, bookDetails = null, selectedBook = null)
                                    is Resource.Success -> currentState.copy(isLoading = false, error = null, bookReading = userBookReading, bookDetails = bookResource.data, selectedBook = null) // Définit bookDetails ici, et s'assure que selectedBook est null
                                }
                            }
                        }
                    }
                    Log.d(TAG, "loadExistingReading: UI State mis à jour: ${_uiState.value}")
                }
        }
    }

    /**
     * Met à jour le livre sélectionné dans l'état de l'UI.
     * Appelé après qu'un livre a été choisi dans le sélecteur de livres.
     * Ce livre est considéré comme le "nouveau livre" en cours de sélection.
     * @param book Le livre sélectionné (peut être null si on veut annuler la sélection).
     */
    fun setSelectedBook(book: Book?) {
        _uiState.update { currentState ->
            if (book == null) {
                // Si la sélection est annulée, réinitialiser selectedBook et effacer bookReading
                // pour que le formulaire puisse être effacé, MAIS conserver bookDetails
                // si une lecture existante était en cours de modification.
                currentState.copy(selectedBook = null)
            } else {
                // Si un livre est sélectionné :
                // Mettre à jour selectedBook.
                // Effacer bookReading pour indiquer qu'on est en train de créer une nouvelle lecture
                // OU si le livre sélectionné est le même que le livre existant, ne pas toucher à bookReading
                val updatedBookReading = if (currentState.bookReading?.bookId == book.id) {
                    currentState.bookReading // On garde la lecture existante si c'est le même livre
                } else {
                    null // C'est un nouveau livre, on part d'une nouvelle lecture
                }
                currentState.copy(selectedBook = book, bookReading = updatedBookReading)
            }
        }
        Log.d(TAG, "setSelectedBook: Livre sélectionné mis à jour: ${book?.title ?: "aucun"}")
    }

    /**
     * Sauvegarde la lecture en cours de l'utilisateur.
     * @param currentPage La page actuelle lue.
     * @param totalPages Le nombre total de pages du livre.
     * @param favoriteQuote La citation favorite (peut être null).
     * @param personalReflection La réflexion personnelle (peut être null).
     */
    fun saveCurrentReading(currentPage: Int, totalPages: Int, favoriteQuote: String?, personalReflection: String?) {
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Utilisateur non connecté.") }
            sendEvent(EditReadingEvent.ShowToast("Erreur: Utilisateur non connecté."))
            return
        }

        // Déterminer quel livre est concerné par la sauvegarde (le nouvellement sélectionné ou l'existant)
        val bookToSave = _uiState.value.selectedBook ?: _uiState.value.bookDetails
        if (bookToSave == null || bookToSave.id.isBlank()) {
            _uiState.update { it.copy(error = "Veuillez sélectionner un livre.") }
            sendEvent(EditReadingEvent.ShowToast("Veuillez sélectionner un livre."))
            return
        }

        // Validation des pages : currentPage peut être 0 (début), totalPages doit être > 0
        if (currentPage < 0) {
            _uiState.update { it.copy(error = "La page actuelle ne peut pas être négative.") }
            sendEvent(EditReadingEvent.ShowToast("La page actuelle ne peut pas être négative."))
            return
        }
        if (totalPages <= 0) {
            _uiState.update { it.copy(error = "Le total des pages doit être supérieur à zéro.") }
            sendEvent(EditReadingEvent.ShowToast("Le total des pages doit être supérieur à zéro."))
            return
        }
        if (currentPage > totalPages) {
            _uiState.update { it.copy(error = "La page actuelle ne peut pas dépasser le total des pages.") }
            sendEvent(EditReadingEvent.ShowToast("La page actuelle ne peut pas dépasser le total des pages."))
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, isSavedSuccessfully = false) }

        // Utilise la lecture existante si elle est présente, sinon crée une nouvelle lecture
        val currentReading = _uiState.value.bookReading ?: UserBookReading(bookId = bookToSave.id)
        val updatedReading = currentReading.copy(
            bookId = bookToSave.id,
            currentPage = currentPage,
            totalPages = totalPages,
            favoriteQuote = favoriteQuote?.takeIf { it.isNotBlank() },
            personalReflection = personalReflection?.takeIf { it.isNotBlank() },
            lastPageUpdateAt = System.currentTimeMillis()
        )

        // Gérer le statut de la lecture (en_cours, terminée)
        val finalReading = if (currentPage == totalPages && updatedReading.status != "completed") {
            updatedReading.copy(status = "completed", finishedReadingAt = System.currentTimeMillis())
        } else if (currentPage < totalPages && updatedReading.status == "completed") {
            updatedReading.copy(status = "in_progress", finishedReadingAt = null) // Revenir en cours si la page n'est plus la dernière
        } else {
            updatedReading
        }

        viewModelScope.launch {
            when (val result = userRepository.updateCurrentReading(currentUserId, finalReading)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSavedSuccessfully = true, bookReading = finalReading, selectedBook = null, bookDetails = bookToSave) } // Met à jour bookReading et bookDetails, nettoie selectedBook
                    sendEvent(EditReadingEvent.ShowToast("Lecture en cours enregistrée avec succès !"))
                    sendEvent(EditReadingEvent.NavigateBack)
                    Log.i(TAG, "saveCurrentReading: Lecture en cours enregistrée avec succès.")
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message, isSavedSuccessfully = false) }
                    sendEvent(EditReadingEvent.ShowToast("Erreur: ${result.message ?: "Erreur inconnue"}"))
                    Log.e(TAG, "saveCurrentReading: Erreur lors de l'enregistrement: ${result.message}")
                }
                is Resource.Loading -> { /* Handled by UI state */ }
            }
        }
    }

    /**
     * Demande la confirmation de suppression avant de supprimer.
     */
    fun confirmRemoveCurrentReading() {
        _uiState.update { it.copy(isRemoveConfirmed = true) }
        sendEvent(EditReadingEvent.ShowDeleteConfirmationDialog)
        Log.d(TAG, "confirmRemoveCurrentReading: Demande de confirmation de suppression.")
    }

    /**
     * Supprime la lecture en cours ou annule une sélection non sauvegardée.
     * Appelé après confirmation de l'utilisateur ou par le fragment pour annuler la sélection.
     */
    fun removeCurrentReading() {
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Utilisateur non connecté.") }
            sendEvent(EditReadingEvent.ShowToast("Erreur: Utilisateur non connecté."))
            return
        }

        // Si il y a un selectedBook mais pas de bookReading, c'est une nouvelle sélection qu'on veut annuler
        if (_uiState.value.selectedBook != null && _uiState.value.bookReading == null) {
            setSelectedBook(null) // Appel direct pour nettoyer l'état de sélection
            sendEvent(EditReadingEvent.ShowToast("Sélection annulée."))
            _uiState.update { it.copy(isLoading = false, isRemoveConfirmed = false) } // S'assurer que le chargement est faux et la confirmation est réinitialisée
            Log.d(TAG, "removeCurrentReading: Sélection non sauvegardée annulée.")
            return
        }

        // Sinon, c'est une lecture existante à supprimer
        _uiState.update { it.copy(isLoading = true, error = null, isSavedSuccessfully = false, isRemoveConfirmed = false) } // Réinitialise la confirmation

        viewModelScope.launch {
            when (val result = userRepository.updateCurrentReading(currentUserId, null)) { // Passe null pour supprimer
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSavedSuccessfully = true, bookReading = null, selectedBook = null, bookDetails = null) }
                    sendEvent(EditReadingEvent.ShowToast("Lecture en cours retirée avec succès."))
                    sendEvent(EditReadingEvent.NavigateBack)
                    Log.i(TAG, "removeCurrentReading: Lecture en cours retirée avec succès.")
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message, isSavedSuccessfully = false) }
                    sendEvent(EditReadingEvent.ShowToast("Erreur lors du retrait: ${result.message ?: "Erreur inconnue"}"))
                    Log.e(TAG, "removeCurrentReading: Erreur lors du retrait: ${result.message}")
                }
                is Resource.Loading -> { /* Handled by UI state */ }
            }
        }
    }

    /**
     * Annule la confirmation de suppression (si l'utilisateur clique sur "Annuler" dans le dialogue).
     */
    fun cancelRemoveConfirmation() {
        _uiState.update { it.copy(isRemoveConfirmed = false) }
        Log.d(TAG, "cancelRemoveConfirmation: Confirmation de suppression annulée.")
    }

    /**
     * Envoie un événement ponctuel à l'interface utilisateur.
     */
    private fun sendEvent(event: EditReadingEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    companion object {
        private const val TAG = "EditReadingViewModel"
    }
}