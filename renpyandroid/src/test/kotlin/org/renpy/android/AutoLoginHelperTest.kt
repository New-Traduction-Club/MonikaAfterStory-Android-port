package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLoginHelperTest {

    @Test
    fun testModesList() {
        assertEquals(4, AutoLoginHelper.MODES.size)
        assertTrue(AutoLoginHelper.MODES.contains(AutoLoginHelper.MODE_DISABLED))
        assertTrue(AutoLoginHelper.MODES.contains(AutoLoginHelper.MODE_MAS))
        assertTrue(AutoLoginHelper.MODES.contains(AutoLoginHelper.MODE_MINE))
        assertTrue(AutoLoginHelper.MODES.contains(AutoLoginHelper.MODE_LAST_USED))
    }

    @Test
    fun testResolveDisabledMode() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_DISABLED, ProfileNavigationHelper.PROFILE_MAS)
        assertNull(result)
    }

    @Test
    fun testResolveMasMode() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_MAS, ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER)
        assertEquals(ProfileNavigationHelper.PROFILE_MAS, result)
    }

    @Test
    fun testResolveMineMode() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_MINE, ProfileNavigationHelper.PROFILE_MAS)
        assertEquals(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER, result)
    }

    @Test
    fun testResolveLastUsedModeWithMas() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_LAST_USED, ProfileNavigationHelper.PROFILE_MAS)
        assertEquals(ProfileNavigationHelper.PROFILE_MAS, result)
    }

    @Test
    fun testResolveLastUsedModeWithMine() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_LAST_USED, ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER)
        assertEquals(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER, result)
    }

    @Test
    fun testResolveLastUsedModeWithNullFallsBackToMas() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_LAST_USED, null)
        assertEquals(ProfileNavigationHelper.PROFILE_MAS, result)
    }

    @Test
    fun testResolveUnknownModeReturnsNull() {
        val result = AutoLoginHelper.resolveProfileForMode("unknown_mode", ProfileNavigationHelper.PROFILE_MAS)
        assertNull(result)
    }
}
