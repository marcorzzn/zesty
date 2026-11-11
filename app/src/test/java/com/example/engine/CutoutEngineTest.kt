package com.example.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CutoutEngineTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun img(w: Int, h: Int, f: (Int, Int) -> Int) = IntArray(w * h) { f(it % w, it / w) }

    @Test
    fun labOfReferenceColors() {
        val white = CutoutEngine.rgbToLab(255, 255, 255)
        assertEquals(100f, white[0], 0.5f); assertEquals(0f, white[1], 0.5f); assertEquals(0f, white[2], 0.5f)
        val black = CutoutEngine.rgbToLab(0, 0, 0)
        assertEquals(0f, black[0], 0.5f)
        // rosso sRGB puro: L≈53, a≈80, b≈67
        val red = CutoutEngine.rgbToLab(255, 0, 0)
        assertEquals(53.2f, red[0], 1f); assertEquals(80.1f, red[1], 1.5f); assertEquals(67.2f, red[2], 1.5f)
    }

    /** Due quadrati rossi separati da blu: contigua prende solo quello toccato, globale entrambi. */
    @Test
    fun wandContiguousVersusGlobal() {
        val w = 40; val h = 20
        val px = img(w, h) { x, _ -> if (x in 2..15 || x in 24..37) argb(220, 30, 30) else argb(20, 40, 200) }
        val lab = CutoutEngine.toLab(px, w, h)
        val local = CutoutEngine.wand(lab, 5, 10, 15f, 0f, contiguous = true, gradual = false)
        assertEquals(1f, local[10 * w + 5], 0f)
        assertEquals(0f, local[10 * w + 30], 0f)
        assertEquals(0f, local[10 * w + 20], 0f)
        val global = CutoutEngine.wand(lab, 5, 10, 15f, 0f, contiguous = false, gradual = false)
        assertEquals(1f, global[10 * w + 30], 0f)
        assertEquals(0f, global[10 * w + 20], 0f)
    }

    /** Una sfumatura lunga: la bacchetta classica si ferma presto, quella graduale la segue fino al bordo netto. */
    @Test
    fun gradualFollowsSmoothGradientButStopsAtEdges() {
        val w = 200; val h = 10
        val px = img(w, h) { x, _ -> if (x == 150) argb(0, 0, 0) else { val v = 60 + x * 140 / 200; argb(v, v, v) } }
        val lab = CutoutEngine.toLab(px, w, h)
        val classic = CutoutEngine.wand(lab, 2, 5, 8f, 0f, contiguous = true, gradual = false)
        val gradual = CutoutEngine.wand(lab, 2, 5, 8f, 0f, contiguous = true, gradual = true)
        val classicReach = (0 until w).count { classic[5 * w + it] >= 1f }
        val gradualReach = (0 until w).count { gradual[5 * w + it] >= 1f }
        assertTrue("classica $classicReach px", classicReach < 60)
        assertEquals(1f, gradual[5 * w + 149], 0f) // arriva fino alla linea nera
        assertEquals(0f, gradual[5 * w + 151], 0f) // e non la attraversa
        assertTrue(gradualReach > classicReach * 2)
    }

    @Test
    fun softnessGivesPartialEdge() {
        val w = 60; val h = 4
        val px = img(w, h) { x, _ -> val v = x * 4; argb(v, v, v) }
        val lab = CutoutEngine.toLab(px, w, h)
        val sel = CutoutEngine.wand(lab, 0, 1, 10f, 1f, contiguous = false, gradual = false)
        val partial = (0 until w).map { sel[w + it] }.filter { it > 0f && it < 1f }
        assertTrue("fascia morbida assente", partial.isNotEmpty())
    }

    /** Fondale bianco, anello scuro con interno bianco: senza "buchi" l'interno resta, con "buchi" va via. */
    @Test
    fun uniformBackgroundAndHoles() {
        val w = 80; val h = 80
        val px = img(w, h) { x, y ->
            val d = hypot(x - 40.0, y - 40.0)
            if (d in 15.0..28.0) argb(40, 70, 120) else argb(250, 250, 248)
        }
        val lab = CutoutEngine.toLab(px, w, h)
        val bg = CutoutEngine.uniformBackground(lab, 12f, 0.3f, holes = false)
        assertEquals(1f, bg[2 * w + 2], 0f)         // angolo: sfondo
        assertEquals(0f, bg[40 * w + 40 + 20], 0f)   // anello: soggetto
        assertEquals(0f, bg[40 * w + 40], 0f)        // interno racchiuso: resta
        val bgHoles = CutoutEngine.uniformBackground(lab, 12f, 0.3f, holes = true)
        assertEquals(1f, bgHoles[40 * w + 40], 0f)
    }

    /** Fondale con vignettatura (più scuro agli angoli): il riempimento graduale lo prende tutto. */
    @Test
    fun uniformBackgroundFollowsVignette() {
        val w = 120; val h = 120
        val px = img(w, h) { x, y ->
            val d = hypot(x - 60.0, y - 60.0)
            if (d < 20) argb(200, 40, 40) else { val v = (240 - d * 1.2).toInt().coerceIn(0, 255); argb(v, v, v) }
        }
        val lab = CutoutEngine.toLab(px, w, h)
        val bg = CutoutEngine.uniformBackground(lab, 10f, 0.2f, holes = false)
        assertEquals(1f, bg[60 * w + 60 + 30], 0f) // vicino al soggetto, molto più chiaro degli angoli
        assertEquals(0f, bg[60 * w + 60], 0f)
    }

    @Test
    fun colorKeyRemovesGreen() {
        val w = 30; val h = 10
        val px = img(w, h) { x, _ -> if (x < 15) argb(0, 200, 60) else argb(230, 180, 150) }
        val lab = CutoutEngine.toLab(px, w, h)
        val key = CutoutEngine.rgbToLab(0, 200, 60)
        val bg = CutoutEngine.colorKey(lab, key, 20f, 0.3f)
        assertEquals(1f, bg[5], 0f); assertEquals(0f, bg[25], 0f)
    }

    @Test
    fun growAndShrink() {
        val w = 11; val h = 11
        val dot = FloatArray(w * h).also { it[5 * w + 5] = 1f }
        val grown = CutoutEngine.grow(dot, w, h, 2)
        assertEquals(25, grown.count { it == 1f })
        assertEquals(1f, grown[3 * w + 3], 0f); assertEquals(0f, grown[2 * w + 5], 0f)
        val shrunk = CutoutEngine.grow(grown, w, h, -2)
        assertArrayEquals(dot, shrunk, 0f)
        // la contrazione non mangia il soggetto che tocca il bordo della foto
        val full = FloatArray(w * h) { 1f }
        assertTrue(CutoutEngine.grow(full, w, h, -3).all { it == 1f })
    }

    @Test
    fun fillHolesFillsEnclosedOnly() {
        val w = 20; val h = 20
        val ring = FloatArray(w * h) { i -> val x = i % w; val y = i / w; if (x in 4..15 && y in 4..15 && !(x in 7..12 && y in 7..12)) 1f else 0f }
        val filled = CutoutEngine.fillHoles(ring, w, h)
        assertEquals(1f, filled[10 * w + 10], 0f)
        assertEquals(0f, filled[1 * w + 1], 0f)
    }

    @Test
    fun removeIslandsDropsSpecks() {
        val w = 40; val h = 40
        val a = FloatArray(w * h) { i -> val x = i % w; val y = i / w; if ((x in 5..25 && y in 5..25) || (x in 33..34 && y in 33..34)) 1f else 0f }
        a[32 * w + 33] = 0.3f // sfumatura attorno al puntino
        val out = CutoutEngine.removeIslands(a, w, h, 0.05f)
        assertEquals(1f, out[10 * w + 10], 0f)
        assertEquals(0f, out[33 * w + 33], 0f)
        assertEquals(0f, out[32 * w + 33], 0f)
    }

    /** Il filtro guidato lascia intatte le zone piene e vuote e resta nei limiti 0..1. */
    @Test
    fun refineEdgeKeepsFlatAreas() {
        val w = 64; val h = 64
        val px = img(w, h) { x, _ -> if (x < 32) argb(30, 30, 30) else argb(230, 230, 230) }
        val alpha = FloatArray(w * h) { i -> if (i % w < 30) 1f else 0f } // maschera imprecisa di 2 px
        val out = CutoutEngine.refineEdge(px, alpha, w, h, 4)
        assertTrue(out.all { it in 0f..1f })
        assertEquals(1f, out[32 * w + 5], 0.02f)
        assertEquals(0f, out[32 * w + 60], 0.02f)
        // il bordo si sposta verso il contorno vero (x = 32)
        assertTrue("bordo non spostato: ${out[32 * w + 31]}", out[32 * w + 31] > 0.3f)
    }

    @Test
    fun decontaminateMovesEdgeColorTowardSubject() {
        val w = 20; val h = 5
        val px = img(w, h) { x, _ -> if (x < 10) argb(200, 30, 30) else if (x == 10) argb(120, 120, 120) else argb(20, 200, 20) }
        val alpha = FloatArray(w * h) { i -> val x = i % w; if (x < 10) 1f else if (x == 10) 0.5f else 0f }
        val out = CutoutEngine.decontaminate(px, alpha, w, h, 3)
        val c = out[2 * w + 10]
        assertTrue(((c shr 16) and 255) > 120); assertTrue(((c shr 8) and 255) < 120)
        assertEquals(px[2 * w + 3], out[2 * w + 3]) // il soggetto pieno non cambia
    }

    @Test
    fun boundsAndCombine() {
        val w = 10; val h = 10
        val a = FloatArray(w * h).also { it[3 * w + 2] = 1f; it[6 * w + 7] = 0.5f }
        assertArrayEquals(intArrayOf(2, 3, 8, 7), CutoutEngine.bounds(a, w, h))
        assertNull(CutoutEngine.bounds(FloatArray(4), 2, 2))
        val m = floatArrayOf(1f, 1f, 0f); val sel = floatArrayOf(1f, 0.5f, 1f)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 0f), CutoutEngine.combine(m, sel, true), 1e-6f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), CutoutEngine.combine(m, sel, false), 1e-6f)
    }
}
