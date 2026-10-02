package com.diego.mibiblioteca

import org.junit.Assert.*
import org.junit.Test

class LibraryMergeTest {
    private data class Record(val uri: String, val title: String, val favorite: Boolean = false)
    private fun merge(scanned: List<Record>, before: List<Record>, latest: List<Record>) =
        mergeLibraryScan(scanned, before.associateBy { it.uri }, latest.associateBy { it.uri },
            { it.uri }) { fresh, current -> fresh.copy(favorite = current?.favorite ?: false) }

    @Test fun incompleteListingKeepsUnconfirmedRecords() {
        val records = listOf(Record("1", "Uno"), Record("2", "Dos", true))
        assertEquals(records, merge(records.take(1), records, records))
    }
    @Test fun editMadeDuringScanWinsOverOldSnapshot() {
        val old = Record("1", "Uno")
        assertTrue(merge(listOf(old), listOf(old), listOf(old.copy(favorite = true))).single().favorite)
    }
    @Test fun explicitDeletionDuringScanDoesNotReappear() {
        val removed = Record("1", "Uno")
        val added = Record("2", "Dos")
        assertEquals(listOf(added), merge(listOf(removed, added), listOf(removed), emptyList()))
    }
    @Test fun newMetadataIsKeptAlongsideCurrentUserState() {
        val old = Record("1", "Viejo", true)
        assertEquals(Record("1", "Nuevo", true), merge(listOf(old.copy(title = "Nuevo", favorite = false)),
            listOf(old), listOf(old)).single())
    }
}
