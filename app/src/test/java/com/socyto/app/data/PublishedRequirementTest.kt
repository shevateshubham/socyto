package com.socyto.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PublishedRequirementTest {
    @Test fun checklistStatusUsesPublishedCompletion() {
        val pending = PublishedRequirement("Electrical", "checklist",
            false, null, 0.0, null)
        val done = pending.copy(completed = true)
        assertEquals("Pending", pending.statusText())
        assertEquals("Completed", done.statusText())
    }

    @Test fun quantityStatusShowsPublishedAmountAndUnit() {
        val task = PublishedRequirement("Flooring", "quantity",
            false, 100.0, 50.0, "sq m")
        assertEquals("50 / 100 sq m", task.statusText())
    }

    @Test fun snapshotTasksAreImmutableAcrossLaterDraftChanges() {
        val published = PublishedRequirement("Flooring", "quantity",
            false, 100.0, 50.0, "sq m")
        val snapshot = PublishedUpdate("62.5%", "Published work", "2026-11-24",
            requirements = listOf(published))
        val laterDraft = published.copy(completedQuantity = 80.0)
        assertEquals("50 / 100 sq m", snapshot.requirements.single().statusText())
        assertEquals("80 / 100 sq m", laterDraft.statusText())
    }
}
