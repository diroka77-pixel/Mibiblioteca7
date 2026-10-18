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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.res.painterResource
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.ui.graphics.toArgb
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

private data class BackupRestoreResult(val matched: Int, val pending: String?)

private fun safeSavedArray(raw: String?): org.json.JSONArray = try {
    org.json.JSONArray(raw ?: "[]")
} catch (_: Exception) { org.json.JSONArray() }

internal fun bookSections(book: Book): List<String> =
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

internal fun inferredSaga(book: Book): Pair<String, String> {
    if (book.saga.isNotBlank()) return book.saga.trim() to book.sagaOrder.trim()
    val sources = listOf(displayTitle(book), Uri.decode(book.uri.lastPathSegment.orEmpty()))
    sources.forEach { source -> extractSagaFromText(source)?.let { return it } }
    return "" to book.sagaOrder.trim()
}

internal fun extractSagaFromText(raw: String): Pair<String, String>? {
    val source = raw.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    if (source.isBlank()) return null
    val number = "(\\d+(?:[.,]\\d+)?)"
    val patterns = listOf(
        Regex("(?i)\\[([^]]{2,100}?)\\s*(?:#|№|n[ºo.]?|vol(?:umen)?[.]?|tomo|libro)\\s*$number\\]"),
        Regex("(?i)\\(([^()]{2,100}?)\\s*(?:#|№|n[ºo.]?|vol(?:umen)?[.]?|tomo|libro)?\\s*$number\\)"),
        Regex("(?i)^(.{2,100}?)\\s*(?:-|–|—|:)\\s*(?:libro|vol(?:umen)?|tomo|parte|n[ºo.]?)\\s*$number(?:\\s*[-–—:].*)?$"),
        Regex("(?i)^(.{2,100}?)\\s+(?:#|№|n[ºo.]?|vol(?:umen)?[.]?|tomo|libro)\\s*$number(?:\\s*[-–—:].*)?$"),
        Regex("(?i)^(.{2,100}?)\\s*[-–—:]\\s*(?:libro|vol(?:umen)?|tomo)?\\s*$number\\s*[-–—:]\\s*.+$"),
        Regex("(?i)^(.{2,100}?)\\s*,?\\s*$number\\s*(?:de\\s+la\\s+serie|de\\s+la\\s+saga|series?)$")
    )
    for (pattern in patterns) {
        val match = pattern.find(source) ?: continue
        val saga = match.groupValues.getOrNull(1).orEmpty()
            .trim(' ', '-', '–', '—', ':', ',', ';')
            .replace(Regex("(?i)\\b(?:saga|serie|coleccion|collection)\\b"), "")
            .trim()
        val rawOrder = match.groupValues.lastOrNull().orEmpty().replace(',', '.')
        val numericOrder = rawOrder.toDoubleOrNull()
        val order = if (numericOrder != null && numericOrder % 1.0 == 0.0)
            numericOrder.toInt().toString() else rawOrder.trimStart('0').ifBlank { "0" }
        if (saga.length >= 2 && numericOrder?.let { it !in 1900.0..2100.0 } == true) {
            return saga to order
        }
    }
    return null
}

private fun sagaGroupKey(book: Book): String = normalizeSagaName(inferredSaga(book).first)

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

private val publicImageCache = object : LruCache<String, Bitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int =
        (value.byteCount / 1024).coerceAtLeast(1)
}

