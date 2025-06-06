package com.lesmangeursdurouleau.app.ui.chat.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Message // Assurez-vous que cette classe a reactions: Map<String, Int>
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Interface existante pour le clic sur le profil
interface OnProfileClickListener {
    fun onProfileClicked(userId: String, username: String)
}

// NOUVELLE INTERFACE pour les interactions avec les messages (appui long et réaction)
interface OnMessageInteractionListener {
    fun onMessageLongClicked(message: Message, anchorView: View)
    // Ajouté : Pour gérer le clic sur un emoji de réaction déjà affiché (pour le basculer)
    fun onReactionClicked(message: Message, reactionEmoji: String)
}

class ChatAdapter(
    private val profileClickListener: OnProfileClickListener,
    private val messageInteractionListener: OnMessageInteractionListener
) : ListAdapter<Message, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var userDetailsMap: Map<String, User> = emptyMap()

    // NOUVEAU: Liste d'emojis de réaction disponibles (peut être étendue, mais n'est pas utilisée directement dans l'affichage ici)
    // Elle sera utile dans le ViewModel/UI pour afficher les options de réaction.
    private val availableEmojis = listOf("👍", "❤️", "😂", "😡", "🥳")

    companion object {
        private const val TAG = "ChatAdapter"
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val SENDER_INFO_CONSOLIDATION_THRESHOLD_MS = 5 * 60 * 1000L
    }

    fun setUserDetails(newUserMap: Map<String, User>) {
        val oldUserMap = userDetailsMap
        userDetailsMap = newUserMap
        if (oldUserMap != newUserMap) {
            Log.d(TAG, "setUserDetails: Cache mis à jour. Items: ${itemCount}")
            // Comme un changement dans userDetailsMap peut affecter l'affichage de l'avatar/nom d'utilisateur
            // de *tous* les messages, une notification plus large est nécessaire.
            // notifyDataSetChanged() est inefficace. Une meilleure approche serait de trouver les messages
            // affectés et d'appeler notifyItemChanged pour chacun, mais cela complexifie l'adaptateur.
            // Pour l'instant, notifyItemRangeChanged est un compromis.
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        val previousMessage = if (position > 0) getItem(position - 1) else null
        holder.bind(message, getItemViewType(position), previousMessage)
    }

    inner class MessageViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            Log.d(TAG, "MessageViewHolder init pour item à la position (au moment de la création): $adapterPosition")

            val clickListener = View.OnClickListener { view ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = getItem(position)
                    Log.d(TAG, "Listener de clic profil activé pour message: ${message.text}")
                    if (message.senderId != currentUserId && message.senderId.isNotBlank()) {
                        val userDetails = userDetailsMap[message.senderId]
                        val usernameToPass = userDetails?.username?.takeIf { it.isNotEmpty() }
                            ?: message.senderUsername.takeIf { it.isNotEmpty() }
                            ?: itemView.context.getString(R.string.unknown_user)

                        Log.i(TAG, "APPEL profileClickListener.onProfileClicked. UserID: ${message.senderId}")
                        profileClickListener.onProfileClicked(message.senderId, usernameToPass)
                    } else {
                        Log.d(TAG, "Clic sur profil/avatar ignoré (message envoyé ou senderId vide). senderId: '${message.senderId}'")
                    }
                } else {
                    Log.w(TAG, "Clic sur profil/avatar ignoré. NO_POSITION")
                }
            }
            binding.ivSenderAvatar.setOnClickListener(clickListener)
            binding.tvMessageSender.setOnClickListener(clickListener)

            // Listener pour l'appui long
            binding.bubbleLayoutContainer.setOnLongClickListener { view ->
                Log.d(TAG, "bubbleLayoutContainer APPUI LONG DÉTECTÉ pour item à la position: $adapterPosition")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = getItem(position)
                    Log.i(TAG, "APPEL messageInteractionListener.onMessageLongClicked pour MessageID: ${message.messageId}")
                    messageInteractionListener.onMessageLongClicked(message, binding.bubbleLayoutContainer)
                    return@setOnLongClickListener true
                }
                Log.w(TAG, "Appui long BUBBLE ignoré. NO_POSITION")
                return@setOnLongClickListener false
            }

            // Le conteneur de réactions lui-même peut avoir un clic pour déclencher l'ajout d'une nouvelle réaction
            // Ou pour ouvrir un sélecteur d'emoji. C'est à vous de décider de la logique ici.
            // Pour l'instant, je vais laisser le long clic gérer cela, et ce listener peut être utilisé pour d'autres actions.
            binding.llReactionsContainer.setOnClickListener {
                // Vous pouvez implémenter une action ici, par exemple, afficher un sélecteur d'emoji
                // ou ajouter/retirer une réaction par défaut.
                Log.d(TAG, "Clic sur le conteneur de réactions.")
            }
        }

        fun bind(message: Message, viewType: Int, previousMessage: Message?) {
            binding.tvMessageText.text = message.text
            val senderDetails = userDetailsMap[message.senderId]
            val showSenderDetails = shouldShowSenderDetails(message, previousMessage, viewType)

            if (viewType == VIEW_TYPE_RECEIVED) {
                if (showSenderDetails) {
                    binding.tvMessageSender.text = senderDetails?.username?.takeIf { it.isNotEmpty() }
                        ?: message.senderUsername.takeIf { it.isNotEmpty() }
                                ?: itemView.context.getString(R.string.unknown_user)
                    binding.tvMessageSender.visibility = View.VISIBLE
                    binding.ivSenderAvatar.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(senderDetails?.profilePictureUrl)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(binding.ivSenderAvatar)
                } else {
                    binding.tvMessageSender.visibility = View.GONE
                    binding.ivSenderAvatar.visibility = View.INVISIBLE
                }
            } else { // VIEW_TYPE_SENT
                binding.tvMessageSender.visibility = View.GONE
                binding.ivSenderAvatar.visibility = View.GONE
            }

            if (message.timestamp != null) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.tvMessageTimestamp.text = sdf.format(message.timestamp)
                binding.tvMessageTimestamp.visibility = View.VISIBLE
                binding.tvMessageTimestamp.setTextAppearance(R.style.ChatTimestamp)
                if (viewType == VIEW_TYPE_SENT) {
                    binding.tvMessageTimestamp.setTextColor(itemView.context.getColor(R.color.chat_text_sent_dark))
                } else {
                    binding.tvMessageTimestamp.setTextColor(itemView.context.getColor(R.color.chat_text_received_dark))
                }
            } else {
                binding.tvMessageTimestamp.visibility = View.GONE
            }

            val constraintLayoutRoot = binding.root as ConstraintLayout
            val set = ConstraintSet()
            set.clone(constraintLayoutRoot)
            set.clear(binding.bubbleLayoutContainer.id, ConstraintSet.START)
            set.clear(binding.bubbleLayoutContainer.id, ConstraintSet.END)
            // Réinitialiser les contraintes du conteneur de réactions pour un re-calcul propre
            set.clear(binding.llReactionsContainer.id, ConstraintSet.START)
            set.clear(binding.llReactionsContainer.id, ConstraintSet.END)
            set.clear(binding.llReactionsContainer.id, ConstraintSet.TOP)
            set.clear(binding.llReactionsContainer.id, ConstraintSet.BOTTOM)


            if (viewType == VIEW_TYPE_SENT) {
                binding.bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_sent_dark)
                binding.tvMessageText.setTextAppearance(R.style.ChatTextSent)
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                set.setHorizontalBias(binding.bubbleLayoutContainer.id, 1.0f)

                // Positionner les réactions pour les messages envoyés (en bas à gauche de la bulle)
                set.connect(binding.llReactionsContainer.id, ConstraintSet.START, binding.bubbleLayoutContainer.id, ConstraintSet.START, 0)
                set.connect(binding.llReactionsContainer.id, ConstraintSet.TOP, binding.bubbleLayoutContainer.id, ConstraintSet.BOTTOM, itemView.context.resources.getDimensionPixelSize(R.dimen.reaction_container_margin_top))
                set.constrainWidth(binding.llReactionsContainer.id, ConstraintSet.WRAP_CONTENT)
                set.constrainHeight(binding.llReactionsContainer.id, ConstraintSet.WRAP_CONTENT)

            } else { // VIEW_TYPE_RECEIVED
                binding.bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_received_dark)
                binding.tvMessageText.setTextAppearance(R.style.ChatTextReceived)
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.setHorizontalBias(binding.bubbleLayoutContainer.id, 0.0f)

                // Positionner les réactions pour les messages reçus (en bas à droite de la bulle)
                set.connect(binding.llReactionsContainer.id, ConstraintSet.END, binding.bubbleLayoutContainer.id, ConstraintSet.END, 0)
                set.connect(binding.llReactionsContainer.id, ConstraintSet.TOP, binding.bubbleLayoutContainer.id, ConstraintSet.BOTTOM, itemView.context.resources.getDimensionPixelSize(R.dimen.reaction_container_margin_top))
                set.constrainWidth(binding.llReactionsContainer.id, ConstraintSet.WRAP_CONTENT)
                set.constrainHeight(binding.llReactionsContainer.id, ConstraintSet.WRAP_CONTENT)
            }

            set.applyTo(constraintLayoutRoot)

            // Gérer l'affichage des réactions
            displayReactions(message)
        }

        private fun shouldShowSenderDetails(message: Message, previousMessage: Message?, viewType: Int): Boolean {
            if (viewType == VIEW_TYPE_SENT) return false // Ne jamais afficher les détails de l'expéditeur pour les messages envoyés par l'utilisateur actuel

            if (previousMessage == null) return true // Toujours afficher pour le premier message

            val timeDiff = message.timestamp?.time?.let { it - (previousMessage.timestamp?.time ?: 0) } ?: Long.MAX_VALUE
            val isSameSender = message.senderId == previousMessage.senderId

            // N'afficher les détails de l'expéditeur que si ce n'est pas le même expéditeur OU si un certain temps s'est écoulé
            return !isSameSender || timeDiff > SENDER_INFO_CONSOLIDATION_THRESHOLD_MS
        }

        private fun displayReactions(message: Message) {
            binding.llReactionsContainer.removeAllViews() // Nettoyer les vues précédentes

            // C'est ici que le champ 'reactions' est crucial.
            // Si vous n'avez pas réintroduit 'reactions: Map<String, Int>' dans Message,
            // cette ligne compilera une erreur ou 'reactionsMap' sera toujours vide.
            val reactionsMap = message.reactions // Récupère la map d'emoji à leur compte total
            val userReactionEmoji = message.userReaction // La réaction de l'utilisateur courant

            if (reactionsMap.isNotEmpty()) {
                binding.llReactionsContainer.visibility = View.VISIBLE

                // Créer une liste triée d'entrées (emoji -> count) pour un affichage cohérent
                val sortedReactions = reactionsMap.entries.sortedByDescending { it.value }

                for ((emoji, count) in sortedReactions) {
                    val reactionView = LayoutInflater.from(binding.root.context)
                        .inflate(R.layout.item_reaction_chip, binding.llReactionsContainer, false) as LinearLayout
                    val tvEmoji = reactionView.findViewById<TextView>(R.id.tv_emoji)
                    val tvCount = reactionView.findViewById<TextView>(R.id.tv_count)

                    tvEmoji.text = emoji
                    tvCount.text = count.toString()

                    // Mettre en surbrillance si c'est la réaction de l'utilisateur courant
                    if (emoji == userReactionEmoji) {
                        reactionView.setBackgroundResource(R.drawable.bg_reaction_chip_selected)
                        tvEmoji.setTextColor(binding.root.context.getColor(R.color.reaction_text_color_selected))
                        tvCount.setTextColor(binding.root.context.getColor(R.color.reaction_text_color_selected))
                    } else {
                        reactionView.setBackgroundResource(R.drawable.bg_reaction_chip_default)
                        tvEmoji.setTextColor(binding.root.context.getColor(R.color.reaction_text_color))
                        tvCount.setTextColor(binding.root.context.getColor(R.color.reaction_text_color))
                    }

                    // Gérer le clic sur un emoji de réaction existant (pour basculer la réaction)
                    reactionView.setOnClickListener {
                        messageInteractionListener.onReactionClicked(message, emoji)
                    }

                    binding.llReactionsContainer.addView(reactionView)
                }
            } else {
                binding.llReactionsContainer.visibility = View.GONE
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.messageId == newItem.messageId
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            // Compare tous les champs pertinents pour détecter un changement
            // Inclure les réactions et la réaction de l'utilisateur pour une mise à jour correcte
            return oldItem.text == newItem.text &&
                    oldItem.senderId == newItem.senderId &&
                    oldItem.senderUsername == newItem.senderUsername && // Important si le nom d'utilisateur peut changer
                    oldItem.timestamp == newItem.timestamp &&
                    oldItem.reactions == newItem.reactions && // TRÈS IMPORTANT: Nécessite que 'reactions' soit dans Message
                    oldItem.userReaction == newItem.userReaction // TRÈS IMPORTANT: Pour l'état de la réaction de l'utilisateur
        }
    }
}