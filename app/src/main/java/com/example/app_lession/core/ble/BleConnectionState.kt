package com.example.app_lession.core.ble

data class BleDiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val advertisesLessionService: Boolean
)

enum class BleConnectionState {
    DISCONNECTED,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    RECEIVING,
    ERROR
}

data class BleConnectionUiState(
    val state: BleConnectionState = BleConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val errorMessage: String? = null,
    val discoveredDevices: List<BleDiscoveredDevice> = emptyList(),
    val selectedDeviceAddress: String? = null
)