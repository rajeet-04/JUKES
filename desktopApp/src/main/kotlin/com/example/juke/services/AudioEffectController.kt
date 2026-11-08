package com.example.juke.services

import android.content.Context
import androidx.core.content.edit
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bass and booster ride JavaFX's 10-band equalizer; normalisation has no desktop equivalent yet. */
class AudioEffectController private constructor(context: Context) {
    companion object {
        @Volatile private var instance: AudioEffectController? = null
        fun get(context: Context): AudioEffectController = instance ?: synchronized(this) { instance ?: AudioEffectController(context.applicationContext).also { instance = it } }
    }
    private val prefs = context.getSharedPreferences("audio_effects", Context.MODE_PRIVATE)
    private var player: MediaPlayer? = null

    private val _boosterLevel = MutableStateFlow(prefs.getInt("booster_level", 0))
    val boosterLevel: StateFlow<Int> = _boosterLevel.asStateFlow()
    private val _bassLevel = MutableStateFlow(prefs.getInt("bass_level", 0))
    val bassLevel: StateFlow<Int> = _bassLevel.asStateFlow()
    private val _isBoosterEnabled = MutableStateFlow(prefs.getBoolean("booster_enabled", false))
    val isBoosterEnabled: StateFlow<Boolean> = _isBoosterEnabled.asStateFlow()
    private val _isNormalizationEnabled = MutableStateFlow(prefs.getBoolean("normalization_enabled", false))
    val isNormalizationEnabled: StateFlow<Boolean> = _isNormalizationEnabled.asStateFlow()

    /** Called on the FX thread for each new player. */
    fun attach(p: MediaPlayer) { player = p; apply() }

    private fun apply() {
        val bands = player?.audioEqualizer?.bands ?: return
        val boost = if (_isBoosterEnabled.value) _boosterLevel.value / 100.0 * 9.0 else 0.0
        val bass = if (_isBoosterEnabled.value) _bassLevel.value / 100.0 * 12.0 else 0.0 // one switch gates both, as on Android
        bands.forEachIndexed { i, b -> b.gain = (boost + if (i < 3) bass * (1 - i / 3.0) else 0.0).coerceIn(-24.0, 12.0) }
        player?.audioEqualizer?.isEnabled = boost != 0.0 || bass != 0.0
    }
    private fun again(block: () -> Unit) { block(); Fx.run { apply() } }
    fun setBoosterLevel(percentage: Int) = again { _boosterLevel.value = percentage.coerceIn(0, 100); prefs.edit { putInt("booster_level", _boosterLevel.value) } }
    fun setBassLevel(percentage: Int) = again { _bassLevel.value = percentage.coerceIn(0, 100); prefs.edit { putInt("bass_level", _bassLevel.value) } }
    fun setBoosterEnabled(enabled: Boolean) = again { _isBoosterEnabled.value = enabled; prefs.edit { putBoolean("booster_enabled", enabled) } }
    fun setNormalizationEnabled(enabled: Boolean) { _isNormalizationEnabled.value = enabled; prefs.edit { putBoolean("normalization_enabled", enabled) } }
    fun release() {}
}
