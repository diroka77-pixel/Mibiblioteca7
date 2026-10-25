package com.diego.mibiblioteca

/**
 * Wikipedia y Wikcionario en el idioma de la app.
 *
 * Antes todas las consultas iban a es.wikipedia.org y es.wiktionary.org, de modo que
 * un usuario con la app en inglés o alemán recibía biografías y definiciones en español.
 * Ahora se consulta primero la edición del idioma elegido y, si no hay resultado,
 * la inglesa (la más completa) como respaldo.
 */
internal object WikiLanguage {
    fun current(): String = I18n.currentLanguage()

    /** Ediciones a consultar, por orden: la del idioma de la app y, después, la inglesa. */
    fun hosts(language: String = current()): List<String> = listOf(language, "en").distinct()

    fun wikipediaApi(language: String = current()): String =
        "https://$language.wikipedia.org/w/api.php"

    fun wiktionaryApi(language: String = current()): String =
        "https://$language.wiktionary.org/w/api.php"

    fun wiktionaryPage(word: String, language: String = current()): String =
        "https://$language.wiktionary.org/wiki/" + java.net.URLEncoder.encode(word, "UTF-8")

    fun wiktionaryName(language: String = current()): String = when (language) {
        "es" -> "Wikcionario"
        "pt" -> "Wikcionário"
        "fr" -> "Wiktionnaire"
        "it" -> "Wikizionario"
        else -> "Wiktionary"
    }

    /** Palabras que ayudan al buscador a distinguir a un escritor de un homónimo. */
    fun authorHint(language: String = current()): String = when (language) {
        "en" -> "writer author"
        "pt" -> "escritor autor"
        "fr" -> "écrivain auteur"
        "de" -> "Schriftsteller Autor"
        "it" -> "scrittore autore"
        else -> "escritor autor"
    }

    /** Palabras que ayudan a encontrar el artículo del libro y no el de una película. */
    fun bookHint(language: String = current()): String = when (language) {
        "en" -> "book novel"
        "pt" -> "livro romance"
        "fr" -> "livre roman"
        "de" -> "Buch Roman"
        "it" -> "libro romanzo"
        else -> "libro novela"
    }

    /** Títulos habituales de la sección de argumento en cada Wikipedia. */
    fun plotSection(language: String = current()): Regex = Regex("(?i)^(" + when (language) {
        "en" -> "plot|plot summary|synopsis|summary|story"
        "pt" -> "enredo|sinopse|resumo|trama|argumento"
        "fr" -> "résumé|intrigue|synopsis|histoire"
        "de" -> "handlung|inhalt|zusammenfassung"
        "it" -> "trama|sinossi|riassunto"
        else -> "argumento|sinopsis|trama|resumen"
    } + ")$")

    private val disambiguationWords = listOf(
        "desambiguación", "disambiguation", "desambiguação", "homonymie",
        "désambiguïsation", "begriffsklärung", "disambigua"
    )

    fun isDisambiguation(title: String): Boolean =
        disambiguationWords.any { title.contains(it, ignoreCase = true) }

    /** Idiomas para las etiquetas de Wikidata: el de la app, inglés y español. */
    fun wikidataLanguages(language: String = current()): List<String> =
        listOf(language, "en", "es").distinct()
}

/** Búsqueda en la Wikipedia del idioma de la app (para enlazar la fuente de un texto). */
internal fun wikipediaSearchUrl(query: String, language: String = WikiLanguage.current()): String =
    "https://$language.wikipedia.org/w/index.php?search=" + java.net.URLEncoder.encode(query, "UTF-8")
