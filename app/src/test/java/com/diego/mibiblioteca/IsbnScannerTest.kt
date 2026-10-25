package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnScannerTest {
    @Test fun acceptsBookBarcodesOnly() {
        assertEquals("9788439747369", normalizeIsbn("9788439747369"))
        assertEquals("9791234567896", normalizeIsbn("979-1234567896"))
        assertEquals("843974736X", normalizeIsbn("84-397-4736-x"))
        assertNull(normalizeIsbn("5901234123457"))   // EAN de un producto, no de un libro
        assertNull(normalizeIsbn("12345"))
    }

    @Test fun convertsBetweenIsbn10And13() {
        assertEquals("0306406152", isbn13To10("9780306406157"))
        assertEquals("9780306406157", isbn10To13("0306406152"))
        assertNull(isbn13To10("9791234567896"))
    }

    @Test fun matchesTheSameBookWrittenInEitherFormat() {
        val scanned = isbnVariants("9780306406157")
        assertTrue(isbnVariants("0-306-40615-2").any(scanned::contains))
    }
}
