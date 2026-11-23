package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import android.app.DatePickerDialog
import java.time.LocalDate
import java.util.Locale
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
    var intakeTab by remember(business.id) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var oneTimeCredentials by remember(business.id) { mutableStateOf<ClientTemporaryCredentials?>(null) }

    LaunchedEffect(business.id) {
        api.async({ api.clients(session, business.id) }) { result ->
            result.onSuccess { clients = it }.onFailure { message = it.message ?: "Unable to load clients" }
        }
    }

    if (oneTimeCredentials != null) {
        SocytoOneTimeCredentials(oneTimeCredentials!!) {
            oneTimeCredentials = null
        }
    } else {
    SocytoSectionHeader(
        "Set up a project",
        "Create a client first, then connect their project to your practice"
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = intakeTab == 0, onClick = { intakeTab = 0 },
            label = { Text("1 · Client") })
        FilterChip(selected = intakeTab == 1, onClick = { intakeTab = 1 },
            label = { Text("2 · Project") })
    }
    if (intakeTab == 0) {
    SocytoStepCard("Step 01 · Client details", "Who are we designing for?",
        "Create a client or select an existing client in the next step.") {
    OutlinedTextField(
        value = clientName, onValueChange = { clientName = it },
        label = { Text("Client name") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = clientEmail, onValueChange = { clientEmail = it },
        label = { Text("Client email (required for login)") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Button(
        enabled = !loading && ProjectInputRules.validName(clientName) &&
            clientEmail.isNotBlank() && ProjectInputRules.validEmail(clientEmail),
        onClick = {
            loading = true
            message = ""
            api.async({ api.createClient(session, business.id, clientName, clientEmail) }) { result ->
                loading = false
                result.onSuccess { client ->
                    clients = clients + client
                    selectedClientId = client.id
                    intakeTab = 1
                    clientName = ""
                    clientEmail = ""
                    message = "Client saved to database."
                }.onFailure { message = it.message ?: "Unable to save client." }
            }
        }
    , modifier = Modifier.fillMaxWidth()) { Text("Save client & continue") }
    }
    } else {
    SocytoStepCard("Step 02 · Project details", "Start a new project",
        "Choose an existing client and describe the site.") {
    if (clients.isEmpty()) {
        SocytoEmptyState("No clients yet", "Add a client before creating their project.")
        OutlinedButton(onClick = { intakeTab = 0 }) { Text("Add a client") }
    } else {
        Text("SELECT CLIENT", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary)
        clients.forEach { client ->
            Surface(
                onClick = { selectedClientId = client.id },
                modifier = Modifier.fillMaxWidth(),
                color = if (selectedClientId == client.id)
                    MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RadioButton(selected = selectedClientId == client.id,
                        onClick = { selectedClientId = client.id })
                    Text(client.name, Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val selectedClient = clients.firstOrNull { it.id == selectedClientId }
        if (selectedClient != null) {
            Text(
                "After creating a project, SOCYTO automatically creates a new client login " +
                    "when this client has an unused email. Existing users keep their passwords.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                onClick = {
                    loading = true
                    message = ""
                    api.async({
                        api.provisionClientLogin(session, business.id, selectedClient.id)
                    }) { result ->
                        loading = false
                        result.onSuccess { credentials ->
                            oneTimeCredentials = credentials
                        }.onFailure {
                            message = it.message ?: "Unable to create client login."
                        }
                    }
                }
            ) { Text("Issue temporary login for an existing project") }
            TextButton(
                onClick = {
                    loading = true
                    message = ""
                    api.async({
                        api.linkClientLogin(session, business.id, selectedClient.id)
                    }) { result ->
                        loading = false
                        result.onSuccess {
                            message = "Existing verified login linked for " + selectedClient.name
                        }.onFailure {
                            message = it.message ?: "Client login linking failed."
                        }
                    }
                }
            ) { Text("Already has an account? Link verified login") }
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
                    val project = api.createProject(
                        session, business.id, clientId, projectName,
                        projectType, projectLocation
                    )
                    // Provisioning failure must not delete or duplicate the saved project.
                    project to runCatching {
                        api.provisionClientLogin(session, business.id, clientId)
                    }
                }) { result ->
                    loading = false
                    result.onSuccess { (project, provisionResult) ->
                        projectName = ""
                        projectType = ""
                        projectLocation = ""
                        onProjectCreated(project)
                        provisionResult.onSuccess { credentials ->
                            oneTimeCredentials = credentials
                            message = ""
                        }.onFailure { problem ->
                            message = "Project saved. Client login was not issued: " +
                                (problem.message ?: "Try issuing temporary login.")
                        }
                    }.onFailure { message = it.message ?: "Unable to save project." }
                }
            }
        , modifier = Modifier.fillMaxWidth()) { Text("Create project") }
    }
    }
    }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) SocytoFeedback(message,
        isError = message.contains("unable",true) || message.contains("failed",true) ||
            message.contains("not issued", true),
        onDismiss = { message = "" })
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
    var receiptDate by remember(project.id) {
        mutableStateOf(LocalDate.now().toString())
    }
    val context = LocalContext.current
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
    SocytoStepCard("Private work plan", "Add a requirement",
        "Draft work stays invisible to clients until you publish progress.") {
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
    , modifier = Modifier.fillMaxWidth()) { Text("Add requirement") }
    }

    } else {
    SocytoSectionHeader("Material expenditure", "Calculated from actual received goods")
    val totalExpenditure = receiptList.sumOf { it.amount }
    SocytoMetric(
        "Received material expenditure",
        "₹" + String.format("%,.2f", totalExpenditure),
        "${receiptList.size} recorded material receipt(s)"
    )
    receiptList.forEach { receipt ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(receipt.materialName, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("${receipt.quantity} ${receipt.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("₹" + String.format("%,.2f", receipt.amount),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    SocytoStepCard("Actual received goods", "Record a material receipt",
        "This receipt contributes to the project's real expenditure.") {
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
    Text("Date received: " + receiptDate,
        style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = {
        val date = runCatching { LocalDate.parse(receiptDate) }
            .getOrElse { LocalDate.now() }
        DatePickerDialog(context, { _, year, month, day ->
            receiptDate = String.format(Locale.ROOT, "%04d-%02d-%02d",
                year, month + 1, day)
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("Choose received date") }
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
                    receiptUnit = ""; supplier = ""; receiptDate = LocalDate.now().toString()
                    message = "Material receipt saved."
                    reloadReceipts()
                }.onFailure { message = it.message ?: "Unable to save material receipt." }
            }
        }
    , modifier = Modifier.fillMaxWidth()) { Text("Save material receipt") }
    }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) SocytoFeedback(message,
        isError = message.contains("unable", true) ||
            message.contains("failed", true) ||
            message.contains("could not", true),
        onDismiss = { message = "" })
}
