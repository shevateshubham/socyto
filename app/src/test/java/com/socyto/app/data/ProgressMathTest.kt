package com.socyto.app.data

import org.junit.Assert.*
import org.junit.Test

class ProgressMathTest {
    @Test fun emptyProjectHasNoComputedProgress() {
        assertNull(ProgressMath.percent(emptyList()))
    }

    @Test fun combinesBinaryAndQuantityWithEffortWeights() {
        val result = ProgressMath.percent(listOf(
            ProgressMath.Task("checklist", 1, completed = true),
            ProgressMath.Task("quantity", 3, completedQuantity = 2.0, requiredQuantity = 4.0)
        ))
        assertEquals(62.5, result!!, 0.00001)
    }

    @Test fun rejectsQuantityAboveRequiredAndInvalidNumbers() {
        assertFalse(ProgressMath.isValidQuantityUpdate(5.0, 5.1))
        assertFalse(ProgressMath.isValidQuantityUpdate(0.0, 0.0))
        assertFalse(ProgressMath.isValidQuantityUpdate(null, 0.0))
        assertFalse(ProgressMath.isValidQuantityUpdate(5.0, Double.NaN))
        assertFalse(ProgressMath.isValidQuantityUpdate(Double.POSITIVE_INFINITY, 0.0))
        assertTrue(ProgressMath.isValidQuantityUpdate(5.0, 5.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownTrackingType() {
        ProgressMath.percent(listOf(ProgressMath.Task("unknown", 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroEffort() {
        ProgressMath.percent(listOf(ProgressMath.Task("checklist", 0)))
    }
}
