package com.socyto.app.data

import org.junit.Assert.*
import org.junit.Test

class ProjectInputRulesTest {
    @Test fun requiresMeaningfulProjectNames() {
        assertFalse(ProjectInputRules.validName("   "))
        assertTrue(ProjectInputRules.validName("Wai Residence"))
        assertFalse(ProjectInputRules.validName("X".repeat(121)))
    }
    @Test fun validatesReceiptNumbers() {
        assertFalse(ProjectInputRules.validQuantity(-1.0))
        assertFalse(ProjectInputRules.validQuantity(0.0))
        assertFalse(ProjectInputRules.validQuantity(Double.NaN))
        assertTrue(ProjectInputRules.validQuantity(2.5))
        assertFalse(ProjectInputRules.validRate(Double.POSITIVE_INFINITY))
        assertTrue(ProjectInputRules.validRate(0.0))
    }
    @Test fun validatesOptionalContactEmail() {
        assertTrue(ProjectInputRules.validEmail(""))
        assertTrue(ProjectInputRules.validEmail("office@company.in"))
        assertFalse(ProjectInputRules.validEmail("notanemail"))
    }
}
