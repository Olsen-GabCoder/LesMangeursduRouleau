package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle // Retiré `private val` si non utilisé ailleurs que dans init
) : ViewModel() {

    private val _userProfile = MutableLiveData<Resource<User>>()
    val userProfile: LiveData<Resource<User>> = _userProfile

    private val userIdFromArgs: String? = savedStateHandle.get<String>("userId")

    init {
        Log.d("PublicProfileViewModel", "ViewModel initialisé. Tentative de récupération de l'userId des arguments.")
        if (!userIdFromArgs.isNullOrBlank()) {
            Log.i("PublicProfileViewModel", "UserId reçu des arguments: '$userIdFromArgs'. Lancement de fetchUserProfile.")
            fetchUserProfile(userIdFromArgs)
        } else {
            Log.e("PublicProfileViewModel", "User ID est null ou vide dans SavedStateHandle. Impossible de charger le profil.")
            _userProfile.value = Resource.Error("ID utilisateur manquant pour charger le profil.")
        }
    }

    private fun fetchUserProfile(id: String) {
        Log.d("PublicProfileViewModel", "fetchUserProfile appelé pour l'ID: '$id'")
        viewModelScope.launch {
            _userProfile.value = Resource.Loading()
            userRepository.getUserById(id)
                .catch { e ->
                    Log.e("PublicProfileViewModel", "Exception non gérée dans le flow getUserById pour '$id'", e)
                    _userProfile.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    _userProfile.value = resource
                    when (resource) {
                        is Resource.Success -> Log.i("PublicProfileViewModel", "Profil chargé avec succès pour ID '$id': ${resource.data?.username}")
                        is Resource.Error -> Log.e("PublicProfileViewModel", "Erreur lors du chargement du profil pour ID '$id': ${resource.message}")
                        is Resource.Loading -> Log.d("PublicProfileViewModel", "Chargement du profil pour ID '$id'...")
                    }
                }
        }
    }
}