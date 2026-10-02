package com.diego.mibiblioteca

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

internal object FileLimits {
    const val BOOK_BYTES = 256L * 1024 * 1024
    const val BACKUP_BYTES = 32L * 1024 * 1024
    const val TEXT_BYTES = 16L * 1024 * 1024
    const val INDEX_BYTES = 1024L * 1024
    const val CHAPTER_BYTES = 3L * 1024 * 1024
    const val CHAPTERS = 2000
    const val ZIP_ENTRIES = 20000
    const val FREE_SPACE = 32L * 1024 * 1024
}

/** Count actual bytes, including streams without a trustworthy declared size. */
internal fun InputStream.copyLimited(output: OutputStream, limit: Long,
    checkChunk: (Long) -> Unit = {}): Long {
    require(limit >= 0)
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
        val count = read(buffer, 0, minOf(buffer.size.toLong(), limit - total + 1).toInt())
        if (count < 0) return total
        if (count == 0) continue
        total += count
        require(total <= limit) { "El archivo supera el límite permitido (${limit / 1024 / 1024} MB)." }
        checkChunk(total)
        output.write(buffer, 0, count)
    }
}

internal fun InputStream.readLimited(limit: Long): ByteArray =
    ByteArrayOutputStream().also { copyLimited(it, limit) }.toByteArray()

internal fun ZipFile.readEntryLimited(path: String, limit: Long): ByteArray? {
    require(size() <= FileLimits.ZIP_ENTRIES) { "El documento contiene demasiadas entradas." }
    val entry = getEntry(path) ?: return null
    require(!entry.isDirectory && entry.size <= limit) { "Una sección del documento es demasiado grande." }
    return getInputStream(entry).use { it.readLimited(limit) }
}
