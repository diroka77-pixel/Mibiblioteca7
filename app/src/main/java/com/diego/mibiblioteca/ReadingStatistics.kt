package com.diego.mibiblioteca

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

data class ReadingStatsInput(
    val status: ReadingStatus,
    val progress: Int = 0,
    val finishedDate: String = "",
    val rating: Int = 0,
    val genres: List<String> = emptyList(),
    val author: String = "",
    val rereadCount: Int = 0
)

data class ReadingStatsSummary(
    val totalRead: Int,
    val reading: Int,
    val pending: Int,
    val currentYearRead: Int,
    val currentMonthRead: Int,
    val previousYearRead: Int,
    val yearOverYearDelta: Int,
    val goal: Int,
    val goalPercent: Float,
    val remainingForGoal: Int,
    val projectedYearTotal: Int,
    val requiredMonthlyPace: Double,
    val onTrackForGoal: Boolean,
    val averageActiveProgress: Int,
    val averageRating: Double?,
    val rereads: Int,
    val completedByMonth: List<Int>,
    val bestMonth: Int?,
    val bestMonthCount: Int,
    val catalogReadPercent: Int,
    val topGenres: List<Pair<String, Int>>,
    val topAuthors: List<Pair<String, Int>>,
    val monthStreak: Int,
    val readWithoutFinishDate: Int
)

private val readingDateFormats = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("d/M/uuuu"),
    DateTimeFormatter.ofPattern("d-M-uuuu"),
    DateTimeFormatter.ofPattern("d.M.uuuu")
)

internal fun parseReadingDate(raw: String): LocalDate? {
    val value = raw.trim()
    if (value.isBlank()) return null
    for (format in readingDateFormats) {
        try { return LocalDate.parse(value, format) }
        catch (_: DateTimeParseException) { }
    }
    return null
}

internal fun calculateReadingStats(
    books: List<ReadingStatsInput>,
    today: LocalDate = LocalDate.now(),
    annualGoal: Int = 24
): ReadingStatsSummary {
    val safeGoal = annualGoal.coerceIn(1, 365)
    val readBooks = books.filter { it.status == ReadingStatus.READ }
    val datedReads = readBooks.mapNotNull { book -> parseReadingDate(book.finishedDate)?.let { it to book } }
    val currentYear = datedReads.filter { it.first.year == today.year }
    val previousYearRead = datedReads.count { it.first.year == today.year - 1 }
    val months = (1..12).map { month -> currentYear.count { it.first.monthValue == month } }
    val activeProgress = books.filter { it.status == ReadingStatus.READING }
        .map { it.progress.coerceIn(0, 100) }.filter { it > 0 }
    val ratings = readBooks.map { it.rating }.filter { it in 1..5 }
    val readMonths = datedReads.map { YearMonth.from(it.first) }.toSet()
    var cursor = YearMonth.from(today)
    if (cursor !in readMonths) cursor = cursor.minusMonths(1)
    var streak = 0
    while (cursor in readMonths) { streak++; cursor = cursor.minusMonths(1) }
    val yearRead = currentYear.size
    val projected = if (yearRead == 0) 0 else
        (yearRead * today.lengthOfYear().toDouble() / today.dayOfYear).roundToInt()
    val remaining = (safeGoal - yearRead).coerceAtLeast(0)
    val monthsRemaining = (13 - today.monthValue).coerceAtLeast(1)
    val bestMonthCount = months.maxOrNull() ?: 0
    val bestMonth = if (bestMonthCount == 0) null else months.indexOfLast { it == bestMonthCount } + 1
    fun top(values: List<String>) = values.map(String::trim).filter(String::isNotBlank)
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
        .take(5).map { it.key to it.value }
    return ReadingStatsSummary(
        totalRead = readBooks.size,
        reading = books.count { it.status == ReadingStatus.READING },
        pending = books.count { it.status == ReadingStatus.PENDING },
        currentYearRead = yearRead,
        currentMonthRead = currentYear.count { it.first.monthValue == today.monthValue },
        previousYearRead = previousYearRead,
        yearOverYearDelta = yearRead - previousYearRead,
        goal = safeGoal,
        goalPercent = (yearRead.toFloat() / safeGoal).coerceIn(0f, 1f),
        remainingForGoal = remaining,
        projectedYearTotal = projected,
        requiredMonthlyPace = remaining.toDouble() / monthsRemaining,
        onTrackForGoal = remaining == 0 || projected >= safeGoal,
        averageActiveProgress = activeProgress.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0,
        averageRating = ratings.average().takeIf { !it.isNaN() },
        rereads = readBooks.sumOf { it.rereadCount.coerceAtLeast(0) },
        completedByMonth = months,
        bestMonth = bestMonth,
        bestMonthCount = bestMonthCount,
        catalogReadPercent = if (books.isEmpty()) 0 else
            (readBooks.size * 100.0 / books.size).roundToInt(),
        topGenres = top(readBooks.flatMap { it.genres }),
        topAuthors = top(readBooks.map { it.author }),
        monthStreak = streak,
        readWithoutFinishDate = readBooks.count { parseReadingDate(it.finishedDate) == null }
    )
}
