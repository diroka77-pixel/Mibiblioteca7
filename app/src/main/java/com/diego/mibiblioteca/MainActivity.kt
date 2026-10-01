package com.diego.mibiblioteca

import android.app.Application
import android.app.SearchManager
import androidx.activity.compose.BackHandler
import android.util.LruCache
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.metrics.performance.JankStats
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import android.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile

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
    val sections: List<String> = emptyList(),
    val notes: String = "",
    val goodreadsUrl: String = "",
    val wantToRead: Boolean = false,
    val customTitle: String = "",
    val customAuthor: String = "",
    val sagaOrder: String = "",
    val sourceSize: Long = -1L,
    val sourceModified: Long = -1L,
    val metadataRevision: Int = 0,
    val sourceCoverChecked: Boolean = false,
    val hadEmbeddedCover: Boolean = false,
    val plotSource: String = "",
    val bioSource: String = "",
    val coverSource: String = ""
)

private fun bookSections(book: Book): List<String> =
    (book.sections + book.section).map(String::trim).filter(String::isNotBlank).distinct()

data class InfoCandidate(
    val author: String, val plot: String, val bio: String,
    val plotSource: String, val bioSource: String
)

private class BookStore(context: Context) : SQLiteOpenHelper(context, "catalog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE books (uri TEXT PRIMARY KEY, content TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun readArray(): org.json.JSONArray {
        val result = org.json.JSONArray()
        readableDatabase.rawQuery("SELECT content FROM books ORDER BY rowid", null).use { cursor ->
            while (cursor.moveToNext()) result.put(JSONObject(cursor.getString(0)))
        }
        return result
    }

    fun replaceAll(array: org.json.JSONArray) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("books", null, null)
            for (i in 0 until array.length()) {
                val book = array.getJSONObject(i)
                db.insertOrThrow("books", null, ContentValues().apply {
                    put("uri", book.getString("uri"))
                    put("content", book.toString())
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}

private val newsImageCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int =
        (value.byteCount / 1024).coerceAtLeast(1)
}

@Composable private fun rememberNewsImage(url: String): State<Bitmap?> {
    val imageUrl = when {
        url.startsWith("https://www.bing.com/th?") && !url.contains("&w=") -> "$url&w=800&h=450"
        url.contains("casadellibro.com/a/l/s5/") -> url.replace("/s5/", "/s7/")
        else -> url
    }
    return produceState<Bitmap?>(initialValue = newsImageCache.get(imageUrl), imageUrl) {
        if (value == null) value = withContext(Dispatchers.IO) {
            try {
                val conn = URL(imageUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 6000
                try {
                    if (conn.contentLengthLong > 4_000_000) return@withContext null
                    val bytes = conn.inputStream.use { it.readNBytes(4_000_001) }
                    if (bytes.size > 4_000_000) return@withContext null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val sample = generateSequence(1) { it * 2 }
                        .first { bounds.outWidth / it <= 1400 && bounds.outHeight / it <= 1400 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })?.also {
                        newsImageCache.put(imageUrl, it)
                    }
                } finally { conn.disconnect() }
            } catch (_: Exception) { null }
        }
    }
}

private fun filterBooks(books: List<Book>, query: String, status: ReadingStatus?,
    favorites: Boolean, section: String?, quality: String, pending: Set<Uri>): List<Book> =
    books.filter { book ->
        val matches = query.isBlank() || listOf(book.title, displayTitle(book), book.author, book.saga)
            .any { it.contains(query, ignoreCase = true) }
        matches && (status == null || book.status == status) &&
            (!favorites || book.favorite) &&
            (section == null || section in bookSections(book)) &&
            when (quality) {
                "Portada" -> book.cover == null
                "Argumento" -> book.spanishPlot.isBlank()
                "Biografía" -> book.authorBio.isBlank()
                "Autor" -> displayAuthor(book) == "Biblioteca de Diroka77"
                "Revisar" -> book.uri in pending
                "Todos" -> book.cover == null || book.spanishPlot.isBlank() ||
                    book.authorBio.isBlank() || displayAuthor(book) == "Biblioteca de Diroka77" ||
                    book.uri in pending
                else -> true
            }
    }

private fun findDuplicateGroups(books: List<Book>): List<List<Book>> {
    val normalized = Regex("[^\\p{L}\\p{N}]")
    val titles = books.associate { it.uri to displayTitle(it) }
    return books.groupBy { book ->
        val isbn = book.isbn.filter(Char::isDigit)
        if (isbn.length == 13) "isbn:$isbn"
        else (titles.getValue(book.uri) + "|" + displayAuthor(book)).lowercase().replace(normalized, "")
    }.values.filter { it.size > 1 && titles.getValue(it.first().uri).length >= 3 }
        .sortedBy { titles.getValue(it.first().uri).lowercase() }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    var query by mutableStateOf("")
    var groupMode by mutableStateOf("Todos")
    var viewMode by mutableStateOf("Lista"); private set
    var showHomeNews by mutableStateOf(true); private set
    var readingFirst by mutableStateOf(true); private set
    var selectedSection by mutableStateOf<String?>(null)
    var sections by mutableStateOf<List<String>>(emptyList()); private set
    var wishList by mutableStateOf<List<Pair<String, String>>>(emptyList()); private set
    var detailMessage by mutableStateOf<String?>(null); private set
    var infoCandidate by mutableStateOf<Pair<Uri, InfoCandidate>?>(null); private set
    var statusFilter by mutableStateOf<ReadingStatus?>(null)
    var onlyFavorites by mutableStateOf(false)
    var qualityFilter by mutableStateOf("Ninguno")
    var bulkInfoLoading by mutableStateOf(false); private set
    var bulkProgress by mutableIntStateOf(0); private set
    private var bulkJob: Job? = null
    private var coverRefreshJob: Job? = null
    var coverUpdateRunning by mutableStateOf(false); private set
    var coverUpdateDone by mutableIntStateOf(0); private set
    var coverUpdateTotal by mutableIntStateOf(0); private set
    var reviewPending by mutableStateOf<Set<Uri>>(emptySet()); private set
    var books by mutableStateOf<List<Book>>(emptyList()); private set
    private val readerPrefs = app.getSharedPreferences("reader", Context.MODE_PRIVATE)
    var readingPercents by mutableStateOf<Map<Uri, Int>>(emptyMap()); private set
    private var readerCloudJob: Job? = null
    fun readingPercent(uri: Uri): Int = readingPercents[uri]
        ?: readerPrefs.getInt("percent_" + readerProgressKey(uri), 0)
    fun updateReadingPercent(uri: Uri, percent: Int) {
        if (readingPercents[uri] == percent) return
        readingPercents = readingPercents + (uri to percent.coerceIn(0, 100))
        readerCloudJob?.cancel()
        readerCloudJob = viewModelScope.launch {
            delay(15_000)
            saveCloud()
        }
    }
    fun readerAnnotationsChanged() { saveCloud() }
    var coverLoading by mutableStateOf<Uri?>(null); private set
    var infoLoading by mutableStateOf<Uri?>(null); private set
    var autoInfoLoading by mutableStateOf<Set<Uri>>(emptySet()); private set
    var autoCoverLoading by mutableStateOf<Set<Uri>>(emptySet()); private set
    private val autoJobs = mutableSetOf<Uri>()
    var syncing by mutableStateOf(false); private set
    var syncCount by mutableIntStateOf(0); private set
    var syncReport by mutableStateOf(""); private set
    var syncSummary by mutableStateOf(""); private set
    var launchNews by mutableStateOf<List<LaunchNews>>(emptyList()); private set
    var newsRefreshing by mutableStateOf(false); private set
    var hiddenNewsSources by mutableStateOf<Set<String>>(emptySet()); private set
    var hiddenNewsUrls by mutableStateOf<Set<String>>(emptySet()); private set
    var newsLastChecked by mutableStateOf(0L); private set
    var folderName by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set

    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val bookStore = BookStore(app)
    private val saveMutex = Mutex()
    @Volatile private var saveVersion = 0
    private val folderKey = "folder_uri"
    private val cloudMutex = Mutex()
    private val cloudName = "MiBiblioteca_Diroka77.json"

    init {
        sections = org.json.JSONArray(prefs.getString("sections", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }
        viewModelScope.launch {
            restoreCachedBooks(onlyIfEmpty = true)
        }
        viewMode = prefs.getString("view_mode", "Galería") ?: "Galería"
        if (!prefs.getBoolean("cover_first_v48", false)) {
            if (!prefs.contains("view_mode_general")) viewMode = "Galería"
            prefs.edit().putString("view_mode", viewMode)
                .putBoolean("cover_first_v48", true).apply()
        }
        reviewPending = org.json.JSONArray(prefs.getString("review_pending", "[]")).let { arr ->
            (0 until arr.length()).map { Uri.parse(arr.optString(it)) }.toSet()
        }
        showHomeNews = prefs.getBoolean("show_home_news", true)
        readingFirst = prefs.getBoolean("reading_first", true)
        loadWishList()
        folderName = prefs.getString("folder_name", null)
        restoreLaunchNews()
        hiddenNewsSources = org.json.JSONArray(prefs.getString("hidden_news_sources", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }
        hiddenNewsUrls = org.json.JSONArray(prefs.getString("hidden_news_urls", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }
        newsLastChecked = prefs.getLong("literary_news_v3_checked", 0L)
        refreshLaunchNews()
        syncReport = prefs.getString("sync_report", "Todavía no se ha sincronizado.") ?: ""
    }

    private fun restoreLaunchNews() {
        val saved = try { org.json.JSONArray(prefs.getString("literary_news", "[]")) }
            catch (_: Exception) { org.json.JSONArray() }
        val cached = (0 until saved.length()).mapNotNull { i ->
            try { saved.getJSONObject(i).let {
                LaunchNews(it.getString("source"), it.getString("title"), it.getString("url"),
                    it.getString("image"), it.getString("date"))
            } } catch (_: Exception) { null }
        }
        launchNews = cached.ifEmpty { starterNews }
    }

    fun isNewsVisible(news: LaunchNews) = news.source !in hiddenNewsSources &&
        news.url !in hiddenNewsUrls

    fun toggleNewsSource(source: String) {
        hiddenNewsSources = if (source in hiddenNewsSources) hiddenNewsSources - source
            else hiddenNewsSources + source
        prefs.edit().putString("hidden_news_sources",
            org.json.JSONArray(hiddenNewsSources.toList()).toString()).apply()
    }

    fun hideNews(url: String) {
        hiddenNewsUrls = hiddenNewsUrls + url
        prefs.edit().putString("hidden_news_urls",
            org.json.JSONArray(hiddenNewsUrls.toList()).toString()).apply()
    }

    fun showAllNews() {
        hiddenNewsUrls = emptySet()
        prefs.edit().remove("hidden_news_urls").apply()
    }

    fun refreshLaunchNews(force: Boolean = false) {
        if (newsRefreshing || (!force &&
            System.currentTimeMillis() - prefs.getLong("literary_news_v3_checked", 0L) < 12L * 60 * 60 * 1000)) return
        newsRefreshing = true
        viewModelScope.launch {
            try {
                val queries = newsQueries(includeLatest = force).map { query ->
                    async(Dispatchers.IO) { fetchLiteraryNews(query) }
                }
                val books = async(Dispatchers.IO) { fetchCasaUpcomingBooks() }
                val results = queries.awaitAll().flatten() + books.await()
                val sorted = results.distinctBy { cleanCatalogText(it.title).lowercase() }
                    .sortedByDescending { it.releaseDate }.take(12)
                if (sorted.isNotEmpty()) launchNews = sorted
                val array = org.json.JSONArray()
                launchNews.forEach { array.put(JSONObject().put("source", it.source)
                    .put("title", it.title).put("url", it.url)
                    .put("image", it.imageUrl).put("date", it.releaseDate)) }
                prefs.edit().putString("literary_news", array.toString())
                    .putLong("literary_news_v3_checked", System.currentTimeMillis()).apply()
                newsLastChecked = System.currentTimeMillis()
            } catch (_: Exception) {
                // Se muestran las noticias guardadas si alguna fuente falla.
            } finally { newsRefreshing = false }
        }
    }

    private suspend fun restoreCachedBooks(onlyIfEmpty: Boolean = false) {
        try {
            val restored = withContext(Dispatchers.IO) {
            val fromDatabase = bookStore.readArray()
            val array = if (fromDatabase.length() > 0) fromDatabase
                else org.json.JSONArray(prefs.getString("books_cache", "[]"))
            (0 until array.length()).mapNotNull { i ->
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
                    sections = j.optJSONArray("sections")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }.filter(String::isNotBlank)
                    }.orEmpty(),
                    notes = j.optString("notes"), goodreadsUrl = j.optString("goodreadsUrl"),
                    wantToRead = j.optBoolean("wantToRead"),
                    customTitle = j.optString("customTitle"), customAuthor = j.optString("customAuthor"),
                    sagaOrder = j.optString("sagaOrder"),
                    favorite = j.optBoolean("favorite"),
                    status = ReadingStatus.entries.firstOrNull { it.name == j.optString("status") } ?: ReadingStatus.PENDING,
                    sourceSize = j.optLong("sourceSize", -1L), sourceModified = j.optLong("sourceModified", -1L),
                    metadataRevision = j.optInt("metadataRevision"),
                    sourceCoverChecked = j.optBoolean("sourceCoverChecked"),
                    hadEmbeddedCover = j.optBoolean("hadEmbeddedCover"),
                    plotSource = j.optString("plotSource"), bioSource = j.optString("bioSource"),
                    coverSource = j.optString("coverSource"))
                } catch (_: Exception) { null }
            }
            }
            if (!onlyIfEmpty || books.isEmpty()) books = restored
        } catch (_: Exception) { if (!onlyIfEmpty) books = emptyList() }
    }

    private fun saveBooks() {
        val snapshot = books.toList()
        val revision = ++saveVersion
        prefs.edit().putLong("local_revision", System.currentTimeMillis()).apply()
        viewModelScope.launch {
            try {
            withContext(Dispatchers.IO) {
                saveMutex.withLock {
                    if (revision != saveVersion) return@withLock
        val array = org.json.JSONArray()
        snapshot.forEach { b -> array.put(JSONObject().put("uri", b.uri.toString())
            .put("title", b.title).put("author", b.author).put("date", b.date)
            .put("publisher", b.publisher).put("genre", b.genre).put("description", b.description)
            .put("isbn", b.isbn).put("saga", b.saga).put("language", b.language)
            .put("section", b.section).put("sections", org.json.JSONArray(bookSections(b)))
            .put("notes", b.notes)
            .put("goodreadsUrl", b.goodreadsUrl).put("wantToRead", b.wantToRead)
            .put("customTitle", b.customTitle).put("customAuthor", b.customAuthor)
            .put("sagaOrder", b.sagaOrder).put("favorite", b.favorite).put("status", b.status.name)
            .put("sourceSize", b.sourceSize).put("sourceModified", b.sourceModified)
            .put("metadataRevision", b.metadataRevision).put("sourceCoverChecked", b.sourceCoverChecked).put("hadEmbeddedCover", b.hadEmbeddedCover)
            .put("plotSource", b.plotSource).put("bioSource", b.bioSource).put("coverSource", b.coverSource)) }
                    bookStore.replaceAll(array)
                    prefs.edit().putString("books_cache", array.toString()).apply()
                }
            }
            if (revision == saveVersion) saveCloud()
            } catch (e: Exception) {
                detailMessage = "No se pudo guardar el catálogo local: ${e.localizedMessage}"
            }
        }
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
            val progressKey = readerProgressKey(uri)
            j.put("readerPercent", readerPrefs.getInt("percent_$progressKey", 0))
                .put("readerItem", readerPrefs.getInt("item_$progressKey", 0))
                .put("readerOffset", readerPrefs.getInt("offset_$progressKey", 0))
                .put("readerPage", readerPrefs.getInt("page_$progressKey", 0))
                .put("readerHighlights", readerPrefs.getString("highlights_$progressKey", "[]"))
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
        return JSONObject().put("backupVersion", 1)
            .put("updatedAt", prefs.getLong("local_revision", 0L))
            .put("sections", org.json.JSONArray(sections))
            .put("wishList", org.json.JSONArray(prefs.getString("wish_list", "[]")))
            .put("books", booksJson).toString()
    }

    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val payload = saveMutex.withLock { cloudSnapshot() }
                    getApplication<Application>().contentResolver.openOutputStream(destination, "wt")
                        ?.bufferedWriter()?.use { it.write(payload) }
                        ?: throw IllegalStateException("No se pudo escribir el respaldo")
                }
                message = "Respaldo de MiBiblioteca guardado."
            } catch (e: Exception) { message = "No se pudo exportar: ${e.localizedMessage}" }
        }
    }

    fun importBackup(source: Uri) {
        viewModelScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(source)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("No se pudo abrir el respaldo")
                }
                val backup = JSONObject(payload)
                if (backup.optInt("backupVersion") != 1 || backup.optJSONArray("books") == null)
                    throw IllegalArgumentException("El archivo no es un respaldo de MiBiblioteca")
                val matched = applyBackup(backup)
                if (matched < backup.getJSONArray("books").length()) {
                    prefs.edit().putString("pending_backup", payload).apply()
                    message = if (books.isEmpty())
                        "Respaldo recibido. Selecciona tu carpeta de libros y sincroniza para recuperarlo."
                    else "$matched fichas restauradas. Las restantes se recuperarán al sincronizar."
                } else message = "$matched fichas restauradas."
            } catch (e: Exception) { message = "No se pudo importar: ${e.localizedMessage}" }
        }
    }

    private suspend fun applyBackup(backup: JSONObject): Int {
        val entries = backup.optJSONArray("books") ?: return 0
        val records = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
        val normalize: (String) -> String = { value ->
            java.text.Normalizer.normalize(cleanCatalogText(value).lowercase(),
                java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^\\p{L}\\p{N}]"), "")
        }
        val selected = mutableSetOf<Int>()
        var matched = 0
        val restored = books.map { book ->
            val index = records.indices.firstOrNull { i ->
                if (i in selected) false else {
                    val record = records[i]
                    val isbn = record.optString("isbn").filter(Char::isDigit)
                    record.optString("uri") == book.uri.toString() ||
                        (isbn.length >= 10 && isbn == book.isbn.filter(Char::isDigit)) ||
                        (normalize(record.optString("customTitle").ifBlank { record.optString("title") }) ==
                            normalize(displayTitle(book)) &&
                            normalize(record.optString("customAuthor").ifBlank { record.optString("author") }) ==
                            normalize(displayAuthor(book)))
                }
            } ?: return@map book
            selected += index; matched++
            val record = records[index]
            val names = record.optJSONArray("sections")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter(String::isNotBlank)
            }.orEmpty().ifEmpty { listOfNotNull(record.optString("section").takeIf(String::isNotBlank)) }
            val uri = book.uri
            val progressKey = readerProgressKey(uri)
            readerPrefs.edit().putInt("percent_$progressKey", record.optInt("readerPercent"))
                .putInt("item_$progressKey", record.optInt("readerItem"))
                .putInt("offset_$progressKey", record.optInt("readerOffset"))
                .putInt("page_$progressKey", record.optInt("readerPage"))
                .putString("highlights_$progressKey", record.optString("readerHighlights", "[]")).apply()
            prefs.edit().putString("info_" + uri, JSONObject()
                .put("plot", record.optString("plot")).put("bio", record.optString("bio")).toString())
                .putBoolean("manual_info_" + uri, record.optBoolean("manualInfo")).apply()
            val image = record.optString("coverBase64")
            if (image.isNotBlank() && image.length < 2_000_000) withContext(Dispatchers.IO) {
                val bytes = Base64.decode(image, Base64.DEFAULT)
                val file = coverFile(getApplication(), uri)
                file.writeBytes(bytes)
                File(getApplication<Application>().filesDir, "manual-" + file.name).writeBytes(bytes)
            }
            book.copy(section = names.firstOrNull().orEmpty(), sections = names,
                notes = record.optString("notes"), goodreadsUrl = record.optString("goodreadsUrl"),
                wantToRead = record.optBoolean("wantToRead"), favorite = record.optBoolean("favorite"),
                customTitle = record.optString("customTitle"), customAuthor = record.optString("customAuthor"),
                sagaOrder = record.optString("sagaOrder"),
                status = ReadingStatus.entries.firstOrNull { it.name == record.optString("status") }
                    ?: book.status,
                spanishPlot = record.optString("plot"), authorBio = record.optString("bio"),
                plotSource = record.optString("plotSource"), bioSource = record.optString("bioSource"),
                cover = if (image.isNotBlank()) withContext(Dispatchers.IO) {
                    coverFile(getApplication(), uri).readBytes()
                } else book.cover)
        }
        if (matched > 0) {
            books = restored
            val names = backup.optJSONArray("sections")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            }.orEmpty()
            sections = (sections + names).filter(String::isNotBlank).distinct()
            prefs.edit().putString("sections", org.json.JSONArray(sections).toString()).apply()
            backup.optJSONArray("wishList")?.let { prefs.edit().putString("wish_list", it.toString()).apply() }
            loadWishList(); saveBooks()
        }
        return matched
    }

    private fun saveCloud() {
        prefs.edit().putLong("local_revision", System.currentTimeMillis()).apply()
        val tree = prefs.getString(folderKey, null)?.let(Uri::parse) ?: return
        viewModelScope.launch {
            try {
                cloudMutex.withLock {
                    val snapshot = withContext(Dispatchers.IO) { cloudSnapshot() }
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
        withContext(Dispatchers.IO) { bookStore.replaceAll(booksArray) }
        for (i in 0 until booksArray.length()) {
            val j = booksArray.getJSONObject(i)
            val uri = Uri.parse(j.getString("uri"))
            val progressKey = readerProgressKey(uri)
            readerPrefs.edit().putInt("percent_$progressKey", j.optInt("readerPercent"))
                .putInt("item_$progressKey", j.optInt("readerItem"))
                .putInt("offset_$progressKey", j.optInt("readerOffset"))
                .putInt("page_$progressKey", j.optInt("readerPage"))
                .putString("highlights_$progressKey", j.optString("readerHighlights", "[]")).apply()
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

    fun moveSection(name: String, direction: Int) {
        val from = sections.indexOf(name)
        val to = from + direction
        if (from < 0 || to !in sections.indices) return
        sections = sections.toMutableList().apply { add(to, removeAt(from)) }
        prefs.edit().putString("sections", org.json.JSONArray(sections).toString()).apply()
        saveCloud()
    }

    fun deleteSection(name: String) {
        sections = sections.filterNot { it == name }
        prefs.edit().putString("sections", org.json.JSONArray(sections).toString()).apply()
        books = books.map { book ->
            val remaining = bookSections(book).filterNot { it == name }
            if (bookSections(book).contains(name)) book.copy(section = remaining.firstOrNull().orEmpty(),
                sections = remaining) else book
        }
        if (selectedSection == name) selectedSection = null
        saveBooks()
    }

    fun editIdentity(uri: Uri, title: String, author: String, saga: String, order: String) {
        prefs.edit().putBoolean("manual_identity_" + uri, true).apply()
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
            authorBio = bio.trim(), plotSource = "Editado por ti",
            bioSource = "Editado por ti") else it }
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
                books = books.map { if (it.uri == uri) it.copy(cover = bytes, coverSource = "Imagen elegida por ti") else it }
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
        books = books.map { book -> if (book.uri == uri) {
            val current = bookSections(book)
            val next = when {
                section.isBlank() -> emptyList()
                section in current -> current - section
                else -> current + section
            }
            book.copy(section = next.firstOrNull().orEmpty(), sections = next)
        } else book }
        saveBooks()
    }

    val filtered: List<Book> get() = filterBooks(books, query, statusFilter,
        onlyFavorites, selectedSection, qualityFilter, reviewPending)

    private fun needsDetails(book: Book) = book.cover == null || book.spanishPlot.isBlank() ||
        book.authorBio.isBlank() || displayAuthor(book) == "Biblioteca de Diroka77"

    private fun markForReview(uri: Uri) {
        reviewPending = reviewPending + uri
        prefs.edit().putString("review_pending",
            org.json.JSONArray(reviewPending.map(Uri::toString)).toString()).apply()
    }

    fun confirmBookDetails(uri: Uri) {
        reviewPending = reviewPending - uri
        prefs.edit().putString("review_pending",
            org.json.JSONArray(reviewPending.map(Uri::toString)).toString()).apply()
    }

    fun refreshIncomplete() {
        if (bulkInfoLoading) return
        val targets = books.filter(::needsDetails).map { it.uri }
        if (targets.isEmpty()) { message = "Todas las fichas tienen portada y datos."; return }
        bulkJob = viewModelScope.launch {
            bulkInfoLoading = true; bulkProgress = 0
            try {
                for (uri in targets) {
                    while (uri in autoJobs) delay(300)
                    completeMissing(uri, retry = true)
                    while (uri in autoJobs) delay(300)
                    bulkProgress++
                    delay(800)
                }
            } finally { bulkInfoLoading = false }
        }
    }

    fun cancelIncomplete() { bulkJob?.cancel(); bulkInfoLoading = false }

    fun recordOpen(uri: Uri) {
        prefs.edit().putLong("opened_" + uri.toString().hashCode(), System.currentTimeMillis()).apply()
    }

    fun readingBooks(): List<Book> = books.filter { it.status == ReadingStatus.READING }
        .sortedByDescending { prefs.getLong("opened_" + it.uri.toString().hashCode(), 0L) }

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
                    syncReport = message.orEmpty()
                    return@launch
                }
                books = result.map { b ->
                    val saved = prefs.getString("info_" + b.uri, null)?.let { JSONObject(it) }
                    val prior = cached[b.uri]
                    b.copy(spanishPlot = prior?.spanishPlot ?: saved?.optString("plot").orEmpty(),
                        authorBio = prior?.authorBio ?: saved?.optString("bio").orEmpty(),
                        section = prior?.section.orEmpty(), sections = prior?.sections.orEmpty(),
                        notes = prior?.notes.orEmpty(),
                        goodreadsUrl = prior?.goodreadsUrl.orEmpty(), wantToRead = prior?.wantToRead ?: false,
                        customTitle = prior?.customTitle.orEmpty(),
                        customAuthor = if (b.metadataRevision >= 50 && !unknownAuthor(b.author) &&
                            prior?.customTitle.isNullOrBlank() &&
                            !prefs.getBoolean("manual_identity_" + b.uri, false)) ""
                            else prior?.customAuthor.orEmpty(),
                        sagaOrder = prior?.sagaOrder ?: b.sagaOrder,
                        favorite = prior?.favorite ?: false, status = prior?.status ?: ReadingStatus.PENDING,
                        plotSource = prior?.plotSource.orEmpty(), bioSource = prior?.bioSource.orEmpty(),
                        coverSource = prior?.coverSource?.takeIf(String::isNotBlank)
                            ?: if (b.cover != null) "Portada del EPUB" else "")
                }
                val previousUris = cached.keys
                val currentUris = result.map { it.uri }.toSet()
                val added = (currentUris - previousUris).size
                val changed = result.count { fresh ->
                    cached[fresh.uri]?.let { old ->
                        old.sourceSize != fresh.sourceSize || old.sourceModified != fresh.sourceModified
                    } ?: false
                }
                val missing = (previousUris - currentUris).size
                syncSummary = "$added nuevos · $changed modificados · $missing fuera de la carpeta"
                saveBooks()
                syncCount = result.size
                prefs.getString("pending_backup", null)?.let { pending ->
                    try {
                        val backup = JSONObject(pending)
                        if (applyBackup(backup) >= (backup.optJSONArray("books")?.length() ?: 0))
                            prefs.edit().remove("pending_backup").apply()
                    } catch (_: Exception) {}
                }
                syncReport = "Última sincronización: " +
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "ES"))
                        .format(java.util.Date()) +
                    "\n${result.size} archivos · $added nuevos · $changed modificados · $missing ya no están en la carpeta."
                prefs.edit().putString("sync_report", syncReport).apply()
                folderName = withContext(Dispatchers.IO) { DocumentFile.fromTreeUri(getApplication(), uri)?.name }
                prefs.edit().putString("folder_name", folderName).apply()
                message = "${result.size} libros y documentos encontrados"
                refreshMissingCovers()
                } catch (e: Exception) {
                message = "No se pudo leer la carpeta de Drive: ${e.localizedMessage ?: "error de acceso"}"
                syncReport = message.orEmpty()
            } finally { syncing = false }
        }
    }

    private fun refreshMissingCovers() {
        coverRefreshJob?.cancel()
        val targets = books.filter { it.cover == null }.map { it.uri }
        coverRefreshJob = viewModelScope.launch {
            coverUpdateRunning = targets.isNotEmpty()
            coverUpdateDone = 0; coverUpdateTotal = targets.size
            var dirty = false
            var savedCount = 0
            try {
                val pending = java.util.ArrayDeque(targets)
                kotlinx.coroutines.coroutineScope {
                  repeat(2) { launch {
                   while (pending.isNotEmpty()) {
                    val uri = pending.removeFirst()
                    val current = books.firstOrNull { it.uri == uri } ?: continue
                    if (current.cover != null || !autoJobs.add(uri)) continue
                    try {
                        val app = getApplication<Application>()
                        withContext(Dispatchers.IO) {
                            File(app.filesDir, "removed-" + coverFile(app, uri).name).delete()
                        }
                        autoCoverLoading = autoCoverLoading + uri
                        val bytes = withContext(Dispatchers.IO) { fetchCover(current) } ?: continue
                        // A manual replacement during the request must win.
                        if (books.firstOrNull { it.uri == uri }?.cover != null) continue
                        withContext(Dispatchers.IO) {
                            File(app.filesDir, "removed-" + coverFile(app, uri).name).delete()
                            coverFile(app, uri).writeBytes(bytes)
                        }
                        books = books.map { if (it.uri == uri) it.copy(cover = bytes,
                            coverSource = "Catálogos públicos") else it }
                        dirty = true
                        savedCount++
                        if (savedCount % 8 == 0) { saveBooks(); dirty = false }
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        throw kotlinx.coroutines.CancellationException()
                    } catch (_: Exception) {
                        // An unavailable catalogue must not interrupt the remaining books.
                    } finally {
                        autoCoverLoading = autoCoverLoading - uri
                        autoJobs.remove(uri)
                        coverUpdateDone++
                    }
                    delay(150)
                   }
                  } }
                }
            } finally {
                if (dirty) saveBooks()
                coverUpdateRunning = false
            }
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
                    books = books.map { if (it.uri == book.uri) it.copy(cover = bytes, coverSource = "Catálogos públicos") else it }
                    message = "Portada guardada en este dispositivo."
                }
            } catch (e: Exception) {
                message = "No se pudo descargar la portada: ${e.localizedMessage ?: "comprueba la conexión"}"
            } finally { coverLoading = null }
        }
    }

    fun duplicateGroups(): List<List<Book>> = findDuplicateGroups(books)

    fun possibleDuplicates(book: Book): List<Book> = duplicateGroups()
        .firstOrNull { group -> group.any { it.uri == book.uri } }
        ?.filterNot { it.uri == book.uri }.orEmpty()

    fun deleteDuplicate(book: Book) {
        viewModelScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) {
                    DocumentFile.fromSingleUri(getApplication(), book.uri)?.delete() == true
                }
                if (removed) {
                    books = books.filterNot { it.uri == book.uri }
                    saveBooks()
                    message = null
                } else message = "No se pudo borrar. Comprueba el permiso de escritura de Drive."
            } catch (e: Exception) {
                message = "No se pudo borrar: ${e.localizedMessage ?: "sin permiso"}"
            }
        }
    }

    fun completeMissing(uri: Uri, force: Boolean = false, retry: Boolean = false) {
        val initial = books.firstOrNull { it.uri == uri } ?: return
        if (!autoJobs.add(uri)) return
        val app = getApplication<Application>()
        val now = System.currentTimeMillis()
        val cooldown = 3L * 24 * 60 * 60 * 1000
        val coverBlocked = !force && !retry &&
            File(app.filesDir, "removed-" + coverFile(app, uri).name).exists()
        val needCover = initial.cover == null && !coverBlocked &&
            (force || retry || now - prefs.getLong("auto_cover_v28_" + uri, 0L) > cooldown)
        val needInfo = (force || initial.spanishPlot.isBlank() || initial.authorBio.isBlank() ||
                displayAuthor(initial) == "Biblioteca de Diroka77") &&
            !prefs.getBoolean("manual_info_" + uri, false) &&
            (force || retry || now - prefs.getLong("auto_info_v39_" + uri, 0L) > cooldown)
        if (!needCover && !needInfo) { autoJobs.remove(uri); return }
        viewModelScope.launch {
            var changed = false
            try {
                if (needInfo) {
                    autoInfoLoading = autoInfoLoading + uri
                    prefs.edit().putLong("auto_info_v39_" + uri, now).apply()
                    try {
                        val latest = books.firstOrNull { it.uri == uri } ?: initial
                        val found = withContext(Dispatchers.IO) {
                            fetchSpanishInfo(latest)
                        }
                        val google = if (found.plot.isBlank() ||
                            displayAuthor(latest) == "Biblioteca de Diroka77")
                            withContext(Dispatchers.IO) { fetchGoogleBookInfo(latest) }
                        else "" to ""
                        val catalogAuthor = if (google.first.isBlank() &&
                            displayAuthor(latest) == "Biblioteca de Diroka77")
                            withContext(Dispatchers.IO) { fetchOpenLibraryAuthor(latest) } else ""
                        val author = google.first.ifBlank { catalogAuthor.ifBlank { displayAuthor(latest) } }
                        val improved = if (found.bio.isBlank() && author != displayAuthor(latest))
                            withContext(Dispatchers.IO) {
                                fetchSpanishInfo(latest.copy(customAuthor = author))
                            } else found
                        val rawPlot = found.plot.ifBlank { improved.plot.ifBlank { google.second } }
                        val plotSource = if (found.plot.isNotBlank()) found.plotSource else
                            improved.plotSource.ifBlank { "Google Libros" }
                        val plot = if (rawPlot.isBlank()) "" else if (plotSource == "Google Libros" ||
                            plotSource == "Wikipedia en español") rawPlot else ensureSpanish(rawPlot)
                        val rawBio = found.bio.ifBlank { improved.bio }
                        val bioSource = found.bioSource.ifBlank { improved.bioSource }
                        val bio = if (rawBio.isBlank()) "" else if (bioSource == "Wikipedia (es)")
                            rawBio else ensureSpanish(rawBio)
                        val current = books.firstOrNull { it.uri == uri }
                        if (current != null) {
                            val replaceInfo = force && !prefs.getBoolean("manual_info_" + uri, false)
                            val updated = current.copy(
                                customAuthor = if ((current.customAuthor.isBlank() ||
                                    current.customAuthor == "Biblioteca de Diroka77") &&
                                    author != "Biblioteca de Diroka77" &&
                                    author.isNotBlank()) author else current.customAuthor,
                                spanishPlot = if (replaceInfo && plot.isNotBlank()) plot else
                                    current.spanishPlot.ifBlank { plot },
                                authorBio = if (replaceInfo && bio.isNotBlank()) bio else
                                    current.authorBio.ifBlank { bio },
                                plotSource = if ((replaceInfo || current.spanishPlot.isBlank()) && plot.isNotBlank())
                                    plotSource else current.plotSource,
                                bioSource = if ((replaceInfo || current.authorBio.isBlank()) && bio.isNotBlank())
                                    bioSource else current.bioSource)
                            if (updated != current) {
                                books = books.map { if (it.uri == uri) updated else it }
                                markForReview(uri)
                                prefs.edit().putString("info_" + uri, JSONObject()
                                    .put("plot", updated.spanishPlot).put("bio", updated.authorBio).toString()).apply()
                                changed = true
                            }
                        }
                    } catch (_: Exception) {}
                    autoInfoLoading = autoInfoLoading - uri
                }
                if (needCover) {
                    autoCoverLoading = autoCoverLoading + uri
                    prefs.edit().putLong("auto_cover_v28_" + uri, now).apply()
                    val bytes = try { withContext(Dispatchers.IO) { fetchCover(books.firstOrNull { it.uri == uri } ?: initial) } }
                        catch (_: Exception) { null }
                    val current = books.firstOrNull { it.uri == uri }
                    if (bytes != null && current?.cover == null) {
                        withContext(Dispatchers.IO) {
                            File(app.filesDir, "removed-" + coverFile(app, uri).name).delete()
                            coverFile(app, uri).writeBytes(bytes)
                        }
                        books = books.map { if (it.uri == uri) it.copy(
                            cover = bytes, coverSource = "Catálogos públicos") else it }
                        markForReview(uri)
                        changed = true
                    }
                    autoCoverLoading = autoCoverLoading - uri
                }
                if (changed) saveBooks()
                if (force) detailMessage = if (changed)
                    "Ficha actualizada con los datos encontrados."
                else null
            } finally {
                autoCoverLoading = autoCoverLoading - uri
                autoInfoLoading = autoInfoLoading - uri
                autoJobs.remove(uri)
            }
        }
    }

    fun enrich(book: Book) {
        if (infoLoading != null) return
        viewModelScope.launch {
            infoLoading = book.uri
            try {
                val google = withContext(Dispatchers.IO) { fetchGoogleBookInfo(book) }
                val resolvedAuthor = if (google.first.isNotBlank()) google.first else displayAuthor(book)
                val fallback = withContext(Dispatchers.IO) {
                    fetchSpanishInfo(book.copy(customAuthor = resolvedAuthor))
                }
                val plot = ensureSpanish(google.second.ifBlank { fallback.plot })
                val bio = ensureSpanish(fallback.bio)
                if (google.first.isBlank() && plot.isBlank() && bio.isBlank()) {
                    message = null
                } else {
                    infoCandidate = book.uri to InfoCandidate(
                        resolvedAuthor, plot, bio,
                        if (google.second.isNotBlank()) "Google Libros" else fallback.plotSource,
                        fallback.bioSource)
                }
            } catch (e: Exception) {
                message = "No se pudo consultar la información: ${e.localizedMessage ?: "comprueba la conexión"}"
            } finally { infoLoading = null }
        }
    }

    fun dismissCandidate() { infoCandidate = null }

    fun acceptCandidate(uri: Uri, candidate: InfoCandidate) {
        val current = books.firstOrNull { it.uri == uri } ?: return
        val updated = current.copy(
            customAuthor = candidate.author.takeIf { it.isNotBlank() && it != "Biblioteca de Diroka77" }
                ?: current.customAuthor,
            spanishPlot = candidate.plot.ifBlank { current.spanishPlot },
            authorBio = candidate.bio.ifBlank { current.authorBio },
            plotSource = candidate.plotSource.ifBlank { current.plotSource },
            bioSource = candidate.bioSource.ifBlank { current.bioSource })
        books = books.map { if (it.uri == uri) updated else it }
        prefs.edit().putString("info_" + uri, JSONObject()
            .put("plot", updated.spanishPlot).put("bio", updated.authorBio).toString()).apply()
        saveBooks()
        infoCandidate = null
    }

    private suspend fun fillMissingDetails() {
        for (book in books.toList().filter { it.cover == null &&
            !File(getApplication<Application>().filesDir, "removed-" + coverFile(getApplication(), it.uri).name).exists() &&
            !prefs.getBoolean("cover_attempt_v9_" + it.uri, false) }.take(20)) {
            if (prefs.getBoolean("cover_attempt_v9_" + book.uri, false)) continue
            prefs.edit().putBoolean("cover_attempt_v9_" + book.uri, true).apply()
            val cover = try { withContext(Dispatchers.IO) { fetchCover(book) } } catch (_: Exception) { null }
            if (cover != null) {
                withContext(Dispatchers.IO) { coverFile(getApplication(), book.uri).writeBytes(cover) }
                books = books.map { if (it.uri == book.uri) it.copy(cover = cover, coverSource = "Catálogos públicos") else it }
            }
        }
        for (book in books.toList().filter { (it.spanishPlot.isBlank() || it.authorBio.isBlank()) &&
            !prefs.getBoolean("info_attempt_v9_" + it.uri, false) &&
            !prefs.getBoolean("manual_info_" + it.uri, false) }.take(20)) {
            if (prefs.getBoolean("info_attempt_v9_" + book.uri, false)) continue
            prefs.edit().putBoolean("info_attempt_v9_" + book.uri, true).apply()
            try {
                val found = withContext(Dispatchers.IO) { fetchSpanishInfo(book) }
                val plot = ensureSpanish(found.plot)
                val bio = ensureSpanish(found.bio)
                books = books.map { if (it.uri == book.uri) it.copy(
                    spanishPlot = plot, authorBio = bio,
                    plotSource = if (plot.isNotBlank()) found.plotSource else it.plotSource,
                    bioSource = if (bio.isNotBlank()) found.bioSource else it.bioSource) else it }
                prefs.edit().putString("info_" + book.uri,
                    JSONObject().put("plot", plot).put("bio", bio).toString()).apply()
            } catch (_: Exception) {}
        }
        saveBooks()
    }

    fun toggleFavorite(uri: Uri) { books = books.map { if (it.uri == uri) it.copy(favorite=!it.favorite) else it }; saveBooks() }
    fun setStatus(uri: Uri, status: ReadingStatus) { books = books.map { if (it.uri == uri) it.copy(status=status) else it }; saveBooks() }
    fun chooseViewMode(mode: String, section: String? = selectedSection) {
        viewMode = mode
        prefs.edit().putString("view_mode_" + (section ?: "general"), mode).apply()
    }
    fun viewModeFor(section: String?): String = prefs.getString(
        "view_mode_" + (section ?: "general"), viewMode) ?: viewMode
    fun setHomeNews(show: Boolean) {
        showHomeNews = show; prefs.edit().putBoolean("show_home_news", show).apply()
    }
    fun updateReadingFirst(first: Boolean) {
        readingFirst = first; prefs.edit().putBoolean("reading_first", first).apply()
    }
    fun clearMessage() { message = null }
    fun clearDetailMessage() { detailMessage = null }
}

