package com.lesmangeursdurouleau.app.ui.auth

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import com.lesmangeursdurouleau.app.utils.auth.AuthErrorConverter
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
        // L'arrière-plan du dialogue est rendu transparent dans onStart() pour ne pas masquer les coins arrondis du MaterialCardView
        // Vous pouvez retirer la ligne suivante si elle posait problème ou si vous n'avez plus besoin de ce drawable spécifique
        // dialog?.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // C'est ici que nous définissons la taille du dialogue et rendons son fond transparent
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, // Le dialogue prendra toute la largeur disponible
            ViewGroup.LayoutParams.WRAP_CONTENT // Le dialogue s'adaptera à la hauteur de son contenu
        )
        // Rendre l'arrière-plan par défaut du dialogue transparent pour voir les coins arrondis de la MaterialCardView
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override  fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
            result ?: return@observe

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
                    authViewModel.consumePasswordResetResult()
                    dismiss()
                }
                is AuthResultWrapper.Error -> {
                    setLoadingState(false)
                    // Utilise AuthErrorConverter pour obtenir le message d'erreur
                    val errorMessage = AuthErrorConverter.getFirebaseAuthErrorMessage(requireContext(), result.exception, result.errorCode)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.password_reset_failed_generic_format, errorMessage),
                        Toast.LENGTH_LONG
                    ).show()
                    authViewModel.consumePasswordResetResult()
                }
                // Les cas EmailNotVerified et AccountExistsWithDifferentCredential ne sont pas attendus ici,
                // mais le bloc 'else' assure que le chargement est désactivé et le résultat consommé.
                else -> {
                    setLoadingState(false)
                    authViewModel.consumePasswordResetResult()
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBarForgotPassword.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSendResetEmail.isEnabled = !isLoading
        binding.etForgotPasswordEmail.isEnabled = !isLoading
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}