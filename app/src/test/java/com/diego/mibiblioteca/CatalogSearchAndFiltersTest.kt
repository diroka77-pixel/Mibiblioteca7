package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchAndFiltersTest {
    @Test fun searchIgnoresAccentsAndRequiresEveryTerm() {
        val text = normalizeSearchText("La canción del cielo · Rodrigo Cortés")
        assertTrue(matchesSearchDocument(text, "cancion cortes"))
        assertFalse(matchesSearchDocument(text, "cancion almela"))
    }

    @Test fun fuzzySearchRunsOnlyAfterExactSearchMisses() {
        assertTrue(shouldUseFuzzyCatalogFallback(emptySet(), "Blackwatr"))
        assertFalse(shouldUseFuzzyCatalogFallback(setOf("book://1"), "Blackwatr"))
        assertFalse(shouldUseFuzzyCatalogFallback(emptySet(), "abc"))
        assertTrue(shouldRankCatalogSearch("La riada"))
        assertFalse(shouldRankCatalogSearch("a"))
    }

    @Test fun emptyQueryRestoresEveryDocument() {
        assertTrue(matchesSearchDocument("cualquier libro", ""))
        assertTrue(matchesSearchDocument("cualquier libro", "   "))
    }

    @Test fun favoritesNeverIncludeUnmarkedBooks() {
        assertFalse(matchesCatalogFlags(
            ReadingStatus.PENDING, false, emptyList(),
            hasCover = true, hasPlot = true, hasBiography = true,
            hasKnownAuthor = true, needsReview = false,
            status = null, favoritesOnly = true, section = null, quality = "Ninguno"
        ))
        assertTrue(matchesCatalogFlags(
            ReadingStatus.PENDING, true, emptyList(),
            hasCover = true, hasPlot = true, hasBiography = true,
            hasKnownAuthor = true, needsReview = false,
            status = null, favoritesOnly = true, section = null, quality = "Ninguno"
        ))
    }

    @Test fun sagaAndOrderAreRecoveredFromTechnicalTitles() {
        assertEquals("La Torre Oscura" to "2",
            extractSagaFromText("La Torre Oscura - Libro 02 - La llegada de los tres"))
        assertEquals("Blackwater" to "6",
            extractSagaFromText("[Blackwater Vol. 06] Lluvia"))
    }

    @Test fun sectionAndQualityFiltersComposeCorrectly() {
        assertTrue(matchesCatalogFlags(
            ReadingStatus.READING, true, listOf("Fantasía"),
            hasCover = false, hasPlot = true, hasBiography = true,
            hasKnownAuthor = true, needsReview = false,
            status = ReadingStatus.READING, favoritesOnly = true,
            section = "Fantasía", quality = "Portada"
        ))
        assertFalse(matchesCatalogFlags(
            ReadingStatus.READING, true, listOf("Fantasía"),
            hasCover = true, hasPlot = true, hasBiography = true,
            hasKnownAuthor = true, needsReview = false,
            status = ReadingStatus.READING, favoritesOnly = true,
            section = "Fantasía", quality = "Portada"
        ))
    }
}
