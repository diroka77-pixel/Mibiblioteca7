package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleBooksTest {
    private val all = sampleBooksByLanguage.values.flatten()

    @Test fun offersFiveClassicsInEveryAppLanguage() {
        I18n.languages.forEach { language ->
            assertEquals(language, 5, sampleBooksFor(language).size)
        }
    }

    @Test fun classicsAreDistinctAcrossLanguages() {
        assertEquals(all.size, all.map { it.gutenbergId }.distinct().size)
        assertEquals(all.size, all.map { it.fileName }.distinct().size)
    }

    @Test fun unknownLanguageFallsBackToEnglish() {
        assertEquals(sampleBooksFor("en"), sampleBooksFor("ja"))
    }

    @Test fun downloadsOnlyEpubFromGutenbergOverHttps() {
        all.forEach {
            assertTrue(it.downloadUrl.startsWith("https://www.gutenberg.org/ebooks/"))
            assertTrue(it.downloadUrl.endsWith(".epub3.images"))
            assertTrue(it.fileName.endsWith(".epub"))
        }
    }

    @Test fun coversComeFromGutenbergOverHttps() {
        all.forEach {
            assertTrue(it.coverUrl.startsWith("https://www.gutenberg.org/cache/epub/${it.gutenbergId}/"))
        }
    }
}
