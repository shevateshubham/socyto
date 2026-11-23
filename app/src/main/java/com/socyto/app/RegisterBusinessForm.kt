package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*

/** Super Admin creates a working firm account from just name and phone. */
@Composable
fun RegisterBusinessForm(
    session: Session,
    api: SocytoApi,
    onRegistered: () -> Unit
) {
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
    HorizontalDivider()
    Text("Add a firm", style = MaterialTheme.typography.titleLarge)
    Text("Enter the firm name and mobile number. SOCYTO securely issues a unique F-prefixed login ID and a one-time temporary password. The firm changes its password on first sign-in.")
    OutlinedTextField(name, { name = it }, label = { Text("Firm / business name") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(phone, { phone = it }, label = { Text("Mobile number with country code (+91...)") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button(
        onClick = {
            saving = true
            message = ""
            api.async({ api.provisionFirmAccount(session, name, phone.trim()) }) { result ->
                saving = false
                result.onSuccess {
                    credentials = it
                    name = ""
                    phone = ""
                }.onFailure { message = it.message ?: "Unable to create firm account." }
            }
        },
        enabled = !saving && ProjectInputRules.validName(name) &&
            Regex("^\\+[1-9][0-9]{7,14}$").matches(phone.trim()),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Create firm & generate credentials") }
    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) SocytoFeedback(message, isError = true, onDismiss = { message = "" })
}
