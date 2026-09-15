package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperManagerTest {

    @Test
    fun testWallpaperTargetEnum() {
        val targets = WallpaperManager.WallpaperTarget.values()
        assertEquals(3, targets.size)

        assertEquals("wallpaper_mas", WallpaperManager.WallpaperTarget.MAS.prefsKey)
        assertEquals("wallpaper_mine", WallpaperManager.WallpaperTarget.MINE.prefsKey)
        assertEquals("wallpaper_lockscreen", WallpaperManager.WallpaperTarget.LOCKSCREEN.prefsKey)
    }

    @Test
    fun testWallpaperCropNormalization() {
        val crop = WallpaperManager.WallpaperCrop(
            left = 0.8f,
            top = 0.9f,
            right = 0.2f,
            bottom = 0.1f
        )
        val normalized = crop.normalized()

        assertEquals(0.2f, normalized.left, 0.001f)
        assertEquals(0.1f, normalized.top, 0.001f)
        assertEquals(0.8f, normalized.right, 0.001f)
        assertEquals(0.9f, normalized.bottom, 0.001f)
        assertFalse(normalized.isFullFrame())
    }

    @Test
    fun testWallpaperCropFullFrame() {
        val fullFrameCrop = WallpaperManager.WallpaperCrop(
            left = 0f,
            top = 0f,
            right = 1f,
            bottom = 1f
        )
        assertTrue(fullFrameCrop.isFullFrame())

        val partialCrop = WallpaperManager.WallpaperCrop(
            left = 0.1f,
            top = 0f,
            right = 1f,
            bottom = 1f
        )
        assertFalse(partialCrop.isFullFrame())
    }
}
