package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentConversationsListBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
        setupFiltersAndSearch()
        observeConversations()
    }

    // MODIFIÉ: Correction de l'erreur de compilation en passant le callback onFavoriteClick
    private fun setupRecyclerView() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""
        conversationsAdapter = ConversationsAdapter(
            currentUserId = currentUserId,
            onConversationClick = { conversation ->
                val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }
                if (conversation.id != null && otherUserId != null) {
                    val action = ConversationsListFragmentDirections
                        .actionConversationsListFragmentDestinationToPrivateChatFragmentDestination(
                            targetUserId = otherUserId,
                            conversationId = conversation.id
                        )
                    findNavController().navigate(action)
                } else {
                    Toast.makeText(context, "Impossible d'ouvrir la conversation.", Toast.LENGTH_SHORT).show()
                }
            },
            onFavoriteClick = { conversation ->
                viewModel.toggleFavoriteStatus(conversation)
            }
        )
        binding.rvConversations.adapter = conversationsAdapter
    }

    // MODIFIÉ: Ajout de la logique pour le chip des favoris
    private fun setupFiltersAndSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_unread -> viewModel.setFilter(ConversationFilterType.UNREAD)
                R.id.chip_favorites -> viewModel.setFilter(ConversationFilterType.FAVORITES)
                else -> viewModel.setFilter(ConversationFilterType.ALL) // R.id.chip_all ou null
            }
        }
    }

    // MODIFIÉ: Amélioration du message pour l'état vide
    private fun observeConversations() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.conversations.collect { resource ->
                    binding.progressBar.isVisible = resource is Resource.Loading

                    if (resource is Resource.Success) {
                        val conversations = resource.data
                        binding.rvConversations.isVisible = !conversations.isNullOrEmpty()
                        conversationsAdapter.submitList(conversations)

                        if (conversations.isNullOrEmpty()) {
                            binding.tvEmptyState.isVisible = true
                            binding.tvEmptyState.text = when {
                                binding.chipFavorites.isChecked -> "Aucune conversation en favori."
                                binding.searchView.query.isNotEmpty() || binding.chipUnread.isChecked -> "Aucune conversation ne correspond à vos filtres."
                                else -> "Aucune conversation pour le moment."
                            }
                        } else {
                            binding.tvEmptyState.isVisible = false
                        }
                    } else if (resource is Resource.Error) {
                        binding.rvConversations.isVisible = false
                        binding.tvEmptyState.isVisible = true
                        binding.tvEmptyState.text = resource.message
                        Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
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