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
    val status: ReadingStatus = ReadingStatus.PENDING
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    var query by mutableStateOf("")
    var statusFilter by mutableStateOf<ReadingStatus?>(null)
    var onlyFavorites by mutableStateOf(false)
    var books by mutableStateOf<List<Book>>(emptyList()); private set
    var syncing by mutableStateOf(false); private set
    var folderName by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set

    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val folderKey = "folder_uri"

    init { prefs.getString(folderKey, null)?.let { sync(Uri.parse(it)) } }

    val filtered: List<Book> get() = books.filter { b ->
        val haystack = listOf(b.title,b.author,b.date,b.publisher,b.genre,b.description,b.isbn,b.saga).joinToString(" ")
        haystack.contains(query, true) && (statusFilter == null || b.status == statusFilter) && (!onlyFavorites || b.favorite)
    }

    fun selectFolder(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString(folderKey, uri.toString()).apply()
        sync(uri)
    }

    fun sync(uri: Uri? = prefs.getString(folderKey, null)?.let(Uri::parse)) {
        if (uri == null) { message = "Selecciona primero tu carpeta de libros."; return }
        viewModelScope.launch {
            syncing = true; message = null
            val result = withContext(Dispatchers.IO) { scanFolder(getApplication(), uri) }
            books = result
            folderName = DocumentFile.fromTreeUri(getApplication(), uri)?.name
            syncing = false
            message = "${result.size} EPUB encontrados"
        }
    }

    fun toggleFavorite(uri: Uri) { books = books.map { if (it.uri == uri) it.copy(favorite=!it.favorite) else it } }
    fun setStatus(uri: Uri, status: ReadingStatus) { books = books.map { if (it.uri == uri) it.copy(status=status) else it } }
    fun clearMessage() { message = null }
}

private fun scanFolder(context: Context, treeUri: Uri): List<Book> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val out = mutableListOf<Book>()
    fun walk(dir: DocumentFile) {
        dir.listFiles().forEach { f ->
            if (f.isDirectory) walk(f)
            else if (f.name?.endsWith(".epub", true) == true) out += readEpub(context, f.uri, f.name ?: "Libro")
        }
    }
    walk(root)
    return out.sortedBy { it.title.lowercase() }
}

private fun readEpub(context: Context, uri: Uri, fallbackName: String): Book {
    return try {
        val container = zipEntry(context, uri, "META-INF/container.xml")?.toString(Charsets.UTF_8).orEmpty()
        val opfPath = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(container)?.groupValues?.get(1)
        if (opfPath == null) return Book(uri, fallbackName.removeSuffix(".epub"))
        val opfBytes = zipEntry(context, uri, opfPath) ?: return Book(uri, fallbackName.removeSuffix(".epub"))
        val meta = parseOpf(opfBytes)
        val base = opfPath.substringBeforeLast('/', "")
        val coverPath = meta.coverHref?.let { if (base.isBlank()) it else "$base/$it" }
        val cover = coverPath?.let { zipEntry(context, uri, normalizePath(it)) }
        Book(uri, meta.title.ifBlank { fallbackName.removeSuffix(".epub") }, meta.author, meta.date, meta.publisher, meta.genre, meta.description, meta.isbn, meta.saga, cover)
    } catch (_: Exception) { Book(uri, fallbackName.removeSuffix(".epub")) }
}

private fun zipEntry(context: Context, uri: Uri, wanted: String): ByteArray? {
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (normalizePath(e.name) == normalizePath(wanted)) return zip.readBytes()
                e = zip.nextEntry
            }
        }
    }
    return null
}

private fun normalizePath(path: String): String {
    val parts = mutableListOf<String>()
    path.replace('\\','/').split('/').forEach { when(it) { "", "." -> {}; ".." -> if(parts.isNotEmpty()) parts.removeAt(parts.lastIndex); else -> parts += it } }
    return parts.joinToString("/")
}

