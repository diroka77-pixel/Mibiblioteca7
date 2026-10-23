package com.diego.mibiblioteca

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalRecommendationsTest {
    private val library = listOf(
        "Alicia en el país de las maravillas" to "Lewis Carroll",
        "1984" to "George Orwell",
        "El nombre de la rosa" to "Umberto Eco"
    )

    @Test fun rejectsAnotherEditionWithSubtitle() {
        assertTrue(isLikelyOwnedRecommendation(
            "1984: una novela", "George Orwell", library))
        assertTrue(isLikelyOwnedRecommendation(
            "El nombre de la rosa (edición ilustrada)", "Umberto Eco", library))
    }

    @Test fun keepsAGenuinelyDifferentBook() {
        assertFalse(isLikelyOwnedRecommendation(
            "La casa de los espíritus", "Isabel Allende", library))
    }

    @Test fun convertsUpcomingCatalogEntryAndSeparatesAuthor() {
        val result = launchNewsAsPublicBooks(listOf(LaunchNews("Casa del Libro",
            "Espinas en el aire · Javier Castillo", "https://www.casadellibro.com/libro/1",
            "https://example.com/cover.jpg", "2026-11-12")), library)
        assertTrue(result.size == 1)
        assertTrue(result.first().title == "Espinas en el aire")
        assertTrue(result.first().author == "Javier Castillo")
    }

    @Test fun excludesUpcomingEntryAlreadyOwned() {
        val result = launchNewsAsPublicBooks(listOf(LaunchNews("Casa del Libro",
            "1984 · George Orwell", "https://www.casadellibro.com/libro/2",
            "https://example.com/cover.jpg", "2026-11-12")), library)
        assertTrue(result.isEmpty())
    }

    @Test fun rejectsWebsiteLogosAsLiteraryNews() {
        assertTrue(isLowQualityFeaturedNews(LaunchNews("Librotea", "Logo Librotea",
            "https://librotea.eldiario.es", "https://example.com/logo.png", "2026-10-23")))
        assertFalse(isLowQualityFeaturedNews(LaunchNews("Librotea",
            "Las novelas españolas que marcarán este otoño", "https://librotea.eldiario.es/a",
            "https://example.com/book.jpg", "2026-10-23")))
    }
}
