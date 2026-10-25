package com.diego.mibiblioteca

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.ClipData
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.app.Activity
import android.graphics.Typeface
import android.media.AudioAttributes
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandMore
import android.widget.TextView
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Build
import android.content.pm.PackageManager
import java.util.concurrent.atomic.AtomicInteger
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    val chapterStart: Boolean = false, val partHeading: Boolean = false,
    val imagePath: String? = null)
private data class ReaderHighlight(val paragraph: Int, val start: Int, val end: Int,
    val quote: String, val color: String = "amarillo", val note: String = "")
private data class ReadingSlice(val paragraph: Int, val start: Int, val end: Int, val height: Int)
private data class ReadingPage(val slices: List<ReadingSlice>, val startChar: Int)
private data class SpeechPage(val text: String, val sourcePositions: IntArray)
internal data class SpeechChunk(val start: Int, val text: String)

internal fun readerImageHeight(
    sourceWidth: Int,
    sourceHeight: Int,
    contentWidth: Int,
    pageHeight: Int
): Int {
    if (pageHeight <= 0) return 1
    val targetWidth = (contentWidth * 0.78f).toInt().coerceAtLeast(1)
    val proportional = if (sourceWidth > 0 && sourceHeight > 0)
        (targetWidth.toLong() * sourceHeight / sourceWidth).toInt()
    else (pageHeight * 0.24f).toInt()
    return proportional.coerceIn(
        (pageHeight * 0.14f).toInt().coerceAtLeast(1),
        (pageHeight * 0.55f).toInt().coerceAtLeast(1)
    )
}
internal fun speechWordChunks(text: String, maxChars: Int = 320): List<SpeechChunk> {
    if (text.isBlank()) return emptyList()
    val limit = maxChars.coerceAtLeast(120)
    val chunks = mutableListOf<SpeechChunk>()
    var from = 0
    while (from < text.length) {
        val hardEnd = (from + limit).coerceAtMost(text.length)
        val end = if (hardEnd == text.length) hardEnd else {
            val sentenceFloor = from + (limit / 2)
            val sentenceEnd = listOf('.', '!', '?', ';', ':')
                .maxOfOrNull { text.lastIndexOf(it, hardEnd - 1) }
                ?.takeIf { it >= sentenceFloor }
                ?.plus(1)
            sentenceEnd ?: text.lastIndexOf(' ', hardEnd - 1).takeIf { it > from }
                ?: text.indexOf(' ', hardEnd).takeIf { it >= 0 }
                ?: hardEnd
        }
        val content = text.substring(from, end)
        if (content.isNotBlank()) chunks += SpeechChunk(from, content)
        from = if (end < text.length && text[end].isWhitespace()) end + 1 else end
    }
    return chunks
}

internal fun speechSeekDistance(seconds: Int, rate: Float): Int =
    (seconds.coerceAtLeast(1) * 18f * rate.coerceIn(0.5f, 2f)).toInt().coerceAtLeast(1)

internal fun speechCursorForVisiblePage(
    cursor: Int,
    pageStart: Int,
    nextPageStart: Int,
    total: Int
): Int {
    val safeTotal = total.coerceAtLeast(1)
    val safeStart = pageStart.coerceIn(0, safeTotal - 1)
    val safeEnd = nextPageStart.coerceIn(safeStart + 1, safeTotal)
    return if (cursor in safeStart until safeEnd) cursor else safeStart
}

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
    textSizePx: Float, gapPx: Int, lineSpacing: Float = 1.45f): List<ReadingPage> {
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
            // Start a chapter on a new page only when the current page is nearly full.
            if (slices.isNotEmpty() && occupied > (height * 0.82f).toInt()) finish()
            occupied += (gapPx * 2).coerceAtMost(height / 8)
        }
        if (paragraph.imagePath != null) {
            // La ilustración comparte página con el texto siguiente cuando
            // queda espacio, como en la maquetación EPUB original.
            // La ilustración debe quedar integrada en la página, sin dejar
            // un bloque vacío debajo. Reservamos una fracción compacta y
            // dejamos que el texto siguiente ocupe todo el espacio restante.
            // El límite superior evita que una imagen grande se coma la página.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(paragraph.imagePath, bounds)
            val imageHeight = readerImageHeight(
                bounds.outWidth, bounds.outHeight, width, height
            )
            if (occupied + imageHeight > height && slices.isNotEmpty()) finish()
            slices += ReadingSlice(index, 0, 0, imageHeight)
            occupied += imageHeight + gapPx
            if (occupied >= height) finish()
            return@forEachIndexed
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
            .setJustificationMode(Layout.JUSTIFICATION_MODE_NONE)
            .setLineSpacing(0f, lineSpacing.coerceIn(1.0f, 2.0f)).setIncludePad(false).build()
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

private data class DictionaryDefinition(val source: String, val text: String)

private fun dictionaryDefinitions(context: Context, word: String): List<DictionaryDefinition> {
    val normalized = word.trim().replace(Regex("\\s+"), " ")
        .trim('¿', '¡', '.', ',', ';', ':', '!', '?', '«', '»', '"', '\'')
    if (normalized.isBlank()) return emptyList()
    val encoded = java.net.URLEncoder.encode(normalized, "UTF-8")
    val cache = context.getSharedPreferences("reader_dictionary", Context.MODE_PRIVATE)
    val cacheKey = "entry_${normalized.lowercase(java.util.Locale.ROOT)}"
    val now = System.currentTimeMillis()
    cache.getString(cacheKey, null)?.let { raw -> runCatching {
        val saved = JSONObject(raw)
        if (now - saved.optLong("time") < 30L * 24 * 60 * 60 * 1000) {
            val array = saved.getJSONArray("items")
            return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let {
                DictionaryDefinition(it.optString("source"), it.optString("text"))
            } }.filter { it.text.isNotBlank() }
        }
    } }
    fun request(url: String): String? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 4500
        connection.readTimeout = 5500
        connection.setRequestProperty("User-Agent", AppBrand.API_USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
        try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
    } catch (_: Exception) { null }

    // Consult the DLE data service first; keep Wiktionary as an independent fallback.
    val rae = request("https://rae-api.com/api/words/$encoded")?.let { raw -> runCatching {
        val root = JSONObject(raw)
        val data = root.optJSONObject("data") ?: JSONObject()
        val meanings = data.optJSONArray("meanings") ?: JSONArray()
        buildList {
            for (i in 0 until meanings.length()) {
                val senses = meanings.optJSONObject(i)?.optJSONArray("senses") ?: continue
                for (j in 0 until senses.length()) {
                    val sense = senses.optJSONObject(j) ?: continue
                    val definition = sense.optString("description").trim()
                    if (definition.length >= 4) {
                        val category = sense.optString("category").takeIf { it.isNotBlank() }
                        add(DictionaryDefinition("RAE API comunitaria", if (category == null) definition
                            else "${category.replaceFirstChar { it.uppercase() }} · $definition"))
                    }
                }
            }
        }.distinctBy { it.text }.take(8)
    }.getOrNull() }.orEmpty()
    if (rae.isNotEmpty()) {
        val array = JSONArray().apply { rae.forEach { put(JSONObject()
            .put("source", it.source).put("text", it.text)) } }
        cache.edit().putString(cacheKey, JSONObject().put("time", now).put("items", array).toString()).apply()
        return rae
    }

    val parsed = request("https://es.wiktionary.org/w/api.php?action=parse&format=json&prop=text&page=$encoded")
        ?.let { raw -> runCatching {
            Jsoup.parse(JSONObject(raw).getJSONObject("parse").getJSONObject("text").getString("*"))
                .select(".mw-parser-output > ol > li, .mw-parser-output ol > li")
                .map { it.text().trim() }.filter { it.length in 8..400 }.distinct().take(6)
                .map { DictionaryDefinition("Wikcionario", it) }
        }.getOrNull() }.orEmpty()
    if (parsed.isNotEmpty()) {
        val array = JSONArray().apply { parsed.forEach { put(JSONObject()
            .put("source", it.source).put("text", it.text)) } }
        cache.edit().putString(cacheKey, JSONObject().put("time", now).put("items", array).toString()).apply()
        return parsed
    }

    val fallback = request("https://es.wiktionary.org/w/api.php?action=query&format=json&redirects=1&prop=extracts&explaintext=1&exsectionformat=plain&titles=$encoded")
        ?: return emptyList()
    val extracted = runCatching {
        val pages = JSONObject(fallback).getJSONObject("query").getJSONObject("pages")
        val page = pages.keys().asSequence().map { pages.getJSONObject(it) }
            .firstOrNull { !it.has("missing") } ?: return emptyList()
        page.optString("extract").lineSequence().map { it.trim().removePrefix("•").trim() }
            .filter { line -> line.length in 12..400 && !line.startsWith("==") &&
                !line.startsWith("Etimología") && !line.startsWith("Pronunciación") &&
                !line.startsWith("Sinónimos") }
            .distinct().take(6).map { DictionaryDefinition("Wikcionario", it) }.toList()
    }.getOrDefault(emptyList())
    if (extracted.isNotEmpty()) {
        val array = JSONArray().apply { extracted.forEach { put(JSONObject()
            .put("source", it.source).put("text", it.text)) } }
        cache.edit().putString(cacheKey, JSONObject().put("time", now).put("items", array).toString()).apply()
    }
    return extracted
}

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
        val source = try { context.contentResolver.openInputStream(book.uri) }
            catch (_: Exception) { null }
        val available = source ?: offlineBookUri(context, book)?.let { offline ->
            context.contentResolver.openInputStream(offline)
        }
        available?.use { input ->
            require(folder.usableSpace > FileLimits.FREE_SPACE + book.sourceSize.coerceAtLeast(0)) {
                "No hay espacio suficiente para abrir el libro."
            }
            partial.outputStream().use { output ->
                input.copyLimited(output, FileLimits.BOOK_BYTES) {
                    require(folder.usableSpace > FileLimits.FREE_SPACE) { "No queda espacio suficiente en el teléfono." }
                }
            }
        } ?: error("No se pudo leer el archivo de Drive ni la copia de Descargas")
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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

