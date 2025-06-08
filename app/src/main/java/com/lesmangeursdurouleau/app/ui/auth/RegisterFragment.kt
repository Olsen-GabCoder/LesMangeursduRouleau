package com.lesmangeursdurouleau.app.ui.auth

import android.os.Bundle
import android.util.Log // Ajout de l'import pour Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuthException // Import pour les codes d'erreur Firebase
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        private const val TAG = "RegisterFragment" // Ajout d'un TAG pour les logs du fragment
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners() // Nouvelle méthode pour regrouper les listeners
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val username = binding.etUsernameRegister.text.toString().trim()
            val email = binding.etEmailRegister.text.toString().trim()
            val password = binding.etPasswordRegister.text.toString().trim()
            val confirmPassword = binding.etConfirmPasswordRegister.text.toString().trim()

            // Réinitialiser les erreurs des champs avant la validation
            binding.tilUsernameRegister.error = null
            binding.tilEmailRegister.error = null
            binding.tilPasswordRegister.error = null
            binding.tilConfirmPasswordRegister.error = null

            var isValid = true

            if (username.isEmpty()) {
                binding.tilUsernameRegister.error = getString(R.string.error_field_required)
                isValid = false
            }
            if (email.isEmpty()) {
                binding.tilEmailRegister.error = getString(R.string.error_field_required)
                isValid = false
            }
            if (password.isEmpty()) {
                binding.tilPasswordRegister.error = getString(R.string.error_field_required)
                isValid = false
            } else if (password.length < 6) {
                binding.tilPasswordRegister.error = getString(R.string.error_password_too_short)
                isValid = false
            }
            if (confirmPassword.isEmpty()) {
                binding.tilConfirmPasswordRegister.error = getString(R.string.error_field_required)
                isValid = false
            } else if (password != confirmPassword) {
                binding.tilConfirmPasswordRegister.error = getString(R.string.error_passwords_do_not_match)
                isValid = false
            }

            if (isValid) {
                Log.d(TAG, "Tentative d'inscription avec email: $email, username: $username")
                authViewModel.registerUser(email, password, username)
            } else {
                Toast.makeText(requireContext(), getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvGoToLogin.setOnClickListener {
            Log.d(TAG, "Navigation vers LoginFragment via popBackStack.")
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        authViewModel.registrationResult.observe(viewLifecycleOwner) { result ->
            // Si le résultat est null, cela signifie qu'il a déjà été consommé, on ne fait rien.
            result ?: return@observe

            when (result) {
                is AuthResultWrapper.Loading -> {
                    Log.d(TAG, "Registration result: Loading")
                    binding.progressBarRegister.visibility = View.VISIBLE
                    setFieldsEnabled(false)
                }
                is AuthResultWrapper.Success -> {
                    Log.d(TAG, "Registration result: Success for ${result.user?.email}")
                    binding.progressBarRegister.visibility = View.GONE
                    // Message modifié pour être plus clair sur la vérification d'email
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.registration_successful_check_email, result.user?.email ?: "votre adresse email"),
                        Toast.LENGTH_LONG
                    ).show()
                    parentFragmentManager.popBackStack() // Redirection vers l'écran de connexion
                    authViewModel.consumeRegistrationResult() // Consommer le résultat après traitement
                }
                is AuthResultWrapper.Error -> {
                    Log.e(TAG, "Registration result: Error - ${result.exception.message}", result.exception)
                    binding.progressBarRegister.visibility = View.GONE
                    setFieldsEnabled(true)
                    // Utiliser la fonction utilitaire pour obtenir un message d'erreur convivial
                    val errorMessage = getFirebaseAuthErrorMessage(result.exception, result.errorCode)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.registration_failed, errorMessage), // Utiliser errorMessage ici
                        Toast.LENGTH_LONG
                    ).show()
                    authViewModel.consumeRegistrationResult() // Consommer le résultat après traitement
                }
                is AuthResultWrapper.EmailNotVerified -> {
                    // Ce cas n'est pas censé se produire directement après une inscription réussie pour un 'EmailNotVerified'
                    // car le 'Success' est déjà géré. S'il survient ici, c'est une erreur de logique.
                    Log.e(TAG, "Registration result: Unexpected EmailNotVerified state during registration.")
                    binding.progressBarRegister.visibility = View.GONE
                    setFieldsEnabled(true)
                    Toast.makeText(
                        requireContext(),
                        "Erreur inattendue durant l'inscription (statut: email non vérifié).",
                        Toast.LENGTH_LONG
                    ).show()
                    authViewModel.consumeRegistrationResult() // Consommer le résultat après traitement
                }
                // Suppression de la branche 'AuthResultWrapper.AccountExistsWithDifferentCredential'
                // car elle est logiquement inatteignable pour le flux d'inscription email/mot de passe.
                // FirebaseAuthentication ne lève pas ce type de "collision" lors de la création
                // d'un compte email/mdp avec un email déjà existant via un fournisseur social.
                is AuthResultWrapper.AccountExistsWithDifferentCredential -> TODO()
            }
        }
    }

    /**
     * Fonction utilitaire pour obtenir des messages d'erreur Firebase plus conviviaux.
     * Cette fonction est dupliquée dans LoginFragment et ForgotPasswordDialog.
     * Pour une meilleure maintenabilité, elle pourrait être factorisée dans une classe utilitaire.
     */
    private fun getFirebaseAuthErrorMessage(exception: Exception?, errorCode: String?): String {
        Log.w(TAG, "Auth Error: Code: $errorCode, Message: ${exception?.message}", exception)
        return when (errorCode) {
            "ERROR_INVALID_CUSTOM_TOKEN" -> getString(R.string.firebase_error_invalid_custom_token)
            "ERROR_CUSTOM_TOKEN_MISMATCH" -> getString(R.string.firebase_error_custom_token_mismatch)
            "ERROR_INVALID_CREDENTIAL" -> getString(R.string.firebase_error_invalid_credential)
            "ERROR_USER_DISABLED" -> getString(R.string.firebase_error_user_disabled)
            "ERROR_USER_TOKEN_EXPIRED" -> getString(R.string.firebase_error_user_token_expired)
            "ERROR_USER_NOT_FOUND" -> getString(R.string.firebase_error_user_not_found)
            "ERROR_INVALID_USER_TOKEN" -> getString(R.string.firebase_error_invalid_user_token)
            "ERROR_OPERATION_NOT_ALLOWED" -> getString(R.string.firebase_error_operation_not_allowed)
            "ERROR_EMAIL_ALREADY_IN_USE" -> getString(R.string.firebase_error_email_already_in_use)
            "ERROR_WEAK_PASSWORD" -> getString(R.string.firebase_error_weak_password)
            "ERROR_WRONG_PASSWORD" -> getString(R.string.firebase_error_wrong_password)
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> getString(R.string.firebase_error_account_exists_with_different_credential)
            "ERROR_REQUIRES_RECENT_LOGIN" -> getString(R.string.firebase_error_requires_recent_login)
            "ERROR_NETWORK_REQUEST_FAILED" -> getString(R.string.firebase_error_network_request_failed)
            else -> exception?.message ?: getString(R.string.unknown_error)
        }
    }

    private fun setFieldsEnabled(enabled: Boolean) {
        binding.etUsernameRegister.isEnabled = enabled
        binding.etEmailRegister.isEnabled = enabled
        binding.etPasswordRegister.isEnabled = enabled
        binding.etConfirmPasswordRegister.isEnabled = enabled
        binding.btnRegister.isEnabled = enabled
        binding.tvGoToLogin.isClickable = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Consommer le résultat au cas où le fragment est détruit avant le traitement
        authViewModel.consumeRegistrationResult()
    }
}