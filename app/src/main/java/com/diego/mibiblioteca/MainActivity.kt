package com.diego.mibiblioteca

import android.app.Application
import android.app.SearchManager
import androidx.activity.compose.BackHandler
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import android.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
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
    val section: String = "",
    val notes: String = "",
    val goodreadsUrl: String = "",
    val wantToRead: Boolean = false,
    val customTitle: String = "",
    val customAuthor: String = "",
    val sagaOrder: String = "",
    val sourceSize: Long = -1L,
    val sourceModified: Long = -1L
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    var query by mutableStateOf("")
    var groupMode by mutableStateOf("Todos")
    var selectedSection by mutableStateOf<String?>(null)
    var sections by mutableStateOf<List<String>>(emptyList()); private set
    var wishList by mutableStateOf<List<Pair<String, String>>>(emptyList()); private set
    var detailMessage by mutableStateOf<String?>(null); private set
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
    private val cloudMutex = Mutex()
    private val cloudName = "MiBiblioteca_Diroka77.json"

    init {
        sections = org.json.JSONArray(prefs.getString("sections", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }
        restoreCachedBooks()
        loadWishList()
        folderName = prefs.getString("folder_name", null)
    }

    private fun restoreCachedBooks() {
        try {
            val array = org.json.JSONArray(prefs.getString("books_cache", "[]"))
            books = (0 until array.length()).mapNotNull { i ->
                try {
                val j = array.getJSONObject(i)
                val uri = Uri.parse(j.getString("uri"))
                val saved = prefs.getString("info_" + uri, null)?.let(::JSONObject)
                Book(uri, j.optString("title"), j.optString("author"), j.optString("date"),
                    j.optString("publisher"), j.optString("genre"), j.optString("description"),
                    j.optString("isbn"), j.optString("saga"),
                    coverFile(getApplication(), uri).takeIf { it.exists() }?.readBytes(),
                    language = j.optString("language"), spanishPlot = saved?.optString("plot").orEmpty(),
                    authorBio = saved?.optString("bio").orEmpty(), section = j.optString("section"),
                    notes = j.optString("notes"), goodreadsUrl = j.optString("goodreadsUrl"),
                    wantToRead = j.optBoolean("wantToRead"),
                    customTitle = j.optString("customTitle"), customAuthor = j.optString("customAuthor"),
                    sagaOrder = j.optString("sagaOrder"),
                    favorite = j.optBoolean("favorite"),
                    status = ReadingStatus.entries.firstOrNull { it.name == j.optString("status") } ?: ReadingStatus.PENDING,
                    sourceSize = j.optLong("sourceSize", -1L), sourceModified = j.optLong("sourceModified", -1L))
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { books = emptyList() }
    }

    private fun saveBooks() {
        val array = org.json.JSONArray()
        books.forEach { b -> array.put(JSONObject().put("uri", b.uri.toString())
            .put("title", b.title).put("author", b.author).put("date", b.date)
            .put("publisher", b.publisher).put("genre", b.genre).put("description", b.description)
            .put("isbn", b.isbn).put("saga", b.saga).put("language", b.language)
            .put("section", b.section).put("notes", b.notes)
            .put("goodreadsUrl", b.goodreadsUrl).put("wantToRead", b.wantToRead)
            .put("customTitle", b.customTitle).put("customAuthor", b.customAuthor)
            .put("sagaOrder", b.sagaOrder).put("favorite", b.favorite).put("status", b.status.name)
            .put("sourceSize", b.sourceSize).put("sourceModified", b.sourceModified)) }
        prefs.edit().putString("books_cache", array.toString()).commit()
        saveCloud()
    }

    private fun loadWishList() {
        val arr = org.json.JSONArray(prefs.getString("wish_list", "[]"))
        wishList = (0 until arr.length()).map { arr.getJSONObject(it).let { j ->
            j.optString("title") to j.optString("url")
        } }
    }

    private fun saveWishList() {
        val arr = org.json.JSONArray()
        wishList.forEach { (title, url) -> arr.put(JSONObject().put("title", title).put("url", url)) }
        prefs.edit().putString("wish_list", arr.toString()).apply()
        saveCloud()
    }

    fun addGoodreadsWish(title: String, url: String) {
        val clean = url.trim()
        if (!clean.startsWith("https://www.goodreads.com/")) return
        if (wishList.none { it.second == clean }) {
            wishList = wishList + (title.trim().ifBlank { "Libro de Goodreads" } to clean)
            saveWishList()
        }
    }

    fun removeGoodreadsWish(url: String) {
        wishList = wishList.filterNot { it.second == url }
        saveWishList()
    }

    private fun cloudSnapshot(): String {
        val booksJson = org.json.JSONArray(prefs.getString("books_cache", "[]"))
        for (i in 0 until booksJson.length()) {
            val j = booksJson.getJSONObject(i)
            val uri = Uri.parse(j.getString("uri"))
            val info = prefs.getString("info_" + uri, null)?.let(::JSONObject)
            j.put("manualInfo", prefs.getBoolean("manual_info_" + uri, false))
            j.put("plot", info?.optString("plot").orEmpty())
            j.put("bio", info?.optString("bio").orEmpty())
            val manual = File(getApplication<Application>().filesDir,
                "manual-" + coverFile(getApplication(), uri).name)
            j.put("coverRemoved", File(getApplication<Application>().filesDir,
                "removed-" + coverFile(getApplication(), uri).name).exists())
            if (manual.exists() && manual.length() < 1_000_000) {
                j.put("coverBase64", Base64.encodeToString(manual.readBytes(), Base64.NO_WRAP))
            }
        }
        return JSONObject().put("updatedAt", prefs.getLong("local_revision", 0L))
            .put("sections", org.json.JSONArray(sections))
            .put("wishList", org.json.JSONArray(prefs.getString("wish_list", "[]")))
            .put("books", booksJson).toString()
    }

    private fun saveCloud() {
        prefs.edit().putLong("local_revision", System.currentTimeMillis()).apply()
        val tree = prefs.getString(folderKey, null)?.let(Uri::parse) ?: return
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { cloudSnapshot() }
            try {
                cloudMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val root = DocumentFile.fromTreeUri(getApplication(), tree)
                            ?: throw IllegalStateException("Carpeta no disponible")
                        val file = root.findFile(cloudName)
                            ?: root.createFile("application/json", cloudName)
                            ?: throw IllegalStateException("No se pudo crear el archivo de datos")
                        getApplication<Application>().contentResolver.openOutputStream(file.uri, "wt")
                            ?.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("Sin permiso para escribir en Drive")
                    }
                }
            } catch (e: Exception) {
                detailMessage = "Guardado en el teléfono; no se pudo guardar en Drive: ${e.localizedMessage}"
            }
        }
    }

    private suspend fun restoreCloud(tree: Uri) {
        val cloud = withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(getApplication(), tree) ?: return@withContext null
            root.findFile(cloudName)?.let { file ->
                getApplication<Application>().contentResolver.openInputStream(file.uri)
                    ?.bufferedReader()?.use { JSONObject(it.readText()) }
            }
        } ?: return
        if (cloud.optLong("updatedAt") <= prefs.getLong("local_revision", 0L)) return
        val booksArray = cloud.optJSONArray("books") ?: return
        prefs.edit().putString("books_cache", booksArray.toString())
            .putString("sections", cloud.optJSONArray("sections")?.toString() ?: "[]")
            .putString("wish_list", cloud.optJSONArray("wishList")?.toString() ?: "[]")
            .putLong("local_revision", cloud.optLong("updatedAt")).apply()
        for (i in 0 until booksArray.length()) {
            val j = booksArray.getJSONObject(i)
            val uri = Uri.parse(j.getString("uri"))
            prefs.edit().putString("info_" + uri, JSONObject()
                .put("plot", j.optString("plot")).put("bio", j.optString("bio")).toString())
                .putBoolean("manual_info_" + uri, j.optBoolean("manualInfo")).apply()
            val base64 = j.optString("coverBase64")
            if (j.optBoolean("coverRemoved")) withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir,
                    "removed-" + coverFile(getApplication(), uri).name).writeText("1")
            }
            if (base64.isNotBlank()) withContext(Dispatchers.IO) {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                coverFile(getApplication(), uri).writeBytes(bytes)
                File(getApplication<Application>().filesDir,
                    "manual-" + coverFile(getApplication(), uri).name).writeBytes(bytes)
            }
        }
        sections = org.json.JSONArray(prefs.getString("sections", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }
        loadWishList()
        restoreCachedBooks()
    }

    fun addSection(name: String) {
        val clean = name.trim()
        if (clean.isBlank() || sections.any { it.equals(clean, true) }) return
        sections = sections + clean
        prefs.edit().putString("sections", org.json.JSONArray(sections).toString()).apply()
        saveCloud()
    }

    fun deleteSection(name: String) {
        sections = sections.filterNot { it == name }
        prefs.edit().putString("sections", org.json.JSONArray(sections).toString()).apply()
        books = books.map { if (it.section == name) it.copy(section = "") else it }
        if (selectedSection == name) selectedSection = null
        saveBooks()
    }

    fun editIdentity(uri: Uri, title: String, author: String, saga: String, order: String) {
        books = books.map { if (it.uri == uri) it.copy(
            customTitle = title.trim(), customAuthor = author.trim(),
            saga = saga.trim(), sagaOrder = order.trim()) else it }
        saveBooks()
    }

    fun saveNotes(uri: Uri, notes: String) {
        books = books.map { if (it.uri == uri) it.copy(notes = notes) else it }
        saveBooks()
    }

    fun saveManualInfo(uri: Uri, plot: String, bio: String) {
        books = books.map { if (it.uri == uri) it.copy(spanishPlot = plot.trim(),
            authorBio = bio.trim()) else it }
        prefs.edit().putString("info_" + uri,
            JSONObject().put("plot", plot.trim()).put("bio", bio.trim()).toString())
            .putBoolean("manual_info_" + uri, true).apply()
        saveBooks()
    }

    fun setGoodreadsUrl(uri: Uri, url: String) {
        books = books.map { if (it.uri == uri) it.copy(goodreadsUrl = url.trim()) else it }
        saveBooks()
    }

    fun toggleWantToRead(uri: Uri) {
        books = books.map { if (it.uri == uri) it.copy(wantToRead = !it.wantToRead) else it }
        saveBooks()
    }

    fun replaceCover(uri: Uri, imageUri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val original = getApplication<Application>().contentResolver.openInputStream(imageUri)
                        ?.use { BitmapFactory.decodeStream(it) }
                        ?: throw IllegalArgumentException("Imagen no válida")
                    val width = minOf(original.width, 900)
                    val scaled = Bitmap.createScaledBitmap(original, width,
                        (original.height.toLong() * width / original.width).toInt().coerceAtLeast(1), true)
                    java.io.ByteArrayOutputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
                        out.toByteArray()
                    }
                }
                withContext(Dispatchers.IO) {
                    val file = coverFile(getApplication(), uri)
                    file.writeBytes(bytes)
                    File(getApplication<Application>().filesDir, "manual-" + file.name).writeBytes(bytes)
                    File(getApplication<Application>().filesDir, "removed-" + file.name).delete()
                }
                books = books.map { if (it.uri == uri) it.copy(cover = bytes) else it }
                saveCloud()
            } catch (e: Exception) { detailMessage = "No se pudo cambiar la portada: ${e.localizedMessage}" }
        }
    }

    fun removeCover(uri: Uri) {
        val file = coverFile(getApplication(), uri)
        file.delete()
        File(getApplication<Application>().filesDir, "manual-" + file.name).delete()
        File(getApplication<Application>().filesDir, "removed-" + file.name).writeText("1")
        books = books.map { if (it.uri == uri) it.copy(cover = null) else it }
        saveCloud()
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
        val changedFolder = prefs.getString(folderKey, null) != uri.toString()
        prefs.edit().putString(folderKey, uri.toString()).commit()
        sync(uri, changedFolder)
    }

    fun sync(uri: Uri? = prefs.getString(folderKey, null)?.let(Uri::parse), changedFolder: Boolean = false) {
        if (uri == null) { message = "Selecciona primero tu carpeta de libros."; return }
        if (syncing) return
        viewModelScope.launch {
            syncing = true; syncCount = 0; message = null
            try {
                if (!changedFolder) try { restoreCloud(uri); restoreCachedBooks() } catch (_: Exception) {}
                val cached = if (changedFolder) emptyMap() else books.associateBy { it.uri }
                val result = withContext(Dispatchers.IO) {
                    scanFolder(getApplication(), uri, cached) { count ->
                        if (count == 1 || count % 5 == 0) {
                            viewModelScope.launch(Dispatchers.Main) { syncCount = count }
                        }
                    }
                }
                if (result.isEmpty() && books.isNotEmpty() && !changedFolder) {
                    message = "No se pudo confirmar el contenido de Drive. Se conserva la biblioteca guardada."
                    return@launch
                }
                books = result.map { b ->
                    val saved = prefs.getString("info_" + b.uri, null)?.let { JSONObject(it) }
                    val prior = cached[b.uri]
                    b.copy(spanishPlot = prior?.spanishPlot ?: saved?.optString("plot").orEmpty(),
                        authorBio = prior?.authorBio ?: saved?.optString("bio").orEmpty(),
                        section = prior?.section.orEmpty(), notes = prior?.notes.orEmpty(),
                        goodreadsUrl = prior?.goodreadsUrl.orEmpty(), wantToRead = prior?.wantToRead ?: false,
                        customTitle = prior?.customTitle.orEmpty(), customAuthor = prior?.customAuthor.orEmpty(),
                        sagaOrder = prior?.sagaOrder ?: b.sagaOrder,
                        favorite = prior?.favorite ?: false, status = prior?.status ?: ReadingStatus.PENDING)
                }
                saveBooks()
                viewModelScope.launch { fillMissingDetails() }
                syncCount = result.size
                folderName = withContext(Dispatchers.IO) { DocumentFile.fromTreeUri(getApplication(), uri)?.name }
                prefs.edit().putString("folder_name", folderName).apply()
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
                    withContext(Dispatchers.IO) {
                        val file = coverFile(getApplication(), book.uri)
                        file.writeBytes(bytes)
                        File(getApplication<Application>().filesDir, "removed-" + file.name).delete()
                    }
                    books = books.map { if (it.uri == book.uri) it.copy(cover = bytes) else it }
                    message = "Portada guardada en este dispositivo."
                }
            } catch (e: Exception) {
                message = "No se pudo descargar la portada: ${e.localizedMessage ?: "comprueba la conexión"}"
            } finally { coverLoading = null }
        }
    }

    fun isPossibleDuplicate(book: Book): Boolean {
        fun key(b: Book) = (displayTitle(b) + "|" + displayAuthor(b)).lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
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
                val google = withContext(Dispatchers.IO) { fetchGoogleBookInfo(book) }
                val resolvedAuthor = if ((book.customAuthor.isBlank() || book.customAuthor == "Biblioteca de Diroka77") && google.first.isNotBlank())
                    google.first else displayAuthor(book)
                val lookupBook = book.copy(customAuthor = resolvedAuthor)
                val (fallbackPlot, rawBio) = withContext(Dispatchers.IO) { fetchSpanishInfo(lookupBook) }
                val plot = ensureSpanish(google.second.ifBlank { fallbackPlot })
                val bio = ensureSpanish(rawBio)
                val current = books.firstOrNull { it.uri == book.uri } ?: book
                val updated = current.copy(
                    customAuthor = if ((current.customAuthor.isBlank() || current.customAuthor == "Biblioteca de Diroka77") && google.first.isNotBlank())
                        google.first else current.customAuthor,
                    spanishPlot = plot.ifBlank { current.spanishPlot },
                    authorBio = bio.ifBlank { current.authorBio })
                books = books.map { if (it.uri == book.uri) updated else it }
                prefs.edit().putString("info_" + book.uri, JSONObject()
                    .put("plot", updated.spanishPlot).put("bio", updated.authorBio).toString()).apply()
                saveBooks()
                message = if (google.first.isBlank() && updated.spanishPlot.isBlank() && updated.authorBio.isBlank())
                    "No se encontraron datos para este libro."
                else "Autor y argumento actualizados con los datos disponibles."
            } catch (e: Exception) {
                message = "No se pudo consultar la información: ${e.localizedMessage ?: "comprueba la conexión"}"
            } finally { infoLoading = null }
        }
    }

    private suspend fun fillMissingDetails() {
        for (book in books.toList().filter { it.cover == null &&
            !File(getApplication<Application>().filesDir, "removed-" + coverFile(getApplication(), it.uri).name).exists() &&
            !prefs.getBoolean("cover_attempt_v9_" + it.uri, false) }.take(20)) {
            if (prefs.getBoolean("cover_attempt_v9_" + book.uri, false)) continue
            prefs.edit().putBoolean("cover_attempt_" + book.uri, true).apply()
            val cover = try { withContext(Dispatchers.IO) { fetchCover(book) } } catch (_: Exception) { null }
            if (cover != null) {
                withContext(Dispatchers.IO) { coverFile(getApplication(), book.uri).writeBytes(cover) }
                books = books.map { if (it.uri == book.uri) it.copy(cover = cover) else it }
            }
        }
        for (book in books.toList().filter { (it.spanishPlot.isBlank() || it.authorBio.isBlank()) &&
            !prefs.getBoolean("info_attempt_v9_" + it.uri, false) &&
            !prefs.getBoolean("manual_info_" + it.uri, false) }.take(20)) {
            if (prefs.getBoolean("info_attempt_v9_" + book.uri, false)) continue
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

    fun toggleFavorite(uri: Uri) { books = books.map { if (it.uri == uri) it.copy(favorite=!it.favorite) else it }; saveBooks() }
    fun setStatus(uri: Uri, status: ReadingStatus) { books = books.map { if (it.uri == uri) it.copy(status=status) else it }; saveBooks() }
    fun clearMessage() { message = null }
    fun clearDetailMessage() { detailMessage = null }
}

private val sourceTag = Regex("""(?i)(?:https?://)?(?:www\.)?(?:e[\s.-]*pub[\s.-]*libre|lecturalia|anna'?s[\s.-]*archive|annas[\s.-]*archive)(?:\.[a-z]{2,})?""")
private val metadataTag = Regex("""(?i)(?:epub|pdf|mobi|azw3|descarga|ebook|libro digital|sin drm|ocr|scan|r\d+(?:\.\d+)*|v\d+(?:\.\d+)*)""")

private fun cleanCatalogText(value: String): String {
    val normalized = value.replace(Regex("""(?i)\.epub$"""), "").replace('_', ' ')
        .replace(Regex("""\[[^]]*]""")) { match ->
            if (sourceTag.containsMatchIn(match.value) || metadataTag.containsMatchIn(match.value)) " " else match.value
        }
        .replace(Regex("""\([^)]*\)""")) { match ->
            if (sourceTag.containsMatchIn(match.value) || metadataTag.containsMatchIn(match.value)) " " else match.value
        }
    return normalized.split(Regex("""\s+[-–—|·]\s+"""))
        .filterNot { sourceTag.containsMatchIn(it) || metadataTag.matches(it.trim()) }
        .joinToString(" - ")
        .replace(sourceTag, " ")
        .replace(Regex("""\s+"""), " ").trim(' ', '-', '|', '·', '–', '—')
}

private fun displayAuthor(book: Book): String {
    val known = cleanCatalogText(book.customAuthor.ifBlank { book.author })
    if (known.isNotBlank() && known != "Autor desconocido") return known
    val parts = book.title.replace('_', ' ').split(Regex("""\s+[-–—]\s+"""))
        .filterNot { sourceTag.containsMatchIn(it) || metadataTag.matches(it.trim()) }
    val possible = cleanCatalogText(parts.lastOrNull().orEmpty())
    if (parts.size >= 2 && possible.length in 4..45 &&
        possible.split(' ').size in 2..5 && possible.none { it.isDigit() }) return possible
    return "Biblioteca de Diroka77"
}

private fun displayTitle(book: Book): String {
    val author = displayAuthor(book)
    var title = cleanCatalogText(book.customTitle.ifBlank { book.title })
        .replace(Regex("""(?i)\b(?:isbn(?:-1[03])?|autor|editorial|publicad[oa]|idioma|formato|páginas|paginas|sinopsis|descripci[oó]n)\s*[:=].*$"""), "")
        .trim()
    if (author != "Biblioteca de Diroka77") {
        title = title.replace(Regex(Regex.escape(author), RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\(\s*\)"""), " ")
            .trim(' ', '-', '–', '—', '|', ',', ':')
    }
    // El texto que sigue a un separador suele ser autor, colección o datos del fichero.
    title = title.split(Regex("""\s+[-–—|·]\s+""")).firstOrNull().orEmpty()
        .replace(Regex("""(?i)\s*\((?:\d{4}|(?:e?pub|pdf|mobi)[^)]*)\)"""), "")
        .replace(Regex("""\s+"""), " ").trim(' ', '-', '–', '—', '|', ',', ':')
    return title.take(100).ifBlank { cleanCatalogText(book.title).substringBefore(" - ").ifBlank { "Libro sin título" } }
}

private fun sagaNumber(book: Book): Double {
    book.sagaOrder.toDoubleOrNull()?.let { return it }
    val match = Regex("(?:#|n[ºo.]?\\s*|vol\\.?\\s*|tomo\\s*|\\[)(\\d+(?:\\.\\d+)?)",
        RegexOption.IGNORE_CASE).find(book.title)
    return match?.groupValues?.get(1)?.toDoubleOrNull() ?: Double.POSITIVE_INFINITY
}

private fun scanFolder(context: Context, treeUri: Uri, cached: Map<Uri, Book>, onProgress: (Int) -> Unit): List<Book> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: throw IllegalStateException("Carpeta no disponible")
    val out = mutableListOf<Book>()
    fun walk(dir: DocumentFile) {
        dir.listFiles().forEach { f ->
            if (f.isDirectory) walk(f)
            else if (f.name?.endsWith(".epub", true) == true) {
                val size = f.length()
                val modified = f.lastModified()
                val prior = cached[f.uri]
                val unchanged = prior != null && size > 0 && prior.sourceSize == size &&
                    (modified <= 0L || prior.sourceModified == modified)
                val epub = if (unchanged) prior!! else readEpub(context, f.uri, f.name ?: "Libro")
                    .copy(sourceSize = size, sourceModified = modified)
                val file = coverFile(context, f.uri)
                val removed = File(context.filesDir, "removed-" + file.name).exists()
                val saved = file.takeIf { it.exists() }?.readBytes()
                out += if (removed) epub.copy(cover = null)
                    else if (saved != null) epub.copy(cover = saved) else epub
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
            meta.genre, meta.description, meta.isbn, meta.saga, coverPath?.let(entries::get),
            language = meta.language, sagaOrder = meta.sagaOrder)
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
            "&author=" + java.net.URLEncoder.encode(displayAuthor(book).takeUnless { it == "Biblioteca de Diroka77" }.orEmpty(), "UTF-8")
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
            else "intitle:${displayTitle(book)} inauthor:${displayAuthor(book)}"
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

private fun fetchGoogleBookInfo(book: Book): Pair<String, String> {
    val isbn = book.isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
    val title = displayTitle(book)
    val knownAuthor = displayAuthor(book).takeUnless { it == "Biblioteca de Diroka77" }.orEmpty()
    val queries = if (isbn.length == 10 || isbn.length == 13) listOf("isbn:$isbn", "intitle:$title")
        else listOf("intitle:$title" + if (knownAuthor.isNotBlank()) " inauthor:$knownAuthor" else "", "intitle:$title")
    val titleKey = title.lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "")
    for (query in queries.distinct()) try {
        val url = "https://www.googleapis.com/books/v1/volumes?q=" +
            java.net.URLEncoder.encode(query, "UTF-8") + "&langRestrict=es&maxResults=10"
        val items = getJson(url).optJSONArray("items")
        for (i in 0 until (items?.length() ?: 0)) {
            val info = items?.optJSONObject(i)?.optJSONObject("volumeInfo") ?: continue
            val candidate = info.optString("title").lowercase()
                .replace(Regex("""[^\p{L}\p{N}]"""), "")
            val isbnMatch = query.startsWith("isbn:")
            if (!isbnMatch && (titleKey.length < 3 || !candidate.contains(titleKey))) continue
            val authors = info.optJSONArray("authors")
            val author = (0 until (authors?.length() ?: 0))
                .mapNotNull { authors?.optString(it)?.takeIf(String::isNotBlank) }
                .joinToString(", ")
            if (knownAuthor.isNotBlank() && !isbnMatch && author.isNotBlank()) {
                val authorKey = knownAuthor.lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "")
                val candidateAuthor = author.lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "")
                if (!candidateAuthor.contains(authorKey) && !authorKey.contains(candidateAuthor)) continue
            }
            val plot = android.text.Html.fromHtml(info.optString("description"),
                android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim().take(2500)
            if (author.isNotBlank() || plot.isNotBlank()) return author to plot
        }
    } catch (_: Exception) {}
    return "" to ""
}

private fun fetchSpanishInfo(book: Book): Pair<String, String> {
    var plot = ""
    try {
        val isbn = book.isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
        val query = if (isbn.length == 10 || isbn.length == 13) "isbn:$isbn"
            else "intitle:${displayTitle(book)} inauthor:${displayAuthor(book)}"
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
            "&author=" + java.net.URLEncoder.encode(displayAuthor(book), "UTF-8")
        val key = getJson("https://openlibrary.org/search.json?$q&fields=key,title&limit=1")
            .optJSONArray("docs")?.optJSONObject(0)?.optString("key").orEmpty()
        if (key.startsWith("/works/")) {
            val desc = getJson("https://openlibrary.org$key.json").opt("description")
            plot = (if (desc is JSONObject) desc.optString("value") else desc as? String).orEmpty().take(2500)
        }
    } catch (_: Exception) {}
    if (plot.isBlank()) {
        for (title in listOf(displayTitle(book), displayTitle(book) + " (novela)")) {
            try {
                val encoded = java.net.URLEncoder.encode(title, "UTF-8")
                val url = "https://es.wikipedia.org/w/api.php?action=query&prop=extracts" +
                    "&exintro=1&explaintext=1&redirects=1&format=json&formatversion=2&titles=$encoded"
                val page = getJson(url).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
                if (page != null && !page.has("missing")) {
                    plot = page.optString("extract").trim().take(2500)
                    if (plot.isNotBlank()) break
                }
            } catch (_: Exception) {}
        }
    }
    var bio = ""
    if (displayAuthor(book).isNotBlank() && displayAuthor(book) != "Biblioteca de Diroka77") {
        for (host in listOf("es", "en")) {
            try {
                val title = java.net.URLEncoder.encode(displayAuthor(book), "UTF-8")
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
    if (bio.isBlank() && displayAuthor(book).isNotBlank() && displayAuthor(book) != "Biblioteca de Diroka77") try {
        val q = java.net.URLEncoder.encode(displayAuthor(book), "UTF-8")
        val key = getJson("https://openlibrary.org/search/authors.json?q=$q")
            .optJSONArray("docs")?.optJSONObject(0)?.optString("key").orEmpty()
        if (key.matches(Regex("OL[0-9]+A"))) {
            val value = getJson("https://openlibrary.org/authors/$key.json").opt("bio")
            bio = (if (value is JSONObject) value.optString("value") else value as? String)
                .orEmpty().take(1800)
        }
    } catch (_: Exception) {}
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

private data class EpubMeta(var title:String="",var author:String="Autor desconocido",var date:String="",var publisher:String="",var genre:String="",var description:String="Sin descripción disponible.",var isbn:String="",var saga:String="",var coverHref:String?=null,var language:String="",var sagaOrder:String="")

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
                    if(name?.contains("series_index",true)==true || prop?.contains("group-position",true)==true) m.sagaOrder = content ?: text()
                    else if(name?.contains("series",true)==true || prop?.contains("belongs-to-collection",true)==true) m.saga=content ?: text()
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
    private lateinit var libraryVm: LibraryViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libraryVm = ViewModelProvider(this)[LibraryViewModel::class.java]
        handleSharedBook(intent)
        setContent { MaterialTheme(colorScheme = libraryColors) { LibraryApp(libraryVm) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedBook(intent)
    }
    private fun handleSharedBook(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Regex("https://(?:www\\.)?goodreads\\.com/[^\\s]+").find(shared)?.value ?: return
        val title = shared.substringBefore("https://").trim().take(120)
        libraryVm.addGoodreadsWish(title, url)
    }
}

private fun openGoodreads(context: Context, book: Book? = null) {
    if (book == null) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.goodreads")
        if (launch != null) context.startActivity(launch)
        else android.widget.Toast.makeText(context, "Goodreads no está instalada", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    CoroutineScope(Dispatchers.Main).launch {
        val url = withContext(Dispatchers.IO) { findGoodreadsBook(book) }
        if (url != null) openGoodreadsUrl(context, url)
        else openGoodreadsUrl(context, "https://www.goodreads.com/search?q=" +
            java.net.URLEncoder.encode(displayTitle(book) + " " + displayAuthor(book), "UTF-8"))
    }
}

private fun findGoodreadsBook(book: Book): String? {
    if (book.goodreadsUrl.matches(Regex("https://(?:www\\.)?goodreads\\.com/book/show/[^\\s?#]+.*"))) return book.goodreadsUrl
    val isbn = book.isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
    if (isbn.length == 10 || isbn.length == 13) {
        try {
            val connection = URL("https://www.goodreads.com/book/isbn/$isbn").openConnection() as HttpURLConnection
            connection.connectTimeout = 8000; connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MiBiblioteca)")
            val exact = connection.inputStream.use { connection.url.toString() }
            connection.disconnect()
            if (exact.contains("/book/show/")) return exact
        } catch (_: Exception) {}
    }
    try {
        val query = java.net.URLEncoder.encode(displayTitle(book) + " " + displayAuthor(book), "UTF-8")
        val connection = URL("https://www.goodreads.com/search?q=$query").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000; connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MiBiblioteca)")
        val html = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val path = Regex("""/book/show/[0-9]+[^"'\s<]*""").find(html)?.value
        if (path != null) return "https://www.goodreads.com" + path.replace("&amp;", "&")
    } catch (_: Exception) {}
    return null
}

private fun openGoodreadsUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.goodreads")) }
    catch (_: Exception) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.goodreads")
        if (launch != null) context.startActivity(launch)
        else android.widget.Toast.makeText(context, "Goodreads no está instalada", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun openGoogleAi(context: Context) {
    val url = Uri.parse("https://www.google.com/ai")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url).setPackage("com.google.android.googlequicksearchbox"))
    } catch (_: Exception) {
        openChrome(context, url.toString())
    }
}

