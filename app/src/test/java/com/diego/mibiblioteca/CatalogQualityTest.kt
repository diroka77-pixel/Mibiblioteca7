package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogQualityTest {
    @Test fun normalizaAcentosSignosYEspacios() {
        assertEquals("la torre oscura 01", CatalogQuality.normalize("  La Torre Óscura [01]  "))
    }

    @Test fun isbnExactoTieneConfianzaMaxima() {
        val score = CatalogQuality.confidenceForIdentity(
            "Título distinto", "Autor distinto", "Otra edición", "Otra persona",
            "9788439747369", "978-84-397-4736-9"
        )
        assertEquals(100, score)
    }

    @Test fun rechazaIdentidadDeOtroLibro() {
        val score = CatalogQuality.confidenceForIdentity(
            "Hija del cielo", "Rodrigo Cortés",
            "Hijas del cielo", "Pedro Antonio Almela"
        )
        assertTrue(score < 70)
    }

    @Test fun aceptaPequenaErrata() {
        assertTrue(CatalogQuality.levenshtein("tom sawyer", "tom soyer") <= 2)
    }

    @Test fun validaProporcionDePortada() {
        assertTrue(CatalogQuality.acceptableCover(90, 600, 900))
        assertTrue(!CatalogQuality.acceptableCover(40, 600, 900))
        assertTrue(!CatalogQuality.acceptableCover(90, 80, 80))
    }

    @Test fun metadataCompletaGanaSinAceptarOtraIdentidad() {
        val selected = bestMetadataEvidence(
            "El dique", "Michael McDowell", "",
            listOf(
                MetadataEvidence("El dique", "Michael McDowell", summary = "Breve", confidence = 70),
                MetadataEvidence("El dique", "Michael McDowell",
                    summary = "Un argumento suficientemente completo para presentar correctamente la obra al lector.",
                    coverUrl = "https://example.test/cover.jpg", confidence = 80),
                MetadataEvidence("El dique", "Otro autor", confidence = 95)
            )
        )
        assertEquals("Michael McDowell", selected?.author)
        assertTrue(selected?.coverUrl?.isNotBlank() == true)
    }
}
