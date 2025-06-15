package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// NOUVEAU: UI State pour la lecture en cours du profil privé
data class PrivateCurrentReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookReading: UserBookReading? = null,
    val bookDetails: Book? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val bookRepository: BookRepository,
    internal val firebaseAuth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    // Ceci est la source de vérité principale pour le profil de l'utilisateur
    private val _userProfileData = MutableLiveData<Resource<User>>()
    val userProfileData: LiveData<Resource<User>> = _userProfileData

    // Les LiveData individuels pour email, displayName, profilePictureUrl, bio, et city ont été supprimés.
    // Le ProfileFragment devra désormais observer 'userProfileData' et extraire ces informations de l'objet User.

    // Les résultats des mises à jour, pour les SnackBar/Toast
    private val _cityUpdateResult = MutableLiveData<Resource<Unit>?>()
    val cityUpdateResult: LiveData<Resource<Unit>?> = _cityUpdateResult

    private val _bioUpdateResult = MutableLiveData<Resource<Unit>?>()
    val bioUpdateResult: LiveData<Resource<Unit>?> = _bioUpdateResult

    private val _usernameUpdateResult = MutableLiveData<Resource<Unit>?>()
    val usernameUpdateResult: LiveData<Resource<Unit>?> = _usernameUpdateResult

    // NOUVEAU: StateFlow pour la lecture en cours du profil privé
    private val _currentReadingUiState = MutableStateFlow(PrivateCurrentReadingUiState(isLoading = true))
    val currentReadingUiState: StateFlow<PrivateCurrentReadingUiState> = _currentReadingUiState.asStateFlow()

    init {
        Log.d(TAG, "ViewModel initialisé.")
        loadCurrentUserProfile()

        // L'observateur 'userProfileData.observeForever' qui mettait à jour les LiveData individuels a été supprimé car ces LiveData n'existent plus.

        // LOGIQUE EXISTANTE : Observer la lecture en cours de l'utilisateur connecté
        val currentUserUid = firebaseAuth.currentUser?.uid
        if (!currentUserUid.isNullOrBlank()) {
            viewModelScope.launch {
                userRepository.getCurrentReading(currentUserUid)
                    .flatMapLatest { readingResource ->
                        when (readingResource) {
                            is Resource.Loading -> flowOf(
                                PrivateCurrentReadingUiState(isLoading = true)
                            )
                            is Resource.Error -> flowOf(
                                PrivateCurrentReadingUiState(isLoading = false, error = readingResource.message)
                            )
                            is Resource.Success -> {
                                val userBookReading = readingResource.data
                                if (userBookReading != null) {
                                    Log.d(TAG, "Lecture en cours trouvée pour $currentUserUid, bookId: ${userBookReading.bookId}. Tentative de récupération des détails du livre.")
                                    bookRepository.getBookById(userBookReading.bookId)
                                        .map { bookResource ->
                                            when (bookResource) {
                                                is Resource.Loading -> PrivateCurrentReadingUiState(
                                                    isLoading = true,
                                                    bookReading = userBookReading,
                                                    bookDetails = null // Clear book details while loading them
                                                )
                                                is Resource.Error -> {
                                                    Log.e(TAG, "Erreur lors de la récupération des détails du livre ${userBookReading.bookId}: ${bookResource.message}")
                                                    PrivateCurrentReadingUiState(
                                                        isLoading = false,
                                                        error = bookResource.message,
                                                        bookReading = userBookReading,
                                                        bookDetails = null
                                                    )
                                                }
                                                is Resource.Success -> {
                                                    Log.d(TAG, "Détails du livre ${userBookReading.bookId} récupérés avec succès: ${bookResource.data?.title}")
                                                    PrivateCurrentReadingUiState(
                                                        isLoading = false,
                                                        error = null,
                                                        bookReading = userBookReading,
                                                        bookDetails = bookResource.data
                                                    )
                                                }
                                            }
                                        }
                                        .catch { e ->
                                            Log.e(TAG, "Exception lors de la récupération des détails du livre pour lecture en cours: ${e.message}", e)
                                            emit(PrivateCurrentReadingUiState(
                                                isLoading = false,
                                                error = "Erreur chargement détails livre: ${e.localizedMessage}",
                                                bookReading = userBookReading,
                                                bookDetails = null
                                            ))
                                        }
                                } else {
                                    Log.d(TAG, "Aucune lecture en cours trouvée pour l'utilisateur $currentUserUid.")
                                    flowOf(PrivateCurrentReadingUiState(
                                        isLoading = false,
                                        error = null,
                                        bookReading = null,
                                        bookDetails = null
                                    ))
                                }
                            }
                        }
                    }
                    .catch { e ->
                        Log.e(TAG, "Exception générale du flow de lecture en cours pour $currentUserUid: ${e.message}", e)
                        _currentReadingUiState.value = PrivateCurrentReadingUiState(
                            isLoading = false,
                            error = "Erreur générale lecture en cours: ${e.localizedMessage}"
                        )
                    }
                    .collectLatest { uiState ->
                        _currentReadingUiState.value = uiState
                    }
            }
        } else {
            Log.e(TAG, "Aucun utilisateur connecté pour charger la lecture en cours.")
            _currentReadingUiState.value = PrivateCurrentReadingUiState(
                isLoading = false,
                error = "Utilisateur non connecté."
            )
        }
    }

    fun loadCurrentUserProfile() {
        Log.d(TAG, "loadCurrentUserProfile: Entrée dans la fonction.")
        val firebaseCurrentUser = firebaseAuth.currentUser
        if (firebaseCurrentUser == null) {
            _userProfileData.value = Resource.Error("Utilisateur non connecté.")
            Log.e(TAG, "loadCurrentUserProfile: Aucun utilisateur Firebase connecté.")
            return
        }

        _userProfileData.value = Resource.Loading()

        viewModelScope.launch {
            userRepository.getUserById(firebaseCurrentUser.uid)
                .catch { e ->
                    Log.e(TAG, "Erreur lors de la collecte du getUserById flow", e)
                    _userProfileData.postValue(Resource.Error("Erreur de chargement du profil: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    Log.d(TAG, "loadCurrentUserProfile: Reçu de Firestore: $resource")
                    _userProfileData.postValue(resource)
                }
        }
    }

    // MODIFIÉ : Cette fonction met maintenant à jour directement 'userProfileData'
    fun setCurrentProfilePictureUrl(newUrl: String?) {
        Log.d(TAG, "setCurrentProfilePictureUrl: Tentative de mise à jour de profilePictureUrl dans userProfileData avec: '$newUrl'")
        val currentResource = _userProfileData.value
        if (currentResource is Resource.Success && currentResource.data != null) {
            val updatedUser = currentResource.data.copy(profilePictureUrl = newUrl)
            _userProfileData.value = Resource.Success(updatedUser)
            Log.d(TAG, "setCurrentProfilePictureUrl: _userProfileData mis à jour avec la nouvelle URL.")
        } else {
            Log.w(TAG, "setCurrentProfilePictureUrl: Impossible de mettre à jour _userProfileData, ressource actuelle non SUCCESS ou données null.")
        }
    }

    fun updateUsername(newUsername: String) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _usernameUpdateResult.value = Resource.Error("Utilisateur non connecté pour la mise à jour du pseudo.")
            Log.e(TAG, "updateUsername: UserID est null, impossible de mettre à jour.")
            return
        }
        if (newUsername.isBlank()) {
            _usernameUpdateResult.value = Resource.Error("Le pseudo ne peut pas être vide.")
            Log.w(TAG, "updateUsername: Tentative de mise à jour avec un pseudo vide.")
            return
        }
        Log.d(TAG, "updateUsername: Tentative de mise à jour du pseudo vers '$newUsername' pour UserID: $userId")
        _usernameUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            val result = userRepository.updateUserProfile(userId, newUsername)
            _usernameUpdateResult.postValue(result)
            if (result is Resource.Success) {
                Log.i(TAG, "updateUsername: Succès de la mise à jour du pseudo vers '$newUsername'.")
                val currentResource = _userProfileData.value
                if (currentResource is Resource.Success && currentResource.data != null) {
                    val updatedUser = currentResource.data.copy(username = newUsername)
                    _userProfileData.postValue(Resource.Success(updatedUser)) // Mise à jour de la source de vérité
                }
            } else if (result is Resource.Error) {
                Log.e(TAG, "updateUsername: Échec de la mise à jour du pseudo: ${result.message}")
            }
        }
    }

    fun updateBio(newBio: String) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _bioUpdateResult.value = Resource.Error("Utilisateur non connecté pour la mise à jour de la biographie.")
            Log.e(TAG, "updateBio: UserID est null, impossible de mettre à jour.")
            return
        }
        Log.d(TAG, "updateBio: Tentative de mise à jour de la bio vers '$newBio' pour UserID: $userId")
        _bioUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            val result = userRepository.updateUserBio(userId, newBio.trim())
            _bioUpdateResult.postValue(result)
            if (result is Resource.Success) {
                Log.i(TAG, "updateBio: Succès de la mise à jour de la bio.")
                val currentResource = _userProfileData.value
                if (currentResource is Resource.Success && currentResource.data != null) {
                    val updatedUser = currentResource.data.copy(bio = newBio.trim())
                    _userProfileData.postValue(Resource.Success(updatedUser)) // Mise à jour de la source de vérité
                }
            } else if (result is Resource.Error) {
                Log.e(TAG, "updateBio: Échec de la mise à jour de la bio: ${result.message}")
            }
        }
    }

    fun updateCity(newCity: String) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _cityUpdateResult.value = Resource.Error("Utilisateur non connecté pour la mise à jour de la ville.")
            Log.e(TAG, "updateCity: UserID est null, impossible de mettre à jour.")
            return
        }
        Log.d(TAG, "updateCity: Tentative de mise à jour de la ville vers '$newCity' pour UserID: $userId")
        _cityUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            val result = userRepository.updateUserCity(userId, newCity.trim())
            _cityUpdateResult.postValue(result)

            if (result is Resource.Success<*>) {
                Log.i(TAG, "updateCity: Succès de la mise à jour de la ville.")
                val currentResource = _userProfileData.value
                if (currentResource is Resource.Success && currentResource.data != null) {
                    val updatedUser = currentResource.data.copy(city = newCity.trim())
                    _userProfileData.postValue(Resource.Success(updatedUser)) // Mise à jour de la source de vérité
                }
            } else if (result is Resource.Error<*>) {
                Log.e(TAG, "updateCity: Échec de la mise à jour de la ville: ${result.message}")
            }
        }
    }

    /**
     * Updates or clears the current reading for the logged-in user.
     * @param userBookReading The UserBookReading object to save/update, or null to clear the current reading.
     */
    fun updateCurrentReading(userBookReading: UserBookReading?) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            _currentReadingUiState.value = _currentReadingUiState.value.copy(error = "Utilisateur non connecté pour gérer la lecture.")
            Log.e(TAG, "updateCurrentReading: UserID is null, cannot update current reading.")
            return
        }

        viewModelScope.launch {
            _currentReadingUiState.value = _currentReadingUiState.value.copy(isLoading = true, error = null)
            val result = userRepository.updateCurrentReading(userId, userBookReading)
            when (result) {
                is Resource.Success -> {
                    Log.i(TAG, "updateCurrentReading: Lecture en cours mise à jour avec succès.")
                    // L'observation du flow getCurrentReading dans le init block va automatiquement mettre à jour _currentReadingUiState.
                }
                is Resource.Error -> {
                    Log.e(TAG, "updateCurrentReading: Erreur lors de la mise à jour: ${result.message}")
                    _currentReadingUiState.value = _currentReadingUiState.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> { /* Ne devrait pas arriver pour une fonction suspendue retournant Resource */ }
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