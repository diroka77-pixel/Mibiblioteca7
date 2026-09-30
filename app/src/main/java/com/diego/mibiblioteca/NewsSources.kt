package com.diego.mibiblioteca

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import org.jsoup.Jsoup

data class LaunchNews(
    val source: String, val title: String, val url: String,
    val imageUrl: String, val releaseDate: String
)

internal val starterNews = listOf(
    LaunchNews("Clara", "Las 25 novedades en libros más esperadas del otoño de 2026",
        "https://www.clara.es/estilo-de-vida/novedades-libros-esperadas-otono-2026_49129",
        "https://www.bing.com/th?id=ONUT.GHE09UlTMubiIlAklf_Jbw&pid=News", "2026-09-27"),
    LaunchNews("Cosmopolitan", "Diez libros nuevos para leer en octubre",
        "https://www.cosmopolitan.com/es/entretenimiento-cultura/g73876578/libros-recomendados-octubre-2026/",
        "https://www.bing.com/th?id=ONUT.kbO_qElBMGBPxEkKw6CKIg&pid=News", "2026-09-28"),
    LaunchNews("El Independiente", "Grandes lanzamientos literarios de octubre",
        "https://www.msn.com/es-es/noticias/otras/de-p%C3%A9rez-reverte-y-almod%C3%B3var-a-jon-fosse-los-grandes-lanzamientos-literarios-para-octubre/ar-AA2dczPZ",
        "https://www.bing.com/th?id=ONUT.m7bQKzY3qjbARUUJY99_lA&pid=News", "2026-09-29")
)

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
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MiBiblioteca)")
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
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MiBiblioteca)")
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
        LaunchNews("Casa del Libro", title.text() +
            if (author.isBlank()) "" else " · $author", link, image, checked)
    }.distinctBy { it.url }.take(5)
} catch (_: Exception) { emptyList() }
