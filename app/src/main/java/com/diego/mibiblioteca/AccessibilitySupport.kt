package com.diego.mibiblioteca

import java.util.Locale

internal fun percentDescription(label: String, percent: Int): String =
    "$label: ${percent.coerceIn(0, 100)} por ciento"

internal fun pageDescription(pageIndex: Int, pageCount: Int): String {
    val safeCount = pageCount.coerceAtLeast(1)
    val page = (pageIndex + 1).coerceIn(1, safeCount)
    return "Página $page de $safeCount"
}

internal fun speechRateDescription(rate: Float): String =
    "Velocidad de lectura: ${String.format(Locale.forLanguageTag("es-ES"), "%.1f", rate)} veces"

