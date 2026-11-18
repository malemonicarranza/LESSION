package com.example.app_lession.core.model

data class EmgSample(
    val deviceTimeMs: Long,
    val adcValue: Int,
    val receivedAtEpochMillis: Long
)