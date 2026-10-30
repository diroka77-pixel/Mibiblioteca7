package com.diego.mibiblioteca

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumTest {
    @Test fun phoneFoldersAreLocalAndFree() {
        assertTrue(Premium.isLocalAuthority("com.android.externalstorage.documents"))
        assertTrue(Premium.isLocalAuthority("com.android.providers.downloads.documents"))
        assertTrue(Premium.isLocalAuthority("com.android.providers.media.documents"))
    }

    @Test fun cloudProvidersNeedPremium() {
        assertFalse(Premium.isLocalAuthority("com.google.android.apps.docs.storage"))
        assertFalse(Premium.isLocalAuthority("com.dropbox.product.android.dbapp.document_provider.documents"))
        assertFalse(Premium.isLocalAuthority("com.microsoft.skydrive.content.StorageAccessProvider"))
        assertFalse(Premium.isLocalAuthority(null))
    }
}

class PremiumTokenAndFolderTest {
    @Test fun tokenIsTiedToDeviceAndKind() {
        val a = premiumToken("device-a", "purchase")
        org.junit.Assert.assertEquals(a, premiumToken("device-a", "purchase"))
        org.junit.Assert.assertNotEquals(a, premiumToken("device-b", "purchase"))
        org.junit.Assert.assertNotEquals(a, premiumToken("device-a", "legacy"))
        org.junit.Assert.assertNotEquals("true", a)
    }

    @Test fun switchingFolderOrProviderIsDetected() {
        val drive = "com.google.android.apps.docs.storage"
        val dropbox = "com.dropbox.product.android.dbapp.document_provider.documents"
        org.junit.Assert.assertTrue(isDifferentFolderTree(drive, "root/Libros", dropbox, "/Libros"))
        org.junit.Assert.assertTrue(isDifferentFolderTree(drive, "Libros", drive, "Otra"))
        org.junit.Assert.assertFalse(isDifferentFolderTree(drive, "Libros", drive, "Libros"))
        org.junit.Assert.assertFalse(isDifferentFolderTree(drive, null, dropbox, "/Libros"))
    }
}
