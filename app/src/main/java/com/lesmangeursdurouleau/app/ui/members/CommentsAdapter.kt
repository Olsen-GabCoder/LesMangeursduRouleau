// app/src/main/java/com/lesmangeursdurouleau/app/ui/members/CommentsAdapter.kt
package com.lesmangeursdurouleau.app.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.databinding.ItemCommentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CommentsAdapter : ListAdapter<Comment, CommentsAdapter.CommentViewHolder>(CommentDiffCallback()) {

    // Inner class pour le ViewHolder
    inner class CommentViewHolder(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: Comment) {
            // Charger la photo de profil de l'auteur du commentaire
            Glide.with(binding.root.context)
                .load(comment.userPhotoUrl)
                .placeholder(R.drawable.ic_profile_placeholder) // Placeholder si l'image charge ou est nulle
                .error(R.drawable.ic_profile_placeholder)       // Image d'erreur si le chargement échoue
                .transition(DrawableTransitionOptions.withCrossFade())
                .circleCrop() // Pour une image de profil ronde
                .into(binding.ivCommentAuthorPicture)

            // Afficher le nom d'utilisateur de l'auteur
            binding.tvCommentAuthorUsername.text = comment.userName.takeIf { it.isNotBlank() }
                ?: binding.root.context.getString(R.string.username_not_defined)

            // Afficher le texte du commentaire
            binding.tvCommentText.text = comment.commentText

            // Afficher l'horodatage formaté (ex: "il y a 5 min", "1 jour", "22 mars")
            binding.tvCommentTimestamp.text = formatTimestamp(comment.timestamp.toDate())
        }
    }

    // Callback pour la comparaison des éléments dans ListAdapter (efficacité des mises à jour)
    class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.commentId == newItem.commentId
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Formate un horodatage en une chaîne lisible (ex: "il y a 5 min", "1 jour", "12 mars 2024").
     * @param date L'objet Date à formater.
     * @return La chaîne formatée.
     */
    private fun formatTimestamp(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "il y a qlq s." // Quelques secondes
            diff < TimeUnit.HOURS.toMillis(1) -> "il y a ${TimeUnit.MILLISECONDS.toMinutes(diff)} min" // Moins d'une heure
            diff < TimeUnit.DAYS.toMillis(1) -> "il y a ${TimeUnit.MILLISECONDS.toHours(diff)} h" // Moins d'un jour
            diff < TimeUnit.DAYS.toMillis(7) -> "il y a ${TimeUnit.MILLISECONDS.toDays(diff)} j" // Moins d'une semaine
            else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date) // Plus d'une semaine, format complet
        }
    }
}