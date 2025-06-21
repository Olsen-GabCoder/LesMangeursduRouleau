package com.lesmangeursdurouleau.app.ui.members

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.ChatItem
import com.lesmangeursdurouleau.app.data.model.DateSeparatorItem
import com.lesmangeursdurouleau.app.data.model.MessageItem
import com.lesmangeursdurouleau.app.data.model.MessageStatus
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.data.model.ReplyInfo
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ToolbarState(
    val userStatus: String = ""
)

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val privateChatRepository: PrivateChatRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TYPING_DEBOUNCE_MS = 500L
        private const val TYPING_TIMEOUT_MS = 3000L
    }
    private val textInputFlow = MutableStateFlow("")
    private var typingTimeoutJob: Job? = null
    private var isCurrentlyTyping = false

    private val targetUserId: String = savedStateHandle.get<String>("targetUserId")!!
    private val _conversationId = MutableStateFlow<String?>(null)

    private val _messages = MutableStateFlow<Resource<List<PrivateMessage>>>(Resource.Loading())
    private val _targetUser = MutableStateFlow<Resource<User>>(Resource.Loading())
    val targetUser = _targetUser.asStateFlow()
    private val _isTargetUserTyping = MutableStateFlow(false)

    private val _replyingToMessage = MutableStateFlow<PrivateMessage?>(null)
    val replyingToMessage = _replyingToMessage.asStateFlow()

    val toolbarState = combine(
        _targetUser,
        _isTargetUserTyping
    ) { userResource, isTyping ->
        val user = (userResource as? Resource.Success)?.data
        ToolbarState(
            userStatus = formatUserStatus(user, isTyping)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ToolbarState()
    )

    val chatItems = _messages.map { resource ->
        when (resource) {
            is Resource.Success -> Resource.Success(addDateSeparators(resource.data ?: emptyList()))
            is Resource.Error -> Resource.Error(resource.message ?: "Erreur inconnue")
            is Resource.Loading -> Resource.Loading()
        }
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = Resource.Loading()
    )

    private val _sendState = MutableStateFlow<Resource<Unit>?>(null)
    val sendState = _sendState.asStateFlow()
    private val _deleteState = MutableStateFlow<Resource<Unit>?>(null)
    val deleteState = _deleteState.asStateFlow()
    private val _editState = MutableStateFlow<Resource<Unit>?>(null)
    val editState = _editState.asStateFlow()

    init {
        initializeConversation()
        loadTargetUser()
        observeTypingStatus()
    }

    fun onReplyMessage(message: PrivateMessage) {
        _replyingToMessage.value = message
    }

    fun cancelReply() {
        _replyingToMessage.value = null
    }

    fun sendPrivateMessage(text: String) {
        val convId = _conversationId.value
        val senderId = firebaseAuth.currentUser?.uid
        if (convId == null || senderId == null || text.isBlank()) return

        val replyInfo = _replyingToMessage.value?.let { originalMessage ->
            val originalSenderName = if (originalMessage.senderId == senderId) {
                "Vous"
            } else {
                (_targetUser.value as? Resource.Success)?.data?.username ?: ""
            }
            val preview = originalMessage.text ?: (if (originalMessage.imageUrl != null) "Image" else "Message")

            ReplyInfo(
                repliedToMessageId = originalMessage.id ?: "",
                repliedToSenderName = originalSenderName,
                repliedToMessagePreview = preview
            )
        }

        viewModelScope.launch {
            _sendState.value = Resource.Loading()
            setTypingStatus(false)
            val message = PrivateMessage(
                senderId = senderId,
                text = text,
                replyInfo = replyInfo
            )
            val result = privateChatRepository.sendPrivateMessage(convId, message)
            _sendState.value = result

            if (result is Resource.Success) {
                cancelReply()
            }
        }
    }

    private fun formatUserStatus(user: User?, isTyping: Boolean): String {
        if (isTyping) return "est en train d'écrire..."
        if (user?.isOnline == true) return "en ligne"
        val lastSeenDate = user?.lastSeen ?: return ""

        val now = System.currentTimeMillis()
        val diff = now - lastSeenDate.time
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            minutes < 1 -> "Dernière activité à l'instant"
            minutes < 60 -> "Dernière activité il y a $minutes min"
            hours < 24 -> "Dernière activité il y a $hours h"
            days < 2 -> "Dernière activité hier"
            else -> {
                val format = SimpleDateFormat("le dd/MM/yy", Locale.FRENCH)
                "Dernière activité ${format.format(lastSeenDate)}"
            }
        }
    }

    private fun initializeConversation() {
        viewModelScope.launch {
            val currentUserId = firebaseAuth.currentUser?.uid ?: return@launch
            when (val resource = privateChatRepository.createOrGetConversation(currentUserId, targetUserId)) {
                is Resource.Success -> {
                    val convId = resource.data
                    _conversationId.value = convId
                    if (convId != null) {
                        loadMessages(convId)
                        markConversationAsRead(convId, currentUserId)
                        observeTypingStatusInConversation(convId)
                    } else _messages.value = Resource.Error("Impossible de trouver la conversation.")
                }
                is Resource.Error -> _messages.value = Resource.Error(resource.message ?: "Erreur inconnue.")
                is Resource.Loading -> _messages.value = Resource.Loading()
            }
        }
    }

    private fun observeTypingStatusInConversation(conversationId: String) {
        privateChatRepository.getConversation(conversationId)
            .onEach { resource ->
                if (resource is Resource.Success) {
                    val conversation = resource.data
                    _isTargetUserTyping.value = conversation?.typingStatus?.get(targetUserId) ?: false
                }
            }
            .launchIn(viewModelScope)
    }

    private fun addDateSeparators(messages: List<PrivateMessage>): List<ChatItem> {
        if (messages.isEmpty()) return emptyList()
        val itemsWithSeparators = mutableListOf<ChatItem>()
        val calendar = Calendar.getInstance()
        messages.forEachIndexed { index, message ->
            val messageDate = message.timestamp ?: return@forEachIndexed
            if (index == 0) {
                itemsWithSeparators.add(DateSeparatorItem(timestamp = messageDate))
            } else {
                val previousMessageDate = messages[index - 1].timestamp ?: return@forEachIndexed
                if (!isSameDay(previousMessageDate, messageDate, calendar)) {
                    itemsWithSeparators.add(DateSeparatorItem(timestamp = messageDate))
                }
            }
            itemsWithSeparators.add(MessageItem(message = message))
        }
        return itemsWithSeparators
    }

    private fun isSameDay(date1: Date, date2: Date, calendar: Calendar): Boolean {
        calendar.time = date1
        val year1 = calendar.get(Calendar.YEAR)
        val day1 = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.time = date2
        val year2 = calendar.get(Calendar.YEAR)
        val day2 = calendar.get(Calendar.DAY_OF_YEAR)
        return year1 == year2 && day1 == day2
    }

    fun formatDateLabel(date: Date, context: Context): String {
        val calendar = Calendar.getInstance()
        val today = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calendar.time
        val targetCalendar = Calendar.getInstance().apply { time = date }
        return when {
            isSameDay(date, today, Calendar.getInstance()) -> "AUJOURD'HUI"
            isSameDay(date, yesterday, Calendar.getInstance()) -> "HIER"
            else -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val targetYear = targetCalendar.get(Calendar.YEAR)
                val format = if (currentYear == targetYear) SimpleDateFormat("d MMMM", Locale.FRENCH)
                else SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
                format.format(date).uppercase(Locale.FRENCH)
            }
        }
    }

    fun onUserTyping(text: String) {
        textInputFlow.value = text
    }

    private fun observeTypingStatus() {
        textInputFlow.debounce(TYPING_DEBOUNCE_MS).onEach {
            typingTimeoutJob?.cancel()
            val currentUserId = firebaseAuth.currentUser?.uid
            val convId = _conversationId.value
            if (currentUserId == null || convId == null) return@onEach
            if (it.isNotBlank() && !isCurrentlyTyping) {
                isCurrentlyTyping = true
                privateChatRepository.updateTypingStatus(convId, currentUserId, true)
            }
            if (it.isBlank() && isCurrentlyTyping) {
                setTypingStatus(false)
            } else if (it.isNotBlank()) {
                typingTimeoutJob = viewModelScope.launch {
                    delay(TYPING_TIMEOUT_MS)
                    setTypingStatus(false)
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun setTypingStatus(isTyping: Boolean) {
        typingTimeoutJob?.cancel()
        val currentUserId = firebaseAuth.currentUser?.uid
        val convId = _conversationId.value
        if (currentUserId != null && convId != null && isCurrentlyTyping != isTyping) {
            isCurrentlyTyping = isTyping
            viewModelScope.launch { privateChatRepository.updateTypingStatus(convId, currentUserId, isTyping) }
        } else if (isCurrentlyTyping != isTyping) {
            isCurrentlyTyping = isTyping
        }
    }

    override fun onCleared() {
        super.onCleared()
        setTypingStatus(false)
        cancelReply()
    }

    private fun markConversationAsRead(conversationId: String, userId: String) {
        viewModelScope.launch {
            val result = privateChatRepository.markConversationAsRead(conversationId, userId)
            if (result is Resource.Error) {
                Log.e("PrivateChatViewModel", "Erreur lors du marquage de la conversation comme lue: ${result.message}")
            }
        }
    }

    private fun loadTargetUser() {
        userProfileRepository.getUserById(targetUserId).onEach { _targetUser.value = it }.launchIn(viewModelScope)
    }

    private fun loadMessages(conversationId: String) {
        privateChatRepository.getConversationMessages(conversationId).onEach { resource ->
            _messages.value = resource
            if (resource is Resource.Success && resource.data != null) {
                markReceivedMessagesAsRead(resource.data)
            }
        }.launchIn(viewModelScope)
    }

    private fun markReceivedMessagesAsRead(messages: List<PrivateMessage>) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val convId = _conversationId.value ?: return
        val messagesToMark = messages.filter {
            it.senderId != currentUserId && it.status == MessageStatus.SENT.name && it.id != null
        }.mapNotNull { it.id }
        if (messagesToMark.isNotEmpty()) {
            viewModelScope.launch {
                privateChatRepository.updateMessagesStatusToRead(convId, messagesToMark)
            }
        }
    }

    fun sendImageMessage(uri: Uri) {
        val convId = _conversationId.value
        if (convId == null) {
            _sendState.value = Resource.Error("ID de conversation non disponible")
            return
        }
        viewModelScope.launch {
            _sendState.value = Resource.Loading()
            setTypingStatus(false)
            _sendState.value = privateChatRepository.sendImageMessage(convId, uri, null)
        }
    }

    fun deleteMessage(messageId: String) {
        val convId = _conversationId.value
        if (convId == null || messageId.isBlank()) return
        viewModelScope.launch {
            _deleteState.value = Resource.Loading()
            _deleteState.value = privateChatRepository.deletePrivateMessage(convId, messageId)
        }
    }

    fun resetDeleteState() {
        _deleteState.value = null
    }

    fun addOrUpdateReaction(messageId: String, emoji: String) {
        val convId = _conversationId.value
        val uid = firebaseAuth.currentUser?.uid
        if (convId == null || uid == null) return
        viewModelScope.launch {
            privateChatRepository.addOrUpdateReaction(convId, messageId, uid, emoji)
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val convId = _conversationId.value
        if (convId == null || messageId.isBlank() || newText.isBlank()) return
        viewModelScope.launch {
            _editState.value = Resource.Loading()
            _editState.value = privateChatRepository.editPrivateMessage(convId, messageId, newText)
        }
    }

    fun resetEditState() {
        _editState.value = null
    }
}