package com.lesmangeursdurouleau.app.ui.members

import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.color.MaterialColors
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.databinding.ItemCommentBinding
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CommentsAdapter(
    private val currentUserId: String?,
    private val targetProfileOwnerId: String?, // NOUVEAU: ID du propriétaire du profil affiché (qui est aussi propriétaire de la lecture)
    private val onDeleteClickListener: ((comment: Comment) -> Unit)? = null,
    private val onLikeClickListener: ((comment: Comment) -> Unit)? = null,
    private val getCommentLikeStatus: (commentId: String) -> Flow<Resource<Boolean>>,
    private val lifecycleOwner: LifecycleOwner
) : ListAdapter<Comment, CommentsAdapter.CommentViewHolder>(CommentDiffCallback()) {

    inner class CommentViewHolder(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        private var likeStatusJob: Job? = null // Job pour gérer l'observation du statut de like

        fun bind(comment: Comment) {
            Glide.with(binding.root.context)
                .load(comment.userPhotoUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .circleCrop()
                .into(binding.ivCommentAuthorPicture)

            val authorUsername = comment.userName.takeIf { it.isNotBlank() }
                ?: binding.root.context.getString(R.string.username_not_defined)

            binding.tvCommentAuthorUsername.text = authorUsername
            binding.ivCommentAuthorPicture.contentDescription =
                binding.root.context.getString(R.string.profile_picture_of_user_description, authorUsername)

            binding.tvCommentText.text = comment.commentText
            binding.tvCommentTimestamp.text = formatTimestamp(comment.timestamp.toDate())

            // Gérer la visibilité et le clic du bouton de suppression
            // MODIFICATION: Autorise la suppression si l'utilisateur est l'auteur du commentaire OU le propriétaire du profil.
            val canDelete = currentUserId != null && (comment.userId == currentUserId || currentUserId == targetProfileOwnerId)
            if (canDelete) {
                binding.btnDeleteComment.visibility = View.VISIBLE
                binding.btnDeleteComment.setOnClickListener {
                    onDeleteClickListener?.invoke(comment)
                }
            } else {
                binding.btnDeleteComment.visibility = View.GONE
                binding.btnDeleteComment.setOnClickListener(null)
            }

            // AJOUTS POUR LE DIAGNOSTIC (maintenus temporairement, peuvent être retirés après validation)
            Log.d("CommentsAdapterDebug", "Binding commentaire ID: ${comment.commentId}")
            Log.d("CommentsAdapterDebug", "Current User ID: $currentUserId")
            Log.d("CommentsAdapterDebug", "Comment Author ID: ${comment.userId}")
            Log.d("CommentsAdapterDebug", "Target Profile Owner ID: $targetProfileOwnerId")
            Log.d("CommentsAdapterDebug", "Condition suppression (canDelete): $canDelete")

            // Gérer la visibilité et le clic du bouton de like, et afficher le compteur
            // MODIFICATION: Le bouton de like est visible si l'utilisateur est connecté.
            // L'API Firestore (via le Repository et ViewModel) gérera si le like est autorisé sur son propre commentaire.
            if (currentUserId != null) { // Visible si connecté
                binding.btnLikeComment.visibility = View.VISIBLE
                binding.btnLikeComment.setOnClickListener {
                    onLikeClickListener?.invoke(comment)
                }

                // Afficher le nombre de likes
                binding.btnLikeComment.text = comment.likesCount.toString()

                // Annuler le job précédent pour éviter les fuites de mémoire lors du recyclage
                likeStatusJob?.cancel()

                // Lancer une nouvelle coroutine pour observer le statut de like de ce commentaire
                likeStatusJob = lifecycleOwner.lifecycleScope.launch {
                    getCommentLikeStatus(comment.commentId).collectLatest { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                val isLiked = resource.data ?: false
                                val iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                                binding.btnLikeComment.setIconResource(iconRes)

                                val resolvedIconColor = if (isLiked) {
                                    ContextCompat.getColor(binding.root.context, R.color.red_love)
                                } else {
                                    MaterialColors.getColor(binding.btnLikeComment, com.google.android.material.R.attr.colorOnSurfaceVariant)
                                }
                                binding.btnLikeComment.iconTint = ColorStateList.valueOf(resolvedIconColor)
                            }
                            is Resource.Error -> {
                                Log.e("CommentsAdapter", "Erreur lors du chargement du statut de like du commentaire ${comment.commentId}: ${resource.message}")
                                binding.btnLikeComment.setIconResource(R.drawable.ic_heart_outline)
                                binding.btnLikeComment.iconTint = ColorStateList.valueOf(MaterialColors.getColor(binding.btnLikeComment, com.google.android.material.R.attr.colorOnSurfaceVariant))
                            }
                            is Resource.Loading -> {
                                // Peut afficher un indicateur de chargement si nécessaire
                            }
                        }
                    }
                }
            } else {
                // Si l'utilisateur n'est pas connecté, le bouton de like est masqué.
                binding.btnLikeComment.visibility = View.GONE
                binding.btnLikeComment.setOnClickListener(null)
                likeStatusJob?.cancel() // Annule le job si le bouton est masqué
            }
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.commentId == newItem.commentId
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.commentText == newItem.commentText &&
                    oldItem.timestamp == newItem.timestamp &&
                    oldItem.userId == newItem.userId &&
                    oldItem.userName == newItem.userName &&
                    oldItem.userPhotoUrl == newItem.userPhotoUrl &&
                    oldItem.likesCount == newItem.likesCount &&
                    oldItem.lastLikeTimestamp == newItem.lastLikeTimestamp
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // MODIFICATION DE LA LOGIQUE DU FORMATTAGE DU TIMESTAMP
    private fun formatTimestamp(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        return when {
            diff < TimeUnit.SECONDS.toMillis(60) -> "il y a qlq s."
            diff < TimeUnit.MINUTES.toMillis(60) -> "il y a ${TimeUnit.MILLISECONDS.toMinutes(diff)} min"
            diff < TimeUnit.HOURS.toMillis(24) -> "il y a ${TimeUnit.MILLISECONDS.toHours(diff)} h"
            diff < TimeUnit.DAYS.toMillis(7) -> "il y a ${TimeUnit.MILLISECONDS.toDays(diff)} j"
            else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
        }
    }
}