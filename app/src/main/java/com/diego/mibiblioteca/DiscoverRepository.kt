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
    val publishDates: String = "", val confidence: Int = 0,
    val source: String = "", val categories: List<String> = emptyList(),
    val publisher: String = "", val language: String = ""
)

data class PublicBookDetails(
    val summary: String, val authorBio: String, val published: String = "",
    val pages: String = "", val language: String = "", val publisher: String = "",
    val source: String = "Open Library", val authorImageUrl: String = "", val coverUrl: String = "",
    val saga: String = "", val sagaOrder: String = "", val review: String = "",
    val editions: List<PublicBookEdition> = emptyList(), val promotionalVideoUrl: String = ""
)

data class PublicBookEdition(
    val label: String, val coverUrl: String = "", val publisher: String = "",
    val published: String = "", val isbn: String = "",
    val availability: String = "Disponibilidad no confirmada"
)

data class ExternalRecommendationSeed(
    val title: String,
    val author: String,
    val genres: List<String> = emptyList()
)

private fun publicIdentity(title: String, author: String): String =
    discoverNormalizeSearchText(title) + "|" + discoverNormalizeSearchText(author)

private fun cleanPublicAuthor(value: String): String = value
    .replace(Regex("[()\\[\\]{}]+"), " ")
    .replace(Regex("\\s*[,;/]\\s*"), ", ")
    .replace(Regex("\\s+"), " ")
    .trim(' ', ',', ';')

private fun cleanEditorialText(value: String): String {
    val normalized = org.jsoup.Jsoup.parse(value).text()
        .replace(Regex("(?i)\\s*[|·•]\\s*"), ". ")
        .replace(Regex("\\s+"), " ").trim()
    val commercial = Regex(
        "(?i)(casa del libro|env[ií]os? gratis|mejores ofertas|compr[ae]|c[oó]mpralo|" +
            "cons[ií]guelo|hazte con|precio|descuento|promoci[oó]n|stock|tienda online|" +
            "entrega(?:s)?|pedido|gastos de env[iío]|a la venta)"
    )
    return normalized.split(Regex("(?<=[.!?])\\s+|\\n+"))
        .map { it.trim().trimStart('-', '·', '•', ':', ';') }
        .filter { sentence -> sentence.length >= 24 && !commercial.containsMatchIn(sentence) }
        .joinToString(" ")
        .replace(Regex("(?i)^(?:descripci[oó]n|sinopsis|resumen)\\s*:?\\s*"), "")
        .replace(Regex("\\s+"), " ").trim()
}

private fun findPromotionalVideo(title: String, author: String): String {
    val query = java.net.URLEncoder.encode("$title $author trailer libro presentación editorial", "UTF-8")
    return runCatching {
        val connection = (java.net.URL("https://www.youtube.com/results?search_query=$query")
            .openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6500
            readTimeout = 8000
            setRequestProperty("User-Agent", AppBrand.WEB_USER_AGENT)
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
        }
        val html = try { connection.inputStream.bufferedReader().use { it.readText() } }
            finally { connection.disconnect() }
        val ids = Regex("\\\"videoId\\\":\\\"([A-Za-z0-9_-]{11})\\\"")
            .findAll(html).map { it.groupValues[1] }.distinct().toList()
        ids.firstOrNull()?.let { "https://www.youtube-nocookie.com/embed/$it?playsinline=1&rel=0&modestbranding=1" }.orEmpty()
    }.getOrDefault("")
}

private val discoveryArticles = setOf("a", "al", "de", "del", "el", "la", "las", "los", "the", "un", "una", "y")

private fun discoveryWords(value: String): Set<String> = discoverNormalizeSearchText(value)
    .split(' ').filter { it.length >= 2 && it !in discoveryArticles }.toSet()

