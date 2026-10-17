package com.diego.mibiblioteca

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal data class SyncManifestEntry(
    val uri: String,
    val size: Long,
    val modified: Long,
    val identityRevision: Int
)

internal data class LibrarySyncManifest(
    val generatedAt: Long,
    val entries: List<SyncManifestEntry>,
    val checksum: String
) {
    fun toJson(): String {
        val rows = JSONArray()
        entries.forEach { entry ->
            rows.put(JSONObject()
                .put("uri", entry.uri)
                .put("size", entry.size)
                .put("modified", entry.modified)
                .put("revision", entry.identityRevision))
        }
        return JSONObject()
            .put("version", VERSION)
            .put("generatedAt", generatedAt)
            .put("entries", rows)
            .put("checksum", checksum)
            .toString()
    }

    fun isValid(): Boolean = checksum == digest(entries)

    companion object {
        private const val VERSION = 1

        fun from(books: List<Book>): LibrarySyncManifest {
            val entries = books.map {
                SyncManifestEntry(it.uri.toString(), it.sourceSize, it.sourceModified, it.metadataRevision)
            }.sortedBy(SyncManifestEntry::uri)
            return LibrarySyncManifest(System.currentTimeMillis(), entries, digest(entries))
        }

        fun parse(raw: String?): LibrarySyncManifest? = runCatching {
            val json = JSONObject(raw ?: return null)
            if (json.optInt("version") != VERSION) return null
            val rows = json.optJSONArray("entries") ?: return null
            val entries = (0 until rows.length()).mapNotNull { index ->
                rows.optJSONObject(index)?.let {
                    SyncManifestEntry(
                        it.optString("uri"),
                        it.optLong("size", -1L),
                        it.optLong("modified", -1L),
                        it.optInt("revision")
                    )
                }?.takeIf { it.uri.isNotBlank() }
            }.sortedBy(SyncManifestEntry::uri)
            LibrarySyncManifest(
                json.optLong("generatedAt"),
                entries,
                json.optString("checksum")
            ).takeIf(LibrarySyncManifest::isValid)
        }.getOrNull()

        private fun digest(entries: List<SyncManifestEntry>): String {
            val canonical = entries.joinToString("\n") {
                it.uri + "|" + it.size + "|" + it.modified + "|" + it.identityRevision
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}
