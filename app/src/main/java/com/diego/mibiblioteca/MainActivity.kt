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
import android.content.ClipData
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.FileProvider
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
    val sourceModified: Long = -1L,
    val sourceCoverChecked: Boolean = false,
    val hadEmbeddedCover: Boolean = false,
    val plotSource: String = "",
    val bioSource: String = "",
    val coverSource: String = ""
)

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

private data class LaunchSource(
    val name: String, val url: String, val host: String, val selector: String,
    val fallback: String
)
data class LaunchNews(
    val source: String, val title: String, val url: String,
    val imageUrl: String, val releaseDate: String
)

private val starterNews = listOf(
    LaunchNews("Clara", "Las 25 novedades en libros más esperadas del otoño de 2026",
        "https://www.clara.es/estilo-de-vida/novedades-libros-esperadas-otono-2026_49129",
        "https://www.bing.com/th?id=ONUT.GHE09UlTMubiIlAklf_Jbw&pid=News", "2026-09-27"),
    LaunchNews("Cosmopolitan", "Diez libros nuevos para leer en octubre",
        "https://www.cosmopolitan.com/es/entretenimiento-cultura/g73876578/libros-recomendados-octubre-2026/",
        "https://www.bing.com/th?id=ONUT.kbO_qElBMGBPxEkKw6CKIg&pid=News", "2026-09-28"),
    LaunchNews("El Independiente", "Grandes lanzamientos literarios de octubre",
        "https://www.msn.com/es-es/noticias/otras/de-p%C3%A9rez-reverte-y-almod%C3%B3var-a-jon-fosse-los-grandes-lanzamientos-literarios-para-octubre/ar-AA2dczPZ",
        "https://www.bing.com/th?id=ONUT.m7bQKzY3qjbARUUJY99_lA&pid=News", "2026-09-29")
)

private fun newsQueries(includeLatest: Boolean = false): List<String> {
    val today = java.time.LocalDate.now()
    val next = today.plusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",
        java.util.Locale("es", "ES")))
    val season = when (today.monthValue) {
        3, 4, 5 -> "primavera"
        6, 7, 8 -> "verano"
        9, 10, 11 -> "otoño"
        else -> "invierno"
    }
    val regular = listOf("lanzamientos libros $season ${today.year}",
        "libros nuevos $next", "novedades literarias $season ${today.year}")
    return if (includeLatest) regular + listOf("libros nuevos literatura",
        "próximos lanzamientos novelas", "novedades editoriales libros") else regular
}

private fun fetchLiteraryNews(query: String): List<LaunchNews> = try {
    val address = "https://www.bing.com/news/search?q=" +
        java.net.URLEncoder.encode(query, "UTF-8") + "&format=rss&mkt=es-ES"
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 7000
    connection.readTimeout = 8000
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MiBiblioteca)")
    connection.useCaches = false
    connection.setRequestProperty("Cache-Control", "no-cache")
    val doc = try { connection.inputStream.use { stream ->
        Jsoup.parse(stream, "UTF-8", address, org.jsoup.parser.Parser.xmlParser())
    } } finally { connection.disconnect() }
    val today = java.time.LocalDate.now()
    doc.select("item").mapNotNull { item ->
        val headline = item.selectFirst("title")?.text()?.trim().orEmpty()
        val image = item.children().firstOrNull { it.tagName().substringAfterLast(':')
            .equals("Image", ignoreCase = true) }?.text()?.replace("http://", "https://").orEmpty()
        val rssLink = item.selectFirst("link")?.text().orEmpty()
        val article = try { Uri.parse(rssLink).getQueryParameter("url") ?: rssLink }
            catch (_: Exception) { rssLink }
        val source = item.children().firstOrNull { it.tagName().substringAfterLast(':')
            .equals("Source", ignoreCase = true) }?.text()?.ifBlank { "Noticias" } ?: "Noticias"
        val published = try {
            java.time.ZonedDateTime.parse(item.selectFirst("pubDate")?.text().orEmpty(),
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate()
        } catch (_: Exception) { null }
        val topical = Regex("(?i)libros?|novelas?|literari|editorial|lecturas?")
            .containsMatchIn(headline) && Regex("(?i)nuev|novedad|lanzamiento|próxim|esperad|publica|lectur")
            .containsMatchIn(headline)
        val timely = published != null && !published.isBefore(today.minusDays(60)) &&
            !published.isAfter(today.plusDays(1))
        if (topical && timely && headline.length in 20..180 &&
            image.startsWith("https://") && article.startsWith("https://"))
            LaunchNews(source, headline, article, image, published.toString()) else null
    }.take(10)
} catch (_: Exception) { emptyList() }

