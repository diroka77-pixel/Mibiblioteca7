package com.diego.mibiblioteca

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

enum class ReadingStatus(val label: String) { PENDING("Pendiente"), READING("Leyendo"), READ("Leído") }

data class Book(
    val uri: Uri,
    val title: String,
    val author: String = "Autor desconocido",
    val date: String = "",
    val publisher: String = "",
    val genre: String = "",
    val description: String = "Sin descripción disponible.",
    val isbn: String = "",
    val saga: String = "",
    val cover: ByteArray? = null,
    val favorite: Boolean = false,
    val status: ReadingStatus = ReadingStatus.PENDING,
    val language: String = "",
    val spanishPlot: String = "",
    val authorBio: String = "",
    val section: String = ""
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    var query by mutableStateOf("")
    var groupMode by mutableStateOf("Todos")
    var selectedSection by mutableStateOf<String?>(null)
    var sections by mutableStateOf<List<String>>(emptyList()); private set
    var statusFilter by mutableStateOf<ReadingStatus?>(null)
    var onlyFavorites by mutableStateOf(false)
    var books by mutableStateOf<List<Book>>(emptyList()); private set
    var coverLoading by mutableStateOf<Uri?>(null); private set
    var infoLoading by mutableStateOf<Uri?>(null); private set
    var syncing by mutableStateOf(false); private set
    var syncCount by mutableIntStateOf(0); private set
    var folderName by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set

    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val folderKey = "folder_uri"

    init {
        sections = org.json.JSONArray(prefs.getString("sections", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }
        restoreCachedBooks()
        prefs.getString(folderKey, null)?.let { sync(Uri.parse(it)) }
    }

    private fun restoreCachedBooks() {
        try {
            val array = org.json.JSONArray(prefs.getString("books_cache", "[]"))
            books = (0 until array.length()).map { i ->
                val j = array.getJSONObject(i)
                val uri = Uri.parse(j.getString("uri"))
                val saved = prefs.getString("info_" + uri, null)?.let(::JSONObject)
                Book(uri, j.optString("title"), j.optString("author"), j.optString("date"),
                    j.optString("publisher"), j.optString("genre"), j.optString("description"),
                    j.optString("isbn"), j.optString("saga"),
                    coverFile(getApplication(), uri).takeIf { it.exists() }?.readBytes(),
                    language = j.optString("language"), spanishPlot = saved?.optString("plot").orEmpty(),
                    authorBio = saved?.optString("bio").orEmpty(), section = j.optString("section"))
            }
        } catch (_: Exception) { books = emptyList() }
    }

    private fun saveBooks() {
        val array = org.json.JSONArray()
        books.forEach { b -> array.put(JSONObject().put("uri", b.uri.toString())
            .put("title", b.title).put("author", b.author).put("date", b.date)
            .put("publisher", b.publisher).put("genre", b.genre).put("description", b.description)
            .put("isbn", b.isbn).put("saga", b.saga).put("language", b.language)
            .put("section", b.section)) }
        prefs.edit().putString("books_cache", array.toString()).apply()
    }

    fun addSection(name: String) {
        val clean = name.trim()
        if (clean.isBlank() || sections.any { it.equals(clean, true) }) return
        sections = sections + clean
        prefs.edit().putString("sections", org.json.JSONArray(sections).toString()).apply()
    }

    fun assignSection(uri: Uri, section: String) {
        books = books.map { if (it.uri == uri) it.copy(section = section) else it }
        saveBooks()
    }

    val filtered: List<Book> get() = books.filter { b ->
        val haystack = listOf(b.title, displayTitle(b), b.author, b.saga).joinToString(" ")
        haystack.contains(query, true) && (statusFilter == null || b.status == statusFilter) &&
            (!onlyFavorites || b.favorite) && (selectedSection == null || b.section == selectedSection)
    }

    fun selectFolder(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            message = "Drive ha concedido solo lectura; no se podrán borrar archivos."
        }
        prefs.edit().putString(folderKey, uri.toString()).apply()
        sync(uri)
    }

    fun sync(uri: Uri? = prefs.getString(folderKey, null)?.let(Uri::parse)) {
        if (uri == null) { message = "Selecciona primero tu carpeta de libros."; return }
        if (syncing) return
        viewModelScope.launch {
            syncing = true; syncCount = 0; message = null
            try {
                val result = withContext(Dispatchers.IO) {
                    scanFolder(getApplication(), uri) { count ->
                        if (count == 1 || count % 5 == 0) {
                            viewModelScope.launch(Dispatchers.Main) { syncCount = count }
                        }
                    }
                }
                books = result.map { b ->
                    val saved = prefs.getString("info_" + b.uri, null)?.let { JSONObject(it) }
                    val section = books.firstOrNull { it.uri == b.uri }?.section.orEmpty()
                    if (saved == null) b.copy(section = section) else b.copy(spanishPlot = saved.optString("plot"), authorBio = saved.optString("bio"), section = section)
                }
                saveBooks()
                viewModelScope.launch { fillMissingDetails() }
                syncCount = result.size
                folderName = withContext(Dispatchers.IO) { DocumentFile.fromTreeUri(getApplication(), uri)?.name }
                message = "${result.size} EPUB encontrados"
            } catch (e: Exception) {
                message = "No se pudo leer la carpeta de Drive: ${e.localizedMessage ?: "error de acceso"}"
            } finally { syncing = false }
        }
    }

    fun downloadCover(book: Book) {
        if (coverLoading != null) return
        viewModelScope.launch {
            coverLoading = book.uri
            try {
                val bytes = withContext(Dispatchers.IO) { fetchCover(book) }
                if (bytes == null) {
                    message = "No se encontró portada en Open Library."
                } else {
                    withContext(Dispatchers.IO) { coverFile(getApplication(), book.uri).writeBytes(bytes) }
                    books = books.map { if (it.uri == book.uri) it.copy(cover = bytes) else it }
                    message = "Portada guardada en este dispositivo."
                }
            } catch (e: Exception) {
                message = "No se pudo descargar la portada: ${e.localizedMessage ?: "comprueba la conexión"}"
            } finally { coverLoading = null }
        }
    }

    fun isPossibleDuplicate(book: Book): Boolean {
        fun key(b: Book) = (b.title + "|" + b.author).lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
        return books.count { key(it) == key(book) } > 1
    }

    fun deleteDuplicate(book: Book) {
        viewModelScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) {
                    DocumentFile.fromSingleUri(getApplication(), book.uri)?.delete() == true
                }
                if (removed) {
                    books = books.filterNot { it.uri == book.uri }
                    saveBooks()
                    message = "EPUB eliminado de la carpeta."
                } else message = "No se pudo borrar. Comprueba el permiso de escritura de Drive."
            } catch (e: Exception) {
                message = "No se pudo borrar: ${e.localizedMessage ?: "sin permiso"}"
            }
        }
    }

    fun enrich(book: Book) {
        if (infoLoading != null) return
        viewModelScope.launch {
            infoLoading = book.uri
            try {
                val (rawPlot, rawBio) = withContext(Dispatchers.IO) { fetchSpanishInfo(book) }
                val plot = ensureSpanish(rawPlot)
                val bio = ensureSpanish(rawBio)
                val updated = book.copy(spanishPlot = plot, authorBio = bio)
                books = books.map { if (it.uri == book.uri) updated else it }
                val json = JSONObject().put("plot", plot).put("bio", bio)
                prefs.edit().putString("info_" + book.uri, json.toString()).apply()
                message = if (plot.isBlank() && bio.isBlank()) "No se encontró información en castellano."
                    else "Información en castellano actualizada."
            } catch (e: Exception) {
                message = "No se pudo consultar la información: ${e.localizedMessage ?: "comprueba la conexión"}"
            } finally { infoLoading = null }
        }
    }

    private suspend fun fillMissingDetails() {
        for (book in books.toList().filter { it.cover == null && !prefs.getBoolean("cover_attempt_" + it.uri, false) }.take(20)) {
            if (prefs.getBoolean("cover_attempt_" + book.uri, false)) continue
            prefs.edit().putBoolean("cover_attempt_" + book.uri, true).apply()
            val cover = try { withContext(Dispatchers.IO) { fetchCover(book) } } catch (_: Exception) { null }
            if (cover != null) {
                withContext(Dispatchers.IO) { coverFile(getApplication(), book.uri).writeBytes(cover) }
                books = books.map { if (it.uri == book.uri) it.copy(cover = cover) else it }
            }
        }
        for (book in books.toList().filter { (it.spanishPlot.isBlank() || it.authorBio.isBlank()) &&
            !prefs.getBoolean("info_attempt_" + it.uri, false) }.take(20)) {
            if (prefs.getBoolean("info_attempt_" + book.uri, false)) continue
            prefs.edit().putBoolean("info_attempt_" + book.uri, true).apply()
            try {
                val (rawPlot, rawBio) = withContext(Dispatchers.IO) { fetchSpanishInfo(book) }
                val plot = ensureSpanish(rawPlot)
                val bio = ensureSpanish(rawBio)
                books = books.map { if (it.uri == book.uri) it.copy(
                    spanishPlot = plot, authorBio = bio) else it }
                prefs.edit().putString("info_" + book.uri,
                    JSONObject().put("plot", plot).put("bio", bio).toString()).apply()
            } catch (_: Exception) {}
        }
    }

    fun toggleFavorite(uri: Uri) { books = books.map { if (it.uri == uri) it.copy(favorite=!it.favorite) else it } }
    fun setStatus(uri: Uri, status: ReadingStatus) { books = books.map { if (it.uri == uri) it.copy(status=status) else it } }
    fun clearMessage() { message = null }
}

