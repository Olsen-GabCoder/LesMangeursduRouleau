package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompletedReadingDetailViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Arguments de navigation
    private val targetUserId: String = savedStateHandle.get<String>("userId")!!
    private val bookId: String = savedStateHandle.get<String>("bookId")!!

    // Note: Pour réutiliser les fonctions existantes, nous aurons besoin de l'ID de l'utilisateur actuel.
    // Pour l'instant, nous le laissons en "TODO", car sa récupération dépend de la couche Auth.
    // Supposons qu'il est disponible pour l'implémentation des fonctions.
    private val currentUserId: String = "TODO-REPLACE-WITH-ACTUAL-CURRENT-USER-ID"

    // --- StateFlows pour exposer les données à l'UI ---

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

    val isReadingLikedByCurrentUser: StateFlow<Resource<Boolean>> =
        userRepository.isLikedByCurrentUser(targetUserId, bookId, currentUserId)
            .stateIn(
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
        viewModelScope.launch {
            userRepository.toggleLikeOnActiveReading(targetUserId, bookId, currentUserId)
        }
    }

    fun toggleLikeOnComment(commentId: String) {
        viewModelScope.launch {
            userRepository.toggleLikeOnComment(targetUserId, bookId, commentId, currentUserId)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            userRepository.deleteCommentOnActiveReading(targetUserId, bookId, commentId)
        }
    }

    fun addComment(commentText: String) {
        // TODO: Implémenter la logique pour créer et ajouter un nouvel objet Comment.
        // Cela nécessitera l'objet User de l'utilisateur actuel pour le nom, l'ID, la photo.
    }

    fun isCommentLikedByCurrentUser(commentId: String): Flow<Resource<Boolean>> {
        return userRepository.isCommentLikedByCurrentUser(targetUserId, bookId, commentId, currentUserId)
    }
}