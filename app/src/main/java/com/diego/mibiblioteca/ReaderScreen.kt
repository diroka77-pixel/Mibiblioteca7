package com.diego.mibiblioteca

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

private sealed interface ReadingDocument {
    data class TextDocument(val paragraphs: List<ReadingParagraph>) : ReadingDocument
    data class PdfDocument(val file: File, val pages: Int) : ReadingDocument
    data class Unsupported(val reason: String) : ReadingDocument
}

private data class ReadingParagraph(val text: String, val heading: Boolean = false)
private data class ReaderHighlight(val paragraph: Int, val start: Int, val end: Int, val quote: String)

private fun readHighlights(raw: String?): List<ReaderHighlight> = try {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { j ->
        ReaderHighlight(j.optInt("paragraph"), j.optInt("start"), j.optInt("end"), j.optString("quote"))
    } }
} catch (_: Exception) { emptyList() }

private fun saveHighlights(context: Context, key: String, highlights: List<ReaderHighlight>) {
    val array = JSONArray()
    highlights.forEach { h -> array.put(JSONObject().put("paragraph", h.paragraph)
        .put("start", h.start).put("end", h.end).put("quote", h.quote)) }
    context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit()
        .putString("highlights_$key", array.toString()).apply()
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
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Compartir libro"))
}

internal fun readerProgressKey(uri: Uri): String = MessageDigest.getInstance("SHA-256")
    .digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }

