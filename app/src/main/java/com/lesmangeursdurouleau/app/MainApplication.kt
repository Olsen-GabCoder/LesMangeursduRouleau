package com.lesmangeursdurouleau.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp // Annotation pour Hilt
class MainApplication : Application() { // Doit hériter de Application
    // Pour l'instant, cette classe peut rester vide.
    // Hilt s'occupe de la génération du code nécessaire.
    // Tu pourras y ajouter de la logique d'initialisation globale
    // pour ton application plus tard si besoin (ex: Timber, Stetho, etc.).

    override fun onCreate() {
        super.onCreate()
        // Tu peux ajouter des initialisations ici si besoin un jour
        // Par exemple: Timber.plant(Timber.DebugTree())
    }
}