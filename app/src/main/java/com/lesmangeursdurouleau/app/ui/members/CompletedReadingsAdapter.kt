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
import android.util.Log // Ajout de l'import pour Log, utile pour le débogage

class CompletedReadingsAdapter : ListAdapter<CompletedReading, CompletedReadingsAdapter.CompletedReadingViewHolder>(CompletedReadingDiffCallback()) {

    inner class CompletedReadingViewHolder(private val binding: ItemCompletedReadingBinding) : RecyclerView.ViewHolder(binding.root) {
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
                    // CORRECTION ICI: Encadrer le texte littéral par des guillemets simples
                    val dateFormat = SimpleDateFormat("'Terminé le' dd MMMM yyyy", Locale.getDefault())
                    tvCompletionDate.text = dateFormat.format(date)
                } ?: run {
                    // CORRECTION ICI: Utilisation d'une ressource string pour le texte par défaut
                    tvCompletionDate.text = binding.root.context.getString(R.string.date_completion_unknown)
                    Log.w("CompletedReadingsAdapter", "Date de complétion manquante pour le livre: ${completedReading.title}")
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompletedReadingViewHolder {
        val binding = ItemCompletedReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CompletedReadingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CompletedReadingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CompletedReadingDiffCallback : DiffUtil.ItemCallback<CompletedReading>() {
        override fun areItemsTheSame(oldItem: CompletedReading, newItem: CompletedReading): Boolean {
            return oldItem.bookId == newItem.bookId
        }

        // CORRECTION ICI: Renommer la méthode de areContentsAreTheSame à areContentsTheSame
        override fun areContentsTheSame(oldItem: CompletedReading, newItem: CompletedReading): Boolean {
            return oldItem == newItem
        }
    }
}