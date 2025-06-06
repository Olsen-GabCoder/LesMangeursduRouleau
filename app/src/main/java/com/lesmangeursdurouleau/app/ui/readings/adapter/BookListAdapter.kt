package com.lesmangeursdurouleau.app.ui.readings.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // AJOUT: Import de Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.ItemBookBinding

class BookListAdapter(private val onItemClicked: (Book) -> Unit) :
    ListAdapter<Book, BookListAdapter.BookViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = getItem(position)
        holder.bind(book)
        holder.itemView.setOnClickListener {
            onItemClicked(book)
        }
    }

    inner class BookViewHolder(private val binding: ItemBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(book: Book) {
            binding.tvBookTitle.text = book.title
            binding.tvBookAuthor.text = book.author
            binding.tvBookSynopsisPreview.text = book.synopsis ?: itemView.context.getString(R.string.no_synopsis_available)

            // Charger l'image de couverture avec Glide
            if (!book.coverImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context) // Contexte (peut être l'itemView, un fragment, une activity)
                    .load(book.coverImageUrl) // URL de l'image
                    .placeholder(R.drawable.ic_book_placeholder) // Image affichée pendant le chargement (OPTIONNEL)
                    .error(R.drawable.ic_book_placeholder_error) // Image affichée en cas d'erreur de chargement (OPTIONNEL)
                    .into(binding.ivBookCoverPlaceholder) // ImageView cible
            } else {
                // Si l'URL est vide ou nulle, afficher le placeholder par défaut
                binding.ivBookCoverPlaceholder.setImageResource(R.drawable.ic_book_placeholder)
            }
        }
    }

    class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem == newItem // Compare tous les champs grâce à la data class
        }
    }
}