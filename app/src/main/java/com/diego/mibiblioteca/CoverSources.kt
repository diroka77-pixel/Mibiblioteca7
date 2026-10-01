package com.diego.mibiblioteca

import org.jsoup.Jsoup
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer

internal fun coverWords(text: String): List<String> = Normalizer.normalize(
    cleanCatalogText(text).lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(Regex("\\s+"))
    .filter { it.isNotBlank() }

internal fun coverTitleMatches(wanted: String, candidate: String): Boolean {
    val a = coverWords(wanted.substringBefore(" (").substringBefore(" [")).filterNot { it in setOf("el", "la", "los", "las", "un", "una", "de", "del", "y") }
    val b = coverWords(candidate)
    if (a.isEmpty() || b.isEmpty()) return false
    if (a.size == 1) return a.first().length >= 2 && a.first() in b
    return a.count { it in b }.toDouble() / a.size >= 0.9
}

internal fun coverAuthorMatches(wanted: String, candidate: String): Boolean {
    val a = coverWords(wanted).filter { it.length > 1 && it !in setOf("de", "del", "la", "van", "von") }
    val b = coverWords(candidate).toSet()
    if (a.isEmpty() || b.isEmpty()) return false
    // Accept reordered surnames and abbreviated given names, never a conflicting surname.
    return a.last() in b && (a.size == 1 || a.count { it in b } >= minOf(2, a.size) ||
        a.first().take(1) in b)
}

internal fun isPublicBookPage(url: String): Boolean = try {
    val uri = URI(url)
    val host = uri.host.orEmpty().lowercase().removePrefix("www.")
    val path = uri.path.orEmpty()
    uri.scheme == "https" && when (host) {
        "casadellibro.com" -> path.startsWith("/libro-") || path.startsWith("/ebook-")
        "planetadelibros.com" -> path.startsWith("/libro-")
        "goodreads.com" -> path.startsWith("/book/show/")
        "kobo.com" -> path.contains("/ebook/") || path.contains("/book/")
        "fnac.es" -> path.matches(Regex("/a[0-9]+/.*"))
        "penguinlibros.com", "alianzaeditorial.es", "anagrama-ed.es", "tusquetseditores.com" ->
            !path.contains("/autor") && path.length > 12
        else -> false
    }
} catch (_: Exception) { false }

internal fun coverSearchLinks(html: String, baseUrl: String): List<String> {
    val doc = Jsoup.parse(html, baseUrl)
    val urls = mutableListOf<String>()
    doc.select("a[href]").forEach { link ->
        var href = link.absUrl("href")
        val query = try { URI(href).rawQuery.orEmpty() } catch (_: Exception) { "" }
        val redirect = query.split('&').firstOrNull {
            it.startsWith("q=") || it.startsWith("url=") || it.startsWith("uddg=")
        }?.substringAfter('=')
        if (redirect != null) href = try { URLDecoder.decode(redirect, "UTF-8") } catch (_: Exception) { href }
        if (isPublicBookPage(href)) urls += href.substringBefore('#')
    }
    // Bing RSS contains real destination links without a browser or JavaScript.
    Jsoup.parse(html, baseUrl, org.jsoup.parser.Parser.xmlParser()).select("item > link").forEach {
        val url = it.text().trim()
        if (isPublicBookPage(url)) urls += url
    }
    return urls.distinct()
}

internal fun publicCoverImages(html: String, baseUrl: String, wantedTitle: String,
    wantedAuthor: String, wantedIsbn: String): List<String> {
    val doc = Jsoup.parse(html, baseUrl)
    val result = mutableListOf<String>()
    fun text(value: JsonElement?): String = when {
        value == null || value.isJsonNull -> ""
        value.isJsonPrimitive -> value.asString
        value.isJsonObject -> text(value.asJsonObject.get("name"))
        value.isJsonArray -> value.asJsonArray.joinToString(" ") { text(it) }
        else -> ""
    }
    fun images(value: JsonElement?): List<String> = when {
        value == null || value.isJsonNull -> emptyList()
        value.isJsonPrimitive -> listOf(value.asString)
        value.isJsonArray -> value.asJsonArray.flatMap { images(it) }
        value.isJsonObject -> images(value.asJsonObject.get("url") ?: value.asJsonObject.get("contentUrl"))
        else -> emptyList()
    }
    fun inspect(value: JsonElement) {
        if (value.isJsonArray) { value.asJsonArray.forEach(::inspect); return }
        if (!value.isJsonObject) return
        val node = value.asJsonObject
        val type = text(node.get("@type"))
        if (type.contains("Book") || type.contains("Product")) {
            val title = text(node.get("name"))
            val author = text(node.get("author"))
            val isbn = text(node.get("isbn")).replace("-", "")
            val exactIsbn = wantedIsbn.length in listOf(10, 13) && isbn == wantedIsbn
            if (exactIsbn || (coverTitleMatches(wantedTitle, title) &&
                (unknownAuthor(wantedAuthor) || coverAuthorMatches(wantedAuthor, author))))
                result += images(node.get("image"))
        }
        node.get("@graph")?.let(::inspect)
        node.get("mainEntity")?.let(::inspect)
    }
    doc.select("script[type=application/ld+json]").forEach {
        try { inspect(JsonParser.parseString(it.data())) } catch (_: Exception) {}
    }
    val pageTitles = listOf(doc.selectFirst("h1")?.text().orEmpty(),
        doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty(), doc.title())
    val authorText = doc.select("[itemprop=author], .authorName, a[href*=/autor/], a[href*=/author/], " +
        ".ContributorLink, .contributor, .f-productDetails-Author, meta[name=author]")
        .joinToString(" ") { it.text().ifBlank { it.attr("content") } }
    val identityText = authorText + " " + doc.title() + " " +
        doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
    if (pageTitles.any { coverTitleMatches(wantedTitle, it) } &&
        (unknownAuthor(wantedAuthor) || coverAuthorMatches(wantedAuthor, identityText))) {
        doc.select("meta[property=og:image], meta[name=twitter:image], meta[property=twitter:image]")
            .forEach { result += it.attr("content") }
        doc.select("img[itemprop=image], img[data-testid=bookCover], .BookCover img, .bookCover img").forEach {
            result += it.attr("src").ifBlank { it.attr("data-src") }
        }
        doc.select("img[alt]").filter { coverTitleMatches(wantedTitle, it.attr("alt")) &&
            !it.attr("alt").contains("contraportada", true) && !it.attr("alt").contains("back cover", true) }.take(3).forEach {
            result += it.attr("data-src").ifBlank { it.attr("src") }
        }
    }
    return result.mapNotNull { url -> try {
        URI(baseUrl).resolve(url).toString().takeIf {
            it.startsWith("https://") && !it.contains("placeholder", true) && !it.contains("no-cover", true) &&
                !it.contains("/logos/", true) && !it.contains("/logo.", true)
        }
    } catch (_: Exception) { null } }.distinct()
}
