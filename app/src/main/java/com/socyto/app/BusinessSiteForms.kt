package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*

/**
 * Business-admin operations. Business ID originates from verified membership.
 * All mutations are protected again by database RLS.
 */
@Composable
fun BusinessManagementPanel(
    business: BusinessSummary,
    session: Session,
    api: SocytoApi,
    onProjectCreated: (ProjectSummary) -> Unit
) {
    var clients by remember(business.id) { mutableStateOf(emptyList<ClientSummary>()) }
    var clientName by remember(business.id) { mutableStateOf("") }
    var clientEmail by remember(business.id) { mutableStateOf("") }
    var projectName by remember(business.id) { mutableStateOf("") }
    var projectType by remember(business.id) { mutableStateOf("") }
    var projectLocation by remember(business.id) { mutableStateOf("") }
    var selectedClientId by remember(business.id) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(business.id) {
        api.async({ api.clients(session, business.id) }) { result ->
            result.onSuccess { clients = it }.onFailure { message = it.message ?: "Unable to load clients" }
        }
    }

    SocytoSectionHeader("New client", "Add a client to your business workspace")
    OutlinedTextField(
        value = clientName, onValueChange = { clientName = it },
        label = { Text("Client name") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = clientEmail, onValueChange = { clientEmail = it },
        label = { Text("Client email (optional)") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Button(
        enabled = !loading && ProjectInputRules.validName(clientName) &&
            ProjectInputRules.validEmail(clientEmail),
        onClick = {
            loading = true
            message = ""
            api.async({ api.createClient(session, business.id, clientName, clientEmail) }) { result ->
                loading = false
                result.onSuccess { client ->
                    clients = clients + client
                    selectedClientId = client.id
                    clientName = ""
                    clientEmail = ""
                    message = "Client saved to database."
                }.onFailure { message = it.message ?: "Unable to save client." }
            }
        }
    ) { Text("Save client") }

    HorizontalDivider()
    SocytoSectionHeader("New project", "Connect a project to an existing client")
    if (clients.isEmpty()) {
        Text("Create a client first, then assign the site to that client.")
    } else {
        Text("Select the project's client")
        clients.forEach { client ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(
                    selected = selectedClientId == client.id,
                    onClick = { selectedClientId = client.id }
                )
                Text(client.name, modifier = Modifier.padding(top = 12.dp))
            }
            OutlinedButton(enabled = !loading, onClick = {
                loading = true
                message = ""
                api.async({ api.linkClientLogin(session, business.id, client.id) }) { result ->
                    loading = false
                    result.onSuccess { message = "Verified login linked for ${client.name}." }
                        .onFailure { message = it.message ?: "Client login linking failed." }
                }
            }) { Text("Link verified login — ${client.name}") }
        }
        OutlinedTextField(
            projectName, { projectName = it }, label = { Text("Project name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            projectType, { projectType = it }, label = { Text("Project type (optional)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            projectLocation, { projectLocation = it }, label = { Text("Site location (optional)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Button(
            enabled = !loading && selectedClientId != null &&
                ProjectInputRules.validName(projectName),
            onClick = {
                val clientId = selectedClientId ?: return@Button
                loading = true
                message = ""
                api.async({
                    api.createProject(
                        session, business.id, clientId, projectName,
                        projectType, projectLocation
                    )
                }) { result ->
                    loading = false
                    result.onSuccess { project ->
                        projectName = ""
                        projectType = ""
                        projectLocation = ""
                        message = "Project saved to database."
                        onProjectCreated(project)
                    }.onFailure { message = it.message ?: "Unable to save project." }
                }
            }
        ) { Text("Create project") }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message)
}

@Composable
fun ProjectSiteOperations(
    project: ProjectSummary,
    session: Session,
    api: SocytoApi
) {
    var receiptName by remember(project.id) { mutableStateOf("") }
    var receiptQuantity by remember(project.id) { mutableStateOf("") }
    var receiptUnit by remember(project.id) { mutableStateOf("") }
    var receiptRate by remember(project.id) { mutableStateOf("") }
    var supplier by remember(project.id) { mutableStateOf("") }
    var receiptDate by remember(project.id) { mutableStateOf("") }
    var receiptList by remember(project.id) { mutableStateOf(emptyList<MaterialReceiptSummary>()) }
    var requirementName by remember(project.id) { mutableStateOf("") }
    var quantityBased by remember(project.id) { mutableStateOf(false) }
    var requiredQuantity by remember(project.id) { mutableStateOf("") }
    var requirementUnit by remember(project.id) { mutableStateOf("") }
    var effort by remember(project.id) { mutableIntStateOf(2) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var activeSection by remember(project.id) { mutableIntStateOf(0) }

    fun reloadReceipts() {
        api.async({ api.materialReceipts(session, project) }) { result ->
            result.onSuccess { receiptList = it }
                .onFailure { message = it.message ?: "Could not load received materials." }
        }
    }
    LaunchedEffect(project.id) { reloadReceipts() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = activeSection == 0,
            onClick = { activeSection = 0 },
            label = { Text("Requirements") }
        )
        FilterChip(
            selected = activeSection == 1,
            onClick = { activeSection = 1 },
            label = { Text("Materials") }
        )
    }
    if (activeSection == 0) {
    SocytoSectionHeader("Work requirements", "Private drafts until explicitly published")
    Text("Draft requirements are private until published through an approved update.")
    OutlinedTextField(requirementName, { requirementName = it },
        label = { Text("Requirement description") }, modifier = Modifier.fillMaxWidth())
    Row {
        Checkbox(quantityBased, { quantityBased = it })
        Text("Track by quantity", modifier = Modifier.padding(top = 12.dp))
    }
    if (quantityBased) {
        OutlinedTextField(requiredQuantity, { requiredQuantity = it },
            label = { Text("Required quantity") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(requirementUnit, { requirementUnit = it },
            label = { Text("Unit (e.g. sq ft)") }, modifier = Modifier.fillMaxWidth())
    }
    Text("Effort score: $effort (1 small · 2 medium · 3 large)")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..3).forEach { value ->
            FilterChip(selected = effort == value, onClick = { effort = value },
                label = { Text("$value") })
        }
    }
    Button(
        enabled = !busy && ProjectInputRules.validName(requirementName) &&
            (!quantityBased || (ProjectInputRules.validQuantity(requiredQuantity.toDoubleOrNull() ?: 0.0)
                && requirementUnit.isNotBlank())),
        onClick = {
            busy = true
            api.async({
                if (quantityBased) api.addQuantityRequirement(session, project, requirementName,
                    effort, requiredQuantity.toDouble(), requirementUnit)
                else api.addChecklistRequirement(session, project, requirementName, effort)
            }) { result ->
                busy = false
                result.onSuccess { requirementName = ""; requiredQuantity = ""; message = "Requirement saved as private draft." }
                    .onFailure { message = it.message ?: "Unable to save requirement." }
            }
        }
    ) { Text("Save requirement") }

    } else {
    SocytoSectionHeader("Material expenditure", "Calculated from actual received goods")
    val totalExpenditure = receiptList.sumOf { it.amount }
    Text("Expenditure from received materials: ₹" + String.format("%.2f", totalExpenditure))
    receiptList.forEach { receipt ->
        Text("${receipt.materialName} · ${receipt.quantity} ${receipt.unit} · ₹${String.format("%.2f", receipt.amount)}")
    }
    OutlinedTextField(receiptName, { receiptName = it }, label = { Text("Material name") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(receiptQuantity, { receiptQuantity = it }, label = { Text("Quantity received") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(receiptUnit, { receiptUnit = it }, label = { Text("Unit") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(receiptRate, { receiptRate = it }, label = { Text("Unit rate (₹)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(supplier, { supplier = it }, label = { Text("Supplier (optional)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(receiptDate, { receiptDate = it },
        label = { Text("Received date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
    Button(
        enabled = !busy && ProjectInputRules.validName(receiptName) &&
            ProjectInputRules.validQuantity(receiptQuantity.toDoubleOrNull() ?: 0.0) &&
            ProjectInputRules.validRate(receiptRate.toDoubleOrNull() ?: -1.0) &&
            receiptUnit.isNotBlank() && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(receiptDate),
        onClick = {
            busy = true
            api.async({
                api.addMaterialReceipt(session, project, receiptName,
                    receiptQuantity.toDouble(), receiptUnit, receiptRate.toDouble(),
                    supplier, receiptDate)
            }) { result ->
                busy = false
                result.onSuccess {
                    receiptName = ""; receiptQuantity = ""; receiptRate = ""
                    receiptUnit = ""; supplier = ""; receiptDate = ""
                    message = "Material receipt saved."
                    reloadReceipts()
                }.onFailure { message = it.message ?: "Unable to save material receipt." }
            }
        }
    ) { Text("Save material receipt") }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message)
}
