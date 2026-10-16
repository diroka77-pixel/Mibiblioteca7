package com.diego.mibiblioteca

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val SearchPaper = Color.White
private val SearchInk = Color(0xFF2C221D)
private val SearchMahogany = Color(0xFF49352A)
private val SearchBrass = Color(0xFFAD8248)
private val SearchTeal = Color(0xFF5AA69E)

@Composable
private fun LibrarySearchField(
    query: String,
    voiceListening: Boolean,
    onQueryCommit: (String) -> Unit,
    onVoice: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (query != draft) draft = query }
    LaunchedEffect(draft) {
        delay(180)
        if (draft != query) onQueryCommit(draft)
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
        singleLine = true,
        placeholder = { Text("Buscar título, saga o autor") },
        leadingIcon = { Icon(Icons.Outlined.Search, "Buscar") },
        trailingIcon = {
            if (draft.isNotEmpty()) IconButton(onClick = {
                draft = ""
                onQueryCommit("")
            }) { Icon(Icons.Outlined.Close, "Borrar búsqueda") }
            else IconButton(onClick = onVoice) {
                Icon(Icons.Outlined.Mic,
                    if (voiceListening) "Detener búsqueda por voz" else "Búsqueda por voz",
                    tint = if (voiceListening) SearchTeal else SearchMahogany)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SearchPaper, unfocusedContainerColor = SearchPaper,
            focusedTextColor = SearchInk, unfocusedTextColor = SearchInk),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    )
}

@Composable
private fun DiscoverSearchField(
    query: String,
    searching: Boolean,
    voiceListening: Boolean,
    onVoice: () -> Unit,
    onSearch: (String) -> Unit
) {
    var draft by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (query != draft) draft = query }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Título, autor o ISBN") },
        leadingIcon = { AppIcon("Buscar") },
        trailingIcon = {
            Row {
                IconButton(onClick = onVoice) {
                    Icon(Icons.Outlined.Mic,
                        if (voiceListening) "Detener voz" else "Buscar por voz",
                        tint = if (voiceListening) SearchTeal else SearchMahogany)
                }
                IconButton(enabled = !searching && draft.isNotBlank(),
                    onClick = { onSearch(draft.trim()) }) {
                    Icon(Icons.Outlined.Search, "Consultar")
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = SearchPaper,
            focusedTextColor = SearchInk,
            unfocusedTextColor = SearchInk,
            focusedBorderColor = SearchBrass,
            unfocusedBorderColor = SearchBrass.copy(alpha = .7f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    )
}
