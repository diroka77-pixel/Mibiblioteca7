package com.diego.mibiblioteca

import org.junit.Assert.*
import org.junit.Test

class CoverPresentationTest {
    @Test fun portraitCoverFitsWithoutCropping() {
        assertEquals(Pair(120, 180), CoverPresentation.fittedSize(600, 900, 120, 180))
    }
    @Test fun landscapeCoverKeepsItsAspectRatio() {
        assertEquals(Pair(180, 90), CoverPresentation.fittedSize(800, 400, 180, 180))
    }
    @Test fun invalidSourceIsRejected() {
        assertNull(CoverPresentation.fittedSize(0, 300, 120, 180))
    }
    @Test fun smallCoversAreNotUpscaled() {
        assertEquals(Pair(50, 75), CoverPresentation.fittedSize(50, 75, 120, 180))
    }
}
