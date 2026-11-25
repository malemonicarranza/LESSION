package com.example.app_lession.core.session

import com.example.app_lession.core.model.EmgSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionCsvFormatter {
    fun format(samples: List<EmgSample>): String {
        val dateFormatter = SimpleDateFormat(DATE_PATTERN, Locale.ROOT)
        return buildString {
            append('\uFEFF')
            append("fecha;t_ms;adc\r\n")
            samples.forEach { sample ->
                append(dateFormatter.format(Date(sample.receivedAtEpochMillis)))
                append(';')
                append(sample.deviceTimeMs)
                append(';')
                append(sample.adcValue)
                append("\r\n")
            }
        }
    }

    private const val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"
}
