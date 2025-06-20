package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

object ConversationFilterType {
    const val ALL = "ALL"
    const val UNREAD = "UNREAD"
    const val FAVORITES = "FAVORITES"
}

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val privateChatRepository: PrivateChatRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _conversations = MutableStateFlow<Resource<List<Conversation>>>(Resource.Loading())
    val conversations = _conversations.asStateFlow()

    private var _allConversations = listOf<Conversation>()
    private val _activeFilter = MutableStateFlow(ConversationFilterType.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    init {
        loadUserConversations()
    }

    private fun loadUserConversations() {
        if (currentUserId == null) {
            _conversations.value = Resource.Error("Utilisateur non authentifié.")
            return
        }

        privateChatRepository.getUserConversations(currentUserId!!)
            .onEach { resource ->
                if (resource is Resource.Success) {
                    // On met à jour notre liste "source de vérité"
                    _allConversations = resource.data ?: emptyList()
                    // Et on applique les filtres actuels
                    applyFilters()
                } else {
                    // On propage directement les états Loading et Error
                    _conversations.value = resource
                }
            }.launchIn(viewModelScope)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun setFilter(filterType: String) {
        _activeFilter.value = filterType
        applyFilters()
    }

    // --- LOGIQUE DE MISE À JOUR OPTIMISTE ---
    fun toggleFavoriteStatus(conversationToToggle: Conversation) {
        if (conversationToToggle.id == null) return

        // 1. Mise à jour "optimiste" de l'état local
        val updatedList = _allConversations.map { conversation ->
            if (conversation.id == conversationToToggle.id) {
                // Créer une nouvelle instance de l'objet avec la valeur inversée
                conversation.copy(isFavorite = !conversation.isFavorite)
            } else {
                conversation
            }
        }
        _allConversations = updatedList
        applyFilters() // Appliquer les filtres pour rafraîchir l'UI immédiatement

        // 2. Lancer la mise à jour persistante dans Firestore en arrière-plan
        viewModelScope.launch {
            privateChatRepository.updateFavoriteStatus(conversationToToggle.id, !conversationToToggle.isFavorite)
            // Pas besoin de gérer le résultat ici, car l'UI est déjà à jour.
            // Le listener Firestore resynchronisera l'état au prochain chargement de toute façon.
        }
    }

    private fun applyFilters() {
        val filteredList = _allConversations.filter { conversation ->
            val passesFilterType = when (_activeFilter.value) {
                ConversationFilterType.UNREAD -> (conversation.unreadCount[currentUserId] ?: 0) > 0
                ConversationFilterType.FAVORITES -> conversation.isFavorite
                else -> true // ALL
            }

            val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }
            val participantName = conversation.participantNames[otherUserId] ?: ""
            val passesSearchQuery = participantName.contains(_searchQuery.value, ignoreCase = true)

            passesFilterType && passesSearchQuery
        }
        _conversations.value = Resource.Success(filteredList)
    }
}