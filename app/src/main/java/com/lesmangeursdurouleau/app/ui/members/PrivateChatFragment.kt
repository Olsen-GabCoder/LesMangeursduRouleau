package com.lesmangeursdurouleau.app.ui.members

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
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
import com.google.android.material.textfield.TextInputLayout
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

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                viewModel.sendImageMessage(it)
            }
        }
    }

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
                launch {
                    viewModel.sendState.collectLatest { resource ->
                        if (resource is Resource.Error) {
                            Toast.makeText(context, "Erreur d'envoi: ${resource.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.targetUser.collect { resource ->
                        if (resource is Resource.Success) {
                            binding.toolbar.title = resource.data?.username ?: "Message"
                        }
                    }
                }
                launch {
                    viewModel.deleteState.collectLatest { resource ->
                        when(resource) {
                            is Resource.Loading -> { /* No-op */ }
                            is Resource.Success -> {
                                Toast.makeText(context, getString(R.string.message_deleted_successfully), Toast.LENGTH_SHORT).show()
                                viewModel.resetDeleteState()
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                                viewModel.resetDeleteState()
                            }
                            null -> { /* Initial state */ }
                        }
                    }
                }
                launch {
                    viewModel.editState.collectLatest { resource ->
                        when(resource) {
                            is Resource.Loading -> { /* No-op */ }
                            is Resource.Success -> {
                                Toast.makeText(context, "Message modifié", Toast.LENGTH_SHORT).show()
                                viewModel.resetEditState()
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                                viewModel.resetEditState()
                            }
                            null -> { /* Initial state */ }
                        }
                    }
                }

                // --- NOUVELLE OBSERVATION ---
                // Observe l'état de saisie de l'interlocuteur et met à jour l'UI.
                launch {
                    viewModel.isTargetUserTyping.collect { isTyping ->
                        binding.tvTypingIndicatorInChat.isVisible = isTyping
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
        messagesAdapter = PrivateMessagesAdapter(
            currentUserId = currentUserId,
            onMessageLongClick = { anchorView, message ->
                showActionsMenuForMessage(anchorView, message)
            },
            onImageClick = { imageUrl ->
                val action = PrivateChatFragmentDirections.actionPrivateChatFragmentToFullScreenImageFragment(imageUrl)
                findNavController().navigate(action)
            }
        )
        val layoutManager = LinearLayoutManager(context)
        layoutManager.stackFromEnd = true
        binding.rvMessages.adapter = messagesAdapter
        binding.rvMessages.layoutManager = layoutManager

        messagesAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                binding.rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
            }
        })
    }

    private fun showActionsMenuForMessage(anchorView: View, message: PrivateMessage) {
        if (message.id.isNullOrBlank()) {
            Toast.makeText(context, getString(R.string.error_invalid_message_id), Toast.LENGTH_SHORT).show()
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.popup_message_actions, binding.root, false)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val emojis = mapOf(
            R.id.emoji_thumbs_up to "👍", R.id.emoji_heart to "❤️", R.id.emoji_laugh to "😂",
            R.id.emoji_wow to "😮", R.id.emoji_sad to "😢"
        )
        emojis.forEach { (id, emoji) ->
            popupView.findViewById<TextView>(id).setOnClickListener {
                viewModel.addOrUpdateReaction(message.id, emoji)
                popupWindow.dismiss()
            }
        }

        popupView.findViewById<TextView>(R.id.action_copy_message_popup).setOnClickListener {
            copyMessageToClipboard(message.text)
            popupWindow.dismiss()
        }

        val editActionView = popupView.findViewById<TextView>(R.id.action_edit_message_popup)
        val deleteActionView = popupView.findViewById<TextView>(R.id.action_delete_message_popup)
        val separatorView = popupView.findViewById<View>(R.id.separator)
        val isSentByCurrentUser = message.senderId == firebaseAuth.currentUser?.uid

        if (isSentByCurrentUser && !message.text.isNullOrBlank()) {
            editActionView.setOnClickListener {
                showEditMessageDialog(message)
                popupWindow.dismiss()
            }
        } else {
            editActionView.visibility = View.GONE
        }

        if (isSentByCurrentUser) {
            deleteActionView.setOnClickListener {
                showDeleteConfirmationDialog(message)
                popupWindow.dismiss()
            }
        } else {
            deleteActionView.visibility = View.GONE
        }

        if (editActionView.visibility == View.GONE && deleteActionView.visibility == View.GONE) {
            separatorView.visibility = View.GONE
        }


        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val x = location[0] + (anchorView.width - popupWidth) / 2
        val y = location[1] - popupHeight - 16

        popupWindow.showAtLocation(anchorView, 0, x, y)
    }

    private fun showEditMessageDialog(message: PrivateMessage) {
        if (message.id.isNullOrBlank()) return

        val textInputLayout = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_message, null) as TextInputLayout
        val editText = textInputLayout.editText
        editText?.setText(message.text)
        message.text?.let { editText?.setSelection(it.length) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifier le message")
            .setView(textInputLayout)
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Enregistrer") { dialog, _ ->
                val newText = editText?.text.toString().trim()
                if (newText.isNotEmpty() && newText != message.text) {
                    viewModel.editMessage(message.id, newText)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun copyMessageToClipboard(text: String?) {
        if (text.isNullOrEmpty()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, getString(R.string.message_copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmationDialog(message: PrivateMessage) {
        if (message.id.isNullOrBlank()) {
            Toast.makeText(context, getString(R.string.error_invalid_message_id), Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_message_dialog_title))
            .setMessage(getString(R.string.delete_message_dialog_message))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                viewModel.deleteMessage(message.id)
                dialog.dismiss()
            }
            .show()
    }

    private fun setupInput() {
        binding.etMessageInput.addTextChangedListener {
            binding.btnSend.isEnabled = it.toString().isNotBlank()
            viewModel.onUserTyping(it.toString())
        }

        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                viewModel.sendPrivateMessage(messageText)
                binding.etMessageInput.text.clear()
            }
        }

        binding.btnAttachFile.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}