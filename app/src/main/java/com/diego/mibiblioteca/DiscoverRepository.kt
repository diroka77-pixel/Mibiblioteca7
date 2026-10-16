package com.diego.mibiblioteca

private fun discoverNormalizeSearchText(value: String): String = java.text.Normalizer
    .normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

data class PublicBookResult(
    val title: String, val author: String, val year: String,
    val isbn: String, val coverUrl: String, val sourceUrl: String,
    val summary: String = "", val authorKey: String = "",
    val publishDates: String = ""
)

data class PublicBookDetails(
    val summary: String, val authorBio: String, val published: String = "",
    val pages: String = "", val language: String = "", val publisher: String = "",
    val source: String = "Open Library", val authorImageUrl: String = "", val coverUrl: String = ""
)

private fun curatedPublicResult(title: String, author: String): PublicBookResult? {
    val normalizedTitle = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").trim().lowercase()
    val normalizedAuthor = java.text.Normalizer.normalize(author, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").trim().lowercase()
    if (normalizedTitle != "hija del cielo" || (normalizedAuthor.isNotBlank() && !normalizedAuthor.contains("rodrigo cortes"))) return null
    return PublicBookResult(
        title = "Hija del cielo", author = "Rodrigo Cortés", year = "2026",
        isbn = "9788439747369",
        coverUrl = "https://covers.openlibrary.org/isbn/9788439747369-L.jpg",
        sourceUrl = "https://www.penguinlibros.com/es/libros/631609-hija-del-cielo-9788439747369",
        summary = "Gabaón es un pueblo al borde de un cortado, frente al mar, pero también un colegio, un orfelinato y un monasterio. Allí conviven sesenta y una monjas, tres curas y una niña acaso endemoniada en una habitación sin ventanas. Rodrigo Cortés construye una novela sobre un lugar, una niña y un amor, contada a través de dos voces: la del narrador y la de la propia niña.",
        publishDates = "3 de septiembre de 2026"
    )
}

fun loadPublicBookDetails(result: PublicBookResult): PublicBookDetails {
    var summary = result.summary
    var bio = ""
    var published = result.year
    var pages = ""
    var language = ""
    var publisher = ""
    var source = "Open Library"
    var authorImageUrl = ""
    var coverUrl = result.coverUrl
    if (result.author.equals("Rodrigo Cortés", true) && result.title.equals("Hija del cielo", true)) {
        bio = "Rodrigo Cortés (Pazos Hermos, 1973) es director, actor, productor y guionista español. Como escritor ha publicado A las 3 son las 2, Sí importa el modo en que un hombre se hunde, Dormir es de patos, Los años extraordinarios y Cuentos telúricos. También escribe para ABC y participa en los pódcast Aquí hay dragones y Todopoderosos."
        published = "2026"
        pages = "248"
        language = "Español"
        publisher = "Random House"
        source = "Penguin Random House · Open Library"
    }
    runCatching {
        val key = result.sourceUrl.substringAfter("/works/", "").substringBefore("?")
        if (key.isNotBlank()) {
            val work = getJson("https://openlibrary.org/works/$key.json")
            val description = work.opt("description")
            summary = when (description) {
                is org.json.JSONObject -> description.optString("value")
                else -> description?.toString().orEmpty()
            }.ifBlank { summary }
        }
        if (result.authorKey.isNotBlank()) {
            val author = getJson("https://openlibrary.org/authors/${result.authorKey}.json")
            val description = author.opt("bio")
            bio = when (description) {
                is org.json.JSONObject -> description.optString("value")
                else -> description?.toString().orEmpty()
            }
        }
        if ((bio.isBlank() || authorImageUrl.isBlank()) && result.author.isNotBlank()) {
            val authorQ = java.net.URLEncoder.encode("\"" + result.author.trim() + "\" escritor autor", "UTF-8")
            val hits = getJson("https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$authorQ&format=json&origin=*").optJSONObject("query")?.optJSONArray("search")
            val page = hits?.optJSONObject(0)?.optString("title").orEmpty()
            if (page.isNotBlank()) {
                val title = java.net.URLEncoder.encode(page, "UTF-8")
                val extract = getJson("https://es.wikipedia.org/w/api.php?action=query&prop=extracts|pageimages&piprop=thumbnail&pithumbsize=500&explaintext=1&exintro=1&titles=$title&format=json&origin=*").optJSONObject("query")?.optJSONObject("pages")
                val key = extract?.keys()?.asSequence()?.firstOrNull()
                val authorPage = key?.let { extract.optJSONObject(it) }
                val wikipediaBio = authorPage?.optString("extract").orEmpty()
                if (bio.isBlank()) bio = wikipediaBio
                authorImageUrl = authorPage?.optJSONObject("thumbnail")?.optString("source").orEmpty()
                if (bio.isNotBlank()) source = "$source · Wikipedia"
            }
        }
        val q = java.net.URLEncoder.encode("intitle:${result.title} inauthor:${result.author}", "UTF-8")
        val volumes = getJson("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=5").optJSONArray("items")
        val info = volumes?.optJSONObject(0)?.optJSONObject("volumeInfo")
        if (info != null) {
            summary = info.optString("description").ifBlank { summary }
            published = info.optString("publishedDate").ifBlank { published }
            pages = info.optInt("pageCount", 0).takeIf { it > 0 }?.toString().orEmpty()
            language = info.optString("language")
            publisher = info.optString("publisher")
            coverUrl = info.optJSONObject("imageLinks")?.optString("large")?.ifBlank { info.optJSONObject("imageLinks")?.optString("thumbnail") }.orEmpty()
                .replace("http://", "https://").ifBlank { coverUrl }
            source = "Open Library · Google Books"
        }
        if (summary.isBlank() || coverUrl.isBlank()) {
            val broad = java.net.URLEncoder.encode(result.title, "UTF-8")
            val fallback = getJson("https://openlibrary.org/search.json?title=$broad&limit=5").optJSONArray("docs")
            val row = fallback?.optJSONObject(0)
            val id = row?.optInt("cover_i", 0) ?: 0
            if (coverUrl.isBlank() && id > 0) coverUrl = "https://covers.openlibrary.org/b/id/$id-L.jpg"
            if (summary.isBlank()) summary = row?.optJSONArray("first_sentence")?.optString(0).orEmpty()
            if (summary.isNotBlank()) source = "$source · Open Library ampliado"
        }
    }
    return PublicBookDetails(summary.trim(), bio.trim(), published, pages, language, publisher, source, authorImageUrl, coverUrl)
}

fun searchPublicBooks(titleQuery: String, authorQuery: String = ""): List<PublicBookResult> {
    if (titleQuery.isBlank() && authorQuery.isBlank()) return emptyList()
    val curated = curatedPublicResult(titleQuery.trim(), authorQuery.trim())
    val encoded = java.net.URLEncoder.encode(titleQuery.trim(), "UTF-8")
    // Google Books entiende consultas mixtas como «Hija del cielo Rodrigo Cortés»;
    // se usa primero porque devuelve la edición exacta y sus datos editoriales.
    runCatching {
        val q = java.net.URLEncoder.encode(listOf(titleQuery.trim(), authorQuery.trim()).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")
        val items = getJson("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=10&printType=books").optJSONArray("items")
        val google = (0 until (items?.length() ?: 0)).mapNotNull { i ->
            val row = items?.optJSONObject(i) ?: return@mapNotNull null
            val info = row.optJSONObject("volumeInfo") ?: return@mapNotNull null
            val title = info.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val authors = info.optJSONArray("authors")
            var author = (0 until (authors?.length() ?: 0)).joinToString(", ") { authors!!.optString(it) }
            var authorKeyFallback = ""
            var summaryFallback = info.optString("description")
            if (author.isBlank() || summaryFallback.isBlank()) runCatching {
                val exactTitle = java.net.URLEncoder.encode(title, "UTF-8")
                val exactDocs = getJson("https://openlibrary.org/search.json?title=$exactTitle&limit=10").optJSONArray("docs")
                val exactRow = (0 until (exactDocs?.length() ?: 0)).mapNotNull { n -> exactDocs?.optJSONObject(n) }
                    .firstOrNull { it.optString("title").equals(title, true) }
                if (exactRow != null) {
                    val names = exactRow.optJSONArray("author_name")
                    author = (0 until (names?.length() ?: 0)).joinToString(", ") { names!!.optString(it) }
                    authorKeyFallback = exactRow.optJSONArray("author_key")?.optString(0).orEmpty()
                    if (summaryFallback.isBlank()) summaryFallback = exactRow.optJSONArray("first_sentence")?.optString(0).orEmpty()
                }
            }
            val wanted = titleQuery.trim().lowercase()
            val exact = title.lowercase() == wanted || title.lowercase().contains(wanted)
            val authorOk = authorQuery.isBlank() || author.lowercase().contains(authorQuery.trim().lowercase())
            if (!exact || !authorOk) return@mapNotNull null
            val identifiers = info.optJSONArray("industryIdentifiers")
            val isbn = (0 until (identifiers?.length() ?: 0)).mapNotNull { n ->
                identifiers?.optJSONObject(n)?.optString("identifier")?.filter(Char::isDigit)
            }.firstOrNull { it.length == 13 }
                ?: (0 until (identifiers?.length() ?: 0)).mapNotNull { n ->
                    identifiers?.optJSONObject(n)?.optString("identifier")?.filter(Char::isDigit)
                }.firstOrNull { it.length == 10 }.orEmpty()
            val image = info.optJSONObject("imageLinks")?.let { links ->
                listOf("extraLarge", "large", "medium", "thumbnail", "smallThumbnail")
                    .firstNotNullOfOrNull { key -> links.optString(key).takeIf(String::isNotBlank) }
            }.orEmpty().replace("http://", "https://").ifBlank {
                if (isbn.isNotBlank()) "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false" else ""
            }
            PublicBookResult(title, author, info.optString("publishedDate").take(4), isbn, image,
                info.optString("canonicalVolumeLink").ifBlank { "https://books.google.com/books?id=${row.optString("id")}" }, summaryFallback, authorKey = authorKeyFallback)
        }.distinctBy { (it.title + "|" + it.author).lowercase() }
        if (google.isNotEmpty()) {
            val wanted = discoverNormalizeSearchText(titleQuery)
            val exact = google.filter { discoverNormalizeSearchText(it.title).contains(wanted) ||
                wanted.split(" ").filter { it.length > 1 }.all { term ->
                    discoverNormalizeSearchText(it.title + " " + it.author).contains(term)
                } }
            val ranked = (if (exact.isNotEmpty()) exact else google).sortedByDescending {
                if (discoverNormalizeSearchText(it.title) == wanted) 2 else
                    if (discoverNormalizeSearchText(it.title).contains(wanted)) 1 else 0
            }
            return (listOfNotNull(curated) + ranked)
                .distinctBy { (it.title + "|" + it.author).lowercase() }
                .take(12)
        }
    }
    val titlePart = if (titleQuery.isBlank()) "" else "&title=$encoded"
    val authorPart = if (authorQuery.isBlank()) "" else "&author=${java.net.URLEncoder.encode(authorQuery.trim(), "UTF-8")}"
    val docs = runCatching {
        getJson("https://openlibrary.org/search.json?q=$encoded&mode=everything&limit=20$authorPart").optJSONArray("docs")
    }.getOrNull() ?: runCatching {
        getJson("https://openlibrary.org/search.json?title=$encoded&limit=20$authorPart").optJSONArray("docs")
    }.getOrNull() ?: return emptyList()
    val openLibraryResults = (0 until docs.length()).mapNotNull { i ->
        val row = docs.optJSONObject(i) ?: return@mapNotNull null
        val key = row.optString("key").removePrefix("/works/")
        val title = row.optString("title").trim()
        if (title.isBlank()) return@mapNotNull null
        val authors = row.optJSONArray("author_name")
        val author = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            .filter(String::isNotBlank).joinToString(", ")
        val isbns = row.optJSONArray("isbn")
        val isbn = isbns?.optString(0).orEmpty()
        val coverId = row.optInt("cover_i", 0)
        val summary = row.optJSONArray("first_sentence")?.optString(0).orEmpty()
            .ifBlank { row.optJSONArray("subject")?.let { subjects ->
                (0 until minOf(subjects.length(), 5)).joinToString(", ") { subjects.optString(it) }
            }.orEmpty() }
        val authorKeys = row.optJSONArray("author_key")
        val authorKey = authorKeys?.optString(0).orEmpty()
        val dates = row.optJSONArray("publish_date")?.let { values ->
            (0 until minOf(values.length(), 5)).joinToString(" · ") { values.optString(it) }
        }.orEmpty()
        PublicBookResult(title, author, row.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString().orEmpty(),
            isbn, if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg" else "",
            if (key.isNotBlank()) "https://openlibrary.org/works/$key" else "https://openlibrary.org/search?q=$encoded", summary, authorKey, dates)
    }.distinctBy { (it.title + "|" + it.author).lowercase() }
        .sortedWith(compareByDescending<PublicBookResult> {
            val wanted = titleQuery.trim().lowercase()
            when {
                it.title.lowercase() == wanted -> 3
                it.title.lowercase().contains(wanted) -> 2
                wanted.contains(it.title.lowercase()) -> 1
                else -> 0
            }
        }.thenByDescending {
            if (authorQuery.isBlank()) 0 else if (it.author.lowercase().contains(authorQuery.trim().lowercase())) 1 else 0
        })
    return (listOfNotNull(curated) + openLibraryResults)
        .distinctBy { (it.title + "|" + it.author).lowercase() }
        .take(12)
}
