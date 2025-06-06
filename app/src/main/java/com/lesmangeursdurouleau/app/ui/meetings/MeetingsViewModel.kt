package com.lesmangeursdurouleau.app.ui.meetings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lesmangeursdurouleau.app.data.model.ClubEvent
import java.util.Calendar
import java.util.Date

class MeetingsViewModel : ViewModel() {

    private val _meetings = MutableLiveData<List<ClubEvent>>()
    val meetings: LiveData<List<ClubEvent>> = _meetings

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadMeetings()
    }

    fun loadMeetings() {
        _isLoading.value = true
        _error.value = null

        // Données factices
        val calendar = Calendar.getInstance()
        val mockMeetings = mutableListOf<ClubEvent>()

        calendar.set(2024, Calendar.JULY, 20, 19, 30) // Année, Mois (0-11), Jour, Heure, Minute
        mockMeetings.add(
            ClubEvent(
                id = "meet001",
                title = "Analyse & Débat : Dune",
                date = calendar.time,
                location = "En ligne (Zoom)",
                description = "Discussion approfondie du premier tome de Dune.",
                bookToDiscussId = "book001"
            )
        )

        calendar.set(2024, Calendar.AUGUST, 10, 18, 0)
        mockMeetings.add(
            ClubEvent(
                id = "meet002",
                title = "Réunion thématique : Les Dystopies",
                date = calendar.time,
                location = "Café Littéraire 'Le Rouleau'",
                description = "Comparaison de différentes œuvres dystopiques."
            )
        )

        calendar.set(2024, Calendar.AUGUST, 24, 19, 0)
        mockMeetings.add(
            ClubEvent(
                id = "meet003",
                title = "Préparation prochaine lecture : Fondation",
                date = calendar.time,
                location = "Discord Club",
                description = "Questions et premières impressions avant la lecture de Fondation."
            )
        )

        _meetings.value = mockMeetings.sortedBy { it.date } // Trier par date
        _isLoading.value = false
    }
}