@Composable
private fun rememberPublicCover(url: String): State<Bitmap?> {
    val cached = remember(url) {
        if (url.isBlank()) null else synchronized(publicImageCache) { publicImageCache.get(url) }
    }
    return produceState<Bitmap?>(initialValue = cached, url) {
        if (url.isBlank() || cached != null) return@produceState
        value = withContext(Dispatchers.IO) {
            val candidates = listOf(
                url.replace("http://", "https://"),
                url.replace("http://", "https://").replace("/isbn/", "/b/isbn/"),
                url.replace("http://", "https://").replace("-L.jpg", "-M.jpg"),
                url + if (url.contains("?")) "&default=false" else "?default=false"
            ).filter(String::isNotBlank).distinct()
            candidates.firstNotNullOfOrNull { candidate ->
                runCatching {
                    val connection = (URL(candidate).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6500
                        readTimeout = 9000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "MiBiblioteca/1.0 Android")
                        setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                    }
                    try {
                        if (connection.responseCode !in 200..299) return@runCatching null
                        val bytes = connection.inputStream.use { it.readNBytes(6_000_001) }
                        if (bytes.isEmpty() || bytes.size > 6_000_000) return@runCatching null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        if (bounds.outWidth < 80 || bounds.outHeight < 80) return@runCatching null
                        var sample = 1
                        while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                            BitmapFactory.Options().apply { inSampleSize = sample })
                    } finally { connection.disconnect() }
                }.getOrNull()
            }?.also { bitmap ->
                synchronized(publicImageCache) { publicImageCache.put(url, bitmap) }
            }
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

@Composable
private fun PublicBookDetailPage(result: PublicBookResult, details: PublicBookDetails?, wishListed: Boolean, onBack: () -> Unit, onWish: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        TextButton(onClick = onBack) { Text("← Volver a resultados", color = Brass) }
        Surface(Modifier.fillMaxWidth(), color = Paper.copy(alpha = .98f), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp)) {
                Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.Center) {
                    val detailCover by rememberPublicCover(details?.coverUrl?.ifBlank { result.coverUrl } ?: result.coverUrl)
                    if (detailCover != null) Image(detailCover!!.asImageBitmap(), "Portada de ${result.title}", Modifier.fillMaxHeight().width(190.dp), contentScale = ContentScale.Fit)
                    else if (details == null) CircularProgressIndicator(color = Brass)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon("Libros", tint = Mahogany.copy(alpha = .5f), size = 64.dp)
                        Text("Portada no disponible", color = Mahogany.copy(alpha = .7f), fontSize = 12.sp)
                    }
                }
                Text(result.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Ink)
                Text(result.author.ifBlank { "Autor no disponible" }, fontSize = 18.sp, color = Mahogany)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onWish, colors = ButtonDefaults.buttonColors(containerColor = Teal), modifier = Modifier.fillMaxWidth()) { Text(if (wishListed) "♥  Quitar de deseos" else "♡  Añadir a deseos") }
                Spacer(Modifier.height(18.dp))
                Text("Descripción", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Mahogany)
                Text(details?.summary?.ifBlank { "No hay descripción disponible en las fuentes públicas." } ?: "Cargando información pública…", color = Ink, fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(18.dp))
                Text("Sobre el autor", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Mahogany)
                val authorPicture by rememberPublicCover(details?.authorImageUrl.orEmpty())
                if (authorPicture != null) Image(authorPicture!!.asImageBitmap(), "Foto de ${result.author}", Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
                Text(details?.authorBio?.ifBlank { "No hay biografía disponible en las fuentes públicas." } ?: "Cargando biografía…", color = Ink, fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(18.dp))
                Text("Detalles del libro", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Mahogany)
                val rows = listOf("Publicación" to (details?.published ?: result.year), "Páginas" to (details?.pages ?: ""), "Idioma" to (details?.language ?: ""), "Editorial" to (details?.publisher ?: ""), "ISBN" to result.isbn).filter { it.second.isNotBlank() }
                rows.forEach { (label, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("$label: ", fontWeight = FontWeight.Bold, color = Ink); Text(value, color = Ink) } }
                Spacer(Modifier.height(12.dp))
                Text("Metadatos públicos: ${details?.source ?: "Open Library"}", fontSize = 12.sp, color = Mahogany.copy(alpha = .7f))
                if (result.confidence > 0) Text("Coincidencia: ${result.confidence} % · ${result.source}",
                    fontSize = 11.sp, color = Mahogany.copy(alpha = .62f))
            }
        }
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
    fun driveDiagnosticText(): String = prefs.getString(
        "drive_diagnostic", "Todavía no hay un diagnóstico disponible."
    ).orEmpty()
    var syncSummary by mutableStateOf(""); private set
    var newBooksNotice by mutableStateOf<String?>(null); private set
    fun clearNewBooksNotice() { newBooksNotice = null }
    var launchNews by mutableStateOf<List<LaunchNews>>(emptyList()); private set
    var newsRefreshing by mutableStateOf(false); private set
    var hiddenNewsSources by mutableStateOf<Set<String>>(emptySet()); private set
    var hiddenNewsUrls by mutableStateOf<Set<String>>(emptySet()); private set
    var newsLastChecked by mutableStateOf(0L); private set
    var folderName by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var activityLog by mutableStateOf<List<String>>(emptyList()); private set

    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val scopedViewModes = mutableStateMapOf<String, String>()
    var annualReadingGoal by mutableIntStateOf(prefs.getInt("annual_reading_goal", 12))
        private set
    fun updateAnnualReadingGoal(goal: Int) {
        annualReadingGoal = goal.coerceIn(1, 365)
        prefs.edit().putInt("annual_reading_goal", annualReadingGoal).apply()
    }
    private val bookStore = CatalogStoreV2(app)
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
            normalizeCachedIdentities()
            // Las portadas se buscan manualmente desde Ajustes. Así abrir la app
            // no vuelve a recorrer toda la biblioteca ni consume red.
        }
        viewModelScope.launch {
            initialRestoreJob?.join()
            if (prefs.getString(folderKey, null) != null) {
                delay(350)
                sync()
            }
        }
        viewMode = prefs.getString("view_mode", "Galería") ?: "Galería"
        (listOf("general") + sections).forEach { scope ->
            scopedViewModes[scope] = prefs.getString("view_mode_$scope", viewMode) ?: viewMode
        }
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
                    loadStoredCover(coverFile(getApplication(), uri)),
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
        // Agrupa cambios consecutivos (favorito, estado, edición, clasificación)
        // en una sola escritura del catálogo y una sola copia en la nube.
        catalogSaveJob?.cancel()
        catalogSaveJob = viewModelScope.launch {
            delay(260)
            try {
            withContext(Dispatchers.IO) {
                saveMutex.withLock {
                    if (revision != saveVersion) return@withLock
                    val array = booksToJsonArray(snapshot)
                    bookStore.applySnapshot(array)
                    // SharedPreferences conservaba otra copia completa y obligaba
                    // a serializar cientos de fichas con cada cambio. La base
                    // transaccional ya es la fuente local; se elimina la copia
                    // antigua después de migrarla correctamente.
                    prefs.edit().putBoolean("catalog_v2_ready", true)
                        .remove("books_cache").apply()
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

    fun removeGoodreadsWish(title: String, url: String) {
        wishList = wishList.filterNot { it.first.equals(title, true) && it.second == url }
        saveWishList()
    }

    private fun cloudSnapshot(): String {
        val booksJson = sanitizeBackupBooks(booksToJsonArray(books))
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
        val backup = JSONObject().put("backupVersion", 2)
            .put("updatedAt", prefs.getLong("local_revision", 0L))
            .put("sections", org.json.JSONArray(sections))
            .put("wishList", safeSavedArray(prefs.getString("wish_list", "[]")))
            .put("books", booksJson)
        backup.put("contentSha256", backupChecksum(backup))
        return backup.toString().also {
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
                if (detailMessage?.startsWith("Guardado en el teléfono;") == true) detailMessage = null
            } catch (e: Exception) {
                detailMessage = "Guardado en el teléfono; no se pudo guardar en Drive: ${e.localizedMessage}"
            }
        }
    }

    private suspend fun restoreCloud(tree: Uri) {
        val knownLocalCatalogSize = books.size
        val cloud = withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(getApplication(), tree) ?: return@withContext null
            fun readSnapshot(name: String): JSONObject? = root.findFile(name)?.let { file ->
                getApplication<Application>().contentResolver.openInputStream(file.uri)?.use {
                    parseBackup(it.readLimited(FileLimits.BACKUP_BYTES).toString(Charsets.UTF_8))
                        .also(::validateBackupImages)
                }
            }
            var currentFailure: Exception? = null
            val current = try { readSnapshot(cloudName) }
                catch (failure: Exception) { currentFailure = failure; null }
            val previous = try { readSnapshot(cloudName + ".previous") }
                catch (_: Exception) { null }
            when {
                current == null -> previous ?: currentFailure?.let { throw it }
                previous == null -> current
                shouldPreferPreviousDriveSnapshot(
                    localCatalogSize = knownLocalCatalogSize,
                    currentSnapshotSize = current.optJSONArray("books")?.length() ?: 0,
                    previousSnapshotSize = previous.optJSONArray("books")?.length() ?: 0
                ) -> previous
                else -> current
            }
        } ?: return

        // La carpeta ya se ha escaneado antes de llegar aquí. El respaldo nunca
        // crea ni elimina fichas: solo recupera datos personales de libros que
        // existen realmente en la carpeta seleccionada.
        withContext(Dispatchers.IO) { validateBackupImages(cloud) }
        val result = applyBackup(cloud)
        prefs.edit()
            .putString("pending_backup", result.pending)
            .putLong("last_backup_restore", System.currentTimeMillis())
            .putInt("last_backup_matches", result.matched)
            .apply()
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

    fun renameSection(name: String, replacement: String) {
        val clean = replacement.trim()
        if (clean.isBlank() || sections.any { it != name && it.equals(clean, true) }) return
        sections = sections.map { if (it == name) clean else it }
        books = books.map { book ->
            if (name !in bookSections(book)) book else {
                val updated = bookSections(book).map { if (it == name) clean else it }
                book.copy(section = updated.firstOrNull().orEmpty(), sections = updated)
            }
        }
        if (selectedSection == name) selectedSection = clean
        prefs.edit().putString("sections", org.json.JSONArray(sections).toString()).apply()
        saveBooks()
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
        if (prefs.getBoolean("manual_identity_" + uri, false)) {
            detailMessage = "Se conserva la saga editada manualmente. Puedes cambiarla desde Editar datos."
            return
        }
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
            val targets = books.filter { book ->
                !prefs.getBoolean("manual_identity_" + book.uri, false) &&
                    (book.saga.isBlank() || book.sagaOrder.isBlank() || inferredSaga(book).first.isNotBlank())
            }
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
                            saga = current.saga.ifBlank { found.first.trim() },
                            sagaOrder = current.sagaOrder.ifBlank { found.second.trim() }) else current }
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
                    detailMessage = "Saga comprobada: ${found.size} volúmenes encontrados."
                }
            } catch (e: Exception) {
                detailMessage = "No se pudo comprobar la saga: ${e.localizedMessage ?: "sin conexión"}"
            } finally { sagaCatalogSearches = sagaCatalogSearches - key }
        }
    }

    fun sagaCatalog(saga: String): List<SagaVolume> = sagaCatalogs[normalizeSagaName(saga)].orEmpty()
    fun sagaCatalogLoading(saga: String): Boolean = normalizeSagaName(saga) in sagaCatalogSearches

    fun updateGenre(uri: Uri, genre: String) {
        books = books.map { if (it.uri == uri)
            it.copy(genre = normalizeBookGenres(genre).joinToString(", ")) else it }
        saveBooks()
    }

    fun searchGenre(uri: Uri) {
        val book = books.firstOrNull { it.uri == uri } ?: return
        if (uri in genreSearches) return
        genreSearches = genreSearches + uri
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchGenresOnline(book) }
                if (result.genres.isEmpty()) detailMessage = "No encontré géneros fiables para «${displayTitle(book)}». Puedes editarlos manualmente."
                else {
                    val value = result.genres.joinToString(" · ")
                    updateGenre(uri, value)
                    prefs.edit().putString("genre_source_" + uri, result.source)
                        .putLong("genre_checked_" + uri, System.currentTimeMillis()).apply()
                    detailMessage = "Géneros actualizados: $value"
                }
            } catch (e: Exception) {
                detailMessage = "No se pudieron consultar los catálogos: ${e.localizedMessage ?: "comprueba la conexión"}"
            } finally { genreSearches = genreSearches - uri }
        }
    }

    var genreSearches by mutableStateOf<Set<Uri>>(emptySet())

    fun saveNotes(uri: Uri, notes: String) {
        books = books.map { if (it.uri == uri) it.copy(notes = notes) else it }
        saveBooks()
    }

    fun saveReadingRecord(uri: Uri, rating: Int, started: String, finished: String, quotes: String) {
        books = books.map { if (it.uri == uri) it.copy(
            rating = rating.coerceIn(0, 5), startedDate = started.trim(),
            finishedDate = finished.trim(), quotes = quotes.trim()) else it }
        saveBooks()
        detailMessage = "Diario de lectura guardado."
        books.firstOrNull { it.uri == uri }?.let { addActivity("Diario actualizado · ${displayTitle(it)}") }
    }

    private fun addActivity(text: String) {
        val stamp = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "ES"))
            .format(java.util.Date())
        activityLog = (listOf("$stamp · $text") + activityLog).distinct().take(100)
        prefs.edit().putString("activity_log", org.json.JSONArray(activityLog).toString()).apply()
    }

    fun saveBookManagement(uri: Uri, tags: String, loanTo: String, loanDate: String,
        rereadCount: Int) {
        books = books.map { if (it.uri == uri) it.copy(
            tags = tags.split(',', ';').map(String::trim).filter(String::isNotBlank).distinct(),
            loanTo = loanTo.trim(), loanDate = loanDate.trim(), rereadCount = rereadCount.coerceAtLeast(0)
        ) else it }
        saveBooks()
        books.firstOrNull { it.uri == uri }?.let { addActivity("Organización actualizada · ${displayTitle(it)}") }
        detailMessage = "Etiquetas y préstamo guardados."
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
                    val source = getApplication<Application>().contentResolver.openInputStream(imageUri)
                        ?.use { it.readLimited(FileLimits.COVER_INPUT_BYTES) }
                        ?: throw IllegalArgumentException("No se pudo leer la imagen.")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
                    val sample = imageSampleSize(bounds.outWidth, bounds.outHeight, 1800, 2400)
                    val original = BitmapFactory.decodeByteArray(source, 0, source.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: throw IllegalArgumentException("Imagen no válida")
                    val scale = minOf(900f / original.width, 2400f / original.height, 1f)
                    val width = (original.width * scale).toInt().coerceAtLeast(1)
                    val height = (original.height * scale).toInt().coerceAtLeast(1)
                    val scaled = if (width == original.width && height == original.height) original
                        else Bitmap.createScaledBitmap(original, width, height, true)
                    try {
                        java.io.ByteArrayOutputStream().use { out ->
                            check(scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)) {
                                "No se pudo procesar la portada."
                            }
                            out.toByteArray()
                        }
                    } finally {
                        if (scaled !== original) scaled.recycle()
                        original.recycle()
                    }
                }
                withContext(Dispatchers.IO) {
                    val file = coverFile(getApplication(), uri)
                    file.writeSafely(bytes)
                    File(getApplication<Application>().filesDir, "manual-" + file.name).writeSafely(bytes)
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

    /** Normaliza títulos y metadatos locales al arrancar, sin tocar campos editados manualmente. */
    private fun normalizeCachedIdentities() {
        var changed = false
        books = books.map { book ->
            if (prefs.getBoolean("manual_identity_" + book.uri, false)) return@map book
            var updated = book
            val visible = displayTitle(book)
            if (book.customTitle.isBlank() && visible.isNotBlank() &&
                !visible.equals(book.title.trim(), ignoreCase = false) && visible.length >= 2) {
                updated = updated.copy(customTitle = visible)
            }
            if (book.customAuthor.isBlank() && unknownAuthor(book.author)) {
                val filename = Uri.decode(book.uri.lastPathSegment.orEmpty())
                val inferred = inferFilenameIdentity(filename)
                if (!unknownAuthor(inferred.author)) updated = updated.copy(customAuthor = inferred.author)
            }
            val localSaga = inferredSaga(updated)
            if (updated.saga.isBlank() && localSaga.first.isNotBlank())
                updated = updated.copy(saga = localSaga.first.trim(), sagaOrder = localSaga.second)
            if (updated != book) changed = true
            updated
        }
        if (changed) saveBooks()
    }

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

    fun classifyGenres(force: Boolean = false) {
        if (genreClassifying) return
        val targets = books.filter { force || it.genre.isBlank() }
            .filterNot { prefs.getBoolean("manual_genre_" + it.uri, false) }
        if (targets.isEmpty()) {
            message = if (books.any { it.genre.isNotBlank() }) "Todos los libros posibles ya están clasificados."
                else "No hay libros disponibles para clasificar."
            return
        }
        viewModelScope.launch {
            genreClassifying = true; genreProgress = 0; genreTotal = targets.size
            var changed = false
            try {
                for (target in targets) {
                    val current = books.firstOrNull { it.uri == target.uri } ?: continue
                    val result = withContext(Dispatchers.IO) { fetchGenresOnline(current) }
                    if (result.genres.isNotEmpty()) {
                        val value = result.genres.joinToString(" · ")
                        books = books.map { if (it.uri == current.uri) it.copy(genre = value) else it }
                        prefs.edit().putString("genre_source_" + current.uri, result.source)
                            .putLong("genre_checked_" + current.uri, System.currentTimeMillis()).apply()
                        changed = true
                    }
                    genreProgress++
                    if (genreProgress % 10 == 0 && changed) { saveBooks(); changed = false }
                    delay(180)
                }
                if (changed) saveBooks()
                message = "Clasificación terminada: " + books.count { it.genre.isNotBlank() } + " libros con género."
            } finally { genreClassifying = false }
        }
    }

    fun setGenres(uri: Uri, genres: String) {
        val value = normalizeBookGenres(genres).joinToString(" · ")
        books = books.map { if (it.uri == uri) it.copy(genre = value) else it }
        prefs.edit().putBoolean("manual_genre_" + uri, true)
            .putString("genre_source_" + uri, "Editado manualmente").apply()
        saveBooks()
    }

    fun recordOpen(uri: Uri) {
        lastOpenedUri = uri
        val title = books.firstOrNull { it.uri == uri }?.let(::displayTitle).orEmpty()
        prefs.edit().putLong("opened_" + uri.toString().hashCode(), System.currentTimeMillis())
            .putString("last_opened_book", uri.toString()).putString("last_opened_title", title).apply()
    }

    fun chooseWallpaper(mode: String) {
        if (mode !in (listOf("Automático", "Clásico", "Portada") + wallpaperGenres)) return
        wallpaperMode = mode
        prefs.edit().putString("wallpaper_mode", mode).apply()
    }

    fun chooseAnimationMode(mode: String) {
        if (mode !in listOf("Suave", "Equilibrada", "Sin animaciones")) return
        animationMode = mode
        prefs.edit().putString("animation_mode", mode).apply()
    }

    fun readingBooks(): List<Book> = books.filter { it.status == ReadingStatus.READING }
        .sortedWith(compareByDescending<Book> { it.uri == lastOpenedUri }
            .thenByDescending { prefs.getLong("opened_" + it.uri.toString().hashCode(), 0L) })

    fun importLocalEpub(source: Uri) {
        viewModelScope.launch {
            try {
                // Abrir un archivo desde Drive lanza la actividad con ACTION_VIEW.
                // Cargamos primero el catálogo persistente para no sustituirlo por
                // el único libro que se acaba de abrir.
                initialRestoreJob?.join()
                if (books.isEmpty()) restoreCachedBooks(onlyIfEmpty = true)
                val imported = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val name = DocumentFile.fromSingleUri(context, source)?.name
                        ?: source.lastPathSegment.orEmpty().substringAfterLast('/')
                    val mime = context.contentResolver.getType(source).orEmpty().substringBefore(';')
                    val extension = when {
                        name.endsWith(".epub", ignoreCase = true) || mime == "application/epub+zip" -> "epub"
                        name.endsWith(".pdf", ignoreCase = true) || mime == "application/pdf" -> "pdf"
                        name.endsWith(".mobi", true) || name.endsWith(".azw", true) || name.endsWith(".fb2", true) -> name.substringAfterLast('.').lowercase()
                        else -> error("Mi Biblioteca puede abrir aquí archivos EPUB, MOBI, FB2 y PDF.")
                    }
                    val safeName = name.ifBlank { "Libro.$extension" }
                    val folder = File(context.filesDir, "imported").apply { mkdirs() }
                    val target = File(folder, "book-" + MessageDigest.getInstance("SHA-256")
                        .digest(source.toString().toByteArray()).joinToString("") {
                            "%02x".format(it)
                        } + ".$extension")
                    val partial = File(folder, target.name + ".partial")
                    try {
                        context.contentResolver.openInputStream(source)?.use { input ->
                            partial.outputStream().use { output ->
                                input.copyLimited(output, FileLimits.BOOK_BYTES)
                            }
                        } ?: error("No se pudo leer el archivo")
                        require(partial.length() > 0) { "El archivo está vacío" }
                        if (target.exists()) target.delete()
                        check(partial.renameTo(target)) { "No se pudo guardar el libro" }
                    } finally { partial.delete() }
                    val localUri = Uri.fromFile(target)
                    if (extension == "epub") readEpub(context, localUri, safeName,
                        target.length(), target.lastModified())
                    else Book(localUri, cleanCatalogText(safeName),
                        description = "Documento PDF", sourceSize = target.length(),
                        sourceModified = target.lastModified())
                }
                books = (books.filterNot { it.uri == imported.uri } + imported)
                    .sortedBy { it.title.lowercase() }
                saveBooks()
                importedBookUri = imported.uri
                message = if (imported.uri.lastPathSegment?.endsWith(".pdf", true) == true)
                    "PDF añadido a Mi Biblioteca" else "EPUB añadido a Mi Biblioteca"
            } catch (e: Exception) {
                message = "No se pudo abrir el archivo: ${e.localizedMessage ?: "archivo no válido"}"
            }
        }
    }

    fun selectFolder(uri: Uri) {
        if (syncing) { message = "Espera a que termine la sincronización antes de cambiar de carpeta."; return }
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        val persisted = try {
            resolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            true
        } catch (_: Exception) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                message = "Drive ha concedido solo lectura; no se podrán borrar archivos."
                true
            } catch (_: Exception) {
                // El URI sigue siendo válido durante esta sesión. Se conserva para
                // poder explicar y reparar el permiso sin perder la selección.
                message = "La carpeta se ha seleccionado, pero Android no confirmó el permiso permanente."
                false
            }
        }
        val changedFolder = prefs.getString(folderKey, null) != uri.toString()
        // El permiso y la carpeta se guardan al seleccionarlos, no después del escaneo.
        // Así una respuesta temporalmente vacía de Drive no borra la vinculación.
        folderName = try { DocumentFile.fromTreeUri(app, uri)?.name } catch (_: Exception) { null }
        prefs.edit()
            .putString(folderKey, uri.toString())
            .putString("folder_name", folderName.orEmpty())
            .putBoolean("folder_permission_persisted", persisted)
            .putLong("folder_selected_at", System.currentTimeMillis())
            .commit()
        sync(uri, changedFolder)
    }

    fun sync(uri: Uri? = prefs.getString(folderKey, null)?.let(Uri::parse), changedFolder: Boolean = false) {
        if (uri == null) { message = "Selecciona primero tu carpeta de libros."; return }
        if (syncing) return
        val switchingFolder = prefs.getString(folderKey, null) != uri.toString()
        viewModelScope.launch {
            syncing = true; syncCount = 0; message = null
            val syncStartedAt = System.currentTimeMillis()
            var completed = false
            try {
                initialRestoreJob?.join()
                if (books.isEmpty()) restoreCachedBooks(onlyIfEmpty = true)
                // La carpeta es la fuente de verdad. El respaldo se aplica después
                // de obtener el listado real y nunca sustituye el escaneo.
                // Reutilizar metadatos por URI también después de volver a seleccionar
                // la misma carpeta. En otra carpeta las URI no coinciden y no se mezclan.
                val cached = books.associateBy { it.uri }
                // Google Drive puede devolver una lista antigua aun sin marcarla
                // como "loading". Hacemos dos lecturas separadas incluso cuando la
                // primera parece completa y combinamos sus resultados por URI. Los
                // metadatos ya catalogados se reutilizan para que la verificación
                // no vuelva a descargar ni analizar todos los EPUB.
                val result = withContext(Dispatchers.IO) {
                    val collected = linkedMapOf<Uri, Book>()
                    repeat(1) { attempt ->
                        val pass = scanFolder(getApplication(), uri, cached) { count ->
                            viewModelScope.launch(Dispatchers.Main) { syncCount = collected.size + count }
                        }
                        pass.forEach { collected[it.uri] = it }
                    }
                    collected.values.sortedBy { it.title.lowercase() }
                }
                if (result.isEmpty()) {
                    val kind = if (books.isNotEmpty()) DriveFailureKind.PROVIDER_BUSY
                        else DriveFailureKind.UNKNOWN
                    message = driveFailureMessage(kind, books.isNotEmpty(), folderName)
                    syncReport = message.orEmpty()
                    val diagnostic = DriveSyncDiagnostic(
                        folderUri = uri.toString(),
                        folderName = folderName.orEmpty(),
                        startedAt = syncStartedAt,
                        finishedAt = System.currentTimeMillis(),
                        detected = 0, added = 0, modified = 0,
                        unconfirmed = books.size,
                        failureKind = kind,
                        failureDetail = "La enumeración devolvió cero archivos."
                    ).toDisplayText()
                    prefs.edit().putString("drive_diagnostic", diagnostic)
                        .putString("sync_report", syncReport).apply()
                    return@launch
                }
                // Algunos proveedores de documentos (especialmente Drive al
                // volver de ACTION_VIEW) entregan temporalmente solo el archivo
                // abierto aunque la carpeta contenga muchos más. Nunca usamos
                // esa respuesta parcial para borrar el catálogo persistente.
                val catalogBeforeScan = books.toList()
                val partialDriveScan = result.size < catalogBeforeScan.size
                if (changedFolder) prefs.edit().putString(folderKey, uri.toString()).commit()
                else if (prefs.getString(folderKey, null) != uri.toString()) return@launch
                val latest = books.associateBy { it.uri }
                val localBooks = books.filter { it.uri.scheme == "file" }
                books = mergeLibraryScan(result, cached, latest, { it.uri }) { b, prior ->
                    val saved = prefs.getString("info_" + b.uri, null)?.let { JSONObject(it) }
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
                            ?: if (b.cover != null) "Portada del EPUB" else "",
                        rating = prior?.rating ?: 0, startedDate = prior?.startedDate.orEmpty(),
                        finishedDate = prior?.finishedDate.orEmpty(), quotes = prior?.quotes.orEmpty(),
                        tags = prior?.tags.orEmpty(), loanTo = prior?.loanTo.orEmpty(),
                        loanDate = prior?.loanDate.orEmpty(), rereadCount = prior?.rereadCount ?: 0)
                }
                books = (books + localBooks.filter { it.uri.scheme == "file" &&
                    books.none { current -> current.uri == it.uri } })
                    .let { merged ->
                        if (partialDriveScan) {
                            merged + catalogBeforeScan.filter { old ->
                                merged.none { current -> current.uri == old.uri }
                            }
                        } else merged
                    }
                    .sortedBy { it.title.lowercase() }
                val previousUris = cached.keys
                val currentUris = result.map { it.uri }.toSet()
                val addedUris = currentUris - previousUris
                val added = addedUris.size
                val addedTitles = result.filter { it.uri in addedUris }
                    .map(::displayTitle).filter(String::isNotBlank).distinct()
                newBooksNotice = addedTitles.takeIf { it.isNotEmpty() }?.joinToString("\n")
                val changed = result.count { fresh ->
                    cached[fresh.uri]?.let { old ->
                        old.sourceSize != fresh.sourceSize || old.sourceModified != fresh.sourceModified
                    } ?: false
                }
                val missing = (previousUris - currentUris).size
                val coversMissing = books.count { it.cover == null }
                syncSummary = "$added nuevos · $changed modificados · $missing fuera de la carpeta · $coversMissing sin portada"
                if (added + changed + missing > 0) addActivity("Drive · $syncSummary")
                saveBooks()
                // Recuperar favoritos, progreso, secciones y ediciones solo
                // después de que la carpeta haya confirmado sus archivos.
                try { restoreCloud(uri) } catch (e: Exception) {
                    detailMessage = "La biblioteca se sincronizó; no se pudo aplicar el respaldo: ${e.localizedMessage}"
                }
                syncCount = result.size
                prefs.getString("pending_backup", null)?.let { pending ->
                    try {
                        val backup = parseBackup(pending)
                        val restored = applyBackup(backup)
                        prefs.edit().putString("pending_backup", restored.pending).apply()
                    } catch (_: Exception) {}
                }
                syncReport = "Última sincronización: " +
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "ES"))
                        .format(java.util.Date()) +
                    "\n${result.size} archivos detectados · ${books.size} libros en la biblioteca · $added nuevos · $changed modificados · $missing no confirmados; se conservan sus fichas."
                val diagnostic = DriveSyncDiagnostic(
                    folderUri = uri.toString(),
                    folderName = folderName.orEmpty(),
                    startedAt = syncStartedAt,
                    finishedAt = System.currentTimeMillis(),
                    detected = result.size,
                    added = added,
                    modified = changed,
                    unconfirmed = missing
                ).toDisplayText()
                prefs.edit()
                    .putString("sync_report", syncReport)
                    .putString("drive_diagnostic", diagnostic)
                    .putString("sync_manifest", LibrarySyncManifest.from(books).toJson())
                    .apply()
                folderName = withContext(Dispatchers.IO) { DocumentFile.fromTreeUri(getApplication(), uri)?.name }
                prefs.edit().putString("folder_name", folderName).apply()
                message = if (partialDriveScan)
                    "Drive devolvió ${result.size} archivos; se conservaron ${books.size} libros de la biblioteca."
                else "${result.size} libros y documentos encontrados"
                completed = true
                } catch (e: Exception) {
                val detail = e.localizedMessage.orEmpty()
                val kind = classifyDriveFailure(e::class.java.name, detail)
                // Nunca se elimina automáticamente la carpeta elegida. Los fallos
                // de Drive suelen ser temporales y el catálogo local debe seguir útil.
                message = driveFailureMessage(kind, books.isNotEmpty(), folderName)
                syncReport = message.orEmpty()
                val diagnostic = DriveSyncDiagnostic(
                    folderUri = uri.toString(),
                    folderName = folderName.orEmpty(),
                    startedAt = syncStartedAt,
                    finishedAt = System.currentTimeMillis(),
                    detected = syncCount,
                    added = 0, modified = 0, unconfirmed = books.size,
                    failureKind = kind,
                    failureDetail = detail.ifBlank { e::class.java.simpleName }
                ).toDisplayText()
                prefs.edit().putString("drive_diagnostic", diagnostic)
                    .putString("sync_report", syncReport).apply()
            } finally {
                syncing = false
                if (completed) saveCloud()
            }
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
                        val bytes = withContext(Dispatchers.IO) {
                            // Reinspect the book first. A surprising number of EPUBs use an
                            // unusual cover declaration that is recovered by the full parser.
                            val name = (if (current.uri.scheme == "file") current.uri.lastPathSegment
                                else DocumentFile.fromSingleUri(app, current.uri)?.name).orEmpty()
                            val embedded = if (name.endsWith(".epub", ignoreCase = true))
                                try { readEpub(app, current.uri, name, current.sourceSize,
                                    current.sourceModified).cover } catch (_: Exception) { null }
                            else null
                            embedded ?: fetchCover(current)
                        } ?: continue
                        // A manual replacement during the request must win.
                        if (books.firstOrNull { it.uri == uri }?.cover != null) continue
                        withContext(Dispatchers.IO) {
                            File(app.filesDir, "removed-" + coverFile(app, uri).name).delete()
                            coverFile(app, uri).writeSafely(bytes)
                        }
                        books = books.map { if (it.uri == uri) it.copy(cover = bytes,
                            coverSource = if (current.hadEmbeddedCover) "Portada del EPUB"
                                else "Catálogos públicos") else it }
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

    fun forceMissingCovers() {
        if (coverUpdateRunning) return
        googleBooksRetryAt = 0L
        message = "Buscando portadas para ${books.count { it.cover == null }} libros…"
        refreshMissingCovers()
    }

    fun downloadCover(book: Book) {
        if (coverLoading != null) return
        viewModelScope.launch {
            coverLoading = book.uri
            try {
                val embedded = withContext(Dispatchers.IO) {
                    val name = (if (book.uri.scheme == "file") book.uri.lastPathSegment else DocumentFile.fromSingleUri(getApplication(), book.uri)?.name).orEmpty()
                    if (name.endsWith(".epub", ignoreCase = true))
                        readEpub(getApplication(), book.uri, name, book.sourceSize, book.sourceModified).cover
                    else null
                }
                val bytes = embedded ?: withContext(Dispatchers.IO) { fetchCover(book) }
                if (bytes == null) {
                    detailMessage = "No se encontró una portada en el EPUB ni en los catálogos consultados."
                } else {
                    withContext(Dispatchers.IO) {
                        val file = coverFile(getApplication(), book.uri)
                        file.writeSafely(bytes)
                        File(getApplication<Application>().filesDir, "removed-" + file.name).delete()
                    }
                    books = books.map { if (it.uri == book.uri) it.copy(cover = bytes,
                        coverSource = if (embedded != null) "Portada del EPUB" else "Catálogos públicos") else it }
                    saveBooks()
                    detailMessage = "Portada incorporada a la ficha."
                }
            } catch (e: Exception) {
                detailMessage = "No se pudo recuperar la portada: ${e.localizedMessage ?: "comprueba la conexión"}"
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
                            coverFile(app, uri).writeSafely(bytes)
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
                withContext(Dispatchers.IO) { coverFile(getApplication(), book.uri).writeSafely(cover) }
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
    fun setStatus(uri: Uri, status: ReadingStatus) {
        val title = books.firstOrNull { it.uri == uri }?.let(::displayTitle).orEmpty()
        books = books.map { if (it.uri == uri) it.copy(status=status) else it }
        saveBooks()
        if (title.isNotBlank()) addActivity("$title · ${status.label}")
    }
    fun chooseViewMode(mode: String, section: String? = selectedSection) {
        val scope = section ?: "general"
        scopedViewModes[scope] = mode
        viewMode = mode
        prefs.edit().putString("view_mode_$scope", mode).apply()
    }
    fun viewModeFor(section: String?): String =
        scopedViewModes[section ?: "general"] ?: viewMode
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

private val WallpaperTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.90f),
    offset = Offset(0f, 2f),
    blurRadius = 7f
)

@Composable
private fun WallpaperLabel(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    fontWeight: FontWeight? = null
) {
    Text(text, modifier = modifier, color = Color.White, fontSize = fontSize,
        fontWeight = fontWeight,
        style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
}

internal fun displayAuthor(book: Book): String =
    cleanCatalogText(book.customAuthor.ifBlank { book.author })
        .takeUnless(::unknownAuthor) ?: "Biblioteca de Diroka77"

internal fun displayTitle(book: Book): String {
    val author = displayAuthor(book)
    var title = cleanCatalogText(book.customTitle.ifBlank { book.title })
        .replace(Regex("""(?i)\b(?:isbn(?:-1[03])?|autor|editorial|publicad[oa]|idioma|formato|páginas|paginas|sinopsis|descripci[oó]n)\s*[:=].*$"""), "")
        .trim()
    if (author != "Biblioteca de Diroka77") {
        // Remove an author only at a filename boundary; preserve real subtitles.
        title = title.replace(Regex("^" + Regex.escape(author) + "\\s*[-–—|:]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-–—|]\\s*" + Regex.escape(author) + "$", RegexOption.IGNORE_CASE), "")
    }
    // Algunos EPUB incorporan en el título el identificador técnico del
    // archivo y el origen de la biblioteca. Nunca deben aparecer en pantalla.
    title = title
        .replace(Regex("(?i)\\s*[-–—|:]?\\s*[0-9a-f]{24,}\\s*"), " ")
        .replace(Regex("(?i)\\s*[-–—|:]?\\s*(?:Anna'?s Archive|Library Genesis|LibGen|Z-Library)\\s*$"), "")
        .replace(Regex("(?i)\\s*[-–—|:]?\\s*(?:epub|mobi|azw3|fb2|pdf)\\s*$"), "")
    return title.replace(Regex("""\s+"""), " ").trim(' ', '-', '–', '—', '|', ',', ':')
        .ifBlank { "Libro sin título" }
}

private fun sagaNumber(book: Book): Double {
    book.sagaOrder.toDoubleOrNull()?.let { return it }
    inferredSaga(book).second.toDoubleOrNull()?.let { return it }
    val match = Regex("(?:#|n[ºo.]?\\s*|vol\\.?\\s*|tomo\\s*|\\[)(\\d+(?:\\.\\d+)?)",
        RegexOption.IGNORE_CASE).find(book.title)
    return match?.groupValues?.get(1)?.toDoubleOrNull() ?: Double.POSITIVE_INFINITY
}

private data class FilenameIdentity(
    val title: String, val author: String = "Autor desconocido",
    val saga: String = "", val order: String = ""
)

private fun inferFilenameIdentity(filename: String): FilenameIdentity {
    var value = cleanCatalogText(filename.substringBeforeLast('.')).trim()
    var saga = ""
    var order = ""
    val bracketSeries = Regex("^\\[([^]]{2,80}?)(?:\\s*[#№]|\\s+[Nn][ºo.]?|\\s+[Vv]ol(?:umen)?[.]?)\\s*(\\d+(?:[.]\\d+)?)\\]\\s*[-–—:]?\\s*(.+)$")
        .find(value)
    if (bracketSeries != null) {
        saga = bracketSeries.groupValues[1].trim()
        order = bracketSeries.groupValues[2]
        value = bracketSeries.groupValues[3].trim()
    } else {
        val leadingSeries = Regex("^(.{2,80}?)\\s+(?:#|[Nn][ºo.]?|[Vv]ol(?:umen)?[.]?)\\s*(\\d+(?:[.]\\d+)?)\\s*[-–—:]\\s*(.+)$")
            .find(value)
        if (leadingSeries != null) {
            saga = leadingSeries.groupValues[1].trim()
            order = leadingSeries.groupValues[2]
            value = leadingSeries.groupValues[3].trim()
        }
    }
    val parts = value.split(Regex("\\s+(?:[-–—|]{1,3})\\s+"))
        .map(String::trim)
        .filter(String::isNotBlank)

    fun looksLikePerson(text: String): Boolean {
        val words = text.replace(",", " ").trim().split(Regex("\\s+"))
        return text.length in 3..60 &&
            words.size in 2..5 &&
            text.none(Char::isDigit) &&
            words.all { it.length > 1 || it.lowercase() in setOf("de", "y") }
    }

    val first = parts.getOrNull(0).orEmpty()
    val second = parts.getOrNull(1).orEmpty()
    val authorFirst = second.isNotBlank() &&
        (first.contains(",") || (looksLikePerson(first) && (second.any(Char::isDigit) || parts.size == 2)))
    val titleFirst = second.isNotBlank() && !authorFirst && looksLikePerson(second)
    val cleanTitle = when {
        authorFirst -> second
        titleFirst -> first
        else -> value
    }
    val cleanAuthor = when {
        authorFirst -> first
        titleFirst -> second
        else -> "Autor desconocido"
    }
    return FilenameIdentity(
        title = cleanTitle.ifBlank { "Libro sin título" },
        author = cleanAuthor,
        saga = saga, order = order
    )
}

private data class DriveDocumentEntry(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val modified: Long
) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

private fun queryDriveChildren(
    context: Context,
    treeUri: Uri,
    parentDocumentId: String
): List<DriveDocumentEntry> {
    val resolver = context.contentResolver
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    // Una consulta devuelve todos los metadatos de la carpeta de una vez.
    // DocumentFile queda como respaldo para proveedores que no implementen bien
    // DocumentsContract, evitando cientos de consultas individuales en Drive.
    repeat(3) { attempt ->
        val cursor = try {
            resolver.query(childrenUri, projection, null, null, null)
        } catch (_: Exception) { null }
        if (cursor != null) {
            val result = ArrayList<DriveDocumentEntry>()
            val loading = try {
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idColumn >= 0) while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)?.takeIf(String::isNotBlank) ?: continue
                    result += DriveDocumentEntry(
                        documentId = id,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        name = if (nameColumn >= 0) cursor.getString(nameColumn).orEmpty()
                            else id.substringAfterLast('/'),
                        mimeType = if (mimeColumn >= 0) cursor.getString(mimeColumn).orEmpty()
                            else "application/octet-stream",
                        size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn))
                            cursor.getLong(sizeColumn) else -1L,
                        modified = if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn))
                            cursor.getLong(modifiedColumn) else -1L
                    )
                }
                cursor.extras?.getBoolean(DocumentsContract.EXTRA_LOADING, false) == true
            } finally { cursor.close() }
            if (result.isNotEmpty()) return result
        }
        if (attempt < 2) Thread.sleep(250L)
    }

    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
    return try {
        DocumentFile.fromSingleUri(context, parentUri)?.listFiles()?.mapNotNull { child ->
            val id = try { DocumentsContract.getDocumentId(child.uri) } catch (_: Exception) { null }
            id?.let {
                DriveDocumentEntry(
                    documentId = it,
                    uri = child.uri,
                    name = child.name.orEmpty(),
                    mimeType = child.type.orEmpty(),
                    size = child.length(),
                    modified = child.lastModified()
                )
            }
        }.orEmpty()
    } catch (_: Exception) { emptyList() }
}

private fun isSupportedBookFile(name: String, mimeType: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    val hasNoExtension = name.isNotBlank() && !name.contains('.')
    return extension in setOf("epub", "pdf", "mobi", "azw", "azw3", "fb2", "txt", "html", "htm", "rtf", "docx", "md") ||
        (hasNoExtension && mimeType.lowercase() !in setOf(
            "image/jpeg", "image/png", "image/webp", "application/json"
        )) ||
        mimeType.lowercase() in setOf(
            "application/epub+zip", "application/pdf", "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook", "text/plain", "text/html",
            "application/rtf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/markdown"
        )
}

