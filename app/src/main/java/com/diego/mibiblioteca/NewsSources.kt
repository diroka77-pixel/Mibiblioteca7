package com.diego.mibiblioteca

import org.json.JSONObject

data class LaunchNews(
    val source: String, val title: String, val url: String,
    val imageUrl: String, val releaseDate: String
)

internal fun launchBookTitle(news: LaunchNews): String = news.title.substringBefore(" · ").trim()
internal fun launchBookAuthor(news: LaunchNews): String = news.title.substringAfter(" · ", "").trim()
internal fun formatLaunchDate(value: String): String = try {
    java.time.LocalDate.parse(value).format(java.time.format.DateTimeFormatter
        .ofLocalizedDate(java.time.format.FormatStyle.LONG)
        .withLocale(java.util.Locale.forLanguageTag(I18n.currentLanguage())))
} catch (_: Exception) { value.ifBlank { tr("Próximamente") } }

internal fun launchNewsAsPublicBooks(
    news: List<LaunchNews>,
    ownedBooks: List<Pair<String, String>>
): List<PublicBookResult> = news.asSequence()
    .filter { it.source == LAUNCH_SOURCE }
    .mapNotNull { item ->
        val title = launchBookTitle(item)
        val author = launchBookAuthor(item)
        if (title.isBlank() || author.isBlank() ||
            isLikelyOwnedRecommendation(title, author, ownedBooks)) null
        else PublicBookResult(
            title = title, author = author,
            year = item.releaseDate.take(4), isbn = "",
            coverUrl = item.imageUrl, sourceUrl = item.url,
            publishDates = item.releaseDate,
            confidence = 90, source = LAUNCH_SOURCE, language = I18n.currentLanguage()
        )
    }.distinctBy { it.title.lowercase() + "|" + it.author.lowercase() }.toList()

/** Permite que Descubrir encuentre también libros aún no indexados por las APIs públicas. */
internal fun searchLaunchCatalog(
    query: String,
    news: List<LaunchNews>,
    ownedBooks: List<Pair<String, String>> = emptyList()
): List<PublicBookResult> {
    fun normalize(value: String): String = java.text.Normalizer
        .normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
    val wanted = normalize(query)
    if (wanted.isBlank()) return emptyList()
    val terms = wanted.split(' ').filter { it.length >= 2 }
    return launchNewsAsPublicBooks(news, ownedBooks).filter { book ->
        val searchable = normalize(book.title + " " + book.author)
        searchable.contains(wanted) || terms.all(searchable::contains)
    }
}

/**
 * Novedades y próximos lanzamientos del país del usuario.
 *
 * Proceden de la API pública y oficial de Google Books. Antes se leía la web de
 * Casa del Libro y de varias librerías por detrás, algo que sus condiciones no
 * permiten y que se rompía cada vez que cambiaban su diseño. Desde la 1.0.86 se
 * consultan las novedades de las grandes editoriales de cada país (ver
 * ReleaseMarkets.kt) y solo se muestran libros con portada.
 */
internal const val LAUNCH_SOURCE = "Google Books"

/**
 * Fecha de publicación aceptada: "2026-11-12", "2026-11" (se toma el día 1) o solo el
 * año ("2026"), que se conserva tal cual y se muestra como año.
 */
internal fun googleBooksReleaseDate(value: String): String? {
    val clean = value.trim()
    return when {
        Regex("""\d{4}-\d{2}-\d{2}""").matches(clean) ->
            clean.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
        Regex("""\d{4}-\d{2}""").matches(clean) ->
            "$clean-01".takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
        Regex("""\d{4}""").matches(clean) -> clean
        else -> null
    }
}

/** ¿La fecha cae entre cuatro meses atrás y un año adelante? Con solo el año: este año o el siguiente. */
private fun isRecentRelease(date: String, today: java.time.LocalDate): Boolean =
    if (date.length == 4) date.toInt() in today.year..(today.year + 1)
    else java.time.LocalDate.parse(date).let { !it.isBefore(today.minusDays(120)) && !it.isAfter(today.plusDays(365)) }

/** Extrae las novedades de una respuesta de Google Books (sin red: se puede probar). */
internal fun launchNewsFromGoogleBooks(
    response: JSONObject,
    today: java.time.LocalDate = java.time.LocalDate.now(),
    publisher: String? = null,
    language: String? = null
): List<LaunchNews> {
    val items = response.optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).mapNotNull { index ->
        val info = items.optJSONObject(index)?.optJSONObject("volumeInfo") ?: return@mapNotNull null
        val title = info.optString("title").trim()
        val author = info.optJSONArray("authors")?.optString(0).orEmpty().trim()
        val date = googleBooksReleaseDate(info.optString("publishedDate")) ?: return@mapNotNull null
        val cover = info.optJSONObject("imageLinks")?.optString("thumbnail").orEmpty()
            .replace("http://", "https://").replace("&edge=curl", "")
        val link = info.optString("infoLink").replace("http://", "https://")
        // Google Books busca la editorial de forma aproximada: se comprueba que coincide.
        val publisherMatches = publisher == null ||
            publisherKey(info.optString("publisher")).contains(publisherKey(publisher))
        val languageMatches = language == null || info.optString("language").isBlank() ||
            info.optString("language").substringBefore('-') == language
        if (title.isBlank() || author.isBlank() || !link.startsWith("https://") ||
            cover.isBlank() || !publisherMatches || !languageMatches ||
            !isLikelyNovelty(title) || !isRecentRelease(date, today)) null
        else LaunchNews(LAUNCH_SOURCE, "$title · $author", link, cover, date)
    }.distinctBy { (launchBookTitle(it) + "|" + launchBookAuthor(it)).lowercase() }
}

/**
 * Descarga las novedades del mercado del usuario: las más recientes de varias grandes
 * editoriales de su país, en su idioma y con portada, repartidas entre editoriales.
 * Se ejecuta fuera del hilo principal.
 */
internal fun fetchGoogleBooksReleases(
    market: ReleaseMarket = currentReleaseMarket(),
    today: java.time.LocalDate = java.time.LocalDate.now(),
    publishers: List<String> = publishersForToday(market, today.dayOfYear),
    perPublisher: Int = 20
): List<LaunchNews> {
    val responses = publishers.mapNotNull { publisher ->
        runCatching { publisher to getJson(publisherReleasesUrl(publisher, market, perPublisher)) }.getOrNull()
    }
    fun collect(checkPublisher: Boolean) = interleave(responses.map { (publisher, json) ->
        launchNewsFromGoogleBooks(json, today, publisher.takeIf { checkPublisher }, market.language)
            .sortedByDescending { it.releaseDate }
    }).distinctBy { (launchBookTitle(it) + "|" + launchBookAuthor(it)).lowercase() }
    // Si la comprobación de editorial deja muy pocos libros (Google escribe a veces el
    // sello de otra forma), se aceptan los resultados de la búsqueda, siempre con portada.
    return collect(checkPublisher = true).takeIf { it.size >= 4 } ?: collect(checkPublisher = false)
}

/** ¿Aún no ha salido? Fecha completa posterior a [today] (AAAA-MM-DD) o un año posterior. */
internal fun isUpcomingRelease(date: String, today: String): Boolean = when {
    date.length >= 10 -> date.take(10) > today
    date.length == 4 -> date > today.take(4)
    else -> false
}
