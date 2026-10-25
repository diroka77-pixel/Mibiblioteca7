package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMarketsTest {
    @Test fun marketFollowsTheCountry() {
        assertEquals("es", releaseMarket("ES", "en").language)
        assertTrue("Planeta" in releaseMarket("ES", "es").publishers)
        assertEquals("MX", releaseMarket("MX", "es").country)
        assertEquals("en", releaseMarket("US", "es").language)
        assertEquals("fr", releaseMarket("FR", "es").language)
        assertEquals("pt", releaseMarket("BR", "pt").language)
        assertTrue("Porto Editora" in releaseMarket("PT", "pt").publishers)
    }

    @Test fun multilingualOrUnknownCountriesUseTheAppLanguage() {
        assertEquals("de", releaseMarket("CH", "de").language)
        assertEquals("fr", releaseMarket("CH", "fr").language)
        assertEquals("fr", releaseMarket("CA", "fr").language)
        assertEquals("en", releaseMarket("CA", "en").language)
        assertEquals("it", releaseMarket("JP", "it").language)
        assertEquals("ES", releaseMarket(null, "es").country)
    }

    @Test fun publishersRotateWithoutRepeating() {
        val market = releaseMarket("ES", "es")
        val today = publishersForToday(market, 10)
        assertEquals(8, today.size)
        assertEquals(today.size, today.distinct().size)
        assertTrue(publishersForToday(market, 11) != today)
    }

    @Test fun resultsAlternateBetweenPublishers() {
        assertEquals(listOf(1, 10, 2, 20, 3), interleave(listOf(listOf(1, 2, 3), listOf(10, 20))))
    }

    @Test fun summariesAndPacksAreNotNovelties() {
        assertTrue(isLikelyNovelty("Las hijas de la criada"))
        assertTrue(!isLikelyNovelty("Resumen de Las hijas de la criada"))
        assertTrue(!isLikelyNovelty("Trilogía completa (Pack)"))
    }

    @Test fun publisherNamesCompareWithoutAccentsOrSymbols() {
        assertTrue(publisherKey("PLAZA & JANES").contains(publisherKey("Plaza & Janés")))
        assertTrue(publisherKey("Little, Brown and Company").contains(publisherKey("Little, Brown")))
    }

    @Test fun upcomingReleasesAreAfterToday() {
        assertTrue(isUpcomingRelease("2026-11-12", "2026-10-25"))
        assertTrue(!isUpcomingRelease("2026-10-01", "2026-10-25"))
        assertTrue(isUpcomingRelease("2027", "2026-10-25"))
        assertTrue(!isUpcomingRelease("2026", "2026-10-25"))
    }

    @Test fun openLibraryQueryUsesPublisherLanguageAndYears() {
        val url = java.net.URLDecoder.decode(publisherReleasesUrl("Seix Barral", releaseMarket("ES", "es"), year = 2026), "UTF-8")
        assertTrue(url.startsWith("https://openlibrary.org/search.json"))
        assertTrue(url.contains("publisher:\"Seix Barral\""))
        assertTrue(url.contains("language:spa"))
        assertTrue(url.contains("first_publish_year:[2025 TO 2027]"))
        assertTrue(url.contains("lang=es"))
    }
}
