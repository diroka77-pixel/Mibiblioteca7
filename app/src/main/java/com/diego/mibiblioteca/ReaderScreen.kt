package com.diego.mibiblioteca

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.app.Activity
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

internal sealed interface ReadingDocument {
    data class TextDocument(val paragraphs: List<ReadingParagraph>) : ReadingDocument
    data class PdfDocument(val file: File, val pages: Int) : ReadingDocument
    data class Unsupported(val reason: String) : ReadingDocument
}

internal data class ReadingParagraph(val text: String, val heading: Boolean = false,
    val chapterStart: Boolean = false, val partHeading: Boolean = false)
private data class ReaderHighlight(val paragraph: Int, val start: Int, val end: Int,
    val quote: String, val color: String = "amarillo", val note: String = "")
private data class ReadingSlice(val paragraph: Int, val start: Int, val end: Int, val height: Int)
private data class ReadingPage(val slices: List<ReadingSlice>, val startChar: Int)
private data class SpeechPage(val text: String, val sourcePositions: IntArray)

private fun speechPage(page: ReadingPage, paragraphs: List<ReadingParagraph>, positions: IntArray): SpeechPage {
    val text = StringBuilder()
    val sourcePositions = ArrayList<Int>()
    page.slices.forEachIndexed { index, slice ->
        if (index > 0) {
            text.append(' ')
            sourcePositions += positions[slice.paragraph] + slice.start
        }
        val part = paragraphs[slice.paragraph].text.substring(slice.start, slice.end)
        part.forEachIndexed { offset, char ->
            text.append(char)
            sourcePositions += positions[slice.paragraph] + slice.start + offset
        }
    }
    return SpeechPage(text.toString(), sourcePositions.toIntArray())
}

private fun speechOffsetForChar(page: SpeechPage, charPosition: Int): Int {
    val found = page.sourcePositions.indexOfFirst { it >= charPosition }
    return if (found < 0) page.text.length else found
}

private fun speechCharForOffset(page: SpeechPage, offset: Int, total: Int): Int =
    page.sourcePositions.getOrNull(offset.coerceIn(0, (page.sourcePositions.size - 1).coerceAtLeast(0)))
        ?.coerceIn(0, (total - 1).coerceAtLeast(0)) ?: 0
private fun openingParagraph(paragraphs: List<ReadingParagraph>, index: Int): Boolean =
    !paragraphs[index].heading && (index == 0 || paragraphs[index - 1].heading)

private suspend fun paginateText(paragraphs: List<ReadingParagraph>, width: Int, height: Int,
    textSizePx: Float, gapPx: Int): List<ReadingPage> {
    val pages = mutableListOf<ReadingPage>()
    val slices = mutableListOf<ReadingSlice>()
    var occupied = 0
    val positions = IntArray(paragraphs.size + 1)
    paragraphs.forEachIndexed { i, p -> positions[i + 1] = positions[i] + p.text.length }
    fun finish() {
        if (slices.isNotEmpty()) {
            pages += ReadingPage(slices.toList(), positions[slices.first().paragraph] +
                slices.first().start)
            slices.clear(); occupied = 0
        }
    }
    val workerContext = kotlinx.coroutines.currentCoroutineContext()
    paragraphs.forEachIndexed { index, paragraph ->
        workerContext.ensureActive()
        if (paragraph.chapterStart) {
            finish()
            occupied = (gapPx * 3).coerceAtMost(height / 4)
        }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx + when {
                paragraph.partHeading -> 8 * textSizePx / 18f
                paragraph.heading -> 4 * textSizePx / 18f
                else -> 0f
            }
            typeface = Typeface.create(Typeface.SERIF,
                if (paragraph.heading) Typeface.BOLD else Typeface.NORMAL)
        }
        val measured = SpannableString(paragraph.text)
        if (openingParagraph(paragraphs, index) && measured.isNotEmpty()) {
            measured.setSpan(RelativeSizeSpan(1.9f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            measured.setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val layout = StaticLayout.Builder.obtain(measured, 0, measured.length,
            paint, width.coerceAtLeast(1)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setJustificationMode(if (paragraph.heading) Layout.JUSTIFICATION_MODE_NONE
                else Layout.JUSTIFICATION_MODE_INTER_WORD)
            .setLineSpacing(0f, 1.45f).setIncludePad(false).build()
        var line = 0
        while (line < layout.lineCount) {
            if (slices.isNotEmpty() && occupied + gapPx +
                layout.getLineBottom(line) - layout.getLineTop(line) > height) finish()
            val first = line
            var used = 0
            val room = height - occupied - if (slices.isEmpty()) 0 else gapPx
            while (line < layout.lineCount) {
                val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
                if (used + lineHeight > room && line > first) break
                used += lineHeight; line++
                if (used >= room) break
            }
            val start = layout.getLineStart(first)
            val end = if (line == layout.lineCount) paragraph.text.length else layout.getLineStart(line)
            if (slices.isNotEmpty()) occupied += gapPx
            slices += ReadingSlice(index, start, end, used)
            occupied += used
            if (line < layout.lineCount) finish()
        }
    }
    finish()
    return pages.ifEmpty { listOf(ReadingPage(emptyList(), 0)) }
}

private fun readHighlights(raw: String?): List<ReaderHighlight> = try {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { j ->
        ReaderHighlight(j.optInt("paragraph"), j.optInt("start"), j.optInt("end"), j.optString("quote"),
            j.optString("color", "amarillo"), j.optString("note"))
    } }
} catch (_: Exception) { emptyList() }

private fun saveHighlights(context: Context, key: String, highlights: List<ReaderHighlight>) {
    val array = JSONArray()
    highlights.forEach { h -> array.put(JSONObject().put("paragraph", h.paragraph)
        .put("start", h.start).put("end", h.end).put("quote", h.quote)
        .put("color", h.color).put("note", h.note)) }
    context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit()
        .putString("highlights_$key", array.toString()).apply()
}

private fun dictionaryDefinitions(word: String): List<String> = try {
    val url = "https://es.wiktionary.org/w/api.php?action=parse&format=json&prop=text&page=" +
        java.net.URLEncoder.encode(word, "UTF-8")
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 7000
    connection.readTimeout = 9000
    connection.setRequestProperty("User-Agent", "MiBiblioteca/0.47 (lector; contacto: GitHub diroka77-pixel)")
    val html = try {
        connection.inputStream.use { stream ->
            JSONObject(stream.bufferedReader().readText()).getJSONObject("parse")
                .getJSONObject("text").getString("*")
        }
    } finally { connection.disconnect() }
    Jsoup.parse(html).select("ol > li").map { it.text().trim() }
        .filter { it.length in 8..400 }.distinct().take(4)
} catch (_: Exception) { emptyList() }

private suspend fun shareReadingFile(context: Context, book: Book) {
    val file = withContext(Dispatchers.IO) { localReaderFile(context, book) }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, context.packageName + ".fileprovider", file)
    val mime = when (file.extension.lowercase()) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        "html", "htm" -> "text/html"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Libro", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Compartir libro"))
}

internal fun readerProgressKey(uri: Uri): String = MessageDigest.getInstance("SHA-256")
    .digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

private val readerFileLock = Any()

