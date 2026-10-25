package com.diego.mibiblioteca

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.net.HttpURLConnection
import java.security.MessageDigest

/**
 * Clave de la API de Google Books (opcional).
 *
 * Sin clave, todas las instalaciones comparten un cupo anónimo pequeño. Con clave, cada
 * proyecto tiene su propio cupo gratuito diario. La clave no está en el código: GitHub la
 * pasa al compilar desde el secreto GOOGLE_BOOKS_API_KEY (ver docs/GOOGLE_BOOKS_API.md).
 *
 * Para que nadie pueda reutilizar la clave fuera de la app, en Google Cloud se restringe
 * a «Aplicaciones Android» (paquete + huella SHA-1). Google comprueba esa restricción con
 * las cabeceras X-Android-Package y X-Android-Cert, que se añaden aquí.
 */
internal object GoogleBooksKey {
    val key: String get() = BuildConfig.GOOGLE_BOOKS_API_KEY
    @Volatile private var packageName = ""
    @Volatile private var certSha1 = ""

    fun init(context: Context) {
        packageName = context.packageName
        certSha1 = runCatching { signingSha1(context) }.getOrDefault("")
    }

    /** Añade la clave a una URL de Google Books, si hay clave. */
    fun withKey(url: String): String = withKey(url, key)

    internal fun withKey(url: String, apiKey: String): String =
        if (apiKey.isBlank() || !url.contains("www.googleapis.com/books") || url.contains("key=")) url
        else url + (if (url.contains('?')) "&" else "?") + "key=" + java.net.URLEncoder.encode(apiKey, "UTF-8")

    fun addHeaders(connection: HttpURLConnection) {
        if (key.isBlank()) return
        if (packageName.isNotBlank()) connection.setRequestProperty("X-Android-Package", packageName)
        if (certSha1.isNotBlank()) connection.setRequestProperty("X-Android-Cert", certSha1)
    }

    @Suppress("DEPRECATION")
    private fun signingSha1(context: Context): String {
        val pm = context.packageManager
        val signature = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return ""
        return MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
    }
}
