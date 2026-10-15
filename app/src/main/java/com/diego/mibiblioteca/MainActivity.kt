Warning: truncated output (original token count: 94041)
Total output lines: 6605

package com.diego.mibiblioteca

import android.app.Application
import android.app.Activity
import android.app.SearchManager
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.metrics.performance.JankStats
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import android.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
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
    val coverSource: String = "",
    val rating: Int = 0,
    val startedDate: String = "",
    val finishedDate: String = "",
    val quotes: String = "",
    val tags: List<String> = emptyList(),
    val loanTo: String = "",
    val loanDate: String = "",
    val rereadCount: Int = 0
)

private data class BackupRestoreResult(val matched: Int, val pending: String?)

private fun safeSavedArray(raw: String?): org.json.JSONArray = try {
    org.json.JSONArray(raw ?: "[]")
} catch (_: Exception) { org.json.JSONArray() }

private fun bookSections(book: Book): List<String> =
    (book.sections + book.section).map(String::trim).filter(String::isNotBlank).distinct()

data class InfoCandidate(
    val author: String, val plot: String, val bio: String,
    val plotSource: String, val bioSource: String
)

data class SagaVolume(val order: String, val title: String)

private fun normalizeSagaName(value: String): String = java.text.Normalizer
    .normalize(cleanCatalogText(value).lowercase(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("(?i)\\b(saga|serie|coleccion|collection)\\b"), " ")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private fun inferredSaga(book: Book): Pair<String, String> {
    if (book.saga.isNotBlank()) return book.saga.trim() to book.sagaOrder.trim()
    val source = listOf(displayTitle(book), Uri.decode(book.uri.lastPathSegment.orEmpty()))
        .joinToString(" ")
    val patterns = listOf(
        Regex("(?i)\\[([^]]{2,80}?)\\s*(?:#|n[ºo.]?|vol(?:umen)?[.]?|tomo|libro)\\s*(\\d+(?:[.,]\\d+)?)\\]"),
        Regex("(?i)([^\\[\\]()]{2,80}?)\\s+(?:#|n[ºo.]?|vol(?:umen)?[.]?|tomo|libro)\\s*(\\d+(?:[.,]\\d+)?)\\s*[-–—:]"),
        Regex("(?i)\\(([^()]{2,80}?)\\s+(\\d+(?:[.,]\\d+)?)\\)")
    )
    patterns.forEach { pattern -> pattern.find(source)?.let { match ->
        return match.groupValues[1].trim(' ', '-', '–', '—', ':') to
            match.groupValues[2].replace(',', '.')
    } }
    return "" to book.sagaOrder.trim()
}

private fun sagaGroupKey(book: Book): String = normalizeSagaName(inferredSaga(book).first)

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

@Composable private fun rememberNewsImage(news: LaunchNews): State<Bitmap?> {
    val url = news.imageUrl
    val imageUrl = when {
        url.startsWith("https://www.bing.com/th?") && !url.contains("&w=") -> "$url&w=800&h=450"
        url.contains("casadellibro.com/a/l/s5/") -> url.replace("/s5/", "/s7/")
        else -> url
    }
    return produceState<Bitmap?>(initialValue = newsImageCache.get(imageUrl), news.url, imageUrl) {
        if (value == null) value = withContext(Dispatchers.IO) {
            fun load(candidate: String): Bitmap? {
                return try {
                if (!candidate.startsWith("https://")) return null
                val conn = URL(candidate).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MiBiblioteca)")
                conn.setRequestProperty("Referer", news.url)
                conn.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                try {
                    if (conn.contentLengthLong > 4_000_000) return null
                    val bytes = conn.inputStream.use { it.readNBytes(4_000_001) }
                    if (bytes.size > 4_000_000) return null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth < 100 || bounds.outHeight < 100) return null
                    var sample = 1
                    while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })?.also {
                        newsImageCache.put(imageUrl, it)
                    }
                } finally { conn.disconnect() }
                } catch (_: Exception) { null }
            }
            load(imageUrl) ?: if (news.source in featuredNewsSources)
                articleImageUrl(news.url)?.let(::load) else null
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
    var wallpaperMode by mutableStateOf("Automático"); private set
    var animationMode by mutableStateOf("Equilibrada"); private set
    var lastOpenedUri by mutableStateOf<Uri?>(null); private set
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
    var genreClassifying by mutableStateOf(false); private set
    var genreProgress by mutableIntStateOf(0); private set
    var genreTotal by mutableIntStateOf(0); private set
    var sagaSearches by mutableStateOf<Set<Uri>>(emptySet()); private set
    var sagaCatalogs by mutableStateOf<Map<String, List<SagaVolume>>>(emptyMap()); private set
    var sagaCatalogSearches by mutableStateOf<Set<String>>(emptySet()); private set
    var sagaOrganizing by mutableStateOf(false); private set
    var sagaOrganized by mutableIntStateOf(0); private set
    var sagaOrganizeTotal by mutableIntStateOf(0); private set
    private var bulkJob: Job? = null
    private var coverRefreshJob: Job? = null
    var coverUpdateRunning by mutableStateOf(false); private set
    var coverUpdateDone by mutableIntStateOf(0); private set
    var coverUpdateTotal by mutableIntStateOf(0); private set
    var reviewPending by mutableStateOf<Set<Uri>>(emptySet()); private set
    var books by mutableStateOf<List<Book>>(emptyList()); private set
    var importedBookUri by mutableStateOf<Uri?>(null); private set
    fun clearImportedBook() { importedBookUri = null }
    private val readerPrefs = app.getSharedPreferences("reader", Context.MODE_PRIVATE)
    var readingPercents by mutableStateOf<Map<Uri, Int>>(emptyMap()); private set
    private var readerCloudJob: Job? = null
    fun readingPercent(uri: Uri): Int = readingPercents[uri]
        ?: readerPrefs.getInt("percent_" + readerProgressKey(uri), 0)
    fun openedThisMonth(): Int {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        return books.count { book ->
            prefs.getLong("opened_" + book.uri.toString().hashCode(), 0L) >= start
        }
    }
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
    var offlineDownloadUri by mutableStateOf<Uri?>(null); private set
    var offlineDownloadPercent by mutableIntStateOf(0); private set
    var offlineDownloadMessage by mutableStateOf<String?>(null); private set
    var offlineRevision by mutableIntStateOf(0); private set
    fun downloadForOffline(book: Book) {
        if (offlineDownloadUri != null) return
        viewModelScope.launch {
            offlineDownloadUri = book.uri
            offlineDownloadPercent = 0
            offlineDownloadMessage = null
            try {
                val filename = saveOfflineBook(getApplication(), book) { percent ->
                    withContext(Dispatchers.Main) { offlineDownloadPercent = percent }
                }
                offlineDownloadMessage = "Guardado en Descargas: $filename"
                offlineRevision++
            } catch (e: Exception) {
                offlineDownloadMessage = "No se pudo descargar: ${e.localizedMessage ?: "error de lectura"}"
            } finally { offlineDownloadUri = null }
        }
    }
    fun removeOfflineCopy(book: Book) {
        if (offlineDownloadUri != null) return
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { deleteOfflineBook(getApplication(), book) }
            offlineDownloadMessage = if (removed) "Copia local eliminada. El libro sigue en tu biblioteca."
                else "No se encontró la copia local en Descargas."
            offlineRevision++
        }
    }
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
    var activityLog by mutableStateOf<List<String>>(emptyList()); private set

    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
    var annualReadingGoal by mutableIntStateOf(prefs.getInt("annual_reading_goal", 12))
        private set
    fun setAnnualReadingGoal(goal: Int) {
        annualReadingGoal = goal.coerceIn(1, 365)
        prefs.edit().putInt("annual_reading_goal", annualReadingGoal).apply()
    }
    private val bookStore = BookStore(app)
    private val saveMutex = Mutex()
    private var catalogSaveJob: Job? = null
    private var initialRestoreJob: Job? = null
    @Volatile private var saveVersion = 0
    private val folderKey = "folder_uri"
    private val cloudMutex = Mutex()
    private val cloudName = "MiBiblioteca_Diroka77.json"

    init {
        sections = safeSavedArray(prefs.getString("sections", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }
        initialRestoreJob = viewModelScope.launch {
            restoreCachedBooks(onlyIfEmpty = true)
        }
        viewMode = prefs.getString("view_mode", "Galería") ?: "Galería"
        if (!prefs.getBoolean("cover_first_v48", false)) {
            if (!prefs.contains("view_mode_general")) viewMode = "Galería"
            prefs.edit().putString("view_mode", viewMode)
                .putBoolean("cover_first_v48", true).apply()
        }
        reviewPending = safeSavedArray(prefs.getString("review_pending", "[]")).let { arr ->
            (0 until arr.length()).map { Uri.parse(arr.optString(it)) }.toSet()
        }
        showHomeNews = prefs.getBoolean("show_home_news", true)
        readingFirst = prefs.getBoolean("reading_first", true)
        loadWishList()
        activityLog = safeSavedArray(prefs.getString("activity_log", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter(String::isNotBlank)
        }
        sagaCatalogs = try {
            val saved = JSONObject(prefs.getString("saga_catalogs", "{}") ?: "{}")
            saved.keys().asSequence().map { key -> normalizeSagaName(key) to run {
                val array = saved.optJSONArray(key) ?: org.json.JSONArray()
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { item ->
                        SagaVolume(item.optString("order"), item.optString("title"))
                    }?.takeIf { it.title.isNotBlank() }
                }
            } }.groupBy({ it.first }, { it.second }).mapValues { (_, lists) ->
                lists.flatten().distinctBy { it.order + "|" + it.title.lowercase() }
            }
        } catch (_: Exception) { emptyMap() }
        wallpaperMode = prefs.getString("wallpaper_mode", "Automático") ?: "Automático"
        animationMode = prefs.getString("animation_mode", "Equilibrada") ?: "Equilibrada"
        lastOpenedUri = prefs.getString("last_opened_book", null)?.let(Uri::parse)
        folderName = prefs.getString("folder_name", null)
        restoreLaunchNews()
        hiddenNewsSources = safeSavedArray(prefs.getString("hidden_news_sources", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }
        hiddenNewsUrls = safeSavedArray(prefs.getString("hidden_news_urls", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }
        newsLastChecked = prefs.getLong("literary_news_v3_checked", 0L)
        refreshLaunchNews()
        syncReport = prefs.getString("sync_report", "Todavía no se ha sincronizado.") ?: ""
    }

    private fun restoreLaunchNews() {
        val saved = try { safeSavedArray(prefs.getString("literary_news", "[]")) }
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
                val publishers = featuredNewsSources.map { (source, address) ->
                    async(Dispatchers.IO) { fetchFeaturedNews(source, address).take(1) }
                }
                val results = queries.awaitAll().flatten() + books.await() +
                    publishers.awaitAll().flatten()
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
        val revision = saveVersion
        val folder = prefs.getString(folderKey, null)
        try {
            val restored = withContext(Dispatchers.IO) {
            val fromDatabase = bookStore.readArray()
            val array = if (fromDatabase.length() > 0) fromDatabase
                else safeSavedArray(prefs.getString("books_cache", "[]"))
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
                    coverSource = j.optString("coverSource"), rating = j.optInt("rating"),
                    startedDate = j.optString("startedDate"), finishedDate = j.optString("finishedDate"),
                    quotes = j.optString("quotes"),
                    tags = j.optJSONArray("tags")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }.filter(String::isNotBlank)
                    }.orEmpty(), loanTo = j.optString("loanTo"), loanDate = j.optString("loanDate"),
                    rereadCount = j.optInt("rereadCount"))
                } catch (_: Exception) { null }
            }
            }
            if (revision == saveVersion && folder == prefs.getString(folderKey, null) &&
                (!onlyIfEmpty || books.isEmpty())) books = restored
        } catch (_: Exception) {
            detailMessage = "No se pudo recuperar el catálogo. Se conserva la biblioteca actual."
        }
    }

    private fun saveBooks() {
        val snapshot = books.toList()
        val publishAfterSave = !syncing
        val revision = ++saveVersion
        prefs.edit().putLong("local_revision", System.currentTimeMillis()).apply()
        catalogSaveJob = viewModelScope.launch {
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
            .put("plotSource", b.plotSource).put("bioSource", b.bioSource).put("coverSource", b.coverSource)
            .put("rating", b.rating).put("startedDate", b.startedDate)
            .put("finishedDate", b.finishedDate).put("quotes", b.quotes)
            .put("tags", org.json.JSONArray(b.tags)).put("loanTo", b.loanTo)
            .put("loanDate", b.loanDate).put("rereadCount", b.rereadCount)) }
                    bookStore.replaceAll(array)
                    prefs.edit().putString("books_cache", array.toString()).apply()
                }
            }
            if (revision == saveVersion && publishAfterSave) saveCloud()
            } catch (e: Exception) {
                detailMessage = "No se pudo guardar el catálogo local: ${e.localizedMessage}"
            }
        }
    }

    private fun loadWishList() {
        val arr = safeSavedArray(prefs.getString("wish_list", "[]"))
        wishList = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { j ->
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
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        if (wishList.none { it.first.equals(cleanTitle, true) && it.second == clean }) {
            wishList = wishList + (cleanTitle to clean)
            saveWishList()
        }
    }

    fun removeGoodreadsWish(url: String) {
        wishList = wishList.filterNot { it.second == url }
        saveWishList()
    }

    private fun cloudSnapshot(): String {
        val booksJson = safeSavedArray(prefs.getString("books_cache", "[]"))
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
                .put("readerChar", readerPrefs.getInt("char_$progressKey", 0))
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
            .put("wishList", safeSavedArray(prefs.getString("wish_list", "[]")))
            .put("books", booksJson).toString().also {
                parseBackup(it)
                require(it.toByteArray(Charsets.UTF_8).size <= FileLimits.BACKUP_BYTES) {
                    "El respaldo supera el límite de 32 MB."
                }
            }
    }

    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            try {
                catalogSaveJob?.join()
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

    fun exportCatalogCsv(destination: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fun cell(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                val rows = buildString {
                    appendLine("Título;Autor;Saga;Número;Géneros;ISBN;Estado;Valoración;Inicio;Fin;Secciones;Etiquetas;Prestado a;Fecha préstamo;Relecturas;Citas;Notas")
                    books.forEach { b -> appendLine(listOf(displayTitle(b), displayAuthor(b), b.saga,
                        b.sagaOrder, b.genre, b.isbn, b.status.label, b.rating.toString(),
                        b.startedDate, b.finishedDate, bookSections(b).joinToString(", "), b.tags.joinToString(", "),
                        b.loanTo, b.loanDate, b.rereadCount.toString(),
                        b.quotes, b.notes).joinToString(";") { cell(it) }) }
                }
                getApplication<Application>().contentResolver.openOutputStream(destination, "w")
                    ?.bufferedWriter(Charsets.UTF_8)?.use { it.write("\uFEFF"); it.write(rows) }
                    ?: error("No se pudo crear el archivo")
                withContext(Dispatchers.Main) { message = "Catálogo exportado. Puedes abrirlo con Excel." }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message = "No se pudo exportar el catálogo: ${e.localizedMessage}" }
            }
        }
    }

    fun exportReadingNotebook(destination: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = buildString {
                    appendLine("MI BIBLIOTECA · CUADERNO DE LECTURA")
                    appendLine()
                    books.filter { it.notes.isNotBlank() || it.quotes.isNotBlank() ||
                        readerPrefs.getString("notebook_" + readerProgressKey(it.uri), "").orEmpty().isNotBlank() ||
                        readerPrefs.getString("highlights_" + readerProgressKey(it.uri), "[]") != "[]" }
                        .forEach { book ->
                            appendLine(displayTitle(book)); appendLine(displayAuthor(book))
                            if (book.quotes.isNotBlank()) appendLine("Citas:\n${book.quotes}")
                            if (book.notes.isNotBlank()) appendLine("Observaciones:\n${book.notes}")
                            readerPrefs.getString("notebook_" + readerProgressKey(book.uri), "")
                                .orEmpty().takeIf(String::isNotBlank)?.let { appendLine("Libreta:\n$it") }
                            val marks = safeSavedArray(readerPrefs.getString(
                                "highlights_" + readerProgressKey(book.uri), "[]"))
                            if (marks.length() > 0) appendLine("Subrayados:")
                            for (i in 0 until marks.length()) marks.optJSONObject(i)?.let { mark ->
                                appendLine("• " + mark.optString("quote"))
                                mark.optString("note").takeIf(String::isNotBlank)?.let { appendLine("  Nota: $it") }
                            }
                            appendLine("\n—\n")
                        }
                }
                getApplication<Application>().contentResolver.openOutputStream(destination, "w")
                    ?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                    ?: error("No se pudo crear el archivo")
                withContext(Dispatchers.Main) { message = "Cuaderno de lectura exportado." }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message = "No se pudo exportar el cuaderno: ${e.localizedMessage}" }
            }
        }
    }

    fun importBackup(source: Uri) {
        viewModelScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(source)
                        ?.use { it.readLimited(FileLimits.BACKUP_BYTES).toString(Charsets.UTF_8) }
                        ?: throw IllegalStateException("No se pudo abrir el respaldo")
                }
                val backup = parseBackup(payload)
                val result = applyBackup(backup)
                val matched = result.matched
                prefs.edit().putString("pending_backup", result.pending).apply()
                if (result.pending != null) {
                    message = if (books.isEmpty())
                        "Respaldo recibido. Selecciona tu carpeta de libros y sincroniza para recuperarlo."
                    else "$matched fichas restauradas. Las restantes se recuperarán al sincronizar."
                } else message = "$matched fichas restauradas."
            } catch (e: Exception) { message = "No se pudo importar: ${e.localizedMessage}" }
        }
    }

    private suspend fun applyBackup(backup: JSONObject): BackupRestoreResult {
        withContext(Dispatchers.IO) {
            validateBackup(backup)
            validateBackupImages(backup)
        }
        val entries = backup.getJSONArray("books")
        val records = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
        val normalize: (String) -> String = { value ->
            java.text.Normalizer.normalize(cleanCatalogText(value).lowercase(),
                java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^\\p{L}\\p{N}]"), "")
        }
        val byUri = records.indices.groupBy { records[it].optString("uri") }
        val byIsbn = records.indices.groupBy { records[it].optString("isbn").filter(Char::isDigit) }
        fun identity(title: String, author: String) = normalize(title) + "|" + normalize(author)
        val byIdentity = records.indices.groupBy { i ->
            val record = records[i]
            identity(record.optString("customTitle").ifBlank { record.optString("title") },
                record.optString("customAuthor").ifBlank { record.optString("author") })
        }
        val readerEditor = readerPrefs.edit()
        val dataEditor = prefs.edit()
        val requiredSpace = records.sumOf { it.optString("coverBase64").length.toLong() * 2 } + FileLimits.FREE_SPACE
        require(getApplication<Application>().filesDir.usableSpace > requiredSpace) {
            "No hay espacio suficiente para restaurar el respaldo."
        }
        val selected = mutableSetOf<Int>()
        var matched = 0
        val restored = books.map { book ->
            val isbn = book.isbn.filter(Char::isDigit)
            val candidates = byUri[book.uri.toString()].orEmpty() +
                (if (isbn.length >= 10) byIsbn[isbn].orEmpty() else emptyList()) +
                byIdentity[identity(displayTitle(book), displayAuthor(book))].orEmpty()
            val index = candidates.firstOrNull { it !in selected } ?: return@map book
            selected += index; matched++
            val record = records[index]
            val names = record.optJSONArray("sections")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter(String::isNotBlank)
            }.orEmpty().ifEmpty { listOfNotNull(record.optString("section").takeIf(String::isNotBlank)) }
            val uri = book.uri
            val progressKey = readerProgressKey(uri)
            val image = record.optString("coverBase64")
            if (image.isNotBlank()) run {
                val bytes = decodeBackupCover(image)
                val file = coverFile(getApplication(), uri)
                file.writeSafely(bytes)
                File(getApplication<Application>().filesDir, "manual-" + file.name).writeSafely(bytes)
            }
            readerEditor.putInt("percent_$progressKey", record.optInt("readerPercent"))
                .putInt("item_$progressKey", record.optInt("readerItem"))
                .putInt("offset_$progressKey", record.optInt("readerOffset"))
                .putInt("page_$progressKey", record.optInt("readerPage"))
                .putString("highlights_$progressKey", record.optString("readerHighlights", "[]"))
            if (record.has("readerChar")) readerEditor.putInt("char_$progressKey", record.getInt("readerChar"))
            else readerEditor.remove("char_$progressKey")
            dataEditor.putString("info_" + uri, JSONObject()
                .put("plot", record.optString("plot")).put("bio", record.optString("bio")).toString())
                .putBoolean("manual_info_" + uri, record.optBoolean("manualInfo"))
            val removedMarker = File(getApplication<Application>().filesDir,
                "removed-" + coverFile(getApplication(), uri).name)
            if (record.optBoolean("coverRemoved")) removedMarker.writeText("1")
            else removedMarker.delete()
            book.copy(section = names.firstOrNull().orEmpty(), sections = names,
                notes = record.optString("notes"), goodreadsUrl = record.optString("goodreadsUrl"),
                wantToRead = record.optBoolean("wantToRead"), favorite = record.optBoolean("favorite"),
                customTitle = record.optString("customTitle"), customAuthor = record.optString("customAuthor"),
                sagaOrder = record.optString("sagaOrder"),
                status = ReadingStatus.entries.firstOrNull { it.name == record.optString("status") }
                    ?: book.status,
                spanishPlot = record.optString("plot"), authorBio = record.optString("bio"),
                plotSource = record.optString("plotSource"), bioSource = record.optString("bioSource"),
                rating = record.optInt("rating"), startedDate = record.optString("startedDate"),
                finishedDate = record.optString("finishedDate"), quotes = record.optString("quotes"),
                tags = record.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter(String::isNotBlank)
                }.orEmpty(), loanTo = record.optString("loanTo"), loanDate = record.optString("loanDate"),
                rereadCount = record.optInt("rereadCount"),
                cover = if (record.optBoolean("coverRemoved")) null else if (image.isNotBlank()) run {
                    coverFile(getApplication(), uri).readBytes()
                } else book.cover)
        }
        if (matched > 0) {
            readerEditor.apply()
            dataEditor.apply()
            books = restored
            val names = backup.optJSONArray("sections")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            }.orEmpty()
            sections = (sections + names).filter(String::isNotBlank).distinct()
            prefs.edit().putString("sections", org.json.JSONArray(sections).toString()).apply()
            backup.optJSONArray("wishList")?.let { prefs.edit().putString("wish_list", it.toString()).apply() }
            loadWishList(); saveBooks()
        }
        val remaining = records.filterIndexed { i, _ -> i !in selected }
        val pending = if (remaining.isEmpty()) null else JSONObject(backup.toString())
            .put("books", org.json.JSONArray(remaining)).toString()
        return BackupRestoreResult(matched, pending)
    }

    private fun saveCloud() {
        if (syncing) return // Never publish an incomplete catalog while Drive is being scanned.
        prefs.edit().putLong("local_revision", System.currentTimeMillis()).apply()
        val tree = prefs.getString(folderKey, null)?.let(Uri::parse) ?: return
        viewModelScope.launch {
            try {
                catalogSaveJob?.join()
                cloudMutex.withLock {
                    val snapshot = withContext(Dispatchers.IO) { saveMutex.withLock { cloudSnapshot() } }
                    withContext(Dispatchers.IO) {
                        val root = DocumentFile.fromTreeUri(getApplication(), tree)
                            ?: throw IllegalStateException("Carpeta no disponible")
                        val file = root.findFile(cloudName)
                            ?: root.createFile("application/json", cloudName)
                            ?: throw IllegalStateException("No se pudo crear el archivo de datos")
                        val resolver = getApplication<Application>().contentResolver
                        // Save a validated previous generation before truncating the current one.
                        val previous = try {
                            resolver.openInputStream(file.uri)?.use {
                                it.readLimited(FileLimits.BACKUP_BYTES).also { bytes ->
                                    parseBackup(bytes.toString(Charsets.UTF_8))
                                }
                            }
                        } catch (_: Exception) { null }
                        if (previous != null) {
                            val recovery = root.findFile(cloudName + ".previous")
                                ?: root.createFile("application/octet-stream", cloudName + ".previous")
                                ?: error("No se pudo conservar el respaldo anterior")
                            resolver.openOutputStream(recovery.uri, "wt")?.use { it.write(previous) }
                                ?: error("No se pudo conservar el respaldo anterior")
                        }
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

    private suspend fun restoreCloud(tree: Uri, force: Boolean = false) {
        val cloud = withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(getApplication(), tree) ?: return@withContext null
            fun readSnapshot(name: String): JSONObject? = root.findFile(name)?.let { file ->
                getApplication<Application>().contentResolver.openInputStream(file.uri)?.use {
                    parseBackup(it.readLimited(FileLimits.BACKUP_BYTES).toString(Charsets.UTF_8)).also(::validateBackupImages)
                }
            }
            try { readSnapshot(cloudName) ?: readSnapshot(cloudName + ".previous") }
            catch (failure: Exception) { readSnapshot(cloudName + ".previous") ?: throw failure }
        } ?: return
        if (!force && books.isNotEmpty() &&
            cloud.optLong("updatedAt") <= prefs.getLong("local_revision", 0L)) return
        withContext(Dispatchers.IO) { validateBackupImages(cloud) }
        // Existing local records must never disappear because a cloud snapshot is incomplete.
        if (books.isNotEmpty()) {
            val result = applyBackup(cloud)
            if (result.pending != null) prefs.edit().putString("pending_backup", result.pending).apply()
            return
        }
        val booksArray = cloud.optJSONArray("books") ?: return
        withContext(Dispatchers.IO) { bookStore.replaceAll(booksArray) }
        prefs.edit().putString("books_cache", booksArray.toString())
            .putString("sections", cloud.optJSONArray("sections")?.toString() ?: "[]")
            .putString("wish_list", cloud.optJSONArray("wishList")?.toString() ?: "[]")
            .putLong("local_revision", cloud.optLong("updatedAt")).apply()
        for (i in 0 until booksArray.length()) {
            val j = booksArray.getJSONObject(i)
            val uri = Uri.parse(j.getString("uri"))
            val progressKey = readerProgressKey(uri)
            readerPrefs.edit().putInt("percent_$progressKey", j.optInt("readerPercent"))
                .putInt("item_$progressKey", j.optInt("readerItem"))
                .putInt("offset_$progressKey", j.optInt("readerOffset"))
                .putInt("page_$progressKey", j.optInt("readerPage"))
                .putString("highlights_$progressKey", j.optString("readerHighlights", "[]")).apply()
            if (j.has("readerChar")) readerPrefs.edit().putInt("char_$progressKey", j.getInt("readerChar")).apply()
            else readerPrefs.edit().remove("char_$progressKey").apply()
            prefs.edit().putString("info_" + uri, JSONObject()
                .put("plot", j.optString("plot")).put("bio", j.optString("bio")).toString())
                .putBoolean("manual_info_" + uri, j.optBoolean("manualInfo")).apply()
            val base64 = j.optString("coverBase64")
            if (j.optBoolean("coverRemoved")) withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir,
                    "removed-" + coverFile(getApplication(), uri).name).writeText("1")
            }
            if (base64.isNotBlank()) withContext(Dispatchers.IO) {
                val bytes = decodeBackupCover(base64)
                coverFile(getApplication(), uri).writeSafely(bytes)
                File(getApplication<Application>().filesDir,
                    "manual-" + coverFile(getApplication(), uri).name).writeSafely(bytes)
            }
        }
        sections = safeSavedArray(prefs.getString("sections", "[]")).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }
        loadWishList()
        prefs.edit().putString("pending_backup", cloud.toString()).apply()
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

    fun searchSaga(uri: Uri) {
        if (uri in sagaSearches) return
        val book = books.firstOrNull { it.uri == uri } ?: return
        viewModelScope.launch {
            sagaSearches = sagaSearches + uri
            try {
                val found = withContext(Dispatchers.IO) { fetchSagaMetadata(book) }
                if (found.first.isBlank()) {
                    detailMessage = "No se ha podido identificar la saga automáticamente."
                } else {
                    books = books.map { current -> if (current.uri == uri) current.copy(
                        saga = found.first,
                        sagaOrder = found.second.ifBlank { current.sagaOrder }
                    ) else current }
                    saveBooks()
                    detailMessage = "Saga encontrada: ${found.first}" +
                        found.second.takeIf(String::isNotBlank)?.let { " · número $it" }.orEmpty()
                    searchSagaCatalog(found.first, book)
                }
            } catch (e: Exception) {
                detailMessage = "No se pudo consultar la saga: ${e.localizedMessage ?: "sin conexión"}"
            } finally { sagaSearches = sagaSearches - uri }
        }
    }

    fun organizeAllSagas() {
        if (sagaOrganizing) return
        viewModelScope.launch {
            sagaOrganizing = true
            val targets = books.filter { it.saga.isBlank() }
            sagaOrganized = 0; sagaOrganizeTotal = targets.size
            var changed = 0
            try {
                targets.forEach { original ->
                    val local = inferredSaga(original)
                    val found = if (local.first.isNotBlank()) local else try {
                        withContext(Dispatchers.IO) { fetchSagaMetadata(original) }
                    } catch (_: Exception) { "" to "" }
                    if (found.first.isNotBlank()) {
                        books = books.map { current -> if (current.uri == original.uri) current.copy(
                            saga = found.first.trim(),
                            sagaOrder = found.second.ifBlank { current.sagaOrder }) else current }
                        changed++
                        if (changed % 10 == 0) saveBooks()
                    }
                    sagaOrganized++
                    delay(100)
                }
                if (changed > 0) saveBooks()
                detailMessage = "Sagas revisadas: $changed libros identificados de ${targets.size}."
            } finally { sagaOrganizing = false }
        }
    }

    fun renameSagaGroup(source: String, target: String) {
        val clean = target.trim()
        val sourceKey = normalizeSagaName(source)
        if (clean.isBlank() || sourceKey.isBlank()) return
        var changed = 0
        books = books.map { book ->
            if (sagaGroupKey(book) == sourceKey) {
                changed++; book.copy(saga = clean, sagaOrder = inferredSaga(book).second)
            } else book
        }
        if (changed > 0) {
            saveBooks(); addActivity("Saga unificada · $clean · $changed libros")
            detailMessage = "$changed libros agrupados como «$clean»."
        }
    }

    fun searchSagaCatalog(saga: String, reference: Book? = null) {
        val clean = saga.trim()
        val key = normalizeSagaName(clean)
        if (clean.isBlank() || key in sagaCatalogSearches) return
        val book = reference ?: books.firstOrNull { it.saga.equals(clean, true) }
        viewModelScope.launch {
            sagaCatalogSearches = sagaCatalogSearches + key
            try {
                val found = withContext(Dispatchers.IO) { fetchSagaCatalog(clean, book) }
                if (found.isEmpty()) {
                    detailMessage = "No se ha podido obtener el catálogo completo de «$clean»."
                } else {
                    sagaCatalogs = sagaCatalogs + (key to found)
                    val root = JSONObject()
                    sagaCatalogs.forEach { (savedKey, volumes) ->
                        root.put(savedKey, org.json.JSONArray().apply {
                            volumes.forEach { volume -> put(JSONObject()
                                .put("order", volume.order).put("title", volume.title)) }
                        })
                    }
                    prefs.edit().putString("saga_catalogs", root.toString()).apply()
                    detailMessage = "Saga comprob…64041 tokens truncated…                  val x = size.width * ((n * 37 % 89 + 7) / 100f)
                            val y = size.height * ((n * 53 % 93 + 3) / 100f)
                            val radius = if (n % 4 == 0) .52.dp.toPx() else .32.dp.toPx()
                            drawCircle(Color(0xFF806545).copy(alpha = .09f),
                                radius, Offset(x, y))
                        }
                        val center = size.width * .5f
                        val ornamentY = size.height * .29f
                        drawLine(Color(0xFF8E7051).copy(alpha = .60f),
                            Offset(size.width * .25f, ornamentY),
                            Offset(size.width * .43f, ornamentY), .7.dp.toPx())
                        drawLine(Color(0xFF8E7051).copy(alpha = .60f),
                            Offset(size.width * .57f, ornamentY),
                            Offset(size.width * .76f, ornamentY), .7.dp.toPx())
                        val diamond = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center, ornamentY - 3.dp.toPx())
                            lineTo(center + 3.dp.toPx(), ornamentY)
                            lineTo(center, ornamentY + 3.dp.toPx())
                            lineTo(center - 3.dp.toPx(), ornamentY)
                            close()
                        }
                        drawPath(diamond, Color(0xFF8E7051).copy(alpha = .72f))
                    }
                    Column(Modifier.align(Alignment.Center).fillMaxWidth()
                        .padding(start = 27.dp, end = 17.dp, top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(displayTitle(book), color = Color(0xFF4A2B1E),
                            fontFamily = FontFamily.Serif,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, lineHeight = 23.sp, letterSpacing = .15.sp,
                            textAlign = TextAlign.Center, maxLines = 3)
                        Spacer(Modifier.height(15.dp))
                        Text(displayAuthor(book).uppercase(), color = Color(0xFF76563D),
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
                            fontSize = 8.sp, letterSpacing = 1.1.sp,
                            textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                val p = transition.value.coerceIn(0f, 1f)
                val opening = FastOutSlowInEasing.transform(
                    ((p - 0.03f) / 0.59f).coerceIn(0f, 1f))
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.04f, 0.5f)
                rotationY = -158f * opening
                rotationZ = -2.4f * kotlin.math.sin(opening * kotlin.math.PI).toFloat()
                scaleX = 1f + .018f * kotlin.math.sin(opening * kotlin.math.PI).toFloat()
                cameraDistance = 30.dp.toPx()
                alpha = 1f - FastOutSlowInEasing.transform(
                    ((opening - 0.56f) / 0.33f).coerceIn(0f, 1f))
            }) {
                Cover(book, 185.dp, 265.dp)
            }
        }
    }
}

