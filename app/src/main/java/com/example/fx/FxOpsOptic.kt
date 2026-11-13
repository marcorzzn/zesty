package com.example.fx

import kotlin.math.*

/**
 * Specchi, illusioni e geometrie (docs/EFFETTI.md §6 e §10). Scritte per Zesty.
 *
 * - `droste`       la foto contiene sé stessa all'infinito; modo "spiral" = spirale logaritmica alla Escher
 * - `glass_sphere` bolla di cristallo: dentro la scena capovolta e ingrandita, fuori lo sfondo sfocato
 * - `shatter`      vetro in frantumi a ragnatela: crepe radiali e anelli attorno al punto d'impatto
 * - `low_poly`     la foto ricostruita con triangoli a tinta unita, con luce sulle facce
 * - `blinds`       persiane: lamelle in rilievo che spezzano e spostano la foto
 * - `pixel_sort`   ordina i pixel per luminosità lungo righe o colonne, dentro una fascia di luce
 * Centri in % della foto, distanze in u (1% del lato corto).
 */
object FxOpsOptic {
    val ops: Map<String, FxOp> = mapOf(
        "droste" to FxOp { img, a, _ -> droste(img, a) },
        "glass_sphere" to FxOp { img, a, _ -> glassSphere(img, a) },
        "shatter" to FxOp { img, a, ctx -> shatter(img, a, ctx.seed) },
        "low_poly" to FxOp { img, a, ctx -> lowPoly(img, a, ctx.seed) },
        "blinds" to FxOp { img, a, _ -> blinds(img, a) },
        "pixel_sort" to FxOp { img, a, _ -> pixelSort(img, a) }
    )

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t)
    }

    private fun keepAlpha(src: Int, c: Int) = (src and 0xFF000000.toInt()) or (c and 0xFFFFFF)

    // ---------------------------------------------------------------- Droste

    /**
     * Ricorsione: dentro la foto, una copia rimpicciolita (scale) e ruotata (rotate) attorno al centro, e così via.
     * Ogni copia ha una cornice (frame, in u) con un'ombra leggera. In modo "spiral" il piano viene avvolto
     * in una spirale logaritmica: le copie si susseguono senza giunture, come nelle stampe di Escher.
     */
    fun droste(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val s = a.num("scale", 45f).coerceIn(15f, 85f) / 100f
        val rot = Math.toRadians(a.num("rotate", 0f).toDouble()).toFloat()
        val cx = a.num("cx", 50f) / 100f * w; val cy = a.num("cy", 50f) / 100f * h
        val frame = a.num("frame", 0.6f) * img.unit
        val frameCol = a.color("frameColor", 0xFFFFFFFF.toInt())
        val spiral = a.choice("mode", listOf("flat", "spiral"), "flat") == "spiral"
        val arms = a.num("arms", 1f).coerceIn(1f, 4f)
        val cr = cos(-rot); val sr = sin(-rot)
        val shadowW = 1.2f * img.unit

        val out = IntArray(w * h)
        val lnS = ln(s)
        // raggio della corona usata dalla spirale: il cerchio più grande attorno al centro che sta nella foto
        val rOut = min(min(cx, w - cx), min(cy, h - cy)).coerceAtLeast(4f)
        // fattore complesso della spirale: alpha = atan(ln s / 2π·arms), moltiplicatore = cos(alpha)·e^{i·alpha}
        val alpha = atan(lnS / (2f * PI.toFloat() * arms))
        val ca = cos(alpha); val sa = sin(alpha)

        for (y in 0 until h) for (x in 0 until w) {
            var qx = x + 0.5f; var qy = y + 0.5f
            if (spiral) {
                val dx = qx - cx; val dy = qy - cy
                val r = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                val lr = ln(r / rOut); val th = atan2(dy, dx)
                // w' = w · cos(alpha) · e^{i alpha}
                var u = (lr * ca - th * sa) * ca
                val v = (lr * sa + th * ca) * ca
                // riporta il raggio nella corona [s·rOut, rOut): le copie si ripetono senza fine
                u = ((u % lnS) + lnS) % lnS   // lnS < 0: risultato in (lnS, 0]
                val rr = exp(u) * rOut
                qx = cx + rr * cos(v); qy = cy + rr * sin(v)
            }
            // discesa nelle copie interne: finché il punto cade dentro la copia del livello, si passa a quella.
            // Le distanze si misurano nella scala del livello corrente, così cornici e ombre rimpiccioliscono.
            var depth = 0
            var onFrame = false
            var shadow = 0f
            while (depth < 24) {
                val dx = qx - cx; val dy = qy - cy
                val nx = cx + (dx * cr - dy * sr) / s; val ny = cy + (dx * sr + dy * cr) / s
                if (nx < 0f || nx >= w || ny < 0f || ny >= h) {
                    if (frame > 0f) {
                        val ox = max(0f, max(-nx, nx - w)); val oy = max(0f, max(-ny, ny - h))
                        val dOut = sqrt(ox * ox + oy * oy) * s
                        if (dOut < shadowW) shadow = 0.35f * (1f - dOut / shadowW)
                    }
                    break
                }
                if (frame > 0f && min(min(nx, w - nx), min(ny, h - ny)) * s <= frame) { onFrame = true; break }
                qx = nx; qy = ny; depth++
            }
            var c = if (onFrame) frameCol else FxSample.bilinear(img, qx - 0.5f, qy - 0.5f)
            if (shadow > 0f) c = Px.mix(c, 0xFF000000.toInt(), shadow)
            out[y * w + x] = keepAlpha(img.px[y * w + x], c)
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Bolla di cristallo

    /**
     * Sfera di vetro: i raggi attraversano la sfera e la scena appare capovolta e ingrandita (indice di
     * rifrazione ior). Bordo scuro (Fresnel), riflesso, ombra di contatto, sfondo sfocato (bgBlur, u).
     */
    fun glassSphere(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val cx = a.num("cx", 50f) / 100f * w; val cy = a.num("cy", 50f) / 100f * h
        val rad = (a.num("radius", 30f) / 100f * min(w, h)).coerceAtLeast(4f)
        val ior = a.num("ior", 1.5f).coerceIn(1.05f, 2.5f)
        val zoom = a.num("zoom", 100f) / 100f
        val shine = a.num("shine", 60f) / 100f
        val bgBlur = a.num("bgBlur", 1.5f) * img.unit
        val bgDark = a.num("bgDark", 15f) / 100f
        val bg = if (bgBlur >= 0.5f) FxImage(w, h, FxBlur.gaussian(img.px, w, h, bgBlur)) else img
        val out = IntArray(w * h)
        val lx = -0.45f; val ly = -0.55f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val dx = (x + 0.5f - cx) / rad; val dy = (y + 0.5f - cy) / rad
            val rho2 = dx * dx + dy * dy
            val res: Int
            if (rho2 < 1f) {
                val rho = sqrt(rho2)
                // deviazione del raggio nella sfera: due rifrazioni
                val th = asin(rho.coerceAtMost(0.9999f)); val ph = asin((rho / ior).coerceAtMost(0.9999f))
                val dev = 2f * (th - ph)
                // il raggio esce dalla parte opposta: immagine capovolta, ingrandita al centro
                val k = if (rho > 1e-4f) tan(dev.coerceAtMost(1.45f)) / rho else 2f * (1f - 1f / ior)
                val sxp = cx - dx * rad * k * zoom * 1.2f
                val syp = cy - dy * rad * k * zoom * 1.2f
                var c = FxSample.bilinear(img, sxp, syp)
                // Fresnel: bordo più scuro e un po' riflettente
                val fres = rho2 * rho2
                c = Px.mix(c, 0xFF101418.toInt(), 0.45f * fres)
                // riflesso della luce in alto a sinistra + piccolo riflesso secondario
                val hx = dx - lx * 0.6f; val hy = dy - ly * 0.6f
                val spec = exp(-(hx * hx + hy * hy) * 28f) * shine
                val rim = smoothstep(0.86f, 0.99f, rho) * (0.5f + 0.5f * (-(dx * lx + dy * ly) / max(rho, 1e-3f))).coerceIn(0f, 1f) * shine * 0.5f
                c = Px.mix(c, 0xFFFFFFFF.toInt(), (spec + rim).coerceIn(0f, 1f))
                // bordo antialias
                val edge = smoothstep(1f, 1f - 2.5f / rad, rho)
                res = Px.mix(Px.mix(bg.px[i], 0xFF000000.toInt(), bgDark), c, edge)
            } else {
                var c = Px.mix(bg.px[i], 0xFF000000.toInt(), bgDark)
                // ombra di contatto sotto la sfera
                val sx2 = dx / 1.05f; val sy2 = (dy - 1.0f) / 0.22f
                val sh = exp(-(sx2 * sx2 + sy2 * sy2) * 1.4f) * 0.55f
                c = Px.mix(c, 0xFF000000.toInt(), sh)
                res = c
            }
            out[i] = keepAlpha(img.px[i], res)
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Vetro in frantumi

    /**
     * Vetro rotto "a ragnatela" attorno al punto d'impatto (cx, cy): crepe radiali (spokes) e anelli che si
     * allargano verso l'esterno (size, u, = primo anello), entrambi ondulati. Ogni scheggia si sposta verso
     * l'esterno e ruota un poco (amount); le crepe sono linee scure con un filo di luce (crack).
     */
    fun shatter(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val w = img.w; val h = img.h
        val r0 = (a.num("size", 9f) * img.unit).coerceAtLeast(4f)
        val amount = a.num("amount", 60f) / 100f
        val cx = a.num("cx", 50f) / 100f * w; val cy = a.num("cy", 45f) / 100f * h
        val crack = a.num("crack", 70f) / 100f
        val spokes = a.int("spokes", 22).coerceIn(5, 80)
        val growth = 1.6f
        val lnG = ln(growth)
        val twoPi = (2 * PI).toFloat()
        val out = IntArray(w * h)
        val lineW = 0.1f * img.unit + 0.6f
        val nf = 1f / (8f * img.unit)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            val r = sqrt(dx * dx + dy * dy).coerceAtLeast(0.5f)
            // crepe ondulate: un po' di rumore su angolo e raggio
            val nz = FxRand.fbm(x * nf, y * nf, seed + 5, 3) - 0.5f
            var th = atan2(dy, dx) + nz * 0.07f
            if (th < 0) th += twoPi
            val lr = ln(r / r0) / lnG + nz * 0.2f
            val ring = floor(lr).toInt()
            // ogni anello sposta appena i suoi raggi: le crepe radiali proseguono ma con piccoli scarti
            val off = (FxRand.r01(ring, 3, seed) - 0.5f) * 0.3f
            val ts = th / twoPi * spokes + off
            val sector = floor(ts).toInt()
            val shard = ring * 1000 + ((sector % spokes) + spokes) % spokes
            // distanza dalle crepe in pixel: radiale (r·Δθ) e circolare (Δr)
            val fs = ts - sector
            val dRad = min(fs, 1f - fs) / spokes * twoPi * r
            val fr = lr - ring
            val ringR = r0 * growth.pow(ring.toFloat())
            val dRing = min(fr, 1f - fr) * ringR * (growth - 1f)
            val inCenter = lr < 0f
            val edge = if (inCenter) Float.MAX_VALUE else min(dRad, dRing)
            // scheggia: spinta verso l'esterno e rotazione attorno all'impatto, più forti a metà distanza
            val near = if (inCenter) 0f else (1f / (1f + ring * 0.35f))
            val push = amount * near * r0 * 0.25f * (0.5f + FxRand.r01(shard, 1, seed))
            val rot = (FxRand.r01(shard, 2, seed) - 0.5f) * 0.05f * amount * near
            val ux = dx / r; val uy = dy / r
            val ct = cos(rot); val st = sin(rot)
            val sx = cx + dx * ct - dy * st - ux * push
            val sy = cy + dx * st + dy * ct - uy * push
            var c = FxSample.bilinear(img, sx - 0.5f, sy - 0.5f)
            val lit = if (inCenter) 0.15f * amount else (FxRand.r01(shard, 4, seed) - 0.5f) * 0.3f * amount
            c = if (lit > 0) Px.mix(c, 0xFFFFFFFF.toInt(), lit) else Px.mix(c, 0xFF000000.toInt(), -lit)
            if (crack > 0f && edge < lineW * 3f) {
                // le crepe si assottigliano lontano dall'impatto
                val lw = lineW * (1.4f / (1f + max(0, ring) * 0.15f) + 0.3f)
                val line = 1f - smoothstep(lw * 0.5f, lw * 1.1f, edge)
                val glint = ((1f - smoothstep(lw * 1.1f, lw * 2.2f, edge)) - line).coerceAtLeast(0f)
                c = Px.mix(c, 0xFF0C0C0E.toInt(), line * crack * 0.85f)
                c = Px.mix(c, 0xFFFFFFFF.toInt(), glint * 0.6f * crack)
            }
            out[y * w + x] = keepAlpha(img.px[y * w + x], c)
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Low-poly

    /**
     * Triangoli su una griglia mossa (size, u): ogni triangolo prende il colore medio della foto che copre,
     * con una luce leggera per faccia (facet) e, se richiesto, il filo dei bordi (wire).
     */
    fun lowPoly(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val w = img.w; val h = img.h
        val cell = (a.num("size", 6f) * img.unit).coerceAtLeast(4f)
        val jit = a.num("jitter", 70f) / 100f
        val facet = a.num("facet", 25f) / 100f
        val wire = a.num("wire", 0f) / 100f
        val wireCol = a.color("wireColor", 0xFFFFFFFF.toInt())
        val gw = ceil(w / cell).toInt() + 1; val gh = ceil(h / cell).toInt() + 1
        val px = FloatArray(gw * gh); val py = FloatArray(gw * gh)
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val k = gy * gw + gx
            // i punti del bordo restano sul bordo: niente buchi agli angoli della foto
            val jx = if (gx == 0 || gx == gw - 1) 0f else (FxRand.r01(gx, gy, seed) - 0.5f) * jit * cell
            val jy = if (gy == 0 || gy == gh - 1) 0f else (FxRand.r01(gx, gy, seed + 1) - 0.5f) * jit * cell
            px[k] = (gx * cell + jx).coerceIn(0f, w.toFloat()); py[k] = (gy * cell + jy).coerceIn(0f, h.toFloat())
            if (gx == gw - 1) px[k] = w.toFloat()
            if (gy == gh - 1) py[k] = h.toFloat()
        }
        val ntri = (gw - 1) * (gh - 1) * 2
        val tri = IntArray(w * h) { -1 }
        val sumR = LongArray(ntri); val sumG = LongArray(ntri); val sumB = LongArray(ntri); val cnt = IntArray(ntri)
        val edgeD = FloatArray(w * h) { Float.MAX_VALUE }
        fun raster(t: Int, a0: Int, b0: Int, c0: Int) {
            val x0 = px[a0]; val y0 = py[a0]; val x1 = px[b0]; val y1 = py[b0]; val x2 = px[c0]; val y2 = py[c0]
            val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if (abs(area) < 1e-3f) return
            val minX = max(0, floor(min(x0, min(x1, x2))).toInt()); val maxX = min(w - 1, ceil(max(x0, max(x1, x2))).toInt())
            val minY = max(0, floor(min(y0, min(y1, y2))).toInt()); val maxY = min(h - 1, ceil(max(y0, max(y1, y2))).toInt())
            val l01 = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)).coerceAtLeast(1e-3f)
            val l12 = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).coerceAtLeast(1e-3f)
            val l20 = sqrt((x0 - x2) * (x0 - x2) + (y0 - y2) * (y0 - y2)).coerceAtLeast(1e-3f)
            for (y in minY..maxY) for (x in minX..maxX) {
                val fx = x + 0.5f; val fy = y + 0.5f
                val w0 = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) / area
                val w1 = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) / area
                val w2 = 1f - w0 - w1
                if (w0 < -1e-4f || w1 < -1e-4f || w2 < -1e-4f) continue
                val i = y * w + x
                if (tri[i] >= 0) continue
                tri[i] = t
                val c = img.px[i]
                sumR[t] += Px.r(c); sumG[t] += Px.g(c); sumB[t] += Px.b(c); cnt[t]++
                // distanza dal lato più vicino (in pixel): per il filo
                val d = min(abs(w0) * abs(area) / l12, min(abs(w1) * abs(area) / l20, abs(w2) * abs(area) / l01))
                edgeD[i] = d
            }
        }
        var t = 0
        for (gy in 0 until gh - 1) for (gx in 0 until gw - 1) {
            val k00 = gy * gw + gx; val k10 = k00 + 1; val k01 = k00 + gw; val k11 = k01 + 1
            // diagonale alternata: triangoli meno regolari
            if ((gx + gy) % 2 == 0) { raster(t, k00, k10, k11); raster(t + 1, k00, k11, k01) }
            else { raster(t, k00, k10, k01); raster(t + 1, k10, k11, k01) }
            t += 2
        }
        val col = IntArray(ntri)
        for (k in 0 until ntri) {
            if (cnt[k] == 0) continue
            var r = sumR[k].toFloat() / cnt[k]; var g = sumG[k].toFloat() / cnt[k]; var b = sumB[k].toFloat() / cnt[k]
            // luce per faccia: ogni triangolo leggermente più chiaro o più scuro, come un solido sfaccettato
            val f = 1f + (FxRand.r01(k, 7, seed) - 0.5f) * facet
            r *= f; g *= f; b *= f
            col[k] = Px.argbF(255, r, g, b)
        }
        val out = IntArray(w * h)
        val ww = 0.15f * img.unit + 0.5f
        for (i in 0 until w * h) {
            val k = tri[i]
            var c = if (k >= 0) col[k] else img.px[i]
            if (wire > 0f && k >= 0) c = Px.mix(c, wireCol, wire * (1f - smoothstep(ww * 0.5f, ww * 1.5f, edgeD[i])))
            out[i] = keepAlpha(img.px[i], c)
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Persiane

    /**
     * Lamelle (count) inclinate di angle gradi: ogni lamella è un piccolo cilindro in luce, separata da una
     * fessura d'ombra (gap %). La foto si sposta dentro ogni lamella (tilt %), come guardata attraverso.
     */
    fun blinds(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val count = a.num("count", 18f).coerceIn(3f, 120f)
        val ang = Math.toRadians(a.num("angle", 0f).toDouble()).toFloat()
        val gap = a.num("gap", 12f) / 100f
        val tilt = a.num("tilt", 35f) / 100f
        val relief = a.num("relief", 60f) / 100f
        val gapCol = a.color("gapColor", 0xFF000000.toInt())
        val nx = -sin(ang); val ny = cos(ang)                 // normale alle lamelle
        val span = abs(w * nx) + abs(h * ny)
        val period = span / count
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val along = (x - w / 2f) * nx + (y - h / 2f) * ny
            val t = along / period + count * 0.5f
            val f = t - floor(t)
            // spostamento dentro la lamella: la foto "scorre" come vista da un angolo
            val shift = (f - 0.5f) * tilt * period
            var c = FxSample.bilinear(img, x - nx * shift, y - ny * shift)
            // rilievo cilindrico: chiaro in alto, scuro in basso
            val shade = 1f + relief * (0.28f * cos(f * PI.toFloat()) - 0.08f)
            c = if (shade >= 1f) Px.mix(c, 0xFFFFFFFF.toInt(), (shade - 1f).coerceAtMost(1f)) else Px.mix(c, 0xFF000000.toInt(), (1f - shade).coerceAtMost(1f))
            if (gap > 0f) {
                val g = smoothstep(1f - gap, 1f - gap * 0.4f, f)
                c = Px.mix(c, gapCol, g * 0.92f)
            }
            out[y * w + x] = keepAlpha(img.px[y * w + x], c)
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Pixel sort

    /**
     * Pixel sort: lungo ogni riga (o colonna) i tratti consecutivi con luminosità fra low e high (%) vengono
     * ordinati per luminosità. Ne nascono le colature di luce tipiche della glitch art.
     * direction "horizontal" | "vertical" · reverse inverte l'ordine · maxRun lunghezza massima (u, 0 = senza limite)
     */
    fun pixelSort(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val low = a.num("low", 25f) / 100f * 255f; val high = a.num("high", 85f) / 100f * 255f
        val vertical = a.choice("direction", listOf("horizontal", "vertical"), "horizontal") == "vertical"
        val reverse = a.bool("reverse", false)
        val maxRun = (a.num("maxRun", 0f) * img.unit).toInt()
        val out = img.px.copyOf()
        val lines = if (vertical) w else h
        val len = if (vertical) h else w
        FxParallel.rows(lines) { l0, l1 ->
            val buf = LongArray(len)
            for (line in l0 until l1) {
                fun idx(p: Int) = if (vertical) p * w + line else line * w + p
                var p = 0
                while (p < len) {
                    val lum = Px.luma(img.px[idx(p)])
                    if (lum < low || lum > high) { p++; continue }
                    var q = p
                    while (q < len && (maxRun <= 0 || q - p < maxRun)) {
                        val lq = Px.luma(img.px[idx(q)])
                        if (lq < low || lq > high) break
                        q++
                    }
                    val n = q - p
                    if (n > 1) {
                        for (k in 0 until n) {
                            val c = img.px[idx(p + k)]
                            val lk = (Px.luma(c) * 16f).toLong()
                            buf[k] = (lk shl 32) or (c.toLong() and 0xFFFFFFFFL)
                        }
                        java.util.Arrays.sort(buf, 0, n)
                        for (k in 0 until n) {
                            val src = buf[if (reverse) n - 1 - k else k]
                            out[idx(p + k)] = (src and 0xFFFFFFFFL).toInt()
                        }
                    }
                    p = q
                }
            }
        }
        return FxImage(w, h, out)
    }
}