internal fun isLikelyOwnedRecommendation(
    title: String,
    author: String,
    ownedBooks: List<Pair<String, String>>
): Boolean {
    val candidateTitle = discoverNormalizeSearchText(title)
    val candidateAuthor = discoverNormalizeSearchText(author)
    val candidateWords = discoveryWords(title)
    return ownedBooks.any { (ownedTitleRaw, ownedAuthorRaw) ->
        val ownedTitle = discoverNormalizeSearchText(ownedTitleRaw)
        val ownedAuthor = discoverNormalizeSearchText(ownedAuthorRaw)
        val ownedWords = discoveryWords(ownedTitleRaw)
        val sameAuthor = candidateAuthor.isNotBlank() && ownedAuthor.isNotBlank() &&
            (candidateAuthor == ownedAuthor || candidateAuthor.contains(ownedAuthor) || ownedAuthor.contains(candidateAuthor))
        val intersection = candidateWords.intersect(ownedWords).size
        val smallest = minOf(candidateWords.size, ownedWords.size).coerceAtLeast(1)
        val sameWork = candidateTitle == ownedTitle ||
            candidateTitle.startsWith("$ownedTitle ") || ownedTitle.startsWith("$candidateTitle ") ||
            (intersection >= 2 && intersection.toDouble() / smallest >= .72)
        sameWork && (sameAuthor || candidateWords.size >= 3)
    }
}

/**
 * Recomendaciones públicas y gratuitas. Combina Google Books y Open Library,
 * y descarta los libros que ya forman parte de la biblioteca local.
 */
