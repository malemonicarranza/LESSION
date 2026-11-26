package com.example.app_lession.feature.sessions

data class AdcBoxPlot(
    val minimum: Int,
    val firstQuartile: Double,
    val median: Double,
    val thirdQuartile: Double,
    val maximum: Int
)

fun calculateAdcBoxPlot(values: List<Int>): AdcBoxPlot? {
    if (values.isEmpty()) return null
    require(values.all { it in 0..4095 }) { "ADC values must be between 0 and 4095." }

    val sorted = values.sorted()
    val midpoint = sorted.size / 2
    val lowerHalf = sorted.subList(0, midpoint)
    val upperHalf = sorted.subList((sorted.size + 1) / 2, sorted.size)

    return AdcBoxPlot(
        minimum = sorted.first(),
        firstQuartile = median(lowerHalf) ?: sorted.first().toDouble(),
        median = median(sorted) ?: sorted.first().toDouble(),
        thirdQuartile = median(upperHalf) ?: sorted.last().toDouble(),
        maximum = sorted.last()
    )
}

private fun median(sortedValues: List<Int>): Double? {
    if (sortedValues.isEmpty()) return null
    val middle = sortedValues.size / 2
    return if (sortedValues.size % 2 == 0) {
        (sortedValues[middle - 1] + sortedValues[middle]) / 2.0
    } else {
        sortedValues[middle].toDouble()
    }
}
