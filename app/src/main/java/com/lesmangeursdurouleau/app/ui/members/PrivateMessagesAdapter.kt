package com.lesmangeursdurouleau.app.ui.members

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.ChatItem
import com.lesmangeursdurouleau.app.data.model.DateSeparatorItem
import com.lesmangeursdurouleau.app.data.model.MessageItem
import com.lesmangeursdurouleau.app.data.model.MessageStatus
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.databinding.ItemDateSeparatorBinding
import com.lesmangeursdurouleau.app.databinding.ItemPrivateMessageReceivedBinding
import com.lesmangeursdurouleau.app.databinding.ItemPrivateMessageSentBinding
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val VIEW_TYPE_SENT = 1
private const val VIEW_TYPE_RECEIVED = 2
private const val VIEW_TYPE_DATE_SEPARATOR = 3

private const val GROUP_THRESHOLD_MINUTES = 2L

enum class MessagePosition {
    SINGLE, TOP, MIDDLE, BOTTOM
}

class PrivateMessagesAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit,
    private val onImageClick: (imageUrl: String) -> Unit,
    private val formatDateLabel: (date: Date, context: Context) -> String,
    val onMessageSwiped: (message: PrivateMessage) -> Unit,
    private val onReplyClicked: (messageId: String) -> Unit
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is MessageItem -> {
                if (item.message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            }
            is DateSeparatorItem -> VIEW_TYPE_DATE_SEPARATOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val binding = ItemPrivateMessageSentBinding.inflate(inflater, parent, false)
                MessageViewHolder.SentMessageViewHolder(binding, currentUserId, onReplyClicked)
            }
            VIEW_TYPE_RECEIVED -> {
                val binding = ItemPrivateMessageReceivedBinding.inflate(inflater, parent, false)
                MessageViewHolder.ReceivedMessageViewHolder(binding, currentUserId, onReplyClicked)
            }
            VIEW_TYPE_DATE_SEPARATOR -> {
                val binding = ItemDateSeparatorBinding.inflate(inflater, parent, false)
                DateSeparatorViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentItem = getItem(position)

        when (holder) {
            is MessageViewHolder -> {
                val currentMessageItem = currentItem as MessageItem
                val previousMessageItem = findPreviousMessageItem(position)
                val nextMessageItem = findNextMessageItem(position)

                val isFirstInGroup = !areMessagesInSameGroup(currentMessageItem.message, previousMessageItem?.message)
                val isLastInGroup = !areMessagesInSameGroup(currentMessageItem.message, nextMessageItem?.message)

                val messagePosition = when {
                    isFirstInGroup && isLastInGroup -> MessagePosition.SINGLE
                    isFirstInGroup && !isLastInGroup -> MessagePosition.TOP
                    !isFirstInGroup && isLastInGroup -> MessagePosition.BOTTOM
                    else -> MessagePosition.MIDDLE
                }

                holder.bind(currentMessageItem.message, onMessageLongClick, onImageClick, messagePosition)
            }
            is DateSeparatorViewHolder -> {
                val dateSeparatorItem = currentItem as DateSeparatorItem
                holder.bind(dateSeparatorItem, formatDateLabel)
            }
        }
    }

    private fun findPreviousMessageItem(currentPosition: Int): MessageItem? {
        for (i in currentPosition - 1 downTo 0) {
            val item = getItem(i)
            if (item is MessageItem) return item
        }
        return null
    }

    private fun findNextMessageItem(currentPosition: Int): MessageItem? {
        for (i in currentPosition + 1 until itemCount) {
            val item = getItem(i)
            if (item is MessageItem) return item
        }
        return null
    }

    private fun areMessagesInSameGroup(msg1: PrivateMessage, msg2: PrivateMessage?): Boolean {
        if (msg2 == null) return false
        val time1 = msg1.timestamp ?: return false
        val time2 = msg2.timestamp ?: return false
        val diffInMillis = abs(time1.time - time2.time)
        val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
        return msg1.senderId == msg2.senderId && diffInMinutes < GROUP_THRESHOLD_MINUTES
    }

    class DateSeparatorViewHolder(private val binding: ItemDateSeparatorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DateSeparatorItem, formatDateLabel: (date: Date, context: Context) -> String) {
            binding.tvDateSeparator.text = formatDateLabel(item.timestamp, itemView.context)
        }
    }

    abstract class MessageViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        @RequiresApi(Build.VERSION_CODES.N)
        protected val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        @RequiresApi(Build.VERSION_CODES.N)
        abstract fun bind(
            message: PrivateMessage,
            onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit,
            onImageClick: (imageUrl: String) -> Unit,
            positionInGroup: MessagePosition
        )

        class SentMessageViewHolder(
            private val binding: ItemPrivateMessageSentBinding,
            private val currentUserId: String,
            private val onReplyClicked: (messageId: String) -> Unit
        ) : MessageViewHolder(binding) {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun bind(
                message: PrivateMessage,
                onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit,
                onImageClick: (imageUrl: String) -> Unit,
                positionInGroup: MessagePosition
            ) {
                if (message.replyInfo != null) {
                    binding.replyContainer.isVisible = true
                    binding.tvReplySenderName.text = if (message.replyInfo.repliedToSenderName == "Vous") "Vous" else message.replyInfo.repliedToSenderName
                    binding.tvReplyPreview.text = message.replyInfo.repliedToMessagePreview
                    binding.replyContainer.setOnClickListener {
                        onReplyClicked(message.replyInfo.repliedToMessageId)
                    }
                } else {
                    binding.replyContainer.isVisible = false
                }

                if (!message.text.isNullOrBlank()) {
                    binding.tvMessageBody.isVisible = true
                    binding.tvMessageBody.text = message.text
                } else {
                    binding.tvMessageBody.isVisible = false
                }
                if (message.imageUrl != null) {
                    binding.ivMessageImage.isVisible = true
                    Glide.with(itemView.context).load(message.imageUrl).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_error).into(binding.ivMessageImage)
                    binding.ivMessageImage.setOnClickListener { onImageClick(message.imageUrl) }
                } else {
                    binding.ivMessageImage.isVisible = false
                }
                binding.tvMessageTimestamp.text = message.timestamp?.let { timeFormat.format(it) } ?: ""
                binding.llReactionsContainer.isVisible = message.reactions.isNotEmpty()
                if (message.reactions.isNotEmpty()) {
                    binding.tvReactions.text = message.reactions.values.joinToString("")
                }
                binding.tvEditedIndicator.isVisible = message.isEdited
                val statusIcon = binding.ivMessageStatus
                when (message.status) {
                    MessageStatus.READ.name -> {
                        statusIcon.isVisible = true
                        statusIcon.setImageResource(R.drawable.ic_check_double)
                        statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.status_read_color))
                    }
                    MessageStatus.SENT.name -> {
                        statusIcon.isVisible = true
                        statusIcon.setImageResource(R.drawable.ic_check_single)
                        statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.white_60_alpha))
                    }
                    else -> statusIcon.isVisible = false
                }
                itemView.setOnLongClickListener { onMessageLongClick(binding.bubbleContainer, message); true }

                val backgroundRes = when (positionInGroup) {
                    MessagePosition.SINGLE -> R.drawable.bg_chat_bubble_sent
                    MessagePosition.TOP -> R.drawable.bg_chat_bubble_sent_top
                    MessagePosition.MIDDLE -> R.drawable.bg_chat_bubble_sent_middle
                    MessagePosition.BOTTOM -> R.drawable.bg_chat_bubble_sent_bottom
                }
                binding.bubbleContainer.setBackgroundResource(backgroundRes)

                val topPadding = if (positionInGroup == MessagePosition.TOP || positionInGroup == MessagePosition.SINGLE) {
                    itemView.context.resources.getDimensionPixelSize(R.dimen.message_group_spacing_default)
                } else {
                    itemView.context.resources.getDimensionPixelSize(R.dimen.message_group_spacing_reduced)
                }
                itemView.setPadding(itemView.paddingLeft, topPadding, itemView.paddingRight, itemView.paddingBottom)
            }
        }

        class ReceivedMessageViewHolder(
            private val binding: ItemPrivateMessageReceivedBinding,
            private val currentUserId: String,
            private val onReplyClicked: (messageId: String) -> Unit
        ) : MessageViewHolder(binding) {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun bind(
                message: PrivateMessage,
                onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit,
                onImageClick: (imageUrl: String) -> Unit,
                positionInGroup: MessagePosition
            ) {
                if (message.replyInfo != null) {
                    binding.replyContainer.isVisible = true
                    binding.tvReplySenderName.text = if (message.replyInfo.repliedToSenderName == "Vous") "Vous" else message.replyInfo.repliedToSenderName
                    binding.tvReplyPreview.text = message.replyInfo.repliedToMessagePreview
                    binding.replyContainer.setOnClickListener {
                        onReplyClicked(message.replyInfo.repliedToMessageId)
                    }
                } else {
                    binding.replyContainer.isVisible = false
                }

                if (!message.text.isNullOrBlank()) {
                    binding.tvMessageBody.isVisible = true
                    binding.tvMessageBody.text = message.text
                } else {
                    binding.tvMessageBody.isVisible = false
                }
                if (message.imageUrl != null) {
                    binding.ivMessageImage.isVisible = true
                    Glide.with(itemView.context).load(message.imageUrl).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_error).into(binding.ivMessageImage)
                    binding.ivMessageImage.setOnClickListener { onImageClick(message.imageUrl) }
                } else {
                    binding.ivMessageImage.isVisible = false
                }
                binding.tvMessageTimestamp.text = message.timestamp?.let { timeFormat.format(it) } ?: ""
                binding.llReactionsContainer.isVisible = message.reactions.isNotEmpty()
                if (message.reactions.isNotEmpty()) {
                    binding.tvReactions.text = message.reactions.values.joinToString("")
                }
                binding.tvEditedIndicator.isVisible = message.isEdited
                itemView.setOnLongClickListener { onMessageLongClick(binding.bubbleContainer, message); true }

                val backgroundRes = when (positionInGroup) {
                    MessagePosition.SINGLE -> R.drawable.bg_chat_bubble_received
                    MessagePosition.TOP -> R.drawable.bg_chat_bubble_received_top
                    MessagePosition.MIDDLE -> R.drawable.bg_chat_bubble_received_middle
                    MessagePosition.BOTTOM -> R.drawable.bg_chat_bubble_received_bottom
                }
                binding.bubbleContainer.setBackgroundResource(backgroundRes)

                val topPadding = if (positionInGroup == MessagePosition.TOP || positionInGroup == MessagePosition.SINGLE) {
                    itemView.context.resources.getDimensionPixelSize(R.dimen.message_group_spacing_default)
                } else {
                    itemView.context.resources.getDimensionPixelSize(R.dimen.message_group_spacing_reduced)
                }
                itemView.setPadding(itemView.paddingLeft, topPadding, itemView.paddingRight, itemView.paddingBottom)
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem == newItem
        }
    }
}