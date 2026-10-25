package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLanguageTest {
    @Test fun usesTheEditionOfTheAppLanguage() {
        assertEquals("https://de.wikipedia.org/w/api.php", WikiLanguage.wikipediaApi("de"))
        assertEquals("https://fr.wiktionary.org/w/api.php", WikiLanguage.wiktionaryApi("fr"))
        assertEquals("https://it.wiktionary.org/wiki/casa", WikiLanguage.wiktionaryPage("casa", "it"))
    }

    @Test fun fallsBackToEnglishWithoutRepeating() {
        assertEquals(listOf("pt", "en"), WikiLanguage.hosts("pt"))
        assertEquals(listOf("en"), WikiLanguage.hosts("en"))
    }

    @Test fun recognisesPlotSectionsAndDisambiguationPages() {
        assertTrue(WikiLanguage.plotSection("en").containsMatchIn("Plot"))
        assertTrue(WikiLanguage.plotSection("de").containsMatchIn("Handlung"))
        assertTrue(WikiLanguage.plotSection("es").containsMatchIn("Argumento"))
        assertFalse(WikiLanguage.plotSection("en").containsMatchIn("Reception"))
        assertTrue(WikiLanguage.isDisambiguation("Dune (disambiguation)"))
        assertTrue(WikiLanguage.isDisambiguation("Faust (Begriffsklärung)"))
        assertFalse(WikiLanguage.isDisambiguation("Dune (novel)"))
    }

    @Test fun wikidataLabelsIncludeAppLanguageEnglishAndSpanish() {
        assertEquals(listOf("it", "en", "es"), WikiLanguage.wikidataLanguages("it"))
        assertEquals(listOf("es", "en"), WikiLanguage.wikidataLanguages("es"))
    }
}
