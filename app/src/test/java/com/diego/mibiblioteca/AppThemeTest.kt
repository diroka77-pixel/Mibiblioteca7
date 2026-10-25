package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {
    @Test fun automaticFollowsTheSystem() {
        assertTrue(resolveDarkTheme(ThemePreference.SYSTEM, systemDark = true))
        assertFalse(resolveDarkTheme(ThemePreference.SYSTEM, systemDark = false))
    }

    @Test fun manualChoiceIgnoresTheSystem() {
        assertFalse(resolveDarkTheme(ThemePreference.LIGHT, systemDark = true))
        assertTrue(resolveDarkTheme(ThemePreference.DARK, systemDark = false))
    }

    @Test fun cyclesThroughTheThreeOptions() {
        assertEquals(ThemePreference.LIGHT, ThemePreference.SYSTEM.next())
        assertEquals(ThemePreference.DARK, ThemePreference.LIGHT.next())
        assertEquals(ThemePreference.SYSTEM, ThemePreference.DARK.next())
    }

    @Test fun unknownStoredValueFallsBackToAutomatic() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored("rosa"))
        assertEquals(ThemePreference.DARK, ThemePreference.fromStored("DARK"))
    }

    @Test fun galleryGetsMoreColumnsOnWiderScreens() {
        assertEquals(2, galleryColumnsFor(411))
        assertEquals(3, galleryColumnsFor(700))
        assertEquals(4, galleryColumnsFor(1000))
        assertEquals(5, galleryColumnsFor(1400))
    }
}