private fun displayTitle(book: Book): String {
    var title = book.title.substringBeforeLast(".epub", book.title)
        .replace('_', ' ').replace(Regex("\\[[^]]*]"), " ")
        .replace(Regex("\\([^)]*(?:epub|pdf|descarga|edici[oó]n digital)[^)]*\\)", RegexOption.IGNORE_CASE), " ")
        .trim()
    val parts = title.split(Regex("\\s+-\\s+"))
    if (parts.size > 1) {
        title = if (parts.first().equals(book.author, true)) parts[1] else parts.first()
    }
    return title.replace(Regex("\\s+"), " ").take(100).ifBlank { book.title }
}

private fun scanFolder(context: Context, treeUri: Uri, onProgress: (Int) -> Unit): List<Book> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val out = mutableListOf<Book>()
    fun walk(dir: DocumentFile) {
        dir.listFiles().forEach { f ->
            if (f.isDirectory) walk(f)
            else if (f.name?.endsWith(".epub", true) == true) {
                val epub = readEpub(context, f.uri, f.name ?: "Libro")
                val saved = coverFile(context, f.uri).takeIf { it.exists() }?.readBytes()
                out += if (saved != null) epub.copy(cover = saved) else epub
                onProgress(out.size)
            }
        }
    }
    walk(root)
    return out.sortedBy { it.title.lowercase() }
}

