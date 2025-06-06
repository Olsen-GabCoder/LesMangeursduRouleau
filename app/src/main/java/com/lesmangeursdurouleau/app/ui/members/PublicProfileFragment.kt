package com.lesmangeursdurouleau.app.ui.members

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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.FragmentPublicProfileBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// import java.util.TimeZone // Décommenter si utilisé

@AndroidEntryPoint
class PublicProfileFragment : Fragment() {

    private var _binding: FragmentPublicProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PublicProfileViewModel by viewModels()
    private val args: PublicProfileFragmentArgs by navArgs()

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
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarPublicProfile.visibility = View.VISIBLE
                    binding.tvPublicProfileError.visibility = View.GONE
                    binding.scrollViewPublicProfile.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBarPublicProfile.visibility = View.GONE
                    resource.data?.let { user ->
                        binding.scrollViewPublicProfile.visibility = View.VISIBLE
                        populateProfileData(user)
                        if ((activity as? AppCompatActivity)?.supportActionBar?.title != user.username && user.username.isNotBlank()) {
                            (activity as? AppCompatActivity)?.supportActionBar?.title = user.username
                        }
                    } ?: run {
                        binding.tvPublicProfileError.text = getString(R.string.error_loading_user_data)
                        binding.tvPublicProfileError.visibility = View.VISIBLE
                        binding.scrollViewPublicProfile.visibility = View.GONE
                        Log.e("PublicProfileFragment", "User data is null on success for ID: ${args.userId}")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarPublicProfile.visibility = View.GONE
                    binding.tvPublicProfileError.text = resource.message ?: getString(R.string.error_unknown)
                    binding.tvPublicProfileError.visibility = View.VISIBLE
                    binding.scrollViewPublicProfile.visibility = View.GONE
                    Log.e("PublicProfileFragment", "Error loading profile: ${resource.message}")
                }
            }
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

        binding.tvPublicProfileUsername.text = user.username.ifEmpty { getString(R.string.username_not_set) }
        binding.tvPublicProfileEmail.text = user.email.ifEmpty { getString(R.string.na) }

        user.createdAt?.let { timestamp ->
            try {
                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                binding.tvPublicProfileJoinedDate.text = sdf.format(Date(timestamp))
            } catch (e: Exception) {
                Log.e("PublicProfileFragment", "Erreur de formatage de la date createdAt: $timestamp", e)
                binding.tvPublicProfileJoinedDate.text = getString(R.string.na)
            }
        } ?: run {
            binding.tvPublicProfileJoinedDate.text = getString(R.string.na)
        }

        if (!user.bio.isNullOrBlank()) {
            binding.tvPublicProfileBioLabel.visibility = View.VISIBLE
            binding.tvPublicProfileBio.visibility = View.VISIBLE
            binding.tvPublicProfileBio.text = user.bio
        } else {
            binding.tvPublicProfileBioLabel.visibility = View.GONE
            binding.tvPublicProfileBio.visibility = View.GONE
            binding.tvPublicProfileBio.text = ""
        }

        // --- AFFICHAGE DE LA VILLE ---
        if (!user.city.isNullOrBlank()) {
            binding.tvPublicProfileCityLabel.visibility = View.VISIBLE
            binding.tvPublicProfileCity.visibility = View.VISIBLE
            binding.tvPublicProfileCity.text = user.city
        } else {
            binding.tvPublicProfileCityLabel.visibility = View.GONE
            binding.tvPublicProfileCity.visibility = View.GONE
            binding.tvPublicProfileCity.text = "" // Effacer au cas où
        }
        // --- FIN DE L'AFFICHAGE DE LA VILLE ---
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}