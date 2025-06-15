package com.lesmangeursdurouleau.app.ui.members

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val profileViewModel: ProfileViewModel by viewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

    // Définir un TAG propre à ce Fragment pour les logs
    companion object {
        private const val TAG = "ProfileFragment"
    }

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

                val imageData: ByteArray? = requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                }

                imageData?.let { data ->
                    authViewModel.currentUser.value?.uid?.let { userId ->
                        binding.fabSelectPicture.isEnabled = false
                        authViewModel.updateProfilePicture(userId, data)
                    } ?: Snackbar.make(binding.root, getString(R.string.user_not_connected_error), Snackbar.LENGTH_SHORT).show()
                } ?: Snackbar.make(binding.root, getString(R.string.error_reading_image), Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, getString(R.string.error_generic_with_message, e.localizedMessage), Snackbar.LENGTH_LONG).show()
                binding.fabSelectPicture.isEnabled = true
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
        setupManageCurrentReadingButton() // Initialise le listener pour le bouton de gestion de la lecture
    }

    private fun setupObservers() {
        // MODIFICATION : Les observateurs individuels pour email, displayName, bio, city, profilePictureUrl sont supprimés.
        // Toutes ces informations sont maintenant observées via userProfileData.
        profileViewModel.userProfileData.observe(viewLifecycleOwner) { resource ->
            val isLoadingProfile = resource is Resource.Loading
            val isUpdatingUsername = profileViewModel.usernameUpdateResult.value is Resource.Loading
            val isUpdatingBio = profileViewModel.bioUpdateResult.value is Resource.Loading
            val isUpdatingCity = profileViewModel.cityUpdateResult.value is Resource.Loading

            // Mise à jour de l'état des boutons
            binding.buttonSaveProfile.isEnabled = !isLoadingProfile && !isUpdatingUsername && !isUpdatingBio && !isUpdatingCity
            binding.fabSelectPicture.isEnabled = !isLoadingProfile && (authViewModel.profilePictureUpdateResult.value !is Resource.Loading)

            when (resource) {
                is Resource.Loading -> {
                    Log.d(TAG, "Chargement des données du profil...")
                    // Vous pouvez ajouter ici un indicateur de chargement si nécessaire
                }
                is Resource.Success -> {
                    Log.d(TAG, "Données du profil chargées/mises à jour.")
                    resource.data?.let { user ->
                        // Remplir les champs de l'UI directement à partir de l'objet User
                        binding.tvProfileEmail.text = user.email ?: getString(R.string.email_not_available)
                        binding.etProfileUsername.setText(user.username ?: getString(R.string.username_not_defined))
                        binding.etProfileBio.setText(user.bio ?: "")
                        binding.etProfileCity.setText(user.city ?: "")

                        // Charger l'image de profil
                        val photoUrl = user.profilePictureUrl
                        Log.d(TAG, "Chargement de l'image de profil pour '${user.username}'. URL: '$photoUrl'")
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
                                    Log.e(TAG, "Glide onLoadFailed pour URL: $model", e)
                                    return false
                                }
                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>?,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Log.d(TAG, "Glide onResourceReady pour URL: $model")
                                    return false
                                }
                            })
                            .circleCrop()
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(binding.ivProfilePicture)
                    } ?: run {
                        // Cas où data est null dans Resource.Success (improbable si le repository est correct)
                        Log.e(TAG, "User data is null in Resource.Success for profile.")
                        Snackbar.make(binding.root, getString(R.string.error_loading_profile), Snackbar.LENGTH_LONG).show()
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "Erreur de chargement du profil: ${resource.message}")
                    Snackbar.make(binding.root, resource.message ?: getString(R.string.error_loading_profile), Snackbar.LENGTH_LONG).show()
                    // Optionnel: Réinitialiser l'UI en cas d'erreur grave
                    binding.tvProfileEmail.text = getString(R.string.email_not_available)
                    binding.etProfileUsername.setText(getString(R.string.username_not_defined))
                    binding.etProfileBio.setText("")
                    binding.etProfileCity.setText("")
                    binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder) // Retour au placeholder
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
                is Resource.Loading -> Log.d(TAG, "Mise à jour du pseudo en cours...")
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
                is Resource.Loading -> Log.d(TAG, "Mise à jour de la biographie en cours...")
                null -> {
                    if (!isLoadingProfile && !isUpdatingUsername && !isUpdatingCity) {
                        binding.buttonSaveProfile.isEnabled = true
                    }
                }
            }
        }

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
                is Resource.Loading -> Log.d(TAG, "Mise à jour de la ville en cours...")
                null -> {
                    if (!isLoadingProfile && !isUpdatingUsername && !isUpdatingBio) {
                        binding.buttonSaveProfile.isEnabled = true
                    }
                }
            }
        }

        authViewModel.profilePictureUpdateResult.observe(viewLifecycleOwner) { result ->
            binding.fabSelectPicture.isEnabled = result !is Resource.Loading
            when (result) {
                is Resource.Success -> {
                    val newImageUrl = result.data
                    Snackbar.make(binding.root, getString(R.string.profile_picture_updated_success), Snackbar.LENGTH_SHORT).show()
                    // Suppression de l'appel direct à profileViewModel.setCurrentProfilePictureUrl()
                    // car userProfileData.observe va se mettre à jour automatiquement via Firestore listener
                    // ou bien un reload explicite du profil est nécessaire si le flow n'est pas réactif aux mises à jour externes.
                    // Pour le moment, profileViewModel.loadCurrentUserProfile() est plus sûr si l'AuthViewModel met à jour Auth seulement.
                    profileViewModel.loadCurrentUserProfile()
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, result.message ?: getString(R.string.error_updating_profile_picture), Snackbar.LENGTH_LONG).show()
                    profileViewModel.loadCurrentUserProfile()
                }
                is Resource.Loading -> { /* Bouton géré */ }
                null -> {
                    if (profileViewModel.userProfileData.value !is Resource.Loading) {
                        binding.fabSelectPicture.isEnabled = true
                    }
                }
            }
        }

        // OBSERVATEUR POUR LA LECTURE EN COURS DU PROFIL PRIVÉ
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.currentReadingUiState.collectLatest { uiState ->
                    // La carte de lecture en cours est toujours visible sur le profil privé,
                    // même si aucune lecture n'est définie (pour afficher le bouton "Ajouter").
                    binding.cardPrivateCurrentReading.visibility = View.VISIBLE
                    binding.btnManageCurrentReading.visibility = View.VISIBLE // Rendre le bouton visible explicitement
                    binding.btnManageCurrentReading.isEnabled = !uiState.isLoading // Activer/désactiver en fonction du chargement

                    when {
                        uiState.isLoading -> {
                            // Masquer les détails pendant le chargement
                            binding.tvPrivateCurrentReadingBookTitle.text = ""
                            binding.tvPrivateCurrentReadingBookAuthor.text = ""
                            binding.ivPrivateCurrentReadingBookCover.setImageResource(android.R.color.transparent) // Effacer l'image
                            binding.tvPrivateCurrentReadingProgressText.visibility = View.GONE
                            binding.progressBarPrivateCurrentReading.visibility = View.GONE
                            binding.llPrivatePersonalReflectionSection.visibility = View.GONE
                            binding.btnManageCurrentReading.setText(R.string.manage_reading_button) // Garder le texte par défaut
                            Log.d(TAG, "currentReadingUiState (Private): Chargement en cours.")
                        }
                        uiState.error != null -> {
                            // Afficher l'erreur et masquer les détails
                            binding.tvPrivateCurrentReadingBookTitle.text = getString(R.string.error_loading_data)
                            binding.tvPrivateCurrentReadingBookAuthor.text = uiState.error
                            binding.ivPrivateCurrentReadingBookCover.setImageResource(R.drawable.ic_error) // Icône d'erreur
                            binding.tvPrivateCurrentReadingProgressText.visibility = View.GONE
                            binding.progressBarPrivateCurrentReading.visibility = View.GONE
                            binding.llPrivatePersonalReflectionSection.visibility = View.GONE
                            binding.btnManageCurrentReading.setText(R.string.manage_reading_button)
                            Snackbar.make(binding.root, uiState.error, Snackbar.LENGTH_LONG).show()
                            Log.e(TAG, "currentReadingUiState (Private): Erreur: ${uiState.error}")
                        }
                        uiState.bookReading == null || uiState.bookDetails == null -> {
                            // Si aucune lecture en cours ou détails manquants, afficher le placeholder pour ajouter
                            binding.tvPrivateCurrentReadingBookTitle.text = getString(R.string.no_current_reading_title)
                            binding.tvPrivateCurrentReadingBookAuthor.text = getString(R.string.tap_to_add_reading)
                            binding.ivPrivateCurrentReadingBookCover.setImageResource(R.drawable.ic_add_book_placeholder)
                            binding.tvPrivateCurrentReadingProgressText.visibility = View.GONE
                            binding.progressBarPrivateCurrentReading.visibility = View.GONE
                            binding.llPrivatePersonalReflectionSection.visibility = View.GONE
                            binding.btnManageCurrentReading.setText(R.string.add_reading_button) // Texte du bouton "Ajouter une lecture"
                            Log.d(TAG, "currentReadingUiState (Private): Aucune lecture en cours. Affichage 'Ajouter'.")
                        }
                        else -> {
                            // Afficher les détails de la lecture en cours
                            binding.tvPrivateCurrentReadingProgressText.visibility = View.VISIBLE
                            binding.progressBarPrivateCurrentReading.visibility = View.VISIBLE
                            // IMPORTANT : Réactiver la visibilité de la section de note personnelle ici
                            binding.llPrivatePersonalReflectionSection.visibility = View.VISIBLE
                            binding.btnManageCurrentReading.setText(R.string.manage_reading_button) // Texte du bouton "Gérer la lecture"
                            Log.d(TAG, "currentReadingUiState (Private): Affichage de la lecture en cours.")

                            val reading = uiState.bookReading
                            val book = uiState.bookDetails

                            // Couverture du livre
                            Glide.with(this@ProfileFragment)
                                .load(book.coverImageUrl)
                                .placeholder(R.drawable.ic_book_placeholder)
                                .error(R.drawable.ic_book_placeholder)
                                .transition(DrawableTransitionOptions.withCrossFade())
                                .into(binding.ivPrivateCurrentReadingBookCover)

                            // Titre et Auteur
                            binding.tvPrivateCurrentReadingBookTitle.text = book.title
                            binding.tvPrivateCurrentReadingBookAuthor.text = book.author

                            // Progression
                            val currentPage = reading.currentPage
                            val totalPages = reading.totalPages
                            if (totalPages > 0) {
                                binding.tvPrivateCurrentReadingProgressText.text = getString(R.string.page_progress_format, currentPage, totalPages)
                                val progressPercentage = (currentPage.toFloat() / totalPages.toFloat() * 100).toInt()
                                binding.progressBarPrivateCurrentReading.progress = progressPercentage
                            } else {
                                binding.tvPrivateCurrentReadingProgressText.text = getString(R.string.page_progress_unknown)
                                binding.progressBarPrivateCurrentReading.progress = 0
                            }

                            // Note personnelle (visibilité conditionnelle à son contenu)
                            val personalNote = reading.favoriteQuote?.takeIf { it.isNotBlank() }
                                ?: reading.personalReflection?.takeIf { it.isNotBlank() }

                            if (!personalNote.isNullOrBlank()) {
                                binding.llPrivatePersonalReflectionSection.visibility = View.VISIBLE
                                binding.tvPrivateCurrentReadingPersonalNote.text = personalNote
                            } else {
                                binding.llPrivatePersonalReflectionSection.visibility = View.GONE
                                binding.tvPrivateCurrentReadingPersonalNote.text = ""
                            }
                        }
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

        binding.fabSelectPicture.setOnClickListener {
            if (profileViewModel.firebaseAuth.currentUser != null) {
                pickImageLauncher.launch("image/*")
            } else {
                Snackbar.make(binding.root, getString(R.string.user_not_connected_error_for_action, getString(R.string.select_picture)), Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.buttonSaveProfile.setOnClickListener {
            val newUsername = binding.etProfileUsername.text.toString().trim()
            val newBio = binding.etProfileBio.text.toString().trim()
            val newCity = binding.etProfileCity.text.toString().trim()

            binding.tilProfileUsername.error = null

            var canProceedWithUsername = true
            if (newUsername.isBlank()) {
                binding.tilProfileUsername.error = getString(R.string.username_cannot_be_empty)
                canProceedWithUsername = false
            }

            if (canProceedWithUsername) {
                Log.d(TAG, "Clic sur Enregistrer. Pseudo: '$newUsername', Bio: '$newBio', Ville: '$newCity'.")
                profileViewModel.updateUsername(newUsername)
            }
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

    // MISE À JOUR : Listener pour le bouton "Gérer la lecture"
    private fun setupManageCurrentReadingButton() {
        binding.btnManageCurrentReading.setOnClickListener {
            Log.d(TAG, "Bouton 'Gérer la lecture' ou 'Ajouter une lecture' cliqué. Navigation vers l'écran d'édition.")
            // L'action correcte pour naviguer du fragment Profile (qui est navigation_members_profile)
            // vers editCurrentReadingFragment
            val action = ProfileFragmentDirections.actionNavigationMembersProfileToEditCurrentReadingFragment()
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
        profileViewModel.clearCityUpdateResult()
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}