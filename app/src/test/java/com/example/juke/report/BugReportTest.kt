package com.example.juke.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BugReportTest {
    @Test fun hidesBearerTokens() =
        assertFalse(BugReport.scrub("Authorization: Bearer abcdefghijklmnop12345").contains("abcdefghijklmnop12345"))

    @Test fun hidesKeysInUrlsAndEmails() {
        assertEquals("GET /x?access_token=[redacted]&a=1", BugReport.scrub("GET /x?access_token=abc123secret&a=1"))
        assertEquals("mail [email]", BugReport.scrub("mail a.b@example.com"))
    }

    @Test fun leavesPlainLinesAlone() = assertEquals("Starting 'Tere Bina' for the Jam", BugReport.scrub("Starting 'Tere Bina' for the Jam"))
}
