package com.diego.mibiblioteca

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
        plot: Boolean = true,
        bio: Boolean = true,
        saga: String = "",
        order: String = "",
        extension: String = "epub"
    ) = MaintenanceBookInput(
        id = "content://books/$id.$extension",
        title = title,
        author = author,
        hasCover = cover,
        hasPlot = plot,
        hasBiography = bio,
        extension = extension,
        sourceSize = size,
        saga = saga,
        sagaOrder = order
    )

    @Test fun reportsCatalogProblemsWithoutOpeningDriveFiles() {
        val books = listOf(
            book(
                "one",
                "Uno",
                size = 0,
                cover = false,
                author = "Biblioteca de Diroka77",
                plot = false,
                bio = false
            ),
            book("two", "Dos")
        )
        val report = calculateLibraryMaintenanceInputs(books)
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
        val report = calculateLibraryMaintenanceInputs(books)
        assertEquals(1, report.duplicateCopies)
        assertEquals(1, report.sagasWithGaps)
    }

    @Test fun incrementalProbeDetectsUnreadableFilesAndBadCovers() {
        val target = book("damaged", "Portada rota")
        val report = calculateLibraryMaintenanceInputs(
            listOf(target),
            probes = mapOf(target.id to MaintenanceProbe(
                accessible = false,
                coverWidth = 120,
                coverHeight = 180,
                coverDecodable = false
            ))
        )
        assertEquals(1, report.inaccessibleFiles)
        assertEquals(1, report.damagedCovers)
        assertEquals(1, report.smallCovers)
        assertTrue(target.id in report.fileProblemUris)
        assertTrue(target.id in report.coverProblemUris)
        assertTrue(report.needsAttention > 0)
    }

    @Test fun separatesRepeatedReferencesFromPossibleDuplicateBooks() {
        val repeated = book("same", "Una historia")
        val report = calculateLibraryMaintenanceInputs(listOf(repeated, repeated))
        assertEquals(1, report.duplicateReferences)
        assertEquals(1, report.duplicateCopies)
    }

    @Test fun ignoredWarningsRemainCountedButDoNotRequireAttention() {
        val incomplete = book("keep", "Conservar", cover = false)
        val warning = "cover:${incomplete.id}"
        val report = calculateLibraryMaintenanceInputs(listOf(incomplete), ignored = setOf(warning))
        assertEquals(1, report.missingCover)
        assertEquals(1, report.ignoredProblems)
        assertEquals(0, report.needsAttention)
        assertEquals(100, report.healthScore)
    }
}
