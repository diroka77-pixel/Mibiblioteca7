Warning: truncated output (original token count: 98605)
Total output lines: 6898

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
import androidx.compose.ui.text.style.TextOverflow
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

private data class PublicBookResult(
    val title: String, val author: String, val year: String,
    val isbn: String, val coverUrl: String, val sourceUrl: String,
    val summary: String = "", val authorKey: String = "",
    val publishDates: String = ""
)

private data class PublicBookDetails(val summary: String, val authorBio: String)

private fun loadPublicBookDetails(result: PublicBookResult): PublicBookDetails {
    var summary = result.summary
    var bio = ""
    runCatching {
        val key = result.sourceUrl.substringAfter("/works/", "").substringBefore("?")
        if (key.isNotBlank()) {
            val work = getJson("https://openlibrary.org/works/$key.json")
            val description = work.opt("description")
            summary = when (description) {
                is org.json.JSONObject -> description.optString("value")
                else -> description?.toString().orEmpty()
            }.ifBlank { summary }
        }
        if (result.authorKey.isNotBlank()) {
            val author = getJson("https://openlibrary.org/authors/${result.authorKey}.json")
            val description = author.opt("bio")
            bio = when (description) {
                is org.json.JSONObject -> description.optString("value")
                else -> description?.toString().orEmpty()
            }
        }
    }
    return PublicBookDetails(summary.trim(), bio.trim())
}

private fun searchPublicBooks(titleQuery: String, authorQuery: String = ""): List<PublicBookResult> {
    if (titleQuery.isBlank() && authorQuery.isBlank()) return emptyList()
    val encoded = java.net.URLEncoder.encode(titleQuery.trim(), "UTF-8")
    val titlePart = if (titleQuery.isBlank()) "" else "&title=$encoded"
    val authorPart = if (authorQuery.isBlank()) "" else "&author=${java.net.URLEncoder.encode(authorQuery.trim(), "UTF-8")}"
    val docs = getJson("https://openlibrary.org/search.json?mode=everything&limit=10$titlePart$authorPart")
        .optJSONArray("docs") ?: return emptyList()
    return (0 until docs.length()).mapNotNull { i ->
        val row = docs.optJSONObject(i) ?: return@mapNotNull null
        val key = row.optString("key").removePrefix("/works/")
        val title = row.optString("title").trim()
        if (title.isBlank()) return@mapNotNull null
        val authors = row.optJSONArray("author_name")
        val author = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            .filter(String::isNotBlank).joinToString(", ")
        val isbns = row.optJSONArray("isbn")
        val isbn = isbns?.optString(0).orEmpty()
        val coverId = row.optInt("cover_i", 0)
        val summary = row.optJSONArray("first_sentence")?.optString(0).orEmpty()
            .ifBlank { row.optJSONArray("subject")?.let { subjects ->
                (0 until minOf(subjects.length(), 5)).joinToString(", ") { subjects.optString(it) }
            }.orEmpty() }
        val authorKeys = row.optJSONArray("author_key")
        val authorKey = authorKeys?.optString(0).orEmpty()
        val dates = row.optJSONArray("publish_date")?.let { values ->
            (0 until minOf(values.length(), 5)).joinToString(" · ") { values.optString(it) }
        }.orEmpty()
        PublicBookResult(title, author, row.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString().orEmpty(),
            isbn, if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg" else "",
            if (key.isNotBlank()) "https://openlibrary.org/works/$key" else "https://openlibrary.org/search?q=$encoded", summary, authorKey, dates)
    }.distinctBy { (it.title + "|" + it.author).lowercase() }
}

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

@Composable
private fun rememberPublicCover(url: String): State<Bitmap?> {
    return produceState<Bitmap?>(initialValue = null, url) {
        if (url.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 7000
                    setRequestProperty("User-Agent", "MiBiblioteca/1.0 Android")
                }
                try {
                    val bytes = connection.inputStream.use { it.readNBytes(2_500_001) }
                    if (bytes.isEmpty() || bytes.size > 2_500_000) null
                    else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
}

@Composable
private fun PublicBookCard(result: PublicBookResult, onOpen: () -> Unit, onWish: () -> Unit) {
    val cover by rememberPublicCover(result.coverUrl)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = Paper.copy(alpha = .97f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Brass.copy(alpha = .35f)),
        shadowElevation = 2.dp
    ) {
        Row(Modifier.padding(12.dp).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(78.dp).height(112.dp), contentAlignment = Alignment.Center) {
                if (cover != null) Image(cover!!.asImageBitmap(), "Portada de ${result.title}",
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else {
                    AppIcon("Libros", tint = Mahogany.copy(alpha = .55f), size = 38.dp)
                    Text("Sin portada", fontSize = 9.sp, color = Mahogany.copy(alpha = .65f))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    color = Ink, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (result.author.isNotBlank()) Text(result.author, color = Mahogany,
                    fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (result.year.isNotBlank()) Text("Publicado en ${result.year}",
                    color = Mahogany.copy(alpha = .72f), fontSize = 12.sp)
                if (result.publishDates.isNotBlank()) Text("Ediciones: ${result.publishDates}",
                    color = Mahogany.copy(alpha = .62f), fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text("Toca para ver argumento y autor", color = Mahogany.copy(alpha = .7f), fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onWish, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    AppIcon("Favoritos", tint = Color.White, size = 17.dp)
                    Spacer(Modifier.width(5.dp)); Text("Añadir a deseos", fontSize = 12.sp)
                }
            }
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
    fun updateAnnualReadingGoal(goal: Int) {
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
        wishList.forEach { (title, url) -> arr.put(JSONObject().put("title", title).put("url", url))…78605 tokens truncated…searchSaga: () -> Unit,
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
                                extension.takeIf { it in setOf("epub", "pdf", "mobi", "azw", "azw3", "fb2", "txt", "html", "htm", "rtf", "docx", "md") }.orEmpty().ifBlank { "epub" })
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
                        color = Paper) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OfflineBadge()
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Disponible sin conexión", fontWeight = FontWeight.SemiBold,
                                    color = Ink)
                                Text(listOfNotNull(
                                    offlineCopy?.displayName?.takeIf(String::isNotBlank),
                                    offlineCopy?.sizeBytes?.takeIf { it >= 0L }?.let(::readableSize)
                                ).joinToString(" · ").ifBlank { "Guardado en Descargas" },
                                    fontSize = 11.sp, color = Ink.copy(alpha = 0.78f), maxLines = 2)
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
