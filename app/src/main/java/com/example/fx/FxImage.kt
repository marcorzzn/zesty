package com.example.fx

import kotlin.math.*

/**
 * Pixart FX — buffer immagine e funzioni di base (sfocature, campionamento, rumore, fusioni).
 * I pixel sono ARGB 8 bit non premoltiplicati, come restituiti da Bitmap.getPixels().
 */
class FxImage(val w: Int, val h: Int, val px: IntArray = IntArray(w * h)) {
    val size get() = w * h
    /** Lato corto: base delle unità spaziali "u" (1u = 1% del lato corto). */
    val unit get() = min(w, h) / 100f
    fun copy() = FxImage(w, h, px.copyOf())
}

@Suppress("NOTHING_TO_INLINE")
object Px {
    inline fun a(c: Int) = (c ushr 24) and 0xFF
    inline fun r(c: Int) = (c shr 16) and 0xFF
    inline fun g(c: Int) = (c shr 8) and 0xFF
    inline fun b(c: Int) = c and 0xFF
    inline fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
    inline fun clampF(v: Float) = if (v < 0f) 0 else if (v > 255f) 255 else (v + 0.5f).toInt()
    inline fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    inline fun argbF(a: Int, r: Float, g: Float, b: Float) = argb(a, clampF(r), clampF(g), clampF(b))
    /** Luminanza Rec.709 (0..255). */
    inline fun luma(c: Int) = (r(c) * 0.2126f + g(c) * 0.7152f + b(c) * 0.0722f)
    inline fun lumaRGB(r: Float, g: Float, b: Float) = r * 0.2126f + g * 0.7152f + b * 0.0722f
    inline fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    inline fun smooth(e0: Float, e1: Float, x: Float): Float {
        if (e1 == e0) return if (x < e0) 0f else 1f
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    fun mix(c0: Int, c1: Int, t: Float): Int {
        if (t <= 0f) return c0
        if (t >= 1f) return c1
        return argb(
            (a(c0) + (a(c1) - a(c0)) * t + 0.5f).toInt(),
            (r(c0) + (r(c1) - r(c0)) * t + 0.5f).toInt(),
            (g(c0) + (g(c1) - g(c0)) * t + 0.5f).toInt(),
            (b(c0) + (b(c1) - b(c0)) * t + 0.5f).toInt()
        )
    }
}

/** RGB <-> HSL (h 0..360, s,l 0..1) usato da tinte, splash e sostituzione colore. */
object Hsl {
    fun fromRgb(r: Int, g: Int, b: Int, out: FloatArray) {
        val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
        val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf))
        val l = (mx + mn) / 2f
        var h = 0f; var s = 0f
        val d = mx - mn
        if (d > 1e-6f) {
            s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            h = when (mx) {
                rf -> ((gf - bf) / d + (if (gf < bf) 6f else 0f))
                gf -> ((bf - rf) / d + 2f)
                else -> ((rf - gf) / d + 4f)
            } * 60f
        }
        out[0] = h; out[1] = s; out[2] = l
    }

    private fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    fun toRgb(a: Int, hIn: Float, s: Float, l: Float): Int {
        val h = (((hIn % 360f) + 360f) % 360f) / 360f
        if (s <= 1e-6f) { val v = Px.clampF(l * 255f); return Px.argb(a, v, v, v) }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        return Px.argbF(a, hue2rgb(p, q, h + 1f / 3f) * 255f, hue2rgb(p, q, h) * 255f, hue2rgb(p, q, h - 1f / 3f) * 255f)
    }
}

