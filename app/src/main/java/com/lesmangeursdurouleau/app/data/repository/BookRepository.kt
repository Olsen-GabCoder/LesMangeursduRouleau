package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<Resource<List<Book>>>
    // MODIFIÉ : Le type de retour indique que le Book peut être null
    fun getBookById(bookId: String): Flow<Resource<Book?>>
    suspend fun addBook(book: Book): Resource<String>
    suspend fun updateBook(book: Book): Resource<Unit>
}