private fun readableSize(size: Long): String = when {
    size < 0 -> "Desconocido"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> "${"%.1f".format(java.util.Locale.ROOT, size / 1048576.0)} MB"
}

private fun displayAuthor(book: Book): String =
    cleanCatalogText(book.customAuthor.ifBlank { book.author })
        .takeUnless(::unknownAuthor) ?: "Biblioteca de Diroka77"

private fun displayTitle(book: Book): String {
    val author = displayAuthor(book)
    var title = cleanCatalogText(book.customTitle.ifBlank { book.title })
        .replace(Regex("""(?i)\b(?:isbn(?:-1[03])?|autor|editorial|publicad[oa]|idioma|formato|páginas|paginas|sinopsis|descripci[oó]n)\s*[:=].*$"""), "")
        .trim()
    if (author != "Biblioteca de Diroka77") {
        // Remove an author only at a filename boundary; preserve real subtitles.
        title = title.replace(Regex("^" + Regex.escape(author) + "\\s*[-–—|:]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-–—|]\\s*" + Regex.escape(author) + "$", RegexOption.IGNORE_CASE), "")
    }
    return title.replace(Regex("""\s+"""), " ").trim(' ', '-', '–', '—', '|', ',', ':')
        .ifBlank { "Libro sin título" }
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
            else if (f.name.orEmpty().substringAfterLast('.', "").lowercase() in
                setOf("epub", "pdf", "mobi", "azw", "azw3", "txt", "html", "htm", "rtf", "docx", "md")) {
                val size = f.length()
                val modified = f.lastModified()
                val file = coverFile(context, f.uri)
                val prior = cached[f.uri]
                val removalMarker = File(context.filesDir, "removed-" + file.name)
                if (prior?.cover == null) removalMarker.delete()
                val removed = removalMarker.exists()
                val unchanged = prior != null && size > 0 && prior.sourceSize == size &&
                    (modified <= 0L || prior.sourceModified == modified) &&
                    prior.metadataRevision >= 50 && (prior.cover != null || prior.metadataRevision >= 53) &&
                    prior.sourceCoverChecked &&
                    (!prior.hadEmbeddedCover || file.exists() || removed)
                val epub = if (unchanged) prior!! else {
                    val parsed = if (f.name?.endsWith(".epub", true) == true)
                        readEpub(context, f.uri, f.name ?: "Libro", size, modified)
                    else Book(f.uri, f.name.orEmpty().substringBeforeLast('.'),
                        author = prior?.author ?: "Autor desconocido")
                    if (parsed.metadataRevision < 50 && prior != null) prior.copy(
                        sourceSize = size, sourceModified = modified)
                    else parsed.copy(sourceSize = size, sourceModified = modified,
                        author = parsed.author.takeUnless(::unknownAuthor)
                            ?: prior?.author?.takeUnless(::unknownAuthor) ?: "Autor desconocido",
                        sourceCoverChecked = true, hadEmbeddedCover = parsed.cover != null,
                        cover = parsed.cover ?: prior?.cover)
                }
                if (!removed && !file.exists() && epub.cover != null) file.writeBytes(epub.cover)
                val saved = if (unchanged && prior?.cover != null) prior.cover
                    else file.takeIf { it.exists() }?.readBytes()
                out += if (removed) epub.copy(cover = null)
                    else if (saved != null) epub.copy(cover = saved) else epub
                onProgress(out.size)
            }
        }
    }
    walk(root)
    return out.sortedBy { it.title.lowercase() }
}