/** Generatore pseudo-casuale deterministico (identico in JS con Math.imul). */
object FxRand {
    fun hash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }
    /** 0..1 */
    fun r01(x: Int, y: Int, seed: Int): Float = (hash(x, y, seed) ushr 8) / 16777216f

    /** Value noise 2D liscio (0..1). */
    fun noise(x: Float, y: Float, seed: Int): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val u = xf * xf * (3f - 2f * xf); val v = yf * yf * (3f - 2f * yf)
        val a = r01(xi, yi, seed); val b = r01(xi + 1, yi, seed)
        val c = r01(xi, yi + 1, seed); val d = r01(xi + 1, yi + 1, seed)
        return Px.lerp(Px.lerp(a, b, u), Px.lerp(c, d, u), v)
    }

    /** Rumore frattale a più ottave (0..1). */
    fun fbm(x: Float, y: Float, seed: Int, octaves: Int = 4): Float {
        var sum = 0f; var amp = 0.5f; var f = 1f; var norm = 0f
        for (o in 0 until octaves) {
            sum += noise(x * f, y * f, seed + o * 31) * amp
            norm += amp; amp *= 0.5f; f *= 2f
        }
        return sum / norm
    }
}

object FxBlur {
    /** Sfocatura box separabile su ARGB impacchettato (raggio in pixel). */
    fun box(src: IntArray, w: Int, h: Int, r: Int, dst: IntArray = IntArray(w * h)): IntArray {
        if (r < 1) { System.arraycopy(src, 0, dst, 0, src.size); return dst }
        val tmp = IntArray(w * h)
        boxPass(src, tmp, w, h, r, true)
        boxPass(tmp, dst, w, h, r, false)
        return dst
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val win = 2 * r + 1
        for (line in 0 until lines) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            fun idx(i: Int): Int {
                val k = if (i < 0) 0 else if (i >= len) len - 1 else i
                return if (horizontal) line * w + k else k * w + line
            }
            for (i in -r..r) {
                val c = src[idx(i)]
                sa += (c ushr 24) and 0xFF; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = Px.argb(sa / win, sr / win, sg / win, sb / win)
                val cOut = src[idx(i - r)]
                val cIn = src[idx(i + r + 1)]
                sa += ((cIn ushr 24) and 0xFF) - ((cOut ushr 24) and 0xFF)
                sr += ((cIn shr 16) and 0xFF) - ((cOut shr 16) and 0xFF)
                sg += ((cIn shr 8) and 0xFF) - ((cOut shr 8) and 0xFF)
                sb += (cIn and 0xFF) - (cOut and 0xFF)
            }
        }
    }

    /** Approssimazione gaussiana con tre passate box (sigma in pixel). */
    fun gaussian(src: IntArray, w: Int, h: Int, sigma: Float): IntArray {
        if (sigma < 0.5f) return src.copyOf()
        val radii = boxesForGauss(sigma, 3)
        var cur = src
        for (r in radii) cur = box(cur, w, h, r)
        return cur
    }

    private fun boxesForGauss(sigma: Float, n: Int): IntArray {
        val wIdeal = sqrt((12f * sigma * sigma / n) + 1f)
        var wl = floor(wIdeal).toInt(); if (wl % 2 == 0) wl--
        val wu = wl + 2
        val mIdeal = (12f * sigma * sigma - n * wl * wl - 4f * n * wl - 3f * n) / (-4f * wl - 4f)
        val m = mIdeal.roundToInt()
        return IntArray(n) { i -> ((if (i < m) wl else wu) - 1) / 2 }
    }

    /** Media box su canale float (per il filtro guidato). */
    fun boxF(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r < 1) return src.copyOf()
        val tmp = FloatArray(w * h); val dst = FloatArray(w * h)
        val win = (2 * r + 1).toFloat()
        for (y in 0 until h) {
            var s = 0f
            val row = y * w
            for (i in -r..r) s += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = s / win
                s += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var s = 0f
            for (i in -r..r) s += tmp[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = s / win
                s += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return dst
    }
}

object FxSample {
    /** Campionamento bilineare con bordo bloccato. */
    fun bilinear(img: FxImage, x: Float, y: Float): Int {
        val w = img.w; val h = img.h
        val fx = x.coerceIn(0f, (w - 1).toFloat()); val fy = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val tx = fx - x0; val ty = fy - y0
        val p = img.px
        val top = Px.mix(p[y0 * w + x0], p[y0 * w + x1], tx)
        val bot = Px.mix(p[y1 * w + x0], p[y1 * w + x1], tx)
        return Px.mix(top, bot, ty)
    }
}

