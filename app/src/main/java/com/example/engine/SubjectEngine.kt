package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Soggetto automatico di Zesty: separa il soggetto da uno sfondo qualsiasi (non solo uniforme).
 *
 * Metodo (scritto per Zesty, ispirato all'idea di GrabCut di Rother, Kolmogorov e Blake):
 * 1. una mappa "dove probabilmente sta il soggetto": dai volti trovati (testa + busto) oppure,
 *    senza volti, un'ellisse centrale; i bordi della foto non toccati dal soggetto sono sfondo certo;
 * 2. due modelli di colore (misture di gaussiane): uno per il soggetto e uno per lo sfondo;
 * 3. taglio minimo su grafo (max-flow di Boykov–Kolmogorov): ogni pixel sceglie soggetto o sfondo
 *    bilanciando colore, posizione e continuità con i vicini (i tagli seguono i contorni netti);
 * 4. si ripetono 2 e 3 alcune volte, poi il bordo si rifinisce a piena risoluzione con il filtro guidato.
 *
 * Lavora su una copia ridotta (lato massimo [WORK_SIDE]) per restare veloce sul telefono.
 * Nessuna dipendenza Android: verificabile con test JVM.
 */
object SubjectEngine {

    /** Volto trovato nella foto: punto tra gli occhi e distanza tra gli occhi, in pixel della foto. */
    data class Face(val cx: Float, val cy: Float, val eyeDist: Float)

    const val WORK_SIDE = 360

    private const val BG_SURE: Byte = 0
    private const val BG_PROB: Byte = 1
    private const val FG_PROB: Byte = 2
    private const val FG_SURE: Byte = 3

    /**
     * @param px pixel ARGB della foto (w×h)
     * @param keep maschera attuale (1 = visibile); dove è già 0 è sfondo certo (es. tolto con la bacchetta)
     * @return opacità del soggetto 0..1, w×h
     */
    fun subject(px: IntArray, w: Int, h: Int, faces: List<Face>, keep: FloatArray? = null, iterations: Int = 4): FloatArray {
        val scale = min(1f, WORK_SIDE.toFloat() / max(w, h))
        val sw = max(8, (w * scale).roundToInt()); val sh = max(8, (h * scale).roundToInt())
        val small = downscale(px, w, h, sw, sh)
        val keepSmall = keep?.let { downscaleAlpha(it, w, h, sw, sh) }
        val fx = sw.toFloat() / w; val fy = sh.toFloat() / h
        val smallFaces = faces.map { Face(it.cx * fx, it.cy * fy, it.eyeDist * (fx + fy) / 2f) }
        val labels = segment(small, sw, sh, smallFaces, keepSmall, iterations)

        // dalla copia ridotta alla piena risoluzione, poi il bordo aderisce ai dettagli veri
        val up = upscale(FloatArray(sw * sh) { if (labels[it]) 1f else 0f }, sw, sh, w, h)
        val radius = max(2, (1.5f / scale).roundToInt())
        val refined = CutoutEngine.refineEdge(px, up, w, h, radius, 2e-3f)
        for (i in refined.indices) {
            // contrasto morbido: niente velature grigie dove il soggetto è pieno o lo sfondo è vuoto
            val a = refined[i]
            refined[i] = ((a - 0.08f) / 0.84f).coerceIn(0f, 1f)
            if ((px[i] ushr 24) == 0) refined[i] = 0f
        }
        return CutoutEngine.removeIslands(refined, w, h, 0.03f)
    }

    /**
     * Il mix di Scontorna: unisce il parere dei modelli IA con il motore di Zesty.
     * - [salient]: IS-Net (qualunque soggetto); [portrait]: MODNet (persone, capelli), può mancare;
     * - dove l'IA è sicura vince l'IA; nella fascia incerta decide il taglio su grafo (colori e contorni
     *   della foto), con l'IA come guida; infine il bordo aderisce ai dettagli a piena risoluzione.
     * Tutte le mappe sono w×h, valori 0..1.
     */
    fun fuse(px: IntArray, w: Int, h: Int, salient: FloatArray, portrait: FloatArray?, faces: List<Face>, keep: FloatArray? = null): FloatArray {
        val n = w * h
        // 1. parere combinato dell'IA: MODNet conta solo attorno alle persone trovate (testa, busto, braccia)
        val ai = salient.copyOf()
        if (portrait != null && faces.isNotEmpty()) {
            val zone = priorMap(w, h, faces.map { Face(it.cx, it.cy + 0.6f * it.eyeDist, it.eyeDist * 1.7f) })
            for (i in 0 until n) {
                val z = ((zone[i] - 0.12f) / 0.6f).coerceIn(0f, 1f)
                val m = portrait[i]
                // nella zona della persona i capelli del matte valgono più della sagoma generica
                // (un oggetto tenuto in mano resta: l'IA generica pesa ancora per il 65%)
                ai[i] = z * max(m, 0.65f * ai[i]) + (1f - z) * ai[i]
            }
        }

        // 2. taglio su grafo alla risoluzione di lavoro, guidato dall'IA
        val scale = min(1f, WORK_SIDE.toFloat() / max(w, h))
        val sw = max(8, (w * scale).roundToInt()); val sh = max(8, (h * scale).roundToInt())
        val small = downscale(px, w, h, sw, sh)
        val aiSmall = downscaleAlpha(ai, w, h, sw, sh)
        val keepSmall = keep?.let { downscaleAlpha(it, w, h, sw, sh) }
        // fascia attorno al contorno dell'IA in cui il taglio può correggerla (anche dove l'IA è "sicura")
        val bandR = max(3, (min(sw, sh) * 0.04f).roundToInt())
        val sure = CutoutEngine.grow(FloatArray(sw * sh) { if (aiSmall[it] > 0.92f) 1f else 0f }, sw, sh, -bandR)
        val maybe = CutoutEngine.grow(FloatArray(sw * sh) { if (aiSmall[it] > 0.08f) 1f else 0f }, sw, sh, bandR)
        val tri = ByteArray(sw * sh) {
            when {
                sure[it] > 0.5f -> FG_SURE
                maybe[it] < 0.5f -> BG_SURE
                aiSmall[it] > 0.5f -> FG_PROB
                else -> BG_PROB
            }
        }
        val labels = segment(small, sw, sh, faces, keepSmall, 3, priorIn = aiSmall, triIn = tri, priorWeight = 2.2f)
        val cut = upscale(FloatArray(sw * sh) { if (labels[it]) 1f else 0f }, sw, sh, w, h)
        val band = upscale(FloatArray(sw * sh) { if (sure[it] < 0.5f && maybe[it] > 0.5f) 1f else 0f }, sw, sh, w, h)

        // 3. lontano dal contorno conta l'IA; nella fascia del contorno:
        //    opacità fine dell'IA (capelli, pelo) -> resta, il taglio la completa solo dove è incerta;
        //    IA netta ma in disaccordo con colori e contorni della foto -> prevale il taglio
        val fused = FloatArray(n)
        for (i in 0 until n) {
            val a = ai[i]
            val conf = abs(2f * a - 1f)
            fused[i] = when {
                band[i] < 0.5f -> a
                a > 0.08f && a < 0.92f -> a * conf + cut[i] * (1f - conf)
                else -> 0.25f * a + 0.75f * cut[i]
            }
        }

        // 4. bordo a piena risoluzione: filtro guidato fine, poi pulizia
        val radius = max(2, (0.6f / scale).roundToInt())
        val refined = CutoutEngine.refineEdge(px, fused, w, h, radius, 6e-4f)
        for (i in 0 until n) {
            // il filtro guidato aggancia i contorni; dove l'IA ha già un'opacità fine (capelli) la si conserva
            val soft = fused[i] > 0.04f && fused[i] < 0.96f
            var v = if (soft) 0.5f * (refined[i] + fused[i]) else refined[i]
            v = ((v - 0.03f) / 0.94f).coerceIn(0f, 1f)
            if ((px[i] ushr 24) == 0) v = 0f
            if (keep != null && keep[i] < 0.05f) v = 0f
            fused[i] = v
        }
        return CutoutEngine.removeIslands(fused, w, h, 0.01f)
    }

    /** Segmentazione alla risoluzione di lavoro: true = soggetto. */
    fun segment(
        px: IntArray, w: Int, h: Int, faces: List<Face>, keep: FloatArray?, iterations: Int,
        priorIn: FloatArray? = null, triIn: ByteArray? = null, priorWeight: Float = 1.2f
    ): BooleanArray {
        val n = w * h
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        for (i in 0 until n) { val c = px[i]; r[i] = ((c shr 16) and 255).toFloat(); g[i] = ((c shr 8) and 255).toFloat(); b[i] = (c and 255).toFloat() }

        val prior = priorIn ?: priorMap(w, h, faces)
        val tri = triIn ?: trimap(w, h, prior, faces.isNotEmpty())
        for (i in 0 until n) {
            if ((px[i] ushr 24) < 16) tri[i] = BG_SURE
            if (keep != null && keep[i] < 0.05f && tri[i] != FG_SURE) tri[i] = BG_SURE
        }
        val fg = BooleanArray(n) { tri[it] >= FG_PROB }
        if (fg.none { it }) return fg

        val beta = edgeBeta(r, g, b, w, h)
        val gamma = 50f
        val big = 1e6f
        val fgModel = Gmm(); val bgModel = Gmm()
        repeat(iterations.coerceAtLeast(1)) { iter ->
            val fgIdx = (0 until n).filter { fg[it] && tri[it] != BG_SURE }
            val bgIdx = (0 until n).filter { !fg[it] }
            if (fgIdx.size < 10 || bgIdx.size < 10) return fg
            fgModel.fit(r, g, b, fgIdx, iter == 0)
            bgModel.fit(r, g, b, bgIdx, iter == 0)

            val graph = MaxFlow(n, 4 * n)
            for (i in 0 until n) {
                when (tri[i]) {
                    FG_SURE -> graph.setTerminal(i, big, 0f)
                    BG_SURE -> graph.setTerminal(i, 0f, big)
                    else -> {
                        val p = prior[i].coerceIn(0.02f, 0.98f)
                        val dFg = -fgModel.logLik(r[i], g[i], b[i]) - priorWeight * ln(p)
                        val dBg = -bgModel.logLik(r[i], g[i], b[i]) - priorWeight * ln(1f - p)
                        val m = min(dFg, dBg)
                        // tagliare il ramo verso lo sfondo (sink) = scegliere soggetto, costa dFg
                        graph.setTerminal(i, dBg - m, dFg - m)
                    }
                }
            }
            val diag = (1.0 / sqrt(2.0)).toFloat()
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (x + 1 < w) graph.addEdge(i, i + 1, gamma * exp(-beta * d2(r, g, b, i, i + 1)))
                if (y + 1 < h) {
                    graph.addEdge(i, i + w, gamma * exp(-beta * d2(r, g, b, i, i + w)))
                    if (x + 1 < w) graph.addEdge(i, i + w + 1, gamma * diag * exp(-beta * d2(r, g, b, i, i + w + 1)))
                    if (x > 0) graph.addEdge(i, i + w - 1, gamma * diag * exp(-beta * d2(r, g, b, i, i + w - 1)))
                }
            }
            graph.maxflow()
            var changed = 0
            for (i in 0 until n) {
                val v = when (tri[i]) { FG_SURE -> true; BG_SURE -> false; else -> graph.isSource(i) }
                if (v != fg[i]) changed++
                fg[i] = v
            }
            if (iter > 0 && changed < n / 500) return fg
        }
        return fg
    }

    private fun d2(r: FloatArray, g: FloatArray, b: FloatArray, i: Int, j: Int): Float {
        val dr = r[i] - r[j]; val dg = g[i] - g[j]; val db = b[i] - b[j]
        return dr * dr + dg * dg + db * db
    }

    private fun edgeBeta(r: FloatArray, g: FloatArray, b: FloatArray, w: Int, h: Int): Float {
        var sum = 0.0; var cnt = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (x + 1 < w) { sum += d2(r, g, b, i, i + 1); cnt++ }
            if (y + 1 < h) { sum += d2(r, g, b, i, i + w); cnt++ }
        }
        val mean = if (cnt > 0) sum / cnt else 1.0
        return (1.0 / (2.0 * max(mean, 1e-3))).toFloat()
    }

    /**
     * Probabilità a priori che un pixel sia soggetto, solo dalla posizione.
     * Con volti: testa (ellisse) e busto che si allarga verso il basso fino al bordo della foto.
     * Senza volti: ellisse centrale (il soggetto di solito è inquadrato al centro).
     */
    fun priorMap(w: Int, h: Int, faces: List<Face>): FloatArray {
        val out = FloatArray(w * h)
        if (faces.isEmpty()) {
            val cx = w / 2f; val cy = h * 0.53f; val rx = w * 0.36f; val ry = h * 0.42f
            for (y in 0 until h) for (x in 0 until w) {
                val dx = (x - cx) / rx; val dy = (y - cy) / ry
                val d = sqrt(dx * dx + dy * dy)
                out[y * w + x] = 0.25f + 0.45f * smooth(1.25f, 0.75f, d)
            }
            return out
        }
        java.util.Arrays.fill(out, 0.12f)
        for (f in faces) {
            val d = f.eyeDist.coerceAtLeast(2f)
            val hx = f.cx; val hy = f.cy + 0.25f * d
            val hrx = 1.35f * d; val hry = 1.85f * d
            val neckY = f.cy + 1.6f * d
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val dx = (x - hx) / hrx; val dy = (y - hy) / hry
                val head = smooth(1.25f, 0.85f, sqrt(dx * dx + dy * dy))
                var body = 0f
                if (y > neckY) {
                    val t = ((y - neckY) / (2.6f * d)).coerceIn(0f, 1f)
                    val half = d * (0.9f + 2.4f * t) // dal collo alle spalle
                    body = smooth(1.2f, 0.8f, abs(x - f.cx) / half)
                }
                out[i] = max(out[i], 0.12f + 0.8f * max(head, body))
            }
        }
        return out
    }

    private fun trimap(w: Int, h: Int, prior: FloatArray, hasFaces: Boolean): ByteArray {
        val n = w * h
        val tri = ByteArray(n) { if (prior[it] > 0.5f) FG_PROB else BG_PROB }
        // un lato della foto è sfondo certo solo se il soggetto probabile non lo tocca
        val band = max(2, (min(w, h) * 0.02f).roundToInt())
        fun touches(xs: IntRange, ys: IntRange): Boolean {
            for (y in ys) for (x in xs) if (prior[y * w + x] > 0.5f) return true
            return false
        }
        val top = !touches(0 until w, 0 until band)
        val bottom = !touches(0 until w, h - band until h)
        val left = !touches(0 until band, 0 until h)
        val right = !touches(w - band until w, 0 until h)
        for (y in 0 until h) for (x in 0 until w) {
            val edge = (top && y < band) || (bottom && y >= h - band) || (left && x < band) || (right && x >= w - band)
            if (edge) tri[y * w + x] = BG_SURE
        }
        if (hasFaces) for (i in 0 until n) if (prior[i] > 0.85f) tri[i] = FG_SURE // centro del volto e del busto
        return tri
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ---------------- ridimensionamento ----------------

    /** Riduzione a media d'area (niente scalettature che ingannano i modelli di colore). */
    fun downscale(px: IntArray, w: Int, h: Int, sw: Int, sh: Int): IntArray {
        if (sw == w && sh == h) return px.copyOf()
        val out = IntArray(sw * sh)
        for (y in 0 until sh) {
            val y0 = y * h / sh; val y1 = max(y0 + 1, (y + 1) * h / sh)
            for (x in 0 until sw) {
                val x0 = x * w / sw; val x1 = max(x0 + 1, (x + 1) * w / sw)
                var a = 0L; var r = 0L; var g = 0L; var b = 0L; var c = 0
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val p = px[yy * w + xx]
                    a += p ushr 24; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; c++
                }
                out[y * sw + x] = ((a / c).toInt() shl 24) or ((r / c).toInt() shl 16) or ((g / c).toInt() shl 8) or (b / c).toInt()
            }
        }
        return out
    }

    private fun downscaleAlpha(a: FloatArray, w: Int, h: Int, sw: Int, sh: Int): FloatArray {
        val out = FloatArray(sw * sh)
        for (y in 0 until sh) {
            val y0 = y * h / sh; val y1 = max(y0 + 1, (y + 1) * h / sh)
            for (x in 0 until sw) {
                val x0 = x * w / sw; val x1 = max(x0 + 1, (x + 1) * w / sw)
                var s = 0f; var c = 0
                for (yy in y0 until y1) for (xx in x0 until x1) { s += a[yy * w + xx]; c++ }
                out[y * sw + x] = s / c
            }
        }
        return out
    }

    /** Ingrandimento bilineare di una maschera. */
    fun upscale(a: FloatArray, sw: Int, sh: Int, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * sh / h - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(sh - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) * sw / w - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(sw - 1, x0 + 1); val tx = fx - x0
                val top = a[y0 * sw + x0] * (1 - tx) + a[y0 * sw + x1] * tx
                val bot = a[y1 * sw + x0] * (1 - tx) + a[y1 * sw + x1] * tx
                out[y * w + x] = top * (1 - ty) + bot * ty
            }
        }
        return out
    }

    // ---------------- mistura di gaussiane (colore) ----------------

    /** Modello di colore: K gaussiane in RGB con covarianza piena. */
    class Gmm(private val k: Int = 5) {
        private val weight = FloatArray(k)
        private val mean = Array(k) { FloatArray(3) }
        private val inv = Array(k) { FloatArray(9) }
        private val logNorm = FloatArray(k) // ln(peso) - ½ln(det) - 1.5 ln(2π)
        private var comp = IntArray(0)
        private var used = 0
        private val buf = FloatArray(k)

        fun fit(r: FloatArray, g: FloatArray, b: FloatArray, idx: List<Int>, init: Boolean) {
            if (init || comp.size != r.size || used == 0) {
                comp = IntArray(r.size)
                kmeans(r, g, b, idx)
            } else {
                // ogni pixel va alla gaussiana che lo spiega meglio
                for (i in idx) comp[i] = best(r[i], g[i], b[i])
            }
            estimate(r, g, b, idx)
        }

        private fun kmeans(r: FloatArray, g: FloatArray, b: FloatArray, idx: List<Int>) {
            val kk = min(k, idx.size)
            val cr = FloatArray(kk); val cg = FloatArray(kk); val cb = FloatArray(kk)
            // partenza deterministica: centri sparsi per luminosità
            val sorted = idx.sortedBy { 0.299f * r[it] + 0.587f * g[it] + 0.114f * b[it] }
            for (c in 0 until kk) { val p = sorted[((c + 0.5f) / kk * sorted.size).toInt().coerceAtMost(sorted.size - 1)]; cr[c] = r[p]; cg[c] = g[p]; cb[c] = b[p] }
            repeat(6) {
                val sr = DoubleArray(kk); val sg = DoubleArray(kk); val sb = DoubleArray(kk); val cnt = IntArray(kk)
                for (i in idx) {
                    var bi = 0; var bd = Float.MAX_VALUE
                    for (c in 0 until kk) {
                        val dr = r[i] - cr[c]; val dg = g[i] - cg[c]; val db = b[i] - cb[c]
                        val d = dr * dr + dg * dg + db * db
                        if (d < bd) { bd = d; bi = c }
                    }
                    comp[i] = bi; sr[bi] += r[i].toDouble(); sg[bi] += g[i].toDouble(); sb[bi] += b[i].toDouble(); cnt[bi]++
                }
                for (c in 0 until kk) if (cnt[c] > 0) { cr[c] = (sr[c] / cnt[c]).toFloat(); cg[c] = (sg[c] / cnt[c]).toFloat(); cb[c] = (sb[c] / cnt[c]).toFloat() }
            }
        }

        private fun estimate(r: FloatArray, g: FloatArray, b: FloatArray, idx: List<Int>) {
            val s = Array(k) { DoubleArray(3) }
            val ss = Array(k) { DoubleArray(9) }
            val cnt = IntArray(k)
            for (i in idx) {
                val c = comp[i]; val v = doubleArrayOf(r[i].toDouble(), g[i].toDouble(), b[i].toDouble())
                cnt[c]++
                for (p in 0 until 3) { s[c][p] += v[p]; for (q in 0 until 3) ss[c][p * 3 + q] += v[p] * v[q] }
            }
            used = 0
            val total = idx.size.coerceAtLeast(1)
            for (c in 0 until k) {
                if (cnt[c] == 0) { weight[c] = 0f; logNorm[c] = Float.NEGATIVE_INFINITY; continue }
                used++
                weight[c] = cnt[c].toFloat() / total
                for (p in 0 until 3) mean[c][p] = (s[c][p] / cnt[c]).toFloat()
                val cov = DoubleArray(9)
                for (p in 0 until 3) for (q in 0 until 3) cov[p * 3 + q] = ss[c][p * 3 + q] / cnt[c] - mean[c][p].toDouble() * mean[c][q]
                for (p in 0 until 3) cov[p * 4] += 4.0 // regolarizzazione: colori quasi piatti restano stabili
                val det = cov[0] * (cov[4] * cov[8] - cov[5] * cov[7]) - cov[1] * (cov[3] * cov[8] - cov[5] * cov[6]) + cov[2] * (cov[3] * cov[7] - cov[4] * cov[6])
                val d = max(det, 1e-6)
                val iv = inv[c]
                iv[0] = ((cov[4] * cov[8] - cov[5] * cov[7]) / d).toFloat()
                iv[1] = ((cov[2] * cov[7] - cov[1] * cov[8]) / d).toFloat()
                iv[2] = ((cov[1] * cov[5] - cov[2] * cov[4]) / d).toFloat()
                iv[3] = ((cov[5] * cov[6] - cov[3] * cov[8]) / d).toFloat()
                iv[4] = ((cov[0] * cov[8] - cov[2] * cov[6]) / d).toFloat()
                iv[5] = ((cov[2] * cov[3] - cov[0] * cov[5]) / d).toFloat()
                iv[6] = ((cov[3] * cov[7] - cov[4] * cov[6]) / d).toFloat()
                iv[7] = ((cov[1] * cov[6] - cov[0] * cov[7]) / d).toFloat()
                iv[8] = ((cov[0] * cov[4] - cov[1] * cov[3]) / d).toFloat()
                logNorm[c] = (ln(weight[c].toDouble()) - 0.5 * ln(d) - 1.5 * ln(2 * PI)).toFloat()
            }
        }

        private fun compLog(c: Int, r: Float, g: Float, b: Float): Float {
            if (weight[c] <= 0f) return Float.NEGATIVE_INFINITY
            val m = mean[c]; val iv = inv[c]
            val dr = r - m[0]; val dg = g - m[1]; val db = b - m[2]
            val q = dr * (iv[0] * dr + iv[1] * dg + iv[2] * db) +
                dg * (iv[3] * dr + iv[4] * dg + iv[5] * db) +
                db * (iv[6] * dr + iv[7] * dg + iv[8] * db)
            return logNorm[c] - 0.5f * q
        }

        private fun best(r: Float, g: Float, b: Float): Int {
            var bi = 0; var bv = Float.NEGATIVE_INFINITY
            for (c in 0 until k) { val v = compLog(c, r, g, b); if (v > bv) { bv = v; bi = c } }
            return bi
        }

        /** ln p(colore | modello), con un minimo per non dare costi infiniti. */
        fun logLik(r: Float, g: Float, b: Float): Float {
            var mx = Float.NEGATIVE_INFINITY
            val v = buf
            for (c in 0 until k) { v[c] = compLog(c, r, g, b); if (v[c] > mx) mx = v[c] }
            if (mx == Float.NEGATIVE_INFINITY) return -40f
            var s = 0.0
            for (c in 0 until k) if (v[c] > Float.NEGATIVE_INFINITY) s += exp((v[c] - mx).toDouble())
            return max(-40f, mx + ln(s).toFloat())
        }
    }

    // ---------------- taglio minimo su grafo (Boykov–Kolmogorov) ----------------

    /**
     * Max-flow / min-cut con alberi di ricerca riutilizzati (Boykov–Kolmogorov 2004),
     * adatto ai grafi a griglia delle immagini. Sorgente = soggetto, pozzo = sfondo.
     */
    class MaxFlow(private val n: Int, maxEdges: Int) {
        private val first = IntArray(n) { -1 }
        private val head = IntArray(2 * maxEdges)
        private val nextArc = IntArray(2 * maxEdges)
        private val cap = FloatArray(2 * maxEdges)
        private var arcs = 0
        private val trCap = FloatArray(n)          // >0 verso la sorgente, <0 verso il pozzo
        private val parent = IntArray(n) { NONE }
        private val isSink = BooleanArray(n)
        private val ts = IntArray(n)
        private val dist = IntArray(n)
        private val active = IntArray(n + 1); private var aHead = 0; private var aTail = 0
        private val inActive = BooleanArray(n)
        private val orphans = IntArray(n + 1); private var oHead = 0; private var oTail = 0
        private var time = 0
        var flow = 0.0
            private set

        fun setTerminal(i: Int, source: Float, sink: Float) {
            val d = trCap[i] + source - sink
            flow += min(source, sink).toDouble()
            trCap[i] = d
        }

        fun addEdge(i: Int, j: Int, c: Float, rev: Float = c) {
            val a = arcs; arcs += 2
            head[a] = j; cap[a] = c; nextArc[a] = first[i]; first[i] = a
            head[a + 1] = i; cap[a + 1] = rev; nextArc[a + 1] = first[j]; first[j] = a + 1
        }

        fun isSource(i: Int) = parent[i] != NONE && !isSink[i]

        private fun setActive(i: Int) {
            if (inActive[i]) return
            inActive[i] = true; active[aTail] = i; aTail = (aTail + 1) % active.size
        }

        private fun nextActive(): Int {
            while (aHead != aTail) {
                val i = active[aHead]; aHead = (aHead + 1) % active.size
                inActive[i] = false
                if (parent[i] != NONE) return i
            }
            return -1
        }

        private fun orphanFront(i: Int) { parent[i] = ORPHAN; oHead = (oHead - 1 + orphans.size) % orphans.size; orphans[oHead] = i }
        private fun orphanRear(i: Int) { parent[i] = ORPHAN; orphans[oTail] = i; oTail = (oTail + 1) % orphans.size }

        fun maxflow(): Double {
            for (i in 0 until n) {
                when {
                    trCap[i] > 0f -> { isSink[i] = false; parent[i] = TERMINAL; setActive(i); ts[i] = 0; dist[i] = 1 }
                    trCap[i] < 0f -> { isSink[i] = true; parent[i] = TERMINAL; setActive(i); ts[i] = 0; dist[i] = 1 }
                    else -> parent[i] = NONE
                }
            }
            var current = -1
            while (true) {
                var i = current
                if (i >= 0) { inActive[i] = false; if (parent[i] == NONE) i = -1 }
                if (i < 0) { i = nextActive(); if (i < 0) break }

                // crescita degli alberi
                var meet = -1
                var a = first[i]
                if (!isSink[i]) {
                    while (a >= 0) {
                        if (cap[a] > 0f) {
                            val j = head[a]
                            if (parent[j] == NONE) { isSink[j] = false; parent[j] = a xor 1; ts[j] = ts[i]; dist[j] = dist[i] + 1; setActive(j) }
                            else if (isSink[j]) { meet = a; break }
                            else if (ts[j] <= ts[i] && dist[j] > dist[i]) { parent[j] = a xor 1; ts[j] = ts[i]; dist[j] = dist[i] + 1 }
                        }
                        a = nextArc[a]
                    }
                } else {
                    while (a >= 0) {
                        if (cap[a xor 1] > 0f) {
                            val j = head[a]
                            if (parent[j] == NONE) { isSink[j] = true; parent[j] = a xor 1; ts[j] = ts[i]; dist[j] = dist[i] + 1; setActive(j) }
                            else if (!isSink[j]) { meet = a xor 1; break }
                            else if (ts[j] <= ts[i] && dist[j] > dist[i]) { parent[j] = a xor 1; ts[j] = ts[i]; dist[j] = dist[i] + 1 }
                        }
                        a = nextArc[a]
                    }
                }
                time++
                if (meet >= 0) {
                    inActive[i] = true // resta il nodo corrente
                    current = i
                    augment(meet)
                    adopt()
                } else current = -1
            }
            return flow
        }

        /** [mid] va da un nodo dell'albero sorgente a uno dell'albero pozzo. */
        private fun augment(mid: Int) {
            var bott = cap[mid]
            var i = head[mid xor 1]
            while (true) { val p = parent[i]; if (p == TERMINAL) break; bott = min(bott, cap[p xor 1]); i = head[p] }
            bott = min(bott, trCap[i])
            i = head[mid]
            while (true) { val p = parent[i]; if (p == TERMINAL) break; bott = min(bott, cap[p]); i = head[p] }
            bott = min(bott, -trCap[i])

            cap[mid xor 1] += bott; cap[mid] -= bott
            i = head[mid xor 1]
            while (true) {
                val p = parent[i]; if (p == TERMINAL) break
                cap[p] += bott; cap[p xor 1] -= bott
                if (cap[p xor 1] <= 0f) orphanFront(i)
                i = head[p]
            }
            trCap[i] -= bott
            if (trCap[i] <= 0f) orphanFront(i)
            i = head[mid]
            while (true) {
                val p = parent[i]; if (p == TERMINAL) break
                cap[p xor 1] += bott; cap[p] -= bott
                if (cap[p] <= 0f) orphanFront(i)
                i = head[p]
            }
            trCap[i] += bott
            if (trCap[i] >= 0f) orphanFront(i)
            flow += bott
        }

        private fun adopt() {
            while (oHead != oTail) {
                val i = orphans[oHead]; oHead = (oHead + 1) % orphans.size
                processOrphan(i, isSink[i])
            }
        }

        private fun processOrphan(i: Int, sinkTree: Boolean) {
            var dMin = INF; var aMin = NONE
            var a0 = first[i]
            while (a0 >= 0) {
                val residual = if (sinkTree) cap[a0] else cap[a0 xor 1]
                var j = head[a0]
                if (residual > 0f && isSink[j] == sinkTree && parent[j] != NONE) {
                    // il vicino è ancora collegato al terminale?
                    var d = 0
                    while (true) {
                        if (ts[j] == time) { d += dist[j]; break }
                        val a = parent[j]
                        d++
                        if (a == TERMINAL) { ts[j] = time; dist[j] = 1; break }
                        if (a == ORPHAN) { d = INF; break }
                        j = head[a]
                    }
                    if (d < INF) {
                        if (d < dMin) { aMin = a0; dMin = d }
                        j = head[a0]
                        while (ts[j] != time) { ts[j] = time; dist[j] = d--; j = head[parent[j]] }
                    }
                }
                a0 = nextArc[a0]
            }
            parent[i] = aMin
            if (aMin != NONE) { ts[i] = time; dist[i] = dMin + 1; return }
            // nessun nuovo genitore: il nodo diventa libero e i suoi figli orfani
            a0 = first[i]
            while (a0 >= 0) {
                val j = head[a0]
                val a = parent[j]
                if (isSink[j] == sinkTree && a != NONE) {
                    val residual = if (sinkTree) cap[a0] else cap[a0 xor 1]
                    if (residual > 0f) setActive(j)
                    if (a != TERMINAL && a != ORPHAN && head[a] == i) orphanRear(j)
                }
                a0 = nextArc[a0]
            }
        }

        private companion object {
            const val NONE = -1
            const val TERMINAL = -2
            const val ORPHAN = -3
            const val INF = Int.MAX_VALUE
        }
    }
}
