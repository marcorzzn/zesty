package com.example.engine

import com.example.fx.FxBlur
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Motore di scontorno di Zesty (Scontorna). Lavora su array di pixel ARGB e su maschere alpha 0..1
 * della stessa dimensione: niente dipendenze Android, quindi è verificabile con test JVM.
 *
 * Differenze rispetto a una bacchetta magica classica (confronto RGB, bordo a scalini):
 * - distanza percettiva CIELAB (ΔE): la tolleranza corrisponde a ciò che l'occhio vede come "simile";
 * - modalità graduale: segue sfumature morbide (cielo, fondali illuminati) confrontando ogni pixel
 *   anche con il vicino già selezionato, senza superare i contorni netti;
 * - bordo morbido: i pixel al limite della tolleranza ricevono un'opacità parziale;
 * - rifinitura con filtro guidato (He et al.), che fa aderire la maschera a capelli e contorni sottili.
 */
object CutoutEngine {

    /** Immagine convertita in CIELAB (D65), con l'opacità originale in scala 0..100. */
    class Lab(val w: Int, val h: Int, val l: FloatArray, val a: FloatArray, val b: FloatArray, val alpha: FloatArray) {
        val size get() = w * h
        fun dist(i: Int, cl: Float, ca: Float, cb: Float, cAlpha: Float): Float {
            val dl = l[i] - cl; val da = a[i] - ca; val db = b[i] - cb; val dA = alpha[i] - cAlpha
            return sqrt(dl * dl + da * da + db * db + dA * dA)
        }
        fun dist(i: Int, j: Int): Float = dist(i, l[j], a[j], b[j], alpha[j])
    }

    private val SRGB_TO_LINEAR = FloatArray(256) { v ->
        val c = v / 255f
        (if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat())
    }

    private fun f(t: Float): Float = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f

