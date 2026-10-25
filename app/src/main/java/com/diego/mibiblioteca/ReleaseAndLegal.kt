package com.diego.mibiblioteca

internal data class LegalNotice(val name: String, val notice: String)

internal fun shouldShowReleaseNotes(
    firstRunGuideVisible: Boolean,
    seenVersion: String?,
    currentVersion: String
): Boolean = !firstRunGuideVisible && currentVersion.isNotBlank() && seenVersion != currentVersion

internal fun currentReleaseNotes(): List<String> = listOf(
    "Centro de ayuda y guía inicial disponibles desde el menú.",
    "Diagnóstico privado para soporte, sin títulos, notas ni rutas de archivos.",
    "Pantallas de privacidad, permisos e información legal.",
    "Mantenimiento de la biblioteca con revisión segura y sin borrado automático."
)

internal fun legalNotices(): List<LegalNotice> = listOf(
    LegalNotice("AndroidX, Jetpack Compose y Material", "Componentes de interfaz y plataforma distribuidos principalmente bajo licencia Apache 2.0."),
    LegalNotice("Gson", "Biblioteca de serialización de Google distribuida bajo licencia Apache 2.0."),
    LegalNotice("jsoup", "Biblioteca de análisis HTML distribuida bajo licencia MIT."),
    LegalNotice("Google ML Kit", "Se utiliza para traducción, identificación de idioma y lectura de códigos ISBN. Su uso está sujeto a las condiciones de Google."),
    LegalNotice("Open Library y Google Books", "Fuentes externas de metadatos y portadas. Los datos y las imágenes pertenecen a sus respectivos proveedores y titulares."),
    LegalNotice("Project Gutenberg", "Los clásicos de ejemplo son obras de dominio público descargadas de gutenberg.org a petición del usuario. Project Gutenberg es una marca de la Project Gutenberg Literary Archive Foundation, que no está vinculada a esta aplicación."),
    LegalNotice("Wikipedia y Wikcionario", "Fuentes externas para biografías, argumentos y definiciones. Cada contenido conserva las condiciones indicadas por Wikimedia."),
    LegalNotice("DLE de la RAE", "${AppBrand.NAME} abre el sitio oficial para consultas voluntarias. La RAE y sus marcas no están vinculadas a esta aplicación."),
    LegalNotice("Davefx y sherpa-onnx", "Motor de voz opcional que no se incluye en ${AppBrand.NAME}. Su página oficial se abre externamente y conserva su propia licencia."),
    LegalNotice("Fuentes literarias", "Las noticias y enlaces de editoriales, librerías y catálogos se muestran como referencias externas; sus nombres y marcas pertenecen a sus titulares.")
)
