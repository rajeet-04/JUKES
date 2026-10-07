package com.example.juke.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.core.content.edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.juke.services.Source
import com.example.juke.services.SourceMemory
import com.example.juke.ui.components.GlassButton
import com.example.juke.ui.components.GlassFilterChip
import com.example.juke.ui.components.GlassTopAppBar
import com.example.juke.ui.theme.GlassCard
import com.example.juke.utils.Diagnostics
import com.example.juke.utils.ListeningStats
import com.example.juke.utils.rememberJukeHaptics
import kotlinx.coroutines.launch

/** Advanced settings, gesture shortcuts and diagnostics for people who want to tune the app. */
@Composable
fun PowerToolsScreen(onNavigateBack: () -> Unit, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val haptic = rememberJukeHaptics()
    val scope = rememberCoroutineScope()
    val memory = remember { SourceMemory(context) }
    val power = remember { context.getSharedPreferences("power_prefs", Context.MODE_PRIVATE) }
    val settings = remember { context.getSharedPreferences("music_settings_prefs", Context.MODE_PRIVATE) }

    var preferred by remember { mutableStateOf(memory.preferred) }
    var rejected by remember { mutableIntStateOf(memory.rejectedSongCount()) }
    var threshold by remember { mutableIntStateOf(settings.getInt("repeat_threshold", 2).coerceIn(2, 6)) }
    var doubleTap by remember { mutableStateOf(power.getBoolean("mini_double_tap_play_pause", false)) }
    var longPress by remember { mutableStateOf(power.getBoolean("mini_long_press_favorite", false)) }
    var stats by remember { mutableStateOf<ListeningStats?>(null) }
    LaunchedEffect(Unit) { stats = Diagnostics.stats(context) }

    fun share(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, subject))
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopAppBar(
            title = { Text("Power Tools", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
        LazyColumn(
            // Weighted so the list owns the remaining height and scrolls; the bottom inset keeps the last
            // card clear of the floating mini player and tab bar.
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 100.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Section("Advanced") {
                    Label("First audio source", "Which provider is tried first. A source you reject with \"Wrong song? Refetch\" always goes last for that song.")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = listOf<Source?>(null) + memory.available
                        options.forEach { option ->
                            GlassFilterChip(
                                selected = preferred == option,
                                onClick = {
                                    haptic.click()
                                    preferred = option
                                    memory.preferred = option
                                },
                                label = { Text(option?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Auto") }
                            )
                        }
                    }
                    Label("Repeat threshold", "Plays in one day after which a song counts as on repeat and may return in recommendations.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (2..6).forEach { n ->
                            GlassFilterChip(
                                selected = threshold == n,
                                onClick = {
                                    haptic.click()
                                    threshold = n
                                    settings.edit { putInt("repeat_threshold", n) }
                                },
                                label = { Text("$n") }
                            )
                        }
                    }
                    Label("Rejected sources", "$rejected songs have a source you rejected.")
                    GlassButton(onClick = {
                        haptic.confirm()
                        memory.resetRejected()
                        rejected = 0
                    }) { Text("Reset rejected sources") }
                }
            }

            item {
                Section("Gestures and shortcuts") {
                    SwitchRow("Double-tap mini player", "Play or pause", doubleTap) {
                        haptic.toggle()
                        doubleTap = it
                        power.edit { putBoolean("mini_double_tap_play_pause", it) }
                    }
                    SwitchRow("Long-press mini player", "Add or remove the favourite", longPress) {
                        haptic.toggle()
                        longPress = it
                        power.edit { putBoolean("mini_long_press_favorite", it) }
                    }
                    Label("Also built in", "Tap Search again to select your query and type. Swipe the mini player to change songs. Queue ⋮ menu: shuffle, sort, clear played, save as playlist.")
                }
            }

            item {
                Section("Lock") {
                    Label("Hide Power Tools", "Locks this page again. Unlock it by tapping the version in Audio Control 7 times.")
                    GlassButton(onClick = {
                        haptic.confirm()
                        power.edit { putBoolean("power_tools_unlocked", false) }
                        onNavigateBack()
                    }) { Text("Lock Power Tools") }
                }
            }

            item {
                Section("Diagnostics and export") {
                    val s = stats
                    if (s == null) {
                        Label("Library", "Loading…")
                    } else {
                        Label(
                            "Library",
                            "${s.tracks} tracks · ${s.totalPlays} plays · ${s.favourites} favourites" +
                                if (s.topArtists.isNotEmpty()) "\nTop: " + s.topArtists.joinToString { "${it.first} (${it.second})" } else ""
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(onClick = {
                            haptic.click()
                            scope.launch { share("JUKE diagnostics", Diagnostics.report(context)) }
                        }) { Text("Share diagnostics") }
                        GlassButton(onClick = {
                            haptic.click()
                            scope.launch { share("JUKE backup", Diagnostics.backupJson(context)) }
                        }) { Text("Export backup") }
                    }
                    Label("Backup contents", "Settings, playlists and favourites as JSON. No credentials. Diagnostics include recent app log lines.")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Label(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
