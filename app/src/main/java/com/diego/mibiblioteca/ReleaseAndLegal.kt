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
    "Disponible en español, inglés, portugués, francés, alemán e italiano.",
    "Solo fuentes abiertas y oficiales para portadas, fichas y novedades."
)

internal fun legalNotices(): List<LegalNotice> = listOf(
    LegalNotice("AndroidX, Jetpack Compose y Material", "Componentes de interfaz y plataforma distribuidos principalmente bajo licencia Apache 2.0."),
    LegalNotice("Gson", "Biblioteca de serialización de Google distribuida bajo licencia Apache 2.0."),
    LegalNotice("jsoup", "Biblioteca de análisis HTML distribuida bajo licencia MIT."),
    LegalNotice("Google ML Kit", "Se utiliza para traducción, identificación de idioma y lectura de códigos ISBN. Su uso está sujeto a las condiciones de Google."),
    LegalNotice("Open Library y Google Books", "Fuentes externas de metadatos y portadas. Los datos y las imágenes pertenecen a sus respectivos proveedores y titulares."),
    LegalNotice("Project Gutenberg", "Los clásicos de ejemplo son obras de dominio público descargadas de gutenberg.org a petición del usuario. Project Gutenberg es una marca de la Project Gutenberg Literary Archive Foundation, que no está vinculada a esta aplicación."),
    LegalNotice("Wikipedia y Wikcionario", "Fuentes externas para biografías, argumentos y definiciones. Cada contenido conserva las condiciones indicadas por Wikimedia."),
    LegalNotice("DLE de la RAE", "{app} abre el sitio oficial para consultas voluntarias. La RAE y sus marcas no están vinculadas a esta aplicación."),
    LegalNotice("Davefx y sherpa-onnx", "Motor de voz opcional que no se incluye en {app}. Su página oficial se abre externamente y conserva su propia licencia."),
    LegalNotice("Fuentes literarias", "Las novedades proceden de la API pública de Google Books. Los botones de Goodreads, Casa del Libro, YouTube y otras webs solo abren esas páginas cuando el usuario lo pide; la app no lee sus webs por su cuenta. Sus nombres y marcas pertenecen a sus titulares.")
)
