package com.socyto.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.socyto.app.data.PublishedUpdate

/** Native components transferred from Floot, without fictional production records. */
@Composable
fun SocytoPageHeading(eyebrow: String, title: String, subtitle: String,
                      action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.primary,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(13.dp)) {
                Text(action)
                Spacer(Modifier.width(7.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SocytoProjectOverview(name: String, updates: List<PublishedUpdate>,
                          readOnly: Boolean, onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
            modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text("All projects")
    }
    SocytoPageHeading(
        eyebrow = if (readOnly) "CLIENT PROJECT" else "PROJECT WORKSPACE",
        title = name,
        subtitle = if (readOnly) "Your progress, published by your design studio."
            else "Site records, private drafts and client publications."
    )
    val latest = updates.firstOrNull()
    val progress = latest?.progress?.trim()?.removeSuffix("%")?.trim()?.toFloatOrNull()
        ?.coerceIn(0f,100f)
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(23.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f))
                Spacer(Modifier.width(9.dp))
                Text("LATEST PUBLISHED PROGRESS", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold)
            }
            Text(latest?.progress ?: "Not yet published",
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onPrimary)
            if (progress != null) LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
            )
            Text(latest?.note?.takeIf { it.isNotBlank() }
                    ?: "The first published update will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.90f),
                maxLines = 5, overflow = TextOverflow.Ellipsis)
            if (latest != null) Text("Shared " + latest.publishedAt,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall)
        }
    }
    if (readOnly) SocytoPrivacyNotice(
        "You only see approved progress updates. Unpublished work and material costs remain private.")
}

@Composable
fun SocytoPublicationTimeline(updates: List<PublishedUpdate>) {
    SocytoSectionHeader("Update history", "Progress updates published by the studio")
    if (updates.isEmpty()) {
        SocytoEmptyState("No published updates",
            "Updates appear here after the business administrator publishes them.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        updates.forEachIndexed { index, update ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(35.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index != updates.lastIndex) {
                        Box(Modifier.width(2.dp).heightIn(min = 24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Column(Modifier.weight(1f).padding(bottom = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(update.progress, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(update.publishedAt, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                    if (update.note.isNotBlank()) Text(update.note,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun SocytoPrivacyNotice(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(text, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SocytoFeedback(message: String, isError: Boolean = false, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        color = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(if (isError) Icons.Filled.ErrorOutline else Icons.Filled.Info,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer)
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
fun SocytoLoadingPanel(title: String = "Loading your workspace") {
    Surface(shape = RoundedCornerShape(21.dp), modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Retrieving current records securely",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SocytoStepCard(step: String, title: String, subtitle: String,
                   content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(step.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
