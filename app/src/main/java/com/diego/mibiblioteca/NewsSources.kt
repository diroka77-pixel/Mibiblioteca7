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

/** Novedades en el idioma de la interfaz y, si se conoce, del país del teléfono. */
internal fun releasesUrl(language: String = I18n.currentLanguage(),
    country: String = java.util.Locale.getDefault().country): String {
    val countryParam = country.uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) }
        ?.let { "&country=$it" }.orEmpty()
    return "https://www.googleapis.com/books/v1/volumes" +
        "?q=subject:fiction&orderBy=newest&langRestrict=$language&printType=books&maxResults=40$countryParam"
}

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
    today: java.time.LocalDate = java.time.LocalDate.now()
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
        if (title.isBlank() || author.isBlank() || !link.startsWith("https://") ||
            !isRecentRelease(date, today)) null
        else LaunchNews(LAUNCH_SOURCE, "$title · $author", link, cover, date)
    }.distinctBy { (launchBookTitle(it) + "|" + launchBookAuthor(it)).lowercase() }
}

/** Código de idioma de Open Library (ISO 639-2) para cada idioma de la app. */
private val openLibraryLanguages = mapOf("es" to "spa", "en" to "eng", "pt" to "por",
    "fr" to "fre", "de" to "ger", "it" to "ita")

internal fun openLibraryReleasesUrl(language: String = I18n.currentLanguage()): String {
    val lang = openLibraryLanguages[language] ?: "eng"
    // sort=new ordena del más reciente al más antiguo; los años se filtran en la app.
    return "https://openlibrary.org/search.json?q=subject%3Afiction&language=$lang&sort=new&limit=60" +
        "&fields=key,title,author_name,cover_i,first_publish_year"
}

/** Segunda fuente de novedades: Open Library (abierta y sin cupo diario). */
internal fun launchNewsFromOpenLibrary(
    response: JSONObject,
    today: java.time.LocalDate = java.time.LocalDate.now()
): List<LaunchNews> {
    val docs = response.optJSONArray("docs") ?: return emptyList()
    return (0 until docs.length()).mapNotNull { index ->
        val doc = docs.optJSONObject(index) ?: return@mapNotNull null
        val title = doc.optString("title").trim()
        val author = doc.optJSONArray("author_name")?.optString(0).orEmpty().trim()
        val year = doc.optInt("first_publish_year", 0)
        val key = doc.optString("key")
        val coverId = doc.optInt("cover_i", 0)
        if (title.isBlank() || author.isBlank() || !key.startsWith("/works/") ||
            year !in (today.year - 1)..(today.year + 1)) null
        else LaunchNews(LAUNCH_SOURCE, "$title · $author", "https://openlibrary.org$key",
            if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg" else "", year.toString())
    }.distinctBy { (launchBookTitle(it) + "|" + launchBookAuthor(it)).lowercase() }
}

/**
 * Descarga las novedades. Primero Google Books; si no responde (por ejemplo, porque
 * se ha agotado su cupo diario gratuito) o trae pocas, se completa con Open Library.
 * Se ejecuta fuera del hilo principal.
 */
internal fun fetchGoogleBooksReleases(): List<LaunchNews> {
    val google = runCatching { launchNewsFromGoogleBooks(getJson(releasesUrl())) }.getOrDefault(emptyList())
    if (google.size >= 6) return google
    val openLibrary = runCatching { launchNewsFromOpenLibrary(getJson(openLibraryReleasesUrl())) }
        .getOrDefault(emptyList())
    return (google + openLibrary).distinctBy { (launchBookTitle(it) + "|" + launchBookAuthor(it)).lowercase() }
}
