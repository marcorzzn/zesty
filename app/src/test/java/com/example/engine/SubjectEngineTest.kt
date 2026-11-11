package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SubjectEngineTest {

    @Test
    fun maxflow_simpleChain() {
        // S -> 0 (5), 0 -> 1 (3), 1 -> T (4): flusso massimo 3, il taglio è sul lato 0-1
        val g = SubjectEngine.MaxFlow(2, 1)
        g.setTerminal(0, 5f, 0f)
        g.setTerminal(1, 0f, 4f)
        g.addEdge(0, 1, 3f, 0f)
        assertEquals(3.0, g.maxflow(), 1e-6)
        assertTrue(g.isSource(0))
        assertFalse(g.isSource(1))
    }

    @Test
    fun maxflow_classicGraph() {
        // grafo con quattro nodi interni e flusso massimo noto (23)
        val g = SubjectEngine.MaxFlow(4, 5)
        g.setTerminal(0, 16f, 0f)
        g.setTerminal(1, 13f, 0f)
        g.setTerminal(2, 0f, 20f)
        g.setTerminal(3, 0f, 4f)
        g.addEdge(0, 1, 10f, 4f)
        g.addEdge(0, 2, 12f, 0f)
        g.addEdge(1, 3, 14f, 0f)
        g.addEdge(2, 1, 9f, 0f)
        g.addEdge(3, 2, 7f, 0f)
        assertEquals(23.0, g.maxflow(), 1e-4)
    }

    @Test
    fun maxflow_randomGridMatchesCutValue() {
        // il flusso deve uguagliare il costo del taglio trovato (dualità max-flow / min-cut)
        val rnd = Random(7)
        val w = 12; val h = 9; val n = w * h
        val src = FloatArray(n) { rnd.nextFloat() * 10f }
        val snk = FloatArray(n) { rnd.nextFloat() * 10f }
        val edges = ArrayList<Triple<Int, Int, Float>>()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (x + 1 < w) edges += Triple(i, i + 1, rnd.nextFloat() * 6f)
            if (y + 1 < h) edges += Triple(i, i + w, rnd.nextFloat() * 6f)
        }
        val g = SubjectEngine.MaxFlow(n, edges.size)
        for (i in 0 until n) g.setTerminal(i, src[i], snk[i])
        for ((a, b, c) in edges) g.addEdge(a, b, c)
        val flow = g.maxflow()
        var cut = 0.0
        for (i in 0 until n) cut += if (g.isSource(i)) snk[i] else src[i]
        for ((a, b, c) in edges) if (g.isSource(a) != g.isSource(b)) cut += c
        assertEquals(cut, flow, 1e-3)
    }

    /** Sfondo "generico": righe colorate con rumore, nessun colore dominante uniforme. */
    private fun clutteredBackground(w: Int, h: Int, rnd: Random): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val band = ((x + y / 2) / 7) % 4
        val base = when (band) { 0 -> intArrayOf(40, 90, 160); 1 -> intArrayOf(200, 200, 190); 2 -> intArrayOf(60, 130, 60); else -> intArrayOf(120, 70, 40) }
        val r = (base[0] + rnd.nextInt(-25, 25)).coerceIn(0, 255)
        val g = (base[1] + rnd.nextInt(-25, 25)).coerceIn(0, 255)
        val b = (base[2] + rnd.nextInt(-25, 25)).coerceIn(0, 255)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun accuracy(alpha: FloatArray, truth: (Int, Int) -> Boolean, w: Int, h: Int): Float {
        var ok = 0
        for (y in 0 until h) for (x in 0 until w) if ((alpha[y * w + x] > 0.5f) == truth(x, y)) ok++
        return ok.toFloat() / (w * h)
    }

    @Test
    fun subject_centralObjectOnClutteredBackground() {
        val w = 160; val h = 120
        val rnd = Random(3)
        val px = clutteredBackground(w, h, rnd)
        val inside = { x: Int, y: Int -> val dx = (x - 80) / 34f; val dy = (y - 62) / 40f; dx * dx + dy * dy < 1f }
        for (y in 0 until h) for (x in 0 until w) if (inside(x, y)) {
            // soggetto con due toni (come pelle e maglia) e un po' di rumore
            val top = y < 62
            val r = (if (top) 225 else 210) + rnd.nextInt(-12, 12)
            val g = (if (top) 160 else 40) + rnd.nextInt(-12, 12)
            val b = (if (top) 120 else 70) + rnd.nextInt(-12, 12)
            px[y * w + x] = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        val a = SubjectEngine.subject(px, w, h, emptyList())
        val acc = accuracy(a, inside, w, h)
        assertTrue("accuratezza $acc", acc > 0.93f)
    }

    @Test
    fun subject_personFromFaceReachesBottomEdge() {
        // "persona": testa + busto che arriva al bordo inferiore, su sfondo a righe
        val w = 150; val h = 200
        val rnd = Random(11)
        val px = clutteredBackground(w, h, rnd)
        val fx = 75f; val fy = 60f; val d = 16f
        val head = { x: Int, y: Int -> val dx = (x - fx) / (1.2f * d); val dy = (y - (fy + 4f)) / (1.6f * d); dx * dx + dy * dy < 1f }
        val body = { x: Int, y: Int -> y > fy + 1.5f * d && kotlin.math.abs(x - fx) < d * (1f + 2.2f * ((y - fy - 1.5f * d) / (3f * d)).coerceIn(0f, 1f)) }
        val person = { x: Int, y: Int -> head(x, y) || body(x, y) }
        for (y in 0 until h) for (x in 0 until w) {
            if (head(x, y)) px[y * w + x] = (0xFF shl 24) or ((228 + rnd.nextInt(-10, 10)) shl 16) or ((175 + rnd.nextInt(-10, 10)) shl 8) or (140 + rnd.nextInt(-10, 10))
            else if (body(x, y)) px[y * w + x] = (0xFF shl 24) or ((150 + rnd.nextInt(-10, 10)) shl 16) or ((30 + rnd.nextInt(-10, 10)) shl 8) or (50 + rnd.nextInt(-10, 10))
        }
        val a = SubjectEngine.subject(px, w, h, listOf(SubjectEngine.Face(fx, fy, d)))
        val acc = accuracy(a, person, w, h)
        assertTrue("accuratezza $acc", acc > 0.93f)
        assertTrue("il busto tocca il bordo in basso", a[(h - 2) * w + 75] > 0.5f)
        assertTrue("angolo in alto è sfondo", a[3 * w + 3] < 0.5f)
    }

    @Test
    fun subject_respectsAlreadyRemovedBackground() {
        val w = 120; val h = 90
        val px = clutteredBackground(w, h, Random(5))
        // nessun soggetto distinguibile: ciò che l'utente ha già tolto resta tolto
        val keep = FloatArray(w * h) { if (it % w < 60) 0f else 1f }
        val a = SubjectEngine.subject(px, w, h, emptyList(), keep)
        for (y in 0 until h) for (x in 0 until 55) assertTrue(a[y * w + x] < 0.5f)
    }

    @Test
    fun upscaleKeepsCorners() {
        val a = floatArrayOf(1f, 0f, 0f, 1f)
        val up = SubjectEngine.upscale(a, 2, 2, 8, 8)
        assertEquals(1f, up[0], 1e-4f)
        assertEquals(0f, up[7], 1e-4f)
        assertEquals(1f, up[63], 1e-4f)
    }
}
