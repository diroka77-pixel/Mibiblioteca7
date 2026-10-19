package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHelpTest {
    @Test fun helpCoversSetupReadingRecoveryAndPrivacy() {
        val sections = libraryHelpSections()
        val text = sections.joinToString(" ") { it.title + " " + it.body }

        assertEquals(sections.map { it.title }.distinct().size, sections.size)
        assertTrue(text.contains("Usar esta carpeta"))
        assertTrue(text.contains("EPUB"))
        assertTrue(text.contains("Lectura en voz alta"))
        assertTrue(text.contains("respaldo"))
        assertTrue(text.contains("Privacidad"))
    }

    @Test fun formatLimitationsAreExplicit() {
        val text = libraryHelpSections().joinToString(" ") { it.body }

        assertTrue(text.contains("AZW3"))
        assertTrue(text.contains("contraseña"))
        assertTrue(text.contains("convertirse a EPUB"))
    }
}
