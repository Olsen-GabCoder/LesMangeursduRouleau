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
                                // Utilise directement les champs du UserBookReading pour les détails du livre,
                                // car le modèle UserBookReading a été enrichi avec ces infos.
                                // S'assurer que Book a les champs nécessaires (totalPages, title, author, coverImageUrl)
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
                                // bookResource sera Resource.Success(Book) ou Resource.Success(null)
                                // puisque nous ne passons plus par bookRepository.getBookById() dans ce flux
                                currentState.copy(isLoading = false, error = null, bookReading = userBookReading, bookDetails = bookResource.data, selectedBook = null)
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
                // Si la sélection est annulée, on efface le selectedBook
                currentState.copy(selectedBook = null)
            } else {
                // Si un livre est sélectionné :
                // Mettre à jour selectedBook.
                // Si le livre sélectionné est le même que le livre existant (bookDetails), on garde bookReading.
                // Sinon (nouveau livre), on vide bookReading pour commencer une "nouvelle" lecture.
                val updatedBookReading = if (currentState.bookDetails?.id == book.id) {
                    currentState.bookReading // On garde la lecture existante si c'est le même livre
                } else {
                    null // C'est un nouveau livre, on part d'une nouvelle lecture pour cet ID
                }
                currentState.copy(selectedBook = book, bookReading = updatedBookReading)
            }
        }
        Log.d(TAG, "setSelectedBook: Livre sélectionné mis à jour: ${book?.title ?: "aucun"}")
    }

    /**
     * Sauvegarde la lecture en cours de l'utilisateur ou la marque comme terminée/réinitialisée.
     * Gère la logique transactionnelle via UserRepository.
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

        val bookToSave = _uiState.value.selectedBook ?: _uiState.value.bookDetails
        if (bookToSave == null || bookToSave.id.isBlank()) {
            _uiState.update { it.copy(error = "Veuillez sélectionner un livre.") }
            sendEvent(EditReadingEvent.ShowToast("Veuillez sélectionner un livre."))
            return
        }
        // Utilise totalPages du formulaire si renseigné, sinon celui du BookToSave.
        // C'est important si l'utilisateur met à jour le total des pages pour un nouveau livre.
        val finalTotalPages = if (totalPages > 0) totalPages else bookToSave.totalPages
        if (finalTotalPages <= 0) {
            _uiState.update { it.copy(error = "Le total des pages doit être supérieur à zéro.") }
            sendEvent(EditReadingEvent.ShowToast("Le total des pages doit être supérieur à zéro."))
            return
        }

        // Validation des pages
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
            val previousActiveReading = _uiState.value.bookReading // État de la lecture avant les modifications de l'utilisateur

            // Indique si le livre est supposé être terminé après cette action
            val isNowCompleted = currentPage == finalTotalPages

            // Indique si le livre était déjà marqué comme "completed" dans le UserBookReading précédent.
            // Ceci est basé sur le champ 'status' interne de UserBookReading, pas sur son existence en tant que CompletedReading.
            val wasPreviousReadingCompletedStatus = previousActiveReading?.status == "completed"

            // Objet UserBookReading pour la lecture active.
            // Notez que 'finishedReadingAt' ne devrait pas être persistant pour une lecture active.
            // Il sera surtout pertinent pour le 'CompletedReading'.
            val newActiveReadingData = UserBookReading(
                bookId = bookToSave.id,
                title = bookToSave.title,
                author = bookToSave.author,
                coverImageUrl = bookToSave.coverImageUrl,
                currentPage = currentPage,
                totalPages = finalTotalPages, // Utilise le total des pages finalisé
                favoriteQuote = favoriteQuote?.takeIf { it.isNotBlank() },
                personalReflection = personalReflection?.takeIf { it.isNotBlank() },
                startedReadingAt = previousActiveReading?.startedReadingAt ?: System.currentTimeMillis(),
                lastPageUpdateAt = System.currentTimeMillis(),
                status = "in_progress", // Une lecture active est TOUJOURS "in_progress"
                finishedReadingAt = null // Une lecture active n'est JAMAIS "finished"
            )

            val result: Resource<Unit>

            when {
                // CAS 1: Une lecture est marquée comme TERMINÉE (était active/nouvelle, est maintenant à 100%)
                isNowCompleted && !wasPreviousReadingCompletedStatus -> {
                    Log.d(TAG, "saveCurrentReading: CAS 1 - Marquage de la lecture comme terminée.")
                    // On passe le UserBookReading complet pour que le repository puisse créer le CompletedReading
                    val completedReadingData = newActiveReadingData.copy(
                        status = "completed",
                        finishedReadingAt = System.currentTimeMillis() // Marquer la date de fin
                    )
                    result = userRepository.markActiveReadingAsCompleted(currentUserId!!, completedReadingData)
                }
                // CAS 2: Une lecture (précédemment marquée comme terminée dans le UIState) est remise EN COURS
                // (N'est plus à 100% ET était marquée comme 'completed' dans son statut interne)
                !isNowCompleted && wasPreviousReadingCompletedStatus -> {
                    Log.d(TAG, "saveCurrentReading: CAS 2 - Remise en cours d'une lecture précédemment terminée.")
                    // Tenter de la supprimer des completed_readings (si elle y est)
                    val removeResult = userRepository.removeCompletedReading(currentUserId!!, bookToSave.id)
                    if (removeResult is Resource.Success) {
                        Log.i(TAG, "saveCurrentReading: Livre retiré des lectures terminées. Re-enregistrement comme lecture active.")
                        // Puis la mettre à jour comme lecture active
                        result = userRepository.updateCurrentReading(currentUserId, newActiveReadingData)
                    } else if (removeResult is Resource.Error && removeResult.message?.contains("not found", true) == true) {
                        // Si le livre n'a pas été trouvé dans completed_readings (ex: c'était juste un statut dans activeReading),
                        // on le met simplement à jour comme actif.
                        Log.w(TAG, "saveCurrentReading: Le livre n'était pas trouvé dans completed_readings. Mise à jour simple de l'active reading.")
                        result = userRepository.updateCurrentReading(currentUserId, newActiveReadingData)
                    } else {
                        // Erreur réelle lors de la suppression de la lecture terminée
                        result = Resource.Error(removeResult.message ?: "Erreur lors de la suppression de la lecture terminée.")
                        Log.e(TAG, "saveCurrentReading: Erreur lors de la suppression de la lecture terminée: ${removeResult.message}")
                    }
                }
                // CAS 3: Nouvelle lecture OU mise à jour STANDARD d'une lecture en cours
                // OU mise à jour d'une lecture déjà "terminée" (qui reste à 100% et n'a pas déclenché le CAS 1)
                else -> {
                    Log.d(TAG, "saveCurrentReading: CAS 3 - Nouvelle lecture ou mise à jour standard.")
                    // Pour ce cas, on sauvegarde directement le newActiveReadingData
                    result = userRepository.updateCurrentReading(currentUserId, newActiveReadingData)
                }
            }

            when (result) {
                is Resource.Success -> {
                    // Les mises à jour de _uiState seront gérées par l'observation de getCurrentReading dans loadExistingReading(),
                    // qui sera déclenchée par les modifications Firestore.
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
                is Resource.Loading -> { /* Handled by UI state */ } // Ne devrait pas arriver pour une fonction suspendue
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
        // et il n'y a rien à supprimer dans Firestore.
        if (_uiState.value.selectedBook != null && _uiState.value.bookReading == null) {
            setSelectedBook(null) // Appel direct pour nettoyer l'état de sélection
            sendEvent(EditReadingEvent.ShowToast("Sélection annulée."))
            _uiState.update { it.copy(isLoading = false, isRemoveConfirmed = false) } // S'assurer que le chargement est faux et la confirmation est réinitialisée
            Log.d(TAG, "removeCurrentReading: Sélection non sauvegardée annulée (pas d'appel Firestore).")
            return
        }

        // Sinon, c'est une lecture existante à supprimer (de activeReading)
        _uiState.update { it.copy(isLoading = true, error = null, isSavedSuccessfully = false, isRemoveConfirmed = false) } // Réinitialise la confirmation

        viewModelScope.launch {
            val result = userRepository.updateCurrentReading(currentUserId, null) // Passe null pour supprimer la lecture active
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