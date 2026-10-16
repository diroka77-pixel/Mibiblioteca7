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

        matchesQuery &&
            (status == null || book.status == status) &&
            (!favorites || book.favorite) &&
            (section == null || section in bookSections(book)) &&
            when (quality) {
                "Portada" -> book.cover == null
                "Argumento" -> book.spanishPlot.isBlank()
                "Biografía" -> book.authorBio.isBlank()
                "Autor" -> displayAuthor(book) == "Biblioteca de Diroka77"
                "Revisar" -> book.uri in pending
                "Todos" -> book.cover == null ||
                    book.spanishPlot.isBlank() ||
                    book.authorBio.isBlank() ||
                    displayAuthor(book) == "Biblioteca de Diroka77" ||
                    book.uri in pending
                else -> true
            }
    }
}
