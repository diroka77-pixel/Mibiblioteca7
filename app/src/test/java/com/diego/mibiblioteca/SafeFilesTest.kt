package com.diego.mibiblioteca

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SafeFilesTest {
    @Test fun acceptsExactLimitAndEmptyStream() {
        assertArrayEquals(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf(1, 2, 3)).readLimited(3))
        assertEquals(0, ByteArrayInputStream(byteArrayOf()).readLimited(0).size)
    }

    @Test fun oversizedStreamNeverWritesBeyondLimit() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(ByteArray(100000)).copyLimited(output, 70000)
        }
        assertTrue(output.size() <= 70000)
    }

    @Test fun spaceCheckStopsCopyBeforeWritingChunk() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            ByteArrayInputStream(ByteArray(100)).copyLimited(output, 100) { error("Sin espacio") }
        }
        assertEquals(0, output.size())
    }

    @Test fun compressedEntryIsLimitedByUncompressedSize() {
        val file = File.createTempFile("safe-epub", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use {
                it.putNextEntry(ZipEntry("chapter.xhtml"))
                it.write(ByteArray(1000000))
                it.closeEntry()
            }
            assertTrue(file.length() < 10000)
            ZipFile(file).use {
                assertThrows(IllegalArgumentException::class.java) { it.readEntryLimited("chapter.xhtml", 10000) }
                assertEquals(1000000, it.readEntryLimited("chapter.xhtml", 1000000)!!.size)
                assertNull(it.readEntryLimited("missing", 100))
            }
        } finally { file.delete() }
    }
}
