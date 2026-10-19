package com.diego.mibiblioteca

internal data class SupportDiagnosticInput(
    val appVersion: String,
    val versionCode: Long,
    val androidVersion: String,
    val deviceModel: String,
    val totalBooks: Int,
    val readingBooks: Int,
    val offlineBooks: Int,
    val folderLinked: Boolean,
    val cameraGranted: Boolean,
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
    val driveDiagnostic: String,
    val performanceDiagnostic: String
)

internal fun buildSupportDiagnostic(input: SupportDiagnosticInput): String = buildString {
    appendLine("Diagnóstico de MiBiblioteca")
    appendLine("Versión: ${input.appVersion} (${input.versionCode})")
    appendLine("Android: ${input.androidVersion}")
    appendLine("Dispositivo: ${input.deviceModel.ifBlank { "No identificado" }}")
    appendLine("Libros catalogados: ${input.totalBooks}")
    appendLine("Leyendo: ${input.readingBooks}")
    appendLine("Copias sin conexión: ${input.offlineBooks}")
    appendLine("Carpeta vinculada: ${if (input.folderLinked) "Sí" else "No"}")
    appendLine("Cámara: ${permissionLabel(input.cameraGranted)}")
    appendLine("Micrófono: ${permissionLabel(input.microphoneGranted)}")
    appendLine("Notificaciones: ${permissionLabel(input.notificationsGranted)}")
    appendLine()
    appendLine(redactSensitiveDiagnostic(input.driveDiagnostic)
        .ifBlank { "Sin diagnóstico de sincronización." })
    if (input.performanceDiagnostic.isNotBlank()) {
        appendLine()
        appendLine("Rendimiento de la sesión")
        appendLine(redactSensitiveDiagnostic(input.performanceDiagnostic))
    }
    appendLine()
    append("El informe no contiene títulos, notas, rutas ni contenido de los libros.")
}.trim()

internal fun permissionLabel(granted: Boolean): String = if (granted) "Permitido" else "No concedido"

internal fun redactSensitiveDiagnostic(text: String): String = text
    .replace(Regex("(?:content|file)://\\S+", RegexOption.IGNORE_CASE), "[ruta omitida]")
    .replace(Regex("(?:/storage|/data|/sdcard)/\\S+", RegexOption.IGNORE_CASE), "[ruta omitida]")
