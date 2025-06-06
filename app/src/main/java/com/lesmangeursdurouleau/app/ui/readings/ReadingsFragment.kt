package com.lesmangeursdurouleau.app.ui.readings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentReadingsBinding
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingListAdapter
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingWithBook
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class ReadingsFragment : Fragment() {

    private var _binding: FragmentReadingsBinding? = null
    private val binding get() = _binding!!

    private val readingsViewModel: ReadingsViewModel by viewModels()
    private lateinit var monthlyReadingListAdapter: MonthlyReadingListAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            monthlyReadingListAdapter.submitList(monthlyReadingListAdapter.currentList)
            Log.d("ReadingsFragment", "Refreshing monthly readings list for progress bars update.")
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    private val REFRESH_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L // 24 heures

    // Stocke l'ID de la lecture mensuelle en attente si le dialogue de permission est affiché
    // Sera null si l'action est un ajout, ou si le dialogue est affiché de manière proactive
    private var pendingMonthlyReadingId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        setupObservers()
        setupSecretCodeResultListener()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun setupRecyclerView() {
        monthlyReadingListAdapter = MonthlyReadingListAdapter { selectedMonthlyReadingWithBook ->
            // Vérifie la permission d'édition avant de naviguer vers le formulaire d'édition
            // L'ID est passé pour l'édition d'une lecture spécifique
            checkEditPermissionAndNavigate(selectedMonthlyReadingWithBook.monthlyReading.id)
        }

        binding.recyclerViewMonthlyReadings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = monthlyReadingListAdapter
        }
    }

    /**
     * Gère la navigation vers le formulaire d'ajout/édition de lecture.
     * Déclenche le dialogue de code secret si la permission d'édition n'est pas active.
     * @param monthlyReadingId L'ID de la lecture à modifier (null pour un ajout).
     */
    private fun checkEditPermissionAndNavigate(monthlyReadingId: String?) {
        // Lire la valeur actuelle du StateFlow canEditReadings du ViewModel.
        // Cette valeur reflète si l'utilisateur a la permission d'édition active,
        // en tenant compte de la date d'octroi de la permission (expiration 3 minutes).
        val hasActiveEditPermission = readingsViewModel.canEditReadings.value

        if (hasActiveEditPermission) {
            Log.d("ReadingsFragment", "User has active edit permission, navigating to AddEditMonthlyReading for ID: $monthlyReadingId")
            navigateToAddEditMonthlyReading(monthlyReadingId)
        } else {
            Log.d("ReadingsFragment", "User does NOT have active edit permission (either never granted or expired), showing secret code dialog for ID: $monthlyReadingId")
            // Stocke l'ID pour pouvoir naviguer vers le bon formulaire après la validation du code.
            pendingMonthlyReadingId = monthlyReadingId
            // Affiche le dialogue de code secret si la permission n'est pas active.
            // On vérifie si le dialogue n'est pas déjà affiché pour éviter les superpositions.
            if (childFragmentManager.findFragmentByTag("SecretCodeDialog") == null) {
                EnterSecretCodeDialogFragment().show(childFragmentManager, "SecretCodeDialog")
            }
        }
    }

    private fun navigateToAddEditMonthlyReading(monthlyReadingId: String? = null) {
        val action = ReadingsFragmentDirections.actionReadingsFragmentToAddEditMonthlyReadingFragment(monthlyReadingId)
        findNavController().navigate(action)
        pendingMonthlyReadingId = null // Réinitialise l'ID en attente après la navigation
    }

    private fun setupSecretCodeResultListener() {
        // Écoute le résultat du dialogue de code secret
        setFragmentResultListener(EnterSecretCodeDialogFragment.REQUEST_KEY) { _, bundle ->
            val permissionGranted = bundle.getBoolean(EnterSecretCodeDialogFragment.BUNDLE_KEY_PERMISSION_GRANTED, false)
            if (permissionGranted) {
                // Si la permission est accordée, nous navigons si un ID de lecture était en attente.
                // Sinon, cela signifie que le dialogue a été déclenché de manière proactive,
                // et l'utilisateur est simplement ré-authentifié sans navigation automatique.
                Log.d("ReadingsFragment", "Permission granted from dialog. Pending ID: $pendingMonthlyReadingId")
                // On navigue UNIQUEMENT si un pendingMonthlyReadingId était défini par une action explicite.
                // Si le dialogue s'est affiché passivement (expiration), pendingMonthlyReadingId sera null,
                // et l'utilisateur devra cliquer sur une action pour naviguer.
                pendingMonthlyReadingId?.let { id ->
                    navigateToAddEditMonthlyReading(id)
                } ?: run {
                    Log.d("ReadingsFragment", "No pending action, permission re-validated proactively. User can now proceed with actions.")
                    // Optionnel: On pourrait aussi déclencher un rafraîchissement des données si nécessaire.
                    readingsViewModel.forcePermissionCheck() // Force une nouvelle évaluation des permissions
                }
            } else {
                // Si la permission n'est pas accordée ou si l'utilisateur annule, on annule l'ID en attente.
                Log.d("ReadingsFragment", "Permission NOT granted or dialog cancelled. Clearing pending ID.")
                pendingMonthlyReadingId = null
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observer les lectures mensuelles combinées avec les livres
                launch {
                    readingsViewModel.monthlyReadingsWithBooks.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.progressBarReadings.visibility = View.VISIBLE
                                binding.recyclerViewMonthlyReadings.visibility = View.GONE
                                binding.tvErrorReadings.visibility = View.GONE
                            }
                            is Resource.Success -> {
                                binding.progressBarReadings.visibility = View.GONE
                                val data = resource.data ?: emptyList()
                                monthlyReadingListAdapter.submitList(data)
                                updateEmptyStateView(data.isEmpty(), null)
                                Log.d("ReadingsFragment", "Displayed ${data.size} monthly readings.")
                            }
                            is Resource.Error -> {
                                binding.progressBarReadings.visibility = View.GONE
                                monthlyReadingListAdapter.submitList(emptyList())
                                updateEmptyStateView(true, resource.message ?: getString(R.string.error_loading_monthly_readings, "inconnu"))
                                Log.e("ReadingsFragment", "Error loading monthly readings: ${resource.message}")
                            }
                        }
                    }
                }

                // Observer l'état de la permission d'édition (pour le débogage ou futures UI conditionnelles)
                launch {
                    readingsViewModel.canEditReadings.collect { canEdit ->
                        Log.d("ReadingsFragment", "User can edit readings (observed): $canEdit")
                        // Optionnel: Mettre à jour l'UI, par exemple désactiver le FAB si pas de permission.
                        // binding.fabAddMonthlyReading.isEnabled = canEdit
                    }
                }

                // NOUVEL OBSERVER : Pour demander une ré-validation de la permission (proactive)
                launch {
                    readingsViewModel.requestPermissionRevalidation.collect { shouldRevalidate ->
                        if (shouldRevalidate) {
                            Log.d("ReadingsFragment", "Received request to revalidate permission from ViewModel (proactive).")
                            // Affiche le dialogue de code secret si la permission a expiré et n'est pas déjà affiché
                            if (childFragmentManager.findFragmentByTag("SecretCodeDialog") == null) {
                                pendingMonthlyReadingId = null // Pas d'action spécifique en attente pour cette ré-validation proactive
                                EnterSecretCodeDialogFragment().show(childFragmentManager, "SecretCodeDialog")
                            }
                        }
                    }
                }

                // Observer le mois et l'année courants pour l'affichage de l'en-tête
                launch {
                    readingsViewModel.currentMonthYear.collect { calendar ->
                        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                        binding.tvCurrentMonthYear.text = monthFormat.format(calendar.time)
                    }
                }
            }
        }
    }

    private fun updateEmptyStateView(isEmpty: Boolean, errorMessage: String?) {
        if (isEmpty) {
            binding.recyclerViewMonthlyReadings.visibility = View.GONE
            binding.tvErrorReadings.visibility = View.VISIBLE
            binding.tvErrorReadings.text = errorMessage ?: getString(R.string.no_monthly_readings_available)
        } else {
            binding.recyclerViewMonthlyReadings.visibility = View.VISIBLE
            binding.tvErrorReadings.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.btnPreviousMonth.setOnClickListener {
            readingsViewModel.goToPreviousMonth()
        }
        binding.btnNextMonth.setOnClickListener {
            readingsViewModel.goToNextMonth()
        }

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_filter_all -> ReadingsFilter.ALL
                R.id.chip_filter_in_progress -> ReadingsFilter.IN_PROGRESS
                R.id.chip_filter_planned -> ReadingsFilter.PLANNED
                R.id.chip_filter_past -> ReadingsFilter.PAST
                else -> ReadingsFilter.ALL
            }
            readingsViewModel.setFilter(filter)
            Log.d("ReadingsFragment", "Filter changed to: $filter")
        }

        binding.fabAddMonthlyReading.setOnClickListener {
            // Vérifie la permission d'édition avant de naviguer pour ajouter une nouvelle lecture
            // L'ID est null pour un ajout
            checkEditPermissionAndNavigate(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewMonthlyReadings.adapter = null
        _binding = null
        handler.removeCallbacks(refreshRunnable)
    }
}