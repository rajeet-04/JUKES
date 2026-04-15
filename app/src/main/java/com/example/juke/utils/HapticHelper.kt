package com.example.juke.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

class JukeHaptics(private val view: View, context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun hasPremiumVibration(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val currentVibrator = vibrator ?: return false
        return currentVibrator.hasVibrator() && currentVibrator.hasAmplitudeControl()
    }

    private fun tryVibrate(effect: VibrationEffect): Boolean {
        val currentVibrator = vibrator ?: return false
        if (!currentVibrator.hasVibrator()) return false
        return runCatching {
            currentVibrator.vibrate(effect)
        }.isSuccess
    }

    /** Light, crisp tap for list items and minor interactions */
    fun click() {
        if (hasPremiumVibration() && tryVibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))) {
            return
        }
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Substantive, deep feel for main play/pause or major state changes */
    fun heavyClick() {
        if (hasPremiumVibration() && tryVibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))) {
            return
        }
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Very light, mechanical ratchet feel for scrubbing the progress bar */
    fun tick() {
        if (hasPremiumVibration() && tryVibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))) {
            return
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Distinct feeling for turning a feature on/off (Shuffle/Repeat) */
    fun toggle() {
        if (hasPremiumVibration()) {
            val timings = longArrayOf(0, 10, 50, 10)
            val amplitudes = intArrayOf(0, 100, 0, 150)
            if (tryVibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))) {
                return
            }
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Double-bump success feeling for adding to queue and favorites */
    fun confirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            return
        }
        if (hasPremiumVibration() && tryVibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))) {
            return
        }
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Heavy warning feel for deleting/removing */
    fun reject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 40, 20, 40)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            if (tryVibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))) {
                return
            }
        }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun gestureStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            tick()
        }
    }

    fun gestureEnd() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            click()
        }
    }
}

@Composable
fun rememberJukeHaptics(): JukeHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) { JukeHaptics(view, context) }
}
