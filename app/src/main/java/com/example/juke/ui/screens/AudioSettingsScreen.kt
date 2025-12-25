package com.example.juke.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.juke.viewmodels.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    musicViewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val isEqualizerEnabled by musicViewModel.isEqualizerEnabled.collectAsState()
    val equalizerBands by musicViewModel.equalizerBands.collectAsState()
    val isBoosterEnabled by musicViewModel.isBoosterEnabled.collectAsState()
    val boosterLevel by musicViewModel.boosterLevel.collectAsState()
    val isNormalizationEnabled by musicViewModel.isNormalizationEnabled.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Stable Volume Section
            StableVolumeSection(
                isEnabled = isNormalizationEnabled,
                onToggle = { musicViewModel.toggleVolumeNormalization(it) }
            )

            HorizontalDivider()

            // Equalizer Section
            EqualizerSection(
                isEnabled = isEqualizerEnabled,
                bandLevels = equalizerBands,
                onToggle = { musicViewModel.toggleEqualizer(it) },
                onBandChange = { index, level -> musicViewModel.setEqualizerBand(index, level) },
                onReset = { musicViewModel.resetEqualizer() },
                levelRange = musicViewModel.getEqualizerLevelRange()
            )
            
            HorizontalDivider()
            
            // Volume Booster Section
            VolumeBoosterSection(
                isEnabled = isBoosterEnabled,
                level = boosterLevel,
                onToggle = { musicViewModel.toggleVolumeBooster(it) },
                onLevelChange = { musicViewModel.setVolumeBoosterLevel(it) }
            )
            
            // Footer with credits and links
            val context = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Made with ❤️ by RASH",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    "⭐ Star this repo to keep me motivated for CONSTANT UPDATES",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            "https://github.com/rajeet-04/JUKES".toUri())
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        "⭐ Star this repo: https://github.com/rajeet-04/JUKES",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                }
                
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            "https://github.com/rajeet-04/JUKES/issues".toUri())
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        "🐛 Report issues: https://github.com/rajeet-04/JUKES/issues",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StableVolumeSection(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Stable Volume",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Keeps loud and quiet tracks at a more consistent level using system gain control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun EqualizerSection(
    isEnabled: Boolean,
    bandLevels: List<Int>,
    onToggle: (Boolean) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onReset: () -> Unit,
    levelRange: Pair<Int, Int>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "10-Band Equalizer",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
        
        if (isEnabled) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val frequencies = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1KHz", "2KHz", "4KHz", "8KHz", "16KHz")

                        bandLevels.forEachIndexed { index, level ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.width(32.dp)
                            ) {
                                Text(
                                    text = "${level / 100}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                                Slider(
                                    value = level.toFloat(),
                                    onValueChange = { onBandChange(index, it.toInt()) },
                                    valueRange = levelRange.first.toFloat()..levelRange.second.toFloat(),
                                    modifier = Modifier
                                        .height(120.dp)
                                        .width(24.dp)
                                        .graphicsLayer {
                                            rotationZ = 270f
                                            transformOrigin = TransformOrigin(0f, 0f)
                                        }
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(
                                                Constraints(
                                                    minWidth = constraints.minHeight,
                                                    maxWidth = constraints.maxHeight,
                                                    minHeight = constraints.minWidth,
                                                    maxHeight = constraints.maxWidth
                                                )
                                            )
                                            layout(placeable.height, placeable.width) {
                                                placeable.place(-placeable.width, 0)
                                            }
                                        }
                                )
                                Text(
                                    text = frequencies.getOrElse(index) { "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                    
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset to Flat")
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeBoosterSection(
    isEnabled: Boolean,
    level: Int,
    onToggle: (Boolean) -> Unit,
    onLevelChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume Booster",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
        
        if (isEnabled) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Boost: ${level}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { onLevelChange(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "Increases volume up to 200%. Use carefully to avoid hearing damage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
