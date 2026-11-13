package com.example.fx

import kotlin.math.*

/**
 * Caleidoscopi, geometrie non euclidee e op-art (docs/EFFETTI.md §6.2, §6.3, §6.7 e §10). Scritte per Zesty.
 *
 * - `mandala`     anelli concentrici, ognuno con la sua simmetria e una rotazione alternata, con ombra fra gli anelli
 * - `tessellate`  piastrelle esagonali, triangolari o quadrate: in ognuna la foto riflessa come in un caleidoscopio
 * - `julia_warp`  il piano deformato dall'insieme di Julia z² + c: la foto si avvolge nei riccioli del frattale
 * - `mobius`      inversione circolare: dentro il cerchio tutto il mondo esterno, capovolto
 * - `poincare`    disco di Poincaré {p,q}: la foto ripetuta in poligoni che rimpiccioliscono verso il bordo
 * - `op_art`      righe, cerchi o pois che si piegano con la luce della foto (op-art alla Riley e Vasarely)
 * Centri in % della foto, distanze in u (1% del lato corto).
 */
object FxOpsGeo {
    val ops: Map<String, FxOp> = mapOf(
        "mandala" to FxOp { img, a, _ -> mandala(img, a) },
        "tessellate" to FxOp { img, a, _ -> tessellate(img, a) },
        "julia_warp" to FxOp { img, a, _ -> juliaWarp(img, a) },
        "mobius" to FxOp { img, a, _ -> mobius(img, a) },
        "poincare" to FxOp { img, a, _ -> poincare(img, a) },
        "op_art" to FxOp { img, a, _ -> opArt(img, a) }
    )