private fun readEpub(context: Context, uri: Uri, fallbackName: String): Book {
    val fallback = Book(uri, fallbackName.substringBeforeLast('.', fallbackName))
    return try {
        // Drive may download the EPUB for each openInputStream call. Read it once.
        val entries = mutableMapOf<String, ByteArray>()
        var imageBytes = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val path = normalizePath(entry.name)
                    val xml = path == "META-INF/container.xml" || path.endsWith(".opf", true)
                    val image = path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) || path.endsWith(".png", true)
                    val limit = if (xml) 2_000_000 else if (image && imageBytes < 2_000_000) 300_000 else 0
                    if (!entry.isDirectory && limit > 0 && (entry.size < 0 || entry.size <= limit)) {
                        val buffer = ByteArray(8192)
                        val output = java.io.ByteArrayOutputStream()
                        while (output.size() <= limit) {
                            val n = zip.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                        if (output.size() <= limit) {
                            entries[path] = output.toByteArray()
                            if (image) imageBytes += output.size()
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: return fallback
        val container = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8).orEmpty()
        val opfPath = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(container)?.groupValues?.get(1)
            ?.let(::normalizePath) ?: return fallback
        val opfBytes = entries[opfPath] ?: return fallback
        val meta = parseOpf(opfBytes)
        val base = opfPath.substringBeforeLast('/', "")
        val coverPath = meta.coverHref?.let { normalizePath(if (base.isBlank()) it else "$base/$it") }
        Book(uri, meta.title.ifBlank { fallback.title }, meta.author, meta.date, meta.publisher,
            meta.genre, meta.description, meta.isbn, meta.saga, coverPath?.let(entries::get), language = meta.language)
    } catch (_: Exception) { fallback }
}

private fun coverFile(context: Context, uri: Uri): File {
    val hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(context.filesDir, "cover-$hash.jpg")
}

private fun downloadImage(url: String): ByteArray? {
    val connection = URL(url.replace("http://", "https://")).openConnection() as HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = 12000
    return try {
        if (connection.responseCode != 200 || connection.contentLengthLong > 2_000_000) return null
        connection.inputStream.use { input ->
            val bytes = input.readNBytes(2_000_001)
            bytes.takeIf { it.size <= 2_000_000 &&
                BitmapFactory.decodeByteArray(it, 0, it.size) != null }
        }
    } finally { connection.disconnect() }
}

private fun fetchCover(book: Book): ByteArray? {
    val isbn = book.isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
    if (isbn.length == 10 || isbn.length == 13) {
        try {
            downloadImage("https://covers.openlibrary.org/b/isbn/$isbn-M.jpg?default=false")
                ?.let { return it }
        } catch (_: Exception) {}
    }
    try {
        val q = "title=" + java.net.URLEncoder.encode(displayTitle(book), "UTF-8") +
            "&author=" + java.net.URLEncoder.encode(book.author.takeUnless { it == "Autor desconocido" }.orEmpty(), "UTF-8")
        val docs = getJson("https://openlibrary.org/search.json?$q&fields=cover_i,title,author_name&limit=3")
            .optJSONArray("docs")
        for (i in 0 until (docs?.length() ?: 0)) {
            val id = docs?.optJSONObject(i)?.optLong("cover_i", 0) ?: 0
            if (id > 0) downloadImage("https://covers.openlibrary.org/b/id/$id-M.jpg?default=false")
                ?.let { return it }
        }
    } catch (_: Exception) {}
    try {
        val q = if (isbn.length == 10 || isbn.length == 13) "isbn:$isbn"
            else "intitle:${displayTitle(book)} inauthor:${book.author}"
        val url = "https://www.googleapis.com/books/v1/volumes?q=" +
            java.net.URLEncoder.encode(q, "UTF-8") + "&maxResults=3"
        val items = getJson(url).optJSONArray("items")
        for (i in 0 until (items?.length() ?: 0)) {
            val link = items?.optJSONObject(i)?.optJSONObject("volumeInfo")
                ?.optJSONObject("imageLinks")?.optString("thumbnail").orEmpty()
            if (link.isNotBlank()) downloadImage(link)?.let { return it }
        }
    } catch (_: Exception) {}
    return null
}

private fun getJson(url: String): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 12000
    connection.setRequestProperty("User-Agent", "MiBiblioteca/0.6 (Android)")
    return try {
        connection.inputStream.use { JSONObject(it.bufferedReader().readText()) }
    } finally { connection.disconnect() }
}

