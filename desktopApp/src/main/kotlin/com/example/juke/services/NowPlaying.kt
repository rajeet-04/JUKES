package com.example.juke.services

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * macOS Now Playing widget + hardware media keys (MPNowPlayingInfoCenter / MPRemoteCommandCenter),
 * driven through the Objective-C runtime. Other platforms: no-op.
 * Everything is best effort: any failure just leaves the OS controls out, never the player.
 */
object NowPlaying {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start(pm: PlaybackManager) {
        if (started || !System.getProperty("os.name").orEmpty().startsWith("Mac")) return
        started = true
        scope.launch {
            val mac = runCatching { Mac(pm) }.onFailure { println("NowPlaying unavailable: $it") }.getOrNull() ?: return@launch
            var shownPos = 0L; var shownAt = 0L; var shownPlaying = false
            combine(pm.currentTrackIdFlow, pm.isPlayingFlow) { id, playing -> id to playing }.collect { (_, playing) ->
                mac.publish(pm.currentTrackNow, pm.getCurrentPosition(), playing)
                shownPos = pm.getCurrentPosition(); shownAt = System.currentTimeMillis(); shownPlaying = playing
            }
        }
        scope.launch { // seeks: if the position drifts from what the OS extrapolates, tell it
            var lastPos = 0L; var lastAt = System.currentTimeMillis()
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis(); val pos = pm.getCurrentPosition()
                val expected = lastPos + if (pm.isPlayingFlow.value) now - lastAt else 0
                if (abs(pos - expected) > 1500) macRef?.publish(pm.currentTrackNow, pos, pm.isPlayingFlow.value)
                lastPos = pos; lastAt = now
            }
        }
    }

    @Volatile private var macRef: Mac? = null

    private class Mac(private val pm: PlaybackManager) {
        private val objc = NativeLibrary.getInstance("objc")
        private val msg = Function.getFunction("objc", "objc_msgSend")
        private val arm = System.getProperty("os.arch") == "aarch64"
        private val msgFp = if (arm) msg else Function.getFunction("objc", "objc_msgSend_fpret")
        private val mp = NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer")
        private val system = NativeLibrary.getInstance("System")
        private val keep = ArrayList<Any>() // blocks and callbacks must outlive the call that registers them

        private fun cls(n: String): Pointer = Function.getFunction("objc", "objc_getClass").invokePointer(arrayOf(n))
        private fun sel(n: String): Pointer = Function.getFunction("objc", "sel_registerName").invokePointer(arrayOf(n))
        private fun send(o: Pointer, s: String, vararg a: Any?): Pointer? = msg.invokePointer(arrayOf<Any?>(o, sel(s), *a))
        private fun sendL(o: Pointer, s: String, vararg a: Any?): Long = msg.invokeLong(arrayOf<Any?>(o, sel(s), *a))
        private fun sendD(o: Pointer, s: String): Double = msgFp.invokeDouble(arrayOf<Any?>(o, sel(s)))
        private fun nsString(s: String): Pointer = send(cls("NSString"), "stringWithUTF8String:", s)!!
        private fun nsNumber(d: Double): Pointer = send(cls("NSNumber"), "numberWithDouble:", d)!!
        private fun constant(name: String): Pointer = mp.getGlobalVariableAddress(name).getPointer(0)

        private val center = send(cls("MPNowPlayingInfoCenter"), "defaultCenter")!!
        private val kTitle = constant("MPMediaItemPropertyTitle")
        private val kArtist = constant("MPMediaItemPropertyArtist")
        private val kDuration = constant("MPMediaItemPropertyPlaybackDuration")
        private val kElapsed = constant("MPNowPlayingInfoPropertyElapsedPlaybackTime")
        private val kRate = constant("MPNowPlayingInfoPropertyPlaybackRate")

        class BlockDescriptor : Structure() {
            @JvmField var reserved: Long = 0
            @JvmField var size: Long = 40
            override fun getFieldOrder() = listOf("reserved", "size")
        }
        class Block : Structure() {
            @JvmField var isa: Pointer? = null
            @JvmField var flags: Int = 1 shl 28 // BLOCK_IS_GLOBAL: copying is a no-op
            @JvmField var reserved: Int = 0
            @JvmField var invoke: Callback? = null
            @JvmField var descriptor: Pointer? = null
            override fun getFieldOrder() = listOf("isa", "flags", "reserved", "invoke", "descriptor")
        }
        interface Handler : Callback { fun invoke(block: Pointer?, event: Pointer?): Long }

        private fun addCommand(name: String, action: (Pointer?) -> Unit) {
            val rc = send(cls("MPRemoteCommandCenter"), "sharedCommandCenter")!!
            val cmd = send(rc, name)!!
            sendL(cmd, "setEnabled:", 1)
            val d = BlockDescriptor().also { it.write() }
            val h = object : Handler { override fun invoke(block: Pointer?, event: Pointer?): Long { runCatching { action(event) }; return 0 } }
            val b = Block().also { it.isa = system.getGlobalVariableAddress("_NSConcreteGlobalBlock"); it.invoke = h; it.descriptor = d.pointer; it.write() }
            keep += d; keep += h; keep += b
            send(cmd, "addTargetWithHandler:", b.pointer)
        }

        init {
            addCommand("playCommand") { if (!pm.isPlayingFlow.value) pm.togglePlayPause() }
            addCommand("pauseCommand") { if (pm.isPlayingFlow.value) pm.togglePlayPause() }
            addCommand("togglePlayPauseCommand") { pm.togglePlayPause() }
            addCommand("nextTrackCommand") { pm.skipToNext() }
            addCommand("previousTrackCommand") { pm.skipToPrevious() }
            addCommand("changePlaybackPositionCommand") { e -> if (e != null) pm.seekTo((sendD(e, "positionTime") * 1000).toLong()) }
            macRef = this
        }

        fun publish(track: com.example.juke.models.Track?, posMs: Long, playing: Boolean) {
            runCatching {
                if (track == null) { send(center, "setNowPlayingInfo:", null); sendL(center, "setPlaybackState:", 3); return }
                val d = send(cls("NSMutableDictionary"), "dictionary")!!
                fun put(k: Pointer, v: Pointer) = send(d, "setObject:forKey:", v, k)
                put(kTitle, nsString(track.title)); put(kArtist, nsString(track.artist))
                put(kDuration, nsNumber(maxOf(pm.getDuration(), track.durationSec * 1000L) / 1000.0))
                put(kElapsed, nsNumber(posMs / 1000.0)); put(kRate, nsNumber(if (playing) 1.0 else 0.0))
                send(center, "setNowPlayingInfo:", d)
                sendL(center, "setPlaybackState:", if (playing) 1 else 2)
            }.onFailure { println("NowPlaying publish failed: $it") }
        }
    }
}
