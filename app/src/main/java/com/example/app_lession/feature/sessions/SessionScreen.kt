package com.example.app_lession.feature.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.app_lession.core.model.EmgSample
import com.example.app_lession.core.session.EmgSession
import com.example.app_lession.core.ui.EmgLineChart
import java.util.Date

@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState()
    val selectedSessionId by viewModel.selectedSessionId.collectAsState()
    val measurements by viewModel.selectedMeasurements.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (selectedSessionId != null) {
            val session = sessions.firstOrNull { it.id == selectedSessionId }
            SessionDetail(
                session = session,
                measurements = measurements,
                onBack = viewModel::closeSession,
                onRefresh = viewModel::refreshSessionMeasurements
            )
        } else {
            Text("Historial de sesiones", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            if (sessions.isEmpty()) {
                Text(
                    "Todavía no hay sesiones guardadas. Iniciá una desde la pantalla Conexión.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions, key = EmgSession::id) { session ->
                        SessionRow(
                            session = session,
                            onClick = { viewModel.openSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: EmgSession, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDate(session.startedAtEpochMillis),
                    style = MaterialTheme.typography.titleMedium
                )
                if (session.endedAtEpochMillis == null) {
                    Text(
                        "En curso",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Duración: ${formatDuration(sessionDuration(session))}")
            Text("Muestras: ${session.sampleCount}")
        }
    }
}

@Composable
private fun SessionDetail(
    session: EmgSession?,
    measurements: List<EmgSample>,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    OutlinedButton(onClick = onBack) {
        Text("Volver al historial")
    }
    Spacer(Modifier.height(8.dp))
    if (session == null) {
        Text("Cargando sesión…")
        return
    }

    Text("Sesión", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text("Fecha: ${formatDate(session.startedAtEpochMillis)}")
    Text(
        "Duración: ${formatDuration(sessionDuration(session))}" +
            if (session.endedAtEpochMillis == null) " · En curso" else ""
    )
    Text("Muestras guardadas: ${session.sampleCount}")
    if (session.endedAtEpochMillis == null) {
        OutlinedButton(onClick = onRefresh) {
            Text("Actualizar mediciones")
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Evolución EMG · ADC", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    if (measurements.isEmpty()) {
        Text("Esta sesión todavía no tiene mediciones.")
    } else {
        Text(
            "Última muestra: ADC = ${measurements.last().adcValue}, " +
                "t_ms = ${measurements.last().deviceTimeMs}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        EmgLineChart(measurements)
    }
}

private fun formatDate(epochMillis: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    )
        .format(Date(epochMillis))

private fun sessionDuration(session: EmgSession): Long =
    session.endedAtEpochMillis?.let { session.durationMillis }
        ?: (System.currentTimeMillis() - session.startedAtEpochMillis).coerceAtLeast(0)

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
