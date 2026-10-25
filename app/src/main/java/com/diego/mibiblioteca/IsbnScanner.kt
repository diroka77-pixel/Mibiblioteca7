package com.diego.mibiblioteca

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Lee el código de barras de un libro con el escáner de Google Play services:
 * vídeo en directo a pantalla completa, enfoque y zoom automáticos. No necesita el
 * permiso de cámara porque la cámara la gestiona Google Play services.
 *
 * Antes se usaba una foto en miniatura (TakePicturePreview): Android la entrega
 * a muy baja resolución y las barras finas de un EAN-13 no se distinguían.
 */
internal fun scanIsbnWithGoogle(
    context: Context,
    onIsbn: (String) -> Unit,
    onNotIsbn: () -> Unit,
    onUnavailable: () -> Unit
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()
    try {
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val isbn = normalizeIsbn(barcode.rawValue.orEmpty())
                if (isbn != null) onIsbn(isbn) else onNotIsbn()
            }
            .addOnCanceledListener { }
            .addOnFailureListener { onUnavailable() }
    } catch (_: Exception) {
        onUnavailable()
    }
}

/**
 * Devuelve un ISBN limpio (10 o 13 caracteres) o null si el código no es de un libro.
 * Los EAN-13 de libros empiezan por 978 o 979.
 */
internal fun normalizeIsbn(raw: String): String? {
    val clean = raw.uppercase().filter { it.isDigit() || it == 'X' }
    return when {
        clean.length == 13 && (clean.startsWith("978") || clean.startsWith("979")) &&
            clean.all(Char::isDigit) -> clean
        clean.length == 10 && clean.dropLast(1).all(Char::isDigit) -> clean
        else -> null
    }
}

/** Formas equivalentes de un ISBN (13 y, si existe, 10 cifras) para comparar. */
internal fun isbnVariants(raw: String): Set<String> {
    val isbn = normalizeIsbn(raw) ?: return emptySet()
    return when (isbn.length) {
        13 -> setOfNotNull(isbn, isbn13To10(isbn))
        else -> setOf(isbn, isbn10To13(isbn))
    }
}

internal fun isbn13To10(isbn13: String): String? {
    if (isbn13.length != 13 || !isbn13.startsWith("978")) return null
    val core = isbn13.substring(3, 12)
    val sum = core.mapIndexed { index, c -> (10 - index) * (c - '0') }.sum()
    val check = (11 - sum % 11) % 11
    return core + if (check == 10) "X" else check.toString()
}

internal fun isbn10To13(isbn10: String): String {
    val core = "978" + isbn10.take(9)
    val sum = core.mapIndexed { index, c -> (c - '0') * if (index % 2 == 0) 1 else 3 }.sum()
    return core + ((10 - sum % 10) % 10).toString()
}
