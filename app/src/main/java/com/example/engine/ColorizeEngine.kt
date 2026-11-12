package com.example.engine

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Preparazione e ricomposizione per la colorizzazione (DDColor).
 *
 * Il modello stima solo il **colore** (canali a, b di CIELAB) a 512×512; la **luminosità** resta quella
 * originale a piena risoluzione. Così dettagli, luci e ombre sono quelli veri della foto e solo il colore
 * è stimato. Il colore, ingrandito, viene fatto aderire ai contorni della foto con il filtro guidato.
 * Solo Kotlin: verificabile con test JVM.
 */
object ColorizeEngine {
    const val SIDE = 512

    private fun lin(c: Float): Float { val v = c / 255f; return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f) }
    private fun gam(v: Float): Int {
        val c = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.coerceAtLeast(0f).pow(1f / 2.4f) - 0.055f
        return (c * 255f).roundToInt().coerceIn(0, 255)
    }
    private fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
    private fun finv(t: Float) = if (t * t * t > 0.008856f) t * t * t else (t - 16f / 116f) / 7.787f

    /** Luminosità CIELAB (0..100) di ogni pixel. */
    fun lightness(px: IntArray): FloatArray = FloatArray(px.size) { i ->
        val c = px[i]
        val y = 0.2126729f * lin(((c shr 16) and 255).toFloat()) + 0.7151522f * lin(((c shr 8) and 255).toFloat()) + 0.0721750f * lin((c and 255).toFloat())
        116f * f(y) - 16f
    }

    /**
     * Come [labToRgb], ma se il colore esce dalla gamma rappresentabile ne riduce l'intensità invece di
     * tagliarlo: la luminosità resta quella vera (un colore tagliato scurirebbe le zone chiare).
     */
    fun labToRgbInGamut(l: Float, a: Float, b: Float): Int {
        var k = 1f
        repeat(8) {
            val fy = (l + 16f) / 116f
            val x = finv(a * k / 500f + fy) * 0.95047f; val y = finv(fy); val z = finv(fy - b * k / 200f) * 1.08883f
            val r = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
            val g = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
            val bb = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z
            if (r in -0.002f..1.002f && g in -0.002f..1.002f && bb in -0.002f..1.002f) return labToRgb(l, a * k, b * k)
            k *= 0.8f
        }
        return labToRgb(l, 0f, 0f)
    }

    /** CIELAB (D65) → ARGB opaco. */
    fun labToRgb(l: Float, a: Float, b: Float): Int {
        val fy = (l + 16f) / 116f
        val fx = a / 500f + fy
        val fz = fy - b / 200f
        val x = finv(fx) * 0.95047f; val y = finv(fy); val z = finv(fz) * 1.08883f
        val r = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
        val g = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
        val bb = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z
        return (0xFF shl 24) or (gam(r) shl 16) or (gam(g) shl 8) or gam(bb)
    }

    /**
     * Ingresso del modello: grigio ricostruito dalla sola luminosità (a = b = 0), 512×512, valori 0..1,
     * canali separati (1×3×512×512). Nessuna normalizzazione tipo ImageNet.
     */
    fun modelInput(lum: FloatArray, w: Int, h: Int): FloatArray {
        val small = SubjectEngine.upscale(lum, w, h, SIDE, SIDE) // vale anche per ridurre (bilineare)
        val plane = SIDE * SIDE
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val g = gam(finv((small[i].coerceIn(0f, 100f) + 16f) / 116f)) / 255f
            out[i] = g; out[plane + i] = g; out[2 * plane + i] = g
        }
        return out
    }

    /**
     * Ricompone la foto a colori: luminosità originale + colore del modello ingrandito.
     * @param ab uscita del modello (2×512×512)
     * @param saturation 0..2 (1 = come stimato dal modello)
     * @param guidePx pixel originali per far aderire il colore ai contorni (null = solo ingrandimento)
     */
    fun compose(px: IntArray, w: Int, h: Int, ab: FloatArray, saturation: Float, guidePx: IntArray? = px): IntArray {
        val plane = SIDE * SIDE
        // a e b portati in 0..1 per il filtro guidato, poi riportati alla scala Lab
        val a0 = FloatArray(plane) { ((ab[it] + 128f) / 256f).coerceIn(0f, 1f) }
        val b0 = FloatArray(plane) { ((ab[plane + it] + 128f) / 256f).coerceIn(0f, 1f) }
        var aUp = SubjectEngine.upscale(a0, SIDE, SIDE, w, h)
        var bUp = SubjectEngine.upscale(b0, SIDE, SIDE, w, h)
        if (guidePx != null && max(w, h) > SIDE) {
            val r = max(2, (max(w, h).toFloat() / SIDE * 2f).roundToInt())
            aUp = CutoutEngine.refineEdge(guidePx, aUp, w, h, r, 1e-3f)
            bUp = CutoutEngine.refineEdge(guidePx, bUp, w, h, r, 1e-3f)
        }
        val lum = lightness(px)
        val s = saturation.coerceIn(0f, 3f)
        return IntArray(w * h) { i ->
            val a = (aUp[i] * 256f - 128f) * s
            val b = (bUp[i] * 256f - 128f) * s
            val rgb = labToRgbInGamut(lum[i], a, b)
            (px[i] and 0xFF000000.toInt()) or (rgb and 0xFFFFFF)
        }
    }

}
