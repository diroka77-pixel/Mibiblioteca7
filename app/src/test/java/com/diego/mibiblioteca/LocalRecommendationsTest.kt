package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecommendationsTest {
    @Test fun prioritizesKnownAuthorsAndGenres() {
        val result = localBookRecommendations(listOf(
            input("read", "Leído", "Autora A", listOf("Fantasía"), ReadingStatus.READ, rating = 5),
            input("match", "Coincide", "Autora A", listOf("Fantasía"), ReadingStatus.PENDING),
            input("other", "Otro", "Autor B", listOf("Ensayo"), ReadingStatus.PENDING)
        ))
        assertEquals("match", result.first().id)
        assertTrue(result.first().reason.contains("Autora A"))
    }

    @Test fun wantToReadIsVisibleAndRecommendationsStayDiverse() {
        val books = mutableListOf(
            input("read", "Leído", "Autor A", listOf("Terror"), ReadingStatus.READ)
        )
        repeat(4) { books += input("a$it", "A $it", "Autor A", listOf("Terror"), ReadingStatus.PENDING) }
        books += input("wanted", "Deseado", "Autor B", listOf("Drama"), ReadingStatus.PENDING, want = true)
        val result = localBookRecommendations(books, 3)
        assertTrue(result.any { it.id == "wanted" && it.reason.contains("Quiero leer") })
        assertEquals(3, result.size)
    }

    @Test fun fallsBackToPendingBooksWithoutHistory() {
        val result = localBookRecommendations(listOf(
            input("b", "Beta", "B", emptyList(), ReadingStatus.PENDING),
            input("a", "Alfa", "A", emptyList(), ReadingStatus.PENDING)
        ))
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    private fun input(
        id: String, title: String, author: String, genres: List<String>, status: ReadingStatus,
        rating: Int = 0, want: Boolean = false
    ) = RecommendationInput(id, title, author, genres, "", status,
        wantToRead = want, rating = rating)
}

