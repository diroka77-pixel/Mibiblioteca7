package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyDisclosureTest {
    @Test fun explainsStorageNetworkAndOptionalPermissions() {
        val text = privacyDisclosures().joinToString(" ") { it.title + " " + it.detail }
        assertTrue(text.contains("Drive"))
        assertTrue(text.contains("Internet"))
        assertTrue(text.contains("cámara"))
        assertTrue(text.contains("micrófono"))
        assertTrue(text.contains("notificaciones"))
    }

    @Test fun keepsCorePrivacyPromisesVisible() {
        val text = privacyDisclosures().joinToString(" ") { it.detail }
        assertEquals(5, privacyDisclosures().size)
        assertTrue(text.contains("publicidad"))
        assertTrue(text.contains("analítica"))
        assertTrue(text.contains("automáticamente"))
    }
}
