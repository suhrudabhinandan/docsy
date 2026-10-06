package com.suhrud.docsy.domain.health

object StepDeltaCalculator {

    data class CalculationResult(
        val newStepCount: Long,
        val newBaseline: Long,
        val isMidDayStart: Boolean
    )

    fun calculateSteps(
        existingSteps: Long,
        lastSensorReading: Long,
        currentSensorReading: Long,
        isNewDay: Boolean
    ): CalculationResult {
        if (currentSensorReading <= 0) {
            return CalculationResult(existingSteps, lastSensorReading, false)
        }

        if (isNewDay || lastSensorReading <= 0) {
            return CalculationResult(0L, currentSensorReading, isMidDayStart = true)
        }

        val delta = if (currentSensorReading >= lastSensorReading) {
            currentSensorReading - lastSensorReading
        } else {
            currentSensorReading
        }

        val updatedTotal = (existingSteps + delta).coerceAtLeast(0L)
        return CalculationResult(updatedTotal, currentSensorReading, isMidDayStart = false)
    }
}
