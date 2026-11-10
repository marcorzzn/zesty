package com.example.engine

import android.graphics.*
import com.example.model.EditorLayer
import com.example.model.LayerType
import com.example.model.MaskShapeDraft
import com.example.model.ShapeKind
import kotlin.math.*

/**
 * Geometria e rasterizzazione delle zone (maschere) per effetti e livelli.
 * Coordinate: la bozza vive nello spazio della tela; la maschera nello spazio del livello
 * (tela - posizione del livello), come il pennello selettivo esistente.
 */
object MaskGeometry {

    /** Forme utilizzabili come zona (la linea non delimita un'area). */
    val SHAPES: List<ShapeKind> = ShapeKind.entries.filter { it != ShapeKind.LINE }

    // ---------------------------------------------------------------- maniglie della bozza
    /** 0..3 angoli (alto-sx, alto-dx, basso-dx, basso-sx), 4 = rotazione. */
    fun handles(d: MaskShapeDraft, knobOffset: Float): List<PointF> {
        val pts = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (sx, sy) -> local(d, sx * d.w / 2f, sy * d.h / 2f) }
        return pts + local(d, 0f, -d.h / 2f - knobOffset)
    }

    /** Da coordinate locali della forma (centro = 0,0) a coordinate della tela. */
    fun local(d: MaskShapeDraft, lx: Float, ly: Float): PointF {
        val a = Math.toRadians(d.rotation.toDouble())
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        return PointF(d.cx + lx * c - ly * s, d.cy + lx * s + ly * c)
    }

    /** Da coordinate della tela a coordinate locali della forma. */
    fun toLocal(d: MaskShapeDraft, x: Float, y: Float): PointF {
        val a = Math.toRadians(-d.rotation.toDouble())
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        val dx = x - d.cx; val dy = y - d.cy
        return PointF(dx * c - dy * s, dx * s + dy * c)
    }

    fun contains(d: MaskShapeDraft, x: Float, y: Float): Boolean {
        val p = toLocal(d, x, y)
        return abs(p.x) <= d.w / 2f && abs(p.y) <= d.h / 2f
    }

    /** Contorno della forma nello spazio della tela (per l'anteprima a schermo). */
    fun outline(d: MaskShapeDraft): Path {
        val ref = EditorLayer(id = "mask", type = LayerType.SHAPE, name = "mask", shapeKind = d.kind, cornerRadius = min(d.w, d.h) * 0.12f)
        val path = PixelEngine.buildShapePath(d.w.coerceAtLeast(2f), d.h.coerceAtLeast(2f), ref)
        val m = Matrix()
        m.postTranslate(-d.w / 2f, -d.h / 2f)
        m.postRotate(d.rotation)
        m.postTranslate(d.cx, d.cy)
        path.transform(m)
        return path
    }

    fun polygonPath(points: List<PointF>): Path {
        val p = Path()
        if (points.isEmpty()) return p
        p.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) p.lineTo(points[i].x, points[i].y)
        p.close()
        return p
    }

    // ---------------------------------------------------------------- poligono modellabile
    fun nearestPoint(points: List<PointF>, x: Float, y: Float, radius: Float): Int {
        var best = -1; var bestD = radius
        points.forEachIndexed { i, p -> val d = hypot(p.x - x, p.y - y); if (d <= bestD) { bestD = d; best = i } }
        return best
    }

    /** Indice dopo cui inserire un punto se (x,y) è vicino a un lato del poligono chiuso, altrimenti -1. */
    fun nearestSegment(points: List<PointF>, x: Float, y: Float, radius: Float): Int {
        if (points.size < 2) return -1
        var best = -1; var bestD = radius
        for (i in points.indices) {
            val a = points[i]; val b = points[(i + 1) % points.size]
            val vx = b.x - a.x; val vy = b.y - a.y
            val len2 = vx * vx + vy * vy
            val t = if (len2 == 0f) 0f else (((x - a.x) * vx + (y - a.y) * vy) / len2).coerceIn(0f, 1f)
            val d = hypot(a.x + vx * t - x, a.y + vy * t - y)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** Riduce i punti del lazo mantenendo la forma (distanza minima fra punti). */
    fun simplify(points: List<PointF>, minDist: Float): List<PointF> {
        if (points.size < 3) return points
        val out = mutableListOf(points.first())
        for (p in points.drop(1)) if (hypot(p.x - out.last().x, p.y - out.last().y) >= minDist) out += p
        return out
    }

    // ---------------------------------------------------------------- rasterizzazione
    /**
     * Disegna un contorno (spazio tela) nella maschera del livello.
     * @param subtract true = toglie la zona, false = la aggiunge
     * @param feather sfumatura del bordo in unità della tela
     */
    fun paint(mask: Bitmap, canvasPath: Path, layer: EditorLayer, subtract: Boolean, feather: Float): Bitmap {
        val out = mask.copy(Bitmap.Config.ARGB_8888, true)
        val path = Path(canvasPath)
        // dalla tela allo spazio del livello, scalando se la maschera ha dimensioni diverse dal livello
        val m = Matrix()
        m.postTranslate(-layer.x, -layer.y)
        m.postScale(out.width / layer.w.coerceAtLeast(1f), out.height / layer.h.coerceAtLeast(1f))
        path.transform(m)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            if (feather > 0.5f) maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
            if (subtract) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        Canvas(out).drawPath(path, paint)
        return out
    }

    /** Sfuma i bordi dell'intera maschera (raggio in pixel). */
    fun blurMask(mask: Bitmap, radius: Float): Bitmap {
        if (radius < 0.5f) return mask
        val w = mask.width; val h = mask.height
        val px = IntArray(w * h)
        mask.getPixels(px, 0, w, 0, 0, w, h)
        val blurred = com.example.fx.FxBlur.gaussian(px, w, h, radius)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // la maschera usa l'alpha: si sfoca solo quel canale, il colore resta bianco
        for (i in blurred.indices) blurred[i] = (blurred[i] and 0xFF000000.toInt()) or 0x00FFFFFF
        out.setPixels(blurred, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Fonde il risultato di un effetto sull'originale secondo la maschera (alpha).
     * inverted = l'effetto va fuori dalla zona.
     */
    fun blendThroughMask(original: Bitmap, effected: Bitmap, mask: Bitmap, inverted: Boolean): Bitmap {
        val w = original.width; val h = original.height
        val fx = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val fc = Canvas(fx)
        fc.drawBitmap(effected, 0f, 0f, null)
        val mp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(if (inverted) PorterDuff.Mode.DST_OUT else PorterDuff.Mode.DST_IN)
        }
        fc.drawBitmap(mask, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), mp)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        oc.drawBitmap(original, 0f, 0f, null)
        oc.drawBitmap(fx, 0f, 0f, null)
        return out
    }
}
