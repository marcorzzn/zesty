package com.example.fx

import kotlin.math.*

/**
 * Zesty FX — buffer immagine e funzioni di base (sfocature, campionamento, rumore, fusioni).
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
        boxVertical(tmp, dst, w, h, r)
        return dst
    }

    /**
     * Passata verticale riga per riga con un accumulatore per colonna: la memoria si legge in ordine.
     * Leggere a colonne (salti di w pixel) svuota la cache del processore e costa ~20 volte di più.
     */
    private fun boxVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val win = 2 * r + 1
        val inv = (1 shl 16) / win
        val half = 1 shl 15
        val last = h - 1
        val sa = IntArray(w); val sr = IntArray(w); val sg = IntArray(w); val sb = IntArray(w)
        for (i in -r..r) {
            val row = (if (i < 0) 0 else if (i > last) last else i) * w
            for (x in 0 until w) {
                val c = src[row + x]
                sa[x] += (c ushr 24) and 0xFF; sr[x] += (c shr 16) and 0xFF; sg[x] += (c shr 8) and 0xFF; sb[x] += c and 0xFF
            }
        }
        for (y in 0 until h) {
            val o = y * w
            val rowOut = (if (y - r < 0) 0 else y - r) * w
            val rowIn = (if (y + r + 1 > last) last else y + r + 1) * w
            for (x in 0 until w) {
                dst[o + x] = (((sa[x] * inv + half) ushr 16) shl 24) or (((sr[x] * inv + half) ushr 16) shl 16) or
                        (((sg[x] * inv + half) ushr 16) shl 8) or ((sb[x] * inv + half) ushr 16)
                val cOut = src[rowOut + x]; val cIn = src[rowIn + x]
                sa[x] += ((cIn ushr 24) and 0xFF) - ((cOut ushr 24) and 0xFF)
                sr[x] += ((cIn shr 16) and 0xFF) - ((cOut shr 16) and 0xFF)
                sg[x] += ((cIn shr 8) and 0xFF) - ((cOut shr 8) and 0xFF)
                sb[x] += (cIn and 0xFF) - (cOut and 0xFF)
            }
        }
    }

    /**
     * Una passata di media scorrevole lungo righe o colonne.
     * Indici calcolati a passo fisso (niente funzioni per pixel) e divisione sostituita da
     * moltiplicazione per il reciproco in virgola fissa: è il punto più usato del motore.
     */
    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val win = 2 * r + 1
        val inv = (1 shl 16) / win // reciproco in virgola fissa 16.16
        val half = 1 shl 15
        val last = len - 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val k = if (i < 0) 0 else if (i > last) last else i
                val c = src[base + k * step]
                sa += (c ushr 24) and 0xFF; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            var o = base
            for (i in 0 until len) {
                dst[o] = (((sa * inv + half) ushr 16) shl 24) or (((sr * inv + half) ushr 16) shl 16) or
                        (((sg * inv + half) ushr 16) shl 8) or ((sb * inv + half) ushr 16)
                val ko = i - r; val ki = i + r + 1
                val cOut = src[base + (if (ko < 0) 0 else ko) * step]
                val cIn = src[base + (if (ki > last) last else ki) * step]
                sa += ((cIn ushr 24) and 0xFF) - ((cOut ushr 24) and 0xFF)
                sr += ((cIn shr 16) and 0xFF) - ((cOut shr 16) and 0xFF)
                sg += ((cIn shr 8) and 0xFF) - ((cOut shr 8) and 0xFF)
                sb += (cIn and 0xFF) - (cOut and 0xFF)
                o += step
            }
        }
    }

    /** Approssimazione gaussiana con tre passate box (sigma in pixel). */
    fun gaussian(src: IntArray, w: Int, h: Int, sigma: Float): IntArray {
        if (sigma < 0.5f) return src.copyOf()
        // piramide: con sigma grande i dettagli fini spariscono comunque, quindi si sfoca una copia ridotta
        // (2× o 4×) e la si riporta a piena dimensione. Risultato visivamente uguale, 4–16 volte più veloce.
        val f = when { sigma >= 16f -> 4; sigma >= 6f -> 2; else -> 1 }
        if (f > 1 && w >= f * 8 && h >= f * 8) {
            val sw = w / f; val sh = h / f
            val small = downsample(src, w, h, f, sw, sh)
            val blurred = gaussianDirect(small, sw, sh, sigma / f)
            return upsample(blurred, sw, sh, w, h)
        }
        return gaussianDirect(src, w, h, sigma)
    }

    private fun gaussianDirect(src: IntArray, w: Int, h: Int, sigma: Float): IntArray {
        if (sigma < 0.5f) return src.copyOf()
        var cur = src
        for (r in boxesForGauss(sigma, 3)) cur = box(cur, w, h, r)
        return cur
    }

    /** Riduzione con media di blocchi f×f (nessun aliasing). */
    fun downsample(src: IntArray, w: Int, h: Int, f: Int, sw: Int, sh: Int): IntArray {
        val out = IntArray(sw * sh)
        val n = f * f
        for (y in 0 until sh) for (x in 0 until sw) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (dy in 0 until f) {
                val row = (y * f + dy) * w + x * f
                for (dx in 0 until f) {
                    val c = src[row + dx]
                    a += (c ushr 24) and 0xFF; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                }
            }
            out[y * sw + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        return out
    }

    /** Ingrandimento bilineare (centri dei pixel allineati). */
    fun upsample(src: IntArray, sw: Int, sh: Int, w: Int, h: Int): IntArray {
        val img = FxImage(sw, sh, src)
        val out = IntArray(w * h)
        val kx = sw.toFloat() / w; val ky = sh.toFloat() / h
        for (y in 0 until h) {
            val sy = (y + 0.5f) * ky - 0.5f
            for (x in 0 until w) out[y * w + x] = FxSample.bilinear(img, (x + 0.5f) * kx - 0.5f, sy)
        }
        return out
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
                tmp[row + x] = s * (1f / win)
                s += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
        val acc = FloatArray(w)
        val invW = 1f / win
        for (i in -r..r) { val row = i.coerceIn(0, h - 1) * w; for (x in 0 until w) acc[x] += tmp[row + x] }
        for (y in 0 until h) {
            val o = y * w
            val rowOut = (y - r).coerceAtLeast(0) * w
            val rowIn = (y + r + 1).coerceAtMost(h - 1) * w
            for (x in 0 until w) {
                dst[o + x] = acc[x] * invW
                acc[x] += tmp[rowIn + x] - tmp[rowOut + x]
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
        // pesi in virgola fissa (0..256): solo moltiplicazioni intere
        val tx = ((fx - x0) * 256f).toInt(); val ty = ((fy - y0) * 256f).toInt()
        val p = img.px
        val c00 = p[y0 * w + x0]; val c10 = p[y0 * w + x1]; val c01 = p[y1 * w + x0]; val c11 = p[y1 * w + x1]
        val w00 = (256 - tx) * (256 - ty); val w10 = tx * (256 - ty); val w01 = (256 - tx) * ty; val w11 = tx * ty
        // un canale alla volta: la somma dei pesi è 65536, quindi due canali nello stesso intero si sovrapporrebbero
        // (era la causa dei puntini gialli e blu su cornici e deformazioni)
        fun ch(sh: Int): Int = ((((c00 ushr sh) and 0xFF) * w00 + ((c10 ushr sh) and 0xFF) * w10 +
            ((c01 ushr sh) and 0xFF) * w01 + ((c11 ushr sh) and 0xFF) * w11 + 32768) ushr 16)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
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

    /** Tabelle 256×256 (base, top) -> risultato per le modalità separabili, create alla prima richiesta. */
    private val luts = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val SEPARABLE = setOf("multiply", "screen", "overlay", "soft-light", "hard-light", "darken", "lighten",
        "color-dodge", "color-burn", "difference", "exclusion", "add", "subtract")

    private fun lut(mode: String): ByteArray = luts.getOrPut(mode) {
        ByteArray(65536) { i -> (ch(mode, (i ushr 8) / 255f, (i and 0xFF) / 255f) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte() }
    }

    // ---- modalità non separabili: formule W3C (le stesse di CSS mix-blend-mode, quindi uguali nella web app) ----
    private fun lum(r: Float, g: Float, b: Float) = 0.3f * r + 0.59f * g + 0.11f * b

    private fun setLumClip(r0: Float, g0: Float, b0: Float, l: Float): Int {
        val d = l - lum(r0, g0, b0)
        var r = r0 + d; var g = g0 + d; var b = b0 + d
        val ll = lum(r, g, b)
        val n = min(r, min(g, b)); val x = max(r, max(g, b))
        if (n < 0f) { val k = ll / (ll - n).coerceAtLeast(1e-6f); r = ll + (r - ll) * k; g = ll + (g - ll) * k; b = ll + (b - ll) * k }
        if (x > 255f) { val k = (255f - ll) / (x - ll).coerceAtLeast(1e-6f); r = ll + (r - ll) * k; g = ll + (g - ll) * k; b = ll + (b - ll) * k }
        return Px.argbF(255, r, g, b)
    }

    /** Porta la saturazione (max-min) di (r,g,b) al valore s mantenendo l'ordine dei canali. */
    private fun setSat(r: Float, g: Float, b: Float, s: Float, out: FloatArray) {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val span = mx - mn
        fun f(c: Float) = if (span > 1e-6f) (c - mn) * s / span else 0f
        out[0] = f(r); out[1] = f(g); out[2] = f(b)
    }

    private val satTmp = ThreadLocal.withInitial { FloatArray(3) }

    private fun nonSeparable(mode: String, base: Int, top: Int): Int {
        val br = Px.r(base).toFloat(); val bg = Px.g(base).toFloat(); val bb = Px.b(base).toFloat()
        val tr = Px.r(top).toFloat(); val tg = Px.g(top).toFloat(); val tb = Px.b(top).toFloat()
        return when (mode) {
            "color" -> setLumClip(tr, tg, tb, lum(br, bg, bb))
            "luminosity" -> setLumClip(br, bg, bb, lum(tr, tg, tb))
            "hue" -> { val o = satTmp.get()!!; setSat(tr, tg, tb, max(br, max(bg, bb)) - min(br, min(bg, bb)), o); setLumClip(o[0], o[1], o[2], lum(br, bg, bb)) }
            else -> { val o = satTmp.get()!!; setSat(br, bg, bb, max(tr, max(tg, tb)) - min(tr, min(tg, tb)), o); setLumClip(o[0], o[1], o[2], lum(br, bg, bb)) }
        }
    }

    private val tmpA = ThreadLocal.withInitial { FloatArray(3) }
    private val tmpB = ThreadLocal.withInitial { FloatArray(3) }

    /** Fonde top su base con l'opacità indicata (0..1), rispettando l'alpha del top. */
    fun pixel(mode: String, base: Int, top: Int, opacity: Float): Int {
        val ta = Px.a(top) / 255f * opacity
        if (ta <= 0f) return base
        val res: Int = when (mode) {
            "normal" -> top
            "hue", "saturation", "color", "luminosity" -> nonSeparable(mode, base, top)
            else -> {
                val t = lut(normalize(mode))
                Px.argb(
                    255,
                    t[(Px.r(base) shl 8) or Px.r(top)].toInt() and 0xFF,
                    t[(Px.g(base) shl 8) or Px.g(top)].toInt() and 0xFF,
                    t[(Px.b(base) shl 8) or Px.b(top)].toInt() and 0xFF
                )
            }
        }
        val out = Px.mix(base, (res and 0x00FFFFFF) or (Px.a(base) shl 24), ta)
        return out
    }

    fun image(mode: String, base: FxImage, top: FxImage, opacity: Float): FxImage {
        val m = normalize(mode)
        val out = IntArray(base.size)
        blendInto(m, base.px, top.px, null, opacity, out)
        return FxImage(base.w, base.h, out)
    }

    /**
     * Fonde un'intera immagine. Le modalità separabili usano la tabella precalcolata e aritmetica intera;
     * mask (0..1, facoltativa) modula l'opacità pixel per pixel. La modalità deve essere già normalizzata.
     */
    fun blendInto(mode: String, bp: IntArray, tp: IntArray, mask: FloatArray?, opacity: Float, out: IntArray) {
        if (mode in SEPARABLE) {
            val t = lut(mode)
            val op256 = (opacity.coerceIn(0f, 1f) * 256f).toInt()
            for (i in out.indices) {
                val b = bp[i]; val c = tp[i]
                val k = if (mask == null) op256 else (mask[i] * op256).toInt()
                val a = (((c ushr 24) and 0xFF) * k) ushr 8 // 0..256
                if (a <= 0) { out[i] = b; continue }
                val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
                val rr = t[(br shl 8) or ((c shr 16) and 0xFF)].toInt() and 0xFF
                val rg = t[(bg shl 8) or ((c shr 8) and 0xFF)].toInt() and 0xFF
                val rb = t[(bb shl 8) or (c and 0xFF)].toInt() and 0xFF
                out[i] = (b and 0xFF000000.toInt()) or
                        ((br + (((rr - br) * a) shr 8)) shl 16) or ((bg + (((rg - bg) * a) shr 8)) shl 8) or (bb + (((rb - bb) * a) shr 8))
            }
        } else {
            for (i in out.indices) out[i] = pixel(mode, bp[i], tp[i], if (mask == null) opacity else mask[i] * opacity)
        }
    }
}
