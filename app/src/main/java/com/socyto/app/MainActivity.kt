package com.socyto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
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
private fun hexColor(value: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SocytoApp()
        }
    }
}

@Composable
private fun SocytoApp() {
    val api = remember { SocytoApi() }
    var session by remember { mutableStateOf<Session?>(null) }
    var branding by remember { mutableStateOf<BusinessBranding?>(null) }
    val brandPrimary = hexColor(branding?.primaryColorHex ?: "#2E4A2B", deepOlive)
    val brandBackground = hexColor(branding?.backgroundColorHex ?: "#F7F5F0", Color.White)
    val brandText = hexColor(branding?.textColorHex ?: "#1F1F1F", Color.Black)
    val brandAccent = hexColor(branding?.accentColorHex ?: "#D4AF7C", Color(0xFFD4AF7C))
    val brandSecondary = hexColor(branding?.secondaryColorHex ?: "#6B8E49", Color(0xFF6B8E49))
    val onPrimary = if (android.graphics.Color.luminance(
        android.graphics.Color.parseColor(branding?.primaryColorHex ?: "#2E4A2B")
    ) > 0.4f) Color.Black else Color.White

    var scope by remember { mutableStateOf<AccountScope?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf(emptyList<ProjectSummary>()) }
    var businesses by remember { mutableStateOf(emptyList<BusinessSummary>()) }
    var selectedBusiness by remember { mutableStateOf<BusinessSummary?>(null) }
    var editingBranding by remember { mutableStateOf(false) }
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
            val businessId = when (accountScope) {
                is AccountScope.BusinessAdmin -> accountScope.business.id
                AccountScope.Client -> projectList.firstOrNull()?.businessId
                AccountScope.PlatformAdmin -> null
            }
            Triple(businessList, projectList, businessId?.let { api.branding(authenticated, it) })
        }) { result ->
            busy = false
            result.onSuccess { (b, p, tenantBrand) ->
                businesses = b
                projects = p
                branding = tenantBrand
            }
                .onFailure { message = it.message ?: "Failed to open workspace." }
        }
    }

    fun loadProject(authenticated: Session, project: ProjectSummary) {
        selectedProject = project
        busy = true
        updates = emptyList()
        api.async({
            api.publications(authenticated, project) to api.branding(authenticated, project.businessId)
        }) { result ->
            busy = false
            result.onSuccess { (published, tenantBrand) ->
                updates = published
                branding = tenantBrand
            }.onFailure { message = it.message ?: "Unable to read published updates." }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = brandPrimary,
            onPrimary = onPrimary,
            secondary = brandSecondary,
            tertiary = brandAccent,
            background = brandBackground,
            onBackground = brandText,
            surface = brandBackground,
            onSurface = brandText
        )
    ) {
    Scaffold(
        containerColor = brandBackground,
        topBar = {
            Surface(color = brandPrimary) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val brandLogo = branding?.logoUrl
                    if (brandLogo != null) {
                        AsyncImage(
                            model = brandLogo,
                            contentDescription = "Business logo",
                            modifier = Modifier.size(42.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column {
                        Text(
                            branding?.displayName ?: "Project Workspace",
                            color = onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "DESIGN · EXECUTE · DELIVER",
                            color = onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    ) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (session == null) {
                Text("Welcome", style = MaterialTheme.typography.headlineMedium)
                Text("Secure business and project management")
                if (!api.isConfigured) {
                    Text("Cloud backend is not configured for this build. No business data is stored locally.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                    LoginForm(enabled = !busy, onSignUp = { email, password ->
                        busy = true
                        message = ""
                        api.async({ api.signUp(email, password) }) { result ->
                            busy = false
                            result.onSuccess {
                                message = "Verify the email sent to you before signing in. An administrator must assign access."
                            }.onFailure { message = it.message ?: "Sign-up failed." }
                        }
                    }, onLogin = { email, password ->
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
                    branding = null
                    scope = null
                    projects = emptyList()
                    businesses = emptyList()
                    selectedBusiness = null
                    editingBranding = false
                    selectedProject = null
                    updates = emptyList()
                    message = ""
                }) { Text("Sign out") }
                val authenticated = session!!
                if (scope == AccountScope.PlatformAdmin && editingBranding && selectedBusiness != null) {
                    val firm = selectedBusiness!!
                    BusinessBrandEditor(
                        business = firm,
                        current = branding?.takeIf { it.businessId == firm.id }?.let {
                            if (it.displayName == "Project workspace") it.copy(displayName = firm.name) else it
                        } ?: BusinessBranding(firm.id, firm.name),
                        session = authenticated,
                        api = api,
                        onSaved = { updated ->
                            branding = updated
                            editingBranding = false
                            message = ""
                        },
                        onCancel = { editingBranding = false }
                    )
                } else {
                if (scope == AccountScope.PlatformAdmin) {
                    Text("Subscribed businesses", fontWeight = FontWeight.SemiBold)
                    if (businesses.isEmpty() && !busy) Text("No businesses provisioned.")
                    businesses.forEach { business ->
                        OutlinedButton(onClick = {
                            busy = true
                            api.async({
                                api.projects(authenticated, business.id) to api.branding(authenticated, business.id)
                            }) { result ->
                                busy = false
                                result.onSuccess { (p, tenantBrand) ->
                                    selectedBusiness = business
                                    editingBranding = false
                                    projects = p
                                    branding = tenantBrand
                                    selectedProject = null
                                }
                                    .onFailure { message = it.message ?: "Unable to load projects." }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(business.name)
                        }
                    }
                    RegisterBusinessForm(
                        session = authenticated,
                        api = api,
                        onRegistered = { loadWorkspace(authenticated, AccountScope.PlatformAdmin) }
                    )
                    if (selectedBusiness != null) {
                        OutlinedButton(
                            onClick = { editingBranding = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Edit ${selectedBusiness!!.name} branding") }
                    }
                }
                if (scope is AccountScope.BusinessAdmin && selectedProject == null) {
                    BusinessManagementPanel(
                        business = (scope as AccountScope.BusinessAdmin).business,
                        session = authenticated,
                        api = api,
                        onProjectCreated = { created ->
                            projects = listOf(created) + projects
                            selectedProject = created
                            message = ""
                        }
                    )
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

                } else {
                    Text(selectedProject!!.name, style = MaterialTheme.typography.titleLarge)
                    if (scope is AccountScope.BusinessAdmin) {
                        ProjectSiteOperations(selectedProject!!, authenticated, api)
                        ProjectPublishingPanel(authenticated, api, selectedProject!!) {
                            loadProject(authenticated, selectedProject!!)
                        }
                    }
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
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
    }
}

@Composable
private fun LoginForm(enabled: Boolean, onLogin: (String, String) -> Unit, onSignUp: (String, String) -> Unit) {
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
    OutlinedButton(
        onClick = { onSignUp(email.trim(), password) },
        enabled = enabled && email.contains("@") && password.length >= 8,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Create account") }
    Text("New accounts have no administrative privileges until approved.")
}