private data class EpubMeta(var title:String="",var author:String="Autor desconocido",var date:String="",var publisher:String="",var genre:String="",var description:String="Sin descripción disponible.",var isbn:String="",var saga:String="",var coverHref:String?=null)

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = darkColorScheme()) { LibraryApp() } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryApp(vm: LibraryViewModel = viewModel()) {
    var selected by remember { mutableStateOf<Book?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::selectFolder) }
    val current = selected?.let { s -> vm.books.firstOrNull { it.uri == s.uri } }
    if (current != null) { BookDetail(current, { selected=null }, { vm.toggleFavorite(current.uri) }, { vm.setStatus(current.uri,it) }); return }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Mi Biblioteca", fontWeight = FontWeight.Bold); vm.folderName?.let { Text(it, style = MaterialTheme.typography.labelSmall) } } },
                actions = { TextButton(onClick = { folderPicker.launch(null) }) { Text("Carpeta") } }
            )
        },
        bottomBar = {
            Button(onClick = { vm.sync() }, enabled = !vm.syncing, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(if (vm.syncing) "Sincronizando…" else "↻  Sincronizar biblioteca")
            }
        }
    ) { p ->
        Column(Modifier.padding(p).padding(horizontal=16.dp)){
            OutlinedTextField(vm.query,{vm.query=it},Modifier.fillMaxWidth().padding(top=12.dp),singleLine=true,label={Text("Buscar título, autor, saga, género…")},leadingIcon={Text("⌕")})
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(vm.statusFilter==null,{vm.statusFilter=null},{Text("Todos")})
                FilterChip(vm.onlyFavorites,{vm.onlyFavorites=!vm.onlyFavorites},{Text("★")})
                FilterChip(vm.statusFilter==ReadingStatus.READING,{vm.statusFilter=if(vm.statusFilter==ReadingStatus.READING)null else ReadingStatus.READING},{Text("Leyendo")})
                FilterChip(vm.statusFilter==ReadingStatus.READ,{vm.statusFilter=if(vm.statusFilter==ReadingStatus.READ)null else ReadingStatus.READ},{Text("Leídos")})
            }
            Text("${vm.filtered.size} libros",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=8.dp))
            if(vm.books.isEmpty() && !vm.syncing) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("📚",style=MaterialTheme.typography.displayMedium);Text("Selecciona tu carpeta de EPUB en Google Drive");Spacer(Modifier.height(12.dp));Button(onClick={folderPicker.launch(null)}){Text("Elegir carpeta")}}}
            else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(vm.filtered,key={it.uri.toString()}){book->BookCard(book){selected=book}}}
        }
    }
    vm.message?.let { msg -> LaunchedEffect(msg){ kotlinx.coroutines.delay(2200); vm.clearMessage() } }
}

@Composable private fun BookCard(book:Book,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Cover(book,70.dp,100.dp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(book.title,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(book.favorite)Text("★")};Text(book.author);Text(listOf(book.date.take(4),book.genre).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall);Text(book.status.label,style=MaterialTheme.typography.labelMedium)}}}}

@Composable private fun Cover(book:Book,w:androidx.compose.ui.unit.Dp,h:androidx.compose.ui.unit.Dp){val bmp=remember(book.cover){book.cover?.let{BitmapFactory.decodeByteArray(it,0,it.size)}};Surface(Modifier.width(w).height(h),shape=MaterialTheme.shapes.small,tonalElevation=5.dp){if(bmp!=null)Image(bmp.asImageBitmap(),book.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Box(contentAlignment=Alignment.Center){Text("📖",style=MaterialTheme.typography.headlineLarge)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BookDetail(book:Book,back:()->Unit,toggleFavorite:()->Unit,setStatus:(ReadingStatus)->Unit){val context=androidx.compose.ui.platform.LocalContext.current;Scaffold(topBar={TopAppBar(title={Text("Ficha del libro")},navigationIcon={TextButton(onClick=back){Text("‹ Volver")}},actions={TextButton(onClick=toggleFavorite){Text(if(book.favorite)"★" else "☆")}})}){p->LazyColumn(Modifier.padding(p).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Row{Cover(book,110.dp,160.dp);Spacer(Modifier.width(18.dp));Column{Text(book.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(book.author,style=MaterialTheme.typography.titleMedium);if(book.saga.isNotBlank())Text("Saga: ${book.saga}")}}};item{Text("Estado",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){ReadingStatus.entries.forEach{s->FilterChip(book.status==s,{setStatus(s)},{Text(s.label)})}}};item{Info("Publicación",book.date);Info("Editorial",book.publisher);Info("Género",book.genre);Info("ISBN",book.isbn)};item{Text("Argumento / Sinopsis",fontWeight=FontWeight.Bold);Text(book.description)};item{Button(onClick={val i=Intent(Intent.ACTION_VIEW).apply{setDataAndType(book.uri,"application/epub+zip");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};try{context.startActivity(i)}catch(_:Exception){}},modifier=Modifier.fillMaxWidth()){Text("📖  Abrir EPUB")}}}}}

@Composable private fun Info(label:String,value:String){if(value.isNotBlank()){Text(label,fontWeight=FontWeight.Bold);Text(value);Spacer(Modifier.height(4.dp))}}
