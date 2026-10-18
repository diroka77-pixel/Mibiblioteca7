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

internal data class MaintenanceBookInput(
    val id: String,
    val title: String,
    val author: String,
    val isbn: String = "",
    val hasCover: Boolean,
    val hasPlot: Boolean,
    val hasBiography: Boolean,
    val extension: String,
    val sourceSize: Long,
    val saga: String = "",
    val sagaOrder: String = ""
)

private val readableBookFormats = setOf(
    "epub", "pdf", "mobi", "azw", "azw3", "fb2", "txt",
    "html", "htm", "rtf", "docx", "md"
)

internal fun calculateLibraryMaintenance(books: List<Book>): LibraryMaintenanceReport =
    calculateLibraryMaintenanceInputs(books.map { book ->
        val rawName = book.uri.lastPathSegment.orEmpty()
        MaintenanceBookInput(
            id = book.uri.toString(),
            title = displayTitle(book),
            author = displayAuthor(book),
            isbn = book.isbn,
            hasCover = book.cover != null,
            hasPlot = book.spanishPlot.isNotBlank(),
            hasBiography = book.authorBio.isNotBlank(),
            extension = rawName.substringAfterLast('.', "").lowercase(),
            sourceSize = book.sourceSize,
            saga = book.saga,
            sagaOrder = book.sagaOrder
        )
    })

internal fun calculateLibraryMaintenanceInputs(
    books: List<MaintenanceBookInput>
): LibraryMaintenanceReport {
    if (books.isEmpty()) return LibraryMaintenanceReport(
        0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, emptySet()
    )
    fun knownAuthor(value: String) = value.isNotBlank() &&
        !value.equals("Biblioteca de Diroka77", true) &&
        !value.equals("Autor desconocido", true)
    val missingCover = books.count { !it.hasCover }
    val unknownAuthor = books.count { !knownAuthor(it.author) }
    val missingPlot = books.count { !it.hasPlot }
    val missingBiography = books.count { !it.hasBiography }
    val emptyFiles = books.count { it.sourceSize == 0L }
    val unsupported = books.count {
        it.extension.isNotBlank() && it.extension !in readableBookFormats
    }
    val unverified = books.count { it.sourceSize < 0L }
    val fileProblems = books.filter {
        it.sourceSize == 0L || it.extension.isNotBlank() && it.extension !in readableBookFormats
    }.mapTo(linkedSetOf()) { it.id }
    val duplicates = books.groupBy { book ->
        val digits = book.isbn.filter(Char::isDigit)
        if (digits.length == 13) "isbn:$digits"
        else (book.title + "|" + book.author).let(CatalogQuality::normalize)
    }.values.filter { it.size > 1 && it.first().title.length >= 3 }
        .sumOf { (it.size - 1).coerceAtLeast(0) }
    val sagaGaps = books.filter { it.saga.isNotBlank() }
        .groupBy { CatalogQuality.normalize(it.saga) }
        .count { (_, sagaBooks) ->
            val positions = sagaBooks.mapNotNull {
                it.sagaOrder.replace(',', '.').toDoubleOrNull()?.toInt()
            }.filter { it > 0 }.distinct().sorted()
            positions.size >= 2 && (positions.first()..positions.last()).any { it !in positions }
        }
    val healthy = books.count {
        it.hasCover && knownAuthor(it.author) && it.hasPlot && it.hasBiography &&
            it.id !in fileProblems
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
