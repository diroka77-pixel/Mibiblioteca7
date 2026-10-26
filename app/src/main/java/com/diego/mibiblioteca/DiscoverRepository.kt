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
 * Recomendaciones públicas y gratuitas de Open Library, por los géneros de los libros que
 * más te gustan, en el idioma de la app y siempre con portada. Descarta los libros que ya
 * forman parte de la biblioteca local.
 */
fun externalBookRecommendations(
    seeds: List<ExternalRecommendationSeed>,
    ownedBooks: List<Pair<String, String>>,
    limit: Int = 12
): List<PublicBookResult> {
    if (limit <= 0 || seeds.isEmpty()) return emptyList()
    val ownedAuthors = ownedBooks.map { discoverNormalizeSearchText(it.second) }
        .filter(String::isNotBlank).toSet()
    val subjects = buildList {
        seeds.take(6).forEach { seed -> seed.genres.mapNotNull(::openLibrarySubject).take(3).forEach(::add) }
        // Variedad aunque los EPUB aún no tengan el género bien clasificado.
        addAll(listOf("fantasy", "thriller", "historical_fiction", "romance", "science_fiction", "mystery"))
    }.distinct().take(8)
    val language = I18n.currentLanguage()
    val olLanguage = openLibraryLanguageCode(language)
    val currentYear = java.time.LocalDate.now().year
    val sinceYear = currentYear - 25
    val recentIds = mutableSetOf<String>()
    val results = mutableListOf<PublicBookResult>()
    // Dos pasadas por género: libros del año actual y del anterior, y después los de los
    // últimos 25 años (los que mejor valoración tienen), para no ofrecer solo lo antiguo.
    subjects.forEach { subject ->
        listOf(currentYear - 1 to 15, sinceYear to 25).forEach { (fromYear, rows) ->
            runCatching {
                val q = java.net.URLEncoder.encode(
                    "subject:\"${subject.replace('_', ' ')}\" language:$olLanguage first_publish_year:[$fromYear TO *]", "UTF-8")
                val docs = getJson("https://openlibrary.org/search.json?q=$q&lang=$language&sort=rating&limit=$rows" +
                    "&fields=key,title,author_name,author_key,cover_i,first_publish_year,subject,publisher," +
                    "editions,editions.key,editions.title,editions.cover_i,editions.language")
                    .optJSONArray("docs")
                for (index in 0 until (docs?.length() ?: 0)) {
                    val doc = docs?.optJSONObject(index) ?: continue
                    val book = openLibraryDocToResult(doc, language, confidence = 70, source = "Open Library")
                        ?: continue
                    if (isLikelyOwnedRecommendation(book.title, book.author, ownedBooks)) continue
                    // No propone otras ediciones de los autores usados como semilla.
                    if (discoverNormalizeSearchText(book.author) in ownedAuthors &&
                        seeds.any { discoverNormalizeSearchText(it.author) == discoverNormalizeSearchText(book.author) }) continue
                    if (fromYear == currentYear - 1) recentIds += publicIdentity(book.title, book.author)
                    results += book
                }
            }
        }
    }
    // Se mezclan: dos libros recientes por cada uno anterior, barajados por día.
    val unique = results.distinctBy { publicIdentity(it.title, it.author) }.filter { it.coverUrl.isNotBlank() }
    val random = java.util.Random(java.time.LocalDate.now().toEpochDay())
    val recent = unique.filter { publicIdentity(it.title, it.author) in recentIds }.shuffled(random).toMutableList()
    val older = unique.filterNot { publicIdentity(it.title, it.author) in recentIds }.shuffled(random).toMutableList()
    val mixed = mutableListOf<PublicBookResult>()
    while (recent.isNotEmpty() || older.isNotEmpty()) {
        repeat(2) { if (recent.isNotEmpty()) mixed += recent.removeAt(0) }
        if (older.isNotEmpty()) mixed += older.removeAt(0)
    }
    return mixed.take(limit)
}

