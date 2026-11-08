package com.example.juke.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Figtree (variable). Round, open apertures that stay crisp at small sizes over blurred
 * backgrounds. Weight steps are wide on purpose: glass hierarchy comes from weight and size,
 * not from extra containers.
 */
private fun face(w: Int, weight: FontWeight) = androidx.compose.ui.text.platform.Font(
    identity = "figtree_$w",
    data = object {}.javaClass.getResourceAsStream("/font/figtree_$w.ttf")!!.use { it.readBytes() },
    weight = weight,
)
val Figtree = FontFamily(
    face(400, FontWeight.Normal), face(500, FontWeight.Medium), face(600, FontWeight.SemiBold),
    face(700, FontWeight.Bold), face(800, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Figtree,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

val Typography = Typography(
    displayLarge = style(52, 58, FontWeight.ExtraBold, -1.2),
    displayMedium = style(42, 48, FontWeight.ExtraBold, -0.9),
    displaySmall = style(34, 40, FontWeight.Bold, -0.6),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.5),
    headlineMedium = style(26, 32, FontWeight.Bold, -0.4),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.3),
    titleLarge = style(20, 26, FontWeight.SemiBold, -0.2),
    titleMedium = style(16, 22, FontWeight.SemiBold, 0.0),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.15),
    bodySmall = style(12, 17, FontWeight.Normal, 0.2),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.3),
    labelSmall = style(11, 15, FontWeight.Medium, 0.4),
)