private val wallpaperGenres = listOf(
    "Fantasía", "Ciencia ficción", "Terror", "Thriller", "Misterio",
    "Romance", "Novela histórica", "Aventuras", "Juvenil", "Infantil",
    "Clásicos", "Biografía", "Historia", "Ensayo", "Divulgación",
    "Humor", "Poesía", "Teatro", "Cómic y novela gráfica", "No ficción"
)

private val wallpaperImages = intArrayOf(
    R.drawable.wallpaper_fantasy, R.drawable.wallpaper_science_fiction,
    R.drawable.wallpaper_horror, R.drawable.wallpaper_thriller,
    R.drawable.wallpaper_mystery, R.drawable.wallpaper_romance,
    R.drawable.wallpaper_historical_novel, R.drawable.wallpaper_adventure,
    R.drawable.wallpaper_young_adult, R.drawable.wallpaper_children,
    R.drawable.wallpaper_classics, R.drawable.wallpaper_biography,
    R.drawable.wallpaper_history, R.drawable.wallpaper_essay,
    R.drawable.wallpaper_popular_science, R.drawable.wallpaper_humor,
    R.drawable.wallpaper_poetry, R.drawable.wallpaper_theatre,
    R.drawable.wallpaper_comics, R.drawable.wallpaper_nonfiction
)

