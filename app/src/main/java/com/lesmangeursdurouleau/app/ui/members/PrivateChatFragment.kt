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
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.databinding.FragmentPrivateChatBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class PrivateChatFragment : Fragment() {

    private var _binding: FragmentPrivateChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PrivateChatViewModel by viewModels()
    private val args: PrivateChatFragmentArgs by navArgs()
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
        observeMessages()
        observeSendState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        // Le titre sera défini lorsque nous aurons les détails de l'utilisateur
        binding.toolbar.title = "Chargement..."
    }

    private fun setupRecyclerView() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""
        messagesAdapter = PrivateMessagesAdapter(currentUserId)
        val layoutManager = LinearLayoutManager(context)
        binding.rvMessages.adapter = messagesAdapter
        binding.rvMessages.layoutManager = layoutManager

        // Scroll vers le bas quand de nouveaux messages arrivent
        messagesAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
            }
        })
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

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { resource ->
                    when(resource) {
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
        }
    }

    private fun observeSendState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.sendState.collectLatest { resource ->
                if (resource is Resource.Error) {
                    Toast.makeText(context, "Erreur d'envoi: ${resource.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}