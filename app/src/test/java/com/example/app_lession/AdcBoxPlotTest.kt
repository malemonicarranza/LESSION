package com.example.app_lession

import com.example.app_lession.feature.sessions.calculateAdcBoxPlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdcBoxPlotTest {
    @Test
    fun calculatesMedianOfHalvesForOddSampleCount() {
        val result = calculateAdcBoxPlot(listOf(9, 1, 5, 3, 7))

        assertEquals(1, result?.minimum)
        assertEquals(2.0, result?.firstQuartile)
        assertEquals(5.0, result?.median)
        assertEquals(8.0, result?.thirdQuartile)
        assertEquals(9, result?.maximum)
    }

    @Test
    fun calculatesInterpolatedMediansForEvenHalves() {
        val result = calculateAdcBoxPlot(listOf(1, 2, 3, 4))

        assertEquals(1.5, result?.firstQuartile)
        assertEquals(2.5, result?.median)
        assertEquals(3.5, result?.thirdQuartile)
    }

    @Test
    fun returnsNullForNoSamples() {
        assertNull(calculateAdcBoxPlot(emptyList()))
    }
}