private fun fetchSpanishInfo(book: Book): Pair<String, String> {
    var plot = ""
    try {
        val isbn = book.isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
        val query = if (isbn.length == 10 || isbn.length == 13) "isbn:$isbn"
            else "intitle:${displayTitle(book)} inauthor:${book.author}"
        val url = "https://www.googleapis.com/books/v1/volumes?q=" +
            java.net.URLEncoder.encode(query, "UTF-8") + "&langRestrict=es&maxResults=5"
        val items = getJson(url).optJSONArray("items")
        for (i in 0 until (items?.length() ?: 0)) {
            val info = items?.optJSONObject(i)?.optJSONObject("volumeInfo") ?: continue
            if (info.optString("language") == "es") {
                plot = android.text.Html.fromHtml(info.optString("description"), android.text.Html.FROM_HTML_MODE_LEGACY)
                    .toString().trim().take(2500)
                if (plot.isNotBlank()) break
            }
        }
    } catch (_: Exception) {}
    if (plot.isBlank() && book.description != "Sin descripción disponible.") {
        plot = book.description.take(2500)
    }
    if (plot.isBlank()) try {
        val q = "title=" + java.net.URLEncoder.encode(displayTitle(book), "UTF-8") +
            "&author=" + java.net.URLEncoder.encode(book.author, "UTF-8")
        val key = getJson("https://openlibrary.org/search.json?$q&fields=key,title&limit=1")
            .optJSONArray("docs")?.optJSONObject(0)?.optString("key").orEmpty()
        if (key.startsWith("/works/")) {
            val desc = getJson("https://openlibrary.org$key.json").opt("description")
            plot = (if (desc is JSONObject) desc.optString("value") else desc as? String).orEmpty().take(2500)
        }
    } catch (_: Exception) {}
    var bio = ""
    if (book.author.isNotBlank() && book.author != "Autor desconocido") {
        for (host in listOf("es", "en")) {
            try {
                val title = java.net.URLEncoder.encode(book.author, "UTF-8")
                val url = "https://$host.wikipedia.org/w/api.php?action=query&prop=extracts" +
                    "&exintro=1&explaintext=1&redirects=1&format=json&formatversion=2&titles=$title"
                val page = getJson(url).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
                if (page != null && !page.has("missing")) {
                    bio = page.optString("extract").trim().take(1800)
                    if (bio.isNotBlank()) break
                }
            } catch (_: Exception) {}
        }
    }
    return plot to bio
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

private suspend fun ensureSpanish(text: String): String {
    if (text.isBlank()) return ""
    val identifier = LanguageIdentification.getClient()
    val tag = try { identifier.identifyLanguage(text.take(1000)).awaitResult() }
        catch (_: Exception) { "und" }
        finally { identifier.close() }
    if (tag == "es") return text
    val source = TranslateLanguage.fromLanguageTag(tag) ?: return ""
    val options = TranslatorOptions.Builder()
        .setSourceLanguage(source).setTargetLanguage(TranslateLanguage.SPANISH).build()
    val translator = Translation.getClient(options)
    return try {
        translator.downloadModelIfNeeded().awaitResult()
        translator.translate(text.take(2500)).awaitResult()
    } catch (_: Exception) { "" }
    finally { translator.close() }
}

private fun normalizePath(path: String): String {
    val parts = mutableListOf<String>()
    path.replace('\\','/').split('/').forEach { when(it) { "", "." -> {}; ".." -> if(parts.isNotEmpty()) parts.removeAt(parts.lastIndex); else -> parts += it } }
    return parts.joinToString("/")
}

private data class EpubMeta(var title:String="",var author:String="Autor desconocido",var date:String="",var publisher:String="",var genre:String="",var description:String="Sin descripción disponible.",var isbn:String="",var saga:String="",var coverHref:String?=null,var language:String="")

private fun parseOpf(bytes: ByteArray): EpubMeta {
    val m = EpubMeta(); val manifest = mutableMapOf<String,String>(); var coverId:String? = null
    val p = XmlPullParserFactory.newInstance().newPullParser(); p.setInput(ByteArrayInputStream(bytes), "UTF-8")
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            val n = p.name.lowercase()
            fun text() = try { p.nextText().trim() } catch (_:Exception) { "" }
            when(n) {
                "title" -> if(m.title.isBlank()) m.title=text()
                "creator" -> if(m.author=="Autor desconocido") m.author=text()
                "date" -> if(m.date.isBlank()) m.date=text()
                "language" -> if(m.language.isBlank()) m.language=text()
                "publisher" -> if(m.publisher.isBlank()) m.publisher=text()
                "subject" -> if(m.genre.isBlank()) m.genre=text()
                "description" -> if(m.description=="Sin descripción disponible.") m.description=text().replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+")," ")
                "identifier" -> { val v=text(); if(v.contains("isbn",true) || v.replace("-","").length in 10..13) m.isbn=v }
                "meta" -> {
                    val name=(0 until p.attributeCount).firstOrNull{p.getAttributeName(it)=="name"}?.let{p.getAttributeValue(it)}
                    val content=(0 until p.attributeCount).firstOrNull{p.getAttributeName(it)=="content"}?.let{p.getAttributeValue(it)}
                    val prop=(0 until p.attributeCount).firstOrNull{p.getAttributeName(it)=="property"}?.let{p.getAttributeValue(it)}
                    if(name=="cover") coverId=content
                    if(name?.contains("series",true)==true || prop?.contains("belongs-to-collection",true)==true) m.saga=content ?: text()
                }
                "item" -> {
                    var id=""; var href=""; var properties=""
                    for(i in 0 until p.attributeCount) when(p.getAttributeName(i)){"id"->id=p.getAttributeValue(i);"href"->href=p.getAttributeValue(i);"properties"->properties=p.getAttributeValue(i)}
                    if(id.isNotBlank()) manifest[id]=href
                    if(properties.contains("cover-image")) m.coverHref=href
                }
            }
        }
        event=p.next()
    }
    if(m.coverHref==null) m.coverHref=coverId?.let{manifest[it]}
    return m
}

