package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNavigationTest {
    private val paragraphs = listOf(
        ReadingParagraph("Prólogo", heading = true, chapterStart = true),
        ReadingParagraph("Texto inicial"),
        ReadingParagraph("Capítulo uno", heading = true, chapterStart = true),
        ReadingParagraph("Contenido")
    )

    @Test fun buildsChaptersWithStableCharacterPositions() {
        val chapters = readerChapters(paragraphs)
        assertEquals(listOf("Prólogo", "Capítulo uno"), chapters.map { it.title })
        assertEquals(0, chapters.first().startChar)
        assertEquals("Prólogo".length + "Texto inicial".length, chapters[1].startChar)
    }

    @Test fun resolvesChapterPageAndProgressAtBoundaries() {
        val chapters = readerChapters(paragraphs)
        assertEquals(0, readerChapterIndexAt(chapters, 2))
        assertEquals(1, readerChapterIndexAt(chapters, chapters[1].startChar))
        assertEquals(2, readerPageForChar(listOf(0, 10, 20, 30), 29))
        assertEquals(50, readerProgressPercent(50, 100))
        assertEquals(100, readerProgressPercent(200, 100))
    }
}