private fun scanFolder(context: Context, treeUri: Uri, cached: Map<Uri, Book>, onProgress: (Int) -> Unit): List<Book> {
    DocumentFile.fromTreeUri(context, treeUri)
        ?: throw IllegalStateException("Carpeta no disponible")
    val out = mutableListOf<Book>()
    val visited = mutableSetOf<String>()
    val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

    fun addFile(f: DriveDocumentEntry) {
        if (!isSupportedBookFile(f.name, f.mimeType) || out.size >= 10000) return
        val prior = cached[f.uri]
        val cover = coverFile(context, f.uri)
        val removed = File(context.filesDir, "removed-" + cover.name).exists()
        val metadataMatches = prior != null && when {
            f.size > 0L -> prior.sourceSize == f.size &&
                (f.modified <= 0L || prior.sourceModified == f.modified)
            f.modified > 0L -> prior.sourceModified == f.modified
            else -> true
        }
        val unchanged = prior != null && metadataMatches &&
            prior.metadataRevision >= 50 && prior.sourceCoverChecked &&
            (!prior.hadEmbeddedCover || cover.exists() || removed)

        val book = if (unchanged) prior!! else {
            val identity = inferFilenameIdentity(f.name)
            val isEpub = f.name.endsWith(".epub", true) ||
                f.mimeType.equals("application/epub+zip", true)
            val parsed = if (isEpub) try {
                readEpub(context, f.uri, f.name.ifBlank { "Libro" }, f.size, f.modified)
            } catch (_: Exception) {
                Book(f.uri, identity.title,
                    author = prior?.author?.takeUnless(::unknownAuthor) ?: identity.author,
                    saga = prior?.saga.orEmpty().ifBlank { identity.saga },
                    sagaOrder = prior?.sagaOrder.orEmpty().ifBlank { identity.order },
                    sourceSize = f.size, sourceModified = f.modified)
            } else Book(f.uri, identity.title,
                author = prior?.author?.takeUnless(::unknownAuthor) ?: identity.author,
                saga = prior?.saga.orEmpty().ifBlank { identity.saga },
                sagaOrder = prior?.sagaOrder.orEmpty().ifBlank { identity.order },
                sourceSize = f.size, sourceModified = f.modified)
            if (parsed.metadataRevision < 50 && prior != null) parsed.copy(
                sourceSize = f.size, sourceModified = f.modified,
                favorite = prior.favorite, status = prior.status,
                sections = prior.sections, section = prior.section,
                customTitle = prior.customTitle, customAuthor = prior.customAuthor)
            else parsed.copy(
                sourceSize = f.size, sourceModified = f.modified,
                author = parsed.author.takeUnless(::unknownAuthor)
                    ?: prior?.author?.takeUnless(::unknownAuthor) ?: "Autor desconocido",
                sourceCoverChecked = true,
                hadEmbeddedCover = parsed.cover != null,
                cover = parsed.cover ?: prior?.cover)
        }
        try {
            if (!removed && !cover.exists() && book.cover != null) cover.writeSafely(book.cover)
        } catch (_: Exception) {}
        val savedCover = try {
            if (unchanged && prior?.cover != null) prior.cover
            else cover.takeIf { it.exists() }?.readBytes()
        } catch (_: Exception) { book.cover }
        out += if (removed) book.copy(cover = null)
            else if (savedCover != null) book.copy(cover = savedCover) else book
        onProgress(out.size)
    }

    fun walk(parentDocumentId: String, depth: Int = 0) {
        if (depth > 32 || visited.size >= 10000 || !visited.add(parentDocumentId)) return
        val children = try {
            queryDriveChildren(context, treeUri, parentDocumentId)
        } catch (_: Exception) { emptyList() }
        children.forEach { entry ->
            if (entry.isDirectory) {
                try { walk(entry.documentId, depth + 1) } catch (_: Exception) {}
            } else {
                try { addFile(entry) } catch (_: Exception) {}
            }
        }
    }

    walk(rootDocumentId)
    return out.sortedBy { it.title.lowercase() }
}
private fun readEpub(context: Context, uri: Uri, fallbackName: String,
    sourceSize: Long = -1L, sourceModified: Long = -1L): Book {
    val inferred = inferFilenameIdentity(fallbackName)
    val fallback = Book(uri, inferred.title, author = inferred.author,
        saga = inferred.saga, sagaOrder = inferred.order,
        sourceSize = sourceSize, sourceModified = sourceModified)
    return try {
        // Reuse the reader cache so Drive downloads each unchanged file only once.
        ZipFile(localReaderFile(context, fallback)).use { zip ->
            require(zip.size() <= FileLimits.ZIP_ENTRIES) { "El EPUB contiene demasiadas entradas." }
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
                ?: return@use fallback.copy(cover = imageCoverFromZip(zip), metadataRevision = 54)
            val opfBytes = entryBytes(opfPath, 2_000_000)
                ?: return@use fallback.copy(cover = imageCoverFromZip(zip), metadataRevision = 54)
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
            if (cover == null) cover = imageCoverFromZip(zip)
            Book(uri, meta.title.ifBlank { fallback.title },
                meta.author.takeUnless(::unknownAuthor) ?: fallback.author, meta.date, meta.publisher,
                meta.genre, meta.description, meta.isbn, meta.saga, cover,
                language = meta.language,
                sagaOrder = meta.sagaOrder.ifBlank { fallback.sagaOrder },
                metadataRevision = 54).let { parsed ->
                    if (parsed.saga.isBlank() && fallback.saga.isNotBlank())
                        parsed.copy(saga = fallback.saga) else parsed
                }
        }
    } catch (_: Exception) { fallback }
}

private fun compactCover(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = try { imageSampleSize(bounds.outWidth, bounds.outHeight, 1200, 1800) }
        catch (_: IllegalArgumentException) { return null }
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
    if (bounds.outWidth < 120 || bounds.outHeight < 160 ||
        bounds.outWidth.toFloat() / bounds.outHeight !in 0.4f..0.95f) return null
    return compactCover(bytes)
}

private fun imageCoverFromZip(zip: ZipFile): ByteArray? {
    val candidates = zip.entries().asSequence().filter { entry ->
        !entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in
            setOf("jpg", "jpeg", "png", "webp") &&
            !Regex("(?i)logo|icon|banner|chapter|capitulo|decor|backcover|contraportada")
                .containsMatchIn(entry.name)
    }.toList()
    val coverName = Regex("(?i)cover|portada|front|couverture|couv|jacket|tapa")
    val ranked = candidates.withIndex().sortedWith(compareByDescending<IndexedValue<java.util.zip.ZipEntry>> {
        coverName.containsMatchIn(it.value.name)
    }.thenBy { it.index }).take(40)
    for ((_, entry) in ranked) {
        if (entry.size > 16_000_000) continue
        val bytes = zip.getInputStream(entry).use { it.readNBytes(16_000_001) }
        if (bytes.size > 16_000_000) continue
        compactPortraitCover(bytes)?.let { return it }
    }
    return null
}

private fun coverFile(context: Context, uri: Uri): File {
    val hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(context.filesDir, "cover-$hash.jpg")
}

private fun loadStoredCover(file: File): ByteArray? {
    if (!file.exists() || file.length() <= 0L) return null
    return try {
        val bytes = file.readBytes()
        if (bytes.size <= 600_000) bytes
        else compactCover(bytes)?.also { compacted ->
            if (compacted.size < bytes.size) file.writeSafely(compacted)
        } ?: bytes
    } catch (_: Exception) { null }
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

internal fun catalogMatch(candidateTitle: String, candidateAuthors: List<String>, book: Book): Boolean {
    val expectedAuthor = displayAuthor(book).takeUnless(::unknownAuthor).orEmpty()
    val candidateAuthor = candidateAuthors.joinToString(", ")
    val confidence = CatalogQuality.confidenceForIdentity(
        displayTitle(book), expectedAuthor, candidateTitle, candidateAuthor, book.isbn, ""
    )
    return confidence >= if (expectedAuthor.isBlank()) 76 else 70
}

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

internal fun getJson(url: String): JSONObject {
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
        connection.inputStream.use { JSONObject(it.readLimited(2L * 1024 * 1024).toString(Charsets.UTF_8)) }
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


private val ignoredGenreLabels = setOf(
    "misc", "miscellaneous", "uncategorized", "uncategorised",
    "sin clasificar", "desconocido", "unknown", "unspecified"
)
private val otherGenreAliases = setOf("altres", "otro", "otros", "other", "others")

private val normalizedBookGenreCache = object : LruCache<String, List<String>>(512) {}

private fun normalizeBookGenres(raw: String): List<String> {
    synchronized(normalizedBookGenreCache) { normalizedBookGenreCache.get(raw) }?.let { return it }
    val parts = raw.split(Regex("[,;|/]+|\\s+--\\s+"))
        .map { it.trim().trim('.', ':', ' ') }
        .filter { it.length in 2..64 }
    val normalized = normalizeGenres(parts)
    val custom = parts.filterNot { it.lowercase() in ignoredGenreLabels }
        .filter { normalizeGenres(listOf(it)).isEmpty() }
        .map { part ->
            if (part.lowercase() in otherGenreAliases) "Otros"
            else part.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
        }
    val result = (normalized + custom).distinctBy { it.lowercase() }.take(5)
    synchronized(normalizedBookGenreCache) { normalizedBookGenreCache.put(raw, result) }
    return result
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

private fun parseSagaLabel(rawValue: String): Pair<String, String> {
    val raw = rawValue.trim().trim('(', ')', '[', ']', ' ')
    if (raw.isBlank()) return "" to ""
    val patterns = listOf(
        Regex("^(.*?)(?:\\s*[#№]|\\s+[Nn][ºo.]?|\\s+[Vv]ol(?:umen)?[.]?|\\s+[Ll]ibro)\\s*(\\d+(?:[.]\\d+)?)$"),
        Regex("^(.*?)[,;:]?\\s+(\\d+(?:[.]\\d+)?)\\s*(?:de\\s+la\\s+serie|series)?$", RegexOption.IGNORE_CASE)
    )
    val match = patterns.firstNotNullOfOrNull { it.find(raw) }
    val saga = match?.groupValues?.get(1)?.trim(' ', '-', ':', ',').orEmpty().ifBlank {
        raw.replace(Regex("\\s*[#№]\\s*\\d+(?:[.]\\d+)?$"), "").trim()
    }
    return saga to match?.groupValues?.get(2).orEmpty()
}

private fun fetchSagaMetadata(book: Book): Pair<String, String> {
    val plainTitle = displayTitle(book).trim()
    val author = displayAuthor(book).takeUnless(::unknownAuthor).orEmpty()
    val encodedTitle = java.net.URLEncoder.encode(plainTitle, "UTF-8")
    val encodedAuthor = java.net.URLEncoder.encode(author, "UTF-8")

    // Open Library is the best source when an edition exposes its series field. Try both a
    // precise and a broad search because many Spanish editions omit or alter the author name.
    val openLibraryQueries = listOf(
        "title=$encodedTitle" + if (author.isNotBlank()) "&author=$encodedAuthor" else "",
        "q=" + java.net.URLEncoder.encode("$plainTitle $author", "UTF-8")
    ).distinct()
    for (query in openLibraryQueries) try {
        val docs = getJson("https://openlibrary.org/search.json?$query&fields=title,author_name,series&limit=20")
            .optJSONArray("docs")
        for (i in 0 until (docs?.length() ?: 0)) {
            val item = docs?.optJSONObject(i) ?: continue
            val names = item.optJSONArray("author_name")
            val authors = (0 until (names?.length() ?: 0)).map { names!!.optString(it) }
            if (!catalogMatch(item.optString("title"), authors, book)) continue
            val series = item.optJSONArray("series") ?: continue
            for (j in 0 until series.length()) {
                val parsed = parseSagaLabel(series.optString(j))
                if (parsed.first.isNotBlank()) return parsed
            }
        }
    } catch (_: Exception) { }

    // Google Books often knows the number even when Open Library has no series metadata.
    try {
        val q = java.net.URLEncoder.encode("intitle:$plainTitle" +
            if (author.isNotBlank()) " inauthor:$author" else "", "UTF-8")
        val items = getJson("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=20")
            .optJSONArray("items")
        for (i in 0 until (items?.length() ?: 0)) {
            val volume = items?.optJSONObject(i) ?: continue
            val info = volume.optJSONObject("volumeInfo") ?: continue
            val authorsJson = info.optJSONArray("authors")
            val authors = (0 until (authorsJson?.length() ?: 0)).map { authorsJson!!.optString(it) }
            if (!catalogMatch(info.optString("title"), authors, book)) continue
            val series = info.optJSONObject("seriesInfo") ?: volume.optJSONObject("seriesInfo")
            val name = series?.optString("shortSeriesBookTitle").orEmpty()
            val number = series?.optString("bookDisplayNumber").orEmpty()
            if (name.isNotBlank()) return parseSagaLabel(name).let { parsed ->
                parsed.first to number.ifBlank { parsed.second }
            }
            listOf(info.optString("subtitle"), info.optString("title")).forEach { candidate ->
                Regex("(?:serie|saga)\\s+([^,;:()]+?)(?:[,;:]|\\s*[#№]\\s*)(\\d+(?:[.]\\d+)?)",
                    RegexOption.IGNORE_CASE).find(candidate)?.let { match ->
                    return match.groupValues[1].trim() to match.groupValues[2]
                }
            }
        }
    } catch (_: Exception) { }

    // Last local fallback: filenames and titles frequently include “Saga #3” or “Libro 3”.
    val sourceName = book.uri.lastPathSegment.orEmpty().substringAfterLast('/').substringBeforeLast('.')
    val candidates = listOf(plainTitle, sourceName)
    for (candidate in candidates) {
        Regex("[\\[(]([^]()]*(?:saga|serie)[^]()]*)[\\])]", RegexOption.IGNORE_CASE)
            .find(candidate)?.groupValues?.get(1)?.let { label ->
                val parsed = parseSagaLabel(label)
                if (parsed.first.isNotBlank()) return parsed
            }
    }
    return "" to ""
}

private fun fetchSagaCatalog(saga: String, reference: Book?): List<SagaVolume> {
    val found = linkedMapOf<String, SagaVolume>()
    val sagaKey = saga.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
    fun add(order: String, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val cleanOrder = order.trim().replace(',', '.')
        val key = cleanOrder.takeIf(String::isNotBlank)?.let { "n:$it" }
            ?: "t:" + cleanTitle.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
        found.putIfAbsent(key, SagaVolume(cleanOrder, cleanTitle))
    }

    // Open Library supplies the edition-independent series label for many works.
    try {
        val encoded = java.net.URLEncoder.encode(saga, "UTF-8")
        val docs = getJson("https://openlibrary.org/search.json?series=$encoded" +
            "&fields=title,author_name,series,first_publish_year&limit=100").optJSONArray("docs")
        for (i in 0 until (docs?.length() ?: 0)) {
            val item = docs?.optJSONObject(i) ?: continue
            val series = item.optJSONArray("series") ?: continue
            val matching = (0 until series.length()).map { series.optString(it) }
                .firstOrNull { raw ->
                    raw.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "").contains(sagaKey) ||
                        sagaKey.contains(raw.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), ""))
                } ?: continue
            val parsed = parseSagaLabel(matching)
            add(parsed.second, item.optString("title"))
        }
    } catch (_: Exception) { }

    // Wikidata models “part of the series” (P179) and the series ordinal (P1545).
    try {
        val escaped = saga.replace("\\", "\\\\").replace("\"", "\\\"")
        val authorFilter = reference?.let(::displayAuthor)?.takeUnless(::unknownAuthor)
            ?.replace("\\", "\\\\")?.replace("\"", "\\\"").orEmpty()
        val query = "SELECT DISTINCT ?work ?workLabel ?ordinal WHERE { " +
            "?series rdfs:label ?seriesLabel . " +
            "FILTER(LANG(?seriesLabel) IN (\"es\",\"en\")) " +
            "FILTER(CONTAINS(LCASE(STR(?seriesLabel)), LCASE(\"$escaped\"))) " +
            "?work p:P179 ?statement . ?statement ps:P179 ?series . " +
            "OPTIONAL { ?statement pq:P1545 ?ordinal . } " +
            (if (authorFilter.isNotBlank())
                "OPTIONAL { ?work wdt:P50 ?author . ?author rdfs:label ?authorLabel . " +
                    "FILTER(LANG(?authorLabel) IN (\"es\",\"en\")) } " else "") +
            "SERVICE wikibase:label { bd:serviceParam wikibase:language \"es,en\". } } LIMIT 100"
        val url = "https://query.wikidata.org/sparql?format=json&query=" +
            java.net.URLEncoder.encode(query, "UTF-8")
        val bindings = getJson(url).optJSONObject("results")?.optJSONArray("bindings")
        for (i in 0 until (bindings?.length() ?: 0)) {
            val row = bindings?.optJSONObject(i) ?: continue
            val title = row.optJSONObject("workLabel")?.optString("value").orEmpty()
            val order = row.optJSONObject("ordinal")?.optString("value").orEmpty()
            add(order, title)
        }
    } catch (_: Exception) { }

    // Ensure the books already present are represented even if public data is incomplete.
    reference?.let { add(it.sagaOrder, displayTitle(it)) }
    return found.values.sortedWith(compareBy<SagaVolume> {
        it.order.toDoubleOrNull() ?: Double.POSITIVE_INFINITY
    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }).take(100)
}

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
        if (plot.isNotBlank()) plotSource = "Reseña"
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

private suspend fun localizedPublicBookDetails(result: PublicBookResult): PublicBookDetails {
    val details = withContext(Dispatchers.IO) { loadPublicBookDetails(result) }
    val translatedSummary = ensureSpanish(details.summary).ifBlank { details.summary }
    val translatedBio = ensureSpanish(details.authorBio).ifBlank { details.authorBio }
    return details.copy(summary = translatedSummary, authorBio = translatedBio)
}

private fun normalizePath(path: String): String {
    val parts = mutableListOf<String>()
    path.replace('\\','/').split('/').forEach { when(it) { "", "." -> {}; ".." -> if(parts.isNotEmpty()) parts.removeAt(parts.lastIndex); else -> parts += it } }
    return parts.joinToString("/")
}

private val Ink = Color(0xFF31271F)
private val Mahogany = Color(0xFF49352A)
private val HeaderBrown = Color(0xFF765B50)
private val Brass = Color(0xFFAD8248)
private val Parchment = Color(0xFFF7F6F2)
private val Paper = Color.White
private val Teal = Color(0xFF5C9F94)

private val libraryColors = lightColorScheme(
    primary = Teal, onPrimary = Ink, secondary = Brass,
    background = Parchment, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFECE0C8)
)

class MainActivity : ComponentActivity() {
    private lateinit var libraryVm: LibraryViewModel
    private var jankStats: JankStats? = null
    private var metricScreen = "Inicio"
    private val framesByScreen = mutableMapOf<String, IntArray>()

    fun setMetricScreen(screen: String) { metricScreen = screen }
    fun performanceReport(): String = listOf("Inicio", "Biblioteca", "Géneros", "Secciones", "Favoritos", "Ficha")
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
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            libraryVm.importLocalEpub(intent.data!!)
            return
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type != "text/plain") {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                libraryVm.importLocalEpub(it)
                return
            }
        }
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
        else android.widget.Toast.makeText(context, "La aplicación de reseñas no está instalada", android.widget.Toast.LENGTH_LONG).show()
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
        val html = try {
            connection.inputStream.use { it.readLimited(2L * 1024 * 1024).toString(Charsets.UTF_8) }
        } finally { connection.disconnect() }
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
        else android.widget.Toast.makeText(context, "La aplicación de reseñas no está instalada", android.widget.Toast.LENGTH_LONG).show()
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

private fun searchBookInGoogleAi(context: Context, book: Book) {
    val query = "Reseña, argumento y datos del libro " + displayTitle(book) +
        " de " + displayAuthor(book)
    val url = Uri.parse("https://www.google.com/search?udm=50&q=" +
        java.net.URLEncoder.encode(query, "UTF-8"))
    try { context.startActivity(Intent(Intent.ACTION_VIEW, url)
        .setPackage("com.google.android.googlequicksearchbox")) }
    catch (_: Exception) { searchInGoogleApp(context, query) }
}

private fun shareBookFile(context: Context, book: Book) {
    try {
        val name = (if (book.uri.scheme == "file") book.uri.lastPathSegment else DocumentFile.fromSingleUri(context, book.uri)?.name).orEmpty()
        val mime = when (name.substringAfterLast('.', "").lowercase()) {
            "epub" -> "application/epub+zip"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, book.uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, name, book.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir libro"))
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "No se pudo compartir este archivo de Drive",
            android.widget.Toast.LENGTH_LONG).show()
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
        "Leyendo", "Leer", "Reseña", "Sincronizar" -> Icons.Outlined.AutoStories
        "Goodreads" -> Icons.Outlined.Bookmarks
        "Leídos", "Leído", "Revisado" -> Icons.Outlined.CheckCircle
        "Autores", "Autor", "Biografía" -> Icons.Outlined.PersonOutline
        "Sagas" -> Icons.Outlined.Layers
        "Géneros" -> Icons.Outlined.Category
        "Google", "Búsqueda IA" -> Icons.Outlined.AutoAwesome
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
        "Retroceder" -> Icons.Outlined.FastRewind
        "Reproducir" -> Icons.Outlined.PlayArrow
        "Pausar" -> Icons.Outlined.Pause
        "Detener" -> Icons.Outlined.Stop
        "Avanzar" -> Icons.Outlined.FastForward
        "Bloquear" -> Icons.Outlined.Lock
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
            if (selected) accent else Brass.copy(alpha = 0.35f)),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Paper.copy(alpha = 0.95f), labelColor = Ink,
            iconColor = Teal, selectedLeadingIconColor = Color.White,
            selectedContainerColor = accent, selectedLabelColor = Color.White))
}

@Composable private fun LibraryActionButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    color: Color = Teal, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        content = content)
}

