package com.example.juke.services

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * The backend serves DASH-style fragmented MP4 (moof/mdat pairs). JavaFX's native player can't seek in
 * those (it jumps to the end), so for playback we rewrite the file as a normal MP4: same audio bytes,
 * one moov with full sample tables. Single audio track; anything unexpected returns the original file.
 */
object Mp4Defrag {
    private class Box(val type: String, val start: Int, val size: Int, val hdr: Int) { val end get() = start + size; val body get() = start + hdr }
    private class Sample(val off: Int, val size: Int, val dur: Int)

    fun playable(src: File): File {
        if (!src.isFile || src.length() > 80_000_000) return src
        val out = File(src.parentFile, src.name + ".plain.m4a")
        if (out.isFile && out.lastModified() >= src.lastModified() && out.length() > 0) return out
        return runCatching {
            val d = ByteBuffer.wrap(src.readBytes())
            val top = boxes(d, 0, d.limit())
            if (top.none { it.type == "moof" }) return src
            val bytes = rewrite(d, top)
            val tmp = File(src.parentFile, src.name + ".plain.tmp"); tmp.writeBytes(bytes); tmp.renameTo(out); out
        }.getOrElse { src }
    }

    private fun boxes(d: ByteBuffer, from: Int, to: Int): List<Box> {
        val r = ArrayList<Box>(); var p = from
        while (p + 8 <= to) {
            var size = d.getInt(p).toLong() and 0xFFFFFFFFL; var hdr = 8
            val type = String(ByteArray(4) { d.get(p + 4 + it) }, Charsets.ISO_8859_1)
            if (size == 1L) { size = d.getLong(p + 8); hdr = 16 } else if (size == 0L) size = (to - p).toLong()
            if (size < hdr || p + size > to) break
            r += Box(type, p, size.toInt(), hdr); p += size.toInt()
        }
        return r
    }
    private fun kids(d: ByteBuffer, b: Box) = boxes(d, b.body, b.end)
    private fun find(d: ByteBuffer, b: Box, vararg path: String): Box? { var cur = b; for (t in path) cur = kids(d, cur).firstOrNull { it.type == t } ?: return null; return cur }

    private fun box(type: String, payload: ByteArray): ByteArray = ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(payload).array()
    private fun ints(vararg v: Int): ByteArray = ByteBuffer.allocate(4 * v.size).also { b -> v.forEach { b.putInt(it) } }.array()

