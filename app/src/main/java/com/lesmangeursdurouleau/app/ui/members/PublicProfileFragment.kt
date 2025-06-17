package com.lesmangeursdurouleau.app.ui.members

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.FragmentPublicProfileBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class PublicProfileFragment : Fragment() {

    private var _binding: FragmentPublicProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PublicProfileViewModel by viewModels()
    private val args: PublicProfileFragmentArgs by navArgs()

    private lateinit var commentsAdapter: CommentsAdapter

    companion object {
        private const val TAG = "PublicProfileFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublicProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateActionBarTitle(args.username)

        setupRecyclerViews()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerViews() {
        // MODIFICATION ICI : Passage du nouveau paramètre targetProfileOwnerId au CommentsAdapter
        commentsAdapter = CommentsAdapter(
            currentUserId = viewModel.currentUserId.value,
            targetProfileOwnerId = args.userId, // Le propriétaire du profil est l'userId passé en argument
            onDeleteClickListener = { commentToDelete ->
                Log.d(TAG, "Clic sur bouton de suppression pour le commentaire ID: ${commentToDelete.commentId}")
                showDeleteConfirmationDialog(commentToDelete)
            },
            onLikeClickListener = { commentToLike ->
                Log.d(TAG, "Clic sur bouton de like pour le commentaire ID: ${commentToLike.commentId}")
                viewModel.toggleLikeOnComment(commentToLike)
            },
            getCommentLikeStatus = { commentId ->
                viewModel.getCommentLikeStatus(commentId)
            },
            lifecycleOwner = viewLifecycleOwner
        )
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentsAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(false)
        }
    }

    private fun setupClickListeners() {
        setupFollowButton()
        setupCounterClickListeners()
        setupCurrentReadingButton()
        setupCommentInputListeners()
        setupLikeButton()
        setupBooksReadClickListener()
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarPublicProfile.visibility = View.VISIBLE
                    binding.tvPublicProfileError.visibility = View.GONE
                    binding.scrollViewPublicProfile.visibility = View.GONE
                    Log.d(TAG, "Chargement du profil public pour ID: ${args.userId}")
                }
                is Resource.Success -> {
                    binding.progressBarPublicProfile.visibility = View.GONE
                    resource.data?.let { user ->
                        binding.scrollViewPublicProfile.visibility = View.VISIBLE
                        populateProfileData(user)
                        updateActionBarTitle(user.username)
                    } ?: run {
                        binding.tvPublicProfileError.text = getString(R.string.error_loading_user_data)
                        binding.tvPublicProfileError.visibility = View.VISIBLE
                        binding.scrollViewPublicProfile.visibility = View.GONE
                        Log.e(TAG, "User data is null on success for ID: ${args.userId}")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarPublicProfile.visibility = View.GONE
                    binding.tvPublicProfileError.text = resource.message ?: getString(R.string.error_unknown)
                    binding.tvPublicProfileError.visibility = View.VISIBLE
                    binding.scrollViewPublicProfile.visibility = View.GONE
                    Log.e(TAG, "Erreur lors du chargement du profil public pour ID: ${args.userId}: ${resource.message}")
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observer le statut de suivi
                launch {
                    viewModel.isFollowing.collectLatest { isFollowingResource ->
                        val currentUserId = viewModel.currentUserId.value
                        val targetUserId = args.userId

                        if (currentUserId != null && currentUserId == targetUserId) {
                            binding.btnToggleFollow.visibility = View.GONE
                            Log.d(TAG, "Bouton de suivi masqué car c'est le propre profil de l'utilisateur.")
                            return@collectLatest
                        }

                        when (isFollowingResource) {
                            is Resource.Loading -> {
                                binding.btnToggleFollow.text = getString(R.string.loading_follow_status)
                                binding.btnToggleFollow.isEnabled = false
                                binding.btnToggleFollow.visibility = View.VISIBLE
                                Log.d(TAG, "Chargement du statut de suivi...")
                            }
                            is Resource.Success -> {
                                binding.btnToggleFollow.isEnabled = true
                                binding.btnToggleFollow.visibility = View.VISIBLE
                                if (isFollowingResource.data == true) {
                                    binding.btnToggleFollow.text = getString(R.string.unfollow)
                                    binding.btnToggleFollow.setTextColor(requireContext().getColor(R.color.error_color))
                                    binding.btnToggleFollow.strokeColor = requireContext().getColorStateList(R.color.error_color)
                                    Log.d(TAG, "Bouton affiché: Désabonner")
                                } else {
                                    binding.btnToggleFollow.text = getString(R.string.follow)
                                    binding.btnToggleFollow.setTextColor(requireContext().getColor(R.color.primary_accent))
                                    binding.btnToggleFollow.strokeColor = requireContext().getColorStateList(R.color.primary_accent)
                                    Log.d(TAG, "Bouton affiché: Suivre")
                                }
                            }
                            is Resource.Error -> {
                                binding.btnToggleFollow.text = getString(R.string.follow_error)
                                binding.btnToggleFollow.isEnabled = false
                                binding.btnToggleFollow.visibility = View.VISIBLE
                                Toast.makeText(context, isFollowingResource.message, Toast.LENGTH_LONG).show()
                                Log.e(TAG, "Erreur de statut de suivi: ${isFollowingResource.message}")
                            }
                        }
                    }
                }

                // Observer le statut de suivi réciproque
                launch {
                    viewModel.isMutualFollow.collectLatest { mutualFollowResource ->
                        val currentUserId = viewModel.currentUserId.value
                        val targetUserId = args.userId

                        Log.d(TAG, "Observateur isMutualFollow: Reçu -> $mutualFollowResource")

                        if (currentUserId != null && currentUserId == targetUserId) {
                            binding.cardMutualFollowBadge.visibility = View.GONE
                            Log.d(TAG, "Badge de suivi mutuel masqué car c'est le propre profil de l'utilisateur.")
                            return@collectLatest
                        }

                        when (mutualFollowResource) {
                            is Resource.Loading -> {
                                binding.cardMutualFollowBadge.visibility = View.GONE
                                Log.d(TAG, "Chargement du statut de suivi mutuel. Badge masqué temporairement.")
                            }
                            is Resource.Success -> {
                                if (mutualFollowResource.data == true) {
                                    binding.cardMutualFollowBadge.visibility = View.VISIBLE
                                    Log.d(TAG, "Suivi mutuel détecté. Badge affiché. Visibilité: VISIBLE")
                                } else {
                                    binding.cardMutualFollowBadge.visibility = View.GONE
                                    Log.d(TAG, "Pas de suivi mutuel. Badge masqué. Visibilité: GONE")
                                }
                            }
                            is Resource.Error -> {
                                binding.cardMutualFollowBadge.visibility = View.GONE
                                Log.e(TAG, "Erreur lors de la détermination du suivi mutuel: ${mutualFollowResource.message}. Badge masqué.")
                            }
                        }
                    }
                }

                // Observer la lecture en cours (avec visibilité de la section like)
                launch {
                    viewModel.currentReadingUiState.collectLatest { uiState ->
                        binding.btnEditCurrentReading.visibility = View.GONE

                        when {
                            uiState.isLoading -> {
                                binding.cardCurrentReading.visibility = View.GONE
                                binding.cardCommentsSection.visibility = View.GONE
                                binding.llLikeSection.visibility = View.GONE
                                Log.d(TAG, "currentReadingUiState (Public): Chargement en cours.")
                            }
                            uiState.error != null -> {
                                binding.cardCurrentReading.visibility = View.GONE
                                binding.cardCommentsSection.visibility = View.GONE
                                binding.llLikeSection.visibility = View.GONE
                                Toast.makeText(context, uiState.error, Toast.LENGTH_LONG).show()
                                Log.e(TAG, "currentReadingUiState (Public): Erreur: ${uiState.error}")
                            }
                            uiState.bookReading == null || uiState.bookDetails == null -> {
                                binding.cardCurrentReading.visibility = View.GONE
                                binding.cardCommentsSection.visibility = View.GONE
                                binding.llLikeSection.visibility = View.GONE
                                Log.d(TAG, "currentReadingUiState (Public): Aucune lecture en cours ou détails du livre manquants. Carte masquée.")
                            }
                            else -> {
                                binding.cardCurrentReading.visibility = View.VISIBLE
                                binding.cardCommentsSection.visibility = View.VISIBLE
                                binding.llLikeSection.visibility = View.VISIBLE
                                Log.d(TAG, "currentReadingUiState (Public): Affichage de la lecture en cours.")

                                val reading = uiState.bookReading
                                val book = uiState.bookDetails

                                Glide.with(this@PublicProfileFragment)
                                    .load(book.coverImageUrl)
                                    .placeholder(R.drawable.ic_book_placeholder)
                                    .error(R.drawable.ic_book_placeholder)
                                    .transition(DrawableTransitionOptions.withCrossFade())
                                    .into(binding.ivCurrentReadingBookCover)

                                binding.tvCurrentReadingBookTitle.text = book.title
                                binding.tvCurrentReadingBookAuthor.text = book.author

                                val currentPage = reading.currentPage
                                val totalPages = reading.totalPages
                                if (totalPages > 0) {
                                    binding.tvCurrentReadingProgressText.text = getString(R.string.page_progress_format, currentPage, totalPages)
                                    val progressPercentage = (currentPage.toFloat() / totalPages.toFloat() * 100).toInt()
                                    binding.progressBarCurrentReading.progress = progressPercentage
                                } else {
                                    binding.tvCurrentReadingProgressText.text = getString(R.string.page_progress_unknown)
                                    binding.progressBarCurrentReading.progress = 0
                                }

                                val personalNote = reading.favoriteQuote?.takeIf { it.isNotBlank() }
                                    ?: reading.personalReflection?.takeIf { it.isNotBlank() }

                                if (!personalNote.isNullOrBlank()) {
                                    binding.llPersonalReflectionSection.visibility = View.VISIBLE
                                    binding.tvCurrentReadingPersonalNote.text = personalNote
                                } else {
                                    binding.llPersonalReflectionSection.visibility = View.GONE
                                    binding.tvCurrentReadingPersonalNote.text = ""
                                }

                                binding.btnEditCurrentReading.visibility = if (uiState.isOwnedProfile) View.VISIBLE else View.GONE
                            }
                        }
                    }
                }

                // Observer les commentaires
                launch {
                    viewModel.comments.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                Log.d(TAG, "Chargement des commentaires...")
                            }
                            is Resource.Success -> {
                                val commentsList = resource.data ?: emptyList()
                                commentsAdapter.submitList(commentsList)
                                if (commentsList.isEmpty()) {
                                    binding.rvComments.visibility = View.GONE
                                    binding.llCommentInputSection.visibility = View.VISIBLE
                                    binding.tvNoCommentsYet.visibility = View.VISIBLE
                                } else {
                                    binding.rvComments.visibility = View.VISIBLE
                                    binding.llCommentInputSection.visibility = View.VISIBLE
                                    binding.tvNoCommentsYet.visibility = View.GONE
                                }
                                Log.d(TAG, "${commentsList.size} commentaires chargés.")
                            }
                            is Resource.Error -> {
                                commentsAdapter.submitList(emptyList())
                                binding.rvComments.visibility = View.GONE
                                binding.llCommentInputSection.visibility = View.GONE
                                binding.tvNoCommentsYet.visibility = View.VISIBLE
                                binding.tvNoCommentsYet.text = getString(R.string.error_loading_comments, resource.message ?: getString(R.string.error_unknown))
                                Log.e(TAG, "Erreur lors du chargement des commentaires: ${resource.message}")
                                Toast.makeText(context, getString(R.string.error_loading_comments, resource.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                // Observer les événements de commentaire
                launch {
                    viewModel.commentEvents.collectLatest { event ->
                        when (event) {
                            is CommentEvent.ShowCommentError -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "Événement CommentError: ${event.message}")
                            }
                            is CommentEvent.ClearCommentInput -> {
                                binding.etCommentInput.setText("")
                                Log.d(TAG, "Événement ClearCommentInput: Champ de saisie effacé.")
                            }
                            is CommentEvent.CommentDeletedSuccess -> {
                                Toast.makeText(context, getString(R.string.comment_deleted_success), Toast.LENGTH_SHORT).show()
                                Log.i(TAG, "Commentaire ${event.commentId} supprimé avec succès.")
                            }
                            is CommentEvent.ShowCommentLikeError -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "Événement CommentLikeError: ${event.message}")
                            }
                        }
                    }
                }

                // Observateur : Pour le statut de like de l'utilisateur courant sur la LECTURE
                launch {
                    viewModel.isLikedByCurrentUser.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.btnToggleLike.isEnabled = false
                                Log.d(TAG, "Chargement du statut de like de l'utilisateur courant (lecture)...")
                            }
                            is Resource.Success -> {
                                binding.btnToggleLike.isEnabled = true
                                val isLiked = resource.data ?: false
                                val iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline

                                binding.btnToggleLike.setIconResource(iconRes)

                                val resolvedIconColor: Int
                                if (isLiked) {
                                    resolvedIconColor = ContextCompat.getColor(requireContext(), R.color.red_love)
                                } else {
                                    resolvedIconColor = MaterialColors.getColor(binding.btnToggleLike, com.google.android.material.R.attr.colorOnSurfaceVariant)
                                }
                                binding.btnToggleLike.iconTint = ColorStateList.valueOf(resolvedIconColor)

                                Log.d(TAG, "Statut de like de l'utilisateur courant (lecture): $isLiked. Icône mise à jour.")
                            }
                            is Resource.Error -> {
                                binding.btnToggleLike.isEnabled = false
                                binding.btnToggleLike.setIconResource(R.drawable.ic_heart_outline)
                                val defaultColor = MaterialColors.getColor(binding.btnToggleLike, com.google.android.material.R.attr.colorOnSurfaceVariant)
                                binding.btnToggleLike.iconTint = ColorStateList.valueOf(defaultColor)
                                Log.e(TAG, "Erreur lors du chargement du statut de like de l'utilisateur courant (lecture): ${resource.message}")
                            }
                        }
                    }
                }

                // Observateur : Pour le nombre de likes sur la LECTURE
                launch {
                    viewModel.likesCount.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.btnToggleLike.text = getString(R.string.loading_likes_count)
                                Log.d(TAG, "Chargement du nombre de likes (lecture)...")
                            }
                            is Resource.Success -> {
                                val count = resource.data ?: 0
                                binding.btnToggleLike.text = count.toString()
                                Log.d(TAG, "Nombre de likes (lecture) mis à jour: $count.")
                            }
                            is Resource.Error -> {
                                binding.btnToggleLike.text = getString(R.string.error_loading_likes_count)
                                Log.e(TAG, "Erreur lors du chargement du nombre de likes (lecture): ${resource.message}")
                            }
                        }
                    }
                }

                // Observateur : Pour les événements de like sur la LECTURE
                launch {
                    viewModel.likeEvents.collectLatest { event ->
                        when (event) {
                            is LikeEvent.ShowLikeError -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "Événement LikeError (lecture): ${event.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(comment: Comment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_comment_dialog_title))
            .setMessage(getString(R.string.delete_comment_dialog_message))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                Log.d(TAG, "Suppression du commentaire annulée par l'utilisateur.")
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                viewModel.deleteComment(comment.commentId, comment.userId)
                dialog.dismiss()
                Log.d(TAG, "Suppression du commentaire confirmée par l'utilisateur pour l'ID: ${comment.commentId}.")
            }
            .show()
    }

    private fun setupFollowButton() {
        binding.btnToggleFollow.setOnClickListener {
            Log.d(TAG, "Bouton de suivi cliqué.")
            viewModel.toggleFollowStatus()
        }
    }

    private fun setupCounterClickListeners() {
        binding.llFollowersClickableArea.setOnClickListener {
            val targetUserId = args.userId
            val targetUsername = args.username ?: getString(R.string.profile_title_default)
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowers(
                userId = targetUserId,
                listType = "followers",
                listTitle = getString(R.string.title_followers_of, targetUsername)
            )
            findNavController().navigate(action)
            Log.d(TAG, "Clic sur 'Followers'. Navigation vers la liste des followers pour User ID: $targetUserId")
        }

        binding.llFollowingClickableArea.setOnClickListener {
            val targetUserId = args.userId
            val targetUsername = args.username ?: getString(R.string.profile_title_default)
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowing(
                userId = targetUserId,
                listType = "following",
                listTitle = getString(R.string.title_following_by, targetUsername)
            )
            findNavController().navigate(action)
            Log.d(TAG, "Clic sur 'Following'. Navigation vers la liste des abonnements pour User ID: $targetUserId")
        }
    }

    private fun setupCurrentReadingButton() {
        binding.btnEditCurrentReading.setOnClickListener {
            val uiState = viewModel.currentReadingUiState.value
            if (uiState.isOwnedProfile) {
                Log.d(TAG, "Bouton 'Mettre à jour la lecture' cliqué. Navigation vers l'écran d'édition.")
                val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToEditCurrentReadingFragment()
                findNavController().navigate(action)
            } else {
                Toast.makeText(context, "Vous ne pouvez modifier que votre propre lecture.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Tentative de modification de lecture sur un profil public qui n'est pas le sien.")
            }
        }
    }

    private fun setupCommentInputListeners() {
        binding.btnSendComment.setOnClickListener {
            val commentText = binding.etCommentInput.text.toString().trim()
            viewModel.postComment(commentText)
            Log.d(TAG, "Bouton d'envoi de commentaire cliqué. Texte: '$commentText'")
        }
    }

    private fun setupLikeButton() {
        binding.btnToggleLike.setOnClickListener {
            val currentUserId = viewModel.currentUserId.value
            val targetUserId = args.userId

            // MODIFICATION ICI: Suppression de la restriction côté client pour permettre de liker sa propre lecture.
            // La validation des permissions est désormais entièrement gérée par le ViewModel et les règles Firestore.
            if (currentUserId.isNullOrBlank() || targetUserId.isNullOrBlank()) {
                val message = "Vous devez être connecté pour liker."
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Tentative de like non autorisée: utilisateur non connecté ou IDs manquants. Message: $message")
                return@setOnClickListener
            }

            Log.d(TAG, "Bouton 'J'aime' (lecture) cliqué. Bascule du statut de like.")
            viewModel.toggleLike()
        }
    }

    // Implémentation de la navigation vers CompletedReadingsFragment
    private fun setupBooksReadClickListener() {
        binding.llBooksReadClickableArea.setOnClickListener {
            val targetUserId = args.userId
            val targetUsername = args.username ?: getString(R.string.profile_title_default)

            // Création de l'action de navigation avec les arguments nécessaires
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToCompletedReadingsFragment(
                userId = targetUserId,
                username = targetUsername
            )
            // Exécution de la navigation
            findNavController().navigate(action)
            Log.d(TAG, "Clic sur 'Livres lus'. Navigation vers la liste des lectures terminées pour User ID: $targetUserId, Username: $targetUsername")
        }
    }

    private fun updateActionBarTitle(username: String?) {
        val title = username?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_title_default)
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
        Log.i(TAG, "Titre ActionBar mis à jour avec: $title")
    }

    private fun populateProfileData(user: User) {
        Glide.with(this)
            .load(user.profilePictureUrl)
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder)
            .transition(DrawableTransitionOptions.withCrossFade())
            .circleCrop()
            .into(binding.ivPublicProfilePicture)
        Log.d(TAG, "Image de profil chargée pour ${user.username}. URL: ${user.profilePictureUrl}")

        binding.tvPublicProfileUsername.text = user.username.ifEmpty { getString(R.string.username_not_set) }
        Log.d(TAG, "Pseudo: ${binding.tvPublicProfileUsername.text}")

        binding.tvFollowersCount.text = user.followersCount.toString()
        binding.tvFollowingCount.text = user.followingCount.toString()
        binding.tvBooksReadCount.text = user.booksReadCount.toString()
        Log.d(TAG, "Compteurs mis à jour: Followers=${user.followersCount}, Following=${user.followingCount}, BooksRead=${user.booksReadCount}")

        binding.tvPublicProfileEmail.text = user.email.ifEmpty { getString(R.string.na) }
        Log.d(TAG, "Email: ${binding.tvPublicProfileEmail.text}")

        user.createdAt?.let { timestamp ->
            try {
                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                binding.tvPublicProfileJoinedDate.text = sdf.format(Date(timestamp))
                Log.d(TAG, "Date d'inscription: ${binding.tvPublicProfileJoinedDate.text}")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur de formatage de la date createdAt: $timestamp", e)
                binding.tvPublicProfileJoinedDate.text = getString(R.string.na)
            }
        } ?: run {
            binding.tvPublicProfileJoinedDate.text = getString(R.string.na)
            Log.d(TAG, "Date d'inscription: N/A (createdAt est null)")
        }

        if (!user.bio.isNullOrBlank()) {
            binding.cardPublicProfileBio.visibility = View.VISIBLE
            binding.tvPublicProfileBio.text = user.bio
            Log.d(TAG, "Bio affichée: ${user.bio}")
        } else {
            binding.cardPublicProfileBio.visibility = View.GONE
            binding.tvPublicProfileBio.text = ""
            Log.d(TAG, "Bio non disponible ou vide. Carte masquée.")
        }

        if (!user.city.isNullOrBlank()) {
            binding.cardPublicProfileCity.visibility = View.VISIBLE
            binding.tvPublicProfileCity.text = user.city
            Log.d(TAG, "Ville affichée: ${user.city}")
        } else {
            binding.cardPublicProfileCity.visibility = View.GONE
            binding.tvPublicProfileCity.text = ""
            Log.d(TAG, "Ville non disponible ou vide. Carte masquée.")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvComments.adapter = null
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}