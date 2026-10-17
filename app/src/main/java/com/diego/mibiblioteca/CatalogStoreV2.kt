package com.diego.mibiblioteca

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Catálogo transaccional e incremental. Conserva la tabla anterior y añade una
 * huella por ficha para escribir únicamente los libros que realmente cambian.
 */
internal class CatalogStoreV2(context: Context) :
    SQLiteOpenHelper(context, "catalog_v2.db", null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE books (
                uri TEXT PRIMARY KEY NOT NULL,
                content TEXT NOT NULL,
                fingerprint TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_books_updated ON books(updated_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Las siguientes versiones se migrarán de forma acumulativa.
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE books ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE books ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0") }
            runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_updated ON books(updated_at)") }
        }
    }

    fun readArray(): JSONArray {
        val result = JSONArray()
        readableDatabase.rawQuery("SELECT content FROM books ORDER BY rowid", null).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { result.put(JSONObject(cursor.getString(0))) }
            }
        }
        return result
    }

    fun isEmpty(): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM books LIMIT 1", null
    ).use { !it.moveToFirst() }

    /**
     * Aplica una fotografía del catálogo sin borrar la tabla. Una transacción
     * garantiza que un cierre o error nunca deje media biblioteca guardada.
     */
    fun applySnapshot(array: JSONArray): SnapshotResult {
        val incoming = LinkedHashMap<String, String>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val uri = row.optString("uri")
            if (uri.isNotBlank()) incoming[uri] = row.toString()
        }
        val existing = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT uri, fingerprint FROM books", null).use { cursor ->
            while (cursor.moveToNext()) existing[cursor.getString(0)] = cursor.getString(1)
        }

        var inserted = 0
        var updated = 0
        var removed = 0
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            incoming.forEach { (uri, content) ->
                val fingerprint = sha256(content)
                when {
                    existing[uri] == null -> {
                        db.insertOrThrow("books", null, values(uri, content, fingerprint, now))
                        inserted++
                    }
                    existing[uri] != fingerprint -> {
                        db.update("books", values(uri, content, fingerprint, now),
                            "uri = ?", arrayOf(uri))
                        updated++
                    }
                }
            }
            val stale = existing.keys - incoming.keys
            stale.chunked(300).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }
                removed += db.delete("books", "uri IN ($placeholders)", batch.toTypedArray())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return SnapshotResult(inserted, updated, removed)
    }

    private fun values(uri: String, content: String, fingerprint: String, updated: Long) =
        ContentValues().apply {
            put("uri", uri)
            put("content", content)
            put("fingerprint", fingerprint)
            put("updated_at", updated)
        }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    data class SnapshotResult(val inserted: Int, val updated: Int, val removed: Int)

    companion object { private const val VERSION = 2 }
}