    fun rgbToLab(r: Int, g: Int, b: Int): FloatArray {
        val lr = SRGB_TO_LINEAR[r]; val lg = SRGB_TO_LINEAR[g]; val lb = SRGB_TO_LINEAR[b]
        val x = (0.4124f * lr + 0.3576f * lg + 0.1805f * lb) / 0.95047f
        val y = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb
        val z = (0.0193f * lr + 0.1192f * lg + 0.9505f * lb) / 1.08883f
        val fx = f(x); val fy = f(y); val fz = f(z)
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    fun toLab(px: IntArray, w: Int, h: Int): Lab {
        val n = w * h
        val l = FloatArray(n); val a = FloatArray(n); val b = FloatArray(n); val al = FloatArray(n)
        // le foto ripetono molti colori: piccola cache per non ricalcolare le radici cubiche
        val cacheKey = IntArray(4096) { -1 }; val cacheVal = FloatArray(4096 * 3)
        for (i in 0 until n) {
            val c = px[i]
            val rgb = c and 0xFFFFFF
            val slot = ((rgb * -0x61c88647) ushr 20)
            if (cacheKey[slot] != rgb) {
                val lab = rgbToLab((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
                cacheKey[slot] = rgb
                cacheVal[slot * 3] = lab[0]; cacheVal[slot * 3 + 1] = lab[1]; cacheVal[slot * 3 + 2] = lab[2]
            }
            l[i] = cacheVal[slot * 3]; a[i] = cacheVal[slot * 3 + 1]; b[i] = cacheVal[slot * 3 + 2]
            al[i] = ((c ushr 24) and 255) * (100f / 255f)
        }
        return Lab(w, h, l, a, b, al)
    }

    /** Colore medio (3×3) attorno al punto: un singolo pixel rumoroso non falsa la selezione. */
    fun seedColor(lab: Lab, x: Int, y: Int, radius: Int = 1): FloatArray {
        var sl = 0f; var sa = 0f; var sb = 0f; var sal = 0f; var n = 0
        for (dy in -radius..radius) for (dx in -radius..radius) {
            val xx = x + dx; val yy = y + dy
            if (xx < 0 || yy < 0 || xx >= lab.w || yy >= lab.h) continue
            val i = yy * lab.w + xx
            sl += lab.l[i]; sa += lab.a[i]; sb += lab.b[i]; sal += lab.alpha[i]; n++
        }
        if (n == 0) return floatArrayOf(0f, 0f, 0f, 100f)
        return floatArrayOf(sl / n, sa / n, sb / n, sal / n)
    }

    /** Opacità da una distanza: 1 entro la tolleranza, poi cala fino a 0 nella fascia morbida. */
    private fun ramp(d: Float, tol: Float, outer: Float): Float = when {
        d <= tol -> 1f
        d >= outer -> 0f
        else -> (outer - d) / (outer - tol)
    }

    /**
     * Bacchetta: seleziona i pixel simili al punto toccato.
     * @param tolerance ΔE (circa 2 = appena percepibile, 20 = simile, 50 = molto largo)
     * @param softness 0..1, larghezza della fascia morbida rispetto alla tolleranza
     * @param contiguous true = solo l'area collegata al punto, false = in tutta la foto
     * @param gradual true = segue le sfumature confrontando anche i pixel vicini
     */
    fun wand(lab: Lab, x: Int, y: Int, tolerance: Float, softness: Float, contiguous: Boolean, gradual: Boolean): FloatArray {
        val w = lab.w; val h = lab.h
        val out = FloatArray(lab.size)
        if (x !in 0 until w || y !in 0 until h) return out
        val seed = seedColor(lab, x, y)
        val tol = tolerance.coerceAtLeast(0.5f)
        val outer = tol * (1f + softness.coerceIn(0f, 2f))
        if (!contiguous) {
            for (i in 0 until lab.size) out[i] = ramp(lab.dist(i, seed[0], seed[1], seed[2], seed[3]), tol, outer)
            return out
        }
        floodFrom(lab, intArrayOf(y * w + x), out, tol, outer, gradual) { i -> lab.dist(i, seed[0], seed[1], seed[2], seed[3]) }
        return out
    }

    /**
     * Riempimento a 4 vicini: si propaga solo dai pixel pienamente selezionati; quelli nella fascia
     * morbida ricevono un'opacità parziale ma non fanno da ponte (niente fughe attraverso i bordi).
     * In modalità graduale la distanza efficace è min(dSeme, max(4·dVicino, dSeme/8)).
     */
    private inline fun floodFrom(lab: Lab, starts: IntArray, out: FloatArray, tol: Float, outer: Float, gradual: Boolean, seedDist: (Int) -> Float) {
        val w = lab.w; val h = lab.h; val n = lab.size
        val queue = IntArray(n)
        var head = 0; var tail = 0
        val core = BooleanArray(n)
        for (s in starts) {
            if (core[s]) continue
            val v = ramp(seedDist(s), tol, outer)
            if (v >= 1f) { core[s] = true; out[s] = 1f; queue[tail++] = s }
            else if (v > out[s]) out[s] = v
        }
        while (head < tail) {
            val c = queue[head++]
            val cx = c % w; val cy = c / w
            for (k in 0 until 4) {
                val nx = when (k) { 0 -> cx - 1; 1 -> cx + 1; else -> cx }
                val ny = when (k) { 2 -> cy - 1; 3 -> cy + 1; else -> cy }
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val ni = ny * w + nx
                if (core[ni]) continue
                val ds = seedDist(ni)
                val d = if (gradual) min(ds, max(4f * lab.dist(ni, c), ds / 8f)) else ds
                val v = ramp(d, tol, outer)
                if (v >= 1f) { core[ni] = true; out[ni] = 1f; queue[tail++] = ni }
                else if (v > out[ni]) out[ni] = v
            }
        }
    }

    /** Colori dominanti del bordo della foto (k-means su campioni del perimetro). */
    fun borderColors(lab: Lab, k: Int = 3): List<FloatArray> {
        val w = lab.w; val h = lab.h
        val idx = ArrayList<Int>()
        val step = max(1, (2 * (w + h)) / 1600)
        for (x in 0 until w step step) { idx += x; idx += (h - 1) * w + x }
        for (y in 0 until h step step) { idx += y * w; idx += y * w + w - 1 }
        if (idx.isEmpty()) return emptyList()
        // inizializzazione deterministica: punto più lontano dai centri già scelti
        val centers = ArrayList<FloatArray>()
        centers += floatArrayOf(lab.l[idx[0]], lab.a[idx[0]], lab.b[idx[0]], lab.alpha[idx[0]])
        while (centers.size < k) {
            var best = -1; var bestD = -1f
            for (i in idx) {
                val d = centers.minOf { c -> lab.dist(i, c[0], c[1], c[2], c[3]) }
                if (d > bestD) { bestD = d; best = i }
            }
            if (bestD < 6f) break // il bordo è già uniforme
            centers += floatArrayOf(lab.l[best], lab.a[best], lab.b[best], lab.alpha[best])
        }
        val counts = IntArray(centers.size)
        repeat(8) {
            val sum = Array(centers.size) { FloatArray(4) }
            counts.fill(0)
            for (i in idx) {
                var bc = 0; var bd = Float.MAX_VALUE
                centers.forEachIndexed { ci, c -> val d = lab.dist(i, c[0], c[1], c[2], c[3]); if (d < bd) { bd = d; bc = ci } }
                counts[bc]++
                sum[bc][0] += lab.l[i]; sum[bc][1] += lab.a[i]; sum[bc][2] += lab.b[i]; sum[bc][3] += lab.alpha[i]
            }
            for (ci in centers.indices) if (counts[ci] > 0) for (j in 0 until 4) centers[ci][j] = sum[ci][j] / counts[ci]
        }
        // si tengono solo i colori che occupano una parte significativa del bordo
        return centers.filterIndexed { ci, _ -> counts[ci] >= idx.size * 0.12f }
    }

    /**
     * Sfondo uniforme: parte da tutti i pixel del bordo simili ai colori dominanti del perimetro e
     * si espande verso l'interno (graduale: segue vignettature e luci morbide sul fondale).
     * @param holes true = toglie anche le zone dello stesso colore racchiuse dal soggetto
     * @return opacità dello SFONDO (1 = sfondo da togliere)
     */
    fun uniformBackground(lab: Lab, tolerance: Float, softness: Float, holes: Boolean): FloatArray {
        val w = lab.w; val h = lab.h
        val out = FloatArray(lab.size)
        val colors = borderColors(lab)
        if (colors.isEmpty()) return out
        val tol = tolerance.coerceAtLeast(0.5f)
        val outer = tol * (1f + softness.coerceIn(0f, 2f))
        val bgDist = { i: Int -> colors.minOf { c -> lab.dist(i, c[0], c[1], c[2], c[3]) } }
        if (holes) {
            for (i in 0 until lab.size) out[i] = ramp(bgDist(i), tol, outer)
            return out
        }
        val starts = ArrayList<Int>()
        for (x in 0 until w) { starts += x; starts += (h - 1) * w + x }
        for (y in 1 until h - 1) { starts += y * w; starts += y * w + w - 1 }
        floodFrom(lab, starts.toIntArray(), out, tol, outer, gradual = true) { i -> bgDist(i) }
        return out
    }

    /**
     * Chiave colore (nero, bianco, verde, blu o colore scelto) su tutta la foto.
     * @return opacità dello SFONDO
     */
    fun colorKey(lab: Lab, keyLab: FloatArray, tolerance: Float, softness: Float): FloatArray {
        val tol = tolerance.coerceAtLeast(0.5f)
        val outer = tol * (1f + softness.coerceIn(0f, 2f))
        val out = FloatArray(lab.size)
        for (i in 0 until lab.size) out[i] = ramp(lab.dist(i, keyLab[0], keyLab[1], keyLab[2], 100f), tol, outer)
        return out
    }

    // ------------------------------------------------------------------ rifinitura

    /**
     * Rifinisci bordo: filtro guidato (He, Sun, Tang) con la luminosità della foto come guida.
     * La maschera si adatta ai contorni reali: ciocche di capelli, pelo, foglie.
     * @param radius raggio in pixel; @param eps regolarizzazione (più alto = più morbido)
     */
    fun refineEdge(px: IntArray, alpha: FloatArray, w: Int, h: Int, radius: Int, eps: Float = 1e-3f): FloatArray {
        if (radius < 1) return alpha.copyOf()
        val n = w * h
        val g = FloatArray(n)
        for (i in 0 until n) {
            val c = px[i]
            g[i] = (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f
        }
        val ip = FloatArray(n); val ii = FloatArray(n)
        for (i in 0 until n) { ip[i] = g[i] * alpha[i]; ii[i] = g[i] * g[i] }
        val mI = FxBlur.boxF(g, w, h, radius)
        val mP = FxBlur.boxF(alpha, w, h, radius)
        val mIP = FxBlur.boxF(ip, w, h, radius)
        val mII = FxBlur.boxF(ii, w, h, radius)
        val ca = FloatArray(n); val cb = FloatArray(n)
        for (i in 0 until n) {
            val varI = mII[i] - mI[i] * mI[i]
            val cov = mIP[i] - mI[i] * mP[i]
            val a = cov / (varI + eps)
            ca[i] = a; cb[i] = mP[i] - a * mI[i]
        }
        val mA = FxBlur.boxF(ca, w, h, radius)
        val mB = FxBlur.boxF(cb, w, h, radius)
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = (mA[i] * g[i] + mB[i]).coerceIn(0f, 1f)
        return out
    }

    /**
     * Espandi (r > 0) o contrai (r < 0) la maschera di r pixel: massimo/minimo su finestra
     * con l'algoritmo di van Herk / Gil-Werman (costo indipendente dal raggio).
     */
    fun grow(alpha: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r == 0) return alpha.copyOf()
        val rad = abs(r); val isMax = r > 0
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        val line = FloatArray(max(w, h)); val res = FloatArray(max(w, h))
        for (y in 0 until h) {
            for (x in 0 until w) line[x] = alpha[y * w + x]
            slide(line, w, rad, isMax, res)
            for (x in 0 until w) tmp[y * w + x] = res[x]
        }
        for (x in 0 until w) {
            for (y in 0 until h) line[y] = tmp[y * w + x]
            slide(line, h, rad, isMax, res)
            for (y in 0 until h) out[y * w + x] = res[y]
        }
        return out
    }

    private fun slide(src: FloatArray, n: Int, r: Int, isMax: Boolean, dst: FloatArray) {
        val win = 2 * r + 1
        val pad = n + 2 * r
        val ext = FloatArray(pad)
        val fill = if (isMax) 0f else 1f // fuori dall'immagine: non selezionato per l'espansione, selezionato per la contrazione
        for (i in 0 until pad) { val j = i - r; ext[i] = if (j in 0 until n) src[j] else fill }
        val g = FloatArray(pad); val hh = FloatArray(pad)
        fun op(a: Float, b: Float) = if (isMax) max(a, b) else min(a, b)
        var i = 0
        while (i < pad) {
            val end = min(i + win, pad)
            g[i] = ext[i]
            for (k in i + 1 until end) g[k] = op(g[k - 1], ext[k])
            hh[end - 1] = ext[end - 1]
            for (k in end - 2 downTo i) hh[k] = op(hh[k + 1], ext[k])
            i = end
        }
        for (x in 0 until n) {
            // finestra [x, x + 2r] nello spazio esteso
            dst[x] = op(hh[x], g[x + win - 1])
        }
    }

    /** Riempie i buchi: le zone trasparenti non collegate al bordo della foto diventano piene. */
    fun fillHoles(alpha: FloatArray, w: Int, h: Int, threshold: Float = 0.5f): FloatArray {
        val n = w * h
        val reach = BooleanArray(n)
        val queue = IntArray(n); var head = 0; var tail = 0
        fun push(i: Int) { if (!reach[i] && alpha[i] < threshold) { reach[i] = true; queue[tail++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (head < tail) {
            val c = queue[head++]; val cx = c % w; val cy = c / w
            if (cx > 0) push(c - 1); if (cx < w - 1) push(c + 1)
            if (cy > 0) push(c - w); if (cy < h - 1) push(c + w)
        }
        val out = alpha.copyOf()
        for (i in 0 until n) if (alpha[i] < threshold && !reach[i]) out[i] = 1f
        return out
    }

    /**
     * Togli isolotti: elimina le parti piene staccate dal soggetto più piccole di [minFraction]
     * del pezzo più grande (residui di sfondo, puntini). Con minFraction = 1 resta solo il pezzo più grande.
     */
    fun removeIslands(alpha: FloatArray, w: Int, h: Int, minFraction: Float, threshold: Float = 0.5f): FloatArray {
        val n = w * h
        val label = IntArray(n) { -1 }
        val sizes = ArrayList<Int>()
        val queue = IntArray(n)
        for (s in 0 until n) {
            if (label[s] != -1 || alpha[s] < threshold) continue
            val id = sizes.size
            var head = 0; var tail = 0
            label[s] = id; queue[tail++] = s
            while (head < tail) {
                val c = queue[head++]; val cx = c % w; val cy = c / w
                for (k in 0 until 4) {
                    val ni = when (k) { 0 -> if (cx > 0) c - 1 else -1; 1 -> if (cx < w - 1) c + 1 else -1; 2 -> if (cy > 0) c - w else -1; else -> if (cy < h - 1) c + w else -1 }
                    if (ni >= 0 && label[ni] == -1 && alpha[ni] >= threshold) { label[ni] = id; queue[tail++] = ni }
                }
            }
            sizes += tail
        }
        if (sizes.size <= 1) return alpha.copyOf()
        val largest = sizes.max()
        val keepMin = if (minFraction >= 1f) largest else max(1, (largest * minFraction).toInt())
        val out = alpha.copyOf()
        for (i in 0 until n) { val id = label[i]; if (id >= 0 && sizes[id] < keepMin) out[i] = 0f }
        // le sfumature attorno ai pezzi tolti non devono restare come aloni (raggio 2 attorno ai soli pezzi tolti)
        for (i in 0 until n) {
            val id = label[i]
            if (id < 0 || sizes[id] >= keepMin) continue
            val cx = i % w; val cy = i / w
            for (yy in max(0, cy - 2)..min(h - 1, cy + 2)) for (xx in max(0, cx - 2)..min(w - 1, cx + 2)) {
                val j = yy * w + xx
                if (out[j] < threshold) out[j] = 0f
            }
        }
        return out
    }

    /**
     * Togli alone: nei pixel semitrasparenti del bordo il colore contiene ancora lo sfondo.
     * Si stima il colore del soggetto dalla media dei pixel vicini ben coperti e lo si sostituisce
     * in proporzione alla trasparenza.
     */
    fun decontaminate(px: IntArray, alpha: FloatArray, w: Int, h: Int, radius: Int): IntArray {
        val n = w * h
        val wt = FloatArray(n); val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        for (i in 0 until n) {
            val a = alpha[i]; val ww = a * a * a * a
            val c = px[i]
            wt[i] = ww; r[i] = ((c shr 16) and 255) * ww; g[i] = ((c shr 8) and 255) * ww; b[i] = (c and 255) * ww
        }
        val mw = FxBlur.boxF(wt, w, h, radius); val mr = FxBlur.boxF(r, w, h, radius)
        val mg = FxBlur.boxF(g, w, h, radius); val mb = FxBlur.boxF(b, w, h, radius)
        val out = px.copyOf()
        for (i in 0 until n) {
            val a = alpha[i]
            if (a >= 0.98f || a <= 0.02f || mw[i] < 1e-4f) continue
            val c = px[i]
            val k = a // con alpha alto si tiene il colore originale
            val fr = mr[i] / mw[i]; val fg = mg[i] / mw[i]; val fb = mb[i] / mw[i]
            val nr = (((c shr 16) and 255) * k + fr * (1f - k)).toInt().coerceIn(0, 255)
            val ng = (((c shr 8) and 255) * k + fg * (1f - k)).toInt().coerceIn(0, 255)
            val nb = ((c and 255) * k + fb * (1f - k)).toInt().coerceIn(0, 255)
            out[i] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
        return out
    }

    /** Riquadro dei pixel con opacità sopra la soglia: [left, top, right, bottom) oppure null se vuoto. */
    fun bounds(alpha: FloatArray, w: Int, h: Int, threshold: Float = 0.02f): IntArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if (alpha[y * w + x] > threshold) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        return if (r < 0) null else intArrayOf(l, t, r + 1, b + 1)
    }

    // ------------------------------------------------------------------ combinazione con la maschera
    /** ＋ aggiunge la selezione alla parte visibile, − la toglie. */
    fun combine(mask: FloatArray, sel: FloatArray, subtract: Boolean): FloatArray =
        FloatArray(mask.size) { i -> if (subtract) mask[i] * (1f - sel[i]) else max(mask[i], sel[i]) }
}