private fun openingGenre(genre: String): String =
    normalizeBookGenres(genre).firstOrNull { it in wallpaperGenres } ?: "Clásicos"

private fun wallpaperGlyph(genre: String): List<FloatArray> = when (genre) {
    "Fantasía" -> listOf(
        floatArrayOf(4f,76f,32f,48f,48f,58f,72f,12f,84f,53f,96f,64f,67f,62f,46f,84f,38f,66f),
        floatArrayOf(31f,48f,20f,22f,65f,38f,48f,58f),
        floatArrayOf(72f,12f,64f,4f,61f,27f))
    "Ciencia ficción" -> listOf(
        floatArrayOf(45f,8f,68f,16f,80f,38f,73f,64f,53f,77f,29f,67f,18f,42f,26f,20f),
        floatArrayOf(3f,64f,18f,47f,39f,55f,69f,65f,96f,40f,90f,65f,66f,84f,31f,78f),
        floatArrayOf(74f,6f,79f,13f,86f,11f,81f,18f,83f,25f,75f,20f,69f,24f,71f,16f))
    "Terror" -> listOf(
        floatArrayOf(2f,33f,24f,42f,29f,21f,44f,39f,50f,36f,56f,39f,71f,21f,76f,42f,98f,33f,84f,72f,68f,61f,50f,77f,32f,61f,16f,72f),
        floatArrayOf(39f,77f,50f,85f,61f,77f,57f,93f,43f,93f))
    "Thriller" -> listOf(
        floatArrayOf(50f,5f,80f,23f,76f,58f,50f,90f,24f,58f,20f,23f),
        floatArrayOf(50f,20f,67f,34f,50f,62f,33f,34f),
        floatArrayOf(47f,57f,53f,57f,53f,72f,47f,72f))
    "Misterio" -> listOf(
        floatArrayOf(13f,30f,31f,11f,52f,7f,77f,25f,69f,54f,46f,75f,18f,59f),
        floatArrayOf(46f,34f,61f,38f,64f,53f,50f,61f,36f,53f,35f,40f),
        floatArrayOf(68f,66f,91f,88f,85f,94f,61f,72f))
    "Romance" -> listOf(
        floatArrayOf(50f,86f,7f,45f,9f,24f,24f,13f,39f,17f,50f,29f,61f,17f,76f,13f,91f,24f,93f,45f),
        floatArrayOf(50f,69f,25f,44f,25f,30f,39f,29f,50f,42f,61f,29f,75f,30f,75f,44f))
    "Novela histórica" -> listOf(
        floatArrayOf(18f,8f,82f,8f,82f,17f,64f,38f,55f,48f,64f,58f,82f,79f,82f,89f,18f,89f,18f,79f,36f,58f,45f,48f,36f,38f,18f,17f),
        floatArrayOf(31f,18f,69f,18f,50f,43f),
        floatArrayOf(50f,55f,69f,79f,31f,79f))
    "Aventuras" -> listOf(
        floatArrayOf(50f,3f,60f,40f,97f,50f,60f,60f,50f,97f,40f,60f,3f,50f,40f,40f),
        floatArrayOf(50f,22f,61f,50f,50f,78f,39f,50f),
        floatArrayOf(50f,32f,56f,50f,50f,68f,44f,50f))
    "Juvenil" -> listOf(
        floatArrayOf(8f,65f,26f,19f,43f,31f,63f,8f,92f,29f,74f,76f,47f,92f,25f,79f),
        floatArrayOf(28f,61f,48f,44f,59f,54f,82f,35f,68f,68f,47f,81f))
    "Infantil" -> listOf(
        floatArrayOf(50f,6f,61f,36f,94f,37f,69f,57f,78f,91f,50f,71f,22f,91f,31f,57f,6f,37f,39f,36f),
        floatArrayOf(50f,27f,56f,45f,75f,45f,60f,57f,66f,75f,50f,63f,34f,75f,40f,57f,25f,45f,44f,45f))
    "Clásicos" -> listOf(
        floatArrayOf(14f,16f,48f,10f,50f,78f,17f,86f),
        floatArrayOf(52f,10f,86f,16f,83f,86f,50f,78f),
        floatArrayOf(10f,86f,49f,79f,90f,86f,90f,93f,49f,87f,10f,93f))
    "Biografía" -> listOf(
        floatArrayOf(49f,7f,68f,16f,75f,36f,65f,56f,50f,63f,32f,56f,25f,36f,31f,16f),
        floatArrayOf(11f,91f,18f,72f,34f,65f,50f,69f,66f,65f,82f,72f,89f,91f))
    "Historia" -> listOf(
        floatArrayOf(12f,23f,37f,13f,61f,24f,89f,9f,89f,74f,61f,89f,37f,76f,12f,87f),
        floatArrayOf(37f,13f,37f,76f,61f,89f,61f,24f),
        floatArrayOf(12f,23f,37f,13f,61f,24f,89f,9f))
    "Ensayo" -> listOf(
        floatArrayOf(12f,16f,72f,16f,72f,26f,12f,26f),
        floatArrayOf(12f,37f,89f,37f,89f,47f,12f,47f),
        floatArrayOf(12f,58f,78f,58f,78f,68f,12f,68f),
        floatArrayOf(12f,79f,53f,79f,53f,89f,12f,89f))
    "Divulgación" -> listOf(
        floatArrayOf(50f,7f,61f,32f,87f,21f,76f,48f,96f,65f,68f,65f,50f,91f,32f,65f,4f,65f,24f,48f,13f,21f,39f,32f),
        floatArrayOf(50f,29f,58f,47f,50f,66f,42f,47f),
        floatArrayOf(43f,4f,57f,4f,50f,18f))
    "Humor" -> listOf(
        floatArrayOf(8f,28f,25f,13f,75f,13f,92f,28f,92f,65f,75f,80f,42f,80f,21f,94f,25f,78f,8f,65f),
        floatArrayOf(27f,39f,36f,39f,36f,49f,27f,49f),
        floatArrayOf(64f,39f,73f,39f,73f,49f,64f,49f),
        floatArrayOf(35f,61f,50f,69f,65f,61f,59f,75f,41f,75f))
    "Poesía" -> listOf(
        floatArrayOf(14f,77f,28f,49f,59f,14f,84f,10f,65f,38f,34f,68f),
        floatArrayOf(25f,92f,70f,53f,80f,57f,35f,97f),
        floatArrayOf(41f,58f,57f,40f,66f,43f,50f,61f))
    "Teatro" -> listOf(
        floatArrayOf(8f,20f,48f,11f,48f,82f,28f,92f,12f,76f),
        floatArrayOf(52f,11f,92f,20f,88f,76f,72f,92f,52f,82f),
        floatArrayOf(21f,44f,32f,44f,32f,52f,21f,52f),
        floatArrayOf(68f,44f,79f,44f,79f,52f,68f,52f))
    "Cómic y novela gráfica" -> listOf(
        floatArrayOf(9f,12f,91f,12f,91f,70f,51f,70f,22f,93f,28f,70f,9f,70f),
        floatArrayOf(30f,37f,43f,24f,55f,34f,69f,25f,66f,48f,46f,58f))
    else -> listOf(
        floatArrayOf(11f,20f,42f,10f,50f,26f,58f,10f,89f,20f,89f,83f,58f,73f,50f,91f,42f,73f,11f,83f),
        floatArrayOf(24f,32f,45f,27f,50f,39f,55f,27f,76f,32f,76f,69f,55f,63f,50f,76f,45f,63f,24f,69f))
}

