package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle // Import pour Lifecycle
import androidx.lifecycle.lifecycleScope // Import pour lifecycleScope
import androidx.lifecycle.repeatOnLifecycle // Import pour repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.FragmentPublicProfileBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest // Assurez-vous d'avoir cet import
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

        (activity as? AppCompatActivity)?.supportActionBar?.title = args.username.takeIf { !it.isNullOrBlank() } ?: getString(R.string.profile_title_default)

        setupObservers()
        setupFollowButton()
        setupCounterClickListeners()
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
                        if ((activity as? AppCompatActivity)?.supportActionBar?.title != user.username && user.username.isNotBlank()) {
                            (activity as? AppCompatActivity)?.supportActionBar?.title = user.username
                            Log.i(TAG, "Titre ActionBar mis à jour avec le pseudo: ${user.username}")
                        }
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

        // NOUVEL OBSERVATEUR POUR LE STATUT DE SUIVI - CORRIGÉ
        // Lancement de la collecte dans une coroutine scope liée au cycle de vie du fragment
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isFollowing.collectLatest { isFollowingResource ->
                    val currentUserId = viewModel.currentUserId.value
                    val targetUserId = args.userId

                    if (currentUserId != null && targetUserId != null && currentUserId == targetUserId) {
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
        }
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
        Log.d(TAG, "Compteurs mis à jour: Followers=${user.followersCount}, Following=${user.followingCount}")

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
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}