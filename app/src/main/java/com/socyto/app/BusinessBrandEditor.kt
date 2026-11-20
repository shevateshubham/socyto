package com.socyto.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.socyto.app.data.*
import java.io.ByteArrayOutputStream

private fun parsedColor(value: String, fallback: Color = Color.DarkGray): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)

/** Platform-admin-only UI. Server RLS and RPC enforce the actual permissions. */
@Composable
fun BusinessBrandEditor(
    business: BusinessSummary,
    current: BusinessBranding,
    session: Session,
    api: SocytoApi,
    onSaved: (BusinessBranding) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var displayName by remember(business.id) { mutableStateOf(current.displayName) }
    var primary by remember(business.id) { mutableStateOf(current.primaryColorHex) }
    var secondary by remember(business.id) { mutableStateOf(current.secondaryColorHex) }
    var accent by remember(business.id) { mutableStateOf(current.accentColorHex) }
    var background by remember(business.id) { mutableStateOf(current.backgroundColorHex) }
    var bodyText by remember(business.id) { mutableStateOf(current.textColorHex) }
    var chosenLogo by remember(business.id) { mutableStateOf<Uri?>(null) }
    var logoPath by remember(business.id) { mutableStateOf(current.logoStoragePath) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        chosenLogo = uri
        suggestions = emptyList()
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                        inSampleSize = 8
                    })?.let { LogoPalette.suggestions(it) }
                } ?: emptyList()
            }.onSuccess { suggestions = it }
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Brand settings", style = MaterialTheme.typography.headlineMedium)
        Text("Firm: ${business.name}", fontWeight = FontWeight.SemiBold)
        Text("Only the Super Admin can change this firm's appearance.")
        OutlinedTextField(
            value = displayName, onValueChange = { displayName = it },
            label = { Text("Business display name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Text("Company logo", fontWeight = FontWeight.Medium)
        val previewLogo = chosenLogo ?: current.logoUrl
        if (previewLogo != null) {
            AsyncImage(
                model = previewLogo,
                contentDescription = "Selected firm logo preview",
                modifier = Modifier.size(96.dp)
            )
        }
        OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !saving) {
            Text("Choose logo (PNG, JPEG, WebP; max 2 MB)")
        }
        if (suggestions.isNotEmpty()) {
            Text("Suggested colours from logo", fontWeight = FontWeight.Medium)
            Text("Select a suggestion to use it as the primary colour.")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                suggestions.forEach { proposed ->
                    FilterChip(
                        selected = primary == proposed,
                        onClick = { primary = proposed },
                        label = { Text(proposed) }
                    )
                }
            }
        }
        Text("Brand colour palette", fontWeight = FontWeight.SemiBold)
        BrandColorInput("Primary", primary) { primary = it }
        BrandColorInput("Secondary", secondary) { secondary = it }
        BrandColorInput("Accent", accent) { accent = it }
        BrandColorInput("Background", background) { background = it }
        BrandColorInput("Text", bodyText) { bodyText = it }
        Text("Live preview", fontWeight = FontWeight.SemiBold)
        Surface(color = parsedColor(background), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = parsedColor(primary), shape = MaterialTheme.shapes.small) {
                    Text(
                        displayName.ifBlank { business.name },
                        modifier = Modifier.padding(12.dp),
                        color = if (android.graphics.Color.luminance(
                            android.graphics.Color.parseColor(
                                BrandValidator.colorOrDefault(primary, "#2E4A2B"))
                        ) > 0.4f) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("Projects and site progress",
                    color = parsedColor(bodyText))
                Text("Accent •", color = parsedColor(accent))
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !saving) { Text("Cancel") }
            Button(onClick = {
                val colors = listOf(primary, secondary, accent, background, bodyText)
                val valid = Regex("^#[0-9A-Fa-f]{6}$")
                if (displayName.trim().length !in 1..80 || colors.any { !valid.matches(it) }) {
                    error = "Use a business name and five valid #RRGGBB colours."
                } else {
                    saving = true
                    error = ""
                    val selectedUri = chosenLogo
                    api.async({
                        val uploadedPath = if (selectedUri != null) {
                            val mime = context.contentResolver.getType(selectedUri)
                                ?: throw IllegalArgumentException("Unsupported logo format.")
                            val image = context.contentResolver.openInputStream(selectedUri)?.use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var total = 0
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= 2 * 1024 * 1024) {
                                        "Logo must be smaller than 2 MB."
                                    }
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            } ?: throw IllegalArgumentException("Unable to read logo image.")
                            api.uploadBusinessLogo(session, business.id, image, mime)
                        } else logoPath
                        val revised = BusinessBranding(
                            businessId = business.id,
                            displayName = displayName.trim(),
                            logoStoragePath = uploadedPath,
                            primaryColorHex = primary.uppercase(),
                            secondaryColorHex = secondary.uppercase(),
                            accentColorHex = accent.uppercase(),
                            backgroundColorHex = background.uppercase(),
                            textColorHex = bodyText.uppercase()
                        )
                        api.saveBranding(session, revised)
                        api.branding(session, business.id)
                    }) { result ->
                        saving = false
                        result.onSuccess(onSaved).onFailure {
                            error = it.message ?: "Unable to save branding."
                        }
                    }
                }
            }, enabled = !saving) { Text("Save branding") }
        }
    }
}

@Composable
private fun BrandColorInput(name: String, value: String, change: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(48.dp),
            color = parsedColor(value),
            shape = MaterialTheme.shapes.small
        ) {}
        OutlinedTextField(
            value = value,
            onValueChange = { change(it.take(7)) },
            label = { Text("$name (#RRGGBB)") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }
}
