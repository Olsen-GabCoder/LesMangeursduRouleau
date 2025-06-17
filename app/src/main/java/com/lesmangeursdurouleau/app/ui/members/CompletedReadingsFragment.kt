package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.navArgs
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentCompletedReadingsBinding // Assurez-vous que cet import est correct
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint // Indique que ce fragment peut avoir des dépendances injectées par Hilt
class CompletedReadingsFragment : Fragment() {

    private var _binding: FragmentCompletedReadingsBinding? = null
    // Cette propriété est valide uniquement entre onCreateView et onDestroyView.
    private val binding get() = _binding!!

    // Utilisation de Safe Args pour récupérer les arguments passés au fragment
    private val args: CompletedReadingsFragmentArgs by navArgs()

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

        // Afficher les arguments dans la vue pour vérification (temporaire ou à développer)
        binding.tvCompletedReadingsInfo.text = getString(R.string.completed_readings_info_format, username, userId)
        Toast.makeText(requireContext(), getString(R.string.navigated_to_completed_readings, username), Toast.LENGTH_SHORT).show()

        // TODO: Ici, vous implémenterez la logique pour afficher la liste des lectures terminées
        // Vous aurez probablement besoin d'un ViewModel pour récupérer les données via le UserRepository.getCompletedReadings
    }

    private fun updateActionBarTitle(username: String?) {
        // Définit le titre de l'ActionBar. Si le username est null ou vide, utilise une valeur par défaut.
        val title = username?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_title_default)
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
        Log.i(TAG, "Titre ActionBar mis à jour avec: $title")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Nulifie l'objet binding pour éviter les fuites de mémoire
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}