package com.example.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Preparazione delle immagini per i modelli di scontorno (pura Kotlin, verificabile con test JVM). */
object AiPrep {

    /**
     * Ridimensiona (bilineare) e converte in tensore 1×3×th×tw, canali separati:
     * valore = (canale·scale − mean) / std. I pixel trasparenti diventano grigio neutro.
     */
    fun toChw(px: IntArray, w: Int, h: Int, tw: Int, th: Int, mean: Float, std: Float, scale: Float): FloatArray {
        val plane = tw * th
        val out = FloatArray(3 * plane)
        for (y in 0 until th) {
            val fy = ((y + 0.5f) * h / th - 0.5f).coerceIn(0f, (h - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(h - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until tw) {
                val fx = ((x + 0.5f) * w / tw - 0.5f).coerceIn(0f, (w - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(w - 1, x0 + 1); val tx = fx - x0
                val o = y * tw + x
                for (c in 0 until 3) {
                    val sh = 16 - 8 * c
                    fun ch(p: Int): Float = if ((p ushr 24) < 8) 128f else ((p shr sh) and 255).toFloat()
                    val top = ch(px[y0 * w + x0]) * (1 - tx) + ch(px[y0 * w + x1]) * tx
                    val bot = ch(px[y1 * w + x0]) * (1 - tx) + ch(px[y1 * w + x1]) * tx
                    out[c * plane + o] = ((top * (1 - ty) + bot * ty) * scale - mean) / std
                }
            }
        }
        return out
    }

    /** Porta i valori in 0..1 (come fa la pubblicazione originale di IS-Net). */
    fun normalizeMinMax(a: FloatArray) {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (v in a) { if (v < lo) lo = v; if (v > hi) hi = v }
        val d = hi - lo
        if (d < 1e-6f) return
        for (i in a.indices) a[i] = (a[i] - lo) / d
    }

    /** Dimensioni per MODNet: lato corto ~512, entrambi multipli di 32 (come il codice di riferimento). */
    fun modnetSize(w: Int, h: Int, ref: Int = 512): Pair<Int, Int> {
        var tw = w.toFloat(); var th = h.toFloat()
        if (max(w, h) < ref || min(w, h) > ref) {
            if (w >= h) { th = ref.toFloat(); tw = w * ref.toFloat() / h } else { tw = ref.toFloat(); th = h * ref.toFloat() / w }
        }
        val rw = max(32, (tw.roundToInt() / 32) * 32)
        val rh = max(32, (th.roundToInt() / 32) * 32)
        return rw to rh
    }
}
