package com.diego.mibiblioteca

import org.json.JSONObject

data class LaunchNews(
    val source: String, val title: String, val url: String,
    val imageUrl: String, val releaseDate: String
)

internal fun launchBookTitle(news: LaunchNews): String = news.title.substringBefore(" · ").trim()
internal fun launchBookAuthor(news: LaunchNews): String = news.title.substringAfter(" · ", "").trim()
internal fun formatLaunchDate(value: String): String = try {
    java.time.LocalDate.parse(value).format(java.time.format.DateTimeFormatter.ofPattern(
        "d 'de' MMMM 'de' yyyy", java.util.Locale("es", "ES")))
} catch (_: Exception) { value.ifBlank { "Próximamente" } }

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
            confidence = 90, source = LAUNCH_SOURCE, language = "es"
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
 * Novedades y próximos lanzamientos en español.
 *
 * Proceden de la API pública y oficial de Google Books. Antes se leía la web de
 * Casa del Libro y de varias librerías por detrás, algo que sus condiciones no
 * permiten y que se rompía cada vez que cambiaban su diseño.
 */
internal const val LAUNCH_SOURCE = "Google Books"

private const val RELEASES_URL = "https://www.googleapis.com/books/v1/volumes" +
    "?q=subject:fiction&orderBy=newest&langRestrict=es&printType=books&maxResults=40&country=ES"

/** Convierte "2026-11-12" o "2026-11" en una fecha ISO; un año solo no basta. */
internal fun googleBooksReleaseDate(value: String): String? {
    val clean = value.trim()
    return when {
        Regex("""\d{4}-\d{2}-\d{2}""").matches(clean) -> clean
        Regex("""\d{4}-\d{2}""").matches(clean) -> "$clean-01"
        else -> null
    }?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
}

/** Extrae las novedades de una respuesta de Google Books (sin red: se puede probar). */
internal fun launchNewsFromGoogleBooks(
    response: JSONObject,
    today: java.time.LocalDate = java.time.LocalDate.now()
): List<LaunchNews> {
    val items = response.optJSONArray("items") ?: return emptyList()
    val oldest = today.minusDays(120)
    val newest = today.plusDays(365)
    return (0 until items.length()).mapNotNull { index ->
        val info = items.optJSONObject(index)?.optJSONObject("volumeInfo") ?: return@mapNotNull null
        val title = info.optString("title").trim()
        val author = info.optJSONArray("authors")?.optString(0).orEmpty().trim()
        val date = googleBooksReleaseDate(info.optString("publishedDate")) ?: return@mapNotNull null
        val day = java.time.LocalDate.parse(date)
        val cover = info.optJSONObject("imageLinks")?.optString("thumbnail").orEmpty()
            .replace("http://", "https://").replace("&edge=curl", "")
        val link = info.optString("infoLink").replace("http://", "https://")
        if (title.isBlank() || author.isBlank() || cover.isBlank() || !link.startsWith("https://") ||
            day.isBefore(oldest) || day.isAfter(newest)) null
        else LaunchNews(LAUNCH_SOURCE, "$title · $author", link, cover, date)
    }.distinctBy { (launchBookTitle(it) + "|" + launchBookAuthor(it)).lowercase() }
}

/** Descarga las novedades. Se ejecuta fuera del hilo principal. */
internal fun fetchGoogleBooksReleases(): List<LaunchNews> =
    runCatching { launchNewsFromGoogleBooks(getJson(RELEASES_URL)) }.getOrDefault(emptyList())
