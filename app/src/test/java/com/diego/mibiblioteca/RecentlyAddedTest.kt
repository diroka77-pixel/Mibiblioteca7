package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentlyAddedTest {
    private data class B(val name: String, val added: Long, val file: Long)

    @Test fun newestAdditionsComeFirstThenFileDate() {
        val books = listOf(B("viejo", 0, 100), B("nuevo", 5000, 10), B("mismo-dia-a", 1000, 50),
            B("mismo-dia-b", 1000, 70), B("sin-fecha", 0, -1), B("otro", 0, 300))
        val result = recentlyAddedBooks(books, { it.added }, { it.file }, limit = 5).map { it.name }
        assertEquals(listOf("nuevo", "mismo-dia-b", "mismo-dia-a", "otro", "viejo"), result)
    }

    @Test fun datesSurviveSaving() {
        val dates = mapOf("content://a/tree/x/document/1" to 123L, "file:///b.epub" to 456L)
        assertEquals(dates, parseAddedDates(formatAddedDates(dates)))
        assertEquals(emptyMap<String, Long>(), parseAddedDates(null))
    }
}