    private fun rewrite(d: ByteBuffer, top: List<Box>): ByteArray {
        val ftyp = top.first { it.type == "ftyp" }; val moov = top.first { it.type == "moov" }
        val trak = kids(d, moov).first { it.type == "trak" }
        val trackId = d.getInt(find(d, trak, "tkhd")!!.body + 4 + if (d.get(find(d, trak, "tkhd")!!.body).toInt() == 1) 16 else 8)
        val mdhd = find(d, trak, "mdia", "mdhd")!!
        val mdhdV1 = d.get(mdhd.body).toInt() == 1
        val timescale = d.getInt(mdhd.body + 4 + if (mdhdV1) 16 else 8)
        // trex defaults
        var defDur = 0; var defSize = 0
        find(d, moov, "mvex")?.let { mv -> kids(d, mv).filter { it.type == "trex" && d.getInt(it.body + 4) == trackId }.forEach { defDur = d.getInt(it.body + 12); defSize = d.getInt(it.body + 16) } }

        val samples = ArrayList<Sample>()
        for (moof in top.filter { it.type == "moof" }) for (traf in kids(d, moof).filter { it.type == "traf" }) {
            val tfhd = kids(d, traf).first { it.type == "tfhd" }
            if (d.getInt(tfhd.body + 4) != trackId) continue
            val tf = d.getInt(tfhd.body) and 0xFFFFFF; var q = tfhd.body + 8
            val base = if (tf and 1 != 0) d.getLong(q).toInt().also { q += 8 } else moof.start
            if (tf and 2 != 0) q += 4
            var dDur = defDur; var dSize = defSize
            if (tf and 8 != 0) { dDur = d.getInt(q); q += 4 }
            if (tf and 0x10 != 0) { dSize = d.getInt(q); q += 4 }
            var next = -1
            for (trun in kids(d, traf).filter { it.type == "trun" }) {
                val f = d.getInt(trun.body) and 0xFFFFFF; val n = d.getInt(trun.body + 4); var r = trun.body + 8
                var off = if (f and 1 != 0) (base + d.getInt(r)).also { r += 4 } else if (next >= 0) next else base
                if (f and 4 != 0) r += 4
                repeat(n) {
                    val dur = if (f and 0x100 != 0) d.getInt(r).also { r += 4 } else dDur
                    val sz = if (f and 0x200 != 0) d.getInt(r).also { r += 4 } else dSize
                    if (f and 0x400 != 0) r += 4
                    if (f and 0x800 != 0) r += 4
                    samples += Sample(off, sz, dur); off += sz
                }
                next = off
            }
        }
        require(samples.isNotEmpty())
        val total = samples.sumOf { it.dur.toLong() }

        // new sample tables, keeping the original stsd
        val stsd = find(d, trak, "mdia", "minf", "stbl", "stsd")!!
        val runs = ArrayList<IntArray>() // count, duration
        for (s in samples) if (runs.lastOrNull()?.get(1) == s.dur) runs.last()[0]++ else runs += intArrayOf(1, s.dur)
        fun stbl(chunkOffset: Int): ByteArray {
            val stts = box("stts", ints(0, runs.size, *runs.flatMap { listOf(it[0], it[1]) }.toIntArray()))
            val stsc = box("stsc", ints(0, 1, 1, samples.size, 1))
            val stsz = box("stsz", ints(0, 0, samples.size, *samples.map { it.size }.toIntArray()))
            val stco = box("stco", ints(0, 1, chunkOffset))
            return box("stbl", ByteArray(stsd.size).also { for (i in it.indices) it[i] = d.get(stsd.start + i) } + stts + stsc + stsz + stco)
        }
        val mvhd = find(d, moov, "mvhd")!!
        val mvV1 = d.get(mvhd.body).toInt() == 1
        val mvScale = d.getInt(mvhd.body + 4 + if (mvV1) 16 else 8)
        val movieDur = total * mvScale / timescale

        fun copy(b: Box) = ByteArray(b.size).also { for (i in it.indices) it[i] = d.get(b.start + i) }
        fun patchDur(b: Box, bytes: ByteArray, durOffsetV0: Int, durOffsetV1: Int, dur: Long) {
            val v1 = d.get(b.body).toInt() == 1; val o = b.hdr + if (v1) durOffsetV1 else durOffsetV0
            val bb = ByteBuffer.wrap(bytes); if (v1) bb.putLong(o, dur) else bb.putInt(o, dur.toInt())
        }
        fun container(b: Box, f: (Box) -> ByteArray?): ByteArray {
            val body = ByteArrayOutputStream(); kids(d, b).forEach { c -> f(c)?.let { body.write(it) } }
            return box(b.type, body.toByteArray())
        }
        fun moovBytes(chunkOffset: Int): ByteArray = container(moov) { c ->
            when (c.type) {
                "mvex" -> null
                "mvhd" -> copy(c).also { patchDur(c, it, 16, 24, movieDur) }
                "trak" -> if (c.start != trak.start) null else container(c) { t ->
                    when (t.type) {
                        "tkhd" -> copy(t).also { patchDur(t, it, 20, 28, movieDur) }
                        "mdia" -> container(t) { m ->
                            when (m.type) {
                                "mdhd" -> copy(m).also { patchDur(m, it, 16, 24, total) }
                                "minf" -> container(m) { i -> if (i.type == "stbl") stbl(chunkOffset) else copy(i) }
                                else -> copy(m)
                            }
                        }
                        else -> copy(t)
                    }
                }
                else -> copy(c)
            }
        }
        val head = copy(ftyp)
        val moovSize = moovBytes(0).size
        val mdatStart = head.size + moovSize + 8
        val out = ByteArrayOutputStream()
        out.write(head); out.write(moovBytes(mdatStart))
        val payload = samples.sumOf { it.size.toLong() }
        out.write(ints((payload + 8).toInt())); out.write("mdat".toByteArray())
        for (s in samples) out.write(d.array(), s.off, s.size)
        return out.toByteArray()
    }
}
