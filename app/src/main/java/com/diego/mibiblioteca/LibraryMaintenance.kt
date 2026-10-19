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
    val duplicateReferences: Int,
    val sagasWithGaps: Int,
    val inaccessibleFiles: Int,
    val damagedCovers: Int,
    val smallCovers: Int,
    val ignoredProblems: Int,
    val fileProblemUris: Set<String>,
    val coverProblemUris: Set<String>,
    val problemKeys: Set<String>
) {
    val needsAttention: Int get() = problemKeys.map { it.substringAfter(':') }.distinct().size
}

internal data class MaintenanceProbe(
    val accessible: Boolean? = null,
    val coverWidth: Int = -1,
    val coverHeight: Int = -1,
    val coverDecodable: Boolean? = null
)

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

internal fun calculateLibraryMaintenance(
    books: List<Book>,
    probes: Map<String, MaintenanceProbe> = emptyMap(),
    ignored: Set<String> = emptySet()
): LibraryMaintenanceReport =
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
    }, probes, ignored)

internal fun calculateLibraryMaintenanceInputs(
    books: List<MaintenanceBookInput>,
    probes: Map<String, MaintenanceProbe> = emptyMap(),
    ignored: Set<String> = emptySet()
): LibraryMaintenanceReport {
    if (books.isEmpty()) return LibraryMaintenanceReport(
        total = 0, healthy = 0, healthScore = 100, missingCover = 0,
        unknownAuthor = 0, missingPlot = 0, missingBiography = 0, emptyFiles = 0,
        unsupportedFiles = 0, unverifiedSize = 0, duplicateCopies = 0,
        duplicateReferences = 0, sagasWithGaps = 0, inaccessibleFiles = 0,
        damagedCovers = 0, smallCovers = 0, ignoredProblems = 0,
        fileProblemUris = emptySet(), coverProblemUris = emptySet(), problemKeys = emptySet()
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
    val inaccessible = books.count { probes[it.id]?.accessible == false }
    val damagedCovers = books.count { probes[it.id]?.coverDecodable == false }
    val smallCovers = books.count {
        probes[it.id]?.let { probe -> probe.coverWidth in 1 until 240 || probe.coverHeight in 1 until 320 } == true
    }
    val duplicateReferences = books.groupingBy { it.id }.eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }
    val fileProblems = books.filter {
        it.sourceSize == 0L || it.extension.isNotBlank() && it.extension !in readableBookFormats
            || probes[it.id]?.accessible == false
    }.mapTo(linkedSetOf()) { it.id }
    val coverProblems = books.filter {
        !it.hasCover || probes[it.id]?.coverDecodable == false || probes[it.id]?.let { probe ->
            probe.coverWidth in 1 until 240 || probe.coverHeight in 1 until 320
        } == true
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
    val problemKeys = linkedSetOf<String>()
    books.forEach { book ->
        if (!book.hasCover) problemKeys += "cover:${book.id}"
        if (!knownAuthor(book.author)) problemKeys += "author:${book.id}"
        if (!book.hasPlot) problemKeys += "plot:${book.id}"
        if (!book.hasBiography) problemKeys += "bio:${book.id}"
        if (book.id in fileProblems) problemKeys += "file:${book.id}"
        if (probes[book.id]?.coverDecodable == false) problemKeys += "damaged-cover:${book.id}"
        probes[book.id]?.let { if (it.coverWidth in 1 until 240 || it.coverHeight in 1 until 320)
            problemKeys += "small-cover:${book.id}" }
    }
    val activeProblems = problemKeys - ignored
    val healthy = books.count { book ->
        activeProblems.none { key -> key.endsWith(":${book.id}") }
    }
    val weightedProblems = activeProblems.fold(0) { total, key ->
        total + if (key.startsWith("file:") || key.startsWith("damaged-cover:")) 2 else 1
    }
    val checks = books.size * 8
    val score = (100 - weightedProblems * 100 / checks.coerceAtLeast(1)).coerceIn(0, 100)
    return LibraryMaintenanceReport(
        total = books.size, healthy = healthy, healthScore = score,
        missingCover = missingCover, unknownAuthor = unknownAuthor, missingPlot = missingPlot,
        missingBiography = missingBiography, emptyFiles = emptyFiles,
        unsupportedFiles = unsupported, unverifiedSize = unverified,
        duplicateCopies = duplicates, duplicateReferences = duplicateReferences,
        sagasWithGaps = sagaGaps, inaccessibleFiles = inaccessible,
        damagedCovers = damagedCovers, smallCovers = smallCovers,
        ignoredProblems = problemKeys.count { it in ignored },
        fileProblemUris = fileProblems, coverProblemUris = coverProblems,
        problemKeys = activeProblems
    )
}

internal fun maintenanceProblemUris(books: List<Book>): Set<android.net.Uri> =
    calculateLibraryMaintenance(books).fileProblemUris.mapTo(linkedSetOf(), android.net.Uri::parse)