private fun readEpub(context: Context, uri: Uri, fallbackName: String,
    sourceSize: Long = -1L, sourceModified: Long = -1L): Book {
    val fallback = Book(uri, cleanCatalogText(fallbackName),
        sourceSize = sourceSize, sourceModified = sourceModified)
    return try {
        // Reuse the reader cache so Drive downloads each unchanged file only once.
        ZipFile(localReaderFile(context, fallback)).use { zip ->
            fun entryBytes(path: String, limit: Int): ByteArray? {
                val normalized = normalizePath(path)
                val entry = zip.getEntry(normalized) ?: zip.entries().asSequence()
                    .firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: return null
                if (entry.size > limit) return null
                return zip.getInputStream(entry).use { input ->
                    input.readNBytes(limit + 1).takeIf { it.size <= limit }
                }
            }
            fun resolve(base: String, href: String): String {
                val decoded = Uri.decode(href.substringBefore('#').substringBefore('?'))
                return normalizePath(if (base.isBlank()) decoded else "$base/$decoded")
            }
            val container = entryBytes("META-INF/container.xml", 2_000_000)
                ?.toString(Charsets.UTF_8).orEmpty()
            val containerXml = Jsoup.parse(container, "", org.jsoup.parser.Parser.xmlParser())
            val opfPath = containerXml.getAllElements().firstOrNull {
                it.tagName().substringAfter(':') == "rootfile"
            }?.attr("full-path")?.takeIf(String::isNotBlank)?.let(::normalizePath)
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", true) }?.name
                ?: return@use fallback
            val opfBytes = entryBytes(opfPath, 2_000_000) ?: return@use fallback
            val meta = parseOpf(opfBytes)
            val base = opfPath.substringBeforeLast('/', "")
            val candidates = (listOfNotNull(meta.coverHref) + meta.coverCandidates).distinct()
            var cover: ByteArray? = null
            for (href in candidates) {
                val path = resolve(base, href)
                val bytes = entryBytes(path, 16_000_000) ?: continue
                if (path.endsWith(".html", true) || path.endsWith(".xhtml", true) ||
                    path.endsWith(".htm", true) || path.endsWith(".svg", true)) {
                    val page = Jsoup.parse(bytes.toString(Charsets.UTF_8), "", org.jsoup.parser.Parser.xmlParser())
                    val image = page.getAllElements().firstOrNull {
                        it.tagName().substringAfter(':').lowercase() in listOf("img", "image", "object")
                    }
                    val imageHref = image?.attr("src").orEmpty().ifBlank {
                        image?.attr("href").orEmpty().ifBlank {
                            image?.attr("xlink:href").orEmpty().ifBlank { image?.attr("data").orEmpty() }
                        }
                    }
                    val openingPage = href in meta.coverCandidates && href != meta.coverHref &&
                        !href.contains(Regex("(?i)cover|portada|front|couverture|couv|jacket|tapa"))
                    if (imageHref.isNotBlank() && (!openingPage || page.text().length < 180)) {
                        val imageBytes = entryBytes(
                            resolve(path.substringBeforeLast('/', ""), imageHref), 16_000_000)
                        cover = imageBytes?.let { if (openingPage) compactPortraitCover(it) else compactCover(it) }
                    }
                } else cover = compactCover(bytes)
                if (cover != null) break
            }
            if (cover == null) {
                // Many EPUBs have no cover declaration: the first large portrait image is
                // normally the cover. Ignore small icons, logos and chapter artwork.
                for (href in meta.imageCandidates.take(5)) {
                    if (href.contains(Regex("(?i)logo|icon|banner|chapter|capitulo|decor"))) continue
                    val bytes = entryBytes(resolve(base, href), 16_000_000) ?: continue
                    cover = compactPortraitCover(bytes)
                    if (cover != null) break
                }
            }
            if (cover == null) {
                val namedCover = zip.entries().asSequence().firstOrNull {
                    it.name.substringAfterLast('/').matches(
                        Regex("(?i)(?:cover|portada|front)(?:[-_0-9]*)\\.(?:jpe?g|png|webp)"))
                }
                cover = namedCover?.name?.let { entryBytes(it, 16_000_000) }?.let(::compactPortraitCover)
            }
            Book(uri, meta.title.ifBlank { fallback.title }, meta.author, meta.date, meta.publisher,
                meta.genre, meta.description, meta.isbn, meta.saga, cover,
                language = meta.language, sagaOrder = meta.sagaOrder, metadataRevision = 53)
        }
    } catch (_: Exception) { fallback }
}

