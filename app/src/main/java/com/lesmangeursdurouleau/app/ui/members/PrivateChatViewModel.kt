package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.MessageStatus
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // L'ID de l'utilisateur avec qui on discute, passé via la navigation
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

            when (val resource = userRepository.createOrGetConversation(currentUserId, targetUserId)) {
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
                is Resource.Error -> {
                    _messages.value = Resource.Error(resource.message ?: "Erreur inconnue.")
                }
                is Resource.Loading -> {
                    _messages.value = Resource.Loading()
                }
            }
        }
    }

    private fun markConversationAsRead(conversationId: String, userId: String) {
        viewModelScope.launch {
            val result = userRepository.markConversationAsRead(conversationId, userId)
            if (result is Resource.Error) {
                Log.e("PrivateChatViewModel", "Erreur lors du marquage de la conversation comme lue: ${result.message}")
            }
        }
    }

    private fun loadTargetUser() {
        userRepository.getUserById(targetUserId)
            .onEach { resource ->
                _targetUser.value = resource
            }.launchIn(viewModelScope)
    }

    private fun loadMessages(conversationId: String) {
        userRepository.getConversationMessages(conversationId)
            .onEach { resource ->
                _messages.value = resource
                // CORRIGÉ: Ajout d'une vérification de nullité sur resource.data
                if (resource is Resource.Success && resource.data != null) {
                    markReceivedMessagesAsRead(resource.data)
                }
            }.launchIn(viewModelScope)
    }

    /**
     * AJOUT: Identifie les messages reçus qui ne sont pas encore "lus" et demande leur mise à jour.
     */
    private fun markReceivedMessagesAsRead(messages: List<PrivateMessage>) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val convId = _conversationId.value ?: return

        val messagesToMarkAsRead = messages.filter {
            it.senderId != currentUserId && // C'est un message reçu
                    it.status == MessageStatus.SENT.name && // Il n'est pas déjà "lu"
                    it.id != null // Son ID est valide
        }.mapNotNull { it.id }

        if (messagesToMarkAsRead.isNotEmpty()) {
            viewModelScope.launch {
                Log.d("PrivateChatViewModel", "Marquage de ${messagesToMarkAsRead.size} message(s) comme lus.")
                // CORRIGÉ: Suppression du trait d'union fautif dans le nom de la variable
                userRepository.updateMessagesStatusToRead(convId, messagesToMarkAsRead)
            }
        }
    }

    fun sendPrivateMessage(text: String) {
        val convId = _conversationId.value
        val senderId = firebaseAuth.currentUser?.uid

        if (convId == null || senderId == null || text.isBlank()) {
            return
        }

        viewModelScope.launch {
            _sendState.value = Resource.Loading()
            val message = PrivateMessage(senderId = senderId, text = text)
            val result = userRepository.sendPrivateMessage(convId, message)
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
            val result = userRepository.deletePrivateMessage(convId, messageId)
            _deleteState.value = result
        }
    }

    fun resetDeleteState() {
        _deleteState.value = null
    }

    fun addOrUpdateReaction(messageId: String, emoji: String) {
        val convId = _conversationId.value
        val currentUserId = firebaseAuth.currentUser?.uid

        if (convId == null || currentUserId == null) {
            Log.e("PrivateChatViewModel", "Impossible de réagir: ID de conversation ou d'utilisateur manquant")
            return
        }

        viewModelScope.launch {
            val result = userRepository.addOrUpdateReaction(convId, messageId, currentUserId, emoji)
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
            val result = userRepository.editPrivateMessage(convId, messageId, newText)
            _editState.value = result
        }
    }

    fun resetEditState() {
        _editState.value = null
    }
}