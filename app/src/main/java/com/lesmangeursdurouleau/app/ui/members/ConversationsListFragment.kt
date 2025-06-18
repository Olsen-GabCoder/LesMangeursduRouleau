// Fichier : com/lesmangeursdurouleau/app/ui/members/ConversationsListFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.databinding.FragmentConversationsListBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ConversationsListFragment : Fragment() {

    private var _binding: FragmentConversationsListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConversationsViewModel by viewModels()
    private lateinit var conversationsAdapter: ConversationsAdapter

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeConversations()
    }

    private fun setupRecyclerView() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""
        conversationsAdapter = ConversationsAdapter(currentUserId) { conversation ->
            // Gérer le clic sur une conversation
            val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }
            if (conversation.id != null && otherUserId != null) {
                // La navigation sera définie à l'étape 2.3
                // val action = ConversationsListFragmentDirections.actionToPrivateChat(conversation.id, otherUserId)
                // findNavController().navigate(action)
                Toast.makeText(context, "Ouverture de la conv ${conversation.id}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvConversations.adapter = conversationsAdapter
    }

    private fun observeConversations() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.conversations.collect { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            binding.progressBar.isVisible = true
                            binding.rvConversations.isVisible = false
                            binding.tvEmptyState.isVisible = false
                        }
                        is Resource.Success -> {
                            binding.progressBar.isVisible = false
                            val conversations = resource.data
                            if (conversations.isNullOrEmpty()) {
                                binding.tvEmptyState.isVisible = true
                                binding.rvConversations.isVisible = false
                            } else {
                                binding.tvEmptyState.isVisible = false
                                binding.rvConversations.isVisible = true
                                conversationsAdapter.submitList(conversations)
                            }
                        }
                        is Resource.Error -> {
                            binding.progressBar.isVisible = false
                            binding.tvEmptyState.isVisible = true
                            binding.tvEmptyState.text = resource.message
                            Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}