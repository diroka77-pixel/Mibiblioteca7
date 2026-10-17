package com.diego.mibiblioteca

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Motor común de identidad y relevancia. Mantiene juntas las reglas usadas por
 * la biblioteca, Descubrir, las sagas y el reparador de portadas.
 */
internal object CatalogQuality {
    fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun tokens(value: String): Set<String> = normalize(value).split(' ')
        .filter { it.length > 1 }.toSet()

    fun score(book: Book, rawQuery: String): Int {
        val query = normalize(rawQuery)
        if (query.isBlank()) return 1
        val title = normalize(displayTitle(book))
        val author = normalize(displayAuthor(book))
        val saga = normalize(inferredSaga(book).first)
        val isbn = book.isbn.filter(Char::isDigit)
        val digits = rawQuery.filter(Char::isDigit)
        var score = when {
            title == query -> 1200
            title.startsWith(query) -> 1000
            title.contains(query) -> 850
            author == query -> 760
            author.startsWith(query) -> 680
            saga == query -> 640
            author.contains(query) -> 560
            saga.contains(query) -> 520
            digits.length >= 10 && isbn.contains(digits) -> 1100
            else -> 0
        }
        val queryTokens = tokens(query)
        if (queryTokens.isNotEmpty()) {
            val titleHits = queryTokens.count { it in tokens(title) }
            val authorHits = queryTokens.count { it in tokens(author) }
            val sagaHits = queryTokens.count { it in tokens(saga) }
            // Una palabra común como "el" no debe convertir una búsqueda
            // de dos términos en decenas de resultados irrelevantes.
            val completeTitle = queryTokens.isNotEmpty() && titleHits == queryTokens.size
            val completeAuthor = queryTokens.isNotEmpty() && authorHits == queryTokens.size
            val completeSaga = queryTokens.isNotEmpty() && sagaHits == queryTokens.size
            if (completeTitle) score += titleHits * 85 + 180
            else if (completeAuthor) score += authorHits * 45
            else if (completeSaga) score += sagaHits * 35
        }
        if (score == 0 && query.length >= 4) {
            val distance = levenshtein(query, title.take(max(query.length, min(title.length, query.length + 4))))
            if (distance <= typoLimit(query.length)) score = 260 - distance * 40
        }
        return score
    }

    fun rank(books: List<Book>, query: String): List<Book> {
        if (query.isBlank()) return books
        return books.asSequence().map { it to score(it, query) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Book, Int>> { it.second }
                .thenBy { normalize(displayTitle(it.first)) })
            .map { it.first }.toList()
    }

    fun confidenceForIdentity(expectedTitle: String, expectedAuthor: String,
                              candidateTitle: String, candidateAuthor: String,
                              expectedIsbn: String = "", candidateIsbn: String = ""): Int {
        val wantedIsbn = expectedIsbn.filter(Char::isDigit)
        val foundIsbn = candidateIsbn.filter(Char::isDigit)
        if (wantedIsbn.length >= 10 && wantedIsbn == foundIsbn) return 100
        val title = similarity(normalize(expectedTitle), normalize(candidateTitle))
        val author = if (expectedAuthor.isBlank()) 0.65
            else similarity(normalize(expectedAuthor), normalize(candidateAuthor))
        val combined = ((title * 72.0) + (author * 28.0)).toInt().coerceIn(0, 99)
        // Una portada con un título casi idéntico pero otro autor es un falso
        // positivo frecuente. Sin ISBN coincidente no debe aprobarse sola.
        return if (expectedAuthor.isNotBlank() && candidateAuthor.isNotBlank() && author < 0.45)
            combined.coerceAtMost(64) else combined
    }

    fun acceptableCover(confidence: Int, width: Int, height: Int): Boolean =
        confidence >= 70 && width >= 180 && height >= 240 &&
            height.toDouble() / width.toDouble() in 1.15..2.25

    private fun typoLimit(length: Int): Int = when {
        length < 6 -> 1
        length < 11 -> 2
        else -> 3
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        val distance = levenshtein(left, right)
        return (1.0 - distance.toDouble() / max(left.length, right.length)).coerceIn(0.0, 1.0)
    }

    internal fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = min(
                    min(current[j] + 1, previous[j + 1] + 1),
                    previous[j] + if (left[i] == right[j]) 0 else 1
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}

internal data class MetadataEvidence(
    val title: String = "",
    val author: String = "",
    val isbn: String = "",
    val summary: String = "",
    val coverUrl: String = "",
    val source: String = "",
    val confidence: Int = 0
)

internal fun bestMetadataEvidence(
    wantedTitle: String,
    wantedAuthor: String,
    wantedIsbn: String,
    candidates: List<MetadataEvidence>
): MetadataEvidence? = candidates
    .mapNotNull { candidate ->
        val identity = CatalogQuality.confidenceForIdentity(
            wantedTitle, wantedAuthor, candidate.title, candidate.author,
            wantedIsbn, candidate.isbn
        )
        if (identity < 55) null else candidate.copy(
            confidence = ((identity * 4 + candidate.confidence.coerceIn(0, 100)) / 5)
        )
    }
    .maxWithOrNull(compareBy<MetadataEvidence> { it.confidence }
        .thenBy { if (it.summary.length >= 80) 1 else 0 }
        .thenBy { if (it.coverUrl.isNotBlank()) 1 else 0 })
