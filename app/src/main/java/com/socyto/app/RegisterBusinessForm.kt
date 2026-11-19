package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*

/** Real firm onboarding. Requires Super Admin role enforced by database RPC. */
@Composable
fun RegisterBusinessForm(
    session: Session,
    api: SocytoApi,
    onRegistered: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("interior_design") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    HorizontalDivider()
    Text("Register new business", style = MaterialTheme.typography.titleLarge)
    Text("The firm administrator must create an account and verify their email before registration.")
    OutlinedTextField(name, { name = it }, label = { Text("Legal / business name") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(adminEmail, { adminEmail = it },
        label = { Text("Administrator's verified email") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text("Business type")
    listOf(
        "interior_design" to "Interior design",
        "architecture" to "Architecture",
        "construction" to "Construction contractor"
    ).forEach { (value, label) ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = businessType == value, onClick = { businessType = value })
            Text(label, modifier = Modifier.padding(top = 12.dp))
        }
    }
    Button(
        onClick = {
            saving = true
            message = ""
            api.async({
                api.registerFirm(session, name, adminEmail, businessType)
            }) { result ->
                saving = false
                result.onSuccess {
                    message = "Business registered. Review its pending account status before activation."
                    name = ""
                    adminEmail = ""
                    onRegistered()
                }.onFailure { message = it.message ?: "Business registration failed." }
            }
        },
        enabled = !saving && name.trim().length in 2..120 &&
            adminEmail.contains("@"),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Register firm") }
    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message)
}
