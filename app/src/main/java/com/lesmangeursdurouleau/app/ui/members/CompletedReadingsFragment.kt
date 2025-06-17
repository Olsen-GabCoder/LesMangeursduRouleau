package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels // Import ajouté pour viewModels
import androidx.lifecycle.Lifecycle // Import ajouté
import androidx.lifecycle.lifecycleScope // Import ajouté
import androidx.lifecycle.repeatOnLifecycle // Import ajouté
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager // Import ajouté
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentCompletedReadingsBinding
import com.lesmangeursdurouleau.app.utils.Resource // Import ajouté pour Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest // Import ajouté
import kotlinx.coroutines.launch // Import ajouté

@AndroidEntryPoint // Indique que ce fragment peut avoir des dépendances injectées par Hilt
class CompletedReadingsFragment : Fragment() {

    private var _binding: FragmentCompletedReadingsBinding? = null
    // Cette propriété est valide uniquement entre onCreateView et onDestroyView.
    private val binding get() = _binding!!

    // Utilisation de Safe Args pour récupérer les arguments passés au fragment
    private val args: CompletedReadingsFragmentArgs by navArgs()

    // Injection du ViewModel pour récupérer les données
    private val viewModel: CompletedReadingsViewModel by viewModels()

    // Adaptateur pour le RecyclerView
    private lateinit var completedReadingsAdapter: CompletedReadingsAdapter

    companion object {
        private const val TAG = "CompletedReadingsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate le layout pour ce fragment en utilisant ViewBinding
        _binding = FragmentCompletedReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = args.userId
        val username = args.username

        Log.d(TAG, "CompletedReadingsFragment créé. UserID: $userId, Username: $username")

        // Mettre à jour le titre de l'ActionBar
        updateActionBarTitle(username)

        // Initialisation du RecyclerView et de l'adaptateur
        setupRecyclerView()

        // Observation des données du ViewModel
        setupObservers()

        // Le Toast temporaire de navigation peut être retiré si désiré
        Toast.makeText(requireContext(), getString(R.string.navigated_to_completed_readings, username), Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        completedReadingsAdapter = CompletedReadingsAdapter()
        binding.rvCompletedReadings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = completedReadingsAdapter
            // Permet au RecyclerView d'ajuster sa taille en fonction du contenu
            setHasFixedSize(false)
        }
    }

    private fun setupObservers() {
        // Observer la liste des lectures terminées du ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.completedReadings.collectLatest { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            binding.progressBarCompletedReadings.visibility = View.VISIBLE
                            binding.rvCompletedReadings.visibility = View.GONE
                            binding.tvNoCompletedReadings.visibility = View.GONE
                            binding.tvCompletedReadingsError.visibility = View.GONE
                            Log.d(TAG, "Chargement des lectures terminées...")
                        }
                        is Resource.Success -> {
                            binding.progressBarCompletedReadings.visibility = View.GONE
                            val readings = resource.data ?: emptyList()
                            if (readings.isEmpty()) {
                                binding.rvCompletedReadings.visibility = View.GONE
                                binding.tvNoCompletedReadings.visibility = View.VISIBLE
                                binding.tvCompletedReadingsError.visibility = View.GONE
                                binding.tvNoCompletedReadings.text = getString(R.string.no_completed_readings_yet, args.username)
                                Log.d(TAG, "Aucune lecture terminée trouvée pour ${args.username}.")
                            } else {
                                completedReadingsAdapter.submitList(readings)
                                binding.rvCompletedReadings.visibility = View.VISIBLE
                                binding.tvNoCompletedReadings.visibility = View.GONE
                                binding.tvCompletedReadingsError.visibility = View.GONE
                                Log.d(TAG, "${readings.size} lectures terminées chargées pour ${args.username}.")
                            }
                        }
                        is Resource.Error -> {
                            binding.progressBarCompletedReadings.visibility = View.GONE
                            binding.rvCompletedReadings.visibility = View.GONE
                            binding.tvNoCompletedReadings.visibility = View.GONE
                            binding.tvCompletedReadingsError.visibility = View.VISIBLE
                            binding.tvCompletedReadingsError.text = resource.message ?: getString(R.string.error_unknown)
                            Log.e(TAG, "Erreur lors du chargement des lectures terminées: ${resource.message}")
                            Toast.makeText(requireContext(), getString(R.string.error_loading_completed_readings, resource.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateActionBarTitle(username: String?) {
        // Définit le titre de l'ActionBar. Si le username est null ou vide, utilise une valeur par défaut.
        val title = username?.takeIf { it.isNotBlank() }?.let {
            getString(R.string.completed_readings_title_format, it) // Format plus spécifique pour le titre
        } ?: getString(R.string.title_completed_readings) // Titre par défaut du fragment (si username absent)
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
        Log.i(TAG, "Titre ActionBar mis à jour avec: $title")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Nulifie l'objet binding et l'adaptateur pour éviter les fuites de mémoire
        binding.rvCompletedReadings.adapter = null
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}