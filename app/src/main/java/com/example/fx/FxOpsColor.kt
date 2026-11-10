package com.example.fx

import kotlin.math.*

fun interface FxOp {
    fun run(img: FxImage, a: FxArgs, ctx: FxContext): FxImage
}

class FxContext(val seed: Int, val runSteps: (FxImage, org.json.JSONArray) -> FxImage)

/** Applica una funzione per pixel conservando l'alpha. */
internal inline fun FxImage.mapPixels(crossinline f: (Int) -> Int): FxImage {
    val out = IntArray(size)
    val p = px
    for (i in out.indices) {
        val c = p[i]
        out[i] = if ((c ushr 24) == 0) c else (f(c) and 0x00FFFFFF) or (c and 0xFF000000.toInt())
    }
    return FxImage(w, h, out)
}

/** Tabella 256 valori per canale. */
internal fun FxImage.applyLut(lr: IntArray, lg: IntArray = lr, lb: IntArray = lr): FxImage =
    mapPixels { c -> Px.argb(255, lr[Px.r(c)], lg[Px.g(c)], lb[Px.b(c)]) }

/**
 * Operazioni sul colore e sul tono (per pixel).
 */
object FxOpsColor {

    val ops: Map<String, FxOp> = mapOf(
        "adjust" to FxOp { img, a, _ -> adjust(img, a) },
        "matrix" to FxOp { img, a, _ -> matrix(img, a.nums("m")) },
        "mono" to FxOp { img, a, _ -> mono(img, a) },
        "curves" to FxOp { img, a, _ -> curves(img, a) },
        "levels" to FxOp { img, a, _ -> levels(img, a) },
        "gradient_map" to FxOp { img, a, _ -> gradientMap(img, a.stops("stops")) },
        "split_tone" to FxOp { img, a, _ -> splitTone(img, a) },
        "tint" to FxOp { img, a, _ -> tint(img, a) },
        "invert" to FxOp { img, _, _ -> img.mapPixels { c -> c.inv() } },
        "solarize" to FxOp { img, a, _ -> solarize(img, a.num("threshold", 128f)) },
        "posterize" to FxOp { img, a, _ -> posterize(img, a.int("levels", 5)) },
        "threshold" to FxOp { img, a, _ -> threshold(img, a) },
        "color_keep" to FxOp { img, a, _ -> colorKeep(img, a) },
        "hue_replace" to FxOp { img, a, _ -> hueReplace(img, a) },
        "channel_swap" to FxOp { img, a, _ -> channelSwap(img, a.str("order", "rgb")) },
        "auto_levels" to FxOp { img, a, _ -> autoLevels(img, a) },
        "white_balance" to FxOp { img, a, _ -> whiteBalance(img, a.num("amount", 100f) / 100f) },
        "fill" to FxOp { img, a, _ -> val c = a.color("color", 0xFF000000.toInt()); FxImage(img.w, img.h, IntArray(img.size) { c }) },
        "dither" to FxOp { img, a, _ -> dither(img, a) },
        "duotone" to FxOp { img, a, _ ->
            gradientMap(img, listOf(0f to a.color("dark", 0xFF101030.toInt()), 1f to a.color("light", 0xFFFFE0A0.toInt())))
        }
    )

