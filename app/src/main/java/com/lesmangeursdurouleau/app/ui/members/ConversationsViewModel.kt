// Fichier : com/lesmangeursdurouleau/app/ui/members/ConversationsViewModel.kt
package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val userRepository: UserRepository,
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

        userRepository.getUserConversations(currentUserId)
            .onEach { resource ->
                _conversations.value = resource
            }.launchIn(viewModelScope)
    }
}