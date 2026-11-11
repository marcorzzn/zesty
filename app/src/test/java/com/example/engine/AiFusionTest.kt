package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiFusionTest {

    private fun scene(w: Int, h: Int, rnd: Random, inside: (Int, Int) -> Boolean): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val (r, g, b) = if (inside(x, y)) Triple(220, 120, 60)
        else when (((x + y / 2) / 9) % 3) { 0 -> Triple(40, 90, 160); 1 -> Triple(190, 190, 180); else -> Triple(60, 130, 60) }
        val n = { v: Int -> (v + rnd.nextInt(-15, 15)).coerceIn(0, 255) }
        (0xFF shl 24) or (n(r) shl 16) or (n(g) shl 8) or n(b)
    }

    private fun acc(a: FloatArray, w: Int, h: Int, truth: (Int, Int) -> Boolean): Float {
        var ok = 0
        for (y in 0 until h) for (x in 0 until w) if ((a[y * w + x] > 0.5f) == truth(x, y)) ok++
        return ok.toFloat() / (w * h)
    }

    @Test
    fun fusionCorrectsAnImpreciseAiMask() {
        val w = 200; val h = 160
        val inside = { x: Int, y: Int -> val dx = (x - 100) / 50f; val dy = (y - 80) / 55f; dx * dx + dy * dy < 1f }
        val px = scene(w, h, Random(1), inside)
        // IA imprecisa: sagoma più larga di 5 px con bordo sfumato e un buco sbagliato dentro
        val ai = FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            val dx = (x - 100) / 55f; val dy = (y - 80) / 60f
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            val hole = (x - 110) * (x - 110) + (y - 70) * (y - 70) < 64
            if (hole) 0.3f else ((1.08f - d) / 0.16f).coerceIn(0f, 1f)
        }
        val before = acc(ai, w, h, inside)
        val out = SubjectEngine.fuse(px, w, h, ai, null, emptyList())
        val after = acc(out, w, h, inside)
        assertTrue("prima $before dopo $after", after > before + 0.01f)
        assertTrue("dopo $after", after > 0.975f)
    }

    @Test
    fun fusionKeepsRemovedAreasRemoved() {
        val w = 120; val h = 100
        val inside = { x: Int, y: Int -> x in 30..90 && y in 20..80 }
        val px = scene(w, h, Random(2), inside)
        val ai = FloatArray(w * h) { if (inside(it % w, it / w)) 1f else 0f }
        val keep = FloatArray(w * h) { if (it % w < 60) 0f else 1f }
        val out = SubjectEngine.fuse(px, w, h, ai, null, emptyList(), keep)
        assertTrue(out[50 * w + 45] < 0.05f)
        assertTrue(out[50 * w + 75] > 0.9f)
    }

    @Test
    fun portraitMatteCountsNearFaces() {
        val w = 120; val h = 160
        val px = scene(w, h, Random(3)) { _, _ -> false }
        val salient = FloatArray(w * h) // l'IA generica non vede nulla
        val portrait = FloatArray(w * h) { i -> val x = i % w; val y = i / w; if (x in 45..75 && y in 30..70) 1f else 0f }
        val out = SubjectEngine.fuse(px, w, h, salient, portrait, listOf(SubjectEngine.Face(60f, 45f, 12f)))
        assertTrue(out[50 * w + 60] > 0.5f)
        assertTrue(out[5 * w + 5] < 0.1f)
    }

    @Test
    fun prepTensorLayoutAndNormalization() {
        val px = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0x00000000)
        val t = AiPrep.toChw(px, 2, 2, 2, 2, mean = 0.5f, std = 0.5f, scale = 1f / 255f)
        assertEquals(1f, t[0], 1e-4f)       // R del rosso
        assertEquals(-1f, t[4 + 0], 1e-4f)  // G del rosso
        assertEquals(1f, t[4 + 1], 1e-4f)   // G del verde
        assertEquals(1f, t[8 + 2], 1e-4f)   // B del blu
        assertEquals(128f / 255f * 2 - 1, t[3], 1e-3f) // trasparente = grigio neutro
    }

    @Test
    fun modnetSizeIsMultipleOf32() {
        val (w, h) = AiPrep.modnetSize(4000, 3000)
        assertEquals(0, w % 32); assertEquals(0, h % 32)
        assertEquals(512, h)
        val (w2, h2) = AiPrep.modnetSize(300, 700)
        // lato corto già sotto 512 e lato lungo sopra: si tiene la misura, arrotondata a 32 (come l'originale)
        assertEquals(288, w2); assertEquals(0, h2 % 32)
    }

    @Test
    fun minMaxNormalization() {
        val a = floatArrayOf(0.2f, 0.6f, 0.4f)
        AiPrep.normalizeMinMax(a)
        assertEquals(0f, a[0], 1e-6f); assertEquals(1f, a[1], 1e-6f); assertEquals(0.5f, a[2], 1e-6f)
    }
}
