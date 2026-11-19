package com.socyto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*

private val deepOlive = Color(0xFF2E4A2B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = deepOlive)) { SocytoApp() }
        }
    }
}

@Composable
private fun SocytoApp() {
    val api = remember { SocytoApi() }
    var session by remember { mutableStateOf<Session?>(null) }
    var scope by remember { mutableStateOf<AccountScope?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf(emptyList<ProjectSummary>()) }
    var businesses by remember { mutableStateOf(emptyList<BusinessSummary>()) }
    var selectedProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var updates by remember { mutableStateOf(emptyList<PublishedUpdate>()) }

    fun loadWorkspace(authenticated: Session, accountScope: AccountScope) {
        busy = true
        api.async({
            val businessList = if (accountScope is AccountScope.PlatformAdmin) api.businesses(authenticated) else emptyList()
            val projectList = when (accountScope) {
                AccountScope.PlatformAdmin -> emptyList()
                is AccountScope.BusinessAdmin -> api.projects(authenticated, accountScope.business.id)
                AccountScope.Client -> api.projects(authenticated)
            }
            businessList to projectList
        }) { result ->
            busy = false
            result.onSuccess { (b, p) -> businesses = b; projects = p }
                .onFailure { message = it.message ?: "Failed to open workspace." }
        }
    }

    fun loadProject(authenticated: Session, project: ProjectSummary) {
        selectedProject = project
        busy = true
        updates = emptyList()
        api.async({ api.publications(authenticated, project) }) { result ->
            busy = false
            result.onSuccess { updates = it }.onFailure { message = it.message ?: "Unable to read published updates." }
        }
    }

    Scaffold(
        topBar = {
            Surface(color = deepOlive) {
                Text("SOCYTO  ·  DESIGN · EXECUTE · DELIVER",
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(20.dp))
            }
        }
    ) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (session == null) {
                Text("Welcome to SOCYTO", style = MaterialTheme.typography.headlineMedium)
                Text("Secure business and project management")
                if (!api.isConfigured) {
                    Text("Cloud backend is not configured for this build. No business data is stored locally.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                    LoginForm(enabled = !busy, onLogin = { email, password ->
                        busy = true
                        message = ""
                        api.async({
                            val authenticated = api.signIn(email.trim(), password)
                            authenticated to api.accountScope(authenticated)
                        }) { result ->
                            busy = false
                            result.onSuccess { (authenticated, accountScope) ->
                                session = authenticated
                                scope = accountScope
                                loadWorkspace(authenticated, accountScope)
                            }.onFailure { message = it.message ?: "Sign-in failed." }
                        }
                    })
                }
            } else {
                Text(when (val role = scope) {
                    AccountScope.PlatformAdmin -> "Super Admin"
                    is AccountScope.BusinessAdmin -> role.business.name
                    AccountScope.Client -> "My projects"
                    null -> "Loading account"
                }, style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = {
                    session = null
                    scope = null
                    projects = emptyList()
                    businesses = emptyList()
                    selectedProject = null
                    updates = emptyList()
                    message = ""
                }) { Text("Sign out") }
                val authenticated = session!!
                if (scope == AccountScope.PlatformAdmin) {
                    Text("Subscribed businesses", fontWeight = FontWeight.SemiBold)
                    if (businesses.isEmpty() && !busy) Text("No businesses provisioned.")
                    businesses.forEach { business ->
                        OutlinedButton(onClick = {
                            busy = true
                            api.async({ api.projects(authenticated, business.id) }) { result ->
                                busy = false
                                result.onSuccess { projects = it; selectedProject = null }
                                    .onFailure { message = it.message ?: "Unable to load projects." }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(business.name)
                        }
                    }
                }
                if (selectedProject == null) {
                    Text("Projects", fontWeight = FontWeight.SemiBold)
                    if (projects.isEmpty() && !busy) Text("No accessible projects.")
                    projects.forEach { project ->
                        OutlinedButton(
                            onClick = { loadProject(authenticated, project) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(project.name) }
                    }
                    if (scope is AccountScope.BusinessAdmin) {
                        Text("Project editing, material receipts and publication controls will be enabled after secure backend write workflows are connected.",
                            color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    Text(selectedProject!!.name, style = MaterialTheme.typography.titleLarge)
                    Text("Published project updates", fontWeight = FontWeight.SemiBold)
                    if (updates.isEmpty() && !busy) Text("No updates have been published.")
                    updates.forEach { update ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(update.progress, fontWeight = FontWeight.Bold)
                                Text(update.note)
                                Text(update.publishedAt, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    OutlinedButton(onClick = { selectedProject = null; updates = emptyList() }) {
                        Text("Back to projects")
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LoginForm(enabled: Boolean, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    OutlinedTextField(email, { email = it }, label = { Text("Email") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, { password = it }, label = { Text("Password") },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth())
    Button(onClick = { onLogin(email, password) },
        enabled = enabled && email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
}
