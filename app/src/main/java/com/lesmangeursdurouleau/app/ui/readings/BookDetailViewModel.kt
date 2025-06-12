package com.lesmangeursdurouleau.app.ui.readings.detail

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBookByIdUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val getBookByIdUseCase: GetBookByIdUseCase
) : ViewModel() {

    // MODIFIÉ : Le type de retour indique que le Book peut être null
    private val _bookDetails = MutableLiveData<Resource<Book?>>()
    val bookDetails: LiveData<Resource<Book?>> = _bookDetails

    // La suppression de ces lignes est correcte, car Hilt gère l'injection.

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
                    // Le Resource<Book?> est maintenant correctement géré par le type du LiveData
                    _bookDetails.value = resource
                    when(resource) {
                        is Resource.Success -> {
                            if (resource.data != null) {
                                Log.d("BookDetailViewModel", "Book ID $bookId loaded: ${resource.data.title}")
                            } else {
                                Log.d("BookDetailViewModel", "Book ID $bookId not found or data is null.")
                            }
                        }
                        is Resource.Error -> Log.e("BookDetailViewModel", "Error loading book ID $bookId: ${resource.message}")
                        is Resource.Loading -> Log.d("BookDetailViewModel", "Loading book ID $bookId...")
                    }
                }
        }
    }
}