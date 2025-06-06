package com.lesmangeursdurouleau.app.ui.readings.detail

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBookByIdUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel // NOUVEL IMPORT
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject // NOUVEL IMPORT

@HiltViewModel // AJOUT DE @HiltViewModel
class BookDetailViewModel @Inject constructor( // AJOUT DE @Inject constructor
    private val getBookByIdUseCase: GetBookByIdUseCase // INJECTION DU USE CASE
) : ViewModel() {

    private val _bookDetails = MutableLiveData<Resource<Book>>()
    val bookDetails: LiveData<Resource<Book>> = _bookDetails

    // SUPPRIMER CES LIGNES :
    // private val bookRepository = BookRepositoryImpl()
    // private val getBookByIdUseCase = GetBookByIdUseCase(bookRepository)

    fun loadBookDetails(bookId: String) {
        if (bookId.isBlank()) {
            _bookDetails.value = Resource.Error("ID du livre invalide.")
            Log.w("BookDetailViewModel", "loadBookDetails called with blank bookId.")
            return
        }

        Log.d("BookDetailViewModel", "Loading details for book ID: $bookId")
        viewModelScope.launch {
            getBookByIdUseCase(bookId)
                .catch { e ->
                    Log.e("BookDetailViewModel", "Exception in book detail flow for ID $bookId", e)
                    _bookDetails.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    _bookDetails.value = resource
                    when(resource) {
                        is Resource.Success -> Log.d("BookDetailViewModel", "Book ID $bookId loaded: ${resource.data?.title}")
                        is Resource.Error -> Log.e("BookDetailViewModel", "Error loading book ID $bookId: ${resource.message}")
                        is Resource.Loading -> Log.d("BookDetailViewModel", "Loading book ID $bookId...")
                    }
                }
        }
    }
}