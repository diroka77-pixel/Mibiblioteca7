package com.diego.mibiblioteca

import java.text.Normalizer

internal data class RecommendationInput(
    val id: String,
    val title: String,
    val author: String,
    val genres: List<String>,
    val saga: String,
    val status: ReadingStatus,
    val favorite: Boolean = false,
    val wantToRead: Boolean = false,
    val rating: Int = 0
)

internal data class LocalRecommendation(
    val id: String,
    val score: Int,
    val reason: String
)

private fun recommendationKey(value: String): String = Normalizer.normalize(
    value.trim().lowercase(), Normalizer.Form.NFD
).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

internal fun localBookRecommendations(
    books: List<RecommendationInput>,
    limit: Int = 12
): List<LocalRecommendation> {
    if (limit <= 0) return emptyList()
    val authorWeights = mutableMapOf<String, Int>()
    val genreWeights = mutableMapOf<String, Int>()
    val sagaWeights = mutableMapOf<String, Int>()
    books.forEach { book ->
        var signal = when (book.status) {
            ReadingStatus.READ -> 3
            ReadingStatus.READING -> 4
            ReadingStatus.PENDING -> 0
        }
        if (book.favorite) signal += 4
        if (book.rating >= 4) signal += book.rating
        if (signal == 0) return@forEach
        fun add(target: MutableMap<String, Int>, raw: String) {
            val key = recommendationKey(raw)
            if (key.isBlank() || key == "autor desconocido" || key == "biblioteca de diroka77") return
            target[key] = target.getOrDefault(key, 0) + signal
        }
        add(authorWeights, book.author)
        book.genres.forEach { add(genreWeights, it) }
        add(sagaWeights, book.saga)
    }

    val ranked = books.asSequence().filter { it.status == ReadingStatus.PENDING }
        .map { book ->
            val authorKey = recommendationKey(book.author)
            val genreScores = book.genres.mapNotNull { genre ->
                val key = recommendationKey(genre)
                genreWeights[key]?.let { Triple(key, genre, it) }
            }
            val sagaKey = recommendationKey(book.saga)
            val authorScore = authorWeights.getOrDefault(authorKey, 0)
            val sagaScore = sagaWeights.getOrDefault(sagaKey, 0)
            val score = authorScore * 6 + genreScores.sumOf { it.third } * 3 + sagaScore * 4 +
                (if (book.favorite) 10 else 0) + (if (book.wantToRead) 8 else 0)
            val bestGenre = genreScores.maxByOrNull { it.third }
            val reason = when {
                book.wantToRead -> "Está en tu lista Quiero leer"
                authorScore > 0 -> "Porque te gusta ${book.author.trim()}"
                bestGenre != null -> "Afinidad con ${bestGenre.second.trim()}"
                sagaScore > 0 && book.saga.isNotBlank() -> "Continúa la saga ${book.saga.trim()}"
                book.favorite -> "Lo marcaste como favorito"
                else -> "Pendiente de tu biblioteca"
            }
            Triple(book, LocalRecommendation(book.id, score, reason), authorKey)
        }
        .sortedWith(compareByDescending<Triple<RecommendationInput, LocalRecommendation, String>> {
            it.second.score
        }.thenBy { it.first.title.lowercase() })
        .toList()

    val result = mutableListOf<LocalRecommendation>()
    val authorCounts = mutableMapOf<String, Int>()
    ranked.forEach { (_, recommendation, authorKey) ->
        if (result.size >= limit) return@forEach
        if (authorKey.isNotBlank() && authorCounts.getOrDefault(authorKey, 0) >= 2) return@forEach
        result += recommendation
        if (authorKey.isNotBlank()) authorCounts[authorKey] = authorCounts.getOrDefault(authorKey, 0) + 1
    }
    if (result.size < limit) ranked.forEach { (_, recommendation, _) ->
        if (result.size < limit && result.none { it.id == recommendation.id }) result += recommendation
    }
    return result
}

