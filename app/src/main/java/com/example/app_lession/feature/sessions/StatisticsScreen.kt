package com.example.app_lession.feature.sessions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.app_lession.core.model.EmgSample
import com.example.app_lession.core.session.SessionStatistics
import java.util.Date

@Composable
fun StatisticsScreen(
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier
) {
    val statistics by viewModel.sessionStatistics.collectAsState()
    val sampleSets by viewModel.statisticsMeasurements.collectAsState()
    val loadingSessions by viewModel.loadingStatisticsSessions.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val sessionsWithSamples = statistics.filter { it.sampleCount > 0 }
    var selectedSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var comparedSessionIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(sessionsWithSamples) {
        if (selectedSessionId !in sessionsWithSamples.map { it.id }) {
            selectedSessionId = sessionsWithSamples.lastOrNull()?.id
        }
        selectedSessionId?.let(viewModel::loadStatisticsMeasurements)
        comparedSessionIds
            .filter { id -> sessionsWithSamples.any { it.id == id } }
            .forEach(viewModel::loadStatisticsMeasurements)
    }

    val selectedSession = sessionsWithSamples.firstOrNull { it.id == selectedSessionId }
    val selectedSamples = selectedSessionId?.let(sampleSets::get)
    val comparedSessions = comparedSessionIds.mapNotNull { id ->
        sessionsWithSamples.firstOrNull { it.id == id }?.let { session ->
            sampleSets[id]?.let { session to it }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Estadísticas", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        if (sessionsWithSamples.isEmpty()) {
            Text(
                "No hay mediciones guardadas para analizar. Iniciá y completá una sesión desde Conexión.",
                style = MaterialTheme.typography.bodyLarge
            )
            return@Column
        }

        SectionTitle("Análisis individual por sesión")
        SelectorRow(
            sessions = sessionsWithSamples,
            selectedIds = setOfNotNull(selectedSessionId),
            onSelect = { selectedSessionId = it }
        )
        selectedSession?.let { session ->
            Spacer(Modifier.height(8.dp))
            SessionSummaryCard(session)
            Spacer(Modifier.height(12.dp))
            when {
                selectedSamples != null -> {
                    BoxPlotCard(selectedSamples)
                    Spacer(Modifier.height(12.dp))
                    if (selectedSamples.isEmpty()) {
                        Text("Esta sesión no contiene mediciones guardadas.")
                    } else {
                        IndividualChartCard(selectedSamples)
                    }
                }
                session.id in loadingSessions -> Text("Cargando mediciones guardadas…")
                else -> Text("No se pudieron cargar las mediciones de esta sesión.")
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Comparar sesiones")
        Text(
            "Elegí hasta $MAX_COMPARISON_SESSIONS sesiones. Las curvas se alinean por porcentaje de duración; los puntos son muestras reales.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        SelectorRow(
            sessions = sessionsWithSamples,
            selectedIds = comparedSessionIds.toSet(),
            onSelect = { id ->
                comparedSessionIds = when {
                    id in comparedSessionIds -> comparedSessionIds - id
                    comparedSessionIds.size < MAX_COMPARISON_SESSIONS ->
                        comparedSessionIds + id
                    else -> comparedSessionIds
                }
            }
        )
        when {
            comparedSessionIds.isEmpty() -> Text("Seleccioná sesiones para compararlas.")
            comparedSessions.size == comparedSessionIds.size -> {
                ComparisonChartCard(comparedSessions)
            }
            else -> Text("Cargando mediciones de las sesiones seleccionadas…")
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Resumen por sesión")
        Spacer(Modifier.height(8.dp))
        statistics.asReversed().forEach { session ->
            SessionStatisticsCard(session)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SelectorRow(
    sessions: List<SessionStatistics>,
    selectedIds: Set<Long>,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sessions.forEach { session ->
            FilterChip(
                selected = session.id in selectedIds,
                onClick = { onSelect(session.id) },
                label = { Text(formatShortDate(session.startedAtEpochMillis)) }
            )
        }
    }
}

@Composable
private fun SessionSummaryCard(session: SessionStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(formatDate(session.startedAtEpochMillis), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticValue("Muestras", session.sampleCount.toString())
                StatisticValue("Duración", formatDuration(sessionDuration(session)))
            }
        }
    }
}

@Composable
private fun BoxPlotCard(samples: List<EmgSample>) {
    val boxPlot = remember(samples) {
        calculateAdcBoxPlot(samples.map(EmgSample::adcValue))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Distribución ADC · box plot", style = MaterialTheme.typography.titleMedium)
            if (boxPlot == null) {
                Spacer(Modifier.height(8.dp))
                Text("Esta sesión no contiene muestras.")
                return@Column
            }
            Spacer(Modifier.height(16.dp))
            AdcBoxPlotDiagram(boxPlot)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticValue("Mínimo", formatAdc(boxPlot.minimum.toDouble()))
                StatisticValue("Q1", formatAdc(boxPlot.firstQuartile))
                StatisticValue("Mediana", formatAdc(boxPlot.median))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticValue("Q3", formatAdc(boxPlot.thirdQuartile))
                StatisticValue("Máximo", formatAdc(boxPlot.maximum.toDouble()))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Cálculo: se ordenan las muestras ADC; la mediana divide los datos en dos mitades. " +
                    "Q1 es la mediana de la mitad inferior y Q3 la de la mitad superior; " +
                    "si hay un número impar de muestras, la mediana central no se incluye en las mitades.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AdcBoxPlotDiagram(boxPlot: AdcBoxPlot) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        val centerY = size.height / 2f
        val left = adcX(boxPlot.minimum.toDouble(), size.width)
        val q1 = adcX(boxPlot.firstQuartile, size.width)
        val median = adcX(boxPlot.median, size.width)
        val q3 = adcX(boxPlot.thirdQuartile, size.width)
        val right = adcX(boxPlot.maximum.toDouble(), size.width)
        drawLine(gridColor, Offset(0f, centerY), Offset(size.width, centerY), 1.dp.toPx())
        drawLine(lineColor, Offset(left, centerY), Offset(right, centerY), 3.dp.toPx())
        drawLine(lineColor, Offset(left, centerY - 12.dp.toPx()), Offset(left, centerY + 12.dp.toPx()), 3.dp.toPx())
        drawLine(lineColor, Offset(right, centerY - 12.dp.toPx()), Offset(right, centerY + 12.dp.toPx()), 3.dp.toPx())
        drawRect(
            color = lineColor.copy(alpha = 0.18f),
            topLeft = Offset(q1, centerY - 20.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(q3 - q1, 40.dp.toPx())
        )
        drawLine(lineColor, Offset(q1, centerY - 20.dp.toPx()), Offset(q3, centerY - 20.dp.toPx()), 2.dp.toPx())
        drawLine(lineColor, Offset(q1, centerY + 20.dp.toPx()), Offset(q3, centerY + 20.dp.toPx()), 2.dp.toPx())
        drawLine(lineColor, Offset(q1, centerY - 20.dp.toPx()), Offset(q1, centerY + 20.dp.toPx()), 2.dp.toPx())
        drawLine(lineColor, Offset(q3, centerY - 20.dp.toPx()), Offset(q3, centerY + 20.dp.toPx()), 2.dp.toPx())
        drawLine(lineColor, Offset(median, centerY - 20.dp.toPx()), Offset(median, centerY + 20.dp.toPx()), 3.dp.toPx())
    }
}

@Composable
private fun IndividualChartCard(samples: List<EmgSample>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Mediciones ADC de la sesión", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tiempo desde el inicio · ${samples.first().deviceTimeMs}–${samples.last().deviceTimeMs} ms",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            AdcSeriesChart(listOf(samples to MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun ComparisonChartCard(sessions: List<Pair<SessionStatistics, List<EmgSample>>>) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Mediciones ADC superpuestas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Progreso normalizado de cada sesión · 0–100%", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            AdcSeriesChart(sessions.mapIndexed { index, (_, samples) ->
                samples to colors[index % colors.size]
            })
            Spacer(Modifier.height(12.dp))
            sessions.forEachIndexed { index, (session, _) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.height(16.dp).fillMaxWidth(0.06f)) {
                        drawLine(
                            colors[index % colors.size],
                            Offset(0f, size.height / 2f),
                            Offset(size.width, size.height / 2f),
                            3.dp.toPx()
                        )
                    }
                    Text(
                        "${formatShortDate(session.startedAtEpochMillis)} · ${session.sampleCount} muestras",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AdcSeriesChart(
    series: List<Pair<List<EmgSample>, androidx.compose.ui.graphics.Color>>
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        for (step in 0..4) {
            val y = size.height * step / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        series.forEach { (samples, color) ->
            if (samples.isEmpty()) return@forEach
            val firstTime = samples.first().deviceTimeMs
            val timeSpan = (samples.last().deviceTimeMs - firstTime).coerceAtLeast(1L)
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val x = (sample.deviceTimeMs - firstTime).toFloat() / timeSpan * size.width
                val y = size.height * (1f - sample.adcValue / MAX_ADC_VALUE)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (samples.size == 1) {
                val y = size.height * (1f - samples.single().adcValue / MAX_ADC_VALUE)
                drawCircle(color, 4.dp.toPx(), Offset(size.width, y))
            } else {
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun SessionStatisticsCard(session: SessionStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(formatDate(session.startedAtEpochMillis), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticValue("Promedio", if (session.sampleCount > 0) formatAdc(session.averageAdc) else "—")
                StatisticValue("Mínimo", if (session.sampleCount > 0) session.minimumAdc.toString() else "—")
                StatisticValue("Máximo", if (session.sampleCount > 0) session.maximumAdc.toString() else "—")
            }
            Spacer(Modifier.height(8.dp))
            Text("Muestras: ${session.sampleCount}")
            Text("Duración: ${formatDuration(sessionDuration(session))}")
        }
    }
}

@Composable
private fun StatisticValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun adcX(value: Double, width: Float): Float =
    (value / MAX_ADC_VALUE).toFloat().coerceIn(0f, 1f) * width

private fun formatAdc(value: Double): String =
    String.format(java.util.Locale.getDefault(), "%.1f", value)

private fun formatDate(epochMillis: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    ).format(Date(epochMillis))

private fun formatShortDate(epochMillis: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
        .format(Date(epochMillis))

private fun sessionDuration(session: SessionStatistics): Long =
    session.endedAtEpochMillis?.let { session.durationMillis }
        ?: (System.currentTimeMillis() - session.startedAtEpochMillis).coerceAtLeast(0)

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private const val MAX_COMPARISON_SESSIONS = 4
private const val MAX_ADC_VALUE = 4095f
