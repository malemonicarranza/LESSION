package com.example.app_lession.core.ble

enum class BleConnectionState {
    DISCONNECTED,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class BleConnectionUiState(
    val state: BleConnectionState = BleConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val errorMessage: String? = null
)