private fun fetchCasaUpcomingBooks(): List<LaunchNews> = try {
    val address = "https://www.casadellibro.com/proximos-lanzamientos-en-libros"
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 7000
    connection.readTimeout = 9000
    connection.useCaches = false
    connection.setRequestProperty("Cache-Control", "no-cache")
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MiBiblioteca)")
    val doc = try { connection.inputStream.use { stream ->
        Jsoup.parse(stream, "UTF-8", address)
    } } finally { connection.disconnect() }
    val checked = java.time.LocalDate.now().toString()
    doc.select(".product-card").mapNotNull { card ->
        val title = card.selectFirst("a.product-title") ?: return@mapNotNull null
        val author = card.selectFirst("p.autores")?.text()?.trim().orEmpty()
        val image = card.selectFirst("a.image img")?.absUrl("src").orEmpty()
        val link = title.absUrl("href")
        if (title.text().isBlank() || !link.startsWith("https://www.casadellibro.com/") ||
            !image.startsWith("https://imagessl")) return@mapNotNull null
        LaunchNews("Casa del Libro", title.text() +
            if (author.isBlank()) "" else " · $author", link, image, checked)
    }.distinctBy { it.url }.take(5)
} catch (_: Exception) { emptyList() }

private val newsImageCache = LruCache<String, Bitmap>(24)

