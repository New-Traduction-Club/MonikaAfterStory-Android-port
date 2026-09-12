package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastBlurTest {

    @Test
    fun testZeroOrNegativeRadiusReturnsOriginal() {
        val pix = intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt())
        val result = FastBlur.blurPixels(pix, 2, 1, 0)
        assertEquals(pix[0], result[0])
        assertEquals(pix[1], result[1])

        val resultNeg = FastBlur.blurPixels(pix, 2, 1, -5)
        assertEquals(pix[0], resultNeg[0])
        assertEquals(pix[1], resultNeg[1])
    }

    @Test
    fun testInvalidDimensionsHandledSafely() {
        val pix = intArrayOf(0xFF112233.toInt())
        assertEquals(pix, FastBlur.blurPixels(pix, 0, 1, 2))
        assertEquals(pix, FastBlur.blurPixels(pix, 1, 0, 2))
        assertEquals(pix, FastBlur.blurPixels(pix, 5, 5, 2)) // smaller than w * h
    }

    @Test
    fun testUniformColorRemainsUniformAfterBlur() {
        val w = 8
        val h = 8
        val color = 0xFF2A2A2E.toInt()
        val pix = IntArray(w * h) { color }

        val blurred = FastBlur.blurPixels(pix, w, h, 3)
        for (p in blurred) {
            assertEquals(color, p)
        }
    }

    @Test
    fun testBlurSmoothsHighContrastEdge() {
        val w = 10
        val h = 10
        val pix = IntArray(w * h) { index ->
            val x = index % w
            if (x < 5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }

        val blurred = FastBlur.blurPixels(pix, w, h, 2)

        val centerPixel = blurred[5 * w + 4]
        val r = (centerPixel shr 16) and 0xFF
        assertTrue("Expected red component to be blended, was $r", r in 30..225)
    }

    @Test
    fun testAlphaChannelPreserved() {
        val w = 4
        val h = 4
        val pix = IntArray(w * h) { 0xFF123456.toInt() }
        val blurred = FastBlur.blurPixels(pix, w, h, 2)
        for (p in blurred) {
            assertEquals(0xFF000000.toInt(), (p.toLong() and 0xFF000000L).toInt())
        }
    }

    @Test
    fun testPerformanceOnDownsampledResolution() {
        val w = 36
        val h = 37
        val pix = IntArray(w * h) { index ->
            0xFF000000.toInt() or (index * 13 and 0xFFFFFF)
        }

        val startTime = System.currentTimeMillis()
        val blurred = FastBlur.blurPixels(pix, w, h, 10)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(w * h, blurred.size)
        assertTrue("FastBlur took too long: ${elapsed}ms", elapsed < 100)
    }
}
