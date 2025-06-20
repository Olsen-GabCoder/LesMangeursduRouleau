package com.lesmangeursdurouleau.app.ui.readings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepository
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
// MODIFIÉ: Import de UserProfileRepository et suppression de UserRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingWithBook
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

enum class ReadingsFilter {
    ALL,
    IN_PROGRESS,
    PLANNED,
    PAST
}

@HiltViewModel
class ReadingsViewModel @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository,
    private val getBooksUseCase: GetBooksUseCase,
    // MODIFIÉ: Remplacement de UserRepository par UserProfileRepository
    private val userProfileRepository: UserProfileRepository,
    private val firebaseAuth: FirebaseAuth,
    private val appConfigRepository: AppConfigRepository
) : ViewModel() {

    private companion object {
        private const val TAG = "ReadingsViewModel"
        private const val PERMISSION_LIFESPAN_MILLIS = 3 * 60 * 1000L // 3 minutes
    }

    private val _currentMonthYear = MutableStateFlow(Calendar.getInstance())
    val currentMonthYear: StateFlow<Calendar> = _currentMonthYear.asStateFlow()

    private val _currentFilter = MutableStateFlow(ReadingsFilter.ALL)
    val currentFilter: StateFlow<ReadingsFilter> = _currentFilter.asStateFlow()

    private val _booksMap = MutableStateFlow<Map<String, Book>>(emptyMap())

    private val _monthlyReadingsWithBooks = MutableStateFlow<Resource<List<MonthlyReadingWithBook>>>(Resource.Loading())
    val monthlyReadingsWithBooks: StateFlow<Resource<List<MonthlyReadingWithBook>>> = _monthlyReadingsWithBooks.asStateFlow()

    private val _canEditReadings = MutableStateFlow(false)
    val canEditReadings: StateFlow<Boolean> = _canEditReadings.asStateFlow()

    private val _requestPermissionRevalidation = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val requestPermissionRevalidation: SharedFlow<Boolean> = _requestPermissionRevalidation.asSharedFlow()

    private val _secretCodeLastUpdatedTimestamp = MutableStateFlow<Long?>(null)

    init {
        loadAllBooksIntoMap()
        setupAdminSecretCodeTimestampListener()
        setupUserEditPermissionListener()
        setupMonthlyReadingsFlow()
    }

    fun forcePermissionCheck() {
        Log.d(TAG, "FUNC forcePermissionCheck: Forcing a re-evaluation of user edit permissions.")
        firebaseAuth.currentUser?.uid?.let { uid ->
            viewModelScope.launch {
                // MODIFIÉ: Appel sur userProfileRepository
                userProfileRepository.getUserById(uid)
                    .catch { e ->
                        Log.e(TAG, "FUNC forcePermissionCheck: Error forcing user permission check: ${e.localizedMessage}", e)
                    }
                    .first()
            }
        } ?: Log.w(TAG, "FUNC forcePermissionCheck: Cannot force permission check: No user logged in.")
    }

    private fun setupAdminSecretCodeTimestampListener() {
        Log.d(TAG, "INIT setupAdminSecretCodeTimestampListener: Setting up listener for admin secret code timestamp.")
        viewModelScope.launch {
            appConfigRepository.getSecretCodeLastUpdatedTimestamp()
                .catch { e ->
                    Log.e(TAG, "ERROR setupAdminSecretCodeTimestampListener: Error listening for admin secret code timestamp: ${e.localizedMessage}", e)
                    _secretCodeLastUpdatedTimestamp.value = null
                }
                .collect { resource ->
                    val timestamp = (resource as? Resource.Success)?.data
                    _secretCodeLastUpdatedTimestamp.value = timestamp
                    Log.d(TAG, "UPDATE Admin secret code last updated timestamp: ${timestamp?.let { Date(it) }} (Raw: $timestamp)")
                }
        }
    }

    private fun setupUserEditPermissionListener() {
        Log.d(TAG, "INIT setupUserEditPermissionListener: Setting up combine listener for user and admin timestamps.")
        combine(
            // MODIFIÉ: Appel sur userProfileRepository
            userProfileRepository.getUserById(firebaseAuth.currentUser?.uid ?: "")
                .catch { emit(Resource.Error("User not found or error fetching user data")) },
            _secretCodeLastUpdatedTimestamp
        ) { userResource, adminTimestamp ->
            Log.d(TAG, "COMBINE BLOCK START: Evaluating user permission.")
            Log.d(TAG, "  User Resource: $userResource")
            Log.d(TAG, "  Admin Timestamp State: ${adminTimestamp?.let { Date(it) }} (Raw: $adminTimestamp)")

            val currentUser = (userResource as? Resource.Success)?.data
            val hasInitialPermission = currentUser?.canEditReadings ?: false
            val lastGrantedTimestamp = currentUser?.lastPermissionGrantedTimestamp
            val currentAdminSecretCodeTimestamp = adminTimestamp

            val now = System.currentTimeMillis()
            var isPermissionExpired = false
            var isPermissionInvalidatedByAdmin = false
            var shouldRequestRevalidation = false

            Log.d(TAG, "  Current User ID: ${currentUser?.uid ?: "N/A"}")
            Log.d(TAG, "  User hasInitialPermission (Firestore): $hasInitialPermission")
            Log.d(TAG, "  User lastGrantedTimestamp (Firestore): ${lastGrantedTimestamp?.let { Date(it) }} (Raw: $lastGrantedTimestamp)")
            Log.d(TAG, "  Current Time (now): ${Date(now)} (Raw: $now)")
            Log.d(TAG, "  Permission Lifespan (MS): $PERMISSION_LIFESPAN_MILLIS")

            if (currentUser == null) {
                Log.d(TAG, "  No current user found or error fetching user data. Permission set to FALSE.")
                _canEditReadings.value = false
            } else if (!hasInitialPermission) {
                Log.d(TAG, "  User does not have initial permission. Permission set to FALSE.")
                _canEditReadings.value = false
            } else {
                if (lastGrantedTimestamp == null) {
                    Log.w(TAG, "  User has canEditReadings=true but lastPermissionGrantedTimestamp is NULL. Forcing revalidation.")
                    shouldRequestRevalidation = true
                    _canEditReadings.value = false
                } else {
                    if ((now - lastGrantedTimestamp) > PERMISSION_LIFESPAN_MILLIS) {
                        isPermissionExpired = true
                        Log.d(TAG, "  Permission IS expired by time. (Now - LastGranted = ${now - lastGrantedTimestamp}ms)")
                    } else {
                        Log.d(TAG, "  Permission is NOT expired by time. (Remaining: ${PERMISSION_LIFESPAN_MILLIS - (now - lastGrantedTimestamp)}ms)")
                    }

                    if (currentAdminSecretCodeTimestamp != null && lastGrantedTimestamp < currentAdminSecretCodeTimestamp) {
                        isPermissionInvalidatedByAdmin = true
                        Log.d(TAG, "  Permission IS invalidated by admin. (LastGranted < AdminTimestamp: ${lastGrantedTimestamp} < ${currentAdminSecretCodeTimestamp})")
                    } else {
                        Log.d(TAG, "  Permission is NOT invalidated by admin. (AdminTimestamp: ${currentAdminSecretCodeTimestamp?.let { Date(it) } ?: "N/A"})")
                    }

                    if (isPermissionExpired || isPermissionInvalidatedByAdmin) {
                        Log.i(TAG, "  Final decision: Permission is NOT active due to expiration OR admin invalidation.")
                        shouldRequestRevalidation = true
                        _canEditReadings.value = false
                    } else {
                        Log.i(TAG, "  Final decision: Permission is ACTIVE and valid.")
                        _canEditReadings.value = true
                    }
                }
            }

            if (shouldRequestRevalidation) {
                Log.i(TAG, "  Emitting requestPermissionRevalidation event.")
                _requestPermissionRevalidation.emit(true)
            }
            Log.d(TAG, "COMBINE BLOCK END: _canEditReadings.value is now ${_canEditReadings.value}")
            Unit
        }.launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupMonthlyReadingsFlow() {
        viewModelScope.launch {
            combine(_currentMonthYear, _currentFilter) { calendar, filter ->
                Pair(calendar, filter)
            }
                .flatMapLatest { (calendar, filter) ->
                    Log.d(TAG, "FUNC setupMonthlyReadingsFlow: Fetching for month/year: ${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)} with filter: $filter")
                    if (filter == ReadingsFilter.ALL) {
                        monthlyReadingRepository.getMonthlyReadings(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1
                        )
                    } else {
                        monthlyReadingRepository.getAllMonthlyReadings()
                    }
                }
                .combine(_booksMap) { readingsResource, booksMap ->
                    when (readingsResource) {
                        is Resource.Loading -> Resource.Loading()
                        is Resource.Error -> Resource.Error(readingsResource.message ?: "Erreur inconnue lors du chargement des lectures.")
                        is Resource.Success -> {
                            val combinedList = readingsResource.data?.map { monthlyReading ->
                                MonthlyReadingWithBook(monthlyReading, booksMap[monthlyReading.bookId])
                            }?.filter { monthlyReadingWithBook ->
                                applyStatusFilter(monthlyReadingWithBook.monthlyReading, _currentFilter.value)
                            } ?: emptyList()
                            Resource.Success(combinedList)
                        }
                    }
                }
                .catch { e ->
                    Log.e(TAG, "ERROR setupMonthlyReadingsFlow: Exception in monthly readings flow", e)
                    _monthlyReadingsWithBooks.value = Resource.Error("Une erreur technique est survenue: ${e.localizedMessage}")
                }
                .collect { resource ->
                    _monthlyReadingsWithBooks.value = resource
                    if (resource is Resource.Success) {
                        Log.d(TAG, "SUCCESS setupMonthlyReadingsFlow: Loaded and filtered ${resource.data?.size} monthly readings.")
                    }
                }
        }
    }

    private fun loadAllBooksIntoMap() {
        Log.d(TAG, "FUNC loadAllBooksIntoMap: Loading all books into map.")
        viewModelScope.launch {
            getBooksUseCase()
                .catch { e ->
                    Log.e(TAG, "ERROR loadAllBooksIntoMap: Error loading all books for map", e)
                }
                .collect { resource ->
                    if (resource is Resource.Success) {
                        _booksMap.value = resource.data?.associateBy { it.id } ?: emptyMap()
                        Log.d(TAG, "SUCCESS loadAllBooksIntoMap: Books map updated with ${resource.data?.size} books.")
                    }
                }
        }
    }

    private fun applyStatusFilter(monthlyReading: MonthlyReading, filter: ReadingsFilter): Boolean {
        val now = Date()

        val isAnalysisPlanned = monthlyReading.analysisPhase.date != null && monthlyReading.analysisPhase.date.after(now) && monthlyReading.analysisPhase.status == Phase.STATUS_PLANIFIED
        val isDebatePlanned = monthlyReading.debatePhase.date != null && monthlyReading.debatePhase.date.after(now) && monthlyReading.debatePhase.status == Phase.STATUS_PLANIFIED

        val isAnalysisInProgress = monthlyReading.analysisPhase.date != null && !monthlyReading.analysisPhase.date.after(now) && monthlyReading.analysisPhase.status == Phase.STATUS_IN_PROGRESS
        val isDebateInProgress = monthlyReading.debatePhase.date != null && !monthlyReading.debatePhase.date.after(now) && monthlyReading.debatePhase.status == Phase.STATUS_IN_PROGRESS

        val isAnalysisCompleted = monthlyReading.analysisPhase.status == Phase.STATUS_COMPLETED
        val isDebateCompleted = monthlyReading.debatePhase.status == Phase.STATUS_COMPLETED

        val isCurrentlyInProgress = (isAnalysisInProgress || isDebateInProgress) ||
                (monthlyReading.analysisPhase.date != null && !monthlyReading.analysisPhase.date.after(now) &&
                        monthlyReading.debatePhase.date != null && monthlyReading.debatePhase.date.after(now) &&
                        monthlyReading.analysisPhase.status != Phase.STATUS_COMPLETED)

        val isCurrentlyPlanned = (isAnalysisPlanned || isDebatePlanned) && !isCurrentlyInProgress && !isAnalysisCompleted && !isDebateCompleted

        val isCurrentlyPast = isDebateCompleted || (monthlyReading.debatePhase.date != null && !monthlyReading.debatePhase.date.after(now))

        return when (filter) {
            ReadingsFilter.ALL -> true
            ReadingsFilter.IN_PROGRESS -> isCurrentlyInProgress
            ReadingsFilter.PLANNED -> isCurrentlyPlanned
            ReadingsFilter.PAST -> isCurrentlyPast
        }
    }

    fun goToPreviousMonth() {
        _currentMonthYear.value = _currentMonthYear.value.apply { add(Calendar.MONTH, -1) }
        Log.d(TAG, "FUNC goToPreviousMonth: Navigated to previous month: ${_currentMonthYear.value.get(Calendar.MONTH) + 1}/${_currentMonthYear.value.get(Calendar.YEAR)}")
    }

    fun goToNextMonth() {
        _currentMonthYear.value = _currentMonthYear.value.apply { add(Calendar.MONTH, 1) }
        Log.d(TAG, "FUNC goToNextMonth: Navigated to next month: ${_currentMonthYear.value.get(Calendar.MONTH) + 1}/${_currentMonthYear.value.get(Calendar.YEAR)}")
    }

    fun setFilter(filter: ReadingsFilter) {
        _currentFilter.value = filter
        Log.d(TAG, "FUNC setFilter: Filter changed to: $filter")
    }
}