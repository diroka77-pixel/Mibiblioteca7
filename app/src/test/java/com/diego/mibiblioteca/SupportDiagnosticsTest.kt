package com.diego.mibiblioteca

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportDiagnosticsTest {
    private fun sample() = SupportDiagnosticInput(
        appVersion = "1.0.60",
        versionCode = 160,
        androidVersion = "16",
        deviceModel = "Teléfono de prueba",
        totalBooks = 512,
        readingBooks = 3,
        offlineBooks = 8,
        folderLinked = true,
        cameraGranted = false,
        microphoneGranted = true,
        notificationsGranted = false,
        driveDiagnostic = "Archivos detectados: 512",
        performanceDiagnostic = "Fotogramas lentos: 0"
    )

    @Test fun reportContainsUsefulTechnicalState() {
        val report = buildSupportDiagnostic(sample())
        assertTrue(report.contains("Versión: 1.0.60 (160)"))
        assertTrue(report.contains("Libros catalogados: 512"))
        assertTrue(report.contains("Micrófono: Permitido"))
        assertTrue(report.contains("Cámara: No concedido"))
        assertTrue(report.contains("Archivos detectados: 512"))
    }

    @Test fun reportStatesItsPrivacyBoundary() {
        val report = buildSupportDiagnostic(sample().copy(
            driveDiagnostic = "Error en content://drive/root/Libros/novela.epub",
            performanceDiagnostic = "Caché /storage/emulated/0/Books"
        ))
        assertTrue(report.contains("no contiene títulos"))
        assertFalse(report.contains("content://"))
        assertFalse(report.contains("/storage/"))
        assertTrue(report.contains("[ruta omitida]"))
    }
}
