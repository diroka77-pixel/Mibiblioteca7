package com.diego.mibiblioteca

internal enum class DriveFailureKind {
    PERMISSION,
    OFFLINE,
    PROVIDER_BUSY,
    INVALID_FOLDER,
    UNKNOWN
}

internal fun classifyDriveFailure(typeName: String, rawMessage: String?): DriveFailureKind {
    val type = typeName.lowercase()
    val message = rawMessage.orEmpty().lowercase()
    return when {
        "securityexception" in type ||
            "permission" in message || "permiso" in message ||
            "denied" in message || "denegado" in message -> DriveFailureKind.PERMISSION
        "unknownhost" in type || "socket" in type ||
            "offline" in message || "sin conexión" in message ||
            "network" in message || "red no disponible" in message -> DriveFailureKind.OFFLINE
        "filenotfound" in type || "carpeta no disponible" in message ||
            "document not found" in message -> DriveFailureKind.INVALID_FOLDER
        "deadobject" in type || "remoteexception" in type ||
            "loading" in message || "cargando" in message ||
            "tempor" in message || "provider" in message -> DriveFailureKind.PROVIDER_BUSY
        else -> DriveFailureKind.UNKNOWN
    }
}

internal fun driveFailureMessage(
    kind: DriveFailureKind,
    hasCatalog: Boolean,
    folderName: String?
): String {
    val preserved = if (hasCatalog) " Tus libros guardados se conservan." else ""
    val folder = folderName?.takeIf(String::isNotBlank)?.let { " «$it»" }.orEmpty()
    return when (kind) {
        DriveFailureKind.PERMISSION ->
            "Android no permitió leer la carpeta$folder. La vinculación se conserva; puedes volver a autorizarla desde Ajustes.$preserved"
        DriveFailureKind.OFFLINE ->
            "Drive no está disponible sin conexión. Se intentará de nuevo cuando haya red.$preserved"
        DriveFailureKind.PROVIDER_BUSY ->
            "Drive todavía está preparando la carpeta$folder. Inténtalo de nuevo dentro de unos segundos.$preserved"
        DriveFailureKind.INVALID_FOLDER ->
            "Drive no encuentra la carpeta vinculada$folder. Puedes seleccionar de nuevo la carpeta desde Ajustes.$preserved"
        DriveFailureKind.UNKNOWN ->
            "No se pudo completar la lectura de Drive.$preserved"
    }
}

internal data class DriveSyncDiagnostic(
    val folderUri: String,
    val folderName: String,
    val startedAt: Long,
    val finishedAt: Long,
    val detected: Int,
    val added: Int,
    val modified: Int,
    val unconfirmed: Int,
    val failureKind: DriveFailureKind? = null,
    val failureDetail: String = ""
) {
    fun toDisplayText(): String = buildString {
        appendLine("Diagnóstico de sincronización")
        appendLine("Carpeta: " + folderName.ifBlank { "Sin nombre" })
        appendLine("Vinculación: " + if (folderUri.isBlank()) "No" else "Sí")
        appendLine("Inicio: $startedAt")
        appendLine("Fin: $finishedAt")
        appendLine("Archivos detectados: $detected")
        appendLine("Nuevos: $added")
        appendLine("Modificados: $modified")
        appendLine("No confirmados: $unconfirmed")
        if (failureKind != null) appendLine("Estado: $failureKind")
        if (failureDetail.isNotBlank()) appendLine("Detalle: $failureDetail")
    }.trim()
}
