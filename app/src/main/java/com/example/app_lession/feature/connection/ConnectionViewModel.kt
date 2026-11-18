package com.example.app_lession.feature.connection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app_lession.core.ble.BleConnectionManager

class ConnectionViewModel(
    private val bleConnectionManager: BleConnectionManager
) : ViewModel() {
    val connectionState = bleConnectionManager.connectionState
    val measurements = bleConnectionManager.measurements
    val latestMeasurement = bleConnectionManager.latestMeasurement

    fun startScanning() = bleConnectionManager.startScanning()

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
}