internal fun localReaderFile(context: Context, book: Book): File = synchronized(readerFileLock) {
    val name = (if (book.uri.scheme == "file") book.uri.lastPathSegment
        else DocumentFile.fromSingleUri(context, book.uri)?.name)
        ?: book.uri.lastPathSegment.orEmpty().substringAfterLast('/').substringAfterLast(':')
    val extension = name.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }.orEmpty().ifBlank {
        when (context.contentResolver.getType(book.uri)) {
            "application/pdf" -> "pdf"
            "application/epub+zip" -> "epub"
            "text/plain" -> "txt"
            else -> "epub"
        }
    }
    val folder = File(context.cacheDir, "reader").apply { mkdirs() }
    val file = File(folder, "${readerProgressKey(book.uri)}.$extension")
    val stamp = File(folder, "${readerProgressKey(book.uri)}.stamp")
    val fingerprint = "${book.sourceSize}:${book.sourceModified}"
    require(book.sourceSize <= FileLimits.BOOK_BYTES) { "El libro supera el límite de 256 MB." }
    if (file.exists() && file.length() in 1..FileLimits.BOOK_BYTES && stamp.takeIf { it.exists() && it.length() < 1024 }?.readText() == fingerprint)
        return@synchronized file.also { it.setLastModified(System.currentTimeMillis()) }
    val partial = File(folder, "${readerProgressKey(book.uri)}.partial")
    try {
        context.contentResolver.openInputStream(book.uri)?.use { input ->
            require(folder.usableSpace > FileLimits.FREE_SPACE + book.sourceSize.coerceAtLeast(0)) {
                "No hay espacio suficiente para abrir el libro."
            }
            partial.outputStream().use { output ->
                input.copyLimited(output, FileLimits.BOOK_BYTES) {
                    require(folder.usableSpace > FileLimits.FREE_SPACE) { "No queda espacio suficiente en el teléfono." }
                }
            }
        } ?: error("No se pudo leer el archivo de Drive")
        require(partial.length() > 0) { "El archivo está vacío" }
        if (file.exists()) file.delete()
        check(partial.renameTo(file)) { "No se pudo guardar el archivo temporal" }
        stamp.writeText(fingerprint)
        val older = folder.listFiles()?.filter { it.extension !in listOf("stamp", "partial") && it != file }
            ?.sortedBy { it.lastModified() }.orEmpty()
        var total = folder.listFiles()?.filter { it.extension !in listOf("stamp", "partial") }
            ?.sumOf(File::length) ?: 0L
        for (old in older) {
            if (total <= 500L * 1024 * 1024) break
            total -= old.length(); old.delete()
            File(folder, old.nameWithoutExtension + ".stamp").delete()
        }
        return@synchronized file
    } finally { partial.delete() }
}

private fun resolveZipPath(base: String, path: String): String {
    val parts = mutableListOf<String>()
    (base.substringBeforeLast('/', "") + "/" + path.substringBefore('#'))
        .replace('\\', '/').split('/').forEach { segment -> when (segment) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += segment
        } }
    return parts.joinToString("/")
}

private fun paragraphsFromHtml(html: String): List<ReadingParagraph> {
    val body = Jsoup.parse(html).body()
    val nodes = body.select("h1,h2,h3,h4,p,li,blockquote,pre")
    val result = nodes.mapNotNull { element ->
        val text = element.text().trim().replace(Regex("\\s+"), " ")
        text.takeIf(String::isNotBlank)?.let {
            val part = it.length < 85 && Regex("(?i)^(?:parte|part|libro|book)\\s+[ivxlcdm0-9]+\\b")
                .containsMatchIn(it)
            val namedChapter = it.length < 85 &&
                Regex("(?i)^(?:cap[ií]tulo|chapter)\\s+[ivxlcdm0-9]+\\b")
                    .containsMatchIn(it)
            val heading = element.tagName().startsWith("h") || part || namedChapter
            val chapterHeading = element.tagName() == "h1" ||
                part || namedChapter ||
                (element.tagName() == "h2" &&
                    Regex("(?i)cap[ií]tulo|chapter|parte|pr[oó]logo|ep[ií]logo|introducci[oó]n")
                        .containsMatchIn(it))
            ReadingParagraph(it, heading, chapterStart = chapterHeading, partHeading = part)
        }
    }
    return result.ifEmpty { listOfNotNull(body.text().trim().takeIf(String::isNotBlank)?.let(::ReadingParagraph)) }
}

internal fun readEpubText(file: File): ReadingDocument = ZipFile(file).use { zip ->
    val container = zip.readEntryLimited("META-INF/container.xml", FileLimits.INDEX_BYTES)?.toString(Charsets.UTF_8)
        ?: return@use ReadingDocument.Unsupported("El EPUB no contiene su índice principal")
    val opf = Regex("full-path\\s*=\\s*['\"]([^'\"]+)['\"]")
        .find(container)?.groupValues?.get(1)
        ?: return@use ReadingDocument.Unsupported("No se encontró el contenido del EPUB")
    val packageXml = zip.readEntryLimited(opf, FileLimits.INDEX_BYTES)?.toString(Charsets.UTF_8)
        ?: return@use ReadingDocument.Unsupported("No se pudo leer el contenido del EPUB")
    val xml = Jsoup.parse(packageXml, "", Parser.xmlParser())
    val manifest = xml.select("manifest > item").associate { it.attr("id") to it.attr("href") }
    val spine = xml.select("spine > itemref").mapNotNull { manifest[it.attr("idref")] }
    val paths = spine.ifEmpty { manifest.values.filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) } }
    require(paths.size <= FileLimits.CHAPTERS) { "El EPUB contiene demasiados capítulos." }
    var totalBytes = 0L
    val paragraphs = mutableListOf<ReadingParagraph>()
    for (href in paths) {
        val path = resolveZipPath(opf, java.net.URLDecoder.decode(href, "UTF-8"))
        val bytes = zip.readEntryLimited(path, minOf(FileLimits.CHAPTER_BYTES, FileLimits.TEXT_BYTES - totalBytes)) ?: continue
        totalBytes += bytes.size
        val html = bytes.toString(Charsets.UTF_8)
        val section = paragraphsFromHtml(html)
        if (section.isEmpty()) continue
        if (section.none(ReadingParagraph::heading)) {
            val label = Jsoup.parse(html).title().trim()
                .takeIf { it.isNotBlank() && !it.equals("untitled", true) }
                ?: "Capítulo ${paragraphs.count(ReadingParagraph::heading) + 1}"
            paragraphs += ReadingParagraph(label.take(90), heading = true, chapterStart = true)
            paragraphs += section
        } else {
            paragraphs += section.first().copy(chapterStart = true)
            paragraphs += section.drop(1)
        }
    }
    if (paragraphs.isEmpty()) ReadingDocument.Unsupported("No se encontró texto legible en este EPUB")
    else ReadingDocument.TextDocument(paragraphs)
}

private fun readMobiText(file: File): ReadingDocument {
    if (file.length() > 30_000_000) return ReadingDocument.Unsupported("MOBI demasiado grande para abrirlo aquí")
    val bytes = file.readBytes()
    if (bytes.size < 100) return ReadingDocument.Unsupported("El MOBI está incompleto")
    fun number(at: Int, count: Int): Int {
        if (at < 0 || at + count > bytes.size) error("MOBI incompleto")
        var value = 0
        repeat(count) { value = (value shl 8) or (bytes[at + it].toInt() and 255) }
        return value
    }
    val count = number(76, 2)
    if (count !in 2..20000 || 78L + count * 8L > bytes.size)
        return ReadingDocument.Unsupported("Índice MOBI no compatible")
    val offsets = (0 until count).map { number(78 + it * 8, 4) }
    if (offsets.zipWithNext().any { it.first >= it.second } || offsets.last() >= bytes.size)
        return ReadingDocument.Unsupported("Registros MOBI dañados")
    val compression = number(offsets[0], 2)
    if (compression !in listOf(1, 2)) return ReadingDocument.Unsupported(
        "Este MOBI usa una compresión Kindle que necesita conversión a EPUB")
    val textLength = number(offsets[0] + 4, 4).coerceAtMost(20_000_000)
    val textRecords = number(offsets[0] + 8, 2).coerceAtMost(count - 1)
    val output = ByteArray(textLength)
    var written = 0
    fun append(value: Int) { if (written < output.size) output[written++] = value.toByte() }
    for (record in 1..textRecords) {
        val start = offsets[record]
        val end = if (record + 1 < count) offsets[record + 1] else bytes.size
        var i = start
        while (i < end && written < textLength) {
            val code = bytes[i++].toInt() and 255
            when {
                compression == 1 -> append(code)
                code == 0 || code in 9..127 -> append(code)
                code in 1..8 -> repeat(minOf(code, end - i)) { append(bytes[i++].toInt()) }
                code in 128..191 && i < end -> {
                    val pair = ((code and 63) shl 8) or (bytes[i++].toInt() and 255)
                    val distance = pair shr 3
                    val length = (pair and 7) + 3
                    if (distance > 0 && distance <= written) repeat(length) { append(output[written - distance].toInt()) }
                }
                code >= 192 -> { append(32); append(code xor 128) }
            }
        }
    }
    val encoding = if (offsets[0] + 48 < bytes.size && number(offsets[0] + 44, 4) == 65001)
        Charsets.UTF_8 else charset("windows-1252")
    val html = output.copyOf(written).toString(encoding)
    val paragraphs = paragraphsFromHtml(html)
    return if (paragraphs.isEmpty()) ReadingDocument.Unsupported("Este MOBI no contiene texto legible")
    else ReadingDocument.TextDocument(paragraphs)
}