fun externalBookRecommendations(
    seeds: List<ExternalRecommendationSeed>,
    ownedBooks: List<Pair<String, String>>,
    limit: Int = 12
): List<PublicBookResult> {
    if (limit <= 0 || seeds.isEmpty()) return emptyList()
    val ownedAuthors = ownedBooks.map { discoverNormalizeSearchText(it.second) }
        .filter(String::isNotBlank).toSet()
    val queries = buildList {
        seeds.take(6).forEach { seed ->
            seed.genres.map(String::trim).filter { it.length >= 3 }.take(3).forEach { add("subject:$it") }
        }
        // Garantiza variedad incluso si los EPUB aún no tienen género bien clasificado.
        addAll(listOf("subject:fantasía", "subject:terror", "subject:aventuras",
            "subject:romántica", "subject:drama", "subject:autoayuda"))
    }.distinct().take(10)
    val results = mutableListOf<PublicBookResult>()
    queries.forEach { query ->
        runCatching {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val items = getJson(
                "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=30&printType=books&langRestrict=es&orderBy=newest"
            ).optJSONArray("items")
            for (index in 0 until (items?.length() ?: 0)) {
                val row = items?.optJSONObject(index) ?: continue
                val info = row.optJSONObject("volumeInfo") ?: continue
                val title = info.optString("title").trim()
                val authors = info.optJSONArray("authors")
                val author = (0 until (authors?.length() ?: 0))
                    .joinToString(", ") { authors!!.optString(it) }.trim()
                if (title.isBlank() || author.isBlank()) continue
                if (info.optString("language") != "es") continue
                if (isLikelyOwnedRecommendation(title, author, ownedBooks)) continue
                // No propone otras ediciones/traducciones de los autores usados como semilla.
                if (discoverNormalizeSearchText(author) in ownedAuthors &&
                    seeds.any { discoverNormalizeSearchText(it.author) == discoverNormalizeSearchText(author) }) continue
                val identifiers = info.optJSONArray("industryIdentifiers")
                val isbn = (0 until (identifiers?.length() ?: 0)).mapNotNull { item ->
                    identifiers?.optJSONObject(item)?.optString("identifier")
                        ?.filter(Char::isDigit)?.takeIf { it.length in setOf(10, 13) }
                }.firstOrNull { it.length == 13 }.orEmpty()
                val image = info.optJSONObject("imageLinks")?.let { links ->
                    listOf("extraLarge", "large", "medium", "thumbnail", "smallThumbnail")
                        .firstNotNullOfOrNull { key -> links.optString(key).takeIf(String::isNotBlank) }
                }.orEmpty().replace("http://", "https://").ifBlank {
                    isbn.takeIf(String::isNotBlank)?.let {
                        "https://covers.openlibrary.org/b/isbn/$it-L.jpg?default=false"
                    }.orEmpty()
                }
                results += PublicBookResult(
                    title = title,
                    author = author,
                    year = info.optString("publishedDate").take(4),
                    isbn = isbn,
                    coverUrl = image,
                    sourceUrl = info.optString("canonicalVolumeLink").ifBlank {
                        "https://books.google.com/books?id=" + row.optString("id")
                    },
                    summary = info.optString("description"),
                    publishDates = info.optString("publishedDate"),
                    confidence = 70,
                    source = "Google Books ES",
                    categories = info.optJSONArray("categories")?.let { array ->
                        (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
                    }.orEmpty(),
                    publisher = info.optString("publisher"),
                    language = "es"
                )
            }
        }
    }
    return results.distinctBy { publicIdentity(it.title, it.author) }
        .filter { it.coverUrl.isNotBlank() && it.language == "es" &&
            !isLikelyOwnedRecommendation(it.title, it.author, ownedBooks) }
        .sortedWith(compareByDescending<PublicBookResult> { it.publishDates }
            .thenByDescending { it.summary.length })
        .take(limit)
}

fun loadPublicBookDetails(result: PublicBookResult): PublicBookDetails {
    var summary = cleanEditorialText(result.summary)
    var bio = ""
    var published = result.year
    var pages = ""
    var language = ""
    var publisher = ""
    var source = "Open Library"
    var authorImageUrl = ""
    var coverUrl = result.coverUrl
    var saga = ""
    var sagaOrder = ""
    var review = ""
    val editions = mutableListOf<PublicBookEdition>()
    fetchLaunchBookDetails(result)?.let { catalog ->
        summary = cleanEditorialText(catalog.summary).ifBlank { summary }
        published = catalog.published.ifBlank { published }
        pages = catalog.pages
        language = catalog.language
        publisher = catalog.publisher
        source = catalog.source
        coverUrl = catalog.coverUrl.ifBlank { coverUrl }
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
            val expectedAuthorTerms = discoverNormalizeSearchText(result.author).split(' ')
                .filter { it.length >= 3 }.toSet()
            val normalizedPage = discoverNormalizeSearchText(page)
            if (page.isNotBlank() && expectedAuthorTerms.any(normalizedPage::contains)) {
                val title = java.net.URLEncoder.encode(page, "UTF-8")
                val extract = getJson("https://es.wikipedia.org/w/api.php?action=query&prop=extracts|pageimages&piprop=thumbnail&pithumbsize=500&explaintext=1&exintro=1&titles=$title&format=json&origin=*").optJSONObject("query")?.optJSONObject("pages")
                val key = extract?.keys()?.asSequence()?.firstOrNull()
                val authorPage = key?.let { extract.optJSONObject(it) }
                val wikipediaBio = authorPage?.optString("extract").orEmpty()
                // La Wikipedia en español prevalece sobre biografías inglesas de Open Library.
                if (wikipediaBio.isNotBlank()) bio = wikipediaBio
                authorImageUrl = authorPage?.optJSONObject("thumbnail")?.optString("source").orEmpty()
                if (bio.isNotBlank()) source = "$source · Wikipedia"
            }
        }
        val q = java.net.URLEncoder.encode("intitle:${result.title} inauthor:${result.author}", "UTF-8")
        val volumes = getJson("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=20&country=ES").optJSONArray("items")
        val wantedTitle = discoverNormalizeSearchText(result.title)
        val wantedAuthor = discoverNormalizeSearchText(result.author)
        val volumeRows = (0 until (volumes?.length() ?: 0)).mapNotNull { volumes?.optJSONObject(it) }
        fun volumeScore(row: org.json.JSONObject): Int {
                val candidate = row.optJSONObject("volumeInfo") ?: return 0
                val candidateTitle = discoverNormalizeSearchText(candidate.optString("title"))
                val candidateAuthors = candidate.optJSONArray("authors")?.let { authors ->
                    (0 until authors.length()).joinToString(" ") { authors.optString(it) }
                }.orEmpty()
                val candidateAuthor = discoverNormalizeSearchText(candidateAuthors)
                return (if (candidateTitle == wantedTitle) 100 else 0) +
                    wantedTitle.split(" ").count { it.length > 1 && candidateTitle.contains(it) } * 10 +
                    wantedAuthor.split(" ").count { it.length > 2 && candidateAuthor.contains(it) } * 5
        }
        val selectedVolume = volumeRows.maxByOrNull(::volumeScore)
        val info = selectedVolume?.optJSONObject("volumeInfo")
        if (info != null) {
            summary = cleanEditorialText(info.optString("description")).ifBlank { summary }
            published = info.optString("publishedDate").ifBlank { published }
            pages = info.optInt("pageCount", 0).takeIf { it > 0 }?.toString().orEmpty()
            language = info.optString("language")
            publisher = info.optString("publisher")
            coverUrl = info.optJSONObject("imageLinks")?.optString("large")?.ifBlank { info.optJSONObject("imageLinks")?.optString("thumbnail") }.orEmpty()
                .replace("http://", "https://").ifBlank { coverUrl }
            source = "Open Library · Google Books"
            val series = info.optJSONObject("seriesInfo") ?: selectedVolume?.optJSONObject("seriesInfo")
            saga = series?.optString("shortSeriesBookTitle").orEmpty()
            sagaOrder = series?.optString("bookDisplayNumber").orEmpty()
            if (saga.isBlank()) {
                Regex("(?i)(?:saga|serie)\\s+([^,;:()]+?)(?:[,;:]|\\s*[#№]\\s*)(\\d+(?:[.]\\d+)?)")
                    .find(info.optString("subtitle"))?.let {
                        saga = it.groupValues[1].trim()
                        sagaOrder = it.groupValues[2]
                    }
            }
        }
        volumeRows.sortedByDescending(::volumeScore).forEach { row ->
            if (volumeScore(row) < 42 || editions.size >= 8) return@forEach
            val edition = row.optJSONObject("volumeInfo") ?: return@forEach
            val ids = edition.optJSONArray("industryIdentifiers")
            val editionIsbn = (0 until (ids?.length() ?: 0)).mapNotNull { index ->
                ids?.optJSONObject(index)?.optString("identifier")?.filter(Char::isDigit)
                    ?.takeIf { it.length in setOf(10, 13) }
            }.firstOrNull { it.length == 13 }.orEmpty()
            val editionCover = edition.optJSONObject("imageLinks")?.let { links ->
                listOf("extraLarge", "large", "medium", "thumbnail").firstNotNullOfOrNull { key ->
                    links.optString(key).takeIf(String::isNotBlank)
                }
            }.orEmpty().replace("http://", "https://")
            val availability = when (row.optJSONObject("saleInfo")?.optString("saleability").orEmpty()) {
                "FOR_SALE", "FREE" -> "Disponible"
                "NOT_FOR_SALE" -> "No disponible en este catálogo"
                else -> "Disponibilidad no confirmada"
            }
            val label = listOf(edition.optString("publisher"), edition.optString("publishedDate"))
                .filter(String::isNotBlank).joinToString(" · ").ifBlank { "Edición registrada" }
            val item = PublicBookEdition(label, editionCover, edition.optString("publisher"),
                edition.optString("publishedDate"), editionIsbn, availability)
            if (editions.none { it.isbn.isNotBlank() && it.isbn == item.isbn } &&
                editions.none { it.label == item.label && it.coverUrl == item.coverUrl }) editions += item
        }
        if (summary.length < 80 && result.title.isNotBlank()) {
            val bookQuery = java.net.URLEncoder.encode(
                "\"" + result.title.trim() + "\" " + result.author.trim() + " libro novela",
                "UTF-8"
            )
            val hits = getJson("https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$bookQuery&format=json&origin=*")
                .optJSONObject("query")?.optJSONArray("search")
            val page = hits?.optJSONObject(0)?.optString("title").orEmpty()
            val wantedTerms = discoverNormalizeSearchText(result.title).split(' ')
                .filter { it.length >= 3 }.toSet()
            val pageTerms = discoverNormalizeSearchText(page)
            if (page.isNotBlank() && wantedTerms.isNotEmpty() &&
                wantedTerms.count(pageTerms::contains) * 2 >= wantedTerms.size) {
                val encodedPage = java.net.URLEncoder.encode(page, "UTF-8")
                val pagesJson = getJson("https://es.wikipedia.org/w/api.php?action=query&prop=extracts|pageimages&piprop=thumbnail&pithumbsize=800&explaintext=1&exintro=1&titles=$encodedPage&format=json&origin=*")
                    .optJSONObject("query")?.optJSONObject("pages")
                val pageKey = pagesJson?.keys()?.asSequence()?.firstOrNull()
                val bookPage = pageKey?.let { pagesJson.optJSONObject(it) }
                val extract = bookPage?.optString("extract").orEmpty().trim()
                if (extract.length >= 80) {
                    summary = cleanEditorialText(extract)
                    source = "$source · Wikipedia en español"
                }
                if (coverUrl.isBlank()) {
                    coverUrl = bookPage?.optJSONObject("thumbnail")?.optString("source").orEmpty()
                }
            }
        }
        if (summary.isBlank() || coverUrl.isBlank()) {
            val broad = java.net.URLEncoder.encode(result.title, "UTF-8")
            val fallback = getJson("https://openlibrary.org/search.json?title=$broad&limit=5").optJSONArray("docs")
            val row = (0 until (fallback?.length() ?: 0)).mapNotNull { fallback?.optJSONObject(it) }
                .maxByOrNull {
                    CatalogQuality.confidenceForIdentity(result.title, result.author,
                        it.optString("title"), it.optJSONArray("author_name")?.let { names ->
                            (0 until names.length()).joinToString(", ") { index -> names.optString(index) }
                        }.orEmpty())
                }
            val id = row?.optInt("cover_i", 0) ?: 0
            if (coverUrl.isBlank() && id > 0) coverUrl = "https://covers.openlibrary.org/b/id/$id-L.jpg"
            if (summary.isBlank()) summary = row?.optJSONArray("first_sentence")?.optString(0).orEmpty()
            if (summary.isNotBlank()) source = "$source · Open Library ampliado"
        }
    }
    if (review.isBlank() && summary.length >= 100) {
        val premise = cleanEditorialText(summary).take(420).trim().trimEnd('.', ',', ';')
        review = "${result.title} parte de ${premise.replaceFirstChar { it.lowercase() }}. " +
            "La obra se presenta como una lectura en la que importan tanto el desarrollo de la premisa como " +
            "el tono, el ritmo y la construcción de sus personajes. Esta reseña se ha elaborado con la " +
            "información editorial y bibliográfica disponible, evitando adelantar giros importantes. " +
            "La ficha permite comparar ediciones, fecha, editorial y pertenencia a saga antes de decidir si encaja con tus lecturas."
    }
    val promotionalVideo = findPromotionalVideo(result.title, result.author)
    return PublicBookDetails(cleanEditorialText(summary), bio.trim(), published, pages, language, publisher,
        source, authorImageUrl, coverUrl, saga, sagaOrder, review, editions,
        promotionalVideo)
}

