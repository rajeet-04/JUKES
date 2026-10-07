package com.example.juke.services

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.math.withSign

/**
 * Volume + bass boost applied to the PCM inside ExoPlayer's own audio sink, so it works on every
 * device. The platform LoudnessEnhancer/BassBoost effects attach fine on some phones (iQOO/vivo)
 * but the vendor audio stack ignores them, which is why the boost was inaudible.
 *
 * gain: linear PCM gain with a soft knee (no harsh clipping). bass: low-shelf biquad at 120 Hz.
 * stable: slow AGC steering the loudness (RMS) toward a fixed target so quiet and loud tracks
 * play at a similar level; gain is capped and frozen on near-silence so noise isn't pumped up.
 */
@UnstableApi
class BoostAudioProcessor : BaseAudioProcessor() {

    @Volatile private var gainDb = 0f
    @Volatile private var bassDb = 0f
    @Volatile private var stable = false
    private var rmsPow = 0f
    @Volatile private var agcGain = 1f

    /** Where the stable-volume gain currently sits; remembered per track so the next play starts there. */
    val currentAgcGain: Float get() = agcGain

    /** Start the AGC at a previously learned gain (RMS is set to match so it doesn't snap away). */
    fun seedAgc(gain: Float) {
        val g = gain.coerceIn(0.25f, 4f)
        agcGain = g
        rmsPow = (TARGET_RMS / g).let { it * it }
    }

    // Per-channel biquad state: x1, x2, y1, y2
    private var state = FloatArray(0)
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var coefBassDb = Float.NaN
    private var sampleRate = 44100
    private var passThrough = ByteArray(0)
    private var frame = FloatArray(2)

    fun configure(enabled: Boolean, gainDb: Float, bassDb: Float, stable: Boolean = false) {
        this.stable = stable
        this.gainDb = if (enabled) gainDb else 0f
        this.bassDb = if (enabled) bassDb else 0f
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        state = FloatArray(inputAudioFormat.channelCount * 4)
        coefBassDb = Float.NaN
        rmsPow = 0f
        agcGain = 1f
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun isActive() = super.isActive()

    private fun updateCoefficients(db: Float, sampleRate: Int) {
        coefBassDb = db
        val a = 10.0.pow(db / 40.0)
        val w0 = 2.0 * PI * 120.0 / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val tsa = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cosW + tsa
        b0 = (a * ((a + 1) - (a - 1) * cosW + tsa) / a0).toFloat()
        b1 = (2 * a * ((a - 1) - (a + 1) * cosW) / a0).toFloat()
        b2 = (a * ((a + 1) - (a - 1) * cosW - tsa) / a0).toFloat()
        a1 = (-2 * ((a - 1) + (a + 1) * cosW) / a0).toFloat()
        a2 = ((a + 1) + (a - 1) * cosW - tsa).toFloat() / a0.toFloat()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        val g = gainDb
        val bass = bassDb
        val agc = stable
        if (g < 0.05f && bass < 0.05f && !agc) {
            // Copy first: the reused output buffer can be the buffer we were handed, and put(self) throws.
            // The scratch array is reused so idle pass-through doesn't allocate on every buffer.
            if (passThrough.size < size) passThrough = ByteArray(size)
            inputBuffer.get(passThrough, 0, size)
            val out = replaceOutputBuffer(size)
            out.put(passThrough, 0, size)
            out.flip()
            return
        }
        val out = replaceOutputBuffer(size)
        val channels = inputAudioFormat.channelCount
        if (frame.size != channels) frame = FloatArray(channels)
        if (abs(bass - coefBassDb) > 0.01f || coefBassDb.isNaN()) updateCoefficients(bass, sampleRate)
        val lin = 10.0.pow(g / 20.0).toFloat()
        // ~400 ms RMS window; gain falls fast (60 ms) and recovers slowly (1.5 s)
        val rmsK = 1f - exp(-1f / (0.4f * sampleRate))
        val downK = 1f - exp(-1f / (0.06f * sampleRate))
        val upK = 1f - exp(-1f / (1.5f * sampleRate))
        while (inputBuffer.remaining() >= 2 * channels) {
            var energy = 0f
            for (ch in 0 until channels) {
                var x = inputBuffer.short / 32768f
                if (bass >= 0.05f) {
                    val i = ch * 4
                    val y = b0 * x + b1 * state[i] + b2 * state[i + 1] - a1 * state[i + 2] - a2 * state[i + 3]
                    state[i + 1] = state[i]; state[i] = x
                    state[i + 3] = state[i + 2]; state[i + 2] = y
                    x = y
                }
                frame[ch] = x
                energy += x * x
            }
            var gain = lin
            if (agc) {
                rmsPow += rmsK * (energy / channels - rmsPow)
                val rms = sqrt(rmsPow)
                // target -16 dBFS RMS; boost capped at +12 dB, cut capped at -12 dB; no boost under -55 dB
                val want = if (rms < 0.0018f) agcGain else (TARGET_RMS / rms).coerceIn(0.25f, 4f)
                agcGain += (if (want < agcGain) downK else upK) * (want - agcGain)
                gain *= agcGain
            }
            for (ch in 0 until channels) {
                var x = frame[ch] * gain
                val ax = abs(x)
                if (ax > 0.8f) x = (0.8f + 0.2f * tanh((ax - 0.8f) / 0.2f)).withSign(x)
                out.putShort((x * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        out.flip()
    }

    private companion object {
        const val TARGET_RMS = 0.158f // -16 dBFS
    }
}
