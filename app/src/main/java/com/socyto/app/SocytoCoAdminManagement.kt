package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.socyto.app.data.*
import org.json.JSONObject

/** Only the permanent Primary Super Admin can access this control. */
@Composable
fun SocytoCoAdminManagement(session: Session, api: SocytoApi) {
    var contact by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf<JSONObject?>(null) }
    var secret by remember { mutableStateOf<String?>(null) }
    var expiry by remember { mutableStateOf("") }
    var revokeId by remember { mutableStateOf<String?>(null) }
    var cancelId by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    fun refresh() {
        isBusy = true
        api.async({ api.listCoAdmins(session) }) { result ->
            isBusy = false
            result.onSuccess { listing = it }
                .onFailure { notice = it.message ?: "Unable to load administrators." }
        }
    }
    LaunchedEffect(session.userId) { refresh() }
    SocytoPageHeading(
        "PRIMARY OWNER ONLY",
        "Administrator access",
        "Invite or revoke operational Co-Admins. Only you can control administrator permissions."
    )
    SocytoStepCard(
        "NEW INVITATION",
        "Invite a Co-Admin",
        "Enter the administrator's email or mobile. Share the one-time code privately after it is generated."
    ) {
        OutlinedTextField(contact, { contact = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Mobile or email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = contact.isNotBlank() && !(AccountIdentifiers.validEmail(contact) ||
                AccountIdentifiers.validPhone(contact)))
        Button(
            enabled = !isBusy && (AccountIdentifiers.validPhone(contact) ||
                AccountIdentifiers.validEmail(contact)),
            onClick = {
                isBusy = true
                notice = ""
                api.async({ api.inviteCoAdmin(session, contact) }) { result ->
                    isBusy = false
                    result.onSuccess {
                        secret = it.code
                        expiry = it.expiresAt
                        contact = ""
                        refresh()
                    }.onFailure { notice = it.message ?: "Unable to issue invitation." }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(if (isBusy) "Creating invitation…" else "Generate one-time invitation") }
    }
    if (secret != null) {
        Surface(color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("One-time Co-Admin invitation", style = MaterialTheme.typography.titleMedium)
                Text(secret ?: "", style = MaterialTheme.typography.bodyMedium)
                Text("Valid until $expiry. The recipient must verify the nominated email/mobile and sign in.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(secret.orEmpty())) },
                    modifier = Modifier.fillMaxWidth()) { Text("Copy invitation code") }
                Text("This code will not be shown again after closing this panel.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { secret = null; expiry = "" },
                    modifier = Modifier.fillMaxWidth()) { Text("I have shared the code") }
            }
        }
    }
    if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Co-Admins", modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { refresh() }, enabled = !isBusy) { Text("Refresh") }
    }
    val admins = listing?.optJSONArray("admins")
    if (admins == null || admins.length() == 0) {
        Text("No Co-Admins added yet.", style = MaterialTheme.typography.bodyMedium)
    } else {
        for (i in 0 until admins.length()) {
            val entry = admins.getJSONObject(i)
            val active = entry.optBoolean("active")
            Surface(shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.optString("contact_value"), style = MaterialTheme.typography.titleSmall)
                    Text(if (active) "Active Co-Admin" else "Access revoked",
                        color = MaterialTheme.colorScheme.secondary)
                    if (active) OutlinedButton(
                        onClick = { revokeId = entry.getString("user_id") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Revoke access") }
                }
            }
        }
    }
    val invites = listing?.optJSONArray("invitations")
    if (invites != null && invites.length() > 0) {
        Text("Invitations", style = MaterialTheme.typography.titleLarge)
        for (i in 0 until invites.length()) {
            val invitation = invites.getJSONObject(i)
            val pending = invitation.isNull("claimed_at") && invitation.isNull("revoked_at")
            if (pending) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(invitation.optString("contact_value"))
                        Text("Expires: " + invitation.optString("expires_at"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { cancelId = invitation.getString("invitation_id") }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
    if (revokeId != null) AlertDialog(
        onDismissRequest = { revokeId = null },
        title = { Text("Revoke Co-Admin access?") },
        text = { Text("This immediately removes their permission to manage firms. The Primary Owner is unaffected.") },
        confirmButton = { TextButton(onClick = {
            val id = revokeId ?: return@TextButton
            revokeId = null
            isBusy = true
            api.async({ api.revokeCoAdmin(session, id) }) { result ->
                isBusy = false
                result.onSuccess { refresh() }
                    .onFailure { notice = it.message ?: "Unable to revoke access." }
            }
        }) { Text("Revoke") } },
        dismissButton = { TextButton(onClick = { revokeId = null }) { Text("Keep access") } }
    )
    if (cancelId != null) AlertDialog(
        onDismissRequest = { cancelId = null },
        title = { Text("Cancel invitation?") },
        text = { Text("The unclaimed activation code will stop working.") },
        confirmButton = { TextButton(onClick = {
            val id = cancelId ?: return@TextButton
            cancelId = null
            isBusy = true
            api.async({ api.cancelCoAdminInvitation(session, id) }) { result ->
                isBusy = false
                result.onSuccess { refresh() }
                    .onFailure { notice = it.message ?: "Unable to cancel invitation." }
            }
        }) { Text("Cancel invitation") } },
        dismissButton = { TextButton(onClick = { cancelId = null }) { Text("Keep invitation") } }
    )
}
