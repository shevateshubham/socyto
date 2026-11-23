package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*

/** Super Admin creates a real firm using verified server-side provisioning. */
@Composable
fun RegisterBusinessForm(session: Session, api: SocytoApi, onRegistered: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var credentials by remember { mutableStateOf<ClientTemporaryCredentials?>(null) }

    if (credentials != null) {
        SocytoOneTimeCredentials(credentials!!, accountLabel = "FIRM") {
            credentials = null
            onRegistered()
        }
        return
    }

    SocytoPageHeading(
        "PLATFORM ADMINISTRATION",
        "Add a new firm",
        "Create a private workspace for a design practice or business."
    )
    SocytoStepCard(
        "STEP 01 / FIRM DETAILS",
        "Who will manage this workspace?",
        "Only the firm name and a registered mobile number are required."
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Firm / business name") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
            supportingText = { Text("As it should appear on the firm's workspace") }
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Mobile number") }, singleLine = true,
            placeholder = { Text("+91…") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = phone.isNotBlank() && !AccountIdentifiers.validPhone(phone),
            supportingText = { Text("Include country code; used for secure account recovery") }
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        ) {
            Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text("A unique firm login ID and one-time password will be generated securely. You can copy them on the next screen.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = {
                saving = true
                message = ""
                api.async({ api.provisionFirmAccount(session, name, phone.trim()) }) { result ->
                    saving = false
                    result.onSuccess { credentials = it; name = ""; phone = "" }
                        .onFailure { message = it.message ?: "Unable to create firm account." }
                }
            },
            enabled = !saving && ProjectInputRules.validName(name) &&
                AccountIdentifiers.validPhone(phone),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(if (saving) "Creating firm…" else "Create firm and generate access") }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) SocytoFeedback(message, isError = true, onDismiss = { message = "" })
    }
}
