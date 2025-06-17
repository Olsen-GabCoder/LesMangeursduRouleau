package com.lesmangeursdurouleau.app.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
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
import android.util.Log

// MODIFICATION: Ajout d'un paramètre lambda pour gérer les clics
class CompletedReadingsAdapter(
    private val onItemClickListener: (CompletedReading) -> Unit
) : ListAdapter<CompletedReading, CompletedReadingsAdapter.CompletedReadingViewHolder>(CompletedReadingDiffCallback()) {

    // MODIFICATION: Le ViewHolder prend maintenant le listener en paramètre pour l'attacher à l'item
    inner class CompletedReadingViewHolder(
        private val binding: ItemCompletedReadingBinding,
        private val onItemClickListener: (CompletedReading) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener(getItem(position))
                }
            }
        }

        fun bind(completedReading: CompletedReading) {
            binding.apply {
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
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompletedReadingViewHolder {
        val binding = ItemCompletedReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // MODIFICATION: Passage du listener au ViewHolder
        return CompletedReadingViewHolder(binding, onItemClickListener)
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