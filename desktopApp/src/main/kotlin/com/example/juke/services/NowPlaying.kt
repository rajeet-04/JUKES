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
                val t = pm.currentTrackNow
                mac.publish(t, pm.getCurrentPosition(), playing)
                if (t != null) scope.launch(Dispatchers.IO) { mac.loadArt(t) { mac.publish(pm.currentTrackNow, pm.getCurrentPosition(), pm.isPlayingFlow.value) } }
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
        private val kArtwork = constant("MPMediaItemPropertyArtwork")
        init { NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit") }

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
        class NSSize() : Structure(), Structure.ByValue {
            @JvmField var width: Double = 0.0
            @JvmField var height: Double = 0.0
            constructor(width: Double, height: Double) : this() {
                this.width = width
                this.height = height
            }
            override fun getFieldOrder() = listOf("width", "height")
        }

        private fun makeBlock(handler: Callback): Pointer {
            val d = BlockDescriptor().also { it.write() }
            val b = Block().also { it.isa = system.getGlobalVariableAddress("_NSConcreteGlobalBlock"); it.invoke = handler; it.descriptor = d.pointer; it.write() }
            keep += d; keep += handler; keep += b
            return b.pointer
        }

        private fun addCommand(name: String, action: (Pointer?) -> Unit) {
            val rc = send(cls("MPRemoteCommandCenter"), "sharedCommandCenter")!!
            val cmd = send(rc, name)!!
            sendL(cmd, "setEnabled:", 1)
            val h = object : Handler { override fun invoke(block: Pointer?, event: Pointer?): Long { runCatching { action(event) }; return 0 } }
            send(cmd, "addTargetWithHandler:", makeBlock(h))
        }

        // ---- artwork: the widget shows the song's cover instead of the app icon ----
        private var artFor: String? = null
        private var artObj: Pointer? = null
        private var artImage: Pointer? = null
        private val retired = ArrayList<Pointer>()
        interface ArtHandler : Callback { fun invoke(block: Pointer?, w: Double, h: Double): Pointer? }

        /** Fetches and decodes the cover off the main thread, then republishes with it. */
        fun loadArt(track: com.example.juke.models.Track, republish: () -> Unit) {
            if (artFor == track.uuid) return
            val uri = track.thumbnailUri ?: return
            val bytes = runCatching {
                if (uri.startsWith("http", true)) java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI(uri)).build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body()
                else java.io.File(if (uri.startsWith("file:")) java.net.URI(uri).path else uri).readBytes()
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
            runCatching {
                val mem = com.sun.jna.Memory(bytes.size.toLong()).also { it.write(0, bytes, 0, bytes.size) }
                val data = send(cls("NSData"), "dataWithBytes:length:", mem, bytes.size.toLong())!!
                val img = send(send(cls("NSImage"), "alloc")!!, "initWithData:", data) ?: return
                val handler = object : ArtHandler { override fun invoke(block: Pointer?, w: Double, h: Double): Pointer? = img }
                val art = msg.invokePointer(arrayOf<Any?>(
                    send(cls("MPMediaItemArtwork"), "alloc")!!,
                    sel("initWithBoundsSize:requestHandler:"),
                    NSSize(600.0, 600.0),
                    makeBlock(handler)
                ))
                // macOS can still hold the previous artwork and call its handler (which returns this image), so a cover is
                // only released once three newer ones exist; releasing it at once crashed the VM on a song change
                artImage?.let { retired += it; while (retired.size > 3) send(retired.removeAt(0), "release") }
                artImage = img; artObj = art; artFor = track.uuid
            }.onFailure { println("NowPlaying artwork failed: $it") }
            republish()
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

        /** Objects handed to the OS are retained by us and never released: with no autorelease pool on these threads
         *  macOS's async Now Playing push (issue #25) can read a value that was already freed. A few bytes per update. */
        private fun own(o: Pointer): Pointer = send(o, "retain") ?: o

        @Synchronized fun publish(track: com.example.juke.models.Track?, posMs: Long, playing: Boolean) {
            runCatching {
                if (track == null) { send(center, "setNowPlayingInfo:", null); sendL(center, "setPlaybackState:", 3); return }
                val d = send(send(cls("NSMutableDictionary"), "alloc")!!, "init")!!
                fun put(k: Pointer, v: Pointer) = send(d, "setObject:forKey:", own(v), k)
                put(kTitle, nsString(track.title)); put(kArtist, nsString(track.artist))
                put(kDuration, nsNumber(maxOf(pm.getDuration(), track.durationSec * 1000L) / 1000.0))
                if (artFor == track.uuid) artObj?.let { put(kArtwork, it) }
                put(kElapsed, nsNumber(posMs / 1000.0)); put(kRate, nsNumber(if (playing) 1.0 else 0.0))
                send(center, "setNowPlayingInfo:", d)
                sendL(center, "setPlaybackState:", if (playing) 1 else 2)
            }.onFailure { println("NowPlaying publish failed: $it") }
        }
    }
}
