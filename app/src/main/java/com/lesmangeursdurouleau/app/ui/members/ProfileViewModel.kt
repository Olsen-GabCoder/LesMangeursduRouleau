package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    internal val firebaseAuth: FirebaseAuth
) : ViewModel() {

    // Ceci est la source de vérité principale pour le profil de l'utilisateur
    private val _userProfileData = MutableLiveData<Resource<User>>()
    val userProfileData: LiveData<Resource<User>> = _userProfileData

    // LiveData individuels, ils seront mis à jour LORSQUE userProfileData change
    // et aussi optimistically lors des mises à jour spécifiques.
    // LE FRAGMENT NE DEVRAIT OBSERVER QUE userProfileData pour peupler l'UI.
    // Ces LiveData individuels servent principalement pour les bindings bidirectionnels ou des cas très spécifiques.
    // Sincèrement, je recommande de les supprimer et de ne travailler qu'avec userProfileData.
    // Si vous les gardez, ils DOIVENT refléter ce qui est dans userProfileData.
    private val _email = MutableLiveData<String?>()
    val email: LiveData<String?> = _email

    private val _displayName = MutableLiveData<String?>()
    val displayName: LiveData<String?> = _displayName

    private val _profilePictureUrl = MutableLiveData<String?>()
    val profilePictureUrl: LiveData<String?> = _profilePictureUrl

    private val _bio = MutableLiveData<String?>()
    val bio: LiveData<String?> = _bio

    private val _city = MutableLiveData<String?>()
    val city: LiveData<String?> = _city

    // Les résultats des mises à jour, pour les SnackBar/Toast
    private val _cityUpdateResult = MutableLiveData<Resource<Unit>?>()
    val cityUpdateResult: LiveData<Resource<Unit>?> = _cityUpdateResult

    private val _bioUpdateResult = MutableLiveData<Resource<Unit>?>()
    val bioUpdateResult: LiveData<Resource<Unit>?> = _bioUpdateResult

    private val _usernameUpdateResult = MutableLiveData<Resource<Unit>?>()
    val usernameUpdateResult: LiveData<Resource<Unit>?> = _usernameUpdateResult

    // NOTE: profilePictureUpdateResult est mieux géré dans AuthViewModel car c'est lui qui upload.
    // L'observation de AuthViewModel.profilePictureUpdateResult dans ProfileFragment est correcte.
    // Ce LiveData ici est redondant avec celui d'AuthViewModel.

    init {
        Log.d("ProfileViewModel", "ViewModel initialisé.")
        loadCurrentUserProfile()

        // Important: Observer userProfileData et mettre à jour les LiveData individuels
        // Ceci garantit que les LiveData individuels sont toujours synchronisés avec la source de vérité.
        _userProfileData.observeForever { resource ->
            if (resource is Resource.Success && resource.data != null) {
                val user = resource.data
                _email.value = user.email
                _displayName.value = user.username
                _profilePictureUrl.value = user.profilePictureUrl
                _bio.value = user.bio
                _city.value = user.city
                Log.d("ProfileViewModel", "LiveData individuels mis à jour depuis userProfileData: ${user.username}")
            } else if (resource is Resource.Error) {
                // En cas d'erreur de chargement principal, réinitialiser les LiveData individuels
                _email.value = null
                _displayName.value = null
                _profilePictureUrl.value = null
                _bio.value = null
                _city.value = null
                Log.e("ProfileViewModel", "LiveData individuels réinitialisés suite à une erreur userProfileData.")
            }
            // Ne rien faire en cas de Loading, car les valeurs précédentes peuvent encore être valides.
        }
    }

    fun loadCurrentUserProfile() {
        Log.d("ProfileViewModel", "loadCurrentUserProfile: Entrée dans la fonction.")
        val firebaseCurrentUser = firebaseAuth.currentUser
        if (firebaseCurrentUser == null) {
            _userProfileData.value = Resource.Error("Utilisateur non connecté.")
            Log.e("ProfileViewModel", "loadCurrentUserProfile: Aucun utilisateur Firebase connecté.")
            return
        }

        _userProfileData.value = Resource.Loading()

        viewModelScope.launch {
            userRepository.getUserById(firebaseCurrentUser.uid)
                .catch { e ->
                    Log.e("ProfileViewModel", "Erreur lors de la collecte du getUserById flow", e)
                    _userProfileData.postValue(Resource.Error("Erreur de chargement du profil: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    Log.d("ProfileViewModel", "loadCurrentUserProfile: Reçu de Firestore: $resource")
                    _userProfileData.postValue(resource) // Émettre le Resource brut pour l'état général de l'UI
                    // Les LiveData individuels seront mis à jour via l'observeForever ci-dessus.
                }
        }
    }

    // Cette méthode est appelée depuis ProfileFragment après un succès d'upload.
    // Elle met à jour _profilePictureUrl pour une mise à jour UI immédiate et optimiste.
    // Et met à jour l'objet User interne.
    fun setCurrentProfilePictureUrl(newUrl: String?) {
        Log.d("ProfileViewModel", "setCurrentProfilePictureUrl: Mise à jour directe de _profilePictureUrl avec: '$newUrl'")
        _profilePictureUrl.value = newUrl
        // Mise à jour de userProfileData pour que l'objet User interne reflète le changement optimiste.
        val currentResource = _userProfileData.value
        if (currentResource is Resource.Success && currentResource.data != null) {
            val updatedUser = currentResource.data.copy(profilePictureUrl = newUrl)
            _userProfileData.value = Resource.Success(updatedUser)
            Log.d("ProfileViewModel", "setCurrentProfilePictureUrl: _userProfileData également mis à jour avec la nouvelle URL.")
        }
    }


    fun updateUsername(newUsername: String) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _usernameUpdateResult.value = Resource.Error("Utilisateur non connecté pour la mise à jour du pseudo.")
            Log.e("ProfileViewModel", "updateUsername: UserID est null, impossible de mettre à jour.")
            return
        }
        if (newUsername.isBlank()) {
            _usernameUpdateResult.value = Resource.Error("Le pseudo ne peut pas être vide.")
            Log.w("ProfileViewModel", "updateUsername: Tentative de mise à jour avec un pseudo vide.")
            return
        }
        Log.d("ProfileViewModel", "updateUsername: Tentative de mise à jour du pseudo vers '$newUsername' pour UserID: $userId")
        _usernameUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            val result = userRepository.updateUserProfile(userId, newUsername)
            _usernameUpdateResult.postValue(result)
            if (result is Resource.Success) {
                Log.i("ProfileViewModel", "updateUsername: Succès de la mise à jour du pseudo vers '$newUsername'.")
                // Mettre à jour l'objet User dans userProfileData pour refléter le changement
                val currentResource = _userProfileData.value
                if (currentResource is Resource.Success && currentResource.data != null) {
                    val updatedUser = currentResource.data.copy(username = newUsername)
                    _userProfileData.postValue(Resource.Success(updatedUser))
                }
                // _displayName sera mis à jour via l'observeForever de _userProfileData
            } else if (result is Resource.Error) {
                Log.e("ProfileViewModel", "updateUsername: Échec de la mise à jour du pseudo: ${result.message}")
            }
        }
    }

    fun updateBio(newBio: String) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _bioUpdateResult.value = Resource.Error("Utilisateur non connecté pour la mise à jour de la biographie.")
            Log.e("ProfileViewModel", "updateBio: UserID est null, impossible de mettre à jour.")
            return
        }
        Log.d("ProfileViewModel", "updateBio: Tentative de mise à jour de la bio vers '$newBio' pour UserID: $userId")
        _bioUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            val result = userRepository.updateUserBio(userId, newBio.trim())
            _bioUpdateResult.postValue(result)
            if (result is Resource.Success) {
                Log.i("ProfileViewModel", "updateBio: Succès de la mise à jour de la bio.")
                val currentResource = _userProfileData.value
                if (currentResource is Resource.Success && currentResource.data != null) {
                    val updatedUser = currentResource.data.copy(bio = newBio.trim())
                    _userProfileData.postValue(Resource.Success(updatedUser))
                }
                // _bio sera mis à jour via l'observeForever de _userProfileData
            } else if (result is Resource.Error) {
                Log.e("ProfileViewModel", "updateBio: Échec de la mise à jour de la bio: ${result.message}")
            }
        }
    }

    fun updateCity(newCity: String) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _cityUpdateResult.value = Resource.Error("Utilisateur non connecté pour la mise à jour de la ville.")
            Log.e("ProfileViewModel", "updateCity: UserID est null, impossible de mettre à jour.")
            return
        }
        Log.d("ProfileViewModel", "updateCity: Tentative de mise à jour de la ville vers '$newCity' pour UserID: $userId")
        _cityUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            val result = userRepository.updateUserCity(userId, newCity.trim())
            _cityUpdateResult.postValue(result)

            if (result is Resource.Success<*>) {
                Log.i("ProfileViewModel", "updateCity: Succès de la mise à jour de la ville.")
                val currentResource = _userProfileData.value
                if (currentResource is Resource.Success && currentResource.data != null) {
                    val updatedUser = currentResource.data.copy(city = newCity.trim())
                    _userProfileData.postValue(Resource.Success(updatedUser))
                }
                // _city sera mis à jour via l'observeForever de _userProfileData
            } else if (result is Resource.Error<*>) {
                Log.e("ProfileViewModel", "updateCity: Échec de la mise à jour de la ville: ${result.message}")
            }
        }
    }

    fun clearUsernameUpdateResult() {
        _usernameUpdateResult.value = null
    }

    fun clearBioUpdateResult() {
        _bioUpdateResult.value = null
    }

    fun clearCityUpdateResult() {
        _cityUpdateResult.value = null
    }

}