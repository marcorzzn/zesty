package com.example.engine

import android.graphics.PointF
import kotlin.math.*

/**
 * Trasformazione dei livelli secondo le convenzioni degli editor professionali:
 * - ogni gesto riparte dalla geometria di inizio trascinamento (nessuna deriva accumulata);
 * - il ridimensionamento tiene fermo il lato/angolo opposto, nel sistema ruotato del livello;
 * - gli angoli mantengono le proporzioni (per immagini e testi), i lati allungano su un asse;
 * - le maniglie hanno un'area di tocco costante sullo schermo, a qualsiasi zoom;
 * - la rotazione si aggancia a 0°/45°/90°... vicino a quegli angoli.
 * Coordinate: spazio della tela.
 */
object TransformGeometry {

    /** Geometria di un livello: centro, dimensioni, rotazione in gradi. */
    data class Box(val cx: Float, val cy: Float, val w: Float, val h: Float, val rotation: Float) {
        val x get() = cx - w / 2f
        val y get() = cy - h / 2f
    }

    /** sx, sy = direzione della maniglia rispetto al centro (-1, 0, +1). */
    enum class Handle(val sx: Int, val sy: Int) {
        TL(-1, -1), T(0, -1), TR(1, -1), R(1, 0), BR(1, 1), B(0, 1), BL(-1, 1), L(-1, 0),
        ROTATE(0, 0), DELETE(0, 0), BODY(0, 0);
        val isCorner get() = sx != 0 && sy != 0
        val isSide get() = (sx == 0) != (sy == 0)
    }

    fun box(x: Float, y: Float, w: Float, h: Float, rotation: Float) = Box(x + w / 2f, y + h / 2f, w, h, rotation)

    fun toWorld(b: Box, lx: Float, ly: Float): PointF {
        val r = Math.toRadians(b.rotation.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return PointF(b.cx + lx * c - ly * s, b.cy + lx * s + ly * c)
    }

    fun toLocal(b: Box, x: Float, y: Float): PointF {
        val r = Math.toRadians(-b.rotation.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val dx = x - b.cx; val dy = y - b.cy
        return PointF(dx * c - dy * s, dx * s + dy * c)
    }

    /**
     * Posizioni delle maniglie. [rotateOffset] e [deleteOffset] sono distanze in unità della tela
     * (calcolate dal chiamante da dp di schermo, così restano costanti allo zoom).
     */
    fun handles(b: Box, rotateOffset: Float, deleteOffset: Float): Map<Handle, PointF> {
        val hw = b.w / 2f; val hh = b.h / 2f
        val m = LinkedHashMap<Handle, PointF>()
        for (h in Handle.entries) if (h != Handle.ROTATE && h != Handle.DELETE && h != Handle.BODY) m[h] = toWorld(b, h.sx * hw, h.sy * hh)
        m[Handle.ROTATE] = toWorld(b, 0f, -hh - rotateOffset)
        // l'eliminazione sta fuori dall'angolo, lungo la diagonale: non copre la maniglia
        val diag = deleteOffset / sqrt(2f)
        m[Handle.DELETE] = toWorld(b, -hw - diag, -hh - diag)
        return m
    }

    /**
     * Quale maniglia c'è sotto il dito. Le maniglie vincono sul corpo; i lati si nascondono
     * quando il livello sullo schermo è troppo piccolo per distinguerli dagli angoli.
     */
    fun hit(b: Box, x: Float, y: Float, radius: Float, rotateOffset: Float, deleteOffset: Float, showSides: Boolean): Handle? {
        val hs = handles(b, rotateOffset, deleteOffset)
        var best: Handle? = null; var bestD = radius
        for ((h, p) in hs) {
            if (h.isSide && !showSides) continue
            val d = hypot(p.x - x, p.y - y)
            if (d <= bestD) { bestD = d; best = h }
        }
        if (best != null) return best
        val l = toLocal(b, x, y)
        return if (abs(l.x) <= b.w / 2f && abs(l.y) <= b.h / 2f) Handle.BODY else null
    }

    /**
     * Nuova geometria dal punto corrente del dito, partendo dalla geometria iniziale [start].
     * @param keepRatio angoli proporzionali
     * @param fromCenter ridimensiona simmetricamente attorno al centro
     */
    fun resize(start: Box, handle: Handle, x: Float, y: Float, keepRatio: Boolean, fromCenter: Boolean, minSize: Float): Box {
        if (!handle.isCorner && !handle.isSide) return start
        val p = toLocal(start, x, y)
        val hw = start.w / 2f; val hh = start.h / 2f
        // ancora = lato/angolo opposto (o il centro)
        val ax = if (fromCenter) 0f else -handle.sx * hw
        val ay = if (fromCenter) 0f else -handle.sy * hh
        val k = if (fromCenter) 2f else 1f
        var nw = if (handle.sx != 0) ((p.x - ax) * handle.sx * k).coerceAtLeast(minSize) else start.w
        var nh = if (handle.sy != 0) ((p.y - ay) * handle.sy * k).coerceAtLeast(minSize) else start.h
        if (keepRatio && handle.isCorner) {
            // proiezione sulla diagonale: segue il dito in modo naturale in entrambe le direzioni
            val s = max(nw / start.w, nh / start.h).coerceAtLeast(minSize / min(start.w, start.h))
            nw = start.w * s; nh = start.h * s
        }
        val ncx = if (fromCenter || handle.sx == 0) 0f else ax + handle.sx * nw / 2f
        val ncy = if (fromCenter || handle.sy == 0) 0f else ay + handle.sy * nh / 2f
        val c = toWorld(start, ncx, ncy)
        return start.copy(cx = c.x, cy = c.y, w = nw, h = nh)
    }

    /** Rotazione: l'angolo segue il dito rispetto al centro, con aggancio vicino ai multipli di 45°. */
    fun rotate(start: Box, startAngleDeg: Float, x: Float, y: Float, snapDeg: Float = 4f): Box {
        val a = Math.toDegrees(atan2((y - start.cy).toDouble(), (x - start.cx).toDouble())).toFloat()
        var r = start.rotation + (a - startAngleDeg)
        r = ((r % 360f) + 360f) % 360f
        val nearest = (r / 45f).roundToInt() * 45f
        if (abs(r - nearest) <= snapDeg) r = nearest % 360f
        return start.copy(rotation = r)
    }

    fun angleOf(b: Box, x: Float, y: Float): Float =
        Math.toDegrees(atan2((y - b.cy).toDouble(), (x - b.cx).toDouble())).toFloat()
}
