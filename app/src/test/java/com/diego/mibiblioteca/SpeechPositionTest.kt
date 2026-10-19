package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechPositionTest {
    @Test fun speechUsesReadableChunksAndKeepsSourceOffsets() {
        val text = "Uno, dos y tres. " + "palabra ".repeat(80)
        val chunks = speechWordChunks(text, maxChars = 120)
        assertEquals(0, chunks.first().start)
        assertEquals(text.take(chunks.first().text.length), chunks.first().text)
        assertEquals(chunks.zipWithNext().all { (a, b) ->
            b.start == a.start + a.text.length || text.substring(a.start + a.text.length,
                b.start).all(Char::isWhitespace)
        }, true)
        assertEquals(text.trimEnd(), chunks.joinToString(" ") { it.text.trim() })
    }

    @Test fun emptyTextHasNoSpeechChunks() { assertEquals(emptyList<SpeechChunk>(), speechWordChunks("")) }

    @Test fun fallbackChunksPreferSentenceBoundaries() {
        val text = "Primera frase suficientemente larga para superar el mínimo y alcanzar el corte. " +
            "Segunda frase mucho más extensa que mantiene contenido suficiente para superar ciento veinte caracteres."
        val chunks = speechWordChunks(text, maxChars = 120)
        assertEquals("Primera frase suficientemente larga para superar el mínimo y alcanzar el corte.",
            chunks.first().text)
    }

    @Test fun fifteenSecondSeekFollowsSpeechRate() {
        assertEquals(270, speechSeekDistance(15, 1f))
        assertEquals(405, speechSeekDistance(15, 1.5f))
    }

    @Test fun speechStartsInsideTheVisiblePage() {
        assertEquals(450, speechCursorForVisiblePage(450, 400, 600, 1_000))
        assertEquals(400, speechCursorForVisiblePage(250, 400, 600, 1_000))
        assertEquals(400, speechCursorForVisiblePage(760, 400, 600, 1_000))
    }

    @Test fun lastPageSpeechCursorIsClampedSafely() {
        assertEquals(900, speechCursorForVisiblePage(1_000, 900, 1_000, 1_000))
    }
}
