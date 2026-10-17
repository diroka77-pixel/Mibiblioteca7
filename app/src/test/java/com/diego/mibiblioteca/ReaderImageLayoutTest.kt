package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageLayoutTest {
    @Test fun portraitImageNeverConsumesTheWholePage() {
        val height = readerImageHeight(800, 1600, 1000, 1800)
        assertTrue(height <= 990)
    }

    @Test fun decorativeLabelKeepsACompactReadableHeight() {
        val height = readerImageHeight(1200, 300, 1000, 1800)
        assertEquals(252, height)
    }

    @Test fun invalidDimensionsUseSafeFallback() {
        assertEquals(432, readerImageHeight(0, 0, 1000, 1800))
    }
}
