package com.diego.mibiblioteca

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import org.jsoup.Jsoup

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
    .filter { it.source == "Casa del Libro" }
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
            confidence = 90, source = "Catálogo editorial", language = "es"
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

internal val featuredNewsSources = linkedMapOf(
    "Fnac" to "https://www.fnac.es/s129487/Proximos-lanzamientos-en-libros",
    "Librotea" to "https://librotea.eldiario.es/estanterias",
    "Lecturalia" to "https://www.lecturalia.com/libros/pu/novedades-editoriales",
    "Agapea" to "https://www.agapea.com/proximos-lanzamientos-libros/",
    "Planeta de Libros" to "https://www.planetadelibros.com/blog/noticias"
)

private fun imageFrom(element: org.jsoup.nodes.Element?): String? {
    if (element == null) return null
    val direct = listOf("data-src", "data-original", "data-lazy-src", "src")
        .firstNotNullOfOrNull { key -> element.absUrl(key).takeIf { it.startsWith("https://") } }
    if (direct != null && !Regex("(?i)placeholder|logo|icon|avatar|sprite")
            .containsMatchIn(direct)) return direct
    val srcset = element.attr("data-srcset").ifBlank { element.attr("srcset") }
        .substringBefore(',').trim().substringBefore(' ')
    if (srcset.isBlank()) return null
    return try { java.net.URI(element.baseUri()).resolve(srcset).toString()
        .takeIf { it.startsWith("https://") } } catch (_: Exception) { null }
}

internal fun articleImageUrl(address: String): String? = try {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 5000
    connection.readTimeout = 6000
    connection.setRequestProperty("User-Agent", AppBrand.WEB_USER_AGENT)
    val doc = try { connection.inputStream.use { Jsoup.parse(it, "UTF-8", address) } }
        finally { connection.disconnect() }
    listOf("meta[property=og:image]", "meta[property=og:image:secure_url]",
        "meta[name=twitter:image]", "meta[itemprop=image]")
        .firstNotNullOfOrNull { selector ->
            doc.selectFirst(selector)?.let { imageFrom(it) ?: it.absUrl("content")
                .takeIf { url -> url.startsWith("https://") } }
        } ?: imageFrom(doc.selectFirst("article img, main img"))
} catch (_: Exception) { null }

internal fun fetchFeaturedNews(source: String, address: String): List<LaunchNews> = try {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 6500
    connection.readTimeout = 8000
    connection.setRequestProperty("User-Agent", AppBrand.WEB_USER_AGENT)
    val doc = try { connection.inputStream.use { Jsoup.parse(it, "UTF-8", address) } }
        finally { connection.disconnect() }
    val host = Uri.parse(address).host.orEmpty().removePrefix("www.")
    val today = java.time.LocalDate.now().toString()
    val rejected = Regex("(?i)^(logo|inicio|libros|revista|actualidad|lecturalia|librotea|planeta ?de ?libros|ver más|leer más)")
    val topical = Regex("(?i)novedad|libro|novela|lanzamiento|lectura|publica|autor|escritor|premio|literar")
    val candidates = doc.select("article a[href], h1 a[href], h2 a[href], h3 a[href], .card a[href], main a[href]").mapNotNull { link ->
        val destination = link.absUrl("href")
        val destinationHost = Uri.parse(destination).host.orEmpty().removePrefix("www.")
        if (!destination.startsWith("https://") || destination == address ||
            destinationHost != host || destination.contains("#")) return@mapNotNull null
        val picture = link.selectFirst("img") ?: link.parent()?.selectFirst("img")
        val image = imageFrom(picture)
        val title = link.text().trim().ifBlank { picture?.attr("alt")?.trim().orEmpty() }
        if (title.length !in 16..150 || rejected.containsMatchIn(title) || title.equals(source, true) ||
            title.contains("cookie", true) ||
            title.contains("iniciar sesión", true) ||
            !topical.containsMatchIn(title)) return@mapNotNull null
        title to (destination to image)
    }.distinctBy { it.first.lowercase() }.take(6)
    val stories = candidates.mapNotNull { (title, linkAndImage) ->
        val (destination, listingImage) = linkAndImage
        val image = articleImageUrl(destination) ?: listingImage ?: return@mapNotNull null
        LaunchNews(source, title, destination, image, today)
    }.take(2)
    if (stories.isNotEmpty()) stories else {
        val headline = doc.select("main h1, article h1, main h2, article h2").asSequence()
            .map { it.text().trim() }
            .firstOrNull { it.length in 16..150 && !rejected.containsMatchIn(it) &&
                !it.equals(source, true) && topical.containsMatchIn(it) }
        val image = imageFrom(doc.selectFirst("main img, article img"))
        if (headline != null && image != null) listOf(LaunchNews(source, headline, address, image, today))
        else emptyList()
    }
} catch (_: Exception) { emptyList() }

