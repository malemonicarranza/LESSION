package com.example.app_lession.feature.sessions

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.app_lession.core.database.AppDatabase
import com.example.app_lession.core.model.EmgSample
import com.example.app_lession.core.session.EmgSession
import com.example.app_lession.core.session.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionViewModel(
    private val sessionRepository: SessionRepository,
    private val measurements: Flow<EmgSample>
) : ViewModel() {
    private val recordingMutex = Mutex()
    private var measurementLoadJob: Job? = null
    private val _sessions = MutableStateFlow<List<EmgSession>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _activeSession = MutableStateFlow<EmgSession?>(null)
    val activeSession = _activeSession.asStateFlow()

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing = _isInitializing.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    private val _selectedMeasurements = MutableStateFlow<List<EmgSample>>(emptyList())
    val selectedMeasurements = _selectedMeasurements.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.sessions
                .catch { error -> handleDatabaseError(error) }
                .collect { _sessions.value = it }
        }

        viewModelScope.launch {
            try {
                recordingMutex.withLock {
                    _activeSession.value = sessionRepository.getActiveSession()
                    _isInitializing.value = false
                }
                measurements.collect { sample ->
                    recordingMutex.withLock {
                        _activeSession.value?.let { active ->
                            sessionRepository.recordMeasurement(active.id, sample)
                        }
                    }
                }
            } catch (error: SQLiteException) {
                _isInitializing.value = false
                _errorMessage.value = "No se pudo guardar la medición local: ${error.message}"
            }
        }
    }

    fun startSession() {
        if (_isInitializing.value || _isBusy.value || _activeSession.value != null) return
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                recordingMutex.withLock {
                    if (_activeSession.value == null) {
                        _activeSession.value =
                            sessionRepository.startSession(System.currentTimeMillis())
                                ?: sessionRepository.getActiveSession()
                    }
                }
            } catch (error: SQLiteException) {
                _errorMessage.value = "No se pudo iniciar la sesión: ${error.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun finishSession() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                recordingMutex.withLock {
                    _activeSession.value?.let { active ->
                        sessionRepository.finishSession(active, System.currentTimeMillis())
                        _activeSession.value = null
                    }
                }
            } catch (error: SQLiteException) {
                _errorMessage.value = "No se pudo finalizar la sesión: ${error.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun openSession(sessionId: Long) {
        _selectedSessionId.value = sessionId
        _selectedMeasurements.value = emptyList()
        _errorMessage.value = null
        loadMeasurements(sessionId)
    }

    fun refreshSessionMeasurements() {
        _selectedSessionId.value?.let { sessionId ->
            loadMeasurements(sessionId)
        }
    }

    fun closeSession() {
        _selectedSessionId.value = null
        measurementLoadJob?.cancel()
        _selectedMeasurements.value = emptyList()
    }

    private fun handleDatabaseError(error: Throwable) {
        if (error is SQLiteException) {
            _errorMessage.value = "No se pudo leer el historial local: ${error.message}"
        } else {
            throw error
        }
    }

    private fun loadMeasurements(sessionId: Long) {
        measurementLoadJob?.cancel()
        measurementLoadJob = viewModelScope.launch {
            try {
                _selectedMeasurements.value = sessionRepository.measurements(sessionId)
            } catch (error: SQLiteException) {
                _errorMessage.value = "No se pudieron cargar las mediciones: ${error.message}"
            }
        }
    }

    class Factory(
        context: Context,
        measurements: Flow<EmgSample>
    ) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext
        private val measurements = measurements

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SessionViewModel::class.java))
            val repository = SessionRepository(
                AppDatabase.getInstance(applicationContext).sessionDao()
            )
            @Suppress("UNCHECKED_CAST")
            return SessionViewModel(repository, measurements) as T
        }
    }
}
