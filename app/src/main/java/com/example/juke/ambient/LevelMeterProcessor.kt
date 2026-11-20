package com.example.juke.ambient

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Last in the audio chain: passes audio through unchanged and, while the background waves are on
 * screen ([AudioLevels.listening]), measures each buffer into [AudioLevels]. No microphone or
 * Visualizer permission: it only sees the app's own audio.
 */
@UnstableApi
class LevelMeterProcessor : BaseAudioProcessor() {
    private var analyzer: LevelAnalyzer? = null
    private var scratch = ByteArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        analyzer = LevelAnalyzer(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (AudioLevels.listening) {
            analyzer?.analyze(inputBuffer)?.let { AudioLevels.timeline.push(System.currentTimeMillis(), it) }
        }
        // Copy through a reused array: the output buffer can be the very buffer we were handed.
        val size = inputBuffer.remaining()
        if (scratch.size < size) scratch = ByteArray(size)
        inputBuffer.get(scratch, 0, size)
        replaceOutputBuffer(size).put(scratch, 0, size).flip()
    }

    override fun onReset() {
        analyzer = null
        AudioLevels.timeline.clear()
    }
}
