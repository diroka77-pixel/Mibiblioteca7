package com.diego.mibiblioteca

import android.net.Uri

enum class ReadingStatus(val label: String) {
    PENDING("Pendiente"),
    READING("Leyendo"),
    READ("Leído")
}

/** Estado de lectura traducido para mostrarlo. [ReadingStatus.label] sigue en español
 *  porque también identifica el icono. */
internal val ReadingStatus.displayLabel: String get() = tr(label)

/** Registro persistente de un libro. Los campos custom siempre prevalecen. */
data class Book(
    val uri: Uri,
    val title: String,
    val author: String = "Autor desconocido",
    val date: String = "",
    val publisher: String = "",
    val genre: String = "",
    val description: String = "Sin descripción disponible.",
    val isbn: String = "",
    val saga: String = "",
    val cover: ByteArray? = null,
    val favorite: Boolean = false,
    val status: ReadingStatus = ReadingStatus.PENDING,
    val language: String = "",
    val spanishPlot: String = "",
    val authorBio: String = "",
    val section: String = "",
    val sections: List<String> = emptyList(),
    val notes: String = "",
    val goodreadsUrl: String = "",
    val wantToRead: Boolean = false,
    val customTitle: String = "",
    val customAuthor: String = "",
    val sagaOrder: String = "",
    val sourceSize: Long = -1L,
    val sourceModified: Long = -1L,
    val metadataRevision: Int = 0,
    val sourceCoverChecked: Boolean = false,
    val hadEmbeddedCover: Boolean = false,
    val plotSource: String = "",
    val bioSource: String = "",
    val coverSource: String = "",
    val rating: Int = 0,
    val startedDate: String = "",
    val finishedDate: String = "",
    val quotes: String = "",
    val tags: List<String> = emptyList(),
    val loanTo: String = "",
    val loanDate: String = "",
    val rereadCount: Int = 0
)
