package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*

/** Draft edits and publications are available ONLY on the authenticated Business Admin screen. */
@Composable
fun ProjectPublishingPanel(
    session: Session,
    api: SocytoApi,
    project: ProjectSummary,
    onPublished: () -> Unit
) {
    var requirements by remember(project.id) { mutableStateOf(emptyList<RequirementSummary>()) }
    var loading by remember(project.id) { mutableStateOf(true) }
    var message by remember(project.id) { mutableStateOf("") }
    var publicationNote by remember(project.id) { mutableStateOf("") }
    var confirmation by remember(project.id) { mutableStateOf(false) }
    var editingQuantity by remember(project.id) { mutableStateOf(mapOf<String, String>()) }

    fun reload() {
        loading = true
        api.async({ api.requirements(session, project) }) { result ->
            loading = false
            result.onSuccess {
                requirements = it
                editingQuantity = it.associate { requirement ->
                    requirement.id to requirement.completedQuantity.toString()
                }
            }.onFailure { message = it.message ?: "Unable to load project requirements." }
        }
    }
    LaunchedEffect(project.id) { reload() }

    SocytoPageHeading(
        "PRIVATE WORK PROGRESS",
        "Update your work plan",
        "Track checklist and quantity completion. The client sees only published snapshots."
    )
    SocytoPrivacyNotice("Changes remain private until you explicitly publish this progress.")
    OutlinedButton(
        onClick = { reload() }, enabled = !loading,
        modifier = Modifier.heightIn(min = 48.dp)
    ) { Text("Refresh requirements") }
    if (requirements.isEmpty() && !loading) Text("No work requirements yet.")
    requirements.forEach { requirement ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(requirement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "EFFORT " + requirement.effortScore +
                        " / 3  ·  " + (if (requirement.type == "checklist") "CHECKLIST" else "QUANTITY"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (requirement.type == "checklist") {
                    Row {
                        Checkbox(
                            checked = requirement.completed,
                            enabled = !loading,
                            onCheckedChange = { checked ->
                                loading = true
                                api.async({
                                    api.updateRequirement(session, project, requirement, checked, 0.0)
                                    api.requirements(session, project)
                                }) { result ->
                                    loading = false
                                    result.onSuccess { requirements = it }
                                        .onFailure { message = it.message ?: "Unable to update task." }
                                }
                            }
                        )
                        Text("Completed", modifier = Modifier.padding(top = 12.dp))
                    }
                } else {
                    Text("Target: " + (requirement.requiredQuantity ?: 0.0) +
                        " " + requirement.unit.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium)
                    val target = requirement.requiredQuantity ?: 0.0
                    if (target > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (requirement.completedQuantity / target).toFloat()
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = editingQuantity[requirement.id] ?: requirement.completedQuantity.toString(),
                        onValueChange = { entered ->
                            editingQuantity = editingQuantity + (requirement.id to entered)
                        },
                        label = { Text("Completed quantity") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(enabled = !loading && (editingQuantity[requirement.id]?.toDoubleOrNull()?.let {
                        it.isFinite() && it >= 0
                    } == true), onClick = {
                        val quantity = editingQuantity[requirement.id]?.toDoubleOrNull() ?: return@Button
                        loading = true
                        api.async({
                            api.updateRequirement(session, project, requirement, false, quantity)
                            api.requirements(session, project)
                        }) { result ->
                            loading = false
                            result.onSuccess { requirements = it }
                                .onFailure { message = it.message ?: "Unable to update quantity." }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save completed quantity") }
                }
            }
        }
    }
    val weightedProgress = if (requirements.isEmpty()) null else {
        val totalEffort = requirements.sumOf { it.effortScore }.toDouble()
        if (totalEffort <= 0) null else
            requirements.sumOf { item ->
                item.effortScore * if (item.type == "checklist") {
                    if (item.completed) 1.0 else 0.0
                } else {
                    val target = item.requiredQuantity ?: 0.0
                    if (target > 0) (item.completedQuantity / target).coerceIn(0.0, 1.0) else 0.0
                }
            } / totalEffort * 100.0
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PRIVATE DRAFT", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                weightedProgress?.let { "%.1f%%".format(it) } ?: "Not defined",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            LinearProgressIndicator(
                progress = { ((weightedProgress ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text("Client progress changes only after you publish this draft.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    SocytoStepCard(
        "SHARE WITH CLIENT",
        "Prepare a publication",
        "Only approved updates will be visible in the client portal."
    ) {
    OutlinedTextField(
        value = publicationNote,
        onValueChange = { publicationNote = it.take(4000) },
        label = { Text("Client update note (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
    Button(
        enabled = !loading && requirements.isNotEmpty(),
        onClick = { confirmation = true },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Review and publish progress") }
    }
    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text("Publish this update?") },
            text = { Text("Your client will see a permanent snapshot of the current project progress and note. Unpublished changes remain private until you publish.") },
            confirmButton = {
                Button(onClick = {
                    confirmation = false
                    loading = true
                    api.async({ api.publishProgress(session, project, publicationNote) }) { result ->
                        loading = false
                        result.onSuccess {
                            message = "Progress published successfully."
                            publicationNote = ""
                            onPublished()
                        }.onFailure { message = it.message ?: "Unable to publish update." }
                    }
                }) { Text("Publish") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmation = false }) { Text("Cancel") } }
        )
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) SocytoFeedback(
        message, isError = message.contains("unable", true) ||
            message.contains("failed", true) ||
            message.contains("could not", true),
        onDismiss = { message = "" })
}
