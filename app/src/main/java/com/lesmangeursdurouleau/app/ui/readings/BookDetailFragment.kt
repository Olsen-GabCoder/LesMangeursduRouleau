package com.lesmangeursdurouleau.app.ui.readings.detail

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.FragmentBookDetailBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

@AndroidEntryPoint // ANNOTATION AJOUTÉE
class BookDetailFragment : Fragment() {

    private var _binding: FragmentBookDetailBinding? = null
    private val binding get() = _binding!!

    private val args: BookDetailFragmentArgs by navArgs()
    private val viewModel: BookDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bookId = args.bookId
        Log.d("BookDetailFragment", "Received Book ID for detail: $bookId")

        if (bookId.isNotBlank()) {
            viewModel.loadBookDetails(bookId)
        } else {
            Log.e("BookDetailFragment", "Book ID is blank, cannot load details.")
            binding.tvBookDetailSynopsis.text = "Erreur : ID du livre manquant."
            binding.progressBarBookDetail.visibility = View.GONE
        }
        setupObservers()
    }

    private fun setupObservers() {
        viewModel.bookDetails.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarBookDetail.visibility = View.VISIBLE
                    binding.ivBookDetailCover.visibility = View.INVISIBLE
                    binding.tvBookDetailTitle.visibility = View.INVISIBLE
                }
                is Resource.Success -> {
                    binding.progressBarBookDetail.visibility = View.GONE
                    resource.data?.let { book ->
                        populateBookDetails(book)
                        binding.ivBookDetailCover.visibility = View.VISIBLE
                        binding.tvBookDetailTitle.visibility = View.VISIBLE
                    } ?: run {
                        binding.tvBookDetailSynopsis.text = "Livre non trouvé ou données invalides."
                        Log.e("BookDetailFragment", "Book data is null on success for ID: ${args.bookId}")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarBookDetail.visibility = View.GONE
                    binding.tvBookDetailSynopsis.text = resource.message ?: getString(R.string.error_loading_book_details)
                    Log.e("BookDetailFragment", "Error loading book details: ${resource.message}")
                }
            }
        }
    }

    private fun populateBookDetails(book: Book) {
        binding.tvBookDetailTitle.text = book.title
        binding.tvBookDetailAuthor.text = book.author
        binding.tvBookDetailSynopsis.text = book.synopsis ?: getString(R.string.no_synopsis_available)

        if (!book.coverImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(book.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder_error)
                .into(binding.ivBookDetailCover)
        } else {
            binding.ivBookDetailCover.setImageResource(R.drawable.ic_book_placeholder)
        }

        if (activity is AppCompatActivity) {
            (activity as AppCompatActivity).supportActionBar?.title = book.title
        } else {
            (activity as? AppCompatActivity)?.supportActionBar?.title = args.bookTitle ?: "Détails"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}