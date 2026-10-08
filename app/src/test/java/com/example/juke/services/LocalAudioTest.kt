package com.example.juke.services

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalAudioTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun nullUriIsMissing() {
        assertTrue(LocalAudio.isMissing(null))
    }

    @Test
    fun existingFileIsNotMissing() {
        val f = tmp.newFile("a_stream.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertFalse(LocalAudio.isMissing(f.absolutePath))
        assertFalse(LocalAudio.isMissing(f.toURI().toString()))
    }

    @Test
    fun deletedFileIsMissing() {
        // Simulates LRU eviction or a manual/system cache clear after the DB row was written.
        val f = tmp.newFile("b_stream.mp3").apply { writeBytes(byteArrayOf(1)) }
        val path = f.absolutePath
        f.delete()
        assertTrue(LocalAudio.isMissing(path))
        assertTrue(LocalAudio.isMissing("file://$path"))
    }

    @Test
    fun emptyFileOrDirectoryIsMissing() {
        assertTrue(LocalAudio.isMissing(tmp.newFile("empty.mp3").absolutePath))
        assertTrue(LocalAudio.isMissing(tmp.newFolder("dir").absolutePath))
        assertTrue(LocalAudio.isMissing(File(tmp.root, "gone/c.mp3").absolutePath))
    }

    @Test
    fun remoteUrisAreNotMissing() {
        assertFalse(LocalAudio.isMissing("https://example.com/a.mp3"))
        assertFalse(LocalAudio.isMissing("content://media/external/audio/1"))
    }
}
