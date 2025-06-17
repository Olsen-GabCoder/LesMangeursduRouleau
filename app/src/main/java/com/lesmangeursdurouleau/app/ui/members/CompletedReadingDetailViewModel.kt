package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompletedReadingDetailViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth, // INJECTION de FirebaseAuth
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Arguments de navigation
    private val targetUserId: String = savedStateHandle.get<String>("userId")!!
    private val bookId: String = savedStateHandle.get<String>("bookId")!!

    // RÉCUPÉRATION DYNAMIQUE de l'utilisateur connecté
    private val currentUser = firebaseAuth.currentUser
    val currentUserId: StateFlow<String?> = MutableStateFlow(currentUser?.uid).asStateFlow()

    val completedReading: StateFlow<Resource<CompletedReading?>> =
        userRepository.getCompletedReadingDetail(targetUserId, bookId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    val comments: StateFlow<Resource<List<Comment>>> =
        userRepository.getCommentsOnActiveReading(targetUserId, bookId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    // Les StateFlows d'interaction dépendent maintenant de currentUserId
    // flatMapLatest permet de relancer la requête si l'état de connexion change (peu probable, mais robuste)
    @OptIn(ExperimentalCoroutinesApi::class)
    val isReadingLikedByCurrentUser: StateFlow<Resource<Boolean>> = currentUserId.flatMapLatest { id ->
        if (id == null) {
            flowOf(Resource.Success(false)) // Non connecté, ne peut pas avoir liké
        } else {
            userRepository.isLikedByCurrentUser(targetUserId, bookId, id)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Resource.Loading()
    )

    val readingLikesCount: StateFlow<Resource<Int>> =
        userRepository.getActiveReadingLikesCount(targetUserId, bookId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    // --- Fonctions pour les interactions UI ---

    fun toggleLikeOnReading() {
        val uid = currentUserId.value ?: return // Ne fait rien si non connecté
        viewModelScope.launch {
            userRepository.toggleLikeOnActiveReading(targetUserId, bookId, uid)
        }
    }

    fun toggleLikeOnComment(commentId: String) {
        val uid = currentUserId.value ?: return // Ne fait rien si non connecté
        viewModelScope.launch {
            userRepository.toggleLikeOnComment(targetUserId, bookId, commentId, uid)
        }
    }

    fun deleteComment(commentId: String) {
        // La permission est vérifiée côté serveur, mais on peut ajouter une vérification client si besoin.
        // L'adaptateur gère déjà la visibilité du bouton.
        viewModelScope.launch {
            userRepository.deleteCommentOnActiveReading(targetUserId, bookId, commentId)
        }
    }

    // IMPLÉMENTATION COMPLÈTE de addComment
    fun addComment(commentText: String) {
        val user = currentUser ?: return // Sécurité: ne fait rien si l'utilisateur est déconnecté

        val comment = Comment(
            userId = user.uid,
            userName = user.displayName ?: "Utilisateur inconnu",
            userPhotoUrl = user.photoUrl?.toString() ?: "",
            commentText = commentText.trim(),
            timestamp = Timestamp.now(),
            bookId = bookId
            // commentId est généré par Firestore
        )

        viewModelScope.launch {
            val result = userRepository.addCommentOnActiveReading(targetUserId, bookId, comment)
            if (result is Resource.Error) {
                Log.e("ViewModel", "Erreur lors de l'ajout du commentaire: ${result.message}")
                // TODO: Exposer l'erreur à l'UI via un SharedFlow/StateFlow pour afficher un Toast/Snackbar
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isCommentLikedByCurrentUser(commentId: String): Flow<Resource<Boolean>> {
        return currentUserId.flatMapLatest { id ->
            if (id == null) {
                flowOf(Resource.Success(false)) // Non connecté, ne peut pas avoir liké
            } else {
                userRepository.isCommentLikedByCurrentUser(targetUserId, bookId, commentId, id)
            }
        }
    }
}