private fun loadReadingDocument(context: Context, book: Book): ReadingDocument {
    val file = localReaderFile(context, book)
    return when (file.extension.lowercase()) {
        "epub" -> readEpubText(file)
        "pdf" -> {
            val pages = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { it.pageCount }
            }
            if (pages > 0) ReadingDocument.PdfDocument(file, pages)
            else ReadingDocument.Unsupported("El PDF no tiene páginas")
        }
        "mobi", "azw" -> readMobiText(file)
        "azw3" -> ReadingDocument.Unsupported("AZW3 requiere conversión a EPUB para este lector")
        "txt", "md", "html", "htm", "rtf", "docx" -> {
            val paragraphs = when (file.extension.lowercase()) {
                "docx" -> ZipFile(file).use { zip ->
                    zip.getEntry("word/document.xml")?.let { entry ->
                        val xml = zip.readEntryLimited(entry.name, FileLimits.TEXT_BYTES)!!.toString(Charsets.UTF_8)
                        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                        doc.select("w|p").mapNotNull { p -> p.text().trim().takeIf(String::isNotBlank)
                            ?.let { text -> ReadingParagraph(text,
                                p.selectFirst("w|pStyle")?.attr("w:val")?.startsWith("Heading", true) == true) } }
                    }.orEmpty()
                }
                "html", "htm" -> paragraphsFromHtml(file.inputStream().use { it.readLimited(FileLimits.TEXT_BYTES).toString(Charsets.UTF_8) })
                "rtf" -> file.inputStream().use { it.readLimited(FileLimits.TEXT_BYTES).toString(Charsets.UTF_8) }.replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ")
                    .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), " ")
                    .replace(Regex("[{}]"), " ").split(Regex("\\n+"))
                    .mapNotNull { it.trim().takeIf(String::isNotBlank)?.let(::ReadingParagraph) }
                else -> file.inputStream().use { it.readLimited(FileLimits.TEXT_BYTES).toString(Charsets.UTF_8) }.split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
                    .mapNotNull { raw -> raw.trim().takeIf(String::isNotBlank)?.let { text ->
                        ReadingParagraph(text.trimStart('#', ' '),
                            file.extension.equals("md", true) && text.startsWith('#'))
                    } }
            }
            if (paragraphs.isEmpty()) ReadingDocument.Unsupported("No se encontró texto legible")
            else ReadingDocument.TextDocument(paragraphs)
        }
        else -> ReadingDocument.Unsupported("Formato no compatible con el lector")
    }
}

