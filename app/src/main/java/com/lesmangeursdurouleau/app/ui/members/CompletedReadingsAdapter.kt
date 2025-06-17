package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.databinding.ItemCompletedReadingBinding
import java.text.SimpleDateFormat
import java.util.Locale

class CompletedReadingsAdapter(
    private val currentUserId: String?,
    private val profileOwnerId: String,
    // MODIFIÉ: Le listener passe maintenant la vue cliquée (la couverture) pour la transition
    private val onItemClickListener: (completedReading: CompletedReading, coverImageView: View) -> Unit,
    private val onDeleteClickListener: (CompletedReading) -> Unit
) : ListAdapter<CompletedReading, CompletedReadingsAdapter.CompletedReadingViewHolder>(CompletedReadingDiffCallback()) {

    inner class CompletedReadingViewHolder(
        private val binding: ItemCompletedReadingBinding,
        private val onItemClickListener: (completedReading: CompletedReading, coverImageView: View) -> Unit,
        private val onDeleteClickListener: (CompletedReading) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(completedReading: CompletedReading) {
            binding.apply {
                // NOUVEAU: Assigner un nom de transition unique à l'image de couverture
                val transitionName = "cover_${completedReading.bookId}"
                ViewCompat.setTransitionName(ivBookCover, transitionName)

                // MODIFIÉ: Le clic sur la racine passe l'objet et la vue de l'image
                root.setOnClickListener {
                    onItemClickListener(completedReading, ivBookCover)
                }

                Glide.with(ivBookCover.context)
                    .load(completedReading.coverImageUrl)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivBookCover)

                tvBookTitle.text = completedReading.title
                tvBookAuthor.text = completedReading.author

                completedReading.completionDate?.let { date ->
                    val dateFormat = SimpleDateFormat("'Terminé le' dd MMMM yyyy", Locale.getDefault())
                    tvCompletionDate.text = dateFormat.format(date)
                } ?: run {
                    tvCompletionDate.text = binding.root.context.getString(R.string.date_completion_unknown)
                    Log.w("CompletedReadingsAdapter", "Date de complétion manquante pour le livre: ${completedReading.title}")
                }

                val isOwner = currentUserId != null && currentUserId == profileOwnerId
                btnDeleteReading.isVisible = isOwner
                if (isOwner) {
                    btnDeleteReading.setOnClickListener {
                        onDeleteClickListener(completedReading)
                    }
                } else {
                    btnDeleteReading.setOnClickListener(null)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompletedReadingViewHolder {
        val binding = ItemCompletedReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CompletedReadingViewHolder(binding, onItemClickListener, onDeleteClickListener)
    }

    override fun onBindViewHolder(holder: CompletedReadingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CompletedReadingDiffCallback : DiffUtil.ItemCallback<CompletedReading>() {
        override fun areItemsTheSame(oldItem: CompletedReading, newItem: CompletedReading): Boolean {
            return oldItem.bookId == newItem.bookId
        }

        override fun areContentsTheSame(oldItem: CompletedReading, newItem: CompletedReading): Boolean {
            return oldItem == newItem
        }
    }
}