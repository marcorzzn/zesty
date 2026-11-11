package com.example.fx

import kotlin.math.*

/**
 * Operazioni spaziali: sfocature, nitidezza, stilizzazioni artistiche e disegno.
 * Le dimensioni sono in unità "u" = 1% del lato corto, così l'effetto è identico
 * sulla miniatura e sull'immagine a piena risoluzione.
 */
object FxOpsSpatial {

    val ops: Map<String, FxOp> = mapOf(
        "blur" to FxOp { img, a, _ -> FxImage(img.w, img.h, FxBlur.gaussian(img.px, img.w, img.h, a.num("radius", 1f) * img.unit)) },
        "sharpen" to FxOp { img, a, _ -> sharpen(img, a.num("amount", 80f) / 100f, a.num("radius", 0.4f) * img.unit, a.num("threshold", 0f)) },
        "clarity" to FxOp { img, a, _ -> clarity(img, a.num("amount", 40f) / 100f, a.num("radius", 4f) * img.unit) },
        "smooth" to FxOp { img, a, _ -> smooth(img, a.num("radius", 1.5f) * img.unit, a.num("strength", 40f) / 100f) },
        "oil" to FxOp { img, a, _ -> oil(img, a.num("radius", 1.2f) * img.unit) },
        "edges" to FxOp { img, a, _ -> edges(img, a) },
        "sketch" to FxOp { img, a, _ -> sketch(img, a) },
        "emboss" to FxOp { img, a, _ -> emboss(img, a) },
        "pixelate" to FxOp { img, a, _ -> pixelate(img, a) },
        "halftone" to FxOp { img, a, _ -> halftone(img, a) },
        "crystallize" to FxOp { img, a, ctx -> crystallize(img, a, ctx.seed) },
        "motion_blur" to FxOp { img, a, _ -> motionBlur(img, a.num("angle", 0f), a.num("distance", 4f) * img.unit) },
        "zoom_blur" to FxOp { img, a, _ -> zoomBlur(img, a.num("cx", 0.5f), a.num("cy", 0.5f), a.num("amount", 30f) / 100f) },
        "glow" to FxOp { img, a, _ -> glow(img, a) },
        "skin_smooth" to FxOp { img, a, _ -> skinSmooth(img, a) },
        "warhol" to FxOp { img, a, _ -> warhol(img, a) },
        "cell_tint" to FxOp { img, a, ctx -> cellTint(img, a, ctx.seed) }
    )

