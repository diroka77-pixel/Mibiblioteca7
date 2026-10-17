package com.diego.mibiblioteca

import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncManifestTest {
    @Test fun emptyManifestRoundTripsWithValidChecksum() {
        val manifest = LibrarySyncManifest.from(emptyList())
        assertTrue(manifest.isValid())
        assertNotNull(LibrarySyncManifest.parse(manifest.toJson()))
    }

    @Test fun modifiedManifestIsRejected() {
        val manifest = LibrarySyncManifest.from(emptyList())
        val changed = JSONObject(manifest.toJson()).put("checksum", "0".repeat(64)).toString()
        assertNull(LibrarySyncManifest.parse(changed))
    }
}
