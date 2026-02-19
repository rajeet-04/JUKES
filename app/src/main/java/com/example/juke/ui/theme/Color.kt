package com.example.juke.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

data class ExtractedColors(
    val primary: Color = Purple40,
    val secondary: Color = PurpleGrey40,
    val tertiary: Color = Pink40,
    val background: Color = Color.Black,
    val surface: Color = Color.Black,
    val onPrimary: Color = Color.White,
    val onSecondary: Color = Color.White,
    val onTertiary: Color = Color.White,
    val onBackground: Color = Color.White,
    val onSurface: Color = Color.White
)