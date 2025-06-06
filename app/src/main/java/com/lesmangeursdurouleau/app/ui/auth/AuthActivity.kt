package com.lesmangeursdurouleau.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.MainActivity
import com.lesmangeursdurouleau.app.databinding.ActivityAuthBinding
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

@AndroidEntryPoint // ANNOTATION AJOUTÉE
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("AuthActivity", "onCreate")
        firebaseAuth = FirebaseAuth.getInstance()
    }

    override fun onStart() {
        super.onStart()
        Log.d("AuthActivity", "onStart - Tentative de vérification de l'utilisateur")
        val currentUser = firebaseAuth.currentUser
        Log.d("AuthActivity", "onStart - currentUser: $currentUser")

        if (currentUser != null) {
            Log.d("AuthActivity", "onStart - Utilisateur trouvé ($currentUser.uid), navigation vers MainActivity")
            navigateToMainActivity()
        } else {
            Log.d("AuthActivity", "onStart - Aucun utilisateur connecté, reste sur AuthActivity")
        }
    }

    private fun navigateToMainActivity() {
        if (isFinishing || isChangingConfigurations) {
            Log.d("AuthActivity", "navigateToMainActivity - Activité en cours de fermeture, navigation annulée.")
            return
        }
        Log.d("AuthActivity", "navigateToMainActivity - Lancement de MainActivity")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}