@Composable
private fun LibraryWallpaper(mode: String, book: Book?) {
    if (mode == "Clásico") return
    val genre = if (mode in wallpaperGenres) mode else openingGenre(book?.genre.orEmpty())
    val palette = when (genre) {
        "Terror", "Thriller", "Misterio" -> Color(0xFF68747B)
        "Ciencia ficción", "Divulgación" -> Color(0xFF537D86)
        "Romance", "Poesía" -> Color(0xFFAE7783)
        "Fantasía", "Aventuras" -> Color(0xFF9B7854)
        "Infantil", "Juvenil", "Humor" -> Color(0xFFAF925D)
        else -> Color(0xFF817A65)
    }
    val sceneReveal = remember(genre) { Animatable(0f) }
    LaunchedEffect(genre) {
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            sceneReveal.snapTo(0f)
            sceneReveal.animateTo(1f, tween(1650, easing = FastOutSlowInEasing))
        } else sceneReveal.snapTo(1f)
    }
    val reveal = sceneReveal.value
    val drift = 1f - reveal
    val glimmer = .07f + .08f * reveal
    Crossfade(targetState = genre to palette,
        animationSpec = tween(1700, easing = FastOutSlowInEasing),
        label = "Cambio de género") { (activeGenre, color) ->
        val glyphs = remember(activeGenre) { wallpaperGlyph(activeGenre) }
        val imageId = wallpaperImages[wallpaperGenres.indexOf(activeGenre).coerceAtLeast(0)]
        Box(Modifier.fillMaxSize().background(Parchment)) {
            Image(painterResource(imageId), contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val move = if (android.animation.ValueAnimator.areAnimatorsEnabled()) drift else 0f
                    scaleX = 1.10f - reveal * .06f
                    scaleY = 1.10f - reveal * .06f
                    translationX = move * 14.dp.toPx()
                    translationY = move * 7.dp.toPx()
                    alpha = .52f + reveal * .48f
                }, contentScale = ContentScale.Crop)
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val shift = if (android.animation.ValueAnimator.areAnimatorsEnabled())
                    drift * 16.dp.toPx() else 0f
                // Dark, warm library atmosphere from the approved catalogue concept.
                drawRect(Brush.verticalGradient(listOf(
                    Color(0x8A160E09), Color(0x701B110B),
                    Color(0x82130D09), Color(0xA00B0806)),
                    startY = 0f, endY = h))
                drawRect(Brush.horizontalGradient(listOf(
                    Color(0x52000000), Color(0x26000000), Color(0x42000000))))
                drawRect(Brush.radialGradient(listOf(
                    Color(0xFFFFB75E).copy(alpha = glimmer), Color.Transparent),
                    center = Offset(w * .70f + shift, h * .24f), radius = w * .85f))
                drawRect(Brush.radialGradient(listOf(
                    Color(0x3A8D572F), Color.Transparent),
                    center = Offset(w * .50f, h * .52f), radius = w * .72f))
                drawRect(Color(0xFF120C08).copy(alpha = (1f - reveal) * .24f))
                val band = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, h * .13f)
                    lineTo(w * .38f, h * .08f)
                    lineTo(w, h * .19f)
                    lineTo(w, h * .41f)
                    lineTo(w * .62f, h * .35f)
                    lineTo(0f, h * .44f)
                    close()
                }
                drawPath(band, color.copy(alpha = .055f))
                val lower = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, h * .76f)
                    lineTo(w * .38f, h * .71f)
                    lineTo(w, h * .82f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(lower, color.copy(alpha = .055f))
                fun facet(points: FloatArray, centerX: Float, centerY: Float,
                          scale: Float, opacity: Float, index: Int) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(centerX + (points[0] - 50f) * scale + shift,
                            centerY + (points[1] - 50f) * scale)
                        var n = 2
                        while (n < points.size) {
                            lineTo(centerX + (points[n] - 50f) * scale + shift,
                                centerY + (points[n + 1] - 50f) * scale)
                            n += 2
                        }
                        close()
                    }
                    drawPath(path, color.copy(alpha = opacity * (if (index == 0) 1f else .64f)))
                    drawPath(path, color.copy(alpha = opacity * .44f),
                        style = Stroke(width = 1.3.dp.toPx()))
                }
                val scale = w * .0062f
                glyphs.forEachIndexed { index, points ->
                    facet(points, w * .70f, h * .27f, scale, .17f, index)
                    facet(points, w * .20f, h * .80f, scale * .46f, .09f, index)
                }
                drawCircle(color.copy(alpha = .065f), w * .31f,
                    Offset(w * .72f + shift, h * .30f),
                    style = Stroke(width = 1.dp.toPx()))
                when (activeGenre) {
                    "Fantasía", "Aventuras", "Infantil" -> {
                        for (n in 0..14) {
                            val x = w * ((n * 37 % 91 + 4) / 100f) + shift * (n % 3 + 1)
                            val y = h * ((n * 23 % 83 + 8) / 100f)
                            drawCircle(Color(0xFFB68C51).copy(alpha = .18f),
                                (if (n % 4 == 0) 1.4f else .7f).dp.toPx(),
                                Offset(x, y))
                        }
                    }
                    "Ciencia ficción", "Divulgación" -> {
                        drawArc(color.copy(alpha = .16f), -58f + drift * 7f,
                            124f, false,
                            topLeft = Offset(w * .22f + shift, h * .08f),
                            size = Size(w * .68f, w * .68f),
                            style = Stroke(width = 1.2.dp.toPx()))
                        drawArc(color.copy(alpha = .10f), 108f - drift * 5f,
                            90f, false,
                            topLeft = Offset(w * .12f - shift, h * .72f),
                            size = Size(w * .54f, w * .54f),
                            style = Stroke(width = .8.dp.toPx()))
                    }
                    "Terror", "Thriller", "Misterio" -> {
                        for (n in 0..3) {
                            val y = h * (.25f + n * .19f) + shift * (n % 2 * 2 - 1)
                            val mist = androidx.compose.ui.graphics.Path().apply {
                                moveTo(-w * .06f, y)
                                quadraticTo(w * .50f, y - 8.dp.toPx(),
                                    w * 1.06f, y + 3.dp.toPx())
                            }
                            drawPath(mist, color.copy(alpha = .09f),
                                style = Stroke(width = (8 + n * 3).dp.toPx()))
                        }
                    }
                    "Romance", "Poesía" -> {
                        for (n in 0..8) {
                            val x = w * ((n * 31 % 87 + 7) / 100f) + shift
                            val y = h * ((n * 41 % 85 + 8) / 100f)
                            drawOval(color.copy(alpha = .13f),
                                topLeft = Offset(x, y),
                                size = Size(2.dp.toPx(), 5.dp.toPx()))
                        }
                    }
                    "Novela histórica", "Historia", "Clásicos" -> {
                        for (n in 0..7) {
                            val y = h * (.12f + n * .105f)
                            val length = w * (.08f + (n % 3) * .045f)
                            val x = w * ((n * 29 % 71 + 6) / 100f) + shift * .35f
                            drawLine(color.copy(alpha = .105f),
                                Offset(x, y), Offset((x + length).coerceAtMost(w), y),
                                .65.dp.toPx())
                        }
                    }
                    "Juvenil", "Humor", "Cómic y novela gráfica" -> {
                        for (n in 0..11) {
                            val x = w * ((n * 43 % 93 + 3) / 100f) - shift * .45f
                            val y = h * ((n * 31 % 89 + 5) / 100f)
                            val r = (if (n % 3 == 0) 2.1f else 1.05f).dp.toPx()
                            drawCircle(color.copy(alpha = .11f), r, Offset(x, y),
                                style = Stroke(width = .7.dp.toPx()))
                        }
                    }
                    "Biografía", "Ensayo", "No ficción", "Teatro" -> {
                        for (n in 0..5) {
                            val y = h * (.18f + n * .125f) + shift * .16f
                            drawLine(color.copy(alpha = .085f),
                                Offset(w * .11f, y),
                                Offset(w * (.29f + (n % 2) * .08f), y),
                                .7.dp.toPx())
                        }
                    }
                }
            }
        }
    }
    val unclassified = book != null && normalizeBookGenres(book.genre)
        .none { it in wallpaperGenres }
    if ((mode == "Portada" || (mode == "Automático" && unclassified)) && book != null) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp)
                .graphicsLayer { alpha = .10f; rotationZ = -8f }) {
                FlatCover(book, 180.dp, 255.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BookDetail(
    book: Book, back: () -> Unit, toggleFavorite: () -> Unit,
    onOpened: () -> Unit, setStatus: (ReadingStatus) -> Unit, openBook: () -> Unit, message: String?,
    possibleDuplicates: List<Book>, deleteBook: () -> Unit, enrich: () -> Unit,
    recoverCover: () -> Unit, infoLoading: Boolean, coverSearching: Boolean,
    sections: List<String>, assignSection: (String) -> Unit,
    saveNotes: (String) -> Unit, saveInfo: (String, String) -> Unit,
    replaceCover: (Uri) -> Unit,
    saveGoodreadsUrl: (String) -> Unit, toggleWant: () -> Unit, detailMessage: String?,
    needsReview: Boolean, confirmDetails: () -> Unit,
    editIdentity: (String, String, String, String) -> Unit,
    genreSearching: Boolean, searchGenre: () -> Unit, updateGenre: (String) -> Unit,
    sagaSearching: Boolean, searchSaga: () -> Unit,
    sagaCatalog: List<SagaVolume>, sagaCatalogSearching: Boolean,
    searchSagaCatalog: () -> Unit,
    downloadForOffline: () -> Unit, offlineDownloading: Boolean,
    offlineDownloadPercent: Int, offlineDownloadMessage: String?,
    offlineAvailable: Boolean, removeOfflineCopy: () -> Unit,
    wallpaperMode: String, libraryBooks: List<Book>,
    saveReadingRecord: (Int, String, String, String) -> Unit,
    showAuthorBooks: () -> Unit, showSagaBooks: () -> Unit,
    saveBookManagement: (String, String, String, Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val opening = remember(book.uri) { Animatable(0f) }
    LaunchedEffect(book.uri) {
        onOpened()
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            opening.snapTo(0f)
            opening.animateTo(1f, tween(1050, easing = FastOutSlowInEasing))
        } else opening.snapTo(1f)
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var sectionMenu by remember { mutableStateOf(false) }
    var moreActionsExpanded by rememberSaveable(book.uri) { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    var editIdentityDialog by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    var authorDraft by remember { mutableStateOf("") }
    var sagaDraft by remember { mutableStateOf("") }
    var orderDraft by remember { mutableStateOf("") }
    var plotDraft by remember { mutableStateOf("") }
    var bioDraft by remember { mutableStateOf("") }
    var genreDraft by remember(book.uri, book.genre) {
        mutableStateOf(normalizeBookGenres(book.genre).joinToString(", "))
    }
    var editGenre by remember { mutableStateOf(false) }
    var notesDraft by remember(book.uri, book.notes) { mutableStateOf(book.notes) }
    var ratingDraft by remember(book.uri, book.rating) { mutableIntStateOf(book.rating) }
    var startedDraft by remember(book.uri, book.startedDate) { mutableStateOf(book.startedDate) }
    var finishedDraft by remember(book.uri, book.finishedDate) { mutableStateOf(book.finishedDate) }
    var quotesDraft by remember(book.uri, book.quotes) { mutableStateOf(book.quotes) }
    var tagsDraft by remember(book.uri, book.tags) { mutableStateOf(book.tags.joinToString(", ")) }
    var loanToDraft by remember(book.uri, book.loanTo) { mutableStateOf(book.loanTo) }
    var loanDateDraft by remember(book.uri, book.loanDate) { mutableStateOf(book.loanDate) }
    var rereadDraft by remember(book.uri, book.rereadCount) { mutableIntStateOf(book.rereadCount) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(replaceCover)
    }
    var exportingEpub by remember(book.uri) { mutableStateOf(false) }
    val offlineCopy by produceState<OfflineBookCopy?>(null, book.uri, offlineAvailable,
        offlineDownloadMessage) {
        value = if (offlineAvailable) withContext(Dispatchers.IO) {
            offlineBookCopy(context, book)
        } else null
    }
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
        confirmButton = { LibraryTextButton(onClick = { confirmDelete = false; deleteBook() }) { Text("Borrar archivo") } },
        dismissButton = { LibraryTextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
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
        confirmButton = { LibraryTextButton(onClick = {
            editIdentity(titleDraft, authorDraft, sagaDraft, orderDraft)
            editIdentityDialog = false
        }) { Text("Guardar") } },
        dismissButton = { LibraryTextButton(onClick = { editIdentityDialog = false }) { Text("Cancelar") } }
    )
    if (editGenre) AlertDialog(
        onDismissRequest = { editGenre = false },
        title = { Text("Editar géneros") },
        text = { Column {
            Text("Escribe varios géneros separados por comas.", fontSize = 12.sp)
            OutlinedTextField(genreDraft, { genreDraft = it }, label = { Text("Géneros") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
        } },
        confirmButton = { LibraryTextButton(onClick = { updateGenre(genreDraft); editGenre = false }) { Text("Guardar") } },
        dismissButton = { LibraryTextButton(onClick = { editGenre = false }) { Text("Cancelar") } }
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
        confirmButton = { LibraryTextButton(onClick = { saveInfo(plotDraft, bioDraft); editInfo = false }) { Text("Guardar") } },
        dismissButton = { LibraryTextButton(onClick = { editInfo = false }) { Text("Cancelar") } }
    )
    val plot = book.spanishPlot.ifBlank {
        book.description.takeIf { book.language.lowercase().startsWith("es") ||
            book.language.lowercase().startsWith("spa") }.orEmpty()
    }
    val sagaBooks = remember(book.saga, libraryBooks) {
        if (book.saga.isBlank()) emptyList() else libraryBooks
            .filter { it.saga.equals(book.saga, ignoreCase = true) }
            .sortedWith(compareBy<Book> { sagaNumber(it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { displayTitle(it) })
    }
    val sagaNumbers = remember(sagaBooks) {
        sagaBooks.map { sagaNumber(it) }
            .filter { it.isFinite() && it >= 1 && it <= 100 && it % 1.0 == 0.0 }
            .map { it.toInt() }.toSet()
    }
    val sagaMissing = remember(sagaNumbers) {
        val last = sagaNumbers.maxOrNull() ?: 0
        if (last > 1) (1..last).filterNot(sagaNumbers::contains) else emptyList()
    }
    val ownedSagaOrders = remember(sagaBooks) {
        sagaBooks.map { it.sagaOrder.trim().replace(',', '.') }
            .filter(String::isNotBlank).toSet()
    }
    val ownedSagaTitles = remember(sagaBooks) {
        sagaBooks.map { displayTitle(it).lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "") }.toSet()
    }
    val missingCatalogVolumes = remember(sagaCatalog, ownedSagaOrders, ownedSagaTitles) {
        sagaCatalog.filter { volume ->
            val titleKey = volume.title.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
            volume.order !in ownedSagaOrders && titleKey !in ownedSagaTitles
        }
    }
    Box(Modifier.fillMaxSize().background(Parchment)) {
    LibraryWallpaper(wallpaperMode, book)
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            title = { Text("Mi Biblioteca", fontFamily = FontFamily.Serif, color = Paper,
                style = MaterialTheme.typography.titleLarge.copy(shadow = WallpaperTextShadow)) },
            navigationIcon = { IconButton(onClick = back) { AppIcon("Volver", "Volver", tint = Paper) } },
            actions = { IconButton(onClick = toggleFavorite) { AppIcon(if (book.favorite) "Favorito" else "Favoritos", "Favorito", tint = Paper) } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0x85211711),
                scrolledContainerColor = Color(0xA6211711))
        )
    }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (detailMessage ?: message)?.let { notice -> item {
                WallpaperLabel(notice, fontSize = 12.sp)
            } }
            if (needsReview) item(key = "review-details") {
                Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Datos encontrados: revisa que correspondan a tu libro.",
                            modifier = Modifier.weight(1f), color = Mahogany, fontSize = 12.sp)
                        LibraryTextButton(onClick = confirmDetails) { Text("Revisado") }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.graphicsLayer {
                        val p = opening.value
                        alpha = 0.88f + 0.12f * p
                        scaleX = 0.88f + 0.12f * p
                        scaleY = 0.88f + 0.12f * p
                        rotationY = -12f * (1f - p)
                        translationY = (1f - p) * 24.dp.toPx()
                        cameraDistance = 18.dp.toPx()
                    }) {
                        FlatCover(book, 165.dp, 240.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.38f * opening.value)
                        .height(3.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        .background(Brass))
                    if (book.cover == null && coverSearching)
                        WallpaperLabel("Buscando portada…", fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(displayTitle(book), fontSize = 18.sp, lineHeight = 22.sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = opening.value
                            translationY = (1f - opening.value) * 22.dp.toPx()
                        },
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
                    Spacer(Modifier.height(4.dp))
                    WallpaperLabel(displayAuthor(book), fontSize = 14.sp)
                    if (book.saga.isNotBlank()) WallpaperLabel("Saga: ${book.saga}" +
                        book.sagaOrder.takeIf { it.isNotBlank() }?.let { " · nº $it" }.orEmpty(),
                        fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryActionButton(onClick = showAuthorBooks,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(displayAuthor(book), maxLines = 2, textAlign = TextAlign.Center,
                                fontSize = 12.sp)
                        }
                        if (book.saga.isNotBlank()) LibraryActionButton(onClick = showSagaBooks,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(book.saga, maxLines = 2, textAlign = TextAlign.Center,
                                fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    openBook()
                }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    ActionLabel("Leer este libro", "Leer")
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryActionButton(onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            downloadForOffline()
                        } else {
                            val safeName = displayTitle(book).replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(90)
                            val extension = (if (book.uri.scheme == "file") book.uri.lastPathSegment
                                else androidx.documentfile.provider.DocumentFile.fromSingleUri(context, book.uri)?.name)
                                ?.substringAfterLast('.', "epub")?.lowercase().orEmpty()
                            exportPicker.launch((safeName.ifBlank { "Libro" }) + "." +
                                extension.takeIf { it in setOf("epub", "pdf", "mobi", "azw", "azw3", "txt", "html", "htm", "rtf", "docx", "md") }.orEmpty().ifBlank { "epub" })
                        }
                    }, modifier = Modifier.weight(1f).height(56.dp), enabled = !exportingEpub && !offlineDownloading) {
                        ActionLabel(if (exportingEpub || offlineDownloading) "Descargando…"
                            else if (offlineAvailable) "Actualizar copia" else "Sin conexión",
                            "Descargar", 12.sp)
                    }
                    LibraryActionButton(onClick = {
                        titleDraft = displayTitle(book); authorDraft = displayAuthor(book)
                        sagaDraft = book.saga; orderDraft = book.sagaOrder
                        editIdentityDialog = true
                    }, modifier = Modifier.weight(1f).height(56.dp)) {
                        ActionLabel("Editar datos", "Editar", 12.sp)
                    }
                }
                if (offlineDownloading) {
                    LinearProgressIndicator(progress = { offlineDownloadPercent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Teal)
                    WallpaperLabel("Descargando en el teléfono · $offlineDownloadPercent %",
                        fontSize = 12.sp)
                } else if (offlineDownloadMessage != null) {
                    WallpaperLabel(offlineDownloadMessage, fontSize = 12.sp)
                }
                if (offlineAvailable) {
                    Surface(Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        color = Teal.copy(alpha = 0.12f)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OfflineBadge()
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Disponible sin conexión", fontWeight = FontWeight.SemiBold,
                                    color = Mahogany)
                                Text(listOfNotNull(
                                    offlineCopy?.displayName?.takeIf(String::isNotBlank),
                                    offlineCopy?.sizeBytes?.takeIf { it >= 0L }?.let(::readableSize)
                                ).joinToString(" · ").ifBlank { "Guardado en Descargas" },
                                    fontSize = 11.sp, color = Mahogany.copy(alpha = 0.75f), maxLines = 2)
                            }
                            TextButton(onClick = removeOfflineCopy, enabled = !offlineDownloading) {
                                Text("Borrar copia")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { moreActionsExpanded = !moreActionsExpanded },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mahogany.copy(alpha = .62f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Paper.copy(alpha = .94f),
                        contentColor = Mahogany)) {
                    ActionLabel(if (moreActionsExpanded) "Menos opciones" else "Más opciones", "Más", 12.sp)
                }
                AnimatedVisibility(visible = moreActionsExpanded,
                    enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
                    Column(Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LibraryActionButton(onClick = recoverCover, enabled = !coverSearching,
                                modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                                ActionLabel(if (coverSearching) "Recuperando…" else "Recuperar portada", "Buscar", 11.sp)
                            }
                            LibraryActionButton(onClick = { coverPicker.launch("image/*") },
                                modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                                ActionLabel("Cambiar portada", "Portada", 11.sp)
                            }
                        }
                        LibraryTextButton(onClick = { searchCoverImages(context, book) }) {
                            ActionLabel("Buscar imágenes de portada", "Buscar", 12.sp)
                        }
                    }
                }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    LibraryActionButton(onClick = { sectionMenu = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
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
                LibraryActionButton(onClick = toggleWant,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Text(if (book.wantToRead) "✓ Quiero leer" else "+ Quiero leer más adelante")
                }
                }
            }
            item {
                WallpaperLabel("Estado", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadingStatus.entries.forEach { status ->
                        CatalogChip(status.label, book.status == status) { setStatus(status) }
                    }
                }
            }
            item {
                Text("Datos del libro", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    color = Color.White, modifier = Modifier.padding(top = 8.dp),
                    style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
            }
            item {
                BookPanel("Saga y orden de lectura", book.uri.toString(), initiallyExpanded = true) {
                    if (book.saga.isBlank()) {
                        Text("No hay una saga asignada a este libro.",
                            fontSize = 12.sp, color = Mahogany)
                        Text("Puedes buscarla automáticamente o añadirla con «Editar datos».",
                            fontSize = 12.sp, color = Mahogany.copy(alpha = .76f))
                    } else {
                        Text(book.saga, fontWeight = FontWeight.Bold, color = Mahogany)
                        Text(
                            if (book.sagaOrder.isNotBlank()) "Este libro es el número ${book.sagaOrder}."
                            else "Este libro no tiene número de saga asignado.",
                            fontSize = 12.sp, color = Mahogany
                        )
                        Spacer(Modifier.height(8.dp))
                        if (sagaCatalog.isNotEmpty()) {
                            Text(if (missingCatalogVolumes.isEmpty())
                                "Saga completa según el catálogo consultado."
                            else "Te faltan ${missingCatalogVolumes.size} volúmenes:",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (missingCatalogVolumes.isEmpty()) Teal else Color(0xFF9A4C32))
                            sagaCatalog.forEach { volume ->
                                val titleKey = volume.title.lowercase()
                                    .replace(Regex("[^\\p{L}\\p{N}]"), "")
                                val owned = volume.order in ownedSagaOrders || titleKey in ownedSagaTitles
                                Text((if (owned) "✓ " else "○ ") +
                                    volume.order.takeIf(String::isNotBlank)?.let { "$it. " }.orEmpty() +
                                    volume.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (owned) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (owned) Teal else Color(0xFF9A4C32))
                            }
                        } else {
                            sagaBooks.forEach { sagaBook ->
                                val order = sagaBook.sagaOrder.ifBlank { "?" }
                                Text("$order. ${displayTitle(sagaBook)}" +
                                    if (sagaBook.uri == book.uri) "  · Este libro" else "",
                                    fontSize = 12.sp,
                                    fontWeight = if (sagaBook.uri == book.uri) FontWeight.Bold else FontWeight.Normal,
                                    color = Mahogany)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(if (sagaMissing.isEmpty())
                                "Pulsa «Comprobar saga completa» para buscar volúmenes posteriores."
                            else "Faltan entre los números conocidos: ${sagaMissing.joinToString(", ")}.",
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = if (sagaMissing.isEmpty()) Mahogany else Color(0xFF9A4C32))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LibraryActionButton(onClick = searchSaga, enabled = !sagaSearching,
                        modifier = Modifier.fillMaxWidth()) {
                        ActionLabel(if (sagaSearching) "Buscando saga…" else
                            "Buscar saga y número", "Buscar", 12.sp)
                    }
                    if (book.saga.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        LibraryActionButton(onClick = searchSagaCatalog,
                            enabled = !sagaCatalogSearching,
                            modifier = Modifier.fillMaxWidth()) {
                            ActionLabel(if (sagaCatalogSearching) "Comprobando catálogo…"
                                else "Comprobar saga completa", "Sagas", 12.sp)
                        }
                    }
                }
            }
            item {
                BookPanel("Géneros", book.uri.toString(), initiallyExpanded = true) {
                    Text(normalizeBookGenres(book.genre).joinToString(" · ")
                        .ifBlank { "Sin clasificar" }, fontSize = 13.sp, color = Mahogany)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryTextButton(onClick = searchGenre, enabled = !genreSearching) {
                            ActionLabel(if (genreSearching) "Consultando catálogos…" else "Buscar en Internet",
                                "Buscar", 12.sp)
                        }
                        LibraryTextButton(onClick = { genreDraft = book.genre; editGenre = true }) {
                            ActionLabel("Editar", "Editar", 12.sp)
                        }
                    }
                    Text("Fuentes de catálogo. Comprueba las etiquetas encontradas.",
                        fontSize = 11.sp, color = Mahogany)
                }
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
                    val authorBooks = libraryBooks.filter {
                        displayAuthor(it).equals(displayAuthor(book), ignoreCase = true)
                    }.sortedBy(::displayTitle)
                    Spacer(Modifier.height(8.dp))
                    Text("En tu biblioteca: ${authorBooks.size} libros", fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = Mahogany)
                    authorBooks.take(12).forEach { other ->
                        Text("• ${displayTitle(other)}" + other.date.takeIf(String::isNotBlank)
                            ?.let { " ($it)" }.orEmpty(), fontSize = 12.sp, color = Mahogany)
                    }
                    LibraryTextButton(onClick = {
                        searchInGoogleApp(context, displayAuthor(book) + " bibliografía completa")
                    }) { Text("Consultar bibliografía completa") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryActionButton(onClick = enrich, enabled = !infoLoading,
                        modifier = Modifier.weight(1f)) {
                        ActionLabel(if (infoLoading) "Buscando…" else "Reintentar búsqueda", "Buscar", 12.sp)
                    }
                    LibraryActionButton(onClick = {
                        plotDraft = book.spanishPlot.ifBlank { plot }
                        bioDraft = book.authorBio
                        editInfo = true
                    }, modifier = Modifier.weight(1f)) { ActionLabel("Editar texto", "Editar") }
                }
            }
            item {
                BookPanel("Organización personal", book.uri.toString()) {
                    OutlinedTextField(tagsDraft, { tagsDraft = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Etiquetas separadas por comas") },
                        placeholder = { Text("Releer, edición especial, regalo…") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(loanToDraft, { loanToDraft = it }, modifier = Modifier.weight(1f),
                            label = { Text("Prestado a") })
                        OutlinedTextField(loanDateDraft, { loanDateDraft = it }, modifier = Modifier.weight(1f),
                            label = { Text("Fecha") }, placeholder = { Text("AAAA-MM-DD") })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Relecturas: $rereadDraft", modifier = Modifier.weight(1f), color = Mahogany)
                        IconButton(onClick = { rereadDraft = (rereadDraft - 1).coerceAtLeast(0) }) {
                            Icon(Icons.Outlined.Remove, "Restar relectura", tint = Mahogany)
                        }
                        IconButton(onClick = { rereadDraft++ }) {
                            Icon(Icons.Outlined.Add, "Añadir relectura", tint = Mahogany)
                        }
                    }
                    LibraryActionButton(onClick = {
                        saveBookManagement(tagsDraft, loanToDraft, loanDateDraft, rereadDraft)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar organización") }
                }
            }
            item {
                BookPanel("Mi diario de lectura", book.uri.toString(), initiallyExpanded = true) {
                    Text("Valoración", fontSize = 12.sp, color = Mahogany)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { ratingDraft = if (ratingDraft == star) 0 else star }) {
                                Icon(if (star <= ratingDraft) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "$star estrellas", tint = Brass)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(startedDraft, { startedDraft = it }, modifier = Modifier.weight(1f),
                            label = { Text("Inicio") }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true)
                        OutlinedTextField(finishedDraft, { finishedDraft = it }, modifier = Modifier.weight(1f),
                            label = { Text("Fin") }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true)
                    }
                    OutlinedTextField(quotesDraft, { quotesDraft = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Citas favoritas") }, minLines = 3)
                    LibraryActionButton(onClick = {
                        saveReadingRecord(ratingDraft, startedDraft, finishedDraft, quotesDraft)
                    }, modifier = Modifier.fillMaxWidth()) { ActionLabel("Guardar diario", "Guardar") }
                }
            }
            item {
                BookPanel("Mis observaciones", book.uri.toString()) {
                    OutlinedTextField(notesDraft, { notesDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Escribe tus notas sobre este libro") }, minLines = 3)
                    LibraryActionButton(onClick = { saveNotes(notesDraft) }) {
                        ActionLabel("Guardar observaciones", "Guardar")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openGoodreads(context, book) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Paper.copy(alpha = .94f),
                            contentColor = Mahogany)) {
                        ActionLabel("Abrir reseña del libro", "Reseña")
                    }
                    OutlinedButton(onClick = {
                        searchInGoogleApp(context, displayTitle(book) + " " + displayAuthor(book))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Paper.copy(alpha = .94f),
                            contentColor = Mahogany)) {
                        ActionLabel("Buscar información del libro", "Búsqueda IA")
                    }
                    OutlinedButton(onClick = {
                        val query = Uri.encode("title:(\"${displayTitle(book)}\") AND mediatype:texts")
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://archive.org/advancedsearch.php?q=$query")))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Paper.copy(alpha = .94f), contentColor = Mahogany)) {
                        ActionLabel("Buscar préstamo o EPUB legal", "Buscar", 12.sp)
                    }
                    OutlinedButton(onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB54A42)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Paper.copy(alpha = .94f),
                            contentColor = Color(0xFF9D332D))) {
                        ActionLabel("Borrar este archivo de Drive", "Borrar")
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun BookPanel(title: String, bookKey: String, initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(bookKey, title) { mutableStateOf(initiallyExpanded) }
    Card(colors = CardDefaults.cardColors(containerColor = Paper),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = Mahogany)
                Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Contraer" else "Ampliar", tint = Mahogany)
            }
            AnimatedVisibility(visible = expanded,
                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(110))) {
                Column {
                Spacer(Modifier.height(6.dp))
                content()
                }
            }
        }
    }
}

@Composable private fun Info(label:String,value:String){if(value.isNotBlank()){Text(label,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(value,fontSize=12.sp,lineHeight=17.sp);Spacer(Modifier.height(4.dp))}}
