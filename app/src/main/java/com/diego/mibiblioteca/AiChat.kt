package com.diego.mibiblioteca

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val ChatWine = Color(0xFF49352A)
private val ChatTeal = Color(0xFF176C66)
private val ChatPaper = Color(0xFFF7F6F2)
private data class ChatLine(val fromUser: Boolean, val text: String)

private object ChatCredential {
    private const val alias = "mibiblioteca_gemini_key"
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(context: Context, key: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(key.toByteArray()), Base64.NO_WRAP)
        context.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).edit().putString("key", encoded).apply()
    }
    fun read(context: Context): String? {
        return try {
        val encrypted = context.getSharedPreferences("ai_chat", Context.MODE_PRIVATE)
            .getString("key", null) ?: return null
        val bytes = Base64.decode(encrypted, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
        } catch (_: Exception) { null }
    }
    fun clear(context: Context) {
        context.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).edit().remove("key").apply()
    }
}

private fun loadChat(context: Context): List<ChatLine> = try {
    val array = JSONArray(context.getSharedPreferences("ai_chat", Context.MODE_PRIVATE)
        .getString("history", "[]"))
    (0 until array.length()).map { i -> array.getJSONObject(i).let {
        ChatLine(it.optBoolean("user"), it.optString("text"))
    } }
} catch (_: Exception) { emptyList() }

private fun saveChat(context: Context, lines: List<ChatLine>) {
    val array = JSONArray()
    lines.takeLast(30).forEach { array.put(JSONObject().put("user", it.fromUser).put("text", it.text)) }
    context.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).edit()
        .putString("history", array.toString()).apply()
}

private fun askGemini(key: String, history: List<ChatLine>): String {
    val body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray()
        .put(JSONObject().put("text", "Responde siempre en castellano. Eres un asistente de lectura de Mi Biblioteca. Sé claro, breve y reconoce la incertidumbre."))))
    val messages = JSONArray()
    history.takeLast(12).forEach { line -> messages.put(JSONObject()
        .put("role", if (line.fromUser) "user" else "model")
        .put("parts", JSONArray().put(JSONObject().put("text", line.text)))) }
    body.put("contents", messages)
    val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
        .openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.connectTimeout = 8000
    conn.readTimeout = 35000
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    conn.setRequestProperty("x-goog-api-key", key)
    try {
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val result = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText().take(100_000) }.orEmpty()
        val json = JSONObject(result)
        if (conn.responseCode !in 200..299)
            throw IllegalStateException(json.optJSONObject("error")?.optString("message")
                ?.take(180).orEmpty().ifBlank { "Error de Google (${conn.responseCode})" })
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        return (0 until (parts?.length() ?: 0)).mapNotNull { parts?.optJSONObject(it)?.optString("text") }
            .joinToString("\n").ifBlank { "No llegó una respuesta de texto. Prueba con otra pregunta." }
    } finally { conn.disconnect() }
}

@Composable fun AiChatScreen(onBack: () -> Unit, onGoogle: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<ChatLine>().apply { addAll(loadChat(context)) } }
    val listState = rememberLazyListState()
    var key by remember { mutableStateOf(ChatCredential.read(context)) }
    var keyDialog by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    if (keyDialog) AlertDialog(onDismissRequest = { keyDialog = false },
        title = { Text("Conectar Gemini") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Para conversar dentro de Mi Biblioteca necesitas una clave propia de Gemini API en Google AI Studio. Se guarda cifrada en este teléfono y tus preguntas se envían a Google. Consulta las cuotas y posibles cargos de tu proyecto.")
            OutlinedTextField(keyDraft, { keyDraft = it.trim() }, singleLine = true,
                label = { Text("Clave de Gemini API") },
                visualTransformation = PasswordVisualTransformation())
            Text("Crear o consultar mi clave", color = ChatTeal,
                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://aistudio.google.com/api-keys"))) })
            if (key != null) Text("Eliminar clave del teléfono", color = ChatWine,
                modifier = Modifier.clickable {
                    ChatCredential.clear(context); key = null; keyDialog = false
                })
        } },
        confirmButton = { TextButton(onClick = {
            if (keyDraft.isNotBlank()) {
                try { ChatCredential.save(context, keyDraft); key = keyDraft; keyDraft = ""; keyDialog = false }
                catch (_: Exception) { error = "No se pudo guardar la clave en el teléfono." }
            }
        }, enabled = keyDraft.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { keyDialog = false }) { Text("Cancelar") } })

    Scaffold(containerColor = ChatPaper,
        topBar = {
            Row(Modifier.fillMaxWidth().background(ChatWine).statusBarsPadding().height(64.dp)
                .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Volver", tint = Color.White) }
                Text("Consulta literaria", Modifier.weight(1f), color = Color.White,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { keyDraft = ""; keyDialog = true }) {
                    Icon(Icons.Outlined.Settings, "Configurar Gemini", tint = Color.White)
                }
            }
        }, bottomBar = {
            Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()
                .imePadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f),
                    placeholder = { Text("Escribe tu consulta…") }, maxLines = 4,
                    shape = RoundedCornerShape(20.dp))
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = {
                    val question = input.trim()
                    if (question.isNotEmpty() && key != null && !busy) {
                        input = ""; error = null
                        lines.add(ChatLine(true, question)); saveChat(context, lines)
                        val activeKey = key!!
                        busy = true
                        scope.launch {
                            try {
                                val answer = withContext(Dispatchers.IO) { askGemini(activeKey, lines.toList()) }
                                lines.add(ChatLine(false, answer)); saveChat(context, lines)
                            } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo responder." }
                            finally { busy = false }
                        }
                    } else if (key == null) keyDialog = true
                }, enabled = input.isNotBlank() && !busy,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ChatTeal)) {
                    Icon(Icons.Outlined.Send, "Enviar")
                }
            }
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lines.isEmpty()) item {
                Text("Tu rincón de consultas", color = ChatWine, fontSize = 23.sp,
                    fontWeight = FontWeight.Bold)
                Text("Pregunta por autores, sagas o recomendaciones. Las respuestas aparecen aquí, sin salir de la aplicación.",
                    color = ChatWine, modifier = Modifier.padding(top = 10.dp))
            }
            if (key == null) item {
                Button(onClick = { keyDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ChatTeal)) { Text("Conectar Gemini") }
                TextButton(onClick = onGoogle) { Text("Abrir modo IA de Google") }
            }
            itemsIndexed(lines) { index, line ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser)
                    Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(shape = RoundedCornerShape(18.dp), color = if (line.fromUser)
                        ChatTeal else Color.White, shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(0.88f)) {
                        Text(line.text, Modifier.padding(14.dp), color = if (line.fromUser)
                            Color.White else ChatWine, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
            if (busy) item { CircularProgressIndicator(color = ChatTeal, modifier = Modifier.size(24.dp)) }
            error?.let { message -> item { Text(message, color = Color(0xFFAC3E35)) } }
            if (lines.isNotEmpty()) item {
                TextButton(onClick = { lines.clear(); saveChat(context, lines) }) { Text("Borrar conversación") }
            }
        }
    }
}
