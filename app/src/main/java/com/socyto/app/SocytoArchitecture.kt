package com.socyto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*
import java.text.NumberFormat
import java.util.Locale

private val StudioShape = RoundedCornerShape(22.dp)
private val CardShape = RoundedCornerShape(18.dp)

/** Floot design handoff: native architecture geometry and editorial hierarchy.
 * No fictional project, percentage, client or financial data is ever used here. */
@Composable
fun SocytoEditorialHeader(eyebrow: String, heading: String, description: String) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(eyebrow.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            color = MaterialTheme.colorScheme.secondary)
        Text(heading, style = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = FontFamily.Serif),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SocytoArchitecturalHero(
    headline: String,
    kicker: String,
    description: String,
    compact: Boolean = false
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Surface(Modifier.fillMaxWidth(), shape = StudioShape, color = primary) {
        Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 158.dp else 198.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val line = onPrimary.copy(alpha = 0.15f)
                val accent = onPrimary.copy(alpha = 0.28f)
                val w = size.width
                val h = size.height
                val left = w * .72f
                drawLine(line, Offset(left, 0f), Offset(left, h), 1.3.dp.toPx())
                drawLine(line, Offset(w * .85f, 0f), Offset(w * .85f, h), 1.dp.toPx())
                drawLine(line, Offset(0f, h * .72f), Offset(w, h * .72f), 1.dp.toPx())
                drawCircle(line, radius = h * .53f, center = Offset(w * .95f, h * .45f),
                    style = Stroke(width = 1.3.dp.toPx()))
                drawRect(accent, topLeft = Offset(w * .78f, h * .48f),
                    size = Size(w * .4f, h * .58f),
                    style = Stroke(width = 1.5.dp.toPx()))
                drawLine(accent, Offset(w * .86f, h * .08f),
                    Offset(w * .68f, h * .94f), 1.dp.toPx())
            }
            Column(Modifier.fillMaxWidth(.85f)
                .padding(if (compact) 18.dp else 25.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(kicker.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onPrimary.copy(alpha = .8f))
                Text(headline, style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif),
                    color = onPrimary, lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = onPrimary.copy(alpha = .82f),
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SocytoArchitectureProjectCard(project: ProjectSummary, clientView: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = CardShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(93.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)) {
                val stroke = MaterialTheme.colorScheme.primary.copy(alpha = .23f)
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(stroke, topLeft = Offset(w * .15f, h * .06f),
                        size = Size(w * .5f, h * 1.3f),
                        style = Stroke(width = 1.4.dp.toPx()))
                    drawLine(stroke, Offset(w * .29f, 0f),
                        Offset(w * .29f, h), 1.5.dp.toPx())
                    drawLine(stroke, Offset(w * .62f, 0f),
                        Offset(w * .86f, h), 1.5.dp.toPx())
                    drawLine(stroke, Offset(0f, h * .69f),
                        Offset(w, h * .69f), 1.dp.toPx())
                    drawCircle(stroke, radius = h * .56f,
                        center = Offset(w * .91f, h * .92f),
                        style = Stroke(width = 1.dp.toPx()))
                }
                Surface(
                    modifier = Modifier.padding(12.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)
                ) {
                    Text("PROJECT", Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.padding(17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(project.name, fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(if (clientView) "View published project updates" else "Open project workspace",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("↗", Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun SocytoRoleNavigation(
    scope: AccountScope,
    selected: String,
    onChange: (String) -> Unit
) {
    val tabs = when (scope) {
        AccountScope.PlatformAdmin -> listOf(
            Triple("overview", "Overview", Icons.Filled.Home),
            Triple("businesses", "Businesses", Icons.Filled.Business))
        is AccountScope.BusinessAdmin -> listOf(
            Triple("overview", "Overview", Icons.Filled.Home),
            Triple("projects", "Projects", Icons.Filled.Folder),
            Triple("clients", "Clients", Icons.Filled.People),
            Triple("materials", "Materials", Icons.Filled.Inventory2))
        AccountScope.Client -> listOf(
            Triple("overview", "Overview", Icons.Filled.Home),
            Triple("projects", "Projects", Icons.Filled.Folder))
        AccountScope.Unassigned -> emptyList()
    }
    if (tabs.isEmpty()) return
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        tabs.forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onChange(tab) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, maxLines = 1) },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
fun SocytoMaterialsOverview(
    projects: List<ProjectSummary>,
    session: Session,
    api: SocytoApi,
    onOpenProject: (ProjectSummary) -> Unit
) {
    var receipts by remember(projects.map { it.id }) {
        mutableStateOf<Map<String,List<MaterialReceiptSummary>>>(emptyMap())
    }
    var loading by remember(projects.map { it.id }) { mutableStateOf(true) }
    var error by remember(projects.map { it.id }) { mutableStateOf<String?>(null) }
    LaunchedEffect(projects.map { it.id }) {
        if (projects.isEmpty()) { loading = false; return@LaunchedEffect }
        loading = true
        api.async({
            projects.associate { it.id to api.materialReceipts(session, it) }
        }) { result ->
            loading = false
            result.onSuccess { receipts = it; error = null }
                .onFailure { error = it.message ?: "Could not load material receipts." }
        }
    }
    SocytoEditorialHeader("MATERIAL LEDGER", "Received material expenditure",
        "Amounts are calculated from actual receipts in your projects.")
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    if (!loading && error == null) {
        val amount = receipts.values.flatten().sumOf { it.amount }
        val rupees = NumberFormat.getCurrencyInstance(Locale("en","IN")).format(amount)
        SocytoMetric("RECEIVED MATERIALS", rupees,
            "${receipts.values.sumOf { it.size }} recorded receipt(s)")
        projects.forEach { project ->
            val items = receipts[project.id] ?: emptyList()
            Surface(
                onClick = { onOpenProject(project) },
                shape = CardShape,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(Modifier.padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${items.size} receipt(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(NumberFormat.getCurrencyInstance(Locale("en","IN"))
                        .format(items.sumOf { it.amount }),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