internal fun localReaderFile(context: Context, book: Book): File {
    val name = DocumentFile.fromSingleUri(context, book.uri)?.name
        ?: book.uri.lastPathSegment.orEmpty().substringAfterLast('/').substringAfterLast(':')
    val extension = name.substringAfterLast('.', "").lowercase().take(5).ifBlank {
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
    if (file.exists() && file.length() > 0 && stamp.takeIf(File::exists)?.readText() == fingerprint)
        return file.also { it.setLastModified(System.currentTimeMillis()) }
    val partial = File(folder, "${readerProgressKey(book.uri)}.partial")
    try {
        context.contentResolver.openInputStream(book.uri)?.use { input ->
            partial.outputStream().use { input.copyTo(it, 64 * 1024) }
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
        return file
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
            ReadingParagraph(it, element.tagName().startsWith("h"))
        }
    }
    return result.ifEmpty { listOfNotNull(body.text().trim().takeIf(String::isNotBlank)?.let(::ReadingParagraph)) }
}

private fun readEpubText(file: File): ReadingDocument = ZipFile(file).use { zip ->
    val container = zip.getEntry("META-INF/container.xml")?.let { zip.getInputStream(it).bufferedReader().readText() }
        ?: return@use ReadingDocument.Unsupported("El EPUB no contiene su índice principal")
    val opf = Regex("full-path\\s*=\\s*['\"]([^'\"]+)['\"]")
        .find(container)?.groupValues?.get(1)
        ?: return@use ReadingDocument.Unsupported("No se encontró el contenido del EPUB")
    val packageXml = zip.getEntry(opf)?.let { zip.getInputStream(it).bufferedReader().readText() }
        ?: return@use ReadingDocument.Unsupported("No se pudo leer el contenido del EPUB")
    val xml = Jsoup.parse(packageXml, "", Parser.xmlParser())
    val manifest = xml.select("manifest > item").associate { it.attr("id") to it.attr("href") }
    val spine = xml.select("spine > itemref").mapNotNull { manifest[it.attr("idref")] }
    val paths = spine.ifEmpty { manifest.values.filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) } }
    val paragraphs = mutableListOf<ReadingParagraph>()
    for (href in paths) {
        val path = resolveZipPath(opf, java.net.URLDecoder.decode(href, "UTF-8"))
        val entry = zip.getEntry(path) ?: continue
        if (entry.size > 3_000_000) continue
        val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        val section = paragraphsFromHtml(html)
        if (section.isNotEmpty() && section.none(ReadingParagraph::heading)) {
            val label = Jsoup.parse(html).title().trim()
                .takeIf { it.isNotBlank() && !it.equals("untitled", true) }
                ?: "Capítulo ${paragraphs.count(ReadingParagraph::heading) + 1}"
            paragraphs += ReadingParagraph(label.take(90), heading = true)
        }
        paragraphs += section
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
                        val xml = zip.getInputStream(entry).bufferedReader().readText()
                        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                        doc.select("w|p").mapNotNull { p -> p.text().trim().takeIf(String::isNotBlank)
                            ?.let { text -> ReadingParagraph(text,
                                p.selectFirst("w|pStyle")?.attr("w:val")?.startsWith("Heading", true) == true) } }
                    }.orEmpty()
                }
                "html", "htm" -> paragraphsFromHtml(file.readText())
                "rtf" -> file.readText().replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ")
                    .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), " ")
                    .replace(Regex("[{}]"), " ").split(Regex("\\n+"))
                    .mapNotNull { it.trim().takeIf(String::isNotBlank)?.let(::ReadingParagraph) }
                else -> file.readText().split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
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
    paragraph: ReadingParagraph, index: Int, size: Float, foreground: Color,
    dark: Boolean, highlights: List<ReaderHighlight>, onHighlight: (ReaderHighlight) -> Unit
) {
    val action by rememberUpdatedState(onHighlight)
    AndroidView(modifier = Modifier.fillMaxWidth(), factory = { context ->
        TextView(context).apply {
            setTextIsSelectable(true)
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            val selectable = this
            setCustomSelectionActionModeCallback(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(0, 8001, 0, "Resaltar")
                    menu.add(0, 8002, 1, "Significado")
                    menu.add(0, 8003, 2, "Compartir")
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                override fun onDestroyActionMode(mode: ActionMode) = Unit
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (item.itemId !in 8001..8003) return false
                    val start = selectable.selectionStart.coerceAtLeast(0)
                    val end = selectable.selectionEnd.coerceAtMost(selectable.text.length)
                    if (end <= start) return false
                    val quote = selectable.text.subSequence(start, end).toString().trim()
                    if (quote.isBlank()) return false
                    when (item.itemId) {
                        8001 -> action(ReaderHighlight(index, start, end, quote))
                        8002 -> {
                            val word = quote.split(Regex("\\s+")).first().trim('¿', '¡', '.', ',', ';',
                                ':', '!', '?', '«', '»', '"', '\'')
                            if (word.isNotBlank()) try {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://dle.rae.es/" +
                                        java.net.URLEncoder.encode(word, "UTF-8"))))
                            } catch (_: Exception) { }
                        }
                        8003 -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, quote)
                        }, "Compartir cita"))
                    }
                    mode.finish()
                    return true
                }
            })
        }
    }, update = { view ->
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size + if (paragraph.heading) 4f else 0f)
        view.typeface = Typeface.create(Typeface.SERIF,
            if (paragraph.heading) Typeface.BOLD else Typeface.NORMAL)
        view.setLineSpacing(0f, 1.45f)
        view.setTextColor(foreground.toArgb())
        val relevant = highlights.filter { it.paragraph == index }
        val stamp = paragraph.text to relevant
        if (view.tag != stamp) {
            val styled = SpannableString(paragraph.text)
            relevant.forEach { h ->
                if (h.start >= 0 && h.end <= styled.length && h.end > h.start)
                    styled.setSpan(BackgroundColorSpan(if (dark) 0xFF80622D.toInt()
                        else 0xFFFFE39A.toInt()), h.start, h.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
    var page by remember(book.uri) { mutableIntStateOf(prefs.getInt("page_$key", 0)) }
    val scope = rememberCoroutineScope()
    val swipeDistance = with(LocalDensity.current) { 64.dp.toPx() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var jumpToItem by remember(book.uri) { mutableIntStateOf(-1) }
    var highlights by remember(book.uri) {
        mutableStateOf(readHighlights(prefs.getString("highlights_$key", "[]")))
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
    val background = if (dark) Color(0xFF1D1A17) else Color(0xFFF5EEDD)
    val foreground = if (dark) Color(0xFFF3E9D7) else Color(0xFF31271F)
    val state by produceState<ReadingDocument?>(null, book.uri) {
        value = try { withContext(Dispatchers.IO) { loadReadingDocument(context, book) } }
        catch (e: Exception) { ReadingDocument.Unsupported(e.localizedMessage ?: "No se pudo abrir") }
    }
    val document = state
    BackHandler { if (drawer.isOpen) scope.launch { drawer.close() } else onBack() }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Column(Modifier.fillMaxHeight().widthIn(max = 320.dp).padding(16.dp)) {
                Text("Índice de lectura", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("Capítulos", fontWeight = FontWeight.Bold)
                val chapters = if (document is ReadingDocument.TextDocument)
                    document.paragraphs.withIndex().filter { it.value.heading }
                else emptyList()
                LazyColumn(Modifier.weight(1f)) {
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
                                    Text(highlight.quote.take(60), maxLines = 2)
                                }
                                TextButton(onClick = {
                                    highlights = highlights.filterIndexed { i, _ -> i != number }
                                    saveHighlights(context, key, highlights)
                                    onHighlightsChanged()
                                }) { Text("×") }
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
            }
        }
    }) {
    Scaffold(containerColor = background, contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        TopAppBar(title = { Text(book.customTitle.ifBlank { book.title }.take(38), maxLines = 1) },
            navigationIcon = { TextButton(onClick = onBack) { Text("‹ Volver") } },
            actions = {
                TextButton(onClick = { scope.launch { drawer.open() } }) { Text("☰") }
                TextButton(onClick = { scope.launch {
                    try { shareReadingFile(context, book) }
                    catch (e: Exception) { android.widget.Toast.makeText(context,
                        "No se pudo compartir: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show() }
                } }) { Text("↗") }
                if (document is ReadingDocument.TextDocument) {
                    TextButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(12f);
                        prefs.edit().putFloat("font_size", fontSize).apply() }) { Text("A−") }
                    TextButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(30f);
                        prefs.edit().putFloat("font_size", fontSize).apply() }) { Text("A+") }
                }
                TextButton(onClick = { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() }) {
                    Text(if (dark) "☀" else "☾")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = background,
                titleContentColor = foreground, navigationIconContentColor = foreground,
                actionIconContentColor = foreground))
    }) { padding ->
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
                val image by produceState<Bitmap?>(null, document.file, index) {
                    value = try { withContext(Dispatchers.IO) { renderPdfPage(document.file, index) } }
                    catch (_: Exception) { null }
                }
                LaunchedEffect(index) {
                    prefs.edit().putInt("page_$key", index).apply()
                    onProgress(((index + 1) * 100f / document.pages).toInt())
                }
                Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(if (dark) Color.DarkGray else Color.LightGray)
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
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (image != null) Image(image!!.asImageBitmap(), "Página ${index + 1}",
                                Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                            else CircularProgressIndicator()
                        }
                        Box(Modifier.align(Alignment.CenterStart).width(38.dp).fillMaxHeight()
                            .clickable { page = (index - 1).coerceAtLeast(0) })
                        Box(Modifier.align(Alignment.CenterEnd).width(38.dp).fillMaxHeight()
                            .clickable { page = (index + 1).coerceAtMost(document.pages - 1) })
                    }
                    Text("Página ${index + 1} de ${document.pages} · ${((index + 1) * 100f / document.pages).toInt()} %",
                        color = foreground, modifier = Modifier.padding(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { page = (index - 1).coerceAtLeast(0) }, enabled = index > 0) { Text("‹ Anterior") }
                        if (document.pages > 1) Slider(value = index.toFloat(),
                            onValueChange = { page = it.toInt() },
                            valueRange = 0f..(document.pages - 1).toFloat(), modifier = Modifier.weight(1f))
                        else Spacer(Modifier.weight(1f))
                        TextButton(onClick = { page = (index + 1).coerceAtMost(document.pages - 1) },
                            enabled = index + 1 < document.pages) { Text("Siguiente ›") }
                    }
                }
            }
            is ReadingDocument.TextDocument -> {
                val paragraphs = document.paragraphs
                val positions = remember(document) {
                    val sums = IntArray(paragraphs.size + 1)
                    paragraphs.forEachIndexed { i, value -> sums[i + 1] = sums[i] + value.text.length.coerceAtLeast(1) }
                    sums
                }
                val total = positions.last().coerceAtLeast(1)
                val list = rememberLazyListState()
                var percent by remember(book.uri) { mutableIntStateOf(prefs.getInt("percent_$key", 0)) }
                LaunchedEffect(document) {
                    list.scrollToItem(prefs.getInt("item_$key", 0).coerceIn(0, paragraphs.lastIndex),
                        prefs.getInt("offset_$key", 0).coerceAtLeast(0))
                }
                LaunchedEffect(jumpToItem, document) {
                    if (jumpToItem >= 0) {
                        list.scrollToItem(jumpToItem.coerceIn(0, paragraphs.lastIndex))
                        jumpToItem = -1
                    }
                }
                LaunchedEffect(list, document) {
                    snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
                        .distinctUntilChanged().debounce(700).collect { (item, offset) ->
                            val at = item.coerceIn(0, paragraphs.lastIndex)
                            val height = list.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
                            percent = if (!list.canScrollForward) 100 else ((positions[at] + paragraphs[at].text.length *
                                (offset.toFloat() / height).coerceIn(0f, 1f)) * 100 / total)
                                .toInt().coerceIn(0, 100)
                            prefs.edit().putInt("item_$key", at).putInt("offset_$key", offset)
                                .putInt("percent_$key", percent).apply()
                            onProgress(percent)
                        }
                }
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().pointerInput(document, swipeDistance) {
                        var drag = 0f
                        detectHorizontalDragGestures(onDragStart = { drag = 0f },
                            onHorizontalDrag = { _, amount -> drag += amount },
                            onDragEnd = {
                                val height = list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset
                                if (drag < -swipeDistance) scope.launch { list.animateScrollBy(height * 0.88f) }
                                if (drag > swipeDistance) scope.launch { list.animateScrollBy(-height * 0.88f) }
                            })
                    }) {
                        LazyColumn(Modifier.fillMaxSize(), state = list,
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(paragraphs) { index, paragraph ->
                                SelectableParagraph(paragraph, index, fontSize, foreground, dark, highlights) { mark ->
                                    highlights = (highlights + mark).distinct()
                                    saveHighlights(context, key, highlights)
                                    onHighlightsChanged()
                                }
                            }
                        }
                        Box(Modifier.align(Alignment.CenterStart).width(32.dp).fillMaxHeight()
                            .clickable {
                                val height = list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset
                                scope.launch { list.animateScrollBy(-height * 0.88f) }
                            })
                        Box(Modifier.align(Alignment.CenterEnd).width(32.dp).fillMaxHeight()
                            .clickable {
                                val height = list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset
                                scope.launch { list.animateScrollBy(height * 0.88f) }
                            })
                    }
                    Text("$percent % leído", Modifier.align(Alignment.CenterHorizontally), color = foreground)
                    Slider(value = percent.toFloat(), onValueChange = { value ->
                        percent = value.toInt()
                    }, onValueChangeFinished = {
                        val wanted = total * percent / 100
                        val target = positions.binarySearch(wanted).let { if (it >= 0) it else -it - 2 }
                            .coerceIn(0, paragraphs.lastIndex)
                        scope.launch { list.scrollToItem(target) }
                    }, valueRange = 0f..100f, modifier = Modifier.padding(horizontal = 22.dp))
                }
            }
        }
    }
    }
}
