package com.example.juke.services

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Skip Silence, edges only: drops silence at the start and end of a track and leaves silence in
 * the middle untouched (ExoPlayer's built-in skipSilence also cuts mid-song pauses).
 *
 * Leading: dropped until the first loud chunk. Trailing: silent chunks after that are held back;
 * a later loud chunk releases them (it was a mid-song gap), end-of-stream discards them.
 */
@UnstableApi
class EdgeSilenceProcessor : BaseAudioProcessor() {

    @Volatile var enabled = false

    private var leading = true
    private val held = HeldBytes()
    private var input = ByteArray(0)
    private var heldFrames = 0L
    private var bytesPerFrame = 4
    private var sampleRate = 44100

    /** Frames dropped for good since the last flush; the sink adds this to the playback position. */
    @Volatile var skippedFrames = 0L
        private set

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        bytesPerFrame = inputAudioFormat.bytesPerFrame
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun onFlush() {
        leading = true
        held.reset()
        heldFrames = 0
        skippedFrames = 0
    }

    override fun onReset() = onFlush()

    override fun onQueueEndOfStream() {
        // Whatever silence is still held is the track's tail: drop it, and re-arm for the next track.
        skippedFrames += heldFrames
        held.reset()
        heldFrames = 0
        leading = true
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Copy first: BaseAudioProcessor reuses its output buffer, which can be the very buffer
        // we were handed, and ByteBuffer.put(self) throws. The scratch array is reused, so the
        // audio thread doesn't allocate (and wake the GC) on every buffer.
        val size = inputBuffer.remaining()
        if (input.size < size) input = ByteArray(size)
        inputBuffer.get(input, 0, size)
        // Output never exceeds what is held plus this buffer.
        val out = replaceOutputBuffer(held.size() + size)
        if (!enabled) {
            if (held.size() > 0) releaseHeld(out)
            out.put(input, 0, size)
            out.flip()
            return
        }
        val chunkBytes = (sampleRate / 100) * bytesPerFrame // 10 ms
        var offset = 0
        while (offset < size) {
            val n = minOf(chunkBytes, size - offset)
            val frames = n / bytesPerFrame
            if (isSilent(input, offset, n)) {
                if (leading) {
                    skippedFrames += frames
                } else {
                    held.write(input, offset, n)
                    heldFrames += frames
                    if (heldFrames > sampleRate * 20L) releaseHeld(out) // very long gap: it is mid-song
                }
            } else {
                leading = false
                if (held.size() > 0) releaseHeld(out)
                out.put(input, offset, n)
            }
            offset += n
        }
        out.flip()
    }

    private fun releaseHeld(out: ByteBuffer) {
        held.writeTo(out)
        held.reset()
        heldFrames = 0
    }

    /** 16-bit little-endian PCM (Android's native order), read straight from the array. */
    private fun isSilent(bytes: ByteArray, from: Int, n: Int): Boolean {
        var i = from
        val end = from + n - 1
        while (i < end) {
            val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
            if (abs(sample.toShort().toInt()) > THRESHOLD) return false
            i += 2
        }
        return true
    }

    /** ByteArrayOutputStream that drains into a ByteBuffer without the toByteArray() copy. */
    private class HeldBytes : ByteArrayOutputStream() {
        fun writeTo(out: ByteBuffer) { out.put(buf, 0, count) }
    }

    private companion object {
        const val THRESHOLD = 512 // about -36 dBFS
    }
}

/** Default chain (speed/pitch via Sonic) plus our processors; reports edge-skipped frames to the sink. */
@UnstableApi
class JukeAudioChain(
    private val edge: EdgeSilenceProcessor,
    vararg processors: AudioProcessor
) : DefaultAudioSink.DefaultAudioProcessorChain(edge, *processors) {
    override fun getSkippedOutputFrameCount(): Long = super.getSkippedOutputFrameCount() + edge.skippedFrames
}
