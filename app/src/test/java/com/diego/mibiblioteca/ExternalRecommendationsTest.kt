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

    @Test fun readsReleaseDatesInSeveralFormats() {
        assertEquals("2026-11-12", releaseDateFrom("2026-11-12"))
        assertEquals("2026-11-01", releaseDateFrom("2026-11"))
        assertEquals("2026-11-12", releaseDateFrom("Nov 12, 2026"))
        assertEquals("2026-11-12", releaseDateFrom("12 November 2026"))
        assertEquals("2026", releaseDateFrom("2026"))
        assertEquals("2026", releaseDateFrom("noviembre de 2026"))
        assertNull(releaseDateFrom("noviembre"))
    }

    @Test fun releasesFromOpenLibraryNeedCoverPublisherAndRecentYear() {
        val response = org.json.JSONObject("""
            {"docs": [
              {"key": "/works/OL1W", "title": "Original title", "author_name": ["Autora"],
               "first_publish_year": 2026, "cover_i": 11, "publisher": ["Seix Barral"],
               "editions": {"docs": [{"key": "/books/OL1M", "title": "Con portada",
                 "cover_i": 12, "publish_date": ["Oct 1, 2026"]}]}},
              {"key": "/works/OL2W", "title": "Sin portada", "author_name": ["Autor"],
               "first_publish_year": 2026, "publisher": ["Seix Barral"]},
              {"key": "/works/OL3W", "title": "Otra editorial", "author_name": ["Autor"],
               "first_publish_year": 2026, "cover_i": 3, "publisher": ["Autoedición"]},
              {"key": "/works/OL4W", "title": "Muy antigua", "author_name": ["Autor"],
               "first_publish_year": 1990, "cover_i": 4, "publisher": ["Seix Barral"]},
              {"key": "/works/OL5W", "title": "Resumen de una novela", "author_name": ["Alguien"],
               "first_publish_year": 2026, "cover_i": 5, "publisher": ["Seix Barral"]}
            ]}
        """.trimIndent())
        val news = launchNewsFromOpenLibrary(response, java.time.LocalDate.of(2026, 10, 25), publisher = "Seix Barral")
        assertEquals(listOf("Con portada"), news.map(::launchBookTitle))
        assertEquals("2026-10-01", news.single().releaseDate)
        assertEquals("https://covers.openlibrary.org/b/id/12-L.jpg", news.single().imageUrl)
        assertEquals("https://openlibrary.org/works/OL1W", news.single().url)
    }

    @Test fun genresMapToOpenLibrarySubjects() {
        assertEquals("science_fiction", openLibrarySubject("Ciencia ficción"))
        assertEquals("fantasy", openLibrarySubject("Fantasía"))
        assertEquals("historical_fiction", openLibrarySubject("Novela histórica"))
        assertNull(openLibrarySubject("Otros"))
    }
}
