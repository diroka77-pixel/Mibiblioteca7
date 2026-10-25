package com.diego.mibiblioteca

import android.app.Activity
import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * Traducción de la interfaz.
 *
 * Cada texto se escribe en español en el código y se traduce con [tr]. Las
 * traducciones están en `src/main/resources/i18n/translations.tsv`: una fila por
 * texto y una columna por idioma (es, en, pt, fr, de, it). Para añadir o corregir
 * una traducción basta con editar esa tabla; no hace falta tocar el código.
 *
 * Idioma: el del teléfono (o el elegido para la app en Ajustes de Android 13+).
 * Si no está disponible, la app se muestra en inglés. Un texto que no esté en la
 * tabla se muestra tal cual, en español.
 */
internal object I18n {
    /** Idiomas de la tabla, en el orden de sus columnas. */
    val languages = listOf("es", "en", "pt", "fr", "de", "it")
    private const val FALLBACK = "en"

    private val table: Map<String, List<String>> by lazy { load() }

    private fun load(): Map<String, List<String>> = runCatching {
        val stream = I18n::class.java.getResourceAsStream("/i18n/translations.tsv")
            ?: return@runCatching emptyMap()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines -> parse(lines.toList()) }
    }.getOrDefault(emptyMap())

    /** Lee la tabla. Ignora la cabecera, las líneas vacías y los comentarios (#). */
    fun parse(lines: List<String>): Map<String, List<String>> = lines.asSequence()
        .drop(1)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line -> line.split('\t').map { it.replace("\\n", "\n") } }
        .filter { it.size == languages.size && it[0].isNotBlank() }
        // Los espacios finales («Fondo: ») pueden perderse al editar la tabla o al
        // aplicar un parche: se recuperan a partir del texto en español.
        .map { row -> row.mapIndexed { index, value ->
            if (index > 0 && row[0].endsWith(" ") && !value.endsWith(" ")) "$value " else value
        } }
        .associate { it[0] to it }

    /** Idioma elegido dentro de la app (Ajustes → Idioma); null = el del teléfono. */
    @Volatile var chosenLanguage: String? = null

    /** Idioma que se usará: el elegido en la app; si no, el del teléfono; si no está, inglés. */
    fun currentLanguage(locale: Locale = Locale.getDefault()): String =
        chosenLanguage?.takeIf { it in languages }
            ?: locale.language.lowercase().let { if (it in languages) it else FALLBACK }

    fun translate(spanish: String, language: String = currentLanguage()): String {
        if (language == "es") return spanish
        val row = table[spanish] ?: return spanish
        return row[languages.indexOf(language)].ifBlank { row[languages.indexOf(FALLBACK)] }
            .ifBlank { spanish }
    }

    /** Para las pruebas: traduce con una tabla dada sin leer el archivo. */
    fun translateWith(rows: Map<String, List<String>>, spanish: String, language: String): String {
        if (language == "es") return spanish
        val row = rows[spanish] ?: return spanish
        return row[languages.indexOf(language)].ifBlank { spanish }
    }

    /** Todas las filas, para comprobar la tabla en las pruebas. */
    fun allRows(): Map<String, List<String>> = table
}

/** Traduce un texto escrito en español al idioma de la interfaz. */
internal fun tr(spanish: String): String = I18n.translate(spanish)

/**
 * Para palabras que en español significan cosas distintas según el sitio
 * («Inicio» como pestaña o como fecha de inicio). En la tabla la fila se llama
 * "contexto|texto"; en español se muestra solo el texto.
 */
internal fun tr(spanish: String, context: String): String {
    val key = "$context|$spanish"
    val translated = I18n.translate(key)
    return if (translated == key) spanish else translated
}

/** Igual que [tr], pero sustituye {app} por el nombre de la aplicación. */
internal fun trApp(spanish: String): String = tr(spanish).replace("{app}", AppBrand.NAME)

/**
 * Traduce un texto con datos: {0}, {1}… se sustituyen por los argumentos, de modo
 * que cada idioma puede colocarlos donde le corresponda. {app} es el nombre de la app.
 */
internal fun trf(spanish: String, vararg args: Any?): String {
    var result = trApp(spanish)
    args.forEachIndexed { index, value -> result = result.replace("{$index}", value.toString()) }
    return result
}

/**
 * Selector de idioma de la app (Ajustes → Idioma). Se guarda en el teléfono y, en
 * Android 13 o posterior, se comunica también al sistema para que el nombre de los
 * accesos directos y los textos de Android sigan el mismo idioma.
 */
internal object AppLanguage {
    private const val PREFS = "apariencia"
    private const val KEY = "idioma"

    /** Nombre de cada idioma escrito en ese mismo idioma, como es costumbre en los selectores. */
    val nativeNames = linkedMapOf("es" to "Español", "en" to "English", "pt" to "Português",
        "fr" to "Français", "de" to "Deutsch", "it" to "Italiano")

    fun load(context: Context) {
        I18n.chosenLanguage = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)?.takeIf { it in I18n.languages }
    }

    /** Cambia el idioma (null = el del teléfono) y vuelve a dibujar la app. */
    fun choose(activity: Activity, language: String?) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, language).apply()
        I18n.chosenLanguage = language
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                activity.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales =
                    if (language == null) android.os.LocaleList.getEmptyLocaleList()
                    else android.os.LocaleList.forLanguageTags(if (language == "pt") "pt-BR" else language)
            }
        }
        // En Android 13+ el sistema ya recrea la pantalla al cambiar el idioma; recrearla
        // también aquí es inofensivo y garantiza el cambio en versiones anteriores.
        activity.recreate()
    }
}
