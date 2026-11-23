package com.socyto.app.data

/**
 * Pure progress rules shared by screens and repositories.
 * A null result means a project has no tasks, not that it is 0% or 100% done.
 */
object ProgressMath {
    data class Task(
        val trackingType: String,
        val effort: Int,
        val completed: Boolean = false,
        val completedQuantity: Double = 0.0,
        val requiredQuantity: Double? = null
    )

    fun isValidQuantityUpdate(required: Double?, completed: Double): Boolean =
        required != null && required.isFinite() && required > 0.0 &&
            completed.isFinite() && completed >= 0.0 && completed <= required

    fun percent(tasks: List<Task>): Double? {
        if (tasks.isEmpty()) return null
        var weightedCompletion = 0.0
        var weightTotal = 0.0
        for (task in tasks) {
            require(task.effort > 0) { "Effort must be positive." }
            val completion = when (task.trackingType) {
                "checklist" -> if (task.completed) 1.0 else 0.0
                "quantity" -> {
                    require(isValidQuantityUpdate(task.requiredQuantity, task.completedQuantity)) {
                        "Quantity progress must be within the required quantity."
                    }
                    task.completedQuantity / task.requiredQuantity!!
                }
                else -> throw IllegalArgumentException("Unknown progress tracking type.")
            }
            weightedCompletion += task.effort * completion
            weightTotal += task.effort
        }
        return 100.0 * weightedCompletion / weightTotal
    }
}
