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
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.databinding.ActivityMainBinding
import com.lesmangeursdurouleau.app.notifications.MyFirebaseMessagingService
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject
    lateinit var userProfileRepository: UserProfileRepository
    @Inject
    lateinit var firebaseAuth: FirebaseAuth

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

        binding.bottomNavigationView.setupWithNavController(navController)

        askNotificationPermission()
        saveFCMToken()
        handleNotificationIntent(intent)
    }

    // NOUVELLES FONCTIONS DE GESTION DU CYCLE DE VIE
    override fun onResume() {
        super.onResume()
        // L'utilisateur est considéré comme "en ligne" lorsque l'application est au premier plan.
        updateUserStatus(true)
    }

    override fun onPause() {
        super.onPause()
        // L'utilisateur est considéré comme "hors ligne" lorsque l'application quitte le premier plan.
        updateUserStatus(false)
    }

    private fun updateUserStatus(isOnline: Boolean) {
        firebaseAuth.currentUser?.uid?.let { userId ->
            // Lancer la coroutine sur le thread IO pour ne pas bloquer le thread principal.
            lifecycleScope.launch(Dispatchers.IO) {
                userProfileRepository.updateUserPresence(userId, isOnline)
            }
        }
    }
    // FIN DES NOUVELLES FONCTIONS

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("MainActivity", "Permission POST_NOTIFICATIONS déjà accordée.")
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                Log.d("MainActivity", "Explication nécessaire pour la permission POST_NOTIFICATIONS.")
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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
                    val result = userProfileRepository.updateUserFCMToken(user.uid, token)
                    if (result is Resource.Success) {
                        Log.d("MainActivity", "Jeton FCM sauvegardé avec succès pour l'utilisateur ${user.uid}")
                    } else if (result is Resource.Error){
                        Log.e("MainActivity", "Échec de la sauvegarde du jeton FCM: ${result.message}")
                    }
                }
            }
        } ?: run {
            Log.d("MainActivity", "Aucun utilisateur connecté, impossible de sauvegarder le jeton FCM.")
        }
    }

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