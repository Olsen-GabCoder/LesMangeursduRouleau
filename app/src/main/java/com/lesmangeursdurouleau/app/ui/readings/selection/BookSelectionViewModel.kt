package com.lesmangeursdurouleau.app.ui.readings.selection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
// import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookSelectionViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BookSelectionViewModel"
    }

    private val _allBooks = MutableStateFlow<Resource<List<Book>>>(Resource.Loading(null))
    private val _searchQuery = MutableStateFlow("")

    val booksList: StateFlow<Resource<List<Book>>> = _allBooks.combine<Resource<List<Book>>, String, Resource<List<Book>>>(_searchQuery) { allBooksResource, query ->
        when (allBooksResource) {
            is Resource.Loading -> {
                Resource.Loading(null)
            }
            is Resource.Error -> {
                Resource.Error(allBooksResource.message ?: "Erreur inconnue", null)
            }
            is Resource.Success -> {
                val books = allBooksResource.data ?: emptyList()
                if (query.isBlank()) {
                    Resource.Success(books)
                } else {
                    val filteredBooks = books.filter { book ->
                        book.title.contains(query, ignoreCase = true) ||
                                book.author.contains(query, ignoreCase = true)
                    }
                    Resource.Success(filteredBooks)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = Resource.Loading(null)
    )

    init {
        Log.d(TAG, "ViewModel initialisé. Lancement du chargement des livres.")
        loadAllBooks()
    }

    private fun loadAllBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks()
                .catch { e ->
                    Log.e(TAG, "Erreur lors de la collecte du flow getAllBooks", e)
                    _allBooks.value = Resource.Error("Erreur lors du chargement des livres: ${e.localizedMessage}")
                }
                .collectLatest { resource ->
                    Log.d(TAG, "Livres reçus du repository: $resource")
                    _allBooks.value = resource
                }
        }
    }

    fun searchBooks(query: String) {
        Log.d(TAG, "Recherche lancée avec la requête: '$query'")
        _searchQuery.value = query
    }
}