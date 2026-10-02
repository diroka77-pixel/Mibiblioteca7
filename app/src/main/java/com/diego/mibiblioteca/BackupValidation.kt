package com.diego.mibiblioteca

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Base64

internal fun checkJsonDepth(raw: String) {
    var depth = 0
    var quoted = false
    var escaped = false
    for (char in raw) {
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else when (char) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 8) { "El respaldo tiene demasiados niveles." } }
            '}', ']' -> { depth--; require(depth >= 0) { "El respaldo está incompleto." } }
        }
    }
    require(!quoted && depth == 0) { "El respaldo está incompleto." }
}

internal fun parseBackup(raw: String): JSONObject {
    require(raw.toByteArray(Charsets.UTF_8).size <= FileLimits.BACKUP_BYTES)
    checkJsonDepth(raw)
    return validateBackup(JSONObject(raw))
}

internal fun decodeBackupCover(image: String): ByteArray {
    require(image.length <= 1_400_000 && image.all {
        it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "+/=\r\n\t "
    }) { "La portada contiene datos inválidos." }
    return Base64.getMimeDecoder().decode(image).also {
        require(it.isNotEmpty() && it.size <= 1_000_000) { "La portada tiene un tamaño inválido." }
    }
}

/** Validate the complete payload before touching preferences, files or the database. */
internal fun validateBackup(backup: JSONObject): JSONObject {
    require(backup.optInt("backupVersion") == 1) { "Versión de respaldo no compatible." }
    val books = backup.getJSONArray("books")
    require(books.length() <= 10000) { "El respaldo contiene demasiados libros." }
    fun inspect(value: Any?, depth: Int = 0, key: String = "") {
        require(depth <= 8) { "El respaldo tiene una estructura demasiado compleja." }
        when (value) {
            is JSONObject -> {
                require(value.length() <= 100) { "Demasiados campos en el respaldo." }
                val keys = value.keys()
                while (keys.hasNext()) { val name = keys.next(); inspect(value.get(name), depth + 1, name) }
            }
            is JSONArray -> {
                require(value.length() <= 10000) { "Demasiados elementos en el respaldo." }
                for (i in 0 until value.length()) inspect(value.get(i), depth + 1, key)
            }
            is String -> require(value.length <= if (key == "coverBase64") 1_400_000 else 128_000) {
                "Un campo del respaldo es demasiado largo."
            }
        }
    }
    inspect(backup)
    val uris = mutableSetOf<String>()
    for (i in 0 until books.length()) {
        val book = books.getJSONObject(i)
        val uri = book.getString("uri")
        require(uri.length <= 8192 && URI(uri).scheme == "content" && uris.add(uri)) {
            "El respaldo contiene una referencia de archivo inválida o duplicada."
        }
        val status = book.optString("status")
        require(status.isBlank() || status in setOf("PENDING", "READING", "READ")) { "Estado de lectura inválido." }
        require(book.optInt("readerPercent") in 0..100) { "Porcentaje de lectura inválido." }
        for (field in listOf("readerItem", "readerOffset", "readerPage", "readerChar"))
            require(book.optLong(field) in 0..100_000_000L) { "Posición de lectura inválida." }
        val highlights = book.optString("readerHighlights", "[]")
        checkJsonDepth(highlights)
        val marks = JSONArray(highlights)
        require(marks.length() <= 2000) { "Demasiadas anotaciones en un libro." }
        for (j in 0 until marks.length()) {
            val mark = marks.getJSONObject(j)
            require(mark.getInt("paragraph") >= 0 && mark.getInt("start") >= 0 &&
                mark.getInt("end") >= mark.getInt("start")) { "Anotación inválida." }
        }
        val image = book.optString("coverBase64")
        if (image.isNotBlank()) decodeBackupCover(image)
    }
    backup.optJSONArray("sections")?.let { sections ->
        require(sections.length() <= 1000)
        for (i in 0 until sections.length()) require(sections.get(i) is String)
    }
    backup.optJSONArray("wishList")?.let { wishes ->
        for (i in 0 until wishes.length()) {
            val item = wishes.getJSONObject(i)
            val url = URI(item.getString("url"))
            require(url.scheme == "https" && url.host in setOf("goodreads.com", "www.goodreads.com")) {
                "Enlace de Goodreads inválido."
            }
        }
    }
    return backup
}
