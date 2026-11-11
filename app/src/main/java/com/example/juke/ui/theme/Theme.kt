package com.example.juke.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

// Neutral, Apple-style surfaces: true black / soft off-white, grouped grays for raised layers.
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF2C2C2E),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF111113),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF242426),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFA1A1A6),
    outline = Color(0xFF636366),
    outlineVariant = Color(0xFF38383A)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDEDF2),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    onBackground = Color(0xFF111113),
    onSurface = Color(0xFF111113),
    onSurfaceVariant = Color(0xFF5F5F66),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFD1D1D6)
)

@Composable
fun JUKETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Wallpaper-derived color is opt-in: the app keeps neutral black/white surfaces.
    dynamicColor: Boolean = false,
    extractedColors: ExtractedColors? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = extractedColors?.let { colors ->
        val destination = if (darkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        var accent = colors.primary.toArgb()
        for (step in 0..20) {
            accent = androidx.core.graphics.ColorUtils.blendARGB(colors.primary.toArgb(), destination, step / 20f)
            if (androidx.core.graphics.ColorUtils.calculateContrast(accent, baseScheme.surface.toArgb()) >= 4.5) break
        }
        val foreground = if (androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, accent) >= 4.5)
            Color.White else Color.Black
        baseScheme.copy(primary = Color(accent), onPrimary = foreground)
    } ?: baseScheme

    val glassAccent = extractedColors?.primary ?: colorScheme.primary

    CompositionLocalProvider(LocalGlassAccent provides glassAccent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