private fun compactCover(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1800) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return try {
        java.io.ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
            output.toByteArray()
        }
    } finally { bitmap.recycle() }
}

private fun compactPortraitCover(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth < 250 || bounds.outHeight < 350 ||
        bounds.outWidth.toFloat() / bounds.outHeight !in 0.42f..0.9f) return null
    return compactCover(bytes)
}

private fun coverFile(context: Context, uri: Uri): File {
    val hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(context.filesDir, "cover-$hash.jpg")
}

private fun downloadImage(url: String): ByteArray? {
    val connection = URL(url.replace("http://", "https://")).openConnection() as HttpURLConnection
    connection.connectTimeout = 6000
    connection.readTimeout = 8000
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MiBiblioteca/0.51)")
    connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
    return try {
        if (connection.responseCode != 200 || connection.contentLengthLong > 16_000_000) return null
        connection.inputStream.use { input ->
            val bytes = input.readNBytes(16_000_001)
            if (bytes.size > 16_000_000) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth < 80 || bounds.outHeight < 120 ||
                bounds.outWidth.toFloat() / bounds.outHeight !in 0.3f..1.25f) return null
            compactCover(bytes)
        }
    } catch (_: Exception) { null }
    finally { connection.disconnect() }
}

private fun catalogMatch(candidateTitle: String, candidateAuthors: List<String>, book: Book): Boolean =
    coverTitleMatches(displayTitle(book), candidateTitle) &&
        (unknownAuthor(displayAuthor(book)) || candidateAuthors.any {
            coverAuthorMatches(displayAuthor(book), it)
        })

private fun publicCoverPage(url: String): String = Jsoup.connect(url)
    .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
    .timeout(8000).maxBodySize(2_000_000).get().outerHtml()

private fun fetchPublicCover(book: Book): ByteArray? {
    val title = displayTitle(book).substringBefore(" (").substringBefore(" [")
    val author = displayAuthor(book).takeUnless(::unknownAuthor).orEmpty()
    val isbn = book.isbn.filter { it.isDigit() || it.uppercaseChar() == 'X' }
    val seen = mutableSetOf<String>()
    fun tryPage(url: String): ByteArray? {
        if (!isPublicBookPage(url) || !seen.add(url)) return null
        return try {
            publicCoverImages(publicCoverPage(url), url, title, author, isbn).firstNotNullOfOrNull(::downloadImage)
        } catch (_: Exception) { null }
    }
    if (book.goodreadsUrl.isNotBlank()) tryPage(book.goodreadsUrl)?.let { return it }
    val query = java.net.URLEncoder.encode("\"$title\" $author portada " +
        "(site:casadellibro.com OR site:planetadelibros.com OR site:penguinlibros.com OR " +
        "site:goodreads.com OR site:fnac.es OR site:kobo.com OR site:anagrama-ed.es OR site:alianzaeditorial.es)", "UTF-8")
    val engines = listOf("https://www.google.com/search?hl=es&num=8&q=$query",
        "https://www.bing.com/search?format=rss&q=$query",
        "https://html.duckduckgo.com/html/?q=$query")
    for (engine in engines) {
        val links = try { coverSearchLinks(publicCoverPage(engine), engine) }
            catch (_: Exception) { emptyList() }
        for (link in links.take(5)) tryPage(link)?.let { return it }
    }
    // Goodreads' own search is independent of the search engines.
    try {
        val url = "https://www.goodreads.com/search?q=" +
            java.net.URLEncoder.encode("$title $author", "UTF-8")
        for (link in coverSearchLinks(publicCoverPage(url), url).take(3))
            tryPage(link)?.let { return it }
    } catch (_: Exception) {}
    return null
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
        val docs = getJson("https://openlibrary.org/search.json?$q&fields=cover_i,title,author_name&limit=10")
            .optJSONArray("docs")
        for (i in 0 until (docs?.length() ?: 0)) {
            val doc = docs?.optJSONObject(i) ?: continue
            val authors = doc.optJSONArray("author_name")
            val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            if (!catalogMatch(doc.optString("title"), names, book)) continue
            val id = doc.optLong("cover_i", 0)
            if (id > 0) for (size in listOf("L", "M"))
                downloadImage("https://covers.openlibrary.org/b/id/$id-$size.jpg?default=false")
                    ?.let { return it }
        }
    } catch (_: Exception) {}
    try {
        val author = displayAuthor(book).takeUnless { it == "Biblioteca de Diroka77" }.orEmpty()
        val queries = listOfNotNull(
            "isbn:$isbn".takeIf { isbn.length == 10 || isbn.length == 13 },
            "intitle:${displayTitle(book)}" + if (author.isNotBlank()) " inauthor:$author" else "",
            "intitle:${displayTitle(book)}").distinct()
        for (q in queries) {
            val url = "https://www.googleapis.com/books/v1/volumes?q=" +
                java.net.URLEncoder.encode(q, "UTF-8") + "&maxResults=10"
            val items = try { getJson(url).optJSONArray("items") } catch (_: Exception) { continue }
            for (i in 0 until (items?.length() ?: 0)) {
                val info = items?.optJSONObject(i)?.optJSONObject("volumeInfo") ?: continue
                val authors = info.optJSONArray("authors")
                val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
                if (!catalogMatch(info.optString("title"), names, book)) continue
                val images = info.optJSONObject("imageLinks")
                for (size in listOf("large", "medium", "thumbnail")) {
                    val link = images?.optString(size).orEmpty()
                    if (link.isNotBlank()) downloadImage(link.replace("http://", "https://"))
                        ?.let { return it }
                }
            }
        }
    } catch (_: Exception) {}
    return fetchPublicCover(book)
}

private fun getJson(url: String): JSONObject {
    if (url.contains("www.googleapis.com/books") && System.currentTimeMillis() < googleBooksRetryAt)
        throw IllegalStateException("Google Libros ha agotado su cuota temporalmente")
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 12000
    connection.setRequestProperty("User-Agent", "MiBiblioteca/0.39 (https://github.com/diroka77-pixel/Mibiblioteca7)")
    return try {
        if (connection.responseCode == 429 && url.contains("www.googleapis.com/books")) {
            googleBooksRetryAt = System.currentTimeMillis() + 60 * 60 * 1000L
            throw IllegalStateException("Cuota de Google Libros agotada")
        }
        connection.inputStream.use { JSONObject(it.bufferedReader().readText()) }
    } finally { connection.disconnect() }
}

@Volatile private var googleBooksRetryAt = 0L