private fun searchInGoogleApp(context: Context, query: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_WEB_SEARCH)
            .setPackage("com.google.android.googlequicksearchbox")
            .putExtra(SearchManager.QUERY, query))
    } catch (_: Exception) {
        try {
            val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage("com.google.android.googlequicksearchbox"))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "No se encontró la aplicación de Google", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

private fun openChrome(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.android.chrome"))
    } catch (_: Exception) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
        if (launch != null) context.startActivity(launch)
        else android.widget.Toast.makeText(context, "Chrome no está instalada", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun openCasaDelLibro(context: Context) {
    val manager = context.packageManager
    val launch = manager.getLaunchIntentForPackage("com.tagus")
        ?: manager.getLaunchIntentForPackage("com.casadellibro.lecturadigital")
    if (launch != null) context.startActivity(launch)
    else android.widget.Toast.makeText(context, "No se encontró Casa del Libro en el teléfono",
        android.widget.Toast.LENGTH_LONG).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryApp(vm: LibraryViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf<Book?>(null) }
    var addingSection by remember { mutableStateOf(false) }
    var sectionName by remember { mutableStateOf("") }
    var sectionToDelete by remember { mutableStateOf<String?>(null) }
    var showWishList by remember { mutableStateOf(false) }
    var addWishDialog by remember { mutableStateOf(false) }
    var wishTitle by remember { mutableStateOf("") }
    var wishUrl by remember { mutableStateOf("") }
    if (addingSection) AlertDialog(
        onDismissRequest = { addingSection = false },
        title = { Text("Nueva sección") },
        text = { OutlinedTextField(sectionName, { sectionName = it }, label = { Text("Nombre") }) },
        confirmButton = { TextButton(onClick = { vm.addSection(sectionName); sectionName = ""; addingSection = false }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = { addingSection = false }) { Text("Cancelar") } }
    )
    sectionToDelete?.let { name -> AlertDialog(
        onDismissRequest = { sectionToDelete = null },
        title = { Text("Eliminar sección") },
        text = { Text("Se eliminará la sección «$name». Los libros permanecerán en la biblioteca.") },
        confirmButton = { TextButton(onClick = { vm.deleteSection(name); sectionToDelete = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { sectionToDelete = null }) { Text("Cancelar") } }
    ) }
    if (addWishDialog) AlertDialog(
        onDismissRequest = { addWishDialog = false },
        title = { Text("Añadir desde Goodreads") },
        text = { Column {
            OutlinedTextField(wishTitle, { wishTitle = it }, label = { Text("Título") })
            OutlinedTextField(wishUrl, { wishUrl = it }, label = { Text("Enlace compartido de Goodreads") })
        } },
        confirmButton = { TextButton(onClick = {
            vm.addGoodreadsWish(wishTitle, wishUrl); addWishDialog = false; wishTitle = ""; wishUrl = ""
        }) { Text("Añadir") } },
        dismissButton = { TextButton(onClick = { addWishDialog = false }) { Text("Cancelar") } }
    )
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::selectFolder) }
    val current = selected?.let { s -> vm.books.firstOrNull { it.uri == s.uri } }
    BackHandler(enabled = selected != null || showWishList) {
        if (selected != null) selected = null else showWishList = false
    }
    if (current != null) {
        BookDetail(current, { selected = null }, { vm.toggleFavorite(current.uri) },
            { vm.setStatus(current.uri, it) }, { vm.downloadCover(current) }, vm.coverLoading == current.uri,
            vm.message, vm.isPossibleDuplicate(current), { vm.deleteDuplicate(current) },
            { vm.enrich(current) }, vm.infoLoading == current.uri,
            vm.sections, { vm.assignSection(current.uri, it) },
            { vm.saveNotes(current.uri, it) }, { plot, bio -> vm.saveManualInfo(current.uri, plot, bio) },
            { vm.replaceCover(current.uri, it) },
            { vm.setGoodreadsUrl(current.uri, it) }, { vm.toggleWantToRead(current.uri) },
            vm.detailMessage,
            { title, author, saga, order -> vm.editIdentity(current.uri, title, author, saga, order) })
        return
    }
    Scaffold(
        containerColor = Parchment,
        topBar = {
            Column(Modifier.fillMaxWidth().background(Mahogany).statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    Text("Mi Biblioteca   By Diroka77", color = Color.White,
                        fontSize = 17.sp, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                Row(Modifier.fillMaxWidth().height(44.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Carpeta", color = Paper, fontSize = 12.sp,
                        modifier = Modifier.clickable { folderPicker.launch(null) }.padding(6.dp))
                    Text("Goodreads", color = Paper, fontSize = 12.sp,
                        modifier = Modifier.clickable { openGoodreads(context) }.padding(6.dp))
                    Text("Google IA", color = Paper, fontSize = 12.sp,
                        modifier = Modifier.clickable { openGoogleAi(context) }.padding(6.dp))
                    Text("Casa del Libro", color = Paper, fontSize = 12.sp,
                        modifier = Modifier.clickable { openCasaDelLibro(context) }.padding(6.dp))
                }
            }
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("✦  ENTRE ESTANTERÍAS  ✦", color = Brass, fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Historias por descubrir", fontFamily = FontFamily.Serif,
                    style = MaterialTheme.typography.headlineSmall, color = Ink,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.sync() }, enabled = !vm.syncing) {
                    Text("📖", fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
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
                    TextButton(onClick = { sectionToDelete = name }) { Text("×") }
                }
                TextButton(onClick = { addingSection = true }) { Text("+ Sección") }
            }
            Row {
                FilterChip(showWishList, { showWishList = !showWishList }, { Text("Quiero leer (${vm.wishList.size})") })
                if (showWishList) TextButton(onClick = { addWishDialog = true }) { Text("+ Goodreads") }
            }
            if (showWishList) {
                LazyColumn {
                    items(vm.wishList, key = { it.second }) { (title, url) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { openGoodreadsUrl(context, url) }, modifier = Modifier.weight(1f)) {
                                Text(title.ifBlank { "Libro de Goodreads" })
                            }
                            TextButton(onClick = { vm.removeGoodreadsWish(url) }) { Text("×") }
                        }
                    }
                }
            } else if (vm.books.isEmpty() && !vm.syncing) {
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
                    "Autores" -> vm.filtered.groupBy { displayAuthor(it) }
                    "Sagas" -> vm.filtered.groupBy { it.saga.ifBlank { "Sin saga" } }
                    "Secciones" -> vm.filtered.groupBy { it.section.ifBlank { "Sin sección" } }
                    else -> mapOf("" to vm.filtered)
                }
                groups.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (name, group) ->
                    if (name.isNotBlank()) item(key = "group:$name") {
                        Text(name, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = Mahogany, modifier = Modifier.padding(top = 10.dp))
                    }
                    items(if (vm.groupMode == "Sagas") group.sortedWith(
                        compareBy<Book> { sagaNumber(it) }.thenBy(String.CASE_INSENSITIVE_ORDER) { displayTitle(it) })
                        else group, key = { it.uri.toString() }) { book ->
                        BookCard(book) { selected = book }
                    }
                }
            }
        }
    }
}