private fun renderPdfPage(file: File, index: Int): Bitmap =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(index).use { page ->
                val factor = minOf(1500f / page.width, 2100f / page.height, 2f)
                val bitmap = Bitmap.createBitmap((page.width * factor).toInt().coerceAtLeast(1),
                    (page.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

@Composable
private fun SelectableParagraph(
    paragraph: ReadingParagraph, index: Int, start: Int, end: Int, size: Float, foreground: Color,
    dark: Boolean, opening: Boolean, highlights: List<ReaderHighlight>,
    onHighlight: (ReaderHighlight) -> Unit, onNote: (ReaderHighlight) -> Unit,
    onLookup: (String) -> Unit, onTranslate: (String) -> Unit, onSpeak: (String) -> Unit,
    onSearch: (String) -> Unit, onSaveToNotebook: (String) -> Unit,
    onBookmarkChar: (Int, Int) -> Unit
) {
    val action by rememberUpdatedState(onHighlight)
    val noteAction by rememberUpdatedState(onNote)
    val lookupAction by rememberUpdatedState(onLookup)
    val translateAction by rememberUpdatedState(onTranslate)
    val speakAction by rememberUpdatedState(onSpeak)
    val searchAction by rememberUpdatedState(onSearch)
    val notebookAction by rememberUpdatedState(onSaveToNotebook)
    val bookmarkAction by rememberUpdatedState(onBookmarkChar)
    AndroidView(modifier = Modifier.fillMaxWidth(), factory = { context ->
        TextView(context).apply {
            setTextIsSelectable(true)
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            val selectable = this
            setCustomSelectionActionModeCallback(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(0, 8001, 0, "Subrayar amarillo")
                    menu.add(0, 8004, 1, "Subrayar azul")
                    menu.add(0, 8005, 2, "Nota")
                    menu.add(0, 8010, 3, "Copiar")
                    menu.add(0, 8011, 4, "Fijar página")
                    menu.add(0, 8002, 5, "Diccionario")
                    menu.add(0, 8003, 6, "Compartir")
                    menu.add(0, 8006, 7, "Traducir")
                    menu.add(0, 8007, 8, "Escuchar")
                    menu.add(0, 8008, 9, "Buscar en el libro")
                    menu.add(0, 8009, 10, "Guardar en libreta")
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                override fun onDestroyActionMode(mode: ActionMode) = Unit
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (item.itemId !in 8001..8011) return false
                    val selectedStart = selectable.selectionStart.coerceAtLeast(0)
                    val selectedEnd = selectable.selectionEnd.coerceAtMost(selectable.text.length)
                    if (selectedEnd <= selectedStart) return false
                    val quote = selectable.text.subSequence(selectedStart, selectedEnd).toString().trim()
                    if (quote.isBlank()) return false
                    when (item.itemId) {
                        8001, 8004 -> action(ReaderHighlight(index, start + selectedStart,
                            start + selectedEnd, quote, if (item.itemId == 8004) "azul" else "amarillo"))
                        8005 -> noteAction(ReaderHighlight(index, start + selectedStart,
                            start + selectedEnd, quote))
                        8002 -> {
                            val word = quote.split(Regex("\\s+")).first().trim('¿', '¡', '.', ',', ';',
                                ':', '!', '?', '«', '»', '"', '\'')
                            if (word.isNotBlank()) lookupAction(word)
                        }
                        8003 -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, quote)
                        }, "Compartir cita"))
                        8006 -> translateAction(quote)
                        8007 -> speakAction(quote)
                        8008 -> searchAction(quote)
                        8009 -> notebookAction("«$quote»")
                        8010 -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Texto del libro", quote))
                        }
                        8011 -> bookmarkAction(index, start + selectedStart)
                    }
                    mode.finish()
                    return true
                }
            })
        }
    }, update = { view ->
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size + when {
            paragraph.partHeading -> 8f
            paragraph.heading -> 4f
            else -> 0f
        })
        view.typeface = Typeface.create(Typeface.SERIF,
            if (paragraph.heading) Typeface.BOLD else Typeface.NORMAL)
        view.setLineSpacing(0f, 1.45f)
        view.justificationMode = if (paragraph.heading) Layout.JUSTIFICATION_MODE_NONE
            else Layout.JUSTIFICATION_MODE_INTER_WORD
        view.textAlignment = if (paragraph.heading) TextView.TEXT_ALIGNMENT_CENTER
            else TextView.TEXT_ALIGNMENT_INHERIT
        view.setTextColor(foreground.toArgb())
        val content = paragraph.text.substring(start, end)
        val relevant = highlights.filter { it.paragraph == index && it.end > start && it.start < end }
        val stamp = listOf(content, relevant, dark, opening)
        if (view.tag != stamp) {
            val styled = SpannableString(content)
            if (opening && start == 0 && styled.isNotEmpty()) {
                styled.setSpan(RelativeSizeSpan(1.9f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            relevant.forEach { h ->
                val from = (h.start - start).coerceIn(0, styled.length)
                val to = (h.end - start).coerceIn(0, styled.length)
                if (to > from)
                    styled.setSpan(BackgroundColorSpan(when {
                        h.color == "azul" && dark -> 0xFF285D69.toInt()
                        h.color == "azul" -> 0xFFACEEF5.toInt()
                        dark -> 0xFF80622D.toInt()
                        else -> 0xFFFFE39A.toInt()
                    }), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            view.text = styled
            view.tag = stamp
        }
    })
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(book: Book, onBack: () -> Unit, onProgress: (Int) -> Unit,
    onHighlightsChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("reader", Context.MODE_PRIVATE) }
    val key = remember(book.uri) { readerProgressKey(book.uri) }
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 18f)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var sepia by remember { mutableStateOf(prefs.getBoolean("sepia", false)) }
    var page by remember(book.uri) { mutableIntStateOf(prefs.getInt("page_$key", 0)) }
    val scope = rememberCoroutineScope()
    val swipeDistance = with(LocalDensity.current) { 64.dp.toPx() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var jumpToItem by remember(book.uri) { mutableIntStateOf(-1) }
    var showReaderTools by rememberSaveable(book.uri) { mutableStateOf(false) }
    var notebook by remember(book.uri) { mutableStateOf(prefs.getString("notebook_$key", "").orEmpty()) }
    var bookmarkChars by remember(book.uri) {
        mutableStateOf(prefs.getString("bookmarks_$key", "").orEmpty()
            .split(",").mapNotNull(String::toIntOrNull).distinct())
    }
    fun saveNotebookEntry(entry: String) {
        val clean = entry.trim()
        if (clean.isNotBlank()) {
            notebook = listOf(notebook.trim(), clean).filter(String::isNotBlank).joinToString("\n\n")
            prefs.edit().putString("notebook_$key", notebook).apply()
        }
    }
    fun toggleBookmark(charPosition: Int) {
        val position = charPosition.coerceAtLeast(0)
        bookmarkChars = if (position in bookmarkChars) bookmarkChars - position
            else bookmarkChars + position
        prefs.edit().putString("bookmarks_$key", bookmarkChars.joinToString(",")).apply()
    }
    var highlights by remember(book.uri) {
        mutableStateOf(readHighlights(prefs.getString("highlights_$key", "[]")))
    }
    var pendingNote by remember { mutableStateOf<ReaderHighlight?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var dictionaryWord by remember { mutableStateOf<String?>(null) }
    var translationQuote by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechReady by remember { mutableStateOf(false) }
    var audioActive by remember(book.uri) { mutableStateOf(false) }
    var audioControlsVisible by remember(book.uri) { mutableStateOf(false) }
    var audioCursor by remember(book.uri) {
        mutableIntStateOf(prefs.getInt("char_$key", 0))
    }
    val activeSpeechCursor = remember(book.uri) {
        AtomicInteger(prefs.getInt("char_$key", 0))
    }
    fun saveSpeechCursor(position: Int) {
        val safePosition = position.coerceAtLeast(0)
        activeSpeechCursor.set(safePosition)
        audioCursor = safePosition
        prefs.edit().putInt("char_$key", safePosition).apply()
    }
    fun pauseSpeech() {
        if (audioActive) saveSpeechCursor(activeSpeechCursor.get())
        audioActive = false
    }
    var audioSeekRequest by remember(book.uri) { mutableIntStateOf(0) }
    var audioSeekDelta by remember(book.uri) { mutableIntStateOf(0) }
    var audioAdvance by remember(book.uri) { mutableIntStateOf(0) }
    fun requestAudioSeek(delta: Int) {
        audioSeekDelta = delta
        audioSeekRequest++
    }
    var showVoicePicker by remember { mutableStateOf(false) }
    var davefxDownloading by remember { mutableStateOf(false) }
    var davefxReadyFile by remember { mutableStateOf<File?>(null) }
    var davefxProgress by remember { mutableIntStateOf(0) }
    var davefxError by remember { mutableStateOf<String?>(null) }
    var selectedVoiceName by remember { mutableStateOf(prefs.getString("voice_name", "").orEmpty()) }
    DisposableEffect(context, book.uri, selectedVoiceName) {
        var engine: TextToSpeech? = null
        speechReady = false
        engine = TextToSpeech(context, { status ->
            speechReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS && selectedVoiceName == "davefx")
                android.widget.Toast.makeText(context, "No se pudo iniciar Davefx. Revisa la instalación de la voz.",
                    android.widget.Toast.LENGTH_LONG).show()
        }, if (selectedVoiceName == "davefx") DAVEFX_ENGINE else null)
        speech = engine
        onDispose {
            audioActive = false
            audioControlsVisible = false
            engine?.stop()
            engine?.shutdown()
            speech = null
        }
    }
    var brightness by remember { mutableFloatStateOf(prefs.getFloat("brightness", -1f)) }
    val activity = context as? Activity
    DisposableEffect(activity) {
        val old = activity?.window?.attributes?.screenBrightness
        onDispose {
            if (old != null && activity != null) activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = old
            }
        }
    }
    LaunchedEffect(brightness) {
        if (activity != null)
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness }
    }
    val background = if (dark) Color(0xFF1D1A17) else if (sepia) Color(0xFFF5EEDD) else Color.White
    val foreground = if (dark) Color(0xFFF3E9D7) else Color(0xFF24201D)
    val state by produceState<ReadingDocument?>(null, book.uri) {
        value = try { withContext(Dispatchers.IO) { loadReadingDocument(context, book) } }
        catch (e: Exception) { ReadingDocument.Unsupported(e.localizedMessage ?: "No se pudo abrir") }
    }
    val document = state
    BackHandler { if (drawer.isOpen) scope.launch { drawer.close() } else onBack() }
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = false, drawerContent = {
        ModalDrawerSheet(
            modifier = Modifier.width(320.dp),
            drawerContainerColor = Color(0xFFF8F3E9),
            drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(
                topEnd = 24.dp, bottomEnd = 24.dp)
        ) {
            Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 22.dp)) {
                Text("Mi Biblioteca", color = Color(0xFF8A623C),
                    style = MaterialTheme.typography.labelLarge)
                Text(book.customTitle.ifBlank { book.title }.take(54),
                    color = Color(0xFF392A24), maxLines = 2,
                    style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(label = { Text("Volver a la biblioteca") },
                    selected = false, icon = { AppIcon("Volver", "Volver") },
                    onClick = { scope.launch { drawer.close() }; onBack() })
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("ESCUCHAR", color = Color(0xFF785940),
                    style = MaterialTheme.typography.labelMedium)
                if (document is ReadingDocument.TextDocument) {
                    NavigationDrawerItem(
                        label = { Text(if (audioActive) "Pausar lectura" else "Leer en voz alta") },
                        selected = audioActive,
                        icon = { AppIcon(if (audioActive) "Pausar" else "Reproducir") },
                        onClick = {
                            if (speechReady) {
                                audioControlsVisible = true
                                if (audioActive) pauseSpeech() else audioActive = true
                            }
                            scope.launch { drawer.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFFE7DCC8),
                            selectedTextColor = Color(0xFF503727)))
                    NavigationDrawerItem(label = { Text("Detener lectura") }, selected = false,
                        icon = { AppIcon("Cerrar") },
                        onClick = {
                            audioActive = false
                            audioControlsVisible = false
                            speech?.stop()
                        })
                    NavigationDrawerItem(label = { Text("Elegir voz") }, selected = false,
                        icon = { AppIcon("Ajustes") },
                        onClick = { showVoicePicker = true })
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("APARIENCIA", color = Color(0xFF785940),
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                if (document is ReadingDocument.TextDocument) {
                    Text("Tamaño de letra · ${fontSize.toInt()} pt", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            fontSize = (fontSize - 1).coerceAtLeast(12f)
                            prefs.edit().putFloat("font_size", fontSize).apply()
                        }, enabled = fontSize > 12f) { Text("A−") }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = {
                            fontSize = (fontSize + 1).coerceAtMost(30f)
                            prefs.edit().putFloat("font_size", fontSize).apply()
                        }, enabled = fontSize < 30f) { Text("A+") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        dark = false; sepia = false
                        prefs.edit().putBoolean("dark", false).putBoolean("sepia", false).apply()
                    }) { Text("Día") }
                    TextButton(onClick = {
                        dark = false; sepia = true
                        prefs.edit().putBoolean("dark", false).putBoolean("sepia", true).apply()
                    }) { Text("Sepia") }
                    TextButton(onClick = {
                        dark = true; prefs.edit().putBoolean("dark", true).apply()
                    }) { Text("Noche") }
                }
                if (document is ReadingDocument.TextDocument)
                    OutlinedTextField(searchText, { searchText = it }, singleLine = true,
                        label = { Text("Buscar en el libro") },
                        modifier = Modifier.fillMaxWidth())
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("CAPÍTULOS Y MARCAS", color = Color(0xFF785940),
                    style = MaterialTheme.typography.labelMedium)
                val chapters = if (document is ReadingDocument.TextDocument)
                    document.paragraphs.withIndex().filter { it.value.heading }
                else emptyList()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    if (document is ReadingDocument.TextDocument && searchText.isNotBlank()) {
                        val matches = document.paragraphs.withIndex()
                            .filter { it.value.text.contains(searchText, ignoreCase = true) }
                            .take(30)
                        item { Text("${matches.size} coincidencias", fontSize = 12.sp) }
                        items(matches.size) { number ->
                            val match = matches[number]
                            TextButton(onClick = {
                                jumpToItem = match.index; scope.launch { drawer.close() }
                            }) { Text(match.value.text.take(100), maxLines = 2) }
                        }
                    }
                    when (document) {
                        is ReadingDocument.TextDocument -> {
                            if (chapters.isEmpty()) item { TextButton(onClick = {
                                jumpToItem = 0; scope.launch { drawer.close() }
                            }) { Text("Inicio") } }
                            items(chapters.size) { number ->
                                val chapter = chapters[number]
                                TextButton(onClick = {
                                    jumpToItem = chapter.index; scope.launch { drawer.close() }
                                }) { Text(chapter.value.text.take(70), maxLines = 2) }
                            }
                        }
                        is ReadingDocument.PdfDocument -> items(document.pages) { number ->
                            TextButton(onClick = { page = number; scope.launch { drawer.close() } }) {
                                Text("Página ${number + 1}")
                            }
                        }
                        else -> Unit
                    }
                    if (highlights.isNotEmpty()) {
                        item { Text("Mis subrayados", fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp)) }
                        items(highlights.size) { number ->
                            val highlight = highlights[number]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    jumpToItem = highlight.paragraph
                                    scope.launch { drawer.close() }
                                }, modifier = Modifier.weight(1f)) {
                                    Column {
                                        Text(highlight.quote.take(60), maxLines = 2)
                                        if (highlight.note.isNotBlank()) Text(highlight.note.take(80),
                                            fontSize = 11.sp, maxLines = 2)
                                    }
                                }
                                IconButton(onClick = {
                                    highlights = highlights.filterIndexed { i, _ -> i != number }
                                    saveHighlights(context, key, highlights)
                                    onHighlightsChanged()
                                }) { AppIcon("Borrar", "Eliminar subrayado") }
                            }
                        }
                    }
                }
                Text(if (brightness < 0f) "Brillo del sistema" else
                    "Brillo de lectura: ${(brightness * 100).toInt()} %", fontSize = 12.sp)
                Slider(value = if (brightness < 0f) 0.5f else brightness, onValueChange = {
                    brightness = it.coerceIn(0.05f, 1f)
                    prefs.edit().putFloat("brightness", brightness).apply()
                }, valueRange = 0.05f..1f)
                TextButton(onClick = {
                    brightness = -1f
                    prefs.edit().putFloat("brightness", -1f).apply()
                }) { Text("Usar brillo del sistema") }
                TextButton(onClick = { scope.launch {
                    try { shareReadingFile(context, book) }
                    catch (e: Exception) { android.widget.Toast.makeText(context,
                        "No se pudo compartir: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show() }
                } }) { Text("Compartir libro") }
            }
        }
    }) {
    Scaffold(containerColor = background, contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        if (document !is ReadingDocument.TextDocument && document !is ReadingDocument.PdfDocument)
        TopAppBar(title = { Text(book.customTitle.ifBlank { book.title }.take(38), maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { AppIcon("Volver", "Volver") } },
            actions = {
                IconButton(onClick = { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() }) {
                    AppIcon(if (dark) "Día" else "Noche", "Cambiar tema")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = background,
                titleContentColor = foreground, navigationIconContentColor = foreground,
                actionIconContentColor = foreground))
    }) { padding ->
      Box(Modifier.fillMaxSize().pointerInput(document, showReaderTools) {
          awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
              val start = down.position
              var end = start
              var released = false
              var releaseTime = down.uptimeMillis
              while (true) {
                  val change = awaitPointerEvent(PointerEventPass.Initial).changes
                      .firstOrNull { it.id == down.id } ?: break
                  end = change.position
                  if (!change.pressed) { released = true; releaseTime = change.uptimeMillis; break }
              }
              if (released && (document is ReadingDocument.TextDocument ||
                  document is ReadingDocument.PdfDocument)) {
                  val horizontal = end.x - start.x
                  val vertical = end.y - start.y
                  val edgeSwipe = start.x <= 28.dp.toPx() &&
                      horizontal >= 72.dp.toPx() && kotlin.math.abs(vertical) < 54.dp.toPx()
                  val centerTap = document is ReadingDocument.TextDocument &&
                      start.x in size.width * 0.30f..size.width * 0.70f &&
                      start.y in size.height * 0.30f..size.height * 0.75f
                  val still = kotlin.math.abs(horizontal) < 14.dp.toPx() &&
                      kotlin.math.abs(vertical) < 14.dp.toPx()
                  if (edgeSwipe) scope.launch { drawer.open() }
                  else if (centerTap && still && releaseTime - down.uptimeMillis < 350L)
                      showReaderTools = true
              }
          }
      }) {
        when (document) {
            null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Preparando libro…")
                }
            }
            is ReadingDocument.Unsupported -> Box(Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(document.reason, color = foreground)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = {
                        val mime = when (book.uri.lastPathSegment.orEmpty().substringAfterLast('.').lowercase()) {
                            "mobi", "azw", "azw3" -> "application/x-mobipocket-ebook"
                            "pdf" -> "application/pdf"
                            else -> "application/octet-stream"
                        }
                        try {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(book.uri, mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Abrir con otra aplicación"))
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "No hay otra aplicación compatible",
                                android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Abrir con otra aplicación") }
                }
            }
            is ReadingDocument.PdfDocument -> {
                val index = page.coerceIn(0, document.pages - 1)
                LaunchedEffect(index) {
                    prefs.edit().putInt("page_$key", index).apply()
                    onProgress(((index + 1) * 100f / document.pages).toInt())
                }
                Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()
                        .background(if (dark) Color.DarkGray else Color.LightGray)
                        .pointerInput(index, document.pages) {
                            var drag = 0f
                            detectHorizontalDragGestures(onDragStart = { drag = 0f },
                                onHorizontalDrag = { _, amount -> drag += amount },
                                onDragEnd = {
                                    if (drag < -swipeDistance) page = (index + 1).coerceAtMost(document.pages - 1)
                                    if (drag > swipeDistance) page = (index - 1).coerceAtLeast(0)
                                })
                        },
                        contentAlignment = Alignment.TopCenter) {
                        AnimatedContent(targetState = index, transitionSpec = {
                            val forward = targetState > initialState
                            (slideInHorizontally(tween(280)) { if (forward) it / 5 else -it / 5 } +
                                fadeIn(tween(180))).togetherWith(
                                slideOutHorizontally(tween(280)) { if (forward) -it / 5 else it / 5 } +
                                    fadeOut(tween(180))).using(SizeTransform(clip = true))
                        }, label = "Pasar página PDF") { shown ->
                            val angle by transition.animateFloat(transitionSpec = { tween(280) },
                                label = "Pliegue PDF") { state ->
                                when (state) {
                                    EnterExitState.PreEnter -> 62f
                                    EnterExitState.Visible -> 0f
                                    EnterExitState.PostExit -> -62f
                                }
                            }
                            val entering = transition.targetState == EnterExitState.Visible
                            val shownImage by produceState<Bitmap?>(null, document.file, shown) {
                                value = try { withContext(Dispatchers.IO) { renderPdfPage(document.file, shown) } }
                                catch (_: Exception) { null }
                            }
                            Box(Modifier.fillMaxSize().graphicsLayer {
                                rotationY = angle
                                transformOrigin = TransformOrigin(if (entering) 1f else 0f, 0.5f)
                                cameraDistance = 12000f
                                shadowElevation = if (angle == 0f) 0f else 18.dp.toPx()
                            }, contentAlignment = Alignment.TopCenter) {
                                if (shownImage != null) Image(shownImage!!.asImageBitmap(),
                                    "Página ${shown + 1}", Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit)
                                else CircularProgressIndicator()
                            }
                        }
                        Box(Modifier.align(Alignment.CenterStart).width(38.dp).fillMaxHeight()
                            .clickable { page = (index - 1).coerceAtLeast(0) })
                        Box(Modifier.align(Alignment.CenterEnd).width(38.dp).fillMaxHeight()
                            .clickable { page = (index + 1).coerceAtMost(document.pages - 1) })
                    }
                    Text("Página ${index + 1} de ${document.pages} · ${((index + 1) * 100f / document.pages).toInt()} %",
                        color = foreground, modifier = Modifier.padding(6.dp))
                }
            }
            is ReadingDocument.TextDocument -> {
                val paragraphs = document.paragraphs
                val positions = remember(document) {
                    IntArray(paragraphs.size + 1).also { sums ->
                        paragraphs.forEachIndexed { i, value -> sums[i + 1] = sums[i] + value.text.length }
                    }
                }
                val total = positions.last().coerceAtLeast(1)
                var textPage by remember(book.uri) { mutableIntStateOf(0) }
                var totalTextPages by remember(book.uri) { mutableIntStateOf(0) }
                var ready by remember(book.uri) { mutableStateOf(false) }
                var percent by remember(book.uri) { mutableIntStateOf(prefs.getInt("percent_$key", 0)) }
                Column(Modifier.padding(padding).fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val density = LocalDensity.current
                        val width = with(density) { (maxWidth - 44.dp).roundToPx() }
                        val height = with(density) { (maxHeight - 32.dp).roundToPx() }
                        val textSizePx = with(density) { fontSize.sp.toPx() }
                        val gap = with(density) { 14.dp.roundToPx() }
                        val pages by produceState<List<ReadingPage>>(emptyList(), document,
                            width, height, fontSize) {
                            ready = false
                            value = emptyList()
                            value = withContext(Dispatchers.Default) {
                                paginateText(paragraphs, width, height.coerceAtLeast(1), textSizePx, gap)
                            }
                        }
                        if (pages.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = foreground)
                            }
                            return@BoxWithConstraints
                        }
                        LaunchedEffect(pages) {
                            totalTextPages = pages.size
                            val legacyItem = prefs.getInt("item_$key", 0).coerceIn(0, paragraphs.lastIndex)
                            val savedChar = prefs.getInt("char_$key", positions[legacyItem])
                            audioCursor = savedChar
                            textPage = pages.indexOfLast { it.startChar <= savedChar }.coerceAtLeast(0)
                            ready = true
                        }
                        LaunchedEffect(jumpToItem, pages) {
                            if (jumpToItem >= 0) {
                                val target = positions[jumpToItem.coerceIn(0, paragraphs.lastIndex)]
                                textPage = pages.indexOfLast { it.startChar <= target }.coerceAtLeast(0)
                                jumpToItem = -1
                            }
                        }
                        LaunchedEffect(textPage, pages, ready) {
                            if (ready) {
                                val current = pages[textPage.coerceIn(pages.indices)]
                                percent = if (textPage >= pages.lastIndex) 100
                                    else (current.startChar * 100 / total).coerceIn(0, 100)
                                val pageEnd = current.slices.lastOrNull()?.let { last ->
                                    positions[last.paragraph] + last.end
                                } ?: current.startChar
                                val savedPosition = audioCursor.takeIf {
                                    it >= current.startChar && it < pageEnd.coerceAtLeast(current.startChar + 1)
                                } ?: current.startChar
                                activeSpeechCursor.set(savedPosition)
                                audioCursor = savedPosition
                                prefs.edit().putInt("char_$key", savedPosition)
                                    .putInt("percent_$key", percent).apply()
                                onProgress(percent)
                            }
                        }
                        LaunchedEffect(audioSeekRequest, pages) {
                            if (audioSeekRequest > 0) {
                                val target = (audioCursor + audioSeekDelta).coerceIn(0, total - 1)
                                saveSpeechCursor(target)
                                prefs.edit().putInt("percent_$key", (target * 100 / total).coerceIn(0, 99)).apply()
                                textPage = pages.indexOfLast { it.startChar <= target }.coerceAtLeast(0)
                            }
                        }
                        val currentPage = textPage.coerceIn(pages.indices)
                        LaunchedEffect(audioAdvance) {
                            if (audioAdvance > 0 && audioActive) {
                                if (textPage < pages.lastIndex) textPage++
                                else audioActive = false
                            }
                        }
                        LaunchedEffect(audioActive, currentPage, pages, speechReady, selectedVoiceName, audioSeekRequest) {
                            val engine = speech
                            if (!audioActive || !speechReady || engine == null) {
                                engine?.stop()
                            } else {
                                val locale = if (book.language.startsWith("en", true))
                                    java.util.Locale.ENGLISH else java.util.Locale.forLanguageTag("es-ES")
                                val availability = engine.setLanguage(locale)
                                 val chosenVoice = engine.voices?.firstOrNull {
                                     selectedVoiceName != "davefx" && it.name == selectedVoiceName }
                                 if (chosenVoice != null && chosenVoice.locale.language == locale.language)
                                     engine.voice = chosenVoice
                                if (availability < TextToSpeech.LANG_AVAILABLE) {
                                    audioActive = false
                                    android.widget.Toast.makeText(context,
                                        "Instala una voz del idioma del libro en los ajustes de voz de Android",
                                        android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    val pageData = pages[currentPage]
                                    val spokenPage = speechPage(pageData, paragraphs, positions)
                                    val offsetInPage = speechOffsetForChar(spokenPage, audioCursor)
                                        .coerceIn(0, spokenPage.text.length)
                                    val spoken = spokenPage.text.substring(offsetInPage)
                                    val spokenBaseOffset = offsetInPage
                                    activeSpeechCursor.set(audioCursor)
                                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                        override fun onStart(utteranceId: String?) {}
                                        override fun onRangeStart(utteranceId: String?, start: Int,
                                            end: Int, frame: Int) {
                                            val charPosition = speechCharForOffset(
                                                spokenPage, spokenBaseOffset + start, total)
                                            activeSpeechCursor.set(charPosition)
                                            Handler(Looper.getMainLooper()).post {
                                                audioCursor = charPosition
                                                prefs.edit().putInt("char_$key", charPosition)
                                                    .putInt("percent_$key",
                                                        (charPosition * 100 / total).coerceIn(0, 99)).apply()
                                            }
                                        }
                                        override fun onDone(utteranceId: String?) {
                                            Handler(Looper.getMainLooper()).post {
                                                val nextChar = pages.getOrNull(currentPage + 1)?.startChar ?: total
                                                saveSpeechCursor(nextChar.coerceAtMost(total - 1))
                                                audioAdvance++
                                            }
                                        }
                                        override fun onError(utteranceId: String?) {
                                            Handler(Looper.getMainLooper()).post { audioActive = false }
                                        }
                                    })
                                    if (spoken.isNotBlank())
                                        engine.speak(spoken.take(3900), TextToSpeech.QUEUE_FLUSH,
                                            null, "page_$currentPage")
                                    else audioAdvance++
                                }
                            }
                        }
                        fun turn(delta: Int) {
                            val targetPage = (textPage + delta).coerceIn(pages.indices)
                            textPage = targetPage
                            if (audioControlsVisible) {
                                saveSpeechCursor(pages[targetPage].startChar)
                            }
                        }
                        Box(Modifier.fillMaxSize().clipToBounds().pointerInput(pages, swipeDistance) {
                            var drag = 0f
                            detectHorizontalDragGestures(onDragStart = { drag = 0f },
                                onHorizontalDrag = { _, amount -> drag += amount },
                                onDragEnd = {
                                    if (drag < -swipeDistance) turn(1)
                                    if (drag > swipeDistance) turn(-1)
                                })
                        }) {
                            AnimatedContent(targetState = currentPage,
                                transitionSpec = {
                                    val forward = targetState > initialState
                                    (slideInHorizontally(tween(280)) { if (forward) it / 5 else -it / 5 } +
                                        fadeIn(tween(180))).togetherWith(
                                        slideOutHorizontally(tween(280)) { if (forward) -it / 5 else it / 5 } +
                                            fadeOut(tween(180))).using(SizeTransform(clip = true))
                                }, label = "Pasar página") { shown ->
                                val angle by transition.animateFloat(transitionSpec = { tween(280) },
                                    label = "Pliegue") { state ->
                                    when (state) {
                                        EnterExitState.PreEnter -> 62f
                                        EnterExitState.Visible -> 0f
                                        EnterExitState.PostExit -> -62f
                                    }
                                }
                                val entering = transition.targetState == EnterExitState.Visible
                                Column(Modifier.fillMaxSize().graphicsLayer {
                                    rotationY = angle
                                    transformOrigin = TransformOrigin(if (entering) 1f else 0f, 0.5f)
                                    cameraDistance = 12000f
                                    shadowElevation = if (angle == 0f) 0f else 18.dp.toPx()
                                }.background(background).padding(horizontal = 22.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    pages[shown].slices.forEachIndexed { position, slice ->
                                        if (position == 0 && slice.start == 0 &&
                                            paragraphs[slice.paragraph].chapterStart)
                                            Spacer(Modifier.height(28.dp))
                                        SelectableParagraph(paragraphs[slice.paragraph], slice.paragraph,
                                            slice.start, slice.end, fontSize, foreground, dark,
                                            openingParagraph(paragraphs, slice.paragraph), highlights,
                                            onHighlight = { mark ->
                                                highlights = (highlights + mark).distinct()
                                                saveHighlights(context, key, highlights)
                                                onHighlightsChanged()
                                            }, onNote = { mark ->
                                                pendingNote = mark
                                                noteDraft = highlights.firstOrNull { h ->
                                                    h.paragraph == mark.paragraph && h.start == mark.start &&
                                                        h.end == mark.end
                                                }?.note.orEmpty()
                                            }, onLookup = { word -> dictionaryWord = word },
                                            onTranslate = { quote -> translationQuote = quote },
                                            onSpeak = { quote ->
                                                speech?.apply {
                                                    language = if (book.language.startsWith("en", true))
                                                        java.util.Locale.ENGLISH else
                                                        java.util.Locale.forLanguageTag("es-ES")
                                                    speak(quote, TextToSpeech.QUEUE_FLUSH, null, "reader_quote")
                                                }
                                            }, onSearch = { quote ->
                                                searchText = quote.take(80)
                                                scope.launch { drawer.open() }
                                            }, onSaveToNotebook = ::saveNotebookEntry,
                                            onBookmarkChar = { paragraphIndex, charInParagraph ->
                                                toggleBookmark(positions[paragraphIndex] + charInParagraph)
                                            })
                                    }
                                }
                            }
                            Box(Modifier.align(Alignment.CenterStart).width(32.dp).fillMaxHeight()
                                .clickable { turn(-1) })
                            Box(Modifier.align(Alignment.CenterEnd).width(32.dp).fillMaxHeight()
                                .clickable { turn(1) })
                        }
                        if (showReaderTools) {
                            var requestedPage by remember(currentPage) {
                                mutableFloatStateOf(currentPage.toFloat())
                            }
                            var directPage by remember(currentPage) {
                                mutableStateOf((currentPage + 1).toString())
                            }
                            ModalBottomSheet(onDismissRequest = { showReaderTools = false },
                                containerColor = if (dark) Color(0xFF302A25) else Color(0xFFF8F3E9),
                                contentColor = foreground) {
                                Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(book.customTitle.ifBlank { book.title }.take(60),
                                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp, maxLines = 2)
                                    Text("Página ${currentPage + 1} de ${pages.size} · $percent %",
                                        color = foreground.copy(alpha = 0.75f))
                                    Slider(value = requestedPage, onValueChange = { requestedPage = it },
                                        valueRange = 0f..pages.lastIndex.toFloat().coerceAtLeast(0f),
                                        onValueChangeFinished = {
                                            val target = requestedPage.toInt().coerceIn(pages.indices)
                                            textPage = target
                                            saveSpeechCursor(pages[target].startChar)
                                            showReaderTools = false
                                        })
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(directPage, { directPage = it.filter(Char::isDigit) },
                                            modifier = Modifier.weight(1f), singleLine = true,
                                            label = { Text("Ir a la página") })
                                        Button(onClick = {
                                            val target = (directPage.toIntOrNull() ?: 1)
                                                .coerceIn(1, pages.size) - 1
                                            textPage = target
                                            saveSpeechCursor(pages[target].startChar)
                                            showReaderTools = false
                                        }) { Text("Ir") }
                                    }
                                    HorizontalDivider()
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Marcas de página", fontWeight = FontWeight.Bold)
                                            Text("Guarda este punto para volver después.",
                                                fontSize = 12.sp, color = foreground.copy(alpha = 0.72f))
                                        }
                                        FilledTonalButton(onClick = {
                                            toggleBookmark(pages[currentPage].startChar)
                                        }) {
                                            Text(if (pages[currentPage].startChar in bookmarkChars)
                                                "Quitar marca" else "Poner marca")
                                        }
                                    }
                                    bookmarkChars.forEachIndexed { index, charPosition ->
                                        val targetPage = pages.indexOfLast {
                                            it.startChar <= charPosition
                                        }.coerceAtLeast(0)
                                        TextButton(onClick = {
                                            textPage = targetPage
                                            saveSpeechCursor(charPosition)
                                            showReaderTools = false
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Marcador ${index + 1} · página ${targetPage + 1}",
                                                modifier = Modifier.weight(1f))
                                        }
                                    }
                                    HorizontalDivider()
                                    Text("Libreta de este libro", fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif, fontSize = 17.sp)
                                    OutlinedTextField(notebook, { notebook = it },
                                        modifier = Modifier.fillMaxWidth(), minLines = 4,
                                        label = { Text("Notas, palabras y frases guardadas") })
                                    Button(onClick = {
                                        prefs.edit().putString("notebook_$key", notebook).apply()
                                        showReaderTools = false
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar en la libreta") }
                                    if (highlights.isNotEmpty()) {
                                        Text("Subrayados y notas", fontWeight = FontWeight.Bold)
                                        highlights.takeLast(12).asReversed().forEach { mark ->
                                            TextButton(onClick = {
                                                jumpToItem = mark.paragraph
                                                showReaderTools = false
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Text(mark.quote.take(100) +
                                                    mark.note.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),
                                                    maxLines = 2, modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (audioControlsVisible) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            color = if (dark) Color(0xFF302A25) else Color(0xFFF4EEE4),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = { requestAudioSeek(-270) }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon("Retroceder", size = 19.dp)
                                        Text("−15 s", fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = {
                                        audioControlsVisible = true
                                        audioActive = !audioActive
                                    }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon(if (audioActive) "Pausar" else "Reproducir", size = 21.dp)
                                        Text(if (audioActive) "Pausa" else "Play", fontSize = 10.sp)
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = {
                                        pauseSpeech()
                                        audioControlsVisible = false
                                        speech?.stop()
                                    }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon("Detener", size = 19.dp)
                                        Text("Stop", fontSize = 10.sp)
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = { requestAudioSeek(270) }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon("Avanzar", size = 19.dp)
                                        Text("+15 s", fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1.25f).height(54.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                                    onClick = {
                                        activity?.moveTaskToBack(true)
                                        android.widget.Toast.makeText(
                                            context,
                                            "La lectura continúa en segundo plano. Pulsa el botón lateral para apagar y bloquear la pantalla.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon("Bloquear", size = 19.dp)
                                        Text("Pantalla", fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    Text("Página ${textPage + 1} de ${totalTextPages.coerceAtLeast(1)} · $percent % leído",
                        Modifier.align(Alignment.CenterHorizontally), color = foreground)
                }
            }
        }

      }
    }
    }
    if (showVoicePicker) {
        val language = if (book.language.startsWith("en", true)) "en" else "es"
        val choices = speech?.voices.orEmpty()
            .filter { it.locale.language == language && !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
        AlertDialog(onDismissRequest = { showVoicePicker = false },
            title = { Text("Elegir voz") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text("Voces sin conexión instaladas en el dispositivo",
                        style = MaterialTheme.typography.bodySmall)
                    RadioVoiceOption("Davefx · español de España",
                        selectedVoiceName == "davefx") {
                        if (hasDavefxEngine(context)) {
                            selectedVoiceName = "davefx"
                            prefs.edit().putString("voice_name", "davefx").apply()
                            showVoicePicker = false
                        } else if (!davefxDownloading) {
                            showVoicePicker = false
                            davefxDownloading = true
                            davefxProgress = 0
                            scope.launch {
                                try {
                                    davefxReadyFile = withContext(Dispatchers.IO) {
                                        downloadDavefx(context) { progress ->
                                            Handler(Looper.getMainLooper()).post {
                                                davefxProgress = progress
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    davefxError = e.localizedMessage ?: "No se pudo descargar Davefx."
                                } finally { davefxDownloading = false }
                            }
                        }
                    }
                    if (!hasDavefxEngine(context)) Text(
                        "Al elegir Davefx se descargará el motor oficial (87 MB) y podrás instalarlo.",
                        style = MaterialTheme.typography.bodySmall)
                    RadioVoiceOption("Predeterminada", selectedVoiceName.isBlank()) {
                        selectedVoiceName = ""; prefs.edit().remove("voice_name").apply()
                        showVoicePicker = false
                    }
                    choices.forEach { voice ->
                        RadioVoiceOption(voice.name + " · " + voice.locale.toLanguageTag(),
                            selectedVoiceName == voice.name) {
                            selectedVoiceName = voice.name
                            prefs.edit().putString("voice_name", voice.name).apply()
                            showVoicePicker = false
                        }
                    }
                    if (choices.isEmpty()) Text("No hay voces sin conexión para este idioma.")
                }
            },
            confirmButton = { TextButton(onClick = { showVoicePicker = false }) { Text("Cerrar") } })
    }
    if (davefxDownloading) AlertDialog(onDismissRequest = {},
        title = { Text("Descargando Davefx") },
        text = { Column {
            Text("Motor de voz sin conexión · ${davefxProgress} %")
            LinearProgressIndicator(progress = { davefxProgress / 100f },
                modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = {})
    davefxError?.let { error ->
        AlertDialog(onDismissRequest = { davefxError = null },
            title = { Text("Descarga interrumpida") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { davefxError = null }) { Text("Cerrar") } })
    }
    davefxReadyFile?.let { file ->
        AlertDialog(onDismissRequest = { davefxReadyFile = null },
            title = { Text("Instalar voz Davefx") },
            text = { Text("Descarga comprobada. Android solicitará permiso para instalar el motor de voz. Después vuelve a Elegir voz y selecciona Davefx.") },
            confirmButton = { Button(onClick = {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName)))
                } else {
                    try {
                        installDavefx(context, file)
                        davefxReadyFile = null
                    } catch (e: Exception) {
                        davefxError = e.localizedMessage ?: "No se pudo abrir el instalador."
                    }
                }
            }) { Text("Instalar") } },
            dismissButton = { TextButton(onClick = { davefxReadyFile = null }) { Text("Más tarde") } })
    }
    pendingNote?.let { selected ->
        AlertDialog(onDismissRequest = { pendingNote = null },
            title = { Text("Nota de lectura") },
            text = { Column {
                Text("«${selected.quote.take(180)}»", fontFamily = FontFamily.Serif)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(noteDraft, { noteDraft = it },
                    label = { Text("Escribe tu nota") }, minLines = 3)
            } },
            confirmButton = { TextButton(onClick = {
                highlights = highlights.filterNot { h ->
                    h.paragraph == selected.paragraph && h.start == selected.start &&
                        h.end == selected.end
                } + selected.copy(note = noteDraft.trim())
                saveHighlights(context, key, highlights)
                onHighlightsChanged()
                pendingNote = null
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { pendingNote = null }) { Text("Cancelar") } })
    }
    dictionaryWord?.let { word ->
        val definitions by produceState<List<String>?>(null, word) {
            value = withContext(Dispatchers.IO) { dictionaryDefinitions(word) }
        }
        ModalBottomSheet(onDismissRequest = { dictionaryWord = null },
            containerColor = background, contentColor = foreground) {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 30.dp)) {
                Text(word, fontFamily = FontFamily.Serif, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text("Diccionario · Wikcionario en castellano", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                when {
                    definitions == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    definitions!!.isEmpty() -> Text("No se encontró una definición para esta palabra.")
                    else -> definitions!!.forEachIndexed { i, definition ->
                        Text("${i + 1}. $definition", modifier = Modifier.padding(bottom = 10.dp),
                            fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        saveNotebookEntry("«$word» — " +
                            (definitions?.firstOrNull() ?: "palabra consultada en el diccionario"))
                    }) { Text("Guardar en mi libreta") }
                    TextButton(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://dle.rae.es/" +
                                java.net.URLEncoder.encode(word, "UTF-8")))) }
                        catch (_: Exception) {}
                    }) { Text("Consultar RAE") }
                }
            }
        }
    }
    translationQuote?.let { quote ->
        val translation by produceState<String?>(null, quote) {
            value = withContext(Dispatchers.IO) { ensureSpanish(quote) }
        }
        ModalBottomSheet(onDismissRequest = { translationQuote = null },
            containerColor = background, contentColor = foreground) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp)) {
                Text("Traducción al castellano", fontFamily = FontFamily.Serif,
                    fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("«${quote.take(500)}»", fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                when {
                    translation == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    translation!!.isBlank() -> Text("No se pudo traducir este fragmento.")
                    else -> Text(translation!!, fontSize = 17.sp, lineHeight = 24.sp)
                }
                if (!translation.isNullOrBlank()) {
                    TextButton(onClick = {
                        saveNotebookEntry("Frase: «${quote.take(300)}»\nTraducción: ${translation.orEmpty()}")
                    }) { Text("Guardar frase en mi libreta") }
                }
            }
        }
    }
}

@Composable
private fun RadioVoiceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
    }
}

