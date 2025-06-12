package com.lesmangeursdurouleau.app.ui.readings.selection

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf // NOUVEL IMPORT NÉCESSAIRE
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult // NOUVEL IMPORT NÉCESSAIRE
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.FragmentBookSelectionBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BookSelectionFragment : Fragment() {

    private var _binding: FragmentBookSelectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BookSelectionViewModel by viewModels()
    private lateinit var bookSelectionAdapter: BookSelectionAdapter

    companion object {
        private const val TAG = "BookSelectionFragment"
        // Clés pour le Fragment Result API
        const val KEY_REQUEST_BOOK_SELECTION = "book_selection_request_key"
        const val KEY_SELECTED_BOOK = "selected_book"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Fragment de sélection de livre créé.")

        setupToolbar()
        setupRecyclerView()
        setupSearchView()
        observeBooksList()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        bookSelectionAdapter = BookSelectionAdapter()
        binding.recyclerViewBooks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = bookSelectionAdapter
        }

        bookSelectionAdapter.onItemClick = { book ->
            Log.d(TAG, "Livre sélectionné: ${book.title} (ID: ${book.id})")
            // 1. Définir le résultat à renvoyer au fragment appelant
            setFragmentResult(
                KEY_REQUEST_BOOK_SELECTION,
                bundleOf(KEY_SELECTED_BOOK to book)
            )
            // 2. Naviguer en arrière vers le fragment appelant
            findNavController().navigateUp()
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.searchBooks(query ?: "")
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.searchBooks(newText ?: "")
                return true
            }
        })
    }

    private fun observeBooksList() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.booksList.collect { resource ->
                    Log.d(TAG, "Nouvel état de booksList: $resource")
                    when (resource) {
                        is Resource.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.recyclerViewBooks.visibility = View.GONE
                            binding.textViewError.visibility = View.GONE
                            binding.textViewNoResults.visibility = View.GONE
                        }
                        is Resource.Success -> {
                            binding.progressBar.visibility = View.GONE
                            val books = resource.data ?: emptyList()
                            bookSelectionAdapter.submitList(books) {
                                if (books.isEmpty() && !binding.searchView.query.isNullOrBlank()) {
                                    binding.textViewNoResults.visibility = View.VISIBLE
                                    binding.recyclerViewBooks.visibility = View.GONE
                                } else {
                                    binding.textViewNoResults.visibility = View.GONE
                                    binding.recyclerViewBooks.visibility = View.VISIBLE
                                }
                            }
                            binding.textViewError.visibility = View.GONE
                        }
                        is Resource.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.recyclerViewBooks.visibility = View.GONE
                            binding.textViewNoResults.visibility = View.GONE
                            binding.textViewError.visibility = View.VISIBLE
                            binding.textViewError.text = resource.message ?: "Une erreur inconnue est survenue."
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}