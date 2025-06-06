package com.lesmangeursdurouleau.app.domain.usecase.books

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.repository.BookRepository // MODIFIED: Changed to data.repository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject // NOUVEL IMPORT

class GetBooksUseCase @Inject constructor( // AJOUT de @Inject constructor()
    private val bookRepository: BookRepository // Dépendance à l'interface
) {
    operator fun invoke(): Flow<Resource<List<Book>>> {
        return bookRepository.getAllBooks()
    }
}