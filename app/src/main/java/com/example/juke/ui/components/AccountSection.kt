package com.example.juke.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.juke.account.Account
import com.example.juke.account.LibrarySync
import com.example.juke.jam.Connect
import com.example.juke.ui.theme.GlassCard
import kotlinx.coroutines.launch

/** Log in / create an account, and manage it. Top of Settings. */
@Composable
fun AccountSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val email by Account.email.collectAsStateWithLifecycle()
    val syncStatus by LibrarySync.status.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) } // "password" or "delete"

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val e = email
            if (e == null) {
                var mail by remember { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }
                Text(
                    "Log in on each device to keep liked songs, playlists and settings the same everywhere, and control playback from any of them.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(mail, { mail = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(pass, { pass = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    fun go(create: Boolean) = scope.launch {
                        busy = true
                        error = Account.signIn(context, mail, pass, create)
                        busy = false
                        if (error == null) { pass = ""; LibrarySync.forgetBase(context); Connect.requestSync() }
                    }
                    Button({ go(false) }, Modifier.weight(1f), enabled = !busy && mail.isNotBlank() && pass.isNotEmpty()) { Text(if (busy) "Please wait…" else "Log in") }
                    OutlinedButton({ go(true) }, Modifier.weight(1f), enabled = !busy && mail.isNotBlank() && pass.isNotEmpty()) { Text("Create account") }
                }
                Text(
                    "There's no email reset: if you forget the password, change it from a device that's still logged in.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Logged in as $e", style = MaterialTheme.typography.bodyMedium)
                if (syncStatus.isNotEmpty()) Text(syncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button({ Connect.requestSync() }, Modifier.weight(1f)) { Text("Sync now") }
                    OutlinedButton({ scope.launch { Account.signOut(context); LibrarySync.forgetBase(context) } }, Modifier.weight(1f)) { Text("Log out") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ dialog = "password"; error = null }) { Text("Change password") }
                    TextButton({ dialog = "delete"; error = null }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    dialog?.let { kind ->
        var current by remember { mutableStateOf("") }
        var next by remember { mutableStateOf("") }
        val deleting = kind == "delete"
        GlassAlertDialog(
            onDismissRequest = { if (!busy) dialog = null },
            title = { Text(if (deleting) "Delete account?" else "Change password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (deleting) Text("Removes the account and its synced library from JUKES. Songs and playlists on your devices stay.")
                    OutlinedTextField(current, { current = it; error = null }, label = { Text("Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (!deleting) OutlinedTextField(next, { next = it; error = null }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton({
                    scope.launch {
                        busy = true
                        error = Account.passwordAction(context, current, if (deleting) null else next)
                        busy = false
                        if (error == null) { dialog = null; if (deleting) LibrarySync.forgetBase(context) }
                    }
                }, enabled = !busy && current.isNotEmpty() && (deleting || next.isNotEmpty())) {
                    Text(if (deleting) "Delete" else "Change", color = if (deleting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton({ dialog = null }, enabled = !busy) { Text("Cancel") } }
        )
    }
}
