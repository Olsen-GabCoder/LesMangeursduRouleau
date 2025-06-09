package com.lesmangeursdurouleau.app.ui.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController // Gardé au cas où vous l'utilisiez pour d'autres navigations
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.lesmangeursdurouleau.app.MainActivity
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentLoginBinding
import com.lesmangeursdurouleau.app.utils.auth.AuthErrorConverter // <-- NOUVEL IMPORT
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    // isResetPasswordMode et la logique associée sont supprimés
    // car la réinitialisation de mot de passe est maintenant gérée exclusivement par ForgotPasswordDialog.

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

        // Afficher un message si l'utilisateur vient juste de s'inscrire
        if (authViewModel.justRegistered.value == true) {
            Toast.makeText(requireContext(), getString(R.string.registration_successful_check_email), Toast.LENGTH_LONG).show()
            authViewModel.consumeJustRegisteredEvent()
        }

        setupObservers()
        setupClickListeners()
        // updateUiForMode() est supprimé car la logique de mode n'existe plus

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
            // Utilise replace pour naviguer vers RegisterFragment et addToBackStack pour le retour
            parentFragmentManager.beginTransaction()
                .replace(R.id.auth_fragment_container, RegisterFragment()) // Vérifiez cet ID si besoin
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
            Log.d(TAG, "Lien 'Mot de passe oublié ?' cliqué. Lancement du dialogue de réinitialisation.")
            // Lancement du ForgotPasswordDialog directement
            ForgotPasswordDialog.newInstance().show(parentFragmentManager, ForgotPasswordDialog.TAG)
        }

        // Les listeners pour btnSendResetEmail et tvBackToLogin sont supprimés
        // car la logique de réinitialisation est déplacée vers ForgotPasswordDialog.
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
                    // Utilise AuthErrorConverter pour obtenir le message d'erreur
                    val errorMessage = AuthErrorConverter.getFirebaseAuthErrorMessage(requireContext(), result.exception, result.errorCode)
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    authViewModel.consumeLoginResult()
                }
                is AuthResultWrapper.EmailNotVerified -> {
                    setUiLoadingState(false)
                    Toast.makeText(requireContext(), getString(R.string.email_not_verified_message_login_prompt), Toast.LENGTH_LONG).show()
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

        // L'observation de authViewModel.passwordResetResult est supprimée de LoginFragment.
        // Elle est maintenant gérée exclusivement par ForgotPasswordDialog.
    }

    private fun showLinkAccountDialog(email: String, pendingCredential: AuthCredential) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_request_password_for_linking, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.et_password_for_linking)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.link_account_title))
            .setMessage(getString(R.string.link_account_message, email))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.link_button_text)) { dialog, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    authViewModel.linkGoogleAccountToExistingEmailUser(pendingCredential, email, password)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.password_required_for_linking), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                Toast.makeText(requireContext(), getString(R.string.linking_cancelled), Toast.LENGTH_SHORT).show()
                authViewModel.consumeLoginResult()
                dialog.dismiss()
            }
            .setOnCancelListener {
                Toast.makeText(requireContext(), getString(R.string.linking_cancelled), Toast.LENGTH_SHORT).show()
                authViewModel.consumeLoginResult()
            }
            .show()
    }


    // updateUiForMode() est supprimé car la logique de mode n'existe plus dans ce fragment.
    // Le titre est maintenant statique dans le layout ou peut être mis à jour au besoin.
    // Si vous souhaitez changer dynamiquement le titre "Connexion", cela peut être fait directement dans onViewCreated
    // binding.tvLoginTitle.text = getString(R.string.login_title)

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

        // Tous les éléments de l'UI sont gérés ensemble sans distinction de mode
        binding.etEmailLogin.isEnabled = enableInteractions
        binding.tilEmailLogin.isEnabled = enableInteractions
        binding.etPasswordLogin.isEnabled = enableInteractions
        binding.tilPasswordLogin.isEnabled = enableInteractions

        binding.btnLogin.isEnabled = enableInteractions
        binding.btnGoogleSignIn.isEnabled = enableInteractions
        binding.tvGoToRegister.isClickable = enableInteractions
        binding.tvForgotPassword.isClickable = enableInteractions
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}