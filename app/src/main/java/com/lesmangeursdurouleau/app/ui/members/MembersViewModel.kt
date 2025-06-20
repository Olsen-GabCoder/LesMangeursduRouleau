package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
// MODIFIÉ: Import de UserProfileRepository et suppression de UserRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MembersViewModel @Inject constructor(
    // MODIFIÉ: Remplacement de UserRepository par UserProfileRepository
    private val userProfileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository
) : ViewModel() {

    private val _members = MutableLiveData<Resource<List<User>>>()
    val members: LiveData<Resource<List<User>>> = _members

    companion object {
        private const val TAG = "MembersViewModel"
    }

    init {
        // Au démarrage, charger tous les membres par défaut.
        // Cette fonction sera également appelée depuis le Fragment avec des arguments
        // si la navigation est initiée pour une liste spécifique (followers/following).
        fetchMembers(null, null)
    }

    /**
     * Récupère la liste des membres, des followers ou des utilisateurs suivis en fonction des arguments.
     * @param targetUserId L'ID de l'utilisateur dont on veut la liste de followers/following. Null pour tous les membres.
     * @param listType Le type de liste à récupérer ("followers", "following", ou null pour tous les membres).
     */
    fun fetchMembers(targetUserId: String?, listType: String?) {
        Log.d(TAG, "fetchMembers: Called with targetUserId='$targetUserId', listType='$listType'")

        _members.postValue(Resource.Loading()) // Publier l'état de chargement

        viewModelScope.launch {
            val userFlow: Flow<Resource<List<User>>> = when (listType) {
                "followers" -> {
                    if (targetUserId.isNullOrBlank()) {
                        Log.e(TAG, "fetchMembers: targetUserId est null ou vide pour listType 'followers'.")
                        // En cas d'erreur de paramètre, on peut aussi envoyer un Resource.Error au LiveData
                        _members.postValue(Resource.Error("ID utilisateur cible manquant pour les followers."))
                        return@launch // Quitter la coroutine
                    }
                    socialRepository.getFollowersUsers(targetUserId)
                }
                "following" -> {
                    if (targetUserId.isNullOrBlank()) {
                        Log.e(TAG, "fetchMembers: targetUserId est null ou vide pour listType 'following'.")
                        // En cas d'erreur de paramètre, on peut aussi envoyer un Resource.Error au LiveData
                        _members.postValue(Resource.Error("ID utilisateur cible manquant pour les abonnements."))
                        return@launch // Quitter la coroutine
                    }
                    socialRepository.getFollowingUsers(targetUserId)
                }
                else -> { // Valeur par défaut: null ou "all" - récupérer tous les utilisateurs
                    // MODIFIÉ: Appel sur userProfileRepository
                    userProfileRepository.getAllUsers()
                }
            }

            userFlow
                .catch { e ->
                    Log.e(TAG, "Exception dans le flow de récupération des membres/listes de suivi", e)
                    _members.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    Log.d(TAG, "fetchMembers: Reçu la ressource: $resource")
                    _members.postValue(resource) // Mettre à jour l'état avec la ressource reçue
                }
        }
    }
}