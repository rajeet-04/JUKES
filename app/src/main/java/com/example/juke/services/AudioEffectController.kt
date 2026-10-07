package com.example.juke.services

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls audio effects (Equalizer and Volume Booster) for media playback.
 * Attaches to ExoPlayer via audio session ID.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class AudioEffectController private constructor(context: Context) {

    companion object {
        @Volatile private var instance: AudioEffectController? = null

        /** One controller per process: the service owns the audio session, the UI drives the same object. */
        fun get(context: Context): AudioEffectController =
            instance ?: synchronized(this) {
                instance ?: AudioEffectController(context.applicationContext).also { instance = it }
            }
    }


    private val tag = "AudioEffectController"
    private val prefs = context.getSharedPreferences("audio_effects_prefs", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    /** Volume/bass boost runs in ExoPlayer's audio sink (see BoostAudioProcessor). */
    val boostProcessor = BoostAudioProcessor()
    val edgeSilence = EdgeSilenceProcessor()
    private var currentAudioSessionId: Int = 0

    // Equalizer state (10 bands)
    private val _equalizerBands = MutableStateFlow(loadEqualizerBands())
    val equalizerBands: StateFlow<List<Int>> = _equalizerBands.asStateFlow()

    private val _isEqualizerEnabled = MutableStateFlow(prefs.getBoolean("equalizer_enabled", false))
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    // Volume booster state (0-100%)
    private val _boosterLevel = MutableStateFlow(prefs.getInt("booster_level", 0))
    val boosterLevel: StateFlow<Int> = _boosterLevel.asStateFlow()

    // Bass boost level (0-100%), independent of the volume boost
    private val _bassLevel = MutableStateFlow(prefs.getInt("bass_level", 0))
    val bassLevel: StateFlow<Int> = _bassLevel.asStateFlow()

    private val _isBoosterEnabled = MutableStateFlow(prefs.getBoolean("booster_enabled", false))
    val isBoosterEnabled: StateFlow<Boolean> = _isBoosterEnabled.asStateFlow()

    private val _isNormalizationEnabled =
        MutableStateFlow(prefs.getBoolean("normalization_enabled", false))
    val isNormalizationEnabled: StateFlow<Boolean> = _isNormalizationEnabled.asStateFlow()

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pendingWrites = mutableListOf<android.content.SharedPreferences.Editor.() -> Unit>()
    private val flush = Runnable {
        val writes = pendingWrites.toList(); pendingWrites.clear()
        prefs.edit { writes.forEach { it() } }
    }

    /** Slider drags fire per pixel; write the latest values once the drag settles. */
    private fun persistLater(write: android.content.SharedPreferences.Editor.() -> Unit) {
        pendingWrites.add(write)
        handler.removeCallbacks(flush)
        handler.postDelayed(flush, 400)
    }

    /** 0-100% -> up to +14 dB gain and +9 dB low shelf. */
    private fun applyBoost() {
        val level = _boosterLevel.value
        val bass = _bassLevel.value
        boostProcessor.configure(_isBoosterEnabled.value, level * 0.14f, bass * 0.12f, _isNormalizationEnabled.value)
    }

    private fun loadEqualizerBands(): List<Int> {
        return (0 until 10).map { index ->
            prefs.getInt("eq_band_$index", 0)
        }
    }

    /**
     * Attach audio effects to the given audio session ID.
     * Should be called when ExoPlayer's audio session ID changes.
     */
    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId == currentAudioSessionId && equalizer != null) {
            return
        }

        releaseEffects()
        currentAudioSessionId = audioSessionId

        try {
            // Initialize Equalizer (try to create and catch exceptions if not available)
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = _isEqualizerEnabled.value

                    // Verify we have bands available
                    val numBands = numberOfBands.toInt()
                    val levelRange = bandLevelRange
                    val minLevel = levelRange[0]
                    val maxLevel = levelRange[1]
                    Log.d(tag, "Equalizer initialized: $numBands bands, Range: $minLevel to $maxLevel mB")

                    // Log frequencies for debugging
                    for (i in 0 until numBands) {
                        val centerFreq = getCenterFreq(i.toShort()) / 1000
                        Log.d(tag, "  Band $i: ${centerFreq}Hz")
                    }

                    // Apply saved band levels (up to available bands)
                    _equalizerBands.value.forEachIndexed { index, level ->
                        if (index < numBands) {
                            val safeLevel = level.coerceIn(minLevel.toInt(), maxLevel.toInt())
                            setBandLevel(index.toShort(), safeLevel.toShort())
                            Log.d(tag, "  Applied Band $index: $safeLevel mB")
                        }
                    }
                    
                    // Force enable update to ensure it takes effect
                    enabled = _isEqualizerEnabled.value
                }
            } catch (e: Exception) {
                Log.w(tag, "Equalizer not available on this device: ${e.message}")
            }

            Log.d(tag, "Audio effects attached to session $audioSessionId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize audio effects: ${e.message}", e)
        }
    }

    /**
     * Set equalizer band level.
     * @param bandIndex Band index (0-9)
     * @param level Level in millibels (-5000 to 5000)
     */
    fun setEqualizerBandLevel(bandIndex: Int, level: Int) {
        val eq = equalizer
        if (eq == null) {
            // Save pref even if eq not ready, so it applies later
            saveEqualizerBandPref(bandIndex, level)
            return
        }

        val numBands = eq.numberOfBands.toInt()
        if (bandIndex !in 0 until numBands) {
            Log.w(tag, "Invalid band index $bandIndex (max $numBands)")
            return
        }

        // Clamp level to valid range reported by engine
        val range = try {
            eq.bandLevelRange
        } catch (_: Exception) {
            ShortArray(2).apply {
                this[0] = -1500
                this[1] = 1500
            }
        }

        val minLevel = range[0].toInt()
        val maxLevel = range[1].toInt()
        val clampedLevel = level.coerceIn(minLevel, maxLevel)

        saveEqualizerBandPref(bandIndex, clampedLevel)

        try {
            eq.setBandLevel(bandIndex.toShort(), clampedLevel.toShort())
            Log.d(tag, "Set equalizer band $bandIndex to $clampedLevel")
        } catch (e: Exception) {
            Log.e(tag, "Failed to set equalizer band: ${e.message}")
        }
    }

    private fun saveEqualizerBandPref(bandIndex: Int, level: Int) {
        val currentBands = _equalizerBands.value.toMutableList()
        // Ensure list is large enough (should be 10)
        if (bandIndex < currentBands.size) {
            currentBands[bandIndex] = level
            _equalizerBands.value = currentBands
            prefs.edit { putInt("eq_band_$bandIndex", level) }
        }
    }

    /**
     * Toggle equalizer on/off.
     */
    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        prefs.edit { putBoolean("equalizer_enabled", enabled) }
        try {
            equalizer?.enabled = enabled
            Log.d(tag, "Equalizer enabled: $enabled")
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle equalizer: ${e.message}")
        }
    }

    /**
     * Set volume booster level.
     * @param percentage 0-100%
     */
    fun setBoosterLevel(percentage: Int) {
        _boosterLevel.value = percentage.coerceIn(0, 100)
        persistLater { putInt("booster_level", _boosterLevel.value) }
        applyBoost()
    }

    fun setBassLevel(percentage: Int) {
        _bassLevel.value = percentage.coerceIn(0, 100)
        persistLater { putInt("bass_level", _bassLevel.value) }
        applyBoost()
    }

    /**
     * Toggle volume + bass booster on/off.
     */
    fun setBoosterEnabled(enabled: Boolean) {
        _isBoosterEnabled.value = enabled
        prefs.edit { putBoolean("booster_enabled", enabled) }
        applyBoost()
    }

    /** Stable volume: AGC inside the audio sink (see BoostAudioProcessor). */
    fun setNormalizationEnabled(enabled: Boolean) {
        _isNormalizationEnabled.value = enabled
        prefs.edit { putBoolean("normalization_enabled", enabled) }
        applyBoost()
    }

    /**
     * Get equalizer band level range.
     */
    fun getEqualizerBandLevelRange(): Pair<Int, Int> {
        return try {
            val range = equalizer?.bandLevelRange
            (range?.get(0)?.toInt() ?: -5000) to (range?.get(1)?.toInt() ?: 5000)
        } catch (e: Exception) {
            Log.e(tag, "Failed to get level range: ${e.message}")
            -5000 to 5000
        }
    }

    /**
     * Reset all equalizer bands to 0.
     */
    fun resetEqualizer() {
        _equalizerBands.value = List(10) { 0 }

        // Save to preferences
        prefs.edit().apply {
            for (i in 0 until 10) {
                putInt("eq_band_$i", 0)
            }
            apply()
        }

        try {
            val numBands = equalizer?.numberOfBands?.toInt() ?: 0
            for (i in 0 until minOf(10, numBands)) {
                equalizer?.setBandLevel(i.toShort(), 0)
            }
            Log.d(tag, "Equalizer reset to flat")
        } catch (e: Exception) {
            Log.e(tag, "Failed to reset equalizer: ${e.message}")
        }
    }

    init {
        loadPreferences()
        applyBoost()
    }

    private fun loadPreferences() {
        // Load enabled states
        _isEqualizerEnabled.value = prefs.getBoolean("equalizer_enabled", false)
        _isBoosterEnabled.value = prefs.getBoolean("booster_enabled", false)
        _isNormalizationEnabled.value = prefs.getBoolean("normalization_enabled", false)
        
        // Load booster level
        _boosterLevel.value = prefs.getInt("booster_level", 0)
        _bassLevel.value = prefs.getInt("bass_level", 0)
        
        // Load eq bands
        val loadedBands = MutableList(10) { 0 }
        for (i in 0 until 10) {
            loadedBands[i] = prefs.getInt("eq_band_$i", 0)
        }
        _equalizerBands.value = loadedBands
        
        Log.d(tag, "Loaded preferences: EqEnabled=${_isEqualizerEnabled.value}, BoosterEnabled=${_isBoosterEnabled.value}, Level=${_boosterLevel.value}")
    }

    /**
     * Release audio effects resources without unregistering listener.
     */
    private fun releaseEffects() {
        try {
            equalizer?.release()
            equalizer = null
            Log.d(tag, "Audio effects keys released")
        } catch (e: Exception) {
            Log.e(tag, "Failed to release audio effects: ${e.message}")
        }
    }

    /**
     * Release all resources including listener.
     */
    fun release() {
        try {
            releaseEffects()
            currentAudioSessionId = 0
            Log.d(tag, "Audio effects fully released")
        } catch (e: Exception) {
            Log.e(tag, "Failed to release audio effects: ${e.message}")
        }
    }
}
