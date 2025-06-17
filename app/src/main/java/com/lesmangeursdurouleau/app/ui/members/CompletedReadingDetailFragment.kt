package com.lesmangeursdurouleau.app.ui.members

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.databinding.FragmentCompletedReadingDetailBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class CompletedReadingDetailFragment : Fragment() {

    private var _binding: FragmentCompletedReadingDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CompletedReadingDetailViewModel by viewModels()
    private val args: CompletedReadingDetailFragmentArgs by navArgs()

    private var commentsAdapter: CommentsAdapter? = null // Rendu nullable pour un nettoyage propre

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompletedReadingDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setupClickListeners()
    }

    // Le setup du RecyclerView est maintenant appelé depuis l'observateur
    // pour s'assurer que currentUserId est disponible.
    private fun setupRecyclerView(currentUserId: String?) {
        if (commentsAdapter == null) { // Initialise l'adaptateur une seule fois
            commentsAdapter = CommentsAdapter(
                currentUserId = currentUserId,
                targetProfileOwnerId = args.userId,
                onDeleteClickListener = { comment ->
                    viewModel.deleteComment(comment.commentId)
                },
                onLikeClickListener = { comment ->
                    viewModel.toggleLikeOnComment(comment.commentId)
                },
                getCommentLikeStatus = { commentId ->
                    viewModel.isCommentLikedByCurrentUser(commentId)
                },
                lifecycleOwner = viewLifecycleOwner
            )
            binding.rvComments.adapter = commentsAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observer l'ID de l'utilisateur connecté pour initialiser l'adaptateur
                // et gérer la visibilité des contrôles d'interaction.
                launch {
                    viewModel.currentUserId.collectLatest { uid ->
                        setupRecyclerView(uid)
                        binding.commentInputBar.isVisible = uid != null
                    }
                }

                // Observer les détails de la lecture
                launch {
                    viewModel.completedReading.collect { resource ->
                        binding.progressBarDetails.isVisible = resource is Resource.Loading
                        if (resource is Resource.Success) {
                            resource.data?.let { updateReadingDetailsUI(it) }
                        }
                    }
                }

                // Observer les commentaires
                launch {
                    viewModel.comments.collect { resource ->
                        binding.progressBarComments.isVisible = resource is Resource.Loading
                        binding.rvComments.isVisible = resource is Resource.Success
                        binding.tvNoComments.isVisible = resource is Resource.Success && resource.data.isNullOrEmpty()

                        if (resource is Resource.Success) {
                            commentsAdapter?.submitList(resource.data)
                        }
                    }
                }

                // Observer le statut de like de la lecture
                launch {
                    viewModel.isReadingLikedByCurrentUser.collect { resource ->
                        if (resource is Resource.Success) {
                            val isLiked = resource.data ?: false
                            val iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                            binding.btnLikeReading.setIconResource(iconRes)
                        }
                    }
                }

                // Observer le compteur de likes de la lecture
                launch {
                    viewModel.readingLikesCount.collect { resource ->
                        if (resource is Resource.Success) {
                            binding.btnLikeReading.text = resource.data?.toString() ?: "0"
                        }
                    }
                }
            }
        }
    }

    private fun updateReadingDetailsUI(reading: CompletedReading) {
        Glide.with(this)
            .load(reading.coverImageUrl)
            .placeholder(R.drawable.ic_book_placeholder)
            .into(binding.ivBookCover)

        binding.tvBookTitle.text = reading.title
        binding.tvBookAuthor.text = reading.author

        reading.completionDate?.let { date ->
            val dateFormat = SimpleDateFormat("'Terminé le' dd MMMM yyyy", Locale.getDefault())
            binding.tvCompletionDate.text = dateFormat.format(date)
        } ?: run {
            binding.tvCompletionDate.text = getString(R.string.date_completion_unknown)
        }
    }

    private fun setupClickListeners() {
        binding.btnLikeReading.setOnClickListener {
            viewModel.toggleLikeOnReading()
        }

        binding.btnSendComment.setOnClickListener {
            val commentText = binding.etComment.text.toString().trim()
            if (commentText.isNotEmpty()) {
                viewModel.addComment(commentText)
                binding.etComment.text?.clear()
                // Cacher le clavier
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                binding.etComment.clearFocus()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvComments.adapter = null
        commentsAdapter = null // Nettoyage de l'adaptateur
        _binding = null
    }
}