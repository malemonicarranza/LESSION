package com.example.app_lession.feature.connection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.app_lession.core.ble.BleConnectionManager
import com.example.app_lession.core.ble.BleConnectionState
import com.example.app_lession.core.model.EmgSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val bleConnectionManager: BleConnectionManager
) : ViewModel() {
    val connectionState = bleConnectionManager.connectionState
    val measurements = bleConnectionManager.measurements
    val latestMeasurement = bleConnectionManager.latestMeasurement

    private val _liveMeasurementState = MutableStateFlow(LiveMeasurementUiState())
    val liveMeasurementState = _liveMeasurementState.asStateFlow()
    private val receptionTimes = mutableListOf<Long>()

    init {
        viewModelScope.launch {
            var previousState = BleConnectionState.DISCONNECTED
            connectionState.collect { state ->
                if (state.state == BleConnectionState.CONNECTING &&
                    previousState != BleConnectionState.CONNECTING
                ) {
                    receptionTimes.clear()
                    _liveMeasurementState.value = LiveMeasurementUiState()
                }
                previousState = state.state
            }
        }

        viewModelScope.launch {
            measurements.collect { sample ->
                val current = _liveMeasurementState.value
                receptionTimes.add(sample.receivedAtEpochMillis)
                while (
                    receptionTimes.isNotEmpty() &&
                    (sample.receivedAtEpochMillis - receptionTimes.first() > FREQUENCY_WINDOW_MS ||
                        receptionTimes.size > FREQUENCY_WINDOW_CAPACITY)
                ) {
                    receptionTimes.removeAt(0)
                }

                val elapsedMs = receptionTimes.last() - receptionTimes.first()
                val frequencyHz = if (elapsedMs > 0L) {
                    (receptionTimes.size - 1) * 1000.0 / elapsedMs
                } else {
                    0.0
                }

                _liveMeasurementState.value = LiveMeasurementUiState(
                    latestMeasurement = sample,
                    history = (current.history + sample).takeLast(HISTORY_CAPACITY),
                    sampleCount = current.sampleCount + 1,
                    approximateFrequencyHz = frequencyHz
                )
            }
        }
    }

    fun startScanning() = bleConnectionManager.startScanning()

    fun selectDevice(address: String) = bleConnectionManager.selectDevice(address)

    fun connectSelected() = bleConnectionManager.connectSelected()

    fun disconnect() = bleConnectionManager.disconnect()

    fun reportPermissionError() = bleConnectionManager.reportPermissionError()

    override fun onCleared() {
        bleConnectionManager.close()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConnectionViewModel::class.java))
            return ConnectionViewModel(
                BleConnectionManager(context)
            ) as T
        }
    }

    private companion object {
        const val HISTORY_CAPACITY = 300
        const val FREQUENCY_WINDOW_MS = 2_000L
        const val FREQUENCY_WINDOW_CAPACITY = 128
    }
}

data class LiveMeasurementUiState(
    val latestMeasurement: EmgSample? = null,
    val history: List<EmgSample> = emptyList(),
    val sampleCount: Long = 0,
    val approximateFrequencyHz: Double = 0.0
)