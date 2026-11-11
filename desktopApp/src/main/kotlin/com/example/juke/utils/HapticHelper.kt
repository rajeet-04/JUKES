package com.example.juke.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Desktop has no haptics: every call is a no-op so shared UI code stays unchanged. */
class JukeHaptics {
    fun click() {}; fun nav() {}; fun heavyClick() {}; fun tick() {}; fun toggle() {}
    fun confirm() {}; fun reject() {}; fun gestureStart() {}; fun gestureEnd() {}
}

@Composable
fun rememberJukeHaptics(): JukeHaptics = remember { JukeHaptics() }
