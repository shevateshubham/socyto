package com.socyto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.socyto.app.data.SocytoApi
import com.socyto.app.data.AccountIdentifiers
import java.util.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Real authenticated SOCYTO login, translated from Floot's editorial login study. */
@Composable
fun SocytoFlootLogin(
    api: SocytoApi,
    enabled: Boolean,
    backendConfigured: Boolean,
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp &&
        configuration.screenWidthDp >= 740 && configuration.screenHeightDp >= 600
    val compact = configuration.screenWidthDp < 370 || configuration.screenHeightDp < 480
    val color = MaterialTheme.colorScheme
    val primary = color.primary
    val secondary = color.secondary
    if (landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FlootEditorialHero(compact = true, modifier = Modifier.weight(1f))
            FlootCredentialCard(
                api = api,
                enabled = enabled, backendConfigured = backendConfigured,
                landscape = true, modifier = Modifier.weight(1f),
                onLogin = onLogin, onSignUp = onSignUp
            )
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlootEditorialHero(compact = compact)
            FlootCredentialCard(
                api = api,
                enabled = enabled, backendConfigured = backendConfigured,
                landscape = false, modifier = Modifier.fillMaxWidth(),
                onLogin = onLogin, onSignUp = onSignUp
            )
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null,
                    modifier = Modifier.size(14.dp), tint = color.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("Private by design. Securely managed.  ·  v" + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FlootEditorialHero(
    compact: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val onOlive = Color(0xFFF7F5F0)
    val olive = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Surface(modifier = modifier, shape = RoundedCornerShape(25.dp), color = olive) {
        Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 184.dp else 248.dp)) {
            val deco = onOlive.copy(alpha = 0.13f)
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawCircle(deco, radius = h * .67f,
                    center = Offset(w * .92f, h * .72f),
                    style = Stroke(width = 1.2.dp.toPx()))
                drawCircle(deco, radius = h * .49f,
                    center = Offset(w * .92f, h * .72f),
                    style = Stroke(width = 1.0.dp.toPx()))
                drawRect(deco, topLeft = Offset(w * .64f, h * .19f),
                    size = Size(w * .52f, h * .76f),
                    style = Stroke(width = 1.2.dp.toPx()))
                drawLine(deco, Offset(w * .68f, 0f), Offset(w * .68f, h), 1.dp.toPx())
                drawLine(deco, Offset(w * .77f, 0f), Offset(w * .77f, h), 1.dp.toPx())
            }
            Column(
                Modifier.fillMaxWidth(.88f)
                    .padding(if (compact) 19.dp else 27.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp),
                        color = onOlive.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("S", fontFamily = FontFamily.Serif,
                                style = MaterialTheme.typography.headlineSmall,
                                color = onOlive)
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("SOCYTO", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = onOlive)
                        Text("DESIGN · EXECUTE · DELIVER",
                            style = MaterialTheme.typography.labelSmall,
                            color = onOlive.copy(alpha = .78f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(if (compact) 3.dp else 8.dp))
                Text("Every detail.\nIn its place.",
                    style = if (compact) MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif
                    ) else MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    fontWeight = FontWeight.Normal, lineHeight = if (compact) 32.sp else 38.sp,
                    color = onOlive, maxLines = 2)
                if (!compact) Text(
                    "One considered home for your practice, projects and client communications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onOlive.copy(alpha = .84f),
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FlootCredentialCard(
    api: SocytoApi,
    enabled: Boolean,
    backendConfigured: Boolean,
    landscape: Boolean,
    modifier: Modifier,
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit
) {
    val color = MaterialTheme.colorScheme
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signingUp by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }
    var recoveryPhone by remember { mutableStateOf("") }
    var recoveryOtp by remember { mutableStateOf("") }
    var recoveryPassword by remember { mutableStateOf("") }
    var recoveryRequested by remember { mutableStateOf(false) }
    var recoveryBusy by remember { mutableStateOf(false) }
    var recoveryMessage by remember { mutableStateOf("") }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(25.dp),
        shadowElevation = 3.dp,
        color = color.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, color.outlineVariant.copy(alpha = .6f))
    ) {
        Column(
            Modifier.padding(if (landscape) 13.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 7.dp else 12.dp)
        ) {
            if (!landscape) {
                Surface(
                    modifier = Modifier.size(43.dp),
                    color = color.primaryContainer, shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("S", fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.headlineSmall,
                            color = color.primary)
                    }
                }
                Text("WELCOME TO SOCYTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color.secondary)
            }
            Text(if (recovering) "Recover access."
                 else if (signingUp) "Set up platform access."
                 else "Welcome back.",
                style = (if (landscape) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.headlineSmall)
                    .copy(fontFamily = FontFamily.Serif),
                maxLines = 2, color = color.primary)
            if (!landscape) Text(
                if (recovering) "Confirm your registered mobile number before changing your password."
                    else if (signingUp) "Super Admin setup uses a verified email. Firms and clients receive separate IDs."
                    else "Use your firm ID, client ID or administrator email to sign in.",
                style = MaterialTheme.typography.bodySmall,
                color = color.onSurfaceVariant
            )
            if (!backendConfigured) Text(
                "The cloud connection is unavailable in this build.",
                style = MaterialTheme.typography.bodySmall, color = color.error)
            if (recovering) {
                Text("Reset password using verified mobile number",
                    style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(recoveryPhone, { recoveryPhone = it },
                    label = { Text("Registered mobile (+91...)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = { Text("Use the number registered when the account was created") })
                if (recoveryRequested) {
                    OutlinedTextField(recoveryOtp, { recoveryOtp = it },
                        label = { Text("SMS verification code") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(recoveryPassword, { recoveryPassword = it },
                        label = { Text("New password (12+ characters, letters and numbers)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = enabled && !recoveryBusy &&
                        Regex("^[0-9]{6,8}$").matches(recoveryOtp.trim()) &&
                        recoveryPassword.length in 12..128 &&
                        recoveryPassword.any { it.isLetter() } &&
                        recoveryPassword.any { it.isDigit() },
                        onClick = {
                            recoveryBusy = true
                            recoveryMessage = ""
                            api.async({ api.recoverPasswordWithOtp(recoveryPhone, recoveryOtp, recoveryPassword) }) { result ->
                                recoveryBusy = false
                                result.onSuccess {
                                    recoveryMessage = "Password updated. Sign in with your login ID and new password."
                                    recoveryRequested = false
                                    recoveryPassword = ""
                                    recoveryOtp = ""
                                }.onFailure {
                                    recoveryMessage = it.message ?: "Phone verification or password reset failed."
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Verify SMS & reset password") }
                } else {
                    Button(enabled = enabled && !recoveryBusy &&
                        AccountIdentifiers.validPhone(recoveryPhone),
                        onClick = {
                            recoveryBusy = true
                            recoveryMessage = ""
                            api.async({ api.sendRecoveryOtp(recoveryPhone.trim()) }) { result ->
                                recoveryBusy = false
                                result.onSuccess {
                                    recoveryRequested = true
                                    recoveryMessage = "If this number is registered and SMS is configured, a code has been sent."
                                }.onFailure {
                                    recoveryMessage = it.message ?: "Unable to request SMS verification."
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Send SMS verification code") }
                }
                if (recoveryBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (recoveryMessage.isNotBlank()) Text(recoveryMessage,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { recovering = false; recoveryRequested = false; recoveryOtp = ""; recoveryPassword = ""; recoveryMessage = "" },
                    modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
            } else {
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (signingUp) "Administrator email" else "Login ID or administrator email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (signingUp) KeyboardType.Email else KeyboardType.Text),
                supportingText = { Text(if (signingUp) "Email verification required" else "Firm IDs begin F; client IDs begin C") },
                shape = RoundedCornerShape(12.dp)
            )
            if (email.isNotBlank() && email.contains("@")) {
                Text(
                    "Email: " + email.trim().lowercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = color.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password"
                                else "Show password"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                enabled = enabled && backendConfigured &&
                    (if (signingUp) email.contains("@") else email.contains("@") || AccountIdentifiers.isAccountCode(email)) && password.length >= (if (signingUp) 8 else 1),
                modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp),
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    val normalizedEmail = email.trim().lowercase(Locale.ROOT)
                    if (signingUp) onSignUp(normalizedEmail, password)
                    else onLogin(normalizedEmail, password)
                }
            ) {
                Text(if (signingUp) "Create account" else "Sign in")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null, modifier = Modifier.size(18.dp))
            }
            TextButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                onClick = { signingUp = !signingUp }
            ) { Text(if (signingUp) "Back to sign in" else "Set up Super Admin account") }
            if (!signingUp) {
                TextButton(onClick = { recovering = true },
                    modifier = Modifier.fillMaxWidth()) { Text("Forgot password? Reset using mobile") }
            }
            }
            if (!landscape) {
                HorizontalDivider(color = color.outlineVariant.copy(alpha = .65f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null,
                        modifier = Modifier.size(17.dp), tint = color.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Verified identity · Role-based access",
                        style = MaterialTheme.typography.labelSmall,
                        color = color.onSurfaceVariant)
                }
            }
        }
    }
}
