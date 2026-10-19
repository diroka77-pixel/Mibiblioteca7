package com.diego.mibiblioteca

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class UnifiedLibraryMode(val label: String) {
    BOOKS("Libros"), GENRES("Géneros"), SECTIONS("Secciones")
}

@Composable
internal fun UnifiedLibraryModeBar(
    current: UnifiedLibraryMode,
    onBooks: () -> Unit,
    onGenres: () -> Unit,
    onSections: () -> Unit
) {
    val actions = listOf(
        Triple(UnifiedLibraryMode.BOOKS, Icons.Outlined.AutoStories, onBooks),
        Triple(UnifiedLibraryMode.GENRES, Icons.Outlined.Category, onGenres),
        Triple(UnifiedLibraryMode.SECTIONS, Icons.Outlined.CollectionsBookmark, onSections)
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = Color(0xD31D1B19),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .34f))
    ) {
        Row(Modifier.fillMaxWidth().padding(3.dp)) {
            actions.forEach { (mode, icon, action) ->
                val selected = current == mode
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .semantics { stateDescription = if (selected) "Seleccionado" else "No seleccionado" }
                        .selectable(selected = selected, onClick = action, role = Role.Tab),
                    color = if (selected) Color(0xFF9A6843) else Color.Transparent,
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White)
                        Text(
                            mode.label,
                            modifier = Modifier.padding(start = 5.dp),
                            color = Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