@Composable private fun LaunchImage(url: String, title: String, fit: Boolean = false) {
    val imageUrl = when {
        url.startsWith("https://www.bing.com/th?") && !url.contains("&w=") -> "$url&w=800&h=450"
        url.contains("casadellibro.com/a/l/s5/") -> url.replace("/s5/", "/s7/")
        else -> url
    }
    val picture by produceState<Bitmap?>(initialValue = newsImageCache.get(imageUrl), imageUrl) {
        if (value == null) value = withContext(Dispatchers.IO) {
            try {
                val conn = URL(imageUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 6000
                try {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
                        newsImageCache.put(imageUrl, it)
                    }
                } finally { conn.disconnect() }
            } catch (_: Exception) { null }
        }
    }
    if (picture != null) Image(picture!!.asImageBitmap(), contentDescription = "Portada de $title",
        modifier = Modifier.fillMaxSize(), contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
    else Box(Modifier.fillMaxSize().background(Mahogany), contentAlignment = Alignment.Center) {
        Text("📖", fontSize = 32.sp)
    }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    var query by mutableStateOf("")
    var groupMode by mutableStateOf("Todos")
    var viewMode by mutableStateOf("Lista"); private set
    var selectedSection by mutableStateOf<String?>(null)
    var sections by mutableStateOf<List<String>>(emptyList()); private set
    var wishList by mutableStateOf<List<Pair<String, String>>>(emptyList()); private set
    var detailMessage by mutableStateOf<String?>(null); private set
    var infoCandidate by mutableStateOf<Pair<Uri, InfoCandidate>?>(null); private set
    var statusFilter by mutableStateOf<ReadingStatus?>(null)
    var onlyFavorites by mutableStateOf(false)
    var books by mutableStateOf<List<Book>>(emptyList()); private set
    var coverLoading by mutableStateOf<Uri?>(null); private set
    var infoLoading by mutableStateOf<Uri?>(null); private set
    var autoInfoLoading by mutableStateOf<Set<Uri>>(emptySet()); private set
    var autoCoverLoading by mutableStateOf<Set<Uri>>(emptySet()); private set
    private val autoJobs = mutableSetOf<Uri>()
    var syncing by mutableStateOf(false); private set
    var syncCount by mutableIntStateOf(0); private set
    var syncReport by mutableStateOf(""); private set
    var launchNews by mutableStateOf<List<LaunchNews>>(emptyList()); private set
    var newsRefreshing by mutableStateOf(false); private set
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
        viewModelScope.launch { restoreCachedBooks(onlyIfEmpty = true) }
        viewMode = prefs.getString("view_mode", "Lista") ?: "Lista"
        loadWishList()
        folderName = prefs.getString("folder_name", null)
        restoreLaunchNews()
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
                val sorted = results.distinctBy { it.url }.sortedByDescending { it.releaseDate }.take(12)
                if (sorted.isNotEmpty()) launchNews = sorted
                val array = org.json.JSONArray()
                launchNews.forEach { array.put(JSONObject().put("source", it.source)
                    .put("title", it.title).put("url", it.url)
                    .put("image", it.imageUrl).put("date", it.releaseDate)) }
                prefs.edit().putString("literary_news", array.toString())
                    .putLong("literary_news_v3_checked", System.currentTimeMillis()).apply()
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
                    notes = j.optString("notes"), goodreadsUrl = j.optString("goodreadsUrl"),
                    wantToRead = j.optBoolean("wantToRead"),
                    customTitle = j.optString("customTitle"), customAuthor = j.optString("customAuthor"),
                    sagaOrder = j.optString("sagaOrder"),
                    favorite = j.optBoolean("favorite"),
                    status = ReadingStatus.entries.firstOrNull { it.name == j.optString("status") } ?: ReadingStatus.PENDING,
                    sourceSize = j.optLong("sourceSize", -1L), sourceModified = j.optLong("sourceModified", -1L),
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
            .put("section", b.section).put("notes", b.notes)
            .put("goodreadsUrl", b.goodreadsUrl).put("wantToRead", b.wantToRead)
            .put("customTitle", b.customTitle).put("customAuthor", b.customAuthor)
            .put("sagaOrder", b.sagaOrder).put("favorite", b.favorite).put("status", b.status.name)
            .put("sourceSize", b.sourceSize).put("sourceModified", b.sourceModified)
            .put("sourceCoverChecked", b.sourceCoverChecked).put("hadEmbeddedCover", b.hadEmbeddedCover)
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
        books = books.map { if (it.uri == uri) it.copy(section = section) else it }
        saveBooks()
    }

    val filtered: List<Book> get() = books.filter { b ->
        val haystack = listOf(b.title, displayTitle(b), b.author, b.saga).joinToString(" ")
        haystack.contains(query, true) && (statusFilter == null || b.status == statusFilter) &&
            (!onlyFavorites || b.favorite) && (selectedSection == null || b.section == selectedSection)
    }

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
                        section = prior?.section.orEmpty(), notes = prior?.notes.orEmpty(),
                        goodreadsUrl = prior?.goodreadsUrl.orEmpty(), wantToRead = prior?.wantToRead ?: false,
                        customTitle = prior?.customTitle.orEmpty(), customAuthor = prior?.customAuthor.orEmpty(),
                        sagaOrder = prior?.sagaOrder ?: b.sagaOrder,
                        favorite = prior?.favorite ?: false, status = prior?.status ?: ReadingStatus.PENDING,
                        plotSource = prior?.plotSource.orEmpty(), bioSource = prior?.bioSource.orEmpty(),
                        coverSource = prior?.coverSource ?: if (b.cover != null) "Portada del EPUB" else "")
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
                saveBooks()
                syncCount = result.size
                syncReport = "Última sincronización: " +
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "ES"))
                        .format(java.util.Date()) +
                    "\n${result.size} EPUB · $added nuevos · $changed modificados · $missing ya no están en la carpeta."
                prefs.edit().putString("sync_report", syncReport).apply()
                folderName = withContext(Dispatchers.IO) { DocumentFile.fromTreeUri(getApplication(), uri)?.name }
                prefs.edit().putString("folder_name", folderName).apply()
                message = "${result.size} EPUB encontrados"
            } catch (e: Exception) {
                message = "No se pudo leer la carpeta de Drive: ${e.localizedMessage ?: "error de acceso"}"
                syncReport = message.orEmpty()
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
                    books = books.map { if (it.uri == book.uri) it.copy(cover = bytes, coverSource = "Catálogos públicos") else it }
                    message = "Portada guardada en este dispositivo."
                }
            } catch (e: Exception) {
                message = "No se pudo descargar la portada: ${e.localizedMessage ?: "comprueba la conexión"}"
            } finally { coverLoading = null }
        }
    }

    fun possibleDuplicates(book: Book): List<Book> {
        fun key(b: Book) = (displayTitle(b) + "|" + displayAuthor(b)).lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
        return books.filter { it.uri != book.uri && key(it) == key(book) }
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
                    message = null
                } else message = "No se pudo borrar. Comprueba el permiso de escritura de Drive."
            } catch (e: Exception) {
                message = "No se pudo borrar: ${e.localizedMessage ?: "sin permiso"}"
            }
        }
    }

    fun completeMissing(uri: Uri, force: Boolean = false) {
        val initial = books.firstOrNull { it.uri == uri } ?: return
        if (!autoJobs.add(uri)) return
        val app = getApplication<Application>()
        val now = System.currentTimeMillis()
        val cooldown = 3L * 24 * 60 * 60 * 1000
        val coverBlocked = File(app.filesDir, "removed-" + coverFile(app, uri).name).exists() ||
            File(app.filesDir, "manual-" + coverFile(app, uri).name).exists()
        val needCover = initial.cover == null && !coverBlocked &&
            (force || now - prefs.getLong("auto_cover_v28_" + uri, 0L) > cooldown)
        val needInfo = (initial.spanishPlot.isBlank() || initial.authorBio.isBlank() ||
                displayAuthor(initial) == "Biblioteca de Diroka77") &&
            (force || now - prefs.getLong("auto_info_v28_" + uri, 0L) > cooldown)
        if (!needCover && !needInfo) { autoJobs.remove(uri); return }
        viewModelScope.launch {
            var changed = false
            try {
                if (needInfo) {
                    autoInfoLoading = autoInfoLoading + uri
                    prefs.edit().putLong("auto_info_v28_" + uri, now).apply()
                    try {
                        val latest = books.firstOrNull { it.uri == uri } ?: initial
                        val google = withContext(Dispatchers.IO) { fetchGoogleBookInfo(latest) }
                        val author = google.first.ifBlank { displayAuthor(latest) }
                        val found = withContext(Dispatchers.IO) {
                            fetchSpanishInfo(latest.copy(customAuthor = author))
                        }
                        val rawPlot = google.second.ifBlank { found.plot }
                        val plotSource = if (google.second.isNotBlank()) "Google Libros" else found.plotSource
                        val plot = if (rawPlot.isBlank()) "" else if (plotSource == "Google Libros" ||
                            plotSource == "Wikipedia en español") rawPlot else ensureSpanish(rawPlot)
                        val bio = if (found.bio.isBlank()) "" else if (found.bioSource == "Wikipedia (es)")
                            found.bio else ensureSpanish(found.bio)
                        val current = books.firstOrNull { it.uri == uri }
                        if (current != null) {
                            val updated = current.copy(
                                customAuthor = if ((current.customAuthor.isBlank() ||
                                    current.customAuthor == "Biblioteca de Diroka77") &&
                                    google.first.isNotBlank()) google.first else current.customAuthor,
                                spanishPlot = current.spanishPlot.ifBlank { plot },
                                authorBio = current.authorBio.ifBlank { bio },
                                plotSource = if (current.spanishPlot.isBlank() && plot.isNotBlank())
                                    plotSource else current.plotSource,
                                bioSource = if (current.authorBio.isBlank() && bio.isNotBlank())
                                    found.bioSource else current.bioSource)
                            if (updated != current) {
                                books = books.map { if (it.uri == uri) updated else it }
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
                    if (bytes != null && current?.cover == null &&
                        !File(app.filesDir, "removed-" + coverFile(app, uri).name).exists() &&
                        !File(app.filesDir, "manual-" + coverFile(app, uri).name).exists()) {
                        withContext(Dispatchers.IO) { coverFile(app, uri).writeBytes(bytes) }
                        books = books.map { if (it.uri == uri) it.copy(
                            cover = bytes, coverSource = "Catálogos públicos") else it }
                        changed = true
                    }
                    autoCoverLoading = autoCoverLoading - uri
                }
                if (changed) saveBooks()
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
                    message = "No se encontraron datos para este libro."
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
    fun chooseViewMode(mode: String) { viewMode = mode; prefs.edit().putString("view_mode", mode).apply() }
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

private fun readableSize(size: Long): String = when {
    size < 0 -> "Desconocido"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> "${"%.1f".format(java.util.Locale.ROOT, size / 1048576.0)} MB"
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
                val file = coverFile(context, f.uri)
                val removed = File(context.filesDir, "removed-" + file.name).exists()
                val prior = cached[f.uri]
                val unchanged = prior != null && size > 0 && prior.sourceSize == size &&
                    (modified <= 0L || prior.sourceModified == modified) &&
                    prior.sourceCoverChecked && (!prior.hadEmbeddedCover || file.exists() || removed)
                val epub = if (unchanged) prior!! else {
                    val parsed = readEpub(context, f.uri, f.name ?: "Libro")
                    parsed.copy(sourceSize = size, sourceModified = modified,
                        sourceCoverChecked = true, hadEmbeddedCover = parsed.cover != null)
                }
                if (!removed && !file.exists() && epub.cover != null) file.writeBytes(epub.cover)
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

private fun cachedEpub(context: Context, book: Book): File {
    val folder = File(context.cacheDir, "epubs").apply { mkdirs() }
    val hash = MessageDigest.getInstance("SHA-256").digest(book.uri.toString().toByteArray())
        .joinToString("") { "%02x".format(it) }
    val file = File(folder, "$hash.epub")
    val stampFile = File(folder, "$hash.meta")
    val stamp = "${book.sourceSize}:${book.sourceModified}"
    if (file.exists() && file.length() > 0 &&
        (book.sourceSize <= 0 || file.length() == book.sourceSize) &&
        stampFile.takeIf { it.exists() }?.readText() == stamp) {
        file.setLastModified(System.currentTimeMillis())
        return file
    }
    val temp = File(folder, "$hash.partial")
    try {
        context.contentResolver.openInputStream(book.uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        } ?: throw IllegalStateException("No se pudo leer el EPUB de Drive")
        if (temp.length() == 0L) throw IllegalStateException("El EPUB está vacío")
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) throw IllegalStateException("No se pudo guardar el EPUB temporal")
        stampFile.writeText(stamp)
        file.setLastModified(System.currentTimeMillis())
        val old = folder.listFiles()?.filter { it.extension == "epub" && it != file }
            ?.sortedBy { it.lastModified() }.orEmpty()
        var total = folder.listFiles()?.filter { it.extension == "epub" }?.sumOf { it.length() } ?: 0L
        for (candidate in old) {
            if (total <= 700L * 1024 * 1024) break
            total -= candidate.length()
            candidate.delete()
            File(folder, candidate.nameWithoutExtension + ".meta").delete()
        }
        return file
    } finally { temp.delete() }
}

private suspend fun openEpubInReader(context: Context, book: Book): Boolean {
    try {
        val file = withContext(Dispatchers.IO) { cachedEpub(context, book) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/epub+zip")
            clipData = ClipData.newRawUri("EPUB", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        return true
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(book.uri, "application/epub+zip")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            return true
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "No hay un lector EPUB disponible",
                android.widget.Toast.LENGTH_LONG).show()
            return false
        }
    }
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

private fun catalogMatch(candidateTitle: String, candidateAuthors: List<String>, book: Book): Boolean {
    val normalize: (String) -> String = { value ->
        java.text.Normalizer.normalize(cleanCatalogText(value).lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
    }
    val wanted = normalize(displayTitle(book))
    val title = normalize(candidateTitle)
    if (wanted.length < 3 || title.length < 3 ||
        !(title.contains(wanted) || wanted.contains(title))) return false
    val author = displayAuthor(book).takeUnless { it == "Biblioteca de Diroka77" }.orEmpty()
    if (author.isNotBlank() && candidateAuthors.isNotEmpty()) {
        val wantedAuthor = normalize(author)
        if (candidateAuthors.none { name ->
                val candidate = normalize(name)
                candidate.contains(wantedAuthor) || wantedAuthor.contains(candidate)
            }) return false
    }
    return true
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
            val doc = docs?.optJSONObject(i) ?: continue
            val authors = doc.optJSONArray("author_name")
            val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            if (!catalogMatch(doc.optString("title"), names, book)) continue
            val id = doc.optLong("cover_i", 0)
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
            val info = items?.optJSONObject(i)?.optJSONObject("volumeInfo") ?: continue
            val authors = info.optJSONArray("authors")
            val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            if (!catalogMatch(info.optString("title"), names, book)) continue
            val link = info.optJSONObject("imageLinks")?.optString("thumbnail").orEmpty()
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

private fun fetchSpanishInfo(book: Book): InfoCandidate {
    var plotSource = ""
    var bioSource = ""
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
            val authors = info.optJSONArray("authors")
            val names = (0 until (authors?.length() ?: 0)).map { authors!!.optString(it) }
            if (info.optString("language") == "es" &&
                catalogMatch(info.optString("title"), names, book)) {
                plot = android.text.Html.fromHtml(info.optString("description"), android.text.Html.FROM_HTML_MODE_LEGACY)
                    .toString().trim().take(2500)
                if (plot.isNotBlank()) { plotSource = "Google Libros"; break }
            }
        }
    } catch (_: Exception) {}
    if (plot.isBlank() && book.description != "Sin descripción disponible.") {
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
        for (title in listOf(displayTitle(book), displayTitle(book) + " (novela)")) {
            try {
                val encoded = java.net.URLEncoder.encode(title, "UTF-8")
                val url = "https://es.wikipedia.org/w/api.php?action=query&prop=extracts" +
                    "&exintro=1&explaintext=1&redirects=1&format=json&formatversion=2&titles=$encoded"
                val page = getJson(url).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
                if (page != null && !page.has("missing")) {
                    plot = page.optString("extract").trim().take(2500)
                    if (plot.isNotBlank()) { plotSource = "Wikipedia en español"; break }
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
                    if (bio.isNotBlank()) { bioSource = "Wikipedia (" + host + ")"; break }
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
            if (bio.isNotBlank()) bioSource = "Open Library"
        }
    } catch (_: Exception) {}
    return InfoCandidate("", plot, bio, plotSource, bioSource)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryApp(vm: LibraryViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf<Book?>(null) }
    var tab by rememberSaveable { mutableStateOf("Inicio") }
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
    BackHandler(enabled = selected != null || showWishList || tab != "Inicio") {
        when {
            selected != null -> selected = null
            showWishList -> showWishList = false
            else -> { tab = "Inicio"; vm.selectedSection = null }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(tab) { listState.scrollToItem(0) }
    val scope = rememberCoroutineScope()
    var openingReading by remember { mutableStateOf<Uri?>(null) }
    var showSyncReport by remember { mutableStateOf(false) }
    var organizeSections by remember { mutableStateOf(false) }
    val readingBooks = remember(vm.books) { vm.readingBooks() }
    val filteredBooks = remember(vm.books, vm.query, vm.statusFilter, vm.onlyFavorites, vm.selectedSection) { vm.filtered }
    val visibleBooks = remember(vm.books, filteredBooks, tab) {
        if (tab == "Inicio") emptyList<Book>() else filteredBooks
    }
    val groupMode = if (tab == "Secciones") "Secciones" else if (tab == "Inicio") "Todos" else vm.groupMode
    val groupedBooks = remember(visibleBooks, groupMode, vm.sections, tab) {
        val groups = when (groupMode) {
            "Autores" -> visibleBooks.groupBy { displayAuthor(it) }
            "Sagas" -> visibleBooks.groupBy { it.saga.ifBlank { "Sin saga" } }
            "Secciones" -> visibleBooks.groupBy { it.section.ifBlank { "Sin sección" } }
            else -> mapOf("" to visibleBooks)
        }
        val names = if (tab == "Secciones")
            vm.sections.filter(groups::containsKey) +
                groups.keys.filterNot { it in vm.sections }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        else groups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
        names.associateWith { name ->
            val group = groups[name].orEmpty()
            if (groupMode == "Sagas") group.sortedWith(
                compareBy<Book> { sagaNumber(it) }.thenBy(String.CASE_INSENSITIVE_ORDER) { displayTitle(it) })
            else group
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
                    TextButton(onClick = { vm.moveSection(section, -1) }) { Text("↑") }
                    TextButton(onClick = { vm.moveSection(section, 1) }) { Text("↓") }
                    TextButton(onClick = { sectionToDelete = section }) { Text("×") }
                }
            }
            if (vm.sections.isEmpty()) Text("Aún no hay secciones.")
        } },
        confirmButton = { TextButton(onClick = { organizeSections = false }) { Text("Cerrar") } })
    if (showSyncReport) AlertDialog(onDismissRequest = { showSyncReport = false },
        title = { Text("Sincronización de Drive") },
        text = { Column {
            Text(vm.syncReport)
            Spacer(Modifier.height(14.dp))
            Text("Fluidez de esta sesión", fontWeight = FontWeight.Bold)
            Text((context as? MainActivity)?.performanceReport().orEmpty(), fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { showSyncReport = false }) { Text("Cerrar") } })
    val shown = current
    LaunchedEffect(shown?.uri) { shown?.let { vm.completeMissing(it.uri) } }
    LaunchedEffect(tab, shown?.uri) {
        (context as? MainActivity)?.setMetricScreen(if (shown != null) "Ficha" else tab)
    }
    if (shown != null) {
            BookDetail(shown, { selected = null }, { vm.toggleFavorite(shown.uri) },
                { vm.recordOpen(shown.uri) },
                { vm.setStatus(shown.uri, it) }, vm.message, vm.possibleDuplicates(shown),
                { vm.deleteDuplicate(shown) }, { vm.completeMissing(shown.uri, force = true) },
                vm.autoInfoLoading.contains(shown.uri), vm.autoCoverLoading.contains(shown.uri),
                vm.sections, { vm.assignSection(shown.uri, it) },
                { vm.saveNotes(shown.uri, it) }, { plot, bio -> vm.saveManualInfo(shown.uri, plot, bio) },
                { vm.replaceCover(shown.uri, it) },
                { vm.setGoodreadsUrl(shown.uri, it) }, { vm.toggleWantToRead(shown.uri) },
                vm.detailMessage,
                { title, author, saga, order -> vm.editIdentity(shown.uri, title, author, saga, order) })
        } else {
    Scaffold(
        containerColor = Parchment,
        bottomBar = {
            NavigationBar(containerColor = Paper) {
                listOf("Inicio", "Biblioteca", "Secciones").forEach { name ->
                    NavigationBarItem(selected = tab == name, onClick = {
                        tab = name; showWishList = false; vm.selectedSection = null
                    }, icon = { Text(when (name) { "Inicio" -> "⌂"; "Biblioteca" -> "▦"; else -> "▤" }) },
                        label = { Text(name) })
                }
            }
        },
        topBar = {
            Column(Modifier.fillMaxWidth().background(Mahogany).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Mi Biblioteca", color = Color.White, fontSize = 17.sp,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Image(painterResource(R.drawable.ic_bookshelf_foreground), null,
                        modifier = Modifier.size(36.dp))
                    Text("By Diroka77", color = Color.White, fontSize = 17.sp,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
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
                OutlinedTextField(vm.query, { vm.query = it; if (it.isNotBlank()) tab = "Biblioteca" },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    singleLine = true, placeholder = { Text("Buscar título, saga o autor") },
                    leadingIcon = { Text("⌕") },
                    trailingIcon = { if (vm.query.isNotEmpty()) TextButton(onClick = { vm.query = "" }) { Text("×") } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Paper, unfocusedContainerColor = Paper,
                        focusedTextColor = Ink, unfocusedTextColor = Ink),
                    shape = MaterialTheme.shapes.medium)
            }
        }
    ) { p ->
        LazyColumn(
            modifier = Modifier.padding(p).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "controls") {
                Column {
                    if (tab == "Inicio") {
                    val reading = readingBooks
                    if (reading.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Continuar leyendo", fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mahogany)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(end = 8.dp)) {
                            items(reading, key = { "reading:" + it.uri }) { book ->
                                Card(Modifier.width(142.dp).clickable(enabled = openingReading == null) {
                                    scope.launch {
                                        openingReading = book.uri
                                        try { if (openEpubInReader(context, book)) {
                                            vm.recordOpen(book.uri)
                                            vm.setStatus(book.uri, ReadingStatus.READING)
                                        } }
                                        finally { openingReading = null }
                                    }
                                },
                                    colors = CardDefaults.cardColors(containerColor = Paper),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                    Column(Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Cover(book, 122.dp, 170.dp)
                                        Spacer(Modifier.height(6.dp))
                                        Text(displayTitle(book), fontSize = 12.sp, lineHeight = 15.sp,
                                            maxLines = 2, textAlign = TextAlign.Center,
                                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                                        Text(displayAuthor(book), fontSize = 10.sp,
                                            maxLines = 1, textAlign = TextAlign.Center)
                                        Text("Toca para leer", fontSize = 10.sp, color = Mahogany)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("✦  ENTRE ESTANTERÍAS  ✦", color = Brass, fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Historias por descubrir", fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.headlineSmall, color = Ink,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { showSyncReport = true },
                            modifier = Modifier.semantics { contentDescription = "Ver estado de sincronización" }) { Text("ⓘ") }
                        TextButton(onClick = { vm.sync() }, enabled = !vm.syncing,
                            modifier = Modifier.semantics { contentDescription = "Sincronizar biblioteca" }) {
                            Text("📖", fontSize = 18.sp)
                        }
                    }
                    }
                    if (tab == "Biblioteca") Text("Tu biblioteca",
                        fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall, color = Mahogany)
                    if (tab == "Secciones") Text("Tus secciones",
                        fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall, color = Mahogany)
                    if (vm.syncing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brass)
                        Text("Revisando ${vm.syncCount} libros…", modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (tab == "Biblioteca") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(vm.statusFilter == null, { vm.statusFilter = null }, { Text("Todos") })
                            FilterChip(vm.onlyFavorites, { vm.onlyFavorites = !vm.onlyFavorites }, { Text("★ Favoritos") })
                            FilterChip(vm.statusFilter == ReadingStatus.READING,
                                { vm.statusFilter = if (vm.statusFilter == ReadingStatus.READING) null else ReadingStatus.READING },
                                { Text("Leyendo") })
                            FilterChip(vm.statusFilter == ReadingStatus.READ,
                                { vm.statusFilter = if (vm.statusFilter == ReadingStatus.READ) null else ReadingStatus.READ },
                                { Text("Leídos") })
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Todos", "Autores", "Sagas", "Secciones").forEach { mode ->
                                FilterChip(vm.groupMode == mode, { vm.groupMode = mode }, { Text(mode) })
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
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(showWishList, { showWishList = !showWishList },
                                { Text("Quiero leer (${vm.wishList.size})") })
                            if (showWishList) TextButton(onClick = { addWishDialog = true }) { Text("+ Goodreads") }
                        }
                    }
                    if (tab == "Secciones") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(vm.selectedSection == null, { vm.selectedSection = null }, { Text("Todas") })
                            vm.sections.forEach { name ->
                                FilterChip(vm.selectedSection == name, { vm.selectedSection = name }, { Text(name) })
                            }
                        }
                        Row {
                            OutlinedButton(onClick = { addingSection = true }) { Text("+ Nueva sección") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { organizeSections = true }) { Text("Ordenar secciones") }
                        }
                    }
                    if (tab != "Inicio" && !showWishList) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Lista", "Galería", "Compacta").forEach { mode ->
                                FilterChip(vm.viewMode == mode, { vm.chooseViewMode(mode) }, { Text(mode) })
                            }
                        }
                    }
                }
            }
            if (tab == "Inicio") {
                item(key = "launch-news") {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Actualidad literaria en España", fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Mahogany,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.refreshLaunchNews(true) }, enabled = !vm.newsRefreshing,
                                modifier = Modifier.semantics { contentDescription = "Actualizar noticias" }) {
                                if (vm.newsRefreshing) CircularProgressIndicator(Modifier.size(18.dp),
                                    strokeWidth = 2.dp, color = Brass)
                                else Text("📰", fontSize = 21.sp)
                            }
                        }
                        if (vm.newsRefreshing && vm.launchNews.isEmpty())
                            Text("Buscando noticias literarias…", color = Mahogany, fontSize = 12.sp)
                    }
                }
                items(vm.launchNews, key = { "news:" + it.url }, contentType = { "news" }) { news ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(news.url))) }
                        catch (_: Exception) {
                            android.widget.Toast.makeText(context, "No se pudo abrir la noticia",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, colors = CardDefaults.cardColors(containerColor = Paper)) {
                        Column(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(news.title, fontFamily = FontFamily.Serif,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mahogany,
                                    lineHeight = 23.sp, maxLines = 4)
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(news.source, color = Brass, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(if (news.source == "Casa del Libro") "Próximamente" else
                                        try {
                                            java.time.LocalDate.parse(news.releaseDate).format(
                                                java.time.format.DateTimeFormatter.ofPattern("d MMM",
                                                    java.util.Locale("es", "ES")))
                                        } catch (_: Exception) { news.releaseDate },
                                        color = Mahogany, fontSize = 11.sp)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(188.dp).background(Mahogany)) {
                                LaunchImage(news.imageUrl, news.title,
                                    fit = news.source == "Casa del Libro")
                            }
                        }
                    }
                }
                if (vm.books.isEmpty() && !vm.syncing) item(key = "empty-home") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = { folderPicker.launch(null) }) { Text("Elegir carpeta de EPUB") }
                    }
                }
            } else if (showWishList && tab == "Biblioteca") {
                items(vm.wishList, key = { "wish:" + it.second }) { (title, url) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { openGoodreadsUrl(context, url) },
                            modifier = Modifier.weight(1f)) { Text(title.ifBlank { "Libro de Goodreads" }) }
                        TextButton(onClick = { vm.removeGoodreadsWish(url) }) { Text("×") }
                    }
                }
            } else if (vm.books.isEmpty() && !vm.syncing) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📚", style = MaterialTheme.typography.displayMedium)
                            Text("Elige tu carpeta de EPUB en Drive", fontFamily = FontFamily.Serif)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { folderPicker.launch(null) }) { Text("Elegir carpeta") }
                        }
                    }
                }
            } else if (visibleBooks.isEmpty()) {
                item(key = "no-results") {
                    Text("No hay libros con estos filtros.", modifier = Modifier.padding(20.dp))
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
                    when (vm.viewMode) {
                        "Galería" -> items(ordered.chunked(2), key = { "grid:" + it.first().uri }, contentType = { "gallery" }) { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { book ->
                                    BookGalleryCard(book, Modifier.weight(1f)) { selected = book }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        else -> items(ordered, key = { it.uri.toString() }, contentType = { vm.viewMode }) { book ->
                            if (vm.viewMode == "Compacta") BookCompactCard(book) { selected = book }
                            else BookCard(book) { selected = book }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable private fun BookCard(book:Book,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Paper), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Cover(book,92.dp,132.dp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(displayTitle(book),fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,fontSize=14.sp,lineHeight=18.sp,modifier=Modifier.weight(1f));if(book.favorite)Text("★")};Text(displayAuthor(book),fontSize=12.sp)}}}}

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

@Composable private fun BookGalleryCard(book: Book, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Paper)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
            val coverWidth = (maxWidth - 16.dp).coerceAtMost(180.dp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(book, coverWidth, coverWidth * 1.42f)
                Spacer(Modifier.height(6.dp))
                Text(displayTitle(book), fontSize = 12.sp, lineHeight = 15.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    maxLines = 2, textAlign = TextAlign.Center)
                Text(displayAuthor(book), fontSize = 10.sp, maxLines = 1,
                    textAlign = TextAlign.Center)
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
    while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 1200) sample *= 2
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
        if (value == null && book.cover != null) {
            value = withContext(Dispatchers.Default) { decodeCover(book) }
        }
    }
    Surface(Modifier.width(w).height(h), shape = MaterialTheme.shapes.medium,
        color = Color(0xFFE9DDC5), tonalElevation = 2.dp) {
        if (bmp != null) Image(bmp!!.asImageBitmap(), book.title, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
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
    onOpened: () -> Unit, setStatus: (ReadingStatus) -> Unit, message: String?,
    possibleDuplicates: List<Book>, deleteBook: () -> Unit, enrich: () -> Unit,
    infoLoading: Boolean, coverSearching: Boolean,
    sections: List<String>, assignSection: (String) -> Unit,
    saveNotes: (String) -> Unit, saveInfo: (String, String) -> Unit,
    replaceCover: (Uri) -> Unit,
    saveGoodreadsUrl: (String) -> Unit, toggleWant: () -> Unit, detailMessage: String?,
    editIdentity: (String, String, String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var openingEpub by remember(book.uri) { mutableStateOf(false) }
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
        ActivityResultContracts.CreateDocument("application/epub+zip")) { destination ->
        if (destination != null && !exportingEpub) scope.launch {
            exportingEpub = true
            try {
                withContext(Dispatchers.IO) {
                    val source = cachedEpub(context, book)
                    context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                        source.inputStream().use { it.copyTo(output, 64 * 1024) }
                    } ?: throw IllegalStateException("No se pudo escribir el archivo")
                }
                android.widget.Toast.makeText(context, "EPUB guardado", android.widget.Toast.LENGTH_LONG).show()
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
        title = { Text("Borrar EPUB") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text("Se borrará de Drive únicamente esta copia:")
            Text(displayTitle(book) + " · " + displayAuthor(book), fontWeight = FontWeight.Bold)
            Text("Tamaño: " + readableSize(book.sourceSize))
            Text("Archivo: " + (duplicateNames[book.uri] ?: "Consultando nombre…"),
                fontSize = 12.sp)
            if (possibleDuplicates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Otras copias que se conservarán:", fontWeight = FontWeight.Bold)
                possibleDuplicates.forEach { other ->
                    Text("• " + (duplicateNames[other.uri] ?: displayTitle(other)) + " · " + readableSize(other.sourceSize), fontSize = 12.sp)
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
            navigationIcon = { TextButton(onClick = back) { Text("‹ Volver", color = Paper) } },
            actions = { TextButton(onClick = toggleFavorite) { Text(if (book.favorite) "★" else "☆", color = Paper) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Mahogany)
        )
    }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (detailMessage ?: message)?.let { notice -> item { Text(notice, color = Mahogany, fontSize = 12.sp) } }
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
                    if (!openingEpub) scope.launch {
                        openingEpub = true
                        try {
                            if (openEpubInReader(context, book)) {
                                onOpened()
                                setStatus(ReadingStatus.READING)
                            }
                        } finally { openingEpub = false }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !openingEpub) {
                    Text(if (openingEpub) "Preparando EPUB…" else "📖  Abrir EPUB")
                }
                OutlinedButton(onClick = {
                    val safeName = displayTitle(book).replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(90)
                    exportPicker.launch((safeName.ifBlank { "Libro" }) + ".epub")
                }, modifier = Modifier.fillMaxWidth(), enabled = !exportingEpub) {
                    Text(if (exportingEpub) "Guardando EPUB…" else "Descargar EPUB de Drive")
                }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = {
                        titleDraft = displayTitle(book); authorDraft = displayAuthor(book)
                        sagaDraft = book.saga; orderDraft = book.sagaOrder
                        editIdentityDialog = true
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Editar título y autor", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedButton(onClick = { searchCoverImages(context, book) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text("Buscar portada", fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(onClick = { coverPicker.launch("image/*") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text("Cambiar portada", fontSize = 12.sp, maxLines = 1)
                        }
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
                Text("Estado", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Mahogany)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadingStatus.entries.forEach { status ->
                        FilterChip(book.status == status, { setStatus(status) }, { Text(status.label) })
                    }
                }
            }
            item {
                BookPanel("Datos del libro", book.uri.toString()) {
                    Info("Publicación", book.date); Info("Editorial", book.publisher)
                    Info("Género", book.genre); Info("ISBN", book.isbn)
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
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = enrich, enabled = !infoLoading,
                        modifier = Modifier.weight(1f)) {
                        Text(if (infoLoading) "Buscando…" else "Reintentar búsqueda")
                    }
                    OutlinedButton(onClick = {
                        plotDraft = book.spanishPlot.ifBlank { plot }
                        bioDraft = book.authorBio
                        editInfo = true
                    }, modifier = Modifier.weight(1f)) { Text("Editar texto") }
                }
            }
            item {
                BookPanel("Mis observaciones", book.uri.toString()) {
                    OutlinedTextField(notesDraft, { notesDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Escribe tus notas sobre este libro") }, minLines = 3)
                    OutlinedButton(onClick = { saveNotes(notesDraft) }) {
                        Text("Guardar observaciones")
                    }
                }
            }
            item {
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