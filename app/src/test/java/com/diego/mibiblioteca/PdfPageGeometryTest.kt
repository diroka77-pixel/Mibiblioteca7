package com.diego.mibiblioteca

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageGeometryTest {
    @Test fun portraitPageFitsWithoutScrolling() {
        val g = PdfPageGeometry(1080f, 2000f, 1.414f)
        assertFalse(g.canPan(1f))
        assertEquals(Offset.Zero, g.clamp(Offset(-50f, -300f), 1f))
    }

    @Test fun landscapePageScrollsVertically() {
        val g = PdfPageGeometry(2000f, 800f, 1.414f)
        assertTrue(g.canPan(1f))
        // 2000 × 1.414 = 2828 de alto: se puede bajar hasta 2028 px, ni uno más.
        assertEquals(Offset(0f, -2028f), g.clamp(Offset(-10f, -5000f), 1f))
    }

    @Test fun zoomAllowsPanningButNotLeavingThePage() {
        val g = PdfPageGeometry(1000f, 1500f, 1.414f)
        assertEquals(Offset(-1000f, 0f), g.clamp(Offset(-4000f, 300f), 2f))
    }
}