fun searchPublicBooks(titleQuery: String, authorQuery: String = ""): List<PublicBookResult> {
    val wantedTitle = titleQuery.trim()
    val wantedAuthor = authorQuery.trim()
    if (wantedTitle.isBlank() && wantedAuthor.isBlank()) return emptyList()
    val candidates = mutableListOf<PublicBookResult>()
    val wantedIsbn = wantedTitle.filter(Char::isDigit).takeIf { it.length in setOf(10, 13) }.orEmpty()

    fun confidence(title: String, author: String, isbn: String = "") =
        CatalogQuality.confidenceForIdentity(wantedTitle, wantedAuthor, title, author, wantedIsbn, isbn)

    val localeLanguage = java.util.Locale.getDefault().language.takeIf {
        it.matches(Regex("[a-z]{2}"))
    } ?: "es"
    val googleQueries = listOf(
        wantedIsbn.takeIf(String::isNotBlank)?.let { "isbn:$it" },
        listOf("intitle:$wantedTitle", wantedAuthor.takeIf(String::isNotBlank)?.let { "inauthor:$it" })
            .filterNotNull().joinToString(" "),
        listOf(wantedTitle, wantedAuthor).filter(String::isNotBlank).joinToString(" "),
        "\"$wantedTitle\" saga",
        "\"$wantedTitle\" cómic novela gráfica",
        "\"$wantedTitle\" universo"
    ).filterNotNull().distinct()
    googleQueries.forEachIndexed { queryIndex, raw ->
        runCatching {
            val query = java.net.URLEncoder.encode(raw, "UTF-8")
            val language = "&langRestrict=$localeLanguage"
            val items = getJson(
                "https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=40&printType=books$language&orderBy=relevance"
            ).optJSONArray("items")
            for (index in 0 until (items?.length() ?: 0)) {
                val row = items?.optJSONObject(index) ?: continue
                val info = row.optJSONObject("volumeInfo") ?: continue
                val title = info.optString("title").trim()
                if (title.isBlank()) continue
                val authors = info.optJSONArray("authors")
                val author = cleanPublicAuthor((0 until (authors?.length() ?: 0))
                    .joinToString(", ") { authors!!.optString(it) })
                val identifiers = info.optJSONArray("industryIdentifiers")
                val allIdentifiers = (0 until (identifiers?.length() ?: 0)).mapNotNull { item ->
                    identifiers?.optJSONObject(item)?.optString("identifier")
                        ?.filter(Char::isDigit)?.takeIf { it.length in setOf(10, 13) }
                }
                val isbn = allIdentifiers.firstOrNull { it.length == 13 }
                    ?: allIdentifiers.firstOrNull().orEmpty()
                val categories = info.optJSONArray("categories")?.let { array ->
                    (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
                }.orEmpty()
                val searchable = discoverNormalizeSearchText(listOf(title, info.optString("subtitle"),
                    info.optString("description"), categories.joinToString(" ")).joinToString(" "))
                val relatedUniverse = discoveryWords(wantedTitle).let { words ->
                    words.size >= 2 && words.all(searchable::contains)
                }
                val score = maxOf(confidence(title, author, isbn), if (relatedUniverse) 44 else 0)
                if (score < 42 || (info.optString("language").isNotBlank() &&
                        info.optString("language") != localeLanguage)) continue
                val image = info.optJSONObject("imageLinks")?.let { links ->
                    listOf("extraLarge", "large", "medium", "thumbnail", "smallThumbnail")
                        .firstNotNullOfOrNull { key ->
                            links.optString(key).takeIf(String::isNotBlank)
                        }
                }.orEmpty().replace("http://", "https://").ifBlank {
                    if (isbn.isNotBlank())
                        "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"
                    else ""
                }
                candidates += PublicBookResult(
                    title = title,
                    author = author,
                    year = info.optString("publishedDate").take(4),
                    isbn = isbn,
                    coverUrl = image,
                    sourceUrl = info.optString("canonicalVolumeLink").ifBlank {
                        "https://books.google.com/books?id=" + row.optString("id")
                    },
                    summary = info.optString("description"),
                    confidence = score,
                    source = "Google Books ${localeLanguage.uppercase()}",
                    categories = categories,
                    publisher = info.optString("publisher"),
                    language = info.optString("language")
                )
            }
        }
    }

    runCatching {
        val combined = listOf(wantedTitle, wantedAuthor, if (localeLanguage == "es") "language:spa" else "")
            .filter(String::isNotBlank).joinToString(" ")
        val encoded = java.net.URLEncoder.encode(combined, "UTF-8")
        val docs = getJson(
            "https://openlibrary.org/search.json?q=$encoded&mode=everything&limit=100&fields=key,title,subtitle,author_name,author_key,isbn,cover_i,first_publish_year,first_sentence,subject,publish_date,language,publisher"
        ).optJSONArray("docs")
        for (index in 0 until docs.length()) {
            val row = docs.optJSONObject(index) ?: continue
            val title = row.optString("title").trim()
            if (title.isBlank()) continue
            val authors = row.optJSONArray("author_name")
            val author = cleanPublicAuthor((0 until (authors?.length() ?: 0))
                .joinToString(", ") { authors!!.optString(it) })
            val isbns = row.optJSONArray("isbn")
            val isbnValues = (0 until (isbns?.length() ?: 0)).map {
                isbns!!.optString(it).filter(Char::isDigit)
            }
            val isbn = isbnValues.firstOrNull { it.length == 13 }
                ?: isbnValues.firstOrNull { it.length == 10 }.orEmpty()
            val subjects = row.optJSONArray("subject")?.let { values ->
                (0 until minOf(values.length(), 8)).map { values.optString(it) }
                    .filter(String::isNotBlank)
            }.orEmpty()
            val searchable = discoverNormalizeSearchText(listOf(title, row.optString("subtitle"),
                subjects.joinToString(" "), row.optJSONArray("first_sentence")?.optString(0).orEmpty())
                .joinToString(" "))
            val relatedUniverse = discoveryWords(wantedTitle).let { words ->
                words.size >= 2 && words.all(searchable::contains)
            }
            val score = maxOf(confidence(title, author, isbn), if (relatedUniverse) 44 else 0)
            if (score < 42) continue
            val coverId = row.optInt("cover_i", 0)
            val summary = row.optJSONArray("first_sentence")?.optString(0).orEmpty()
            val key = row.optString("key").removePrefix("/works/")
            val authorKey = row.optJSONArray("author_key")?.optString(0).orEmpty()
            val dates = row.optJSONArray("publish_date")?.let { values ->
                (0 until minOf(values.length(), 5)).joinToString(" · ") { values.optString(it) }
            }.orEmpty()
            candidates += PublicBookResult(
                title = title,
                author = author,
                year = row.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString().orEmpty(),
                isbn = isbn,
                coverUrl = when {
                    coverId > 0 -> "https://covers.openlibrary.org/b/id/$coverId-L.jpg?default=false"
                    isbn.isNotBlank() -> "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"
                    else -> ""
                },
                sourceUrl = if (key.isNotBlank()) "https://openlibrary.org/works/$key"
                    else "https://openlibrary.org/search?q=$encoded",
                summary = summary,
                authorKey = authorKey,
                publishDates = dates,
                confidence = score,
                source = "Open Library",
                categories = subjects,
                publisher = row.optJSONArray("publisher")?.optString(0).orEmpty(),
                language = if (localeLanguage == "es") "es" else localeLanguage
            )
        }
    }

    return candidates.groupBy { result ->
        result.isbn.filter(Char::isDigit).takeIf { it.length >= 10 }
            ?: (discoverNormalizeSearchText(result.title) + "|" +
                discoverNormalizeSearchText(result.author))
    }.values.mapNotNull { editions ->
        editions.maxWithOrNull(
            compareBy<PublicBookResult> { it.confidence }
                .thenBy { if (it.coverUrl.isNotBlank()) 1 else 0 }
                .thenBy { it.summary.length }
        )
    }.sortedWith(
        compareByDescending<PublicBookResult> { it.confidence }
            .thenByDescending { if (it.coverUrl.isNotBlank()) 1 else 0 }
            .thenBy { discoverNormalizeSearchText(it.title) }
    ).take(60)
}