private fun paragraphsFromHtml(html: String, imageResolver: ((String) -> String?)? = null): List<ReadingParagraph> {
    val body = Jsoup.parse(html).body()
    val nodes = body.select("h1,h2,h3,h4,p,li,blockquote,pre,img")
    val result = nodes.mapNotNull { element ->
        if (element.tagName().equals("img", true)) {
            imageResolver?.invoke(element.attr("src"))?.let { ReadingParagraph("", imagePath = it) }
        } else {
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
    }
    return result.ifEmpty { listOfNotNull(body.text().trim().takeIf(String::isNotBlank)?.let(::ReadingParagraph)) }
}

private fun epubNavigationTitleIsFrontMatter(title: String): Boolean {
    val normalized = java.text.Normalizer.normalize(title.lowercase(java.util.Locale.ROOT),
        java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
    return normalized.matches(Regex(
        "^(portada|cubierta|cover|titulo|title page|creditos|credits|copyright|" +
            "datos editoriales|legal|indice|indices|table of contents|contents|" +
            "dedicatoria|dedication|colofon|imprint|epublibre)( .*)?$"))
}

private fun epubNavigationLabels(
    zip: ZipFile,
    opf: String,
    packageXml: org.jsoup.nodes.Document
): Map<String, String> {
    val labels = linkedMapOf<String, String>()
    fun decoded(value: String): String = runCatching {
        java.net.URLDecoder.decode(value.substringBefore('#'), "UTF-8")
    }.getOrDefault(value.substringBefore('#'))
    fun record(base: String, href: String, title: String) {
        val clean = title.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return
        labels.putIfAbsent(resolveZipPath(base, decoded(href)), clean)
    }
    val manifestItems = packageXml.select("manifest > item")
    manifestItems.firstOrNull { it.attr("properties").split(Regex("\\s+")).contains("nav") }
        ?.let { item ->
            val navPath = resolveZipPath(opf, decoded(item.attr("href")))
            zip.readEntryLimited(navPath, FileLimits.INDEX_BYTES)?.toString(Charsets.UTF_8)?.let { html ->
                val nav = Jsoup.parse(html)
                nav.select("nav a[href], navmap a[href]").forEach { link ->
                    record(navPath, link.attr("href"), link.text())
                }
            }
        }
    manifestItems.firstOrNull {
        it.attr("media-type").equals("application/x-dtbncx+xml", true) ||
            it.attr("href").endsWith(".ncx", true)
    }?.let { item ->
        val ncxPath = resolveZipPath(opf, decoded(item.attr("href")))
        zip.readEntryLimited(ncxPath, FileLimits.INDEX_BYTES)?.toString(Charsets.UTF_8)?.let { source ->
            val ncx = Jsoup.parse(source, "", Parser.xmlParser())
            ncx.select("navPoint, navpoint").forEach { point ->
                val href = point.selectFirst("content")?.attr("src").orEmpty()
                val title = point.selectFirst("navLabel > text, navlabel > text")?.text().orEmpty()
                if (href.isNotBlank()) record(ncxPath, href, title)
            }
        }
    }
    return labels
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
    val bookTitle = xml.selectFirst("metadata > title, metadata > *|title")?.text()?.trim()
        ?.takeIf { it.isNotBlank() }
    val manifest = xml.select("manifest > item").associate { it.attr("id") to it.attr("href") }
    val spine = xml.select("spine > itemref").mapNotNull { manifest[it.attr("idref")] }
    val paths = spine.ifEmpty { manifest.values.filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) } }
    val navigationLabels = epubNavigationLabels(zip, opf, xml)
    require(paths.size <= FileLimits.CHAPTERS) { "El EPUB contiene demasiados capítulos." }
    var totalBytes = 0L
    val paragraphs = mutableListOf<ReadingParagraph>()
    if (bookTitle != null) paragraphs += ReadingParagraph(bookTitle.take(120), heading = true)
    for (href in paths) {
        val decodedHref = runCatching { java.net.URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        val path = resolveZipPath(opf, decodedHref)
        val bytes = zip.readEntryLimited(path, minOf(FileLimits.CHAPTER_BYTES, FileLimits.TEXT_BYTES - totalBytes)) ?: continue
        totalBytes += bytes.size
        val html = bytes.toString(Charsets.UTF_8)
        val imageDir = File(file.parentFile, "${file.nameWithoutExtension}-images").apply { mkdirs() }
        fun imageResolver(source: String): String? {
            val decodedSource = runCatching {
                java.net.URLDecoder.decode(source.substringBefore('#'), "UTF-8")
            }.getOrDefault(source.substringBefore('#'))
            val imagePath = resolveZipPath(path, decodedSource)
            val entry = zip.getEntry(imagePath) ?: return null
            val target = File(imageDir, MessageDigest.getInstance("SHA-256").digest(imagePath.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) } + ".img")
            if (!target.exists()) zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            return target.absolutePath
        }
        val section = paragraphsFromHtml(html, ::imageResolver)
        if (section.isEmpty()) continue
        val navigationTitle = navigationLabels[path]?.takeUnless(::epubNavigationTitleIsFrontMatter)
        val hasRealChapter = section.any(ReadingParagraph::chapterStart)
        when {
            hasRealChapter -> paragraphs += section
            navigationTitle != null -> {
                paragraphs += ReadingParagraph(navigationTitle.take(90), heading = true, chapterStart = true)
                paragraphs += section
            }
            else -> {
                val htmlTitle = Jsoup.parse(html).title().trim()
                    .takeIf { it.isNotBlank() && !it.equals("untitled", true) }
                    ?.takeUnless(::epubNavigationTitleIsFrontMatter)
                val pathLooksLikeChapter = path.substringAfterLast('/').contains(
                    Regex("(?i)(?:chapter|chap|capitulo|cap[ií]tulo|seccion|section|part|parte)[_-]?\\d+"))
                if (htmlTitle != null && (pathLooksLikeChapter ||
                        Regex("(?i)^(?:cap[ií]tulo|chapter|parte|part|pr[oó]logo|ep[ií]logo)\\b")
                            .containsMatchIn(htmlTitle))) {
                    paragraphs += ReadingParagraph(htmlTitle.take(90), heading = true, chapterStart = true)
                }
                paragraphs += section
            }
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

private fun readFb2Text(file: File): ReadingDocument {
    val xml = Jsoup.parse(file.inputStream().use { it.readLimited(FileLimits.TEXT_BYTES).toString(Charsets.UTF_8) }, "", Parser.xmlParser())
    val paragraphs = xml.select("body section title p, body section p, body > title p")
        .mapNotNull { element ->
            element.text().trim().replace(Regex("\\s+"), " ").takeIf { it.isNotBlank() }?.let { text ->
                val heading = element.parent()?.tagName()?.equals("title", true) == true
                ReadingParagraph(text, heading = heading, chapterStart = heading)
            }
        }
    return if (paragraphs.isEmpty()) ReadingDocument.Unsupported("No se encontró texto legible en este FB2")
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
        "fb2" -> readFb2Text(file)
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
    paragraph: ReadingParagraph, index: Int, start: Int, end: Int, reservedHeightPx: Int, size: Float, lineSpacing: Float, foreground: Color,
    dark: Boolean, opening: Boolean, highlightColor: String, highlights: List<ReaderHighlight>,
    onHighlight: (ReaderHighlight) -> Unit, onNote: (ReaderHighlight) -> Unit,
    onRemoveHighlight: (Int, Int, Int) -> Boolean,
    onLookup: (String) -> Unit, onTranslate: (String) -> Unit, onSpeak: (String) -> Unit,
    onSearch: (String) -> Unit, onSaveToNotebook: (String) -> Unit,
    onBookmarkChar: (Int, Int) -> Unit
) {
    val action by rememberUpdatedState(onHighlight)
    val noteAction by rememberUpdatedState(onNote)
    val removeHighlightAction by rememberUpdatedState(onRemoveHighlight)
    val lookupAction by rememberUpdatedState(onLookup)
    val bookmarkAction by rememberUpdatedState(onBookmarkChar)
    if (paragraph.imagePath != null) {
        val density = LocalDensity.current
        val targetWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
        val bitmap by produceState<Bitmap?>(null, paragraph.imagePath, reservedHeightPx, targetWidthPx) {
            value = withContext(Dispatchers.IO) { runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(paragraph.imagePath, bounds)
                val sample = imageSampleSize(
                    bounds.outWidth, bounds.outHeight,
                    targetWidthPx.coerceAtLeast(1), reservedHeightPx.coerceAtLeast(1)
                )
                BitmapFactory.decodeFile(paragraph.imagePath,
                    BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull() }
        }
        if (bitmap != null) {
            // Las ilustraciones deben conservar una escala editorial razonable:
            // no ocupar todo el ancho como una portada ni deformarse al paginar.
            // El ancho limitado deja margen de texto alrededor, como en los
            // lectores EPUB convencionales, y el alto se calcula manteniendo
            // la proporción original.
            val reservedHeight = with(density) {
                reservedHeightPx.coerceAtLeast(1).toDp()
            }
            Box(
                Modifier.fillMaxWidth().height(reservedHeight),
                contentAlignment = Alignment.Center
            ) {
                Image(bitmap!!.asImageBitmap(), "Imagen del libro",
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit)
            }
        }
        return
    }
    AndroidView(modifier = Modifier.fillMaxWidth(), factory = { context ->
        TextView(context).apply {
            setTextIsSelectable(true)
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            val selectable = this
            setCustomSelectionActionModeCallback(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(0, 8001, 0, "Subrayar").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add(0, 8002, 1, "Diccionario").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add(0, 8005, 2, "Nota").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(0, 8010, 3, "Copiar").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(0, 8011, 4, "Fijar").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    val selectedStart = selectable.selectionStart.coerceAtLeast(0)
                    val selectedEnd = selectable.selectionEnd.coerceAtMost(selectable.text.length)
                    val selectedFrom = start + selectedStart
                    val selectedTo = start + selectedEnd
                    val marked = selectedEnd > selectedStart && highlights.any { highlight ->
                        highlight.paragraph == index && highlight.start < selectedTo &&
                            highlight.end > selectedFrom
                    }
                    menu.findItem(8001)?.title = if (marked) "Quitar subrayado" else "Subrayar"
                    return true
                }
                override fun onDestroyActionMode(mode: ActionMode) = Unit
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (item.itemId !in setOf(8001, 8002, 8005, 8010, 8011)) return false
                    val selectedStart = selectable.selectionStart.coerceAtLeast(0)
                    val selectedEnd = selectable.selectionEnd.coerceAtMost(selectable.text.length)
                    if (selectedEnd <= selectedStart) return false
                    val quote = selectable.text.subSequence(selectedStart, selectedEnd).toString().trim()
                    if (quote.isBlank()) return false
                    when (item.itemId) {
                        8001 -> {
                            val removed = removeHighlightAction(index, start + selectedStart,
                                start + selectedEnd)
                            if (!removed) action(ReaderHighlight(index, start + selectedStart,
                                start + selectedEnd, quote, highlightColor))
                        }
                        8005 -> noteAction(ReaderHighlight(index, start + selectedStart,
                            start + selectedEnd, quote))
                        8010 -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Texto del libro", quote))
                        }
                        8011 -> bookmarkAction(index, start + selectedStart)
                        8002 -> {
                            val word = quote.split(Regex("\\s+")).first().trim('¿', '¡', '.', ',', ';',
                                ':', '!', '?', '«', '»', '"', '\'')
                            if (word.isNotBlank()) lookupAction(word)
                        }
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
        view.setLineSpacing(0f, lineSpacing)
        view.justificationMode = Layout.JUSTIFICATION_MODE_NONE
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
    val keepAwakeActivity = remember(context) { context.findActivity() }
    var keepScreenAwakeMinutes by remember {
        mutableIntStateOf(prefs.getInt("keep_screen_awake_minutes", 15))
    }
    var lastReaderInteraction by remember(book.uri) {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
    LaunchedEffect(keepAwakeActivity, keepScreenAwakeMinutes, lastReaderInteraction) {
        val window = keepAwakeActivity?.window ?: return@LaunchedEffect
        val keepScreenOnFlag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (keepScreenAwakeMinutes == 0) {
            window.clearFlags(keepScreenOnFlag)
            return@LaunchedEffect
        }
        window.addFlags(keepScreenOnFlag)
        if (keepScreenAwakeMinutes > 0) {
            val timeoutMs = keepScreenAwakeMinutes * 60_000L
            val elapsedMs = SystemClock.elapsedRealtime() - lastReaderInteraction
            val remainingMs = (timeoutMs - elapsedMs).coerceAtLeast(0L)
            if (remainingMs > 0L) delay(remainingMs)
            if (SystemClock.elapsedRealtime() - lastReaderInteraction >= timeoutMs) {
                window.clearFlags(keepScreenOnFlag)
            }
        }
    }
    DisposableEffect(keepAwakeActivity) {
        onDispose {
            keepAwakeActivity?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val key = remember(book.uri) { readerProgressKey(book.uri) }
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 18f)) }
    var readerMargin by remember { mutableFloatStateOf(prefs.getFloat("reader_margin", 22f)) }
    var lineSpacing by remember { mutableFloatStateOf(prefs.getFloat("line_spacing", 1.45f)) }
    var lockOrientation by remember(book.uri) {
        mutableStateOf(prefs.getBoolean("lock_orientation_$key", false))
    }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    DisposableEffect(keepAwakeActivity, dark) {
        val window = keepAwakeActivity?.window
        if (window != null) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !dark
        }
        onDispose {
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = false
            }
        }
    }
    var sepia by remember { mutableStateOf(prefs.getBoolean("sepia", false)) }
    DisposableEffect(lockOrientation) {
        val activity = context as? Activity
        if (lockOrientation) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (lockOrientation) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    var page by remember(book.uri) { mutableIntStateOf(prefs.getInt("page_$key", 0)) }
    val positionHistory = remember(book.uri) {
        mutableStateListOf<Int>().also { list ->
            prefs.getString("position_history_$key", "").orEmpty()
                .split(",").mapNotNullTo(list) { it.toIntOrNull() }
        }
    }
    val scope = rememberCoroutineScope()
    val swipeDistance = with(LocalDensity.current) { 64.dp.toPx() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var jumpToItem by remember(book.uri) { mutableIntStateOf(-1) }
    var showReaderTools by rememberSaveable(book.uri) { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
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
    fun rememberPosition(position: Int) {
        val safe = position.coerceAtLeast(0)
        if (positionHistory.lastOrNull() != safe) {
            positionHistory.removeAll { it == safe }
            positionHistory.add(safe)
            while (positionHistory.size > 30) positionHistory.removeAt(0)
            prefs.edit().putString("position_history_$key", positionHistory.joinToString(",")).apply()
        }
    }
    var highlights by remember(book.uri) {
        mutableStateOf(readHighlights(prefs.getString("highlights_$key", "[]")))
    }
    var highlightColor by remember(book.uri) {
        mutableStateOf(prefs.getString("highlight_color", "amarillo") ?: "amarillo")
    }
    var pendingNote by remember { mutableStateOf<ReaderHighlight?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var dictionaryWord by remember { mutableStateOf<String?>(null) }
    var translationQuote by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechReady by remember { mutableStateOf(false) }
    var speechInitError by remember { mutableStateOf<String?>(null) }
    var audioActive by remember(book.uri) { mutableStateOf(false) }
    var audioControlsVisible by remember(book.uri) { mutableStateOf(false) }
    var audioCursor by remember(book.uri) {
        mutableIntStateOf(prefs.getInt("char_$key", 0))
    }
    fun ensurePlaybackNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) keepAwakeActivity?.let {
            ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2401)
        }
    }
    var speechRate by remember(book.uri) {
        mutableStateOf(prefs.getFloat("speech_rate", 1f).coerceIn(0.5f, 2f))
    }
    val activeSpeechCursor = remember(book.uri) {
        AtomicInteger(prefs.getInt("char_$key", 0))
    }
    val speechGeneration = remember(book.uri) { AtomicInteger(0) }
    var speechRestartRequest by remember(book.uri) { mutableIntStateOf(0) }
    fun saveSpeechCursor(position: Int) {
        val safePosition = position.coerceAtLeast(0)
        activeSpeechCursor.set(safePosition)
        audioCursor = safePosition
        prefs.edit().putInt("char_$key", safePosition).apply()
    }
    fun persistSpeechCursorNow(position: Int = activeSpeechCursor.get()) {
        val safePosition = position.coerceAtLeast(0)
        activeSpeechCursor.set(safePosition)
        audioCursor = safePosition
        prefs.edit().putInt("char_$key", safePosition).commit()
    }
    fun saveReadingCursor(position: Int) {
        prefs.edit().putInt("reading_char_$key", position.coerceAtLeast(0)).apply()
    }
    fun currentSpeechPosition(): Int {
        return activeSpeechCursor.get()
    }
    fun pauseSpeech() {
        // Ignore callbacks delivered late by the utterance that is being stopped.
        if (audioActive) persistSpeechCursorNow()
        speechGeneration.incrementAndGet()
        speech?.stop()
        audioActive = false
    }
    fun startSpeech() {
        ensurePlaybackNotificationPermission()
        audioControlsVisible = true
        audioActive = true
        // A dedicated restart signal avoids relying on incidental state changes. Android's
        // TTS engine may remain initialized after stop(), but it needs a fresh speak() call.
        speechRestartRequest++
    }
    fun changeSpeechRate(delta: Float) {
        if (audioActive) {
            speechGeneration.incrementAndGet()
            persistSpeechCursorNow()
            speech?.stop()
        }
        speechRate = ((((speechRate + delta) * 10f).toInt()) / 10f).coerceIn(0.5f, 2f)
        prefs.edit().putFloat("speech_rate", speechRate).apply()
        if (audioActive) speechRestartRequest++
    }
    var audioSeekRequest by remember(book.uri) { mutableIntStateOf(0) }
    var audioSeekDelta by remember(book.uri) { mutableIntStateOf(0) }
    var audioAdvance by remember(book.uri) { mutableIntStateOf(0) }
    fun requestAudioSeek(delta: Int) {
        audioSeekDelta = delta
        audioSeekRequest++
    }
    DisposableEffect(context, book.uri) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                when (intent?.action) {
                    ReaderPlaybackService.ACTION_PLAY -> {
                        startSpeech()
                    }
                    ReaderPlaybackService.ACTION_PAUSE -> pauseSpeech()
                    ReaderPlaybackService.ACTION_STOP -> pauseSpeech()
                    ReaderPlaybackService.ACTION_FORWARD ->
                        requestAudioSeek(speechSeekDistance(15, speechRate))
                    ReaderPlaybackService.ACTION_REWIND ->
                        requestAudioSeek(-speechSeekDistance(15, speechRate))
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ReaderPlaybackService.ACTION_PLAY)
            addAction(ReaderPlaybackService.ACTION_PAUSE)
            addAction(ReaderPlaybackService.ACTION_STOP)
            addAction(ReaderPlaybackService.ACTION_FORWARD)
            addAction(ReaderPlaybackService.ACTION_REWIND)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
            context.stopService(Intent(context, ReaderPlaybackService::class.java))
        }
    }
    LaunchedEffect(audioActive, audioControlsVisible, speechRate, book.uri) {
        if (audioActive || audioControlsVisible) {
            val update = Intent(context, ReaderPlaybackService::class.java)
                .setAction(ReaderPlaybackService.ACTION_UPDATE)
                .putExtra(ReaderPlaybackService.EXTRA_TITLE, book.title)
                .putExtra(ReaderPlaybackService.EXTRA_AUTHOR, book.author)
                .putExtra(ReaderPlaybackService.EXTRA_POSITION, audioCursor.toLong())
                .putExtra(ReaderPlaybackService.EXTRA_SPEED, speechRate)
                .putExtra(ReaderPlaybackService.EXTRA_PLAYING, audioActive)
            ContextCompat.startForegroundService(context, update)
        }
    }
    var showVoicePicker by remember { mutableStateOf(false) }
    var selectedVoiceName by remember { mutableStateOf(prefs.getString("voice_name", "").orEmpty()) }
    DisposableEffect(context, book.uri, selectedVoiceName) {
        var engine: TextToSpeech? = null
        var disposed = false
        speechReady = false
        speechInitError = null
        engine = TextToSpeech(context, { status ->
            Handler(Looper.getMainLooper()).post {
                if (disposed) return@post
                speechReady = status == TextToSpeech.SUCCESS
                if (status == TextToSpeech.SUCCESS) {
                    engine?.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    if (audioActive) speechRestartRequest++
                } else {
                    if (selectedVoiceName == "davefx") {
                        // An installation can remain registered even when Android can no longer
                        // start it. Fall back immediately instead of leaving the play button mute.
                        prefs.edit().remove("voice_name").apply()
                        selectedVoiceName = ""
                        speechInitError = "Davefx no respondió. Se ha activado la voz de Android."
                    } else {
                        speechInitError = "No se pudo iniciar la voz de Android. Revisa el motor de texto a voz."
                    }
                }
            }
        }, if (selectedVoiceName == "davefx") DAVEFX_ENGINE else null)
        speech = engine
        onDispose {
            disposed = true
            // Persist the last word reported by Android before shutting the engine down.
            prefs.edit().putInt("char_$key", activeSpeechCursor.get()).apply()
            engine?.stop()
            engine?.shutdown()
            if (speech === engine) speech = null
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
    LaunchedEffect(background, dark) {
        keepAwakeActivity?.window?.apply {
            statusBarColor = background.toArgb()
            navigationBarColor = background.toArgb()
            val lightBars = !dark
            decorView.systemUiVisibility = if (lightBars)
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val appearance = if (lightBars)
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0
                decorView.windowInsetsController?.setSystemBarsAppearance(appearance,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
        }
    }
    val state by produceState<ReadingDocument?>(null, book.uri) {
        value = try { withContext(Dispatchers.IO) { loadReadingDocument(context, book) } }
        catch (e: Exception) { ReadingDocument.Unsupported(e.localizedMessage ?: "No se pudo abrir") }
    }
    val document = state
    BackHandler { if (drawer.isOpen) scope.launch { drawer.close() } else confirmExit = true }
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = drawer.isOpen, drawerContent = {
        ModalDrawerSheet(
            modifier = Modifier.width(320.dp),
            drawerContainerColor = Color(0xFFF8F3E9),
            drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(
                topEnd = 24.dp, bottomEnd = 24.dp)
        ) {
            Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 22.dp)) {
                Text(AppBrand.NAME, color = Color(0xFF8A623C),
                    style = MaterialTheme.typography.labelLarge)
                val openingTitle = book.customTitle.ifBlank { book.title }
                Text(openingTitle,
                    color = Color(0xFF392A24),
                    fontSize = when {
                        openingTitle.length > 70 -> 16.sp
                        openingTitle.length > 46 -> 18.sp
                        else -> 21.sp
                    },
                    lineHeight = when {
                        openingTitle.length > 70 -> 18.sp
                        openingTitle.length > 46 -> 21.sp
                        else -> 24.sp
                    },
                    maxLines = 6,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth())
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
                                if (audioActive) pauseSpeech() else startSpeech()
                            } else {
                                android.widget.Toast.makeText(context,
                                    speechInitError ?: "Preparando la voz. Vuelve a pulsar en unos segundos.",
                                    android.widget.Toast.LENGTH_LONG).show()
                            }
                            scope.launch { drawer.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFFE7DCC8),
                            selectedTextColor = Color(0xFF503727)))
                    NavigationDrawerItem(label = { Text("Detener lectura") }, selected = false,
                        icon = { AppIcon("Cerrar") },
                        onClick = {
                            pauseSpeech()
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
                NavigationDrawerItem(label = { Text("Bloquear orientación") },
                    selected = lockOrientation,
                    icon = { AppIcon("Ajustes", "Orientación") },
                    onClick = {
                        lockOrientation = !lockOrientation
                        prefs.edit().putBoolean("lock_orientation_$key", lockOrientation).apply()
                    })
                Spacer(Modifier.height(8.dp))
                Text("Perfiles", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        fontSize = 17f; dark = false; sepia = false; brightness = -1f
                        prefs.edit().putFloat("font_size", fontSize).putBoolean("dark", false)
                            .putBoolean("sepia", false).putFloat("brightness", brightness).apply()
                    }) { Text("Día") }
                    TextButton(onClick = {
                        fontSize = 19f; dark = false; sepia = true; brightness = .62f
                        prefs.edit().putFloat("font_size", fontSize).putBoolean("dark", false)
                            .putBoolean("sepia", true).putFloat("brightness", brightness).apply()
                    }) { Text("Cómodo") }
                    TextButton(onClick = {
                        fontSize = 20f; dark = true; sepia = false; brightness = .28f
                        prefs.edit().putFloat("font_size", fontSize).putBoolean("dark", true)
                            .putBoolean("sepia", false).putFloat("brightness", brightness).apply()
                    }) { Text("Noche") }
                }
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
                Text("Color del próximo subrayado", color = Color(0xFF785940),
                    style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("amarillo" to 0xFFFFE58A, "azul" to 0xFFACEEF5,
                        "rosa" to 0xFFFFB7C5, "verde" to 0xFFB8E6B8).forEach { (name, color) ->
                        FilterChip(selected = highlightColor == name, onClick = {
                            highlightColor = name
                            prefs.edit().putString("highlight_color", name).apply()
                        }, label = { Text(name.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                            leadingIcon = { Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(color))) })
                    }
                }
                if (document is ReadingDocument.TextDocument)
                    OutlinedTextField(searchText, { searchText = it }, singleLine = true,
                        label = { Text("Buscar en el libro") },
                        modifier = Modifier.fillMaxWidth())
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("CAPÍTULOS Y MARCAS", color = Color(0xFF785940),
                    style = MaterialTheme.typography.labelMedium)
                val chapters = if (document is ReadingDocument.TextDocument)
                    readerChapters(document.paragraphs)
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
                                    jumpToItem = chapter.paragraphIndex; scope.launch { drawer.close() }
                                }) { Text(chapter.title, maxLines = 2) }
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
                }, valueRange = 0.05f..1f,
                    modifier = Modifier.semantics { contentDescription = "Brillo de lectura" })
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
        TopAppBar(title = { Text(book.customTitle.ifBlank { book.title },
                maxLines = 1, fontSize = 17.sp, lineHeight = 19.sp,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { AppIcon("Volver", "Volver") } },
            actions = {}, colors = TopAppBarDefaults.topAppBarColors(containerColor = background,
                titleContentColor = foreground, navigationIconContentColor = foreground,
                actionIconContentColor = foreground))
    }) { padding ->
      Box(Modifier.fillMaxSize().pointerInput(document, showReaderTools, drawer.isOpen) {
          awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
              lastReaderInteraction = SystemClock.elapsedRealtime()
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
                  val closeDrawerSwipe = drawer.isOpen && horizontal <= -72.dp.toPx() &&
                      kotlin.math.abs(vertical) < 54.dp.toPx()
                  val centerTap = document is ReadingDocument.TextDocument &&
                      start.x in size.width * 0.30f..size.width * 0.70f &&
                      start.y in size.height * 0.30f..size.height * 0.75f
                  val still = kotlin.math.abs(horizontal) < 14.dp.toPx() &&
                      kotlin.math.abs(vertical) < 14.dp.toPx()
                  if (edgeSwipe) scope.launch { drawer.open() }
                  else if (closeDrawerSwipe) scope.launch { drawer.close() }
                  else if (centerTap && still && releaseTime - down.uptimeMillis < 350L)
                      showReaderTools = true
              }
          }
      }) {
        when (document) {
            null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Preparando libro…", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (book.uri.scheme == "content") "Leyendo el archivo y preparando sus capítulos"
                        else "Preparando el contenido para continuar donde lo dejaste",
                        fontSize = 13.sp,
                        color = foreground.copy(alpha = 0.72f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            is ReadingDocument.Unsupported -> Box(Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon("Aviso", size = 34.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("No se pudo abrir este libro", color = foreground,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(document.reason, color = foreground.copy(alpha = 0.78f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = {
                        val mime = when (book.uri.lastPathSegment.orEmpty().substringAfterLast('.').lowercase()) {
                            "mobi", "azw", "azw3", "fb2" -> "application/x-mobipocket-ebook"
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
                    TextButton(onClick = onBack) { Text("Volver a ${AppBrand.NAME}") }
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
                            (slideInHorizontally(tween(360)) { if (forward) it / 5 else -it / 5 } +
                                fadeIn(tween(180))).togetherWith(
                                slideOutHorizontally(tween(360)) { if (forward) -it / 5 else it / 5 } +
                                    fadeOut(tween(180))).using(SizeTransform(clip = true))
                        }, label = "Pasar página PDF") { shown ->
                            val angle by transition.animateFloat(transitionSpec = { tween(360) },
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
                        Box(Modifier.align(Alignment.CenterStart).width(48.dp).fillMaxHeight()
                            .semantics { contentDescription = "Página anterior"; role = Role.Button }
                            .clickable { page = (index - 1).coerceAtLeast(0) })
                        Box(Modifier.align(Alignment.CenterEnd).width(48.dp).fillMaxHeight()
                            .semantics { contentDescription = "Página siguiente"; role = Role.Button }
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
                var currentChapterTitle by remember(book.uri) { mutableStateOf("") }
                val chapterNavigation = remember(document) { readerChapters(paragraphs) }
                Column(Modifier.padding(padding).fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val density = LocalDensity.current
                        val width = with(density) { (maxWidth - (readerMargin * 2).dp).roundToPx() }
                        // Keep the progress strip visually separate while using the reading area efficiently.
                        val height = with(density) { maxHeight.roundToPx() }
                        val textSizePx = with(density) { fontSize.sp.toPx() }
                        // Debe coincidir con el espacio real entre párrafos para no reservar
                        // líneas invisibles al final de cada página.
                        val gap = with(density) { 6.dp.roundToPx() }
                        val pages by produceState<List<ReadingPage>>(emptyList(), document,
                            width, height, fontSize, readerMargin, lineSpacing) {
                            ready = false
                            value = emptyList()
                            value = withContext(Dispatchers.Default) {
                                paginateText(paragraphs, width, height.coerceAtLeast(1), textSizePx, gap, lineSpacing)
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
                            val savedChar = prefs.getInt("reading_char_$key",
                                prefs.getInt("char_$key", positions[legacyItem])).coerceIn(0, total - 1)
                            audioCursor = prefs.getInt("char_$key", savedChar).coerceIn(0, total - 1)
                            textPage = readerPageForChar(pages.map { it.startChar }, savedChar)
                                .coerceIn(pages.indices)
                            ready = true
                        }
                        LaunchedEffect(jumpToItem, pages) {
                            if (jumpToItem >= 0) {
                                val target = positions[jumpToItem.coerceIn(0, paragraphs.lastIndex)]
                                textPage = readerPageForChar(pages.map { it.startChar }, target)
                                    .coerceIn(pages.indices)
                                saveReadingCursor(target)
                                saveSpeechCursor(target)
                                jumpToItem = -1
                            }
                        }
                        LaunchedEffect(textPage, pages, ready) {
                            if (ready) {
                                val current = pages[textPage.coerceIn(pages.indices)]
                                saveReadingCursor(current.startChar)
                                percent = if (textPage >= pages.lastIndex) 100
                                    else readerProgressPercent(current.startChar, total)
                                currentChapterTitle = chapterNavigation.getOrNull(
                                    readerChapterIndexAt(chapterNavigation, current.startChar)
                                )?.title.orEmpty()
                                prefs.edit().putInt("percent_$key", percent).apply()
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
                        val currentChapterIndex = readerChapterIndexAt(
                            chapterNavigation, pages[currentPage].startChar)
                        val currentChapter = chapterNavigation.getOrNull(currentChapterIndex)
                        LaunchedEffect(audioAdvance, pages) {
                            if (audioAdvance > 0 && audioActive) {
                                if (textPage < pages.lastIndex) {
                                    textPage++
                                    saveSpeechCursor(pages[textPage].startChar)
                                } else audioActive = false
                            }
                        }
                        LaunchedEffect(audioActive, currentPage, pages, speechReady, selectedVoiceName,
                            audioSeekRequest, speechRate, speechRestartRequest) {
                            val engine = speech
                            if (!audioActive || !speechReady || engine == null) {
                                engine?.stop()
                            } else {
                                val locale = if (book.language.startsWith("en", true))
                                    java.util.Locale.ENGLISH else java.util.Locale.forLanguageTag("es-ES")
                                val availability = engine.setLanguage(locale)
                                engine.setSpeechRate(speechRate)
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
                                    var startCursor = speechCursorForVisiblePage(
                                        audioCursor,
                                        pageData.startChar,
                                        pages.getOrNull(currentPage + 1)?.startChar ?: total,
                                        total
                                    )
                                    val spokenPage = speechPage(pageData, paragraphs, positions)
                                    var offsetInPage = speechOffsetForChar(spokenPage, startCursor)
                                        .coerceIn(0, spokenPage.text.length)
                                    // Some EPUBs leave a small gap between the last readable
                                    // character and the next page. Never treat that gap as silence.
                                    if (offsetInPage >= spokenPage.text.length ||
                                        spokenPage.text.substring(offsetInPage).isBlank()) {
                                        startCursor = pageData.startChar
                                        offsetInPage = 0
                                    }
                                    if (startCursor != audioCursor) saveSpeechCursor(startCursor)
                                    val spoken = spokenPage.text.substring(offsetInPage)
                                    val spokenBaseOffset = offsetInPage
                                    val utteranceGeneration = speechGeneration.incrementAndGet()
                                    val chunks = speechWordChunks(spoken)
                                    fun speakChunk(index: Int, queueMode: Int) {
                                        if (speechGeneration.get() != utteranceGeneration) return
                                        val chunk = chunks.getOrNull(index) ?: return
                                        val result = engine.speak(chunk.text, queueMode, null,
                                            "page_${currentPage}_generation_${utteranceGeneration}_chunk_$index")
                                        if (result == TextToSpeech.ERROR) {
                                            audioActive = false
                                            android.widget.Toast.makeText(context,
                                                "La voz no pudo reproducir este fragmento. Prueba a elegir otra voz.",
                                                android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                        override fun onStart(utteranceId: String?) {
                                            if (speechGeneration.get() != utteranceGeneration) return
                                            val chunkIndex = utteranceId?.substringAfterLast('_')?.toIntOrNull()
                                                ?: return
                                            val chunk = chunks.getOrNull(chunkIndex) ?: return
                                            val fallbackPosition = speechCharForOffset(
                                                spokenPage, spokenBaseOffset + chunk.start, total)
                                            Handler(Looper.getMainLooper()).post {
                                                if (speechGeneration.get() == utteranceGeneration) {
                                                    saveSpeechCursor(fallbackPosition)
                                                    percent = readerProgressPercent(fallbackPosition, total)
                                                }
                                            }
                                        }
                                        override fun onRangeStart(utteranceId: String?, start: Int, end: Int,
                                            frame: Int) {
                                            if (speechGeneration.get() != utteranceGeneration) return
                                            val chunkIndex = utteranceId?.substringAfterLast('_')?.toIntOrNull()
                                                ?: return
                                            val chunk = chunks.getOrNull(chunkIndex) ?: return
                                            val local = start.coerceIn(0, chunk.text.length)
                                            val absolute = (spokenBaseOffset + chunk.start + local)
                                                .coerceIn(0, spokenPage.text.length)
                                            var wordStart = absolute
                                            while (wordStart > 0 && !spokenPage.text[wordStart - 1].isWhitespace())
                                                wordStart--
                                            val sourcePosition = speechCharForOffset(spokenPage, wordStart, total)
                                            Handler(Looper.getMainLooper()).post {
                                                if (speechGeneration.get() == utteranceGeneration) {
                                                    activeSpeechCursor.set(sourcePosition)
                                                    audioCursor = sourcePosition
                                                    percent = readerProgressPercent(sourcePosition, total)
                                                    currentChapterTitle = chapterNavigation.getOrNull(
                                                        readerChapterIndexAt(chapterNavigation, sourcePosition)
                                                    )?.title.orEmpty()
                                                    prefs.edit().putInt("char_$key", sourcePosition)
                                                        .putInt("percent_$key", percent)
                                                        .apply()
                                                }
                                            }
                                        }
                                        override fun onDone(utteranceId: String?) {
                                            if (speechGeneration.get() != utteranceGeneration) return
                                            Handler(Looper.getMainLooper()).post {
                                                if (speechGeneration.get() != utteranceGeneration) return@post
                                                val finishedChunk = utteranceId?.substringAfterLast('_')?.toIntOrNull()
                                                val nextChunk = (finishedChunk ?: (chunks.size - 1)) + 1
                                                if (nextChunk < chunks.size) {
                                                    speakChunk(nextChunk, TextToSpeech.QUEUE_ADD)
                                                } else {
                                                    val nextOffset = spokenBaseOffset +
                                                        (chunks.lastOrNull()?.let { it.start + it.text.length } ?: 0)
                                                    if (nextOffset < spokenPage.text.length) {
                                                        val nextPosition = speechCharForOffset(spokenPage,
                                                            nextOffset, total)
                                                        saveSpeechCursor(nextPosition)
                                                        audioActive = true
                                                        audioAdvance++
                                                    } else if (currentPage < pages.lastIndex) {
                                                        Handler(Looper.getMainLooper()).postDelayed({
                                                            if (speechGeneration.get() == utteranceGeneration && audioActive) {
                                                                val nextPosition = pages[currentPage + 1].startChar
                                                                saveSpeechCursor(nextPosition)
                                                                audioAdvance++
                                                            }
                                                        }, 450L)
                                                    } else {
                                                        saveSpeechCursor(total - 1)
                                                        audioActive = false
                                                    }
                                                }
                                            }
                                        }
                                        override fun onError(utteranceId: String?) {
                                            if (speechGeneration.get() != utteranceGeneration) return
                                            Handler(Looper.getMainLooper()).post {
                                                if (speechGeneration.get() == utteranceGeneration) {
                                                    audioActive = false
                                                    android.widget.Toast.makeText(context,
                                                        "La lectura en voz alta se ha detenido. Prueba a elegir otra voz.",
                                                        android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    })
                                    if (chunks.isNotEmpty()) speakChunk(0, TextToSpeech.QUEUE_FLUSH)
                                    else audioActive = false
                                }
                            }
                        }
                        fun turn(delta: Int) {
                            rememberPosition(pages[textPage].startChar)
                            val targetPage = (textPage + delta).coerceIn(pages.indices)
                            textPage = targetPage
                            // Manual page turns must also become the exact resume point. This was
                            // previously saved only while the audio controls happened to be open.
                            saveSpeechCursor(pages[targetPage].startChar)
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
                            (slideInHorizontally(tween(360)) { if (forward) it / 5 else -it / 5 } +
                                        fadeIn(tween(180))).togetherWith(
                                        slideOutHorizontally(tween(360)) { if (forward) -it / 5 else it / 5 } +
                                            fadeOut(tween(180))).using(SizeTransform(clip = true))
                                }, label = "Pasar página") { shown ->
                                val angle by transition.animateFloat(transitionSpec = { tween(360) },
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
                                    }.background(background).padding(horizontal = readerMargin.dp, vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pages[shown].slices.forEachIndexed { position, slice ->
                                        if (position == 0 && slice.start == 0 &&
                                            paragraphs[slice.paragraph].chapterStart)
                                            Spacer(Modifier.height(28.dp))
                                        SelectableParagraph(paragraphs[slice.paragraph], slice.paragraph,
                                            slice.start, slice.end, slice.height, fontSize, lineSpacing, foreground, dark,
                                            openingParagraph(paragraphs, slice.paragraph), highlightColor, highlights,
                                            onHighlight = { mark ->
                                                highlights = (highlights + mark).distinct()
                                                saveHighlights(context, key, highlights)
                                                onHighlightsChanged()
                                            }, onRemoveHighlight = { paragraphIndex, from, to ->
                                                val updated = highlights.filterNot { mark ->
                                                    mark.paragraph == paragraphIndex && mark.start < to && mark.end > from
                                                }
                                                val removed = updated.size != highlights.size
                                                if (removed) {
                                                    highlights = updated
                                                    saveHighlights(context, key, highlights)
                                                    onHighlightsChanged()
                                                }
                                                removed
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
                            Box(Modifier.align(Alignment.CenterStart).width(48.dp).fillMaxHeight()
                                .semantics { contentDescription = "Página anterior"; role = Role.Button }
                                .clickable { turn(-1) })
                            Box(Modifier.align(Alignment.CenterEnd).width(48.dp).fillMaxHeight()
                                .semantics { contentDescription = "Página siguiente"; role = Role.Button }
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
                                    Text(book.customTitle.ifBlank { book.title },
                                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                                        fontSize = if (book.customTitle.ifBlank { book.title }.length > 52) 17.sp else 20.sp,
                                        maxLines = 6,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth())
                                    Text("Página ${currentPage + 1} de ${pages.size} · $percent %",
                                        color = foreground.copy(alpha = 0.75f))
                                    Slider(value = requestedPage, onValueChange = { requestedPage = it },
                                        valueRange = 0f..pages.lastIndex.toFloat().coerceAtLeast(0f),
                                        modifier = Modifier.semantics {
                                            contentDescription = "Posición de lectura"
                                            stateDescription = pageDescription(requestedPage.toInt(), pages.size)
                                        },
                                        onValueChangeFinished = {
                                            val target = requestedPage.toInt().coerceIn(pages.indices)
                                            textPage = target
                                            saveSpeechCursor(pages[target].startChar)
                                            showReaderTools = false
                                        })
                                    if (chapterNavigation.isNotEmpty()) {
                                        Text(currentChapter?.title ?: "Inicio",
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2)
                                        Row(Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(
                                                enabled = currentChapterIndex > 0,
                                                onClick = {
                                                    val target = chapterNavigation[currentChapterIndex - 1]
                                                    textPage = readerPageForChar(
                                                        pages.map { it.startChar }, target.startChar)
                                                        .coerceIn(pages.indices)
                                                    saveReadingCursor(target.startChar)
                                                    saveSpeechCursor(target.startChar)
                                                    showReaderTools = false
                                                }) { Text("← Capítulo") }
                                            OutlinedButton(
                                                enabled = currentChapterIndex < chapterNavigation.lastIndex,
                                                onClick = {
                                                    val target = chapterNavigation[currentChapterIndex + 1]
                                                    textPage = readerPageForChar(
                                                        pages.map { it.startChar }, target.startChar)
                                                        .coerceIn(pages.indices)
                                                    saveReadingCursor(target.startChar)
                                                    saveSpeechCursor(target.startChar)
                                                    showReaderTools = false
                                                }) { Text("Capítulo →") }
                                        }
                                    }
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
                                    Text("Lectura", fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif, fontSize = 17.sp)
                                    Text("Mantener pantalla encendida",
                                        fontWeight = FontWeight.SemiBold)
                                    Text("El contador se reinicia con cada toque. Al agotarse, vuelve el ajuste de pantalla del teléfono.",
                                        fontSize = 12.sp, color = foreground.copy(alpha = 0.75f))
                                    Row(Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(0, 5, 10, 15, 20, 30, 45, 60, -1).forEach { minutes ->
                                            val label = when (minutes) {
                                                0 -> "Sistema"
                                                -1 -> "Siempre"
                                                else -> "$minutes min"
                                            }
                                            FilterChip(selected = keepScreenAwakeMinutes == minutes,
                                                onClick = {
                                                    keepScreenAwakeMinutes = minutes
                                                    lastReaderInteraction = SystemClock.elapsedRealtime()
                                                    prefs.edit().putInt("keep_screen_awake_minutes", minutes).apply()
                                                }, label = { Text(label, fontSize = 12.sp) })
                                        }
                                    }
                                    Text("Márgenes · ${readerMargin.toInt()} dp",
                                        color = foreground.copy(alpha = 0.78f))
                                    Slider(value = readerMargin, onValueChange = {
                                        readerMargin = it
                                        prefs.edit().putFloat("reader_margin", it).apply()
                                    }, valueRange = 12f..42f,
                                        modifier = Modifier.semantics { contentDescription = "Márgenes de lectura" })
                                    Text("Espaciado entre líneas · ${String.format(java.util.Locale.ROOT, "%.2f", lineSpacing)}",
                                        color = foreground.copy(alpha = 0.78f))
                                    Slider(value = lineSpacing, onValueChange = {
                                        lineSpacing = it
                                        prefs.edit().putFloat("line_spacing", it).apply()
                                    }, valueRange = 1.15f..1.8f,
                                        modifier = Modifier.semantics { contentDescription = "Espaciado entre líneas" })
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
                                        Row(Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = {
                                                textPage = targetPage
                                                saveSpeechCursor(charPosition)
                                                showReaderTools = false
                                            }, modifier = Modifier.weight(1f)) {
                                                Text("Marcador ${index + 1} · página ${targetPage + 1}",
                                                    modifier = Modifier.fillMaxWidth())
                                            }
                                            IconButton(onClick = { toggleBookmark(charPosition) }) {
                                                AppIcon("Borrar", "Eliminar marcador")
                                            }
                                        }
                                    }
                                    if (positionHistory.isNotEmpty()) {
                                        HorizontalDivider()
                                        Text("Historial de posiciones", fontWeight = FontWeight.Bold)
                                        positionHistory.asReversed().take(8).forEachIndexed { index, charPosition ->
                                            val targetPage = pages.indexOfLast { it.startChar <= charPosition }
                                                .coerceAtLeast(0)
                                            TextButton(onClick = {
                                                textPage = targetPage
                                                saveSpeechCursor(charPosition)
                                                showReaderTools = false
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Text("Posición ${index + 1} · página ${targetPage + 1}",
                                                    modifier = Modifier.fillMaxWidth())
                                            }
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
                    if (document is ReadingDocument.TextDocument && totalTextPages > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            color = background,
                            contentColor = foreground
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                if (currentChapterTitle.isNotBlank()) {
                                    Text(currentChapterTitle,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 11.sp,
                                        maxLines = 1)
                                }
                                LinearProgressIndicator(
                                    progress = { (percent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(2.dp).semantics {
                                        contentDescription = "Progreso de lectura"
                                        stateDescription = percentDescription("Lectura", percent)
                                    },
                                    color = if (dark) Color(0xFF8BC7D1) else Color(0xFF8A2D2D),
                                    trackColor = foreground.copy(alpha = 0.12f)
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Página ${textPage.coerceIn(0, totalTextPages - 1) + 1} de $totalTextPages", fontSize = 11.sp)
                                    Text("$percent %", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    if (audioControlsVisible) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                            color = if (dark) Color(0xFF302A25) else Color(0xFFF4EEE4),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(onClick = { changeSpeechRate(-0.1f) },
                                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                                        contentDescription = "Reducir velocidad de lectura"
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                                    Text("−", fontSize = 19.sp, lineHeight = 20.sp)
                                }
                                Text("Velocidad · ${String.format(java.util.Locale.ROOT, "%.1f", speechRate)}×",
                                    fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp).semantics {
                                        contentDescription = speechRateDescription(speechRate)
                                    })
                                TextButton(onClick = { changeSpeechRate(0.1f) },
                                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                                        contentDescription = "Aumentar velocidad de lectura"
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                                    Text("+", fontSize = 19.sp, lineHeight = 20.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                        contentDescription = "Retroceder quince segundos"
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = { requestAudioSeek(-speechSeekDistance(15, speechRate)) }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon("Retroceder", size = 17.dp)
                                        Text("−15 s", fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                        contentDescription = if (audioActive) "Pausar lectura" else "Reproducir lectura"
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = {
                                        if (audioActive) pauseSpeech() else startSpeech()
                                    }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon(if (audioActive) "Pausar" else "Reproducir", size = 19.dp)
                                        Text(if (audioActive) "Pausa" else "Play", fontSize = 9.sp)
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                        contentDescription = "Detener lectura"
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = {
                                        pauseSpeech()
                                        audioControlsVisible = false
                                        speech?.stop()
                                    }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon("Detener", size = 17.dp)
                                        Text("Stop", fontSize = 9.sp)
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                        contentDescription = "Avanzar quince segundos"
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = { requestAudioSeek(speechSeekDistance(15, speechRate)) }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon("Avanzar", size = 17.dp)
                                        Text("+15 s", fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.weight(1.15f).heightIn(min = 48.dp).semantics {
                                        contentDescription = "Continuar con la pantalla bloqueada"
                                    },
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
                                        AppIcon("Bloquear", size = 17.dp)
                                        Text("Pantalla", fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                            }
                            Text(
                                buildString {
                                    append("Escuchando · ").append(percent).append(" %")
                                    if (currentChapterTitle.isNotBlank())
                                        append(" · ").append(currentChapterTitle)
                                },
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            }
                        }
                    }
                }
            }
        }

      }
    }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Salir de la lectura") },
            text = { Text("¿Quieres cerrar este libro?") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Sí") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("No") } }
        )
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
                            if (audioActive) {
                                persistSpeechCursorNow()
                                speechGeneration.incrementAndGet()
                                speech?.stop()
                            }
                            selectedVoiceName = "davefx"
                            prefs.edit().putString("voice_name", "davefx").apply()
                            showVoicePicker = false
                        } else {
                            showVoicePicker = false
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse(DAVEFX_DOWNLOAD_URL)))
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context,
                                    "No se pudo abrir la página de Davefx.",
                                    android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    if (!hasDavefxEngine(context)) Text(
                        "Davefx es un motor de voz externo y opcional. Al elegirlo se abrirá la página oficial del proyecto sherpa-onnx en el navegador.",
                        style = MaterialTheme.typography.bodySmall)
                    RadioVoiceOption("Predeterminada", selectedVoiceName.isBlank()) {
                        if (audioActive) {
                            persistSpeechCursorNow()
                            speechGeneration.incrementAndGet()
                            speech?.stop()
                        }
                        selectedVoiceName = ""; prefs.edit().remove("voice_name").apply()
                        showVoicePicker = false
                    }
                    choices.forEach { voice ->
                        RadioVoiceOption(voice.name + " · " + voice.locale.toLanguageTag(),
                            selectedVoiceName == voice.name) {
                            if (audioActive) {
                                persistSpeechCursorNow()
                                speechGeneration.incrementAndGet()
                                speech?.stop()
                            }
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
        val definitions by produceState<List<DictionaryDefinition>?>(null, word) {
            value = withContext(Dispatchers.IO) { dictionaryDefinitions(context, word) }
        }
        ModalBottomSheet(onDismissRequest = { dictionaryWord = null },
            containerColor = background, contentColor = foreground) {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 30.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        speech?.apply {
                            language = java.util.Locale.forLanguageTag("es-ES")
                            speak(word, TextToSpeech.QUEUE_FLUSH, null, "dictionary_pronounce")
                        }
                    }) { Icon(Icons.Filled.VolumeUp, contentDescription = "Pronunciar $word") }
                    Text(word, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = foreground.copy(alpha = 0.06f))) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Diccionario", modifier = Modifier.weight(1f),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ExpandMore, contentDescription = "Mostrar definiciones")
                        }
                        Spacer(Modifier.height(12.dp))
                        when {
                            definitions == null -> Text("Buscando en Wikcionario y la RAE…")
                            definitions!!.isEmpty() -> Text(
                                "Para ver definiciones sin conexión, abre el diccionario español de Android o consulta la RAE.")
                            else -> definitions!!.forEachIndexed { i, definition ->
                                Column(Modifier.padding(bottom = 12.dp)) {
                                    if (i == 0 || definitions!![i - 1].source != definition.source)
                                        Text(definition.source, fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = foreground.copy(alpha = 0.72f))
                                    Text("${i + 1}. ${definition.text}", fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                        if (definitions == null || definitions!!.isEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Para ver las definiciones, seleccione un idioma y descargue el diccionario gratuito.",
                                fontSize = 16.sp, lineHeight = 24.sp)
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                    try { context.startActivity(Intent(
                                        android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                                    catch (_: Exception) { }
                                }) { Text("Español") }
                                FilledIconButton(onClick = {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://es.wiktionary.org/wiki/" +
                                            java.net.URLEncoder.encode(word, "UTF-8")))) }
                                    catch (_: Exception) { }
                                }) { Text("↓") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Consulta local de Wikcionario y acceso al DLE de la RAE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground.copy(alpha = 0.72f))
                when {
                    definitions == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> Unit
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        saveNotebookEntry("«$word» — " +
                            (definitions?.firstOrNull()?.text ?: "palabra consultada en el diccionario"))
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
// Se abre la página del proyecto, no un .apk directo: Google Play no permite
// que una app empuje a instalar aplicaciones descargadas fuera de la tienda.
private const val DAVEFX_DOWNLOAD_URL = "https://github.com/k2-fsa/sherpa-onnx"

private fun hasDavefxEngine(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(DAVEFX_ENGINE, 0)
    true
} catch (_: android.content.pm.PackageManager.NameNotFoundException) {
    false
}
