package com.example.juke.services

import android.content.Context
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * Controls audio effects (Equalizer and Volume Booster) for media playback.
 * Attaches to ExoPlayer via audio session ID.
 */
class AudioEffectController(private val context: Context) {
    
    private val TAG = "AudioEffectController"
    private val prefs = context.getSharedPreferences("audio_effects_prefs", Context.MODE_PRIVATE)
    
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var automaticGainControl: AutomaticGainControl? = null
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

    private val _isNormalizationEnabled = MutableStateFlow(prefs.getBoolean("normalization_enabled", false))
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
        
        release()
        currentAudioSessionId = audioSessionId
        
        try {
            // Initialize Equalizer (try to create and catch exceptions if not available)
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = _isEqualizerEnabled.value
                    
                    // Verify we have bands available
                    val numBands = numberOfBands.toInt()
                    Log.d(TAG, "Equalizer has $numBands bands")
                    
                    // Apply saved band levels (up to available bands)
                    _equalizerBands.value.forEachIndexed { index, level ->
                        if (index < numBands) {
                            setBandLevel(index.toShort(), level.toShort())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer not available on this device: ${e.message}")
            }
            
            // Initialize Loudness Enhancer
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = _isBoosterEnabled.value
                    setTargetGain(_boosterLevel.value * 800) // 0-100% maps to 0-80000mB
                }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer not available: ${e.message}")
            }

            // Initialize Automatic Gain Control for simple normalization
            if (AutomaticGainControl.isAvailable()) {
                try {
                    // AGC helps even out loud and quiet tracks without changing player volume
                    automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                        enabled = _isNormalizationEnabled.value
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create AutomaticGainControl: ${e.message}")
                }
            } else {
                Log.w(TAG, "AutomaticGainControl not available on this device")
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
        if (bandIndex !in 0..<10) return
        
        val currentBands = _equalizerBands.value.toMutableList()
        currentBands[bandIndex] = level
        _equalizerBands.value = currentBands
        
        // Save to preferences
        prefs.edit { putInt("eq_band_$bandIndex", level) }
        
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), level.toShort())
            Log.d(TAG, "Set equalizer band $bandIndex to $level")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set equalizer band: ${e.message}")
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
            // Map 0-100% to 0-80000mB (0-800%)
            val targetGain = clampedLevel * 800
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
            if (automaticGainControl == null && currentAudioSessionId != 0 && AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(currentAudioSessionId)
            }
            automaticGainControl?.enabled = enabled
            Log.d(TAG, "Volume normalization enabled: $enabled")
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
    
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
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
                        val targetGain = level * 800
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
                    if (automaticGainControl?.enabled != enabled) {
                        automaticGainControl?.enabled = enabled
                        Log.d(TAG, "Listener: Normalization enabled updated to $enabled")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in preference listener: ${e.message}")
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    /**
     * Release audio effects resources.
     */
    fun release() {
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            equalizer?.release()
            loudnessEnhancer?.release()
            automaticGainControl?.release()
            equalizer = null
            loudnessEnhancer = null
            automaticGainControl = null
            currentAudioSessionId = 0
            Log.d(TAG, "Audio effects released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio effects: ${e.message}")
        }
    }
}
