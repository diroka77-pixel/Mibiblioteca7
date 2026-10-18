package com.diego.mibiblioteca

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    private fun book(uri: String = "content://provider/document/1") = JSONObject()
        .put("uri", uri).put("title", "Mi libro").put("status", "READING")
        .put("readerPercent", 42).put("readerChar", 150)
    private fun backup(vararg books: JSONObject) = JSONObject()
        .put("backupVersion", 1).put("books", JSONArray(books.toList()))

    @Test fun acceptsLegacyBackupAndCurrentReadingPosition() {
        assertEquals(150, validateBackup(backup(book())).getJSONArray("books").getJSONObject(0).getInt("readerChar"))
        assertNotNull(validateBackup(backup(book().removeChar())))
    }
    private fun JSONObject.removeChar() = apply { remove("readerChar") }

    @Test fun rejectsDeepJsonBeforeParserAndAllowsBracketsInsideText() {
        assertThrows(IllegalArgumentException::class.java) { parseBackup("[".repeat(10000) + "]".repeat(10000)) }
        assertNotNull(parseBackup(backup(book().put("notes", "[Una nota] {texto}")).toString()))
    }
    @Test fun rejectsDuplicateUrisBeforeRestoration() {
        assertThrows(IllegalArgumentException::class.java) { validateBackup(backup(book(), book())) }
    }
    @Test fun removesDuplicateUrisBeforeCreatingBackup() {
        val unique = book("content://provider/document/2").put("title", "Otro")
        val clean = deduplicateBackupBooks(JSONArray().put(book()).put(book()).put(unique))
        assertEquals(2, clean.length())
        assertEquals("Mi libro", clean.getJSONObject(0).getString("title"))
        assertEquals("Otro", clean.getJSONObject(1).getString("title"))
    }
    @Test fun keepsInvalidUrisForStrictValidation() {
        val clean = deduplicateBackupBooks(JSONArray().put(book("")).put(book("")))
        assertEquals(2, clean.length())
        assertThrows(IllegalArgumentException::class.java) {
            validateBackup(backup(*Array(clean.length()) { clean.getJSONObject(it) }))
        }
    }
    @Test fun rejectsFileReferencesAndInvalidPositions() {
        assertThrows(IllegalArgumentException::class.java) { validateBackup(backup(book("file:///etc/passwd"))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(backup(book().put("readerPercent", 101))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(backup(book().put("readerChar", -1))) }
    }
    @Test fun rejectsOversizedFieldEvenInLastRecord() {
        assertThrows(IllegalArgumentException::class.java) {
            validateBackup(backup(book(), book("content://provider/document/2").put("notes", "x".repeat(128001))))
        }
    }
    @Test fun rejectsInvalidBase64AndHighlights() {
        assertThrows(IllegalArgumentException::class.java) { validateBackup(backup(book().put("coverBase64", "x"))) }
        assertThrows(IllegalArgumentException::class.java) {
            validateBackup(backup(book().put("readerHighlights", "[{\"paragraph\":0,\"start\":10,\"end\":3}]")))
        }
    }
    @Test fun rejectsLookalikeGoodreadsDomain() {
        assertThrows(IllegalArgumentException::class.java) {
            validateBackup(backup(book()).put("wishList", JSONArray().put(JSONObject()
                .put("title", "Un libro").put("url", "https://www.goodreads.com.evil.test/book/1"))))
        }
    }

    @Test fun acceptsDiscoverAndManualWishLinks() {
        assertNotNull(validateBackup(backup(book()).put("wishList", JSONArray()
            .put(JSONObject().put("title", "Uno").put("url", "https://openlibrary.org/works/OL1W"))
            .put(JSONObject().put("title", "Dos").put("url", "")))))
    }

    @Test fun currentBackupDetectsAnyLaterModification() {
        val current = JSONObject()
            .put("backupVersion", 2)
            .put("updatedAt", 123L)
            .put("sections", JSONArray())
            .put("wishList", JSONArray())
            .put("books", JSONArray().put(book()))
        current.put("contentSha256", backupChecksum(current))
        assertNotNull(validateBackup(current))
        current.getJSONArray("books").getJSONObject(0).put("title", "Alterado")
        assertThrows(IllegalArgumentException::class.java) { validateBackup(current) }
    }
}
