package com.example.fx

import kotlin.math.*

/**
 * Distorsioni geometriche, glitch, texture procedurali, particelle e pattern.
 * Tutte le distorsioni usano la mappatura inversa con campionamento bilineare.
 */
object FxOpsDistort {

    val ops: Map<String, FxOp> = mapOf(
        "swirl" to FxOp { img, a, _ -> swirl(img, a) },
        "bulge" to FxOp { img, a, _ -> bulge(img, a) },
        "ripple" to FxOp { img, a, _ -> ripple(img, a) },
        "turbulence" to FxOp { img, a, ctx -> turbulence(img, a, ctx.seed) },
        "polar" to FxOp { img, a, _ -> polar(img, a) },
        "mirror" to FxOp { img, a, _ -> mirror(img, a.str("mode", "left")) },
        "kaleidoscope" to FxOp { img, a, _ -> kaleidoscope(img, a) },
        "shear" to FxOp { img, a, _ -> shear(img, a.num("x", 0f), a.num("y", 0f)) },
        "flip" to FxOp { img, a, _ -> flip(img, a.bool("horizontal", true), a.bool("vertical", false)) },
        "rgb_shift" to FxOp { img, a, _ -> rgbShift(img, a.num("amount", 0.6f) * img.unit, a.num("angle", 0f)) },
        "glitch" to FxOp { img, a, ctx -> glitch(img, a, ctx.seed) },
        "disperse" to FxOp { img, a, ctx -> disperse(img, a, ctx.seed) },
        "scanlines" to FxOp { img, a, _ -> scanlines(img, a) },
        "vignette" to FxOp { img, a, _ -> vignette(img, a) },
        "grain" to FxOp { img, a, ctx -> grain(img, a, ctx.seed) },
        "gradient" to FxOp { img, a, _ -> gradient(img, a) },
        "texture" to FxOp { img, a, ctx -> texture(img, a, ctx.seed) },
        "dust" to FxOp { img, a, ctx -> dust(img, a, ctx.seed) },
        "particles" to FxOp { img, a, ctx -> particles(img, a, ctx.seed) },
        "pattern" to FxOp { img, a, _ -> pattern(img, a) }
    )