private val Ink = Color(0xFF31271F)
private val Mahogany = Color(0xFF49352A)
private val Brass = Color(0xFFAD8248)
private val Parchment = Color(0xFFF5EEDD)
private val Paper = Color(0xFFFFFBF2)

private val libraryColors = lightColorScheme(
    primary = Mahogany, onPrimary = Paper, secondary = Brass,
    background = Parchment, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFECE0C8)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = libraryColors) { LibraryApp() } }
    }
}

private fun openGoodreads(context: Context, book: Book? = null) {
    val packageName = "com.goodreads"
    val url = if (book == null) "https://www.goodreads.com/"
        else "https://www.goodreads.com/search?q=" +
            java.net.URLEncoder.encode(book.title + " " + book.author, "UTF-8")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName))
    } catch (_: Exception) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) context.startActivity(launch)
        else context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryApp(vm: LibraryViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf<Book?>(null) }
    var addingSection by remember { mutableStateOf(false) }
    var sectionName by remember { mutableStateOf("") }
    if (addingSection) AlertDialog(
        onDismissRequest = { addingSection = false },
        title = { Text("Nueva sección") },
        text = { OutlinedTextField(sectionName, { sectionName = it }, label = { Text("Nombre") }) },
        confirmButton = { TextButton(onClick = { vm.addSection(sectionName); sectionName = ""; addingSection = false }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = { addingSection = false }) { Text("Cancelar") } }
    )
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::selectFolder) }
    val current = selected?.let { s -> vm.books.firstOrNull { it.uri == s.uri } }
    if (current != null) {
        BookDetail(current, { selected = null }, { vm.toggleFavorite(current.uri) },
            { vm.setStatus(current.uri, it) }, { vm.downloadCover(current) }, vm.coverLoading == current.uri,
            vm.message, vm.isPossibleDuplicate(current), { vm.deleteDuplicate(current); selected = null },
            { vm.enrich(current) }, vm.infoLoading == current.uri,
            vm.sections, { vm.assignSection(current.uri, it) })
        return
    }
    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = {
                    Text("Mi Biblioteca  By Diroka77", modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, fontSize = 13.sp, color = Color.White,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, maxLines = 1)
                },
                actions = {
                    TextButton(onClick = { vm.sync() }, enabled = !vm.syncing) {
                        Text("↻", style = MaterialTheme.typography.headlineMedium, color = Paper)
                    }
                    TextButton(onClick = { folderPicker.launch(null) }) { Text("▣", color = Paper, fontSize = 21.sp) }
                    TextButton(onClick = { openGoodreads(context) }) { Text("G", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Paper) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Mahogany)
            )
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("✦  ENTRE ESTANTERÍAS  ✦", color = Brass, fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.labelMedium)
            Text("Historias por descubrir", fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.headlineSmall, color = Ink,
                modifier = Modifier.padding(bottom = 14.dp))
            if (vm.syncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brass)
                Text("Revisando ${vm.syncCount} libros…", modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Buscar título, saga o autor") }, leadingIcon = { Text("⌕") },
                shape = MaterialTheme.shapes.medium)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(vm.statusFilter == null, { vm.statusFilter = null }, { Text("Todos") })
                FilterChip(vm.onlyFavorites, { vm.onlyFavorites = !vm.onlyFavorites }, { Text("★") })
                FilterChip(vm.statusFilter == ReadingStatus.READING,
                    { vm.statusFilter = if (vm.statusFilter == ReadingStatus.READING) null else ReadingStatus.READING },
                    { Text("Leyendo") })
                FilterChip(vm.statusFilter == ReadingStatus.READ,
                    { vm.statusFilter = if (vm.statusFilter == ReadingStatus.READ) null else ReadingStatus.READ },
                    { Text("Leídos") })
            }
            Text("${vm.filtered.size} libros", fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "Autores", "Sagas", "Secciones").forEach { mode ->
                    FilterChip(vm.groupMode == mode, { vm.groupMode = mode }, { Text(mode) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(vm.selectedSection == null, { vm.selectedSection = null }, { Text("Todas las secciones") })
                vm.sections.forEach { name ->
                    FilterChip(vm.selectedSection == name, { vm.selectedSection = name }, { Text(name) })
                }
                TextButton(onClick = { addingSection = true }) { Text("+ Sección") }
            }
            if (vm.books.isEmpty() && !vm.syncing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📚", style = MaterialTheme.typography.displayMedium)
                        Text("Elige tu carpeta de EPUB en Drive", fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { folderPicker.launch(null) }) { Text("Elegir carpeta") }
                    }
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)) {
                val groups = when (vm.groupMode) {
                    "Autores" -> vm.filtered.groupBy { it.author.ifBlank { "Autor desconocido" } }
                    "Sagas" -> vm.filtered.groupBy { it.saga.ifBlank { "Sin saga" } }
                    "Secciones" -> vm.filtered.groupBy { it.section.ifBlank { "Sin sección" } }
                    else -> mapOf("" to vm.filtered)
                }
                groups.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (name, group) ->
                    if (name.isNotBlank()) item(key = "group:$name") {
                        Text(name, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = Mahogany, modifier = Modifier.padding(top = 10.dp))
                    }
                    items(group, key = { it.uri.toString() }) { book ->
                        BookCard(book) { selected = book }
                    }
                }
            }
        }
    }
}

