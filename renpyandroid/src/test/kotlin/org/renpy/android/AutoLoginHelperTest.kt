package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLoginHelperTest {

    @Test
    fun testModesList() {
        assertEquals(2, AutoLoginHelper.MODES.size)
        assertTrue(AutoLoginHelper.MODES.contains(AutoLoginHelper.MODE_DISABLED))
        assertTrue(AutoLoginHelper.MODES.contains(AutoLoginHelper.MODE_MINE))
    }

    @Test
    fun testResolveDisabledMode() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_DISABLED, ProfileNavigationHelper.PROFILE_MAS)
        assertNull(result)
    }

    @Test
    fun testResolveMasModeReturnsNull() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_MAS, ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER)
        assertNull(result)
    }

    @Test
    fun testResolveMineMode() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_MINE, ProfileNavigationHelper.PROFILE_MAS)
        assertEquals(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER, result)
    }

    @Test
    fun testResolveLastUsedMode() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_LAST_USED, ProfileNavigationHelper.PROFILE_MAS)
        assertEquals(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER, result)
    }

    @Test
    fun testResolveLastUsedModeWithNullFallsBackToMine() {
        val result = AutoLoginHelper.resolveProfileForMode(AutoLoginHelper.MODE_LAST_USED, null)
        assertEquals(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER, result)
    }

    @Test
    fun testResolveUnknownModeReturnsNull() {
        val result = AutoLoginHelper.resolveProfileForMode("unknown_mode", ProfileNavigationHelper.PROFILE_MAS)
        assertNull(result)
    }
}
