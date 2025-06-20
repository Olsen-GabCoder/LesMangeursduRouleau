package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Conversation
// AJOUT: Import du nouveau repository
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
// SUPPRESSION: L'ancien import n'est plus nécessaire
// import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    // MODIFIÉ: Injection de PrivateChatRepository au lieu de UserRepository
    private val privateChatRepository: PrivateChatRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _conversations = MutableStateFlow<Resource<List<Conversation>>>(Resource.Loading())
    val conversations = _conversations.asStateFlow()

    init {
        loadUserConversations()
    }

    private fun loadUserConversations() {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            _conversations.value = Resource.Error("Utilisateur non authentifié.")
            return
        }

        // MODIFIÉ: Appel de la méthode sur le nouveau repository
        privateChatRepository.getUserConversations(currentUserId)
            .onEach { resource ->
                _conversations.value = resource
            }.launchIn(viewModelScope)
    }
}