package com.lesmangeursdurouleau.app.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentRegisterBinding
import com.lesmangeursdurouleau.app.utils.auth.AuthErrorConverter // <-- NOUVEL IMPORT
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        private const val TAG = "RegisterFragment"
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
        setupClickListeners()
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
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { // <-- AJOUT DE LA VÉRIFICATION DE FORMAT
                binding.tilEmailRegister.error = getString(R.string.error_invalid_email) // <-- Cette chaîne doit être dans strings.xml
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
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.registration_successful_check_email, result.user?.email ?: "votre adresse email"),
                        Toast.LENGTH_LONG
                    ).show()
                    parentFragmentManager.popBackStack() // Redirection vers l'écran de connexion
                    authViewModel.consumeRegistrationResult()
                }
                is AuthResultWrapper.Error -> {
                    Log.e(TAG, "Registration result: Error - ${result.exception?.message}", result.exception)
                    binding.progressBarRegister.visibility = View.GONE
                    setFieldsEnabled(true)
                    // Utilise AuthErrorConverter pour obtenir le message d'erreur
                    val errorMessage = AuthErrorConverter.getFirebaseAuthErrorMessage(requireContext(), result.exception, result.errorCode)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.registration_failed, errorMessage),
                        Toast.LENGTH_LONG
                    ).show()
                    authViewModel.consumeRegistrationResult()
                }
                is AuthResultWrapper.EmailNotVerified -> {
                    Log.e(TAG, "Registration result: Unexpected EmailNotVerified state during registration.")
                    binding.progressBarRegister.visibility = View.GONE
                    setFieldsEnabled(true)
                    Toast.makeText(
                        requireContext(),
                        "Erreur inattendue durant l'inscription (statut: email non vérifié).",
                        Toast.LENGTH_LONG
                    ).show()
                    authViewModel.consumeRegistrationResult()
                }
                // Le cas AuthResultWrapper.AccountExistsWithDifferentCredential est supprimé car non pertinent ici.
                // FirebaseAuthentication ne lève pas ce type d'erreur pour une inscription simple email/mdp.
                else -> { // Ajout d'un else pour capturer d'autres cas inattendus et consommer le résultat
                    Log.e(TAG, "Registration result: Unhandled AuthResultWrapper type: $result")
                    binding.progressBarRegister.visibility = View.GONE
                    setFieldsEnabled(true)
                    authViewModel.consumeRegistrationResult()
                }
            }
        }
    }

    // getFirebaseAuthErrorMessage est supprimé car nous utilisons maintenant AuthErrorConverter.

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