private fun fetchGoogleBookInfo(book: Book): Pair<String, String> {
    val isbn = book.isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
    val title = displayTitle(book)
    val knownAuthor = displayAuthor(book).takeUnless { it == "Biblioteca de Diroka77" }.orEmpty()
    val queries = if (isbn.length == 10 || isbn.length == 13) listOf("isbn:$isbn", "intitle:$title")
        else listOf("intitle:$title" + if (knownAuthor.isNotBlank()) " inauthor:$knownAuthor" else "", "intitle:$title")
    var bestAuthor = ""
    for (query in queries.distinct()) try {
        val url = "https://www.googleapis.com/books/v1/volumes?q=" +
            java.net.URLEncoder.encode(query, "UTF-8") + "&langRestrict=es&maxResults=10"
        val items = getJson(url).optJSONArray("items")
        for (i in 0 until (items?.length() ?: 0)) {
            val info = items?.optJSONObject(i)?.optJSONObject("volumeInfo") ?: continue
            val authors = info.optJSONArray("authors")
            val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            if (!catalogMatch(info.optString("title"), names, book)) continue
            val author = names.filter(String::isNotBlank).joinToString(", ")
            if (bestAuthor.isBlank() && author.isNotBlank()) bestAuthor = author
            if (info.optString("language") != "es") continue
            val plot = android.text.Html.fromHtml(info.optString("description"),
                android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim().take(2500)
            if (plot.length > 80) return (author.ifBlank { bestAuthor }) to plot
        }
    } catch (_: Exception) {}
    return bestAuthor to ""
}

private fun fetchOpenLibraryAuthor(book: Book): String = try {
    val title = java.net.URLEncoder.encode(displayTitle(book), "UTF-8")
    val docs = getJson("https://openlibrary.org/search.json?title=$title&fields=title,author_name&limit=6")
        .optJSONArray("docs")
    (0 until (docs?.length() ?: 0)).mapNotNull { docs?.optJSONObject(it) }
        .firstNotNullOfOrNull { item ->
            val names = item.optJSONArray("author_name") ?: return@firstNotNullOfOrNull null
            val authors = (0 until names.length()).map { names.optString(it) }
            if (catalogMatch(item.optString("title"), authors, book)) authors.firstOrNull()
            else null
        }.orEmpty()
} catch (_: Exception) { "" }

private fun wikipediaPage(query: String, expected: String): Pair<String, String>? {
    val term = java.net.URLEncoder.encode(query, "UTF-8")
    val matches = getJson("https://es.wikipedia.org/w/api.php?action=query&list=search" +
        "&srsearch=$term&srlimit=6&format=json").optJSONObject("query")?.optJSONArray("search")
    val plain: (String) -> String = { value ->
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
    }
    val target = plain(expected)
    if (target.length < 4) return null
    for (i in 0 until (matches?.length() ?: 0)) {
        val title = matches?.optJSONObject(i)?.optString("title").orEmpty()
        val candidate = plain(title)
        if (!(candidate.contains(target) || target.contains(candidate))) continue
        val encoded = java.net.URLEncoder.encode(title, "UTF-8")
        val page = getJson("https://es.wikipedia.org/w/api.php?action=query&prop=extracts" +
            "&exintro=1&explaintext=1&redirects=1&format=json&formatversion=2&titles=$encoded")
            .optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
        val extract = page?.optString("extract").orEmpty().trim()
        if (extract.isNotBlank() && !title.contains("desambiguación", true)) return title to extract
    }
    return null
}

private fun wikipediaPlot(book: Book): String {
    val title = displayTitle(book)
    val author = displayAuthor(book).takeUnless { it == "Biblioteca de Diroka77" }.orEmpty()
    if (author.isBlank()) return ""
    val page = try { wikipediaPage("$title $author", title) ?: wikipediaPage(title, title) }
        catch (_: Exception) { null } ?: return ""
    // A title alone can also resolve to a film, a place or another book.
    if (author.isNotBlank()) {
        val plain: (String) -> String = { value ->
            java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
        }
        val lead = plain(page.second)
        val authorParts = author.split(Regex("[ ,]+")).filter { it.length > 3 }
        if (authorParts.none { lead.contains(plain(it)) }) return ""
    }
    return try {
        val encoded = java.net.URLEncoder.encode(page.first, "UTF-8")
        val sections = getJson("https://es.wikipedia.org/w/api.php?action=parse&prop=sections" +
            "&format=json&page=$encoded").optJSONObject("parse")?.optJSONArray("sections")
        val index = (0 until (sections?.length() ?: 0)).mapNotNull { i ->
            sections?.optJSONObject(i)?.takeIf { section ->
                Regex("(?i)^(argumento|sinopsis|trama|resumen)$")
                    .containsMatchIn(section.optString("line"))
            }?.optString("index")
        }.firstOrNull()
        val body = if (index == null) "" else {
            val html = getJson("https://es.wikipedia.org/w/api.php?action=parse&prop=text" +
                "&format=json&page=$encoded&section=$index")
                .optJSONObject("parse")?.optJSONObject("text")?.optString("*").orEmpty()
            Jsoup.parse(html).select("p").joinToString(" ") { it.text() }.trim()
        }
        body.ifBlank { page.second }.take(1600)
    } catch (_: Exception) { page.second.take(1100) }
}

private fun goodreadsPlot(book: Book): String {
    val title = displayTitle(book)
    val urls = mutableListOf<String>()
    if (book.goodreadsUrl.startsWith("https://www.goodreads.com/book/show/"))
        urls += book.goodreadsUrl
    try {
        val search = Jsoup.connect("https://www.goodreads.com/search?q=" +
            java.net.URLEncoder.encode(title + " " + displayAuthor(book), "UTF-8"))
            .timeout(6500).userAgent("Mozilla/5.0 (Android; MiBiblioteca)").get()
        search.select("a[href*='/book/show/']").firstOrNull()?.absUrl("href")?.let(urls::add)
    } catch (_: Exception) {}
    for (url in urls.distinct()) try {
        val page = Jsoup.connect(url).timeout(6500)
            .userAgent("Mozilla/5.0 (Android; MiBiblioteca)").get()
        if (!page.title().contains(title, ignoreCase = true)) continue
        val description = page.selectFirst("meta[property='og:description']")
            ?.attr("content").orEmpty().trim()
        if (description.length > 90) return description.take(700)
    } catch (_: Exception) {}
    return ""
}

private fun fetchSpanishInfo(book: Book): InfoCandidate {
    var plotSource = ""
    var bioSource = ""
    var bio = ""
    if (displayAuthor(book).isNotBlank() && displayAuthor(book) != "Biblioteca de Diroka77") {
        for (host in listOf("es", "en")) {
            try {
                val title = java.net.URLEncoder.encode(displayAuthor(book), "UTF-8")
                val url = "https://$host.wikipedia.org/w/api.php?action=query&prop=extracts" +
                    "&exintro=1&explaintext=1&redirects=1&format=json&formatversion=2&titles=$title"
                val page = getJson(url).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
                if (page != null && !page.has("missing")) {
                    bio = page.optString("extract").trim().take(900)
                    if (bio.isNotBlank()) { bioSource = "Wikipedia (" + host + ")"; break }
                }
            } catch (_: Exception) {}
        }
    }
    if (bio.isBlank() && displayAuthor(book).isNotBlank() && displayAuthor(book) != "Biblioteca de Diroka77") try {
        val page = wikipediaPage(displayAuthor(book), displayAuthor(book))
        if (page != null) { bio = page.second.take(900); bioSource = "Wikipedia (es)" }
    } catch (_: Exception) {}
    if (bio.isBlank() && displayAuthor(book).isNotBlank() && displayAuthor(book) != "Biblioteca de Diroka77") try {
        val q = java.net.URLEncoder.encode(displayAuthor(book), "UTF-8")
        val authorDocs = getJson("https://openlibrary.org/search/authors.json?q=$q")
            .optJSONArray("docs")
        val wanted = java.text.Normalizer.normalize(displayAuthor(book).lowercase(),
            java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val key = (0 until minOf(authorDocs?.length() ?: 0, 8)).mapNotNull { i ->
            authorDocs?.optJSONObject(i)
        }.firstOrNull { candidate ->
            java.text.Normalizer.normalize(candidate.optString("name").lowercase(),
                java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "") == wanted
        }?.optString("key").orEmpty()
        if (key.matches(Regex("OL[0-9]+A"))) {
            val value = getJson("https://openlibrary.org/authors/$key.json").opt("bio")
            bio = (if (value is JSONObject) value.optString("value") else value as? String)
                .orEmpty().take(1800)
            if (bio.isNotBlank()) bioSource = "Open Library"
        }
    } catch (_: Exception) {}
    var plot = try { wikipediaPlot(book) } catch (_: Exception) { "" }
    if (plot.isNotBlank()) plotSource = "Wikipedia en español"
    if (plot.isBlank() && book.description != "Sin descripción disponible." &&
        (book.language.startsWith("es", true) || book.language.startsWith("spa", true))) {
        plot = book.description.take(2500)
        plotSource = "Metadatos del EPUB"
    }
    if (plot.isBlank()) try {
        val q = "title=" + java.net.URLEncoder.encode(displayTitle(book), "UTF-8") +
            "&author=" + java.net.URLEncoder.encode(displayAuthor(book), "UTF-8")
        val docs = getJson("https://openlibrary.org/search.json?${q}&fields=key,title,author_name&limit=5")
            .optJSONArray("docs")
        for (i in 0 until (docs?.length() ?: 0)) {
            val doc = docs?.optJSONObject(i) ?: continue
            val authors = doc.optJSONArray("author_name")
            val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            if (!catalogMatch(doc.optString("title"), names, book)) continue
            val key = doc.optString("key")
            if (!key.startsWith("/works/")) continue
            val desc = getJson("https://openlibrary.org${key}.json").opt("description")
            plot = (if (desc is JSONObject) desc.optString("value") else desc as? String).orEmpty().take(2500)
            if (plot.isNotBlank()) { plotSource = "Open Library"; break }
        }
    } catch (_: Exception) {}
    if (plot.isBlank()) {
        plot = goodreadsPlot(book)
        if (plot.isNotBlank()) plotSource = "Goodreads"
    }
    return InfoCandidate("", plot, bio, plotSource, bioSource)
}
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

internal suspend fun ensureSpanish(text: String): String {
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

private val Ink = Color(0xFF31271F)
private val Mahogany = Color(0xFF49352A)
private val Brass = Color(0xFFAD8248)
private val Parchment = Color(0xFFF7F6F2)
private val Paper = Color.White
private val Teal = Color(0xFF176C66)

private val libraryColors = lightColorScheme(
    primary = Teal, onPrimary = Paper, secondary = Brass,
    background = Parchment, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFECE0C8)
)

class MainActivity : ComponentActivity() {
    private lateinit var libraryVm: LibraryViewModel
    private var jankStats: JankStats? = null
    private var metricScreen = "Inicio"
    private val framesByScreen = mutableMapOf<String, IntArray>()

    fun setMetricScreen(screen: String) { metricScreen = screen }
    fun performanceReport(): String = listOf("Inicio", "Biblioteca", "Secciones", "Ficha")
        .mapNotNull { name -> framesByScreen[name]?.let { "$name: ${it[1]} lentos de ${it[0]} fotogramas" } }
        .joinToString("\n").ifBlank { "Aún no hay mediciones en esta sesión." }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libraryVm = ViewModelProvider(this)[LibraryViewModel::class.java]
        handleSharedBook(intent)
        setContent { MaterialTheme(colorScheme = libraryColors) { LibraryApp(libraryVm) } }
        jankStats = JankStats.createAndTrack(window) { frame ->
            val counts = framesByScreen.getOrPut(metricScreen) { intArrayOf(0, 0) }
            counts[0]++
            if (frame.isJank) counts[1]++
        }
    }
    override fun onResume() { super.onResume(); jankStats?.isTrackingEnabled = true }
    override fun onPause() { jankStats?.isTrackingEnabled = false; super.onPause() }
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

private fun searchCoverImages(context: Context, book: Book) {
    val query = listOf(displayTitle(book), displayAuthor(book).takeUnless { it == "Biblioteca de Diroka77" }.orEmpty(),
        "portada libro").filter(String::isNotBlank).joinToString(" ")
    val url = Uri.parse("https://www.google.com/search?tbm=isch&q=" +
        java.net.URLEncoder.encode(query, "UTF-8"))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url).setPackage("com.google.android.googlequicksearchbox"))
    } catch (_: Exception) {
        openChrome(context, url.toString())
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

@Composable internal fun AppIcon(kind: String, description: String? = null,
    tint: Color = LocalContentColor.current, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val vector = when (kind) {
        "Inicio" -> Icons.Outlined.Home
        "Biblioteca", "Libros" -> Icons.Outlined.LibraryBooks
        "Pendientes", "Pendiente" -> Icons.Outlined.Schedule
        "Secciones", "Todas" -> Icons.Outlined.CollectionsBookmark
        "Carpeta" -> Icons.Outlined.Folder
        "Favoritos" -> Icons.Outlined.FavoriteBorder
        "Favorito" -> Icons.Outlined.Favorite
        "Leyendo", "Leer", "Goodreads", "Sincronizar" -> Icons.Outlined.AutoStories
        "Leídos", "Leído", "Revisado" -> Icons.Outlined.CheckCircle
        "Autores", "Autor", "Biografía" -> Icons.Outlined.PersonOutline
        "Sagas" -> Icons.Outlined.Layers
        "Google", "Google IA" -> Icons.Outlined.AutoAwesome
        "Casa del Libro" -> Icons.Outlined.Storefront
        "Buscar" -> Icons.Outlined.Search
        "Cerrar" -> Icons.Outlined.Close
        "Volver" -> Icons.Outlined.ArrowBack
        "Índice", "Lista" -> Icons.Outlined.List
        "Galería" -> Icons.Outlined.GridView
        "Compacta" -> Icons.Outlined.ViewHeadline
        "Noticias" -> Icons.Outlined.Newspaper
        "Opciones" -> Icons.Outlined.MoreVert
        "Ajustes" -> Icons.Outlined.Settings
        "Estado" -> Icons.Outlined.Info
        "Subir" -> Icons.Outlined.KeyboardArrowUp
        "Bajar" -> Icons.Outlined.KeyboardArrowDown
        "Borrar" -> Icons.Outlined.DeleteOutline
        "Añadir" -> Icons.Outlined.Add
        "Portada" -> Icons.Outlined.Image
        "Editar" -> Icons.Outlined.Edit
        "Datos", "Actualizar" -> Icons.Outlined.Refresh
        "Compartir" -> Icons.Outlined.Share
        "Descargar" -> Icons.Outlined.Download
        "Guardar" -> Icons.Outlined.Save
        "Noche" -> Icons.Outlined.DarkMode
        "Día" -> Icons.Outlined.LightMode
        else -> Icons.Outlined.MenuBook
    }
    Icon(vector, description, Modifier.size(size), tint = tint)
}

@Composable private fun ActionLabel(text: String, icon: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(icon, size = 20.dp)
        Text(text, fontSize = fontSize)
    }
}

