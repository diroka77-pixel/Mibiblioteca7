package com.diego.mibiblioteca

import androidx.compose.ui.graphics.Color

/**
 * Shared palette for the unified library redesign.
 *
 * Keep decorative wallpaper independent from readable foreground surfaces:
 * cover art is always fitted, never cropped, and controls use opaque surfaces.
 * These tokens are intentionally opt-in until each screen is migrated.
 */
internal object LibraryVisualTokens {
    val surface = Color(0xFFFFFFFF)
    val surfaceMuted = Color(0xFFF5F0E9)
    val textPrimary = Color(0xFF27211D)
    val textSecondary = Color(0xFF5B5149)
    val overlay = Color(0xE822201E)
    val accent = Color(0xFF7D6754)
    val onDark = Color(0xFFFFFFFF)
    val onDarkSecondary = Color(0xFFECE5DE)

    const val transitionFastMs = 180
    const val transitionNormalMs = 280
    const val minimumContrastTarget = 4.5
}

/** Non-UI rules shared by gallery, cards and detail views. */
internal object CoverPresentation {
    // Fit retains the entire image. Crop would cut off titles or illustrations.
    const val cropArtwork = false

    /** Stable dimensions: no stretched or squashed covers. */
    fun fittedSize(sourceWidth: Int, sourceHeight: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int>? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) return null
        val scale = minOf(maxWidth.toDouble() / sourceWidth, maxHeight.toDouble() / sourceHeight, 1.0)
        return Pair((sourceWidth * scale).toInt().coerceAtLeast(1), (sourceHeight * scale).toInt().coerceAtLeast(1))
    }
}

/**
 * Fingerprint based on the image bytes rather than the identity of the array.
 * Drive and backup restores may recreate an equivalent ByteArray; the bitmap
 * cache must still be reusable in that case and must change when the image does.
 */
internal fun coverContentFingerprint(bytes: ByteArray): Int = bytes.contentHashCode()

/** Font size used by the ceremonial opening cover.
 * Long words get extra room because serif bold italic glyphs are wider than
 * ordinary body text and Compose may otherwise split a word by characters. */
internal fun animatedCoverTitleSizeSp(title: String): Int {
    val clean = title.trim().replace(Regex("\\s+"), " ")
    val longest = clean.split(' ').maxOfOrNull(String::length) ?: 0
    return when {
        longest >= 18 -> 10
        longest >= 14 -> 11
        longest >= 10 -> 13
        clean.length > 30 -> 14
        clean.length > 22 -> 16
        else -> 18
    }
}
