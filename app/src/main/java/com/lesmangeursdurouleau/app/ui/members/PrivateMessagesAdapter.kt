package com.lesmangeursdurouleau.app.ui.members

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
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.MessageStatus
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.databinding.ItemPrivateMessageReceivedBinding
import com.lesmangeursdurouleau.app.databinding.ItemPrivateMessageSentBinding
import java.util.Locale

private const val VIEW_TYPE_SENT = 1
private const val VIEW_TYPE_RECEIVED = 2

class PrivateMessagesAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit
) : ListAdapter<PrivateMessage, PrivateMessagesAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val binding = ItemPrivateMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            MessageViewHolder.SentMessageViewHolder(binding)
        } else {
            val binding = ItemPrivateMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            MessageViewHolder.ReceivedMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position), onMessageLongClick)
    }

    abstract class MessageViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        @RequiresApi(Build.VERSION_CODES.N)
        protected val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        abstract fun bind(message: PrivateMessage, onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit)

        class SentMessageViewHolder(private val binding: ItemPrivateMessageSentBinding) : MessageViewHolder(binding) {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun bind(message: PrivateMessage, onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit) {
                binding.tvMessageBody.text = message.text
                binding.tvMessageTimestamp.text = message.timestamp?.let { timeFormat.format(it) } ?: ""

                if (message.reactions.isNotEmpty()) {
                    binding.llReactionsContainer.isVisible = true
                    binding.tvReactions.text = message.reactions.values.joinToString("")
                } else {
                    binding.llReactionsContainer.isVisible = false
                }

                binding.tvEditedIndicator.isVisible = message.isEdited

                // AJOUT: Logique d'affichage de l'indicateur de statut
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
                    else -> {
                        statusIcon.isVisible = false
                    }
                }


                itemView.setOnLongClickListener {
                    onMessageLongClick(binding.bubbleContainer, message)
                    true
                }
            }
        }

        class ReceivedMessageViewHolder(private val binding: ItemPrivateMessageReceivedBinding) : MessageViewHolder(binding) {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun bind(message: PrivateMessage, onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit) {
                binding.tvMessageBody.text = message.text
                binding.tvMessageTimestamp.text = message.timestamp?.let { timeFormat.format(it) } ?: ""

                if (message.reactions.isNotEmpty()) {
                    binding.llReactionsContainer.isVisible = true
                    binding.tvReactions.text = message.reactions.values.joinToString("")
                } else {
                    binding.llReactionsContainer.isVisible = false
                }

                binding.tvEditedIndicator.isVisible = message.isEdited

                itemView.setOnLongClickListener {
                    onMessageLongClick(binding.bubbleContainer, message)
                    true
                }
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<PrivateMessage>() {
        override fun areItemsTheSame(oldItem: PrivateMessage, newItem: PrivateMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PrivateMessage, newItem: PrivateMessage): Boolean {
            return oldItem == newItem
        }
    }
}