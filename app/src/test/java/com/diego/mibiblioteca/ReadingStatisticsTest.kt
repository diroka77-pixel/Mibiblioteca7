package com.diego.mibiblioteca

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatisticsTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test fun acceptsCommonSpanishAndIsoDates() {
        assertEquals(LocalDate.of(2026, 10, 18), parseReadingDate("18/10/2026"))
        assertEquals(LocalDate.of(2026, 10, 18), parseReadingDate("2026-10-18"))
        assertEquals(LocalDate.of(2026, 10, 18), parseReadingDate("18-10-2026"))
        assertNull(parseReadingDate("octubre de 2026"))
    }

    @Test fun annualGoalUsesRealFinishDatesAndNeverDividesByZero() {
        val summary = calculateReadingStats(listOf(
            ReadingStatsInput(ReadingStatus.READ, finishedDate = "18/10/2026"),
            ReadingStatsInput(ReadingStatus.READ, finishedDate = "2026-09-03"),
            ReadingStatsInput(ReadingStatus.READ, finishedDate = "2025-12-31"),
            ReadingStatsInput(ReadingStatus.READING, progress = 50)
        ), today, annualGoal = 0)
        assertEquals(2, summary.currentYearRead)
        assertEquals(1, summary.currentMonthRead)
        assertEquals(1, summary.goal)
        assertEquals(1f, summary.goalPercent)
        assertEquals(50, summary.averageActiveProgress)
    }

    @Test fun habitsUseReadBooksInsteadOfTheWholeCatalogue() {
        val summary = calculateReadingStats(listOf(
            ReadingStatsInput(ReadingStatus.READ, finishedDate = "18/10/2026",
                genres = listOf("Fantasía"), author = "Autora A", rating = 5, rereadCount = 1),
            ReadingStatsInput(ReadingStatus.READ, finishedDate = "02/09/2026",
                genres = listOf("Fantasía", "Aventura"), author = "Autora A", rating = 3),
            ReadingStatsInput(ReadingStatus.PENDING, genres = listOf("Terror"), author = "Autor B")
        ), today, annualGoal = 12)
        assertEquals("Fantasía" to 2, summary.topGenres.first())
        assertEquals("Autora A" to 2, summary.topAuthors.first())
        assertEquals(4.0, summary.averageRating ?: 0.0, 0.01)
        assertEquals(1, summary.rereads)
        assertEquals(2, summary.monthStreak)
        assertTrue(summary.projectedYearTotal >= summary.currentYearRead)
    }

    @Test fun reportsReadBooksWithoutUsableFinishDate() {
        val summary = calculateReadingStats(listOf(
            ReadingStatsInput(ReadingStatus.READ),
            ReadingStatsInput(ReadingStatus.READ, finishedDate = "sin fecha")
        ), today, 20)
        assertEquals(2, summary.totalRead)
        assertEquals(2, summary.readWithoutFinishDate)
        assertEquals(0, summary.currentYearRead)
    }
}
