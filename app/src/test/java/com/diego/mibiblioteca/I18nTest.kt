package com.diego.mibiblioteca

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nTest {
    private val rows = I18n.allRows()

    @Test fun tableLoadsWithSixLanguages() {
        assertTrue("La tabla de traducciones no se ha cargado", rows.size > 400)
        rows.values.forEach { row ->
            assertEquals(I18n.languages.size, row.size)
            assertTrue("Traducción vacía en: ${row[0]}", row.all { it.isNotBlank() })
        }
    }

    @Test fun placeholdersMatchInEveryLanguage() {
        val placeholder = Regex("""\{\w+\}""")
        rows.values.forEach { row ->
            val expected = placeholder.findAll(row[0]).map { it.value }.sorted().toList()
            row.drop(1).forEach { translated ->
                assertEquals("Marcadores distintos en: ${row[0]}", expected,
                    placeholder.findAll(translated).map { it.value }.sorted().toList())
            }
        }
    }

    @Test fun unsupportedLanguagesFallBackToEnglish() {
        assertEquals("en", I18n.currentLanguage(Locale("ja", "JP")))
        assertEquals("pt", I18n.currentLanguage(Locale("pt", "BR")))
        assertEquals("es", I18n.currentLanguage(Locale("es", "MX")))
    }

    @Test fun translatesKnownTextsAndKeepsUnknownOnes() {
        assertEquals("Settings", I18n.translate("Ajustes", "en"))
        assertEquals("Paramètres", I18n.translate("Ajustes", "fr"))
        assertEquals("Ajustes", I18n.translate("Ajustes", "es"))
        assertEquals("Un título de libro", I18n.translate("Un título de libro", "de"))
    }

    @Test fun contextKeysSeparateMeanings() {
        assertEquals("Home", I18n.translate("nav|Inicio", "en"))
        assertEquals("Start", I18n.translate("Inicio", "en"))
    }

    @Test fun releasesFollowTheLanguage() {
        val url = releasesUrl("fr", "FR")
        assertTrue(url.contains("langRestrict=fr"))
        assertTrue(url.contains("country=FR"))
        assertTrue(!releasesUrl("en", "").contains("country="))
    }

    @Test fun trailingSpacesAreRestoredFromSpanish() {
        val parsed = I18n.parse(listOf("es\ten\tpt\tfr\tde\tit",
            "Fondo: \tBackground:\tFundo:\tFond :\tHintergrund:\tSfondo:"))
        assertEquals("Sfondo: ", parsed.getValue("Fondo: ")[5])
        assertEquals("Background: ", parsed.getValue("Fondo: ")[1])
    }

    @Test fun genresAndStatusesHaveTranslations() {
        listOf("Fantasía", "Ciencia ficción", "Terror", "Thriller", "Misterio", "Romance",
            "Novela histórica", "Aventuras", "Juvenil", "Infantil", "Clásicos", "Biografía",
            "Historia", "Ensayo", "Divulgación", "Humor", "Poesía", "Teatro",
            "Cómic y novela gráfica", "No ficción", "Otros", "Sin clasificar").forEach { genre ->
            assertTrue("Falta el género $genre", rows.containsKey("género|$genre"))
        }
        assertEquals("Fantasy", I18n.translate("género|Fantasía", "en"))
        ReadingStatus.values().forEach { assertTrue("Falta el estado ${it.label}", rows.containsKey(it.label)) }
        assertEquals("Lu", I18n.translate("Leído", "fr"))
    }
}