@Composable private fun BookCard(book:Book,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick), colors = CardDefaults.cardColors(containerColor = Paper), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Cover(book,92.dp,132.dp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(displayTitle(book),fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,fontSize=14.sp,lineHeight=18.sp,modifier=Modifier.weight(1f));if(book.favorite)Text("★")};Text(displayAuthor(book),fontSize=12.sp)}}}}

@Composable private fun Cover(book:Book,w:androidx.compose.ui.unit.Dp,h:androidx.compose.ui.unit.Dp){val bmp=remember(book.cover){book.cover?.let{BitmapFactory.decodeByteArray(it,0,it.size)}};Surface(Modifier.width(w).height(h),shape=MaterialTheme.shapes.small,tonalElevation=5.dp){if(bmp!=null)Image(bmp.asImageBitmap(),book.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Box(contentAlignment=Alignment.Center){Text("📖",style=MaterialTheme.typography.headlineLarge)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BookDetail(
    book: Book, back: () -> Unit, toggleFavorite: () -> Unit,
    setStatus: (ReadingStatus) -> Unit, downloadCover: () -> Unit, coverLoading: Boolean, message: String?,
    possibleDuplicate: Boolean, deleteBook: () -> Unit, enrich: () -> Unit, infoLoading: Boolean,
    sections: List<String>, assignSection: (String) -> Unit,
    saveNotes: (String) -> Unit, saveInfo: (String, String) -> Unit,
    replaceCover: (Uri) -> Unit,
    saveGoodreadsUrl: (String) -> Unit, toggleWant: () -> Unit, detailMessage: String?,
    editIdentity: (String, String, String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var sectionMenu by remember { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    var editIdentityDialog by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    var authorDraft by remember { mutableStateOf("") }
    var sagaDraft by remember { mutableStateOf("") }
    var orderDraft by remember { mutableStateOf("") }
    var plotDraft by remember { mutableStateOf("") }
    var bioDraft by remember { mutableStateOf("") }
    var notesDraft by remember(book.uri, book.notes) { mutableStateOf(book.notes) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(replaceCover)
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Borrar EPUB") },
        text = { Text("Se eliminará este archivo de Drive. Comprueba que quieres borrar esta copia de «${displayTitle(book)}».") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; deleteBook() }) { Text("Borrar archivo") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
    )
    if (editIdentityDialog) AlertDialog(
        onDismissRequest = { editIdentityDialog = false },
        title = { Text("Editar título, autor y saga") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(titleDraft, { titleDraft = it }, label = { Text("Título") })
            OutlinedTextField(authorDraft, { authorDraft = it }, label = { Text("Autor") })
            OutlinedTextField(sagaDraft, { sagaDraft = it }, label = { Text("Saga") })
            OutlinedTextField(orderDraft, { orderDraft = it }, label = { Text("Número en la saga") })
        } },
        confirmButton = { TextButton(onClick = {
            editIdentity(titleDraft, authorDraft, sagaDraft, orderDraft)
            editIdentityDialog = false
        }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { editIdentityDialog = false }) { Text("Cancelar") } }
    )
    if (editInfo) AlertDialog(
        onDismissRequest = { editInfo = false },
        title = { Text("Editar ficha en castellano") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(plotDraft, { plotDraft = it }, label = { Text("Argumento") },
                minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bioDraft, { bioDraft = it }, label = { Text("Biografía del autor") },
                minLines = 4, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { saveInfo(plotDraft, bioDraft); editInfo = false }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { editInfo = false }) { Text("Cancelar") } }
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
            (detailMessage ?: message)?.let { notice -> item { Text(notice, color = Mahogany, fontSize = 12.sp) } }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(book, 165.dp, 240.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(displayTitle(book), fontSize = 16.sp, lineHeight = 20.sp,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Text(displayAuthor(book), fontSize = 13.sp)
                    if (book.saga.isNotBlank()) Text("Saga: ${book.saga}" +
                        book.sagaOrder.takeIf { it.isNotBlank() }?.let { " · nº $it" }.orEmpty(), fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            titleDraft = displayTitle(book); authorDraft = displayAuthor(book)
                            sagaDraft = book.saga; orderDraft = book.sagaOrder
                            editIdentityDialog = true
                        }, modifier = Modifier.weight(1f)) { Text("Editar título y autor", fontSize = 12.sp) }
                        OutlinedButton(onClick = { coverPicker.launch("image/*") },
                            modifier = Modifier.weight(1f)) { Text("Cambiar portada", fontSize = 12.sp) }
                    }
                    if (book.cover == null) OutlinedButton(onClick = downloadCover, enabled = !coverLoading) {
                        Text(if (coverLoading) "Buscando portada…" else "Buscar portada")
                    }
                }
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
                OutlinedButton(onClick = toggleWant) {
                    Text(if (book.wantToRead) "✓ Quiero leer" else "+ Quiero leer más adelante")
                }
            }
            item {
                Text("Estado", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                Text("Argumento", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val plot = book.spanishPlot.ifBlank {
                    book.description.takeIf { book.language.lowercase().startsWith("es") ||
                        book.language.lowercase().startsWith("spa") }.orEmpty()
                }
                OutlinedTextField(value = plot, onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("Argumento en castellano pendiente. Puedes buscarlo o escribirlo.") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 17.sp))
                Spacer(Modifier.height(8.dp))
                Text("Sobre el autor", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(value = book.authorBio, onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("Biografía en castellano pendiente. Puedes buscarla o escribirla.") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 17.sp))
                Row {
                    OutlinedButton(onClick = enrich, enabled = !infoLoading) {
                        Text(if (infoLoading) "Consultando…" else "Buscar datos")
                    }
                    OutlinedButton(onClick = {
                        plotDraft = book.spanishPlot.ifBlank { plot }
                        bioDraft = book.authorBio
                        editInfo = true
                    }) { Text("Editar texto") }
                }
            }
            item {
                Text("Mis observaciones", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(notesDraft, { notesDraft = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Escribe tus notas sobre este libro") }, minLines = 3)
                OutlinedButton(onClick = { saveNotes(notesDraft) }) { Text("Guardar observaciones") }
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
                    modifier = Modifier.fillMaxWidth()) { Text("Abrir este libro en Goodreads") }
                OutlinedButton(onClick = { searchInGoogleApp(context, displayTitle(book) + " " + displayAuthor(book)) },
                    modifier = Modifier.fillMaxWidth()) { Text("Consultar en Google") }
                OutlinedButton(onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()) { Text("Borrar este EPUB de Drive") }
            }
        }
    }
}

@Composable private fun Info(label:String,value:String){if(value.isNotBlank()){Text(label,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(value,fontSize=12.sp,lineHeight=17.sp);Spacer(Modifier.height(4.dp))}}
