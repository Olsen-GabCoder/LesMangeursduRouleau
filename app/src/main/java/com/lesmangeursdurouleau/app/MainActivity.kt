// Fichier: com/lesmangeursdurouleau/app/MainActivity.kt
package com.lesmangeursdurouleau.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.databinding.ActivityMainBinding
import com.lesmangeursdurouleau.app.notifications.MyFirebaseMessagingService
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    // Pour demander la permission de notification sur Android 13+
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Permission de notification accordée")
        } else {
            Log.w("MainActivity", "Permission de notification refusée")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_main) as NavHostFragment
        navController = navHostFragment.navController

        // La ligne suivante configure automatiquement la navigation pour la BottomNavigationView.
        // Puisque l'ID de l'item de menu pour "Messages" (@id/conversationsListFragmentDestination)
        // correspond à l'ID du fragment dans le nav_graph, le clic est géré sans code supplémentaire.
        // C'est ici que nous interviendrons plus tard pour ajouter les badges de notification.
        binding.bottomNavigationView.setupWithNavController(navController)

        // Demander la permission de notification si nécessaire (Android 13+)
        askNotificationPermission()

        // Récupérer et sauvegarder le jeton FCM de l'appareil
        saveFCMToken()

        // Gérer la navigation si l'activité est lancée par une notification
        handleNotificationIntent(intent)
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Gérer les nouvelles intentions si l'activité est déjà en cours et une nouvelle notification est reçue
        handleNotificationIntent(intent)
    }

    /**
     * Demande la permission d'afficher des notifications sur Android 13 (API 33) et supérieur.
     */
    private fun askNotificationPermission() {
        // Cette permission est uniquement nécessaire pour Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // La permission est déjà accordée
                Log.d("MainActivity", "Permission POST_NOTIFICATIONS déjà accordée.")
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                // Afficher une explication à l'utilisateur avant de demander la permission
                // Dans une vraie application, vous pourriez montrer une boîte de dialogue ici.
                Log.d("MainActivity", "Explication nécessaire pour la permission POST_NOTIFICATIONS.")
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Demander la permission directement
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Récupère le jeton d'enregistrement FCM et le sauvegarde sur Firestore pour l'utilisateur actuel.
     */
    private fun saveFCMToken() {
        firebaseAuth.currentUser?.let { user ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("MainActivity", "La récupération du jeton FCM a échoué", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("MainActivity", "Jeton FCM de l'appareil : $token")
                lifecycleScope.launch {
                    val result = userRepository.updateUserFCMToken(user.uid, token)
                    if (result is Resource.Success) {
                        Log.d("MainActivity", "Jeton FCM sauvegardé avec succès pour l'utilisateur ${user.uid}")
                    } else {
                        Log.e("MainActivity", "Échec de la sauvegarde du jeton FCM: ${result.message}")
                    }
                }
            }
        } ?: run {
            Log.d("MainActivity", "Aucun utilisateur connecté, impossible de sauvegarder le jeton FCM.")
        }
    }

    /**
     * Gère la navigation de l'application en fonction des données de l'Intent,
     * notamment celles provenant des notifications FCM.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        intent?.extras?.let { extras ->
            val monthlyReadingId = extras.getString(MyFirebaseMessagingService.MONTHLY_READING_ID_KEY)
            val notificationType = extras.getString(MyFirebaseMessagingService.NOTIFICATION_TYPE_KEY)

            Log.d("MainActivity", "Notification Intent received: monthlyReadingId=$monthlyReadingId, notificationType=$notificationType")
            navController.navigate(R.id.navigation_readings)

            intent.replaceExtras(Bundle())
        }
    }
}