@Composable private fun LibraryTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    LibraryActionButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun LibraryApp(vm: LibraryViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hostActivity = context as? android.app.Activity
    SideEffect {
        hostActivity?.window?.let { window ->
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = Parchment.toArgb()
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = true
            }
        }
    }
    val catalogSearchIndex = remember(context.applicationContext) {
        PersistentCatalogSearchIndex(context.applicationContext)
    }
    var selected by remember { mutableStateOf<Book?>(null) }
    var readingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingReader by remember { mutableStateOf<Book?>(null) }
    var readerTransitionBusy by remember { mutableStateOf(false) }
    val readerTransition = remember { Animatable(0f) }
    val coverPreviewWidthPx = with(LocalDensity.current) { 185.dp.roundToPx() }
    val coverPreviewHeightPx = with(LocalDensity.current) { 265.dp.roundToPx() }
    var tab by rememberSaveable { mutableStateOf("Inicio") }
    var selectedGenres by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var genreAllSelected by rememberSaveable { mutableStateOf(true) }
    var genreCarousel by rememberSaveable { mutableStateOf("Horizontal") }
    var genrePickerOpen by rememberSaveable { mutableStateOf(false) }
    var genreSearch by rememberSaveable { mutableStateOf("") }
    var draftGenres by remember { mutableStateOf(emptySet<String>()) }
    var draftAllGenres by remember { mutableStateOf(true) }
    var smartCollection by rememberSaveable { mutableStateOf<String?>(null) }
    val genrePickerListState = rememberLazyListState()
    val tabs = remember { listOf("Inicio", "Biblioteca", "Secciones", "Favoritos", "Estadísticas") }
    val scope = rememberCoroutineScope()
    val pageMotion = remember { Animatable(0f) }
    val pageDrag = remember { mutableFloatStateOf(0f) }
    val pageWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val pageTravelPx = pageWidthPx * 0.18f
    var tabTransitionJob by remember { mutableStateOf<Job?>(null) }
    fun motionDuration(normal: Int): Int = when (vm.animationMode) {
        "Sin animaciones" -> 1
        "Suave" -> (normal * 1.25f).toInt()
        else -> normal
    }
    LaunchedEffect(Unit) { if (tab == "Pendientes") tab = "Secciones" }
    var addingSection by remember { mutableStateOf(false) }
    var sectionName by remember { mutableStateOf("") }
    var sectionToDelete by remember { mutableStateOf<String?>(null) }
    var sectionToRename by remember { mutableStateOf<String?>(null) }
    var renameSectionName by remember { mutableStateOf("") }
    var showWishList by remember { mutableStateOf(false) }
    fun switchTab(requestedName: String, showPending: Boolean = false) {
        val name = if (requestedName == "Géneros") {
            vm.groupMode = "Géneros"
            "Biblioteca"
        } else requestedName
        tabTransitionJob?.cancel()
        tabTransitionJob = scope.launch {
            if (pageDrag.floatValue != 0f) {
                pageMotion.snapTo((pageDrag.floatValue * 0.18f).coerceIn(-pageTravelPx, pageTravelPx))
                pageDrag.floatValue = 0f
            }
            if (name == tab) {
                showWishList = showPending
                pageMotion.animateTo(0f, tween(motionDuration(120), easing = FastOutSlowInEasing))
                return@launch
            }
            val direction = if (tabs.indexOf(name) > tabs.indexOf(tab)) -1f else 1f
            val distance = pageTravelPx
            // El destino cambia en el mismo fotograma del toque. Antes se esperaba
            // a terminar una animación de salida y el botón parecía no responder.
            tab = name
            vm.query = ""
            showWishList = showPending
            vm.selectedSection = null
            vm.qualityFilter = "Ninguno"
            vm.statusFilter = null
            vm.onlyFavorites = name == "Favoritos"
            smartCollection = null
            pageMotion.snapTo(-direction * distance * .55f)
            withFrameNanos { }
            pageMotion.animateTo(0f, tween(motionDuration(210), easing = FastOutSlowInEasing))
        }
    }
    fun settlePage() {
        tabTransitionJob?.cancel()
        tabTransitionJob = scope.launch {
            pageMotion.snapTo((pageDrag.floatValue * 0.18f).coerceIn(-pageTravelPx, pageTravelPx))
            pageDrag.floatValue = 0f
            pageMotion.animateTo(0f, tween(motionDuration(210), easing = FastOutSlowInEasing))
        }
    }
    var bookMenu by remember { mutableStateOf<Book?>(null) }
    var addWishDialog by remember { mutableStateOf(false) }
    var wishTitle by remember { mutableStateOf("") }
    var wishUrl by remember { mutableStateOf("") }
    var publicSearchOpen by remember { mutableStateOf(false) }
    var publicQuery by remember { mutableStateOf("") }
    var publicResults by remember { mutableStateOf<List<PublicBookResult>>(emptyList()) }
    var publicSearching by remember { mutableStateOf(false) }
    var publicSearchJob by remember { mutableStateOf<Job?>(null) }
    var selectedPublicResult by remember { mutableStateOf<PublicBookResult?>(null) }
    var wallpaperPickerOpen by remember { mutableStateOf(false) }
    var animationPickerOpen by remember { mutableStateOf(false) }
    var sagaRenameSource by remember { mutableStateOf<String?>(null) }
    var sagaRenameTarget by remember { mutableStateOf("") }
    if (wallpaperPickerOpen) AlertDialog(
        onDismissRequest = { wallpaperPickerOpen = false },
        title = { Text("Elegir fondo de pantalla") },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(listOf("Automático", "Clásico", "Portada") + wallpaperGenres) { mode ->
                    Row(Modifier.fillMaxWidth().clickable {
                        vm.chooseWallpaper(mode)
                        wallpaperPickerOpen = false
                    }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.wallpaperMode == mode,
                            onClick = { vm.chooseWallpaper(mode); wallpaperPickerOpen = false })
                        Text(mode, color = Mahogany)
                    }
                }
            }
        },
        confirmButton = { LibraryTextButton(onClick = { wallpaperPickerOpen = false }) {
            Text("Cerrar")
        } }
    )
    if (animationPickerOpen) AlertDialog(
        onDismissRequest = { animationPickerOpen = false },
        title = { Text("Movimiento y fluidez") },
        text = { Column {
            listOf("Equilibrada", "Suave", "Sin animaciones").forEach { mode ->
                Row(Modifier.fillMaxWidth().clickable {
                    vm.chooseAnimationMode(mode); animationPickerOpen = false
                }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(vm.animationMode == mode, onClick = {
                        vm.chooseAnimationMode(mode); animationPickerOpen = false
                    })
                    Text(mode)
                }
            }
        } },
        confirmButton = { LibraryTextButton(onClick = { animationPickerOpen = false }) { Text("Cerrar") } })
    if (addingSection) AlertDialog(
        onDismissRequest = { addingSection = false },
        title = { Text("Nueva sección") },
        text = { OutlinedTextField(sectionName, { sectionName = it }, label = { Text("Nombre") }) },
        confirmButton = { LibraryTextButton(onClick = { vm.addSection(sectionName); sectionName = ""; addingSection = false }) { Text("Crear") } },
        dismissButton = { LibraryTextButton(onClick = { addingSection = false }) { Text("Cancelar") } }
    )
    sectionToDelete?.let { name -> AlertDialog(
        onDismissRequest = { sectionToDelete = null },
        title = { Text("Eliminar sección") },
        text = { Text("Se eliminará la sección «$name». Los libros permanecerán en la biblioteca.") },
        confirmButton = { LibraryTextButton(onClick = { vm.deleteSection(name); sectionToDelete = null }) { Text("Eliminar") } },
        dismissButton = { LibraryTextButton(onClick = { sectionToDelete = null }) { Text("Cancelar") } }
    ) }
    if (addWishDialog) AlertDialog(
        onDismissRequest = { addWishDialog = false },
        title = { Text("Añadir a la lista de deseos") },
        text = { Column {
            OutlinedTextField(wishTitle, { wishTitle = it }, label = { Text("Título") })
            OutlinedTextField(wishUrl, { wishUrl = it }, label = { Text("Enlace opcional") })
        } },
        confirmButton = { LibraryTextButton(onClick = {
            vm.addGoodreadsWish(wishTitle, wishUrl); addWishDialog = false; wishTitle = ""; wishUrl = ""
        }) { Text("Añadir") } },
        dismissButton = { LibraryTextButton(onClick = { addWishDialog = false }) { Text("Cancelar") } }
    )
    if (publicSearchOpen) AlertDialog(
        onDismissRequest = { publicSearchOpen = false; selectedPublicResult = null },
        title = { Text("Consultar libros públicos", fontFamily = FontFamily.Serif, color = Ink) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                DiscoverSearchField(
                    query = publicQuery,
                    searching = publicSearching,
                    voiceListening = false,
                    onVoice = { },
                    onSearch = { requested ->
                        publicQuery = requested
                        publicSearching = true
                        publicSearchJob?.cancel()
                        publicSearchJob = scope.launch(Dispatchers.IO) {
                            val found = runCatching { searchPublicBooks(requested) }.getOrDefault(emptyList())
                            withContext(Dispatchers.Main) {
                                publicResults = found
                                publicSearching = false
                                selectedPublicResult = null
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                if (publicSearching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brass)
                if (!publicSearching && publicQuery.isNotBlank() && publicResults.isEmpty())
                    Text("No se han encontrado resultados.", color = Mahogany)
                if (selectedPublicResult == null) {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(publicResults, key = { it.sourceUrl.ifBlank { it.title + "|" + it.author } }) { result ->
                            PublicBookCard(result, onOpen = { selectedPublicResult = result },
                                onWish = { vm.addGoodreadsWish(result.title, result.sourceUrl) })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                } else {
                    val result = selectedPublicResult!!
                    val details by produceState<PublicBookDetails?>(initialValue = null, result.sourceUrl) {
                        value = withContext(Dispatchers.IO) { loadPublicBookDetails(result) }
                    }
                    LazyColumn(Modifier.heightIn(max = 470.dp)) {
                        item {
                            PublicBookDetailPage(result, details,
                                wishListed = vm.wishList.any { it.first.equals(result.title, true) && it.second == result.sourceUrl },
                                onBack = { selectedPublicResult = null },
                                onWish = { vm.addGoodreadsWish(result.title, result.sourceUrl) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { publicSearchOpen = false; selectedPublicResult = null }) { Text("Cerrar") } }
    )
    sagaRenameSource?.let { source -> AlertDialog(
        onDismissRequest = { sagaRenameSource = null },
        title = { Text("Unificar nombre de saga") },
        text = { Column {
            Text("Todos los libros agrupados como «$source» usarán el nuevo nombre.")
            OutlinedTextField(sagaRenameTarget, { sagaRenameTarget = it },
                label = { Text("Nombre definitivo") }, singleLine = true)
        } },
        confirmButton = { LibraryTextButton(onClick = {
            vm.renameSagaGroup(source, sagaRenameTarget)
            sagaRenameSource = null; sagaRenameTarget = ""
        }, enabled = sagaRenameTarget.isNotBlank()) { Text("Unificar") } },
        dismissButton = { LibraryTextButton(onClick = { sagaRenameSource = null }) { Text("Cancelar") } }
    ) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::selectFolder) }
    LaunchedEffect(vm.importedBookUri, vm.books) {
        vm.importedBookUri?.let { uri ->
            vm.books.firstOrNull { it.uri == uri }?.let {
                selected = it
                readingUri = uri.toString()
                vm.recordOpen(uri)
                vm.setStatus(uri, ReadingStatus.READING)
                vm.clearImportedBook()
            }
        }
    }
    val current = selected?.let { s -> vm.books.firstOrNull { it.uri == s.uri } }
    BackHandler(enabled = readingUri == null && (selected != null || showWishList || tab != "Inicio")) {
        when {
            selected != null -> selected = null
            showWishList -> showWishList = false
            else -> switchTab("Inicio")
        }
    }
    val homeListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val genreListState = rememberLazyListState()
    val sectionsListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    val listState = when (tab) {
        "Inicio" -> homeListState
        "Biblioteca" -> libraryListState
        "Géneros" -> genreListState
        "Secciones" -> sectionsListState
        "Favoritos" -> favoritesListState
        else -> sectionsListState
    }
    var restoreListPosition by remember { mutableStateOf(false) }
    var savedListIndex by remember { mutableIntStateOf(0) }
    var savedListOffset by remember { mutableIntStateOf(0) }
    fun startReader(book: Book) {
        if (readerTransitionBusy) return
        readerTransitionBusy = true
        vm.recordOpen(book.uri)
        vm.setStatus(book.uri, ReadingStatus.READING)
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            readingUri = book.uri.toString()
            readerTransitionBusy = false
            return
        }
        scope.launch {
            try {
                if (book.cover != null) coverDecodeSlots.withPermit {
                    withContext(Dispatchers.Default) {
                        decodeCover(book, coverPreviewWidthPx, coverPreviewHeightPx)
                    }
                }
                readerTransition.snapTo(0f)
                pendingReader = book
                // Finish the physical movement before composing the reader behind the page.
                readerTransition.animateTo(0.84f,
                    tween(2600, easing = FastOutSlowInEasing))
                // Deja la portada encuadrada unos instantes antes de mostrar el texto.
                kotlinx.coroutines.delay(900)
                readingUri = book.uri.toString()
                withFrameNanos { }
                readerTransition.animateTo(1f,
                    tween(850, easing = FastOutSlowInEasing))
            } finally {
                pendingReader = null
                readerTransitionBusy = false
            }
        }
    }
    fun openBookFromList(book: Book) {
        savedListIndex = listState.firstVisibleItemIndex
        savedListOffset = listState.firstVisibleItemScrollOffset
        restoreListPosition = true
        selected = book
    }
    LaunchedEffect(selected, restoreListPosition, tab) {
        if (selected == null && restoreListPosition) {
            listState.scrollToItem(savedListIndex, savedListOffset)
            restoreListPosition = false
        }
    }
    // Require a deliberate horizontal gesture. A low threshold made vertical scrolling and
    // taps on cards feel as if the whole screen was trying to change tab.
    val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    val pullRefreshTopInset = with(LocalDensity.current) { 148.dp.toPx() }
    val pullRefreshBottomInset = with(LocalDensity.current) { 80.dp.toPx() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var showSyncReport by remember { mutableStateOf(false) }
    var homeSettings by remember { mutableStateOf(false) }
    var newsSettings by remember { mutableStateOf(false) }
    var organizeSections by remember { mutableStateOf(false) }
    var backupMenu by remember { mutableStateOf(false) }
    var duplicateDialog by remember { mutableStateOf(false) }
    var libraryHealthOpen by remember { mutableStateOf(false) }
    var readingStatsOpen by remember { mutableStateOf(false) }
    var releaseCalendarOpen by remember { mutableStateOf(false) }
    var taskCenterOpen by remember { mutableStateOf(false) }
    var activityOpen by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf("Título") }
    val backupExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }
    val catalogExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportCatalogCsv) }
    val notebookExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(vm::exportReadingNotebook) }
    val isbnCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) BarcodeScanning.getClient().process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { codes ->
                val isbn = codes.asSequence().filter { it.valueType == Barcode.TYPE_ISBN ||
                    it.format == Barcode.FORMAT_EAN_13 || it.format == Barcode.FORMAT_EAN_8 }
                    .mapNotNull { it.rawValue }.firstOrNull()
                if (isbn != null) { vm.query = isbn; switchTab("Biblioteca") }
                else android.widget.Toast.makeText(context, "No se ha detectado un ISBN. Acerca más la cámara.",
                    android.widget.Toast.LENGTH_LONG).show()
            }
    }
    var launchIsbnCamera by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && launchIsbnCamera) isbnCamera.launch(null)
        launchIsbnCamera = false
    }
    var voiceListening by remember { mutableStateOf(false) }
    var voiceTargetPublic by remember { mutableStateOf(false) }
    var startVoiceWhenPermitted by remember { mutableStateOf(false) }
    val speechRecognizer = remember {
        when {
            android.os.Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) ->
                SpeechRecognizer.createSpeechRecognizer(context)
            else -> null
        }
    }
    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { voiceListening = true }
            override fun onBeginningOfSpeech() { voiceListening = true }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { voiceListening = false }
            override fun onError(error: Int) {
                voiceListening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No he entendido la búsqueda. Inténtalo otra vez."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No he oído ninguna búsqueda."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Activa el permiso del micrófono."
                    else -> "No se ha podido reconocer la voz."
                }
                android.widget.Toast.makeText(context, message,
                    android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                voiceListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().trim()
                if (spoken.isNotBlank()) {
                    if (voiceTargetPublic) publicQuery = spoken
                    else { vm.query = spoken; switchTab("Biblioteca") }
                    voiceTargetPublic = false
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val spoken = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().trim()
                if (spoken.isNotBlank()) {
                    if (voiceTargetPublic) publicQuery = spoken else vm.query = spoken
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { speechRecognizer?.destroy() }
    }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceWhenPermitted = true
        else android.widget.Toast.makeText(context, "Se necesita permiso para usar el micrófono.",
            android.widget.Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(startVoiceWhenPermitted) {
        if (!startVoiceWhenPermitted) return@LaunchedEffect
        startVoiceWhenPermitted = false
        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }
    fun launchVoiceSearch(publicSearch: Boolean = false) {
        if (speechRecognizer == null) {
            android.widget.Toast.makeText(context,
                "El reconocimiento de voz no está disponible.",
                android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (voiceListening) { speechRecognizer.cancel(); voiceTargetPublic = false }
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) { voiceTargetPublic = publicSearch; startVoiceWhenPermitted = true }
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val readingBooks = remember(vm.books) { vm.readingBooks() }
    // Secciones mantiene su propia vista, separada de Biblioteca.
    val activeViewScope: String? = if (tab == "Secciones") "__secciones__" else vm.selectedSection
    val activeViewMode = vm.viewModeFor(activeViewScope)
    LaunchedEffect(vm.message) {
        if (vm.message != null) {
            kotlinx.coroutines.delay(2200)
            vm.clearMessage()
        }
    }
    val visibleNews = remember(vm.launchNews, vm.hiddenNewsSources, vm.hiddenNewsUrls) {
        vm.launchNews.filter(vm::isNewsVisible)
    }
    val duplicateSnapshot = vm.books
    var duplicateScanComplete by remember { mutableStateOf(false) }
    val scanDuplicatesNow = selected != null || duplicateDialog || libraryHealthOpen
    val duplicateGroups by produceState(
        initialValue = emptyList<List<Book>>(), duplicateSnapshot, scanDuplicatesNow
    ) {
        if (!scanDuplicatesNow) return@produceState
        duplicateScanComplete = false
        value = withContext(Dispatchers.Default) { findDuplicateGroups(duplicateSnapshot) }
        duplicateScanComplete = true
    }
    val booksSnapshot = vm.books
    val offlineBookUris = remember(booksSnapshot, vm.offlineRevision) {
        booksSnapshot.asSequence().filter { offlineBookUri(context, it) != null }
            .map { it.uri }.toSet()
    }
    val typedQuery = vm.query
    var debouncedQuery by remember { mutableStateOf(typedQuery) }
    LaunchedEffect(typedQuery) {
        if (typedQuery.isBlank()) {
            debouncedQuery = ""
        } else {
            // Esperar una pausa breve evita recalcular resultados por cada tecla.
            delay(180)
            debouncedQuery = typedQuery
        }
    }
    val querySnapshot = debouncedQuery
    val statusSnapshot = vm.statusFilter
    // El destino Favoritos aplica su estado desde el primer fotograma,
    // sin esperar a que el ViewModel cambie después de la navegación.
    val favoritesSnapshot = tab == "Favoritos" || vm.onlyFavorites
    val sectionSnapshot = vm.selectedSection
    val qualitySnapshot = vm.qualityFilter
    val reviewSnapshot = vm.reviewPending
    val hasCatalogFilters = querySnapshot.isNotBlank() || statusSnapshot != null ||
        favoritesSnapshot || sectionSnapshot != null || qualitySnapshot != "Ninguno" ||
        reviewSnapshot.isNotEmpty()
    val baseFilteredBooks by produceState(
        initialValue = if (hasCatalogFilters) emptyList() else booksSnapshot,
        booksSnapshot, querySnapshot, statusSnapshot, favoritesSnapshot,
        sectionSnapshot, qualitySnapshot, reviewSnapshot
    ) {
        value = if (!hasCatalogFilters) booksSnapshot else withContext(Dispatchers.Default) {
            val exactMatches = if (querySnapshot.isBlank()) emptySet()
                else catalogSearchIndex.matchingBookUris(booksSnapshot, querySnapshot)
            val fuzzyCandidates = if (shouldUseFuzzyCatalogFallback(exactMatches, querySnapshot))
                CatalogQuality.rank(booksSnapshot, querySnapshot) else null
            val searchMatches = when {
                querySnapshot.isBlank() -> null
                fuzzyCandidates != null -> fuzzyCandidates.mapTo(hashSetOf()) { it.uri.toString() }
                else -> exactMatches
            }
            val filtered = filterBooks(booksSnapshot, querySnapshot, statusSnapshot, favoritesSnapshot,
                sectionSnapshot, qualitySnapshot, reviewSnapshot, searchMatches)
            when {
                querySnapshot.isBlank() -> filtered
                fuzzyCandidates != null -> {
                    val allowed = filtered.mapTo(hashSetOf()) { it.uri.toString() }
                    fuzzyCandidates.filter { it.uri.toString() in allowed }
                }
                shouldRankCatalogSearch(querySnapshot) -> CatalogQuality.rank(filtered, querySnapshot)
                else -> filtered
            }
        }
    }
    val filteredBooks = remember(baseFilteredBooks, smartCollection) {
        when (smartCollection) {
            "abandonados" -> baseFilteredBooks.filter { it.status == ReadingStatus.PENDING &&
                vm.readingPercent(it.uri) in 1..99 }
            "sin_terminar" -> baseFilteredBooks.filter { vm.readingPercent(it.uri) in 1..99 &&
                it.status != ReadingStatus.READ }
            "pendientes" -> baseFilteredBooks.filter { it.status == ReadingStatus.PENDING &&
                it.startedDate.isBlank() && vm.readingPercent(it.uri) == 0 }
            else -> baseFilteredBooks
        }
    }
    val smartBooks = filteredBooks
    val visibleBooks = if (tab == "Inicio") emptyList() else smartBooks
    val genreSourceBooks = if (tab == "Géneros") visibleBooks else emptyList()
    val genreOptions by produceState(initialValue = listOf("Todos"), genreSourceBooks) {
        value = withContext(Dispatchers.Default) {
            val known = linkedSetOf<String>()
            var hasUnclassified = false
            genreSourceBooks.forEach { book ->
                val genres = normalizeBookGenres(book.genre)
                if (genres.isEmpty()) hasUnclassified = true else known.addAll(genres)
            }
            listOf("Todos") + known.sortedWith(String.CASE_INSENSITIVE_ORDER) +
                if (hasUnclassified) listOf("Sin clasificar") else emptyList()
        }
    }
    val allGenres = remember(genreOptions) { genreOptions.filterNot { it == "Todos" } }
    val activeGenres = if (tab == "Géneros" && !genreAllSelected)
        selectedGenres intersect allGenres.toSet() else emptySet()
    val matchingGenres = remember(allGenres, genreSearch) {
        val query = genreSearch.trim()
        if (query.isBlank()) allGenres
        else allGenres.filter { it.contains(query, ignoreCase = true) }
    }
    fun updateGenreSelection(genre: String, checked: Boolean) {
        if (checked) {
            if (draftAllGenres) return
            val next = draftGenres + genre
            if (allGenres.isNotEmpty() && next.containsAll(allGenres)) {
                draftAllGenres = true
                draftGenres = emptySet()
            } else draftGenres = next
        } else {
            draftGenres = (if (draftAllGenres) allGenres.toSet() else draftGenres) - genre
            draftAllGenres = false
        }
    }
    LaunchedEffect(tab, genreOptions) {
        if (tab == "Géneros" && allGenres.isNotEmpty() &&
            !genreAllSelected && selectedGenres.isNotEmpty()) {
            selectedGenres = selectedGenres intersect allGenres.toSet()
            if (selectedGenres.containsAll(allGenres) && allGenres.isNotEmpty()) {
                selectedGenres = emptySet()
                genreAllSelected = true
            }
        }
    }
    LaunchedEffect(genrePickerOpen, genreSearch) {
        if (genrePickerOpen) genrePickerListState.scrollToItem(0)
    }
    if (genrePickerOpen) AlertDialog(
        onDismissRequest = { genrePickerOpen = false; genreSearch = "" },
        title = { Text("Filtrar géneros") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = genreSearch, onValueChange = { genreSearch = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (genreSearch.isNotEmpty()) IconButton(onClick = { genreSearch = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Borrar búsqueda")
                        }
                    },
                    placeholder = { Text("Escribe para filtrar géneros") }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (genreSearch.isBlank()) "${matchingGenres.size} géneros disponibles"
                    else "${matchingGenres.size} resultados",
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = Mahogany
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = { draftAllGenres = true; draftGenres = emptySet() },
                        enabled = !draftAllGenres
                    ) { Text("Marcar todos") }
                    TextButton(
                        onClick = { draftAllGenres = false; draftGenres = emptySet() },
                        enabled = draftAllGenres || draftGenres.isNotEmpty()
                    ) { Text("Desmarcar todos") }
                }
                LazyColumn(state = genrePickerListState, modifier = Modifier.heightIn(max = 320.dp)) {
                    items(matchingGenres, key = { it }) { genre ->
                        val checked = draftAllGenres || genre in draftGenres
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = checked, role = Role.Checkbox
                            ) { updateGenreSelection(genre, !checked) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(genre, modifier = Modifier.weight(1f))
                        }
                    }
                    if (matchingGenres.isEmpty()) item {
                        Text("No hay géneros que coincidan con «${genreSearch.trim()}».",
                            Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                genreAllSelected = draftAllGenres
                selectedGenres = if (draftAllGenres) emptySet() else draftGenres intersect allGenres.toSet()
                genrePickerOpen = false
                genreSearch = ""
            }) { Text("Listo") }
        }
    )
    val groupMode = if (tab == "Secciones") "Secciones" else if (tab == "Géneros") "Géneros" else if (tab == "Inicio") "Todos" else vm.groupMode
    val sectionNames = vm.sections
    // Keep the most recent groups when returning to a tab; new book data clears the cache.
    val groupingCache = remember(booksSnapshot) {
        mutableMapOf<List<Any?>, Map<String, List<Book>>>()
    }
    val groupingKey = remember(groupMode, sectionNames, sortMode, tab, querySnapshot,
        statusSnapshot, favoritesSnapshot, sectionSnapshot, qualitySnapshot, reviewSnapshot) {
        listOf(groupMode, sectionNames, sortMode, tab, querySnapshot, statusSnapshot,
            favoritesSnapshot, sectionSnapshot, qualitySnapshot, reviewSnapshot)
    }
    val groupedBooks by key(groupingKey, visibleBooks) {
      produceState(
          if (groupMode == "Todos") mapOf("" to visibleBooks)
          else groupingCache[groupingKey].orEmpty(),
          visibleBooks, groupingKey
      ) {
        if (groupMode == "Todos") {
            value = mapOf("" to visibleBooks)
            return@produceState
        }
        groupingCache[groupingKey]?.takeIf { it.isNotEmpty() || visibleBooks.isEmpty() }?.let { cached ->
            value = cached
            return@produceState
        }
        val booksToGroup = visibleBooks
        val result: Map<String, List<Book>> = withContext(Dispatchers.Default) {
        val titles = if (sortMode !in setOf("Autor", "Recientes") || groupMode == "Sagas")
            booksToGroup.associate { it.uri to displayTitle(it) } else emptyMap()
        val authors = if (groupMode == "Autores" || sortMode == "Autor")
            booksToGroup.associate { it.uri to displayAuthor(it) } else emptyMap()
        val genres = if (sortMode == "Género") booksToGroup.associate { book ->
            book.uri to normalizeBookGenres(book.genre).firstOrNull().orEmpty().ifBlank { "Sin clasificar" }
        } else emptyMap()
        val sagaOrders = if (groupMode == "Sagas") booksToGroup.associate { it.uri to sagaNumber(it) }
            else emptyMap()
        val sagaDisplayNames = if (groupMode == "Sagas") booksToGroup
            .map { sagaGroupKey(it) to inferredSaga(it).first }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key.orEmpty() }
        else emptyMap()
        val groups = when (groupMode) {
            "Autores" -> booksToGroup.groupBy { authors.getValue(it.uri) }
            "Sagas" -> booksToGroup.groupBy { sagaGroupKey(it).ifBlank { "Sin saga" } }
            "Géneros" -> {
                val genreGroups = linkedMapOf<String, MutableList<Book>>()
                booksToGroup.forEach { book ->
                    val labels = normalizeBookGenres(book.genre)
                    if (labels.isEmpty()) {
                        genreGroups.getOrPut("Sin clasificar") { mutableListOf() }.add(book)
                    } else {
                        labels.forEach { name ->
                            genreGroups.getOrPut(name) { mutableListOf() }.add(book)
                        }
                    }
                }
                genreGroups
            }
            "Secciones" -> booksToGroup.flatMap { book ->
                bookSections(book).ifEmpty { listOf("Sin sección") }.map { name -> name to book }
            }.groupBy({ it.first }, { it.second })
            else -> mapOf("" to booksToGroup)
        }
        val rawNames = if (tab == "Secciones")
            sectionNames.filter(groups::containsKey) +
                groups.keys.filterNot { it in sectionNames }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        else groups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val groupedResult: Map<String, List<Book>> = rawNames.associate { rawName: String ->
            val name = if (groupMode == "Sagas" && rawName != "Sin saga")
                sagaDisplayNames[rawName].orEmpty().ifBlank { rawName } else rawName
            val group = groups[rawName].orEmpty()
            val sortedGroup: List<Book> = if (groupMode == "Sagas") group.sortedWith(
                compareBy<Book> { sagaOrders.getValue(it.uri) }.thenBy(String.CASE_INSENSITIVE_ORDER) { titles.getValue(it.uri) })
            else when (sortMode) {
                "Autor" -> group.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { authors.getValue(it.uri) })
                "Género" -> group.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER) { value: Book -> genres.getValue(value.uri) }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { titles.getValue(it.uri) })
                "Recientes" -> group.sortedByDescending { it.sourceModified }
                else -> group.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { titles.getValue(it.uri) })
            }
            name to sortedGroup
        }
        groupedResult
        }
        if (groupingCache.size >= 8) groupingCache.clear()
        groupingCache[groupingKey] = result
        value = result
      }
    }
    val genreDisplayGroups = remember(groupedBooks, activeGenres, genreAllSelected, tab) {
        if (tab == "Géneros" && !genreAllSelected)
            groupedBooks.filterKeys { it in activeGenres }
        else groupedBooks
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
            confirmButton = { LibraryTextButton(onClick = {
                vm.acceptCandidate(uri, found.copy(author = authorDraft.trim(),
                    plot = plotDraft.trim(), bio = bioDraft.trim(),
                    plotSource = if (plotDraft.trim() == found.plot) found.plotSource else "Revisado por ti",
                    bioSource = if (bioDraft.trim() == found.bio) found.bioSource else "Revisado por ti"))
            }) { Text("Guardar datos") } },
            dismissButton = { LibraryTextButton(onClick = vm::dismissCandidate) { Text("Cancelar") } })
    }
    if (organizeSections) AlertDialog(onDismissRequest = { organizeSections = false },
        title = { Text("Organizar secciones") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            vm.sections.forEach { section ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(section, Modifier.weight(1f))
                    IconButton(onClick = { vm.moveSection(section, -1) }) { AppIcon("Subir", "Subir sección") }
                    IconButton(onClick = { vm.moveSection(section, 1) }) { AppIcon("Bajar", "Bajar sección") }
                    IconButton(onClick = {
                        sectionToRename = section
                        renameSectionName = section
                    }) { AppIcon("Editar", "Cambiar nombre de sección") }
                    IconButton(onClick = { sectionToDelete = section }) { AppIcon("Borrar", "Eliminar sección") }
                }
            }
            if (vm.sections.isEmpty()) Text("Aún no hay secciones.")
        } },
        confirmButton = { LibraryTextButton(onClick = { organizeSections = false }) { Text("Cerrar") } })
    sectionToRename?.let { original -> AlertDialog(
        onDismissRequest = { sectionToRename = null },
        title = { Text("Cambiar nombre") },
        text = { OutlinedTextField(renameSectionName, { renameSectionName = it },
            label = { Text("Nombre de la sección") }, singleLine = true) },
        confirmButton = { LibraryTextButton(onClick = {
            vm.renameSection(original, renameSectionName)
            sectionToRename = null
        }) { Text("Guardar") } },
        dismissButton = { LibraryTextButton(onClick = { sectionToRename = null }) { Text("Cancelar") } }
    ) }
    if (duplicateDialog) AlertDialog(onDismissRequest = { duplicateDialog = false },
        title = { Text("Posibles duplicados") },
        text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
            if (!duplicateScanComplete) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Comparando libros…")
                }
            } else if (duplicateGroups.isEmpty()) {
                Text("No se han encontrado posibles duplicados.")
            } else duplicateGroups.forEach { group ->
                Text(displayTitle(group.first()), fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, color = Mahogany)
                group.forEach { copy ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(displayAuthor(copy) + " · " + readableSize(copy.sourceSize),
                            modifier = Modifier.weight(1f), fontSize = 12.sp)
                        LibraryTextButton(onClick = { duplicateDialog = false; selected = copy }) {
                            Text("Revisar")
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        } },
        confirmButton = { LibraryTextButton(onClick = { duplicateDialog = false }) { Text("Cerrar") } })
    vm.newBooksNotice?.let { titles ->
        AlertDialog(onDismissRequest = vm::clearNewBooksNotice,
            title = { Text("Nuevas incorporaciones") },
            text = { Column {
                Text("La sincronización ha encontrado estos libros nuevos:")
                Spacer(Modifier.height(10.dp))
                Text(titles, fontWeight = FontWeight.SemiBold)
            } },
            confirmButton = {
                LibraryTextButton(onClick = vm::clearNewBooksNotice) { Text("Aceptar") }
            })
    }
    if (showSyncReport) AlertDialog(onDismissRequest = { showSyncReport = false },
        title = { Text("Sincronización de Drive") },
        text = { Column {
            Text(vm.syncReport)
            Spacer(Modifier.height(14.dp))
            Text("Diagnóstico", fontWeight = FontWeight.Bold)
            Text(vm.driveDiagnosticText(), fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Text("Fluidez de esta sesión", fontWeight = FontWeight.Bold)
            Text((context as? MainActivity)?.performanceReport().orEmpty(), fontSize = 12.sp)
        } },
        dismissButton = {
            LibraryTextButton(onClick = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(
                    "Diagnóstico de MiBiblioteca", vm.driveDiagnosticText()))
                android.widget.Toast.makeText(context, "Diagnóstico copiado", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Copiar diagnóstico") }
        },
        confirmButton = { LibraryTextButton(onClick = { showSyncReport = false }) { Text("Cerrar") } })
    if (libraryHealthOpen) AlertDialog(onDismissRequest = { libraryHealthOpen = false },
        title = { Text("Estado de la biblioteca") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            val noCover = vm.books.count { it.cover == null }
            val noAuthor = vm.books.count { unknownAuthor(displayAuthor(it)) }
            val noPlot = vm.books.count { it.spanishPlot.isBlank() }
            val noBio = vm.books.count { it.authorBio.isBlank() }
            val noSaga = vm.books.count { it.saga.isBlank() }
            val duplicates = duplicateGroups.sumOf { (it.size - 1).coerceAtLeast(0) }
            Text("${vm.books.size} libros analizados", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Info("Sin portada", noCover.toString())
            Info("Autor sin identificar", noAuthor.toString())
            Info("Sin argumento", noPlot.toString())
            Info("Sin biografía del autor", noBio.toString())
            Info("Sin saga identificada", noSaga.toString())
            Info("Copias posiblemente duplicadas", duplicates.toString())
            Spacer(Modifier.height(8.dp))
            LibraryActionButton(onClick = {
                libraryHealthOpen = false
                vm.qualityFilter = "Revisar"
                switchTab("Secciones")
            }, modifier = Modifier.fillMaxWidth()) { Text("Revisar fichas incompletas") }
        } },
        confirmButton = { LibraryTextButton(onClick = { libraryHealthOpen = false }) {
            Text("Cerrar")
        } })
    if (readingStatsOpen) {
        val detailedStats = calculateReadingStats(vm.books.map { book ->
            ReadingStatsInput(book.status, vm.readingPercent(book.uri), book.finishedDate,
                book.rating, normalizeBookGenres(book.genre), displayAuthor(book), book.rereadCount)
        }, annualGoal = vm.annualReadingGoal)
        AlertDialog(onDismissRequest = { readingStatsOpen = false },
            title = { Text("Resumen completo") },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Info("Libros terminados", detailedStats.totalRead.toString())
                Info("Terminados este año", detailedStats.currentYearRead.toString())
                Info("Terminados este mes", detailedStats.currentMonthRead.toString())
                Info("Racha de meses con lecturas", detailedStats.monthStreak.toString())
                Info("Proyección al final de año", detailedStats.projectedYearTotal.toString())
                Info("Leyendo ahora", detailedStats.reading.toString())
                Info("Progreso medio activo", "${detailedStats.averageActiveProgress} %")
                Info("Pendientes", detailedStats.pending.toString())
                Info("Relecturas registradas", detailedStats.rereads.toString())
                Info("Valoración media", detailedStats.averageRating?.let {
                    String.format(java.util.Locale.getDefault(), "%.1f / 5", it)
                } ?: "Sin valorar")
                Info("Libros con citas guardadas", vm.books.count { it.quotes.isNotBlank() }.toString())
                Info("Leídos sin fecha final válida", detailedStats.readWithoutFinishDate.toString())
                val completedSagas = vm.sagaCatalogs.count { (name, catalog) ->
                    val owned = vm.books.filter { it.saga.equals(name, true) }
                    catalog.isNotEmpty() && catalog.all { volume -> owned.any { b ->
                        b.sagaOrder == volume.order || displayTitle(b).equals(volume.title, true)
                    } }
                }
                Info("Sagas completas comprobadas", completedSagas.toString())
                if (detailedStats.topGenres.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text("Géneros realmente leídos", fontWeight = FontWeight.Bold)
                    detailedStats.topGenres.forEach { (name, count) ->
                        Text("$name: $count", fontSize = 12.sp)
                    }
                }
                if (detailedStats.topAuthors.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text("Autores más leídos", fontWeight = FontWeight.Bold)
                    detailedStats.topAuthors.forEach { (name, count) ->
                        Text("$name: $count", fontSize = 12.sp)
                    }
                }
            } },
            confirmButton = { LibraryTextButton(onClick = { readingStatsOpen = false }) {
                Text("Cerrar")
            } })
    }
    if (releaseCalendarOpen) AlertDialog(onDismissRequest = { releaseCalendarOpen = false },
        title = { Text("Calendario de publicaciones") },
        text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
            Text("Próximos lanzamientos de tus fuentes literarias", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val dated = vm.launchNews.filter { it.releaseDate.isNotBlank() }
                .sortedBy { it.releaseDate }
            if (dated.isEmpty()) Text("Actualiza las noticias para consultar próximas fechas.")
            dated.forEach { news ->
                Text(news.releaseDate, color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(news.title, color = Mahogany, fontSize = 13.sp)
                Text(news.source, color = Mahogany.copy(alpha = .7f), fontSize = 11.sp)
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
            }
        } },
        confirmButton = { LibraryTextButton(onClick = { releaseCalendarOpen = false }) { Text("Cerrar") } })
    if (taskCenterOpen) AlertDialog(onDismissRequest = { taskCenterOpen = false },
        title = { Text("Centro de tareas") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Info("Sincronización de Drive", if (vm.syncing) "Revisando ${vm.syncCount} libros" else vm.syncSummary.ifBlank { "En espera" })
            Info("Portadas", if (vm.coverUpdateRunning) "${vm.coverUpdateDone}/${vm.coverUpdateTotal}" else
                "${vm.books.count { it.cover == null }} pendientes")
            Info("Sagas", if (vm.sagaOrganizing) "${vm.sagaOrganized}/${vm.sagaOrganizeTotal}" else
                "${vm.books.count { it.saga.isBlank() }} sin identificar")
            Info("Géneros", if (vm.genreClassifying) "${vm.genreProgress}/${vm.genreTotal}" else
                "${vm.books.count { normalizeBookGenres(it.genre).isEmpty() }} sin clasificar")
            Info("Fichas para revisar", vm.reviewPending.size.toString())
            Spacer(Modifier.height(8.dp))
            LibraryActionButton(onClick = vm::sync, enabled = !vm.syncing,
                modifier = Modifier.fillMaxWidth()) { Text("Actualizar Drive") }
            LibraryActionButton(onClick = vm::forceMissingCovers, enabled = !vm.coverUpdateRunning,
                modifier = Modifier.fillMaxWidth()) { Text("Buscar portadas pendientes") }
            LibraryActionButton(onClick = vm::organizeAllSagas, enabled = !vm.sagaOrganizing,
                modifier = Modifier.fillMaxWidth()) { Text("Identificar sagas pendientes") }
            LibraryActionButton(onClick = { vm.classifyGenres(true) }, enabled = !vm.genreClassifying,
                modifier = Modifier.fillMaxWidth()) { Text("Clasificar géneros pendientes") }
        } },
        confirmButton = { LibraryTextButton(onClick = { taskCenterOpen = false }) { Text("Cerrar") } })
    if (activityOpen) AlertDialog(onDismissRequest = { activityOpen = false },
        title = { Text("Actividad reciente") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            if (vm.activityLog.isEmpty()) Text("Todavía no hay actividad registrada.")
            vm.activityLog.forEach { Text(it, modifier = Modifier.padding(vertical = 5.dp),
                fontSize = 12.sp, color = Mahogany) }
        } },
        confirmButton = { LibraryTextButton(onClick = { activityOpen = false }) { Text("Cerrar") } })
    if (homeSettings) AlertDialog(onDismissRequest = { homeSettings = false },
        title = { Text("Organizar Inicio") },
        text = { Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar noticias", Modifier.weight(1f))
                Switch(vm.showHomeNews, vm::setHomeNews)
            }
        } },
        confirmButton = { LibraryTextButton(onClick = { homeSettings = false }) { Text("Cerrar") } })
    if (newsSettings) AlertDialog(onDismissRequest = { newsSettings = false },
        title = { Text("Fuentes de noticias") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            (vm.launchNews.map { it.source } + featuredNewsSources.keys).distinct().forEach { source ->
                Row(Modifier.fillMaxWidth().clickable { vm.toggleNewsSource(source) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(source !in vm.hiddenNewsSources,
                        onCheckedChange = { vm.toggleNewsSource(source) })
                    Text(source)
                }
            }
            LibraryTextButton(onClick = vm::showAllNews) { Text("Recuperar noticias ocultas") }
        } },
        confirmButton = { LibraryTextButton(onClick = { newsSettings = false }) { Text("Cerrar") } })
    val shown = current
    LaunchedEffect(tab, shown?.uri) {
        (context as? MainActivity)?.setMetricScreen(if (shown != null) "Ficha" else tab)
    }
    bookMenu?.let { picked ->
        val book = vm.books.firstOrNull { it.uri == picked.uri } ?: picked
        ModalBottomSheet(onDismissRequest = { bookMenu = null }, containerColor = Paper) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(displayTitle(book), fontFamily = FontFamily.Serif, fontSize = 21.sp,
                    fontWeight = FontWeight.Bold, color = Mahogany, maxLines = 2)
                Text(displayAuthor(book), color = Teal, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                fun close(action: () -> Unit) { bookMenu = null; action() }
                @Composable fun option(label: String, icon: String, action: () -> Unit) {
                    NavigationDrawerItem(label = { Text(label) }, selected = false,
                        icon = { AppIcon(icon, size = 21.dp) }, onClick = { close(action) })
                }
                option("Abrir ficha", "Libros") { openBookFromList(book) }
                option("Compartir archivo", "Compartir") { shareBookFile(context, book) }
                option("Recuperar portada", "Portada") { vm.downloadCover(book) }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (book.uri in offlineBookUris)
                        option("Borrar copia sin conexión", "Borrar") { vm.removeOfflineCopy(book) }
                    else option("Descargar para leer sin conexión", "Descargar") {
                        vm.downloadForOffline(book)
                    }
                }
                if (book.favorite)
                    option("Quitar de favoritos", "Favoritos") { vm.toggleFavorite(book.uri) }
                else
                    option("Añadir a favoritos", "Favorito") { vm.toggleFavorite(book.uri) }
                if (book.status == ReadingStatus.READING)
                    option("Quitar de Estoy leyendo", "Cerrar") { vm.setStatus(book.uri, ReadingStatus.PENDING) }
                option("Búsqueda IA del libro", "Búsqueda IA") {
                    searchBookInGoogleAi(context, book)
                }
                option("Ver reseña", "Reseña") { openGoodreads(context, book) }
                option("Buscar información del autor", "Buscar") {
                    searchInGoogleApp(context, displayAuthor(book) + " biografía escritor")
                }
            }
        }
    }
    val readingBook = readingUri?.let { key -> vm.books.firstOrNull { it.uri.toString() == key } }
    Box(Modifier.fillMaxSize()) {
    if (readingBook != null) {
        ReaderScreen(readingBook, onBack = { readingUri = null },
            onProgress = { percent -> vm.updateReadingPercent(readingBook.uri, percent) },
            onHighlightsChanged = vm::readerAnnotationsChanged)
    } else if (shown != null) {
            BookDetail(shown, { selected = null }, { vm.toggleFavorite(shown.uri) },
                { vm.recordOpen(shown.uri) },
                { vm.setStatus(shown.uri, it) }, { startReader(shown) },
                vm.message, duplicateGroups.firstOrNull { group -> group.any { it.uri == shown.uri } }
                    ?.filterNot { it.uri == shown.uri }.orEmpty(),
                { vm.deleteDuplicate(shown) }, { vm.completeMissing(shown.uri, force = true) },
                { vm.downloadCover(shown) },
                vm.autoInfoLoading.contains(shown.uri),
                vm.autoCoverLoading.contains(shown.uri) || vm.coverLoading == shown.uri,
                vm.sections, { vm.assignSection(shown.uri, it) },
                { vm.saveNotes(shown.uri, it) }, { plot, bio -> vm.saveManualInfo(shown.uri, plot, bio) },
                { vm.replaceCover(shown.uri, it) },
                { vm.setGoodreadsUrl(shown.uri, it) }, { vm.toggleWantToRead(shown.uri) },
                vm.detailMessage, shown.uri in vm.reviewPending,
                { vm.confirmBookDetails(shown.uri) },
                { title, author, saga, order -> vm.editIdentity(shown.uri, title, author, saga, order) },
                shown.uri in vm.genreSearches, { vm.searchGenre(shown.uri) },
                { vm.updateGenre(shown.uri, it) },
                shown.uri in vm.sagaSearches, { vm.searchSaga(shown.uri) },
                vm.sagaCatalog(shown.saga),
                vm.sagaCatalogLoading(shown.saga),
                { vm.searchSagaCatalog(shown.saga, shown) },
                { vm.downloadForOffline(shown) }, vm.offlineDownloadUri == shown.uri,
                vm.offlineDownloadPercent, vm.offlineDownloadMessage,
                shown.uri in offlineBookUris, { vm.removeOfflineCopy(shown) },
                vm.wallpaperMode, vm.books,
                { rating, started, finished, quotes ->
                    vm.saveReadingRecord(shown.uri, rating, started, finished, quotes)
                }, {
                    selected = null; vm.query = displayAuthor(shown); vm.groupMode = "Autores"
                    switchTab("Biblioteca")
                }, {
                    selected = null; vm.query = shown.saga; vm.groupMode = "Sagas"
                    switchTab("Biblioteca")
                }, { tags, loanTo, loanDate, rereads ->
                    vm.saveBookManagement(shown.uri, tags, loanTo, loanDate, rereads)
                })
        } else {
    val currentTab by rememberUpdatedState(tab)
    val currentSwitch by rememberUpdatedState<(String) -> Unit>({ switchTab(it) })
    var startedOnCarousel by remember { mutableStateOf(false) }
    val mainSwipeModifier = Modifier.pointerInput(swipeThreshold, pageWidthPx) {
        awaitEachGesture {
            // Let buttons, chips and selectors consume their own touch first.  The old
            // Initial-pass listener interpreted a tap/drag over a control as a page swipe.
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
            startedOnCarousel = false
            val start = down.position
            var end = start
            var verticalGesture = false
            var draggingPage = false
            while (true) {
                // Observe before clickable controls consume the movement. Horizontal carousels
                // keep their own drag, while vertical lists continue to own vertical scrolling.
                val change = awaitPointerEvent(PointerEventPass.Main).changes
                    .firstOrNull { it.id == down.id } ?: break
                end = change.position
                if (!change.pressed) break
                val horizontal = end.x - start.x
                val vertical = end.y - start.y
                if (!draggingPage && kotlin.math.abs(vertical) > swipeThreshold * 0.30f &&
                    kotlin.math.abs(vertical) > kotlin.math.abs(horizontal) * 1.35f)
                    verticalGesture = true
                if (!verticalGesture && !startedOnCarousel &&
                    (draggingPage || (kotlin.math.abs(horizontal) > swipeThreshold * 0.55f &&
                        kotlin.math.abs(horizontal) > kotlin.math.abs(vertical) * 1.65f))) {
                    draggingPage = true
                    pageDrag.floatValue = horizontal.coerceIn(-pageWidthPx, pageWidthPx)
                    change.consume()
                }
            }
            val horizontal = end.x - start.x
            val vertical = end.y - start.y
            if (draggingPage && kotlin.math.abs(horizontal) > swipeThreshold) {
                val index = tabs.indexOf(currentTab)
                val next = index + if (horizontal < 0) 1 else -1
                if (next in tabs.indices) currentSwitch(tabs[next])
                else settlePage()
            } else if (!draggingPage && currentTab == "Inicio" &&
                !startedOnCarousel &&
                start.y > pullRefreshTopInset && start.y < size.height - pullRefreshBottomInset &&
                vertical > swipeThreshold * 1.8f &&
                listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0 && !vm.newsRefreshing) {
                vm.refreshLaunchNews(true)
            } else if (draggingPage) {
                settlePage()
            }
        }
    }
    ModalNavigationDrawer(drawerState = drawerState, gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Paper,
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.width(320.dp)) {
                Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp)) {
                    if (settingsOpen) {
                        NavigationDrawerItem(label = { Text("Volver al menú") }, selected = false,
                            icon = { Icon(Icons.Outlined.ArrowBack, null) },
                            onClick = { settingsOpen = false })
                        Text("Ajustes", color = Mahogany, fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
                        Text("Biblioteca y Drive", color = Teal, fontWeight = FontWeight.Bold)
                        NavigationDrawerItem(label = { Text("Carpeta de libros") }, selected = false,
                            icon = { AppIcon("Carpeta") }, onClick = {
                                scope.launch { drawerState.close() }; folderPicker.launch(null)
                            })
                        NavigationDrawerItem(label = { Text(if (vm.syncing) "Sincronizando…" else "Sincronizar biblioteca") },
                            selected = false, icon = { AppIcon("Sincronizar") },
                            onClick = { if (!vm.syncing) vm.sync() })
                        NavigationDrawerItem(label = { Text("Estado de sincronización en Drive") },
                            selected = false, icon = { AppIcon("Estado") }, onClick = {
                                scope.launch { drawerState.close() }; showSyncReport = true
                            })
                        NavigationDrawerItem(label = { Text("Buscar datos y portadas") }, selected = false,
                            icon = { AppIcon("Datos") }, onClick = {
                                if (vm.bulkInfoLoading) vm.cancelIncomplete() else vm.refreshIncomplete()
                            })
                        NavigationDrawerItem(label = { Text("Consultar libros públicos") }, selected = false,
                            icon = { Icon(Icons.Outlined.Search, null) }, onClick = {
                                publicSearchOpen = true
                                scope.launch { drawerState.close() }
                            })
                        NavigationDrawerItem(
                            label = { Text(if (vm.coverUpdateRunning)
                                "Buscando portadas ${vm.coverUpdateDone}/${vm.coverUpdateTotal}…"
                            else "Forzar portadas que faltan") },
                            selected = vm.coverUpdateRunning, icon = { AppIcon("Portada") },
                            onClick = { if (!vm.coverUpdateRunning) vm.forceMissingCovers() })
                        NavigationDrawerItem(
                            label = { Text(if (vm.sagaOrganizing)
                                "Identificando sagas ${vm.sagaOrganized}/${vm.sagaOrganizeTotal}…"
                            else "Identificar todas las sagas") },
                            selected = vm.sagaOrganizing, icon = { AppIcon("Sagas") },
                            onClick = { if (!vm.sagaOrganizing) vm.organizeAllSagas() })
                        NavigationDrawerItem(
                            label = { Text(if (vm.genreClassifying)
                                "Clasificando géneros " + vm.genreProgress + "/" + vm.genreTotal + "…" else "Clasificar biblioteca por géneros") },
                            selected = false, icon = { Icon(Icons.Outlined.Category, null) },
                            onClick = { if (!vm.genreClassifying) vm.classifyGenres() })
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("Fondo de las pantallas", color = Teal, fontWeight = FontWeight.Bold)
                        NavigationDrawerItem(label = { Text("Fondo: " + vm.wallpaperMode) },
                            selected = false, icon = { Icon(Icons.Outlined.Wallpaper, null) },
                            onClick = { wallpaperPickerOpen = true })
                        NavigationDrawerItem(label = { Text("Animaciones: " + vm.animationMode) },
                            selected = false, icon = { Icon(Icons.Outlined.Animation, null) },
                            onClick = { animationPickerOpen = true })
                        Text("Automático sigue el género del último libro abierto.",
                            color = Mahogany, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("Inicio y noticias", color = Teal, fontWeight = FontWeight.Bold)
                        NavigationDrawerItem(label = { Text("Organizar Inicio") }, selected = false,
                            icon = { AppIcon("Ajustes") }, onClick = {
                                scope.launch { drawerState.close() }; homeSettings = true
                            })
                        NavigationDrawerItem(label = { Text("Fuentes de noticias") }, selected = false,
                            icon = { AppIcon("Noticias") }, onClick = {
                                scope.launch { drawerState.close() }; newsSettings = true
                            })
                        NavigationDrawerItem(label = { Text("Actualizar noticias") }, selected = false,
                            icon = { AppIcon("Noticias") }, onClick = {
                                if (!vm.newsRefreshing) vm.refreshLaunchNews(true)
                            })
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        NavigationDrawerItem(label = { Text("Opciones de biblioteca") }, selected = false,
                            icon = { AppIcon("Opciones") }, onClick = {
                                scope.launch { drawerState.close() }; backupMenu = true
                            })
                    } else {
                    Surface(color = Mahogany,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Mi Biblioteca", color = Color.White, fontSize = 23.sp,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                            Text("By Diroka77", color = Color(0xFFE9D8BD), fontSize = 12.sp)
                            Text("Tu espacio de lectura", color = Color(0xFFF4E8D8),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    // La navegación principal ya está disponible en la barra inferior.
                    // Se mantiene aquí únicamente el contenido de gestión y exploración.
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    Text("EXPLORAR", color = Teal,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
                    NavigationDrawerItem(label = { Text("Quiero leer") }, selected = showWishList,
                        icon = { AppIcon("Pendientes") }, onClick = {
                            switchTab("Biblioteca", showPending = !showWishList)
                            scope.launch { drawerState.close() }
                        })
                    NavigationDrawerItem(label = { Text("Sagas y colecciones") },
                        selected = tab == "Biblioteca" && vm.groupMode == "Sagas" && !showWishList,
                        icon = { AppIcon("Sagas") }, onClick = {
                            showWishList = false
                            vm.onlyFavorites = false
                            vm.statusFilter = null
                            vm.groupMode = if (vm.groupMode == "Sagas" && tab == "Biblioteca") "Todos" else "Sagas"
                            switchTab("Biblioteca")
                            scope.launch { drawerState.close() }
                        })
                    if (showWishList) LibraryTextButton(onClick = { addWishDialog = true }) { Text("Añadir libro deseado") }
                    if (tab == "Biblioteca" && !showWishList) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("MOSTRAR", color = Teal,
                            style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Todos", "Leyendo", "Leídos").forEach { kind ->
                                val status = when (kind) {
                                    "Leyendo" -> ReadingStatus.READING
                                    "Leídos" -> ReadingStatus.READ
                                    else -> null
                                }
                                CatalogChip(kind, vm.statusFilter == status && !vm.onlyFavorites) {
                                    vm.statusFilter = if (status != null && vm.statusFilter == status) null else status
                                    vm.onlyFavorites = false
                                }
                            }
                        }
                        Text("AGRUPAR POR", color = Teal, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 14.dp))
                        listOf("Todos", "Autores", "Sagas", "Géneros", "Secciones").forEach { mode ->
                            NavigationDrawerItem(label = { Text(mode) }, selected = vm.groupMode == mode,
                                onClick = {
                                    vm.groupMode = if (mode != "Todos" && vm.groupMode == mode) "Todos" else mode
                                    scope.launch { drawerState.close() }
                                })
                        }
                    }
                    if (tab == "Secciones") {
                        Text("Datos de las fichas", color = Mahogany, fontWeight = FontWeight.SemiBold)
                        listOf("Todos", "Revisar", "Portada", "Argumento", "Biografía", "Autor").forEach { kind ->
                            NavigationDrawerItem(label = { Text(kind) }, selected = vm.qualityFilter == kind,
                                onClick = { vm.qualityFilter = if (kind == "Todos" || vm.qualityFilter == kind) "Ninguno" else kind;
                                    scope.launch { drawerState.close() } })
                        }
                    }
                    if (tab == "Secciones") {
                        Text("MIS SECCIONES", color = Teal,
                            style = MaterialTheme.typography.labelMedium)
                        (listOf("Todas") + vm.sections).forEach { name ->
                            NavigationDrawerItem(label = { Text(name) },
                                selected = vm.selectedSection == name || name == "Todas" && vm.selectedSection == null,
                                onClick = {
                                    vm.selectedSection = name.takeUnless { it == "Todas" }
                                    scope.launch { drawerState.close() }
                                })
                        }
                    }
                    if (tab != "Inicio" && !showWishList) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("VISTA", color = Teal,
                            style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Lista", "Galería", "Compacta").forEach { mode ->
                                CatalogChip(mode, activeViewMode == mode) {
                                    vm.chooseViewMode(mode, activeViewScope); scope.launch { drawerState.close() }
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    NavigationDrawerItem(label = { Text("Estado de la biblioteca") }, selected = false,
                        icon = { AppIcon("Datos") }, onClick = {
                            libraryHealthOpen = true; scope.launch { drawerState.close() }
                        })
                    NavigationDrawerItem(label = { Text("Centro de tareas") }, selected = false,
                        icon = { Icon(Icons.Outlined.TaskAlt, null) }, onClick = {
                            taskCenterOpen = true; scope.launch { drawerState.close() }
                        })
                    NavigationDrawerItem(label = { Text("Actividad reciente") }, selected = false,
                        icon = { Icon(Icons.Outlined.History, null) }, onClick = {
                            activityOpen = true; scope.launch { drawerState.close() }
                        })
                    NavigationDrawerItem(label = { Text("Estadísticas de lectura") }, selected = false,
                        icon = { AppIcon("Pendientes") }, onClick = {
                            switchTab("Estadísticas"); scope.launch { drawerState.close() }
                        })
                    NavigationDrawerItem(label = { Text("Calendario de publicaciones") }, selected = false,
                        icon = { Icon(Icons.Outlined.CalendarMonth, null) }, onClick = {
                            releaseCalendarOpen = true; scope.launch { drawerState.close() }
                        })
                    NavigationDrawerItem(label = { Text("Escanear ISBN") }, selected = false,
                        icon = { Icon(Icons.Outlined.QrCodeScanner, null) }, onClick = {
                            scope.launch { drawerState.close() }
                            launchIsbnCamera = true
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED) {
                                launchIsbnCamera = false; isbnCamera.launch(null)
                            } else cameraPermission.launch(Manifest.permission.CAMERA)
                        })
                    NavigationDrawerItem(label = { Text("Exportar catálogo para Excel") }, selected = false,
                        icon = { Icon(Icons.Outlined.TableView, null) }, onClick = {
                            scope.launch { drawerState.close() }
                            catalogExport.launch("MiBiblioteca-catalogo.csv")
                        })
                    NavigationDrawerItem(label = { Text("Exportar notas y subrayados") }, selected = false,
                        icon = { Icon(Icons.Outlined.EditNote, null) }, onClick = {
                            scope.launch { drawerState.close() }
                            notebookExport.launch("MiBiblioteca-cuaderno.txt")
                        })
                    NavigationDrawerItem(label = { Text("Ajustes") }, selected = false,
                        icon = { AppIcon("Ajustes") }, onClick = { settingsOpen = true })
                    }
                }
            }
        }) {
    val wallpaperBook = vm.books.firstOrNull { it.uri == vm.lastOpenedUri }
    val immersive = vm.wallpaperMode != "Clásico"
    val immersedBar = Color(0xFF211711).copy(alpha = 0.60f)
    Box(Modifier.fillMaxSize().background(Parchment)) {
    LibraryWallpaper(vm.wallpaperMode, wallpaperBook)
    Scaffold(
        modifier = mainSwipeModifier,
        containerColor = Color.Transparent,
        topBar = {
            Column(Modifier.fillMaxWidth().background(if (immersive) immersedBar else HeaderBrown).statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(50.dp)) {
                    IconButton(onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.align(Alignment.CenterStart)
                            .semantics { contentDescription = "Abrir menú" }) {
                        Icon(Icons.Outlined.Menu, "Abrir menú", tint = Color.White)
                    }
                    Text("Mi Biblioteca", modifier = Modifier.align(Alignment.Center),
                        color = Color.White, fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif)
                    IconButton(onClick = {
                        vm.sync()
                        android.widget.Toast.makeText(context,
                            "Buscando libros nuevos y cambios en Drive…",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }, enabled = !vm.syncing, modifier = Modifier.align(Alignment.CenterEnd)
                        .semantics { contentDescription = "Actualizar biblioteca" }) {
                        if (vm.syncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White,
                                strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, "Actualizar biblioteca", tint = Color.White,
                                modifier = Modifier.size(25.dp))
                        }
                    }
                }
                if (tab != "Descubrir" && tab != "Inicio") LibrarySearchField(
                    query = vm.query,
                    voiceListening = voiceListening,
                    onQueryCommit = { value ->
                        vm.query = value
                        if (value.isNotBlank()) vm.qualityFilter = "Ninguno"
                    },
                    onVoice = { launchVoiceSearch(false) }
                )
                if (tab == "Biblioteca" || tab == "Secciones") {
                    UnifiedLibraryModeBar(
                        current = when {
                            tab == "Secciones" -> UnifiedLibraryMode.SECTIONS
                            vm.groupMode == "Géneros" -> UnifiedLibraryMode.GENRES
                            else -> UnifiedLibraryMode.BOOKS
                        },
                        onBooks = {
                            vm.groupMode = "Todos"
                            switchTab("Biblioteca")
                        },
                        onGenres = {
                            vm.groupMode = "Géneros"
                            switchTab("Biblioteca")
                        },
                        onSections = { switchTab("Secciones") }
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White.copy(alpha = 0.96f),
                contentColor = LibraryVisualTokens.onDark,
                tonalElevation = 0.dp) {
                listOf(
                    Triple("Inicio", "Inicio", "Inicio"),
                    Triple("Biblioteca", "Libros", "Libros"),
                    Triple("Favoritos", "Favoritos", "Favoritos"),
                    Triple("Estadísticas", "Estadísticas", "Estadísticas")
                ).forEach { (targetTab, label, icon) ->
                    NavigationBarItem(
                        selected = tab == targetTab ||
                            (targetTab == "Biblioteca" && tab == "Secciones"),
                        onClick = { switchTab(targetTab) },
                        icon = { AppIcon(icon, description = label, size = 22.dp) },
                        label = { Text(label, maxLines = 1, softWrap = false,
                            fontSize = 9.sp, letterSpacing = (-0.15).sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Mahogany,
                            selectedTextColor = Mahogany,
                            indicatorColor = Brass.copy(alpha = 0.28f),
                            unselectedIconColor = Mahogany.copy(alpha = 0.72f),
                            unselectedTextColor = Mahogany.copy(alpha = 0.72f))
                    )
                }
            }
        },
        floatingActionButton = { }
    ) { p ->
      Box(Modifier.padding(p).fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        if (vm.offlineDownloadUri != null) {
            LinearProgressIndicator(progress = { vm.offlineDownloadPercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Teal)
            WallpaperLabel("Descargando libro · ${vm.offlineDownloadPercent} %",
                modifier = Modifier.padding(horizontal = 16.dp), fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val travel = (pageMotion.value + pageDrag.floatValue * 0.18f)
                    .coerceIn(-pageTravelPx, pageTravelPx)
                translationX = travel
                alpha = 1f - FastOutSlowInEasing.transform(
                    (kotlin.math.abs(travel) / pageTravelPx.coerceAtLeast(1f))
                        .coerceIn(0f, 1f))
            },
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tab == "Géneros") item(key = "genre-jump") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                genreSearch = ""
                                draftAllGenres = genreAllSelected
                                draftGenres = selectedGenres
                                genrePickerOpen = true
                            }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color.White.copy(alpha = .55f))) {
                                Icon(Icons.Outlined.FilterList, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (genreAllSelected) "Géneros" else "${selectedGenres.size} seleccionados",
                                    maxLines = 1)
                            }
                            if (!genreAllSelected) {
                                IconButton(onClick = {
                                    genreAllSelected = true
                                    selectedGenres = emptySet()
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Marcar todos los géneros")
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End) {
                            ConceptViewSelector(
                                if (genreCarousel == "Horizontal") "Galería" else "Lista",
                                onGallery = { genreCarousel = "Horizontal" },
                                onList = { genreCarousel = "Vertical" }
                            )
                        }
                }
            }
            if (tab == "Inicio" && readingBooks.isNotEmpty())
                item(key = "reading-shelf") { ReadingShelf(readingBooks, vm::readingPercent,
                    isOffline = { it.uri in offlineBookUris },
                    onLongPress = { bookMenu = it }, onCarouselTouch = { startedOnCarousel = true }) { book ->
                    startReader(book)
                } }
            if (tab == "Estadísticas") item(key = "reading-statistics-page") {
                ReadingStatisticsPage(vm.books, vm::readingPercent,
                    vm.annualReadingGoal,
                    onGoalChanged = vm::updateAnnualReadingGoal,
                    onOpenDetailed = { readingStatsOpen = true })
            }
            if (tab == "Descubrir") item(key = "public-search-page") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Descubrir", color = Color.White, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, fontSize = 30.sp,
                        style = MaterialTheme.typography.headlineSmall.copy(shadow = WallpaperTextShadow))
                    Text("Busca un libro y consulta su información pública.", color = Color.White.copy(alpha = .86f), fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Surface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        color = Paper.copy(alpha = .98f), shadowElevation = 4.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Brass.copy(alpha = .65f))) {
                        DiscoverSearchField(
                            query = publicQuery,
                            searching = publicSearching,
                            voiceListening = voiceListening,
                            onVoice = { launchVoiceSearch(true) },
                            onSearch = { requested ->
                                publicQuery = requested
                                publicSearching = true
                                publicSearchJob?.cancel()
                                publicSearchJob = scope.launch(Dispatchers.IO) {
                                    val found = runCatching { searchPublicBooks(requested) }
                                        .getOrDefault(emptyList())
                                    withContext(Dispatchers.Main) {
                                        publicResults = found
                                        publicSearching = false
                                        selectedPublicResult = null
                                    }
                                }
                            }
                        )
                    }
                    if (publicSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = Brass)
                    Spacer(Modifier.height(14.dp))
                    if (!publicSearching && publicQuery.isNotBlank() && publicResults.isEmpty())
                        Text("No se han encontrado resultados.", color = Color.White.copy(alpha = .9f))
                    if (selectedPublicResult == null && publicResults.isNotEmpty()) {
                        Text(
                            "Resultados encontrados (" + publicResults.size + ")",
                            color = Brass,
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp
                        )
                        publicResults.forEach { result ->
                            Spacer(Modifier.height(12.dp))
                            PublicBookCard(
                                result,
                                onOpen = { selectedPublicResult = result },
                                onWish = { vm.addGoodreadsWish(result.title, result.sourceUrl) }
                            )
                        }
                    }
                    selectedPublicResult?.let { result ->
                        val details by produceState<PublicBookDetails?>(initialValue = null, result.sourceUrl) {
                            value = localizedPublicBookDetails(result)
                        }
                        PublicBookDetailPage(result, details, wishListed = vm.wishList.any { it.first.equals(result.title, true) && it.second == result.sourceUrl }, onBack = { selectedPublicResult = null }, onWish = {
                            if (vm.wishList.any { it.first.equals(result.title, true) && it.second == result.sourceUrl }) {
                                vm.removeGoodreadsWish(result.title, result.sourceUrl)
                                android.widget.Toast.makeText(context, "Quitado de Quiero leer", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                vm.addGoodreadsWish(result.title, result.sourceUrl)
                                android.widget.Toast.makeText(context, "Añadido a Quiero leer", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
            if (tab != "Estadísticas" && tab != "Descubrir") item(key = "controls") {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    if (tab == "Inicio") {
                    Spacer(Modifier.height(12.dp))
                    Text("✦  ENTRE ESTANTERÍAS  ✦", color = Brass, fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.labelMedium.copy(shadow = WallpaperTextShadow))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Historias por descubrir", fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.headlineSmall.copy(shadow = WallpaperTextShadow),
                            color = Color.White,
                            modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = Brass.copy(alpha = 0.4f), thickness = 1.dp)
                    }
                    if (tab == "Secciones") Text("Tus secciones",
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.headlineSmall.copy(shadow = WallpaperTextShadow),
                        color = Color.White, fontWeight = FontWeight.Bold)
                    if (tab == "Favoritos") Text("Tus favoritos",
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.headlineSmall.copy(shadow = WallpaperTextShadow),
                        fontWeight = FontWeight.Bold, color = Color.White)
                    if (vm.syncing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brass)
                        Text("Revisando ${vm.syncCount} libros…", modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (vm.coverUpdateRunning && (tab == "Inicio" || tab == "Biblioteca")) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
                        WallpaperLabel("Buscando portadas ${vm.coverUpdateDone}/${vm.coverUpdateTotal}…",
                            fontSize = 12.sp)
                    }
                    if (tab == "Biblioteca") {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ConceptFilterChip("Todos", "Libros", Modifier.weight(1f),
                                !showWishList && !vm.onlyFavorites && vm.statusFilter == null) {
                                showWishList = false
                                vm.onlyFavorites = false
                                vm.statusFilter = null
                                vm.selectedSection = null
                                vm.qualityFilter = "Ninguno"
                                smartCollection = null
                            }
                            ConceptFilterChip("Favoritos", "Favoritos", Modifier.weight(1f),
                                !showWishList && vm.onlyFavorites) {
                                showWishList = false; vm.onlyFavorites = true; vm.statusFilter = null
                            }
                            ConceptFilterChip("Pendientes", "Pendientes", Modifier.weight(1f),
                                !showWishList && !vm.onlyFavorites && vm.statusFilter == ReadingStatus.PENDING) {
                                showWishList = false; vm.onlyFavorites = false; vm.statusFilter = ReadingStatus.PENDING
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                ConceptSortButton("Ordenar por: $sortMode", Modifier.fillMaxWidth()) { sortMenu = true }
                                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    listOf("Título", "Autor", "Género", "Recientes").forEach { option ->
                                        DropdownMenuItem(text = { Text(option) }, onClick = {
                                            sortMode = option
                                            // Ordenar es independiente de agrupar. Al elegir un criterio
                                            // se vuelve a la lista global para evitar conservar una
                                            // agrupación anterior (por ejemplo, autores al elegir título).
                                            if (option == "Título" || option == "Recientes") {
                                                vm.groupMode = "Todos"
                                            } else if (option == "Autor") {
                                                vm.groupMode = "Autores"
                                            } else if (option == "Género") {
                                                vm.groupMode = "Géneros"
                                            }
                                            sortMenu = false
                                        })
                                    }
                                }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(when {
                                    showWishList -> "Quiero leer"
                                    vm.onlyFavorites -> "Tus favoritos"
                                    vm.statusFilter == ReadingStatus.PENDING -> "Libros pendientes"
                                    vm.statusFilter == ReadingStatus.READING -> "Leyendo"
                                    vm.statusFilter == ReadingStatus.READ -> "Libros leídos"
                                    groupMode == "Sagas" -> "Sagas y colecciones"
                                    else -> "Todos los libros"
                                }, fontFamily = FontFamily.Serif, fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold, color = Color.White,
                                    style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
                                WallpaperLabel("${if (showWishList) vm.wishList.size else smartBooks.size} libros",
                                    fontSize = 13.sp)
                            }
                            if (!showWishList) {
                                ConceptViewSelector(activeViewMode,
                                    onGallery = {
                                        // Preserve the current position when switching presentation.
                                        vm.chooseViewMode("Galería", activeViewScope)
                                    },
                                    onList = {
                                        vm.chooseViewMode("Lista", activeViewScope)
                                    })
                            }
                        }
                        if (vm.bulkInfoLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brass)
                        vm.message?.let { WallpaperLabel(it, fontSize = 12.sp) }
                    }
                    if (tab == "Secciones") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LibraryActionButton(onClick = { addingSection = true }) {
                                ActionLabel("Nueva sección", "Añadir")
                            }
                            Spacer(Modifier.width(8.dp))
                            LibraryTextButton(onClick = { organizeSections = true }) { Text("Organizar") }
                        }
                        Text("Tus secciones", color = Color.White.copy(alpha = .82f),
                            fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        if (vm.sections.isEmpty()) {
                            WallpaperLabel("Crea una sección para organizar tus libros.", fontSize = 12.sp)
                        } else {
                            Row(Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                vm.sections.forEach { name ->
                                    CatalogChip(name, vm.selectedSection == name) {
                                        smartCollection = null
                                        vm.selectedSection = if (vm.selectedSection == name) null else name
                                    }
                                }
                            }
                        }
                        Text("Vista", color = Color.White.copy(alpha = .82f),
                            fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        ConceptViewSelector(activeViewMode,
                            onGallery = { vm.chooseViewMode("Galería", activeViewScope) },
                            onList = { vm.chooseViewMode("Lista", activeViewScope) })
                    }
                    Box {
                        DropdownMenu(expanded = backupMenu, onDismissRequest = { backupMenu = false }) {
                            Text("El respaldo incluye tus notas y lecturas. Guárdalo en una carpeta privada.",
                                modifier = Modifier.width(280.dp).padding(16.dp), fontSize = 12.sp)
                            Text("Exporta tus datos antes de cambiar de teléfono: no se copian automáticamente.",
                                modifier = Modifier.width(280.dp).padding(horizontal = 16.dp), fontSize = 12.sp)
                            DropdownMenuItem(text = { Text("Exportar mis datos") }, onClick = {
                                backupMenu = false; backupExport.launch("MiBiblioteca-respaldo.json")
                            })
                            DropdownMenuItem(text = { Text("Restaurar respaldo") }, onClick = {
                                backupMenu = false; backupImport.launch(arrayOf("application/json"))
                            })
                            DropdownMenuItem(
                                text = { Text("Buscar duplicados") },
                                onClick = { backupMenu = false; duplicateDialog = true })
                        }
                    }
                }
            }
            if (tab == "Inicio") {
                if (vm.showHomeNews) {
                item(key = "launch-news") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Actualidad literaria en España", fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White,
                                modifier = Modifier.weight(1f),
                                style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
                        }
                        if (vm.newsRefreshing && vm.launchNews.isEmpty())
                            WallpaperLabel("Buscando noticias literarias…", fontSize = 12.sp)
                        if (vm.newsLastChecked > 0L) Text("Actualizado " +
                            java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("es", "ES"))
                                .format(java.util.Date(vm.newsLastChecked)), fontSize = 11.sp,
                            color = Color.White,
                            style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
                    }
                }
                items(visibleNews, key = { "news:" + it.url }, contentType = { "news" }) { news ->
                    val picture by rememberNewsImage(news)
                    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp).clickable {
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
                                    if (news.source in featuredNewsSources) "Novedad" else
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

                        }
                    }
                }
                }
                if (vm.books.isEmpty() && !vm.syncing) item(key = "empty-home") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = { folderPicker.launch(null) }) { Text("Elegir carpeta de libros") }
                    }
                }
            } else if (tab == "Estadísticas") {
                // La pantalla de estadísticas no muestra el catálogo.
            } else if (showWishList && tab == "Biblioteca") {
                items(vm.wishList, key = { "wish:" + it.first + "|" + it.second }) { (title, url) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LibraryActionButton(onClick = {
                            if (url.startsWith("http")) openGoodreadsUrl(context, url)
                            else searchInGoogleApp(context, "$title libro EPUB comprar préstamo")
                        },
                            modifier = Modifier.weight(1f)) { Text(title.ifBlank { "Libro de reseñas" }) }
                        IconButton(onClick = { vm.removeGoodreadsWish(title, url) }) {
                            AppIcon("Cerrar", "Quitar de pendientes")
                        }
                    }
                }
            } else if (tab == "Descubrir") {
                // La pantalla de consulta pública tiene su propio contenido.
            } else if (vm.books.isEmpty() && !vm.syncing) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon("Libros", tint = Color.White, size = 48.dp)
                            WallpaperLabel("Elige tu carpeta de libros en Drive", fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { folderPicker.launch(null) }) { Text("Elegir carpeta") }
                        }
                    }
                }
            } else if (filteredBooks.isEmpty()) {
                item(key = "no-results") {
                    WallpaperLabel(if (vm.onlyFavorites) "Todavía no hay libros favoritos."
                        else "No hay libros con estos filtros.", modifier = Modifier.padding(20.dp))
                }
            } else if (tab == "Géneros" && !genreAllSelected && selectedGenres.isEmpty()) {
                item(key = "no-genres-selected") {
                    WallpaperLabel("No has marcado ningún género. Abre el filtro y selecciona los que quieras ver.",
                        modifier = Modifier.padding(20.dp))
                }
            } else if (tab == "Secciones" && genreDisplayGroups.isEmpty()) {
                item(key = "empty-sections") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        color = Color(0xDB211D1A),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color.White.copy(alpha = .28f))
                    ) {
                        Column(Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon("Secciones", tint = Color.White, size = 36.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("Todavía no hay libros en secciones",
                                color = Color.White, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center)
                            Text("Crea una sección y asígnale libros desde su ficha.",
                                color = Color.White.copy(alpha = .86f), fontSize = 12.sp,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            } else if (genreDisplayGroups.isEmpty()) {
                item(key = "organizing-books") {
                    WallpaperLabel("Preparando la biblioteca…",
                        modifier = Modifier.padding(20.dp))
                }
            } else {
                genreDisplayGroups.forEach { (name, group) ->
                    if (name.isNotBlank()) item(key = "group:$name") {
                        Text(name, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp)
                                .then(if (tab == "Secciones") Modifier.clickable {
                                    smartCollection = null
                                    vm.selectedSection = if (vm.selectedSection == name) null else name
                                } else Modifier),
                            style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
                        if (groupMode == "Sagas" && name != "Sin saga") {
                            val numbers = group.map { sagaNumber(it) }
                                .filter { it.isFinite() && it >= 1 && it <= 100 && it % 1.0 == 0.0 }
                                .map { it.toInt() }.toSet()
                            val missing = if (numbers.size >= 2) (numbers.min()..numbers.max())
                                .filterNot(numbers::contains) else emptyList()
                            if (missing.isNotEmpty()) Text(
                                "Sin EPUB en la biblioteca: " + missing.joinToString(", "),
                                fontSize = 11.sp, color = Color.White,
                                style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
                            val catalog = vm.sagaCatalog(name)
                            if (catalog.isNotEmpty()) {
                                val ownedOrders = group.map { it.sagaOrder.trim().replace(',', '.') }
                                    .filter(String::isNotBlank).toSet()
                                val ownedTitles = group.map { displayTitle(it).lowercase()
                                    .replace(Regex("[^\\p{L}\\p{N}]"), "") }.toSet()
                                val absent = catalog.filter { volume ->
                                    volume.order !in ownedOrders && volume.title.lowercase()
                                        .replace(Regex("[^\\p{L}\\p{N}]"), "") !in ownedTitles
                                }
                                Text(if (absent.isEmpty())
                                    "✓ Completa · ${catalog.size} volúmenes"
                                else "Faltan ${absent.size}: " + absent.joinToString { volume ->
                                    volume.order.takeIf(String::isNotBlank)?.let { "$it. " }.orEmpty() +
                                        volume.title
                                }, fontSize = 11.sp,
                                    color = if (absent.isEmpty()) Color(0xFF8FE0CE) else Color(0xFFFFC2A7),
                                    style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
                            }
                            TextButton(onClick = { vm.searchSagaCatalog(name, group.firstOrNull()) },
                                enabled = !vm.sagaCatalogLoading(name),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                                Text(if (vm.sagaCatalogLoading(name))
                                    "Comprobando catálogo…" else if (catalog.isEmpty())
                                    "Comprobar saga completa" else "Actualizar comprobación",
                                    color = Color.White, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                sagaRenameSource = name; sagaRenameTarget = name
                            }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                                Text("Unificar o cambiar nombre", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                    val ordered = group
                    if (tab == "Géneros" && genreCarousel == "Horizontal") {
                        item(key = "genre-carousel:$name", contentType = "genre-carousel") {
                            LazyRow(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    startedOnCarousel = true
                                    var pressed: Boolean
                                    do {
                                        pressed = awaitPointerEvent(PointerEventPass.Final)
                                            .changes.any { it.pressed }
                                    } while (pressed)
                                }
                            },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(ordered, key = { "carousel:$name:" + it.uri },
                                    contentType = { "genre-book" }) { book ->
                                    BookGalleryCard(book, Modifier.width(148.dp), book.uri in offlineBookUris,
                                        vm.readingPercent(book.uri), { bookMenu = book },
                                        onToggleFavorite = { vm.toggleFavorite(book.uri) }) { openBookFromList(book) }
                                }
                            }
                        }
                    } else when (activeViewMode) {
                        "Galería" -> items(
                            count = (ordered.size + 1) / 2,
                            key = { index -> "grid:" + name + ":" + ordered[index * 2].uri },
                            contentType = { "gallery" }
                        ) { index ->
                            val first = ordered[index * 2]
                            val second = ordered.getOrNull(index * 2 + 1)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BookGalleryCard(first, Modifier.weight(1f), first.uri in offlineBookUris,
                                    vm.readingPercent(first.uri), { bookMenu = first },
                                    onToggleFavorite = { vm.toggleFavorite(first.uri) }) {
                                    openBookFromList(first)
                                }
                                if (second != null) {
                                    BookGalleryCard(second, Modifier.weight(1f), second.uri in offlineBookUris,
                                        vm.readingPercent(second.uri), { bookMenu = second },
                                        onToggleFavorite = { vm.toggleFavorite(second.uri) }) {
                                        openBookFromList(second)
                                    }
                                } else Spacer(Modifier.weight(1f))
                            }
                        }
                        else -> items(ordered, key = { name + ":" + it.uri },
                            contentType = { activeViewMode }) { book ->
                            if (activeViewMode == "Compacta")
                                BookCompactCard(book, book.uri in offlineBookUris,
                                    { bookMenu = book }) { openBookFromList(book) }
                            else BookCard(book, book.uri in offlineBookUris,
                                { bookMenu = book }) { openBookFromList(book) }
                        }
                    }
                }
            }
        }
        if ((tab == "Biblioteca" || tab == "Favoritos") && !showWishList) {
            LibraryScrollHandle(listState, Modifier.align(Alignment.CenterEnd))
        }
        }
        }
      }
    }
    }
    }
    }
    pendingReader?.let { BookOpeningTransition(it, readerTransition) }
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
                var dragging = false
                fun seek(y: Float) {
                    val fraction = (y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    seekJob?.cancel()
                    seekJob = scope.launch { listState.scrollToItem((fraction * maxIndex).toInt()) }
                }
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (!dragging && kotlin.math.abs(change.position.y - down.position.y) > 12.dp.toPx())
                        dragging = true
                    if (dragging) {
                        seek(change.position.y)
                        change.consume()
                    }
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

@Composable private fun ConceptQuickAccess(
    onReview: () -> Unit, onAi: () -> Unit
) {
    val actions = listOf(
        Triple("Reseña", "Reseña", Teal) to onReview,
        Triple("Búsqueda IA", "Búsqueda IA", Color(0xFFE46F5D)) to onAi
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEach { (item, action) ->
            Surface(Modifier.weight(1f).height(46.dp).clickable(onClick = action),
                color = Color.White,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Brass.copy(alpha = .55f))) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    AppIcon(item.second, tint = item.third, size = 20.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(item.first, maxLines = 1, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = Ink)
                }
            }
        }
    }
}

