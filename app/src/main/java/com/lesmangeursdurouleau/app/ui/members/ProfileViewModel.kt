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

    private val _userProfileData = MutableLiveData<Resource<User>>()
    val userProfileData: LiveData<Resource<User>> = _userProfileData

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

    private val _cityUpdateResult = MutableLiveData<Resource<Unit>?>()
    val cityUpdateResult: LiveData<Resource<Unit>?> = _cityUpdateResult

    private val _bioUpdateResult = MutableLiveData<Resource<Unit>?>()
    val bioUpdateResult: LiveData<Resource<Unit>?> = _bioUpdateResult

    private val _usernameUpdateResult = MutableLiveData<Resource<Unit>?>()
    val usernameUpdateResult: LiveData<Resource<Unit>?> = _usernameUpdateResult

    private val _profilePictureUpdateResult = MutableLiveData<Resource<String>?>()
    val profilePictureUpdateResult: LiveData<Resource<String>?> = _profilePictureUpdateResult


    init {
        Log.d("ProfileViewModel", "ViewModel initialisé.")
        loadCurrentUserProfile()
    }

    fun loadCurrentUserProfile() {
        Log.d("ProfileViewModel", "loadCurrentUserProfile: Entrée dans la fonction.")
        val firebaseCurrentUser = firebaseAuth.currentUser
        if (firebaseCurrentUser != null) {
            Log.d("ProfileViewModel", "loadCurrentUserProfile: Utilisateur connecté: ${firebaseCurrentUser.uid}, Email: ${firebaseCurrentUser.email}")
            // Mettre à jour les champs de base de Firebase Auth
            _email.value = firebaseCurrentUser.email
            _displayName.value = firebaseCurrentUser.displayName // Sera écrasé par Firestore si présent

            _userProfileData.value = Resource.Loading()

            viewModelScope.launch {
                userRepository.getUserById(firebaseCurrentUser.uid)
                    .catch { e ->
                        Log.e("ProfileViewModel", "Erreur lors de la collecte du getUserById flow", e)
                        _userProfileData.postValue(Resource.Error("Erreur Firestore: ${e.localizedMessage}"))
                        // En cas d'erreur Firestore, fallback sur les données Firebase Auth existantes
                        _displayName.postValue(firebaseCurrentUser.displayName)
                        _email.postValue(firebaseCurrentUser.email)
                        _profilePictureUrl.postValue(firebaseCurrentUser.photoUrl?.toString()) // Utiliser la photo Auth en cas d'erreur Firestore
                        _bio.postValue(null)
                        _city.postValue(null)
                    }
                    .collectLatest { resource ->
                        Log.d("ProfileViewModel", "loadCurrentUserProfile: Reçu de Firestore: $resource")
                        _userProfileData.postValue(resource) // Émettre le Resource brut pour l'état général de l'UI
                        when (resource) {
                            is Resource.Success -> {
                                val userFromDb = resource.data
                                if (userFromDb != null) {
                                    Log.d("ProfileViewModel", "loadCurrentUserProfile: Succès Firestore. User: ${userFromDb.username}, Email: ${userFromDb.email}, DB Photo URL: ${userFromDb.profilePictureUrl}, DB Bio: ${userFromDb.bio}, DB City: ${userFromDb.city}")
                                    // Utiliser Firestore comme source de vérité principale
                                    _displayName.postValue(
                                        userFromDb.username.takeUnless { it.isBlank() }
                                            ?: firebaseCurrentUser.displayName
                                    )
                                    _email.postValue(
                                        userFromDb.email.takeUnless { it.isBlank() }
                                            ?: firebaseCurrentUser.email
                                    )
                                    _bio.postValue(userFromDb.bio)
                                    _city.postValue(userFromDb.city)

                                    val urlFromDb = userFromDb.profilePictureUrl
                                    // firebaseAuth.currentUser.photoUrl est maintenant fiable car rechargé après update
                                    val urlFromAuth = firebaseAuth.currentUser?.photoUrl?.toString()
                                    Log.d("ProfileViewModel", "loadCurrentUserProfile (Final Logic): DB Photo URL: '$urlFromDb', Auth Photo URL: '$urlFromAuth'")

                                    // Prioriser Firestore. Si Firestore est vide, alors Auth.
                                    val finalUrl = urlFromDb.takeUnless { it.isNullOrBlank() } ?: urlFromAuth

                                    if (_profilePictureUrl.value != finalUrl) {
                                        Log.d("ProfileViewModel", "loadCurrentUserProfile (Final Logic): _profilePictureUrl posté avec: '$finalUrl' (différent de la valeur actuelle '${_profilePictureUrl.value}')")
                                        _profilePictureUrl.postValue(finalUrl)
                                    } else {
                                        Log.d("ProfileViewModel", "loadCurrentUserProfile (Final Logic): finalUrl ('$finalUrl') est identique à _profilePictureUrl.value, pas de repost.")
                                    }
                                } else {
                                    Log.w("ProfileViewModel", "loadCurrentUserProfile: Données utilisateur nulles depuis Firestore pour ${firebaseCurrentUser.uid}. Fallback sur données Auth.")
                                    _displayName.postValue(firebaseCurrentUser.displayName)
                                    _email.postValue(firebaseCurrentUser.email)
                                    _profilePictureUrl.postValue(firebaseCurrentUser.photoUrl?.toString())
                                    _bio.postValue(null)
                                    _city.postValue(null)
                                }
                            }
                            is Resource.Error -> {
                                Log.e("ProfileViewModel", "loadCurrentUserProfile: Erreur Resource Firestore: ${resource.message}. Fallback sur données Auth.")
                                // Les postValue en cas d'erreur ont déjà été faits dans le catch block
                                //_displayName.postValue(firebaseCurrentUser.displayName)
                                //_email.postValue(firebaseCurrentUser.email)
                                //_profilePictureUrl.postValue(firebaseCurrentUser.photoUrl?.toString())
                                //_bio.postValue(null)
                                //_city.postValue(null)
                            }
                            is Resource.Loading -> {
                                Log.d("ProfileViewModel", "loadCurrentUserProfile: Chargement des données Firestore...")
                                // L'UI du Fragment gère déjà l'état de chargement via _userProfileData
                            }
                        }
                    }
            }
        } else {
            Log.w("ProfileViewModel", "loadCurrentUserProfile: Aucun utilisateur Firebase connecté.")
            _userProfileData.value = Resource.Error("Utilisateur non connecté.")
            _email.value = null
            _displayName.value = null
            _profilePictureUrl.value = null
            _bio.value = null
            _city.value = null
        }
    }

    // Cette méthode est appelée depuis ProfileFragment après un succès d'upload.
    // Elle met à jour _profilePictureUrl pour une mise à jour UI immédiate et optimiste.
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
                Log.i("ProfileViewModel", "updateUsername: Succès de la mise à jour du pseudo vers '$newUsername'. Mise à jour de _displayName.")
                _displayName.postValue(newUsername)
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
                Log.i("ProfileViewModel", "updateBio: Succès de la mise à jour de la bio. Mise à jour de _bio.")
                _bio.postValue(newBio.trim())
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
                Log.i("ProfileViewModel", "updateCity: Succès de la mise à jour de la ville. Mise à jour de _city.")
                _city.postValue(newCity.trim())
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

    fun clearProfilePictureUpdateResult() {
        _profilePictureUpdateResult.value = null
    }
}