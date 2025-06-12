package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// UI State pour la lecture en cours
data class CurrentReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookReading: UserBookReading? = null,
    val bookDetails: Book? = null,
    val isOwnedProfile: Boolean = false // Vrai si l'utilisateur courant est le propriétaire de ce profil
)

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val bookRepository: BookRepository,
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

    init {
        Log.d(TAG, "ViewModel initialisé. Tentative de récupération de l'userId des arguments.")
        val currentAuthUserId = firebaseAuth.currentUser?.uid // L'UID de l'utilisateur actuellement connecté

        // NOUVEAU LOG POUR DIAGNOSTIC
        Log.d(TAG, "DIAGNOSTIC: currentAuthUserId (utilisateur connecté): $currentAuthUserId")
        Log.d(TAG, "DIAGNOSTIC: userIdFromArgs (profil consulté): $userIdFromArgs")


        if (!userIdFromArgs.isNullOrBlank()) {
            val targetUserId = userIdFromArgs // L'UID du profil affiché
            val isProfileOwner = targetUserId == currentAuthUserId // Détermine si c'est le profil de l'utilisateur connecté

            // NOUVEAU LOG POUR DIAGNOSTIC
            Log.d(TAG, "DIAGNOSTIC: isProfileOwner (profil possédé): $isProfileOwner")


            Log.i(TAG, "UserId reçu des arguments: '$targetUserId'. Lancement de fetchUserProfile.")
            fetchUserProfile(targetUserId)

            // Lancement de l'observation de la lecture en cours
            viewModelScope.launch {
                // Initialize UI state with owner info
                _currentReadingUiState.value = CurrentReadingUiState(isLoading = true, isOwnedProfile = isProfileOwner)

                userRepository.getCurrentReading(targetUserId)
                    .flatMapLatest { readingResource ->
                        when (readingResource) {
                            is Resource.Loading -> flowOf(
                                _currentReadingUiState.value.copy(
                                    isLoading = true,
                                    error = null,
                                    bookReading = null, // Clear previous if loading new
                                    bookDetails = null
                                )
                            )
                            is Resource.Error -> flowOf(
                                _currentReadingUiState.value.copy(
                                    isLoading = false,
                                    error = readingResource.message,
                                    bookReading = null,
                                    bookDetails = null
                                )
                            )
                            is Resource.Success -> {
                                val userBookReading = readingResource.data
                                if (userBookReading != null) {
                                    // Si une lecture en cours est trouvée, récupérer les détails du livre
                                    Log.d(TAG, "Lecture en cours trouvée pour $targetUserId, bookId: ${userBookReading.bookId}. Tentative de récupération des détails du livre.")
                                    bookRepository.getBookById(userBookReading.bookId)
                                        .map { bookResource ->
                                            when (bookResource) {
                                                is Resource.Loading -> _currentReadingUiState.value.copy(
                                                    isLoading = true, // Toujours en chargement des détails du livre
                                                    error = null,
                                                    bookReading = userBookReading,
                                                    bookDetails = null
                                                )
                                                is Resource.Error -> {
                                                    Log.e(TAG, "Erreur lors de la récupération des détails du livre ${userBookReading.bookId}: ${bookResource.message}")
                                                    _currentReadingUiState.value.copy(
                                                        isLoading = false,
                                                        error = bookResource.message,
                                                        bookReading = userBookReading,
                                                        bookDetails = null
                                                    )
                                                }
                                                is Resource.Success -> {
                                                    Log.d(TAG, "Détails du livre ${userBookReading.bookId} récupérés avec succès: ${bookResource.data?.title}")
                                                    _currentReadingUiState.value.copy(
                                                        isLoading = false,
                                                        error = null,
                                                        bookReading = userBookReading,
                                                        bookDetails = bookResource.data
                                                    )
                                                }
                                            }
                                        }
                                        .catch { e ->
                                            Log.e(TAG, "Exception lors de la récupération des détails du livre: ${e.message}", e)
                                            emit(
                                                _currentReadingUiState.value.copy(
                                                    isLoading = false,
                                                    error = "Erreur chargement détails livre: ${e.localizedMessage}",
                                                    bookReading = userBookReading,
                                                    bookDetails = null
                                                )
                                            )
                                        }
                                } else {
                                    // Aucune lecture en cours pour cet utilisateur
                                    Log.d(TAG, "Aucune lecture en cours trouvée pour l'utilisateur $targetUserId.")
                                    flowOf(
                                        _currentReadingUiState.value.copy(
                                            isLoading = false,
                                            error = null,
                                            bookReading = null,
                                            bookDetails = null
                                        )
                                    )
                                }
                            }
                        }
                    }
                    .catch { e ->
                        Log.e(TAG, "Exception générale du flow de lecture en cours pour $targetUserId: ${e.message}", e)
                        _currentReadingUiState.value = _currentReadingUiState.value.copy(
                            isLoading = false,
                            error = "Erreur générale lecture en cours: ${e.localizedMessage}",
                            bookReading = null,
                            bookDetails = null
                        )
                    }
                    .collectLatest { uiState ->
                        _currentReadingUiState.value = uiState
                    }
            }


            // Logique existante pour le suivi (follow/unfollow)
            if (!currentAuthUserId.isNullOrBlank() && currentAuthUserId != targetUserId) {
                // 1. Observer si A suit B
                observeFollowingStatus(currentAuthUserId, targetUserId)
                // 2. Observer si B suit A
                observeFollowedByTargetStatus(targetUserId, currentAuthUserId)

                // 3. Combiner les deux statuts pour déterminer le suivi mutuel
                viewModelScope.launch {
                    _isFollowing.combine(_isFollowedByTarget) { isAFollowsB, isBFollowsA ->
                        when {
                            isAFollowsB is Resource.Loading || isBFollowsA is Resource.Loading -> {
                                // Si l'une des deux informations est encore en chargement, le statut mutuel l'est aussi.
                                Resource.Loading()
                            }
                            isAFollowsB is Resource.Error -> {
                                Log.e(TAG, "Erreur lors de la vérification de A suit B: ${isAFollowsB.message}")
                                isAFollowsB
                            }
                            isBFollowsA is Resource.Error -> {
                                Log.e(TAG, "Erreur lors de la vérification de B suit A: ${isBFollowsA.message}")
                                isBFollowsA
                            }
                            isAFollowsB is Resource.Success && isBFollowsA is Resource.Success -> {
                                if (isAFollowsB.data == true && isBFollowsA.data == true) {
                                    Resource.Success(true)
                                } else {
                                    Resource.Success(false)
                                }
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
            _currentReadingUiState.value = CurrentReadingUiState(
                isLoading = false,
                isOwnedProfile = false,
                error = "ID utilisateur manquant pour charger le profil."
            )
        }
    }

    private fun fetchUserProfile(id: String) {
        Log.d(TAG, "fetchUserProfile appelé pour l'ID: '$id'")
        viewModelScope.launch {
            _userProfile.value = Resource.Loading()
            userRepository.getUserById(id)
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

    // Méthode existante : Observe le statut de suivi de l'utilisateur courant vers le profil public (A suit B)
    private fun observeFollowingStatus(currentUserId: String, targetUserId: String) {
        Log.d(TAG, "observeFollowingStatus: Observation du statut de suivi de $currentUserId vers $targetUserId")
        viewModelScope.launch {
            userRepository.isFollowing(currentUserId, targetUserId)
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

    // Méthode existante : Observe si le profil public suit l'utilisateur courant (B suit A)
    private fun observeFollowedByTargetStatus(targetUserId: String, currentUserId: String) {
        Log.d(TAG, "observeFollowedByTargetStatus: Observation du statut de suivi de $targetUserId vers $currentUserId")
        viewModelScope.launch {
            userRepository.isFollowing(targetUserId, currentUserId)
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
                if (currentStatus.data == true) { // Si déjà suivi, on unfollow
                    Log.d(TAG, "toggleFollowStatus: Unfollowing $targetId by $currentUserUid")
                    val result = userRepository.unfollowUser(currentUserUid, targetId)
                    if (result is Resource.Error) {
                        Log.e(TAG, "toggleFollowStatus: Erreur lors du désabonnement: ${result.message}")
                        _isFollowing.value = Resource.Error(result.message ?: "Erreur de désabonnement")
                    } else {
                        Log.d(TAG, "toggleFollowStatus: Désabonnement réussi.")
                    }
                } else { // Si pas encore suivi, on follow
                    Log.d(TAG, "toggleFollowStatus: Following $targetId by $currentUserUid")
                    val result = userRepository.followUser(currentUserUid, targetId)
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

    /**
     * Met à jour la lecture en cours de l'utilisateur connecté.
     * Cette fonction ne peut être appelée que si l'utilisateur consulte son propre profil.
     * @param userBookReading L'objet UserBookReading à enregistrer/mettre à jour, ou null pour supprimer.
     */
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
            val result = userRepository.updateCurrentReading(currentUserUid, userBookReading)
            when (result) {
                is Resource.Success -> {
                    Log.d(TAG, "updateCurrentReading: Lecture en cours mise à jour avec succès.")
                    // L'observation du flow getCurrentReading va automatiquement mettre à jour _currentReadingUiState
                }
                is Resource.Error -> {
                    Log.e(TAG, "updateCurrentReading: Erreur lors de la mise à jour: ${result.message}")
                    _currentReadingUiState.value = _currentReadingUiState.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> { /* Ne devrait pas arriver pour une fonction suspendue retournant Resource */ }
            }
        }
    }
}