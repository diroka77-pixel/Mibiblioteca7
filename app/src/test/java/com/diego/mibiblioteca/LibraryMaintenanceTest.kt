package com.diego.mibiblioteca

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMaintenanceTest {
    private fun book(
        id: String,
        title: String,
        size: Long = 100,
        cover: Boolean = true,
        author: String = "Autora",
        plot: String = "Argumento",
        bio: String = "Biografía",
        saga: String = "",
        order: String = ""
    ) = Book(
        uri = Uri.parse("content://books/$id.epub"),
        title = title,
        author = author,
        cover = if (cover) byteArrayOf(1) else null,
        spanishPlot = plot,
        authorBio = bio,
        sourceSize = size,
        saga = saga,
        sagaOrder = order
    )

    @Test fun reportsCatalogProblemsWithoutOpeningDriveFiles() {
        val books = listOf(
            book("one", "Uno", size = 0, cover = false,
                author = "Biblioteca de Diroka77", plot = "", bio = ""),
            book("two", "Dos")
        )
        val report = calculateLibraryMaintenance(books)
        assertEquals(1, report.emptyFiles)
        assertEquals(1, report.missingCover)
        assertEquals(1, report.unknownAuthor)
        assertEquals(1, report.healthy)
        assertTrue(report.healthScore < 100)
        assertTrue(report.fileProblemUris.any { it.contains("one.epub") })
    }

    @Test fun detectsDuplicateCopiesAndGapsInsideKnownSagaRange() {
        val books = listOf(
            book("a", "La riada", saga = "Blackwater", order = "1"),
            book("b", "El dique", saga = "Blackwater", order = "2"),
            book("c", "La fortuna", saga = "Blackwater", order = "4"),
            book("copy", "La riada", saga = "Blackwater", order = "1")
        )
        val report = calculateLibraryMaintenance(books)
        assertEquals(1, report.duplicateCopies)
        assertEquals(1, report.sagasWithGaps)
    }
}
