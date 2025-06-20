package com.lesmangeursdurouleau.app.ui.members

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentFullScreenImageBinding

class FullScreenImageFragment : Fragment() {

    private var _binding: FragmentFullScreenImageBinding? = null
    private val binding get() = _binding!!

    private val args: FullScreenImageFragmentArgs by navArgs()

    // Launcher pour la demande de permission. C'est la méthode moderne.
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // La permission a été accordée. On peut lancer le téléchargement.
                downloadImage(args.imageUrl)
            } else {
                // La permission a été refusée. On informe l'utilisateur.
                Toast.makeText(requireContext(), "Permission refusée. Le téléchargement est impossible.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFullScreenImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Glide.with(this)
            .load(args.imageUrl)
            .fitCenter()
            .into(binding.ivFullScreenImage)

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnDownload.setOnClickListener {
            startDownload(args.imageUrl)
        }

        binding.root.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun startDownload(imageUrl: String) {
        // Pour Android 10 (Q) et plus, aucune permission n'est requise pour enregistrer dans le dossier Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadImage(imageUrl)
        } else {
            // Pour les versions plus anciennes, on vérifie si la permission a été accordée
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // La permission est déjà accordée, on télécharge
                    downloadImage(imageUrl)
                }
                else -> {
                    // La permission n'est pas accordée, on la demande
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun downloadImage(imageUrl: String) {
        try {
            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val imageUri = Uri.parse(imageUrl)

            // Création d'un nom de fichier unique basé sur le temps actuel
            val fileName = "LMR_${System.currentTimeMillis()}.jpg"

            val request = DownloadManager.Request(imageUri).apply {
                setTitle("Image de 'Les Mangeurs du Rouleau'")
                setDescription("Téléchargement en cours...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // Le fichier sera sauvegardé dans le dossier public "Downloads"
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                // Permet au Media Scanner de détecter l'image pour qu'elle apparaisse dans la galerie
                allowScanningByMediaScanner()
            }

            downloadManager.enqueue(request)
            Toast.makeText(requireContext(), "Le téléchargement a commencé.", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            // Gestion d'une erreur potentielle (ex: URL invalide)
            Toast.makeText(requireContext(), "Erreur lors du lancement du téléchargement.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}