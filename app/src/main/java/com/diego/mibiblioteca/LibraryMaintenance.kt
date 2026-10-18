package com.diego.mibiblioteca

internal data class LibraryMaintenanceReport(
    val total: Int,
    val healthy: Int,
    val healthScore: Int,
    val missingCover: Int,
    val unknownAuthor: Int,
    val missingPlot: Int,
    val missingBiography: Int,
    val emptyFiles: Int,
    val unsupportedFiles: Int,
    val unverifiedSize: Int,
    val duplicateCopies: Int,
    val sagasWithGaps: Int,
    val fileProblemUris: Set<String>
) {
    val needsAttention: Int get() = total - healthy
}

private val readableBookFormats = setOf(
    "epub", "pdf", "mobi", "azw", "azw3", "fb2", "txt",
    "html", "htm", "rtf", "docx", "md"
)

private fun bookExtension(book: Book): String {
    val raw = android.net.Uri.decode(book.uri.lastPathSegment.orEmpty())
    return raw.substringAfterLast('.', "").lowercase()
}

internal fun calculateLibraryMaintenance(books: List<Book>): LibraryMaintenanceReport {
    if (books.isEmpty()) return LibraryMaintenanceReport(
        0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, emptySet()
    )
    val missingCover = books.count { it.cover == null }
    val unknownAuthor = books.count { unknownAuthor(displayAuthor(it)) }
    val missingPlot = books.count { it.spanishPlot.isBlank() }
    val missingBiography = books.count { it.authorBio.isBlank() }
    val emptyFiles = books.count { it.sourceSize == 0L }
    val unsupported = books.count { bookExtension(it).let { extension ->
        extension.isNotBlank() && extension !in readableBookFormats
    } }
    val unverified = books.count { it.sourceSize < 0L }
    val duplicates = findMaintenanceDuplicateGroups(books)
        .sumOf { (it.size - 1).coerceAtLeast(0) }
    val fileProblems = books.filter { book ->
        book.sourceSize == 0L || bookExtension(book).let { it.isNotBlank() && it !in readableBookFormats }
    }.mapTo(linkedSetOf()) { it.uri.toString() }
    val sagaGaps = books.filter { it.saga.isNotBlank() }
        .groupBy { CatalogQuality.normalize(it.saga) }
        .count { (_, sagaBooks) ->
            val positions = sagaBooks.mapNotNull {
                it.sagaOrder.replace(',', '.').toDoubleOrNull()?.toInt()
            }.filter { it > 0 }.distinct().sorted()
            positions.size >= 2 && (positions.first()..positions.last()).any { it !in positions }
        }
    val healthy = books.count { book ->
        book.cover != null && !unknownAuthor(displayAuthor(book)) &&
            book.spanishPlot.isNotBlank() && book.authorBio.isNotBlank() &&
            book.uri.toString() !in fileProblems
    }
    val weightedProblems = missingCover + unknownAuthor + missingPlot + missingBiography +
        (emptyFiles + unsupported) * 2
    val checks = books.size * 6
    val score = (100 - weightedProblems * 100 / checks.coerceAtLeast(1)).coerceIn(0, 100)
    return LibraryMaintenanceReport(
        books.size, healthy, score, missingCover, unknownAuthor, missingPlot,
        missingBiography, emptyFiles, unsupported, unverified, duplicates,
        sagaGaps, fileProblems
    )
}

internal fun maintenanceProblemUris(books: List<Book>): Set<android.net.Uri> =
    calculateLibraryMaintenance(books).fileProblemUris.mapTo(linkedSetOf(), android.net.Uri::parse)

private fun findMaintenanceDuplicateGroups(books: List<Book>): List<List<Book>> {
    val normalized = Regex("[^\\p{L}\\p{N}]")
    return books.groupBy { book ->
        val isbn = book.isbn.filter(Char::isDigit)
        if (isbn.length == 13) "isbn:$isbn"
        else (displayTitle(book) + "|" + displayAuthor(book)).lowercase().replace(normalized, "")
    }.values.filter { group -> group.size > 1 && displayTitle(group.first()).length >= 3 }
}
