package com.diego.mibiblioteca

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalRecommendationsTest {
    private val library = listOf(
        "Alicia en el país de las maravillas" to "Lewis Carroll",
        "1984" to "George Orwell",
        "El nombre de la rosa" to "Umberto Eco"
    )

    @Test fun rejectsAnotherEditionWithSubtitle() {
        assertTrue(isLikelyOwnedRecommendation(
            "1984: una novela", "George Orwell", library))
        assertTrue(isLikelyOwnedRecommendation(
            "El nombre de la rosa (edición ilustrada)", "Umberto Eco", library))
    }

    @Test fun keepsAGenuinelyDifferentBook() {
        assertFalse(isLikelyOwnedRecommendation(
            "La casa de los espíritus", "Isabel Allende", library))
    }
}
