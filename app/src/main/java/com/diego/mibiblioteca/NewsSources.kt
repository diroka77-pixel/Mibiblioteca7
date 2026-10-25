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
 * Proceden de la API abierta de Open Library: las obras más recientes de las grandes
 * editoriales de cada país (ver ReleaseMarkets.kt), en su idioma y con portada.
 * Hasta la 1.0.86 se usaba Google Books, pero sus condiciones no permiten cobrar por
 * la app; antes aún se leía la web de varias librerías por detrás.
 */
internal const val LAUNCH_SOURCE = "Open Library"

/**
 * Fecha de publicación aceptada: "2026-11-12", "2026-11" (se toma el día 1), fechas en
 * inglés como "Nov 12, 2026" o "November 2026", o solo el año ("2026").
 */
internal fun releaseDateFrom(value: String): String? {
    val clean = value.trim()
    Regex("""\d{4}-\d{2}-\d{2}""").find(clean)?.value
        ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }?.let { return it }
    Regex("""^\d{4}-\d{2}$""").find(clean)?.value?.let { "$it-01" }
        ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }?.let { return it }
    listOf("MMM d, yyyy", "MMMM d, yyyy", "d MMM yyyy", "d MMMM yyyy").forEach { pattern ->
        runCatching {
            java.time.LocalDate.parse(clean, java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH))
        }.getOrNull()?.let { return it.toString() }
    }
    return Regex("""\b(19|20)\d{2}\b""").find(clean)?.value
}

/** ¿La fecha cae entre cuatro meses atrás y un año adelante? Con solo el año: este año o el siguiente. */
private fun isRecentRelease(date: String, today: java.time.LocalDate): Boolean =
    if (date.length == 4) date.toInt() in today.year..(today.year + 1)
    else java.time.LocalDate.parse(date).let { !it.isBefore(today.minusDays(120)) && !it.isAfter(today.plusDays(365)) }

/**
 * Extrae las novedades de una respuesta de búsqueda de Open Library (sin red: se puede
 * probar). Usa la edición en el idioma de la app si viene (título y portada traducidos).
 */
internal fun launchNewsFromOpenLibrary(
    response: JSONObject,
    today: java.time.LocalDate = java.time.LocalDate.now(),
    publisher: String? = null
): List<LaunchNews> {
    val docs = response.optJSONArray("docs") ?: return emptyList()
    return (0 until docs.length()).mapNotNull { index ->
        val doc = docs.optJSONObject(index) ?: return@mapNotNull null
        val edition = doc.optJSONObject("editions")?.optJSONArray("docs")?.optJSONObject(0)
        val title = edition?.optString("title").orEmpty().ifBlank { doc.optString("title") }.trim()
        val author = doc.optJSONArray("author_name")?.optString(0).orEmpty().trim()
        val key = doc.optString("key")
        val coverId = (edition?.optInt("cover_i", 0) ?: 0).takeIf { it > 0 } ?: doc.optInt("cover_i", 0)
        val editionDate = edition?.optJSONArray("publish_date")?.optString(0)
            ?: edition?.optString("publish_date").orEmpty()
        val date = releaseDateFrom(editionDate).takeIf { it != null && it.length > 4 }
            ?: doc.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString()
            ?: return@mapNotNull null
        // La búsqueda por editorial de Open Library es aproximada: se comprueba que coincide.
        val publishers = doc.optJSONArray("publisher")?.let { list ->
            (0 until list.length()).map { publisherKey(list.optString(it)) }
        }.orEmpty()
        val publisherMatches = publisher == null || publishers.any { it.contains(publisherKey(publisher)) }
        if (title.isBlank() || author.isBlank() || !key.startsWith("/works/") || coverId <= 0 ||
            !publisherMatches || !isLikelyNovelty(title) || !isRecentRelease(date, today)) null
        else LaunchNews(LAUNCH_SOURCE, "$title · $author", "https://openlibrary.org$key",
            "https://covers.openlibrary.org/b/id/$coverId-L.jpg", date)
    }.distinctBy { (launchBookTitle(it) + "|" + launchBookAuthor(it)).lowercase() }
}

/**
 * Descarga las novedades del mercado del usuario: las más recientes de varias grandes
 * editoriales de su país, en su idioma y con portada, repartidas entre editoriales.
 * Se ejecuta fuera del hilo principal.
 */
internal fun fetchMarketReleases(
    market: ReleaseMarket = currentReleaseMarket(),
    today: java.time.LocalDate = java.time.LocalDate.now(),
    publishers: List<String> = publishersForToday(market, today.dayOfYear),
    perPublisher: Int = 30
): List<LaunchNews> {
    val responses = publishers.mapNotNull { publisher ->
        runCatching { publisher to getJson(publisherReleasesUrl(publisher, market, perPublisher, today.year)) }.getOrNull()
    }
    fun collect(checkPublisher: Boolean) = interleave(responses.map { (publisher, json) ->
        launchNewsFromOpenLibrary(json, today, publisher.takeIf { checkPublisher })
            .sortedByDescending { it.releaseDate }
    }).distinctBy { (launchBookTitle(it) + "|" + launchBookAuthor(it)).lowercase() }
    // Si la comprobación de editorial deja muy pocos libros (el sello aparece escrito de
    // otra forma), se aceptan los resultados de la búsqueda, siempre con portada.
    return collect(checkPublisher = true).takeIf { it.size >= 4 } ?: collect(checkPublisher = false)
}

/** ¿Aún no ha salido? Fecha completa posterior a [today] (AAAA-MM-DD) o un año posterior. */
internal fun isUpcomingRelease(date: String, today: String): Boolean = when {
    date.length >= 10 -> date.take(10) > today
    date.length == 4 -> date > today.take(4)
    else -> false
}