@Composable private fun ConceptFilterChip(label: String, icon: String,
    modifier: Modifier = Modifier, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier.height(34.dp).clickable(onClick = onClick),
        color = if (selected) Color(0xFF9A6843) else Color(0x991D1B19),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .42f))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            AppIcon(icon, tint = Color.White, size = 15.dp)
            Spacer(Modifier.width(3.dp))
            Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable private fun ConceptSortButton(label: String, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    Surface(modifier.height(34.dp).clickable(onClick = onClick),
        color = Teal, shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            AppIcon("Sagas", tint = Color.White, size = 15.dp)
            Spacer(Modifier.width(3.dp))
            Text(label, Modifier.weight(1f), color = Color.White, fontSize = 10.sp, maxLines = 1)
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable private fun ConceptViewSelector(current: String,
    onGallery: () -> Unit, onList: () -> Unit) {
    Surface(color = Color(0xD31D1B19),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha=.32f))) {
        Row {
            listOf("Galería" to onGallery, "Lista" to onList).forEach { (mode, action) ->
                Box(Modifier.size(44.dp).background(
                    if (current == mode) Color(0xFF9A6843) else Color.Transparent)
                    .clickable(onClick = action), contentAlignment = Alignment.Center) {
                    AppIcon(mode, "Vista $mode", tint = Color.White, size = 21.dp)
                }
            }
        }
    }
}

