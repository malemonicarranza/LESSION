package com.example.app_lession.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.app_lession.core.ble.BleConnectionState
import com.example.app_lession.core.model.EmgSample

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connection by viewModel.connectionState.collectAsState()
    val latestMeasurement by viewModel.latestMeasurement.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
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
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium
                    )
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

        MeasurementCard(latestMeasurement)

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (connection.state == BleConnectionState.CONNECTED ||
                        connection.state == BleConnectionState.CONNECTING
                    ) {
                        viewModel.disconnect()
                    } else {
                        onRequestPermissions()
                    }
                },
                enabled = connection.state != BleConnectionState.SEARCHING
            ) {
                Text(
                    if (connection.state == BleConnectionState.CONNECTED ||
                        connection.state == BleConnectionState.CONNECTING
                    ) {
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
private fun MeasurementCard(sample: EmgSample?) {
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = sample?.adcValue?.toString() ?: "Esperando datos",
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = sample?.let { "t_ms: ${it.deviceTimeMs}" }
                    ?: "El valor ADC se mostrará cuando llegue una notificación TX.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun connectionLabel(state: BleConnectionState): String = when (state) {
    BleConnectionState.DISCONNECTED -> "Desconectado"
    BleConnectionState.SEARCHING -> "Buscando ESP32 LESSION…"
    BleConnectionState.CONNECTING -> "Conectando y preparando notificaciones…"
    BleConnectionState.CONNECTED -> "Conectado. Recibiendo datos reales."
    BleConnectionState.ERROR -> "Error de conexión"
}