package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
// MODIFIÉ: Import de UserProfileRepository et suppression de UserRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// UI State pour la lecture en cours (pas de changement pour cette étape)
data class CurrentReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookReading: UserBookReading? = null,
    val bookDetails: Book? = null,
    val isOwnedProfile: Boolean = false
)

// Événements ponctuels pour la gestion des commentaires UI
sealed class CommentEvent {
    data class ShowCommentError(val message: String) : CommentEvent()
    object ClearCommentInput : CommentEvent()
    data class CommentDeletedSuccess(val commentId: String) : CommentEvent()
    data class ShowCommentLikeError(val message: String) : CommentEvent()
}

// Événements ponctuels pour la gestion des likes UI (pour la lecture, distinct des commentaires)
sealed class LikeEvent {
    data class ShowLikeError(val message: String) : LikeEvent()
}

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    // MODIFIÉ: Remplacement de UserRepository par UserProfileRepository
    private val userProfileRepository: UserProfileRepository,
    private val bookRepository: BookRepository,
    private val socialRepository: SocialRepository,
    private val readingRepository: ReadingRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PublicProfileViewModel"
    }

    private val _userProfile = MutableLiveData<Resource<User>>()
    val userProfile: LiveData<Resource<User>> = _userProfile

    private val userIdFromArgs: String? = savedStateHandle.get<String>("userId")

    private val _currentUserId = MutableLiveData<String?>(firebaseAuth.currentUser?.uid)
    val currentUserId: LiveData<String?> = _currentUserId

    // Statut de suivi de l'utilisateur courant vers le profil public (A suit B)
    private val _isFollowing = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isFollowing: StateFlow<Resource<Boolean>> = _isFollowing.asStateFlow()

    // Statut de suivi du profil public vers l'utilisateur courant (B suit A)
    private val _isFollowedByTarget = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isFollowedByTarget: StateFlow<Resource<Boolean>> = _isFollowedByTarget.asStateFlow()

    // Statut de suivi mutuel (A suit B ET B suit A)
    private val _isMutualFollow = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isMutualFollow: StateFlow<Resource<Boolean>> = _isMutualFollow.asStateFlow()

    // StateFlow pour la lecture en cours
    private val _currentReadingUiState = MutableStateFlow(CurrentReadingUiState(isLoading = true))
    val currentReadingUiState: StateFlow<CurrentReadingUiState> = _currentReadingUiState.asStateFlow()

    // StateFlow pour stocker le bookId de la lecture active, pour les interactions
    private val _bookIdForInteractions = MutableStateFlow<String?>(null)

    // StateFlow pour les commentaires sur la lecture active du profil cible
    private val _comments = MutableStateFlow<Resource<List<Comment>>>(Resource.Loading())
    val comments: StateFlow<Resource<List<Comment>>> = _comments.asStateFlow()

    // StateFlow pour les informations du profil de l'utilisateur connecté (celui qui commente et like)
    private val _currentUserProfileForCommenting = MutableStateFlow<Resource<User>>(Resource.Loading())
    val currentUserProfileForCommenting: StateFlow<Resource<User>> = _currentUserProfileForCommenting.asStateFlow()

    // SharedFlow pour les événements ponctuels liés aux commentaires (erreurs, effacer input, suppression, like/unlike erreur)
    private val _commentEvents = MutableSharedFlow<CommentEvent>()
    val commentEvents: SharedFlow<CommentEvent> = _commentEvents.asSharedFlow()

    // StateFlow pour indiquer si la lecture active est likée par l'utilisateur courant
    private val _isLikedByCurrentUser = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isLikedByCurrentUser: StateFlow<Resource<Boolean>> = _isLikedByCurrentUser.asStateFlow()

    // StateFlow pour le nombre de likes sur la lecture active
    private val _likesCount = MutableStateFlow<Resource<Int>>(Resource.Loading())
    val likesCount: StateFlow<Resource<Int>> = _likesCount.asStateFlow()

    // SharedFlow pour les événements ponctuels liés aux likes (erreurs) sur la lecture principale
    private val _likeEvents = MutableSharedFlow<LikeEvent>()
    val likeEvents: SharedFlow<LikeEvent> = _likeEvents.asSharedFlow()

    init {
        Log.d(TAG, "ViewModel initialisé. Tentative de récupération de l'userId des arguments.")
        val currentAuthUserId = firebaseAuth.currentUser?.uid

        Log.d(TAG, "DIAGNOSTIC: currentAuthUserId (utilisateur connecté): $currentAuthUserId")
        Log.d(TAG, "DIAGNOSTIC: userIdFromArgs (profil consulté): $userIdFromArgs")

        if (!userIdFromArgs.isNullOrBlank()) {
            val targetUserId = userIdFromArgs
            val isProfileOwner = targetUserId == currentAuthUserId

            Log.d(TAG, "DIAGNOSTIC: isProfileOwner (profil possédé): $isProfileOwner")

            Log.i(TAG, "UserId reçu des arguments: '$targetUserId'. Lancement de fetchUserProfile.")
            fetchUserProfile(targetUserId)

            viewModelScope.launch {
                _currentReadingUiState.value = CurrentReadingUiState(isLoading = true, isOwnedProfile = isProfileOwner)
                readingRepository.getCurrentReading(targetUserId)
                    .flatMapLatest { readingResource ->
                        when (readingResource) {
                            is Resource.Loading -> flowOf(_currentReadingUiState.value.copy(isLoading = true, error = null, bookReading = null, bookDetails = null))
                            is Resource.Error -> flowOf(_currentReadingUiState.value.copy(isLoading = false, error = readingResource.message, bookReading = null, bookDetails = null))
                            is Resource.Success -> {
                                val userBookReading = readingResource.data
                                if (userBookReading != null) {
                                    Log.d(TAG, "Lecture en cours trouvée pour $targetUserId, bookId: ${userBookReading.bookId}. Tentative de récupération des détails du livre.")
                                    bookRepository.getBookById(userBookReading.bookId)
                                        .map { bookResource ->
                                            when (bookResource) {
                                                is Resource.Loading -> _currentReadingUiState.value.copy(isLoading = true, error = null, bookReading = userBookReading, bookDetails = null)
                                                is Resource.Error -> {
                                                    Log.e(TAG, "Erreur lors de la récupération des détails du livre ${userBookReading.bookId}: ${bookResource.message}")
                                                    _currentReadingUiState.value.copy(isLoading = false, error = bookResource.message, bookReading = userBookReading, bookDetails = null)
                                                }
                                                is Resource.Success -> {
                                                    Log.d(TAG, "Détails du livre ${userBookReading.bookId} récupérés avec succès: ${bookResource.data?.title}")
                                                    _currentReadingUiState.value.copy(isLoading = false, error = null, bookReading = userBookReading, bookDetails = bookResource.data)
                                                }
                                            }
                                        }
                                        .catch { e ->
                                            Log.e(TAG, "Exception lors de la récupération des détails du livre: ${e.message}", e)
                                            emit(_currentReadingUiState.value.copy(isLoading = false, error = "Erreur chargement détails livre: ${e.localizedMessage}", bookReading = userBookReading, bookDetails = null))
                                        }
                                } else {
                                    Log.d(TAG, "Aucune lecture en cours trouvée pour l'utilisateur $targetUserId.")
                                    flowOf(_currentReadingUiState.value.copy(isLoading = false, error = null, bookReading = null, bookDetails = null))
                                }
                            }
                        }
                    }
                    .catch { e ->
                        Log.e(TAG, "Exception générale du flow de lecture en cours pour $targetUserId: ${e.message}", e)
                        _currentReadingUiState.value = _currentReadingUiState.value.copy(isLoading = false, error = "Erreur générale lecture en cours: ${e.localizedMessage}", bookReading = null, bookDetails = null)
                    }
                    .collectLatest { uiState -> _currentReadingUiState.value = uiState }
            }

            viewModelScope.launch {
                _currentReadingUiState.map { it.bookReading?.bookId }.collectLatest { bookId ->
                    _bookIdForInteractions.value = bookId
                    Log.d(TAG, "Book ID for interactions updated to: $bookId")
                }
            }

            viewModelScope.launch {
                combine(flowOf(targetUserId), _bookIdForInteractions.filterNotNull()) { targetId, bookId -> Pair(targetId, bookId) }
                    .flatMapLatest { (targetId, bookId) ->
                        Log.d(TAG, "Fetching comments for targetId: $targetId, bookId: $bookId")
                        readingRepository.getCommentsOnActiveReading(targetId, bookId)
                    }
                    .catch { e ->
                        Log.e(TAG, "Erreur lors de la récupération des commentaires pour l'utilisateur $targetUserId: ${e.message}", e)
                        _comments.value = Resource.Error("Erreur lors du chargement des commentaires: ${e.localizedMessage}")
                    }
                    .collectLatest { resource ->
                        _comments.value = resource
                        Log.d(TAG, "Comments for $targetUserId updated: ${resource.data?.size} comments. Status: $resource")
                    }
            }

            if (!currentAuthUserId.isNullOrBlank()) {
                viewModelScope.launch {
                    // MODIFIÉ: Appel sur userProfileRepository
                    userProfileRepository.getUserById(currentAuthUserId)
                        .catch { e ->
                            Log.e(TAG, "Erreur lors de la récupération du profil de l'utilisateur connecté pour les commentaires: ${e.message}", e)
                            _currentUserProfileForCommenting.value = Resource.Error("Impossible de charger vos informations de profil: ${e.localizedMessage}")
                        }
                        .collectLatest { resource ->
                            _currentUserProfileForCommenting.value = resource
                            Log.d(TAG, "Current user profile for commenting updated: ${resource.data?.username}. Status: $resource")
                        }
                }

                viewModelScope.launch {
                    combine(flowOf(targetUserId), _bookIdForInteractions.filterNotNull(), flowOf(currentAuthUserId)) { t, b, c -> Triple(t, b, c) }
                        .flatMapLatest { (targetId, bookId, currentId) ->
                            Log.d(TAG, "Fetching like status for targetId: $targetId, bookId: $bookId, currentId: $currentId")
                            readingRepository.isLikedByCurrentUser(targetId, bookId, currentId)
                        }
                        .catch { e ->
                            Log.e(TAG, "Erreur lors de l'observation du statut de like de l'utilisateur courant sur le profil $targetUserId: ${e.message}", e)
                            _isLikedByCurrentUser.value = Resource.Error("Erreur de statut de like: ${e.localizedMessage}")
                        }
                        .collectLatest { resource ->
                            _isLikedByCurrentUser.value = resource
                            Log.d(TAG, "isLikedByCurrentUser for $targetUserId by $currentAuthUserId: $resource")
                        }
                }
                viewModelScope.launch {
                    combine(flowOf(targetUserId), _bookIdForInteractions.filterNotNull()) { t, b -> Pair(t, b) }
                        .flatMapLatest { (targetId, bookId) ->
                            Log.d(TAG, "Fetching likes count for targetId: $targetId, bookId: $bookId")
                            readingRepository.getActiveReadingLikesCount(targetId, bookId)
                        }
                        .catch { e ->
                            Log.e(TAG, "Erreur lors de l'observation du nombre de likes sur le profil $targetUserId: ${e.message}", e)
                            _likesCount.value = Resource.Error("Erreur de compte de likes: ${e.localizedMessage}")
                        }
                        .collectLatest { resource ->
                            _likesCount.value = resource
                            Log.d(TAG, "Likes count for $targetUserId: $resource")
                        }
                }

            } else {
                _currentUserProfileForCommenting.value = Resource.Error("Utilisateur non connecté. Impossible de charger les infos de profil pour commenter.")
                Log.w(TAG, "Utilisateur non connecté, impossible de charger les infos de profil pour commenter.")
                _isLikedByCurrentUser.value = Resource.Error("Utilisateur non connecté. Impossible de vérifier le statut de like.")
                _likesCount.value = Resource.Error("Utilisateur non connecté. Impossible de récupérer le nombre de likes.")
                _bookIdForInteractions.value = null
            }

            if (!currentAuthUserId.isNullOrBlank() && currentAuthUserId != targetUserId) {
                observeFollowingStatus(currentAuthUserId, targetUserId)
                observeFollowedByTargetStatus(targetUserId, currentAuthUserId)
                viewModelScope.launch {
                    _isFollowing.combine(_isFollowedByTarget) { isAFollowsB, isBFollowsA ->
                        when {
                            isAFollowsB is Resource.Loading || isBFollowsA is Resource.Loading -> Resource.Loading()
                            isAFollowsB is Resource.Error -> {
                                Log.e(TAG, "Erreur lors de la vérification de A suit B: ${isAFollowsB.message}")
                                isAFollowsB
                            }
                            isBFollowsA is Resource.Error -> {
                                Log.e(TAG, "Erreur lors de la vérification de B suit A: ${isBFollowsA.message}")
                                isBFollowsA
                            }
                            isAFollowsB is Resource.Success && isBFollowsA is Resource.Success -> {
                                if (isAFollowsB.data == true && isBFollowsA.data == true) Resource.Success(true) else Resource.Success(false)
                            }
                            else -> {
                                Log.w(TAG, "Cas inattendu lors de la combinaison des statuts de suivi.")
                                Resource.Success(false)
                            }
                        }
                    }.catch { e ->
                        Log.e(TAG, "Erreur lors de la combinaison des flows de statut de suivi: ${e.message}", e)
                        _isMutualFollow.value = Resource.Error("Erreur lors de la détermination du suivi mutuel: ${e.localizedMessage}")
                    }.collectLatest { mutualFollowResource ->
                        _isMutualFollow.value = mutualFollowResource
                        Log.d(TAG, "Statut de suivi mutuel mis à jour: $mutualFollowResource")
                    }
                }
            } else {
                _isFollowing.value = Resource.Success(false)
                _isFollowedByTarget.value = Resource.Success(false)
                _isMutualFollow.value = Resource.Success(false)
                Log.d(TAG, "Profil propre ou utilisateur non connecté, pas de suivi mutuel.")
            }
        } else {
            Log.e(TAG, "User ID est null ou vide dans SavedStateHandle. Impossible de charger le profil.")
            _userProfile.value = Resource.Error("ID utilisateur manquant pour charger le profil.")
            _isFollowing.value = Resource.Success(false)
            _isFollowedByTarget.value = Resource.Success(false)
            _isMutualFollow.value = Resource.Success(false)
            _currentReadingUiState.value = CurrentReadingUiState(isLoading = false, isOwnedProfile = false, error = "ID utilisateur manquant pour charger le profil.")
            _comments.value = Resource.Error("ID utilisateur cible manquant pour charger les commentaires.")
            _currentUserProfileForCommenting.value = Resource.Error("ID utilisateur cible manquant pour charger les infos de profil pour commenter.")
            _isLikedByCurrentUser.value = Resource.Error("ID utilisateur cible manquant pour vérifier le statut de like.")
            _likesCount.value = Resource.Error("ID utilisateur cible manquant pour récupérer le nombre de likes.")
            _bookIdForInteractions.value = null
        }
    }

    private fun fetchUserProfile(id: String) {
        Log.d(TAG, "fetchUserProfile appelé pour l'ID: '$id'")
        viewModelScope.launch {
            _userProfile.value = Resource.Loading()
            // MODIFIÉ: Appel sur userProfileRepository
            userProfileRepository.getUserById(id)
                .catch { e ->
                    Log.e(TAG, "Exception non gérée dans le flow getUserById pour '$id'", e)
                    _userProfile.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    _userProfile.value = resource
                    when (resource) {
                        is Resource.Success -> Log.i(TAG, "Profil chargé avec succès pour ID '$id': ${resource.data?.username}. Followers: ${resource.data?.followersCount}, Following: ${resource.data?.followingCount}")
                        is Resource.Error -> Log.e(TAG, "Erreur lors du chargement du profil pour ID '$id': ${resource.message}")
                        is Resource.Loading -> Log.d(TAG, "Chargement du profil pour ID '$id'...")
                    }
                }
        }
    }

    private fun observeFollowingStatus(currentUserId: String, targetUserId: String) {
        Log.d(TAG, "observeFollowingStatus: Observation du statut de suivi de $currentUserId vers $targetUserId")
        viewModelScope.launch {
            socialRepository.isFollowing(currentUserId, targetUserId)
                .catch { e ->
                    Log.e(TAG, "observeFollowingStatus: Erreur lors de l'observation du statut de suivi: ${e.message}", e)
                    _isFollowing.value = Resource.Error("Erreur de suivi: ${e.localizedMessage}")
                }
                .collectLatest { resource ->
                    _isFollowing.value = resource
                    Log.d(TAG, "observeFollowingStatus: Statut de suivi mis à jour: $resource")
                }
        }
    }

    private fun observeFollowedByTargetStatus(targetUserId: String, currentUserId: String) {
        Log.d(TAG, "observeFollowedByTargetStatus: Observation du statut de suivi de $targetUserId vers $currentUserId")
        viewModelScope.launch {
            socialRepository.isFollowing(targetUserId, currentUserId)
                .catch { e ->
                    Log.e(TAG, "observeFollowedByTargetStatus: Erreur lors de l'observation du statut de suivi réciproque: ${e.message}", e)
                    _isFollowedByTarget.value = Resource.Error("Erreur de suivi réciproque: ${e.localizedMessage}")
                }
                .collectLatest { resource ->
                    _isFollowedByTarget.value = resource
                    Log.d(TAG, "observeFollowedByTargetStatus: Statut de suivi réciproque mis à jour: $resource")
                }
        }
    }

    fun toggleFollowStatus() {
        val currentUserUid = firebaseAuth.currentUser?.uid
        val targetId = userIdFromArgs

        if (currentUserUid.isNullOrBlank() || targetId.isNullOrBlank()) {
            _isFollowing.value = Resource.Error("ID utilisateur courant ou cible manquant.")
            Log.e(TAG, "toggleFollowStatus: Impossible de basculer le statut de suivi. currentUserId ou targetId manquant. current: $currentUserUid, target: $targetId")
            return
        }

        if (currentUserUid == targetId) {
            Log.w(TAG, "toggleFollowStatus: Impossible de se suivre soi-même.")
            _isFollowing.value = Resource.Error("Vous ne pouvez pas vous suivre vous-même.")
            return
        }

        viewModelScope.launch {
            val currentStatus = _isFollowing.value
            if (currentStatus is Resource.Success) {
                if (currentStatus.data == true) {
                    Log.d(TAG, "toggleFollowStatus: Unfollowing $targetId by $currentUserUid")
                    val result = socialRepository.unfollowUser(currentUserUid, targetId)
                    if (result is Resource.Error) {
                        Log.e(TAG, "toggleFollowStatus: Erreur lors du désabonnement: ${result.message}")
                        _isFollowing.value = Resource.Error(result.message ?: "Erreur de désabonnement")
                    } else {
                        Log.d(TAG, "toggleFollowStatus: Désabonnement réussi.")
                    }
                } else {
                    Log.d(TAG, "toggleFollowStatus: Following $targetId by $currentUserUid")
                    val result = socialRepository.followUser(currentUserUid, targetId)
                    if (result is Resource.Error) {
                        Log.e(TAG, "toggleFollowStatus: Erreur lors de l'abonnement: ${result.message}")
                        _isFollowing.value = Resource.Error(result.message ?: "Erreur d'abonnement")
                    } else {
                        Log.d(TAG, "toggleFollowStatus: Abonnement réussi.")
                    }
                }
            } else {
                Log.w(TAG, "toggleFollowStatus: Statut de suivi inconnu ou en chargement. Impossible de basculer.")
                _isFollowing.value = Resource.Error("Statut de suivi non disponible pour basculer.")
            }
        }
    }

    fun updateCurrentReading(userBookReading: UserBookReading?) {
        val currentUserUid = firebaseAuth.currentUser?.uid
        val targetId = userIdFromArgs

        if (currentUserUid.isNullOrBlank() || targetId.isNullOrBlank() || currentUserUid != targetId) {
            Log.e(TAG, "updateCurrentReading: Tentative de mise à jour sur un profil non-possédé ou IDs manquants. Current: $currentUserUid, Target: $targetId")
            _currentReadingUiState.value = _currentReadingUiState.value.copy(error = "Permission refusée pour mettre à jour la lecture.")
            return
        }

        viewModelScope.launch {
            _currentReadingUiState.value = _currentReadingUiState.value.copy(isLoading = true, error = null)
            val result = readingRepository.updateCurrentReading(currentUserUid, userBookReading)
            when (result) {
                is Resource.Success -> {
                    Log.d(TAG, "updateCurrentReading: Lecture en cours mise à jour avec succès.")
                }
                is Resource.Error -> {
                    Log.e(TAG, "updateCurrentReading: Erreur lors de la mise à jour: ${result.message}")
                    _currentReadingUiState.value = _currentReadingUiState.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun postComment(commentText: String) {
        val currentAuthUserId = firebaseAuth.currentUser?.uid
        val targetId = userIdFromArgs
        val bookId = _currentReadingUiState.value.bookReading?.bookId

        if (currentAuthUserId.isNullOrBlank() || targetId.isNullOrBlank() || bookId.isNullOrBlank()) {
            val errorMessage = "Impossible de poster le commentaire: informations manquantes (utilisateur connecté, profil cible, ou livre non défini)."
            viewModelScope.launch { _commentEvents.emit(CommentEvent.ShowCommentError(errorMessage)) }
            Log.e(TAG, "postComment: $errorMessage. current: $currentAuthUserId, target: $targetId, bookId: $bookId")
            return
        }
        if (commentText.isBlank()) {
            val errorMessage = "Le commentaire ne peut pas être vide."
            viewModelScope.launch { _commentEvents.emit(CommentEvent.ShowCommentError(errorMessage)) }
            Log.w(TAG, "postComment: Tentative de poster un commentaire vide.")
            return
        }

        val currentUserResource = _currentUserProfileForCommenting.value
        if (currentUserResource !is Resource.Success || currentUserResource.data == null) {
            val errorMessage = "Impossible de récupérer vos informations de profil pour poster le commentaire."
            viewModelScope.launch { _commentEvents.emit(CommentEvent.ShowCommentError(errorMessage)) }
            Log.e(TAG, "postComment: Les informations de profil de l'utilisateur connecté sont nulles ou en erreur: $currentUserResource")
            return
        }
        val currentUser = currentUserResource.data

        val newComment = Comment(userId = currentUser.uid, userName = currentUser.username, userPhotoUrl = currentUser.profilePictureUrl, targetUserId = targetId, commentText = commentText, timestamp = Timestamp.now(), bookId = bookId)

        Log.d(TAG, "postComment: Tentative de poster un commentaire sur la lecture (bookId: $bookId) de '$targetId' par '${currentUser.username}'.")
        viewModelScope.launch {
            val result = readingRepository.addCommentOnActiveReading(targetId, bookId, newComment)
            when (result) {
                is Resource.Success -> {
                    Log.i(TAG, "postComment: Commentaire posté avec succès.")
                    _commentEvents.emit(CommentEvent.ClearCommentInput)
                }
                is Resource.Error -> {
                    Log.e(TAG, "postComment: Erreur lors de l'envoi du commentaire: ${result.message}")
                    _commentEvents.emit(CommentEvent.ShowCommentError(result.message ?: "Erreur inconnue lors de l'envoi du commentaire."))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteComment(commentId: String, commentAuthorId: String) {
        val currentAuthUserId = firebaseAuth.currentUser?.uid
        val targetUserId = userIdFromArgs
        val bookId = _currentReadingUiState.value.bookReading?.bookId

        if (currentAuthUserId.isNullOrBlank() || targetUserId.isNullOrBlank() || commentId.isBlank() || bookId.isNullOrBlank()) {
            val errorMessage = "Impossible de supprimer le commentaire: informations manquantes (utilisateur, profil cible, commentaire ou livre non défini)."
            viewModelScope.launch { _commentEvents.emit(CommentEvent.ShowCommentError(errorMessage)) }
            Log.e(TAG, "deleteComment: $errorMessage. CurrentUser: $currentAuthUserId, TargetUser: $targetUserId, CommentID: $commentId, BookID: $bookId")
            return
        }

        if (currentAuthUserId != commentAuthorId && currentAuthUserId != targetUserId) {
            val errorMessage = "Vous n'êtes pas autorisé à supprimer ce commentaire."
            viewModelScope.launch { _commentEvents.emit(CommentEvent.ShowCommentError(errorMessage)) }
            Log.w(TAG, "deleteComment: Tentative de suppression non autorisée. CurrentUser: $currentAuthUserId, CommentAuthorID: $commentAuthorId, TargetUserId: $targetUserId")
            return
        }

        Log.d(TAG, "deleteComment: Tentative de suppression du commentaire '$commentId' par '$currentAuthUserId' sur le profil de '$targetUserId' (bookId: $bookId).")
        viewModelScope.launch {
            val result = readingRepository.deleteCommentOnActiveReading(targetUserId, bookId, commentId)
            when (result) {
                is Resource.Success -> {
                    Log.i(TAG, "deleteComment: Commentaire '$commentId' supprimé avec succès.")
                    _commentEvents.emit(CommentEvent.CommentDeletedSuccess(commentId))
                }
                is Resource.Error -> {
                    Log.e(TAG, "deleteComment: Erreur lors de la suppression du commentaire '$commentId': ${result.message}")
                    _commentEvents.emit(CommentEvent.ShowCommentError(result.message ?: "Erreur inconnue lors de la suppression du commentaire."))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun toggleLike() {
        val currentAuthUserId = firebaseAuth.currentUser?.uid
        val targetId = userIdFromArgs
        val bookId = _currentReadingUiState.value.bookReading?.bookId

        if (currentAuthUserId.isNullOrBlank() || targetId.isNullOrBlank() || bookId.isNullOrBlank()) {
            val errorMessage = "Impossible de liker/déliker: informations manquantes (utilisateur non connecté, profil cible ou livre non défini)."
            viewModelScope.launch { _likeEvents.emit(LikeEvent.ShowLikeError(errorMessage)) }
            Log.e(TAG, "toggleLike: $errorMessage. current: $currentAuthUserId, target: $targetId, bookId: $bookId")
            return
        }

        viewModelScope.launch {
            val result = readingRepository.toggleLikeOnActiveReading(targetId, bookId, currentAuthUserId)
            when (result) {
                is Resource.Success -> {
                    Log.i(TAG, "toggleLike: Statut de like basculé avec succès pour la lecture (bookId: $bookId) de '$targetId' par '$currentAuthUserId'.")
                }
                is Resource.Error -> {
                    Log.e(TAG, "toggleLike: Erreur lors de la bascule du like: ${result.message}")
                    _likeEvents.emit(LikeEvent.ShowLikeError(result.message ?: "Erreur inconnue lors de la bascule du like."))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun toggleLikeOnComment(comment: Comment) {
        val currentAuthUserId = firebaseAuth.currentUser?.uid
        val targetUserId = userIdFromArgs
        val bookId = comment.bookId

        if (currentAuthUserId.isNullOrBlank() || targetUserId.isNullOrBlank() || comment.commentId.isBlank() || bookId.isBlank()) {
            val errorMessage = "Impossible de liker/déliker le commentaire: informations manquantes (utilisateur, profil cible, commentaire ou livre non défini)."
            viewModelScope.launch { _commentEvents.emit(CommentEvent.ShowCommentLikeError(errorMessage)) }
            Log.e(TAG, "toggleLikeOnComment: $errorMessage. CurrentUser: $currentAuthUserId, TargetUser: $targetUserId, CommentID: ${comment.commentId}, BookID: $bookId")
            return
        }

        viewModelScope.launch {
            val result = readingRepository.toggleLikeOnComment(targetUserId, bookId, comment.commentId, currentAuthUserId)
            when (result) {
                is Resource.Success -> {
                    Log.i(TAG, "toggleLikeOnComment: Statut de like basculé avec succès pour commentaire '${comment.commentId}' (bookId: $bookId) par '$currentAuthUserId'.")
                }
                is Resource.Error -> {
                    Log.e(TAG, "toggleLikeOnComment: Erreur lors de la bascule du like sur commentaire '${comment.commentId}': ${result.message}")
                    _commentEvents.emit(CommentEvent.ShowCommentLikeError(result.message ?: "Erreur inconnue lors de la bascule du like."))
                }
                is Resource.Loading -> {}
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCommentLikeStatus(commentId: String): Flow<Resource<Boolean>> {
        val currentAuthUserId = firebaseAuth.currentUser?.uid
        val targetUserId = userIdFromArgs
        val bookId = _currentReadingUiState.value.bookReading?.bookId

        return if (currentAuthUserId.isNullOrBlank() || targetUserId.isNullOrBlank() || commentId.isBlank() || bookId.isNullOrBlank()) {
            Log.w(TAG, "getCommentLikeStatus: IDs manquants pour vérifier le statut de like. Retourne Resource.Error. current: $currentAuthUserId, target: $targetUserId, comment: $commentId, bookId: $bookId")
            flowOf(Resource.Error("Informations manquantes pour vérifier le statut de like du commentaire."))
        } else {
            readingRepository.isCommentLikedByCurrentUser(targetUserId, bookId, commentId, currentAuthUserId)
                .catch { e ->
                    Log.e(TAG, "Erreur lors de l'observation du statut de like du commentaire '$commentId' (bookId: $bookId): ${e.message}", e)
                    emit(Resource.Error("Erreur de statut de like: ${e.localizedMessage}"))
                }
        }
    }
}