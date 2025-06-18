// Fichier : com/lesmangeursdurouleau/app/ui/members/PrivateChatViewModel.kt
package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
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

    // Pour gérer l'état de l'envoi de message
    private val _sendState = MutableStateFlow<Resource<Unit>?>(null)
    val sendState = _sendState.asStateFlow()

    init {
        initializeConversation()
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

    private fun loadMessages(conversationId: String) {
        userRepository.getConversationMessages(conversationId)
            .onEach { resource ->
                _messages.value = resource
            }.launchIn(viewModelScope)
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
}