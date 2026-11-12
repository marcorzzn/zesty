package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InpaintEngineTest {

    @Test
    fun cropSurroundsTheZoneWithContext() {
        val w = 1000; val h = 800
        val mask = FloatArray(w * h)
        for (y in 300 until 360) for (x in 400 until 480) mask[y * w + x] = 1f
        val c = InpaintEngine.crop(mask, w, h)!!
        assertTrue(c.l <= 400 - 48 && c.r >= 480 + 48 && c.t <= 300 - 48 && c.b >= 360 + 48)
        assertTrue(c.l >= 0 && c.t >= 0 && c.r <= w && c.b <= h)
        assertEquals(c.w, c.h) // quadrato quando la foto lo consente
        assertNull(InpaintEngine.crop(FloatArray(w * h), w, h))
    }

    @Test
    fun cropStaysInsideNearTheBorder() {
        val w = 600; val h = 300
        val mask = FloatArray(w * h)
        for (y in 0 until 20) for (x in 580 until 600) mask[y * w + x] = 1f
        val c = InpaintEngine.crop(mask, w, h)!!
        assertTrue(c.l >= 0 && c.t >= 0 && c.r <= w && c.b <= h)
        assertTrue(c.r == w && c.t == 0)
    }

    @Test
    fun inputsMarkSoftEdgesAsHoleAndScaleImage() {
        val w = 200; val h = 200
        val px = IntArray(w * h) { 0xFF808080.toInt() }
        val mask = FloatArray(w * h)
        for (y in 90 until 110) for (x in 90 until 110) mask[y * w + x] = 0.05f // bordo morbido del pennello
        val c = InpaintEngine.crop(mask, w, h)!!
        val (image, m) = InpaintEngine.inputs(px, mask, w, c)
        val side = InpaintEngine.SIDE
        assertEquals(3 * side * side, image.size)
        assertEquals(128f / 255f, image[side * side / 2], 0.01f)
        assertTrue("anche i pixel quasi vuoti vanno ricostruiti", m.any { it == 1f })
        assertTrue(m.all { it == 0f || it == 1f })
    }

    @Test
    fun blendChangesOnlyTheZone() {
        val w = 300; val h = 300
        val px = IntArray(w * h) { 0xFF102030.toInt() }
        val mask = FloatArray(w * h)
        for (y in 140 until 160) for (x in 140 until 160) mask[y * w + x] = 1f
        val c = InpaintEngine.crop(mask, w, h)!!
        val plane = InpaintEngine.SIDE * InpaintEngine.SIDE
        val out = FloatArray(3 * plane) { 250f } // il "modello" restituisce bianco
        val res = InpaintEngine.blend(px, mask, w, h, c, out)
        assertEquals(0xFFFAFAFA.toInt(), res[150 * w + 150]) // centro della zona: ricostruito
        assertEquals(px[10 * w + 10], res[10 * w + 10])      // lontano: intatto
        assertEquals(px[150 * w + 100], res[150 * w + 100])  // fuori dal bordo sfumato: intatto
    }
}
