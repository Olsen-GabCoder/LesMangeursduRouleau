package com.lesmangeursdurouleau.app.ui.members

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.MessageStatus
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.data.model.User
// AJOUT: Import du nouveau repository
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    // MODIFIÉ: Injection des DEUX repositories.
    // UserRepository est conservé pour getUserById.
    // PrivateChatRepository est ajouté pour toute la logique de chat.
    private val userRepository: UserRepository,
    private val privateChatRepository: PrivateChatRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val targetUserId: String = savedStateHandle.get<String>("targetUserId")!!
    private val _conversationId = MutableStateFlow<String?>(null)
    private val _messages = MutableStateFlow<Resource<List<PrivateMessage>>>(Resource.Loading())
    val messages = _messages.asStateFlow()
    private val _targetUser = MutableStateFlow<Resource<User>>(Resource.Loading())
    val targetUser = _targetUser.asStateFlow()
    private val _sendState = MutableStateFlow<Resource<Unit>?>(null)
    val sendState = _sendState.asStateFlow()
    private val _deleteState = MutableStateFlow<Resource<Unit>?>(null)
    val deleteState = _deleteState.asStateFlow()
    private val _editState = MutableStateFlow<Resource<Unit>?>(null)
    val editState = _editState.asStateFlow()

    init {
        initializeConversation()
        loadTargetUser()
    }

    private fun initializeConversation() {
        viewModelScope.launch {
            val currentUserId = firebaseAuth.currentUser?.uid
            if (currentUserId == null) {
                _messages.value = Resource.Error("Utilisateur non authentifié.")
                return@launch
            }

            // MODIFIÉ: Appel sur privateChatRepository
            when (val resource = privateChatRepository.createOrGetConversation(currentUserId, targetUserId)) {
                is Resource.Success -> {
                    val convId = resource.data
                    _conversationId.value = convId
                    if (convId != null) {
                        loadMessages(convId)
                        markConversationAsRead(convId, currentUserId)
                    } else {
                        _messages.value = Resource.Error("Impossible de créer ou de trouver la conversation.")
                    }
                }
                is Resource.Error -> _messages.value = Resource.Error(resource.message ?: "Erreur inconnue.")
                is Resource.Loading -> _messages.value = Resource.Loading()
            }
        }
    }

    private fun markConversationAsRead(conversationId: String, userId: String) {
        viewModelScope.launch {
            // MODIFIÉ: Appel sur privateChatRepository
            val result = privateChatRepository.markConversationAsRead(conversationId, userId)
            if (result is Resource.Error) {
                Log.e("PrivateChatViewModel", "Erreur lors du marquage de la conversation comme lue: ${result.message}")
            }
        }
    }

    private fun loadTargetUser() {
        // INCHANGÉ: Cet appel reste sur userRepository car il concerne le profil.
        userRepository.getUserById(targetUserId)
            .onEach { resource ->
                _targetUser.value = resource
            }.launchIn(viewModelScope)
    }

    private fun loadMessages(conversationId: String) {
        // MODIFIÉ: Appel sur privateChatRepository
        privateChatRepository.getConversationMessages(conversationId)
            .onEach { resource ->
                _messages.value = resource
                if (resource is Resource.Success && resource.data != null) {
                    markReceivedMessagesAsRead(resource.data)
                }
            }.launchIn(viewModelScope)
    }

    private fun markReceivedMessagesAsRead(messages: List<PrivateMessage>) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val convId = _conversationId.value ?: return
        val messagesToMarkAsRead = messages.filter {
            it.senderId != currentUserId && it.status == MessageStatus.SENT.name && it.id != null
        }.mapNotNull { it.id }

        if (messagesToMarkAsRead.isNotEmpty()) {
            viewModelScope.launch {
                Log.d("PrivateChatViewModel", "Marquage de ${messagesToMarkAsRead.size} message(s) comme lus.")
                // MODIFIÉ: Appel sur privateChatRepository
                privateChatRepository.updateMessagesStatusToRead(convId, messagesToMarkAsRead)
            }
        }
    }

    fun sendPrivateMessage(text: String) {
        val convId = _conversationId.value
        val senderId = firebaseAuth.currentUser?.uid
        if (convId == null || senderId == null || text.isBlank()) return

        viewModelScope.launch {
            _sendState.value = Resource.Loading()
            val message = PrivateMessage(senderId = senderId, text = text)
            // MODIFIÉ: Appel sur privateChatRepository
            val result = privateChatRepository.sendPrivateMessage(convId, message)
            _sendState.value = result
        }
    }

    fun sendImageMessage(uri: Uri) {
        val convId = _conversationId.value
        if (convId == null) {
            _sendState.value = Resource.Error("ID de conversation non disponible pour l'envoi d'image.")
            return
        }

        viewModelScope.launch {
            _sendState.value = Resource.Loading()
            // MODIFIÉ: Appel sur privateChatRepository
            val result = privateChatRepository.sendImageMessage(convId, uri, null)
            _sendState.value = result
        }
    }

    fun deleteMessage(messageId: String) {
        val convId = _conversationId.value
        if (convId == null) {
            _deleteState.value = Resource.Error("ID de conversation non disponible.")
            return
        }
        if (messageId.isBlank()) {
            _deleteState.value = Resource.Error("ID de message invalide.")
            return
        }

        viewModelScope.launch {
            _deleteState.value = Resource.Loading()
            // MODIFIÉ: Appel sur privateChatRepository
            val result = privateChatRepository.deletePrivateMessage(convId, messageId)
            _deleteState.value = result
        }
    }

    fun addOrUpdateReaction(messageId: String, emoji: String) {
        val convId = _conversationId.value
        val currentUserId = firebaseAuth.currentUser?.uid
        if (convId == null || currentUserId == null) return

        viewModelScope.launch {
            // MODIFIÉ: Appel sur privateChatRepository
            val result = privateChatRepository.addOrUpdateReaction(convId, messageId, currentUserId, emoji)
            if (result is Resource.Error) {
                Log.e("PrivateChatViewModel", "Erreur lors de l'ajout/mise à jour de la réaction: ${result.message}")
            }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val convId = _conversationId.value
        if (convId == null) {
            _editState.value = Resource.Error("ID de conversation non disponible.")
            return
        }
        if (messageId.isBlank() || newText.isBlank()) {
            _editState.value = Resource.Error("Le nouveau message ne peut pas être vide.")
            return
        }

        viewModelScope.launch {
            _editState.value = Resource.Loading()
            // MODIFIÉ: Appel sur privateChatRepository
            val result = privateChatRepository.editPrivateMessage(convId, messageId, newText)
            _editState.value = result
        }
    }

    fun resetDeleteState() { _deleteState.value = null }
    fun resetEditState() { _editState.value = null }
}