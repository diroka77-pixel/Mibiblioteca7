package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportedBookFormatTest {
    @Test fun recognizesSupportedExtensionsWithoutReliableMime() {
        assertEquals("EPUB", detectImportedBookFormat("novela.EPUB", "application/octet-stream")?.label)
        assertEquals("PDF", detectImportedBookFormat("ensayo.pdf", "")?.label)
        assertEquals("MOBI", detectImportedBookFormat("lector.mobi", "")?.label)
        assertEquals("azw3", detectImportedBookFormat("kindle.azw3", "")?.extension)
        assertEquals("FB2", detectImportedBookFormat("cuento.fb2", "text/xml")?.label)
    }

    @Test fun recognizesSupportedMimeWhenProviderHidesTheExtension() {
        assertEquals("EPUB", detectImportedBookFormat("documento", "application/epub+zip; charset=binary")?.label)
        assertEquals("MOBI", detectImportedBookFormat("documento", "application/x-mobipocket-ebook")?.label)
        assertEquals("AZW", detectImportedBookFormat("documento", "application/vnd.amazon.ebook")?.label)
        assertEquals("FB2", detectImportedBookFormat("documento", "application/x-fictionbook+xml")?.label)
    }

    @Test fun rejectsUnrelatedFiles() {
        assertNull(detectImportedBookFormat("notas.txt", "text/plain"))
    }
}
