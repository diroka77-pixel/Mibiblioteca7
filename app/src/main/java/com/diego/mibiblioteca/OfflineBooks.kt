package com.diego.mibiblioteca

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private fun offlineFingerprint(book: Book) = "${book.sourceSize}:${book.sourceModified}"

internal fun offlineBookUri(context: Context, book: Book): Uri? {
    val key = readerProgressKey(book.uri)
    val prefs = context.getSharedPreferences("offline_books", Context.MODE_PRIVATE)
    if (prefs.getString("stamp_$key", null) != offlineFingerprint(book)) return null
    return prefs.getString("uri_$key", null)?.let(Uri::parse)
}

/** Keeps a public copy in Downloads. The catalog still points to the Drive document. */
internal suspend fun saveOfflineBook(context: Context, book: Book,
    onProgress: suspend (Int) -> Unit): String = withContext(Dispatchers.IO) {
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    require(book.sourceSize <= FileLimits.BOOK_BYTES) { "El libro supera el límite de 256 MB" }
    val original = try { androidx.documentfile.provider.DocumentFile.fromSingleUri(context, book.uri)?.name }
        catch (_: Exception) { null }
    val extension = original.orEmpty().substringAfterLast('.', "epub").lowercase()
        .takeIf { it in setOf("epub", "pdf", "mobi", "azw", "azw3", "txt", "html", "htm", "rtf", "docx", "md") }
        ?: "epub"
    val filename = (original ?: "${displayTitle(book)}.$extension")
        .substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\x00-\\x1f\\x7f]"), " ").take(120)
        .ifBlank { "Libro.$extension" }
    val mime = if (extension == "epub") "application/epub+zip"
        else if (extension == "pdf") "application/pdf" else "application/octet-stream"
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, filename)
        put(MediaStore.Downloads.MIME_TYPE, mime)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val previous = offlineBookUri(context, book)
    val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("No se pudo crear el archivo en Descargas")
    try {
        var copied = 0L
        var lastPercent = -1
        val total = book.sourceSize.takeIf { it > 0L } ?: try {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, book.uri)
                ?.length()?.takeIf { it > 0L }
        } catch (_: Exception) { null }
        (resolver.openInputStream(book.uri) ?: error("Drive no permite leer este libro")).use { input ->
            (resolver.openOutputStream(target, "w") ?: error("No se pudo escribir en Descargas")).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= FileLimits.BOOK_BYTES) { "El libro supera el límite de 256 MB" }
                    output.write(buffer, 0, count)
                    if (total != null) {
                        val percent = ((copied * 100L) / total).toInt().coerceIn(0, 99)
                        if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                    }
                }
            }
        }
        require(copied > 0L) { "El libro está vacío" }
        check(resolver.update(target, ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }, null, null) > 0) { "No se pudo completar la descarga" }
        val key = readerProgressKey(book.uri)
        context.getSharedPreferences("offline_books", Context.MODE_PRIVATE).edit()
            .putString("uri_$key", target.toString())
            .putString("stamp_$key", offlineFingerprint(book)).apply()
        if (previous != null && previous != target) try { resolver.delete(previous, null, null) }
            catch (_: Exception) { /* La copia anterior sigue disponible en Descargas. */ }
        onProgress(100)
        filename
    } catch (error: Exception) {
        resolver.delete(target, null, null)
        throw error
    }
}