@Composable
private fun HomeReadingSummary(books: List<Book>, progress: (Uri) -> Int) {
    val read = books.count { it.status == ReadingStatus.READ }
    val reading = books.count { it.status == ReadingStatus.READING }
    val pending = books.count { it.status == ReadingStatus.PENDING }
    val started = books.map { progress(it.uri) }.filter { it > 0 }
    val average = started.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text("Tu actividad de lectura", fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Color.White,
            style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMetricCard("Leídos", read.toString(), Teal, Modifier.weight(1f))
            HomeMetricCard("Leyendo", reading.toString(), Brass, Modifier.weight(1f))
            HomeMetricCard("Pendientes", pending.toString(), Color(0xFFD6A0C8), Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .94f)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Progreso medio", color = Ink, fontWeight = FontWeight.Bold)
                        Text("De los libros empezados", color = Mahogany.copy(alpha = .7f), fontSize = 11.sp)
                    }
                    Text("$average %", color = Teal, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { average / 100f }, Modifier.fillMaxWidth(),
                    color = Teal, trackColor = Teal.copy(alpha = .16f))
                Spacer(Modifier.height(10.dp))
                val total = (read + reading + pending).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(read to Teal, reading to Brass, pending to Color(0xFFD6A0C8)).forEach { (count, color) ->
                        Box(Modifier.weight(count.toFloat().coerceAtLeast(.15f)).height(6.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .background(color))
                    }
                }
                Text("Terminados ${read * 100 / total}% · leyendo ${reading * 100 / total}% · pendientes ${pending * 100 / total}%",
                    fontSize = 10.sp, color = Mahogany.copy(alpha = .68f),
                    modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun ReadingStatisticsPage(
    books: List<Book>, progress: (Uri) -> Int,
    annualGoal: Int, onGoalChanged: (Int) -> Unit,
    onOpenDetailed: () -> Unit
) {
    val today = java.time.LocalDate.now()
    val stats = calculateReadingStats(books.map { book ->
        ReadingStatsInput(
            status = book.status,
            progress = progress(book.uri),
            finishedDate = book.finishedDate,
            rating = book.rating,
            genres = normalizeBookGenres(book.genre),
            author = displayAuthor(book),
            rereadCount = book.rereadCount
        )
    }, today, annualGoal)
    val months = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    val monthNames = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
    val maxMonth = stats.completedByMonth.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Estadísticas de lectura", fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold, fontSize = 27.sp, color = Color.White,
            style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
        Text("Datos reales de tu actividad registrada", color = Color.White.copy(alpha = .86f))
        var goalDraft by remember(annualGoal) { mutableStateOf(annualGoal.toString()) }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Paper),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Reto ${today.year}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${(stats.goalPercent * 100).toInt()} %", color = Teal, fontWeight = FontWeight.Bold)
                }
                Text("${stats.currentYearRead} de ${stats.goal} libros terminados", color = Mahogany)
                LinearProgressIndicator(progress = { stats.goalPercent }, Modifier.fillMaxWidth(),
                    color = Brass, trackColor = Brass.copy(alpha = .16f))
                Text(if (stats.remainingForGoal == 0) "Objetivo alcanzado"
                    else "Faltan ${stats.remainingForGoal} · proyección actual: ${stats.projectedYearTotal}",
                    fontSize = 12.sp, color = Mahogany.copy(alpha = .76f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(goalDraft, { goalDraft = it.filter(Char::isDigit).take(3) },
                        label = { Text("Objetivo anual") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { goalDraft.toIntOrNull()?.coerceIn(1, 365)?.let(onGoalChanged) }) {
                        Text("Guardar")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMetricCard("Este mes", stats.currentMonthRead.toString(), Teal, Modifier.weight(1f))
            HomeMetricCard("Racha mensual", stats.monthStreak.toString(), Brass, Modifier.weight(1f))
            HomeMetricCard("Relecturas", stats.rereads.toString(), Color(0xFFD6A0C8), Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Paper),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Libros terminados por mes", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(Modifier.fillMaxWidth().height(112.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom) {
                    stats.completedByMonth.forEachIndexed { index, count ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom) {
                            if (count > 0) Text(count.toString(), fontSize = 9.sp, color = Mahogany)
                            Box(Modifier.fillMaxWidth().height((72f * count / maxMonth).coerceAtLeast(3f).dp)
                                .semantics { contentDescription = "${monthNames[index]}: $count libros terminados" }
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(when {
                                    count == 0 -> Brass.copy(alpha = .14f)
                                    index + 1 == today.monthValue -> Teal
                                    else -> Brass
                                }))
                            Text(months[index], fontSize = 9.sp, color = Ink,
                                modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMetricCard("Leídos", stats.totalRead.toString(), Teal, Modifier.weight(1f))
            HomeMetricCard("Leyendo", stats.reading.toString(), Brass, Modifier.weight(1f))
            HomeMetricCard("Pendientes", stats.pending.toString(), Color(0xFFD6A0C8), Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Paper),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lecturas activas", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Progreso medio: ${stats.averageActiveProgress} %", color = Mahogany)
                LinearProgressIndicator(progress = { stats.averageActiveProgress / 100f },
                    Modifier.fillMaxWidth(), color = Teal, trackColor = Teal.copy(alpha = .16f))
                Text("Valoración media de libros terminados: " +
                    (stats.averageRating?.let { String.format(java.util.Locale.getDefault(), "%.1f / 5", it) }
                        ?: "sin valorar"), fontSize = 12.sp, color = Mahogany.copy(alpha = .76f))
            }
        }
        if (stats.topGenres.isNotEmpty() || stats.topAuthors.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top) {
                StatsRankingCard("Géneros leídos", stats.topGenres, Modifier.weight(1f))
                StatsRankingCard("Autores leídos", stats.topAuthors, Modifier.weight(1f))
            }
        }
        if (stats.readWithoutFinishDate > 0) {
            Surface(Modifier.fillMaxWidth(), color = Ink.copy(alpha = .82f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                Text("${stats.readWithoutFinishDate} libros marcados como leídos no tienen una fecha final válida y no cuentan en el reto anual.",
                    Modifier.padding(14.dp), color = Color.White, fontSize = 12.sp)
            }
        }
        OutlinedButton(onClick = onOpenDetailed, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .55f))) {
            Text("Ver resumen completo")
        }
    }
}

