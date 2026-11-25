package com.example.app_lession

import com.example.app_lession.core.model.EmgSample
import com.example.app_lession.core.session.SessionCsvFormatter
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCsvFormatterTest {
    @Test
    fun formatAddsExcelCompatibleHeaderAndRealSampleValues() {
        val csv = SessionCsvFormatter.format(
            listOf(
                EmgSample(
                    deviceTimeMs = 38_003,
                    adcValue = 4,
                    receivedAtEpochMillis = 1_700_000_000_000
                )
            )
        )

        assertTrue(csv.startsWith("\uFEFFfecha;t_ms;adc\r\n"))
        assertTrue(csv.contains(";38003;4\r\n"))
        assertTrue(csv.matches(Regex("(?s).*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3};38003;4\\r\\n")))
    }

    @Test
    fun formatReturnsHeaderOnlyWhenSessionHasNoMeasurements() {
        assertTrue(SessionCsvFormatter.format(emptyList()).endsWith("fecha;t_ms;adc\r\n"))
    }
}
