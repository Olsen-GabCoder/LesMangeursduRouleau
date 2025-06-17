package com.lesmangeursdurouleau.app.ui.members

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.databinding.FragmentCompletedReadingDetailBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class CompletedReadingDetailFragment : Fragment() {

    private var _binding: FragmentCompletedReadingDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CompletedReadingDetailViewModel by viewModels()
    private val args: CompletedReadingDetailFragmentArgs by navArgs()

    private var commentsAdapter: CommentsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NOUVEAU: Définir l'animation de transition partagée
        sharedElementEnterTransition = TransitionInflater.from(requireContext())
            .inflateTransition(android.R.transition.move)
        // Optionnel: Définir une durée
        (sharedElementEnterTransition as Transition?)?.duration = 300
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompletedReadingDetailBinding.inflate(inflater, container, false)
        // NOUVEAU: Assigner le nom de transition à la vue de destination
        ViewCompat.setTransitionName(binding.ivBookCover, "cover_${args.bookId}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // NOUVEAU: Mettre en pause la transition pour attendre le chargement de l'image
        postponeEnterTransition(250, TimeUnit.MILLISECONDS) // Timeout de sécurité

        observeViewModel()
        setupClickListeners()
    }

    // ... le reste du fragment (setupRecyclerView, setupClickListeners, etc.) ...
    private fun setupRecyclerView(currentUserId: String?) {
        if (commentsAdapter == null) {
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
                launch {
                    viewModel.currentUserId.collectLatest { uid ->
                        setupRecyclerView(uid)
                        binding.commentInputBar.isVisible = uid != null
                    }
                }

                launch {
                    viewModel.completedReading.collect { resource ->
                        binding.progressBarDetails.isVisible = resource is Resource.Loading
                        if (resource is Resource.Success) {
                            resource.data?.let { updateReadingDetailsUI(it) }
                        }
                    }
                }

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

                launch {
                    viewModel.isReadingLikedByCurrentUser.collect { resource ->
                        if (resource is Resource.Success) {
                            val isLiked = resource.data ?: false
                            val iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                            binding.btnLikeReading.setIconResource(iconRes)
                        }
                    }
                }

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
        // MODIFIÉ: Utilisation d'un listener Glide pour démarrer la transition au bon moment
        Glide.with(this)
            .load(reading.coverImageUrl)
            .placeholder(R.drawable.ic_book_placeholder)
            .dontAnimate() // Important pour que la transition soit fluide
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    // Démarrer la transition même si l'image ne se charge pas
                    startPostponedEnterTransition()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    // L'image est chargée, on peut démarrer la transition
                    startPostponedEnterTransition()
                    return false
                }
            })
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
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                binding.etComment.clearFocus()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvComments.adapter = null
        commentsAdapter = null
        _binding = null
    }
}