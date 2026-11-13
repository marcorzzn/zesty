package com.example.fx

import kotlin.math.*

/**
 * Tecniche di disegno e di stampa (docs/EFFETTI.md §10): cambiano il *modo* in cui la foto è resa,
 * non solo i colori. Scritte per Zesty.
 *
 * - `hatching`  tratteggio a china/acquaforte: linee che si infittiscono e si incrociano nelle ombre
 * - `screentone` retino manga: nero pieno, bianco puro e puntini nei mezzitoni
 * - `flat_regions` campiture piatte con contorni conservati (fumetto, linea chiara, ukiyo-e)
 * - `engrave`   incisione: linee ondulate che seguono i volumi (banconota, xilografia)
 * - `misregister` stampa a lastre separate e leggermente fuori registro (risograph, serigrafia, giornale)
 * Distanze in u (1% del lato corto), come il resto del motore.
 */
object FxOpsArt {
    val ops: Map<String, FxOp> = mapOf(
        "hatching" to FxOp { img, a, _ -> hatching(img, a) },
        "screentone" to FxOp { img, a, _ -> screentone(img, a) },
        "flat_regions" to FxOp { img, a, _ -> flatRegions(img, a) },
        "engrave" to FxOp { img, a, _ -> engrave(img, a) },
        "misregister" to FxOp { img, a, ctx -> misregister(img, a, ctx.seed) }
    )

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t)
    }

    /** Luminosità 0..1, con contrasto e luminosità regolabili, leggermente sfocata (il tratto non segue il rumore). */
    private fun tone(img: FxImage, a: FxArgs, blurU: Float = 0.15f): FloatArray {
        val contrast = 1f + a.num("contrast", 0f) / 100f
        val bright = a.num("brightness", 0f) / 100f
        val l = FloatArray(img.size) { i -> (((Px.luma(img.px[i]) / 255f) - 0.5f) * contrast + 0.5f + bright).coerceIn(0f, 1f) }
        val r = blurU * img.unit
        return if (r >= 0.8f) FxBlur.boxF(l, img.w, img.h, r.roundToInt().coerceAtLeast(1)) else l
    }

    /**
     * Tratteggio: fino a 4 strati di linee a angoli diversi; ogni strato compare sotto una soglia di luce,
     * e lo spessore cresce con il buio. keepColor = il tratto scurisce la foto a colori invece della carta.
     */
    fun hatching(img: FxImage, a: FxArgs): FxImage {
        val spacing = (a.num("size", 0.7f) * img.unit).coerceAtLeast(2.5f)
        val layers = a.int("layers", 3).coerceIn(1, 4)
        val base = a.num("angle", 45f)
        val ink = a.color("ink", 0xFF141210.toInt()); val paper = a.color("paper", 0xFFF6F1E6.toInt())
        val keep = a.bool("keepColor", false)
        val l = tone(img, a)
        val angles = floatArrayOf(base, base - 90f, base + 45f, base - 45f)
        val dirs = Array(layers) { k -> val r = Math.toRadians(angles[k].toDouble()); floatArrayOf(cos(r).toFloat(), sin(r).toFloat()) }
        val aa = 1.2f / spacing
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            val i = y * img.w + x
            val v = l[i]
            var cover = 0f
            for (k in 0 until layers) {
                val thr = 1f - (k + 1f) / (layers + 1.5f) // gli strati si aggiungono man mano che l'immagine si scurisce
                if (v >= thr) continue
                val dark = ((thr - v) / thr).coerceIn(0f, 1f)
                val t = (x * dirs[k][0] + y * dirs[k][1]) / spacing
                val d = abs(t - floor(t) - 0.5f)          // 0 al centro della linea, 0.5 a metà fra due linee
                val half = 0.08f + 0.38f * dark            // linee più spesse nel buio
                cover = max(cover, 1f - smoothstep(half - aa, half + aa, d))
            }
            if (v < 0.06f) cover = 1f // neri pieni
            val c = img.px[i]
            out[i] = if (keep) Px.mix(c or 0xFF000000.toInt(), ink, cover * 0.85f) else Px.mix(paper, ink, cover)
            out[i] = (c and 0xFF000000.toInt()) or (out[i] and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    /** Retino manga: sotto [black] nero pieno, sopra [white] bianco, in mezzo puntini a 45° di grandezza variabile. */
    fun screentone(img: FxImage, a: FxArgs): FxImage {
        val cell = (a.num("size", 0.8f) * img.unit).coerceAtLeast(3f)
        val black = a.num("black", 22f) / 100f; val white = a.num("white", 72f) / 100f
        val ink = a.color("ink", 0xFF111111.toInt()); val paper = a.color("paper", 0xFFFFFFFF.toInt())
        val l = tone(img, a, 0.1f)
        val ca = cos(Math.PI / 4).toFloat(); val sa = sin(Math.PI / 4).toFloat()
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            val i = y * img.w + x
            val v = l[i]
            val cover = when {
                v <= black -> 1f
                v >= white -> 0f
                else -> {
                    val t = (v - black) / (white - black)            // 0 scuro … 1 chiaro
                    val u = (x * ca + y * sa) / cell; val w = (-x * sa + y * ca) / cell
                    val du = u - floor(u) - 0.5f; val dw = w - floor(w) - 0.5f
                    val dist = sqrt(du * du + dw * dw)
                    val radius = 0.62f * sqrt(1f - t)                  // area del puntino proporzionale al buio
                    1f - smoothstep(radius - 0.06f, radius + 0.06f, dist)
                }
            }
            out[i] = (img.px[i] and 0xFF000000.toInt()) or (Px.mix(paper, ink, cover) and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    /**
     * Campiture piatte: la foto viene levigata conservando i bordi, poi la luminosità si riduce a pochi livelli
     * mantenendo la tinta di ogni zona (non un unico colore per livello). Il risultato sembra colorato a mano.
     */
    fun flatRegions(img: FxImage, a: FxArgs): FxImage {
        val levels = a.int("levels", 5).coerceIn(2, 12)
        val passes = a.int("passes", 3).coerceIn(1, 6)
        val sat = 1f + a.num("saturation", 10f) / 100f
        // levigatura che conserva i bordi (raggio piccolo, più passate) poi gradini netti: zone piatte, contorni netti
        var cur = img
        for (k in 0 until passes) cur = FxOpsSpatial.smooth(cur, a.num("radius", 0.7f) * img.unit, a.num("strength", 55f) / 100f)
        val out = IntArray(img.size)
        for (i in 0 until img.size) {
            val c = cur.px[i]
            val r = Px.r(c) / 255f; val g = Px.g(c) / 255f; val b = Px.b(c) / 255f
            val l = Px.lumaRGB(r, g, b)
            val lq = ((l * levels).toInt().coerceAtMost(levels - 1) + 0.5f) / levels
            val k = if (l > 0.01f) lq / l else 0f
            // tinta conservata, saturazione regolabile attorno alla nuova luminosità
            val nr = lq + (r * k - lq) * sat; val ng = lq + (g * k - lq) * sat; val nb = lq + (b * k - lq) * sat
            out[i] = Px.argbF(Px.a(img.px[i]), nr * 255f, ng * 255f, nb * 255f)
        }
        return FxImage(img.w, img.h, out)
    }

    /**
     * Incisione: righe parallele ondulate la cui fase segue la luminosità sfocata (le linee "girano" attorno ai volumi)
     * e il cui spessore cresce nelle ombre. Tipico di banconote, acqueforti e xilografie.
     */
    fun engrave(img: FxImage, a: FxArgs): FxImage {
        val spacing = (a.num("size", 0.6f) * img.unit).coerceAtLeast(2.5f)
        val wave = a.num("wave", 6f)
        val ang = Math.toRadians(a.num("angle", 0f).toDouble())
        val ink = a.color("ink", 0xFF1A1714.toInt()); val paper = a.color("paper", 0xFFF3EDE0.toInt())
        val keep = a.bool("keepColor", false)
        val l = tone(img, a, 0.2f)
        val form = FxBlur.boxF(l, img.w, img.h, max(1, (3f * img.unit).roundToInt()))
        val ca = cos(ang).toFloat(); val sa = sin(ang).toFloat()
        val aa = 1.1f / spacing
        val out = IntArray(img.size)
        for (y in 0 until img.h) for (x in 0 until img.w) {
            val i = y * img.w + x
            val v = l[i]
            val t = (-x * sa + y * ca) / spacing + form[i] * wave
            val d = abs(t - floor(t) - 0.5f)
            val half = 0.04f + 0.44f * (1f - v)
            val cover = 1f - smoothstep(half - aa, half + aa, d)
            val c = img.px[i]
            val res = if (keep) Px.mix(c or 0xFF000000.toInt(), ink, cover * 0.8f) else Px.mix(paper, ink, cover)
            out[i] = (c and 0xFF000000.toInt()) or (res and 0xFFFFFF)
        }
        return FxImage(img.w, img.h, out)
    }

    /**
     * Stampa a lastre: l'immagine si separa in inchiostri (2 o 3 colori), ogni lastra si sposta di poco e si
     * stampa per moltiplicazione sulla carta, con grana di inchiostro. Gli spostamenti creano i bordi colorati
     * tipici di risograph, serigrafie economiche e giornali.
     */
    fun misregister(img: FxImage, a: FxArgs, seed: Int): FxImage {
        val inks = a.colors("inks").ifEmpty { listOf(0xFF00A3E0.toInt(), 0xFFFF2E88.toInt(), 0xFFFFE100.toInt()) }
        val paper = a.color("paper", 0xFFF7F2E7.toInt())
        val off = a.num("offset", 0.5f) * img.unit
        val grain = a.num("grain", 20f) / 100f
        // densità dell'inchiostro: sotto 1 la carta respira e le sovrapposizioni non diventano fango
        val density = a.num("density", 70f) / 100f
        val w = img.w; val h = img.h
        // copertura di ogni inchiostro: quanto quel colore serve a ricostruire il pixel (sottrattivo, semplificato)
        val cover = Array(inks.size) { FloatArray(w * h) }
        for (i in 0 until w * h) {
            val c = img.px[i]
            val r = Px.r(c) / 255f; val g = Px.g(c) / 255f; val b = Px.b(c) / 255f
            for (k in inks.indices) {
                val ik = inks[k]
                // inchiostro k assorbe ciò che non riflette: copertura = buio del pixel nei canali che l'inchiostro scurisce
                val ar = 1f - Px.r(ik) / 255f; val ag = 1f - Px.g(ik) / 255f; val ab = 1f - Px.b(ik) / 255f
                val s = ar + ag + ab
                cover[k][i] = if (s < 1e-3f) 0f else ((((1f - r) * ar + (1f - g) * ag + (1f - b) * ab) / s) * density).coerceIn(0f, 1f)
            }
        }
        val out = IntArray(w * h) { paper }
        val angles = floatArrayOf(0f, 2.1f, 4.2f, 1.0f)
        for (k in inks.indices) {
            val dx = (cos(angles[k]) * off).roundToInt(); val dy = (sin(angles[k]) * off).roundToInt()
            val ik = inks[k]
            for (y in 0 until h) for (x in 0 until w) {
                val sx = (x - dx).coerceIn(0, w - 1); val sy = (y - dy).coerceIn(0, h - 1)
                var cv = cover[k][sy * w + sx]
                if (grain > 0f) cv = (cv * (1f - grain * 0.5f + grain * FxRand.r01(x, y, seed + k * 31))).coerceIn(0f, 1f)
                val o = y * w + x
                val p = out[o]
                // inchiostro per moltiplicazione: dove si sovrappongono nasce il terzo colore
                val mr = 1f - cv * (1f - Px.r(ik) / 255f); val mg = 1f - cv * (1f - Px.g(ik) / 255f); val mb = 1f - cv * (1f - Px.b(ik) / 255f)
                out[o] = Px.argbF(255, Px.r(p) * mr, Px.g(p) * mg, Px.b(p) * mb)
            }
        }
        for (i in 0 until w * h) out[i] = (img.px[i] and 0xFF000000.toInt()) or (out[i] and 0xFFFFFF)
        return FxImage(w, h, out)
    }
}
