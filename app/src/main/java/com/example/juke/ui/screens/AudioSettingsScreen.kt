package com.example.juke.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.juke.ui.components.GlassCard
import com.example.juke.ui.components.VerticalEqualizerSlider
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.network.SpotifyApi

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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Volume Normalization
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
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
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        "Stable Volume",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Consistent volume for all tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Switch(
                                checked = isNormalizationEnabled,
                                onCheckedChange = { musicViewModel.toggleVolumeNormalization(it) }
                            )
                        }
                    }
                }

                // Equalizer Section
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Equalizer, 
                                        null, 
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        "Equalizer",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Switch(
                                    checked = isEqualizerEnabled,
                                    onCheckedChange = { musicViewModel.toggleEqualizer(it) }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            if (isEqualizerEnabled) {
                                val frequencies = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
                                val range = musicViewModel.getEqualizerLevelRange()
                                val minLevel = range.first.toFloat()
                                val maxLevel = range.second.toFloat()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    equalizerBands.forEachIndexed { index, level ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            VerticalEqualizerSlider(
                                                value = level.toFloat(),
                                                onValueChange = { musicViewModel.setEqualizerBand(index, it.toInt()) },
                                                range = minLevel..maxLevel,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .width(32.dp)
                                                    .padding(vertical = 8.dp)
                                            )
                                            Text(
                                                text = frequencies.getOrElse(index) { "" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                OutlinedButton(
                                    onClick = { musicViewModel.resetEqualizer() },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Reset to Flat")
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Enable Equalizer to adjust frequencies",
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Volume Booster Section
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp, 
                                        null, 
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        "Bass & Volume Boost",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Switch(
                                    checked = isBoosterEnabled,
                                    onCheckedChange = { musicViewModel.toggleVolumeBooster(it) }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            if (isBoosterEnabled) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${boosterLevel}%",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Slider(
                                        value = boosterLevel.toFloat(),
                                        onValueChange = { musicViewModel.setVolumeBoosterLevel(it.toInt()) },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    "WARNING: Output > 100% may distort audio or damage speakers.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                
                // Recommendation Settings Section
                item {
                    val recommendationCount by musicViewModel.recommendationCount.collectAsState()
                    
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.GraphicEq, 
                                        null, 
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        "Recommendations",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$recommendationCount tracks per session",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Slider(
                                    value = recommendationCount.toFloat(),
                                    onValueChange = { musicViewModel.setRecommendationCount(it.toInt()) },
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
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        "15",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "Controls how many songs are automatically queued when your queue runs low.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // Market Selection Section
                item {
                    val marketCode by musicViewModel.marketCode.collectAsState()
                    var showDialog by remember { mutableStateOf(false) }
                    
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
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
                            "Made with ❤️ by RASH",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/rajeet-04/JUKES".toUri())
                                context.startActivity(intent)
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f),
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
                            onClick = { onSelect(code) },
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
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun getCountryName(code: String): String {
    return popularMarkets.firstOrNull { it.first == code }?.second ?: code
}