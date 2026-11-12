package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorizeEngineTest {
    private fun r(c: Int) = (c shr 16) and 255
    private fun g(c: Int) = (c shr 8) and 255
    private fun b(c: Int) = c and 255

    @Test
    fun labRoundTrip() {
        for (c in listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF1E88E5.toInt(), 0xFF808080.toInt(), 0xFFF1C9A5.toInt())) {
            val lab = CutoutEngine.rgbToLab(r(c), g(c), b(c))
            val back = ColorizeEngine.labToRgb(lab[0], lab[1], lab[2])
            assertTrue("%06X -> %06X".format(c and 0xFFFFFF, back and 0xFFFFFF), abs(r(back) - r(c)) <= 2 && abs(g(back) - g(c)) <= 2 && abs(b(back) - b(c)) <= 2)
        }
    }

    @Test
    fun modelInputIsGreyFromLightness() {
        val w = 64; val h = 64
        val px = IntArray(w * h) { 0xFF336699.toInt() }
        val input = ColorizeEngine.modelInput(ColorizeEngine.lightness(px), w, h)
        val plane = ColorizeEngine.SIDE * ColorizeEngine.SIDE
        assertEquals(3 * plane, input.size)
        assertEquals(input[100], input[plane + 100], 1e-6f)
        assertEquals(input[100], input[2 * plane + 100], 1e-6f)
        // il grigio ha la stessa luminosità del colore originale
        val grey = (input[100] * 255).toInt()
        val lOrig = ColorizeEngine.lightness(intArrayOf(0xFF336699.toInt()))[0]
        val lGrey = ColorizeEngine.lightness(intArrayOf((0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey))[0]
        assertEquals(lOrig, lGrey, 0.8f)
    }

    @Test
    fun composeKeepsLightnessAndAppliesColour() {
        val w = 40; val h = 30
        val px = IntArray(w * h) { i -> val v = 40 + (i % w) * 4; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val plane = ColorizeEngine.SIDE * ColorizeEngine.SIDE
        val ab = FloatArray(2 * plane) { if (it < plane) 40f else 20f } // colore caldo uniforme
        val out = ColorizeEngine.compose(px, w, h, ab, 1f, guidePx = null)
        val lIn = ColorizeEngine.lightness(px); val lOut = ColorizeEngine.lightness(out)
        for (i in px.indices step 37) assertEquals(lIn[i], lOut[i], 1.5f)
        val c = out[15 * w + 20]
        assertTrue("caldo: r > b", r(c) > b(c) + 20)
        // saturazione zero = bianco e nero
        val grey = ColorizeEngine.compose(px, w, h, ab, 0f, guidePx = null)[15 * w + 20]
        assertTrue(abs(r(grey) - b(grey)) <= 2)
    }
}
