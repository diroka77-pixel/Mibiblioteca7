package com.diego.mibiblioteca

import android.content.Context
import android.content.res.Resources
import android.telephony.TelephonyManager

/**
 * País real del dispositivo. Primero la tarjeta SIM o la red móvil (no necesitan ningún
 * permiso) y, si no hay (tablets con solo wifi), la región del sistema.
 *
 * No se usa Locale.getDefault(): cuando el usuario elige el idioma dentro de la app,
 * Android puede dejar el idioma sin país («es» en vez de «es-ES»).
 */
internal object DeviceRegion {
    @Volatile var detected: String? = null

    fun detect(context: Context) {
        val telephony = runCatching {
            val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            listOf(manager?.simCountryIso, manager?.networkCountryIso)
                .firstNotNullOfOrNull { it?.uppercase()?.takeIf(::isCountryCode) }
        }.getOrNull()
        detected = telephony ?: systemRegion()
    }

    private fun systemRegion(): String? = runCatching {
        Resources.getSystem().configuration.locales[0].country.uppercase().takeIf(::isCountryCode)
    }.getOrNull()

    fun current(): String? = detected ?: systemRegion()
        ?: java.util.Locale.getDefault().country.uppercase().takeIf(::isCountryCode)
}

private fun isCountryCode(value: String): Boolean = value.matches(Regex("[A-Z]{2}"))

/**
 * Mercado editorial para las novedades: país, idioma de los libros y grandes editoriales
 * de ese país. Buscar las novedades de editoriales conocidas evita los autopublicados
 * sin portada que devolvía «lo más nuevo» de Google Books, casi siempre en inglés.
 */
internal data class ReleaseMarket(val country: String, val language: String, val publishers: List<String>)

private val spainPublishers = listOf("Planeta", "Alfaguara", "Destino", "Seix Barral", "Anagrama",
    "Tusquets", "Salamandra", "Plaza & Janés", "Lumen", "Espasa", "Grijalbo", "Ediciones B",
    "Suma de Letras", "Minotauro")
private val latamPublishers = listOf("Planeta", "Alfaguara", "Penguin Random House", "Seix Barral",
    "Grijalbo", "Destino", "Océano", "Debolsillo", "Anagrama", "Tusquets")
private val usPublishers = listOf("Knopf", "Doubleday", "Penguin", "Random House", "Simon & Schuster",
    "HarperCollins", "Little, Brown", "Scribner", "St. Martin's", "Viking", "Del Rey", "Tor")
private val ukPublishers = listOf("Penguin", "Bloomsbury", "Faber", "Picador", "Hodder", "Vintage",
    "Orion", "Macmillan", "HarperCollins", "Transworld")
private val francePublishers = listOf("Gallimard", "Albin Michel", "Grasset", "Flammarion", "Actes Sud",
    "Seuil", "Robert Laffont", "Fayard", "Calmann-Lévy", "Stock", "Michel Lafon")
private val germanyPublishers = listOf("Suhrkamp", "Hanser", "Kiepenheuer & Witsch", "Rowohlt",
    "S. Fischer", "Diogenes", "Goldmann", "Heyne", "Droemer", "Piper", "Ullstein", "dtv", "Blanvalet")
private val italyPublishers = listOf("Mondadori", "Einaudi", "Feltrinelli", "Rizzoli", "Adelphi",
    "Garzanti", "Longanesi", "Sellerio", "Bompiani", "Neri Pozza", "Giunti", "Sperling & Kupfer")
private val brazilPublishers = listOf("Companhia das Letras", "Intrínseca", "Rocco", "Record", "Sextante",
    "Arqueiro", "Suma", "Todavia", "Alfaguara", "Planeta")
private val portugalPublishers = listOf("Porto Editora", "Bertrand", "Leya", "Presença", "Dom Quixote",
    "Asa", "Relógio D'Água", "Alfaguara", "Quetzal")

private val latamCountries = setOf("MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO",
    "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR")
private val ukCountries = setOf("GB", "IE", "AU", "NZ", "ZA")

