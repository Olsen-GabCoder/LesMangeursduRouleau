package com.lesmangeursdurouleau.app.ui.members

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
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

                // MODIFIÉ: Gérer l'affichage des réactions
                if (message.reactions.isNotEmpty()) {
                    binding.llReactionsContainer.isVisible = true
                    // Concatène les emojis pour l'affichage
                    binding.tvReactions.text = message.reactions.values.joinToString("")
                } else {
                    binding.llReactionsContainer.isVisible = false
                }

                itemView.setOnLongClickListener {
                    // MODIFIÉ: Ancrer le menu à la bulle de message pour un meilleur positionnement
                    onMessageLongClick(binding.tvMessageBody, message)
                    true // Indique que l'événement a été consommé
                }
            }
        }

        class ReceivedMessageViewHolder(private val binding: ItemPrivateMessageReceivedBinding) : MessageViewHolder(binding) {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun bind(message: PrivateMessage, onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit) {
                binding.tvMessageBody.text = message.text
                binding.tvMessageTimestamp.text = message.timestamp?.let { timeFormat.format(it) } ?: ""

                // MODIFIÉ: Gérer l'affichage des réactions
                if (message.reactions.isNotEmpty()) {
                    binding.llReactionsContainer.isVisible = true
                    // Concatène les emojis pour l'affichage
                    binding.tvReactions.text = message.reactions.values.joinToString("")
                } else {
                    binding.llReactionsContainer.isVisible = false
                }

                itemView.setOnLongClickListener {
                    // MODIFIÉ: Ancrer le menu à la bulle de message pour un meilleur positionnement
                    onMessageLongClick(binding.tvMessageBody, message)
                    true // Indique que l'événement a été consommé
                }
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<PrivateMessage>() {
        override fun areItemsTheSame(oldItem: PrivateMessage, newItem: PrivateMessage): Boolean {
            // MODIFIÉ: Utiliser l'ID unique du message pour une comparaison plus fiable.
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PrivateMessage, newItem: PrivateMessage): Boolean {
            // Cette comparaison est maintenant efficace car elle inclut le champ 'reactions'.
            // DiffUtil détectera un changement dans les réactions et mettra à jour la vue.
            return oldItem == newItem
        }
    }
}