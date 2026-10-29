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
