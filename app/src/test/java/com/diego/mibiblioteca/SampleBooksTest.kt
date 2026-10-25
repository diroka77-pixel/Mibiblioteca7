package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleBooksTest {
    @Test fun offersFiveDistinctClassics() {
        assertEquals(5, sampleBooks.size)
        assertEquals(sampleBooks.size, sampleBooks.map { it.gutenbergId }.distinct().size)
        assertEquals(sampleBooks.size, sampleBooks.map { it.fileName }.distinct().size)
    }

    @Test fun downloadsOnlyEpubFromGutenbergOverHttps() {
        sampleBooks.forEach {
            assertTrue(it.downloadUrl.startsWith("https://www.gutenberg.org/ebooks/"))
            assertTrue(it.downloadUrl.endsWith(".epub3.images"))
            assertTrue(it.fileName.endsWith(".epub"))
        }
    }
}
