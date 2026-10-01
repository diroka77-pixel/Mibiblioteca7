package com.diego.mibiblioteca

import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

internal val sourceTag = Regex("""(?i)(?:https?://)?(?:www\.)?(?:e[\s.-]*pub[\s.-]*libre|lecturalia|anna'?s[\s.-]*archive|annas[\s.-]*archive)(?:\.[a-z]{2,})?""")
internal val metadataTag = Regex("""(?i)(?:epub|pdf|mobi|azw3|descarga|ebook|libro digital|sin drm|ocr|scan|r\d+(?:\.\d+)*|v\d+(?:\.\d+)*)""")

internal fun cleanCatalogText(value: String): String {
    val normalized = value.replace(Regex("""(?i)\.(?:epub|pdf|mobi|azw3|azw|txt|docx|rtf|html|htm|md)$"""), "").replace('_', ' ')
        .replace(Regex("""\[[^]]*]""")) { match ->
            if (sourceTag.containsMatchIn(match.value) || metadataTag.containsMatchIn(match.value)) " " else match.value
        }
        .replace(Regex("""\([^)]*\)""")) { match ->
            if (sourceTag.containsMatchIn(match.value) || metadataTag.containsMatchIn(match.value)) " " else match.value
        }
    return normalized.split(Regex("""\s+[-–—|·]\s+"""))
        .filterNot { metadataTag.matches(it.trim()) }
        .joinToString(" - ")
        .replace(sourceTag, " ")
        .replace(Regex("""(?i)\s+(?:r|v)\d+(?:\.\d+)*\s*$"""), " ")
        .replace(Regex("""\s+"""), " ").trim(' ', '-', '|', '·', '–', '—')
}

internal fun unknownAuthor(value: String): Boolean = value.trim().lowercase() in setOf(
    "", "autor desconocido", "desconocido", "unknown", "unknown author", "sin autor",
    "biblioteca de diroka77", "anonymous", "none", "null")

internal data class EpubMeta(var title: String = "", var author: String = "Autor desconocido",
    var date: String = "", var publisher: String = "", var genre: String = "",
    var description: String = "Sin descripción disponible.", var isbn: String = "",
    var saga: String = "", var coverHref: String? = null, var language: String = "",
    var sagaOrder: String = "", var coverCandidates: List<String> = emptyList(),
    var imageCandidates: List<String> = emptyList())

internal fun parseOpf(bytes: ByteArray): EpubMeta {
    val doc = Jsoup.parse(ByteArrayInputStream(bytes), null, "", org.jsoup.parser.Parser.xmlParser())
    fun org.jsoup.nodes.Element.localName() = tagName().substringAfter(':').lowercase()
    fun org.jsoup.nodes.Element.xmlAttr(name: String) = attributes().firstOrNull {
        it.key.substringAfter(':') == name
    }?.value.orEmpty()
    val metadata = doc.getAllElements().firstOrNull { it.localName() == "metadata" }
        ?: return EpubMeta()
    val fields = metadata.children().toList()
    val refinements = fields.filter { it.localName() == "meta" }
    fun refined(id: String, property: String): String = refinements.firstOrNull {
        it.attr("refines") == "#$id" && it.attr("property") == property
    }?.text().orEmpty()
    fun field(name: String) = fields.firstOrNull { it.localName() == name }?.text().orEmpty().trim()
    val titles = fields.filter { it.localName() == "title" }
    val title = titles.firstOrNull { refined(it.id(), "title-type") == "main" } ?: titles.firstOrNull()
    val creators = fields.filter { it.localName() == "creator" }
    val authors = creators.filter {
        val role = it.xmlAttr("role").ifBlank { refined(it.id(), "role") }
        role.isBlank() || role in listOf("aut", "author")
    }.map { creator -> cleanCatalogText(creator.text().ifBlank { creator.xmlAttr("file-as") }) }
        .filter { it.isNotBlank() && !sourceTag.containsMatchIn(it) && !unknownAuthor(it) }.distinct()
    val manifest = doc.getAllElements().filter { it.localName() == "item" }
    val coverId = refinements.firstOrNull { it.attr("name") == "cover" }?.attr("content")
    val coverItem = manifest.firstOrNull { "cover-image" in it.attr("properties").split(' ') }
        ?: manifest.firstOrNull { coverId != null && it.id() == coverId }
    val guides = doc.getAllElements().filter { it.localName() == "reference" && it.attr("type") == "cover" }
    val fallbackImages = manifest.filter {
        it.attr("media-type").startsWith("image/") &&
            Regex("(?i)cover|portada|front|couverture|couv|jacket|tapa").containsMatchIn(
                it.id() + " " + it.attr("href"))
    }
    val openingPages = doc.getAllElements().filter { it.localName() == "itemref" }
        .take(3).mapNotNull { ref ->
            manifest.firstOrNull { it.id() == ref.attr("idref") }
                ?.takeIf { it.attr("media-type").contains("html") }?.attr("href")
        }
    val otherImages = manifest.filter { it.attr("media-type").startsWith("image/") }
        .map { it.attr("href") }.filter(String::isNotBlank)
    val identifiers = fields.filter { it.localName() == "identifier" }
    val isbn = identifiers.map { it.text().replace(Regex("(?i)^urn:isbn:"), "").replace("-", "").trim() }
        .firstOrNull { it.matches(Regex("(?:[0-9]{13}|[0-9]{9}[0-9Xx])")) }.orEmpty()
    fun series(name: String, property: String) = refinements.firstOrNull {
        it.attr("name") == name || it.attr("property") == property
    }?.let { it.attr("content").ifBlank { it.text() } }.orEmpty()
    return EpubMeta(title = cleanCatalogText(title?.text().orEmpty()),
        author = authors.joinToString(" · ").ifBlank { "Autor desconocido" },
        date = field("date"), publisher = field("publisher"), genre = field("subject"),
        description = Jsoup.parse(field("description")).text().ifBlank { "Sin descripción disponible." },
        isbn = isbn, saga = series("calibre:series", "belongs-to-collection"),
        coverHref = coverItem?.attr("href")?.takeIf(String::isNotBlank), language = field("language"),
        sagaOrder = series("calibre:series_index", "group-position"),
        coverCandidates = (guides + fallbackImages).map { it.attr("href") }
            .plus(openingPages).filter(String::isNotBlank).distinct(),
        imageCandidates = otherImages.distinct())
}
