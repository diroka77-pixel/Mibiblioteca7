package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAndLegalTest {
    @Test fun releaseNotesAppearOncePerInstalledVersion() {
        assertTrue(shouldShowReleaseNotes(false, "1.0.61", "1.0.62"))
        assertTrue(!shouldShowReleaseNotes(false, "1.0.62", "1.0.62"))
        assertTrue(!shouldShowReleaseNotes(true, null, "1.0.62"))
    }

    @Test fun releaseNotesDescribeVisibleChanges() {
        val text = currentReleaseNotes().joinToString(" ")
        assertTrue(text.contains("Centro de ayuda"))
        assertTrue(text.contains("Diagnóstico privado"))
        assertTrue(text.contains("sin borrado automático"))
    }

    @Test fun thirdPartyNoticesCoverBundledAndExternalComponents() {
        val notices = legalNotices()
        val text = notices.joinToString(" ") { it.name + " " + it.notice }
        assertEquals(notices.map { it.name }.distinct().size, notices.size)
        assertTrue(text.contains("Apache 2.0"))
        assertTrue(text.contains("MIT"))
        assertTrue(text.contains("Google ML Kit"))
        assertTrue(text.contains("Open Library"))
        assertTrue(text.contains("Wikipedia"))
        assertTrue(text.contains("no se incluye"))
    }
}
