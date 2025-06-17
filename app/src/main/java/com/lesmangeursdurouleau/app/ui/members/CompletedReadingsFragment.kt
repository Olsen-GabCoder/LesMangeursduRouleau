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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.CompletedReading
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
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
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
        Log.d(TAG, "CompletedReadingsFragment créé. UserID: ${args.userId}, Username: ${args.username}")
        updateActionBarTitle(args.username)
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        val currentUserId = firebaseAuth.currentUser?.uid
        completedReadingsAdapter = CompletedReadingsAdapter(
            currentUserId = currentUserId,
            profileOwnerId = args.userId,
            onItemClickListener = { completedReading ->
                Log.d(TAG, "Clic sur la lecture terminée : ${completedReading.title}")
                val action = CompletedReadingsFragmentDirections.actionCompletedReadingsFragmentToCompletedReadingDetailFragment(
                    userId = args.userId,
                    bookId = completedReading.bookId,
                    username = args.username,
                    bookTitle = completedReading.title
                )
                findNavController().navigate(action)
            },
            onDeleteClickListener = { completedReading ->
                showDeleteConfirmationDialog(completedReading)
            }
        )

        binding.rvCompletedReadings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = completedReadingsAdapter
            setHasFixedSize(false)
        }
    }

    private fun showDeleteConfirmationDialog(reading: CompletedReading) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_reading_dialog_title)
            .setMessage(getString(R.string.delete_reading_dialog_message, reading.title))
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.delete) { dialog, _ ->
                viewModel.deleteCompletedReading(reading.bookId)
                dialog.dismiss()
            }
            .show()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observer la liste des lectures
                launch {
                    viewModel.completedReadings.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.progressBarCompletedReadings.visibility = View.VISIBLE
                                binding.rvCompletedReadings.visibility = View.GONE
                                binding.tvNoCompletedReadings.visibility = View.GONE
                                binding.tvCompletedReadingsError.visibility = View.GONE
                            }
                            is Resource.Success -> {
                                binding.progressBarCompletedReadings.visibility = View.GONE
                                val readings = resource.data ?: emptyList()
                                if (readings.isEmpty()) {
                                    binding.rvCompletedReadings.visibility = View.GONE
                                    binding.tvNoCompletedReadings.visibility = View.VISIBLE
                                    binding.tvCompletedReadingsError.visibility = View.GONE
                                    binding.tvNoCompletedReadings.text = getString(R.string.no_completed_readings_yet, args.username ?: "Cet utilisateur")
                                } else {
                                    completedReadingsAdapter.submitList(readings)
                                    binding.rvCompletedReadings.visibility = View.VISIBLE
                                    binding.tvNoCompletedReadings.visibility = View.GONE
                                    binding.tvCompletedReadingsError.visibility = View.GONE
                                }
                            }
                            is Resource.Error -> {
                                binding.progressBarCompletedReadings.visibility = View.GONE
                                binding.rvCompletedReadings.visibility = View.GONE
                                binding.tvNoCompletedReadings.visibility = View.GONE
                                binding.tvCompletedReadingsError.visibility = View.VISIBLE
                                binding.tvCompletedReadingsError.text = resource.message ?: getString(R.string.error_unknown)
                                Toast.makeText(requireContext(), getString(R.string.error_loading_completed_readings, resource.message), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                // NOUVEAU: Observer le statut de la suppression
                launch {
                    viewModel.deleteStatus.collectLatest { resource ->
                        when(resource) {
                            is Resource.Success -> {
                                Toast.makeText(context, R.string.reading_deleted_successfully, Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
                            }
                            else -> { /* Ne rien faire pour Loading */ }
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvCompletedReadings.adapter = null
        _binding = null
    }
}