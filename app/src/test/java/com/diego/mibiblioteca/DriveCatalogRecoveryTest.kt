package com.diego.mibiblioteca

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCatalogRecoveryTest {
    @Test fun prefersLargePreviousSnapshotWhenCatalogCollapsed() {
        assertTrue(shouldPreferPreviousDriveSnapshot(1, 1, 415))
    }

    @Test fun keepsCurrentSnapshotWhenCountsAreSimilar() {
        assertFalse(shouldPreferPreviousDriveSnapshot(415, 410, 415))
    }

    @Test fun avoidsTreatingSmallCatalogsAsRecoverySources() {
        assertFalse(shouldPreferPreviousDriveSnapshot(1, 1, 2))
        assertFalse(shouldRecoverDriveCatalog(10, 19))
    }

    @Test fun recoversWhenSnapshotIsMuchLargerThanLocalCatalog() {
        assertTrue(shouldRecoverDriveCatalog(1, 415))
        assertFalse(shouldRecoverDriveCatalog(400, 415))
    }
}
