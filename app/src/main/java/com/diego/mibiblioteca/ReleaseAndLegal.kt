package com.diego.mibiblioteca

internal data class LegalNotice(val name: String, val notice: String)

internal fun shouldShowReleaseNotes(
    firstRunGuideVisible: Boolean,
    seenVersion: String?,
    currentVersion: String
): Boolean = !firstRunGuideVisible && currentVersion.isNotBlank() && seenVersion != currentVersion

internal fun currentReleaseNotes(): List<String> = listOf(
    "Nuevo nombre e icono: {app}.",
    "Modo oscuro en Ajustes → Tema.",
    "Cinco clásicos gratuitos de dominio público en tu idioma para empezar a leer.",
    "Wikipedia y diccionario en el idioma de la app, y galería adaptada a tablets.",
    "Novedades de las grandes editoriales de tu país, siempre con portada.",
    "Estantería «Últimos añadidos» en Inicio con los cinco libros más recientes.",
    "Abre y vincula gratis tus libros del teléfono: Descargas, memoria interna o tarjeta SD.",
    "Disponible en español, inglés, portugués, francés, alemán e italiano.",
    "Solo fuentes abiertas: Open Library, Wikipedia y Project Gutenberg."
)

/** Explicación de PREMIUM que cierra la ventana de novedades. */
internal const val RELEASE_PREMIUM_NOTE =
    "Con un pago único, para siempre, sin suscripción ni anuncios, vincula tu carpeta de libros en la nube (Google Drive, Dropbox, OneDrive u otra). {app} catalogará automáticamente todos tus libros con su portada, sinopsis y autor, y añadirá solos los que subas después. Leer, escuchar, los clásicos y las carpetas del propio teléfono siguen siendo gratis. Lo encontrarás en el menú lateral."

internal fun legalNotices(): List<LegalNotice> = listOf(
    LegalNotice("AndroidX, Jetpack Compose y Material", "Componentes de interfaz y plataforma distribuidos principalmente bajo licencia Apache 2.0."),
    LegalNotice("Gson", "Biblioteca de serialización de Google distribuida bajo licencia Apache 2.0."),
    LegalNotice("jsoup", "Biblioteca de análisis HTML distribuida bajo licencia MIT."),
    LegalNotice("Google ML Kit", "Se utiliza para traducción, identificación de idioma y lectura de códigos ISBN. Su uso está sujeto a las condiciones de Google."),
    LegalNotice("Open Library", "Fuente de fichas, portadas, recomendaciones y novedades, consultada mediante su API abierta. Los datos bibliográficos de Open Library son de dominio público (CC0); las portadas pertenecen a sus titulares."),
    LegalNotice("Project Gutenberg", "Los clásicos de ejemplo son obras de dominio público descargadas de gutenberg.org a petición del usuario. Project Gutenberg es una marca de la Project Gutenberg Literary Archive Foundation, que no está vinculada a esta aplicación."),
    LegalNotice("Wikipedia y Wikcionario", "Fuentes de biografías, argumentos y definiciones. Sus textos se usan bajo la licencia Creative Commons Atribución-CompartirIgual 4.0 (CC BY-SA 4.0); cada texto indica su fuente y enlaza al original."),
    LegalNotice("DLE de la RAE", "{app} abre el sitio oficial para consultas voluntarias. La RAE y sus marcas no están vinculadas a esta aplicación."),
    LegalNotice("Davefx y sherpa-onnx", "Motor de voz opcional que no se incluye en {app}. Su página oficial se abre externamente y conserva su propia licencia."),
    LegalNotice("Fuentes literarias", "Las novedades proceden de la API abierta de Open Library. Los botones de Goodreads, Casa del Libro, YouTube y otras webs solo abren esas páginas cuando el usuario lo pide; la app no lee sus webs por su cuenta. Sus nombres y marcas pertenecen a sus titulares.")
)
