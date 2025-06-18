// Fichier : com/lesmangeursdurouleau/app/ui/members/PrivateChatFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.databinding.FragmentPrivateChatBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrivateChatFragment : Fragment() {

    private var _binding: FragmentPrivateChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PrivateChatViewModel by viewModels()
    private lateinit var messagesAdapter: PrivateMessagesAdapter

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrivateChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observer les messages
                launch {
                    viewModel.messages.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                            is Resource.Success -> {
                                binding.progressBar.visibility = View.GONE
                                messagesAdapter.submitList(resource.data)
                            }
                            is Resource.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                // Observer l'état de l'envoi de message
                launch {
                    viewModel.sendState.collectLatest { resource ->
                        if (resource is Resource.Error) {
                            Toast.makeText(context, "Erreur d'envoi: ${resource.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Observer les informations de l'utilisateur cible
                launch {
                    viewModel.targetUser.collect { resource ->
                        if (resource is Resource.Success) {
                            binding.toolbar.title = resource.data?.username ?: "Message"
                        }
                    }
                }

                // Observer l'état de la suppression de message
                launch {
                    viewModel.deleteState.collectLatest { resource ->
                        when(resource) {
                            is Resource.Loading -> {
                                // Optionnel: afficher un indicateur de chargement
                                Toast.makeText(context, "Suppression en cours...", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Success -> {
                                Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()
                                viewModel.resetDeleteState() // Réinitialiser l'état
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                                viewModel.resetDeleteState() // Réinitialiser l'état
                            }
                            null -> { /* État initial ou réinitialisé, ne rien faire */ }
                        }
                    }
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.toolbar.title = "Chargement..."
    }

    private fun setupRecyclerView() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""
        messagesAdapter = PrivateMessagesAdapter(currentUserId) { message ->
            onMessageLongClicked(message)
        }
        val layoutManager = LinearLayoutManager(context)
        binding.rvMessages.adapter = messagesAdapter
        binding.rvMessages.layoutManager = layoutManager

        messagesAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
            }
        })
    }

    /**
     * Gère le clic long sur un message en affichant une boîte de dialogue.
     */
    private fun onMessageLongClicked(message: PrivateMessage) {
        // L'ID du message est crucial. Cette vérification reste une sécurité.
        if (message.id?.isBlank() == true) {
            Toast.makeText(context, "Impossible de supprimer ce message (ID manquant)", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_message_dialog_title))
            .setMessage(getString(R.string.delete_message_dialog_message))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                message.id?.let { viewModel.deleteMessage(it) }
                dialog.dismiss()
            }
            .show()
    }

    private fun setupInput() {
        binding.etMessageInput.addTextChangedListener {
            binding.btnSend.isEnabled = it.toString().isNotBlank()
        }

        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                viewModel.sendPrivateMessage(messageText)
                binding.etMessageInput.text.clear()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}