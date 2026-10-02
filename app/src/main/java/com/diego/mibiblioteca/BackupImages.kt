package com.diego.mibiblioteca

import android.graphics.BitmapFactory
import org.json.JSONObject

internal fun validateBackupImages(backup: JSONObject) {
    val books = backup.getJSONArray("books")
    for (i in 0 until books.length()) {
        val image = books.getJSONObject(i).optString("coverBase64")
        if (image.isBlank()) continue
        val bytes = decodeBackupCover(image)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 8_000_000) {
            "El respaldo contiene una portada inválida o demasiado grande."
        }
    }
}
