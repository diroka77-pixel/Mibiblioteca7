package com.diego.mibiblioteca

/** A provider listing is not proof that a missing file was deleted. */
internal fun <T, K> mergeLibraryScan(scanned: List<T>, before: Map<K, T>, latest: Map<K, T>,
    key: (T) -> K, merge: (T, T?) -> T): List<T> {
    val visible = scanned.filter { key(it) !in before || key(it) in latest }
    val confirmed = visible.map(key).toSet()
    return visible.map { merge(it, latest[key(it)]) } + latest.values.filter { key(it) !in confirmed }
}