    private const val TAU = (2.0 * PI).toFloat()

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t)
    }

    private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

    private fun keepAlpha(src: Int, c: Int) = (src and 0xFF000000.toInt()) or (c and 0xFFFFFF)

    /** Coordinata riflessa: oltre il bordo la foto continua a specchio, senza strisciate. */
    private fun mirror(v: Float, n: Int): Float {
        val m = (n - 1).toFloat(); if (m <= 0f) return 0f
        val p = 2f * m
        var t = v % p; if (t < 0f) t += p
        return if (t > m) p - t else t
    }

    private fun sampleMirror(img: FxImage, x: Float, y: Float) = FxSample.bilinear(img, mirror(x, img.w), mirror(y, img.h))

    private fun shade(c: Int, k: Float): Int =
        Px.argbF(255, Px.r(c) * k, Px.g(c) * k, Px.b(c) * k)

    // ---------------------------------------------------------------- Mandala

    /**
     * Anelli concentrici (rings, fino a radius % del lato corto, poi continuano): l'anello k ha simmetria
     * symmetry·(1 + k·growth), ruota di ±twist in modo alternato e prende la foto da uno spicchio orientato
     * verso angle. Negli anelli dispari il raggio è rovesciato: le bande si specchiano fra loro.
     */
    fun mandala(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val cx = a.num("cx", 50f) / 100f * w; val cy = a.num("cy", 50f) / 100f * h
        val rings = a.num("rings", 5f).coerceIn(1f, 14f)
        val radius = (a.num("radius", 48f) / 100f * min(w, h)).coerceAtLeast(8f)
        val ringW = radius / rings
        val sym = a.num("symmetry", 6f).coerceIn(2f, 32f)
        val growth = a.num("growth", 50f) / 100f
        val twist = rad(a.num("twist", 15f))
        val zoom = a.num("zoom", 100f) / 100f
        val shadow = a.num("shadow", 40f) / 100f
        val angle0 = rad(a.num("angle", -90f))
        val out = IntArray(w * h)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
                val r = sqrt(dx * dx + dy * dy)
                val k = (r / ringW).toInt()
                val n = (sym * (1f + k * growth)).roundToInt().coerceIn(2, 64)
                val slice = TAU / n; val half = slice * 0.5f
                val rotK = if (k % 2 == 0) twist * k * 0.5f else -twist * k * 0.5f
                var th = atan2(dy, dx) - rotK
                th = ((th % slice) + slice) % slice
                if (th > half) th = slice - th
                // ogni anello usa uno spicchio di almeno ~57° della foto, ruotato dell'angolo aureo rispetto
                // all'anello precedente: anelli vicini mostrano parti diverse della foto
                val span = max(half, 1f)
                val ths = angle0 + k * 2.39996f + (th / half - 0.5f) * span
                val rl = r - k * ringW
                val rs = (k * ringW + if (k % 2 == 1) ringW - rl else rl) * zoom
                var c = sampleMirror(img, cx + rs * cos(ths), cy + rs * sin(ths))
                if (shadow > 0f && k >= 1) {
                    val t = rl / ringW
                    val dark = shadow * 0.6f * exp(-t * 8f)
                    val lift = shadow * 0.18f * smoothstep(0.8f, 1f, t)
                    c = shade(c, 1f - dark + lift)
                }
                out[y * w + x] = keepAlpha(img.px[y * w + x], c)
            }
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Tassellazione

    /**
     * Piastrelle (mode hex | triangle | square) di raggio size u, ruotate di rotate. In ogni piastrella
     * un caleidoscopio: hex = 12 spicchi, triangle = 6, square = 8. Il motivo viene dal punto cx, cy della foto,
     * ingrandito di zoom (a 100 una piastrella mostra ~30 u di foto). Bordi in rilievo (relief) e fughe (line, u) facoltative.
     */
    fun tessellate(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val mode = a.choice("mode", listOf("hex", "triangle", "square"), "hex")
        val size = (a.num("size", 12f) * img.unit).coerceAtLeast(3f)
        val rot = rad(a.num("rotate", 0f)); val cr = cos(rot); val sr = sin(rot)
        val zoom = a.num("zoom", 100f) / 100f
        val sx0 = a.num("cx", 50f) / 100f * w; val sy0 = a.num("cy", 50f) / 100f * h
        // il motivo di ogni piastrella copre ~30 u della foto, qualunque sia la misura della piastrella
        val srcK = zoom * 30f * img.unit / size
        val srcA = rad(a.num("angle", 0f)); val ca = cos(srcA) * srcK; val sa = sin(srcA) * srcK
        val line = a.num("line", 0f) * img.unit
        val lineCol = a.color("lineColor", 0xFFF4EFE6.toInt())
        val relief = a.num("relief", 25f) / 100f
        val sq3 = sqrt(3f)
        val slice = if (mode == "hex") TAU / 6f else TAU / 3f
        val bevel = size * 0.22f
        val out = IntArray(w * h)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val px = x + 0.5f - w * 0.5f; val py = y + 0.5f - h * 0.5f
                val ux = px * cr + py * sr; val uy = -px * sr + py * cr
                var lx: Float; var ly: Float; val edge: Float
                if (mode == "square") {
                    val s2 = size * 2f
                    lx = ux - floor(ux / s2 + 0.5f) * s2; ly = uy - floor(uy / s2 + 0.5f) * s2
                    var ax = abs(lx); var ay = abs(ly)
                    edge = size - max(ax, ay)
                    if (ay > ax) { val t = ax; ax = ay; ay = t }
                    lx = ax; ly = ay
                } else {
                    // esagoni a punta in alto, coordinate assiali con arrotondamento cubico
                    val qf = (sq3 / 3f * ux - uy / 3f) / size; val rf = (2f / 3f * uy) / size
                    val sf = -qf - rf
                    var rq = qf.roundToInt().toFloat(); var rr = rf.roundToInt().toFloat(); val rs = sf.roundToInt().toFloat()
                    val dq = abs(rq - qf); val dr = abs(rr - rf); val ds = abs(rs - sf)
                    if (dq > dr && dq > ds) rq = -rr - rs else if (dr > ds) rr = -rq - rs
                    val hx = size * sq3 * (rq + rr * 0.5f); val hy = size * 1.5f * rr
                    lx = ux - hx; ly = uy - hy
                    val d = max(abs(lx), max(abs(0.5f * lx + sq3 * 0.5f * ly), abs(-0.5f * lx + sq3 * 0.5f * ly)))
                    edge = size * sq3 * 0.5f - d
                    var th = atan2(ly, lx)
                    th = ((th % slice) + slice) % slice
                    if (th > slice * 0.5f) th = slice - th
                    val rr2 = sqrt(lx * lx + ly * ly)
                    lx = rr2 * cos(th); ly = rr2 * sin(th)
                }
                var c = FxSample.bilinear(img, sx0 + lx * ca - ly * sa, sy0 + lx * sa + ly * ca)
                if (relief > 0f) {
                    val b = (edge / bevel).coerceIn(0f, 1f)
                    c = shade(c, 1f - relief * 0.45f * (1f - b) * (1f - b))
                }
                if (line > 0f) c = Px.mix(lineCol, c, smoothstep(line - 0.7f, line + 0.7f, edge))
                out[y * w + x] = keepAlpha(img.px[y * w + x], c)
            }
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Julia

    /**
     * Ogni pixel z (centrato in cx, cy, scala zoom) segue z → z² + c per al più iterations passi o finché esce
     * dal raggio 2,5; il punto finale indica dove prendere la foto (ripetuta a specchio). shade scurisce i punti
     * che fuggono subito, così i riccioli del frattale si leggono. 4 campioni per pixel contro le seghettature.
     */
    fun juliaWarp(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val cRe = a.num("re", -80f) / 100f; val cIm = a.num("im", 15.6f) / 100f
        val iters = a.int("iterations", 14).coerceIn(1, 64)
        val zoom = a.num("zoom", 100f).coerceAtLeast(10f) / 100f
        val cx = a.num("cx", 50f) / 100f * w; val cy = a.num("cy", 50f) / 100f * h
        val rot = rad(a.num("rotate", 0f)); val cr = cos(rot); val sr = sin(rot)
        val shadeK = a.num("shade", 40f) / 100f
        val bail = 2.5f; val bail2 = bail * bail
        val zPerPx = 3.2f / (min(w, h) * zoom)
        val pxPerZ = min(w, h) / (2f * bail)
        val out = IntArray(w * h)
        val offs = floatArrayOf(-0.25f, -0.25f, 0.25f, -0.25f, -0.25f, 0.25f, 0.25f, 0.25f)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                var sr0 = 0f; var sg0 = 0f; var sb0 = 0f
                for (s in 0 until 4) {
                    val ox = (x + 0.5f + offs[s * 2] - cx) * zPerPx; val oy = (y + 0.5f + offs[s * 2 + 1] - cy) * zPerPx
                    var zr = ox * cr - oy * sr; var zi = ox * sr + oy * cr
                    var n = 0; var m2 = zr * zr + zi * zi
                    while (n < iters && m2 <= bail2) {
                        val t = zr * zr - zi * zi + cRe
                        zi = 2f * zr * zi + cIm; zr = t
                        m2 = zr * zr + zi * zi; n++
                    }
                    // conteggio continuo: 1 dentro l'insieme, verso 0 per i punti che fuggono subito
                    val f = if (n >= iters) 1f else {
                        val nu = n + 1f - ln(ln(max(m2, 1.0001f)) * 0.5f / ln(bail)) / ln(2f)
                        (nu / iters).coerceIn(0f, 1f)
                    }
                    val c = sampleMirror(img, w * 0.5f + zr * pxPerZ, h * 0.5f + zi * pxPerZ)
                    val k = 1f - shadeK * 0.75f * (1f - smoothstep(0f, 0.45f, f))
                    sr0 += Px.r(c) * k; sg0 += Px.g(c) * k; sb0 += Px.b(c) * k
                }
                out[y * w + x] = keepAlpha(img.px[y * w + x], Px.argbF(255, sr0 * 0.25f, sg0 * 0.25f, sb0 * 0.25f))
            }
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Inversione circolare

    /**
     * Inversione nel cerchio (cx, cy, radius % del lato corto): z → c + R²/conj(z - c). Dentro il cerchio
     * appare tutto il mondo esterno capovolto (ripetuto a specchio, che verso il centro sfuma nel colore medio).
     * mode "swap": anche fuori dal cerchio si vede l'interno, ingrandito. rim = anello del bordo, in u.
     */
    fun mobius(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val cx = a.num("cx", 50f) / 100f * w; val cy = a.num("cy", 50f) / 100f * h
        val rr = (a.num("radius", 30f) / 100f * min(w, h)).coerceAtLeast(4f)
        val rot = rad(a.num("rotate", 0f)); val cr = cos(rot); val sr = sin(rot)
        val swap = a.choice("mode", listOf("inside", "swap"), "inside") == "swap"
        val rim = a.num("rim", 0.4f) * img.unit
        val rimCol = a.color("rimColor", 0xFFFFFFFF.toInt())
        val r2 = rr * rr
        val far0 = 3f * max(w, h); val far1 = 10f * max(w, h)
        var mr = 0L; var mg = 0L; var mb = 0L
        for (c in img.px) { mr += Px.r(c); mg += Px.g(c); mb += Px.b(c) }
        val n = img.size.coerceAtLeast(1)
        val avg = Px.argb(255, (mr / n).toInt(), (mg / n).toInt(), (mb / n).toInt())
        val out = IntArray(w * h)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val i = y * w + x
                val dx0 = x + 0.5f - cx; val dy0 = y + 0.5f - cy
                val d2 = dx0 * dx0 + dy0 * dy0
                val inside = d2 < r2
                var c: Int
                if (inside || swap) {
                    val dx = dx0 * cr - dy0 * sr; val dy = dx0 * sr + dy0 * cr
                    val f = r2 / max(d2, 1e-4f)
                    val qx = dx * f; val qy = dy * f
                    c = sampleMirror(img, cx + qx - 0.5f, cy + qy - 0.5f)
                    val dist = sqrt(qx * qx + qy * qy)
                    if (dist > far0) c = Px.mix(c, avg, smoothstep(far0, far1, dist))
                } else c = img.px[i]
                if (rim > 0f) {
                    val e = abs(sqrt(d2) - rr)
                    c = Px.mix(rimCol, c, smoothstep(rim * 0.5f - 0.6f, rim * 0.5f + 0.6f, e))
                    // ombra appena fuori dall'anello
                    if (!inside && !swap) { val s = sqrt(d2) - rr; if (s > 0f) c = shade(c, 1f - 0.3f * exp(-s / (rim * 2f + 1f))) }
                }
                out[i] = keepAlpha(img.px[i], c)
            }
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Disco di Poincaré

    /**
     * Tassellazione iperbolica {p,q} nel disco (radius % del lato corto, centrato): poligoni con p lati,
     * q attorno a ogni vertice, sempre più piccoli verso il bordo. Ogni pixel viene riportato nel poligono
     * centrale con rotazioni e inversioni nel cerchio del lato; lì si legge la foto attorno a cx, cy.
     * mirror = anche i riflessi dentro il poligono (caleidoscopio); line = lati, in u; alternate = tessere alterne.
     */
    fun poincare(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val p = a.int("p", 6).coerceIn(3, 12)
        var q = a.int("q", 4).coerceIn(3, 12)
        while (1f / p + 1f / q >= 0.5f - 1e-4f) q++
        val dr = (a.num("radius", 96f) / 100f * min(w, h) * 0.5f).coerceAtLeast(8f)
        val dcx = w * 0.5f; val dcy = h * 0.5f
        val sx0 = a.num("cx", 50f) / 100f * w; val sy0 = a.num("cy", 50f) / 100f * h
        val rot = rad(a.num("rotate", 0f)); val crt = cos(rot); val srt = sin(rot)
        val zoom = a.num("zoom", 100f) / 100f
        val mirrorIn = a.bool("mirror", false)
        val line = a.num("line", 0.25f) * img.unit
        val lineCol = a.color("lineColor", 0xFF101010.toInt())
        val alt = a.num("alternate", 15f) / 100f
        val depth = a.num("depth", 30f) / 100f
        val bg = a.color("background", 0xFF0E0E10.toInt())
        val tp = PI.toFloat() / p; val tq = PI.toFloat() / q
        val cc = cos(tq) / sqrt(cos(tq) * cos(tq) - sin(tp) * sin(tp))
        val rc = sqrt(cc * cc - 1f); val rc2 = rc * rc
        val polyR = cc - rc
        val tex = min(w, h) * 0.5f * zoom / polyR
        val seg = TAU / p
        val cosT = FloatArray(p) { cos(-it * seg) }; val sinT = FloatArray(p) { sin(-it * seg) }
        val out = IntArray(w * h)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val i = y * w + x
                val ox = (x + 0.5f - dcx) / dr; val oy = (y + 0.5f - dcy) / dr
                val rho2 = ox * ox + oy * oy
                if (rho2 >= 1f) {
                    val e = (sqrt(rho2) - 1f) * dr
                    out[i] = keepAlpha(img.px[i], bg); if (e < 1f) out[i] = keepAlpha(img.px[i], Px.mix(lineCol, bg, e))
                    continue
                }
                var zx = ox * crt - oy * srt; var zy = ox * srt + oy * crt
                var k = 1f; var flips = 0
                for (it in 0 until 48) {
                    // rotazione nel settore [-π/p, π/p]
                    val idx = ((atan2(zy, zx) / seg).roundToInt() % p + p) % p
                    if (idx != 0) { val tx = zx * cosT[idx] - zy * sinT[idx]; zy = zx * sinT[idx] + zy * cosT[idx]; zx = tx }
                    if (mirrorIn && zy < 0f) { zy = -zy; flips++ }
                    val ddx = zx - cc; val d2 = ddx * ddx + zy * zy
                    if (d2 >= rc2) break
                    val f = rc2 / d2
                    zx = cc + ddx * f; zy *= f; k *= f; flips++
                }
                var c = FxSample.bilinear(img, sx0 + zx * tex, sy0 + zy * tex)
                if (alt > 0f && flips % 2 == 1) c = shade(c, 1f - alt * 0.6f)
                if (depth > 0f) c = Px.mix(c, bg, depth * smoothstep(0.55f, 1f, rho2))
                if (line > 0f) {
                    // distanza dal lato in pixel di uscita: la scala dei passi accumulati riporta al disco
                    val ddx = zx - cc
                    val dist = (sqrt(ddx * ddx + zy * zy) - rc) / k * dr
                    c = Px.mix(lineCol, c, smoothstep(line - 0.6f, line + 0.6f, dist))
                }
                // bordo del disco antialias
                val e = (1f - sqrt(rho2)) * dr
                if (e < 1f) c = Px.mix(lineCol, c, e)
                out[i] = keepAlpha(img.px[i], c)
            }
        }
        return FxImage(w, h, out)
    }

    // ---------------------------------------------------------------- Op-art

    /**
     * mode lines: righe (period u, angle) più spesse dove la foto è scura, che si spostano col buio (amount)
     * e ondeggiano (wave);
     * rings: cerchi concentrici attorno a cx, cy che si piegano allo stesso modo;
     * dots: griglia di pois gonfiata da una sfera (bulge) attorno a cx, cy, il pois cresce col buio e
     * le celle si invertono a scacchiera, alla Vasarely.
     * Due colori (ink, paper) oppure, con colorful, i colori della foto in versione scura e chiara.
     */
    fun opArt(img: FxImage, a: FxArgs): FxImage {
        val w = img.w; val h = img.h
        val mode = a.choice("mode", listOf("lines", "rings", "dots"), "lines")
        val period = (a.num("period", 1.6f) * img.unit).coerceAtLeast(2f)
        val amount = a.num("amount", 60f) / 100f * 0.8f
        val wave = a.num("wave", 0f) / 100f
        val ang = rad(a.num("angle", 0f)); val ca = cos(ang); val sa = sin(ang)
        val cx = a.num("cx", 50f) / 100f * w; val cy = a.num("cy", 50f) / 100f * h
        val bulge = a.num("bulge", 60f) / 100f
        val ink = a.color("ink", 0xFF111111.toInt()); val paper = a.color("paper", 0xFFF5F2EA.toInt())
        val colorful = a.bool("colorful", false)
        val n = w * h
        // luce della foto, ammorbidita sulla scala del periodo
        val lum = FloatArray(n) { Px.luma(img.px[it]) / 255f }
        val lr = (period * 0.35f).roundToInt().coerceAtLeast(1)
        val ls = FxBlur.boxF(FxBlur.boxF(lum, w, h, lr), w, h, lr)
        val v = FloatArray(n)
        val bulgeR = 0.5f * min(w, h)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val i = y * w + x
                val px = x + 0.5f; val py = y + 0.5f
                val dark = 1f - ls[i]
                v[i] = when (mode) {
                    "rings" -> {
                        val dx = px - cx; val dy = py - cy
                        sqrt(dx * dx + dy * dy) / period + amount * dark
                    }
                    "dots" -> 0f
                    else -> {
                        val u = px * ca + py * sa; val t = -px * sa + py * ca
                        t / period + amount * dark + wave * 1.2f * sin(u / (period * 9f) * TAU)
                    }
                }
            }
        }
        val out = IntArray(n)
        FxParallel.rows(h) { ya, yb ->
            for (y in ya until yb) for (x in 0 until w) {
                val i = y * w + x
                val value: Float
                if (mode == "dots") {
                    // sfera: le coordinate vicino al centro si dilatano, come una lente
                    var dx = x + 0.5f - cx; var dy = y + 0.5f - cy
                    val r = sqrt(dx * dx + dy * dy) / bulgeR
                    var scale = 1f
                    if (r < 1f && bulge > 0f) scale = 1f - bulge + bulge * (0.4f + 0.6f * r * r)
                    dx *= scale; dy *= scale
                    val u = (dx * ca + dy * sa) / period; val t = (-dx * sa + dy * ca) / period
                    val gx = floor(u); val gy = floor(t)
                    val fx = u - gx - 0.5f; val fy = t - gy - 0.5f
                    val dist = sqrt(fx * fx + fy * fy)
                    val rd = 0.08f + 0.40f * (1f - ls[i])
                    val aa = (0.9f / (period * scale)).coerceAtMost(0.25f)
                    val dot = 1f - smoothstep(rd - aa, rd + aa, dist)
                    val odd = ((gx.toInt() + gy.toInt()) and 1) == 1
                    value = if (odd) 1f - dot else dot
                } else {
                    val vx = if (x + 1 < w) abs(v[i + 1] - v[i]) else if (x > 0) abs(v[i] - v[i - 1]) else 0f
                    val vy = if (y + 1 < h) abs(v[i + w] - v[i]) else if (y > 0) abs(v[i] - v[i - w]) else 0f
                    val fw = (vx + vy).coerceIn(0.02f, 0.5f)
                    // banda d'inchiostro centrata a metà periodo, più spessa dove la foto è scura;
                    // antialias con la distanza dal bordo della banda in unità di periodo
                    val fr = v[i] - floor(v[i])
                    val duty = 0.12f + 0.76f * (1f - ls[i])
                    val off = abs(fr - 0.5f)
                    val d = abs(off - duty * 0.5f)
                    val inInk = off < duty * 0.5f
                    val e = smoothstep(0f, fw * 0.6f, d)
                    value = if (inInk) 0.5f + 0.5f * e else 0.5f - 0.5f * e
                }
                val c = if (colorful) {
                    val src = img.px[i]
                    Px.mix(Px.mix(src, 0xFFFFFFFF.toInt(), 0.55f), Px.mix(src, 0xFF000000.toInt(), 0.55f), value)
                } else Px.mix(paper, ink, value)
                out[i] = keepAlpha(img.px[i], c)
            }
        }
        return FxImage(w, h, out)
    }
}
