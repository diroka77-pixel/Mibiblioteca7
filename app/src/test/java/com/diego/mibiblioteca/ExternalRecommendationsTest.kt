package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val result = launchNewsAsPublicBooks(listOf(LaunchNews(LAUNCH_SOURCE,
            "Espinas en el aire · Javier Castillo", "https://books.google.com/books?id=1",
            "https://example.com/cover.jpg", "2026-11-12")), library)
        assertTrue(result.size == 1)
        assertTrue(result.first().title == "Espinas en el aire")
        assertTrue(result.first().author == "Javier Castillo")
    }

    @Test fun excludesUpcomingEntryAlreadyOwned() {
        val result = launchNewsAsPublicBooks(listOf(LaunchNews(LAUNCH_SOURCE,
            "1984 · George Orwell", "https://books.google.com/books?id=2",
            "https://example.com/cover.jpg", "2026-11-12")), library)
        assertTrue(result.isEmpty())
    }

    @Test fun discoversUpcomingBookByTitleOrAuthor() {
        val news = listOf(LaunchNews(LAUNCH_SOURCE,
            "Espinas en el aire · Javier Castillo", "https://books.google.com/books?id=1",
            "https://example.com/cover.jpg", "2026-11-24"))
        assertTrue(searchLaunchCatalog("espinas en el aire", news).single().author == "Javier Castillo")
        assertTrue(searchLaunchCatalog("Javier Castillo", news).single().title == "Espinas en el aire")
    }

    @Test fun acceptsOnlyFullReleaseDatesFromGoogleBooks() {
        assertEquals("2026-11-12", googleBooksReleaseDate("2026-11-12"))
        assertEquals("2026-11-01", googleBooksReleaseDate("2026-11"))
        assertEquals("2026", googleBooksReleaseDate("2026"))
        assertNull(googleBooksReleaseDate("2026-13"))
        assertNull(googleBooksReleaseDate("noviembre"))
    }

    @Test fun readsRecentSpanishReleasesFromGoogleBooksResponse() {
        val response = org.json.JSONObject("""
            {"items": [
              {"volumeInfo": {"title": "Espinas en el aire", "authors": ["Javier Castillo"],
                "publishedDate": "2026-11-12", "infoLink": "http://books.google.com/books?id=1",
                "imageLinks": {"thumbnail": "http://books.google.com/cover?id=1&edge=curl"}}},
              {"volumeInfo": {"title": "Sin autor", "publishedDate": "2026-11-12",
                "infoLink": "https://books.google.com/books?id=2",
                "imageLinks": {"thumbnail": "https://books.google.com/cover?id=2"}}},
              {"volumeInfo": {"title": "Muy antiguo", "authors": ["Alguien"], "publishedDate": "1999-01-01",
                "infoLink": "https://books.google.com/books?id=3",
                "imageLinks": {"thumbnail": "https://books.google.com/cover?id=3"}}}
            ]}
        """.trimIndent())
        val news = launchNewsFromGoogleBooks(response, java.time.LocalDate.of(2026, 10, 25))
        assertEquals(1, news.size)
        assertEquals("Espinas en el aire", launchBookTitle(news.single()))
        assertEquals("Javier Castillo", launchBookAuthor(news.single()))
        assertTrue(news.single().imageUrl.startsWith("https://"))
        assertFalse(news.single().imageUrl.contains("edge=curl"))
        assertTrue(news.single().url.startsWith("https://"))
    }

    @Test fun releasesNeedCoverPublisherAndLanguage() {
        val response = org.json.JSONObject("""
            {"items": [
              {"volumeInfo": {"title": "Con portada", "authors": ["Autora"], "publisher": "Seix Barral",
                "language": "es", "publishedDate": "2026-10-01", "infoLink": "https://books.google.com/books?id=1",
                "imageLinks": {"thumbnail": "https://books.google.com/cover?id=1"}}},
              {"volumeInfo": {"title": "Sin portada", "authors": ["Autor"], "publisher": "Seix Barral",
                "language": "es", "publishedDate": "2026-10-01", "infoLink": "https://books.google.com/books?id=2"}},
              {"volumeInfo": {"title": "Otra editorial", "authors": ["Autor"], "publisher": "Autoedición",
                "language": "es", "publishedDate": "2026-10-01", "infoLink": "https://books.google.com/books?id=3",
                "imageLinks": {"thumbnail": "https://books.google.com/cover?id=3"}}},
              {"volumeInfo": {"title": "In English", "authors": ["Writer"], "publisher": "Seix Barral",
                "language": "en", "publishedDate": "2026-10-01", "infoLink": "https://books.google.com/books?id=4",
                "imageLinks": {"thumbnail": "https://books.google.com/cover?id=4"}}},
              {"volumeInfo": {"title": "Resumen de una novela", "authors": ["Alguien"], "publisher": "Seix Barral",
                "language": "es", "publishedDate": "2026-10-01", "infoLink": "https://books.google.com/books?id=5",
                "imageLinks": {"thumbnail": "https://books.google.com/cover?id=5"}}}
            ]}
        """.trimIndent())
        val news = launchNewsFromGoogleBooks(response, java.time.LocalDate.of(2026, 10, 25),
            publisher = "Seix Barral", language = "es")
        assertEquals(listOf("Con portada"), news.map(::launchBookTitle))
    }
}
