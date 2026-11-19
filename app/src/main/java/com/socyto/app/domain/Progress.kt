package com.socyto.app.domain

enum class RequirementType { CHECKLIST, QUANTITY }
data class Requirement(val title: String, val type: RequirementType, val effort: Int, val completed: Boolean = false, val requiredQuantity: Double = 1.0, val completedQuantity: Double = 0.0)

/** Returns null if no requirements have been defined. */
fun calculateProgress(requirements: List<Requirement>): Double? {
    if (requirements.isEmpty()) return null
    require(requirements.all { it.effort in 1..3 })
    val totalEffort = requirements.sumOf { it.effort }
    return 100.0 * requirements.sumOf { item ->
        val completion = when (item.type) {
            RequirementType.CHECKLIST -> if (item.completed) 1.0 else 0.0
            RequirementType.QUANTITY -> if (item.requiredQuantity > 0 && item.requiredQuantity.isFinite()) (item.completedQuantity / item.requiredQuantity).coerceIn(0.0, 1.0) else 0.0
        }
        item.effort * completion
    } / totalEffort
}
