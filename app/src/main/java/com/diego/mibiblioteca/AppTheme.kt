package com.diego.mibiblioteca

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Preferencia de tema de la interfaz de la biblioteca (el lector tiene la suya). */
internal enum class ThemePreference(val label: String) {
    SYSTEM("Automático"), LIGHT("Claro"), DARK("Oscuro");

    fun next(): ThemePreference = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStored(value: String?): ThemePreference =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

internal fun resolveDarkTheme(preference: ThemePreference, systemDark: Boolean): Boolean = when (preference) {
    ThemePreference.SYSTEM -> systemDark
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

/**
 * Estado global del tema. Los colores de la app se leen desde aquí, de modo que
 * al cambiarlo se repinta toda la interfaz sin reiniciar la actividad.
 * Se escribe solo desde la actividad o desde un clic, nunca durante el dibujado.
 */
internal object AppThemeState {
    private const val PREFS = "apariencia"
    private const val KEY = "tema"

    var preference by mutableStateOf(ThemePreference.SYSTEM)
        private set
    var dark by mutableStateOf(false)
        private set

    fun load(context: Context) {
        preference = ThemePreference.fromStored(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        refresh(context)
    }

    /** Recalcula el modo según la preferencia y el modo oscuro del sistema. */
    fun refresh(context: Context) {
        val systemDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        dark = resolveDarkTheme(preference, systemDark)
    }

    fun cycle(context: Context) {
        preference = preference.next()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, preference.name).apply()
        refresh(context)
    }
}
