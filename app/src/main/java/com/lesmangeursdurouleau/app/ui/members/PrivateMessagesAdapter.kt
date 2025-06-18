// Fichier : com/lesmangeursdurouleau/app/ui/members/PrivateMessagesAdapter.kt
package com.lesmangeursdurouleau.app.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.databinding.ItemPrivateMessageReceivedBinding
import com.lesmangeursdurouleau.app.databinding.ItemPrivateMessageSentBinding

private const val VIEW_TYPE_SENT = 1
private const val VIEW_TYPE_RECEIVED = 2

class PrivateMessagesAdapter(
    private val currentUserId: String
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
        holder.bind(getItem(position))
    }

    abstract class MessageViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(message: PrivateMessage)

        class SentMessageViewHolder(private val binding: ItemPrivateMessageSentBinding) : MessageViewHolder(binding) {
            override fun bind(message: PrivateMessage) {
                binding.tvMessageBody.text = message.text
            }
        }

        class ReceivedMessageViewHolder(private val binding: ItemPrivateMessageReceivedBinding) : MessageViewHolder(binding) {
            override fun bind(message: PrivateMessage) {
                binding.tvMessageBody.text = message.text
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