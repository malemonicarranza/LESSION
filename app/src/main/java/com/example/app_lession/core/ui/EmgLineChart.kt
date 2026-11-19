package com.example.app_lession.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.app_lession.core.model.EmgSample
import kotlin.math.ceil

@Composable
fun EmgLineChart(
    samples: List<EmgSample>,
    visibleWindowMillis: Long? = null,
    modifier: Modifier = Modifier
) {
    val chartSamples = remember(samples, visibleWindowMillis) {
        val startTime = visibleWindowMillis?.let {
            samples.lastOrNull()?.receivedAtEpochMillis?.minus(it)
        }
        val visible = if (startTime == null) samples else {
            samples.filter { it.receivedAtEpochMillis >= startTime }
        }
        decimate(visible)
    }

    val lineColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val gridColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        if (chartSamples.isEmpty()) return@Canvas

        val startTime = visibleWindowMillis?.let {
            chartSamples.last().receivedAtEpochMillis - it
        } ?: chartSamples.first().receivedAtEpochMillis
        val timeSpan = (chartSamples.last().receivedAtEpochMillis - startTime)
            .coerceAtLeast(1L)

        for (step in 0..4) {
            val y = size.height * step / 4f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val path = Path()
        chartSamples.forEachIndexed { index, sample ->
            val x = (
                (sample.receivedAtEpochMillis - startTime).toFloat() /
                    timeSpan
                ).coerceIn(0f, 1f) * size.width
            val y = size.height * (1f - sample.adcValue / MAX_ADC_VALUE)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        if (chartSamples.size == 1) {
            val sample = chartSamples.single()
            val x = (
                (sample.receivedAtEpochMillis - startTime).toFloat() /
                    timeSpan
                ).coerceIn(0f, 1f) * size.width
            val y = size.height * (1f - sample.adcValue / MAX_ADC_VALUE)
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

private fun decimate(samples: List<EmgSample>): List<EmgSample> {
    if (samples.size <= MAX_CHART_POINTS) return samples

    val bucketSize = ceil(samples.size.toDouble() / (MAX_CHART_POINTS / 4)).toInt()
    val result = ArrayList<EmgSample>(MAX_CHART_POINTS)
    var start = 0
    while (start < samples.size) {
        val end = (start + bucketSize).coerceAtMost(samples.size)
        var minimumIndex = start
        var maximumIndex = start
        for (index in start + 1 until end) {
            val sample = samples[index]
            if (sample.adcValue < samples[minimumIndex].adcValue) minimumIndex = index
            if (sample.adcValue > samples[maximumIndex].adcValue) maximumIndex = index
        }
        listOf(start, minimumIndex, maximumIndex, end - 1)
            .distinct()
            .sorted()
            .forEach { result.add(samples[it]) }
        start = end
    }
    return result
}

private const val MAX_CHART_POINTS = 2_000
private const val MAX_ADC_VALUE = 4095f
