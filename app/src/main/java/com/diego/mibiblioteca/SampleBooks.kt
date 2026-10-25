package com.diego.mibiblioteca

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Clásicos en español de dominio público para probar la app sin tener libros.
 *
 * Todos proceden de Project Gutenberg, que permite su libre descarga y lectura.
 * Sus autores murieron hace más de 80 años, así que también son de dominio
 * público en España. Cada número se ha comprobado en gutenberg.org (idioma:
 * español). Se descargan solo cuando el usuario lo pide.
 */
internal data class SampleBook(val gutenbergId: Int, val title: String, val author: String) {
    val downloadUrl: String get() = "https://www.gutenberg.org/ebooks/$gutenbergId.epub3.images"
    val fileName: String get() = "clasico-$gutenbergId.epub"
}

internal val sampleBooks = listOf(
    SampleBook(2000, "Don Quijote", "Miguel de Cervantes"),
    SampleBook(320, "Lazarillo de Tormes", "Anónimo"),
    SampleBook(17340, "Marianela", "Benito Pérez Galdós"),
    SampleBook(17223, "Pepita Jiménez", "Juan Valera"),
    SampleBook(49836, "Niebla", "Miguel de Unamuno")
)

/** Tamaño máximo aceptado para un clásico (el Quijote ilustrado ronda los 3 MB). */
private const val SAMPLE_MAX_BYTES = 25L * 1024 * 1024

/**
 * Descarga un clásico a [folder]. Devuelve el archivo o null si falla.
 * Se ejecuta fuera del hilo principal.
 */
internal fun downloadSampleBook(sample: SampleBook, folder: File): File? {
    folder.mkdirs()
    val target = File(folder, sample.fileName)
    val partial = File(folder, sample.fileName + ".partial")
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(sample.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", AppBrand.API_USER_AGENT)
        }
        if (connection.responseCode !in 200..299) return null
        if (connection.contentLengthLong > SAMPLE_MAX_BYTES) return null
        connection.inputStream.use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > SAMPLE_MAX_BYTES) return null
                    output.write(buffer, 0, read)
                }
            }
        }
        if (partial.length() <= 0L) return null
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) return null
        target
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
        partial.delete()
    }
}
