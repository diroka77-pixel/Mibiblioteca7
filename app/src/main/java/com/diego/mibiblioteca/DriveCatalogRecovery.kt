package com.diego.mibiblioteca

private const val MIN_RECOVERABLE_CATALOG_SIZE = 20

internal fun shouldPreferPreviousDriveSnapshot(
    localCatalogSize: Int,
    currentSnapshotSize: Int,
    previousSnapshotSize: Int
): Boolean {
    val expectedSize = maxOf(localCatalogSize, currentSnapshotSize)
    return previousSnapshotSize >= MIN_RECOVERABLE_CATALOG_SIZE &&
        previousSnapshotSize >= maxOf(1, expectedSize) * 2
}

internal fun shouldRecoverDriveCatalog(localCatalogSize: Int, snapshotSize: Int): Boolean =
    snapshotSize >= MIN_RECOVERABLE_CATALOG_SIZE &&
        snapshotSize >= maxOf(1, localCatalogSize) * 2
