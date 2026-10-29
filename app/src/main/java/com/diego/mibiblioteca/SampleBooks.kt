package com.diego.mibiblioteca

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Clásicos de dominio público para probar la app sin tener libros, en el idioma de la app.
 *
 * Todos proceden de Project Gutenberg, que permite su libre descarga y lectura.
 * Sus autores murieron hace más de 80 años, así que también son de dominio
 * público en España y en la Unión Europea. Cada número se ha comprobado en
 * gutenberg.org (título, autor e idioma de la edición). Se descargan solo cuando
 * el usuario lo pide.
 */
internal data class SampleBook(val gutenbergId: Int, val title: String, val author: String) {
    val downloadUrl: String get() = "https://www.gutenberg.org/ebooks/$gutenbergId.epub3.images"
    val fileName: String get() = "clasico-$gutenbergId.epub"
    /** Portada que publica Project Gutenberg para cada libro. */
    val coverUrl: String get() = "https://www.gutenberg.org/cache/epub/$gutenbergId/pg$gutenbergId.cover.medium.jpg"
}

internal val sampleBooksByLanguage: Map<String, List<SampleBook>> = mapOf(
    "es" to listOf(
        SampleBook(2000, "Don Quijote", "Miguel de Cervantes"),
        SampleBook(320, "Lazarillo de Tormes", "Anónimo"),
        SampleBook(17340, "Marianela", "Benito Pérez Galdós"),
        SampleBook(17223, "Pepita Jiménez", "Juan Valera"),
        SampleBook(49836, "Niebla", "Miguel de Unamuno")
    ),
    "en" to listOf(
        SampleBook(1342, "Pride and Prejudice", "Jane Austen"),
        SampleBook(84, "Frankenstein", "Mary Shelley"),
        SampleBook(11, "Alice's Adventures in Wonderland", "Lewis Carroll"),
        SampleBook(1661, "The Adventures of Sherlock Holmes", "Arthur Conan Doyle"),
        SampleBook(345, "Dracula", "Bram Stoker")
    ),
    "pt" to listOf(
        SampleBook(55752, "Dom Casmurro", "Machado de Assis"),
        SampleBook(54829, "Memórias Póstumas de Brás Cubas", "Machado de Assis"),
        SampleBook(67740, "Iracema", "José de Alencar"),
        SampleBook(18220, "A Cidade e as Serras", "Eça de Queirós"),
        SampleBook(3333, "Os Lusíadas", "Luís de Camões")
    ),
    "fr" to listOf(
        SampleBook(14155, "Madame Bovary", "Gustave Flaubert"),
        SampleBook(13951, "Les Trois Mousquetaires", "Alexandre Dumas"),
        SampleBook(19657, "Notre-Dame de Paris", "Victor Hugo"),
        SampleBook(800, "Le Tour du monde en quatre-vingts jours", "Jules Verne"),
        SampleBook(4650, "Candide", "Voltaire")
    ),
    "de" to listOf(
        SampleBook(22367, "Die Verwandlung", "Franz Kafka"),
        SampleBook(2229, "Faust", "Johann Wolfgang von Goethe"),
        SampleBook(19794, "Die Leiden des jungen Werther", "Johann Wolfgang von Goethe"),
        SampleBook(5323, "Effi Briest", "Theodor Fontane"),
        SampleBook(19790, "Der Schimmelreiter", "Theodor Storm")
    ),
    "it" to listOf(
        SampleBook(45334, "I promessi sposi", "Alessandro Manzoni"),
        SampleBook(52484, "Le avventure di Pinocchio", "Carlo Collodi"),
        SampleBook(1000, "La Divina Commedia", "Dante Alighieri"),
        SampleBook(58341, "Novelle", "Edmondo De Amicis"),
        SampleBook(61209, "I Robinson italiani", "Emilio Salgari")
    )
)

/** Clásicos del idioma indicado; si no hay lista para él, los ingleses. */
internal fun sampleBooksFor(language: String): List<SampleBook> =
    sampleBooksByLanguage[language] ?: sampleBooksByLanguage.getValue("en")

/** Clásicos del idioma actual de la app. */
internal val sampleBooks: List<SampleBook> get() = sampleBooksFor(I18n.currentLanguage())

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
