package com.socyto.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
    @Test fun emptyProjectDoesNotInventProgress() {
        assertNull(calculateProgress(emptyList()))
    }

    @Test fun calculatesWeightedChecklistAndQuantityProgress() {
        val requirements = listOf(
            Requirement("Design approval", RequirementType.CHECKLIST, effort = 1, completed = true),
            Requirement("Floor tiling", RequirementType.QUANTITY, effort = 3,
                requiredQuantity = 100.0, completedQuantity = 50.0)
        )
        assertEquals(62.5, calculateProgress(requirements)!!, 0.00001)
    }

    @Test fun completedQuantityCannotExceedHundredPercent() {
        val requirements = listOf(
            Requirement("Painting", RequirementType.QUANTITY, effort = 2,
                requiredQuantity = 10.0, completedQuantity = 12.0)
        )
        assertEquals(100.0, calculateProgress(requirements)!!, 0.00001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedEffort() {
        calculateProgress(listOf(Requirement("Work", RequirementType.CHECKLIST, effort = 0)))
    }
}
