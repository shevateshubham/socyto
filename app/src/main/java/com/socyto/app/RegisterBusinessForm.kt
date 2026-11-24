package com.socyto.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*

/** Only Super Admin creates Firm Admin accounts and receives one-time credentials. */
@Composable
fun RegisterBusinessForm(session: Session, api: SocytoApi, onRegistered: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("interior_design") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var credentials by remember { mutableStateOf<ClientTemporaryCredentials?>(null) }
    val context = LocalContext.current

    HorizontalDivider()
    Text("Register new business", style = MaterialTheme.typography.titleLarge)
    Text("Super Admin creates the firm and its Firm Admin login. Copy the generated temporary password and share it privately. The Firm Admin must change it on first login. Activate the firm before granting workspace access.")

    if (credentials != null) {
        val login = credentials!!
        Text("Firm account created", style = MaterialTheme.typography.titleMedium)
        Text("Login ID: ${login.email}")
        Text("Temporary password (shown only once):")
        Text(login.temporaryPassword, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SOCYTO temporary credentials",
                "Login ID: ${login.email}\nTemporary password: ${login.temporaryPassword}"))
            message = "Credentials copied. Share them securely with the Firm Admin."
        }) { Text("Copy temporary credentials") }
        Text("Do not leave this screen before saving the credentials. They cannot be retrieved from SOCYTO again.")
        Button(onClick = {
            credentials = null
            name = ""
            adminEmail = ""
            message = ""
            onRegistered()
        }) { Text("I saved the credentials — continue") }
    } else {
        OutlinedTextField(name, { name = it }, label = { Text("Legal / business name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(adminEmail, { adminEmail = it },
            label = { Text("Firm Admin login email") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Business type")
        listOf("interior_design" to "Interior design", "architecture" to "Architecture",
            "construction" to "Construction contractor").forEach { (value, label) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(selected = businessType == value, onClick = { businessType = value })
                Text(label, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Button(onClick = {
            saving = true
            message = ""
            api.async({ api.registerFirm(session, name, adminEmail, businessType) }) { result ->
                saving = false
                result.onSuccess { credentials = it }
                    .onFailure { message = it.message ?: "Business registration failed." }
            }
        }, enabled = !saving && name.trim().length in 2..80 &&
            ProjectInputRules.validEmail(adminEmail),
            modifier = Modifier.fillMaxWidth()) { Text("Create firm and Firm Admin login") }
    }
    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message)
}
