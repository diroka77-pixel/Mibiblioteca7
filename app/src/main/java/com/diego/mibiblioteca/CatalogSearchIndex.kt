package com.diego.mibiblioteca

import android.content.Context
import org.json.JSONObject

internal data class CatalogSearchDocument(
    val fingerprint: Int,
    val normalizedText: String
)

internal fun normalizedSearchDocument(book: Book): CatalogSearchDocument {
    val source = searchableBookFields(book).joinToString("\u0000")
    return CatalogSearchDocument(
        fingerprint = source.hashCode(),
        normalizedText = searchableBookFields(book)
            .asSequence()
            .map(::normalizeSearchText)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\u0001")
    )
}

internal fun matchesSearchDocument(normalizedText: String, query: String): Boolean {
    val terms = normalizeSearchText(query).split(" ").filter { it.length >= 2 }
    return terms.isEmpty() || terms.all(normalizedText::contains)
}

/**
 * Índice pequeño y persistente. Solo se reconstruye la entrada cuyo título,
 * autor, saga o nombre de archivo haya cambiado.
 */
internal class PersistentCatalogSearchIndex(context: Context) {
    private val preferences = context.getSharedPreferences("catalog_search_index", Context.MODE_PRIVATE)
    private val documents = LinkedHashMap<String, CatalogSearchDocument>()
    private var loaded = false

    @Synchronized
    private fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        val root = runCatching {
            JSONObject(preferences.getString("documents", "{}") ?: "{}")
        }.getOrElse { JSONObject() }
        root.keys().forEach { key ->
            root.optJSONObject(key)?.let { value ->
                documents[key] = CatalogSearchDocument(
                    fingerprint = value.optInt("fingerprint"),
                    normalizedText = value.optString("text")
                )
            }
        }
    }

    @Synchronized
    fun matchingBookUris(books: List<Book>, query: String): Set<String> {
        loadIfNeeded()
        if (normalizeSearchText(query).isBlank()) return books.mapTo(linkedSetOf()) { it.uri.toString() }

        var changed = false
        val validKeys = HashSet<String>(books.size)
        val matches = LinkedHashSet<String>()
        books.forEach { book ->
            val key = book.uri.toString()
            validKeys += key
            val current = normalizedSearchDocument(book)
            val cached = documents[key]
            val document = if (cached?.fingerprint == current.fingerprint &&
                cached.normalizedText.isNotBlank()) cached else current.also {
                documents[key] = it
                changed = true
            }
            if (matchesSearchDocument(document.normalizedText, query)) matches += key
        }
        if (documents.keys.removeAll { it !in validKeys }) changed = true
        if (changed) persist()
        return matches
    }

    @Synchronized
    fun invalidate(uri: String? = null) {
        loadIfNeeded()
        if (uri == null) documents.clear() else documents.remove(uri)
        persist()
    }

    private fun persist() {
        val root = JSONObject()
        documents.forEach { (key, value) ->
            root.put(key, JSONObject()
                .put("fingerprint", value.fingerprint)
                .put("text", value.normalizedText))
        }
        preferences.edit().putString("documents", root.toString()).apply()
    }
}
