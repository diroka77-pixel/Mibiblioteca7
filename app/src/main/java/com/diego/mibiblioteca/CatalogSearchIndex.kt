package com.diego.mibiblioteca

import android.content.Context
import org.json.JSONObject

internal data class CatalogSearchDocument(
    val fingerprint: Int,
    val normalizedText: String
)

private fun searchDocument(fields: List<String>): CatalogSearchDocument {
    val source = fields.joinToString("\u0000")
    return CatalogSearchDocument(
        fingerprint = source.hashCode(),
        normalizedText = fields.asSequence()
            .map(::normalizeSearchText)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\u0001")
    )
}

internal fun normalizedSearchDocument(book: Book): CatalogSearchDocument =
    searchDocument(searchableBookFields(book))

private fun searchTerms(query: String): List<String> =
    normalizeSearchText(query).split(" ").filter { it.length >= 2 }

private fun matchesSearchTerms(normalizedText: String, terms: List<String>): Boolean =
    terms.isEmpty() || terms.all(normalizedText::contains)

internal fun matchesSearchDocument(normalizedText: String, query: String): Boolean =
    matchesSearchTerms(normalizedText, searchTerms(query))

/**
 * Índice pequeño y persistente. Solo se reconstruye la entrada cuyo título,
 * autor, saga o nombre de archivo haya cambiado.
 */
internal class PersistentCatalogSearchIndex(context: Context) {
    private val preferences = context.getSharedPreferences("catalog_search_index", Context.MODE_PRIVATE)
    private val documents = LinkedHashMap<String, CatalogSearchDocument>()
    private var indexedBooksReference: List<Book>? = null
    private var indexedUris: Set<String> = emptySet()
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
        if (indexedBooksReference !== books) rebuildChangedEntries(books)
        val terms = searchTerms(query)
        if (terms.isEmpty()) return indexedUris.toCollection(linkedSetOf())
        return documents.asSequence()
            .filter { (uri, document) ->
                uri in indexedUris && matchesSearchTerms(document.normalizedText, terms)
            }
            .mapTo(linkedSetOf()) { it.key }
    }

    private fun rebuildChangedEntries(books: List<Book>) {
        var changed = false
        val validKeys = HashSet<String>(books.size)
        books.forEach { book ->
            val key = book.uri.toString()
            validKeys += key
            val fields = searchableBookFields(book)
            val fingerprint = fields.joinToString("\u0000").hashCode()
            val cached = documents[key]
            if (cached?.fingerprint != fingerprint || cached.normalizedText.isBlank()) {
                documents[key] = searchDocument(fields)
                changed = true
            }
        }
        if (documents.keys.removeAll { it !in validKeys }) changed = true
        indexedUris = validKeys
        indexedBooksReference = books
        if (changed) persist()
    }

    @Synchronized
    fun invalidate(uri: String? = null) {
        loadIfNeeded()
        if (uri == null) {
            documents.clear()
            indexedUris = emptySet()
            indexedBooksReference = null
        } else {
            documents.remove(uri)
            indexedUris = indexedUris - uri
            indexedBooksReference = null
        }
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
