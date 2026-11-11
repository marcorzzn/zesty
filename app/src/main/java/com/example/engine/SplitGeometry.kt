package com.example.engine

import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Divisione della foto in pannelli da salvare come file separati (Esporta → Dividi e salva).
 * Due modi:
 * - GRIGLIA: colonne × righe uguali su tutta la foto;
 * - PROPORZIONE: N pannelli della proporzione scelta, in fila (orizzontale) o in colonna (verticale),
 *   la striscia più grande possibile dentro la foto, spostabile lungo l'asse libero.
 * Lo spazio fra i pannelli viene tolto (taglio con intervallo), così ogni pannello mantiene la proporzione.
 */
object SplitGeometry {

    data class Settings(
        val mode: Int = 0,            // 0 griglia, 1 proporzione
        val cols: Int = 3,
        val rows: Int = 1,
        val ratioW: Float = 4f,
        val ratioH: Float = 5f,
        val count: Int = 3,
        val vertical: Boolean = false, // pannelli in colonna invece che in fila
        val position: Float = 0.5f,   // 0..1 posizione della striscia lungo l'asse libero
        val gap: Int = 0              // pixel tolti fra un pannello e l'altro
    )

    fun rects(w: Int, h: Int, s: Settings): List<Rect> = if (s.mode == 0) grid(w, h, s) else ratio(w, h, s)

    private fun grid(w: Int, h: Int, s: Settings): List<Rect> {
        val c = s.cols.coerceIn(1, 20); val r = s.rows.coerceIn(1, 20)
        val g = s.gap.coerceAtLeast(0)
        val cw = (w - g * (c - 1)).toFloat() / c; val rh = (h - g * (r - 1)).toFloat() / r
        if (cw < 1f || rh < 1f) return emptyList()
        val out = ArrayList<Rect>(c * r)
        for (y in 0 until r) for (x in 0 until c) {
            val l = (x * (cw + g)).roundToInt(); val t = (y * (rh + g)).roundToInt()
            out += Rect(l, t, min(w, (l + cw).roundToInt()), min(h, (t + rh).roundToInt()))
        }
        return out
    }

    private fun ratio(w: Int, h: Int, s: Settings): List<Rect> {
        val n = s.count.coerceIn(1, 20)
        val g = s.gap.coerceAtLeast(0)
        val k = (s.ratioW / s.ratioH.coerceAtLeast(0.01f)).coerceIn(0.05f, 20f) // larghezza / altezza del pannello
        // striscia più grande possibile dentro la foto
        var pw: Float; var ph: Float
        if (!s.vertical) {
            ph = h.toFloat(); pw = ph * k
            if (pw * n + g * (n - 1) > w) { pw = (w - g * (n - 1)).toFloat() / n; ph = pw / k }
        } else {
            pw = w.toFloat(); ph = pw / k
            if (ph * n + g * (n - 1) > h) { ph = (h - g * (n - 1)).toFloat() / n; pw = ph * k }
        }
        if (pw < 1f || ph < 1f) return emptyList()
        val stripW = if (s.vertical) pw else pw * n + g * (n - 1)
        val stripH = if (s.vertical) ph * n + g * (n - 1) else ph
        val p = s.position.coerceIn(0f, 1f)
        // la posizione sposta la striscia lungo l'asse in cui avanza spazio (l'altro è pieno)
        val ox = max(0f, (w - stripW)) * p
        val oy = max(0f, (h - stripH)) * p
        return List(n) { i ->
            val l = if (s.vertical) ox else ox + i * (pw + g)
            val t = if (s.vertical) oy + i * (ph + g) else oy
            Rect(l.roundToInt(), t.roundToInt(), min(w, (l + pw).roundToInt()), min(h, (t + ph).roundToInt()))
        }
    }
}
