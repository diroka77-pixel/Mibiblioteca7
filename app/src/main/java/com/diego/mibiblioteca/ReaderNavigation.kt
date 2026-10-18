package com.diego.mibiblioteca

internal data class ReaderChapterNavigation(
    val paragraphIndex: Int,
    val startChar: Int,
    val title: String
)

internal fun readerChapters(paragraphs: List<ReadingParagraph>): List<ReaderChapterNavigation> {
    var cursor = 0
    return buildList {
        paragraphs.forEachIndexed { index, paragraph ->
            if (paragraph.chapterStart || paragraph.heading) {
                val title = paragraph.text.trim().replace(Regex("\\s+"), " ").take(90)
                if (title.isNotBlank() && none { it.startChar == cursor })
                    add(ReaderChapterNavigation(index, cursor, title))
            }
            cursor += paragraph.text.length
        }
        if (isEmpty() && paragraphs.isNotEmpty())
            add(ReaderChapterNavigation(0, 0, "Inicio"))
    }
}

internal fun readerChapterIndexAt(
    chapters: List<ReaderChapterNavigation>,
    charPosition: Int
): Int = chapters.indexOfLast { it.startChar <= charPosition }.coerceAtLeast(0)

internal fun readerPageForChar(pageStarts: List<Int>, charPosition: Int): Int =
    pageStarts.indexOfLast { it <= charPosition }.coerceAtLeast(0)

internal fun readerProgressPercent(charPosition: Int, totalChars: Int): Int =
    if (totalChars <= 0) 0 else (charPosition.coerceIn(0, totalChars) * 100 / totalChars).coerceIn(0, 100)