/** Metodi di fusione implementati direttamente (non dipendono da PorterDuff). */
object FxBlend {
    val MODES = listOf(
        "normal", "multiply", "screen", "overlay", "soft-light", "hard-light", "darken", "lighten",
        "color-dodge", "color-burn", "difference", "exclusion", "add", "subtract",
        "hue", "saturation", "color", "luminosity"
    )

    fun normalize(mode: String): String {
        val m = mode.lowercase().replace("_", "-").replace(" ", "-")
        return when (m) {
            "softlight" -> "soft-light"
            "hardlight" -> "hard-light"
            "colordodge" -> "color-dodge"
            "colorburn" -> "color-burn"
            "source-over" -> "normal"
            else -> if (m in MODES) m else "normal"
        }
    }

    private fun ch(mode: String, a: Float, b: Float): Float = when (mode) { // a = base, b = top (0..1)
        "multiply" -> a * b
        "screen" -> 1f - (1f - a) * (1f - b)
        "overlay" -> if (a < 0.5f) 2f * a * b else 1f - 2f * (1f - a) * (1f - b)
        "hard-light" -> if (b < 0.5f) 2f * a * b else 1f - 2f * (1f - a) * (1f - b)
        "soft-light" -> if (b <= 0.5f) a - (1f - 2f * b) * a * (1f - a)
                        else a + (2f * b - 1f) * ((if (a <= 0.25f) ((16f * a - 12f) * a + 4f) * a else sqrt(a)) - a)
        "darken" -> min(a, b)
        "lighten" -> max(a, b)
        "color-dodge" -> if (b >= 1f) 1f else min(1f, a / (1f - b))
        "color-burn" -> if (b <= 0f) 0f else 1f - min(1f, (1f - a) / b)
        "difference" -> abs(a - b)
        "exclusion" -> a + b - 2f * a * b
        "add" -> min(1f, a + b)
        "subtract" -> max(0f, a - b)
        else -> b
    }

    private val tmpA = ThreadLocal.withInitial { FloatArray(3) }
    private val tmpB = ThreadLocal.withInitial { FloatArray(3) }

    /** Fonde top su base con l'opacità indicata (0..1), rispettando l'alpha del top. */
    fun pixel(mode: String, base: Int, top: Int, opacity: Float): Int {
        val ta = Px.a(top) / 255f * opacity
        if (ta <= 0f) return base
        val res: Int = when (mode) {
            "normal" -> top
            "hue", "saturation", "color", "luminosity" -> {
                val hb = tmpA.get()!!; val ht = tmpB.get()!!
                Hsl.fromRgb(Px.r(base), Px.g(base), Px.b(base), hb)
                Hsl.fromRgb(Px.r(top), Px.g(top), Px.b(top), ht)
                when (mode) {
                    "hue" -> Hsl.toRgb(255, ht[0], hb[1], hb[2])
                    "saturation" -> Hsl.toRgb(255, hb[0], ht[1], hb[2])
                    "color" -> Hsl.toRgb(255, ht[0], ht[1], hb[2])
                    else -> { // luminosity: preserva tinta e saturazione della base, luminanza del top
                        val d = Px.luma(top) - Px.luma(base)
                        Px.argbF(255, Px.r(base) + d, Px.g(base) + d, Px.b(base) + d)
                    }
                }
            }
            else -> Px.argbF(
                255,
                ch(mode, Px.r(base) / 255f, Px.r(top) / 255f) * 255f,
                ch(mode, Px.g(base) / 255f, Px.g(top) / 255f) * 255f,
                ch(mode, Px.b(base) / 255f, Px.b(top) / 255f) * 255f
            )
        }
        val out = Px.mix(base, (res and 0x00FFFFFF) or (Px.a(base) shl 24), ta)
        return out
    }

    fun image(mode: String, base: FxImage, top: FxImage, opacity: Float): FxImage {
        val m = normalize(mode)
        val out = IntArray(base.size)
        val bp = base.px; val tp = top.px
        for (i in out.indices) out[i] = pixel(m, bp[i], tp[i], opacity)
        return FxImage(base.w, base.h, out)
    }
}
