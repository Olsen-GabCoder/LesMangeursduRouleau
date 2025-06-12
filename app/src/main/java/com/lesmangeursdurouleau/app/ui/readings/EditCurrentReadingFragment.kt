package com.lesmangeursdurouleau.app.ui.readings

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.snackbar.Snackbar
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
// import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.databinding.FragmentEditCurrentReadingBinding
import com.lesmangeursdurouleau.app.ui.readings.selection.BookSelectionFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditCurrentReadingFragment : Fragment() {

    private var _binding: FragmentEditCurrentReadingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditCurrentReadingViewModel by viewModels()

    companion object {
        private const val TAG = "EditCurrentReadingFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditCurrentReadingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupObservers()
        setupClickListeners()
        setupInputListeners()
        setupBookSelectionResultListener() // NOUVEL APPEL ICI
    }

    // NOUVELLE FONCTION POUR GÉRER LE RÉSULTAT DE LA SÉLECTION DE LIVRE
    private fun setupBookSelectionResultListener() {
        // Enregistrement de l'écouteur pour le résultat du fragment de sélection de livre
        setFragmentResultListener(BookSelectionFragment.KEY_REQUEST_BOOK_SELECTION) { requestKey, bundle ->
            // S'assurer que la clé correspond
            if (requestKey == BookSelectionFragment.KEY_REQUEST_BOOK_SELECTION) {
                // Compatibilité API pour getParcelable
                val selectedBook: Book? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bundle.getParcelable(BookSelectionFragment.KEY_SELECTED_BOOK, Book::class.java)
                } else {
                    @Suppress("DEPRECATION") // Suppression de l'avertissement de dépréciation pour les anciennes API
                    bundle.getParcelable(BookSelectionFragment.KEY_SELECTED_BOOK)
                }

                selectedBook?.let {
                    Log.d(TAG, "Livre sélectionné reçu du BookSelectionFragment: ${it.title} (ID: ${it.id})")
                    // APPEL À UNE MÉTHODE DU VIEWMODEL QUI DOIT EXISTER
                    viewModel.setSelectedBook(it)
                } ?: run {
                    Log.w(TAG, "Livre sélectionné reçu était null ou de type incorrect.")
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbarEditReading.setNavigationOnClickListener {
            findNavController().navigateUp()
            Log.d(TAG, "Navigation Up cliquée sur la toolbar.")
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { uiState ->
                    Log.d(TAG, "UI State mis à jour: $uiState")
                    showLoading(uiState.isLoading)
                    enableForm(!uiState.isLoading) // Désactiver le formulaire pendant le chargement/sauvegarde

                    if (uiState.error != null) {
                        Snackbar.make(binding.root, uiState.error, Snackbar.LENGTH_LONG).show()
                        Log.e(TAG, "Erreur dans l'UI State: ${uiState.error}")
                        // Optionnel: notifier le VM que l'erreur a été affichée pour éviter de la réafficher
                        // viewModel.clearError()
                    }

                    populateForm(uiState)

                    // Gérer l'état du bouton "Retirer la lecture"
                    // Si une lecture existe, ou si un livre vient d'être sélectionné (en attente de sauvegarde)
                    if (uiState.bookReading != null || uiState.selectedBook != null) {
                        binding.btnRemoveReading.text = getString(R.string.remove_reading_button)
                        binding.btnRemoveReading.isEnabled = !uiState.isLoading // Toujours activé si pas en chargement
                    } else { // Si aucun livre n'est ni existant, ni sélectionné
                        binding.btnRemoveReading.text = getString(R.string.add_reading_button)
                        binding.btnRemoveReading.isEnabled = !uiState.isLoading // Toujours activé si pas en chargement
                    }
                    // Activer le bouton sauvegarder uniquement si un livre est présent (existant ou sélectionné) et pas en chargement
                    binding.btnSaveReading.isEnabled = !uiState.isLoading && (uiState.selectedBook != null || uiState.bookReading != null)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        is EditReadingEvent.ShowToast -> {
                            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Toast affiché: ${event.message}")
                        }
                        is EditReadingEvent.NavigateBack -> {
                            findNavController().navigateUp()
                            Log.d(TAG, "Navigation retour déclenchée par l'événement.")
                        }
                        is EditReadingEvent.ShowDeleteConfirmationDialog -> {
                            showDeleteConfirmationDialog()
                            Log.d(TAG, "Dialogue de confirmation de suppression affiché.")
                        }
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectBook.setOnClickListener {
            // MODIFIÉ : Navigation via Safe Args vers le fragment de sélection de livre
            try {
                val action = EditCurrentReadingFragmentDirections.actionEditCurrentReadingFragmentToBookSelectionFragment()
                findNavController().navigate(action)
                Log.d(TAG, "Bouton 'Sélectionner un livre' cliqué. Navigation vers BookSelectionFragment.")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur de navigation vers BookSelectionFragment. Vérifiez le graphe de navigation et Safe Args.", e)
                Toast.makeText(context, "Erreur de navigation", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveReading.setOnClickListener {
            val currentPageText = binding.etCurrentPageInput.text.toString()
            val totalPagesText = binding.etTotalPagesInput.text.toString()
            val favoriteQuote = binding.etFavoriteQuoteInput.text.toString().trim()
            val personalReflection = binding.etPersonalReflectionInput.text.toString().trim()

            // Réinitialiser les erreurs
            binding.tilCurrentPage.error = null
            binding.tilTotalPages.error = null

            val currentPage = currentPageText.toIntOrNull() ?: 0
            val totalPages = totalPagesText.toIntOrNull() ?: 0

            // Récupérer le livre à sauvegarder (priorité au livre nouvellement sélectionné, sinon le livre de la lecture existante)
            val bookToSave = viewModel.uiState.value.selectedBook ?: viewModel.uiState.value.bookReading?.let { Book(it.bookId, "", "")}

            // Validation de base pour la sauvegarde
            if (bookToSave == null || bookToSave.id.isBlank()) { // Le livre doit être sélectionné ou exister
                Snackbar.make(binding.root, getString(R.string.error_book_not_selected), Snackbar.LENGTH_SHORT).show()
                Log.w(TAG, "Tentative de sauvegarde sans livre sélectionné ou existant.")
                return@setOnClickListener
            }
            // Validation des numéros de page : currentPage peut être 0 (début de lecture), totalPages doit être > 0
            if (currentPage < 0) {
                binding.tilCurrentPage.error = getString(R.string.error_invalid_page_numbers)
                Log.w(TAG, "Page actuelle invalide: $currentPage (doit être >= 0).")
                return@setOnClickListener
            }
            if (totalPages <= 0) {
                binding.tilTotalPages.error = getString(R.string.error_invalid_page_numbers)
                Log.w(TAG, "Total pages invalide: $totalPages (doit être > 0).")
                return@setOnClickListener
            }
            if (currentPage > totalPages) {
                binding.tilCurrentPage.error = getString(R.string.error_invalid_page_numbers)
                binding.tilTotalPages.error = getString(R.string.error_invalid_page_numbers)
                Log.w(TAG, "Page actuelle ($currentPage) > Total pages ($totalPages).")
                return@setOnClickListener
            }

            Log.d(TAG, "Bouton 'Enregistrer' cliqué. Tentative de sauvegarde.")
            viewModel.saveCurrentReading(currentPage, totalPages, favoriteQuote, personalReflection)
        }

        binding.btnRemoveReading.setOnClickListener {
            val uiState = viewModel.uiState.value
            // Si une lecture existe déjà (bookReading), ou si un livre vient d'être sélectionné (selectedBook)
            if (uiState.bookReading != null) { // Il y a une lecture existante à supprimer
                viewModel.confirmRemoveCurrentReading()
                Log.d(TAG, "Bouton 'Retirer la lecture' cliqué. Demande de confirmation (lecture existante).")
            } else if (uiState.selectedBook != null) { // Un livre a été sélectionné mais n'est pas encore sauvegardé comme lecture
                // Ici, l'action "Retirer" devrait annuler la sélection en cours
                viewModel.setSelectedBook(null) // Réinitialise le livre sélectionné dans le ViewModel
                // Optionnel: Réinitialiser les champs de saisie pour commencer une nouvelle sélection
                binding.etCurrentPageInput.setText("")
                binding.etTotalPagesInput.setText("")
                binding.etFavoriteQuoteInput.setText("")
                binding.etPersonalReflectionInput.setText("")
                Log.d(TAG, "Bouton 'Retirer la lecture' cliqué. Livre sélectionné nettoyé (nouvelle lecture non sauvegardée).")
            } else { // Aucune lecture existante et aucun livre sélectionné (état initial)
                // Le bouton agit comme un "Ajouter une lecture", donc naviguer vers la sélection
                binding.btnSelectBook.performClick() // Simule un clic sur le bouton de sélection de livre
                Log.d(TAG, "Bouton 'Ajouter une lecture' cliqué. Lance la sélection de livre.")
            }
        }
    }

    private fun setupInputListeners() {
        // Effacer les erreurs dès que l'utilisateur commence à taper
        binding.etCurrentPageInput.doAfterTextChanged {
            binding.tilCurrentPage.error = null
        }
        binding.etTotalPagesInput.doAfterTextChanged {
            binding.tilTotalPages.error = null
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarEditReading.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun enableForm(enable: Boolean) {
        binding.btnSelectBook.isEnabled = enable
        binding.etCurrentPageInput.isEnabled = enable
        binding.etTotalPagesInput.isEnabled = enable
        binding.etFavoriteQuoteInput.isEnabled = enable
        binding.etPersonalReflectionInput.isEnabled = enable
        // Les boutons "Retirer/Ajouter" et "Save" ont leur propre logique d'activation gérée dans l'observateur uiState.
        // binding.btnSaveReading.isEnabled est géré dynamiquement
    }

    private fun populateForm(uiState: EditReadingUiState) {
        // Priorité au livre nouvellement sélectionné (uiState.selectedBook)
        // Sinon, si une lecture existe, nous utilisons le bookId de UserBookReading.
        // ATTENTION: ici, si bookReading existe mais pas selectedBook, on recrée un Book(bookId, "", "")
        // Ce n'est pas idéal car ça perd les détails (titre, auteur, couverture) de la lecture existante.
        // Dans une prochaine étape, nous devrons Fetch les détails complets du livre via son ID
        // et les stocker dans UiState (par exemple, uiState.bookDetails: Book?).
        // Pour l'instant, je conserve la logique existante.
        val currentBook = uiState.selectedBook ?: uiState.bookReading?.let { Book(it.bookId, "", "") }

        // Gérer l'affichage de la section livre
        if (currentBook != null && currentBook.id.isNotBlank()) {
            Glide.with(this)
                .load(currentBook.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivSelectedBookCover)

            binding.tvSelectedBookTitle.text = currentBook.title.ifBlank { getString(R.string.title_not_available) }
            binding.tvSelectedBookAuthor.text = currentBook.author.ifBlank { getString(R.string.author_not_available) }
            binding.btnSelectBook.text = getString(R.string.change_book_button) // Change le texte du bouton si un livre est déjà sélectionné.
        } else {
            // Pas de livre sélectionné ou de lecture en cours, afficher le placeholder
            binding.ivSelectedBookCover.setImageResource(R.drawable.ic_book_placeholder)
            binding.tvSelectedBookTitle.text = getString(R.string.no_book_selected_title)
            binding.tvSelectedBookAuthor.text = getString(R.string.select_a_book_prompt)
            binding.btnSelectBook.text = getString(R.string.select_book_button)
        }

        // Pré-remplir les champs de saisie
        // Si un nouveau livre a été sélectionné (uiState.selectedBook != null), les champs doivent être vides pour une nouvelle lecture.
        // Sinon, si une lecture existe (uiState.bookReading != null), on pré-remplit avec ses données.
        // Sinon (rien de sélectionné/existant), les champs restent vides.
        if (uiState.selectedBook != null) {
            binding.etCurrentPageInput.setText("")
            binding.etTotalPagesInput.setText("")
            binding.etFavoriteQuoteInput.setText("")
            binding.etPersonalReflectionInput.setText("")
        } else if (uiState.bookReading != null) {
            val reading = uiState.bookReading
            binding.etCurrentPageInput.setText(reading.currentPage.toString())
            binding.etTotalPagesInput.setText(reading.totalPages.toString())
            binding.etFavoriteQuoteInput.setText(reading.favoriteQuote ?: "")
            binding.etPersonalReflectionInput.setText(reading.personalReflection ?: "")
        } else {
            // Si rien n'est sélectionné/existant, s'assurer que les champs sont vides.
            binding.etCurrentPageInput.setText("")
            binding.etTotalPagesInput.setText("")
            binding.etFavoriteQuoteInput.setText("")
            binding.etPersonalReflectionInput.setText("")
        }
    }


    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_removal_title))
            .setMessage(getString(R.string.confirm_removal_message))
            .setPositiveButton(getString(R.string.confirm_button)) { dialog, _ ->
                viewModel.removeCurrentReading()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                viewModel.cancelRemoveConfirmation() // Cette méthode semble exister dans votre VM
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}