package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryWidgetTest {
    @Test fun titleFallsBackWhenThereIsNoLastBook() {
        assertEquals("Continúa tu lectura", widgetBookTitle(null))
        assertEquals("Continúa tu lectura", widgetBookTitle("   "))
        assertEquals("El talismán", widgetBookTitle("  El talismán "))
    }

    @Test fun progressLabelsCoverReadingStates() {
        assertEquals("Sin progreso guardado", widgetProgressLabel(0))
        assertEquals("37 % leído", widgetProgressLabel(37))
        assertEquals("Lectura terminada", widgetProgressLabel(100))
        assertEquals("Lectura terminada", widgetProgressLabel(140))
    }
}
