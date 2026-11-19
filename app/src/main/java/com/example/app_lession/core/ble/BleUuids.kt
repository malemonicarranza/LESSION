package com.example.app_lession.core.ble

import java.util.UUID

object BleUuids {
    const val DEVICE_NAME = "ESP32 LESSION"
    val SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    val RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}