/**
 * Elige el mercado por el país del dispositivo. En países con varios idiomas (Suiza,
 * Bélgica, Canadá) decide el idioma de la app; si el país no está en la lista, también.
 */
internal fun releaseMarket(country: String?, appLanguage: String): ReleaseMarket {
    val c = country?.uppercase().orEmpty()
    return when {
        c == "ES" -> ReleaseMarket("ES", "es", spainPublishers)
        c in latamCountries -> ReleaseMarket(c, "es", latamPublishers)
        c == "US" || (c == "CA" && appLanguage != "fr") -> ReleaseMarket(c, "en", usPublishers)
        c in ukCountries -> ReleaseMarket(c, "en", ukPublishers)
        c == "FR" || c == "LU" || c == "MC" ||
            (c in setOf("BE", "CH", "CA") && appLanguage == "fr") -> ReleaseMarket(c, "fr", francePublishers)
        c == "DE" || c == "AT" || c == "LI" ||
            (c == "CH" && appLanguage == "de") -> ReleaseMarket(c, "de", germanyPublishers)
        c == "IT" || c == "SM" || (c == "CH" && appLanguage == "it") -> ReleaseMarket(c, "it", italyPublishers)
        c == "BR" -> ReleaseMarket("BR", "pt", brazilPublishers)
        c == "PT" || c == "AO" || c == "MZ" -> ReleaseMarket(c, "pt", portugalPublishers)
        else -> when (appLanguage) {
            "es" -> ReleaseMarket("ES", "es", spainPublishers)
            "fr" -> ReleaseMarket("FR", "fr", francePublishers)
            "de" -> ReleaseMarket("DE", "de", germanyPublishers)
            "it" -> ReleaseMarket("IT", "it", italyPublishers)
            "pt" -> ReleaseMarket("BR", "pt", brazilPublishers)
            else -> ReleaseMarket("US", "en", usPublishers)
        }
    }
}

/** Mercado actual: país del dispositivo e idioma de la app. */
internal fun currentReleaseMarket(): ReleaseMarket =
    releaseMarket(DeviceRegion.current(), I18n.currentLanguage())

/**
 * Editoriales a consultar en esta actualización: ocho, rotando cada día para que con el
 * tiempo aparezcan todas sin gastar demasiadas consultas del cupo gratuito de Google Books.
 */
internal fun publishersForToday(market: ReleaseMarket, dayOfYear: Int, count: Int = 8): List<String> {
    val all = market.publishers
    if (all.size <= count) return all
    val start = (dayOfYear * 3) % all.size
    return (0 until count).map { all[(start + it) % all.size] }
}

/** Consulta de Google Books para las novedades de una editorial en un mercado. */
internal fun publisherReleasesUrl(publisher: String, market: ReleaseMarket, maxResults: Int = 20): String =
    "https://www.googleapis.com/books/v1/volumes?q=" +
        java.net.URLEncoder.encode("inpublisher:\"$publisher\"", "UTF-8") +
        "&orderBy=newest&printType=books&maxResults=${maxResults.coerceIn(1, 40)}" +
        "&langRestrict=${market.language}&country=${market.country}"

/** Texto comparable: sin tildes, en minúsculas y solo letras y números. */
internal fun publisherKey(value: String): String =
    java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

/** Resúmenes, cuadernos, agendas, packs… no son novedades literarias. */
private val notANovelty = Regex("(?i)\\b(summary of|resumen de|study guide|workbook|cuaderno|agenda|" +
    "calendar|calendario|colou?ring|colorear|box(ed)? set|estuche|pack|coffret|kalender|quaderno)\\b")

internal fun isLikelyNovelty(title: String): Boolean = !notANovelty.containsMatchIn(title)

/**
 * Reparte el resultado entre editoriales (una de cada, por turnos) para que no salgan
 * doce libros de la misma y el carrusel tenga variedad.
 */
internal fun <T> interleave(groups: List<List<T>>): List<T> {
    val result = mutableListOf<T>()
    var index = 0
    while (groups.any { index < it.size }) {
        groups.forEach { group -> group.getOrNull(index)?.let(result::add) }
        index++
    }
    return result
}
