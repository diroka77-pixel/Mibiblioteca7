package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechPositionTest {
    @Test fun estimatedPausePositionAdvancesToCurrentWordWhenEngineHasNoRangeCallbacks() {
        assertEquals(8, estimateSpeechWordOffset("Uno dos tres cuatro", 0, 600, 1f))
    }

    @Test fun estimateContinuesFromLastExactWordCallback() {
        assertEquals(8, estimateSpeechWordOffset("Uno dos tres cuatro", 4, 300, 1f))
    }

    @Test fun emptyUtteranceHasSafeZeroOffset() {
        assertEquals(0, estimateSpeechWordOffset("", 0, 1000, 1f))
    }
}
