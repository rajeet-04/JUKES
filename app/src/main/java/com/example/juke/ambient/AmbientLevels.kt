package com.example.juke.ambient

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** How loud the music is right now, each 0..1: overall, below ~150 Hz and above ~2.5 kHz. */
data class Levels(val energy: Float, val bass: Float, val treble: Float) {
    companion object { val Silent = Levels(0f, 0f, 0f) }
}

/**
 * Measures 16-bit PCM as it goes to the speaker: RMS of the whole signal, of a low-passed copy
 * (bass) and of what a higher low-pass leaves out (treble). One-pole filters keep it cheap enough
 * for the audio thread; the filter state carries across buffers.
 */
class LevelAnalyzer(sampleRate: Int, private val channels: Int) {
    private val lowCoef = onePole(BASS_HZ, sampleRate)
    private val highCoef = onePole(TREBLE_HZ, sampleRate)
    private var low = 0f
    private var high = 0f

    /** Reads [pcm] from its position to its limit without moving either. */
    fun analyze(pcm: ByteBuffer): Levels {
        val b = pcm.duplicate().order(ByteOrder.nativeOrder())
        var sum = 0.0; var sumLow = 0.0; var sumHigh = 0.0
        var frames = 0
        while (b.remaining() >= 2 * channels) {
            var mono = 0f
            repeat(channels) { mono += b.short / 32768f }
            mono /= channels
            low += lowCoef * (mono - low)
            high += highCoef * (mono - high)
            val treble = mono - high
            sum += mono * mono; sumLow += low * low; sumHigh += treble * treble
            frames++
        }
        if (frames == 0) return Levels.Silent
        fun level(s: Double, gain: Double) = (sqrt(s / frames) * gain).toFloat().coerceIn(0f, 1f)
        return Levels(level(sum, GAIN), level(sumLow, BASS_GAIN), level(sumHigh, TREBLE_GAIN))
    }

    private companion object {
        const val BASS_HZ = 150.0
        const val TREBLE_HZ = 2500.0
        /** Typical music sits around 0.1–0.3 RMS; this puts it mid-range. */
        const val GAIN = 3.0
        /** Each band carries only part of the energy, so it gets more gain to use the same range. */
        const val BASS_GAIN = 5.0
        const val TREBLE_GAIN = 8.0
        fun onePole(hz: Double, rate: Int) = (1 - exp(-2 * PI * hz / rate)).toFloat()
    }
}

/**
 * Levels by the time they were measured. The audio sink is fed a few hundred milliseconds ahead of
 * the speaker, so the waves read the levels from that long ago to move with what you hear.
 */
class LevelTimeline(private val capacity: Int = 64) {
    private val times = LongArray(capacity)
    private val levels = arrayOfNulls<Levels>(capacity)
    private var next = 0
    private var count = 0

    @Synchronized fun push(timeMs: Long, l: Levels) {
        times[next] = timeMs; levels[next] = l
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /**
     * The latest levels measured at or before [timeMs], or null when there are none or they are
     * older than [maxAgeMs] (the audio stopped reaching the meter, e.g. offloaded playback).
     */
    @Synchronized fun at(timeMs: Long, maxAgeMs: Long = Long.MAX_VALUE): Levels? {
        for (k in 1..count) {
            val i = (next - k + capacity) % capacity
            if (times[i] <= timeMs) return levels[i].takeIf { timeMs - times[i] <= maxAgeMs }
        }
        return null
    }

    @Synchronized fun clear() { count = 0; next = 0 }
}

/** Smooths a level: rises in about [attackMs], falls back in about [releaseMs], so a beat swells instead of flickering. */
class Envelope(private val attackMs: Float, private val releaseMs: Float) {
    var value = 0f
        private set

    fun step(target: Float, dtMs: Float): Float {
        val tau = if (target > value) attackMs else releaseMs
        value += (target - value) * (1 - exp(-dtMs / tau))
        return value
    }

    fun snap(v: Float) { value = v }
}

/** The app-wide feed the player writes and the background reads (same process). */
object AudioLevels {
    val timeline = LevelTimeline()
    /** True while the waves are drawing; the meter skips the work otherwise. */
    @Volatile var listening = false
    /** How far behind the measurement the speaker plays; tuned on device. */
    const val LATENCY_MS = 180L

    fun now(): Levels? = timeline.at(System.currentTimeMillis() - LATENCY_MS, maxAgeMs = 1000)
}
