package com.example.juke.services

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.DynamicsProcessing.Config
import android.media.audiofx.DynamicsProcessing.Limiter
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls audio effects (Equalizer and Volume Booster) for media playback.
 * Attaches to ExoPlayer via audio session ID.
 */
class AudioEffectController(private val context: Context) {

    private val TAG = "AudioEffectController"
    private val prefs = context.getSharedPreferences("audio_effects_prefs", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var currentAudioSessionId: Int = 0

    // Equalizer state (10 bands)
    private val _equalizerBands = MutableStateFlow(loadEqualizerBands())
    val equalizerBands: StateFlow<List<Int>> = _equalizerBands.asStateFlow()

    private val _isEqualizerEnabled = MutableStateFlow(prefs.getBoolean("equalizer_enabled", false))
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    // Volume booster state (0-100%)
    private val _boosterLevel = MutableStateFlow(prefs.getInt("booster_level", 0))
    val boosterLevel: StateFlow<Int> = _boosterLevel.asStateFlow()

    private val _isBoosterEnabled = MutableStateFlow(prefs.getBoolean("booster_enabled", false))
    val isBoosterEnabled: StateFlow<Boolean> = _isBoosterEnabled.asStateFlow()

    private val _isNormalizationEnabled =
        MutableStateFlow(prefs.getBoolean("normalization_enabled", false))
    val isNormalizationEnabled: StateFlow<Boolean> = _isNormalizationEnabled.asStateFlow()

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
                    Log.d(TAG, "Equalizer initialized: $numBands bands, Range: $minLevel to $maxLevel mB")

                    // Log frequencies for debugging
                    for (i in 0 until numBands) {
                        val centerFreq = getCenterFreq(i.toShort()) / 1000
                        Log.d(TAG, "  Band $i: ${centerFreq}Hz")
                    }

                    // Apply saved band levels (up to available bands)
                    _equalizerBands.value.forEachIndexed { index, level ->
                        if (index < numBands) {
                            val safeLevel = level.coerceIn(minLevel.toInt(), maxLevel.toInt())
                            setBandLevel(index.toShort(), safeLevel.toShort())
                            Log.d(TAG, "  Applied Band $index: $safeLevel mB")
                        }
                    }
                    
                    // Force enable update to ensure it takes effect
                    enabled = _isEqualizerEnabled.value
                }
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer not available on this device: ${e.message}")
            }

            // Initialize Loudness Enhancer
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = _isBoosterEnabled.value
                    // Map 0-100% to 0-50000mB (0-500dB)
                    val targetGain = _boosterLevel.value * 500
                    setTargetGain(targetGain) 
                }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer not available: ${e.message}")
            }

            // Initialize DynamicsProcessing for normalization (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    // Config: 2 channels, Variant Favor Frequency, PreEq off, MBC on (1 band), PostEq off, Limiter on
                    val config = Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2, // channels
                        false, // preEqInUse
                        0, // preEqBands
                        true, // mbcInUse
                        1, // mbcBands
                        false, // postEqInUse
                        0, // postEqBands
                        true // limiterInUse
                    ).build()

                    dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                        enabled = _isNormalizationEnabled.value

                        // Configure Limiter for safety (Threshold -1dB, release 60ms)
                        // params: inUse, enabled, linkGroup, attackTime, releaseTime, ratio, threshold, postGain
                        val limiter = Limiter(
                            /* inUse = */ true,
                            /* enabled = */ true,
                            /* linkGroup = */ 0,
                            /* attackTime = */ 1.0f,
                            /* releaseTime = */ 60.0f,
                            /* ratio = */ 10.0f,
                            /* threshold = */ -1.0f,
                            /* postGain = */ 0.0f
                        )
                        setLimiterAllChannelsTo(limiter)

                        // Configure MBC for Normalization (bringing up quiet parts)
                        // Single band spanning all frequencies
                        // params: enabled, cutoffFreq, attackTime, releaseTime, ratio, threshold, kneeWidth, noiseGateThreshold, expanderRatio, preGain, postGain
                        // Ratio 3:1 to compress dynamic range, PostGain 3dB to boost perceived volume
                        val mbcBand = DynamicsProcessing.MbcBand(
                            /* enabled = */ true,
                            /* cutoffFrequency = */ 20.0f,
                            /* attackTime = */ 10.0f,
                            /* releaseTime = */ 100.0f,
                            /* ratio = */ 3.0f,
                            /* threshold = */ -30.0f,
                            /* kneeWidth = */ 6.0f,
                            /* noiseGateThreshold = */ -90.0f,
                            /* expanderRatio = */ 1.0f,
                            /* preGain = */ 0.0f,
                            /* postGain = */ 3.0f
                        )
                        setMbcBandAllChannelsTo(0, mbcBand)
                    }
                    Log.d(TAG, "DynamicsProcessing initialized for normalization")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create DynamicsProcessing: ${e.message}")
                }
            } else {
                Log.w(TAG, "DynamicsProcessing not available (requires Android 9+)")
            }

            Log.d(TAG, "Audio effects attached to session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects: ${e.message}", e)
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
            Log.w(TAG, "Invalid band index $bandIndex (max $numBands)")
            return
        }

        // Clamp level to valid range reported by engine
        val range = try {
            eq.bandLevelRange
        } catch (e: Exception) {
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
            Log.d(TAG, "Set equalizer band $bandIndex to $clampedLevel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set equalizer band: ${e.message}")
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
            Log.d(TAG, "Equalizer enabled: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle equalizer: ${e.message}")
        }
    }

    /**
     * Set volume booster level.
     * @param percentage 0-100%
     */
    fun setBoosterLevel(percentage: Int) {
        val clampedLevel = percentage.coerceIn(0, 100)
        _boosterLevel.value = clampedLevel
        prefs.edit { putInt("booster_level", clampedLevel) }

        try {
            // Map 0-100% to 0-5000mB (0-500dB)
            // Increased to 50x factor as 15x was reported insufficient
            val targetGain = clampedLevel * 500 
            loudnessEnhancer?.setTargetGain(targetGain)
            Log.d(TAG, "Volume booster set to $clampedLevel% (${targetGain}mB)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set booster level: ${e.message}")
        }
    }

    /**
     * Toggle volume booster on/off.
     */
    fun setBoosterEnabled(enabled: Boolean) {
        _isBoosterEnabled.value = enabled
        prefs.edit { putBoolean("booster_enabled", enabled) }
        try {
            loudnessEnhancer?.enabled = enabled
            Log.d(TAG, "Volume booster enabled: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle booster: ${e.message}")
        }
    }

    /**
     * Toggle simple loudness normalization using Automatic Gain Control (if available).
     */
    fun setNormalizationEnabled(enabled: Boolean) {
        _isNormalizationEnabled.value = enabled
        prefs.edit { putBoolean("normalization_enabled", enabled) }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dynamicsProcessing?.enabled = enabled
                Log.d(TAG, "Volume normalization (DynamicsProcessing) enabled: $enabled")
            } else {
                Log.w(TAG, "Volume normalization requires Android 9+")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle normalization: ${e.message}")
        }
    }

    /**
     * Get equalizer band level range.
     */
    fun getEqualizerBandLevelRange(): Pair<Int, Int> {
        return try {
            val range = equalizer?.bandLevelRange
            (range?.get(0)?.toInt() ?: -5000) to (range?.get(1)?.toInt() ?: 5000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get level range: ${e.message}")
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
            Log.d(TAG, "Equalizer reset to flat")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset equalizer: ${e.message}")
        }
    }

    private val prefListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            try {
                when {
                    key == "equalizer_enabled" -> {
                        val enabled = sharedPreferences.getBoolean(key, false)
                        // Only update if changed to avoid loops
                        if (_isEqualizerEnabled.value != enabled) {
                            _isEqualizerEnabled.value = enabled
                        }
                        if (equalizer?.enabled != enabled) {
                            equalizer?.enabled = enabled
                            Log.d(TAG, "Listener: Equalizer enabled updated to $enabled")
                        }
                    }

                    key?.startsWith("eq_band_") == true -> {
                        val index = key.removePrefix("eq_band_").toIntOrNull()
                        if (index != null && index in 0..9) {
                            val level = sharedPreferences.getInt(key, 0)

                            // Update flow if needed
                            val currentBands = _equalizerBands.value.toMutableList()
                            if (currentBands[index] != level) {
                                currentBands[index] = level
                                _equalizerBands.value = currentBands
                            }

                            // Apply to hardware equalizer
                            equalizer?.let { eq ->
                                val shortLevel = level.toShort()
                                if (eq.getBandLevel(index.toShort()) != shortLevel) {
                                    eq.setBandLevel(index.toShort(), shortLevel)
                                    Log.d(TAG, "Listener: Set band $index to $level")
                                }
                            }
                        }
                    }

                    key == "booster_enabled" -> {
                        val enabled = sharedPreferences.getBoolean(key, false)
                        if (_isBoosterEnabled.value != enabled) {
                            _isBoosterEnabled.value = enabled
                        }
                        if (loudnessEnhancer?.enabled != enabled) {
                            loudnessEnhancer?.enabled = enabled
                            Log.d(TAG, "Listener: Booster enabled updated to $enabled")
                        }
                    }

                    key == "booster_level" -> {
                        val level = sharedPreferences.getInt(key, 0)
                        if (_boosterLevel.value != level) {
                            _boosterLevel.value = level
                        }
                        loudnessEnhancer?.let { le ->
                            val targetGain = level * 500
                            if (le.targetGain.toInt() != targetGain) {
                                le.setTargetGain(targetGain)
                                Log.d(TAG, "Listener: Set booster gain to ${targetGain}mB")
                            }
                        }
                    }

                    key == "normalization_enabled" -> {
                        val enabled = sharedPreferences.getBoolean(key, false)
                        if (_isNormalizationEnabled.value != enabled) {
                            _isNormalizationEnabled.value = enabled
                        }
                        if (dynamicsProcessing?.enabled != enabled) {
                            dynamicsProcessing?.enabled = enabled
                            Log.d(TAG, "Listener: Normalization enabled updated to $enabled")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in preference listener: ${e.message}")
            }
        }

    init {
        loadPreferences()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun loadPreferences() {
        // Load enabled states
        _isEqualizerEnabled.value = prefs.getBoolean("equalizer_enabled", false)
        _isBoosterEnabled.value = prefs.getBoolean("booster_enabled", false)
        _isNormalizationEnabled.value = prefs.getBoolean("normalization_enabled", false)
        
        // Load booster level
        _boosterLevel.value = prefs.getInt("booster_level", 0)
        
        // Load eq bands
        val loadedBands = MutableList(10) { 0 }
        for (i in 0 until 10) {
            loadedBands[i] = prefs.getInt("eq_band_$i", 0)
        }
        _equalizerBands.value = loadedBands
        
        Log.d(TAG, "Loaded preferences: EqEnabled=${_isEqualizerEnabled.value}, BoosterEnabled=${_isBoosterEnabled.value}, Level=${_boosterLevel.value}")
    }

    /**
     * Release audio effects resources without unregistering listener.
     */
    private fun releaseEffects() {
        try {
            equalizer?.release()
            loudnessEnhancer?.release()
            dynamicsProcessing?.release()
            equalizer = null
            loudnessEnhancer = null
            dynamicsProcessing = null
            Log.d(TAG, "Audio effects keys released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio effects: ${e.message}")
        }
    }

    /**
     * Release all resources including listener.
     */
    fun release() {
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            releaseEffects()
            currentAudioSessionId = 0
            Log.d(TAG, "Audio effects fully released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio effects: ${e.message}")
        }
    }
}