    fun sharpen(img: FxImage, amount: Float, sigma: Float, threshold: Float): FxImage {
        val blur = FxBlur.gaussian(img.px, img.w, img.h, sigma.coerceAtLeast(0.6f))
        val out = IntArray(img.size)
        for (i in out.indices) {
            val c = img.px[i]; val b = blur[i]
            fun s(v: Int, bv: Int): Float { val d = (v - bv).toFloat(); return if (abs(d) < threshold) v.toFloat() else v + d * amount }
            out[i] = (c and 0xFF000000.toInt()) or (Px.argbF(0, s(Px.r(c), Px.r(b)), s(Px.g(c), Px.g(b)), s(Px.b(c), Px.b(b))) and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    /** Contrasto locale sui mezzitoni (luminanza), effetto "chiarezza". */
    fun clarity(img: FxImage, amount: Float, sigma: Float): FxImage {
        val blur = FxBlur.gaussian(img.px, img.w, img.h, sigma.coerceAtLeast(1f))
        val out = IntArray(img.size)
        for (i in out.indices) {
            val c = img.px[i]
            val l = Px.luma(c); val lb = Px.luma(blur[i])
            val mid = 1f - abs(l / 255f - 0.5f) * 2f
            val d = (l - lb) * amount * (0.4f + 0.6f * mid) * 1.6f
            out[i] = (c and 0xFF000000.toInt()) or (Px.argbF(0, Px.r(c) + d, Px.g(c) + d, Px.b(c) + d) and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    /** Filtro guidato (He et al.): leviga preservando i bordi. Usato per pelle, denoise, cartoon. */
    fun smooth(img: FxImage, radiusPx: Float, strength: Float): FxImage {
        val eps = (strength * strength * 0.06f).coerceAtLeast(1e-5f)
        val n = img.size; val w = img.w; val h = img.h
        // Fast guided filter (He e Sun, 2015): medie e coefficienti calcolati su una copia ridotta di s,
        // poi applicati all'immagine piena. Con raggi ≥ 4 px la differenza non si vede, il tempo cala di s².
        val s = when { radiusPx >= 12f -> 4; radiusPx >= 4f -> 2; else -> 1 }
        val sw = if (s > 1) w / s else w; val sh = if (s > 1) h / s else h
        val r = (radiusPx / s).roundToInt().coerceAtLeast(1)
        val small = if (s > 1) FxBlur.downsample(img.px, w, h, s, sw, sh) else img.px
        val sn = sw * sh
        val out = IntArray(n) { img.px[it] and 0xFF000000.toInt() }
        val ch = FloatArray(sn)
        val sq = FloatArray(sn)
        for (shift in intArrayOf(16, 8, 0)) {
            for (i in 0 until sn) { val v = ((small[i] shr shift) and 0xFF) / 255f; ch[i] = v; sq[i] = v * v }
            val mI = FxBlur.boxF(ch, sw, sh, r); val mII = FxBlur.boxF(sq, sw, sh, r)
            val aa = FloatArray(sn); val bb = FloatArray(sn)
            for (i in 0 until sn) {
                val v = mII[i] - mI[i] * mI[i]
                aa[i] = v / (v + eps); bb[i] = mI[i] - aa[i] * mI[i]
            }
            val ma = FxBlur.boxF(aa, sw, sh, r); val mb = FxBlur.boxF(bb, sw, sh, r)
            if (s == 1) {
                for (i in 0 until n) out[i] = out[i] or (Px.clampF((ma[i] * ch[i] + mb[i]) * 255f) shl shift)
            } else {
                // coefficienti ingranditi in bilineare e applicati al canale a piena risoluzione
                val kx = sw.toFloat() / w; val ky = sh.toFloat() / h
                for (y in 0 until h) {
                    val fy = ((y + 0.5f) * ky - 0.5f).coerceIn(0f, (sh - 1).toFloat())
                    val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val ty = fy - y0
                    for (x in 0 until w) {
                        val fx = ((x + 0.5f) * kx - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                        val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val tx = fx - x0
                        val i00 = y0 * sw + x0; val i10 = y0 * sw + x1; val i01 = y1 * sw + x0; val i11 = y1 * sw + x1
                        val av = (ma[i00] * (1 - tx) + ma[i10] * tx) * (1 - ty) + (ma[i01] * (1 - tx) + ma[i11] * tx) * ty
                        val bv = (mb[i00] * (1 - tx) + mb[i10] * tx) * (1 - ty) + (mb[i01] * (1 - tx) + mb[i11] * tx) * ty
                        val i = y * w + x
                        val c = ((img.px[i] shr shift) and 0xFF) / 255f
                        out[i] = out[i] or (Px.clampF((av * c + bv) * 255f) shl shift)
                    }
                }
            }
        }
        return FxImage(w, h, out)
    }

    /** Pittura a olio: filtro di Kuwahara (sceglie il quadrante con varianza minima). */
    fun oil(img: FxImage, radiusPx: Float): FxImage {
        val q = (radiusPx / 2f).roundToInt().coerceAtLeast(1)
        val w = img.w; val h = img.h; val n = img.size
        val cr = FloatArray(n); val cg = FloatArray(n); val cb = FloatArray(n); val cl = FloatArray(n); val cll = FloatArray(n)
        for (i in 0 until n) {
            val c = img.px[i]
            cr[i] = Px.r(c).toFloat(); cg[i] = Px.g(c).toFloat(); cb[i] = Px.b(c).toFloat()
            cl[i] = Px.luma(c); cll[i] = cl[i] * cl[i]
        }
        val mr = FxBlur.boxF(cr, w, h, q); val mg = FxBlur.boxF(cg, w, h, q); val mb = FxBlur.boxF(cb, w, h, q)
        val ml = FxBlur.boxF(cl, w, h, q); val mll = FxBlur.boxF(cll, w, h, q)
        val out = IntArray(n)
        val offs = intArrayOf(-q, -q, q, -q, -q, q, q, q)
        for (y in 0 until h) for (x in 0 until w) {
            var best = -1; var bestVar = Float.MAX_VALUE
            for (k in 0 until 4) {
                val sx = (x + offs[k * 2]).coerceIn(0, w - 1); val sy = (y + offs[k * 2 + 1]).coerceIn(0, h - 1)
                val j = sy * w + sx
                val v = mll[j] - ml[j] * ml[j]
                if (v < bestVar) { bestVar = v; best = j }
            }
            val i = y * w + x
            out[i] = (img.px[i] and 0xFF000000.toInt()) or (Px.argbF(0, mr[best], mg[best], mb[best]) and 0xFFFFFF)
        }
        return FxImage(w, h, out)
    }

    private fun lumaArray(img: FxImage) = FloatArray(img.size) { Px.luma(img.px[it]) }

    /** Gradiente di Sobel normalizzato 0..1. */
    fun sobel(l: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - 1) * w; val y1 = y * w; val y2 = min(h - 1, y + 1) * w
            for (x in 0 until w) {
                val x0 = max(0, x - 1); val x2 = min(w - 1, x + 1)
                val gx = (l[y0 + x2] + 2 * l[y1 + x2] + l[y2 + x2]) - (l[y0 + x0] + 2 * l[y1 + x0] + l[y2 + x0])
                val gy = (l[y2 + x0] + 2 * l[y2 + x] + l[y2 + x2]) - (l[y0 + x0] + 2 * l[y0 + x] + l[y0 + x2])
                out[y1 + x] = (sqrt(gx * gx + gy * gy) / 1020f).coerceIn(0f, 1f)
            }
        }
        return out
    }

    fun edges(img: FxImage, a: FxArgs): FxImage {
        val pre = a.num("smooth", 0.2f) * img.unit
        val src = if (pre >= 0.5f) FxImage(img.w, img.h, FxBlur.gaussian(img.px, img.w, img.h, pre)) else img
        val mag = sobel(lumaArray(src), img.w, img.h)
        val strength = a.num("strength", 100f) / 100f * 3f
        val thr = a.num("threshold", 0f) / 100f
        val mode = a.str("mode", "mono")
        val color = a.color("color", 0xFFFFFFFF.toInt())
        val bg = a.color("background", if (mode == "glow") 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        val out = IntArray(img.size)
        for (i in out.indices) {
            val c = img.px[i]
            val e = (((mag[i] - thr) / (1f - thr).coerceAtLeast(0.01f)) * strength).coerceIn(0f, 1f)
            val res = when (mode) {
                "glow" -> Px.mix(bg, if (a.bool("keepColor", false)) c or 0xFF000000.toInt() else color, e)
                "overlay" -> Px.argbF(255, Px.r(c) * (1f - e), Px.g(c) * (1f - e), Px.b(c) * (1f - e))
                "color" -> Px.mix(bg, c or 0xFF000000.toInt(), e)
                else -> Px.mix(bg, if (a.has("color")) color else 0xFF000000.toInt(), e)
            }
            out[i] = (c and 0xFF000000.toInt()) or (res and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    /** Matita: scherma colore fra grigio e la sua inversa sfocata. */
    fun sketch(img: FxImage, a: FxArgs): FxImage {
        val radius = a.num("radius", 1f) * img.unit
        val darkness = a.num("darkness", 50f) / 100f
        val colored = a.bool("colored", false)
        val gray = IntArray(img.size) { val v = Px.clampF(Px.luma(img.px[it])); Px.argb(255, 255 - v, 255 - v, 255 - v) }
        val blurInv = FxBlur.gaussian(gray, img.w, img.h, radius.coerceAtLeast(0.8f))
        val gExp = 1f + darkness * 3f
        val out = IntArray(img.size)
        for (i in out.indices) {
            val c = img.px[i]
            val base = 1f - Px.r(gray[i]) / 255f
            val top = Px.r(blurInv[i]) / 255f
            val dodge = if (top >= 0.999f) 1f else min(1f, base / (1f - top))
            val v = dodge.pow(gExp)
            out[i] = (c and 0xFF000000.toInt()) or (if (colored) {
                Px.argbF(0, (Px.r(c) * 0.6f + 102f) * v, (Px.g(c) * 0.6f + 102f) * v, (Px.b(c) * 0.6f + 102f) * v) and 0xFFFFFF
            } else { val g = Px.clampF(v * 255f); Px.argb(0, g, g, g) })
        }
        return FxImage(img.w, img.h, out)
    }

    fun emboss(img: FxImage, a: FxArgs): FxImage {
        val ang = Math.toRadians(a.num("angle", 135f).toDouble())
        val d = (a.num("depth", 0.2f) * img.unit).coerceAtLeast(1f)
        val dx = (cos(ang) * d).toFloat(); val dy = (sin(ang) * d).toFloat()
        val k = a.num("strength", 100f) / 100f * 2.5f
        val gray = a.bool("gray", true)
        val src = img
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            val i = y * img.w + x
            val c = img.px[i]
            val l1 = Px.luma(FxSample.bilinear(src, x + dx, y + dy)); val l0 = Px.luma(FxSample.bilinear(src, x - dx, y - dy))
            val v = 128f + (l1 - l0) * k
            val rgb = if (gray) Px.argbF(0, v, v, v) else FxBlend.pixel("overlay", c or 0xFF000000.toInt(), Px.argbF(255, v, v, v), 1f)
            out[i] = (c and 0xFF000000.toInt()) or (rgb and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    fun pixelate(img: FxImage, a: FxArgs): FxImage {
        val s = (a.num("size", 2f) * img.unit).coerceAtLeast(2f)
        val shape = a.str("shape", "square")
        val bg = a.color("background", 0xFF101014.toInt())
        val gap = a.num("gap", 0f) / 100f
        val w = img.w; val h = img.h
        val out = IntArray(img.size)
        val cs = s.toInt().coerceAtLeast(2)
        // colore medio di ogni cella
        val cols = (w + cs - 1) / cs; val rows = (h + cs - 1) / cs
        val avg = IntArray(cols * rows)
        for (cy in 0 until rows) for (cx in 0 until cols) {
            var sr = 0; var sg = 0; var sb = 0; var sa = 0; var n = 0
            for (y in cy * cs until min(h, (cy + 1) * cs)) for (x in cx * cs until min(w, (cx + 1) * cs)) {
                val c = img.px[y * w + x]; sa += Px.a(c); sr += Px.r(c); sg += Px.g(c); sb += Px.b(c); n++
            }
            avg[cy * cols + cx] = if (n == 0) 0 else Px.argb(sa / n, sr / n, sg / n, sb / n)
        }
        for (y in 0 until h) for (x in 0 until w) {
            val cx = x / cs; val cy = y / cs
            val c = avg[cy * cols + cx]
            val fx = (x - cx * cs + 0.5f) / cs - 0.5f; val fy = (y - cy * cs + 0.5f) / cs - 0.5f
            val half = 0.5f - gap * 0.5f
            val inside = when (shape) {
                "circle" -> 1f - Px.smooth(half - 1f / cs, half + 1f / cs, sqrt(fx * fx + fy * fy))
                "diamond" -> 1f - Px.smooth(half - 1f / cs, half + 1f / cs, abs(fx) + abs(fy))
                else -> if (abs(fx) <= half && abs(fy) <= half) 1f else 0f
            }
            out[y * w + x] = Px.mix(bg or (c and 0xFF000000.toInt()), c, inside)
        }
        return FxImage(w, h, out)
    }

    fun halftone(img: FxImage, a: FxArgs): FxImage {
        val cell = (a.num("size", 1.2f) * img.unit).coerceAtLeast(3f)
        val mode = a.str("mode", "mono")
        val ink = a.color("ink", 0xFF111111.toInt()); val paper = a.color("paper", 0xFFF7F3E8.toInt())
        val baseAng = a.num("angle", 45f)
        val contrast = 1f + a.num("contrast", 0f) / 100f
        val w = img.w; val h = img.h
        val src = FxImage(w, h, FxBlur.gaussian(img.px, w, h, cell * 0.25f))
        val out = IntArray(img.size)

        fun coverage(x: Float, y: Float, angDeg: Float, valueOf: (Int) -> Float, lines: Boolean): Float {
            val ang = Math.toRadians(angDeg.toDouble()); val cs = cos(ang).toFloat(); val sn = sin(ang).toFloat()
            val u = (x * cs + y * sn) / cell; val v = (-x * sn + y * cs) / cell
            val cu = floor(u) + 0.5f; val cv = floor(v) + 0.5f
            val sx = (cu * cs - cv * sn) * cell; val sy = (cu * sn + cv * cs) * cell
            val dark = (((valueOf(FxSample.bilinear(src, sx, sy)) - 0.5f) * contrast) + 0.5f).coerceIn(0f, 1f)
            val aa = 1.2f / cell
            return if (lines) {
                val d = abs(v - cv)
                1f - Px.smooth(dark * 0.5f - aa, dark * 0.5f + aa, d)
            } else {
                val du = u - cu; val dv = v - cv
                val r = sqrt(dark) * 0.70711f
                1f - Px.smooth(r - aa, r + aa, sqrt(du * du + dv * dv))
            }
        }

        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val fx = x.toFloat(); val fy = y.toFloat()
            val res = when (mode) {
                "color" -> {
                    val cC = coverage(fx, fy, baseAng - 30f, { c -> 1f - Px.r(c) / 255f }, false)
                    val cM = coverage(fx, fy, baseAng + 30f, { c -> 1f - Px.g(c) / 255f }, false)
                    val cY = coverage(fx, fy, baseAng, { c -> 1f - Px.b(c) / 255f }, false)
                    Px.argbF(255, Px.r(paper) * (1f - cC), Px.g(paper) * (1f - cM), Px.b(paper) * (1f - cY))
                }
                "lines" -> Px.mix(paper, ink, coverage(fx, fy, baseAng, { c -> 1f - Px.luma(c) / 255f }, true))
                else -> Px.mix(paper, ink, coverage(fx, fy, baseAng, { c -> 1f - Px.luma(c) / 255f }, false))
            }
            out[i] = (img.px[i] and 0xFF000000.toInt()) or (res and 0xFFFFFF)
        }
        return FxImage(w, h, out)
    }

    fun crystallize(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val cell = (a.num("size", 3f) * img.unit).coerceAtLeast(3f)
        val border = a.num("border", 0f) / 100f
        val borderColor = a.color("borderColor", 0xFF000000.toInt())
        val sd = seed + a.int("seed", 0)
        val w = img.w; val h = img.h
        val out = IntArray(img.size)
        for (y in 0 until h) for (x in 0 until w) {
            val gx = floor(x / cell).toInt(); val gy = floor(y / cell).toInt()
            var d1 = Float.MAX_VALUE; var d2 = Float.MAX_VALUE; var sxBest = 0f; var syBest = 0f
            for (oy in -1..1) for (ox in -1..1) {
                val cx = gx + ox; val cy = gy + oy
                val sx = (cx + 0.15f + 0.7f * FxRand.r01(cx, cy, sd)) * cell
                val sy = (cy + 0.15f + 0.7f * FxRand.r01(cx, cy, sd + 7)) * cell
                val dd = (sx - x) * (sx - x) + (sy - y) * (sy - y)
                if (dd < d1) { d2 = d1; d1 = dd; sxBest = sx; syBest = sy } else if (dd < d2) d2 = dd
            }
            var c = FxSample.bilinear(img, sxBest, syBest)
            if (border > 0f) {
                val edge = sqrt(d2) - sqrt(d1)
                val t = 1f - Px.smooth(0f, border * cell * 0.25f + 0.5f, edge)
                c = Px.mix(c, borderColor or (c and 0xFF000000.toInt()), t)
            }
            out[y * w + x] = c
        }
        return FxImage(w, h, out)
    }

    fun motionBlur(img: FxImage, angle: Float, distPx: Float): FxImage {
        if (distPx < 1f) return img
        // Passate successive di 3 campioni con passo dimezzato: approssima la media su tutta la scia
        // con 3·log2(d) letture invece di d (fino a 48). Stessa resa, molto più veloce.
        val ang = Math.toRadians(angle.toDouble())
        val ux = cos(ang).toFloat(); val uy = sin(ang).toFloat()
        var cur = img
        var step = distPx / 4f
        while (step >= 0.5f) {
            val src = cur
            val out = IntArray(img.size)
            val dx = ux * step; val dy = uy * step
            for (y in 0 until img.h) for (x in 0 until img.w) {
                val a = FxSample.bilinear(src, x - dx, y - dy); val c = src.px[y * img.w + x]; val b = FxSample.bilinear(src, x + dx, y + dy)
                out[y * img.w + x] = avg3(a, c, b)
            }
            cur = FxImage(img.w, img.h, out)
            step /= 2f
        }
        return cur
    }

    private fun avg3(a: Int, b: Int, c: Int): Int {
        fun ch(sh: Int) = ((((a ushr sh) and 0xFF) + ((b ushr sh) and 0xFF) * 2 + ((c ushr sh) and 0xFF)) + 2) shr 2
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Suppress("unused")
    private fun motionBlurDirect(img: FxImage, angle: Float, distPx: Float): FxImage {
        if (distPx < 1f) return img
        val n = distPx.roundToInt().coerceIn(2, 48)
        val ang = Math.toRadians(angle.toDouble())
        val dx = (cos(ang) * distPx).toFloat(); val dy = (sin(ang) * distPx).toFloat()
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            var sa = 0f; var sr = 0f; var sg = 0f; var sb = 0f
            for (k in 0 until n) {
                val t = k / (n - 1f) - 0.5f
                val c = FxSample.bilinear(img, x + dx * t, y + dy * t)
                sa += Px.a(c); sr += Px.r(c); sg += Px.g(c); sb += Px.b(c)
            }
            out[y * img.w + x] = Px.argbF(Px.clampF(sa / n), sr / n, sg / n, sb / n)
        }
        return FxImage(img.w, img.h, out)
    }

    fun zoomBlur(img: FxImage, cx: Float, cy: Float, amount: Float): FxImage {
        if (amount <= 0f) return img
        // passate successive a 3 campioni con scala dimezzata (come il mosso): ~12 letture invece di 24
        val ox = cx * img.w; val oy = cy * img.h
        var cur = img
        var step = amount * 0.35f / 4f
        val minStep = 0.5f / max(img.w, img.h)
        while (step >= minStep) {
            val src = cur
            val out = IntArray(img.size)
            val s1 = 1f + step; val s2 = 1f - step
            for (y in 0 until img.h) {
                val dy = y - oy
                for (x in 0 until img.w) {
                    val dx = x - ox
                    out[y * img.w + x] = avg3(
                        FxSample.bilinear(src, ox + dx * s1, oy + dy * s1), src.px[y * img.w + x],
                        FxSample.bilinear(src, ox + dx * s2, oy + dy * s2)
                    )
                }
            }
            cur = FxImage(img.w, img.h, out)
            step /= 2f
        }
        return cur
    }

    @Suppress("unused")
    private fun zoomBlurDirect(img: FxImage, cx: Float, cy: Float, amount: Float): FxImage {
        if (amount <= 0f) return img
        val n = 24
        val ox = cx * img.w; val oy = cy * img.h
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            var sa = 0f; var sr = 0f; var sg = 0f; var sb = 0f
            for (k in 0 until n) {
                val s = 1f - amount * 0.35f * k / (n - 1f)
                val c = FxSample.bilinear(img, ox + (x - ox) * s, oy + (y - oy) * s)
                sa += Px.a(c); sr += Px.r(c); sg += Px.g(c); sb += Px.b(c)
            }
            out[y * img.w + x] = Px.argbF(Px.clampF(sa / n), sr / n, sg / n, sb / n)
        }
        return FxImage(img.w, img.h, out)
    }

    /** Bagliore: estrae le luci, le sfoca e le somma in "schermo". */
    fun glow(img: FxImage, a: FxArgs): FxImage {
        val thr = a.num("threshold", 60f) / 100f * 255f
        val radius = a.num("radius", 3f) * img.unit
        val strength = a.num("strength", 70f) / 100f
        val tintC = if (a.has("color")) a.color("color", 0xFFFFFFFF.toInt()) else 0
        val bright = IntArray(img.size) { i ->
            val c = img.px[i]
            val k = Px.smooth(thr, min(255f, thr + 50f), Px.luma(c))
            val base = if (tintC != 0) tintC else c
            Px.argbF(255, Px.r(base) * k, Px.g(base) * k, Px.b(base) * k)
        }
        val blurred = FxBlur.gaussian(bright, img.w, img.h, radius.coerceAtLeast(1f))
        val out = IntArray(img.size) { i -> FxBlend.pixel("screen", img.px[i], blurred[i], strength) }
        return FxImage(img.w, img.h, out)
    }

    /** Pelle levigata: filtro guidato applicato solo dove il colore è compatibile con la pelle. */
    fun skinSmooth(img: FxImage, a: FxArgs): FxImage {
        val amount = a.num("amount", 60f) / 100f
        val sm = smooth(img, a.num("radius", 1.2f) * img.unit, 0.35f + amount * 0.4f)
        val hsl = FloatArray(3)
        val out = IntArray(img.size) { i ->
            val c = img.px[i]
            Hsl.fromRgb(Px.r(c), Px.g(c), Px.b(c), hsl)
            val hueOk = 1f - Px.smooth(25f, 45f, if (hsl[0] > 300f) 0f else hsl[0])
            val satOk = Px.smooth(0.1f, 0.2f, hsl[1]) * (1f - Px.smooth(0.7f, 0.85f, hsl[1]))
            val lumOk = Px.smooth(0.15f, 0.3f, hsl[2]) * (1f - Px.smooth(0.92f, 0.98f, hsl[2]))
            Px.mix(c, sm.px[i], amount * hueOk * satOk * lumOk)
        }
        return FxImage(img.w, img.h, out)
    }

    /** Griglia pop-art: ripete l'immagine in tasselli con palette diverse. */
    fun warhol(img: FxImage, a: FxArgs): FxImage {
        val rows = a.int("rows", 2).coerceIn(1, 6); val cols = a.int("cols", 2).coerceIn(1, 6)
        val levels = a.int("levels", 4).coerceIn(2, 8)
        val pal = a.arr("palettes")
        val palettes = mutableListOf<List<Int>>()
        if (pal != null) for (i in 0 until pal.length()) {
            val p = pal.optJSONArray(i) ?: continue
            palettes += List(p.length()) { a.colorOf(p.get(it), 0xFF000000.toInt()) }
        }
        if (palettes.isEmpty()) palettes += listOf(0xFF1B1B3A.toInt(), 0xFFE03A8C.toInt(), 0xFFFFD23F.toInt())
        val w = img.w; val h = img.h
        val tw = w.toFloat() / cols; val th = h.toFloat() / rows
        val out = IntArray(img.size)
        for (y in 0 until h) for (x in 0 until w) {
            val cx = (x / tw).toInt().coerceAtMost(cols - 1); val cy = (y / th).toInt().coerceAtMost(rows - 1)
            val sx = (x - cx * tw) / tw * w; val sy = (y - cy * th) / th * h
            val c = FxSample.bilinear(img, sx, sy)
            val p = palettes[(cy * cols + cx) % palettes.size]
            val l = Px.luma(c) / 255f
            val q = (l * levels).toInt().coerceAtMost(levels - 1) / (levels - 1f).coerceAtLeast(1f)
            val idx = (q * (p.size - 1)).roundToInt().coerceIn(0, p.size - 1)
            out[y * w + x] = (c and 0xFF000000.toInt()) or (p[idx] and 0xFFFFFF)
        }
        return FxImage(w, h, out)
    }

    /** Griglia di celle con variazioni di tinta casuali (mosaico cromatico). */
    fun cellTint(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val cell = (a.num("size", 8f) * img.unit).coerceAtLeast(2f)
        val range = a.num("hueRange", 120f)
        val amount = a.num("amount", 60f) / 100f
        val sd = seed + a.int("seed", 0)
        val hsl = FloatArray(3)
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            val i = y * img.w + x
            val c = img.px[i]
            val gx = (x / cell).toInt(); val gy = (y / cell).toInt()
            val shift = (FxRand.r01(gx, gy, sd) - 0.5f) * 2f * range
            Hsl.fromRgb(Px.r(c), Px.g(c), Px.b(c), hsl)
            val t = Hsl.toRgb(255, hsl[0] + shift, max(hsl[1], 0.45f), hsl[2])
            out[i] = (c and 0xFF000000.toInt()) or (Px.mix(c, t, amount) and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }
}
