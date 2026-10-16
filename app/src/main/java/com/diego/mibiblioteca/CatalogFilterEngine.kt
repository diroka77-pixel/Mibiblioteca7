package com.diego.mibiblioteca

import android.net.Uri
import java.text.Normalizer

/**
 * Motor único de búsqueda y filtrado para Biblioteca, Secciones y Favoritos.
 *
 * Mantener este estado fuera de las pantallas evita que cada destino conserve
 * una copia obsoleta de los resultados cuando el usuario borra la consulta.
 */
internal fun normalizeSearchText(value: String): String = Normalizer
    .normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

internal fun searchableBookFields(book: Book): List<String> = listOf(
    book.title,
    displayTitle(book),
    book.customTitle,
    book.author,
    displayAuthor(book),
    book.customAuthor,
    book.saga,
    inferredSaga(book).first,
    Uri.decode(book.uri.lastPathSegment.orEmpty())
)

internal fun matchesCatalogFlags(
    bookStatus: ReadingStatus,
    isFavorite: Boolean,
    sections: List<String>,
    hasCover: Boolean,
    hasPlot: Boolean,
    hasBiography: Boolean,
    hasKnownAuthor: Boolean,
    needsReview: Boolean,
    status: ReadingStatus?,
    favoritesOnly: Boolean,
    section: String?,
    quality: String
): Boolean =
    (status == null || bookStatus == status) &&
        (!favoritesOnly || isFavorite) &&
        (section == null || section in sections) &&
        when (quality) {
            "Portada" -> !hasCover
            "Argumento" -> !hasPlot
            "Biografía" -> !hasBiography
            "Autor" -> !hasKnownAuthor
            "Revisar" -> needsReview
            "Todos" -> !hasCover || !hasPlot || !hasBiography || !hasKnownAuthor || needsReview
            else -> true
        }

internal fun filterBooks(
    books: List<Book>,
    query: String,
    status: ReadingStatus?,
    favorites: Boolean,
    section: String?,
    quality: String,
    pending: Set<Uri>,
    searchMatches: Set<String>? = null
): List<Book> {
    val queryTerms = normalizeSearchText(query)
        .split(" ")
        .filter { it.length >= 2 }

    return books.filter { book ->
        val matchesQuery = when {
            queryTerms.isEmpty() -> true
            searchMatches != null -> book.uri.toString() in searchMatches
            else -> {
                val fields = searchableBookFields(book).map(::normalizeSearchText)
                queryTerms.all { term -> fields.any { field -> field.contains(term) } }
            }
        }

        matchesQuery && matchesCatalogFlags(
            bookStatus = book.status,
            isFavorite = book.favorite,
            sections = bookSections(book),
            hasCover = book.cover != null,
            hasPlot = book.spanishPlot.isNotBlank(),
            hasBiography = book.authorBio.isNotBlank(),
            hasKnownAuthor = displayAuthor(book) != "Biblioteca de Diroka77",
            needsReview = book.uri in pending,
            status = status,
            favoritesOnly = favorites,
            section = section,
            quality = quality
        )
    }
}
