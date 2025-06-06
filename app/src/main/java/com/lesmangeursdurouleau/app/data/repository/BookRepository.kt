package com.lesmangeursdurouleau.app.data.repository // Assurez-vous que le package est bien celui-ci !

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<Resource<List<Book>>>
    fun getBookById(bookId: String): Flow<Resource<Book>>
    suspend fun addBook(book: Book): Resource<String> // MODIFIÉ : Retourne l'ID du livre créé (String)
    suspend fun updateBook(book: Book): Resource<Unit> // NOUVEAU : Pour la mise à jour d'un livre
}