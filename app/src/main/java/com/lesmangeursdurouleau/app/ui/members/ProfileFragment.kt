package com.lesmangeursdurouleau.app.ui.members

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.snackbar.Snackbar
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentProfileBinding
import com.lesmangeursdurouleau.app.ui.auth.AuthActivity
import com.lesmangeursdurouleau.app.ui.auth.AuthViewModel
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val profileViewModel: ProfileViewModel by viewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .circleCrop()
                    .into(binding.ivProfilePicture)

                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val imageData = inputStream?.readBytes()
                inputStream?.close()
                imageData?.let { data ->
                    authViewModel.currentUser.value?.uid?.let { userId ->
                        binding.fabSelectPicture.isEnabled = false // CORRIGÉ
                        authViewModel.updateProfilePicture(userId, data)
                    } ?: Snackbar.make(binding.root, getString(R.string.user_not_connected_error), Snackbar.LENGTH_SHORT).show()
                } ?: Snackbar.make(binding.root, getString(R.string.error_reading_image), Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, getString(R.string.error_generic_with_message, e.localizedMessage), Snackbar.LENGTH_LONG).show()
                binding.fabSelectPicture.isEnabled = true // CORRIGÉ
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        profileViewModel.email.observe(viewLifecycleOwner) { email ->
            binding.tvProfileEmail.text = email ?: getString(R.string.email_not_available)
        }

        profileViewModel.displayName.observe(viewLifecycleOwner) { displayName ->
            binding.etProfileUsername.setText(displayName ?: getString(R.string.username_not_defined))
        }

        profileViewModel.bio.observe(viewLifecycleOwner) { bio ->
            binding.etProfileBio.setText(bio ?: "")
        }

        // --- AJOUT DE L'OBSERVATEUR POUR LA VILLE ---
        profileViewModel.city.observe(viewLifecycleOwner) { city ->
            binding.etProfileCity.setText(city ?: "") // Afficher une chaîne vide si la ville est null
        }
        // --- FIN DE L'AJOUT ---


        profileViewModel.profilePictureUrl.observe(viewLifecycleOwner) { photoUrl ->
            Log.d("ProfileFragment", "Observateur profilePictureUrl Reçu: '$photoUrl'")
            Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e("ProfileFragment", "Glide onLoadFailed pour URL: $model", e); return false
                    }
                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.d("ProfileFragment", "Glide onResourceReady pour URL: $model"); return false
                    }
                })
                .circleCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivProfilePicture)
        }

        profileViewModel.userProfileData.observe(viewLifecycleOwner) { resource ->
            val isLoadingProfile = resource is Resource.Loading
            val isUpdatingUsername = profileViewModel.usernameUpdateResult.value is Resource.Loading
            val isUpdatingBio = profileViewModel.bioUpdateResult.value is Resource.Loading
            val isUpdatingCity = profileViewModel.cityUpdateResult.value is Resource.Loading // Vérifier aussi le chargement de la ville
            binding.buttonSaveProfile.isEnabled = !isLoadingProfile && !isUpdatingUsername && !isUpdatingBio && !isUpdatingCity

            binding.fabSelectPicture.isEnabled = !isLoadingProfile && (authViewModel.profilePictureUpdateResult.value !is Resource.Loading) // CORRIGÉ

            when (resource) {
                is Resource.Loading -> Log.d("ProfileFragment", "Chargement des données du profil...")
                is Resource.Success -> Log.d("ProfileFragment", "Données du profil chargées/mises à jour.")
                is Resource.Error -> {
                    Log.e("ProfileFragment", "Erreur de chargement du profil: ${resource.message}")
                    Snackbar.make(binding.root, resource.message ?: getString(R.string.error_loading_profile), Snackbar.LENGTH_LONG).show()
                }
            }
        }

        profileViewModel.usernameUpdateResult.observe(viewLifecycleOwner) { result ->
            val isLoadingProfile = profileViewModel.userProfileData.value is Resource.Loading
            val isUpdatingBio = profileViewModel.bioUpdateResult.value is Resource.Loading
            val isUpdatingCity = profileViewModel.cityUpdateResult.value is Resource.Loading
            binding.buttonSaveProfile.isEnabled = !isLoadingProfile && (result !is Resource.Loading) && !isUpdatingBio && !isUpdatingCity

            when (result) {
                is Resource.Success -> Snackbar.make(binding.root, getString(R.string.username_updated_success), Snackbar.LENGTH_SHORT).show()
                is Resource.Error -> Snackbar.make(binding.root, result.message ?: getString(R.string.error_updating_username), Snackbar.LENGTH_LONG).show()
                is Resource.Loading -> Log.d("ProfileFragment", "Mise à jour du pseudo en cours...")
                null -> {
                    if (!isLoadingProfile && !isUpdatingBio && !isUpdatingCity) {
                        binding.buttonSaveProfile.isEnabled = true
                    }
                }
            }
        }

        profileViewModel.bioUpdateResult.observe(viewLifecycleOwner) { result ->
            val isLoadingProfile = profileViewModel.userProfileData.value is Resource.Loading
            val isUpdatingUsername = profileViewModel.usernameUpdateResult.value is Resource.Loading
            val isUpdatingCity = profileViewModel.cityUpdateResult.value is Resource.Loading
            binding.buttonSaveProfile.isEnabled = !isLoadingProfile && !isUpdatingUsername && (result !is Resource.Loading) && !isUpdatingCity

            when (result) {
                is Resource.Success -> Snackbar.make(binding.root, getString(R.string.bio_updated_success), Snackbar.LENGTH_SHORT).show()
                is Resource.Error -> Snackbar.make(binding.root, result.message ?: getString(R.string.error_updating_bio), Snackbar.LENGTH_LONG).show()
                is Resource.Loading -> Log.d("ProfileFragment", "Mise à jour de la biographie en cours...")
                null -> {
                    if (!isLoadingProfile && !isUpdatingUsername && !isUpdatingCity) {
                        binding.buttonSaveProfile.isEnabled = true
                    }
                }
            }
        }

        // --- AJOUT DE L'OBSERVATEUR POUR LE RÉSULTAT DE LA MISE À JOUR DE LA VILLE ---
        profileViewModel.cityUpdateResult.observe(viewLifecycleOwner) { result ->
            val isLoadingProfile = profileViewModel.userProfileData.value is Resource.Loading
            val isUpdatingUsername = profileViewModel.usernameUpdateResult.value is Resource.Loading
            val isUpdatingBio = profileViewModel.bioUpdateResult.value is Resource.Loading
            binding.buttonSaveProfile.isEnabled = !isLoadingProfile && !isUpdatingUsername && !isUpdatingBio && (result !is Resource.Loading)

            when (result) {
                is Resource.Success -> {
                    Snackbar.make(binding.root, getString(R.string.city_updated_success), Snackbar.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, result.message ?: getString(R.string.error_updating_city), Snackbar.LENGTH_LONG).show()
                }
                is Resource.Loading -> Log.d("ProfileFragment", "Mise à jour de la ville en cours...")
                null -> {
                    if (!isLoadingProfile && !isUpdatingUsername && !isUpdatingBio) {
                        binding.buttonSaveProfile.isEnabled = true
                    }
                }
            }
        }
        // --- FIN DE L'AJOUT ---

        authViewModel.profilePictureUpdateResult.observe(viewLifecycleOwner) { result ->
            binding.fabSelectPicture.isEnabled = result !is Resource.Loading // CORRIGÉ
            when (result) {
                is Resource.Success -> {
                    val newImageUrl = result.data
                    Snackbar.make(binding.root, getString(R.string.profile_picture_updated_success), Snackbar.LENGTH_SHORT).show()
                    if (!newImageUrl.isNullOrBlank()) {
                        Log.d("ProfileFragment", "Mise à jour photo réussie (AuthViewModel). Nouvelle URL: '$newImageUrl'. Appel de profileViewModel.setCurrentProfilePictureUrl().")
                        profileViewModel.setCurrentProfilePictureUrl(newImageUrl)
                    } else {
                        Log.w("ProfileFragment", "Mise à jour photo réussie (AuthViewModel) mais URL retournée est vide/null. Rechargement via profileViewModel.loadCurrentUserProfile().")
                        profileViewModel.loadCurrentUserProfile()
                    }
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, result.message ?: getString(R.string.error_updating_profile_picture), Snackbar.LENGTH_LONG).show()
                    profileViewModel.loadCurrentUserProfile()
                }
                is Resource.Loading -> { /* Bouton géré */ }
                null -> {
                    if (profileViewModel.userProfileData.value !is Resource.Loading) {
                        binding.fabSelectPicture.isEnabled = true // CORRIGÉ
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonLogout.setOnClickListener {
            authViewModel.logoutUser()
            navigateToAuthActivity()
        }

        binding.fabSelectPicture.setOnClickListener { // CORRIGÉ
            if (profileViewModel.firebaseAuth.currentUser != null) {
                pickImageLauncher.launch("image/*")
            } else {
                Snackbar.make(binding.root, getString(R.string.user_not_connected_error_for_action, getString(R.string.select_picture)), Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.buttonSaveProfile.setOnClickListener {
            val newUsername = binding.etProfileUsername.text.toString().trim()
            val newBio = binding.etProfileBio.text.toString().trim()
            // --- RÉCUPÉRATION DE LA VILLE ---
            val newCity = binding.etProfileCity.text.toString().trim()
            // --- FIN RÉCUPÉRATION ---

            binding.tilProfileUsername.error = null
            // Pas de validation pour la bio ou la ville pour l'instant, elles peuvent être vides.

            var canProceedWithUsername = true
            if (newUsername.isBlank()) {
                binding.tilProfileUsername.error = getString(R.string.username_cannot_be_empty)
                canProceedWithUsername = false
            }

            if (canProceedWithUsername) { // On met à jour le pseudo seulement s'il est valide
                Log.d("ProfileFragment", "Clic sur Enregistrer. Pseudo: '$newUsername', Bio: '$newBio', Ville: '$newCity'.")
                profileViewModel.updateUsername(newUsername)
            }
            // La bio et la ville sont mises à jour indépendamment de la validité du pseudo,
            // car elles peuvent être des chaînes vides.
            profileViewModel.updateBio(newBio)
            profileViewModel.updateCity(newCity)
        }

        binding.buttonViewMembers.setOnClickListener {
            val action = ProfileFragmentDirections.actionProfileFragmentToMembersFragment()
            findNavController().navigate(action)
        }

        binding.buttonGeneralChat.setOnClickListener {
            val action = ProfileFragmentDirections.actionProfileFragmentToChatFragment()
            findNavController().navigate(action)
        }
    }

    private fun navigateToAuthActivity() {
        val intent = Intent(requireActivity(), AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finishAffinity()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        profileViewModel.clearUsernameUpdateResult()
        profileViewModel.clearBioUpdateResult()
        profileViewModel.clearCityUpdateResult() // AJOUT : Consommer le résultat de la ville
        _binding = null
    }
}