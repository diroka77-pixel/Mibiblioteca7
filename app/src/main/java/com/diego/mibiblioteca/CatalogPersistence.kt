package com.diego.mibiblioteca

import org.json.JSONArray
import org.json.JSONObject

/** Serialización única del catálogo local y de la copia de seguridad. */
internal fun booksToJsonArray(books: List<Book>): JSONArray {
    val array = JSONArray()
    books.forEach { book ->
        array.put(JSONObject()
            .put("uri", book.uri.toString())
            .put("title", book.title)
            .put("author", book.author)
            .put("date", book.date)
            .put("publisher", book.publisher)
            .put("genre", book.genre)
            .put("description", book.description)
            .put("isbn", book.isbn)
            .put("saga", book.saga)
            .put("language", book.language)
            .put("section", book.section)
            .put("sections", JSONArray(bookSections(book)))
            .put("notes", book.notes)
            .put("goodreadsUrl", book.goodreadsUrl)
            .put("wantToRead", book.wantToRead)
            .put("customTitle", book.customTitle)
            .put("customAuthor", book.customAuthor)
            .put("sagaOrder", book.sagaOrder)
            .put("favorite", book.favorite)
            .put("status", book.status.name)
            .put("sourceSize", book.sourceSize)
            .put("sourceModified", book.sourceModified)
            .put("metadataRevision", book.metadataRevision)
            .put("sourceCoverChecked", book.sourceCoverChecked)
            .put("hadEmbeddedCover", book.hadEmbeddedCover)
            .put("plotSource", book.plotSource)
            .put("bioSource", book.bioSource)
            .put("coverSource", book.coverSource)
            .put("rating", book.rating)
            .put("startedDate", book.startedDate)
            .put("finishedDate", book.finishedDate)
            .put("quotes", book.quotes)
            .put("tags", JSONArray(book.tags))
            .put("loanTo", book.loanTo)
            .put("loanDate", book.loanDate)
            .put("rereadCount", book.rereadCount))
    }
    return array
}
