package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.Book
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

    companion object {
        private const val TAG = "BookRepositoryImpl" // Ajout du TAG pour les logs
    }

    private val booksCollection = firestore.collection(FirebaseConstants.COLLECTION_BOOKS)

    override fun getAllBooks(): Flow<Resource<List<Book>>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = booksCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening for all books updates", error)
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
                            Log.e(TAG, "Error converting document to Book: ${document.id}", e)
                        }
                    }
                    Log.d(TAG, "All Books fetched: ${books.size}")
                    trySend(Resource.Success(books))
                } else {
                    Log.d(TAG, "getAllBooks snapshot is null")
                    trySend(Resource.Success(emptyList()))
                }
            }
        awaitClose {
            Log.d(TAG, "Closing all books listener.")
            listenerRegistration.remove()
        }
    }

    // MODIFIÉ : Signature de retour est maintenant Flow<Resource<Book?>>
    override fun getBookById(bookId: String): Flow<Resource<Book?>> = callbackFlow {
        trySend(Resource.Loading(null)) // Envoyer un état de chargement avec des données null
        Log.d(TAG, "Fetching book with ID: $bookId")
        val documentRef = booksCollection.document(bookId)
        val listenerRegistration = documentRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error listening for book ID $bookId updates", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                try {
                    // Tente de convertir le document en objet Book.
                    // toObject peut retourner null si le document est vide ou ne correspond pas au modèle.
                    val book = documentSnapshot.toObject(Book::class.java)?.copy(
                        id = documentSnapshot.id,
                        title = documentSnapshot.getString("title") ?: "",
                        author = documentSnapshot.getString("author") ?: "",
                        coverImageUrl = documentSnapshot.getString("coverImageUrl"),
                        synopsis = documentSnapshot.getString("synopsis")
                    )
                    // Si la conversion est réussie (book n'est pas null), envoie le livre.
                    // Sinon (book est null), envoie Resource.Success(null) pour indiquer l'absence de livre valide.
                    if (book != null) {
                        Log.d(TAG, "Book ID $bookId fetched: ${book.title}")
                        trySend(Resource.Success(book))
                    } else {
                        Log.w(TAG, "Book with ID $bookId exists but data conversion failed or is incomplete (returned null object).")
                        trySend(Resource.Success(null)) // Indique que le livre n'est pas valide/complet
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document to Book for ID $bookId", e)
                    trySend(Resource.Error("Erreur de conversion des données du livre: ${e.localizedMessage}"))
                }
            } else {
                Log.w(TAG, "Book with ID $bookId does not exist.")
                // Si le document n'existe pas, cela n'est pas une erreur, mais l'absence de la ressource.
                trySend(Resource.Success(null))
            }
        }
        awaitClose {
            Log.d(TAG, "Closing listener for book ID $bookId.")
            listenerRegistration.remove()
        }
    }

    override suspend fun addBook(book: Book): Resource<String> {
        return try {
            val bookData = hashMapOf(
                "title" to book.title,
                "author" to book.author,
                "synopsis" to book.synopsis,
                "coverImageUrl" to book.coverImageUrl,
                "proposedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            val documentRef = booksCollection
                .add(bookData)
                .await()
            Log.d(TAG, "Book added successfully with ID: ${documentRef.id}")
            Resource.Success(documentRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding book: ${book.title}", e)
            Resource.Error("Erreur lors de l'ajout du livre: ${e.localizedMessage}")
        }
    }

    override suspend fun updateBook(book: Book): Resource<Unit> {
        if (book.id.isBlank()) {
            return Resource.Error("L'ID du livre est requis pour la mise à jour.")
        }
        return try {
            val bookData = mapOf(
                "title" to book.title,
                "author" to book.author,
                "synopsis" to book.synopsis,
                "coverImageUrl" to book.coverImageUrl,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            booksCollection.document(book.id)
                .update(bookData)
                .await()
            Log.d(TAG, "Book ID ${book.id} updated successfully: ${book.title}")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating book ${book.id}: $e", e)
            Resource.Error("Erreur lors de la mise à jour du livre: ${e.localizedMessage}")
        }
    }
}