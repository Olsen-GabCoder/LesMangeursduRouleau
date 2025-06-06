package com.lesmangeursdurouleau.app.ui.auth

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.DialogForgotPasswordBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ForgotPasswordDialog : DialogFragment() {

    private var _binding: DialogForgotPasswordBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        const val TAG = "ForgotPasswordDialog"
        fun newInstance(): ForgotPasswordDialog {
            return ForgotPasswordDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogForgotPasswordBinding.inflate(inflater, container, false)
        // Pour les coins arrondis du dialogue (si votre style de dialogue le permet)
        dialog?.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background) // Créez ce drawable
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        setupObservers()
    }

    private fun setupClickListeners() {
        binding.btnSendResetEmail.setOnClickListener {
            binding.tilForgotPasswordEmail.error = null
            val email = binding.etForgotPasswordEmail.text.toString().trim()
            if (isValidEmail(email)) {
                authViewModel.sendPasswordResetEmail(email)
            } else {
                binding.tilForgotPasswordEmail.error = getString(R.string.error_invalid_email)
            }
        }
        // Vous pouvez ajouter un bouton "Annuler" explicite ici si nécessaire
        // par exemple, pour appeler dismiss()
    }

    private fun setupObservers() {
        authViewModel.passwordResetResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe // Ignorer si null (déjà consommé ou état initial)

            when (result) {
                is AuthResultWrapper.Loading -> {
                    setLoadingState(true)
                }
                is AuthResultWrapper.Success -> {
                    setLoadingState(false)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.password_reset_email_sent_success),
                        Toast.LENGTH_LONG
                    ).show()
                    authViewModel.consumePasswordResetResult() // Consommer après avoir traité
                    dismiss() // Fermer la boîte de dialogue
                }
                is AuthResultWrapper.Error -> {
                    setLoadingState(false)
                    val errorMessage = getFirebaseAuthErrorMessage(result.exception, result.errorCode)
                    Toast.makeText(
                        requireContext(),
                        // Utiliser un formatteur pour le message d'erreur
                        getString(R.string.password_reset_failed_generic_format, errorMessage),
                        Toast.LENGTH_LONG
                    ).show()
                    authViewModel.consumePasswordResetResult() // Consommer après avoir traité
                }
                // Les cas EmailNotVerified et AccountExistsWithDifferentCredential ne sont pas attendus ici
                else -> {
                    setLoadingState(false) // État inattendu, réinitialiser le chargement
                    authViewModel.consumePasswordResetResult() // S'assurer de consommer
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBarForgotPassword.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSendResetEmail.isEnabled = !isLoading
        binding.etForgotPasswordEmail.isEnabled = !isLoading
        // Si vous avez un bouton Annuler, vous voudrez peut-être aussi gérer son état isEnabled.
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // Fonction utilitaire pour obtenir des messages d'erreur Firebase plus conviviaux
    // Idéalement, à factoriser dans une classe utilitaire si utilisée par plusieurs fragments/dialogs.
    private fun getFirebaseAuthErrorMessage(exception: Exception?, errorCode: String?): String {
        Log.w(TAG, "Auth Error (Dialog): Code: $errorCode, Message: ${exception?.message}", exception)
        return when (errorCode) {
            "ERROR_INVALID_CUSTOM_TOKEN" -> getString(R.string.firebase_error_invalid_custom_token)
            "ERROR_CUSTOM_TOKEN_MISMATCH" -> getString(R.string.firebase_error_custom_token_mismatch)
            "ERROR_INVALID_CREDENTIAL" -> getString(R.string.firebase_error_invalid_credential_generic) // Message générique
            "ERROR_USER_DISABLED" -> getString(R.string.firebase_error_user_disabled)
            "ERROR_USER_TOKEN_EXPIRED" -> getString(R.string.firebase_error_user_token_expired)
            "ERROR_USER_NOT_FOUND" -> getString(R.string.firebase_error_user_not_found_for_reset) // Message spécifique pour le reset
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
        // N'oubliez pas d'ajouter toutes ces chaînes (R.string.firebase_error_...) à votre fichier strings.xml
        // et les nouvelles comme R.string.firebase_error_invalid_credential_generic, R.string.firebase_error_user_not_found_for_reset
        // et R.string.password_reset_failed_generic_format ("La réinitialisation du mot de passe a échoué : %1$s")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // S'assurer que le résultat est consommé si le dialogue est fermé prématurément
        // authViewModel.consumePasswordResetResult() // Déjà géré dans l'observer après chaque cas traité.
        _binding = null
    }

    // Drawable pour les coins arrondis (exemple, à créer dans res/drawable/dialog_rounded_background.xml):
    // <?xml version="1.0" encoding="utf-8"?>
    // <shape xmlns:android="http://schemas.android.com/apk/res/android">
    //     <solid android:color="@android:color/white"/> <!-- ou votre couleur de fond de dialogue -->
    //     <corners android:radius="16dp"/> <!-- Ajustez le rayon comme vous le souhaitez -->
    // </shape>
}