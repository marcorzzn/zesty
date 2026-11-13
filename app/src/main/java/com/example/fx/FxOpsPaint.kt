package com.example.fx

import kotlin.math.*

/**
 * Pittura, fumetto d'azione, carta e falsi colori (docs/EFFETTI.md §10). Scritte per Zesty.
 *
 * - `brush_strokes` pennellate vere: orientate lungo le forme, fermate dai contorni, con setole e due passate
 *                   (grandi per le masse, piccole dove c'è dettaglio). Impressionismo, olio, pastello.
 * - `speed_lines`   linee cinetiche del manga che convergono verso un punto, lasciandolo libero.
 * - `torn_edge`     bordo di carta strappata, con l'anima bianca delle fibre e un'ombra leggera.
 * - `infrared`      pellicola infrarossa a falsi colori: il verde delle piante diventa rosso o rosa.
 * Distanze in u (1% del lato corto), come il resto del motore.
 */
object FxOpsPaint {
    val ops: Map<String, FxOp> = mapOf(
        "brush_strokes" to FxOp { img, a, ctx -> brushStrokes(img, a, ctx.seed) },
        "speed_lines" to FxOp { img, a, ctx -> speedLines(img, a, ctx.seed) },
        "torn_edge" to FxOp { img, a, ctx -> tornEdge(img, a, ctx.seed) },
        "infrared" to FxOp { img, a, _ -> infrared(img, a) }
    )

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t)
    }

    // ---------------------------------------------------------------- pennellate

    /**
     * Direzione delle forme: tensore di struttura della luminosità, sfocato. Restituisce per ogni pixel la tangente
     * ai contorni (come doppio angolo) e quanto quella direzione è netta (0 = zona piatta).
     */
    private fun flowField(l: FloatArray, w: Int, h: Int, radius: Int): Triple<FloatArray, FloatArray, FloatArray> {
        val jxx = FloatArray(w * h); val jxy = FloatArray(w * h); val jyy = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - 1) * w; val y1 = y * w; val y2 = min(h - 1, y + 1) * w
            for (x in 0 until w) {
                val x0 = max(0, x - 1); val x2 = min(w - 1, x + 1)
                val gx = (l[y0 + x2] + 2 * l[y1 + x2] + l[y2 + x2]) - (l[y0 + x0] + 2 * l[y1 + x0] + l[y2 + x0])
                val gy = (l[y2 + x0] + 2 * l[y2 + x] + l[y2 + x2]) - (l[y0 + x0] + 2 * l[y0 + x] + l[y0 + x2])
                jxx[y1 + x] = gx * gx; jxy[y1 + x] = gx * gy; jyy[y1 + x] = gy * gy
            }
        }
        val sxx = FxBlur.boxF(jxx, w, h, radius); val sxy = FxBlur.boxF(jxy, w, h, radius); val syy = FxBlur.boxF(jyy, w, h, radius)
        // per ogni pixel: coseno e seno del DOPPIO angolo della tangente (niente trigonometria) e la coerenza
        val c2 = FloatArray(w * h); val s2 = FloatArray(w * h); val coh = FloatArray(w * h)
        var maxMag = 1e-6f
        for (i in 0 until w * h) {
            val d = sxx[i] - syy[i]; val e = 2f * sxy[i]
            val mag = sqrt(d * d + e * e)
            // tangente = gradiente ruotato di 90°: il doppio angolo ruota di 180°, cioè cambia segno
            if (mag > 1e-9f) { c2[i] = -d / mag; s2[i] = -e / mag } else { c2[i] = 1f; s2[i] = 0f }
            coh[i] = mag; if (mag > maxMag) maxMag = mag
        }
        // normalizza con una curva morbida: anche i contorni deboli orientano il tratto
        for (i in 0 until w * h) coh[i] = sqrt(coh[i] / maxMag).coerceIn(0f, 1f)
        return Triple(c2, s2, coh)
    }

    /**
     * Pennellate: ogni tratto prende il colore dalla foto nel suo centro, segue la direzione delle forme
     * (dritto o curvo) e si ferma quando incontra un colore troppo diverso, così i contorni restano leggibili.
     *
     * size larghezza (u) · length lunghezza in larghezze · curve 0..100 quanto il tratto segue il flusso curvando
     * angle direzione nelle zone piatte · flow 0..100 quanto quella direzione gira in ampi vortici
     * jitter variazione di direzione (gradi) · colorJitter variazione di tono (%)
     * detail 0..100 seconda passata di tratti fini dove c'è dettaglio · bristle 0..100 setole visibili
     * density 0..100 quanta superficie coprono i tratti · base "photo" (sotto c'è la foto sfocata) o "paper"
     * saturation spinta di colore dei tratti (%) · clip 0..100 quanto i contorni fermano i tratti
     *
     * I tratti si progettano una volta sola, poi si dipingono in parallelo a strisce orizzontali: ogni striscia
     * applica tutti i tratti nello stesso ordine, quindi il risultato è identico a quello sequenziale.
     */
    fun brushStrokes(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val w = img.w; val h = img.h
        val width = (a.num("size", 1f) * img.unit).coerceAtLeast(2f)
        val lengthK = a.num("length", 3f).coerceIn(1f, 12f)
        val curve = a.num("curve", 30f) / 100f
        val baseAngle = Math.toRadians(a.num("angle", 60f).toDouble()).toFloat()
        val flow = a.num("flow", 0f) / 100f
        val jitter = Math.toRadians(a.num("jitter", 12f).toDouble()).toFloat()
        val colorJitter = a.num("colorJitter", 8f) / 100f
        val detail = a.num("detail", 50f) / 100f
        val bristle = a.num("bristle", 35f) / 100f
        val density = (a.num("density", 100f) / 100f).coerceIn(0.1f, 1.5f)
        val sat = 1f + a.num("saturation", 10f) / 100f
        val clip = a.num("clip", 60f) / 100f
        val paperBase = a.choice("base", listOf("photo", "paper"), "photo") == "paper"
        val paper = a.color("paper", 0xFFF4EEE1.toInt())
        val n = w * h

        // colori di riferimento: leggermente sfocati (il tratto prende una media, non il rumore)
        val ref = FxBlur.gaussian(img.px, w, h, max(1f, width * 0.35f))
        val l = FloatArray(n) { Px.luma(ref[it]) / 255f }
        // differenza fra due pixel per decidere se sono la stessa forma: luce E colore (rosso e verde
        // possono avere la stessa luminosità ma sono forme diverse)
        fun diff(i: Int, j: Int): Float {
            val a1 = ref[i]; val a2 = ref[j]
            val dc = (abs(Px.r(a1) - Px.r(a2)) + abs(Px.g(a1) - Px.g(a2)) + abs(Px.b(a1) - Px.b(a2))) / 765f
            return max(abs(l[i] - l[j]), dc * 1.2f)
        }
        val (tc2, ts2, coh) = flowField(l, w, h, max(1, (width * 1.2f).roundToInt()))
        val edge = FxOpsSpatial.sobel(FloatArray(n) { Px.luma(img.px[it]) }, w, h)
        val edgeBlur = FxBlur.boxF(edge, w, h, max(1, (width * 0.8f).roundToInt()))

        val canvas = if (paperBase) IntArray(n) { paper } else FxBlur.gaussian(img.px, w, h, width * 1.5f)
        val cr = FloatArray(n); val cg = FloatArray(n); val cb = FloatArray(n)
        for (i in 0 until n) { val c = canvas[i]; cr[i] = Px.r(c).toFloat(); cg[i] = Px.g(c).toFloat(); cb[i] = Px.b(c).toFloat() }

        // direzione finale per pixel: nelle zone piatte quella di base (che con "flow" gira in ampi vortici),
        // lungo i contorni quella delle forme. Media su angoli doppi per non confondere 0° e 180°.
        val dirX = FloatArray(n); val dirY = FloatArray(n)
        // il vortice di base cambia lentamente: si calcola su una griglia rada (ogni 4 px)
        val gStep = 4
        val gw = w / gStep + 2; val gh = h / gStep + 2
        val gc = FloatArray(gw * gh); val gs = FloatArray(gw * gh)
        val flowFreq = 1f / (14f * img.unit)
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val base = if (flow > 0f) baseAngle + (FxRand.fbm(gx * gStep * flowFreq, gy * gStep * flowFreq, seed + 77, 3) - 0.5f) * 2f * PI.toFloat() * flow * 1.6f else baseAngle
            gc[gy * gw + gx] = cos(2 * base); gs[gy * gw + gx] = sin(2 * base)
        }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = coh[i]
            val g = (y / gStep) * gw + x / gStep
            var bx = gc[g] * (1f - c) + tc2[i] * c
            var by = gs[g] * (1f - c) + ts2[i] * c
            val m = sqrt(bx * bx + by * by)
            if (m < 1e-6f) { bx = 1f; by = 0f } else { bx /= m; by /= m }
            // dal doppio angolo all'angolo: formule di bisezione
            dirX[i] = sqrt(((1f + bx) * 0.5f).coerceAtLeast(0f))
            dirY[i] = sqrt(((1f - bx) * 0.5f).coerceAtLeast(0f)) * (if (by < 0f) -1f else 1f)
        }
        val segs = 4
        val pts = 2 * segs + 1

        /** Progetta i tratti di una passata; ogni tratto: punti della polilinea, colore, riquadro. */
        class Plan(val count: Int, val np: IntArray, val xs: FloatArray, val ys: FloatArray, val col: FloatArray,
                   val box: IntArray, val center: IntArray /* indice del pixel centrale */, val half: Float, val opacity: Float, val passSeed: Int)

        fun plan(strokeW: Float, onlyDetail: Boolean, passSeed: Int): Plan {
            // tratti lunghi = centri più radi: la copertura resta simile e il costo non esplode
            val step = strokeW * 0.75f * sqrt((lengthK / 2.5f).coerceAtLeast(1f)) / sqrt(density)
            val strokeLen = strokeW * lengthK * (if (onlyDetail) 0.7f else 1f)
            // centri su una griglia mossa, poi in ordine casuale: niente righe visibili
            val centers = ArrayList<Long>()
            var gy = 0f; var row = 0
            while (gy < h + step) {
                var gx = if (row % 2 == 0) 0f else step * 0.5f
                while (gx < w + step) {
                    val ix = (gx / step).toInt()
                    val jx = gx + (FxRand.r01(ix, row, passSeed) - 0.5f) * step
                    val jy = gy + (FxRand.r01(ix, row, passSeed + 7) - 0.5f) * step
                    val px = jx.roundToInt().coerceIn(0, w - 1); val py = jy.roundToInt().coerceIn(0, h - 1)
                    val keep = !onlyDetail || edgeBlur[py * w + px] > 0.06f + 0.1f * (1f - detail)
                    if (keep) centers.add((FxRand.hash(ix, row, passSeed + 3).toLong() shl 32) or (py.toLong() shl 16) or px.toLong())
                    gx += step
                }
                gy += step; row++
            }
            centers.sortBy { it ushr 32 }
            val m = centers.size
            val np = IntArray(m); val xs = FloatArray(m * pts); val ys = FloatArray(m * pts)
            val col = FloatArray(m * 3); val box = IntArray(m * 4); val sl = IntArray(m)
            val half = strokeW * 0.5f
            val tx = FloatArray(pts); val ty = FloatArray(pts)
            for ((s, key) in centers.withIndex()) {
                val px = (key and 0xFFFF).toInt(); val py = ((key ushr 16) and 0xFFFF).toInt()
                val ci = py * w + px
                // colore del tratto: dalla foto, con una piccola variazione di tono come in un tocco vero
                val c0 = ref[ci]
                var r = Px.r(c0) / 255f; var g = Px.g(c0) / 255f; var b = Px.b(c0) / 255f
                val lum = Px.lumaRGB(r, g, b)
                r = lum + (r - lum) * sat; g = lum + (g - lum) * sat; b = lum + (b - lum) * sat
                val vj = 1f + (FxRand.r01(px, py, passSeed + 11) - 0.5f) * 2f * colorJitter
                val hj = (FxRand.r01(px, py, passSeed + 13) - 0.5f) * colorJitter
                r = r * vj + hj; g = g * vj; b = b * vj - hj
                col[s * 3] = r.coerceIn(0f, 1f) * 255f; col[s * 3 + 1] = g.coerceIn(0f, 1f) * 255f; col[s * 3 + 2] = b.coerceIn(0f, 1f) * 255f
                sl[s] = ci
                val a0 = atan2(dirY[ci], dirX[ci]) + (FxRand.r01(px, py, passSeed + 17) - 0.5f) * 2f * jitter
                // traccia: dal centro avanti e indietro, curvando secondo il flusso, fermandosi sui contorni
                tx[segs] = px.toFloat(); ty[segs] = py.toFloat()
                var lo = segs; var hi = segs
                val segLen = strokeLen / (2 * segs)
                for (side in 0..1) {
                    var x = px.toFloat(); var y = py.toFloat()
                    var dx = cos(a0); var dy = sin(a0)
                    if (side == 1) { dx = -dx; dy = -dy }
                    for (k in 1..segs) {
                        if (curve > 0f) {
                            val fi = y.roundToInt().coerceIn(0, h - 1) * w + x.roundToInt().coerceIn(0, w - 1)
                            var fx = dirX[fi]; var fy = dirY[fi]
                            if (fx * dx + fy * dy < 0f) { fx = -fx; fy = -fy }
                            dx += (fx - dx) * curve; dy += (fy - dy) * curve
                            val nn = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f); dx /= nn; dy /= nn
                        }
                        val nx = x + dx * segLen; val ny = y + dy * segLen
                        val ji = ny.roundToInt().coerceIn(0, h - 1) * w + nx.roundToInt().coerceIn(0, w - 1)
                        if (clip > 0f && diff(ji, ci) > 0.32f * (1.05f - clip)) break
                        x = nx; y = ny
                        if (side == 0) { hi = segs + k; tx[hi] = x; ty[hi] = y } else { lo = segs - k; tx[lo] = x; ty[lo] = y }
                    }
                }
                val cnt = hi - lo + 1
                np[s] = cnt
                var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                for (k in 0 until cnt) {
                    val x = tx[lo + k]; val y = ty[lo + k]
                    xs[s * pts + k] = x; ys[s * pts + k] = y
                    minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                }
                box[s * 4] = max(0, (minX - half - 1).toInt()); box[s * 4 + 1] = max(0, (minY - half - 1).toInt())
                box[s * 4 + 2] = min(w - 1, (maxX + half + 1).toInt()); box[s * 4 + 3] = min(h - 1, (maxY + half + 1).toInt())
            }
            return Plan(m, np, xs, ys, col, box, sl, half, if (onlyDetail) 0.92f else 0.96f, passSeed)
        }

        // soglia di luminosità oltre la quale un pixel "appartiene" a un'altra forma e il tratto lo risparmia
        val edgeThr = 0.32f * (1.05f - clip) * 1.2f

        /** Dipinge i tratti del piano sulle righe [ya, yb). */
        fun paint(p: Plan, ya: Int, yb: Int) {
            val vxs = FloatArray(pts); val vys = FloatArray(pts); val inv2 = FloatArray(pts); val invL = FloatArray(pts)
            val half = p.half
            val reach2 = (half + 1f) * (half + 1f)
            for (s in 0 until p.count) {
                val y0 = max(ya, p.box[s * 4 + 1]); val y1 = min(yb - 1, p.box[s * 4 + 3])
                if (y0 > y1) continue
                val x0 = p.box[s * 4]; val x1 = p.box[s * 4 + 2]
                val o = s * pts; val cnt = p.np[s]
                val nseg = max(1, cnt - 1)
                for (k in 0 until nseg) {
                    val vx = if (cnt > 1) p.xs[o + k + 1] - p.xs[o + k] else 0f
                    val vy = if (cnt > 1) p.ys[o + k + 1] - p.ys[o + k] else 0f
                    val len2 = vx * vx + vy * vy
                    vxs[k] = vx; vys[k] = vy
                    inv2[k] = if (len2 < 1e-6f) 0f else 1f / len2
                    invL[k] = 1f / sqrt(len2).coerceAtLeast(1e-4f)
                }
                val sr = p.col[s * 3]; val sg = p.col[s * 3 + 1]; val sb = p.col[s * 3 + 2]
                val ci = p.center[s]
                val bristleSeed = s * 977 + p.passSeed
                for (yy in y0..y1) for (xx in x0..x1) {
                    var best = Float.MAX_VALUE; var bestK = 0; var bestTk = 0f
                    for (k in 0 until nseg) {
                        val ax = p.xs[o + k]; val ay = p.ys[o + k]
                        val vx = vxs[k]; val vy = vys[k]
                        val t = (((xx - ax) * vx + (yy - ay) * vy) * inv2[k]).coerceIn(0f, 1f)
                        val qx = xx - (ax + vx * t); val qy = yy - (ay + vy * t)
                        val d2 = qx * qx + qy * qy
                        if (d2 < best) { best = d2; bestK = k; bestTk = t }
                    }
                    if (best > reach2) continue
                    // punte: il tratto è più stretto all'inizio e alla fine
                    val tt = ((bestK + bestTk) / nseg).coerceIn(0f, 1f)
                    val hw = half * (0.55f + 0.45f * sqrt(4f * tt * (1f - tt)))
                    val d = sqrt(best)
                    if (d > hw + 1f) continue
                    var cov = 1f - smoothstep(hw - 1f, hw + 1f, d)
                    if (cov <= 0f) continue
                    val i = yy * w + xx
                    // il tratto risparmia i pixel di un'altra forma: i contorni restano puliti
                    if (clip > 0f) cov *= 1f - smoothstep(edgeThr, edgeThr * 1.7f, diff(i, ci))
                    if (cov <= 0f) continue
                    // setole: righe sottili parallele al tratto, chiare e scure
                    var shade = 1f
                    if (bristle > 0f) {
                        val qx = xx - (p.xs[o + bestK] + vxs[bestK] * bestTk); val qy = yy - (p.ys[o + bestK] + vys[bestK] * bestTk)
                        val side = (qx * -vys[bestK] + qy * vxs[bestK]) * invL[bestK]
                        val sv = side / half * 3.5f + 10f
                        val si = floor(sv).toInt()
                        val f = sv - si
                        val n0 = FxRand.r01(si, 0, bristleSeed); val n1 = FxRand.r01(si + 1, 0, bristleSeed)
                        val nv = n0 + (n1 - n0) * f
                        shade = 1f + (nv - 0.5f) * 0.5f * bristle
                        cov *= 1f - bristle * 0.35f * smoothstep(0.75f, 1f, nv) * (tt * 0.6f + 0.4f) // colpo secco verso la fine
                    }
                    val al = cov * p.opacity
                    cr[i] += (sr * shade - cr[i]) * al
                    cg[i] += (sg * shade - cg[i]) * al
                    cb[i] += (sb * shade - cb[i]) * al
                }
            }
        }

        val passes = ArrayList<Plan>()
        passes += plan(width, false, seed * 31 + 1)
        if (detail > 0f) passes += plan(max(2f, width * 0.5f), true, seed * 31 + 2)
        for (p in passes) FxParallel.rows(h) { ya, yb -> paint(p, ya, yb) }

        val out = IntArray(n)
        for (i in 0 until n) out[i] = (img.px[i] and 0xFF000000.toInt()) or (Px.argbF(0, cr[i], cg[i], cb[i]) and 0xFFFFFF)
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- linee cinetiche

    /**
     * Linee cinetiche: cunei sottili che partono dal bordo e puntano al centro (cx, cy in %), con lunghezze
     * casuali, fermandosi prima di una zona libera (inner, % della distanza dal bordo).
     */
    fun speedLines(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val w = img.w; val h = img.h
        val cx = a.num("cx", 50f) / 100f * w; val cy = a.num("cy", 50f) / 100f * h
        val count = a.int("count", 140).coerceIn(12, 600)
        val inner = (a.num("inner", 40f) / 100f).coerceIn(0f, 0.95f)
        val spread = a.num("spread", 35f) / 100f
        val thick = a.num("thickness", 0.9f) * img.unit
        val ink = a.color("ink", 0xFF111111.toInt())
        val opacity = a.num("opacity", 100f) / 100f
        // distanza massima centro-angolo: le linee arrivano oltre il bordo
        val maxR = sqrt(max(cx, w - cx).pow(2) + max(cy, h - cy).pow(2))
        val angK = FloatArray(count); val startK = FloatArray(count); val thickK = FloatArray(count)
        for (k in 0 until count) {
            angK[k] = ((k + (FxRand.r01(k, 1, seed) - 0.5f) * 0.9f) / count * 2.0 * PI).toFloat()
            startK[k] = maxR * (inner + (1f - inner) * spread * FxRand.r01(k, 2, seed).pow(1.6f))
            thickK[k] = thick * (0.35f + 1.1f * FxRand.r01(k, 3, seed))
        }
        val out = img.px.copyOf()
        val twoPi = (2 * PI).toFloat()
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx; val dy = y - cy
            val r = sqrt(dx * dx + dy * dy)
            var th = atan2(dy, dx); if (th < 0) th += twoPi
            val kc = (th / twoPi * count).toInt()
            var cov = 0f
            for (dk in -2..2) {
                val k = ((kc + dk) % count + count) % count
                val s = startK[k]
                if (r <= s) continue
                var da = abs(th - angK[k]); if (da > PI) da = twoPi - da
                // cuneo: nasce a punta in s e si allarga verso il bordo
                val grow = ((r - s) / (maxR - s).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val hw = thickK[k] * (0.15f + 0.85f * grow.pow(0.7f))
                val dist = da * r
                cov = max(cov, 1f - smoothstep(hw - 0.7f, hw + 0.7f, dist))
            }
            if (cov > 0f) {
                val i = y * w + x
                out[i] = (img.px[i] and 0xFF000000.toInt()) or (Px.mix(img.px[i], ink, cov * opacity) and 0xFFFFFF)
            }
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- carta strappata

    /**
     * Bordo strappato: oltre un margine irregolare la foto lascia il posto allo sfondo (bg), separata da una
     * striscia di fibre bianche (rim) e da un'ombra morbida sullo sfondo.
     */
    fun tornEdge(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val w = img.w; val h = img.h
        val margin = a.num("margin", 4f) * img.unit
        val rough = a.num("roughness", 60f) / 100f
        val rim = a.num("rim", 0.6f) * img.unit
        val bg = a.color("bg", 0xFF1E1C1A.toInt())
        val fiber = a.color("fiber", 0xFFF7F4EC.toInt())
        val shadow = a.num("shadow", 40f) / 100f
        val freq = 1f / (6f * img.unit)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val dEdge = min(min(x, w - 1 - x), min(y, h - 1 - y)).toFloat()
            // contorno irregolare: rumore grosso (onde dello strappo) + rumore fine (fibre)
            val n = FxRand.fbm(x * freq, y * freq, seed, 4) - 0.5f
            val fine = FxRand.r01(x / 2, y / 2, seed + 5) - 0.5f
            val limit = margin * (1f + rough * 1.6f * n) + fine * rough * 0.12f * img.unit
            val f = dEdge - limit                       // >0 dentro la foto
            val c = img.px[i]
            out[i] = when {
                f >= rim -> c
                f >= 0f -> {
                    // fibre: bianco con trama, sfuma verso la foto
                    val t = (f / rim.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val fib = Px.mix(fiber, 0xFFD9D2C3.toInt(), FxRand.r01(x, y, seed + 9) * 0.35f)
                    Px.mix(fib, c, smoothstep(0.55f, 1f, t))
                }
                else -> {
                    val sh = shadow * (1f - smoothstep(0f, 1.2f * img.unit, -f))
                    Px.mix(bg, 0xFF000000.toInt(), sh)
                }
            }
            out[i] = (c and 0xFF000000.toInt()) or (out[i] and 0xFFFFFF)
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- infrarosso

    /**
     * Infrarosso a falsi colori (come le pellicole per la fotografia aerea): la vegetazione riflette molto
     * infrarosso e diventa rossa/magenta, il cielo si fa più profondo, la pelle resta calda e chiara.
     * foliage 0..100 forza dell'effetto sulle piante · sky 0..100 quanto scurisce il cielo · hue gradi di rotazione finale
     */
    fun infrared(img: FxImage, a: FxArgs): FxImage {
        val foliage = a.num("foliage", 80f) / 100f
        val sky = a.num("sky", 40f) / 100f
        val hueShift = a.num("hue", 0f)
        val hsl = FloatArray(3)
        val out = IntArray(img.size)
        for (i in 0 until img.size) {
            val c = img.px[i]
            val r = Px.r(c) / 255f; val g = Px.g(c) / 255f; val b = Px.b(c) / 255f
            // stima dell'infrarosso: le piante (verde > rosso e blu) lo riflettono tantissimo
            val veg = ((g - max(r, b)) * 3f + (g - r).coerceAtLeast(0f)).coerceIn(0f, 1f)
            val lum = Px.lumaRGB(r, g, b)
            val ir = (lum * 0.85f + 0.15f * r + veg * foliage * 0.9f).coerceIn(0f, 1f)
            // scambio dei canali: IR → rosso, rosso → verde, verde → blu
            val swr = ir; val swg = r; val swb = g
            // solo ciò che riflette infrarosso (le piante) vira del tutto; pelle e oggetti restano vicini al vero,
            // appena più pallidi e caldi, come nella pellicola
            val k = (0.22f + veg * 1.3f * foliage).coerceIn(0f, 1f)
            val pr = r * 1.04f + 0.03f; val pg = g * 0.98f + 0.02f; val pb = b * 0.92f
            var nr = pr + (swr - pr) * k; var ng = pg + (swg - pg) * k; var nb = pb + (swb - pb) * k
            // cielo (blu dominante): più profondo
            val blue = ((b - max(r, g)) * 2.5f).coerceIn(0f, 1f)
            if (blue > 0f) {
                val q = blue * sky
                nr *= 1f - 0.55f * q; ng *= 1f - 0.3f * q; nb = nb * (1f - 0.15f * q) + 0.08f * q
            }
            var res = Px.argbF(255, nr * 255f, ng * 255f, nb * 255f)
            if (hueShift != 0f) {
                Hsl.fromRgb(Px.r(res), Px.g(res), Px.b(res), hsl)
                res = Hsl.toRgb(255, hsl[0] + hueShift, hsl[1], hsl[2])
            }
            out[i] = (c and 0xFF000000.toInt()) or (res and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }
}
