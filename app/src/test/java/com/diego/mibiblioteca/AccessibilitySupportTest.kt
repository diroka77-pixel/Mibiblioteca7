package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilitySupportTest {
    @Test fun `el porcentaje se limita al intervalo accesible`() {
        assertEquals("Lectura: 0 por ciento", percentDescription("Lectura", -4))
        assertEquals("Lectura: 100 por ciento", percentDescription("Lectura", 140))
    }

    @Test fun `la pagina se expresa con indices humanos`() {
        assertEquals("Página 1 de 8", pageDescription(0, 8))
        assertEquals("Página 8 de 8", pageDescription(20, 8))
    }

    @Test fun `la velocidad usa formato castellano`() {
        assertEquals("Velocidad de lectura: 1,2 veces", speechRateDescription(1.2f))
    }
}
