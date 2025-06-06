package com.lesmangeursdurouleau.app.data.repository // Le package de l'implémentation reste le même

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : BookRepository {

    private val booksCollection = firestore.collection(FirebaseConstants.COLLECTION_BOOKS) // AJOUT: raccourci pour la collection

    override fun getAllBooks(): Flow<Resource<List<Book>>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = booksCollection // Utilise le raccourci
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("BookRepositoryImpl", "Error listening for all books updates", error)
                    // REVERTED: Revenir à la version sans 'throwable'
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val books = mutableListOf<Book>()
                    for (document in snapshot.documents) {
                        try {
                            val book = Book(
                                id = document.id,
                                title = document.getString("title") ?: "",
                                author = document.getString("author") ?: "",
                                coverImageUrl = document.getString("coverImageUrl"),
                                synopsis = document.getString("synopsis")
                            )
                            books.add(book)
                        } catch (e: Exception) {
                            Log.e("BookRepositoryImpl", "Error converting document to Book: ${document.id}", e)
                        }
                    }
                    Log.d("BookRepositoryImpl", "All Books fetched: ${books.size}")
                    trySend(Resource.Success(books))
                } else {
                    Log.d("BookRepositoryImpl", "getAllBooks snapshot is null")
                    trySend(Resource.Success(emptyList()))
                }
            }
        awaitClose {
            Log.d("BookRepositoryImpl", "Closing all books listener.")
            listenerRegistration.remove()
        }
    }

    override fun getBookById(bookId: String): Flow<Resource<Book>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d("BookRepositoryImpl", "Fetching book with ID: $bookId")
        val documentRef = booksCollection.document(bookId) // Utilise le raccourci
        val listenerRegistration = documentRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.w("BookRepositoryImpl", "Error listening for book ID $bookId updates", error)
                // REVERTED: Revenir à la version sans 'throwable'
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                try {
                    val book = documentSnapshot.toObject(Book::class.java)?.copy(
                        id = documentSnapshot.id,
                        title = documentSnapshot.getString("title") ?: "",
                        author = documentSnapshot.getString("author") ?: "",
                        coverImageUrl = documentSnapshot.getString("coverImageUrl"),
                        synopsis = documentSnapshot.getString("synopsis")
                    )
                    if (book != null) {
                        Log.d("BookRepositoryImpl", "Book ID $bookId fetched: ${book.title}")
                        trySend(Resource.Success(book))
                    } else {
                        // Si la conversion échoue mais que le document existe, on peut envoyer une erreur spécifique
                        trySend(Resource.Error("Données de livre invalides pour l'ID $bookId."))
                    }
                } catch (e: Exception) {
                    Log.e("BookRepositoryImpl", "Error converting document to Book for ID $bookId", e)
                    // REVERTED: Revenir à la version sans 'throwable'
                    trySend(Resource.Error("Erreur de conversion des données du livre."))
                }
            } else {
                Log.w("BookRepositoryImpl", "Book with ID $bookId does not exist.")
                // REVERTED: Revenir à la version originale du message (avant que je ne le modifie pour 'throwable')
                trySend(Resource.Error("Livre non trouvé."))
            }
        }
        awaitClose {
            Log.d("BookRepositoryImpl", "Closing listener for book ID $bookId.")
            listenerRegistration.remove()
        }
    }

    // MODIFIÉ : Retourne l'ID du livre créé
    override suspend fun addBook(book: Book): Resource<String> {
        return try {
            val bookData = hashMapOf(
                "title" to book.title,
                "author" to book.author,
                "synopsis" to book.synopsis,
                "coverImageUrl" to book.coverImageUrl,
                "proposedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            val documentRef = booksCollection // Utilise le raccourci
                .add(bookData)
                .await() // Attend la fin de l'opération
            Log.d("BookRepositoryImpl", "Book added successfully with ID: ${documentRef.id}")
            Resource.Success(documentRef.id) // Retourne l'ID
        } catch (e: Exception) {
            Log.e("BookRepositoryImpl", "Error adding book: ${book.title}", e)
            // REVERTED: Revenir à la version sans 'throwable'
            Resource.Error("Erreur lors de l'ajout du livre: ${e.localizedMessage}")
        }
    }

    // NOUVEAU : Méthode pour mettre à jour un livre
    override suspend fun updateBook(book: Book): Resource<Unit> {
        if (book.id.isBlank()) {
            return Resource.Error("L'ID du livre est requis pour la mise à jour.")
        }
        return try {
            val bookData = mapOf( // Utilise mapOf pour les champs à mettre à jour
                "title" to book.title,
                "author" to book.author,
                "synopsis" to book.synopsis,
                "coverImageUrl" to book.coverImageUrl,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp() // Ajoute un timestamp de mise à jour
            )

            booksCollection.document(book.id) // Utilise le raccourci
                .update(bookData)
                .await()
            Log.d("BookRepositoryImpl", "Book ID ${book.id} updated successfully: ${book.title}")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e("BookRepositoryImpl", "Error updating book ${book.id}: $e", e)
            // REVERTED: Revenir à la version sans 'throwable'
            Resource.Error("Erreur lors de la mise à jour du livre: ${e.localizedMessage}")
        }
    }
}