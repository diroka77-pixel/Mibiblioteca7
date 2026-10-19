package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSyncPolicyTest {
    @Test fun securityFailureIsPermissionWithoutForgettingTheFolder() {
        assertEquals(
            DriveFailureKind.PERMISSION,
            classifyDriveFailure("java.lang.SecurityException", "Permission denied")
        )
        val message = driveFailureMessage(DriveFailureKind.PERMISSION, true, "Libros")
        assertTrue(message.contains("vinculación se conserva"))
        assertTrue(message.contains("Tus libros guardados se conservan"))
    }

    @Test fun temporaryProviderFailureDoesNotLookLikeExpiredPermission() {
        assertEquals(
            DriveFailureKind.PROVIDER_BUSY,
            classifyDriveFailure("android.os.RemoteException", "Provider loading")
        )
        assertTrue(
            driveFailureMessage(DriveFailureKind.PROVIDER_BUSY, true, "Libros")
                .contains("Inténtalo de nuevo")
        )
    }

    @Test fun networkFailureKeepsLocalCatalogAvailable() {
        assertEquals(
            DriveFailureKind.OFFLINE,
            classifyDriveFailure("java.net.UnknownHostException", null)
        )
        assertTrue(
            driveFailureMessage(DriveFailureKind.OFFLINE, true, null)
                .contains("Tus libros guardados se conservan")
        )
    }

    @Test fun diagnosticContainsUsefulCountsWithoutBookTitles() {
        val diagnostic = DriveSyncDiagnostic(
            folderUri = "content://drive/tree/books",
            folderName = "Libros",
            startedAt = 10L,
            finishedAt = 20L,
            detected = 512,
            added = 2,
            modified = 1,
            unconfirmed = 0
        ).toDisplayText()
        assertTrue(diagnostic.contains("Archivos detectados: 512"))
        assertTrue(diagnostic.contains("Nuevos: 2"))
        assertTrue(diagnostic.contains("Modificados: 1"))
    }

    @Test fun failuresRemainVisibleUntilTheUserActs() {
        assertTrue(libraryNoticeIsPersistent("No se pudo leer la carpeta", null))
        assertTrue(libraryNoticeIsPersistent("Drive está ocupado", DriveFailureKind.PROVIDER_BUSY))
        assertEquals(false, libraryNoticeIsPersistent("Sincronización completada", null))
    }
}
