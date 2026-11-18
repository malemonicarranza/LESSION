package com.example.app_lession.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.app_lession.core.model.EmgSample
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class BleConnectionManager(context: Context) {
    private val tag = "LessionBle"
    private val appContext = context.applicationContext
    private val bluetoothAdapter =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanTimeout: Runnable? = null
    private val lineBuffer = StringBuilder()

    private val _connectionState = MutableStateFlow(BleConnectionUiState())
    val connectionState: StateFlow<BleConnectionUiState> = _connectionState

    private val _measurements = MutableSharedFlow<EmgSample>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val measurements: SharedFlow<EmgSample> = _measurements

    private val _latestMeasurement = MutableStateFlow<EmgSample?>(null)
    val latestMeasurement: StateFlow<EmgSample?> = _latestMeasurement

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val advertisedName = device.name ?: result.scanRecord?.deviceName
            val advertisesService = result.scanRecord?.serviceUuids
                ?.any { it.uuid == BleUuids.SERVICE } == true

            if (advertisedName == BleUuids.DEVICE_NAME || advertisesService) {
                Log.d(
                    tag,
                    "Dispositivo BLE encontrado: name=" + advertisedName +
                        ", address=" + device.address +
                        ", serviceAdvertised=" + advertisesService
                )
                stopScanning(publishDisconnected = false)
                connect(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            publishError("No se pudo escanear BLE. Código: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt)
                publishError("Falló la conexión GATT. Código: $status")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    publishState(BleConnectionState.CONNECTING, gatt.device.name)
                    if (!gatt.discoverServices()) {
                        closeGatt(gatt)
                        publishError("No se pudo iniciar el descubrimiento de servicios.")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt(gatt)
                    lineBuffer.clear()
                    publishState(BleConnectionState.DISCONNECTED)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt)
                publishError("No se pudieron descubrir los servicios. Código: $status")
                return
            }

            Log.d(
                tag,
                "Servicios descubiertos: " + gatt.services.joinToString { serviceItem ->
                    serviceItem.uuid.toString() + ":" +
                        serviceItem.characteristics.joinToString { characteristic ->
                            characteristic.uuid.toString() + ":" +
                                characteristic.descriptors.joinToString { it.uuid.toString() }
                        }
                }
            )

            val service = gatt.getService(BleUuids.SERVICE)
            val tx = service?.getCharacteristic(BleUuids.TX)
            val rx = service?.getCharacteristic(BleUuids.RX)
            val cccd = tx?.getDescriptor(BleUuids.CCCD)

            if (service == null || tx == null || rx == null || cccd == null) {
                Log.e(
                    tag,
                    "GATT incompleto: service=" + (service != null) +
                        ", tx=" + (tx != null) +
                        ", rx=" + (rx != null) +
                        ", cccd=" + (cccd != null)
                )
                closeGatt(gatt)
                publishError("El ESP32 no expone el servicio o las características esperadas.")
                return
            }

            enableTxNotifications(gatt, tx, cccd)
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid != BleUuids.CCCD) return

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(tag, "CCCD TX habilitado correctamente")
                publishState(BleConnectionState.CONNECTED, gatt.device.name)
            } else {
                closeGatt(gatt)
                publishError("No se pudieron activar las notificaciones TX. Código: $status")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BleUuids.TX) {
                processNotification(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == BleUuids.TX) {
                processNotification(value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasBlePermissions()) {
            publishError("Faltan permisos de Bluetooth.")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !appContext.packageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE
            )
        ) {
            publishError("Este dispositivo no soporta Bluetooth Low Energy.")
            return
        }

        if (!adapter.isEnabled) {
            publishError("Bluetooth está desactivado.")
            return
        }

        stopScanning(publishDisconnected = false)
        closeGatt()
        lineBuffer.clear()

        val bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            publishError("No se pudo obtener el escáner BLE.")
            return
        }

        scanner = bleScanner
        publishState(BleConnectionState.SEARCHING)
        bleScanner.startScan(null, scanSettings, scanCallback)

        scanTimeout?.let(mainHandler::removeCallbacks)
        scanTimeout = Runnable {
            if (_connectionState.value.state == BleConnectionState.SEARCHING) {
                stopScanning(publishDisconnected = false)
                publishError("No se encontró ESP32 LESSION.")
            }
        }.also { mainHandler.postDelayed(it, SCAN_DURATION_MS) }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(publishDisconnected: Boolean = true) {
        scanTimeout?.let(mainHandler::removeCallbacks)
        scanTimeout = null
        scanner?.stopScan(scanCallback)
        scanner = null

        if (publishDisconnected &&
            _connectionState.value.state == BleConnectionState.SEARCHING
        ) {
            publishState(BleConnectionState.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (!hasBlePermissions()) {
            publishError("Faltan permisos para conectarse por Bluetooth.")
            return
        }

        publishState(
            BleConnectionState.CONNECTING,
            device.name ?: BleUuids.DEVICE_NAME
        )
        bluetoothGatt = device.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableTxNotifications(
        gatt: BluetoothGatt,
        tx: BluetoothGattCharacteristic,
        cccd: BluetoothGattDescriptor
    ) {
        Log.d(tag, "Activando notificaciones TX")
        if (!gatt.setCharacteristicNotification(tx, true)) {
            closeGatt(gatt)
            publishError("No se pudo preparar la suscripción a TX.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                cccd,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    private fun processNotification(value: ByteArray) {
        lineBuffer.append(String(value, StandardCharsets.UTF_8))

        while (true) {
            val lineEnd = lineBuffer.indexOf("\n")
            if (lineEnd < 0) return

            val line = lineBuffer.substring(0, lineEnd).trimEnd('\r')
            lineBuffer.delete(0, lineEnd + 1)
            parseSample(line)?.let {
                _latestMeasurement.value = it
                _measurements.tryEmit(it)
            }
        }
    }

    private fun parseSample(line: String): EmgSample? {
        val fields = line.split(',', limit = 3)
        if (fields.size != 2) return null

        val deviceTimeMs = fields[0].toLongOrNull() ?: return null
        val adcValue = fields[1].toIntOrNull() ?: return null
        if (deviceTimeMs < 0L || adcValue !in 0..4095) return null

        return EmgSample(
            deviceTimeMs = deviceTimeMs,
            adcValue = adcValue,
            receivedAtEpochMillis = System.currentTimeMillis()
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScanning()
        bluetoothGatt?.disconnect()
        if (bluetoothGatt == null) {
            publishState(BleConnectionState.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScanning()
        closeGatt()
        lineBuffer.clear()
        publishState(BleConnectionState.DISCONNECTED)
    }

    fun isBluetoothPermissionGranted(): Boolean = hasBlePermissions()

    fun reportPermissionError() {
        publishError("Se necesitan permisos de Bluetooth para buscar el ESP32.")
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt? = bluetoothGatt) {
        if (gatt == null) return

        if (bluetoothGatt === gatt) {
            bluetoothGatt = null
        }

        try {
            gatt.close()
        } catch (_: SecurityException) {
            // Permission may have been revoked while closing the connection.
        }
    }

    private fun hasBlePermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(appContext, it) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun publishState(
        state: BleConnectionState,
        deviceName: String? = _connectionState.value.deviceName
    ) {
        _connectionState.value = BleConnectionUiState(state, deviceName, null)
    }

    private fun publishError(message: String) {
        _connectionState.value = BleConnectionUiState(
            state = BleConnectionState.ERROR,
            deviceName = _connectionState.value.deviceName,
            errorMessage = message
        )
    }

    companion object {
        private const val SCAN_DURATION_MS = 15_000L
    }
}