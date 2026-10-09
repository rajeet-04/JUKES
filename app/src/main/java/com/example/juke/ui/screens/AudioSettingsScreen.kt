package com.example.juke.ui.screens

import com.example.juke.ui.icons.JukeIcons

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.juke.network.SpotifyApi
import com.example.juke.services.UpdateManager
import kotlinx.coroutines.launch
import com.example.juke.ui.components.GlassAlertDialog
import com.example.juke.ui.components.GlassButton
import com.example.juke.ui.components.GlassTopAppBar
import com.example.juke.ui.theme.GlassCard
import com.example.juke.utils.BlacklistManager
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import kotlin.math.roundToInt

@Composable
fun AudioSettingsScreen(
    musicViewModel: MusicViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPurge: () -> Unit,
    onNavigateToPowerTools: () -> Unit = {}
) {
    val isBoosterEnabled by musicViewModel.isBoosterEnabled.collectAsStateWithLifecycle()
    val boosterLevel by musicViewModel.boosterLevel.collectAsStateWithLifecycle()
    val bassLevel by musicViewModel.bassLevel.collectAsStateWithLifecycle()
    val isNormalizationEnabled by musicViewModel.isNormalizationEnabled.collectAsStateWithLifecycle()
    val isStreamMode by musicViewModel.isStreamMode.collectAsStateWithLifecycle()
    val isSkipSilenceEnabled by musicViewModel.isSkipSilenceEnabled.collectAsStateWithLifecycle()
    val isMiniPlayerLyricsEnabled by musicViewModel.isMiniPlayerLyricsEnabled.collectAsStateWithLifecycle()
    val recommendationCount by musicViewModel.recommendationCount.collectAsStateWithLifecycle()
    val haptic = rememberJukeHaptics()
    // Power Tools stay locked until the version line is tapped 7 times (like Android's developer options).
    val powerCtx = LocalContext.current
    val powerPrefs = remember { powerCtx.getSharedPreferences("power_prefs", android.content.Context.MODE_PRIVATE) }
    var powerUnlocked by remember { mutableStateOf(powerPrefs.getBoolean("power_tools_unlocked", false)) }
    val audioPrefs = remember { powerCtx.getSharedPreferences("audio_effects_prefs", android.content.Context.MODE_PRIVATE) }
    var batterySaver by remember { mutableStateOf(audioPrefs.getBoolean("battery_saver_playback", false)) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var lastVersionTapAt by remember { mutableLongStateOf(0L) }
    var lastBoosterTickBucket by remember { mutableIntStateOf((boosterLevel / 5).coerceIn(0, 20)) }
    var lastRecommendationTick by remember {
        mutableIntStateOf(recommendationCount.coerceIn(3, 15))
    }

    // Gradient Background
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            GlassTopAppBar(
                title = { Text("Audio Control", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(JukeIcons.Back, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                })

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()) {
                        Column {
                            CompactItem(
                                headlineContent = {
                                    Text(
                                        "Stream Mode",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Play instantly without saving to device")
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isStreamMode,
                                        onCheckedChange = {
                                            haptic.toggle()
                                            musicViewModel.toggleStreamMode(it)
                                        }
                                    )
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            CompactItem(
                                headlineContent = {
                                    Text(
                                        "Stable Volume",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Keep loudness steady across tracks")
                                },
                                leadingContent = {
                                    Icon(JukeIcons.Equalizer, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isNormalizationEnabled,
                                        onCheckedChange = {
                                            haptic.toggle()
                                            musicViewModel.toggleVolumeNormalization(it)
                                        }
                                    )
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            CompactItem(
                                headlineContent = {
                                    Text(
                                        "Skip Silence",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Trim quiet intros and outros")
                                },
                                leadingContent = {
                                    Icon(JukeIcons.Next, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isSkipSilenceEnabled,
                                        onCheckedChange = {
                                            haptic.toggle()
                                            musicViewModel.toggleSkipSilence(it)
                                        }
                                    )
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            // Offloaded playback bypasses the in-app audio processing, so it only
                            // engages while boost, stable volume and skip silence are off.
                            val effectsActive = isBoosterEnabled || isNormalizationEnabled || isSkipSilenceEnabled
                            CompactItem(
                                headlineContent = {
                                    Text(
                                        "Battery Saver Playback",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (batterySaver && effectsActive) "Paused while boost, stable volume or skip silence is on"
                                        else "Decode on the audio chip so the CPU can sleep"
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.BatterySaver, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = batterySaver,
                                        onCheckedChange = {
                                            haptic.toggle()
                                            batterySaver = it
                                            audioPrefs.edit { putBoolean("battery_saver_playback", it) }
                                        }
                                    )
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            CompactItem(
                                headlineContent = {
                                    Text(
                                        "Mini-Player Lyrics",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Show synced lyrics directly in the mini-player")
                                },
                                leadingContent = {
                                    Icon(JukeIcons.Lyrics, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isMiniPlayerLyricsEnabled,
                                        onCheckedChange = {
                                            haptic.toggle()
                                            musicViewModel.toggleMiniPlayerLyrics(it)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()) {
                        Column {
                            CompactItem(
                                headlineContent = {
                                    Text(
                                        "Bass & Volume Boost",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Raise volume and bass separately")
                                },
                                leadingContent = {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isBoosterEnabled,
                                        onCheckedChange = {
                                            haptic.toggle()
                                            musicViewModel.toggleVolumeBooster(it)
                                        }
                                    )
                                }
                            )

                            AnimatedVisibility(
                                visible = isBoosterEnabled,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 4.dp)
                                ) {
                                    BoostSlider(
                                        label = "Volume",
                                        level = boosterLevel,
                                        onLevel = { value ->
                                            val intValue = value.roundToInt().coerceIn(0, 100)
                                            val currentBucket = intValue / 5
                                            if (currentBucket != lastBoosterTickBucket) {
                                                haptic.tick()
                                                lastBoosterTickBucket = currentBucket
                                            }
                                            musicViewModel.setVolumeBoosterLevel(intValue)
                                        }
                                    )
                                    BoostSlider(
                                        label = "Bass",
                                        level = bassLevel,
                                        onLevel = { value ->
                                            val intValue = value.roundToInt().coerceIn(0, 100)
                                            if (intValue / 5 != bassLevel / 5) haptic.tick()
                                            musicViewModel.setBassLevel(intValue)
                                        }
                                    )

                                    Text(
                                        "High boost levels may distort audio.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val ctx = LocalContext.current
                    var progressive by remember {
                        mutableStateOf(
                            ctx.getSharedPreferences("music_settings_prefs", android.content.Context.MODE_PRIVATE)
                                .getBoolean("progressive_playback", true)
                        )
                    }
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        CompactItem(
                            headlineContent = {
                                Text(
                                    "Progressive playback",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = { Text("Start songs while they download. Downloads still save the full file") },
                            leadingContent = { Icon(Icons.Rounded.CloudSync, contentDescription = null) },
                            trailingContent = {
                                Switch(
                                    checked = progressive,
                                    onCheckedChange = {
                                        haptic.toggle()
                                        progressive = it
                                        ctx.getSharedPreferences("music_settings_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit { putBoolean("progressive_playback", it) }
                                        com.example.juke.network.JukesApi.progressiveEnabled = it
                                    }
                                )
                            }
                        )
                    }
                }

                item {
                    val ctx = LocalContext.current
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        CompactItem(
                            headlineContent = {
                                Text(
                                    "Solid surfaces",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = { Text("Turn off blur and transparency") },
                            leadingContent = { Icon(JukeIcons.Equalizer, contentDescription = null) },
                            trailingContent = {
                                Switch(
                                    checked = com.example.juke.ui.theme.GlassPrefs.solid,
                                    onCheckedChange = {
                                        haptic.toggle()
                                        com.example.juke.ui.theme.GlassPrefs.solid = it
                                        ctx.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit { putBoolean("solid_surfaces", it) }
                                    }
                                )
                            }
                        )
                    }
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()) {
                        Column {
                            CompactItem(
                                headlineContent = {
                                    Text(
                                        "Recommendation Queue",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Songs kept ready ahead; the rest of the radio waits and loads one by one")
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                }
                            )

                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 10.dp)
                            ) {
                                Text(
                                    text = "$recommendationCount tracks",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )

                                Slider(
                                    value = recommendationCount.toFloat(),
                                    onValueChange = { value ->
                                        val intValue = value.roundToInt().coerceIn(3, 15)
                                        if (intValue != lastRecommendationTick) {
                                            haptic.tick()
                                            lastRecommendationTick = intValue
                                        }
                                        musicViewModel.setRecommendationCount(intValue)
                                    },
                                    valueRange = 3f..15f,
                                    steps = 11,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "3",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                    Text(
                                        "15",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Market Selection Section
                item {
                    val marketCode by musicViewModel.marketCode.collectAsStateWithLifecycle()
                    var showDialog by remember { mutableStateOf(false) }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            "Spotify Region",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            getCountryName(marketCode),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                GlassButton(
                                    onClick = { showDialog = true }) {
                                    Text(marketCode, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Controls which region's music catalog appears in search results.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (showDialog) {
                        MarketCodeDialog(
                            currentCode = marketCode,
                            onDismiss = { showDialog = false },
                            onSelect = { newCode ->
                                musicViewModel.setMarketCode(newCode)
                                SpotifyApi.setDefaultMarket(newCode)
                                showDialog = false
                            }
                        )
                    }
                }

                // Blocked Artists Section
                item {
                    val context = LocalContext.current
                    var blacklistedArtists by remember {
                        mutableStateOf(BlacklistManager.getBlacklistedArtists(context).sorted())
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        "Blocked Artists",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Songs from these artists won't be recommended",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (blacklistedArtists.isEmpty()) {
                                Text(
                                    "No blocked artists",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                blacklistedArtists.forEach { artist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = artist.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                BlacklistManager.removeArtist(context, artist)
                                                blacklistedArtists = BlacklistManager.getBlacklistedArtists(context).sorted()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                JukeIcons.Close,
                                                contentDescription = "Unblock $artist",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Storage / Purge Section
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .clickable { onNavigateToPurge() }
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    JukeIcons.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        "Purge Redundant Tracks",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Free up space by deleting unused songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                JukeIcons.Forward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (powerUnlocked) 1f else 0.45f)
                            .clickable {
                                if (powerUnlocked) {
                                    haptic.click()
                                    onNavigateToPowerTools()
                                } else {
                                    haptic.reject()
                                    android.widget.Toast.makeText(
                                        powerCtx, "Locked. Tap the version below 7 times to unlock.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    ) {
                        CompactItem(
                            headlineContent = {
                                Text("Power Tools", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                Text(
                                    if (powerUnlocked) "Sources, repeat threshold, gestures, diagnostics and backup"
                                    else "Locked. Tap the version below 7 times to unlock"
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (powerUnlocked) Icons.Rounded.AutoAwesome else Icons.Rounded.Lock,
                                    contentDescription = if (powerUnlocked) null else "Locked"
                                )
                            },
                            trailingContent = {
                                if (powerUnlocked) {
                                    Icon(JukeIcons.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        )
                    }
                }

                // Footer
                item {
                    val context = LocalContext.current
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "JUKE v${com.example.juke.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastVersionTapAt > 2000L) versionTaps = 0
                                lastVersionTapAt = now
                                if (powerUnlocked) {
                                    android.widget.Toast.makeText(powerCtx, "Power Tools are already unlocked", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    versionTaps++
                                    haptic.tick()
                                    val left = 7 - versionTaps
                                    if (left <= 0) {
                                        powerUnlocked = true
                                        powerPrefs.edit { putBoolean("power_tools_unlocked", true) }
                                        haptic.confirm()
                                        android.widget.Toast.makeText(powerCtx, "Power Tools unlocked", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (versionTaps >= 3) {
                                        android.widget.Toast.makeText(
                                            powerCtx, "$left more tap${if (left == 1) "" else "s"} to unlock Power Tools",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                        Text(
                            "Made with ❤️ by MEEK",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        var checking by remember { mutableStateOf(false) }
                        GlassButton(
                            onClick = {
                                if (checking) return@GlassButton
                                checking = true
                                scope.launch {
                                    val release = UpdateManager.checkForUpdates(context, force = true)
                                    checking = false
                                    if (release != null) {
                                        UpdateManager.manualRelease.value = release
                                    } else {
                                        android.widget.Toast.makeText(
                                            context, "You're on the latest version", android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }) {
                            Text(if (checking) "Checking…" else "Check for updates")
                        }

                        GlassButton(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/rajeet-04/JUKES".toUri()
                                )
                                context.startActivity(intent)
                            }) {
                            Text("⭐ Star on GitHub")
                        }
                    }
                }
            }
        }
    }
}

// Popular markets for quick access (including IN, PK, NP as requested)
private val popularMarkets = listOf(
    "IN" to "India",
    "US" to "United States",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "AU" to "Australia",
    "PK" to "Pakistan",
    "DE" to "Germany",
    "FR" to "France",
    "JP" to "Japan",
    "BR" to "Brazil",
    "MX" to "Mexico",
    "NP" to "Nepal",
    "BD" to "Bangladesh",
    "LK" to "Sri Lanka",
    "ES" to "Spain",
    "IT" to "Italy",
    "KR" to "South Korea",
    "AR" to "Argentina",
    "NL" to "Netherlands",
    "SE" to "Sweden"
)

@Composable
private fun MarketCodeDialog(
    currentCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val haptic = rememberJukeHaptics()

    val windowHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Region",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Country or code") },
                    placeholder = { Text("Search country or code...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.heightIn(max = windowHeight * 0.4f)) {
                    val filtered = popularMarkets.filter {
                        it.first.contains(searchQuery, ignoreCase = true) ||
                                it.second.contains(searchQuery, ignoreCase = true)
                    }

                    items(filtered) { (code, name) ->
                        TextButton(
                            onClick = {
                                haptic.click()
                                onSelect(code)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (code == currentCode)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    fontWeight = if (code == currentCode) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    code,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptic.click()
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}

private fun getCountryName(code: String): String {
    return popularMarkets.firstOrNull { it.first == code }?.second ?: code
}

@Composable
private fun BoostSlider(label: String, level: Int, onLevel: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$level%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = level.toFloat(),
            onValueChange = onLevel,
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Dense replacement for Material ListItem (which reserves 56-88dp and 16dp gutters). */
@Composable
private fun CompactItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingContent() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            headlineContent()
            if (supportingContent != null) {
                androidx.compose.material3.ProvideTextStyle(
                    MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    supportingContent
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        }
    }
}