@Composable private fun CatalogChip(label: String, selected: Boolean, accent: Color = Teal,
    onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = {
        Text(label.removePrefix("★ "), fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }, leadingIcon = { AppIcon(label.removePrefix("★ ").substringBefore(" ("), size = 18.dp) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) accent.copy(alpha = 0.45f) else Mahogany.copy(alpha = 0.12f)),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Paper, labelColor = Mahogany,
            selectedContainerColor = accent.copy(alpha = 0.14f), selectedLabelColor = accent))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryApp(vm: LibraryViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf<Book?>(null) }
    var readingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf("Inicio") }
    val tabs = remember { listOf("Inicio", "Biblioteca", "Pendientes", "Secciones") }
    var addingSection by remember { mutableStateOf(false) }
    var sectionName by remember { mutableStateOf("") }
    var sectionToDelete by remember { mutableStateOf<String?>(null) }
    var showWishList by remember { mutableStateOf(false) }
    fun switchTab(name: String) {
        tab = name
        showWishList = false
        vm.selectedSection = null
        vm.qualityFilter = if (name == "Pendientes") "Todos" else "Ninguno"
    }
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
    BackHandler(enabled = readingUri == null && (selected != null || showWishList || tab != "Inicio")) {
        when {
            selected != null -> selected = null
            showWishList -> showWishList = false
            else -> { tab = "Inicio"; vm.selectedSection = null; vm.qualityFilter = "Ninguno" }
        }
    }
    val homeListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val pendingListState = rememberLazyListState()
    val sectionsListState = rememberLazyListState()
    val listState = when (tab) {
        "Inicio" -> homeListState
        "Biblioteca" -> libraryListState
        "Pendientes" -> pendingListState
        else -> sectionsListState
    }
    val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    val swipeControlsHeight = with(LocalDensity.current) { 145.dp.toPx() }
    val scope = rememberCoroutineScope()
    var showSyncReport by remember { mutableStateOf(false) }
    var homeSettings by remember { mutableStateOf(false) }
    var newsSettings by remember { mutableStateOf(false) }
    var organizeSections by remember { mutableStateOf(false) }
    var backupMenu by remember { mutableStateOf(false) }
    var duplicateDialog by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf("Título") }
    val backupExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }
    val readingBooks = remember(vm.books) { vm.readingBooks() }
    val visibleNews = remember(vm.launchNews, vm.hiddenNewsSources, vm.hiddenNewsUrls) {
        vm.launchNews.filter(vm::isNewsVisible)
    }
    val duplicateSnapshot = vm.books
    val duplicateGroups by produceState(initialValue = emptyList<List<Book>>(), duplicateSnapshot) {
        value = withContext(Dispatchers.Default) { findDuplicateGroups(duplicateSnapshot) }
    }
    val booksSnapshot = vm.books
    val querySnapshot = vm.query
    val statusSnapshot = vm.statusFilter
    val favoritesSnapshot = vm.onlyFavorites
    val sectionSnapshot = vm.selectedSection
    val qualitySnapshot = vm.qualityFilter
    val reviewSnapshot = vm.reviewPending
    val filteredBooks by produceState(initialValue = emptyList<Book>(), booksSnapshot,
        querySnapshot, statusSnapshot, favoritesSnapshot, sectionSnapshot,
        qualitySnapshot, reviewSnapshot) {
        value = withContext(Dispatchers.Default) {
            filterBooks(booksSnapshot, querySnapshot, statusSnapshot, favoritesSnapshot,
                sectionSnapshot, qualitySnapshot, reviewSnapshot)
        }
    }
    val visibleBooks = if (tab == "Inicio") emptyList() else filteredBooks
    val groupMode = if (tab == "Secciones") "Secciones" else if (tab == "Inicio" ||
        tab == "Pendientes") "Todos" else vm.groupMode
    val sectionNames = vm.sections
    val groupedBooks by produceState<Map<String, List<Book>>>(emptyMap(), visibleBooks,
        groupMode, sectionNames, tab, sortMode) {
        val booksToGroup = visibleBooks
        value = withContext(Dispatchers.Default) {
        val titles = booksToGroup.associate { it.uri to displayTitle(it) }
        val authors = booksToGroup.associate { it.uri to displayAuthor(it) }
        val sagaOrders = if (groupMode == "Sagas") booksToGroup.associate { it.uri to sagaNumber(it) }
            else emptyMap()
        val groups = when (groupMode) {
            "Autores" -> booksToGroup.groupBy { authors.getValue(it.uri) }
            "Sagas" -> booksToGroup.groupBy { it.saga.ifBlank { "Sin saga" } }
            "Secciones" -> booksToGroup.flatMap { book ->
                bookSections(book).ifEmpty { listOf("Sin sección") }.map { name -> name to book }
            }.groupBy({ it.first }, { it.second })
            else -> mapOf("" to booksToGroup)
        }
        val names = if (tab == "Secciones")
            sectionNames.filter(groups::containsKey) +
                groups.keys.filterNot { it in sectionNames }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        else groups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
        names.associateWith { name ->
            val group = groups[name].orEmpty()
            if (groupMode == "Sagas") group.sortedWith(
                compareBy<Book> { sagaOrders.getValue(it.uri) }.thenBy(String.CASE_INSENSITIVE_ORDER) { titles.getValue(it.uri) })
            else when (sortMode) {
                "Autor" -> group.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { authors.getValue(it.uri) })
                "Recientes" -> group.sortedByDescending { it.sourceModified }
                else -> group.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { titles.getValue(it.uri) })
            }
        }
        }
    }
    vm.infoCandidate?.let { (uri, found) ->
        var authorDraft by remember(uri, found) { mutableStateOf(found.author) }
        var plotDraft by remember(uri, found) { mutableStateOf(found.plot) }
        var bioDraft by remember(uri, found) { mutableStateOf(found.bio) }
        AlertDialog(onDismissRequest = vm::dismissCandidate,
            title = { Text("Revisar datos encontrados") },
            text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Comprueba que corresponden a este libro antes de guardarlos.")
                OutlinedTextField(authorDraft, { authorDraft = it }, label = { Text("Autor") })
                Text("Argumento · " + found.plotSource.ifBlank { "Sin fuente" }, fontSize = 12.sp)
                OutlinedTextField(plotDraft, { plotDraft = it }, minLines = 3,
                    label = { Text("Argumento") })
                Text("Biografía · " + found.bioSource.ifBlank { "Sin fuente" }, fontSize = 12.sp)
                OutlinedTextField(bioDraft, { bioDraft = it }, minLines = 3,
                    label = { Text("Sobre el autor") })
            } },
            confirmButton = { TextButton(onClick = {
                vm.acceptCandidate(uri, found.copy(author = authorDraft.trim(),
                    plot = plotDraft.trim(), bio = bioDraft.trim(),
                    plotSource = if (plotDraft.trim() == found.plot) found.plotSource else "Revisado por ti",
                    bioSource = if (bioDraft.trim() == found.bio) found.bioSource else "Revisado por ti"))
            }) { Text("Guardar datos") } },
            dismissButton = { TextButton(onClick = vm::dismissCandidate) { Text("Cancelar") } })
    }
    if (organizeSections) AlertDialog(onDismissRequest = { organizeSections = false },
        title = { Text("Organizar secciones") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            vm.sections.forEach { section ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(section, Modifier.weight(1f))
                    IconButton(onClick = { vm.moveSection(section, -1) }) { AppIcon("Subir", "Subir sección") }
                    IconButton(onClick = { vm.moveSection(section, 1) }) { AppIcon("Bajar", "Bajar sección") }
                    IconButton(onClick = { sectionToDelete = section }) { AppIcon("Borrar", "Eliminar sección") }
                }
            }
            if (vm.sections.isEmpty()) Text("Aún no hay secciones.")
        } },
        confirmButton = { TextButton(onClick = { organizeSections = false }) { Text("Cerrar") } })
    if (duplicateDialog) AlertDialog(onDismissRequest = { duplicateDialog = false },
        title = { Text("Posibles duplicados") },
        text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
            duplicateGroups.forEach { group ->
                Text(displayTitle(group.first()), fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, color = Mahogany)
                group.forEach { copy ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(displayAuthor(copy) + " · " + readableSize(copy.sourceSize),
                            modifier = Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { duplicateDialog = false; selected = copy }) {
                            Text("Revisar")
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        } },
        confirmButton = { TextButton(onClick = { duplicateDialog = false }) { Text("Cerrar") } })
    if (showSyncReport) AlertDialog(onDismissRequest = { showSyncReport = false },
        title = { Text("Sincronización de Drive") },
        text = { Column {
            Text(vm.syncReport)
            Spacer(Modifier.height(14.dp))
            Text("Fluidez de esta sesión", fontWeight = FontWeight.Bold)
            Text((context as? MainActivity)?.performanceReport().orEmpty(), fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { showSyncReport = false }) { Text("Cerrar") } })
    if (homeSettings) AlertDialog(onDismissRequest = { homeSettings = false },
        title = { Text("Organizar Inicio") },
        text = { Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar noticias", Modifier.weight(1f))
                Switch(vm.showHomeNews, vm::setHomeNews)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continuar leyendo primero", Modifier.weight(1f))
                Switch(vm.readingFirst, vm::updateReadingFirst)
            }
        } },
        confirmButton = { TextButton(onClick = { homeSettings = false }) { Text("Cerrar") } })
    if (newsSettings) AlertDialog(onDismissRequest = { newsSettings = false },
        title = { Text("Fuentes de noticias") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            vm.launchNews.map { it.source }.distinct().forEach { source ->
                Row(Modifier.fillMaxWidth().clickable { vm.toggleNewsSource(source) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(source !in vm.hiddenNewsSources,
                        onCheckedChange = { vm.toggleNewsSource(source) })
                    Text(source)
                }
            }
            TextButton(onClick = vm::showAllNews) { Text("Recuperar noticias ocultas") }
        } },
        confirmButton = { TextButton(onClick = { newsSettings = false }) { Text("Cerrar") } })
    val shown = current
    LaunchedEffect(tab, shown?.uri) {
        (context as? MainActivity)?.setMetricScreen(if (shown != null) "Ficha" else tab)
    }
    val readingBook = readingUri?.let { key -> vm.books.firstOrNull { it.uri.toString() == key } }
    if (readingBook != null) {
        ReaderScreen(readingBook, onBack = { readingUri = null },
            onProgress = { percent -> vm.updateReadingPercent(readingBook.uri, percent) },
            onHighlightsChanged = vm::readerAnnotationsChanged)
    } else if (shown != null) {
            BookDetail(shown, { selected = null }, { vm.toggleFavorite(shown.uri) },
                { vm.recordOpen(shown.uri) },
                { vm.setStatus(shown.uri, it) }, { readingUri = shown.uri.toString() },
                vm.message, duplicateGroups.firstOrNull { group -> group.any { it.uri == shown.uri } }
                    ?.filterNot { it.uri == shown.uri }.orEmpty(),
                { vm.deleteDuplicate(shown) }, { vm.completeMissing(shown.uri, force = true) },
                vm.autoInfoLoading.contains(shown.uri), vm.autoCoverLoading.contains(shown.uri),
                vm.sections, { vm.assignSection(shown.uri, it) },
                { vm.saveNotes(shown.uri, it) }, { plot, bio -> vm.saveManualInfo(shown.uri, plot, bio) },
                { vm.replaceCover(shown.uri, it) },
                { vm.setGoodreadsUrl(shown.uri, it) }, { vm.toggleWantToRead(shown.uri) },
                vm.detailMessage, shown.uri in vm.reviewPending,
                { vm.confirmBookDetails(shown.uri) },
                { title, author, saga, order -> vm.editIdentity(shown.uri, title, author, saga, order) })
        } else {
    Scaffold(
        containerColor = Parchment,
        bottomBar = {
            NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                tabs.forEach { name ->
                    NavigationBarItem(selected = tab == name, onClick = {
                        switchTab(name)
                    }, icon = { AppIcon(name, size = 24.dp) },
                        label = { Text(name) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Teal, selectedTextColor = Teal,
                            indicatorColor = Teal.copy(alpha = 0.10f),
                            unselectedIconColor = Mahogany, unselectedTextColor = Mahogany))
                }
            }
        },
        topBar = {
            Column(Modifier.fillMaxWidth().background(Mahogany).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(60.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Mi Biblioteca", color = Color.White, fontSize = 17.sp,
                        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_bookshelf_foreground),
                            "Estantería de libros", modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("By Diroka77", color = Color.White, fontSize = 17.sp,
                        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(vm.query, { vm.query = it; if (it.isNotBlank()) {
                    tab = "Biblioteca"; vm.qualityFilter = "Ninguno"
                } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true, placeholder = { Text("Buscar título, saga o autor") },
                    leadingIcon = { AppIcon("Buscar") },
                    trailingIcon = { if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { AppIcon("Cerrar", "Borrar búsqueda") } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Paper, unfocusedContainerColor = Paper,
                        focusedTextColor = Ink, unfocusedTextColor = Ink),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            }
        }
    ) { p ->
      Box(Modifier.padding(p).fillMaxSize().pointerInput(tab, swipeThreshold) {
          awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
              val start = down.position
              var end = start
              while (true) {
                  val change = awaitPointerEvent(PointerEventPass.Initial).changes
                      .firstOrNull { it.id == down.id } ?: break
                  end = change.position
                  if (!change.pressed) break
              }
              val horizontal = end.x - start.x
              val vertical = end.y - start.y
              if (start.y > swipeControlsHeight && kotlin.math.abs(horizontal) > swipeThreshold &&
                  kotlin.math.abs(horizontal) > kotlin.math.abs(vertical) * 1.25f) {
                  val index = tabs.indexOf(tab)
                  val next = index + if (horizontal < 0) 1 else -1
                  if (next in tabs.indices) switchTab(tabs[next])
              }
          }
      }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tab == "Inicio" || tab == "Biblioteca") item(key = "quick-access") {
                QuickAccess(
                    onLibrary = { switchTab("Biblioteca") },
                    onFolder = { folderPicker.launch(null) },
                    onFavorites = { switchTab("Biblioteca"); vm.onlyFavorites = true },
                    onGoodreads = { openGoodreads(context) },
                    onGoogle = { openGoogleAi(context) },
                    onCasa = { openCasaDelLibro(context) })
            }
            if (tab == "Inicio" && vm.readingFirst && readingBooks.isNotEmpty())
                item(key = "reading-shelf") { ReadingShelf(readingBooks, vm::readingPercent) { book ->
                    readingUri = book.uri.toString()
                    vm.recordOpen(book.uri)
                } }
            item(key = "controls") {
                Column {
                    if (tab == "Inicio") {
                    Spacer(Modifier.height(12.dp))
                    Text("✦  ENTRE ESTANTERÍAS  ✦", color = Brass, fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Historias por descubrir", fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.headlineSmall, color = Ink,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { homeSettings = true },
                            modifier = Modifier.semantics { contentDescription = "Organizar Inicio" }) {
                            AppIcon("Ajustes", "Ajustes de inicio")
                        }
                        IconButton(onClick = { showSyncReport = true },
                            modifier = Modifier.semantics { contentDescription = "Ver estado de sincronización" }) { AppIcon("Estado", "Estado de sincronización") }
                        IconButton(onClick = { vm.sync() }, enabled = !vm.syncing,
                            modifier = Modifier.semantics { contentDescription = "Sincronizar biblioteca" }) {
                            AppIcon("Sincronizar", "Actualizar biblioteca")
                        }
                    }
                    HorizontalDivider(color = Brass.copy(alpha = 0.4f), thickness = 1.dp)
                    }
                    if (tab == "Biblioteca") Text("Todos los libros",
                        fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Mahogany)
                    if (tab == "Pendientes") {
                        Text("Fichas por completar", fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.headlineSmall, color = Mahogany)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Todos", "Revisar", "Portada", "Argumento", "Biografía", "Autor").forEach { kind ->
                                CatalogChip(kind, vm.qualityFilter == kind, Teal) { vm.qualityFilter = kind }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${filteredBooks.size} pendientes", Modifier.weight(1f),
                                fontSize = 12.sp, color = Mahogany)
                            TextButton(onClick = { if (vm.bulkInfoLoading) vm.cancelIncomplete()
                                else vm.refreshIncomplete() }) {
                                Text(if (vm.bulkInfoLoading) "Detener · ${vm.bulkProgress}" else "Buscar datos y portadas")
                            }
                        }
                    }
                    if (tab == "Secciones") Text("Tus secciones",
                        fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall, color = Mahogany)
                    if (vm.syncing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brass)
                        Text("Revisando ${vm.syncCount} libros…", modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (vm.coverUpdateRunning && (tab == "Inicio" || tab == "Biblioteca")) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
                        Text("Buscando portadas ${vm.coverUpdateDone}/${vm.coverUpdateTotal}…", fontSize = 12.sp, color = Mahogany)
                    }
                    if (tab == "Inicio" && vm.syncSummary.isNotBlank())
                        Text(vm.syncSummary, color = Mahogany, fontSize = 12.sp)
                    if (tab == "Biblioteca") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${vm.books.size} libros en tu biblioteca",
                                modifier = Modifier.weight(1f), fontSize = 12.sp, color = Mahogany)
                            OutlinedButton(onClick = {
                                if (vm.bulkInfoLoading) vm.cancelIncomplete()
                                else vm.refreshIncomplete()
                            }) {
                                ActionLabel(if (vm.bulkInfoLoading) "Detener · ${vm.bulkProgress}"
                                    else "Datos y portadas", "Datos", 12.sp)
                            }
                        }
                        if (vm.bulkInfoLoading) {
                            Text("Buscando fichas ${vm.bulkProgress}",
                                fontSize = 12.sp, color = Mahogany)
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brass)
                        }
                        vm.message?.let { feedback ->
                            Text(feedback, color = Mahogany, fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CatalogChip("Todos", vm.statusFilter == null, Teal) { vm.statusFilter = null }
                            CatalogChip("★ Favoritos", vm.onlyFavorites, Color(0xFFAA6200)) { vm.onlyFavorites = !vm.onlyFavorites }
                            CatalogChip("Leyendo", vm.statusFilter == ReadingStatus.READING, Teal) {
                                vm.statusFilter = if (vm.statusFilter == ReadingStatus.READING) null else ReadingStatus.READING
                            }
                            CatalogChip("Leídos", vm.statusFilter == ReadingStatus.READ, Color(0xFF4564A4)) {
                                vm.statusFilter = if (vm.statusFilter == ReadingStatus.READ) null else ReadingStatus.READ
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Todos", "Autores", "Sagas", "Secciones").forEach { mode ->
                                CatalogChip(mode, vm.groupMode == mode, when (mode) {
                                    "Autores" -> Color(0xFF7852A0)
                                    "Sagas" -> Color(0xFFB45D39)
                                    "Secciones" -> Color(0xFF397E56)
                                    else -> Teal
                                }) { vm.groupMode = mode }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${filteredBooks.size} libros", modifier = Modifier.weight(1f),
                                fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium)
                            if (vm.statusFilter != null || vm.onlyFavorites || vm.selectedSection != null) {
                                TextButton(onClick = {
                                    vm.statusFilter = null; vm.onlyFavorites = false; vm.selectedSection = null
                                }) { Text("Limpiar filtros") }
                            }
                            Box {
                                TextButton(onClick = { sortMenu = true }) {
                                    ActionLabel("Ordenar: $sortMode", "Sagas", 12.sp)
                                }
                                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    listOf("Título", "Autor", "Recientes").forEach { option ->
                                        DropdownMenuItem(text = { Text(option) }, onClick = {
                                            sortMode = option; sortMenu = false
                                        })
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CatalogChip("Quiero leer (${vm.wishList.size})", showWishList, Color(0xFF7852A0)) {
                                showWishList = !showWishList
                            }
                            if (showWishList) TextButton(onClick = { addWishDialog = true }) { Text("+ Goodreads") }
                        }
                    }
                    if (tab == "Secciones") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CatalogChip("Todas", vm.selectedSection == null, Color(0xFF397E56)) { vm.selectedSection = null }
                            vm.sections.forEach { name ->
                                CatalogChip(name, vm.selectedSection == name, Color(0xFF397E56)) { vm.selectedSection = name }
                            }
                        }
                        Row {
                            OutlinedButton(onClick = { addingSection = true }) { ActionLabel("Nueva sección", "Añadir") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { organizeSections = true }) { ActionLabel("Ordenar secciones", "Secciones") }
                        }
                    }
                    if (tab != "Inicio" && !showWishList) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Lista", "Galería", "Compacta").forEach { mode ->
                                CatalogChip(when (mode) {
                                    "Galería" -> "Galería"
                                    "Compacta" -> "Compacta"
                                    else -> "Lista"
                                }, vm.viewModeFor(vm.selectedSection) == mode, Teal) {
                                    vm.chooseViewMode(mode)
                                }
                            }
                        }
                    }
                    if (tab == "Biblioteca") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                TextButton(onClick = { backupMenu = true },
                                    modifier = Modifier.semantics { contentDescription = "Opciones de biblioteca" }) {
                                    ActionLabel("Más opciones", "Opciones")
                                }
                                DropdownMenu(expanded = backupMenu, onDismissRequest = { backupMenu = false }) {
                                    DropdownMenuItem(text = { Text("Exportar mis datos") }, onClick = {
                                        backupMenu = false; backupExport.launch("MiBiblioteca-respaldo.json")
                                    })
                                    DropdownMenuItem(text = { Text("Restaurar respaldo") }, onClick = {
                                        backupMenu = false; backupImport.launch(arrayOf("application/json"))
                                    })
                                    if (duplicateGroups.isNotEmpty()) DropdownMenuItem(
                                        text = { Text("Duplicados (${duplicateGroups.size})") },
                                        onClick = { backupMenu = false; duplicateDialog = true })
                                }
                            }
                        }
                    }
                }
            }
            if (tab == "Inicio") {
                if (vm.showHomeNews) {
                item(key = "launch-news") {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Actualidad literaria en España", fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Mahogany,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = { newsSettings = true },
                                modifier = Modifier.semantics { contentDescription = "Elegir fuentes de noticias" }) {
                                AppIcon("Ajustes", "Fuentes de noticias")
                            }
                            IconButton(onClick = { vm.refreshLaunchNews(true) }, enabled = !vm.newsRefreshing,
                                modifier = Modifier.semantics { contentDescription = "Actualizar noticias" }) {
                                if (vm.newsRefreshing) CircularProgressIndicator(Modifier.size(18.dp),
                                    strokeWidth = 2.dp, color = Brass)
                                else AppIcon("Noticias", "Actualizar noticias")
                            }
                        }
                        if (vm.newsRefreshing && vm.launchNews.isEmpty())
                            Text("Buscando noticias literarias…", color = Mahogany, fontSize = 12.sp)
                        if (vm.newsLastChecked > 0L) Text("Actualizado " +
                            java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("es", "ES"))
                                .format(java.util.Date(vm.newsLastChecked)), fontSize = 11.sp,
                            color = Mahogany)
                    }
                }
                items(visibleNews, key = { "news:" + it.url }, contentType = { "news" }) { news ->
                    val picture by rememberNewsImage(news.imageUrl)
                    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(news.url))) }
                        catch (_: Exception) {
                            android.widget.Toast.makeText(context, "No se pudo abrir la noticia",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Paper),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp,
                                end = 12.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(news.title, fontFamily = FontFamily.Serif,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mahogany,
                                    lineHeight = 23.sp, maxLines = 4, modifier = Modifier.weight(1f))
                                if (picture != null) Image(picture!!.asImageBitmap(),
                                    contentDescription = "Imagen de la noticia: ${news.title}",
                                    modifier = Modifier.size(72.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop)
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(22.dp).background(Brass.copy(alpha = 0.16f),
                                    androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    Text(news.source.take(1).uppercase(), color = Mahogany,
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(7.dp))
                                Text(news.source, color = Mahogany, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    modifier = Modifier.weight(1f))
                                Text(if (news.source == "Casa del Libro") "Próximamente" else
                                    try {
                                        java.time.LocalDate.parse(news.releaseDate).format(
                                            java.time.format.DateTimeFormatter.ofPattern("d MMM",
                                                java.util.Locale("es", "ES")))
                                    } catch (_: Exception) { news.releaseDate },
                                    color = Mahogany.copy(alpha = 0.7f), fontSize = 11.sp)
                                IconButton(onClick = { vm.hideNews(news.url) },
                                    modifier = Modifier.semantics { contentDescription = "Ocultar noticia" }) {
                                    AppIcon("Cerrar", "Ocultar noticia", tint = Mahogany)
                                }
                            }
                            if (picture != null) {
                                HorizontalDivider(color = Brass.copy(alpha = 0.22f))
                                Image(picture!!.asImageBitmap(), contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(202.dp),
                                    contentScale = if (news.source == "Casa del Libro")
                                        ContentScale.Fit else ContentScale.Crop)
                            }
                        }
                    }
                }
                }
                if (!vm.readingFirst && readingBooks.isNotEmpty())
                    item(key = "reading-shelf") { ReadingShelf(readingBooks, vm::readingPercent) { book ->
                        readingUri = book.uri.toString()
                        vm.recordOpen(book.uri)
                    } }
                if (vm.books.isEmpty() && !vm.syncing) item(key = "empty-home") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = { folderPicker.launch(null) }) { Text("Elegir carpeta de libros") }
                    }
                }
            } else if (showWishList && tab == "Biblioteca") {
                items(vm.wishList, key = { "wish:" + it.second }) { (title, url) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { openGoodreadsUrl(context, url) },
                            modifier = Modifier.weight(1f)) { Text(title.ifBlank { "Libro de Goodreads" }) }
                        IconButton(onClick = { vm.removeGoodreadsWish(url) }) { AppIcon("Cerrar", "Quitar de pendientes") }
                    }
                }
            } else if (vm.books.isEmpty() && !vm.syncing) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon("Libros", size = 48.dp)
                            Text("Elige tu carpeta de libros en Drive", fontFamily = FontFamily.Serif)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { folderPicker.launch(null) }) { Text("Elegir carpeta") }
                        }
                    }
                }
            } else if (visibleBooks.isEmpty()) {
                item(key = "no-results") {
                    Text(if (tab == "Pendientes") "No hay fichas pendientes en este filtro."
                        else "No hay libros con estos filtros.", modifier = Modifier.padding(20.dp))
                }
            } else {
                groupedBooks.forEach { (name, group) ->
                    if (name.isNotBlank()) item(key = "group:$name") {
                        Text(name, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = Mahogany, modifier = Modifier.padding(top = 10.dp))
                        if (groupMode == "Sagas" && name != "Sin saga") {
                            val numbers = group.map { sagaNumber(it) }
                                .filter { it.isFinite() && it >= 1 && it <= 100 && it % 1.0 == 0.0 }
                                .map { it.toInt() }.toSet()
                            val missing = if (numbers.size >= 2) (numbers.min()..numbers.max())
                                .filterNot(numbers::contains) else emptyList()
                            if (missing.isNotEmpty()) Text(
                                "Sin EPUB en la biblioteca: " + missing.joinToString(", "),
                                fontSize = 11.sp, color = Mahogany)
                        }
                    }
                    val ordered = group
                    when (vm.viewModeFor(vm.selectedSection)) {
                        "Galería" -> items(ordered.chunked(2), key = { "grid:" + name + ":" + it.first().uri }, contentType = { "gallery" }) { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { book ->
                                    BookGalleryCard(book, Modifier.weight(1f),
                                        vm.readingPercent(book.uri)) { selected = book }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        else -> items(ordered, key = { name + ":" + it.uri },
                            contentType = { vm.viewModeFor(vm.selectedSection) }) { book ->
                            if (vm.viewModeFor(vm.selectedSection) == "Compacta") BookCompactCard(book) { selected = book }
                            else BookCard(book) { selected = book }
                        }
                    }
                }
            }
        }
        if (tab == "Biblioteca" && !showWishList) {
            LibraryScrollHandle(listState, Modifier.align(Alignment.CenterEnd))
        }
      }
    }
    }
}

