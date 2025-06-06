package com.lesmangeursdurouleau.app.ui.readings

import android.app.Dialog
import android.os.Bundle
import android.util.Log // AJOUTÉ: Import pour Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.DialogEnterSecretCodeBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EnterSecretCodeDialogFragment : DialogFragment() {

    private var _binding: DialogEnterSecretCodeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EnterSecretCodeViewModel by viewModels()

    companion object {
        const val TAG = "SecretCodeDialog" // AJOUTÉ: TAG pour les logs
        const val REQUEST_KEY = "secret_code_request_key"
        const val BUNDLE_KEY_PERMISSION_GRANTED = "permission_granted_key"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEnterSecretCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // INITIALISATION DE L'ÉTAT DE L'UI AU DÉMARRAGE DU DIALOGUE
        // La barre de progression est cachée, les boutons sont activés, et l'erreur est réinitialisée.
        setUiState(enabled = true, showProgressBar = false, clearError = true)

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnEnterCode.setOnClickListener {
            val enteredCode = binding.etSecretCode.text.toString().trim()
            if (enteredCode.isEmpty()) {
                binding.etSecretCode.error = getString(R.string.error_secret_code_required)
                return@setOnClickListener
            }
            Log.d(TAG, "Attempting to validate secret code.") // AJOUTÉ: Log
            viewModel.validateAndGrantPermission(enteredCode)
        }

        binding.btnCancel.setOnClickListener {
            Log.d(TAG, "Secret code dialog cancelled.") // AJOUTÉ: Log
            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                putBoolean(BUNDLE_KEY_PERMISSION_GRANTED, false)
            })
            dismiss()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // MODIFIÉ: Le SharedFlow n'aura jamais d'état initial 'null',
                // donc la branche 'null' du 'when' est supprimée.
                viewModel.grantPermissionResult.collect { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            Log.d(TAG, "Grant permission result: Loading") // AJOUTÉ: Log
                            setUiState(enabled = false, showProgressBar = true, clearError = true)
                        }
                        is Resource.Success -> {
                            Log.d(TAG, "Grant permission result: Success") // AJOUTÉ: Log
                            setUiState(enabled = true, showProgressBar = false, clearError = true)
                            Toast.makeText(requireContext(), getString(R.string.secret_code_success_message), Toast.LENGTH_SHORT).show()
                            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                                putBoolean(BUNDLE_KEY_PERMISSION_GRANTED, true)
                            })
                            dismiss()
                        }
                        is Resource.Error -> {
                            Log.e(TAG, "Grant permission result: Error - ${resource.message}") // AJOUTÉ: Log
                            setUiState(enabled = true, showProgressBar = false, clearError = false) // Ne pas effacer l'erreur pour qu'elle s'affiche
                            binding.etSecretCode.error = resource.message ?: getString(R.string.error_unknown)
                            Toast.makeText(requireContext(), resource.message ?: getString(R.string.error_unknown), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // AJOUTÉ: Fonction d'aide pour gérer l'état de l'UI
    private fun setUiState(enabled: Boolean, showProgressBar: Boolean, clearError: Boolean) {
        binding.progressBar.visibility = if (showProgressBar) View.VISIBLE else View.GONE
        binding.btnEnterCode.isEnabled = enabled
        binding.btnCancel.isEnabled = enabled
        binding.etSecretCode.isEnabled = enabled
        if (clearError) {
            binding.etSecretCode.error = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}