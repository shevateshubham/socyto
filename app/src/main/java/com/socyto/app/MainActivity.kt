package com.socyto.app

import android.os.Bundle
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    var showManagement by remember { mutableStateOf(false) }
    var showBusinessRegistration by remember { mutableStateOf(false) }
    var projectSection by remember { mutableIntStateOf(0) }
    var workspaceTab by remember { mutableStateOf("overview") }
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
                AccountScope.Unassigned -> emptyList()
            }
            val businessId = when (accountScope) {
                is AccountScope.BusinessAdmin -> accountScope.business.id
                AccountScope.Client -> projectList.firstOrNull()?.businessId
                AccountScope.PlatformAdmin, AccountScope.Unassigned -> null
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
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp)
        ),
        colorScheme = lightColorScheme(
            primary = brandPrimary,
            onPrimary = onPrimary,
            secondary = brandSecondary,
            tertiary = brandAccent,
            background = brandBackground,
            onBackground = brandText,
            surface = Color.White,
            onSurface = brandText,
            primaryContainer = brandPrimary.copy(alpha = 0.13f),
            onPrimaryContainer = brandPrimary,
            surfaceVariant = brandPrimary.copy(alpha = 0.07f),
            outlineVariant = brandPrimary.copy(alpha = 0.15f)
        )
    ) {
    Scaffold(
        containerColor = brandBackground,
        topBar = {
            Surface(color = brandPrimary, shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val brandLogo = branding?.logoUrl
                    if (brandLogo == null) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = onPrimary.copy(alpha = 0.14f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("S", color = onPrimary,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    } else {
                        AsyncImage(
                            model = brandLogo,
                            contentDescription = "Business logo",
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            branding?.displayName ?: "SOCYTO",
                            color = onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isLandscape) Text(
                            "DESIGN · EXECUTE · DELIVER",
                            color = onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (session != null && scope != null && scope != AccountScope.Unassigned &&
                selectedProject == null && !editingBranding && !isLandscape) {
                SocytoRoleNavigation(scope!!, workspaceTab) { tab ->
                    workspaceTab = tab
                    showManagement = false
                    showBusinessRegistration = false
                }
            }
        }
    ) { inset ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(inset).imePadding()) {
        val contentPadding = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 600.dp -> 16.dp
            else -> 24.dp
        }
        val contentWidth = if (maxWidth > 880.dp) 880.dp else maxWidth
        Column(
            Modifier.width(contentWidth).fillMaxHeight()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = contentPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (session == null) {
                SocytoWelcomePanel(compact = isLandscape)
                if (!api.isConfigured) {
                    Text("Cloud backend is not configured for this build. No business data is stored locally.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                    LoginForm(enabled = !busy, compact = isLandscape, onSignUp = { email, password ->
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
                                workspaceTab = "overview"
                                loadWorkspace(authenticated, accountScope)
                            }.onFailure { message = it.message ?: "Sign-in failed." }
                        }
                    })
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("WORKSPACE", color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall)
                        Text(when (val role = scope) {
                            AccountScope.PlatformAdmin -> "Super Admin"
                            is AccountScope.BusinessAdmin -> role.business.name
                            AccountScope.Client -> "My projects"
                            AccountScope.Unassigned -> "Awaiting access"
                            null -> "Loading account"
                        }, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
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
                    workspaceTab = "overview"
                    message = ""
                }) { Text("Sign out") }
                }
                val authenticated = session!!
                if (scope == AccountScope.Unassigned) {
                    Text("Signed in, but no firm or project access assigned. Ask your SOCYTO administrator to link your verified account.")
                    OutlinedButton(onClick = {
                        busy = true
                        api.async({ api.accountScope(authenticated) }) { result ->
                            busy = false
                            result.onSuccess {
                                scope = it
                                loadWorkspace(authenticated, it)
                            }.onFailure { message = it.message ?: "Unable to check access." }
                        }
                    }, enabled = !busy) { Text("Check access again") }
                } else if (scope == AccountScope.PlatformAdmin && editingBranding && selectedBusiness != null) {
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
                    SocytoMetric("Registered businesses", businesses.size.toString(),
                        "Independent tenant workspaces")
                    SocytoSectionHeader(
                        title = "Businesses",
                        subtitle = "Firm accounts and tenant management",
                        action = if (showBusinessRegistration) "Close" else "+ New firm",
                        onAction = { showBusinessRegistration = !showBusinessRegistration }
                    )
                    if (businesses.isEmpty() && !busy) SocytoEmptyState(
                        "No businesses yet", "Register a firm to provision its workspace."
                    )
                    businesses.forEach { business ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(business.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(business.status.uppercase(),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall)
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
                            Text("Open business workspace")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (business.status != "active") {
                                OutlinedButton(onClick = {
                                    busy = true
                                    api.async({ api.setBusinessStatus(authenticated, business.id, "active") }) { result ->
                                        busy = false
                                        result.onSuccess { loadWorkspace(authenticated, AccountScope.PlatformAdmin) }
                                            .onFailure { message = it.message ?: "Could not activate business." }
                                    }
                                }, enabled = !busy) { Text("Activate") }
                            }
                            if (business.status == "active") {
                                OutlinedButton(onClick = {
                                    busy = true
                                    api.async({ api.setBusinessStatus(authenticated, business.id, "suspended") }) { result ->
                                        busy = false
                                        result.onSuccess { loadWorkspace(authenticated, AccountScope.PlatformAdmin) }
                                            .onFailure { message = it.message ?: "Could not suspend business." }
                                    }
                                }, enabled = !busy) { Text("Suspend") }
                            }
                        }
                        }
                        }
                    }
                    if (showBusinessRegistration) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                RegisterBusinessForm(
                                    session = authenticated,
                                    api = api,
                                    onRegistered = {
                                        showBusinessRegistration = false
                                        loadWorkspace(authenticated, AccountScope.PlatformAdmin)
                                    }
                                )
                            }
                        }
                    }
                    if (selectedBusiness != null) {
                        OutlinedButton(
                            onClick = { editingBranding = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Edit ${selectedBusiness!!.name} branding") }
                    }
                }
                if (selectedProject == null && workspaceTab == "overview") {
                    when (scope) {
                        AccountScope.PlatformAdmin -> {
                            SocytoEditorialHeader("PLATFORM ADMINISTRATION", "Your business network",
                                "Independent practices, brought together under one secure platform.")
                            SocytoArchitecturalHero(
                                "Every studio. Its own identity.",
                                "SOCYTO PLATFORM",
                                "Manage firms, subscriptions, branding and business access."
                            )
                        }
                        is AccountScope.BusinessAdmin -> {
                            SocytoEditorialHeader("YOUR STUDIO", "A clearer way to work.",
                                "A considered view of your projects and client commitments.")
                            SocytoArchitecturalHero(
                                "Thoughtful spaces. Clear progress.",
                                "YOUR STUDIO AT A GLANCE",
                                "Keep every project organized from first brief to final detail."
                            )
                            SocytoMetric("PROJECT PORTFOLIO", projects.size.toString(),
                                "Real projects in your workspace")
                        }
                        AccountScope.Client -> {
                            SocytoEditorialHeader("YOUR PROJECT PORTAL", "Welcome to your project.",
                                "A clear view of progress shared by your design studio.")
                            SocytoArchitecturalHero(
                                "Every update, beautifully considered.",
                                "CLIENT PROJECT PORTAL",
                                "Only progress explicitly published to you is visible here."
                            )
                        }
                        AccountScope.Unassigned, null -> Unit
                    }
                }
                if (scope is AccountScope.BusinessAdmin && selectedProject == null) {
                    if (workspaceTab == "clients") {
                        SocytoSectionHeader("Clients and new projects",
                            "Manage contacts and add new project sites")
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                BusinessManagementPanel(
                                    business = (scope as AccountScope.BusinessAdmin).business,
                                    session = authenticated,
                                    api = api,
                                    onProjectCreated = { created ->
                                        projects = listOf(created) + projects
                                        selectedProject = created
                                        workspaceTab = "projects"
                                        message = ""
                                    }
                                )
                            }
                        }
                    } else if (workspaceTab == "materials") {
                        SocytoMaterialsOverview(projects, authenticated, api) { project ->
                            loadProject(authenticated, project)
                        }
                    } else if (workspaceTab == "overview" || workspaceTab == "projects") {
                        SocytoSectionHeader(
                            title = if (workspaceTab == "overview") "Projects in focus" else "Project portfolio",
                            subtitle = "Browse the work in your practice",
                            action = "+ Add new",
                            onAction = { workspaceTab = "clients" }
                        )
                    }
                }
                if (selectedProject == null) {
                    val shouldShowProjects = when (scope) {
                        AccountScope.PlatformAdmin -> selectedBusiness != null
                        is AccountScope.BusinessAdmin ->
                            workspaceTab == "overview" || workspaceTab == "projects"
                        AccountScope.Client ->
                            workspaceTab == "overview" || workspaceTab == "projects"
                        else -> false
                    }
                    if (shouldShowProjects) {
                        if (scope !is AccountScope.BusinessAdmin) {
                            SocytoSectionHeader(
                                if (scope == AccountScope.Client) "Your projects" else "Firm projects",
                                "Open a project to see its published updates"
                            )
                        }
                        if (projects.isEmpty() && !busy) SocytoEmptyState(
                            "No projects yet", "Your assigned projects will be listed here."
                        )
                        projects.forEach { project ->
                            SocytoArchitectureProjectCard(project, clientView = scope == AccountScope.Client) {
                                projectSection = 0
                                loadProject(authenticated, project)
                            }
                        }
                    }
                } else {
                    SocytoProjectOverview(
                        name = selectedProject!!.name,
                        updates = updates,
                        readOnly = scope == AccountScope.Client,
                        onBack = { selectedProject = null; updates = emptyList() }
                    )
                    if (scope is AccountScope.BusinessAdmin) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Site", "Progress", "Updates").forEachIndexed { index, title ->
                                FilterChip(
                                    selected = projectSection == index,
                                    onClick = { projectSection = index },
                                    label = { Text(title) }
                                )
                            }
                        }
                        when (projectSection) {
                            0 -> ProjectSiteOperations(selectedProject!!, authenticated, api)
                            1 -> ProjectPublishingPanel(authenticated, api, selectedProject!!) {
                                loadProject(authenticated, selectedProject!!)
                            }
                            else -> {
                                SocytoPublicationTimeline(updates)
                            }
                        }
                    } else {
                        SocytoPublicationTimeline(updates)
                    }
                }
                }
            }
            if (busy) SocytoLoadingPanel()
            if (message.isNotEmpty()) SocytoFeedback(
                message = message,
                isError = message.contains("failed", ignoreCase = true) ||
                    message.contains("unable", ignoreCase = true) ||
                    message.contains("error", ignoreCase = true),
                onDismiss = { message = "" }
            )
        }
    }
    }
    }
}

@Composable
private fun LoginForm(
    enabled: Boolean,
    compact: Boolean = false,
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 12.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!compact) {
                Text("Your workspace", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Sign in with your verified email, or create your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    email, { email = it },
                    label = { Text("Email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onLogin(email, password) },
                    enabled = enabled && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sign in") }
                OutlinedButton(
                    onClick = { onSignUp(email.trim(), password) },
                    enabled = enabled && email.contains("@") && password.length >= 8,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Create account") }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                email, { email = it },
                label = { Text("Email") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                password, { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onLogin(email, password) },
                enabled = enabled && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign in") }
            OutlinedButton(
                onClick = { onSignUp(email.trim(), password) },
                enabled = enabled && email.contains("@") && password.length >= 8,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create account") }
            Text("New accounts have no administrative privileges until approved.")
        }
    }
        }
    }
}
