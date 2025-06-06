package com.lesmangeursdurouleau.app.ui.readings.adapter

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.TextPaint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.databinding.ItemMonthlyReadingBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// DTO (Data Transfer Object) pour combiner MonthlyReading et Book
data class MonthlyReadingWithBook(
    val monthlyReading: MonthlyReading,
    val book: Book? // Le livre associé, null si non trouvé
)

class MonthlyReadingListAdapter(
    private val onItemEditClicked: (MonthlyReadingWithBook) -> Unit // Callback pour le bouton d'édition
) : ListAdapter<MonthlyReadingWithBook, MonthlyReadingListAdapter.MonthlyReadingViewHolder>(MonthlyReadingDiffCallback()) {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthlyReadingViewHolder {
        val binding = ItemMonthlyReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MonthlyReadingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MonthlyReadingViewHolder, position: Int) {
        val monthlyReadingWithBook = getItem(position)
        holder.bind(monthlyReadingWithBook, onItemEditClicked, dateFormatter)
    }

    inner class MonthlyReadingViewHolder(private val binding: ItemMonthlyReadingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            data: MonthlyReadingWithBook,
            onItemEditClicked: (MonthlyReadingWithBook) -> Unit,
            dateFormatter: SimpleDateFormat
        ) {
            val monthlyReading = data.monthlyReading
            val book = data.book

            binding.tvMonthlyReadingTitle.text = book?.title ?: itemView.context.getString(R.string.unknown_book_title)
            binding.tvMonthlyReadingAuthor.text = book?.author ?: itemView.context.getString(R.string.unknown_author)
            binding.tvMonthlyReadingDescription.text = monthlyReading.customDescription?.ifEmpty { null }
                ?: itemView.context.getString(R.string.no_custom_description)

            if (!book?.coverImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(book?.coverImageUrl)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder_error)
                    .into(binding.ivMonthlyReadingCover)
            } else {
                binding.ivMonthlyReadingCover.setImageResource(R.drawable.ic_book_placeholder)
            }

            // Affichage et logique des phases (statut dynamique et lien)
            bindPhaseDetails(
                monthlyReading.analysisPhase,
                binding.tvAnalysisDate,
                binding.tvAnalysisStatus,
                binding.tvAnalysisLink,
                dateFormatter,
                isDebatePhase = false // Indique que c'est la phase d'analyse
            )
            bindPhaseDetails(
                monthlyReading.debatePhase,
                binding.tvDebateDate,
                binding.tvDebateStatus,
                binding.tvDebateLink,
                dateFormatter,
                isDebatePhase = true // Indique que c'est la phase de débat
            )

            // Mise à jour des barres de progression des phases
            updatePhaseProgressBars(monthlyReading, dateFormatter)

            // Listener pour le nouveau bouton d'édition (ImageButton)
            binding.btnEditMonthlyReading.setOnClickListener {
                onItemEditClicked(data)
            }

            // Accessibilité pour le bouton d'édition
            binding.btnEditMonthlyReading.contentDescription = itemView.context.getString(R.string.edit_monthly_reading_button_description, book?.title ?: "ce livre")
        }

        // Renommée pour être plus descriptive : gère les détails (date, statut, lien) d'une phase
        private fun bindPhaseDetails(
            phase: Phase,
            tvDate: TextView,
            tvStatus: TextView,
            tvLink: TextView,
            dateFormatter: SimpleDateFormat,
            isDebatePhase: Boolean // Ajouté pour différencier le comportement des liens
        ) {
            // Affichage de la date de la phase
            phase.date?.let {
                tvDate.text = dateFormatter.format(it)
            } ?: run {
                tvDate.text = itemView.context.getString(R.string.date_not_available)
            }

            // --- Logique du statut dynamique (Point 2 - MODIFIÉ) ---
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val phaseDateWithoutTime = phase.date?.let {
                Calendar.getInstance().apply {
                    time = it
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
            }

            // Définition du statut affiché basé sur la logique hybride
            val displayStatus: String = when (phase.status) {
                Phase.STATUS_COMPLETED -> Phase.STATUS_COMPLETED // Priorité au statut "Terminée" enregistré
                Phase.STATUS_IN_PROGRESS -> Phase.STATUS_IN_PROGRESS // Priorité au statut "En cours" enregistré
                Phase.STATUS_PLANIFIED -> { // Si enregistré comme Planifié, on utilise la logique date-based
                    if (phaseDateWithoutTime == null) {
                        Phase.STATUS_PLANIFIED
                    } else if (phaseDateWithoutTime.before(today)) {
                        Phase.STATUS_COMPLETED // La date est passée, donc on la considère complétée si elle était planifiée
                    } else if (phaseDateWithoutTime.equals(today)) {
                        Phase.STATUS_IN_PROGRESS // C'est aujourd'hui, donc en cours
                    } else { // phaseDateWithoutTime.after(today)
                        Phase.STATUS_PLANIFIED // Toujours planifiée si la date est à venir
                    }
                }
                else -> { // Cas par défaut si le statut est inconnu ou non spécifié
                    if (phaseDateWithoutTime == null) {
                        Phase.STATUS_PLANIFIED
                    } else if (phaseDateWithoutTime.before(today)) {
                        Phase.STATUS_COMPLETED
                    } else if (phaseDateWithoutTime.equals(today)) {
                        Phase.STATUS_IN_PROGRESS
                    } else {
                        Phase.STATUS_PLANIFIED
                    }
                }
            }

            // Affichage du texte du statut
            tvStatus.text = when (displayStatus) {
                Phase.STATUS_PLANIFIED -> itemView.context.getString(R.string.status_planified)
                Phase.STATUS_IN_PROGRESS -> itemView.context.getString(R.string.status_in_progress)
                Phase.STATUS_COMPLETED -> itemView.context.getString(R.string.status_completed)
                else -> itemView.context.getString(R.string.status_unknown)
            }

            // Définition de la couleur du statut
            val statusColor = when (displayStatus) {
                Phase.STATUS_PLANIFIED -> ContextCompat.getColor(itemView.context, R.color.text_secondary) // Neutre
                Phase.STATUS_IN_PROGRESS -> ContextCompat.getColor(itemView.context, R.color.primary_accent) // Accentuation
                Phase.STATUS_COMPLETED -> ContextCompat.getColor(itemView.context, R.color.primary_green) // Succès
                else -> Color.BLACK
            }
            tvStatus.setTextColor(statusColor)

            // --- Logique de verrouillage du lien (Point 2 - MODIFIÉ) ---
            // Le lien est verrouillé si la phase est COMPLETED (par statut ou date passée)
            val isLinkLocked = displayStatus == Phase.STATUS_COMPLETED

            if (!phase.meetingLink.isNullOrBlank()) {
                tvLink.visibility = View.VISIBLE
                tvLink.isClickable = true

                if (isLinkLocked) {
                    // Visuel pour un lien verrouillé (grisé, barré, icône cadenas)
                    tvLink.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                    tvLink.paintFlags = tvLink.paintFlags or TextPaint.STRIKE_THRU_TEXT_FLAG
                    tvLink.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        ContextCompat.getDrawable(itemView.context, R.drawable.ic_lock),
                        null, null, null
                    )
                    tvLink.text = itemView.context.getString(R.string.meeting_link_text_locked)

                    // Comportement au clic pour un lien verrouillé : afficher un Toast spécifique
                    tvLink.setOnClickListener {
                        val message = if (phase.status == Phase.STATUS_COMPLETED) { // Si explicitement COMPLETED dans les données
                            val completedDateText = phase.date?.let { dateFormatter.format(it) } ?: itemView.context.getString(R.string.date_not_available)
                            itemView.context.getString(R.string.phase_completed_toast_message, completedDateText)
                        } else { // Si la date est juste passée ou calculée comme COMPLETED
                            val passedDateText = phase.date?.let { dateFormatter.format(it) } ?: itemView.context.getString(R.string.date_not_available)
                            itemView.context.getString(R.string.phase_date_passed_toast_message, passedDateText)
                        }
                        Toast.makeText(itemView.context, message, Toast.LENGTH_SHORT).show()
                        Log.d("MonthlyReadingAdapter", "Clicked on locked link. Display Status: $displayStatus, Stored Status: ${phase.status}")
                    }
                } else {
                    // Visuel pour un lien actif (couleur normale, non barré, icône de lien)
                    tvLink.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary_accent))
                    tvLink.paintFlags = tvLink.paintFlags and TextPaint.STRIKE_THRU_TEXT_FLAG.inv() // Supprimer le texte barré
                    tvLink.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        ContextCompat.getDrawable(itemView.context, R.drawable.ic_link),
                        null, null, null
                    )
                    tvLink.text = itemView.context.getString(R.string.meeting_link_text)

                    // Comportement au clic pour un lien actif : ouvrir le lien
                    tvLink.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(phase.meetingLink))
                            itemView.context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(itemView.context, itemView.context.getString(R.string.error_opening_link), Toast.LENGTH_SHORT).show()
                            Log.e("MonthlyReadingAdapter", "Could not open link: ${phase.meetingLink}", e)
                        }
                    }
                }
            } else {
                tvLink.visibility = View.GONE
            }
        }

        // Nouvelle fonction pour mettre à jour les barres de progression des phases
        private fun updatePhaseProgressBars(monthlyReading: MonthlyReading, dateFormatter: SimpleDateFormat) {
            val now = Date()

            // --- Phase Analyse Progress ---
            // Début de la phase d'analyse : le 1er jour du mois de la lecture mensuelle
            val analysisStartCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, monthlyReading.year)
                set(Calendar.MONTH, monthlyReading.month - 1) // Calendar.MONTH est 0-indexé
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val analysisStartDate = analysisStartCal.time
            val analysisEndDate = monthlyReading.analysisPhase.date

            // MODIFIÉ : Utiliser le statut enregistré pour prioriser le calcul de la progression
            val analysisProgressStatus = monthlyReading.analysisPhase.status

            calculateAndDisplayPhaseProgressBar(
                startDate = analysisStartDate,
                endDate = analysisEndDate,
                progressBar = binding.progressBarAnalysis,
                tvPercentage = binding.tvAnalysisPercentage,
                colorResId = R.color.primary_green, // Vert pour l'analyse
                phaseName = "Analyse",
                dateFormatter = dateFormatter,
                currentPhaseStatus = analysisProgressStatus // Passe le statut enregistré
            )

            // --- Phase Débat Progress ---
            // Début de la phase de débat : la date de fin de la phase d'analyse (si définie)
            // Sinon, le 1er jour du mois de la lecture mensuelle + durée de l'analyse (par exemple)
            // Pour l'instant, on prend la date de fin de l'analyse comme début du débat.
            val debateStartDate = monthlyReading.analysisPhase.date
            val debateEndDate = monthlyReading.debatePhase.date

            // MODIFIÉ : Utiliser le statut enregistré pour prioriser le calcul de la progression
            val debateProgressStatus = monthlyReading.debatePhase.status

            calculateAndDisplayPhaseProgressBar(
                startDate = debateStartDate,
                endDate = debateEndDate,
                progressBar = binding.progressBarDebate,
                tvPercentage = binding.tvDebatePercentage,
                colorResId = R.color.error_color, // Rouge pour le débat
                phaseName = "Débat",
                dateFormatter = dateFormatter,
                currentPhaseStatus = debateProgressStatus // Passe le statut enregistré
            )
        }

        // Fonction d'aide générique pour calculer et afficher une barre de progression de phase
        // MODIFIÉ : Ajout du paramètre currentPhaseStatus
        private fun calculateAndDisplayPhaseProgressBar(
            startDate: Date?,
            endDate: Date?,
            progressBar: ProgressBar,
            tvPercentage: TextView,
            @ColorRes colorResId: Int,
            phaseName: String,
            dateFormatter: SimpleDateFormat,
            currentPhaseStatus: String // Statut enregistré dans Firestore
        ) {
            val now = Date()
            val progress: Int
            val percentageText: String
            var progressColorResId = colorResId // Par défaut la couleur de la phase

            when (currentPhaseStatus) {
                Phase.STATUS_COMPLETED -> {
                    // Si le statut est COMPLETED dans Firestore, la progression est 100%
                    progress = 100
                    percentageText = itemView.context.getString(R.string.progress_completed_text)
                    progressColorResId = R.color.primary_green // Force le vert pour terminé
                }
                Phase.STATUS_IN_PROGRESS -> {
                    // Si le statut est IN_PROGRESS dans Firestore, calcule la progression basée sur les dates si possible, sinon un minimum.
                    if (startDate == null || endDate == null || startDate.after(endDate)) {
                        progress = 0 // Impossible de calculer, on met 0 ou un statut spécifique
                        percentageText = itemView.context.getString(R.string.progress_not_started_text) // Ou un message "En cours"
                        progressColorResId = R.color.primary_accent // Couleur accent pour en cours
                    } else {
                        val totalDurationMillis = endDate.time - startDate.time
                        val elapsedDurationMillis = now.time - startDate.time
                        progress = ((elapsedDurationMillis.toFloat() / totalDurationMillis) * 100).toInt().coerceIn(0, 100)
                        percentageText = "$progress%"
                        progressColorResId = colorResId // Couleur de la phase pour en cours
                    }
                }
                Phase.STATUS_PLANIFIED -> {
                    // Si le statut est PLANIFIED dans Firestore, la progression est 0% tant que la date n'est pas passée.
                    if (startDate == null || now.before(startDate)) {
                        progress = 0
                        percentageText = itemView.context.getString(R.string.progress_not_started_text)
                        progressColorResId = R.color.text_secondary // Gris pour planifié
                    } else if (endDate == null || now.after(endDate)) {
                        // Si la date de début est passée, mais que le statut est "planifié",
                        // et la date de fin est passée, on considère terminée.
                        progress = 100
                        percentageText = itemView.context.getString(R.string.progress_completed_text)
                        progressColorResId = R.color.primary_green // Vert pour terminé
                    } else {
                        // Sinon, c'est en cours (transition de planifié à en cours par la date)
                        val totalDurationMillis = endDate.time - startDate.time
                        val elapsedDurationMillis = now.time - startDate.time
                        progress = ((elapsedDurationMillis.toFloat() / totalDurationMillis) * 100).toInt().coerceIn(0, 100)
                        percentageText = "$progress%"
                        progressColorResId = colorResId // Couleur de la phase pour en cours
                    }
                }
                else -> { // Cas d'un statut inconnu ou non spécifié
                    // Revertir à la logique basée sur la date pure
                    if (startDate == null || endDate == null || startDate.after(endDate)) {
                        progress = 0
                        percentageText = itemView.context.getString(R.string.progress_not_started_text)
                        progressColorResId = R.color.text_secondary
                    } else if (now.before(startDate)) {
                        progress = 0
                        percentageText = itemView.context.getString(R.string.progress_not_started_text)
                        progressColorResId = R.color.text_secondary
                    } else if (now.after(endDate)) {
                        progress = 100
                        percentageText = itemView.context.getString(R.string.progress_completed_text)
                        progressColorResId = colorResId
                    } else {
                        val totalDurationMillis = endDate.time - startDate.time
                        val elapsedDurationMillis = now.time - startDate.time
                        progress = ((elapsedDurationMillis.toFloat() / totalDurationMillis) * 100).toInt().coerceIn(0, 100)
                        percentageText = "$progress%"
                        progressColorResId = colorResId
                    }
                }
            }

            progressBar.visibility = View.VISIBLE
            tvPercentage.visibility = View.VISIBLE
            progressBar.progress = progress
            tvPercentage.text = percentageText
            progressBar.progressTintList = ContextCompat.getColorStateList(itemView.context, progressColorResId)

            Log.d("MonthlyReadingAdapter", "$phaseName Progress: $progress%, Stored Status: $currentPhaseStatus, Displayed: $percentageText, Start: ${startDate?.let { dateFormatter.format(it) }}, End: ${endDate?.let { dateFormatter.format(it) }}, Now: ${dateFormatter.format(now)}")
        }
    }

    class MonthlyReadingDiffCallback : DiffUtil.ItemCallback<MonthlyReadingWithBook>() {
        override fun areItemsTheSame(oldItem: MonthlyReadingWithBook, newItem: MonthlyReadingWithBook): Boolean {
            return oldItem.monthlyReading.id == newItem.monthlyReading.id
        }

        override fun areContentsTheSame(oldItem: MonthlyReadingWithBook, newItem: MonthlyReadingWithBook): Boolean {
            return oldItem == newItem
        }
    }
}