package com.lesmangeursdurouleau.app.domain.usecase.books

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// AJOUT DE @Inject constructor
class GetBookByIdUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    // MODIFIÉ : Le type de retour indique que le Book peut être null
    operator fun invoke(bookId: String): Flow<Resource<Book?>> {
        if (bookId.isBlank()) {
            // Gérer le cas d'un ID vide comme un Resource.Error, si c'est la logique désirée.
            // Ou vous pouvez laisser le repository gérer cela et retourner un Resource.Success(null).
            // Pour l'instant, je garde l'appel au repository qui gère déjà l'ID vide.
        }
        return bookRepository.getBookById(bookId)
    }
}