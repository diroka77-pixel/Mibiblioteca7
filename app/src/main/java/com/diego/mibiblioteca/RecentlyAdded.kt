package com.diego.mibiblioteca

/**
 * «Últimos añadidos» de Inicio: los libros más recientes de la biblioteca.
 *
 * Primero cuenta la fecha en que Bookhollow vio el libro por primera vez (se anota al
 * sincronizar o importar). Para los libros que ya estaban antes de existir esta función,
 * o los que entraron a la vez en la primera sincronización, decide la fecha del archivo
 * en la carpeta, que suele ser la de cuando se subió.
 */
internal fun <T> recentlyAddedBooks(books: List<T>, addedAt: (T) -> Long, fileDate: (T) -> Long,
                                    limit: Int = 5): List<T> =
    books.filter { addedAt(it) > 0L || fileDate(it) > 0L }
        .sortedWith(compareByDescending<T> { addedAt(it) }.thenByDescending { fileDate(it) })
        .take(limit)

/** Fechas de alta guardadas como «uri<TAB>milisegundos» por línea. */
internal fun parseAddedDates(raw: String?): Map<String, Long> =
    raw.orEmpty().lineSequence().mapNotNull { line ->
        val tab = line.lastIndexOf('\t')
        if (tab <= 0) null else line.substring(tab + 1).toLongOrNull()?.let { line.substring(0, tab) to it }
    }.toMap()

internal fun formatAddedDates(dates: Map<String, Long>): String =
    dates.entries.joinToString("\n") { it.key + "\t" + it.value }
