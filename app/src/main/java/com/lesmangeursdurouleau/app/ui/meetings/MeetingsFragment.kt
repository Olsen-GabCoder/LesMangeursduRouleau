package com.lesmangeursdurouleau.app.ui.meetings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentMeetingsBinding
import com.lesmangeursdurouleau.app.ui.meetings.adapter.MeetingListAdapter
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

@AndroidEntryPoint // ANNOTATION AJOUTÉE
class MeetingsFragment : Fragment() {

    private var _binding: FragmentMeetingsBinding? = null
    private val binding get() = _binding!!

    private val meetingsViewModel: MeetingsViewModel by viewModels()
    private lateinit var meetingListAdapter: MeetingListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeetingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        meetingListAdapter = MeetingListAdapter { selectedMeeting ->
            Toast.makeText(requireContext(), "Réunion cliquée : ${selectedMeeting.title}", Toast.LENGTH_SHORT).show()
            // TODO: Naviguer vers MeetingDetailActivity
        }

        binding.recyclerViewMeetings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = meetingListAdapter
        }
    }

    private fun setupObservers() {
        meetingsViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBarMeetings.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.recyclerViewMeetings.visibility = View.GONE
                binding.tvErrorMeetings.visibility = View.GONE
            }
        }

        meetingsViewModel.meetings.observe(viewLifecycleOwner) { meetingList ->
            meetingListAdapter.submitList(meetingList)
            updateEmptyStateView(meetingList.isNullOrEmpty() && meetingsViewModel.isLoading.value == false, null)
        }

        meetingsViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            updateEmptyStateView(meetingsViewModel.meetings.value.isNullOrEmpty() && meetingsViewModel.isLoading.value == false, errorMessage)
        }
    }

    private fun updateEmptyStateView(showErrorOrEmpty: Boolean, errorMessage: String?) {
        if (meetingsViewModel.isLoading.value == true) return

        if (showErrorOrEmpty) {
            binding.recyclerViewMeetings.visibility = View.GONE
            binding.tvErrorMeetings.visibility = View.VISIBLE
            binding.tvErrorMeetings.text = errorMessage ?: getString(R.string.no_meetings_scheduled)
        } else {
            binding.recyclerViewMeetings.visibility = View.VISIBLE
            binding.tvErrorMeetings.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewMeetings.adapter = null // Bonne pratique
        _binding = null
    }
}