/** Género de la app → «subject key» de Open Library (que está en inglés). */
internal fun openLibrarySubject(genre: String): String? {
    val g = discoverNormalizeSearchText(genre)
    return when {
        "ciencia ficcion" in g || "science fiction" in g -> "science_fiction"
        "fantas" in g -> "fantasy"
        "terror" in g || "horror" in g -> "horror"
        "negra" in g || "policiac" in g || "crimen" in g || "crime" in g -> "crime"
        "thriller" in g || "suspense" in g -> "thriller"
        "misterio" in g || "mystery" in g -> "mystery"
        "romant" in g || "romance" in g -> "romance"
        "historica" in g || "historical" in g -> "historical_fiction"
        "aventura" in g || "adventure" in g -> "adventure"
        "juvenil" in g || "young adult" in g -> "young_adult_fiction"
        "infantil" in g || "children" in g -> "juvenile_fiction"
        "biograf" in g -> "biography"
        "historia" in g || "history" in g -> "history"
        "humor" in g -> "humor"
        "poesia" in g || "poetry" in g -> "poetry"
        "teatro" in g || "drama" in g -> "drama"
        "comic" in g || "grafica" in g -> "comics"
        "autoayuda" in g || "self help" in g -> "self-help"
        "clasic" in g || "classic" in g -> "classic_literature"
        else -> null
    }
}

/**
 * Convierte un resultado de búsqueda de Open Library en un libro para mostrar. Si viene la
 * edición en el idioma de la app (parámetro lang=), se usan su título y su portada.
 */
internal fun openLibraryDocToResult(doc: org.json.JSONObject, language: String,
                                    confidence: Int, source: String): PublicBookResult? {
    val workKey = doc.optString("key")
    if (!workKey.startsWith("/works/")) return null
    val edition = doc.optJSONObject("editions")?.optJSONArray("docs")?.optJSONObject(0)
    val title = edition?.optString("title").orEmpty().ifBlank { doc.optString("title") }.trim()
    val authors = doc.optJSONArray("author_name")
    val author = (0 until minOf(authors?.length() ?: 0, 2)).joinToString(", ") { authors!!.optString(it) }.trim()
    if (title.isBlank() || author.isBlank()) return null
    val coverId = (edition?.optInt("cover_i", 0) ?: 0).takeIf { it > 0 } ?: doc.optInt("cover_i", 0)
    val year = doc.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString().orEmpty()
    val subjects = doc.optJSONArray("subject")?.let { values ->
        (0 until minOf(values.length(), 6)).map { values.optString(it) }.filter(String::isNotBlank)
    }.orEmpty()
    return PublicBookResult(
        title = title, author = author, year = year, isbn = "",
        coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else "",
        sourceUrl = "https://openlibrary.org$workKey",
        authorKey = doc.optJSONArray("author_key")?.optString(0).orEmpty(),
        publishDates = year, confidence = confidence, source = source,
        categories = subjects, publisher = doc.optJSONArray("publisher")?.optString(0).orEmpty(),
        language = language
    )
}

