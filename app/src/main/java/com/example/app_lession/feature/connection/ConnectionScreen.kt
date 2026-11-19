package com.example.app_lession.feature.connection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.app_lession.core.ble.BleConnectionState
import com.example.app_lession.core.ble.BleDiscoveredDevice
import com.example.app_lession.core.model.EmgSample

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connection by viewModel.connectionState.collectAsState()
    val liveMeasurements by viewModel.liveMeasurementState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Lession",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Adquisición EMG",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = connectionLabel(connection.state),
                    style = MaterialTheme.typography.titleMedium
                )
                connection.deviceName?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                connection.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        MeasurementCard(liveMeasurements)
        Spacer(Modifier.height(16.dp))
        EmgChartCard(liveMeasurements.history)

        if (connection.discoveredDevices.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Dispositivos BLE encontrados",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
            ) {
                items(
                    items = connection.discoveredDevices,
                    key = BleDiscoveredDevice::address
                ) { device ->
                    DeviceRow(
                        device = device,
                        selected = device.address == connection.selectedDeviceAddress,
                        onClick = { viewModel.selectDevice(device.address) }
                    )
                }
            }
            OutlinedButton(
                onClick = viewModel::connectSelected,
                enabled = connection.selectedDeviceAddress != null &&
                    !isBleSessionActive(connection.state),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conectar dispositivo seleccionado")
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (isBleSessionActive(connection.state)) {
                        viewModel.disconnect()
                    } else {
                        onRequestPermissions()
                    }
                },
                enabled = connection.state != BleConnectionState.SEARCHING
            ) {
                Text(
                    if (isBleSessionActive(connection.state)) {
                        "Desconectar"
                    } else {
                        "Buscar ESP32"
                    }
                )
            }

            if (connection.state == BleConnectionState.SEARCHING) {
                OutlinedButton(onClick = viewModel::disconnect) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: BleDiscoveredDevice,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: "Dispositivo sin nombre",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "RSSI: ${device.rssi} dBm · Servicio Lession: " +
                    if (device.advertisesLessionService) "sí" else "no",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MeasurementCard(state: LiveMeasurementUiState) {
    val sample = state.latestMeasurement
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Última medición",
                style = MaterialTheme.typography.labelLarge
            )
            Text("ADC", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = sample?.adcValue?.toString() ?: "Esperando datos",
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = sample?.let { "Tiempo de última muestra: t_ms = ${it.deviceTimeMs}" }
                    ?: "El valor ADC se mostrará cuando llegue una notificación TX.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Muestras recibidas", style = MaterialTheme.typography.labelMedium)
                    Text(state.sampleCount.toString(), style = MaterialTheme.typography.titleLarge)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Frecuencia aprox.", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${"%.1f".format(state.approximateFrequencyHz)} Hz",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun EmgChartCard(samples: List<EmgSample>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Evolución EMG", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("ADC · últimos 6 segundos", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            if (samples.isEmpty()) {
                Text(
                    "El gráfico aparecerá al recibir mediciones reales.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            } else {
                EmgLineChart(samples)
            }
        }
    }
}

@Composable
private fun EmgLineChart(samples: List<EmgSample>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val latestTime = samples.last().receivedAtEpochMillis
        val chartStartTime = latestTime - CHART_WINDOW_MS

        for (step in 0..4) {
            val y = size.height * step / 4f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val visibleSamples = samples.filter {
            it.receivedAtEpochMillis >= chartStartTime
        }
        val path = Path()
        visibleSamples.forEachIndexed { index, sample ->
            val x = (
                (sample.receivedAtEpochMillis - chartStartTime).toFloat() /
                    CHART_WINDOW_MS
                ).coerceIn(0f, 1f) * size.width
            val y = size.height * (1f - sample.adcValue / MAX_ADC_VALUE)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        if (visibleSamples.size == 1) {
            val sample = visibleSamples.single()
            val x = (
                (sample.receivedAtEpochMillis - chartStartTime).toFloat() /
                    CHART_WINDOW_MS
                ).coerceIn(0f, 1f) * size.width
            val y = size.height * (1f - sample.adcValue / MAX_ADC_VALUE)
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

private const val CHART_WINDOW_MS = 6_000L
private const val MAX_ADC_VALUE = 4095f

private fun connectionLabel(state: BleConnectionState): String = when (state) {
    BleConnectionState.DISCONNECTED -> "Desconectado"
    BleConnectionState.SEARCHING -> "Buscando dispositivos BLE…"
    BleConnectionState.CONNECTING -> "Conectando y preparando notificaciones…"
    BleConnectionState.CONNECTED -> "GATT listo. Esperando mediciones…"
    BleConnectionState.RECEIVING -> "Recibiendo mediciones reales"
    BleConnectionState.ERROR -> "Error de conexión"
}

private fun isBleSessionActive(state: BleConnectionState): Boolean =
    state == BleConnectionState.CONNECTING ||
        state == BleConnectionState.CONNECTED ||
        state == BleConnectionState.RECEIVING