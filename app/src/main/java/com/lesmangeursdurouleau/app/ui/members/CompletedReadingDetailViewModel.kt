package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.CompletedReading
// AJOUT: Import du nouveau repository de lecture
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
// SUPPRESSION: L'ancien import n'est plus nécessaire
// import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompletedReadingDetailViewModel @Inject constructor(
    // MODIFIÉ: Injection de ReadingRepository au lieu de UserRepository
    private val readingRepository: ReadingRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val targetUserId: String = savedStateHandle.get<String>("userId")!!
    private val bookId: String = savedStateHandle.get<String>("bookId")!!
    private val currentUser = firebaseAuth.currentUser
    val currentUserId: StateFlow<String?> = MutableStateFlow(currentUser?.uid).asStateFlow()

    val completedReading: StateFlow<Resource<CompletedReading?>> =
        // MODIFIÉ: Appel sur readingRepository
        readingRepository.getCompletedReadingDetail(targetUserId, bookId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    val comments: StateFlow<Resource<List<Comment>>> =
        // MODIFIÉ: Appel sur readingRepository
        readingRepository.getCommentsOnActiveReading(targetUserId, bookId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val isReadingLikedByCurrentUser: StateFlow<Resource<Boolean>> = currentUserId.flatMapLatest { id ->
        if (id == null) {
            flowOf(Resource.Success(false))
        } else {
            // MODIFIÉ: Appel sur readingRepository
            readingRepository.isLikedByCurrentUser(targetUserId, bookId, id)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Resource.Loading()
    )

    val readingLikesCount: StateFlow<Resource<Int>> =
        // MODIFIÉ: Appel sur readingRepository
        readingRepository.getActiveReadingLikesCount(targetUserId, bookId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    fun toggleLikeOnReading() {
        val uid = currentUserId.value ?: return
        viewModelScope.launch {
            // MODIFIÉ: Appel sur readingRepository
            readingRepository.toggleLikeOnActiveReading(targetUserId, bookId, uid)
        }
    }

    fun toggleLikeOnComment(commentId: String) {
        val uid = currentUserId.value ?: return
        viewModelScope.launch {
            // MODIFIÉ: Appel sur readingRepository
            readingRepository.toggleLikeOnComment(targetUserId, bookId, commentId, uid)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            // MODIFIÉ: Appel sur readingRepository
            readingRepository.deleteCommentOnActiveReading(targetUserId, bookId, commentId)
        }
    }

    fun addComment(commentText: String) {
        val user = currentUser ?: return
        val comment = Comment(
            userId = user.uid,
            userName = user.displayName ?: "Utilisateur inconnu",
            userPhotoUrl = user.photoUrl?.toString() ?: "",
            commentText = commentText.trim(),
            timestamp = Timestamp.now(),
            bookId = bookId
        )
        viewModelScope.launch {
            // MODIFIÉ: Appel sur readingRepository
            val result = readingRepository.addCommentOnActiveReading(targetUserId, bookId, comment)
            if (result is Resource.Error) {
                Log.e("ViewModel", "Erreur lors de l'ajout du commentaire: ${result.message}")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isCommentLikedByCurrentUser(commentId: String): Flow<Resource<Boolean>> {
        return currentUserId.flatMapLatest { id ->
            if (id == null) {
                flowOf(Resource.Success(false))
            } else {
                // MODIFIÉ: Appel sur readingRepository
                readingRepository.isCommentLikedByCurrentUser(targetUserId, bookId, commentId, id)
            }
        }
    }
}