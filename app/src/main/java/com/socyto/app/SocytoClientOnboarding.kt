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
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("CLIENT ACCESS READY", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Text("Share these credentials privately",
                style = MaterialTheme.typography.titleLarge)
            Text(
                "This temporary password is shown only once. Give it to your client using a private channel. " +
                "They must replace it at their first sign-in.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Client email", style = MaterialTheme.typography.labelSmall)
            Text(credentials.email, maxLines = 3, overflow = TextOverflow.Ellipsis)
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(credentials.email)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy client email")
            }
            Text("One-time temporary password", style = MaterialTheme.typography.labelSmall)
            Text(credentials.temporaryPassword, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { clipboard.setText(AnnotatedString(credentials.temporaryPassword)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy temporary password")
            }
            Text(
                "After closing, SOCYTO cannot display this password again. " +
                    "If the client loses it, use the secure password-recovery process.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("I've shared the credentials · Close")
            }
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