    // ---------------------------------------------------------------- adjust
    fun adjust(img: FxImage, a: FxArgs): FxImage {
        val exposure = a.num("exposure", 0f) / 100f
        val brightness = a.num("brightness", 0f) / 100f
        val contrast = a.num("contrast", 0f) / 100f
        val saturation = a.num("saturation", 0f) / 100f
        val vibrance = a.num("vibrance", 0f) / 100f
        val temperature = a.num("temperature", 0f) / 100f
        val tint = a.num("tint", 0f) / 100f
        val hue = a.num("hue", 0f)
        val shadows = a.num("shadows", 0f) / 100f
        val highlights = a.num("highlights", 0f) / 100f
        val whites = a.num("whites", 0f) / 100f
        val blacks = a.num("blacks", 0f) / 100f
        val gamma = a.num("gamma", 1f).coerceIn(0.1f, 10f)
        val fade = a.num("fade", 0f) / 100f

        val expMul = 2f.pow(exposure * 2f)
        val cf = if (contrast >= 0f) 1f + contrast * 1.5f else 1f + contrast
        val hm = hueMatrix(hue)
        val invGamma = 1f / gamma
        val lift = fade * 70f

        return img.mapPixels { c ->
            var r = Px.r(c).toFloat(); var g = Px.g(c).toFloat(); var b = Px.b(c).toFloat()
            if (exposure != 0f) { r *= expMul; g *= expMul; b *= expMul }
            if (temperature != 0f) { r += temperature * 32f; b -= temperature * 32f; g += temperature * 4f }
            if (tint != 0f) { g -= tint * 26f; r += tint * 8f; b += tint * 8f }
            if (brightness != 0f) {
                // luminosità percettiva: comprime verso il bianco o il nero senza appiattire
                if (brightness > 0f) { r += (255f - r) * brightness * 0.6f; g += (255f - g) * brightness * 0.6f; b += (255f - b) * brightness * 0.6f }
                else { val k = 1f + brightness * 0.6f; r *= k; g *= k; b *= k }
            }
            if (shadows != 0f || highlights != 0f || whites != 0f || blacks != 0f) {
                val l = (Px.lumaRGB(r, g, b) / 255f).coerceIn(0f, 1f)
                val ws = (1f - l).let { it * it }
                val wh = l * l
                val wb = (1f - l).let { it * it * it * it }
                val ww = wh * wh
                val d = shadows * 90f * ws + highlights * 90f * wh + blacks * 60f * wb + whites * 60f * ww
                r += d; g += d; b += d
            }
            if (contrast != 0f) { r = (r - 128f) * cf + 128f; g = (g - 128f) * cf + 128f; b = (b - 128f) * cf + 128f }
            if (gamma != 1f) {
                r = 255f * (r.coerceIn(0f, 255f) / 255f).pow(invGamma)
                g = 255f * (g.coerceIn(0f, 255f) / 255f).pow(invGamma)
                b = 255f * (b.coerceIn(0f, 255f) / 255f).pow(invGamma)
            }
            if (hue != 0f) {
                val nr = r * hm[0] + g * hm[1] + b * hm[2]
                val ng = r * hm[3] + g * hm[4] + b * hm[5]
                val nb = r * hm[6] + g * hm[7] + b * hm[8]
                r = nr; g = ng; b = nb
            }
            if (saturation != 0f || vibrance != 0f) {
                val l = Px.lumaRGB(r, g, b)
                var k = 1f + saturation
                if (vibrance != 0f) {
                    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                    val sat = ((mx - mn) / 255f).coerceIn(0f, 1f)
                    k *= 1f + vibrance * (1f - sat) * 1.4f
                }
                r = l + (r - l) * k; g = l + (g - l) * k; b = l + (b - l) * k
            }
            if (fade != 0f) {
                r = lift + r * (255f - lift * 1.3f) / 255f
                g = lift + g * (255f - lift * 1.3f) / 255f
                b = lift + b * (255f - lift * 1.3f) / 255f
            }
            Px.argbF(255, r, g, b)
        }
    }

    /** Rotazione della tinta attorno all'asse dei grigi (preserva la luminanza). */
    fun hueMatrix(deg: Float): FloatArray {
        val rad = Math.toRadians(deg.toDouble())
        val cs = cos(rad).toFloat(); val sn = sin(rad).toFloat()
        val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
        return floatArrayOf(
            lr + cs * (1 - lr) + sn * (-lr), lg + cs * (-lg) + sn * (-lg), lb + cs * (-lb) + sn * (1 - lb),
            lr + cs * (-lr) + sn * 0.143f, lg + cs * (1 - lg) + sn * 0.140f, lb + cs * (-lb) + sn * (-0.283f),
            lr + cs * (-lr) + sn * (-(1 - lr)), lg + cs * (-lg) + sn * lg, lb + cs * (1 - lb) + sn * lb
        )
    }