private const val DAVEFX_ENGINE = "com.k2fsa.sherpa.onnx.tts.engine"
private const val DAVEFX_DOWNLOAD_URL =
    "https://huggingface.co/csukuangfj2/sherpa-onnx-apk/resolve/main/tts-engine-new/1.13.8/sherpa-onnx-1.13.8-arm64-v8a-spa-tts-engine-vits-piper-es_ES-davefx-medium.apk"
private const val DAVEFX_SHA256 = "74f515985857e94ecb91c72537f0e7155a51cdbcb35f4f8634f73f117989aee3"

private fun hasDavefxEngine(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(DAVEFX_ENGINE, 0)
    true
} catch (_: android.content.pm.PackageManager.NameNotFoundException) {
    false
}

private fun downloadDavefx(context: Context, onProgress: (Int) -> Unit): File {
    val directory = File(context.cacheDir, "voices").apply { mkdirs() }
    val destination = File(directory, "davefx-engine.apk")
    fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
    if (destination.isFile && checksum(destination) == DAVEFX_SHA256) {
        onProgress(100)
        return destination
    }
    require(context.cacheDir.usableSpace > 200_000_000L) {
        "Necesitas al menos 200 MB libres para descargar e instalar Davefx."
    }
    val temp = File(directory, "davefx-engine.download")
    try {
        val connection = (URL(DAVEFX_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            require(connection.responseCode in 200..299) {
                "El servidor de Davefx respondió ${connection.responseCode}."
            }
            val length = connection.contentLengthLong
            require(length == -1L || length in 50_000_000L..150_000_000L) {
                "El tamaño de la descarga de Davefx no es válido."
            }
            connection.inputStream.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= 150_000_000L) { "Descarga demasiado grande." }
                        output.write(buffer, 0, count)
                        if (length > 0) onProgress((copied * 100 / length).toInt().coerceIn(0, 100))
                    }
                }
            }
            require((length < 0 || temp.length() == length) && checksum(temp) == DAVEFX_SHA256) {
                "La descarga de Davefx no superó la verificación de integridad."
            }
            if (destination.exists()) destination.delete()
            require(temp.renameTo(destination)) { "No se pudo preparar el instalador de Davefx." }
            return destination
        } finally { connection.disconnect() }
    } finally { temp.delete() }
}

private fun installDavefx(context: Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, context.packageName + ".fileprovider", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}