/** Busca la obra en Open Library cuando solo se conoce el título y el autor. */
internal fun findOpenLibraryWork(title: String, author: String): String = runCatching {
    val t = java.net.URLEncoder.encode(title, "UTF-8")
    val a = java.net.URLEncoder.encode(author, "UTF-8")
    val docs = getJson("https://openlibrary.org/search.json?title=$t&author=$a&limit=5&fields=key,title,author_name")
        .optJSONArray("docs")
    (0 until (docs?.length() ?: 0)).mapNotNull { docs?.optJSONObject(it) }.maxByOrNull { doc ->
        CatalogQuality.confidenceForIdentity(title, author, doc.optString("title"),
            doc.optJSONArray("author_name")?.optString(0).orEmpty())
    }?.optString("key").orEmpty().removePrefix("/works/")
}.getOrDefault("")

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
            val authorQ = java.net.URLEncoder.encode("\"" + result.author.trim() + "\" " + WikiLanguage.authorHint(), "UTF-8")
            val hits = getJson(WikiLanguage.wikipediaApi() + "?action=query&list=search&srsearch=$authorQ&format=json&origin=*").optJSONObject("query")?.optJSONArray("search")
            val page = hits?.optJSONObject(0)?.optString("title").orEmpty()
            val expectedAuthorTerms = discoverNormalizeSearchText(result.author).split(' ')
                .filter { it.length >= 3 }.toSet()
            val normalizedPage = discoverNormalizeSearchText(page)
            if (page.isNotBlank() && expectedAuthorTerms.any(normalizedPage::contains)) {
                val title = java.net.URLEncoder.encode(page, "UTF-8")
                val extract = getJson(WikiLanguage.wikipediaApi() + "?action=query&prop=extracts|pageimages&piprop=thumbnail&pithumbsize=500&explaintext=1&exintro=1&titles=$title&format=json&origin=*").optJSONObject("query")?.optJSONObject("pages")
                val key = extract?.keys()?.asSequence()?.firstOrNull()
                val authorPage = key?.let { extract.optJSONObject(it) }
                val wikipediaBio = authorPage?.optString("extract").orEmpty()
                // La Wikipedia en el idioma de la app prevalece sobre las biografías de Open Library.
                if (wikipediaBio.isNotBlank()) bio = wikipediaBio
                authorImageUrl = authorPage?.optJSONObject("thumbnail")?.optString("source").orEmpty()
                if (bio.isNotBlank()) source = "$source · Wikipedia"
            }
        }
        // Ediciones, páginas, editorial y portada salen de Open Library (antes, de Google
        // Books, cuyas condiciones no permiten cobrar por la app).
        val workKey = result.sourceUrl.substringAfter("/works/", "").substringBefore("?")
            .substringBefore("/").ifBlank { findOpenLibraryWork(result.title, result.author) }
        if (workKey.isNotBlank()) {
            val entries = getJson("https://openlibrary.org/works/$workKey/editions.json?limit=60")
                .optJSONArray("entries")
            val rows = (0 until (entries?.length() ?: 0)).mapNotNull { entries?.optJSONObject(it) }
            val wantedLanguage = openLibraryLanguageCode(I18n.currentLanguage())
            fun inAppLanguage(row: org.json.JSONObject): Boolean = row.optJSONArray("languages")?.let { langs ->
                (0 until langs.length()).any { langs.optJSONObject(it)?.optString("key") == "/languages/$wantedLanguage" }
            } == true
            // Primero las ediciones en el idioma de la app, con portada y más recientes.
            val ordered = rows.sortedWith(compareByDescending<org.json.JSONObject> { inAppLanguage(it) }
                .thenByDescending { (it.optJSONArray("covers")?.optInt(0, 0) ?: 0) > 0 }
                .thenByDescending { Regex("\\d{4}").find(it.optString("publish_date"))?.value.orEmpty() })
            ordered.firstOrNull()?.let { best ->
                pages = best.optInt("number_of_pages", 0).takeIf { it > 0 }?.toString().orEmpty()
                publisher = best.optJSONArray("publishers")?.optString(0).orEmpty()
                language = if (inAppLanguage(best)) I18n.currentLanguage() else ""
                if (published.isBlank()) published = best.optString("publish_date")
                val coverId = best.optJSONArray("covers")?.optInt(0, 0) ?: 0
                if (coverId > 0 && (coverUrl.isBlank() || inAppLanguage(best)))
                    coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                if (saga.isBlank()) best.optJSONArray("series")?.optString(0)?.let { raw ->
                    Regex("^(.*?)[,;#]?\\s*(?:n\\.?º|no\\.?|vol\\.?|#)?\\s*(\\d+(?:[.]\\d+)?)\\s*$", RegexOption.IGNORE_CASE)
                        .find(raw.trim())?.let { saga = it.groupValues[1].trim(); sagaOrder = it.groupValues[2] }
                        ?: run { saga = raw.trim() }
                }
            }
            ordered.forEach { row ->
                if (editions.size >= 8) return@forEach
                val isbn = row.optJSONArray("isbn_13")?.optString(0).orEmpty()
                    .ifBlank { row.optJSONArray("isbn_10")?.optString(0).orEmpty() }
                val coverId = row.optJSONArray("covers")?.optInt(0, 0) ?: 0
                val editionPublisher = row.optJSONArray("publishers")?.optString(0).orEmpty()
                val date = row.optString("publish_date")
                val label = listOf(editionPublisher, date).filter(String::isNotBlank)
                    .joinToString(" · ").ifBlank { "Edición registrada" }
                val item = PublicBookEdition(label,
                    if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg" else "",
                    editionPublisher, date, isbn)
                if (editions.none { it.isbn.isNotBlank() && it.isbn == item.isbn } &&
                    editions.none { it.label == item.label && it.coverUrl == item.coverUrl }) editions += item
            }
            if (rows.isNotEmpty() && !source.contains("Open Library")) source = "Open Library · $source"
        }
        if (summary.length < 80 && result.title.isNotBlank()) {
            val bookQuery = java.net.URLEncoder.encode(
                "\"" + result.title.trim() + "\" " + result.author.trim() + " " + WikiLanguage.bookHint(),
                "UTF-8"
            )
            val hits = getJson(WikiLanguage.wikipediaApi() + "?action=query&list=search&srsearch=$bookQuery&format=json&origin=*")
                .optJSONObject("query")?.optJSONArray("search")
            val page = hits?.optJSONObject(0)?.optString("title").orEmpty()
            val wantedTerms = discoverNormalizeSearchText(result.title).split(' ')
                .filter { it.length >= 3 }.toSet()
            val pageTerms = discoverNormalizeSearchText(page)
            if (page.isNotBlank() && wantedTerms.isNotEmpty() &&
                wantedTerms.count(pageTerms::contains) * 2 >= wantedTerms.size) {
                val encodedPage = java.net.URLEncoder.encode(page, "UTF-8")
                val pagesJson = getJson(WikiLanguage.wikipediaApi() + "?action=query&prop=extracts|pageimages&piprop=thumbnail&pithumbsize=800&explaintext=1&exintro=1&titles=$encodedPage&format=json&origin=*")
                    .optJSONObject("query")?.optJSONObject("pages")
                val pageKey = pagesJson?.keys()?.asSequence()?.firstOrNull()
                val bookPage = pageKey?.let { pagesJson.optJSONObject(it) }
                val extract = bookPage?.optString("extract").orEmpty().trim()
                if (extract.length >= 80) {
                    summary = cleanEditorialText(extract)
                    source = "$source · Wikipedia"
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
    // Ya no se redacta una «reseña» con una plantilla a partir de la sinopsis: se
    // presentaba como reseña un texto que nadie había escrito. Solo se muestran datos reales.
    return PublicBookDetails(cleanEditorialText(summary), bio.trim(), published, pages, language, publisher,
        source, authorImageUrl, coverUrl, saga, sagaOrder, review, editions)
}

fun searchPublicBooks(titleQuery: String, authorQuery: String = ""): List<PublicBookResult> {
    val wantedTitle = titleQuery.trim()
    val wantedAuthor = authorQuery.trim()
    if (wantedTitle.isBlank() && wantedAuthor.isBlank()) return emptyList()
    val candidates = mutableListOf<PublicBookResult>()
    val wantedIsbn = wantedTitle.filter(Char::isDigit).takeIf { it.length in setOf(10, 13) }.orEmpty()

    fun confidence(title: String, author: String, isbn: String = "") =
        CatalogQuality.confidenceForIdentity(wantedTitle, wantedAuthor, title, author, wantedIsbn, isbn)

    val localeLanguage = I18n.currentLanguage()
    runCatching {
        val combined = listOf(wantedTitle, wantedAuthor).filter(String::isNotBlank).joinToString(" ")
        val encoded = java.net.URLEncoder.encode(combined, "UTF-8")
        // lang= da prioridad a las ediciones en el idioma de la app sin excluir las demás.
        val docs = getJson(
            "https://openlibrary.org/search.json?q=$encoded&lang=$localeLanguage&mode=everything&limit=100&fields=key,title,subtitle,author_name,author_key,isbn,cover_i,first_publish_year,first_sentence,subject,publish_date,language,publisher"
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
