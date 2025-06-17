package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentCompletedReadingsBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CompletedReadingsFragment : Fragment() {

    private var _binding: FragmentCompletedReadingsBinding? = null
    private val binding get() = _binding!!

    private val args: CompletedReadingsFragmentArgs by navArgs()
    private val viewModel: CompletedReadingsViewModel by viewModels()
    private lateinit var completedReadingsAdapter: CompletedReadingsAdapter

    companion object {
        private const val TAG = "CompletedReadingsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompletedReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = args.userId
        val username = args.username

        Log.d(TAG, "CompletedReadingsFragment créé. UserID: $userId, Username: $username")

        updateActionBarTitle(username)
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        // CORRECTION ICI: Instanciation de l'adaptateur avec le listener de clic
        completedReadingsAdapter = CompletedReadingsAdapter { completedReading ->
            // Logique de navigation au clic sur un item
            Log.d(TAG, "Clic sur la lecture terminée : ${completedReading.title}")

            val action = CompletedReadingsFragmentDirections.actionCompletedReadingsFragmentToCompletedReadingDetailFragment(
                userId = args.userId, // Le propriétaire du profil
                bookId = completedReading.bookId, // L'ID du livre cliqué
                username = args.username, // On passe le nom d'utilisateur pour le titre de l'écran suivant
                bookTitle = completedReading.title // On passe le titre du livre pour l'ActionBar
            )
            findNavController().navigate(action)
        }

        binding.rvCompletedReadings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = completedReadingsAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupObservers() {
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
                                binding.tvNoCompletedReadings.text = getString(R.string.no_completed_readings_yet, args.username ?: "Cet utilisateur")
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
        val title = username?.takeIf { it.isNotBlank() }?.let {
            getString(R.string.completed_readings_title_format, it)
        } ?: getString(R.string.title_completed_readings)
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
        Log.i(TAG, "Titre ActionBar mis à jour avec: $title")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvCompletedReadings.adapter = null
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}