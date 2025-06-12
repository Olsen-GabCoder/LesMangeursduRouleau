package com.lesmangeursdurouleau.app.ui.readings.selection

import android.view.LayoutInflater
import android.view.ViewGroup
// import androidx.core.content.ContextCompat // RETIRÉ : Cet import n'est plus nécessaire et n'est plus utilisé.
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.ItemBookSelectionBinding

class BookSelectionAdapter : ListAdapter<Book, BookSelectionAdapter.BookViewHolder>(BookDiffCallback()) {

    var onItemClick: ((Book) -> Unit)? = null

    var selectedBookId: String? = null
        set(value) {
            val oldSelectedId = field
            field = value
            if (oldSelectedId != null) {
                currentList.indexOfFirst { it.id == oldSelectedId }.let { index ->
                    if (index != -1) notifyItemChanged(index)
                }
            }
            if (value != null) {
                currentList.indexOfFirst { it.id == value }.let { index ->
                    if (index != -1) notifyItemChanged(index)
                }
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = getItem(position)
        holder.bind(book)
    }

    inner class BookViewHolder(private val binding: ItemBookSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(getItem(position))
                }
            }
        }

        fun bind(book: Book) {
            binding.textViewBookTitle.text = book.title
            binding.textViewBookAuthor.text = book.author

            Glide.with(binding.root.context)
                .load(book.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder)
                .into(binding.imageViewBookCover)

            val isSelected = book.id == selectedBookId
            binding.imageViewSelectedIndicator.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE

            if (isSelected) {
                // CORRECTION FINALE : Passer la View (binding.root) à MaterialColors.getColor()
                val primaryColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                binding.cardView.strokeColor = primaryColor
                binding.cardView.strokeWidth = 2
            } else {
                // CORRECTION FINALE : Passer la View (binding.root) à MaterialColors.getColor()
                val colorOnSurfaceVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
                binding.cardView.strokeColor = colorOnSurfaceVariant
                binding.cardView.strokeWidth = 1
            }
        }
    }

    private class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem == newItem
        }
    }
}