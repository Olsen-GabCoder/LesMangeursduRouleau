package com.lesmangeursdurouleau.app.ui.readings.addedit

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.databinding.FragmentAddEditMonthlyReadingBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AddEditMonthlyReadingFragment : Fragment() {

    private var _binding: FragmentAddEditMonthlyReadingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditMonthlyReadingViewModel by viewModels()
    private val args: AddEditMonthlyReadingFragmentArgs by navArgs()

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private lateinit var bookDropdownAdapter: ArrayAdapter<String>
    private lateinit var phaseStatusAdapter: ArrayAdapter<String> // NOUVEAU: Adaptateur pour les statuts de phase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditMonthlyReadingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbarAddEditMonthlyReading)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.title = args.title

        setupBookInputFields()
        setupDatePickers()
        setupPhaseStatusSpinners() // NOUVEAU: Appel à la fonction de setup des Spinners de statut
        setupListeners()
        setupObservers()

        val monthlyReadingId = args.monthlyReadingId
        if (monthlyReadingId != null) {
            Log.d("AddEditMonthlyReading", "Editing MonthlyReading with ID: $monthlyReadingId")
            viewModel.loadMonthlyReading(monthlyReadingId)
            (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.edit_monthly_reading_title)
        } else {
            Log.d("AddEditMonthlyReading", "Adding new MonthlyReading.")
            // En mode ajout, les champs année et mois sont initialisés par défaut
            val currentCalendar = Calendar.getInstance()
            binding.etYear.setText(currentCalendar.get(Calendar.YEAR).toString())
            binding.etMonth.setText(SimpleDateFormat("MMMM", Locale.getDefault()).format(currentCalendar.time))
            viewModel.setYearMonth(currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH) + 1)
            // Le formulaire doit être activé en mode ajout, car il n'y a pas de lecture à charger
            setFormEnabled(true)
            binding.progressBarAddEditMonthlyReading.visibility = View.GONE
            // Initialiser les statuts par défaut pour l'ajout
            viewModel.setAnalysisStatus(Phase.STATUS_PLANIFIED)
            viewModel.setDebateStatus(Phase.STATUS_PLANIFIED)
            // Mettre à jour les dropdowns pour afficher le statut par défaut
            binding.actvAnalysisStatus.setText(getString(R.string.status_planified), false)
            binding.actvDebateStatus.setText(getString(R.string.status_planified), false)
        }
    }

    private fun setupBookInputFields() {
        bookDropdownAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvSelectBookAutocomplete.setAdapter(bookDropdownAdapter)

        binding.actvSelectBookAutocomplete.setOnItemClickListener { parent, _, position, _ ->
            val selectedBookTitle = parent.getItemAtPosition(position).toString()
            val selectedBookFromDropdown = (viewModel.allBooks.value as? Resource.Success)?.data?.find { it.title == selectedBookTitle }

            selectedBookFromDropdown?.let { book ->
                // Mise à jour des StateFlows du ViewModel pour tous les champs du livre
                viewModel.setSelectedBookId(book.id)
                viewModel.setBookTitle(book.title)
                viewModel.setBookAuthor(book.author)
                viewModel.setBookSynopsis(book.synopsis)
                viewModel.setBookCoverImageUrl(book.coverImageUrl)

                // Pré-remplir les EditTexts du formulaire
                binding.etBookTitle.setText(book.title)
                binding.etBookAuthor.setText(book.author)
                binding.etBookSynopsis.setText(book.synopsis)
                binding.etBookCoverUrl.setText(book.coverImageUrl)
            }
            Log.d("AddEditMonthlyReading", "Selected book from dropdown: ${selectedBookFromDropdown?.title} (ID: ${selectedBookFromDropdown?.id})")
        }

        binding.actvSelectBookAutocomplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Si l'utilisateur efface le texte de l'autocomplete ou commence à taper un nouveau titre,
                // on réinitialise l'ID du livre sélectionné dans le ViewModel.
                // Cela indique qu'on ne se réfère plus à un livre existant par son ID.
                if (s.isNullOrBlank() || (viewModel.selectedBookId.value != null && s.toString() != (viewModel.allBooks.value as? Resource.Success)?.data?.find { it.id == viewModel.selectedBookId.value }?.title)) {
                    viewModel.setSelectedBookId(null)
                    // Optionnel : Effacer aussi les autres champs du livre si le titre est effacé.
                    // binding.etBookAuthor.setText("")
                    // binding.etBookSynopsis.setText("")
                    // binding.etBookCoverUrl.setText("")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.tilSelectBookAutocomplete.setEndIconOnClickListener { binding.actvSelectBookAutocomplete.showDropDown() }
    }

    private fun setupDatePickers() {
        binding.etAnalysisDate.setOnClickListener {
            showDatePicker(binding.etAnalysisDate) { year, month, dayOfMonth ->
                val calendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                binding.etAnalysisDate.setText(dateFormatter.format(calendar.time))
                viewModel.setAnalysisDate(calendar.time)
            }
        }

        binding.etDebateDate.setOnClickListener {
            showDatePicker(binding.etDebateDate) { year, month, dayOfMonth ->
                val calendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                binding.etDebateDate.setText(dateFormatter.format(calendar.time))
                viewModel.setDebateDate(calendar.time)
            }
        }

        binding.tilAnalysisDate.setEndIconOnClickListener {
            binding.etAnalysisDate.performClick()
        }
        binding.tilDebateDate.setEndIconOnClickListener {
            binding.etDebateDate.performClick()
        }
    }

    // NOUVEAU: Fonction pour configurer les Spinners de statut
    private fun setupPhaseStatusSpinners() {
        val statusOptions = arrayOf(
            getString(R.string.status_planified),
            getString(R.string.status_in_progress),
            getString(R.string.status_completed)
        )
        // Map pour convertir le texte affiché en constante de statut (pour le ViewModel)
        val statusTextToConstantMap = mapOf(
            getString(R.string.status_planified) to Phase.STATUS_PLANIFIED,
            getString(R.string.status_in_progress) to Phase.STATUS_IN_PROGRESS,
            getString(R.string.status_completed) to Phase.STATUS_COMPLETED
        )

        phaseStatusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statusOptions)

        binding.actvAnalysisStatus.setAdapter(phaseStatusAdapter)
        binding.actvAnalysisStatus.setOnItemClickListener { parent, _, position, _ ->
            val selectedStatusText = parent.getItemAtPosition(position).toString()
            val statusConstant = statusTextToConstantMap[selectedStatusText]
            if (statusConstant != null) {
                viewModel.setAnalysisStatus(statusConstant)
                Log.d("AddEditMonthlyReading", "Analysis status selected: $selectedStatusText -> $statusConstant")
            } else {
                Log.w("AddEditMonthlyReading", "Invalid analysis status selected: $selectedStatusText")
            }
        }
        // Permet d'ouvrir le dropdown en cliquant sur l'icône de fin
        binding.tilAnalysisStatus.setEndIconOnClickListener {
            binding.actvAnalysisStatus.showDropDown()
        }

        binding.actvDebateStatus.setAdapter(phaseStatusAdapter)
        binding.actvDebateStatus.setOnItemClickListener { parent, _, position, _ ->
            val selectedStatusText = parent.getItemAtPosition(position).toString()
            val statusConstant = statusTextToConstantMap[selectedStatusText]
            if (statusConstant != null) {
                viewModel.setDebateStatus(statusConstant)
                Log.d("AddEditMonthlyReading", "Debate status selected: $selectedStatusText -> $statusConstant")
            } else {
                Log.w("AddEditMonthlyReading", "Invalid debate status selected: $selectedStatusText")
            }
        }
        // Permet d'ouvrir le dropdown en cliquant sur l'icône de fin
        binding.tilDebateStatus.setEndIconOnClickListener {
            binding.actvDebateStatus.showDropDown()
        }
    }

    private fun showDatePicker(targetEditText: View, onDateSelected: (Int, Int, Int) -> Unit) {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        val dpd = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            onDateSelected(selectedYear, selectedMonth, selectedDay)
        }, year, month, day)
        dpd.show()
    }

    private fun setupListeners() {
        binding.btnSaveMonthlyReading.setOnClickListener {
            validateAndSave()
        }
    }

    private fun validateAndSave() {
        val bookTitle = binding.etBookTitle.text.toString().trim()
        val bookAuthor = binding.etBookAuthor.text.toString().trim()
        val bookSynopsis = binding.etBookSynopsis.text.toString().trim().ifEmpty { null }
        val bookCoverUrl = binding.etBookCoverUrl.text.toString().trim().ifEmpty { null }

        val analysisDate = viewModel.analysisDate.value
        val debateDate = viewModel.debateDate.value
        val customDescription = binding.etCustomDescription.text.toString().trim().ifEmpty { null }
        val analysisLink = binding.etAnalysisLink.text.toString().trim().ifEmpty { null }
        val debateLink = binding.etDebateLink.text.toString().trim().ifEmpty { null }
        val year = binding.etYear.text.toString().toIntOrNull() ?: 0
        val month = viewModel.month.value

        binding.tilBookTitle.error = null
        binding.tilBookAuthor.error = null
        binding.tilAnalysisDate.error = null
        binding.tilDebateDate.error = null

        var isValid = true

        if (bookTitle.isEmpty()) {
            binding.tilBookTitle.error = getString(R.string.error_book_title_required)
            isValid = false
        }
        if (bookAuthor.isEmpty()) {
            binding.tilBookAuthor.error = getString(R.string.error_book_author_required)
            isValid = false
        }

        if (analysisDate == null) {
            binding.tilAnalysisDate.error = getString(R.string.error_date_required)
            isValid = false
        }
        if (debateDate == null) {
            binding.tilDebateDate.error = getString(R.string.error_date_required)
            isValid = false
        }
        if (analysisDate != null && debateDate != null && analysisDate.after(debateDate)) {
            binding.tilDebateDate.error = getString(R.string.error_invalid_date_order)
            isValid = false
        }

        if (isValid) {
            val bookToSave = Book(
                id = viewModel.selectedBookId.value ?: "", // Cet ID sera ignoré par addBook si vide, ou utilisé par updateBook
                title = bookTitle,
                author = bookAuthor,
                synopsis = bookSynopsis,
                coverImageUrl = bookCoverUrl
            )

            if (args.monthlyReadingId == null) {
                viewModel.addMonthlyReading(
                    book = bookToSave,
                    year = year,
                    month = month,
                    analysisDate = analysisDate!!,
                    analysisMeetingLink = analysisLink,
                    debateDate = debateDate!!,
                    debateMeetingLink = debateLink,
                    customDescription = customDescription
                )
            } else {
                viewModel.updateMonthlyReading(
                    monthlyReadingId = args.monthlyReadingId!!,
                    book = bookToSave,
                    year = year,
                    month = month,
                    analysisDate = analysisDate!!,
                    analysisMeetingLink = analysisLink,
                    debateDate = debateDate!!,
                    debateMeetingLink = debateLink,
                    customDescription = customDescription
                )
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 1. Observer pour la liste de tous les livres (pour l'AutoCompleteTextView)
                launch {
                    viewModel.allBooks.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                // Pas de barre de progression globale ici, on la gère avec le flow combiné
                                binding.actvSelectBookAutocomplete.setHint(R.string.loading_books_label)
                            }
                            is Resource.Success -> {
                                val books = resource.data ?: emptyList()
                                val bookTitles = books.map { it.title }
                                bookDropdownAdapter.clear()
                                bookDropdownAdapter.addAll(bookTitles)
                                bookDropdownAdapter.notifyDataSetChanged()
                                binding.actvSelectBookAutocomplete.setHint(R.string.search_existing_book_label)
                                Log.d("AddEditMonthlyReading", "Loaded ${books.size} books for dropdown.")
                            }
                            is Resource.Error -> {
                                Toast.makeText(requireContext(), resource.message ?: getString(R.string.error_loading_books), Toast.LENGTH_SHORT).show()
                                binding.actvSelectBookAutocomplete.setHint(R.string.error_loading_books_label)
                                Log.e("AddEditMonthlyReading", "Failed to load books for dropdown: ${resource.message}")
                                bookDropdownAdapter.clear()
                                bookDropdownAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }

                // 2. NOUVEL OBSERVER : Pour le MonthlyReading et son Book associé (en mode édition)
                // Ce flow garantit que les deux sont chargés avant de peupler le formulaire.
                // Il est important de n'observer ce flow QUE si nous sommes en mode édition (args.monthlyReadingId != null).
                // Sinon, en mode ajout, le formulaire est activé par défaut et nous n'avons pas besoin de cette logique de chargement.
                if (args.monthlyReadingId != null) {
                    launch {
                        viewModel.monthlyReadingAndBookForEdit.collectLatest { resource ->
                            when (resource) {
                                is Resource.Loading -> {
                                    binding.progressBarAddEditMonthlyReading.visibility = View.VISIBLE
                                    setFormEnabled(false)
                                }
                                is Resource.Success -> {
                                    binding.progressBarAddEditMonthlyReading.visibility = View.GONE
                                    setFormEnabled(true)
                                    // S'assurer que le monthlyReading est non-null pour le peuplement
                                    resource.data?.let { (monthlyReading, book) ->
                                        if (monthlyReading != null) {
                                            populateForm(monthlyReading, book) // Passe le livre aussi
                                            viewModel.setYearMonth(monthlyReading.year, monthlyReading.month)
                                            viewModel.setAnalysisDate(monthlyReading.analysisPhase.date)
                                            viewModel.setDebateDate(monthlyReading.debatePhase.date)
                                            // NOUVEAU: Mettre à jour la sélection des spinners de statut
                                            val analysisStatusText = when (monthlyReading.analysisPhase.status) {
                                                Phase.STATUS_PLANIFIED -> getString(R.string.status_planified)
                                                Phase.STATUS_IN_PROGRESS -> getString(R.string.status_in_progress)
                                                Phase.STATUS_COMPLETED -> getString(R.string.status_completed)
                                                else -> getString(R.string.status_planified) // Fallback
                                            }
                                            binding.actvAnalysisStatus.setText(analysisStatusText, false)
                                            viewModel.setAnalysisStatus(monthlyReading.analysisPhase.status)

                                            val debateStatusText = when (monthlyReading.debatePhase.status) {
                                                Phase.STATUS_PLANIFIED -> getString(R.string.status_planified)
                                                Phase.STATUS_IN_PROGRESS -> getString(R.string.status_in_progress)
                                                Phase.STATUS_COMPLETED -> getString(R.string.status_completed)
                                                else -> getString(R.string.status_planified) // Fallback
                                            }
                                            binding.actvDebateStatus.setText(debateStatusText, false)
                                            viewModel.setDebateStatus(monthlyReading.debatePhase.status)

                                        } else {
                                            // Ce cas signifie qu'un monthlyReadingId a été fourni, mais la lecture n'existe pas.
                                            Toast.makeText(requireContext(), "Lecture mensuelle non trouvée.", Toast.LENGTH_SHORT).show()
                                            findNavController().popBackStack()
                                        }
                                    } ?: run {
                                        // Cas où resource.data est null, mais le Resource.Success a été émis.
                                        // Cela ne devrait pas arriver avec la logique du ViewModel, mais c'est une sécurité.
                                        Toast.makeText(requireContext(), "Données de lecture mensuelle invalides.", Toast.LENGTH_SHORT).show()
                                        findNavController().popBackStack()
                                    }
                                }
                                is Resource.Error -> {
                                    binding.progressBarAddEditMonthlyReading.visibility = View.GONE
                                    setFormEnabled(true)
                                    Toast.makeText(requireContext(), "Erreur de chargement: ${resource.message}", Toast.LENGTH_LONG).show()
                                    Log.e("AddEditMonthlyReading", "Error loading monthly reading and book: ${resource.message}")
                                    findNavController().popBackStack()
                                }
                            }
                        }
                    }
                }


                // 3. Observer pour le résultat de l'ajout/mise à jour (reste inchangé)
                launch {
                    viewModel.saveResult.collectLatest { resource ->
                        // Ne réagit que si resource n'est PAS null (état "idle")
                        resource?.let {
                            when (it) {
                                is Resource.Loading -> {
                                    binding.progressBarAddEditMonthlyReading.visibility = View.VISIBLE
                                    setFormEnabled(false)
                                }
                                is Resource.Success -> {
                                    binding.progressBarAddEditMonthlyReading.visibility = View.GONE
                                    setFormEnabled(true)
                                    val successMessage = if (args.monthlyReadingId == null) {
                                        getString(R.string.success_adding_monthly_reading)
                                    } else {
                                        getString(R.string.success_updating_monthly_reading)
                                    }
                                    Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()
                                    findNavController().popBackStack()
                                }
                                is Resource.Error -> {
                                    binding.progressBarAddEditMonthlyReading.visibility = View.GONE
                                    setFormEnabled(true)
                                    val errorMessage = if (args.monthlyReadingId == null) {
                                        getString(R.string.error_adding_monthly_reading, it.message ?: "inconnu")
                                    } else {
                                        getString(R.string.error_updating_monthly_reading, it.message ?: "inconnu")
                                    }
                                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                                    Log.e("AddEditMonthlyReading", "Save error: ${it.message}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MODIFIÉ : Prend un objet MonthlyReading? en paramètre
    private fun populateForm(monthlyReading: MonthlyReading?, book: Book?) {
        // 1. Pré-remplir les champs du livre
        book?.let {
            binding.actvSelectBookAutocomplete.setText(it.title, false)
            binding.etBookTitle.setText(it.title)
            binding.etBookAuthor.setText(it.author)
            binding.etBookSynopsis.setText(it.synopsis)
            binding.etBookCoverUrl.setText(it.coverImageUrl)
            viewModel.setSelectedBookId(it.id) // IMPORTANT: Met à jour l'ID du livre dans le ViewModel
            viewModel.setBookTitle(it.title) // Met à jour les StateFlows du ViewModel avec les données chargées
            viewModel.setBookAuthor(it.author)
            viewModel.setBookSynopsis(it.synopsis)
            viewModel.setBookCoverImageUrl(it.coverImageUrl)
        } ?: run {
            // Si le livre n'est pas trouvé (par exemple, il a été supprimé), on efface les champs du livre
            // et log un avertissement. L'utilisateur pourra alors resélectionner ou créer un nouveau livre.
            Log.w("AddEditMonthlyReading", "Book with ID ${monthlyReading?.bookId} not found for monthly reading ${monthlyReading?.id}. Clearing book fields.")
            binding.actvSelectBookAutocomplete.setText("")
            binding.etBookTitle.setText("")
            binding.etBookAuthor.setText("")
            binding.etBookSynopsis.setText("")
            binding.etBookCoverUrl.setText("")
            viewModel.setSelectedBookId(null) // Indique qu'aucun livre existant n'est lié actuellement
            viewModel.setBookTitle("")
            viewModel.setBookAuthor("")
            viewModel.setBookSynopsis(null)
            viewModel.setBookCoverImageUrl(null)
            // Afficher le toast d'erreur de livre non trouvé UNIQUEMENT si nous sommes en mode édition
            if (args.monthlyReadingId != null) {
                Toast.makeText(requireContext(), getString(R.string.error_book_not_found), Toast.LENGTH_LONG).show()
            }
        }

        // 2. Pré-remplir les champs de la réunion mensuelle
        monthlyReading?.let { mr ->
            binding.etYear.setText(mr.year.toString())
            binding.etMonth.setText(SimpleDateFormat("MMMM", Locale.getDefault()).format(
                Calendar.getInstance().apply { set(Calendar.MONTH, mr.month - 1) }.time
            ))

            mr.analysisPhase.date?.let {
                binding.etAnalysisDate.setText(dateFormatter.format(it))
            }
            binding.etAnalysisLink.setText(mr.analysisPhase.meetingLink)
            // NOUVEAU: Définir le statut de la phase d'analyse
            val analysisStatusText = when (mr.analysisPhase.status) {
                Phase.STATUS_PLANIFIED -> getString(R.string.status_planified)
                Phase.STATUS_IN_PROGRESS -> getString(R.string.status_in_progress)
                Phase.STATUS_COMPLETED -> getString(R.string.status_completed)
                else -> getString(R.string.status_planified) // Fallback
            }
            binding.actvAnalysisStatus.setText(analysisStatusText, false)


            mr.debatePhase.date?.let {
                binding.etDebateDate.setText(dateFormatter.format(it))
            }
            binding.etDebateLink.setText(mr.debatePhase.meetingLink)
            // NOUVEAU: Définir le statut de la phase de débat
            val debateStatusText = when (mr.debatePhase.status) {
                Phase.STATUS_PLANIFIED -> getString(R.string.status_planified)
                Phase.STATUS_IN_PROGRESS -> getString(R.string.status_in_progress)
                Phase.STATUS_COMPLETED -> getString(R.string.status_completed)
                else -> getString(R.string.status_planified) // Fallback
            }
            binding.actvDebateStatus.setText(debateStatusText, false)

            binding.etCustomDescription.setText(mr.customDescription)
        } ?: run {
            // Si monthlyReading est null (par exemple, en mode ajout),
            // on s'assure que les champs de la réunion mensuelle sont vides ou réinitialisés.
            // L'année et le mois sont déjà définis par défaut pour le mode ajout dans onViewCreated.
            binding.etAnalysisDate.setText("")
            binding.etAnalysisLink.setText("")
            // Ne pas toucher au statut ici, il est déjà initialisé à PLANIFIED en onCreate
            binding.etDebateDate.setText("")
            binding.etDebateLink.setText("")
            // Ne pas toucher au statut ici, il est déjà initialisé à PLANIFIED en onCreate
            binding.etCustomDescription.setText("")
        }
    }


    private fun setFormEnabled(enabled: Boolean) {
        binding.tilSelectBookAutocomplete.isEnabled = enabled
        binding.tilBookTitle.isEnabled = enabled
        binding.tilBookAuthor.isEnabled = enabled
        binding.tilBookSynopsis.isEnabled = enabled
        binding.tilBookCoverUrl.isEnabled = enabled

        binding.tilAnalysisDate.isEnabled = enabled
        binding.tilAnalysisStatus.isEnabled = enabled // AJOUTÉ
        binding.tilAnalysisLink.isEnabled = enabled

        binding.tilDebateDate.isEnabled = enabled
        binding.tilDebateStatus.isEnabled = enabled // AJOUTÉ
        binding.tilDebateLink.isEnabled = enabled

        binding.tilCustomDescription.isEnabled = enabled
        binding.btnSaveMonthlyReading.isEnabled = enabled
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                findNavController().popBackStack()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}