@Composable
private fun StatsRankingCard(title: String, values: List<Pair<String, Int>>, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Paper),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (values.isEmpty()) Text("Sin datos", color = Mahogany.copy(alpha = .68f), fontSize = 11.sp)
            values.forEach { (label, count) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = Mahogany, fontSize = 11.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(count.toString(), color = Teal, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeMetricCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .94f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(label, fontSize = 11.sp, color = Ink, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRecommendations(books: List<Book>, offline: Set<Uri>,
    onLongPress: (Book) -> Unit, onOpen: (Book) -> Unit) {
    if (books.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Recomendados para ti", fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Color.White,
                modifier = Modifier.weight(1f),
                style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
            Text("Ver todos", color = Color.White.copy(alpha = .78f), fontSize = 12.sp)
        }
        LazyRow(Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(books, key = { "recommendation:" + it.uri }) { book ->
                Column(Modifier.width(112.dp)
                    .semantics {
                        contentDescription = "${displayTitle(book)}, ${displayAuthor(book)}. ${book.status.label}"
                        role = Role.Button
                    }
                    .combinedClickable(onClick = { onOpen(book) },
                        onLongClickLabel = "Más opciones", onLongClick = { onLongPress(book) })) {
                    Box {
                        FlatCover(book, 112.dp, 164.dp)
                        if (book.uri in offline) OfflineBadge(
                            Modifier.align(Alignment.TopEnd).padding(4.dp), compact = true)
                    }
                    Text(displayTitle(book), color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp))
                    Text(displayAuthor(book), color = Color.White.copy(alpha = .72f),
                        fontSize = 10.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ReadingShelf(books: List<Book>, progress: (Uri) -> Int,
    isOffline: (Book) -> Boolean,
    onLongPress: (Book) -> Unit, onCarouselTouch: () -> Unit, onOpen: (Book) -> Unit) {
    Column {
        Text("Continuar leyendo", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 21.sp, color = Color.White,
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 12.dp),
            style = androidx.compose.ui.text.TextStyle(shadow = WallpaperTextShadow))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val coverWidth = (maxWidth - 36.dp) / 2
        LazyRow(Modifier.fillMaxWidth().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                onCarouselTouch()
                var pressed: Boolean
                do {
                    pressed = awaitPointerEvent(PointerEventPass.Final).changes.any { it.pressed }
                } while (pressed)
            }
        }, horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(books, key = { "reading:" + it.uri }) { book ->
                Card(Modifier.width(coverWidth)
                    .semantics {
                        contentDescription = "${displayTitle(book)}, ${displayAuthor(book)}. ${book.status.label}"
                        role = Role.Button
                    }
                    .combinedClickable(onClick = { onOpen(book) },
                        onLongClickLabel = "Más opciones", onLongClick = { onLongPress(book) }),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Box {
                        FlatCover(book, coverWidth, coverWidth * 1.50f)
                        if (isOffline(book)) OfflineBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                    Column(Modifier.fillMaxWidth().heightIn(min = 105.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(displayTitle(book), fontSize = 13.sp, lineHeight = 17.sp,
                            minLines = 2, maxLines = 2, fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold, color = Ink,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(displayAuthor(book), fontSize = 11.sp, maxLines = 1,
                            color = Mahogany.copy(alpha = 0.75f),
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        LinearProgressIndicator(progress = { progress(book.uri).coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth(), color = Teal,
                            trackColor = Brass.copy(alpha = 0.15f))
                        Text("${progress(book.uri)} % leído", fontSize = 10.sp, color = Teal)
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun BookCard(book: Book, offline: Boolean,
    onLongPress: () -> Unit, onClick: () -> Unit) {
    val title = remember(book.title, book.author, book.customTitle, book.customAuthor) { displayTitle(book) }
    val author = remember(book.author, book.customAuthor) { displayAuthor(book) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        .semantics { contentDescription = "$title, $author. ${book.status.label}"; role = Role.Button }
        .combinedClickable(onClick = onClick, onLongClickLabel = "Más opciones", onLongClick = onLongPress),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                FlatCover(book, 92.dp, 132.dp)
                if (offline) OfflineBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                        fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                    if (book.favorite) AppIcon("Favorito", tint = Brass, size = 18.dp)
                }
                Text(author, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun BookCompactCard(book: Book, offline: Boolean,
    onLongPress: () -> Unit, onClick: () -> Unit) {
    val title = remember(book.title, book.author, book.customTitle, book.customAuthor) { displayTitle(book) }
    val author = remember(book.author, book.customAuthor) { displayAuthor(book) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        .semantics { contentDescription = "$title, $author. ${book.status.label}"; role = Role.Button }
        .combinedClickable(onClick = onClick, onLongClickLabel = "Más opciones", onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                FlatCover(book, 50.dp, 72.dp)
                if (offline) OfflineBadge(Modifier.align(Alignment.TopEnd).padding(2.dp), compact = true)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, lineHeight = 17.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text(author, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun BookGalleryCard(book: Book, modifier: Modifier = Modifier,
    offline: Boolean, progress: Int, onLongPress: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null, onClick: () -> Unit) {
    val title = remember(book.title, book.author, book.customTitle, book.customAuthor) { displayTitle(book) }
    val author = remember(book.author, book.customAuthor) { displayAuthor(book) }
    val statusColor = when (book.status) {
        ReadingStatus.READING -> Color(0xFF245B76)
        ReadingStatus.READ -> Teal
        ReadingStatus.PENDING -> Color(0xFF936B43)
    }
    BoxWithConstraints(modifier.padding(bottom = 8.dp)) {
        val coverWidth = maxWidth
        Card(Modifier.fillMaxWidth()
            .semantics { contentDescription = "$title, $author. ${book.status.label}"; role = Role.Button }
            .combinedClickable(onClick = onClick, onLongClickLabel = "Más opciones", onLongClick = onLongPress),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) {
            Box(Modifier.fillMaxWidth()) {
                FlatCover(book, coverWidth, coverWidth * 1.50f)
                Surface(Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Ink.copy(alpha = 0.70f)) {
                    Row {
                        if (onToggleFavorite != null) IconButton(onClick = onToggleFavorite,
                            modifier = Modifier.size(48.dp)) {
                            AppIcon(if (book.favorite) "Favorito" else "Favoritos",
                                if (book.favorite) "Quitar de favoritos" else "Añadir a favoritos",
                                tint = Color.White, size = 22.dp)
                        }
                        IconButton(onClick = onLongPress, modifier = Modifier.size(48.dp)) {
                            AppIcon("Opciones", "Opciones del libro", tint = Color.White, size = 22.dp)
                        }
                    }
                }
                if (offline) OfflineBadge(Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
            Column(Modifier.fillMaxWidth().height(108.dp).padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text(title, fontSize = 14.sp, lineHeight = 17.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    minLines = 2, maxLines = 2, color = Ink,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(author, fontSize = 11.sp, maxLines = 1,
                    color = Mahogany.copy(alpha = 0.85f),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                if (book.status == ReadingStatus.READING) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.weight(1f), color = statusColor,
                        trackColor = Brass.copy(alpha = 0.25f))
                    Text("${progress.coerceIn(0, 100)} %", fontSize = 10.sp, color = statusColor)
                }
                Spacer(Modifier.weight(1f))
                Surface(color = statusColor,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        AppIcon(book.status.label, tint = Color.White, size = 14.dp)
                        Text(if (book.status == ReadingStatus.READ) "Completado" else book.status.label,
                            fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable private fun OfflineBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(modifier = modifier.semantics { contentDescription = "Disponible sin conexión" },
        shape = androidx.compose.foundation.shape.CircleShape, color = Teal,
        shadowElevation = 2.dp) {
        Icon(Icons.Outlined.DownloadDone, contentDescription = null, tint = Color.White,
            modifier = Modifier.padding(if (compact) 3.dp else 5.dp)
                .size(if (compact) 12.dp else 16.dp))
    }
}

private val coverBitmapCacheBudgetKb =
    (Runtime.getRuntime().maxMemory() / 1024L / 12L).toInt().coerceIn(12 * 1024, 32 * 1024)

private val coverBitmapCache = object : LruCache<String, Bitmap>(coverBitmapCacheBudgetKb) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}

// Prevent a burst of simultaneous bitmap decodes from stealing frames from scrolling.
private val coverDecodeSlots = Semaphore(2)

private fun coverBucket(pixels: Int): Int {
    val step = if (pixels < 256) 128 else 256
    return ((pixels.coerceAtLeast(1) + step - 1) / step) * step
}

private fun coverKey(book: Book, widthPx: Int, heightPx: Int): String =
    book.uri.toString() + ":" + book.cover?.let(::coverContentFingerprint) +
        ":${coverBucket(widthPx)}:${coverBucket(heightPx)}"

private fun decodeCover(book: Book, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
    val bytes = book.cover ?: return null
    val key = coverKey(book, targetWidthPx, targetHeightPx)
    synchronized(coverBitmapCache) { coverBitmapCache.get(key) }?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    // Malformed cover data should not trigger invalid sampling or a crash.
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // Decode compact previews so switching genre rows does not repeatedly allocate
    // near full-resolution bitmaps. The cover is still upscaled cleanly at card size.
    val targetWidth = coverBucket(targetWidthPx) * 2 / 3
    val targetHeight = coverBucket(targetHeightPx) * 2 / 3
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth &&
        bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: return null
    synchronized(coverBitmapCache) { coverBitmapCache.put(key, bitmap) }
    return bitmap
}

@Composable
private fun FlatCover(book: Book, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current
    val targetWidthPx = with(density) { w.roundToPx() }
    val targetHeightPx = with(density) { h.roundToPx() }
    val cacheKey = remember(book.cover, book.uri, targetWidthPx, targetHeightPx) {
        coverKey(book, targetWidthPx, targetHeightPx)
    }
    val cached = remember(cacheKey) {
        synchronized(coverBitmapCache) { coverBitmapCache.get(cacheKey) }
    }
    val bmp by produceState<Bitmap?>(cached, book.uri, book.cover, targetWidthPx, targetHeightPx) {
        value = cached
        if (cached == null && book.cover != null) {
            value = coverDecodeSlots.withPermit {
                withContext(Dispatchers.Default) { decodeCover(book, targetWidthPx, targetHeightPx) }
            }
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    Surface(Modifier.width(w).height(h), color = Color.White, shape = shape) {
        val bitmap = bmp
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "Portada de ${displayTitle(book)}",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            val fallbackTitle = displayTitle(book).trim().ifBlank { "Sin título" }
            val initials = fallbackTitle.split(Regex("\\s+"))
                .filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
            Box(Modifier.fillMaxSize().background(Color(0xFFF0E8DC)).padding(10.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(initials.ifBlank { "·" }, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, fontSize = (w.value * .22f).sp,
                        color = Mahogany.copy(alpha = .72f))
                    Text(fallbackTitle, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        fontSize = (w.value * .07f).coerceIn(9f, 14f).sp,
                        color = Ink, maxLines = 3, textAlign = TextAlign.Center,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("Portada pendiente", fontSize = 9.sp, color = Mahogany.copy(alpha = .62f))
                }
            }
        }
    }
}

@Composable
private fun Cover(book: Book, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current
    val targetWidthPx = with(density) { w.roundToPx() }
    val targetHeightPx = with(density) { h.roundToPx() }
    val cacheKey = remember(book.cover, book.uri, targetWidthPx, targetHeightPx) {
        coverKey(book, targetWidthPx, targetHeightPx)
    }
    val cached = remember(cacheKey) { synchronized(coverBitmapCache) { coverBitmapCache.get(cacheKey) } }
    val bmp by produceState<Bitmap?>(cached, book.uri, book.cover, targetWidthPx, targetHeightPx) {
        value = cached
        if (cached == null && book.cover != null)
            value = coverDecodeSlots.withPermit {
                withContext(Dispatchers.Default) { decodeCover(book, targetWidthPx, targetHeightPx) }
            }
    }
    val coverEdgeColor = remember(bmp) {
        val bitmap = bmp
        if (bitmap == null || bitmap.width < 2 || bitmap.height < 2) Mahogany
        else {
            val samples = intArrayOf(
                bitmap.getPixel(bitmap.width / 2, 1),
                bitmap.getPixel(bitmap.width / 2, bitmap.height - 2),
                bitmap.getPixel(1, bitmap.height / 2),
                bitmap.getPixel(bitmap.width - 2, bitmap.height / 2)
            )
            val red = samples.sumOf { android.graphics.Color.red(it) } / samples.size / 255f
            val green = samples.sumOf { android.graphics.Color.green(it) } / samples.size / 255f
            val blue = samples.sumOf { android.graphics.Color.blue(it) } / samples.size / 255f
            Color(red * .70f, green * .70f, blue * .70f, 1f)
        }
    }
    val depth = (w.value * 0.033f).coerceIn(2.3f, 5.8f).dp
    val foot = (w.value * 0.058f).coerceIn(3.5f, 10.5f).dp
    val bookShape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 2.dp, topEnd = 3.dp, bottomStart = 2.dp, bottomEnd = 3.dp)
    Box(Modifier.width(w).height(h)
        .shadow(13.dp, bookShape,
            ambientColor = Color(0x442B2019), spotColor = Color(0x8833251B))) {
        Canvas(Modifier.matchParentSize()) {
            val d = depth.toPx()
            val f = foot.toPx()
            val seed = kotlin.math.abs(book.uri.hashCode())
            // A soft contact shadow anchors the book to the surface.
            drawOval(Brush.radialGradient(listOf(
                Color(0x66241812), Color(0x24241812), Color.Transparent),
                center = Offset(size.width * .53f, size.height - f * .40f),
                radius = size.width * .54f),
                topLeft = Offset(size.width * .02f, size.height - f * 1.35f),
                size = Size(size.width * .96f, f * 1.32f))
            // Page edges sit beneath the cover; each fine contour is a separate sheet.
            val pageSide = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width - d - .5.dp.toPx(), .8.dp.toPx())
                quadraticTo(size.width - d * .15f, d * .14f,
                    size.width - .4.dp.toPx(), d * .55f)
                lineTo(size.width - .4.dp.toPx(), size.height - f * .25f)
                quadraticTo(size.width - d * .25f, size.height - f * .02f,
                    size.width - d, size.height - f)
                close()
            }
            drawPath(pageSide, Brush.horizontalGradient(listOf(
                Color(0xFFB8AA95), Color(0xFFF3ECDD),
                Color(0xFFE0D5C2), Color(0xFFC9BDAA)),
                startX = size.width - d, endX = size.width))
            val pageFoot = androidx.compose.ui.graphics.Path().apply {
                moveTo(1.dp.toPx(), size.height - f - .3.dp.toPx())
                lineTo(size.width - d, size.height - f)
                quadraticTo(size.width - d * .32f, size.height - f * .1f,
                    size.width - d * .65f, size.height - .5.dp.toPx())
                lineTo(2.dp.toPx(), size.height - .5.dp.toPx())
                close()
            }
            drawPath(pageFoot, Brush.verticalGradient(listOf(
                Color(0xFFA89983), Color(0xFFEDE4D3),
                Color(0xFFD6C9B6), Color(0xFFB6A894)),
                startY = size.height - f, endY = size.height))
            val sideSheets = (d / .34.dp.toPx()).toInt().coerceIn(6, 22)
            for (n in 1 until sideSheets) {
                val t = n.toFloat() / sideSheets
                val jitter = (((seed shr (n % 12)) and 3) - 1.5f) * .16.dp.toPx()
                val x = size.width - d + d * t
                val top = d * (.08f + .48f * t) + jitter
                val bottom = size.height - f * (1f - .74f * t) - jitter
                val contour = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, top)
                    quadraticTo(x + jitter * .42f, size.height * .34f,
                        x - jitter * .20f, size.height * .57f)
                    quadraticTo(x + jitter * .32f, size.height * .78f,
                        x + .10.dp.toPx(), bottom)
                }
                drawPath(contour, Color(0xFF665A4B).copy(
                    alpha = if ((n + seed) % 5 == 0) .25f else .105f),
                    style = Stroke(width = (if (n % 6 == 0) .36f else .22f).dp.toPx()))
            }
            val footSheets = (f / .34.dp.toPx()).toInt().coerceIn(8, 34)
            for (n in 1 until footSheets) {
                val t = n.toFloat() / footSheets
                val variation = (((seed shr (n % 15)) and 7) - 3.5f)
                val y = size.height - f + f * t
                val start = (1.2f + (variation + 3.5f) * .13f).dp.toPx()
                val end = size.width - d * (1f - .32f * t) -
                    kotlin.math.abs(variation) * .10.dp.toPx()
                val wave = variation * .095.dp.toPx()
                val contour = androidx.compose.ui.graphics.Path().apply {
                    moveTo(start, y)
                    quadraticTo(size.width * .29f, y + wave,
                        size.width * .53f, y - wave * .40f)
                    quadraticTo(size.width * .76f, y + wave * .55f,
                        end, y + f * .012f)
                }
                drawPath(contour, Color(0xFF706252).copy(
                    alpha = if ((n + seed) % 7 == 0) .28f else .105f),
                    style = Stroke(width = (if (n % 8 == 0) .34f else .20f).dp.toPx()))
            }
            // Slightly darker signatures suggest groups of bound sheets.
            for (n in 1..3) {
                val y = size.height - f + f * (n / 4f)
                drawLine(Color(0xFF55483A).copy(alpha = .16f),
                    Offset(3.dp.toPx(), y),
                    Offset(size.width - d * (.92f - n * .08f), y + .12.dp.toPx()),
                    .46.dp.toPx())
            }
            drawLine(Color(0xFF55483A).copy(alpha = .20f),
                Offset(2.dp.toPx(), size.height - f),
                Offset(size.width - d, size.height - f),
                .55.dp.toPx())
            // The back board projects just beyond the page block.
            drawRoundRect(coverEdgeColor,
                topLeft = Offset(.6.dp.toPx(), size.height - 1.45.dp.toPx()),
                size = Size(size.width - d * .30f, 1.25.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(.6.dp.toPx()))
            drawLine(Color.White.copy(alpha = .14f),
                Offset(1.2.dp.toPx(), size.height - 1.55.dp.toPx()),
                Offset(size.width - d * .38f, size.height - 1.55.dp.toPx()),
                .30.dp.toPx())
        }
        Surface(Modifier.fillMaxSize().padding(end = depth, bottom = foot)
            .graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                rotationY = -1.2f
                cameraDistance = 42.dp.toPx()
            }
            .shadow(4.dp, bookShape,
                ambientColor = Color(0x3B21180F), spotColor = Color(0x6030251C)),
            shape = bookShape, color = Mahogany, tonalElevation = 0.dp) {
            Box(Modifier.fillMaxSize()) {
                if (bmp != null)
                    Image(bmp!!.asImageBitmap(), book.title, Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds)
                else Column(Modifier.fillMaxSize().background(Mahogany).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text("✦", color = Brass, fontSize = 15.sp)
                    Text(displayTitle(book), color = Paper, fontFamily = FontFamily.Serif,
                        fontSize = 10.sp, lineHeight = 12.sp, maxLines = 3,
                        textAlign = TextAlign.Center)
                }
                Canvas(Modifier.matchParentSize()) {
                    drawRect(Brush.horizontalGradient(listOf(
                        Color(0x350E0B09), Color(0x082A211D), Color.Transparent),
                        startX = 0f, endX = size.width * .10f))
                    drawRect(Brush.horizontalGradient(listOf(
                        Color.Transparent, Color(0x0AFFFFFF), Color(0x301D1712)),
                        startX = size.width * .92f, endX = size.width))
                    drawRect(Brush.verticalGradient(listOf(
                        Color(0x24FFFFFF), Color.Transparent),
                        startY = 0f, endY = size.height * .22f))
                    // Cover bevel and a soft hinge make the printed image read as a physical cover.
                    drawLine(coverEdgeColor.copy(alpha = .72f),
                        Offset(1.dp.toPx(), size.height - .65.dp.toPx()),
                        Offset(size.width - 1.dp.toPx(), size.height - .65.dp.toPx()),
                        1.05.dp.toPx())
                    drawLine(Color(0xFF17110D).copy(alpha = .24f),
                        Offset(size.width * .045f, 2.dp.toPx()),
                        Offset(size.width * .045f, size.height - 2.dp.toPx()),
                        .65.dp.toPx())
                    drawLine(Color.White.copy(alpha = .12f),
                        Offset(size.width * .054f, 2.dp.toPx()),
                        Offset(size.width * .054f, size.height - 2.dp.toPx()),
                        .42.dp.toPx())
                    drawRoundRect(Color(0x2E20160F), cornerRadius =
                        androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                        style = Stroke(width = .7.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun BookOpeningTransition(
    book: Book,
    transition: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val transitionGenre = openingGenre(book.genre)
    val transitionImage = wallpaperImages[
        wallpaperGenres.indexOf(transitionGenre).coerceAtLeast(0)]
    Box(Modifier.fillMaxSize()
        .clickable(interactionSource = source, indication = null) { }) {
        // Cover the detail screen immediately so its static cover never appears
        // behind the animated book. Fade only after the reader is already composed.
        Box(Modifier.matchParentSize().graphicsLayer {
            val p = transition.value.coerceIn(0f, 1f)
            alpha = 1f - FastOutSlowInEasing.transform(
                ((p - .84f) / .16f).coerceIn(0f, 1f))
            scaleX = 1.055f - p * .025f
            scaleY = 1.055f - p * .025f
        }) {
            Image(painterResource(transitionImage), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Canvas(Modifier.fillMaxSize()) {
                // Keep the genre scene crisp and colourful while guiding the eye to the book.
                drawRect(Color(0xFFFFFBF3).copy(alpha = .08f))
                drawRect(Brush.radialGradient(listOf(
                    Color.Transparent,
                    Color(0xFF2B201B).copy(alpha = .28f)),
                    center = Offset(size.width * .50f, size.height * .46f),
                    radius = kotlin.math.max(size.width, size.height) * .74f))
                drawRect(Brush.radialGradient(listOf(
                    Color.White.copy(alpha = .10f), Color.Transparent),
                    center = Offset(size.width * .50f, size.height * .44f),
                    radius = size.width * .38f))
            }
        }
        Box(Modifier.align(Alignment.Center).size(185.dp, 265.dp).graphicsLayer {
            val p = transition.value.coerceIn(0f, 1f)
            val approach = FastOutSlowInEasing.transform(((p - 0.42f) / 0.42f).coerceIn(0f, 1f))
            val exit = FastOutSlowInEasing.transform(((1f - p) / 0.16f).coerceIn(0f, 1f))
            scaleX = 0.92f + approach * 0.35f
            scaleY = scaleX
            translationY = (1f - approach) * 10.dp.toPx()
            rotationZ = -0.8f * (1f - approach)
            alpha = exit
        }) {
            Surface(Modifier.fillMaxSize().shadow(16.dp,
                androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                color = Color(0xFFF6E9CC), tonalElevation = 0.dp) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.horizontalGradient(listOf(
                            Color(0xFFBCAA88), Color(0xFFEAD9B8),
                            Color(0xFFF8ECCF), Color(0xFFF2E3C4)),
                            startX = 0f, endX = size.width))
                        drawRect(Brush.radialGradient(listOf(
                            Color(0xFFFFF8E5).copy(alpha = .60f),
                            Color.Transparent),
                            center = Offset(size.width * .58f, size.height * .42f),
                            radius = size.height * .78f))
                        drawRect(Color(0xFF785D3D).copy(alpha = .34f),
                            topLeft = Offset(size.width * .13f, size.height * .09f),
                            size = Size(size.width * .75f, size.height * .82f),
                            style = Stroke(width = .8.dp.toPx()))
                        drawLine(Color(0xFF69503A).copy(alpha = .24f),
                            Offset(size.width * .08f, 0f),
                            Offset(size.width * .08f, size.height), 1.dp.toPx())
                        for (n in 0..56) {
                            val x = size.width * ((n * 37 % 89 + 7) / 100f)
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
                    val animatedTitle = displayTitle(book)
                    val longestTitleWord = animatedTitle.split(Regex("\\s+")).maxOfOrNull(String::length) ?: 0
                    val animatedTitleSize = when {
                        longestTitleWord >= 14 -> 14.sp
                        longestTitleWord >= 10 || animatedTitle.length > 30 -> 16.sp
                        animatedTitle.length > 22 -> 18.sp
                        else -> 20.sp
                    }
                    Column(Modifier.align(Alignment.Center).fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(animatedTitle, color = Color(0xFF4A2B1E),
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = FontFamily.Serif,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = animatedTitleSize,
                            lineHeight = when (animatedTitleSize.value.toInt()) {
                                14 -> 17.sp; 16 -> 19.sp; 18 -> 21.sp; else -> 23.sp
                            },
                            letterSpacing = 0.sp,
                            textAlign = TextAlign.Center, maxLines = 4,
                            overflow = TextOverflow.Ellipsis)
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
            actions = {
                IconButton(onClick = toggleFavorite,
                    modifier = Modifier.semantics {
                        contentDescription = if (book.favorite) "Quitar de favoritos" else "Añadir a favoritos"
                    }) {
                    AppIcon(if (book.favorite) "Favorito" else "Favoritos",
                        if (book.favorite) "Quitar de favoritos" else "Añadir a favoritos", tint = Paper)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0x85211711),
                scrolledContainerColor = Color(0xA6211711))
        )
    }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (detailMessage ?: message)?.let { notice -> item {
                Surface(
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Aviso: $notice"
                    },
                    color = Ink.copy(alpha = 0.82f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Brass.copy(alpha = 0.55f))
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon("Estado", "Aviso", tint = Color.White, size = 22.dp)
                        WallpaperLabel(notice, Modifier.weight(1f), fontSize = 13.sp)
                    }
                }
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
