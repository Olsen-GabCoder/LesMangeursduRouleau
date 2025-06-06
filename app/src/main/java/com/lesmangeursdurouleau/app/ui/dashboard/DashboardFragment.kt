package com.lesmangeursdurouleau.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lesmangeursdurouleau.app.databinding.FragmentDashboardBinding
import java.text.SimpleDateFormat
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

@AndroidEntryPoint // ANNOTATION AJOUTÉE
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnJoinMeetingPlaceholder.setOnClickListener {
            dashboardViewModel.onJoinMeetingClicked()
            Toast.makeText(requireContext(), "Rejoindre la réunion (TODO)", Toast.LENGTH_SHORT).show()
        }

        binding.btnReadBookPlaceholder.setOnClickListener {
            dashboardViewModel.onReadBookClicked()
            Toast.makeText(requireContext(), "Lire l'œuvre (TODO)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupObservers() {
        dashboardViewModel.quoteOfTheMonth.observe(viewLifecycleOwner) { quote ->
            binding.textCitationPlaceholder.text = "\"${quote.text}\"${quote.author?.let { " - $it" } ?: ""}"
        }

        dashboardViewModel.nextEvent.observe(viewLifecycleOwner) { event ->
            if (event != null) {
                val dateFormat = SimpleDateFormat("EEEE dd MMMM 'à' HH:mm", Locale.getDefault())
                binding.textEventPlaceholder.text = "Prochain événement : ${event.title} (${dateFormat.format(event.date)})"
                binding.btnJoinMeetingPlaceholder.visibility = View.VISIBLE
            } else {
                binding.textEventPlaceholder.text = "Aucun événement programmé prochainement."
                binding.btnJoinMeetingPlaceholder.visibility = View.GONE
            }
        }

        dashboardViewModel.lastAnalyzedBook.observe(viewLifecycleOwner) { book ->
            if (book != null) {
                binding.textLastBookPlaceholder.text = "Dernière œuvre analysée : ${book.title} par ${book.author}"
                binding.btnReadBookPlaceholder.visibility = View.VISIBLE
            } else {
                binding.textLastBookPlaceholder.text = "Aucune œuvre analysée récemment."
                binding.btnReadBookPlaceholder.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}