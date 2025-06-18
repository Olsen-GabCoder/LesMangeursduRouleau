// Fichier : com/lesmangeursdurouleau/app/ui/members/PrivateMessagesAdapter.kt
package com.lesmangeursdurouleau.app.ui.members

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
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
    // AJOUT: Lambda pour gérer le clic long sur un message
    private val onMessageLongClick: (PrivateMessage) -> Unit
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
        // MODIFIÉ: On passe le message ET le callback au ViewHolder
        holder.bind(getItem(position), onMessageLongClick)
    }

    // MODIFIÉ: La signature de la classe abstraite et de ses enfants a changé
    abstract class MessageViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        @RequiresApi(Build.VERSION_CODES.N)
        protected val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        abstract fun bind(message: PrivateMessage, onMessageLongClick: (PrivateMessage) -> Unit)

        class SentMessageViewHolder(private val binding: ItemPrivateMessageSentBinding) : MessageViewHolder(binding) {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun bind(message: PrivateMessage, onMessageLongClick: (PrivateMessage) -> Unit) {
                binding.tvMessageBody.text = message.text
                // L'heure du message est déjà gérée dans les améliorations précédentes
                binding.tvMessageTimestamp.text = message.timestamp?.let { timeFormat.format(it) } ?: ""

                // AJOUT: Gestion du clic long uniquement pour les messages envoyés
                itemView.setOnLongClickListener {
                    onMessageLongClick(message)
                    true // Indique que l'événement a été consommé
                }
            }
        }

        class ReceivedMessageViewHolder(private val binding: ItemPrivateMessageReceivedBinding) : MessageViewHolder(binding) {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun bind(message: PrivateMessage, onMessageLongClick: (PrivateMessage) -> Unit) {
                binding.tvMessageBody.text = message.text
                // L'heure du message est déjà gérée dans les améliorations précédentes
                binding.tvMessageTimestamp.text = message.timestamp?.let { timeFormat.format(it) } ?: ""

                // Pas de listener de clic long pour les messages reçus
                itemView.setOnLongClickListener(null)
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<PrivateMessage>() {
        override fun areItemsTheSame(oldItem: PrivateMessage, newItem: PrivateMessage): Boolean {
            // Un message est unique par son senderId et son timestamp.
            // Idéalement, on utiliserait un ID de document unique si le modèle en avait un.
            return oldItem.senderId == newItem.senderId && oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: PrivateMessage, newItem: PrivateMessage): Boolean {
            return oldItem == newItem
        }
    }
}