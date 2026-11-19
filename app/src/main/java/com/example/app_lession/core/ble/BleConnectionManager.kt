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
    private var connectionTimeout: Runnable? = null
    private var connectionAttemptId = 0L
    private val discoveredBluetoothDevices = linkedMapOf<String, BluetoothDevice>()
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
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val advertisedName = device.name ?: result.scanRecord?.deviceName
            val advertisesService = result.scanRecord?.serviceUuids
                ?.any { it.uuid == BleUuids.SERVICE } == true

            if (_connectionState.value.state != BleConnectionState.SEARCHING) return

            // List named BLE devices and service advertisers. The UI marks
            // devices that advertise the Lession service as preferred candidates.
            if (advertisedName != null || advertisesService) {
                discoveredBluetoothDevices[device.address] = device
                val discovered = BleDiscoveredDevice(
                    name = advertisedName,
                    address = device.address,
                    rssi = result.rssi,
                    advertisesLessionService = advertisesService
                )
                Log.d(
                    tag,
                    "[SCAN] Dispositivo encontrado: name=${discovered.name}, " +
                        "address=${discovered.address}, rssi=${discovered.rssi}, " +
                        "serviceAdvertised=${discovered.advertisesLessionService}"
                )
                publishDiscoveredDevice(discovered)
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
            // Android may deliver callbacks from a GATT that was already closed.
            // Never let a stale callback alter the current connection state.
            if (bluetoothGatt !== gatt) {
                Log.w(tag, "[GATT] Callback ignorado para una conexión anterior: ${gatt.device.address}")
                closeGatt(gatt)
                return
            }

            Log.d(
                tag,
                "[GATT] onConnectionStateChange address=${gatt.device.address}, " +
                    "status=$status, newState=$newState, attempt=$connectionAttemptId"
            )

            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt)
                publishError("Falló la conexión GATT. Código: $status")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(tag, "[GATT] Conectado a nivel ACL; iniciando discoverServices()")
                    publishState(BleConnectionState.CONNECTING, gatt.device.name)
                    if (!gatt.discoverServices()) {
                        Log.e(tag, "[GATT] discoverServices() devolvió false")
                        closeGatt(gatt)
                        publishError("No se pudo iniciar el descubrimiento de servicios.")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(tag, "[GATT] Desconectado correctamente por el stack")
                    closeGatt(gatt)
                    lineBuffer.clear()
                    publishState(BleConnectionState.DISCONNECTED)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (bluetoothGatt !== gatt) {
                Log.w(tag, "[GATT] onServicesDiscovered ignorado para conexión anterior")
                closeGatt(gatt)
                return
            }
            Log.d(tag, "[GATT] onServicesDiscovered status=$status")
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

            // Some Android/MIUI Bluetooth stacks return null from
            // getCharacteristic(UUID) even though the characteristic is present
            // in the discovered list. Resolve from the actual list instead.
            val service = gatt.services.firstOrNull { uuidMatches(it.uuid, BleUuids.SERVICE) }
            val tx = service?.characteristics?.firstOrNull { uuidMatches(it.uuid, BleUuids.TX) }
            val rx = service?.characteristics?.firstOrNull { uuidMatches(it.uuid, BleUuids.RX) }
            val cccd = tx?.descriptors?.firstOrNull { uuidMatches(it.uuid, BleUuids.CCCD) }

            Log.d(
                tag,
                "[GATT] UUID esperados: service=${BleUuids.SERVICE}, " +
                    "tx=${BleUuids.TX}, rx=${BleUuids.RX}, cccd=${BleUuids.CCCD}"
            )
            Log.d(
                tag,
                "[GATT] Resolución normalizada: service=${service != null}, " +
                    "tx=${tx?.uuid}, rx=${rx?.uuid}, cccd=${cccd?.uuid}, " +
                    "txProperties=${tx?.properties}, rxProperties=${rx?.properties}"
            )

            val validationErrors = buildList {
                if (service == null) add("servicio ${BleUuids.SERVICE}")
                if (tx == null) add("TX ${BleUuids.TX}")
                if (rx == null) add("RX ${BleUuids.RX}")
                if (cccd == null) add("CCCD ${BleUuids.CCCD}")
                if (tx != null && tx.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
                    add("TX sin READ")
                }
                if (tx != null && tx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
                    add("TX sin NOTIFY")
                }
                if (rx != null &&
                    rx.properties and
                    (BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0
                ) {
                    add("RX sin WRITE")
                }
            }

            if (validationErrors.isNotEmpty()) {
                val message = "GATT incompleto: ${validationErrors.joinToString()}."
                Log.e(tag, "[GATT] $message")
                disconnectAndCloseGatt(gatt)
                publishError(message)
                return
            }

            Log.d(tag, "[GATT] Servicio y propiedades verificadas correctamente")
            enableTxNotifications(gatt, tx!!, cccd!!)
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (bluetoothGatt !== gatt) {
                Log.w(tag, "[GATT] onDescriptorWrite ignorado para conexión anterior")
                closeGatt(gatt)
                return
            }
            if (!uuidMatches(descriptor.uuid, BleUuids.CCCD)) return

            Log.d(tag, "[GATT] onDescriptorWrite CCCD status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connectionTimeout?.let(mainHandler::removeCallbacks)
                connectionTimeout = null
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
            if (bluetoothGatt !== gatt) return
            if (uuidMatches(characteristic.uuid, BleUuids.TX)) {
                Log.d(tag, "[GATT] Notificación TX recibida (${characteristic.value.size} bytes)")
                processNotification(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (bluetoothGatt !== gatt) return
            if (uuidMatches(characteristic.uuid, BleUuids.TX)) {
                Log.d(tag, "[GATT] Notificación TX recibida (${value.size} bytes)")
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
        disconnectAndCloseGatt(bluetoothGatt)
        discoveredBluetoothDevices.clear()
        lineBuffer.clear()
        _connectionState.value = _connectionState.value.copy(
            deviceName = null,
            errorMessage = null,
            discoveredDevices = emptyList(),
            selectedDeviceAddress = null
        )

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

    fun selectDevice(address: String) {
        if (discoveredBluetoothDevices.containsKey(address) &&
            _connectionState.value.state != BleConnectionState.CONNECTING &&
            _connectionState.value.state != BleConnectionState.CONNECTED
        ) {
            Log.d(tag, "[SCAN] Dispositivo seleccionado: address=$address")
            _connectionState.value = _connectionState.value.copy(
                selectedDeviceAddress = address,
                errorMessage = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connectSelected() {
        val address = _connectionState.value.selectedDeviceAddress
        val device = address?.let(discoveredBluetoothDevices::get)
        if (device == null) {
            publishError("Seleccioná un dispositivo BLE antes de conectar.")
            return
        }
        stopScanning(publishDisconnected = false)
        connect(device)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (!hasBlePermissions()) {
            publishError("Faltan permisos para conectarse por Bluetooth.")
            return
        }

        // A single ESP32 client is supported. Close every previous GATT before
        // creating a new one, including after an earlier failed attempt.
        disconnectAndCloseGatt(bluetoothGatt)
        lineBuffer.clear()
        connectionAttemptId += 1
        val attempt = connectionAttemptId
        Log.d(tag, "[GATT] Abriendo conexión attempt=$attempt, address=${device.address}")
        publishState(
            BleConnectionState.CONNECTING,
            device.name ?: BleUuids.DEVICE_NAME
        )

        val newGatt = try {
            device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (securityException: SecurityException) {
            Log.e(tag, "[GATT] connectGatt() rechazado por permisos", securityException)
            publishError("No se pudo abrir la conexión BLE por permisos.")
            return
        }

        if (newGatt == null) {
            Log.e(tag, "[GATT] connectGatt() devolvió null")
            publishError("El sistema no pudo crear la conexión GATT.")
            return
        }
        bluetoothGatt = newGatt
        connectionTimeout = Runnable {
            if (bluetoothGatt === newGatt &&
                _connectionState.value.state == BleConnectionState.CONNECTING
            ) {
                Log.e(tag, "[GATT] Timeout esperando conexión/servicios, cerrando attempt=$attempt")
                disconnectAndCloseGatt(newGatt)
                publishError("Tiempo de espera agotado al conectar con el ESP32.")
            }
        }.also { mainHandler.postDelayed(it, GATT_TIMEOUT_MS) }
        Log.d(tag, "[GATT] Objeto BluetoothGatt creado attempt=$attempt")
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

    private fun uuidMatches(actual: java.util.UUID, expected: java.util.UUID): Boolean =
        actual.toString()
            .filter(Char::isLetterOrDigit)
            .equals(
                expected.toString().filter(Char::isLetterOrDigit),
                ignoreCase = true
            )

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
                if (_connectionState.value.state == BleConnectionState.CONNECTED) {
                    Log.d(
                        tag,
                        "[DATA] Medición válida recibida: tiempo_ms=${it.deviceTimeMs}, " +
                            "valor_ADC=${it.adcValue}"
                    )
                    publishState(BleConnectionState.RECEIVING)
                }
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
        connectionAttemptId += 1
        val gatt = bluetoothGatt
        if (gatt != null) {
            Log.d(tag, "[GATT] Cerrando conexión solicitada por el usuario")
            try {
                gatt.disconnect()
            } finally {
                closeGatt(gatt)
            }
        }
        lineBuffer.clear()
        publishState(BleConnectionState.DISCONNECTED)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScanning()
        disconnectAndCloseGatt(bluetoothGatt)
        lineBuffer.clear()
        publishState(BleConnectionState.DISCONNECTED)
    }

    fun isBluetoothPermissionGranted(): Boolean = hasBlePermissions()

    fun reportPermissionError() {
        publishError("Se necesitan permisos de Bluetooth para buscar el ESP32.")
    }

    @SuppressLint("MissingPermission")
    private fun disconnectAndCloseGatt(gatt: BluetoothGatt?) {
        if (gatt == null) return
        try {
            gatt.disconnect()
        } catch (_: SecurityException) {
            Log.w(tag, "[GATT] No se pudo desconectar por permisos revocados")
        }
        closeGatt(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt? = bluetoothGatt) {
        if (gatt == null) return

        if (bluetoothGatt === gatt) {
            connectionTimeout?.let(mainHandler::removeCallbacks)
            connectionTimeout = null
            bluetoothGatt = null
            connectionAttemptId += 1
        }

        try {
            Log.d(tag, "[GATT] BluetoothGatt.close() address=${gatt.device.address}")
            gatt.close()
        } catch (_: SecurityException) {
            // Permission may have been revoked while closing the connection.
            Log.w(tag, "[GATT] No se pudo cerrar por permisos revocados")
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

    private fun publishDiscoveredDevice(device: BleDiscoveredDevice) {
        val devices = (_connectionState.value.discoveredDevices
            .filterNot { it.address == device.address } + device)
            .sortedByDescending(BleDiscoveredDevice::rssi)
        _connectionState.value = _connectionState.value.copy(discoveredDevices = devices)
    }

    private fun publishState(
        state: BleConnectionState,
        deviceName: String? = _connectionState.value.deviceName
    ) {
        _connectionState.value = _connectionState.value.copy(
            state = state,
            deviceName = deviceName,
            errorMessage = null
        )
    }

    private fun publishError(message: String) {
        _connectionState.value = _connectionState.value.copy(
            state = BleConnectionState.ERROR,
            errorMessage = message
        )
    }

    companion object {
        private const val SCAN_DURATION_MS = 15_000L
        private const val GATT_TIMEOUT_MS = 15_000L
    }
}