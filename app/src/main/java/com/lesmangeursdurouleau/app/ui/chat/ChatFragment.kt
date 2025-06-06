package com.lesmangeursdurouleau.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.databinding.FragmentChatBinding
import com.lesmangeursdurouleau.app.ui.chat.adapter.ChatAdapter
import com.lesmangeursdurouleau.app.ui.chat.adapter.OnMessageInteractionListener
import com.lesmangeursdurouleau.app.ui.chat.adapter.OnProfileClickListener
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatFragment : Fragment(), OnProfileClickListener, OnMessageInteractionListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    // Pour le TextWatcher et le Handler du timeout de frappe
    private val typingHandler = Handler(Looper.getMainLooper())
    private var typingStoppedRunnable: Runnable? = null

    // Variables pour la gestion de la pagination et du scroll
    private var isLoadingHistory = false
    private var isScrollingProgrammatically = false

    // Variables pour maintenir la position de scroll
    private var savedScrollPosition = -1
    private var savedScrollOffset = 0

    // NOUVEAU: Liste d'emojis de réaction disponibles pour le menu
    private val reactionEmojis = listOf("👍", "❤️", "😂", "😡", "🥳")

    companion object {
        private const val TAG_FRAGMENT = "ChatFragment"
        private const val TYPING_UI_DEBOUNCE_MS = 1000L
        private const val LOAD_MORE_THRESHOLD = 5
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        Log.d(TAG_FRAGMENT, "onCreateView")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG_FRAGMENT, "onViewCreated")
        setupRecyclerView()
        setupInputTextWatcher()
        setupClickListeners()
        setupObservers()
    }

    private fun setupRecyclerView() {
        Log.d(TAG_FRAGMENT, "setupRecyclerView")
        chatAdapter = ChatAdapter(this, this)
        linearLayoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }

        binding.recyclerViewChat.apply {
            adapter = chatAdapter
            layoutManager = linearLayoutManager

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (isScrollingProgrammatically) return

                    if (dy < 0) {
                        val firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition()

                        if (firstVisibleItemPosition <= LOAD_MORE_THRESHOLD &&
                            !isLoadingHistory &&
                            !viewModel.allOldMessagesLoaded) {

                            Log.d(TAG_FRAGMENT, "Déclenchement du chargement de l'historique")
                            loadPreviousMessages()
                        }
                    }
                }
            })
        }
    }

    private fun setupInputTextWatcher() {
        binding.etMessageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                typingStoppedRunnable?.let { typingHandler.removeCallbacks(it) }

                if (s.toString().trim().isNotEmpty()) {
                    viewModel.userStartedTyping()
                } else {
                    viewModel.userStoppedTyping()
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().trim().isNotEmpty()) {
                    typingStoppedRunnable = Runnable { viewModel.userStoppedTyping() }
                    typingHandler.postDelayed(typingStoppedRunnable!!, TYPING_UI_DEBOUNCE_MS)
                }
            }
        })
    }

    private fun setupClickListeners() {
        Log.d(TAG_FRAGMENT, "setupClickListeners")
        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                Log.d(TAG_FRAGMENT, "Bouton Envoyer cliqué avec le texte: \"$messageText\"")
                viewModel.sendMessage(messageText)
            } else {
                Log.w(TAG_FRAGMENT, "Tentative d'envoi d'un message vide.")
                Toast.makeText(requireContext(), R.string.message_cannot_be_empty, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        Log.d(TAG_FRAGMENT, "setupObservers")

        viewModel.messages.observe(viewLifecycleOwner) { resource ->
            Log.d(TAG_FRAGMENT, "Observation des messages: $resource")
            when (resource) {
                is Resource.Loading -> {
                    showLoadingIndicator(true)
                }
                is Resource.Success -> {
                    showLoadingIndicator(false)
                    val messages = resource.data
                    if (messages.isNullOrEmpty()) {
                        chatAdapter.submitList(emptyList())
                        binding.tvChatEmptyMessage.visibility = View.VISIBLE
                    } else {
                        binding.tvChatEmptyMessage.visibility = View.GONE
                        val currentList = chatAdapter.currentList
                        chatAdapter.submitList(messages.toList()) {
                            val lastVisiblePosition = linearLayoutManager.findLastVisibleItemPosition()
                            val totalItemCount = linearLayoutManager.itemCount
                            val isAtBottom = lastVisiblePosition == totalItemCount - 1 || totalItemCount == 0

                            if (isAtBottom || currentList.isEmpty() || messages.size > currentList.size) {
                                binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
                            }
                        }
                    }
                }
                is Resource.Error -> {
                    showLoadingIndicator(false)
                    Log.e(TAG_FRAGMENT, "Erreur lors du chargement des messages: ${resource.message}")
                    Toast.makeText(requireContext(), "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                    binding.tvChatEmptyMessage.visibility = View.VISIBLE
                }
            }
        }

        viewModel.oldMessagesState.observe(viewLifecycleOwner) { resource ->
            Log.d(TAG_FRAGMENT, "Observation de l'historique: $resource")
            when (resource) {
                is Resource.Loading -> {
                    isLoadingHistory = true
                    showLoadingIndicator(true)
                }
                is Resource.Success -> {
                    isLoadingHistory = false
                    showLoadingIndicator(false)

                    val oldMessages = resource.data
                    if (!oldMessages.isNullOrEmpty()) {
                        addOldMessagesToList(oldMessages)
                    }
                }
                is Resource.Error -> {
                    isLoadingHistory = false
                    showLoadingIndicator(false)
                    Log.e(TAG_FRAGMENT, "Erreur lors du chargement de l'historique: ${resource.message}")
                    Toast.makeText(requireContext(), "Erreur historique: ${resource.message}", Toast.LENGTH_SHORT).show()
                }
                null -> {
                    isLoadingHistory = false
                    showLoadingIndicator(false)
                }
            }
        }

        viewModel.userDetailsCache.observe(viewLifecycleOwner) { userMap ->
            chatAdapter.setUserDetails(userMap)
        }

        viewModel.sendMessageStatus.observe(viewLifecycleOwner) { resource ->
            binding.btnSend.isEnabled = resource !is Resource.Loading
            binding.etMessageInput.isEnabled = resource !is Resource.Loading

            when (resource) {
                is Resource.Loading -> {}
                is Resource.Success -> {
                    binding.etMessageInput.text.clear()
                }
                is Resource.Error -> {
                    Toast.makeText(requireContext(), "Erreur envoi: ${resource.message}", Toast.LENGTH_LONG).show()
                }
                null -> {}
            }

            if (resource != null && resource !is Resource.Loading) {
                viewModel.clearSendMessageStatus()
            }
        }

        viewModel.deleteMessageStatus.observe(viewLifecycleOwner) { resource ->
            Log.d(TAG_FRAGMENT, "Observation statut suppression: $resource")
            when (resource) {
                is Resource.Loading -> Toast.makeText(requireContext(), R.string.deleting_message, Toast.LENGTH_SHORT).show()
                is Resource.Success -> Toast.makeText(requireContext(), R.string.message_deleted_successfully, Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(requireContext(), "Erreur suppression: ${resource.message}", Toast.LENGTH_LONG).show()
                null -> {}
            }
            if (resource != null && resource !is Resource.Loading) {
                viewModel.clearDeleteMessageStatus()
            }
        }

        // Observateur pour le statut des réactions (déjà présent et correct)
        viewModel.reactionStatus.observe(viewLifecycleOwner) { resource ->
            Log.d(TAG_FRAGMENT, "Observation statut réaction: $resource")
            when (resource) {
                is Resource.Loading -> {
                    // Optionnel: afficher un indicateur de chargement discret (ex: un petit spinner sur le message)
                }
                is Resource.Success -> {
                    Toast.makeText(requireContext(), R.string.reaction_updated_successfully, Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    Toast.makeText(requireContext(), "Erreur réaction: ${resource.message}", Toast.LENGTH_LONG).show()
                }
                null -> {}
            }
            if (resource != null && resource !is Resource.Loading) {
                viewModel.clearReactionStatus()
            }
        }

        viewModel.typingUsers.observe(viewLifecycleOwner) { typingUserIds ->
            Log.d(TAG_FRAGMENT, "Utilisateurs en train d'écrire: ${typingUserIds.joinToString()}")
            val currentUserUid = viewModel.firebaseAuth.currentUser?.uid
            val otherTypingUsers = typingUserIds.filter { it != currentUserUid }

            if (otherTypingUsers.isNotEmpty()) {
                val textToShow = if (otherTypingUsers.size == 1) {
                    getString(R.string.single_member_typing)
                } else {
                    getString(R.string.multiple_members_typing)
                }
                binding.tvTypingIndicator.text = textToShow
                binding.tvTypingIndicator.visibility = View.VISIBLE
            } else {
                binding.tvTypingIndicator.visibility = View.GONE
            }
        }
    }

    private fun loadPreviousMessages() {
        Log.d(TAG_FRAGMENT, "loadPreviousMessages appelé")
        viewModel.loadPreviousMessages()
    }

    private fun addOldMessagesToList(oldMessages: List<Message>) {
        Log.d(TAG_FRAGMENT, "Ajout de ${oldMessages.size} messages d'historique")

        saveScrollPosition()

        val currentList = chatAdapter.currentList.toMutableList()
        val newList = (oldMessages + currentList).distinctBy { it.messageId }.sortedBy { it.timestamp }

        isScrollingProgrammatically = true
        chatAdapter.submitList(newList) {
            restoreScrollPosition(oldMessages.size)
            isScrollingProgrammatically = false
        }
    }

    private fun saveScrollPosition() {
        val firstVisiblePosition = linearLayoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePosition != RecyclerView.NO_POSITION) {
            savedScrollPosition = firstVisiblePosition
            val firstVisibleView = linearLayoutManager.findViewByPosition(firstVisiblePosition)
            savedScrollOffset = firstVisibleView?.top ?: 0
            Log.d(TAG_FRAGMENT, "Position sauvegardée: $savedScrollPosition, offset: $savedScrollOffset")
        }
    }

    private fun restoreScrollPosition(newItemsCount: Int) {
        if (savedScrollPosition != -1) {
            val newPosition = savedScrollPosition + newItemsCount
            if (newPosition >= 0 && newPosition < chatAdapter.itemCount) {
                linearLayoutManager.scrollToPositionWithOffset(newPosition, savedScrollOffset)
                Log.d(TAG_FRAGMENT, "Position restaurée: $newPosition (ajout de $newItemsCount items)")
            } else {
                Log.w(TAG_FRAGMENT, "Calculated scroll position invalid ($newPosition), scrolling to bottom.")
                binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    private fun showLoadingIndicator(show: Boolean) {
        binding.progressBarChat.visibility = if (show) View.VISIBLE else View.GONE
        Log.d(TAG_FRAGMENT, "Indicateur de chargement: ${if (show) "VISIBLE" else "GONE"}")
    }

    // Implémentation de OnProfileClickListener
    override fun onProfileClicked(userId: String, username: String) {
        Log.d(TAG_FRAGMENT, "onProfileClicked: userId=$userId, username=$username")
        try {
            val action = ChatFragmentDirections.actionChatFragmentToPublicProfileFragment(userId, username)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e(TAG_FRAGMENT, "Erreur de navigation vers le profil public: ${e.localizedMessage}", e)
            Toast.makeText(requireContext(), "Impossible d'ouvrir le profil.", Toast.LENGTH_SHORT).show()
        }
    }

    // Implémentation de OnMessageInteractionListener
    override fun onMessageLongClicked(message: Message, anchorView: View) {
        Log.d(TAG_FRAGMENT, "onMessageLongClicked: messageId=${message.messageId}")
        val popupMenu = PopupMenu(requireContext(), anchorView)

        // Options de base (copier, supprimer)
        popupMenu.menu.add(0, R.id.menu_item_copy_text, 0, getString(R.string.copy_text_action))
        if (message.senderId == viewModel.firebaseAuth.currentUser?.uid) {
            popupMenu.menu.add(0, R.id.menu_item_delete_message, 1, getString(R.string.delete_message_action))
        }

        // Ajout du sous-menu pour les réactions
        val reactionSubMenu = popupMenu.menu.addSubMenu(0, R.id.menu_group_reactions, 2, getString(R.string.add_reaction_action))

        // Ajoutez les emojis au sous-menu.
        // Si l'utilisateur a déjà réagi avec un emoji spécifique,
        // vous pouvez l'indiquer visuellement (ex: avec un coche ou en changeant son titre).
        val currentUserReaction = message.userReaction // C'est le champ que vous avez ajouté dans Message
        reactionEmojis.forEachIndexed { index, emoji ->
            val menuItemTitle = if (emoji == currentUserReaction) {
                "$emoji ${getString(R.string.remove_reaction_indicator)}" // Ex: "👍 (Supprimer)"
            } else {
                emoji
            }
            // Utilisation du même itemId pour simplifier la gestion, l'important est le titre pour identifier l'emoji
            reactionSubMenu.add(R.id.menu_group_reactions, index, index, menuItemTitle)
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_item_copy_text -> {
                    copyTextToClipboard(message.text)
                    true
                }
                R.id.menu_item_delete_message -> {
                    if (message.messageId.isNotBlank()) {
                        viewModel.deleteMessage(message.messageId)
                    } else {
                        Toast.makeText(requireContext(), "ID message invalide", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> {
                    // Gérer les clics sur les emojis de réaction
                    if (menuItem.groupId == R.id.menu_group_reactions) {
                        val selectedEmoji = menuItem.title.toString().replace(getString(R.string.remove_reaction_indicator), "").trim()
                        Log.d(TAG_FRAGMENT, "Menu réaction sélectionné: '$selectedEmoji' pour message ${message.messageId}")
                        // Appeler la méthode du ViewModel pour gérer l'ajout/suppression de la réaction
                        viewModel.addReactionToMessage(message.messageId, selectedEmoji)
                        true
                    } else {
                        false
                    }
                }
            }
        }
        popupMenu.show()
    }

    // Implémentation de onReactionClicked (appelé par l'adaptateur lorsque l'utilisateur clique sur un emoji affiché)
    override fun onReactionClicked(message: Message, reactionEmoji: String) {
        Log.d(TAG_FRAGMENT, "onReactionClicked (from adapter): messageId=${message.messageId}, emoji='$reactionEmoji'")
        // Quand une réaction existante est cliquée (depuis les bulles de réaction affichées par l'adaptateur),
        // on appelle le ViewModel pour basculer son état (ajouter/supprimer).
        // Le ViewModel/Repository saura si c'est un ajout ou une suppression.
        viewModel.addReactionToMessage(message.messageId, reactionEmoji)
    }

    private fun copyTextToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("message_text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG_FRAGMENT, "onStop: Appel de viewModel.userStoppedTyping()")
        viewModel.userStoppedTyping()
    }

    override fun onDestroyView() {
        Log.d(TAG_FRAGMENT, "onDestroyView")
        viewModel.userStoppedTyping()
        typingStoppedRunnable?.let { typingHandler.removeCallbacks(it) }
        typingStoppedRunnable = null
        binding.recyclerViewChat.adapter = null
        super.onDestroyView()
        _binding = null
    }
}