package com.example.juke.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            
            // Footer with credits
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Made with ❤️ by RASH",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
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
                text = "5-Band Equalizer",
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
                        val frequencies = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
                        
                        bandLevels.forEachIndexed { index, level ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${level / 100}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = level.toFloat(),
                                    onValueChange = { onBandChange(index, it.toInt()) },
                                    valueRange = levelRange.first.toFloat()..levelRange.second.toFloat(),
                                    modifier = Modifier
                                        .height(200.dp)
                                        .graphicsLayer {
                                            rotationZ = 270f
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                        }
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(
                                                androidx.compose.ui.unit.Constraints(
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
                                    style = MaterialTheme.typography.labelSmall
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
