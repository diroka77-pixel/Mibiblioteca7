package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechPositionTest {
    @Test fun speechIsQueuedOneWordAtATimeAndKeepsOriginalOffsets() {
        assertEquals(listOf(SpeechChunk(0, "Uno, "), SpeechChunk(5, "dos "),
            SpeechChunk(9, "y "), SpeechChunk(11, "tres.")),
            speechWordChunks("Uno, dos y tres."))
    }

    @Test fun emptyTextHasNoSpeechChunks() { assertEquals(emptyList<SpeechChunk>(), speechWordChunks("")) }
}
