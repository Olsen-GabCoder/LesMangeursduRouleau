package com.lesmangeursdurouleau.app.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
// MODIFIÉ: Import de UserProfileRepository et suppression de UserRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// AuthResultWrapper modifié pour gérer plus de cas et fournir plus de détails
sealed class AuthResultWrapper {
    data class Success(val user: FirebaseUser? = null) : AuthResultWrapper()
    data class Error(val exception: Exception, val errorCode: String? = null) : AuthResultWrapper()
    object EmailNotVerified : AuthResultWrapper()
    object Loading : AuthResultWrapper()
    data class AccountExistsWithDifferentCredential(val email: String, val pendingCredential: AuthCredential) : AuthResultWrapper()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    // MODIFIÉ: Remplacement de UserRepository par UserProfileRepository
    private val userProfileRepository: UserProfileRepository,
    private val firebaseAuthInstance: FirebaseAuth,
    private val firestoreInstance: FirebaseFirestore
) : AndroidViewModel(application) {

    private val _registrationResult = MutableLiveData<AuthResultWrapper?>()
    val registrationResult: LiveData<AuthResultWrapper?> = _registrationResult

    private val _loginResult = MutableLiveData<AuthResultWrapper?>()
    val loginResult: LiveData<AuthResultWrapper?> = _loginResult

    private val _currentUser = MutableLiveData<FirebaseUser?>()
    val currentUser: LiveData<FirebaseUser?> = _currentUser

    private val _justRegistered = MutableLiveData<Boolean>(false)
    val justRegistered: LiveData<Boolean> = _justRegistered

    private val _userDisplayName = MutableLiveData<String?>()
    val userDisplayName: LiveData<String?> = _userDisplayName

    private val _profileUpdateResult = MutableLiveData<Resource<Unit>?>()
    val profileUpdateResult: LiveData<Resource<Unit>?> = _profileUpdateResult

    private val _profilePictureUpdateResult = MutableLiveData<Resource<String>?>()
    val profilePictureUpdateResult: LiveData<Resource<String>?> = _profilePictureUpdateResult

    private val _passwordResetResult = MutableLiveData<AuthResultWrapper?>()
    val passwordResetResult: LiveData<AuthResultWrapper?> = _passwordResetResult

    companion object {
        private const val TAG = "AuthViewModel"
    }

    init {
        _currentUser.value = firebaseAuthInstance.currentUser
        _currentUser.value?.uid?.let { uid ->
            if (uid.isNotEmpty()) {
                fetchUserDisplayName(uid)
            }
        }
    }

    fun registerUser(email: String, password: String, username: String) {
        _registrationResult.value = AuthResultWrapper.Loading
        _justRegistered.value = false // Réinitialiser avant l'opération

        firebaseAuthInstance.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result?.user != null) {
                    val firebaseUser = task.result!!.user!!
                    Log.d(TAG, "Inscription Firebase Auth réussie pour ${firebaseUser.email}")

                    firebaseUser.sendEmailVerification()
                        .addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                Log.d(TAG, "Email de vérification envoyé à ${firebaseUser.email}.")
                            } else {
                                Log.e(TAG, "Échec de l'envoi de l'email de vérification.", verificationTask.exception)
                            }
                        }

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()
                    firebaseUser.updateProfile(profileUpdates)
                        .addOnCompleteListener { profileUpdateTask ->
                            if (profileUpdateTask.isSuccessful) {
                                Log.d(TAG, "Firebase Auth displayName mis à jour en '$username'.")
                            } else {
                                Log.w(TAG, "Échec de la MAJ du displayName Firebase Auth.", profileUpdateTask.exception)
                            }
                        }

                    val userDocument = hashMapOf(
                        "uid" to firebaseUser.uid,
                        "username" to username,
                        "email" to email,
                        "profilePictureUrl" to null,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "isEmailVerified" to false // Important: Initialement false
                    )
                    firestoreInstance.collection("users").document(firebaseUser.uid)
                        .set(userDocument)
                        .addOnSuccessListener {
                            Log.d(TAG, "Profil utilisateur créé dans Firestore pour ${firebaseUser.uid}")
                            _justRegistered.value = true // Indiquer qu'une inscription vient d'avoir lieu
                            firebaseAuthInstance.signOut() // Déconnexion pour forcer la vérification d'email
                            _currentUser.value = null // Mettre à jour le LiveData pour refléter la déconnexion
                            _userDisplayName.value = null // Mettre à jour le LiveData
                            _registrationResult.value = AuthResultWrapper.Success(firebaseUser)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erreur création profil Firestore pour ${firebaseUser.uid}", e)
                            _justRegistered.value = true // Indiquer qu'une inscription vient d'avoir lieu
                            firebaseAuthInstance.signOut() // Déconnexion pour forcer la vérification d'email
                            _currentUser.value = null // Mettre à jour le LiveData pour refléter la déconnexion
                            _userDisplayName.value = null // Mettre à jour le LiveData
                            _registrationResult.value = AuthResultWrapper.Success(firebaseUser) // L'inscription Auth a réussi
                        }
                } else {
                    Log.e(TAG, "Échec inscription Firebase Auth.", task.exception)
                    val exception = task.exception ?: Exception("Erreur d'inscription inconnue")
                    val errorCode = (exception as? FirebaseAuthException)?.errorCode
                    _registrationResult.value = AuthResultWrapper.Error(exception, errorCode)
                }
            }
    }

    fun loginUser(email: String, password: String) {
        _loginResult.value = AuthResultWrapper.Loading
        firebaseAuthInstance.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result?.user != null) {
                    val firebaseUser = task.result!!.user!!
                    firebaseUser.reload().addOnCompleteListener { reloadTask ->
                        if (reloadTask.isSuccessful) {
                            val updatedUser = firebaseAuthInstance.currentUser
                            if (updatedUser != null && updatedUser.isEmailVerified) {
                                Log.d(TAG, "Connexion réussie et email vérifié pour ${updatedUser.email}")
                                _loginResult.value = AuthResultWrapper.Success(updatedUser)
                                _currentUser.value = updatedUser
                                fetchUserDisplayName(updatedUser.uid)
                            } else {
                                Log.w(TAG, "Connexion réussie MAIS email non vérifié pour ${firebaseUser.email}. Déconnexion.")
                                firebaseAuthInstance.signOut()
                                _loginResult.value = AuthResultWrapper.EmailNotVerified
                            }
                        } else {
                            Log.e(TAG, "Échec du rechargement de l'utilisateur.", reloadTask.exception)
                            firebaseAuthInstance.signOut()
                            val exception = reloadTask.exception ?: Exception("Erreur vérification statut email.")
                            val errorCode = (exception as? FirebaseAuthException)?.errorCode
                            _loginResult.value = AuthResultWrapper.Error(exception, errorCode)
                        }
                    }
                } else {
                    Log.e(TAG, "Échec connexion Firebase Auth.", task.exception)
                    val exception = task.exception ?: Exception("Email ou mot de passe incorrect.")
                    val errorCode = (exception as? FirebaseAuthException)?.errorCode
                    _loginResult.value = AuthResultWrapper.Error(exception, errorCode)
                }
            }
    }

    fun signInWithGoogleToken(idToken: String) {
        _loginResult.value = AuthResultWrapper.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuthInstance.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result?.user != null) {
                    val firebaseUser = task.result!!.user!!
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                    if (isNewUser) {
                        Log.d(TAG, "Nouvel utilisateur via Google: ${firebaseUser.uid}")
                        val userDocument = hashMapOf(
                            "uid" to firebaseUser.uid,
                            "username" to (firebaseUser.displayName ?: firebaseUser.email ?: "Utilisateur Google"),
                            "email" to firebaseUser.email,
                            "profilePictureUrl" to (firebaseUser.photoUrl?.toString()),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "isEmailVerified" to true
                        )
                        firestoreInstance.collection("users").document(firebaseUser.uid)
                            .set(userDocument)
                            .addOnSuccessListener {
                                Log.d(TAG, "Profil Firestore créé pour utilisateur Google ${firebaseUser.uid}")
                                _loginResult.value = AuthResultWrapper.Success(firebaseUser)
                                _currentUser.value = firebaseUser
                                fetchUserDisplayName(firebaseUser.uid)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Erreur création profil Firestore Google ${firebaseUser.uid}", e)
                                _loginResult.value = AuthResultWrapper.Success(firebaseUser) // Continue as success for Auth
                                _currentUser.value = firebaseUser
                                fetchUserDisplayName(firebaseUser.uid)
                            }
                    } else {
                        Log.d(TAG, "Utilisateur existant connecté via Google: ${firebaseUser.uid}")
                        _loginResult.value = AuthResultWrapper.Success(firebaseUser)
                        _currentUser.value = firebaseUser
                        fetchUserDisplayName(firebaseUser.uid)
                    }
                } else {
                    Log.e(TAG, "Échec connexion Google avec credential.", task.exception)
                    val exception = task.exception
                    if (exception is FirebaseAuthUserCollisionException) {
                        val collisionEmail = exception.email
                        val pendingGoogleCredential = exception.updatedCredential
                        val credentialToLink = pendingGoogleCredential ?: credential

                        if (collisionEmail != null) {
                            Log.w(TAG, "Collision de compte Google détectée pour l'email: $collisionEmail. Credential pour liaison: $credentialToLink")
                            _loginResult.value = AuthResultWrapper.AccountExistsWithDifferentCredential(collisionEmail, credentialToLink)
                        } else {
                            Log.e(TAG, "Collision de compte Google, mais email non récupérable de l'exception.")
                            _loginResult.value = AuthResultWrapper.Error(exception, exception.errorCode)
                        }
                    } else {
                        val genericException = exception ?: Exception("Erreur connexion Google.")
                        val errorCode = (exception as? FirebaseAuthException)?.errorCode
                        _loginResult.value = AuthResultWrapper.Error(genericException, errorCode)
                    }
                }
            }
    }

    fun linkGoogleAccountToExistingEmailUser(pendingGoogleCredentialToLink: AuthCredential, emailForExistingAccount: String, passwordForExistingAccount: String) {
        _loginResult.value = AuthResultWrapper.Loading
        Log.d(TAG, "Tentative de liaison du compte Google à un compte existant pour: $emailForExistingAccount")

        firebaseAuthInstance.signInWithEmailAndPassword(emailForExistingAccount, passwordForExistingAccount)
            .addOnCompleteListener { signInTask ->
                if (signInTask.isSuccessful && signInTask.result?.user != null) {
                    val existingUser = signInTask.result!!.user!!
                    Log.d(TAG, "Ré-authentification réussie pour $emailForExistingAccount. Tentative de liaison.")

                    existingUser.linkWithCredential(pendingGoogleCredentialToLink)
                        .addOnCompleteListener { linkTask ->
                            if (linkTask.isSuccessful && linkTask.result?.user != null) {
                                val linkedUser = linkTask.result!!.user!!
                                Log.d(TAG, "Liaison du compte Google réussie pour ${linkedUser.email}")
                                _loginResult.value = AuthResultWrapper.Success(linkedUser)
                                _currentUser.value = linkedUser
                                fetchUserDisplayName(linkedUser.uid)
                            } else {
                                Log.e(TAG, "Échec de la liaison du compte Google.", linkTask.exception)
                                val exception = linkTask.exception ?: Exception("Erreur de liaison de compte.")
                                val errorCode = (exception as? FirebaseAuthException)?.errorCode
                                _loginResult.value = AuthResultWrapper.Error(exception, errorCode)
                            }
                        }
                } else {
                    Log.e(TAG, "Échec de la ré-authentification pour $emailForExistingAccount lors de la tentative de liaison.", signInTask.exception)
                    val exception = signInTask.exception ?: Exception("Mot de passe incorrect pour le compte existant.")
                    val errorCode = (exception as? FirebaseAuthException)?.errorCode
                    _loginResult.value = AuthResultWrapper.Error(exception, errorCode)
                }
            }
    }

    private fun fetchUserDisplayName(userId: String) {
        firestoreInstance.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    _userDisplayName.value = document.getString("username")
                    Log.d(TAG, "Nom d'utilisateur récupéré: ${_userDisplayName.value} pour UID: $userId")
                } else {
                    Log.d(TAG, "Aucun document utilisateur trouvé dans Firestore pour UID: $userId")
                    _userDisplayName.value = firebaseAuthInstance.currentUser?.displayName
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Erreur lors de la récupération du nom d'utilisateur depuis Firestore:", exception)
                _userDisplayName.value = firebaseAuthInstance.currentUser?.displayName
            }
    }

    fun sendPasswordResetEmail(email: String) {
        if (email.isBlank()) {
            _passwordResetResult.value = AuthResultWrapper.Error(IllegalArgumentException("L'adresse e-mail ne peut pas être vide."))
            return
        }
        Log.d(TAG, "Tentative d'envoi de l'email de réinitialisation à: $email")
        _passwordResetResult.value = AuthResultWrapper.Loading
        firebaseAuthInstance.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "Email de réinitialisation envoyé avec succès à $email.")
                    _passwordResetResult.value = AuthResultWrapper.Success()
                } else {
                    Log.w(TAG, "Échec de l'envoi de l'email de réinitialisation à $email.", task.exception)
                    val exception = task.exception ?: Exception("Échec envoi e-mail réinitialisation.")
                    val errorCode = (exception as? FirebaseAuthException)?.errorCode
                    _passwordResetResult.value = AuthResultWrapper.Error(exception, errorCode)
                }
            }
    }

    fun consumePasswordResetResult() {
        Log.d(TAG, "Consommation de passwordResetResult.")
        _passwordResetResult.value = null
    }

    fun consumeLoginResult() {
        Log.d(TAG, "Consommation de loginResult.")
        _loginResult.value = null
    }

    fun consumeRegistrationResult() {
        Log.d(TAG, "Consommation de registrationResult.")
        _registrationResult.value = null
    }

    fun updateUserProfile(userId: String, username: String) {
        if (userId.isBlank()) {
            _profileUpdateResult.value = Resource.Error("ID utilisateur invalide pour la mise à jour du profil.")
            Log.e(TAG, "updateUserProfile: userId est vide.")
            return
        }
        if (username.isBlank()) {
            _profileUpdateResult.value = Resource.Error("Le pseudo ne peut pas être vide.")
            Log.e(TAG, "updateUserProfile: username est vide.")
            return
        }

        _profileUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            Log.d(TAG, "updateUserProfile: Lancement de la coroutine pour appeler userProfileRepository.updateUserProfile")
            // MODIFIÉ: Appel sur userProfileRepository
            val result = userProfileRepository.updateUserProfile(userId, username)
            _profileUpdateResult.postValue(result)

            if (result is Resource.Success) {
                _userDisplayName.value = username
                Log.i(TAG, "updateUserProfile: Pseudo mis à jour avec succès via UserProfileRepository. Nouveau pseudo: $username")
            } else if (result is Resource.Error) {
                Log.e(TAG, "updateUserProfile: Échec de la mise à jour du pseudo via UserProfileRepository: ${result.message}")
            }
        }
    }

    fun updateProfilePicture(userId: String, imageData: ByteArray) {
        Log.d(TAG, "updateProfilePicture: Début pour UserID: $userId")
        _profilePictureUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            Log.d(TAG, "updateProfilePicture: Lancement de la coroutine pour appeler userProfileRepository.updateUserProfilePicture")
            // MODIFIÉ: Appel sur userProfileRepository
            val result = userProfileRepository.updateUserProfilePicture(userId, imageData)
            _profilePictureUpdateResult.postValue(result)

            if (result is Resource.Success) {
                Log.i(TAG, "updateProfilePicture: Mise à jour de la photo de profil réussie. Nouvelle URL: ${result.data}")
            } else if (result is Resource.Error) {
                Log.e(TAG, "updateProfilePicture: Échec de la mise à jour de la photo de profil: ${result.message}")
            }
        }
    }

    fun logoutUser() {
        Log.d(TAG, "Déconnexion de l'utilisateur.")
        firebaseAuthInstance.signOut()
        _currentUser.value = null
        _userDisplayName.value = null
        _justRegistered.value = false
        _loginResult.value = null
        _registrationResult.value = null
        _profileUpdateResult.value = null
        _profilePictureUpdateResult.value = null
        _passwordResetResult.value = null
    }

    fun consumeJustRegisteredEvent() {
        Log.d(TAG, "Consommation de justRegisteredEvent.")
        _justRegistered.value = false
    }
}