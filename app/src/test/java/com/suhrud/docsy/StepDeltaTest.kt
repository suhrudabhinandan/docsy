package com.suhrud.docsy

import com.suhrud.docsy.domain.health.StepDeltaCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepDeltaTest {

    @Test
    fun testFirstReadingBaseline() {
        val result = StepDeltaCalculator.calculateSteps(
            existingSteps = 0L,
            lastSensorReading = 0L,
            currentSensorReading = 5000L,
            isNewDay = true
        )

        assertEquals(0L, result.newStepCount)
        assertEquals(5000L, result.newBaseline)
        assertTrue(result.isMidDayStart)
    }

    @Test
    fun testNormalStepAccumulation() {
        val result = StepDeltaCalculator.calculateSteps(
            existingSteps = 100L,
            lastSensorReading = 5000L,
            currentSensorReading = 5150L,
            isNewDay = false
        )

        assertEquals(250L, result.newStepCount)
        assertEquals(5150L, result.newBaseline)
    }

    @Test
    fun testRebootDelta() {
        val result = StepDeltaCalculator.calculateSteps(
            existingSteps = 400L,
            lastSensorReading = 10000L,
            currentSensorReading = 30L,
            isNewDay = false
        )

        assertEquals(430L, result.newStepCount)
        assertEquals(30L, result.newBaseline)
    }

    @Test
    fun testDayRollover() {
        val result = StepDeltaCalculator.calculateSteps(
            existingSteps = 8500L,
            lastSensorReading = 10030L,
            currentSensorReading = 10050L,
            isNewDay = true
        )

        assertEquals(0L, result.newStepCount)
        assertEquals(10050L, result.newBaseline)
    }
}
