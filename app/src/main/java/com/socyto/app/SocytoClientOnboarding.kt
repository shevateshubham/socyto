package com.socyto.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.socyto.app.data.ClientTemporaryCredentials
import com.socyto.app.data.Session
import com.socyto.app.data.SocytoApi

@Composable
fun SocytoOneTimeCredentials(
    credentials: ClientTemporaryCredentials,
    accountLabel: String = "CLIENT",
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var visible by remember(credentials.email) { mutableStateOf(false) }
    var acknowledged by remember(credentials.email) { mutableStateOf(false) }

    SocytoPageHeading(
        "STEP COMPLETE",
        "$accountLabel access created",
        "Save and share this account's sign-in details privately."
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ONE-TIME ACCESS DETAILS",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("Login ID", style = MaterialTheme.typography.labelMedium)
            Text(credentials.email,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(credentials.email)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy login ID")
            }
            HorizontalDivider()
            Text("Temporary password", style = MaterialTheme.typography.labelMedium)
            Text(
                if (visible) credentials.temporaryPassword else "••••••••••••••••",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (visible) Int.MAX_VALUE else 1
            )
            TextButton(
                onClick = { visible = !visible },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (visible) "Hide password" else "Show password")
            }
            Button(
                onClick = { clipboard.setText(AnnotatedString(credentials.temporaryPassword)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy temporary password")
            }
            SocytoPrivacyNotice(
                "The temporary password will not be shown again after closing this screen. " +
                    "The account owner must create a personal password at first login."
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                Text("I have shared or securely saved these credentials.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                enabled = acknowledged,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Done — continue") }
        }
    }
}

@Composable
fun SocytoFirstLoginPasswordChange(
    session: Session,
    api: SocytoApi,
    onComplete: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val strongEnough = password.length in 12..128 &&
        password.any { it.isLetter() } && password.any { it.isDigit() }
    SocytoPageHeading(
        "YOUR PRIVATE WORKSPACE", "Secure your account",
        "Set a personal password before viewing your assigned projects."
    )
    SocytoPrivacyNotice(
        "Your temporary password is valid for initial activation only. " +
            "Once your new password is saved, use it to sign in again."
    )
    Surface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("Create your personal password",
                style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                password, { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New password") },
                visualTransformation = if (visible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "Hide password" else "Show password")
                    }
                },
                singleLine = true
            )
            OutlinedTextField(
                repeatPassword, { repeatPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm new password") },
                visualTransformation = if (visible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                singleLine = true
            )
            Text("At least 12 characters, including letters and numbers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && strongEnough && password == repeatPassword,
                onClick = {
                    busy = true
                    error = ""
                    api.async({ api.completeClientFirstLogin(session, password) }) { result ->
                        busy = false
                        result.onSuccess { onComplete() }
                            .onFailure {
                                error = it.message ?: "Unable to update password. Please retry."
                            }
                    }
                }
            ) { Text(if (busy) "Saving securely…" else "Save new password") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) SocytoFeedback(
                message = error, isError = true, onDismiss = { error = "" })
        }
    }
}
