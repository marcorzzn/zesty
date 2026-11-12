package com.example.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cancella oggetti: preparazione e ricomposizione per il modello di ricostruzione (LaMa, 512×512).
 *
 * 1. si ritaglia un quadrato attorno alla zona da togliere, con abbastanza contesto intorno
 *    (il modello "capisce" lo sfondo da ciò che vede vicino);
 * 2. il ritaglio va a 512×512; la maschera si allarga di poco e conta ogni pixel non vuoto,
 *    anche i bordi morbidi del pennello (altrimenti resta un'ombra dell'oggetto);
 * 3. il risultato torna alla misura del ritaglio e sostituisce solo la zona, con un bordo sfumato.
 * Solo Kotlin: verificabile con test JVM.
 */
object InpaintEngine {
    const val SIDE = 512

    /** Rettangolo [left, top, right, bottom) in pixel. */
    data class Crop(val l: Int, val t: Int, val r: Int, val b: Int) {
        val w get() = r - l
        val h get() = b - t
    }

    /** Ritaglio quadrato (per quanto possibile) attorno alla zona, con margine di contesto; null se la zona è vuota. */
    fun crop(mask: FloatArray, w: Int, h: Int, threshold: Float = 0.02f): Crop? {
        val bb = CutoutEngine.bounds(mask, w, h, threshold) ?: return null
        val bw = bb[2] - bb[0]; val bh = bb[3] - bb[1]
        // contesto: tre quarti della zona per lato, almeno 48 px; lato del quadrato almeno 256 px se la foto lo consente
        val margin = max(48, (max(bw, bh) * 0.75f).roundToInt())
        var side = max(max(bw, bh) + 2 * margin, min(256, min(w, h)))
        side = min(side, max(w, h))
        val cx = (bb[0] + bb[2]) / 2; val cy = (bb[1] + bb[3]) / 2
        val sw = min(side, w); val sh = min(side, h)
        val l = (cx - sw / 2).coerceIn(0, w - sw); val t = (cy - sh / 2).coerceIn(0, h - sh)
        return Crop(l, t, l + sw, t + sh)
    }

    private fun cropPixels(px: IntArray, w: Int, c: Crop): IntArray {
        val out = IntArray(c.w * c.h)
        for (y in 0 until c.h) System.arraycopy(px, (c.t + y) * w + c.l, out, y * c.w, c.w)
        return out
    }

    private fun cropMask(mask: FloatArray, w: Int, c: Crop): FloatArray {
        val out = FloatArray(c.w * c.h)
        for (y in 0 until c.h) System.arraycopy(mask, (c.t + y) * w + c.l, out, y * c.w, c.w)
        return out
    }

    /** Ingressi del modello: immagine 1×3×512×512 (0..1) e maschera 1×1×512×512 (1 = da ricostruire). */
    fun inputs(px: IntArray, mask: FloatArray, w: Int, c: Crop): Pair<FloatArray, FloatArray> {
        val cp = cropPixels(px, w, c)
        val image = AiPrep.toChw(cp, c.w, c.h, SIDE, SIDE, mean = 0f, std = 1f, scale = 1f / 255f)
        val m512 = SubjectEngine.upscale(cropMask(mask, w, c), c.w, c.h, SIDE, SIDE)
        val hard = FloatArray(m512.size) { if (m512[it] > 0.01f) 1f else 0f }
        val grown = CutoutEngine.grow(hard, SIDE, SIDE, 4) // un po' di margine attorno all'oggetto
        return image to FloatArray(grown.size) { if (grown[it] > 0.5f) 1f else 0f }
    }

    /**
     * Rimette il risultato (3×512×512, valori 0..255) nella foto: solo nella zona allargata,
     * con un bordo sfumato di qualche pixel. Restituisce i nuovi pixel dell'intera foto.
     */
    fun blend(px: IntArray, mask: FloatArray, w: Int, h: Int, c: Crop, out: FloatArray): IntArray {
        val plane = SIDE * SIDE
        val chans = Array(3) { k -> SubjectEngine.upscale(FloatArray(plane) { out[k * plane + it] / 255f }, SIDE, SIDE, c.w, c.h) }
        // zona di sostituzione: maschera allargata (come quella data al modello) e sfumata
        val m = cropMask(mask, w, c)
        val hard = FloatArray(m.size) { if (m[it] > 0.01f) 1f else 0f }
        val scale = max(c.w, c.h).toFloat() / SIDE
        val grown = CutoutEngine.grow(hard, c.w, c.h, max(2, (4 * scale).roundToInt()))
        val soft = com.example.fx.FxBlur.boxF(grown, c.w, c.h, max(1, (2 * scale).roundToInt()))
        val res = px.copyOf()
        for (y in 0 until c.h) for (x in 0 until c.w) {
            val i = y * c.w + x
            val a = max(soft[i], hard[i]).coerceIn(0f, 1f)
            if (a <= 0f) continue
            val gi = (c.t + y) * w + (c.l + x)
            val p = px[gi]
            val r = ((p shr 16) and 255) * (1 - a) + (chans[0][i] * 255f) * a
            val g = ((p shr 8) and 255) * (1 - a) + (chans[1][i] * 255f) * a
            val b = (p and 255) * (1 - a) + (chans[2][i] * 255f) * a
            res[gi] = (p and 0xFF000000.toInt()) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
        return res
    }
}
