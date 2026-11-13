package com.example.fx

import kotlin.math.*

/**
 * Luce e ottica (docs/EFFETTI.md §6.8 e §10). Scritte per Zesty.
 *
 * - `prism`     dispersione spettrale: la foto (o solo le sue luci) scomposta in un arcobaleno lungo una direzione,
 *               con una banda di caustica facoltativa, come la luce che passa da un prisma
 * - `kirlian`   aura elettrica: i contorni emettono un bagliore a filamenti, come nelle fotografie Kirlian
 * - `halation`  alone da pellicola: la luce forte rimbalza sul supporto e lascia un bordo rosso-arancio attorno alle luci
 * Distanze in u (1% del lato corto).
 */
object FxOpsLight {
    val ops: Map<String, FxOp> = mapOf(
        "prism" to FxOp { img, a, _ -> prism(img, a) },
        "kirlian" to FxOp { img, a, ctx -> kirlian(img, a, ctx.seed) },
        "halation" to FxOp { img, a, _ -> halation(img, a) }
    )

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t)
    }

    private fun keepAlpha(src: Int, c: Int) = (src and 0xFF000000.toInt()) or (c and 0xFFFFFF)

    /** Sfocatura gaussiana approssimata di un canale float: tre medie box. */
    private fun gaussF(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        if (sigma < 0.5f) return src
        val r = ((sqrt(4f * sigma * sigma + 1f) - 1f) / 2f).roundToInt().coerceAtLeast(1)
        return FxBlur.boxF(FxBlur.boxF(FxBlur.boxF(src, w, h, r), w, h, r), w, h, r)
    }

    /** Pesi RGB di [n] bande dello spettro, dal rosso al violetto; per ogni canale sommano a 1. */
    private fun spectrum(n: Int): FloatArray {
        val wts = FloatArray(n * 3)
        var sr = 0f; var sg = 0f; var sb = 0f
        for (k in 0 until n) {
            val t = if (n == 1) 0.5f else k / (n - 1f)
            val c = Hsl.toRgb(255, t * 275f, 1f, 0.5f)
            wts[k * 3] = Px.r(c) / 255f; wts[k * 3 + 1] = Px.g(c) / 255f; wts[k * 3 + 2] = Px.b(c) / 255f
            sr += wts[k * 3]; sg += wts[k * 3 + 1]; sb += wts[k * 3 + 2]
        }
        for (k in 0 until n) { wts[k * 3] /= sr; wts[k * 3 + 1] /= sg; wts[k * 3 + 2] /= sb }
        return wts
    }

    // ---------------------------------------------------------------- Prisma

    /**
     * mode full: ogni banda dello spettro prende la foto spostata di una frazione di spread (u) lungo angle,
     * quindi i contorni si sfrangiano in arcobaleno. mode highlights: solo le luci sopra threshold lasciano
     * una scia spettrale lunga spread (sommata in schermo). caustic: banda d'arcobaleno che attraversa
     * la foto a causticPos % (larga causticWidth u), perpendicolare alla dispersione.
     */
    fun prism(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h; val n = w * h
        val highlights = a.choice("mode", listOf("full", "highlights"), "full") == "highlights"
        val spread = a.num("spread", 1.2f) * img.unit
        val ang = Math.toRadians(a.num("angle", 0f).toDouble()).toFloat()
        val dx = cos(ang); val dy = sin(ang)
        val amount = a.num("amount", 80f) / 100f
        val thr = a.num("threshold", 70f) / 100f
        val caustic = a.num("caustic", 0f) / 100f
        val cPos = a.num("causticPos", 35f) / 100f
        val cWidth = (a.num("causticWidth", 12f) * img.unit).coerceAtLeast(2f)
        val bands = 10
        val wts = spectrum(bands)
        val src: FxImage = if (highlights) {
            // mappa delle luci, colore della foto pesato da quanto supera la soglia
            val hp = IntArray(n)
            for (i in 0 until n) {
                val c = img.px[i]
                val m = smoothstep(thr, min(1f, thr + 0.25f), Px.luma(c) / 255f)
                hp[i] = Px.argbF(255, Px.r(c) * m, Px.g(c) * m, Px.b(c) * m)
            }
            FxImage(w, h, FxBlur.gaussian(hp, w, h, 0.25f * img.unit))
        } else img
        // banda di caustica: coordinata lungo la dispersione, rispetto alla diagonale della foto
        val ext = abs(w * dx) + abs(h * dy)
        val c0 = -ext * 0.5f + cPos * ext
        val out = IntArray(n)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val i = y * w + x
                var r = 0f; var g = 0f; var b = 0f
                for (k in 0 until bands) {
                    val t = k / (bands - 1f)
                    val off = if (highlights) t * spread else (t - 0.5f) * spread
                    val c = FxSample.bilinear(src, x - dx * off, y - dy * off)
                    r += Px.r(c) * wts[k * 3]; g += Px.g(c) * wts[k * 3 + 1]; b += Px.b(c) * wts[k * 3 + 2]
                }
                val base = img.px[i]
                var or: Float; var og: Float; var ob: Float
                if (highlights) {
                    // schermo: le scie si sommano senza bruciare
                    val k = amount * 1.6f
                    or = 255f - (255f - Px.r(base)) * (1f - (r * k / 255f).coerceIn(0f, 1f))
                    og = 255f - (255f - Px.g(base)) * (1f - (g * k / 255f).coerceIn(0f, 1f))
                    ob = 255f - (255f - Px.b(base)) * (1f - (b * k / 255f).coerceIn(0f, 1f))
                } else {
                    or = Px.r(base) + (r - Px.r(base)) * amount
                    og = Px.g(base) + (g - Px.g(base)) * amount
                    ob = Px.b(base) + (b - Px.b(base)) * amount
                }
                if (caustic > 0f) {
                    val s = (x - w * 0.5f) * dx + (y - h * 0.5f) * dy
                    val t = (s - c0) / cWidth
                    if (t > -0.6f && t < 1.6f) {
                        val bell = smoothstep(-0.6f, 0.1f, t) * (1f - smoothstep(0.9f, 1.6f, t))
                        val sc = Hsl.toRgb(255, t.coerceIn(0f, 1f) * 275f, 1f, 0.55f)
                        val k = caustic * 0.55f * bell
                        or = 255f - (255f - or) * (1f - Px.r(sc) / 255f * k)
                        og = 255f - (255f - og) * (1f - Px.g(sc) / 255f * k)
                        ob = 255f - (255f - ob) * (1f - Px.b(sc) / 255f * k)
                    }
                }
                out[i] = keepAlpha(base, Px.argbF(255, or, og, ob))
            }
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Aura Kirlian

    /**
     * I contorni (oltre threshold) diventano la sorgente di un'aura: un nucleo quasi bianco, un alone vicino
     * di colore color e uno lontano (length u) di colore color2, spezzato in filamenti (filaments) da un rumore
     * a creste. Lo sfondo è la foto scurita di dark %.
     */
    fun kirlian(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val w = img.w; val h = img.h; val n = w * h
        val thr = a.num("threshold", 20f) / 100f
        val len = (a.num("length", 3f) * img.unit).coerceAtLeast(1f)
        val col = a.color("color", 0xFFB07CFF.toInt())
        val col2 = a.color("color2", 0xFF3D6BFF.toInt())
        val fil = a.num("filaments", 70f) / 100f
        val dark = a.num("dark", 85f) / 100f
        val glow = a.num("glow", 80f) / 100f
        val l0 = FloatArray(n) { Px.luma(img.px[it]) / 255f }
        val l = gaussF(l0, w, h, 0.12f * img.unit)
        val e = FloatArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val xm = max(x - 1, 0); val xp = min(x + 1, w - 1); val ym = max(y - 1, 0); val yp = min(y + 1, h - 1)
            val gx = l[y * w + xp] - l[y * w + xm]; val gy = l[yp * w + x] - l[ym * w + x]
            val g = sqrt(gx * gx + gy * gy) * 2.2f
            e[y * w + x] = smoothstep(thr * 0.5f, thr * 0.5f + 0.18f, g)
        }
        val near = gaussF(e, w, h, len * 0.25f)
        val far = gaussF(e, w, h, len)
        val fscale = len * 0.9f
        val rs = seed and 0xFFFF
        val out = IntArray(n)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val i = y * w + x
                // creste del rumore, deformate da un secondo rumore: scariche ramificate
                val wx = (FxRand.noise(x / (fscale * 3f), y / (fscale * 3f), rs + 7) - 0.5f) * 2.5f
                val nz = FxRand.fbm(x / fscale + wx, y / fscale - wx, rs, 3)
                val ridge = (1f - abs(2f * nz - 1f)).pow(6)
                val streak = 1f - fil + fil * ridge * 2.6f
                val core = e[i]
                val mid = (near[i] * 2.4f).coerceAtMost(1.2f)
                val outer = (far[i] * 4.5f).coerceAtMost(1.4f) * streak
                val src = img.px[i]
                var r = Px.r(src) * (1f - dark); var g = Px.g(src) * (1f - dark); var b = Px.b(src) * (1f - dark)
                val ko = outer * glow; val km = mid * glow; val kc = core * glow * 0.85f
                r += Px.r(col2) * ko + Px.r(col) * km + 255f * kc
                g += Px.g(col2) * ko + Px.g(col) * km + 255f * kc
                b += Px.b(col2) * ko + Px.b(col) * km + 255f * kc
                // compressione morbida delle luci, così il nucleo resta bianco senza bande piatte
                fun soft(v: Float) = if (v <= 200f) v else 200f + 55f * (1f - exp(-(v - 200f) / 55f))
                out[i] = keepAlpha(src, Px.argbF(255, soft(r), soft(g), soft(b)))
            }
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Alone da pellicola

    /**
     * Le luci oltre threshold vengono sfocate (radius u) e tolte dove sono già piene: resta un bordo di colore
     * color attorno a lampioni, finestre e riflessi, sommato in schermo con forza amount.
     */
    fun halation(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h; val n = w * h
        val thr = a.num("threshold", 72f) / 100f
        val radius = (a.num("radius", 1.5f) * img.unit).coerceAtLeast(1f)
        val amount = a.num("amount", 70f) / 100f
        val col = a.color("color", 0xFFFF4A1C.toInt())
        val m = FloatArray(n) { smoothstep(thr, min(1f, thr + 0.2f), Px.luma(img.px[it]) / 255f) }
        val b1 = gaussF(m, w, h, radius)
        val b2 = gaussF(m, w, h, radius * 3f)
        val cr = Px.r(col) / 255f; val cg = Px.g(col) / 255f; val cb = Px.b(col) / 255f
        val out = IntArray(n)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val i = y * w + x
                val halo = ((b1[i] * 1.4f + b2[i] * 0.7f) - m[i] * 0.8f).coerceIn(0f, 1f) * amount * 1.5f
                val src = img.px[i]
                val k = halo.coerceAtMost(1f)
                val r = 255f - (255f - Px.r(src)) * (1f - cr * k)
                val g = 255f - (255f - Px.g(src)) * (1f - cg * k)
                val b = 255f - (255f - Px.b(src)) * (1f - cb * k)
                out[i] = keepAlpha(src, Px.argbF(255, r, g, b))
            }
        }
        return FxImage(w, h, out)
    }
}
