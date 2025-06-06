package com.lesmangeursdurouleau.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentRegisterBinding
// AuthResultWrapper est déjà importé via le AuthViewModel mais une importation explicite ici ne fait pas de mal
// import com.lesmangeursdurouleau.app.ui.auth.AuthResultWrapper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

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

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsernameRegister.text.toString().trim()
            val email = binding.etEmailRegister.text.toString().trim()
            val password = binding.etPasswordRegister.text.toString().trim()
            val confirmPassword = binding.etConfirmPasswordRegister.text.toString().trim()

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
                authViewModel.registerUser(email, password, username)
            }
        }

        binding.tvGoToLogin.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        authViewModel.registrationResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthResultWrapper.Loading -> {
                    binding.progressBarRegister.visibility = View.VISIBLE
                    setFieldsEnabled(false)
                }
                is AuthResultWrapper.Success -> {
                    binding.progressBarRegister.visibility = View.GONE
                    // MODIFIÉ: Message pour informer de l'envoi de l'email de vérification
                    Toast.makeText(
                        requireContext(),
                        "Inscription réussie ! Un email de vérification a été envoyé à ${result.user?.email ?: "votre adresse email"}. Veuillez consulter votre boîte de réception pour activer votre compte.",
                        Toast.LENGTH_LONG
                    ).show()
                    parentFragmentManager.popBackStack() // Redirection vers l'écran de connexion
                }
                is AuthResultWrapper.Error -> {
                    binding.progressBarRegister.visibility = View.GONE
                    setFieldsEnabled(true)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.registration_failed, result.exception.message ?: "Erreur inconnue"),
                        Toast.LENGTH_LONG
                    ).show()
                }

                is AuthResultWrapper.EmailNotVerified -> {
                    binding.progressBarRegister.visibility = View.GONE
                    setFieldsEnabled(true)
                    Toast.makeText(
                        requireContext(),
                        "Erreur inattendue durant l'inscription (statut: email non vérifié).",
                        Toast.LENGTH_LONG
                    ).show()
                }
                null -> {
                    // Ne rien faire ou logguer si vous souhaitez observer la réinitialisation du LiveData
                }
                is AuthResultWrapper.AccountExistsWithDifferentCredential -> TODO() // Gérer ce cas si nécessaire
            }
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

    }
}