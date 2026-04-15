package com.example.juke.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.juke.network.SpotifyApi
import com.example.juke.utils.BlacklistManager
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    musicViewModel: MusicViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPurge: () -> Unit
) {
    val isBoosterEnabled by musicViewModel.isBoosterEnabled.collectAsState()
    val boosterLevel by musicViewModel.boosterLevel.collectAsState()
    val isNormalizationEnabled by musicViewModel.isNormalizationEnabled.collectAsState()
    val isStreamMode by musicViewModel.isStreamMode.collectAsState()
    val isSkipSilenceEnabled by musicViewModel.isSkipSilenceEnabled.collectAsState()
    val recommendationCount by musicViewModel.recommendationCount.collectAsState()
    val haptic = rememberJukeHaptics()
    var lastBoosterTickBucket by remember { mutableIntStateOf((boosterLevel / 5).coerceIn(0, 20)) }
    var lastRecommendationTick by remember {
        mutableIntStateOf(recommendationCount.coerceIn(3, 15))
    }

    // Gradient Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1C1E), // Dark
                        Color(0xFF0F1113), // Darker
                        Color.Black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            TopAppBar(
                title = { Text("Audio Control", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column {
                            ListItem(
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
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            ListItem(
                                headlineContent = {
                                    Text(
                                        "Stable Volume",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Normalize loudness across tracks")
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isNormalizationEnabled,
                                        onCheckedChange = {
                                            haptic.toggle()
                                            musicViewModel.toggleVolumeNormalization(it)
                                        }
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            ListItem(
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
                                    Icon(Icons.Rounded.SkipNext, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isSkipSilenceEnabled,
                                        onCheckedChange = {
                                            haptic.toggle()
                                            musicViewModel.toggleSkipSilence(it)
                                        }
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "Bass & Volume Boost",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Enhance depth and loudness")
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
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )

                            AnimatedVisibility(
                                visible = isBoosterEnabled,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                        .padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "${boosterLevel}%",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )

                                    Slider(
                                        value = boosterLevel.toFloat(),
                                        onValueChange = { value ->
                                            val intValue = value.roundToInt().coerceIn(0, 100)
                                            val currentBucket = intValue / 5
                                            if (currentBucket != lastBoosterTickBucket) {
                                                haptic.tick()
                                                lastBoosterTickBucket = currentBucket
                                            }
                                            musicViewModel.setVolumeBoosterLevel(intValue)
                                        },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.fillMaxWidth()
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "Recommendation Queue",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text("Set how many songs are auto-fetched")
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )

                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 20.dp)
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
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                    Text(
                                        "15",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Market Selection Section
                item {
                    val marketCode by musicViewModel.marketCode.collectAsState()
                    var showDialog by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            getCountryName(marketCode),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(marketCode, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Controls which region's music catalog appears in search results.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
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

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Songs from these artists won't be recommended",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (blacklistedArtists.isEmpty()) {
                                Text(
                                    "No blocked artists",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.4f),
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
                                            color = Color.White,
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
                                                Icons.Default.Close,
                                                contentDescription = "Unblock $artist",
                                                tint = Color.White.copy(alpha = 0.7f),
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
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
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        "Purge Redundant Tracks",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Free up space by deleting unused songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Footer
                item {
                    val context = LocalContext.current
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Made with ❤️ by MEEK",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        Button(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/rajeet-04/JUKES".toUri()
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
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

    AlertDialog(
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
                    placeholder = { Text("Search country or code...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.height(400.dp)) {
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