internal fun isLowQualityFeaturedNews(news: LaunchNews): Boolean =
    news.source in featuredNewsSources && (
        news.title.contains("logo", true) || news.title.equals(news.source, true) ||
        news.title.startsWith("Revista ${news.source}", true) || news.title.length < 16)

internal fun newsQueries(includeLatest: Boolean = false): List<String> {
    val today = java.time.LocalDate.now()
    val next = today.plusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",
        java.util.Locale("es", "ES")))
    val season = when (today.monthValue) {
        3, 4, 5 -> "primavera"
        6, 7, 8 -> "verano"
        9, 10, 11 -> "otoño"
        else -> "invierno"
    }
    val regular = listOf("lanzamientos libros $season ${today.year}",
        "libros nuevos $next", "novedades literarias $season ${today.year}")
    return if (includeLatest) regular + listOf("libros nuevos literatura",
        "próximos lanzamientos novelas", "novedades editoriales libros") else regular
}

internal fun fetchLiteraryNews(query: String): List<LaunchNews> = try {
    val address = "https://www.bing.com/news/search?q=" +
        java.net.URLEncoder.encode(query, "UTF-8") + "&format=rss&mkt=es-ES"
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 7000
    connection.readTimeout = 8000
    connection.setRequestProperty("User-Agent", AppBrand.WEB_USER_AGENT)
    connection.useCaches = false
    connection.setRequestProperty("Cache-Control", "no-cache")
    val doc = try { connection.inputStream.use { stream ->
        Jsoup.parse(stream, "UTF-8", address, org.jsoup.parser.Parser.xmlParser())
    } } finally { connection.disconnect() }
    val today = java.time.LocalDate.now()
    doc.select("item").mapNotNull { item ->
        val headline = item.selectFirst("title")?.text()?.trim().orEmpty()
        val image = item.children().firstOrNull { it.tagName().substringAfterLast(':')
            .equals("Image", ignoreCase = true) }?.text()?.replace("http://", "https://").orEmpty()
        val rssLink = item.selectFirst("link")?.text().orEmpty()
        val article = try { Uri.parse(rssLink).getQueryParameter("url") ?: rssLink }
            catch (_: Exception) { rssLink }
        val source = item.children().firstOrNull { it.tagName().substringAfterLast(':')
            .equals("Source", ignoreCase = true) }?.text()?.ifBlank { "Noticias" } ?: "Noticias"
        val published = try {
            java.time.ZonedDateTime.parse(item.selectFirst("pubDate")?.text().orEmpty(),
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate()
        } catch (_: Exception) { null }
        val topical = Regex("(?i)libros?|novelas?|literari|editorial|lecturas?")
            .containsMatchIn(headline) && Regex("(?i)nuev|novedad|lanzamiento|próxim|esperad|publica|lectur")
            .containsMatchIn(headline)
        val timely = published != null && !published.isBefore(today.minusDays(60)) &&
            !published.isAfter(today.plusDays(1))
        if (topical && timely && headline.length in 20..180 &&
            image.startsWith("https://") && article.startsWith("https://"))
            LaunchNews(source, headline, article, image, published.toString()) else null
    }.take(10)
} catch (_: Exception) { emptyList() }

internal fun fetchCasaUpcomingBooks(): List<LaunchNews> = try {
    val address = "https://www.casadellibro.com/proximos-lanzamientos-en-libros"
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 7000
    connection.readTimeout = 9000
    connection.useCaches = false
    connection.setRequestProperty("Cache-Control", "no-cache")
    connection.setRequestProperty("User-Agent", AppBrand.WEB_USER_AGENT)
    val doc = try { connection.inputStream.use { stream ->
        Jsoup.parse(stream, "UTF-8", address)
    } } finally { connection.disconnect() }
    val checked = java.time.LocalDate.now().toString()
    doc.select(".product-card").mapNotNull { card ->
        val title = card.selectFirst("a.product-title") ?: return@mapNotNull null
        val author = card.selectFirst("p.autores")?.text()?.trim().orEmpty()
        val imageNode = card.selectFirst("a.image img")
        val image = imageNode?.absUrl("data-src").orEmpty().ifBlank {
            imageNode?.absUrl("src").orEmpty()
        }
        val link = title.absUrl("href")
        if (title.text().isBlank() || !link.startsWith("https://www.casadellibro.com/") ||
            !image.startsWith("https://imagessl")) return@mapNotNull null
        val cardText = card.text()
        val dateText = card.selectFirst("time, .release-date, .product-date, .fecha")
            ?.let { it.attr("datetime").ifBlank { it.text() } }.orEmpty()
        val release = parseSpanishReleaseDate(dateText.ifBlank { cardText }) ?: checked
        LaunchNews("Casa del Libro", title.text() +
            if (author.isBlank()) "" else " · $author", link, image, release)
    }.distinctBy { it.url }.take(5)
} catch (_: Exception) { emptyList() }

private fun parseSpanishReleaseDate(text: String): String? {
    val iso = Regex("\\b(20\\d{2})-(\\d{2})-(\\d{2})\\b").find(text)
    if (iso != null) return iso.value
    val numeric = Regex("\\b(\\d{1,2})[/-](\\d{1,2})[/-](20\\d{2})\\b").find(text)
    if (numeric != null) return runCatching {
        java.time.LocalDate.of(numeric.groupValues[3].toInt(), numeric.groupValues[2].toInt(),
            numeric.groupValues[1].toInt()).toString()
    }.getOrNull()
    val months = mapOf("enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12)
    val written = Regex("(?i)\\b(\\d{1,2})\\s+de\\s+([a-záéíóú]+)(?:\\s+de)?\\s+(20\\d{2})\\b").find(text)
        ?: return null
    val month = months[written.groupValues[2].lowercase()] ?: return null
    return runCatching { java.time.LocalDate.of(written.groupValues[3].toInt(), month,
        written.groupValues[1].toInt()).toString() }.getOrNull()
}

internal fun fetchLaunchBookDetails(result: PublicBookResult): PublicBookDetails? {
    if (!result.sourceUrl.startsWith("https://www.casadellibro.com/")) return null
    return try {
    val connection = URL(result.sourceUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 7000
    connection.readTimeout = 9000
    connection.setRequestProperty("User-Agent", AppBrand.WEB_USER_AGENT)
    val doc = try { connection.inputStream.use { Jsoup.parse(it, "UTF-8", result.sourceUrl) } }
        finally { connection.disconnect() }
    val description = listOf("[itemprop=description]", ".product-description", ".sinopsis",
        "#product-description", ".description").firstNotNullOfOrNull { selector ->
        doc.selectFirst(selector)?.text()?.trim()?.takeIf { it.length >= 80 }
    } ?: doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
    val text = doc.text()
    val pages = Regex("(?i)(\\d{2,4})\\s+p[aá]ginas").find(text)?.groupValues?.get(1).orEmpty()
    val isbn = Regex("(?i)ISBN(?:-13)?\\s*:?\\s*([0-9-]{10,17})").find(text)
        ?.groupValues?.get(1)?.filter(Char::isDigit).orEmpty()
    val published = parseSpanishReleaseDate(text) ?: result.publishDates
    val publisher = Regex("(?i)(?:Editorial|Sello)\\s*:?\\s*([\\p{L}0-9 &.'-]{2,50})")
        .find(text)?.groupValues?.get(1)?.trim().orEmpty()
        PublicBookDetails(description, "", published, pages, "Español", publisher,
            "Catálogo editorial", coverUrl = result.coverUrl)
    } catch (_: Exception) { null }
}
