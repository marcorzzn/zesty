package com.example.fx

import kotlin.math.*

/**
 * Operazioni di palette e cornice (effetto Poster e simili).
 *
 * Esempio della struttura per un effetto "su misura":
 *   1. un'operazione pura FxImage -> FxImage, con argomenti letti da FxArgs e unità "u" per le distanze;
 *   2. registrata nella mappa `ops` (qui) e quindi in FxEngine;
 *   3. una ricetta JSON nel catalogo che la combina con altre operazioni e ne espone i parametri.
 * La stessa operazione va scritta in fx-engine.js con lo stesso nome e gli stessi argomenti.
 */
object FxOpsPalette {

    val ops: Map<String, FxOp> = mapOf(
        "palette_quantize" to FxOp { img, a, _ -> paletteQuantize(img, a) },
        "frame" to FxOp { img, a, _ -> frame(img, a) }
    )

    private fun dist2(r1: Float, g1: Float, b1: Float, r2: Float, g2: Float, b2: Float): Float {
        val dr = r1 - r2; val dg = g1 - g2; val db = b1 - b2
        return dr * dr + dg * dg + db * db
    }

    /**
     * Riduce l'immagine a pochi colori scelti dall'immagine stessa (k-means con inizializzazione
     * deterministica "punto più lontano", così l'anteprima e l'esportazione coincidono).
     * colors: 2..16 · tone: 0..100 quanto dell'originale resta (sfumature) · aged: 0..100 palette smorzata
     */
    fun paletteQuantize(img: FxImage, a: FxArgs): FxImage {
        val k = a.int("colors", 5).coerceIn(2, 16)
        val tone = a.num("tone", 15f) / 100f
        val aged = a.num("aged", 0f) / 100f
        val n = img.size
        val step = max(1, n / 8000)
        val sr = ArrayList<Float>(); val sg = ArrayList<Float>(); val sb = ArrayList<Float>()
        var i = 0
        while (i < n) {
            val c = img.px[i]
            if ((c ushr 24) > 16) { sr += Px.r(c).toFloat(); sg += Px.g(c).toFloat(); sb += Px.b(c).toFloat() }
            i += step
        }
        if (sr.isEmpty()) return img
        val m = sr.size
        val cr = FloatArray(k); val cg = FloatArray(k); val cb = FloatArray(k)
        // inizializzazione: campione centrale, poi ogni volta il campione più lontano dai centri già scelti
        cr[0] = sr[m / 2]; cg[0] = sg[m / 2]; cb[0] = sb[m / 2]
        val near = FloatArray(m) { dist2(sr[it], sg[it], sb[it], cr[0], cg[0], cb[0]) }
        for (c in 1 until k) {
            var bi = 0; var bd = -1f
            for (s in 0 until m) if (near[s] > bd) { bd = near[s]; bi = s }
            cr[c] = sr[bi]; cg[c] = sg[bi]; cb[c] = sb[bi]
            for (s in 0 until m) near[s] = min(near[s], dist2(sr[s], sg[s], sb[s], cr[c], cg[c], cb[c]))
        }
        val assign = IntArray(m)
        repeat(10) {
            val ar = FloatArray(k); val ag = FloatArray(k); val ab = FloatArray(k); val cnt = IntArray(k)
            for (s in 0 until m) {
                var bi = 0; var bd = Float.MAX_VALUE
                for (c in 0 until k) { val d = dist2(sr[s], sg[s], sb[s], cr[c], cg[c], cb[c]); if (d < bd) { bd = d; bi = c } }
                assign[s] = bi; ar[bi] += sr[s]; ag[bi] += sg[s]; ab[bi] += sb[s]; cnt[bi]++
            }
            for (c in 0 until k) if (cnt[c] > 0) { cr[c] = ar[c] / cnt[c]; cg[c] = ag[c] / cnt[c]; cb[c] = ab[c] / cnt[c] }
        }
        // palette "da stampa": un po' meno satura e meno contrastata quanto più è invecchiata
        for (c in 0 until k) {
            val l = Px.lumaRGB(cr[c], cg[c], cb[c])
            val desat = 0.12f * (0.5f + aged)
            cr[c] = (cr[c] + (l - cr[c]) * desat) * (1f - 0.07f * aged) + 13f * aged
            cg[c] = (cg[c] + (l - cg[c]) * desat) * (1f - 0.07f * aged) + 13f * aged
            cb[c] = (cb[c] + (l - cb[c]) * desat) * (1f - 0.07f * aged) + 13f * aged
        }
        val out = IntArray(n)
        for (p in 0 until n) {
            val c = img.px[p]
            val r = Px.r(c).toFloat(); val g = Px.g(c).toFloat(); val b = Px.b(c).toFloat()
            var bi = 0; var bd = Float.MAX_VALUE
            for (q in 0 until k) { val d = dist2(r, g, b, cr[q], cg[q], cb[q]); if (d < bd) { bd = d; bi = q } }
            out[p] = (c and 0xFF000000.toInt()) or (Px.argbF(0, cr[bi] + (r - cr[bi]) * tone, cg[bi] + (g - cg[bi]) * tone, cb[bi] + (b - cb[bi]) * tone) and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    /** Cornice interna (margine) del colore indicato; size in u. Non cambia le dimensioni dell'immagine. */
    fun frame(img: FxImage, a: FxArgs): FxImage {
        val m = (a.num("size", 5f) * img.unit).roundToInt()
        if (m <= 0) return img
        val color = a.color("color", 0xFFF3EAD6.toInt())
        val out = img.px.copyOf()
        for (y in 0 until img.h) for (x in 0 until img.w) {
            if (x < m || y < m || x >= img.w - m || y >= img.h - m) out[y * img.w + x] = color
        }
        return FxImage(img.w, img.h, out)
    }
}
