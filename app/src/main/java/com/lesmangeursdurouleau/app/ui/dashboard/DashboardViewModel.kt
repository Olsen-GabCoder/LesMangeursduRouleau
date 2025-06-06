package com.lesmangeursdurouleau.app.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.ClubEvent // Assure-toi que ClubEvent a les champs location et bookToDiscussId (nullable)
import com.lesmangeursdurouleau.app.data.model.Quote
import java.util.Calendar
// import java.util.Date // Pas directement utilisé ici si calendar.time est assigné

class DashboardViewModel : ViewModel() {

    // LiveData pour la citation du mois
    private val _quoteOfTheMonth = MutableLiveData<Quote>()
    val quoteOfTheMonth: LiveData<Quote> = _quoteOfTheMonth

    // LiveData pour le prochain événement
    private val _nextEvent = MutableLiveData<ClubEvent?>()
    val nextEvent: LiveData<ClubEvent?> = _nextEvent

    // LiveData pour la dernière œuvre analysée
    private val _lastAnalyzedBook = MutableLiveData<Book?>()
    val lastAnalyzedBook: LiveData<Book?> = _lastAnalyzedBook

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        // Citation factice
        _quoteOfTheMonth.value = Quote(
            text = "Lire, c'est boire et manger. L'esprit qui ne lit pas maigrit comme le corps qui ne mange pas.",
            author = "Victor Hugo"
        )

        // Prochain événement factice
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 7) // Dans 7 jours
        _nextEvent.value = ClubEvent(
            id = "evt123",
            title = "Analyse de 'Dune'",
            date = calendar.time,
            description = "Réunion en ligne via Zoom",
            location = "En ligne (Zoom)", // VALEUR AJOUTÉE
            bookToDiscussId = "book001"      // VALEUR AJOUTÉE (ou null si pas de livre spécifique)
        )
        // Pour tester le cas où il n'y a pas d'événement :
        // _nextEvent.value = null

        // Dernière œuvre analysée factice
        _lastAnalyzedBook.value = Book(
            id = "book456", // Peut-être utiliser un ID différent de celui de l'événement pour éviter confusion
            title = "Le Problème à trois corps",
            author = "Liu Cixin",
            synopsis = "Une épopée de science-fiction explorant le premier contact de l'humanité avec une civilisation extraterrestre."
        )
        // Pour tester le cas où il n'y a pas de livre :
        // _lastAnalyzedBook.value = null
    }

    // Méthodes pour les actions des boutons rapides (à implémenter plus tard)
    fun onJoinMeetingClicked() {
        // TODO: Logique pour rejoindre la réunion
    }

    fun onReadBookClicked() {
        // TODO: Logique pour lire l'œuvre
    }
}