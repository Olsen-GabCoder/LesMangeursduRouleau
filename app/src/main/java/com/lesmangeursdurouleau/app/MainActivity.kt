package com.lesmangeursdurouleau.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
// import androidx.navigation.ui.AppBarConfiguration // Si décommenté plus tard
// import androidx.navigation.ui.setupActionBarWithNavController // Si décommenté plus tard
import androidx.navigation.ui.setupWithNavController
import com.lesmangeursdurouleau.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

@AndroidEntryPoint // ANNOTATION AJOUTÉE
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_main) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)

        // Optionnel : Configurer l'AppBar (Toolbar)
        // setSupportActionBar(binding.toolbar)
        // val appBarConfiguration = AppBarConfiguration(
        //     setOf(
        //         R.id.navigation_dashboard, R.id.navigation_readings,
        //         R.id.navigation_meetings, R.id.navigation_members_profile // Ajusté si tu as renommé l'ID
        //     )
        // )
        // setupActionBarWithNavController(navController, appBarConfiguration)
    }

    // override fun onSupportNavigateUp(): Boolean {
    //     return navController.navigateUp() || super.onSupportNavigateUp()
    // }
}