package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth // NOUVEL IMPORT
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow // NOUVEL IMPORT
import kotlinx.coroutines.flow.StateFlow // NOUVEL IMPORT
import kotlinx.coroutines.flow.asStateFlow // NOUVEL IMPORT
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine // NOUVEL IMPORT
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth, // INJECTER FirebaseAuth
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object { // Ajouter un TAG pour les logs
        private const val TAG = "PublicProfileViewModel"
    }

    private val _userProfile = MutableLiveData<Resource<User>>()
    val userProfile: LiveData<Resource<User>> = _userProfile

    private val userIdFromArgs: String? = savedStateHandle.get<String>("userId")

    // NOUVEAU : ID de l'utilisateur actuellement connecté
    private val _currentUserId = MutableLiveData<String?>(firebaseAuth.currentUser?.uid)
    val currentUserId: LiveData<String?> = _currentUserId

    // NOUVEAU : Statut de suivi de l'utilisateur courant vers le profil public
    private val _isFollowing = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isFollowing: StateFlow<Resource<Boolean>> = _isFollowing.asStateFlow()

    init {
        Log.d(TAG, "ViewModel initialisé. Tentative de récupération de l'userId des arguments.")
        val currentUserUid = firebaseAuth.currentUser?.uid // Récupérer l'UID de l'utilisateur connecté

        if (!userIdFromArgs.isNullOrBlank()) {
            Log.i(TAG, "UserId reçu des arguments: '$userIdFromArgs'. Lancement de fetchUserProfile.")
            fetchUserProfile(userIdFromArgs)

            // Démarrer l'observation du statut de suivi seulement si on a les deux IDs
            if (!currentUserUid.isNullOrBlank() && currentUserUid != userIdFromArgs) {
                observeFollowingStatus(currentUserUid, userIdFromArgs)
            } else if (currentUserUid == userIdFromArgs) {
                // Si c'est son propre profil, l'utilisateur ne peut pas se suivre, donc isFollowing est toujours faux
                _isFollowing.value = Resource.Success(false)
            } else {
                // Si currentUserId est null, on n'est pas connecté, donc on ne suit pas
                _isFollowing.value = Resource.Success(false)
            }
        } else {
            Log.e(TAG, "User ID est null ou vide dans SavedStateHandle. Impossible de charger le profil.")
            _userProfile.value = Resource.Error("ID utilisateur manquant pour charger le profil.")
            _isFollowing.value = Resource.Success(false) // Par défaut, non suivi
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

    // NOUVELLE MÉTHODE : Observe le statut de suivi
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

    // NOUVELLE MÉTHODE : Déclenche l'action de suivre
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
            // Mettre à jour l'état de suivi en fonction de l'état actuel
            val currentStatus = _isFollowing.value
            if (currentStatus is Resource.Success) {
                if (currentStatus.data == true) { // Si déjà suivi, on unfollow
                    Log.d(TAG, "toggleFollowStatus: Unfollowing $targetId by $currentUserUid")
                    val result = userRepository.unfollowUser(currentUserUid, targetId)
                    if (result is Resource.Error) {
                        Log.e(TAG, "toggleFollowStatus: Erreur lors du désabonnement: ${result.message}")
                        // Réinitialiser le statut précédent si l'opération échoue
                        _isFollowing.value = Resource.Error(result.message ?: "Erreur de désabonnement")
                    } else {
                        Log.d(TAG, "toggleFollowStatus: Désabonnement réussi.")
                        // Le listener isFollowing mettra à jour _isFollowing automatiquement
                    }
                } else { // Si pas encore suivi, on follow
                    Log.d(TAG, "toggleFollowStatus: Following $targetId by $currentUserUid")
                    val result = userRepository.followUser(currentUserUid, targetId)
                    if (result is Resource.Error) {
                        Log.e(TAG, "toggleFollowStatus: Erreur lors de l'abonnement: ${result.message}")
                        // Réinitialiser le statut précédent si l'opération échoue
                        _isFollowing.value = Resource.Error(result.message ?: "Erreur d'abonnement")
                    } else {
                        Log.d(TAG, "toggleFollowStatus: Abonnement réussi.")
                        // Le listener isFollowing mettra à jour _isFollowing automatiquement
                    }
                }
            } else {
                Log.w(TAG, "toggleFollowStatus: Statut de suivi inconnu ou en chargement. Impossible de basculer.")
                _isFollowing.value = Resource.Error("Statut de suivi non disponible pour basculer.")
            }
        }
    }
}