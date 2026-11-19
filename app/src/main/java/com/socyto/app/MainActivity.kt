package com.socyto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.socyto.app.domain.Requirement
import com.socyto.app.domain.RequirementType
import com.socyto.app.domain.calculateProgress

private val olive = Color(0xFF2E4A2B)
private data class LocalProject(val name: String, val client: String, val requirements: List<Requirement>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = olive)) {
                SocytoApp()
            }
        }
    }
}

@Composable
private fun SocytoApp() {
    val projects = remember { mutableStateListOf<LocalProject>() }
    var creating by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LocalProject?>(null) }
    Scaffold(
        topBar = { Surface(color = olive) {
            Text("SOCYTO  ·  DESIGN · EXECUTE · DELIVER",
                color = Color.White, modifier = Modifier.fillMaxWidth().padding(20.dp),
                fontWeight = FontWeight.Bold)
        } }
    ) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                creating -> CreateProject(
                    onBack = { creating = false },
                    onSave = { project -> projects.add(project); creating = false }
                )
                selected != null -> {
                    Text(selected!!.name, style = MaterialTheme.typography.headlineMedium)
                    Text("Client: ${selected!!.client}")
                    val progress = calculateProgress(selected!!.requirements)
                    Text("Initial progress: ${progress?.let { "%.1f%%".format(it) } ?: "Not yet defined"}")
                    Text("Project details are local previews; cloud saving and publishing are not yet enabled.")
                    Button(onClick = { selected = null }) { Text("Back to projects") }
                }
                else -> {
                    Text("Projects", style = MaterialTheme.typography.headlineMedium)
                    Text("Admin workspace · Local prototype")
                    Button(onClick = { creating = true }) { Text("+ Create project") }
                    if (projects.isEmpty()) {
                        Text("No projects yet. Create your first project to begin.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(projects) { project ->
                                OutlinedButton(onClick = { selected = project }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${project.name} · ${project.client}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProject(onBack: () -> Unit, onSave: (LocalProject) -> Unit) {
    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var requirementName by remember { mutableStateOf("") }
    var quantityBased by remember { mutableStateOf(false) }
    var quantityText by remember { mutableStateOf("1") }
    var effort by remember { mutableIntStateOf(2) }
    val requirements = remember { mutableStateListOf<Requirement>() }
    var error by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("New project", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true)
        OutlinedTextField(client, { client = it }, label = { Text("Client name") }, singleLine = true)
        HorizontalDivider()
        Text("Custom work requirements", fontWeight = FontWeight.Bold)
        OutlinedTextField(requirementName, { requirementName = it }, label = { Text("Work requirement") }, singleLine = true)
        Row {
            Checkbox(checked = quantityBased, onCheckedChange = { quantityBased = it })
            Text("Track by quantity", modifier = Modifier.padding(top = 12.dp))
        }
        if (quantityBased) {
            OutlinedTextField(quantityText, { quantityText = it },
                label = { Text("Required quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true)
        }
        Text("Effort: ${when (effort) { 1 -> "Small"; 2 -> "Medium"; else -> "Large" }}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { value ->
                FilterChip(selected = effort == value, onClick = { effort = value }, label = { Text("$value") })
            }
        }
        OutlinedButton(onClick = {
            val quantity = quantityText.toDoubleOrNull()
            if (requirementName.isBlank() || (quantityBased && (quantity == null || !quantity.isFinite() || quantity <= 0))) {
                error = "Enter a requirement and valid positive quantity."
            } else {
                requirements.add(Requirement(
                    title = requirementName.trim(),
                    type = if (quantityBased) RequirementType.QUANTITY else RequirementType.CHECKLIST,
                    effort = effort,
                    requiredQuantity = if (quantityBased) quantity!! else 1.0
                ))
                requirementName = ""
                error = ""
            }
        }) { Text("Add requirement") }
        Text("${requirements.size} requirements added")
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Cancel") }
            Button(enabled = name.isNotBlank() && client.isNotBlank() && requirements.isNotEmpty(),
                onClick = { onSave(LocalProject(name.trim(), client.trim(), requirements.toList())) }) {
                Text("Save project")
            }
        }
    }
}
