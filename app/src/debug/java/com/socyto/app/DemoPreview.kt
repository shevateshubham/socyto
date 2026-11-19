package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Offline UI preview. Compiled only in debug builds; no API calls, sessions,
 * authentication bypass or real client data. All records are explicitly fictional.
 */
@Composable
fun DemoPreview(onExit: () -> Unit) {
    var role by remember { mutableStateOf("Super Admin") }
    var selectedFirm by remember { mutableStateOf("SOCYTO (Sample)") }
    var selectedPage by remember { mutableStateOf("Dashboard") }
    var projectName by remember { mutableStateOf("") }
    var sampleAdded by remember { mutableStateOf(false) }
    val olive = if (selectedFirm.startsWith("SOCYTO")) Color(0xFF2E4A2B) else Color(0xFF253F61)
    val gold = if (selectedFirm.startsWith("SOCYTO")) Color(0xFFD4AF7C) else Color(0xFFD6A557)
    val sampleProject = if (sampleAdded) projectName else "Sample Residence"
    MaterialTheme(colorScheme = lightColorScheme(primary = olive, secondary = gold)) {
        Scaffold(topBar = {
            Surface(color = olive) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("SOCYTO · UI PREVIEW", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("OFFLINE DEMO — FICTIONAL DATA ONLY", color = Color.White,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(onClick = onExit) { Text("Exit demo") }
                Text("View as", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Super Admin","Business Admin","Client").forEach { value ->
                        FilterChip(selected = role == value, onClick = {
                            role = value
                            selectedPage = "Dashboard"
                        }, label = { Text(value, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                if (role == "Super Admin") {
                    Text("Platform overview", style = MaterialTheme.typography.headlineSmall)
                    Text("2 sample firms · 2 sample projects · No subscriptions active")
                    listOf("SOCYTO (Sample)","Example Construction (Sample)").forEach { firm ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                            selectedFirm = firm
                            selectedPage = "Branding"
                        }) { Text(firm) }
                    }
                    if (selectedPage == "Branding") {
                        Text("Business branding preview", style = MaterialTheme.typography.titleLarge)
                        Surface(color = olive, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Text(selectedFirm, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("Projects · Clients · Site updates", color = Color.White)
                            }
                        }
                        Text("Primary colour and accent change by firm. The live editor includes logo selection, colour entry and save.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = olive, modifier = Modifier.size(44.dp), shape = MaterialTheme.shapes.small) {}
                            Surface(color = gold, modifier = Modifier.size(44.dp), shape = MaterialTheme.shapes.small) {}
                        }
                    }
                } else if (role == "Business Admin") {
                    Text(selectedFirm, style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = selectedPage == "Dashboard", onClick = { selectedPage = "Dashboard" },
                            label = { Text("Projects") })
                        FilterChip(selected = selectedPage == "Create", onClick = { selectedPage = "Create" },
                            label = { Text("Add project") })
                    }
                    if (selectedPage == "Create") {
                        Text("Create a project (demo only)", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(projectName, { projectName = it },
                            label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
                        Button(enabled = projectName.isNotBlank(), onClick = {
                            sampleAdded = true
                            selectedPage = "Dashboard"
                        }) { Text("Add sample project") }
                    } else {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(sampleProject, style = MaterialTheme.typography.titleMedium)
                                Text("Assigned client: Sample Client")
                                LinearProgressIndicator(progress = { 0.625f }, modifier = Modifier.fillMaxWidth())
                                Text("62.5% draft progress · Checklist + quantity + effort")
                                Text("Materials received: ₹12,500 (fictional)")
                            }
                        }
                        Text("Client records, material receipts, private requirements and publishing controls are part of the connected workspace.")
                    }
                } else {
                    Text("Sample Client Portal", style = MaterialTheme.typography.headlineSmall)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Sample Residence", style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(progress = { 0.5f }, modifier = Modifier.fillMaxWidth())
                            Text("50% published progress")
                            Text("Latest update: Flooring completed (sample)")
                            Text("Clients cannot see supplier prices, invoices or unpublished drafts.")
                        }
                    }
                }
                Text("Preview only · No sign-in, cloud writes or real data.",
                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