@Composable private fun BookCard(book:Book,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick), colors = CardDefaults.cardColors(containerColor = Paper), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Cover(book,92.dp,132.dp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(displayTitle(book),fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,fontSize=14.sp,lineHeight=18.sp,modifier=Modifier.weight(1f));if(book.favorite)Text("★")};Text(book.author,fontSize=12.sp)}}}}

@Composable private fun Cover(book:Book,w:androidx.compose.ui.unit.Dp,h:androidx.compose.ui.unit.Dp){val bmp=remember(book.cover){book.cover?.let{BitmapFactory.decodeByteArray(it,0,it.size)}};Surface(Modifier.width(w).height(h),shape=MaterialTheme.shapes.small,tonalElevation=5.dp){if(bmp!=null)Image(bmp.asImageBitmap(),book.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Box(contentAlignment=Alignment.Center){Text("📖",style=MaterialTheme.typography.headlineLarge)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BookDetail(
    book: Book, back: () -> Unit, toggleFavorite: () -> Unit,
    setStatus: (ReadingStatus) -> Unit, downloadCover: () -> Unit, coverLoading: Boolean, message: String?,
    possibleDuplicate: Boolean, deleteBook: () -> Unit, enrich: () -> Unit, infoLoading: Boolean,
    sections: List<String>, assignSection: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var sectionMenu by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Borrar EPUB duplicado") },
        text = { Text("Se eliminará este archivo de la carpeta de Drive. Esta acción no se puede deshacer desde MiBiblioteca. Comprueba que quieres borrar esta copia: ${book.title}.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; deleteBook() }) { Text("Borrar archivo") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
    )
    Scaffold(containerColor = Parchment, topBar = {
        TopAppBar(
            title = { Text("Ficha del libro", fontFamily = FontFamily.Serif, color = Paper) },
            navigationIcon = { TextButton(onClick = back) { Text("‹ Volver", color = Paper) } },
            actions = { TextButton(onClick = toggleFavorite) { Text(if (book.favorite) "★" else "☆", color = Paper) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Mahogany)
        )
    }) { p ->
        LazyColumn(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (message != null) item { Text(message, color = Mahogany) }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(book, 165.dp, 240.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(displayTitle(book), fontSize = 16.sp, lineHeight = 20.sp,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Text(book.author, fontSize = 13.sp)
                    if (book.saga.isNotBlank()) Text("Saga: ${book.saga}", fontSize = 12.sp)
                }
            }
            if (book.cover == null) item {
                OutlinedButton(onClick = downloadCover, enabled = !coverLoading) {
                    Text(if (coverLoading) "Buscando portada…" else "↓  Descargar portada")
                }
                Text("Portadas de Open Library. Se guardan en el teléfono.",
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                Box {
                    OutlinedButton(onClick = { sectionMenu = true }) {
                        Text("Sección: ${book.section.ifBlank { "Sin sección" }}  ▾")
                    }
                    DropdownMenu(expanded = sectionMenu, onDismissRequest = { sectionMenu = false }) {
                        (listOf("") + sections).forEach { name ->
                            DropdownMenuItem(text = { Text(name.ifBlank { "Sin sección" }) },
                                onClick = { assignSection(name); sectionMenu = false })
                        }
                    }
                }
            }
            item {
                Text("Estado", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadingStatus.entries.forEach { status ->
                        FilterChip(book.status == status, { setStatus(status) }, { Text(status.label) })
                    }
                }
            }
            item {
                Info("Publicación", book.date); Info("Editorial", book.publisher)
                Info("Género", book.genre); Info("ISBN", book.isbn)
            }
            item {
                Text("Argumento", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 13.sp)
                val plot = book.spanishPlot.ifBlank {
                    book.description.takeIf { book.language.lowercase().startsWith("es") ||
                        book.language.lowercase().startsWith("spa") }.orEmpty()
                }
                Text(plot.ifBlank { "Argumento en castellano no disponible. Pulsa «Buscar argumento y autor»." },
                    fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                Text("Sobre el autor", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 13.sp)
                Text(book.authorBio.ifBlank { "Biografía en castellano no disponible." },
                    fontSize = 12.sp, lineHeight = 17.sp)
                Text("Fuentes: Google Books y Wikipedia en español.",
                    style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = enrich, enabled = !infoLoading) {
                    Text(if (infoLoading) "Consultando…" else "Buscar argumento y autor")
                }
            }
            item {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(book.uri, "application/epub+zip")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }, modifier = Modifier.fillMaxWidth()) { Text("📖  Abrir EPUB") }
                OutlinedButton(onClick = { openGoodreads(context, book) },
                    modifier = Modifier.fillMaxWidth()) { Text("Consultar en Goodreads") }
                if (possibleDuplicate) OutlinedButton(onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()) { Text("Borrar esta copia duplicada") }
            }
        }
    }
}

@Composable private fun Info(label:String,value:String){if(value.isNotBlank()){Text(label,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(value,fontSize=12.sp,lineHeight=17.sp);Spacer(Modifier.height(4.dp))}}
