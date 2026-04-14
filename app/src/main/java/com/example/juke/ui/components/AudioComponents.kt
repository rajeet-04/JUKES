package com.example.juke.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

@Composable
fun VerticalEqualizerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                ) { change, _ ->
                    change.consume()
                    // Use absolute position to avoid stale state issues and ensure smooth tracking
                    // 0 (top) -> Max Value
                    // Height (bottom) -> Min Value
                    val height = size.height.toFloat()
                    val y = change.position.y.coerceIn(0f, height)

                    // Invert fraction because Y=0 is top (Max value)
                    val fraction = 1f - (y / height)

                    val rangeSpan = range.endInclusive - range.start
                    val newValue = (range.start + (fraction * rangeSpan)).coerceIn(range)

                    if (newValue != value) {
                        // haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Too frequent?
                    }
                    onValueChange(newValue)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = 4.dp.toPx()
            val thumbHeight = 16.dp.toPx()
            val cornerRadius = trackWidth / 2

            // Draw Track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(center.x - trackWidth / 2, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(cornerRadius)
            )

            // Calculate active height (from bottom up)
            // Map value to 0..1 fraction
            val fraction = (value - range.start) / (range.endInclusive - range.start)
            val fillHeight = size.height * fraction

            // Draw Active Track (Fill)
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.5f),
                topLeft = Offset(center.x - trackWidth / 2, size.height - fillHeight),
                size = Size(trackWidth, fillHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )

            // Draw Thumb
            val thumbY = size.height - (size.height * fraction) - (thumbHeight / 2)
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(center.x - thumbHeight, thumbY),
                size = Size(thumbHeight * 2, thumbHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}

@Composable
fun CircularBooster(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    maxBoost: Float = 100f
) {
    val haptic = LocalHapticFeedback.current

    // Map value (0..maxBoost) to angle (135..405 degrees)
    // 0 -> 135 deg (Bottom Left)
    // max -> 405 deg (Bottom Right)
    val startAngle = 135f
    val sweepAngle = 270f

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    // Simplified drag logic: Dragging right/up increases, left/down decreases
                    // Or ideally, track angle relative to center.
                    // Let's use simple vertical/horizontal component for now
                    val sensitivity = 1.0f
                    val dragVal = (dragAmount.x - dragAmount.y) * sensitivity
                    val newValue = (value + dragVal).coerceIn(0f, maxBoost)
                    if (newValue != value) {
                        // haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onValueChange(newValue)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            (size.minDimension - strokeWidth) / 2

            // Background Arc
            drawArc(
                color = Color.White.copy(alpha = 0.1f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Arc
            val fraction = value / maxBoost
            val activeSweep = sweepAngle * fraction

            // Color interpolation: Green -> Yellow -> Red
            val activeColor = when {
                fraction < 0.5f -> Color.Green
                fraction < 0.8f -> Color.Yellow
                else -> Color.Red
            }

            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${value.toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "BOOST",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 2.sp
            )
        }
    }
}
