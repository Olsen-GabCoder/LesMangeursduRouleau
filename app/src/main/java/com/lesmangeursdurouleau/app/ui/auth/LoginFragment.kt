package com.lesmangeursdurouleau.app.ui.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText // Pour le dialogue de liaison de compte
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog // Pour le dialogue de liaison de compte
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.lesmangeursdurouleau.app.MainActivity
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    private var isResetPasswordMode = false

    companion object {
        private const val TAG = "LoginFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    if (account != null && account.idToken != null) {
                        Log.d(TAG, "Google Sign-In réussi, préparation pour l'envoi du token.")
                        authViewModel.signInWithGoogleToken(account.idToken!!)
                    } else {
                        Log.e(TAG, "Google Sign-In: idToken est null")
                        Toast.makeText(requireContext(), getString(R.string.error_google_token_null), Toast.LENGTH_LONG).show()
                        setUiLoadingState(false)
                    }
                } catch (e: ApiException) {
                    Log.w(TAG, "Google Sign-In a échoué code: ${e.statusCode}", e)
                    val message = getString(R.string.error_google_signin_failed_api, e.statusCode.toString())
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    setUiLoadingState(false)
                }
            } else {
                Log.w(TAG, "Google Sign-In annulé/échoué, resultCode: ${result.resultCode}")
                if (result.resultCode != Activity.RESULT_CANCELED) { // Ne pas afficher de toast si l'utilisateur a annulé
                    Toast.makeText(requireContext(), getString(R.string.error_google_signin_failed), Toast.LENGTH_SHORT).show()
                }
                setUiLoadingState(false) // Assurer la réinitialisation de l'UI
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (authViewModel.justRegistered.value == true) {
            Toast.makeText(requireContext(), getString(R.string.registration_successful_check_email), Toast.LENGTH_LONG).show()
            authViewModel.consumeJustRegisteredEvent()
        }

        setupObservers()
        setupClickListeners()
        updateUiForMode()

        // Observer currentUser pour déconnecter GoogleSignInClient si l'utilisateur se déconnecte
        authViewModel.currentUser.observe(viewLifecycleOwner) { firebaseUser ->
            if (firebaseUser == null) {
                // Si l'utilisateur est déconnecté de Firebase, s'assurer qu'il est aussi déconnecté de Google
                // pour permettre un nouveau choix de compte Google la prochaine fois.
                googleSignInClient.signOut().addOnCompleteListener {
                    Log.d(TAG, "GoogleSignInClient déconnecté suite à la déconnexion Firebase.")
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmailLogin.text.toString().trim()
            val password = binding.etPasswordLogin.text.toString().trim()

            binding.tilEmailLogin.error = null
            binding.tilPasswordLogin.error = null

            var isValid = true
            if (email.isEmpty()) {
                binding.tilEmailLogin.error = getString(R.string.error_field_required)
                isValid = false
            }
            if (password.isEmpty()) {
                binding.tilPasswordLogin.error = getString(R.string.error_field_required)
                isValid = false
            }

            if (isValid) {
                authViewModel.loginUser(email, password)
            } else {
                Toast.makeText(requireContext(), getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvGoToRegister.setOnClickListener {
            // Assurez-vous que l'action ou l'ID du conteneur est correct
            // Exemple avec Navigation Component:
            // findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
            // Exemple avec FragmentTransaction (si vous gérez manuellement):
            parentFragmentManager.beginTransaction()
                .replace(R.id.auth_fragment_container, RegisterFragment()) // Vérifiez cet ID
                .addToBackStack(null)
                .commit()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            Log.d(TAG, "Bouton Google Sign-In cliqué.")
            setUiLoadingState(true)
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.tvForgotPassword.setOnClickListener {
            Log.d(TAG, "Lien 'Mot de passe oublié ?' cliqué.")
            isResetPasswordMode = true
            updateUiForMode()
            // Alternative: Lancer ForgotPasswordDialog
            // ForgotPasswordDialog.newInstance().show(parentFragmentManager, ForgotPasswordDialog.TAG)
        }

        binding.btnSendResetEmail.setOnClickListener {
            val email = binding.etEmailLogin.text.toString().trim()
            if (email.isEmpty()) {
                binding.tilEmailLogin.error = getString(R.string.email_required_for_reset)
            } else {
                binding.tilEmailLogin.error = null
                Log.d(TAG, "Demande de réinitialisation pour l'email: $email")
                authViewModel.sendPasswordResetEmail(email)
            }
        }

        binding.tvBackToLogin.setOnClickListener {
            Log.d(TAG, "Lien 'Retour à la connexion' cliqué.")
            isResetPasswordMode = false
            binding.tilEmailLogin.error = null // Effacer l'erreur potentielle
            updateUiForMode()
        }
    }

    private fun setupObservers() {
        authViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe // Ignorer si null (après consommation par exemple)

            when (result) {
                is AuthResultWrapper.Loading -> setUiLoadingState(true)
                is AuthResultWrapper.Success -> {
                    setUiLoadingState(false)
                    val successMessage = result.user?.displayName ?: result.user?.email ?: getString(R.string.default_username)
                    Toast.makeText(requireContext(), getString(R.string.login_successful, successMessage), Toast.LENGTH_LONG).show()
                    navigateToMainActivity()
                    authViewModel.consumeLoginResult()
                }
                is AuthResultWrapper.Error -> {
                    setUiLoadingState(false)
                    val errorMessage = getFirebaseAuthErrorMessage(result.exception, result.errorCode)
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    authViewModel.consumeLoginResult()
                }
                is AuthResultWrapper.EmailNotVerified -> {
                    setUiLoadingState(false)
                    Toast.makeText(requireContext(), getString(R.string.email_not_verified_message_login_prompt), Toast.LENGTH_LONG).show()
                    // Optionnel: Proposer de renvoyer l'email de vérification
                    authViewModel.consumeLoginResult()
                }
                is AuthResultWrapper.AccountExistsWithDifferentCredential -> {
                    setUiLoadingState(false)
                    Log.i(TAG, "Collision de compte détectée pour ${result.email}. Demande de liaison.")
                    showLinkAccountDialog(result.email, result.pendingCredential)
                    // Ne pas consommer ici, car le dialogue peut mener à une autre action sur loginResult
                }
            }
        }

        authViewModel.passwordResetResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe

            when (result) {
                is AuthResultWrapper.Loading -> setUiLoadingState(true) // Garder l'UI désactivée pendant la réinitialisation
                is AuthResultWrapper.Success -> {
                    setUiLoadingState(false)
                    Toast.makeText(requireContext(), getString(R.string.password_reset_email_sent_success), Toast.LENGTH_LONG).show()
                    // Optionnel: revenir automatiquement au mode connexion
                    // isResetPasswordMode = false
                    // updateUiForMode()
                    authViewModel.consumePasswordResetResult()
                }
                is AuthResultWrapper.Error -> {
                    setUiLoadingState(false)
                    val errorMessage = getFirebaseAuthErrorMessage(result.exception, result.errorCode)
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    authViewModel.consumePasswordResetResult()
                }
                // Les autres cas (EmailNotVerified, AccountExistsWithDifferentCredential) ne sont pas attendus ici
                else -> {
                    setUiLoadingState(false) // Cas de sécurité
                    authViewModel.consumePasswordResetResult() // S'assurer de consommer
                }
            }
        }
    }

    private fun showLinkAccountDialog(email: String, pendingCredential: AuthCredential) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_request_password_for_linking, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.et_password_for_linking)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.link_account_title))
            // Vous devrez ajouter R.string.link_account_message dans vos strings.xml
            .setMessage(getString(R.string.link_account_message, email))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.link_button_text)) { dialog, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    authViewModel.linkGoogleAccountToExistingEmailUser(pendingCredential, email, password)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.password_required_for_linking), Toast.LENGTH_SHORT).show()
                    // Redemander ou annuler ? Pour l'instant, juste un toast.
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                Toast.makeText(requireContext(), getString(R.string.linking_cancelled), Toast.LENGTH_SHORT).show()
                authViewModel.consumeLoginResult() // Consommer l'état de collision si l'utilisateur annule
                dialog.dismiss()
            }
            .setOnCancelListener {
                // Gérer le cas où l'utilisateur annule en cliquant en dehors du dialogue
                Toast.makeText(requireContext(), getString(R.string.linking_cancelled), Toast.LENGTH_SHORT).show()
                authViewModel.consumeLoginResult()
            }
            .show()
        // NOTE: Vous devez créer le layout R.layout.dialog_request_password_for_linking
        // contenant un TextInputEditText avec l'ID et_password_for_linking.
        // Exemple de contenu pour dialog_request_password_for_linking.xml:
        // <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        //     android:layout_width="match_parent" android:layout_height="wrap_content"
        //     android:orientation="vertical" android:padding="24dp">
        //     <com.google.android.material.textfield.TextInputLayout
        //         android:layout_width="match_parent" android:layout_height="wrap_content"
        //         android:hint="@string/password_hint_for_linking">
        //         <com.google.android.material.textfield.TextInputEditText
        //             android:id="@+id/et_password_for_linking"
        //             android:layout_width="match_parent" android:layout_height="wrap_content"
        //             android:inputType="textPassword"/>
        //     </com.google.android.material.textfield.TextInputLayout>
        // </LinearLayout>
        // N'oubliez pas d'ajouter les strings comme @string/password_hint_for_linking
    }


    private fun updateUiForMode() {
        if (isResetPasswordMode) {
            binding.tvLoginTitle.text = getString(R.string.reset_password_title) // Utilisez R.string.reset_password_title
            binding.groupLoginElements.visibility = View.GONE
            binding.groupResetPasswordElements.visibility = View.VISIBLE
            binding.etEmailLogin.requestFocus()
        } else {
            binding.tvLoginTitle.text = getString(R.string.login_title) // Utilisez R.string.login_title
            binding.groupLoginElements.visibility = View.VISIBLE
            binding.groupResetPasswordElements.visibility = View.GONE
            binding.tilEmailLogin.error = null
        }
        // Réinitialiser la ProgressBar si aucun chargement n'est en cours
        val isLoadingLogin = authViewModel.loginResult.value is AuthResultWrapper.Loading
        val isLoadingReset = authViewModel.passwordResetResult.value is AuthResultWrapper.Loading
        if (!isLoadingLogin && !isLoadingReset) {
            setUiLoadingState(false)
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(requireActivity(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun setUiLoadingState(isLoading: Boolean) {
        binding.progressBarLogin.visibility = if (isLoading) View.VISIBLE else View.GONE

        // Désactiver/Réactiver tous les éléments interactifs en fonction de l'état de chargement
        val enableInteractions = !isLoading

        // Éléments communs
        binding.etEmailLogin.isEnabled = enableInteractions

        // Éléments spécifiques au mode
        if (isResetPasswordMode) {
            binding.btnSendResetEmail.isEnabled = enableInteractions
            binding.tvBackToLogin.isClickable = enableInteractions
            // Désactiver les éléments de connexion
            binding.tilPasswordLogin.isEnabled = false
            binding.btnLogin.isEnabled = false
            binding.btnGoogleSignIn.isEnabled = false
            binding.tvGoToRegister.isClickable = false
            binding.tvForgotPassword.isClickable = false // Est désactivé car on est déjà en mode reset
        } else {
            binding.tilPasswordLogin.isEnabled = enableInteractions
            binding.etPasswordLogin.isEnabled = enableInteractions
            binding.btnLogin.isEnabled = enableInteractions
            binding.btnGoogleSignIn.isEnabled = enableInteractions
            binding.tvGoToRegister.isClickable = enableInteractions
            binding.tvForgotPassword.isClickable = enableInteractions
            // Désactiver les éléments de réinitialisation
            binding.btnSendResetEmail.isEnabled = false
            binding.tvBackToLogin.isClickable = false
        }
    }

    // Fonction utilitaire pour obtenir des messages d'erreur Firebase plus conviviaux
    private fun getFirebaseAuthErrorMessage(exception: Exception?, errorCode: String?): String {
        Log.w(TAG, "Auth Error: Code: $errorCode, Message: ${exception?.message}", exception)
        return when (errorCode) {
            "ERROR_INVALID_CUSTOM_TOKEN" -> getString(R.string.firebase_error_invalid_custom_token)
            "ERROR_CUSTOM_TOKEN_MISMATCH" -> getString(R.string.firebase_error_custom_token_mismatch)
            "ERROR_INVALID_CREDENTIAL" -> getString(R.string.firebase_error_invalid_credential) // Souvent pour mauvais format email/mdp, ou token malformé
            "ERROR_USER_DISABLED" -> getString(R.string.firebase_error_user_disabled)
            "ERROR_USER_TOKEN_EXPIRED" -> getString(R.string.firebase_error_user_token_expired)
            "ERROR_USER_NOT_FOUND" -> getString(R.string.firebase_error_user_not_found) // Pour login ou reset password
            "ERROR_INVALID_USER_TOKEN" -> getString(R.string.firebase_error_invalid_user_token)
            "ERROR_OPERATION_NOT_ALLOWED" -> getString(R.string.firebase_error_operation_not_allowed) // Ex: email/pass auth non activé
            "ERROR_EMAIL_ALREADY_IN_USE" -> getString(R.string.firebase_error_email_already_in_use) // Pour inscription
            "ERROR_WEAK_PASSWORD" -> getString(R.string.firebase_error_weak_password) // Pour inscription
            "ERROR_WRONG_PASSWORD" -> getString(R.string.firebase_error_wrong_password) // Pour login
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> getString(R.string.firebase_error_account_exists_with_different_credential) // Collision
            "ERROR_REQUIRES_RECENT_LOGIN" -> getString(R.string.firebase_error_requires_recent_login)
            "ERROR_NETWORK_REQUEST_FAILED" -> getString(R.string.firebase_error_network_request_failed)
            // Pour les exceptions non FirebaseAuthException ou codes non listés
            else -> exception?.message ?: getString(R.string.unknown_error)
        }
        // N'oubliez pas d'ajouter toutes ces chaînes (R.string.firebase_error_...) à votre fichier strings.xml
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}