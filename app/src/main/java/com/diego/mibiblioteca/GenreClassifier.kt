package com.diego.mibiblioteca

import java.net.URLEncoder

private val genreAliases = linkedMapOf(
    "Fantasía" to listOf("fantasy","fantasia","fantasía","epic fantasy","high fantasy","dark fantasy","urban fantasy","magic"),
    "Ciencia ficción" to listOf("science fiction","ciencia ficcion","ciencia ficción","sci-fi","dystopian","dystopia","space opera","cyberpunk"),
    "Terror" to listOf("horror","terror","ghost","vampire","gothic fiction"),
    "Thriller" to listOf("thriller","suspense","psychological thriller"),
    "Misterio" to listOf("mystery","misterio","detective","crime fiction","policial","policiaca","policíaca"),
    "Romance" to listOf("romance","love stories","romantic"),
    "Novela histórica" to listOf("historical fiction","novela historica","novela histórica"),
    "Aventuras" to listOf("adventure","aventura","aventuras"),
    "Juvenil" to listOf("young adult","juvenile fiction","juvenil","teen"),
    "Infantil" to listOf("children","children's","infantil","juvenile literature"),
    "Clásicos" to listOf("classics","classic literature","clasicos","clásicos"),
    "Biografía" to listOf("biography","autobiography","memoir","biografia","biografía","memorias"),
    "Historia" to listOf("history","historia"),
    "Ensayo" to listOf("essays","essay","ensayo"),
    "Divulgación" to listOf("popular science","science","divulgacion","divulgación"),
    "Humor" to listOf("humor","humour","comedy"),
    "Poesía" to listOf("poetry","poesia","poesía"),
    "Teatro" to listOf("drama","plays","theater","theatre","teatro"),
    "Cómic y novela gráfica" to listOf("comics","graphic novels","comic","manga"),
    "No ficción" to listOf("nonfiction","non-fiction","no ficcion","no ficción")
)

internal fun normalizeGenres(values: Iterable<String>): List<String> {
    val raw = values.flatMap { it.split(',', ';', '/', '|', '·') }.map { it.trim() }.filter { it.length > 2 }
    val out = linkedSetOf<String>()
    for (value in raw) {
        val plain = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        for ((genre, aliases) in genreAliases) {
            if (aliases.any { alias ->
                val a = java.text.Normalizer.normalize(alias.lowercase(), java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
                plain == a || plain.contains(a)
            }) out += genre
        }
    }
    return out.take(4)
}

internal data class GenreResult(val genres: List<String>, val source: String)

internal fun fetchGenresOnline(book: Book): GenreResult {
    val local = normalizeGenres(listOf(book.genre, book.description, book.spanishPlot))
    val found = linkedSetOf<String>()
    found += local
    val isbn = book.isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
    val author = displayAuthor(book).takeUnless { it == "Biblioteca de Diroka77" }.orEmpty()
    val title = displayTitle(book)
    var usedGoogle = false
    var usedOpenLibrary = false

    val googleQueries = listOfNotNull(
        ("isbn:$isbn").takeIf { isbn.length == 10 || isbn.length == 13 },
        "intitle:$title" + if (author.isNotBlank()) " inauthor:$author" else "",
        "intitle:$title"
    ).distinct()
    for (query in googleQueries) try {
        val url = "https://www.googleapis.com/books/v1/volumes?q=" +
            URLEncoder.encode(query, "UTF-8") + "&maxResults=8"
        val items = getJson(url).optJSONArray("items") ?: continue
        for (i in 0 until items.length()) {
            val info = items.optJSONObject(i)?.optJSONObject("volumeInfo") ?: continue
            val authors = info.optJSONArray("authors")
            val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            if (!catalogMatch(info.optString("title"), names, book)) continue
            val categories = info.optJSONArray("categories")
            val values = (0 until (categories?.length() ?: 0)).map { categories!!.optString(it) }
            val normalized = normalizeGenres(values)
            if (normalized.isNotEmpty()) {
                found += normalized
                usedGoogle = true
                break
            }
        }
        if (usedGoogle) break
    } catch (_: Exception) {}

    try {
        val q = if (isbn.length == 10 || isbn.length == 13) "isbn=$isbn"
            else "title=" + URLEncoder.encode(title, "UTF-8") +
                if (author.isNotBlank()) "&author=" + URLEncoder.encode(author, "UTF-8") else ""
        val docs = getJson("https://openlibrary.org/search.json?$q&fields=title,author_name,subject&limit=8")
            .optJSONArray("docs")
        for (i in 0 until (docs?.length() ?: 0)) {
            val doc = docs?.optJSONObject(i) ?: continue
            val authors = doc.optJSONArray("author_name")
            val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            if (!catalogMatch(doc.optString("title"), names, book)) continue
            val subjects = doc.optJSONArray("subject")
            val values = (0 until minOf(subjects?.length() ?: 0, 40)).map { subjects!!.optString(it) }
            val normalized = normalizeGenres(values)
            if (normalized.isNotEmpty()) {
                found += normalized
                usedOpenLibrary = true
                break
            }
        }
    } catch (_: Exception) {}

    val source = when {
        usedGoogle && usedOpenLibrary -> "Google Libros + Open Library"
        usedGoogle -> "Google Libros"
        usedOpenLibrary -> "Open Library"
        local.isNotEmpty() -> "Metadatos del libro"
        else -> ""
    }
    return GenreResult(found.take(4), source)
}
