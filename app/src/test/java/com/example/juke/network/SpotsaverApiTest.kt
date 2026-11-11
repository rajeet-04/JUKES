package com.example.juke.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotsaverApiTest {
    @Test fun usesDownloadUrlFromSuccessfulTunnelResponse() {
        assertEquals("https://worker.example/tunnel?id=signed", parseSpotsaverDownloadUrl("""{"success":1,"status":"tunnel","downloadUrl":"https://worker.example/tunnel?id=signed","url":"https://wrong.example"}"""))
    }
    @Test fun rejectsProviderErrorsChallengesAndUnsafeUrls() {
        for (body in listOf("<html>Just a moment</html>", "{}", """{"success":0,"downloadUrl":"https://worker.example"}""", """{"success":1,"downloadUrl":"http://worker.example"}""", """{"success":true,"downloadUrl":"https://user:password@worker.example"}""")) {
            assertNull(parseSpotsaverDownloadUrl(body))
        }
    }
}