    private inline fun remap(img: FxImage, crossinline f: (Float, Float, FloatArray) -> Unit): FxImage {
        val out = IntArray(img.size)
        val p = FloatArray(2)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            f(x.toFloat(), y.toFloat(), p)
            out[y * img.w + x] = FxSample.bilinear(img, p[0], p[1])
        }
        return FxImage(img.w, img.h, out)
    }

    fun swirl(img: FxImage, a: FxArgs): FxImage {
        val cx = a.num("cx", 0.5f) * img.w; val cy = a.num("cy", 0.5f) * img.h
        val radius = a.num("radius", 50f) / 100f * min(img.w, img.h)
        val angle = Math.toRadians(a.num("angle", 180f).toDouble()).toFloat()
        return remap(img) { x, y, p ->
            val dx = x - cx; val dy = y - cy
            val d = sqrt(dx * dx + dy * dy)
            if (d >= radius) { p[0] = x; p[1] = y } else {
                val t = 1f - d / radius
                val th = angle * t * t
                val cs = cos(th); val sn = sin(th)
                p[0] = cx + dx * cs - dy * sn; p[1] = cy + dx * sn + dy * cs
            }
        }
    }

    /** Gonfia (strength > 0, effetto fish-eye) o comprime (strength < 0). */
    fun bulge(img: FxImage, a: FxArgs): FxImage {
        val cx = a.num("cx", 0.5f) * img.w; val cy = a.num("cy", 0.5f) * img.h
        val radius = a.num("radius", 50f) / 100f * min(img.w, img.h)
        val s = a.num("strength", 50f) / 100f
        return remap(img) { x, y, p ->
            val dx = x - cx; val dy = y - cy
            val d = sqrt(dx * dx + dy * dy)
            if (d >= radius || d == 0f) { p[0] = x; p[1] = y } else {
                val r = d / radius
                val k = if (s >= 0) r.pow(1f + s * 1.5f) / r else r.pow(1f / (1f - s * 0.9f)) / r
                p[0] = cx + dx * k; p[1] = cy + dy * k
            }
        }
    }

    fun ripple(img: FxImage, a: FxArgs): FxImage {
        val amp = a.num("amplitude", 1f) * img.unit
        val wl = (a.num("wavelength", 10f) * img.unit).coerceAtLeast(2f)
        val phase = Math.toRadians(a.num("phase", 0f).toDouble()).toFloat()
        val mode = a.str("mode", "both")
        val cx = a.num("cx", 0.5f) * img.w; val cy = a.num("cy", 0.5f) * img.h
        val k = (2 * PI / wl).toFloat()
        return remap(img) { x, y, p ->
            when (mode) {
                "radial" -> {
                    val dx = x - cx; val dy = y - cy; val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                    val off = sin(d * k - phase) * amp
                    p[0] = x + dx / d * off; p[1] = y + dy / d * off
                }
                "horizontal" -> { p[0] = x + sin(y * k + phase) * amp; p[1] = y }
                "vertical" -> { p[0] = x; p[1] = y + sin(x * k + phase) * amp }
                else -> { p[0] = x + sin(y * k + phase) * amp; p[1] = y + cos(x * k + phase) * amp }
            }
        }
    }

    /** Distorsione liquida guidata da rumore frattale. */
    fun turbulence(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val amt = a.num("amount", 2f) * img.unit
        val scale = (a.num("scale", 15f) * img.unit).coerceAtLeast(2f)
        val sd = seed + a.int("seed", 0)
        return remap(img) { x, y, p ->
            p[0] = x + (FxRand.fbm(x / scale, y / scale, sd, 3) - 0.5f) * 2f * amt
            p[1] = y + (FxRand.fbm(x / scale + 17.3f, y / scale + 5.1f, sd + 3, 3) - 0.5f) * 2f * amt
        }
    }

    /** Piccolo pianeta: coordinate polari, il bordo inferiore diventa il centro. */
    fun polar(img: FxImage, a: FxArgs): FxImage {
        val rot = Math.toRadians(a.num("rotate", 0f).toDouble()).toFloat()
        val zoom = a.num("zoom", 1f).coerceIn(0.2f, 5f)
        val inverted = a.bool("inverted", false)
        val cx = img.w / 2f; val cy = img.h / 2f
        val maxR = min(img.w, img.h) / 2f / zoom * 1.25f
        return remap(img) { x, y, p ->
            val dx = x - cx; val dy = y - cy
            val r = (sqrt(dx * dx + dy * dy) / maxR).coerceIn(0f, 1f)
            var th = atan2(dy, dx) + rot + (PI / 2).toFloat()
            th = ((th / (2 * PI).toFloat()) % 1f + 1f) % 1f
            p[0] = th * (img.w - 1)
            p[1] = if (inverted) r * (img.h - 1) else (1f - r) * (img.h - 1)
        }
    }

    fun mirror(img: FxImage, mode: String): FxImage {
        val w = img.w; val h = img.h
        val out = IntArray(img.size)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = when (mode) {
                "left", "quad" -> if (x < w / 2) x else w - 1 - x
                "right" -> if (x >= w / 2) x else w - 1 - x
                else -> x
            }
            val sy = when (mode) {
                "top", "quad" -> if (y < h / 2) y else h - 1 - y
                "bottom" -> if (y >= h / 2) y else h - 1 - y
                else -> y
            }
            out[y * w + x] = img.px[sy * w + sx]
        }
        return FxImage(w, h, out)
    }

    fun kaleidoscope(img: FxImage, a: FxArgs): FxImage {
        val seg = a.int("segments", 6).coerceIn(2, 32)
        val rot = Math.toRadians(a.num("angle", 0f).toDouble()).toFloat()
        val cx = a.num("cx", 0.5f) * img.w; val cy = a.num("cy", 0.5f) * img.h
        val slice = (2 * PI / seg).toFloat()
        return remap(img) { x, y, p ->
            val dx = x - cx; val dy = y - cy
            val r = sqrt(dx * dx + dy * dy)
            var th = atan2(dy, dx) - rot
            th = ((th % slice) + slice) % slice
            if (th > slice / 2) th = slice - th
            th += rot
            p[0] = cx + cos(th) * r; p[1] = cy + sin(th) * r
        }
    }

    fun shear(img: FxImage, sx: Float, sy: Float): FxImage {
        val kx = tan(Math.toRadians(sx.toDouble())).toFloat(); val ky = tan(Math.toRadians(sy.toDouble())).toFloat()
        val cx = img.w / 2f; val cy = img.h / 2f
        return remap(img) { x, y, p -> p[0] = x + (y - cy) * kx; p[1] = y + (x - cx) * ky }
    }

    fun flip(img: FxImage, hz: Boolean, vt: Boolean): FxImage {
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            val sx = if (hz) img.w - 1 - x else x; val sy = if (vt) img.h - 1 - y else y
            out[y * img.w + x] = img.px[sy * img.w + sx]
        }
        return FxImage(img.w, img.h, out)
    }

    fun rgbShift(img: FxImage, distPx: Float, angle: Float): FxImage {
        val ang = Math.toRadians(angle.toDouble())
        val dx = (cos(ang) * distPx).toFloat(); val dy = (sin(ang) * distPx).toFloat()
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            val c = img.px[y * img.w + x]
            val r = Px.r(FxSample.bilinear(img, x - dx, y - dy))
            val b = Px.b(FxSample.bilinear(img, x + dx, y + dy))
            out[y * img.w + x] = Px.argb(Px.a(c), r, Px.g(c), b)
        }
        return FxImage(img.w, img.h, out)
    }

    fun glitch(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val amount = a.num("amount", 50f) / 100f
        val slices = a.int("slices", 18).coerceIn(1, 120)
        val maxShift = a.num("shift", 6f) * img.unit
        val split = a.num("split", 0.8f) * img.unit
        val sd = seed + a.int("seed", 0)
        val w = img.w; val h = img.h
        val rowShift = FloatArray(h)
        val bandH = h.toFloat() / slices
        for (s in 0 until slices) {
            val active = FxRand.r01(s, 1, sd) < amount
            val shift = if (active) (FxRand.r01(s, 2, sd) - 0.5f) * 2f * maxShift else 0f
            val y0 = (s * bandH + (FxRand.r01(s, 3, sd) - 0.5f) * bandH * 0.6f).toInt().coerceIn(0, h)
            val y1 = ((s + 1) * bandH).toInt().coerceIn(0, h)
            for (y in y0 until y1) rowShift[y] = shift
        }
        val out = IntArray(img.size)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = x - rowShift[y]
            val c = FxSample.bilinear(img, sx, y.toFloat())
            val r = Px.r(FxSample.bilinear(img, sx - split * (1f + abs(rowShift[y]) / (maxShift + 1f)), y.toFloat()))
            val b = Px.b(FxSample.bilinear(img, sx + split, y.toFloat()))
            out[y * w + x] = Px.argb(Px.a(c), r, Px.g(c), b)
        }
        return FxImage(w, h, out)
    }

    /** Dispersione: frammenti che si staccano nella direzione indicata. */
    fun disperse(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val ang = Math.toRadians(a.num("angle", 0f).toDouble())
        val dist = a.num("distance", 20f) * img.unit
        val cell = (a.num("size", 1.5f) * img.unit).coerceAtLeast(2f)
        val amount = a.num("amount", 50f) / 100f
        val start = a.num("start", 0.45f)
        val sd = seed + a.int("seed", 0)
        val dx = cos(ang).toFloat(); val dy = sin(ang).toFloat()
        val out = img.px.copyOf()
        val w = img.w; val h = img.h
        val cols = (w / cell).toInt() + 1; val rows = (h / cell).toInt() + 1
        for (gy in 0 until rows) for (gx in 0 until cols) {
            val px0 = gx * cell; val py0 = gy * cell
            // posizione lungo la direzione (0..1)
            val proj = ((px0 / w - 0.5f) * dx + (py0 / h - 0.5f) * dy) + 0.5f
            val k = Px.smooth(start, 1f, proj)
            if (FxRand.r01(gx, gy, sd) > k * amount * 1.6f) continue
            val off = dist * k * (0.4f + 0.6f * FxRand.r01(gx, gy, sd + 5))
            val tx = (px0 + dx * off).toInt(); val ty = (py0 + dy * off).toInt()
            for (yy in 0 until cell.toInt()) for (xx in 0 until cell.toInt()) {
                val sx = (px0 + xx).toInt(); val sy = (py0 + yy).toInt()
                val ox = tx + xx; val oy = ty + yy
                if (sx in 0 until w && sy in 0 until h && ox in 0 until w && oy in 0 until h) out[oy * w + ox] = img.px[sy * w + sx]
            }
        }
        return FxImage(w, h, out)
    }

    fun scanlines(img: FxImage, a: FxArgs): FxImage {
        val spacing = (a.num("spacing", 0.6f) * img.unit).coerceAtLeast(2f)
        val thick = a.num("thickness", 40f) / 100f
        val opacity = a.num("opacity", 35f) / 100f
        return FxImage(img.w, img.h, IntArray(img.size) { i ->
            val y = i / img.w
            val ph = (y % spacing) / spacing
            val k = 1f - opacity * Px.smooth(1f - thick - 0.1f, 1f - thick + 0.1f, ph)
            val c = img.px[i]
            (c and 0xFF000000.toInt()) or (Px.argbF(0, Px.r(c) * k, Px.g(c) * k, Px.b(c) * k) and 0xFFFFFF)
        })
    }

    fun vignette(img: FxImage, a: FxArgs): FxImage {
        val amount = a.num("amount", 50f) / 100f
        val radius = a.num("radius", 60f) / 100f
        val soft = (a.num("softness", 50f) / 100f).coerceAtLeast(0.02f)
        val roundness = a.num("roundness", 0f) / 100f
        val cx = a.num("cx", 0.5f); val cy = a.num("cy", 0.5f)
        val color = a.color("color", if (amount >= 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        val amt = abs(amount)
        val aspect = img.w.toFloat() / img.h
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            var dx = (x.toFloat() / img.w - cx) * 2f; var dy = (y.toFloat() / img.h - cy) * 2f
            // roundness 1 = cerchio perfetto, 0 = segue la forma del fotogramma
            dx *= Px.lerp(1f, aspect, roundness)
            val d = sqrt(dx * dx + dy * dy) / 1.4142f
            val t = Px.smooth(radius - soft * 0.5f, radius + soft * 0.5f, d) * amt
            val c = img.px[y * img.w + x]
            out[y * img.w + x] = (c and 0xFF000000.toInt()) or (Px.mix(c, color, t) and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    /** Grana fotografica: più evidente nei mezzitoni, come nella pellicola. */
    fun grain(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val amount = a.num("amount", 30f) / 100f * 90f
        val size = (a.num("size", 0.15f) * img.unit).coerceAtLeast(0.6f)
        val mono = a.bool("mono", true)
        val sd = seed + a.int("seed", 0)
        return FxImage(img.w, img.h, IntArray(img.size) { i ->
            val c = img.px[i]
            val x = (i % img.w) / size; val y = (i / img.w) / size
            val l = Px.luma(c) / 255f
            val wMid = 0.35f + 0.65f * (1f - abs(l - 0.5f) * 2f)
            val nR = FxRand.noise(x, y, sd) - 0.5f
            val nG = if (mono) nR else FxRand.noise(x, y, sd + 11) - 0.5f
            val nB = if (mono) nR else FxRand.noise(x, y, sd + 23) - 0.5f
            val k = amount * wMid * 2f
            (c and 0xFF000000.toInt()) or (Px.argbF(0, Px.r(c) + nR * k, Px.g(c) + nG * k, Px.b(c) + nB * k) and 0xFFFFFF)
        })
    }

    /** Gradiente lineare o radiale (da usare con un metodo di fusione: luce, viraggi, leak). */
    fun gradient(img: FxImage, a: FxArgs): FxImage {
        val stops = a.stops("stops").ifEmpty { listOf(0f to 0xFFFF7A00.toInt(), 1f to 0x00FF7A00) }
        val type = a.str("type", "linear")
        val ang = Math.toRadians(a.num("angle", 90f).toDouble())
        val cx = a.num("cx", 0.5f); val cy = a.num("cy", 0.5f)
        val radius = a.num("radius", 70f) / 100f
        val dx = cos(ang).toFloat(); val dy = sin(ang).toFloat()
        val lut = IntArray(256) { v ->
            val t = v / 255f
            if (t <= stops.first().first) stops.first().second
            else if (t >= stops.last().first) stops.last().second
            else {
                var k = 0
                while (k < stops.size - 2 && t > stops[k + 1].first) k++
                val span = (stops[k + 1].first - stops[k].first).let { if (it <= 0f) 1f else it }
                Px.mix(stops[k].second, stops[k + 1].second, (t - stops[k].first) / span)
            }
        }
        val aspect = img.w.toFloat() / img.h
        return FxImage(img.w, img.h, IntArray(img.size) { i ->
            val u = (i % img.w).toFloat() / img.w; val v = (i / img.w).toFloat() / img.h
            val t = if (type == "radial") {
                val ex = (u - cx) * aspect.coerceAtLeast(1f); val ey = (v - cy) / aspect.coerceAtMost(1f)
                sqrt(ex * ex + ey * ey) / radius.coerceAtLeast(0.01f)
            } else ((u - 0.5f) * dx + (v - 0.5f) * dy) + 0.5f
            lut[(t.coerceIn(0f, 1f) * 255f).toInt()]
        })
    }

    /** Texture procedurali neutre (grigio 128 = nessun cambiamento con overlay/soft-light). */
    fun texture(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val kind = a.str("kind", "paper")
        val scale = (a.num("scale", 1f) * img.unit).coerceAtLeast(0.5f)
        val amount = a.num("amount", 50f) / 100f
        val sd = seed + a.int("seed", 0)
        return FxImage(img.w, img.h, IntArray(img.size) { i ->
            val x = (i % img.w) / scale; val y = (i / img.w) / scale
            val n = when (kind) {
                "canvas" -> {
                    val wx = sin(x * 3.1f) * 0.5f + 0.5f; val wy = sin(y * 3.1f) * 0.5f + 0.5f
                    (wx * 0.5f + wy * 0.5f) * 0.6f + FxRand.noise(x * 2f, y * 2f, sd) * 0.4f
                }
                "clouds" -> FxRand.fbm(x / 8f, y / 8f, sd, 5)
                "noise" -> FxRand.r01((x * 4).toInt(), (y * 4).toInt(), sd)
                "marble" -> sin((x + FxRand.fbm(x / 6f, y / 6f, sd, 4) * 18f) * 0.6f) * 0.5f + 0.5f
                "concrete" -> FxRand.fbm(x, y, sd, 5) * 0.7f + FxRand.r01((x * 3).toInt(), (y * 3).toInt(), sd + 9) * 0.3f
                else -> FxRand.fbm(x, y, sd, 4) * 0.6f + FxRand.noise(x * 6f, y * 6f, sd + 1) * 0.4f // carta
            }
            val v = Px.clampF(128f + (n - 0.5f) * 255f * amount)
            Px.argb(255, v, v, v)
        })
    }

    /** Polvere, peli e graffi da vecchia pellicola. */
    fun dust(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val density = a.num("amount", 40f) / 100f
        val sd = seed + a.int("seed", 0)
        val color = a.color("color", 0xFFF2F0EA.toInt())
        val opacity = a.num("opacity", 80f) / 100f
        val out = img.px.copyOf()
        val w = img.w; val h = img.h
        val u = img.unit
        val specks = (density * 180).toInt()
        for (s in 0 until specks) {
            val sx = FxRand.r01(s, 1, sd) * w; val sy = FxRand.r01(s, 2, sd) * h
            val r = (0.08f + FxRand.r01(s, 3, sd) * 0.35f) * u
            val dark = FxRand.r01(s, 4, sd) < 0.5f
            val col = if (dark) 0xFF141210.toInt() else color
            for (yy in (sy - r - 1).toInt()..(sy + r + 1).toInt()) for (xx in (sx - r - 1).toInt()..(sx + r + 1).toInt()) {
                if (xx !in 0 until w || yy !in 0 until h) continue
                val d = sqrt((xx - sx) * (xx - sx) + (yy - sy) * (yy - sy))
                val t = (1f - Px.smooth(r - 0.7f, r + 0.7f, d)) * opacity
                if (t > 0f) out[yy * w + xx] = Px.mix(out[yy * w + xx], col or (out[yy * w + xx] and 0xFF000000.toInt()), t)
            }
        }
        val scratches = (density * 8).toInt()
        for (s in 0 until scratches) {
            val x0 = FxRand.r01(s, 10, sd) * w
            val drift = (FxRand.r01(s, 11, sd) - 0.5f) * 0.05f * w
            val y0 = FxRand.r01(s, 12, sd) * h * 0.5f; val y1 = y0 + (0.3f + FxRand.r01(s, 13, sd) * 0.7f) * h
            for (y in y0.toInt() until min(h, y1.toInt())) {
                val t = (y - y0) / (y1 - y0)
                val x = (x0 + drift * t).toInt()
                if (x in 0 until w) out[y * w + x] = Px.mix(out[y * w + x], color or (out[y * w + x] and 0xFF000000.toInt()), opacity * 0.6f)
            }
        }
        return FxImage(w, h, out)
    }

    /** Neve, pioggia, bokeh e stelle disegnati proceduralmente. */
    fun particles(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val kind = a.str("kind", "snow")
        val density = a.num("density", 40f) / 100f
        val size = a.num("size", 0.5f) * img.unit
        val ang = Math.toRadians(a.num("angle", 80f).toDouble())
        val color = a.color("color", 0xFFFFFFFF.toInt())
        val opacity = a.num("opacity", 85f) / 100f
        val sd = seed + a.int("seed", 0)
        val out = img.px.copyOf()
        val w = img.w; val h = img.h
        val count = when (kind) { "rain" -> (density * 900).toInt(); "bokeh" -> (density * 60).toInt(); "stars" -> (density * 500).toInt(); else -> (density * 600).toInt() }
        fun plot(x: Int, y: Int, t: Float, col: Int) {
            if (x !in 0 until w || y !in 0 until h || t <= 0f) return
            val i = y * w + x
            out[i] = FxBlend.pixel("screen", out[i], col, t)
        }
        for (s in 0 until count) {
            val px0 = FxRand.r01(s, 1, sd) * w; val py0 = FxRand.r01(s, 2, sd) * h
            val rnd = FxRand.r01(s, 3, sd)
            when (kind) {
                "rain" -> {
                    val len = size * (6f + rnd * 10f)
                    val dx = cos(ang).toFloat(); val dy = sin(ang).toFloat()
                    for (k in 0 until len.toInt().coerceAtLeast(2)) plot((px0 + dx * k).toInt(), (py0 + dy * k).toInt(), opacity * 0.45f * (1f - k / len), color)
                }
                else -> {
                    val r = when (kind) { "bokeh" -> size * (3f + rnd * 6f); "stars" -> size * (0.2f + rnd * 0.5f); else -> size * (0.3f + rnd * 0.9f) }
                    val col = if (kind == "bokeh") Hsl.toRgb(255, 20f + rnd * 50f, 0.9f, 0.65f).let { if (a.has("color")) color else it } else color
                    val op = if (kind == "bokeh") opacity * 0.35f else opacity * (0.5f + rnd * 0.5f)
                    for (yy in (py0 - r - 1).toInt()..(py0 + r + 1).toInt()) for (xx in (px0 - r - 1).toInt()..(px0 + r + 1).toInt()) {
                        val d = sqrt((xx - px0) * (xx - px0) + (yy - py0) * (yy - py0))
                        val t = if (kind == "bokeh") (1f - Px.smooth(r * 0.85f, r, d)) * (0.6f + 0.4f * Px.smooth(r * 0.5f, r * 0.95f, d))
                                else 1f - Px.smooth(r * 0.3f, r + 0.6f, d)
                        plot(xx, yy, t * op, col)
                    }
                    if (kind == "stars" && rnd > 0.93f) { // stelle principali con raggi
                        val len = (size * 6f).toInt()
                        for (k in -len..len) { val t = op * (1f - abs(k) / (len + 1f)); plot(px0.toInt() + k, py0.toInt(), t, col); plot(px0.toInt(), py0.toInt() + k, t, col) }
                    }
                }
            }
        }
        return FxImage(w, h, out)
    }

    /** Pattern geometrici con colore e sfondo (sfondo trasparente se non indicato). */
    fun pattern(img: FxImage, a: FxArgs): FxImage {
        val kind = a.str("kind", "dots")
        val size = (a.num("size", 3f) * img.unit).coerceAtLeast(2f)
        val thick = a.num("thickness", 30f) / 100f
        val ang = Math.toRadians(a.num("angle", 0f).toDouble())
        val color = a.color("color", 0xFF000000.toInt())
        val bg = if (a.has("background")) a.color("background", 0xFFFFFFFF.toInt()) else (color and 0x00FFFFFF)
        val cs = cos(ang).toFloat(); val sn = sin(ang).toFloat()
        val aa = 1.2f / size
        return FxImage(img.w, img.h, IntArray(img.size) { i ->
            val x0 = (i % img.w).toFloat(); val y0 = (i / img.w).toFloat()
            val u = (x0 * cs + y0 * sn) / size; val v = (-x0 * sn + y0 * cs) / size
            val fu = u - floor(u); val fv = v - floor(v)
            val t = when (kind) {
                "lines" -> 1f - Px.smooth(thick * 0.5f - aa, thick * 0.5f + aa, abs(fv - 0.5f))
                "grid" -> max(1f - Px.smooth(thick * 0.25f - aa, thick * 0.25f + aa, abs(fv - 0.5f)), 1f - Px.smooth(thick * 0.25f - aa, thick * 0.25f + aa, abs(fu - 0.5f)))
                "checker" -> if ((floor(u).toInt() + floor(v).toInt()) and 1 == 0) 1f else 0f
                "stripes" -> if (fu < 0.5f) 1f else 0f
                "waves" -> { val wv = v + sin(u * 2f * PI.toFloat()) * 0.25f; val f = wv - floor(wv); 1f - Px.smooth(thick * 0.5f - aa, thick * 0.5f + aa, abs(f - 0.5f)) }
                "zigzag" -> { val zz = v + abs(fu - 0.5f); val f = zz - floor(zz); 1f - Px.smooth(thick * 0.5f - aa, thick * 0.5f + aa, abs(f - 0.5f)) }
                "cross" -> {
                    val dx = abs(fu - 0.5f); val dy = abs(fv - 0.5f)
                    if ((dx < thick * 0.15f && dy < 0.3f) || (dy < thick * 0.15f && dx < 0.3f)) 1f else 0f
                }
                "weave" -> {
                    val cellU = floor(u).toInt(); val cellV = floor(v).toInt()
                    val horiz = (cellU + cellV) and 1 == 0
                    val f = if (horiz) fv else fu
                    0.35f + 0.65f * (1f - abs(f - 0.5f) * 2f)
                }
                "hex" -> {
                    val q = u * 1.1547f; val r = v + u * 0.57735f
                    val fq = q - floor(q); val fr = r - floor(r)
                    val e = min(min(fq, 1f - fq), min(fr, 1f - fr))
                    1f - Px.smooth(thick * 0.2f - aa, thick * 0.2f + aa, e)
                }
                "diamonds" -> 1f - Px.smooth(0.5f * (1f - thick) - aa, 0.5f * (1f - thick) + aa, abs(fu - 0.5f) + abs(fv - 0.5f))
                "hearts" -> {
                    val hx = (fu - 0.5f) * 2.4f; val hy = -(fv - 0.45f) * 2.4f
                    val q = hx * hx + hy * hy - 1f
                    if (q * q * q - hx * hx * hy * hy * hy < 0f) 1f else 0f
                }
                "stars" -> {
                    val dx = fu - 0.5f; val dy = fv - 0.5f
                    val r = sqrt(dx * dx + dy * dy); val th = atan2(dy, dx)
                    val lim = 0.18f + 0.14f * cos(5f * th).let { (it + 1f) / 2f }
                    1f - Px.smooth(lim - aa, lim + aa, r)
                }
                else -> { // dots
                    val dx = fu - 0.5f; val dy = fv - 0.5f
                    val r = thick * 0.5f
                    1f - Px.smooth(r - aa, r + aa, sqrt(dx * dx + dy * dy))
                }
            }
            Px.mix(bg, color, t.coerceIn(0f, 1f))
        })
    }
}