@Composable private fun LibraryScrollHandle(
    listState: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val canShow by remember(listState) { derivedStateOf {
        listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size
    } }
    if (!canShow) return
    val maxIndex by remember(listState) { derivedStateOf {
        (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(1)
    } }
    val progress by remember(listState) { derivedStateOf {
        if (!listState.canScrollForward) 1f else
            (listState.firstVisibleItemIndex.toFloat() /
                (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    } }
    BoxWithConstraints(modifier.fillMaxHeight().width(24.dp)
        .semantics { contentDescription = "Desplazamiento rápido de la biblioteca" }
        .pointerInput(listState, maxIndex) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var seekJob: Job? = null
                fun seek(y: Float) {
                    val fraction = (y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    seekJob?.cancel()
                    seekJob = scope.launch { listState.scrollToItem((fraction * maxIndex).toInt()) }
                }
                seek(down.position.y)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    seek(change.position.y)
                    change.consume()
                }
            }
        }) {
        Box(Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight()
            .background(Brass.copy(alpha = 0.15f)))
        val trackHeight = with(LocalDensity.current) { (maxHeight - 48.dp).toPx() }
        Box(Modifier.align(Alignment.TopEnd)
            .offset { androidx.compose.ui.unit.IntOffset(0, (trackHeight * progress).toInt()) }
            .width(5.dp).height(48.dp).background(Brass.copy(alpha = 0.55f),
                androidx.compose.foundation.shape.RoundedCornerShape(3.dp)))
    }
}

@Composable private fun QuickAccess(
    onLibrary: () -> Unit, onFolder: () -> Unit, onFavorites: () -> Unit,
    onGoodreads: () -> Unit, onGoogle: () -> Unit, onCasa: () -> Unit
) {
    val shortcuts = listOf(
        Triple("Libros", "Libros", Color(0xFF673D62)) to onLibrary,
        Triple("Carpeta", "Carpeta", Color(0xFF4689C2)) to onFolder,
        Triple("Favoritos", "Favoritos", Color(0xFFC58D45)) to onFavorites,
        Triple("Goodreads", "Goodreads", Color(0xFF186D66)) to onGoodreads,
        Triple("Google IA", "Google IA", Color(0xFFB96056)) to onGoogle,
        Triple("Casa del Libro", "Casa del Libro", Color(0xFF435C82)) to onCasa
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        .padding(top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        shortcuts.forEach { (item, action) ->
            Column(Modifier.width(70.dp).clickable(onClick = action),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(66.dp).background(item.third,
                    androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center) {
                    AppIcon(item.second, tint = Color.White, size = 28.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(item.first, fontSize = 10.sp, maxLines = 2,
                    color = Mahogany, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun ReadingShelf(books: List<Book>, progress: (Uri) -> Int, onOpen: (Book) -> Unit) {
    Column {
        Text("Continuar leyendo", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 21.sp, color = Mahogany, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 8.dp)) {
            items(books, key = { "reading:" + it.uri }) { book ->
                Column(Modifier.width(144.dp).clickable { onOpen(book) }) {
                    Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)) {
                        Cover(book, 144.dp, 202.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(displayTitle(book), fontSize = 13.sp, lineHeight = 17.sp,
                        maxLines = 2, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        color = Ink)
                    Text(displayAuthor(book), fontSize = 11.sp, maxLines = 1,
                        color = Mahogany.copy(alpha = 0.75f))
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress(book.uri).coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(), color = Teal,
                        trackColor = Brass.copy(alpha = 0.15f))
                    Text("${progress(book.uri)} % leído", fontSize = 10.sp, color = Teal)
                }
            }
        }
    }
}

@Composable private fun BookCard(book:Book,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Paper), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Cover(book,92.dp,132.dp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(displayTitle(book),fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,fontSize=14.sp,lineHeight=18.sp,modifier=Modifier.weight(1f));if(book.favorite)AppIcon("Favorito", tint = Brass, size = 18.dp)};Text(displayAuthor(book),fontSize=12.sp)}}}}

@Composable private fun BookCompactCard(book: Book, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Paper)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book, 50.dp, 72.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(displayTitle(book), fontSize = 13.sp, lineHeight = 17.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text(displayAuthor(book), fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun BookGalleryCard(book: Book, modifier: Modifier = Modifier,
    progress: Int, onClick: () -> Unit) {
    BoxWithConstraints(modifier.clickable(onClick = onClick).padding(bottom = 8.dp)) {
        val coverWidth = maxWidth
        Column(Modifier.fillMaxWidth()) {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)) {
                Cover(book, coverWidth, coverWidth * 1.42f)
            }
            Spacer(Modifier.height(7.dp))
            Text(displayTitle(book), fontSize = 13.sp, lineHeight = 17.sp,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                maxLines = 2, color = Ink)
            Text(displayAuthor(book), fontSize = 11.sp, maxLines = 1,
                color = Mahogany.copy(alpha = 0.75f))
            if (book.status == ReadingStatus.READING) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(), color = Teal,
                    trackColor = Brass.copy(alpha = 0.15f))
                Text("$progress %", fontSize = 10.sp, color = Teal)
            }
        }
    }
}

private val coverBitmapCache = object : LruCache<String, Bitmap>(20 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}

private fun coverKey(book: Book): String =
    book.uri.toString() + ":" + System.identityHashCode(book.cover)

private fun decodeCover(book: Book): Bitmap? {
    val bytes = book.cover ?: return null
    val key = coverKey(book)
    synchronized(coverBitmapCache) { coverBitmapCache.get(key) }?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 900) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    synchronized(coverBitmapCache) { coverBitmapCache.put(key, bitmap) }
    return bitmap
}

