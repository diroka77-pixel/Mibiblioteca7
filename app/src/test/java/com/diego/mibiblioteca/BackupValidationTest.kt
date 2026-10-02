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

    @Test fun rejectsDuplicateUrisBeforeRestoration() {
        assertThrows(IllegalArgumentException::class.java) { validateBackup(backup(book(), book())) }
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
}
