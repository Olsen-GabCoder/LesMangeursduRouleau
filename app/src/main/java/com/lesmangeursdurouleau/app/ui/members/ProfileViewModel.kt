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

    // --- AJOUT POUR LA VILLE ---
    private val _city = MutableLiveData<String?>()
    val city: LiveData<String?> = _city

    private val _cityUpdateResult = MutableLiveData<Resource<Unit>?>()
    val cityUpdateResult: LiveData<Resource<Unit>?> = _cityUpdateResult
    // --- FIN DES AJOUTS POUR LA VILLE ---

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
        Log.d("ProfileViewModel", "loadCurrentUserProfile: Entrée dans la fonction. Valeur ACTUELLE de _profilePictureUrl.value: '${_profilePictureUrl.value}'")
        val firebaseCurrentUser = firebaseAuth.currentUser
        if (firebaseCurrentUser != null) {
            Log.d("ProfileViewModel", "loadCurrentUserProfile: Utilisateur connecté: ${firebaseCurrentUser.uid}, Email: ${firebaseCurrentUser.email}")
            Log.d("ProfileViewModel", "loadCurrentUserProfile: Auth photoUrl AVANT lecture Firestore (dans cet appel de load): ${firebaseCurrentUser.photoUrl?.toString()}")

            if (_profilePictureUrl.value == null || _profilePictureUrl.value != firebaseCurrentUser.photoUrl?.toString()) {
                Log.d("ProfileViewModel", "loadCurrentUserProfile: Initialisation/Mise à jour de _profilePictureUrl.value avec Auth photoUrl: ${firebaseCurrentUser.photoUrl?.toString()}")
                _profilePictureUrl.value = firebaseCurrentUser.photoUrl?.toString()
            }
            _email.value = firebaseCurrentUser.email
            _displayName.value = firebaseCurrentUser.displayName
            // _bio.value et _city.value seront mis à jour après lecture Firestore
            _userProfileData.value = Resource.Loading()

            viewModelScope.launch {
                userRepository.getUserById(firebaseCurrentUser.uid)
                    .catch { e ->
                        Log.e("ProfileViewModel", "Erreur lors de la collecte du getUserById flow", e)
                        _userProfileData.postValue(Resource.Error("Erreur Firestore: ${e.localizedMessage}"))
                    }
                    .collectLatest { resource ->
                        Log.d("ProfileViewModel", "loadCurrentUserProfile: Reçu de Firestore: $resource")
                        _userProfileData.postValue(resource)
                        when (resource) {
                            is Resource.Success -> {
                                val userFromDb = resource.data
                                if (userFromDb != null) {
                                    Log.d("ProfileViewModel", "loadCurrentUserProfile: Succès Firestore. User: ${userFromDb.username}, Email: ${userFromDb.email}, DB Photo URL: ${userFromDb.profilePictureUrl}, DB Bio: ${userFromDb.bio}, DB City: ${userFromDb.city}")
                                    _displayName.postValue(
                                        userFromDb.username.takeUnless { it.isBlank() }
                                            ?: firebaseCurrentUser.displayName
                                    )
                                    _email.postValue(
                                        userFromDb.email.takeUnless { it.isBlank() }
                                            ?: firebaseCurrentUser.email
                                    )
                                    _bio.postValue(userFromDb.bio)
                                    // --- MISE À JOUR DE LA VILLE ---
                                    _city.postValue(userFromDb.city) // Peut être null
                                    // --- FIN MISE À JOUR VILLE ---

                                    val urlFromDb = userFromDb.profilePictureUrl
                                    val urlFromAuthAtFallback = firebaseAuth.currentUser?.photoUrl?.toString()
                                    Log.d("ProfileViewModel", "loadCurrentUserProfile (Fallback Logic): DB Photo URL: '$urlFromDb', Auth Photo URL au moment du fallback: '$urlFromAuthAtFallback'")
                                    val finalUrl = urlFromDb.takeUnless { it.isNullOrBlank() } ?: urlFromAuthAtFallback
                                    if (_profilePictureUrl.value != finalUrl) {
                                        Log.d("ProfileViewModel", "loadCurrentUserProfile (Fallback Logic): _profilePictureUrl posté avec: '$finalUrl' (différent de la valeur actuelle '${_profilePictureUrl.value}')")
                                        _profilePictureUrl.postValue(finalUrl)
                                    } else {
                                        Log.d("ProfileViewModel", "loadCurrentUserProfile (Fallback Logic): finalUrl ('$finalUrl') est identique à _profilePictureUrl.value, pas de repost.")
                                    }
                                } else {
                                    Log.w("ProfileViewModel", "loadCurrentUserProfile: Données utilisateur nulles depuis Firestore pour ${firebaseCurrentUser.uid}.")
                                    _bio.postValue(null)
                                    _city.postValue(null) // Ville nulle si pas de données DB
                                    Log.d("ProfileViewModel", "loadCurrentUserProfile (userFromDb null): _profilePictureUrl.value actuel: '${_profilePictureUrl.value}', Auth photoUrl: '${firebaseAuth.currentUser?.photoUrl?.toString()}'")
                                }
                            }
                            is Resource.Error -> {
                                Log.e("ProfileViewModel", "loadCurrentUserProfile: Erreur Resource Firestore: ${resource.message}")
                                _bio.postValue(null)
                                _city.postValue(null) // Ville nulle en cas d'erreur de chargement
                                Log.d("ProfileViewModel", "loadCurrentUserProfile (Erreur Firestore): _profilePictureUrl.value actuel: '${_profilePictureUrl.value}', Auth photoUrl: '${firebaseAuth.currentUser?.photoUrl?.toString()}'")
                            }
                            is Resource.Loading -> {
                                Log.d("ProfileViewModel", "loadCurrentUserProfile: Chargement des données Firestore...")
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
            _city.value = null // Ville nulle si pas connecté
        }
    }

    fun setCurrentProfilePictureUrl(newUrl: String?) {
        Log.d("ProfileViewModel", "setCurrentProfilePictureUrl: Mise à jour directe de _profilePictureUrl avec: '$newUrl'")
        _profilePictureUrl.value = newUrl
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

    // --- NOUVELLE FONCTION POUR METTRE À JOUR LA VILLE ---
    fun updateCity(newCity: String) { // La ville peut être vide pour la supprimer/effacer.
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _cityUpdateResult.value = Resource.Error("Utilisateur non connecté pour la mise à jour de la ville.")
            Log.e("ProfileViewModel", "updateCity: UserID est null, impossible de mettre à jour.")
            return
        }
        Log.d("ProfileViewModel", "updateCity: Tentative de mise à jour de la ville vers '$newCity' pour UserID: $userId")
        _cityUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            // NOUS AURONS BESOIN D'UNE NOUVELLE MÉTHODE DANS UserRepository: updateUserCity(userId, newCity)
            val result = userRepository.updateUserCity(userId, newCity.trim()) // Appel d'une future méthode
            _cityUpdateResult.postValue(result)

            if (result is Resource.Success<*>) {
                Log.i("ProfileViewModel", "updateCity: Succès de la mise à jour de la ville. Mise à jour de _city.")
                _city.postValue(newCity.trim()) // Mise à jour optimiste
            } else if (result is Resource.Error<*>) {
                Log.e("ProfileViewModel", "updateCity: Échec de la mise à jour de la ville: ${result.message}")
            }
        }
    }
    // --- FIN DE LA NOUVELLE FONCTION ---

    fun clearUsernameUpdateResult() {
        _usernameUpdateResult.value = null
    }

    fun clearBioUpdateResult() {
        _bioUpdateResult.value = null
    }

    // --- NOUVELLE FONCTION POUR CONSOMMER LE RÉSULTAT DE LA VILLE ---
    fun clearCityUpdateResult() {
        _cityUpdateResult.value = null
    }
    // --- FIN ---

    fun clearProfilePictureUpdateResult() {
        _profilePictureUpdateResult.value = null
    }
}