@Composable
private fun Cover(book: Book, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val cached = remember(book.cover, book.uri) {
        synchronized(coverBitmapCache) { coverBitmapCache.get(coverKey(book)) }
    }
    val bmp by produceState<Bitmap?>(cached, book.uri, book.cover) {
        value = cached
        if (cached == null && book.cover != null)
            value = withContext(Dispatchers.Default) { decodeCover(book) }
    }
    Surface(Modifier.width(w).height(h), shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF1EDE6), tonalElevation = 0.dp) {
        if (bmp != null) Image(bmp!!.asImageBitmap(), book.title, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit)
        else Box(Modifier.fillMaxSize().padding(7.dp)
            .background(Mahogany, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))) {
            Box(Modifier.align(Alignment.CenterStart).width(5.dp).fillMaxHeight().background(Brass))
            Column(Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✦", color = Brass, fontSize = 15.sp)
                Text(displayTitle(book), color = Paper, fontFamily = FontFamily.Serif,
                    fontSize = 10.sp, lineHeight = 12.sp, maxLines = 3,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BookDetail(
    book: Book, back: () -> Unit, toggleFavorite: () -> Unit,
    onOpened: () -> Unit, setStatus: (ReadingStatus) -> Unit, openBook: () -> Unit, message: String?,
    possibleDuplicates: List<Book>, deleteBook: () -> Unit, enrich: () -> Unit,
    infoLoading: Boolean, coverSearching: Boolean,
    sections: List<String>, assignSection: (String) -> Unit,
    saveNotes: (String) -> Unit, saveInfo: (String, String) -> Unit,
    replaceCover: (Uri) -> Unit,
    saveGoodreadsUrl: (String) -> Unit, toggleWant: () -> Unit, detailMessage: String?,
    needsReview: Boolean, confirmDetails: () -> Unit,
    editIdentity: (String, String, String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
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
    var exportingEpub by remember(book.uri) { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")) { destination ->
        if (destination != null && !exportingEpub) scope.launch {
            exportingEpub = true
            try {
                withContext(Dispatchers.IO) {
                    val source = localReaderFile(context, book)
                    context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                        source.inputStream().use { it.copyTo(output, 64 * 1024) }
                    } ?: throw IllegalStateException("No se pudo escribir el archivo")
                }
                android.widget.Toast.makeText(context, "Archivo guardado", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "No se pudo guardar: " + e.localizedMessage,
                    android.widget.Toast.LENGTH_LONG).show()
            } finally { exportingEpub = false }
        }
    }
    val duplicateNames by produceState<Map<Uri, String>>(emptyMap(), confirmDelete, book.uri, possibleDuplicates) {
        if (confirmDelete) value = withContext(Dispatchers.IO) {
            (possibleDuplicates + book).associate { other ->
                other.uri to (try { DocumentFile.fromSingleUri(context, other.uri)?.name }
                    catch (_: Exception) { null } ?: other.uri.lastPathSegment.orEmpty())
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Borrar archivo") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text("Se borrará de Drive únicamente esta copia:")
            Text(displayTitle(book) + " · " + displayAuthor(book), fontWeight = FontWeight.Bold)
            Text("Tamaño: " + readableSize(book.sourceSize))
            if (book.isbn.isNotBlank()) Text("ISBN: " + book.isbn, fontSize = 12.sp)
            Text("Archivo: " + (duplicateNames[book.uri] ?: "Consultando nombre…"),
                fontSize = 12.sp)
            if (possibleDuplicates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Otras copias que se conservarán:", fontWeight = FontWeight.Bold)
                possibleDuplicates.forEach { other ->
                    Text("• " + (duplicateNames[other.uri] ?: displayTitle(other)) +
                        " · " + readableSize(other.sourceSize) +
                        other.isbn.takeIf(String::isNotBlank)?.let { " · ISBN $it" }.orEmpty(), fontSize = 12.sp)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("No se detectaron otras copias con el mismo título y autor.", fontSize = 12.sp)
            }
        } },
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
    val plot = book.spanishPlot.ifBlank {
        book.description.takeIf { book.language.lowercase().startsWith("es") ||
            book.language.lowercase().startsWith("spa") }.orEmpty()
    }
    Scaffold(containerColor = Parchment, topBar = {
        TopAppBar(
            title = { Text("Mi Biblioteca", fontFamily = FontFamily.Serif, color = Paper) },
            navigationIcon = { IconButton(onClick = back) { AppIcon("Volver", "Volver", tint = Paper) } },
            actions = { IconButton(onClick = toggleFavorite) { AppIcon(if (book.favorite) "Favorito" else "Favoritos", "Favorito", tint = Paper) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Mahogany)
        )
    }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (detailMessage ?: message)?.let { notice -> item { Text(notice, color = Mahogany, fontSize = 12.sp) } }
            if (needsReview) item(key = "review-details") {
                Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Datos encontrados: revisa que correspondan a tu libro.",
                            modifier = Modifier.weight(1f), color = Mahogany, fontSize = 12.sp)
                        TextButton(onClick = confirmDetails) { Text("Revisado") }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(book, 165.dp, 240.dp)
                    if (book.cover == null && coverSearching) Text("Buscando portada…",
                        fontSize = 11.sp, color = Mahogany)
                    if (book.cover != null) Text("Portada: " + book.coverSource.ifBlank {
                        if (book.hadEmbeddedCover) "EPUB" else "No registrada"
                    }, fontSize = 11.sp, color = Mahogany)
                    Spacer(Modifier.height(12.dp))
                    Text(displayTitle(book), fontSize = 16.sp, lineHeight = 20.sp,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(displayAuthor(book), fontSize = 13.sp, color = Mahogany)
                    if (book.saga.isNotBlank()) Text("Saga: ${book.saga}" +
                        book.sagaOrder.takeIf { it.isNotBlank() }?.let { " · nº $it" }.orEmpty(), fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    onOpened(); setStatus(ReadingStatus.READING); openBook()
                }, modifier = Modifier.fillMaxWidth()) {
                    ActionLabel("Leer este libro", "Leer")
                }
                OutlinedButton(onClick = {
                    val safeName = displayTitle(book).replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(90)
                    val extension = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, book.uri)
                        ?.name?.substringAfterLast('.', "epub")?.lowercase().orEmpty()
                    exportPicker.launch((safeName.ifBlank { "Libro" }) + "." +
                        extension.takeIf { it in setOf("epub", "pdf", "mobi", "azw", "azw3", "txt", "html", "htm", "rtf", "docx", "md") }.orEmpty().ifBlank { "epub" })
                }, modifier = Modifier.fillMaxWidth(), enabled = !exportingEpub) {
                    ActionLabel(if (exportingEpub) "Guardando archivo…" else "Descargar archivo de Drive", "Descargar")
                }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = {
                        titleDraft = displayTitle(book); authorDraft = displayAuthor(book)
                        sagaDraft = book.saga; orderDraft = book.sagaOrder
                        editIdentityDialog = true
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        ActionLabel("Editar título y autor", "Editar")
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedButton(onClick = { searchCoverImages(context, book) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            ActionLabel("Buscar portada", "Buscar", 12.sp)
                        }
                        OutlinedButton(onClick = { coverPicker.launch("image/*") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            ActionLabel("Cambiar portada", "Portada", 12.sp)
                        }
                    }
                }
            }
            item {
                Box {
                    OutlinedButton(onClick = { sectionMenu = true }) {
                        Text("Secciones: ${bookSections(book).joinToString().ifBlank { "Sin sección" }}  ▾")
                    }
                    DropdownMenu(expanded = sectionMenu, onDismissRequest = { sectionMenu = false }) {
                        (listOf("") + sections).forEach { name ->
                            DropdownMenuItem(text = { Text(if (name.isBlank()) "Quitar todas" else
                                (if (name in bookSections(book)) "✓ " else "+ ") + name) },
                                onClick = { assignSection(name); if (name.isBlank()) sectionMenu = false })
                        }
                    }
                }
                OutlinedButton(onClick = toggleWant) {
                    Text(if (book.wantToRead) "✓ Quiero leer" else "+ Quiero leer más adelante")
                }
            }
            item {
                Text("Estado", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Mahogany)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadingStatus.entries.forEach { status ->
                        CatalogChip(status.label, book.status == status) { setStatus(status) }
                    }
                }
            }
            item {
                Text("Datos del libro", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    color = Mahogany, modifier = Modifier.padding(top = 8.dp))
            }
            item {
                BookPanel("Argumento", book.uri.toString(), initiallyExpanded = true) {
                    OutlinedTextField(value = plot, onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth(), minLines = 3,
                        placeholder = { Text(if (infoLoading) "Buscando argumento en castellano…" else
                            "Argumento en castellano pendiente. Puedes escribirlo.") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 19.sp))
                    if (plot.isNotBlank()) Text("Fuente: " + book.plotSource.ifBlank { "No registrada" },
                        fontSize = 11.sp, color = Mahogany)
                }
            }
            item {
                BookPanel("Sobre el autor", book.uri.toString()) {
                    OutlinedTextField(value = book.authorBio, onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth(), minLines = 3,
                        placeholder = { Text(if (infoLoading) "Buscando biografía en castellano…" else
                            "Biografía en castellano pendiente. Puedes escribirla.") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 17.sp))
                    if (book.authorBio.isNotBlank()) Text("Fuente: " + book.bioSource.ifBlank { "No registrada" },
                        fontSize = 11.sp, color = Mahogany)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = enrich, enabled = !infoLoading,
                        modifier = Modifier.weight(1f)) {
                        ActionLabel(if (infoLoading) "Buscando…" else "Reintentar búsqueda", "Buscar", 12.sp)
                    }
                    OutlinedButton(onClick = {
                        plotDraft = book.spanishPlot.ifBlank { plot }
                        bioDraft = book.authorBio
                        editInfo = true
                    }, modifier = Modifier.weight(1f)) { ActionLabel("Editar texto", "Editar") }
                }
            }
            item {
                BookPanel("Mis observaciones", book.uri.toString()) {
                    OutlinedTextField(notesDraft, { notesDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Escribe tus notas sobre este libro") }, minLines = 3)
                    OutlinedButton(onClick = { saveNotes(notesDraft) }) {
                        ActionLabel("Guardar observaciones", "Guardar")
                    }
                }
            }
            item {
                OutlinedButton(onClick = { openGoodreads(context, book) },
                    modifier = Modifier.fillMaxWidth()) { ActionLabel("Abrir este libro en Goodreads", "Goodreads") }
                OutlinedButton(onClick = { searchInGoogleApp(context, displayTitle(book) + " " + displayAuthor(book)) },
                    modifier = Modifier.fillMaxWidth()) { ActionLabel("Consultar en Google", "Google") }
                OutlinedButton(onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()) { ActionLabel("Borrar este archivo de Drive", "Borrar") }
            }
        }
    }
}

@Composable
private fun BookPanel(title: String, bookKey: String, initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(bookKey, title) { mutableStateOf(initiallyExpanded) }
    Card(colors = CardDefaults.cardColors(containerColor = Paper),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = Mahogany)
                Text(if (expanded) "⌃" else "⌄", color = Mahogany, fontSize = 20.sp)
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable private fun Info(label:String,value:String){if(value.isNotBlank()){Text(label,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(value,fontSize=12.sp,lineHeight=17.sp);Spacer(Modifier.height(4.dp))}}
