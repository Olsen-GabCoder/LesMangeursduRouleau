package com.lesmangeursdurouleau.app.ui.readings.addedit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.AddMonthlyReadingUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.GetMonthlyReadingsUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.UpdateMonthlyReadingUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine // NOUVEAU IMPORT : Pour combiner les flows
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AddEditMonthlyReadingViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val getMonthlyReadingsUseCase: GetMonthlyReadingsUseCase,
    private val addMonthlyReadingUseCase: AddMonthlyReadingUseCase,
    private val updateMonthlyReadingUseCase: UpdateMonthlyReadingUseCase,
    private val bookRepository: BookRepository
) : ViewModel() {

    // --- StateFlows pour les données brutes des livres et lectures ---
    private val _allBooks = MutableStateFlow<Resource<List<Book>>>(Resource.Loading())
    val allBooks: StateFlow<Resource<List<Book>>> = _allBooks.asStateFlow()

    // CORRIGÉ : Type générique maintenant MonthlyReading? pour permettre Resource.Success(null)
    private val _loadedMonthlyReading = MutableStateFlow<Resource<MonthlyReading?>>(Resource.Success(null))
    // Cette StateFlow n'est plus directement exposée au Fragment,
    // car le fragment observera le flow combiné.
    // Elle reste interne au ViewModel pour le mécanisme de combinaison.

    // --- NOUVEAUX STATEFLOWS POUR LES CHAMPS DU LIVRE (à maintenir pour les nouvelles saisies) ---
    private val _selectedBookId = MutableStateFlow<String?>(null) // ID du livre existant si sélectionné par autocomplete
    val selectedBookId: StateFlow<String?> = _selectedBookId.asStateFlow()

    private val _bookTitle = MutableStateFlow("")
    val bookTitle: StateFlow<String> = _bookTitle.asStateFlow()

    private val _bookAuthor = MutableStateFlow("")
    val bookAuthor: StateFlow<String> = _bookAuthor.asStateFlow()

    private val _bookSynopsis = MutableStateFlow<String?>(null)
    val bookSynopsis: StateFlow<String?> = _bookSynopsis.asStateFlow()

    private val _bookCoverImageUrl = MutableStateFlow<String?>(null)
    val bookCoverImageUrl: StateFlow<String?> = _bookCoverImageUrl.asStateFlow()
    // --- FIN NOUVEAUX STATEFLOWS POUR LES CHAMPS DU LIVRE ---

    // --- StateFlows pour les champs de la lecture mensuelle ---
    private val _year = MutableStateFlow(0)
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _month = MutableStateFlow(0) // 1-12
    val month: StateFlow<Int> = _month.asStateFlow()

    private val _analysisDate = MutableStateFlow<Date?>(null)
    val analysisDate: StateFlow<Date?> = _analysisDate.asStateFlow()

    private val _debateDate = MutableStateFlow<Date?>(null)
    val debateDate: StateFlow<Date?> = _debateDate.asStateFlow()

    // Statut des phases (pas exposé, car géré lors de la mise à jour)
    internal var analysisStatus: String = Phase.STATUS_PLANIFIED
    internal var debateStatus: String = Phase.STATUS_PLANIFIED

    // CORRIGÉ : Type générique maintenant MonthlyReading? pour permettre Resource.Success(null)
    private val _monthlyReadingAndBookForEdit = MutableStateFlow<Resource<Pair<MonthlyReading?, Book?>>>(Resource.Loading())
    val monthlyReadingAndBookForEdit: StateFlow<Resource<Pair<MonthlyReading?, Book?>>> = _monthlyReadingAndBookForEdit.asStateFlow()

    // --- StateFlow pour le résultat de la sauvegarde ---
    private val _saveResult = MutableStateFlow<Resource<Unit>?>(null)
    val saveResult: StateFlow<Resource<Unit>?> = _saveResult.asStateFlow()

    init {
        loadAllBooks()
        val currentCalendar = Calendar.getInstance()
        _year.value = currentCalendar.get(Calendar.YEAR)
        _month.value = currentCalendar.get(Calendar.MONTH) + 1

        // Initialisation du flow combiné pour le mode édition
        viewModelScope.launch {
            combine(_loadedMonthlyReading, _allBooks) { monthlyReadingResource, allBooksResource ->
                // Si l'un des flows est en Loading, on reste en Loading.
                // Cela s'applique aussi au mode 'add' car _allBooks commencera par Resource.Loading.
                if (monthlyReadingResource is Resource.Loading || allBooksResource is Resource.Loading) {
                    return@combine Resource.Loading()
                }

                // Si une erreur survient dans l'un des flows, on propage l'erreur.
                if (monthlyReadingResource is Resource.Error) {
                    return@combine Resource.Error(monthlyReadingResource.message ?: "Erreur inconnue lors du chargement de la lecture.")
                }
                if (allBooksResource is Resource.Error) {
                    return@combine Resource.Error(allBooksResource.message ?: "Erreur inconnue lors du chargement des livres.")
                }

                // Si les deux sont en Success, on peut combiner les données.
                if (monthlyReadingResource is Resource.Success && allBooksResource is Resource.Success) {
                    val monthlyReading = monthlyReadingResource.data // Peut être null en mode ajout
                    val allBooksList = allBooksResource.data

                    if (allBooksList != null) { // La liste de livres est nécessaire dans tous les cas
                        // Le livre associé à la lecture mensuelle, ou null si la lecture est null (mode ajout)
                        // ou si le livre n'est pas trouvé dans la liste (cas d'erreur)
                        val book = monthlyReading?.let { mr -> allBooksList.find { it.id == mr.bookId } }
                        // Émettre la MonthlyReading (peut être null) et le Book trouvé (peut être null).
                        Log.d("AddEditMonthlyReadingVM", "Combined flow success: MonthlyReading (${monthlyReading != null}) and Books loaded (${allBooksList.size}).")
                        Resource.Success(Pair(monthlyReading, book))
                    } else {
                        // Cas où allBooksList est null après Resource.Success (improbable)
                        Log.w("AddEditMonthlyReadingVM", "Combined flow: allBooksList is null despite Success status.")
                        Resource.Error("La liste de livres n'a pas pu être chargée.")
                    }
                } else {
                    // Cas inattendu (par exemple, si un nouveau type de Resource est introduit)
                    Log.e("AddEditMonthlyReadingVM", "Unexpected state in combined flow.")
                    Resource.Error("État inattendu des ressources de chargement.")
                }
            }
                .catch { e ->
                    Log.e("AddEditMonthlyReadingVM", "Exception in combined flow for edit: ${e.localizedMessage}", e)
                    _monthlyReadingAndBookForEdit.value = Resource.Error("Erreur lors de la préparation des données d'édition: ${e.localizedMessage}")
                }
                .collectLatest { resource ->
                    _monthlyReadingAndBookForEdit.value = resource
                }
        }
    }

    private fun loadAllBooks() {
        viewModelScope.launch {
            getBooksUseCase()
                .catch { e ->
                    Log.e("AddEditMonthlyReadingVM", "Error loading all books for selection", e)
                    _allBooks.value = Resource.Error("Erreur lors du chargement des livres.")
                }
                .collectLatest { resource ->
                    _allBooks.value = resource
                    if (resource is Resource.Success) {
                        Log.d("AddEditMonthlyReadingVM", "Loaded ${resource.data?.size} books.")
                    } else if (resource is Resource.Error) {
                        Log.e("AddEditMonthlyReadingVM", "Failed to load books: ${resource.message}")
                    }
                }
        }
    }

    fun loadMonthlyReading(readingId: String) {
        viewModelScope.launch {
            // Passe en état de chargement SEULEMENT quand une lecture spécifique est demandée
            _loadedMonthlyReading.value = Resource.Loading()
            getMonthlyReadingsUseCase.invoke(readingId)
                .catch { e ->
                    Log.e("AddEditMonthlyReadingVM", "Exception loading monthly reading $readingId", e)
                    _loadedMonthlyReading.value = Resource.Error("Erreur technique: ${e.localizedMessage}")
                }
                .collectLatest { resource ->
                    // L'état de `_loadedMonthlyReading` est mis à jour ici.
                    // Si c'est un succès, `resource.data` pourrait être null si la lecture n'existe pas.
                    _loadedMonthlyReading.value = resource
                    if (resource is Resource.Success) {
                        resource.data?.let { monthlyReading ->
                            _selectedBookId.value = monthlyReading.bookId // Garde l'ID du livre lié
                            _year.value = monthlyReading.year
                            _month.value = monthlyReading.month
                            _analysisDate.value = monthlyReading.analysisPhase.date
                            _debateDate.value = monthlyReading.debatePhase.date
                            // IMPORTANT : Mettre à jour les statuts internes du ViewModel
                            this@AddEditMonthlyReadingViewModel.analysisStatus = monthlyReading.analysisPhase.status
                            this@AddEditMonthlyReadingViewModel.debateStatus = monthlyReading.debatePhase.status

                            // Note: Les champs _bookTitle, _bookAuthor, etc. ne sont plus définis ici directement.
                            // Ils seront définis par le `populateForm` du Fragment une fois que le `monthlyReadingAndBookForEdit` sera prêt.
                        }
                        Log.d("AddEditMonthlyReadingVM", "Monthly reading $readingId loaded successfully into internal StateFlow.")
                    }
                }
        }
    }

    // Setters pour les propriétés du formulaire
    fun setSelectedBookId(id: String?) {
        _selectedBookId.value = id
    }
    fun setBookTitle(title: String) {
        _bookTitle.value = title
    }
    fun setBookAuthor(author: String) {
        _bookAuthor.value = author
    }
    fun setBookSynopsis(synopsis: String?) {
        _bookSynopsis.value = synopsis
    }
    fun setBookCoverImageUrl(url: String?) {
        _bookCoverImageUrl.value = url
    }
    fun setYearMonth(year: Int, month: Int) {
        _year.value = year
        _month.value = month
    }
    fun setAnalysisDate(date: Date?) {
        _analysisDate.value = date
    }
    fun setDebateDate(date: Date?) {
        _debateDate.value = date
    }

    fun addMonthlyReading(
        book: Book, // Objet Book avec les données du formulaire
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisMeetingLink: String?,
        debateDate: Date,
        debateMeetingLink: String?,
        customDescription: String?
    ) {
        viewModelScope.launch {
            _saveResult.value = Resource.Loading()
            try {
                val finalBookId: String

                val existingBookFromAllBooks = (_allBooks.value as? Resource.Success)?.data?.find { it.id == _selectedBookId.value }
                val hasBookChanged = existingBookFromAllBooks == null ||
                        book.title != existingBookFromAllBooks.title ||
                        book.author != existingBookFromAllBooks.author ||
                        book.synopsis != existingBookFromAllBooks.synopsis ||
                        book.coverImageUrl != existingBookFromAllBooks.coverImageUrl

                if (_selectedBookId.value.isNullOrBlank() || hasBookChanged) {
                    val bookToSaveWithId = if (!_selectedBookId.value.isNullOrBlank() && existingBookFromAllBooks != null) {
                        book.copy(id = _selectedBookId.value!!)
                    } else {
                        book.copy(id = "")
                    }

                    val bookSaveResult = if (bookToSaveWithId.id.isNotBlank()) {
                        bookRepository.updateBook(bookToSaveWithId)
                    } else {
                        bookRepository.addBook(bookToSaveWithId)
                    }

                    if (bookSaveResult is Resource.Success) {
                        finalBookId = if (bookSaveResult.data is String) {
                            bookSaveResult.data // addBook retourne l'ID
                        } else {
                            _selectedBookId.value ?: "" // updateBook retourne Unit, donc on garde l'ID original
                        }
                        Log.d("AddEditMonthlyReadingVM", "Book saved/updated with ID: $finalBookId")
                    } else {
                        _saveResult.value = Resource.Error(bookSaveResult.message ?: "Erreur lors de la sauvegarde du livre.")
                        return@launch
                    }
                } else {
                    finalBookId = _selectedBookId.value!!
                    Log.d("AddEditMonthlyReadingVM", "Using existing book ID: $finalBookId (no book update needed)")
                }

                val result = addMonthlyReadingUseCase.invoke(
                    bookId = finalBookId,
                    year = year,
                    month = month,
                    analysisDate = analysisDate,
                    analysisMeetingLink = analysisMeetingLink,
                    debateDate = debateDate,
                    debateMeetingLink = debateMeetingLink,
                    customDescription = customDescription
                )
                _saveResult.value = result
                if (result is Resource.Success) {
                    Log.i("AddEditMonthlyReadingVM", "Monthly reading added successfully.")
                }
            } catch (e: Exception) {
                Log.e("AddEditMonthlyReadingVM", "Exception during add monthly reading process", e)
                _saveResult.value = Resource.Error("Une erreur inattendue est survenue: ${e.localizedMessage}")
            } finally {
                _saveResult.value = null // Réinitialise l'état après le traitement
            }
        }
    }

    fun updateMonthlyReading(
        monthlyReadingId: String,
        book: Book,
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisMeetingLink: String?,
        debateDate: Date,
        debateMeetingLink: String?,
        customDescription: String?
    ) {
        viewModelScope.launch {
            _saveResult.value = Resource.Loading()
            try {
                val finalBookId: String

                val existingBookFromAllBooks = (_allBooks.value as? Resource.Success)?.data?.find { it.id == _selectedBookId.value }
                val hasBookChanged = existingBookFromAllBooks == null ||
                        book.title != existingBookFromAllBooks.title ||
                        book.author != existingBookFromAllBooks.author ||
                        book.synopsis != existingBookFromAllBooks.synopsis ||
                        book.coverImageUrl != existingBookFromAllBooks.coverImageUrl

                if (_selectedBookId.value.isNullOrBlank() || hasBookChanged) {
                    val bookToSaveWithId = if (!_selectedBookId.value.isNullOrBlank() && existingBookFromAllBooks != null) {
                        book.copy(id = _selectedBookId.value!!)
                    } else {
                        book.copy(id = "")
                    }

                    val bookSaveResult = if (bookToSaveWithId.id.isNotBlank()) {
                        bookRepository.updateBook(bookToSaveWithId)
                    } else {
                        bookRepository.addBook(bookToSaveWithId)
                    }

                    if (bookSaveResult is Resource.Success) {
                        finalBookId = if (bookSaveResult.data is String) {
                            bookSaveResult.data
                        } else {
                            _selectedBookId.value ?: ""
                        }
                        Log.d("AddEditMonthlyReadingVM", "Book saved/updated with ID: $finalBookId")
                    } else {
                        _saveResult.value = Resource.Error(bookSaveResult.message ?: "Erreur lors de la sauvegarde du livre.")
                        return@launch
                    }
                } else {
                    finalBookId = _selectedBookId.value!!
                    Log.d("AddEditMonthlyReadingVM", "Using existing book ID: $finalBookId (no book update needed)")
                }

                val result = updateMonthlyReadingUseCase.invoke(
                    id = monthlyReadingId,
                    bookId = finalBookId,
                    year = year,
                    month = month,
                    analysisDate = analysisDate,
                    analysisStatus = this@AddEditMonthlyReadingViewModel.analysisStatus,
                    analysisMeetingLink = analysisMeetingLink,
                    debateDate = debateDate,
                    debateStatus = this@AddEditMonthlyReadingViewModel.debateStatus,
                    debateMeetingLink = debateMeetingLink,
                    customDescription = customDescription
                )
                _saveResult.value = result
                if (result is Resource.Success) {
                    Log.i("AddEditMonthlyReadingVM", "Monthly reading $monthlyReadingId updated successfully.")
                }
            } catch (e: Exception) {
                Log.e("AddEditMonthlyReadingVM", "Exception during update monthly reading process", e)
                _saveResult.value = Resource.Error("Une erreur inattendue est survenue: ${e.localizedMessage}")
            } finally {
                _saveResult.value = null // Réinitialise l'état après le traitement
            }
        }
    }
}