    fun matrix(img: FxImage, m: List<Float>): FxImage {
        if (m.size < 15) return img
        val o = { i: Int -> if (i < m.size) m[i] else 0f }
        return img.mapPixels { c ->
            val r = Px.r(c).toFloat(); val g = Px.g(c).toFloat(); val b = Px.b(c).toFloat()
            Px.argbF(
                255,
                r * o(0) + g * o(1) + b * o(2) + o(4),
                r * o(5) + g * o(6) + b * o(7) + o(9),
                r * o(10) + g * o(11) + b * o(12) + o(14)
            )
        }
    }

    // ---------------------------------------------------------------- mono
    private val BAND_HUES = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 280f, 320f)
    private val BAND_KEYS = listOf("reds", "oranges", "yellows", "greens", "cyans", "blues", "purples", "magentas")

    fun mono(img: FxImage, a: FxArgs): FxImage {
        val wr = a.num("r", 0.2126f); val wg = a.num("g", 0.7152f); val wb = a.num("b", 0.0722f)
        val sum = (wr + wg + wb).let { if (abs(it) < 1e-4f) 1f else it }
        val bands = FloatArray(8) { a.num(BAND_KEYS[it], 0f) / 100f }
        val useBands = bands.any { it != 0f }
        val hsl = FloatArray(3)
        return img.mapPixels { c ->
            val r = Px.r(c); val g = Px.g(c); val b = Px.b(c)
            var l = (r * wr + g * wg + b * wb) / sum
            if (useBands) {
                Hsl.fromRgb(r, g, b, hsl)
                val adj = bandWeight(hsl[0], bands)
                l *= 1f + adj * hsl[1] * 1.2f
            }
            val v = Px.clampF(l)
            Px.argb(255, v, v, v)
        }
    }

    private fun bandWeight(h: Float, bands: FloatArray): Float {
        for (i in 0 until 8) {
            val h0 = BAND_HUES[i]
            val h1 = if (i == 7) 360f else BAND_HUES[i + 1]
            if (h >= h0 && h < h1) {
                val t = (h - h0) / (h1 - h0)
                return Px.lerp(bands[i], bands[(i + 1) % 8], t)
            }
        }
        return bands[0]
    }

    // ---------------------------------------------------------------- curves / levels
    /** Spline cubica monotona (Fritsch–Carlson): niente oscillazioni fra i punti. */
    fun curveLut(pts: List<Pair<Float, Float>>): IntArray {
        val p = (if (pts.size < 2) listOf(0f to 0f, 255f to 255f) else pts)
        val n = p.size
        val xs = FloatArray(n) { p[it].first }; val ys = FloatArray(n) { p[it].second }
        val d = FloatArray(n - 1) { i -> val dx = xs[i + 1] - xs[i]; if (dx == 0f) 0f else (ys[i + 1] - ys[i]) / dx }
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val al = m[i] / d[i]; val be = m[i + 1] / d[i]
            val s = al * al + be * be
            if (s > 9f) { val t = 3f / sqrt(s); m[i] = t * al * d[i]; m[i + 1] = t * be * d[i] }
        }
        return IntArray(256) { xi ->
            val x = xi.toFloat()
            when {
                x <= xs[0] -> Px.clampF(ys[0])
                x >= xs[n - 1] -> Px.clampF(ys[n - 1])
                else -> {
                    var k = 0
                    while (k < n - 2 && x > xs[k + 1]) k++
                    val hh = xs[k + 1] - xs[k]
                    val t = if (hh == 0f) 0f else (x - xs[k]) / hh
                    val t2 = t * t; val t3 = t2 * t
                    val y = (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * hh * m[k] +
                            (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * hh * m[k + 1]
                    Px.clampF(y)
                }
            }
        }
    }

    fun curves(img: FxImage, a: FxArgs): FxImage {
        val all = curveLut(a.points("rgb"))
        val lr = curveLut(a.points("r")); val lg = curveLut(a.points("g")); val lb = curveLut(a.points("b"))
        val fr = IntArray(256) { lr[all[it]] }; val fg = IntArray(256) { lg[all[it]] }; val fb = IntArray(256) { lb[all[it]] }
        return img.applyLut(fr, fg, fb)
    }

    fun levels(img: FxImage, a: FxArgs): FxImage {
        val inB = a.num("inBlack", 0f); val inW = a.num("inWhite", 255f).let { if (it <= inB) inB + 1f else it }
        val gm = a.num("gamma", 1f).coerceIn(0.1f, 10f)
        val outB = a.num("outBlack", 0f); val outW = a.num("outWhite", 255f)
        val lut = IntArray(256) { v ->
            val t = ((v - inB) / (inW - inB)).coerceIn(0f, 1f).pow(1f / gm)
            Px.clampF(outB + t * (outW - outB))
        }
        return img.applyLut(lut)
    }

    // ---------------------------------------------------------------- tonal colour
    fun gradientLut(stops: List<Pair<Float, Int>>): IntArray {
        val s = if (stops.isEmpty()) listOf(0f to 0xFF000000.toInt(), 1f to 0xFFFFFFFF.toInt()) else stops
        return IntArray(256) { v ->
            val t = v / 255f
            if (t <= s.first().first) s.first().second
            else if (t >= s.last().first) s.last().second
            else {
                var k = 0
                while (k < s.size - 2 && t > s[k + 1].first) k++
                val span = (s[k + 1].first - s[k].first).let { if (it <= 0f) 1f else it }
                Px.mix(s[k].second, s[k + 1].second, (t - s[k].first) / span)
            }
        }
    }

    fun gradientMap(img: FxImage, stops: List<Pair<Float, Int>>): FxImage {
        val lut = gradientLut(stops)
        return img.mapPixels { c -> lut[Px.clampF(Px.luma(c))] }
    }

    fun splitTone(img: FxImage, a: FxArgs): FxImage {
        val sh = a.color("shadows", 0xFF2060A0.toInt()); val hi = a.color("highlights", 0xFFF0A040.toInt())
        val balance = a.num("balance", 0f) / 100f
        val amount = a.num("amount", 50f) / 100f
        val pivot = 0.5f + balance * 0.35f
        return img.mapPixels { c ->
            val l = Px.luma(c) / 255f
            val wHi = Px.smooth(pivot - 0.35f, pivot + 0.35f, l)
            val col = Px.mix(sh, hi, wHi)
            // tinta "soft": sposta i canali verso il colore in modo proporzionale ai mezzitoni
            val mid = 1f - abs(l - 0.5f) * 1.2f
            val k = amount * (0.35f + 0.65f * mid.coerceIn(0f, 1f))
            Px.argbF(
                255,
                Px.r(c) + (Px.r(col) - 128f) * k,
                Px.g(c) + (Px.g(col) - 128f) * k,
                Px.b(c) + (Px.b(col) - 128f) * k
            )
        }
    }

    fun tint(img: FxImage, a: FxArgs): FxImage {
        val color = a.color("color", 0xFFFFA040.toInt())
        val amount = a.num("amount", 50f) / 100f
        return img.mapPixels { c -> FxBlend.pixel("color", c, color, amount) }
    }

    fun solarize(img: FxImage, t: Float): FxImage {
        val lut = IntArray(256) { if (it > t) 255 - it else it }
        return img.applyLut(lut)
    }

    fun posterize(img: FxImage, levels: Int): FxImage {
        val n = levels.coerceIn(2, 64)
        val lut = IntArray(256) { v -> ((v * (n - 1) / 255f).roundToInt() * 255f / (n - 1)).roundToInt() }
        return img.applyLut(lut)
    }

    fun threshold(img: FxImage, a: FxArgs): FxImage {
        val t = a.num("level", 128f); val soft = a.num("softness", 8f).coerceAtLeast(0.5f)
        val dark = a.color("dark", 0xFF000000.toInt()); val light = a.color("light", 0xFFFFFFFF.toInt())
        return img.mapPixels { c -> Px.mix(dark, light, Px.smooth(t - soft, t + soft, Px.luma(c))) }
    }

    private fun hueDist(h1: Float, h2: Float): Float {
        val d = abs(h1 - h2) % 360f
        return if (d > 180f) 360f - d else d
    }

    fun colorKeep(img: FxImage, a: FxArgs): FxImage {
        val hues = a.nums("hues").ifEmpty { listOf(a.num("hue", 0f)) }
        val range = a.num("range", 25f); val soft = a.num("softness", 15f).coerceAtLeast(0.1f)
        val keepSat = a.num("outside", 0f) / 100f
        val invert = a.bool("invert", false)
        val hsl = FloatArray(3)
        return img.mapPixels { c ->
            Hsl.fromRgb(Px.r(c), Px.g(c), Px.b(c), hsl)
            var w = 0f
            for (h in hues) w = max(w, 1f - Px.smooth(range, range + soft, hueDist(hsl[0], h)))
            w *= Px.smooth(0.08f, 0.2f, hsl[1]) // i grigi non hanno tinta
            if (invert) w = 1f - w
            val l = Px.luma(c)
            val k = keepSat + (1f - keepSat) * w
            Px.argbF(255, l + (Px.r(c) - l) * k, l + (Px.g(c) - l) * k, l + (Px.b(c) - l) * k)
        }
    }

    fun hueReplace(img: FxImage, a: FxArgs): FxImage {
        val from = a.num("from", 0f); val to = a.num("to", 200f)
        val range = a.num("range", 30f); val soft = a.num("softness", 20f).coerceAtLeast(0.1f)
        val satMul = 1f + a.num("saturation", 0f) / 100f
        val hsl = FloatArray(3)
        return img.mapPixels { c ->
            Hsl.fromRgb(Px.r(c), Px.g(c), Px.b(c), hsl)
            val w = (1f - Px.smooth(range, range + soft, hueDist(hsl[0], from))) * Px.smooth(0.05f, 0.15f, hsl[1])
            if (w <= 0f) c else {
                val shifted = Hsl.toRgb(255, to + (hsl[0] - from), (hsl[1] * satMul).coerceIn(0f, 1f), hsl[2])
                Px.mix(c, shifted, w)
            }
        }
    }

    fun channelSwap(img: FxImage, order: String): FxImage {
        val o = order.lowercase().padEnd(3, 'b')
        fun pick(c: Int, ch: Char) = when (ch) { 'r' -> Px.r(c); 'g' -> Px.g(c); else -> Px.b(c) }
        return img.mapPixels { c -> Px.argb(255, pick(c, o[0]), pick(c, o[1]), pick(c, o[2])) }
    }

    private fun histogramBounds(img: FxImage, clip: Float, ch: (Int) -> Int): Pair<Int, Int> {
        val hist = IntArray(256)
        var n = 0
        for (c in img.px) if ((c ushr 24) != 0) { hist[ch(c)]++; n++ }
        if (n == 0) return 0 to 255
        val lim = (n * clip).toInt()
        var lo = 0; var acc = 0
        while (lo < 255 && acc + hist[lo] <= lim) { acc += hist[lo]; lo++ }
        var hi = 255; acc = 0
        while (hi > 0 && acc + hist[hi] <= lim) { acc += hist[hi]; hi-- }
        return if (hi <= lo) 0 to 255 else lo to hi
    }

    fun autoLevels(img: FxImage, a: FxArgs): FxImage {
        val clip = a.num("clip", 0.5f) / 100f
        val perChannel = a.str("mode", "channels") == "channels"
        val amount = a.num("amount", 100f) / 100f
        fun lut(b: Pair<Int, Int>) = IntArray(256) { v ->
            val t = ((v - b.first).toFloat() / (b.second - b.first)).coerceIn(0f, 1f) * 255f
            Px.clampF(v + (t - v) * amount)
        }
        return if (perChannel) {
            img.applyLut(lut(histogramBounds(img, clip) { Px.r(it) }), lut(histogramBounds(img, clip) { Px.g(it) }), lut(histogramBounds(img, clip) { Px.b(it) }))
        } else {
            val l = lut(histogramBounds(img, clip) { Px.clampF(Px.luma(it)) })
            img.applyLut(l)
        }
    }

    fun whiteBalance(img: FxImage, amount: Float): FxImage {
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0
        for (c in img.px) if ((c ushr 24) != 0) { sr += Px.r(c); sg += Px.g(c); sb += Px.b(c); n++ }
        if (n == 0) return img
        val avg = (sr + sg + sb) / (3 * n)
        val kr = (avg / (sr / n).coerceAtLeast(1.0)).toFloat(); val kg = (avg / (sg / n).coerceAtLeast(1.0)).toFloat(); val kb = (avg / (sb / n).coerceAtLeast(1.0)).toFloat()
        val mr = 1f + (kr - 1f) * amount; val mg = 1f + (kg - 1f) * amount; val mb = 1f + (kb - 1f) * amount
        return img.mapPixels { c -> Px.argbF(255, Px.r(c) * mr, Px.g(c) * mg, Px.b(c) * mb) }
    }

    private val BAYER4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun dither(img: FxImage, a: FxArgs): FxImage {
        val levels = a.int("levels", 2).coerceIn(2, 16)
        val mono = a.bool("mono", true)
        val scale = max(1, (a.num("size", 0.3f) * img.unit).roundToInt())
        val step = 255f / (levels - 1)
        val out = IntArray(img.size)
        if (a.str("mode", "bayer") == "floyd") {
            val w = img.w; val h = img.h
            val er = FloatArray(img.size); val eg = FloatArray(img.size); val eb = FloatArray(img.size)
            for (i in 0 until img.size) {
                val c = img.px[i]
                if (mono) { val l = Px.luma(c); er[i] = l; eg[i] = l; eb[i] = l } else { er[i] = Px.r(c).toFloat(); eg[i] = Px.g(c).toFloat(); eb[i] = Px.b(c).toFloat() }
            }
            fun spread(arr: FloatArray, x: Int, y: Int, err: Float) {
                if (x + 1 < w) arr[y * w + x + 1] += err * 7 / 16
                if (y + 1 < h) {
                    if (x > 0) arr[(y + 1) * w + x - 1] += err * 3 / 16
                    arr[(y + 1) * w + x] += err * 5 / 16
                    if (x + 1 < w) arr[(y + 1) * w + x + 1] += err * 1 / 16
                }
            }
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val qr = (er[i] / step).roundToInt() * step; spread(er, x, y, er[i] - qr)
                val qg = (eg[i] / step).roundToInt() * step; spread(eg, x, y, eg[i] - qg)
                val qb = (eb[i] / step).roundToInt() * step; spread(eb, x, y, eb[i] - qb)
                out[i] = (img.px[i] and 0xFF000000.toInt()) or (Px.argbF(0, qr, qg, qb) and 0xFFFFFF)
            }
        } else {
            for (y in 0 until img.h) for (x in 0 until img.w) {
                val i = y * img.w + x
                val c = img.px[i]
                val bias = (BAYER4[((y / scale) % 4) * 4 + (x / scale) % 4] + 0.5f) / 16f - 0.5f
                fun q(v: Float) = Px.clampF(((v / step) + bias).roundToInt() * step)
                out[i] = if (mono) { val v = q(Px.luma(c)); Px.argb(Px.a(c), v, v, v) }
                         else Px.argb(Px.a(c), q(Px.r(c).toFloat()), q(Px.g(c).toFloat()), q(Px.b(c).toFloat()))
            }
        }
        return FxImage(img.w, img.h, out)
    }
}
