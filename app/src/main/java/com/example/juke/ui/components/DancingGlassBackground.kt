package com.example.juke.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.ExtractedColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DancingGlassBackground(
    extractedColors: ExtractedColors?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Fallback palette when album extraction is unavailable.
    val color1 = extractedColors?.primary ?: Color(0xFF1E1E2E)
    val color2 = extractedColors?.secondary ?: Color(0xFF2D2B55)
    val color3 = extractedColors?.tertiary ?: Color(0xFF1A1A2E)

    val infiniteTransition = rememberInfiniteTransition(label = "dancing_lights")

    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 12_000 else 40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle1"
    )

    val angle2 by infiniteTransition.animateFloat(
        initialValue = PI.toFloat(),
        targetValue = (3f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 15_000 else 45_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle2"
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 48.dp)
        ) {
            val width = size.width
            val height = size.height
            val radius = width * 0.6f

            val center1 = Offset(
                x = width / 2f + (width * 0.3f) * cos(angle1),
                y = height / 2f + (height * 0.3f) * sin(angle1)
            )

            val center2 = Offset(
                x = width / 2f + (width * 0.4f) * cos(angle2),
                y = height / 2f + (height * 0.4f) * sin(angle2)
            )

            val center3 = Offset(
                x = width / 2f + (width * 0.2f) * sin(angle1),
                y = height / 2f + (height * 0.2f) * cos(angle2)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color1.copy(alpha = 0.8f), Color.Transparent),
                    center = center1,
                    radius = radius
                ),
                radius = radius,
                center = center1
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color2.copy(alpha = 0.8f), Color.Transparent),
                    center = center2,
                    radius = radius
                ),
                radius = radius,
                center = center2
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color3.copy(alpha = 0.6f), Color.Transparent),
                    center = center3,
                    radius = radius
                ),
                radius = radius,
                center = center3
            )
        }

        // Frosted glass overlays for subtle tint and specular highlight.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.05f))
                .background(Color.Black.copy(alpha = 0.35f))
        )
    }
}
