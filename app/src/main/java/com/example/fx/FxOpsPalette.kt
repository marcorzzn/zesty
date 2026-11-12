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
        "frame" to FxOp { img, a, _ -> frame(img, a) },
        "mat" to FxOp { img, a, _ -> mat(img, a) },
        "fibers" to FxOp { img, a, ctx -> fibers(img, a, ctx.seed) },
        "bands" to FxOp { img, a, _ -> bands(img, a) }
    )

    /**
     * Bande cinema: oscura sopra e sotto (o ai lati) fino al formato [ratio] (es. 2.39, 1.85, 1.37),
     * come in sala. [offset] sposta la finestra in verticale (-1..1) per scegliere cosa resta visibile.
     */
    fun bands(img: FxImage, a: FxArgs): FxImage {
        val ratio = a.num("ratio", 2.39f).coerceIn(0.3f, 5f)
        val color = a.color("color", 0xFF000000.toInt())
        val opacity = a.num("opacity", 100f) / 100f
        val offset = a.num("offset", 0f).coerceIn(-1f, 1f)
        val out = img.px.copyOf()
        val w = img.w; val h = img.h
        if (w.toFloat() / h < ratio) {
            val vis = (w / ratio).toInt().coerceIn(1, h)
            val free = h - vis
            val top = (free / 2f * (1f + offset)).toInt().coerceIn(0, free)
            for (y in 0 until h) if (y < top || y >= top + vis) for (x in 0 until w) out[y * w + x] = Px.mix(out[y * w + x], color, opacity)
        } else {
            val vis = (h * ratio).toInt().coerceIn(1, w)
            val left = (w - vis) / 2
            for (y in 0 until h) for (x in 0 until w) if (x < left || x >= left + vis) out[y * w + x] = Px.mix(out[y * w + x], color, opacity)
        }
        return FxImage(w, h, out)
    }

    /**
     * Passe-partout: la foto intera viene rimpicciolita (proporzioni invariate) dentro un margine di carta,
     * così la cornice non copre nulla. size/bottom in u; line = filo attorno alla foto; shadow = ombra interna.
     */
    fun mat(img: FxImage, a: FxArgs): FxImage {
        val m = (a.num("size", 5f) * img.unit).coerceAtLeast(0f)
        val mb = (a.num("bottom", a.num("size", 5f)) * img.unit).coerceAtLeast(0f)
        if (m <= 0f && mb <= 0f) return img
        val paper = a.color("color", 0xFFF3EAD6.toInt())
        val lineW = a.num("line", 0f) * img.unit
        val lineC = a.color("lineColor", 0xFF2A2420.toInt())
        val shadow = a.num("shadow", 0f) / 100f
        val w = img.w; val h = img.h
        val availW = w - 2f * m; val availH = h - m - mb
        if (availW < 4f || availH < 4f) return img
        val k = min(availW / w, availH / h)
        val pw = w * k; val ph = h * k
        val ox = (w - pw) / 2f; val oy = m + (availH - ph) / 2f
        val out = IntArray(img.size)
        val inv = 1f / k
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val lx = x + 0.5f - ox; val ly = y + 0.5f - oy
            if (lx >= 0f && ly >= 0f && lx < pw && ly < ph) {
                out[i] = FxSample.bilinear(img, lx * inv - 0.5f, ly * inv - 0.5f) or 0xFF000000.toInt()
            } else {
                var c = paper
                // filo e ombra interna appena fuori dalla foto
                val dx = max(max(-lx, lx - pw), 0f); val dy = max(max(-ly, ly - ph), 0f)
                val d = sqrt(dx * dx + dy * dy)
                if (lineW > 0f && d <= lineW) c = lineC
                else if (shadow > 0f) {
                    val t = (1f - (d - lineW) / (m * 0.35f).coerceAtLeast(1f)).coerceIn(0f, 1f) * shadow * 0.45f
                    if (t > 0f) c = Px.mix(c, 0xFF000000.toInt(), t)
                }
                out[i] = c
            }
        }
        return FxImage(w, h, out)
    }

    /** Fibre della carta: brevi tratti chiari e scuri, densità e tono legati all'invecchiamento. */
    fun fibers(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val amount = a.num("amount", 50f) / 100f
        val aged = a.num("aged", 40f) / 100f
        val sd = seed + a.int("seed", 0)
        val out = img.px.copyOf()
        val w = img.w; val h = img.h
        val count = (w * h / 3500f * amount).toInt()
        val dark = 0xFF6E5A3C.toInt(); val light = 0xFFFFFFFF.toInt()
        for (s in 0 until count) {
            val px0 = FxRand.r01(s, 1, sd) * w; val py0 = FxRand.r01(s, 2, sd) * h
            val ang = FxRand.r01(s, 3, sd) * 6.283f
            val len = (0.4f + FxRand.r01(s, 4, sd) * 1.1f) * img.unit
            val isDark = FxRand.r01(s, 5, sd) < 0.5f
            val col = if (isDark) dark else light
            val op = if (isDark) 0.04f + aged * 0.05f else 0.07f
            val dx = cos(ang); val dy = sin(ang)
            val steps = len.toInt().coerceAtLeast(2)
            for (k in 0 until steps) {
                val x = (px0 + dx * k).toInt(); val y = (py0 + dy * k).toInt()
                if (x in 0 until w && y in 0 until h) out[y * w + x] = Px.mix(out[y * w + x], col or (out[y * w + x] and 0xFF000000.toInt()), op)
            }
        }
        return FxImage(w, h, out)
    }

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
