package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Motore dei pennelli di Zesty.
 *
 * Ogni tratto si dipinge in un **buffer** a parte (a "impronte" ravvicinate, come i pennelli dei programmi
 * professionali) e solo poi si fonde sul livello con l'opacità scelta: l'opacità resta uniforme lungo tutto
 * il tratto, senza nodi più scuri dove i pezzi si sovrappongono. Il **flusso** invece si accumula
 * (aerografo, acquerello).
 * Le coordinate arrivano già in pixel del bitmap del livello (vedi [BrushGeometry]).
 */
object BrushEngine {

    /** Tipi di pennello. */
    enum class Kind(val label: String) {
        ROUND("Tondo"),
        PENCIL("Matita"),
        MARKER("Pennarello"),
        AIRBRUSH("Aerografo"),
        WATERCOLOR("Acquerello"),
        CHALK("Gessetto"),
        NEON("Neon"),
        SPRAY("Spray"),
        GLITTER("Brillantini"),
        SMUDGE("Sfumino"),
        ERASER("Gomma");

        /** Valori di partenza consigliati per il tipo (dimensione, flusso, durezza, spaziatura, dispersione). */
        val preset: Params
            get() = when (this) {
                ROUND -> Params(size = 28f, flow = 100f, hardness = 70f, spacing = 8f)
                PENCIL -> Params(size = 6f, flow = 100f, hardness = 95f, spacing = 6f, scatter = 4f)
                MARKER -> Params(size = 30f, flow = 100f, hardness = 90f, spacing = 5f, opacity = 70f)
                AIRBRUSH -> Params(size = 80f, flow = 12f, hardness = 0f, spacing = 6f)
                WATERCOLOR -> Params(size = 60f, flow = 22f, hardness = 20f, spacing = 6f, opacity = 80f)
                CHALK -> Params(size = 36f, flow = 80f, hardness = 60f, spacing = 10f, scatter = 10f)
                NEON -> Params(size = 18f, flow = 100f, hardness = 85f, spacing = 6f)
                SPRAY -> Params(size = 70f, flow = 60f, hardness = 100f, spacing = 15f, scatter = 100f)
                GLITTER -> Params(size = 60f, flow = 100f, hardness = 100f, spacing = 25f, scatter = 100f)
                SMUDGE -> Params(size = 40f, flow = 70f, hardness = 30f, spacing = 8f)
                ERASER -> Params(size = 40f, flow = 100f, hardness = 70f, spacing = 8f)
            }
    }

    /**
     * Parametri del tratto. Misure in pixel del bitmap; percentuali 0..100.
     * @param flow quanto colore lascia ogni impronta (basso = si accumula passando più volte)
     * @param spacing distanza tra le impronte in % del diametro
     * @param scatter dispersione delle impronte e variazione di dimensione
     * @param smoothing stabilizzatore: il tratto segue il dito con un leggero ritardo e diventa più morbido
     */
    data class Params(
        val size: Float = 28f,
        val opacity: Float = 100f,
        val flow: Float = 100f,
        val hardness: Float = 70f,
        val spacing: Float = 8f,
        val scatter: Float = 0f,
        val smoothing: Float = 30f,
        val color: Int = Color.BLACK
    )

    /**
     * Un tratto in corso su [target] (il bitmap del livello, modificabile).
     * [render] aggiorna solo la zona toccata; [finish] applica gli ultimi ritocchi (bordi dell'acquerello).
     */
    class Stroke(private val target: Bitmap, val kind: Kind, private val p: Params, seed: Int = 7) {
        private val w = target.width
        private val h = target.height
        private val base: Bitmap = target.copy(Bitmap.Config.ARGB_8888, false)
        private val buffer: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        private val bufCanvas = Canvas(buffer)
        private val tgtCanvas = Canvas(target)
        private val rnd = Random(seed)
        private val dirty = Rect()
        private val pending = Rect()
        private var lastX = Float.NaN
        private var lastY = Float.NaN
        private var smX = 0f
        private var smY = 0f
        private var carry = 0f // distanza percorsa non ancora coperta da un'impronta
        private val tip: Bitmap = makeTip()
        private val tipPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (p.flow.coerceIn(1f, 100f) / 100f * 255).roundToInt() }
        private val grain: Bitmap? = if (kind == Kind.CHALK || kind == Kind.PENCIL || kind == Kind.WATERCOLOR) makeGrain() else null

        private val radius get() = max(0.5f, p.size / 2f)

        /** Impronta del pennello: cerchio con bordo morbido secondo la durezza, già del colore scelto. */
        private fun makeTip(): Bitmap {
            val d = max(2, (p.size * (if (kind == Kind.NEON) 1.8f else 1f)).roundToInt() + 2)
            val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = d / 2f - 1f
            val col = if (kind == Kind.ERASER || kind == Kind.SMUDGE) Color.WHITE else p.color
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            when (kind) {
                Kind.MARKER -> {
                    // punta a scalpello arrotondata: bordi netti, colore piatto
                    paint.color = col
                    c.drawRoundRect(RectF(d * 0.15f, d * 0.05f, d * 0.85f, d * 0.95f), d * 0.2f, d * 0.2f, paint)
                }
                Kind.NEON -> {
                    // alone colorato morbido + anima chiara
                    paint.shader = RadialGradient(d / 2f, d / 2f, r, intArrayOf(col, withAlpha(col, 90), withAlpha(col, 0)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                    c.drawCircle(d / 2f, d / 2f, r, paint)
                    paint.shader = null
                    paint.color = mixToWhite(col, 0.75f)
                    c.drawCircle(d / 2f, d / 2f, r * 0.22f, paint)
                }
                else -> {
                    val hard = (p.hardness / 100f).coerceIn(0f, 0.99f)
                    if (hard >= 0.97f) {
                        paint.color = col
                        c.drawCircle(d / 2f, d / 2f, r, paint)
                    } else {
                        paint.shader = RadialGradient(d / 2f, d / 2f, r, intArrayOf(col, col, withAlpha(col, 0)), floatArrayOf(0f, hard, 1f), Shader.TileMode.CLAMP)
                        c.drawCircle(d / 2f, d / 2f, r, paint)
                    }
                }
            }
            return bmp
        }

        /** Grana della carta (gessetto, matita, acquerello): rumore fisso, ripetuto. */
        private fun makeGrain(): Bitmap {
            val n = 64
            val r = Random(11)
            val px = IntArray(n * n) {
                val v = when (kind) {
                    Kind.CHALK -> if (r.nextFloat() < 0.55f) 255 else (r.nextFloat() * 90).toInt()
                    Kind.PENCIL -> (150 + r.nextFloat() * 105).toInt()
                    else -> (190 + r.nextFloat() * 65).toInt()
                }
                (v shl 24) or 0xFFFFFF
            }
            return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        }

        /** Aggiunge un punto del dito (pixel del bitmap). */
        fun addPoint(x: Float, y: Float) {
            if (lastX.isNaN()) {
                smX = x; smY = y
                lastX = x; lastY = y
                dab(x, y)
                return
            }
            // stabilizzatore: media mobile esponenziale
            val k = 1f - (p.smoothing.coerceIn(0f, 95f) / 100f) * 0.85f
            smX += (x - smX) * k
            smY += (y - smY) * k
            val dx = smX - lastX; val dy = smY - lastY
            val dist = hypot(dx, dy)
            val step = max(0.5f, p.size * p.spacing.coerceIn(1f, 200f) / 100f)
            var t = step - carry
            while (t <= dist) {
                dab(lastX + dx * t / dist, lastY + dy * t / dist)
                t += step
            }
            carry = dist - (t - step)
            lastX = smX; lastY = smY
        }

        private fun dab(x: Float, y: Float) {
            when (kind) {
                Kind.SMUDGE -> { smudge(x, y); return }
                Kind.SPRAY, Kind.GLITTER -> { particles(x, y); return }
                else -> {}
            }
            var cx = x; var cy = y
            var scale = 1f
            if (p.scatter > 0f) {
                val a = rnd.nextFloat() * 2f * PI.toFloat()
                val r = rnd.nextFloat() * radius * p.scatter / 100f
                cx += cos(a) * r; cy += sin(a) * r
                scale = 1f - rnd.nextFloat() * 0.35f * p.scatter / 100f
            }
            val half = tip.width * scale / 2f
            val dst = RectF(cx - half, cy - half, cx + half, cy + half)
            bufCanvas.drawBitmap(tip, null, dst, tipPaint)
            grow(dst)
        }

        /** Spray e brillantini: puntini sparsi nel raggio. */
        private fun particles(x: Float, y: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val count = if (kind == Kind.GLITTER) max(3, (p.size / 6f).roundToInt()) else max(8, (p.size * 0.9f).roundToInt())
            for (i in 0 until count) {
                val a = rnd.nextFloat() * 2f * PI.toFloat()
                val r = radius * kotlin.math.sqrt(rnd.nextFloat())
                val px = x + cos(a) * r; val py = y + sin(a) * r
                if (kind == Kind.GLITTER) {
                    val s = 1f + rnd.nextFloat() * max(1.5f, p.size / 18f)
                    paint.color = if (rnd.nextFloat() < 0.35f) Color.WHITE else mixToWhite(p.color, rnd.nextFloat() * 0.5f)
                    bufCanvas.drawCircle(px, py, s, paint)
                    if (rnd.nextFloat() < 0.25f) { // scintilla a croce
                        paint.strokeWidth = max(1f, s * 0.4f)
                        bufCanvas.drawLine(px - s * 3, py, px + s * 3, py, paint)
                        bufCanvas.drawLine(px, py - s * 3, px, py + s * 3, paint)
                    }
                } else {
                    paint.color = withAlpha(p.color, (p.flow / 100f * 255).roundToInt().coerceIn(10, 255))
                    bufCanvas.drawCircle(px, py, max(0.6f, p.size / 60f), paint)
                }
            }
            grow(RectF(x - radius - 12, y - radius - 12, x + radius + 12, y + radius + 12))
        }

        /** Sfumino: porta con sé i pixel da dove passa il dito (lavora direttamente sul livello). */
        private var smudgePrevX = Float.NaN
        private var smudgePrevY = Float.NaN
        private fun smudge(x: Float, y: Float) {
            if (!smudgePrevX.isNaN()) {
                val d = tip.width
                val sx = (smudgePrevX - d / 2f).roundToInt(); val sy = (smudgePrevY - d / 2f).roundToInt()
                val src = Rect(sx, sy, sx + d, sy + d)
                if (src.intersect(0, 0, w, h)) {
                    val patch = Bitmap.createBitmap(target, src.left, src.top, src.width(), src.height())
                        .copy(Bitmap.Config.ARGB_8888, true)
                    // forma morbida del pennello sulla porzione presa
                    val pc = Canvas(patch)
                    val mask = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                    pc.drawBitmap(tip, null, RectF((sx - src.left).toFloat(), (sy - src.top).toFloat(), (sx - src.left + d).toFloat(), (sy - src.top + d).toFloat()), mask)
                    val dx = x - smudgePrevX; val dy = y - smudgePrevY
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (p.flow.coerceIn(5f, 100f) / 100f * 230).roundToInt() }
                    tgtCanvas.drawBitmap(patch, src.left + dx, src.top + dy, paint)
                    grow(RectF(src.left + dx, src.top + dy, src.right + dx, src.bottom + dy))
                }
            }
            smudgePrevX = x; smudgePrevY = y
        }

        private fun grow(r: RectF) {
            val rr = Rect(r.left.toInt() - 2, r.top.toInt() - 2, r.right.toInt() + 3, r.bottom.toInt() + 3)
            if (!rr.intersect(0, 0, w, h)) return
            if (pending.isEmpty) pending.set(rr) else pending.union(rr)
            if (dirty.isEmpty) dirty.set(rr) else dirty.union(rr)
        }

        /** Aggiorna il livello nella zona toccata dall'ultimo [render]: base + tratto con l'opacità scelta. */
        fun render() {
            if (pending.isEmpty) return
            if (kind != Kind.SMUDGE) composeRegion(pending)
            pending.setEmpty()
        }

        private fun composeRegion(r: Rect) {
            val copy = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
            tgtCanvas.drawBitmap(base, r, r, copy)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (p.opacity.coerceIn(1f, 100f) / 100f * 255).roundToInt()
                if (kind == Kind.ERASER) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            if (grain != null) {
                // grana: il tratto si interrompe sulla trama della "carta"
                val grained = Bitmap.createBitmap(buffer, r.left, r.top, r.width(), r.height()).copy(Bitmap.Config.ARGB_8888, true)
                val gp = Paint().apply {
                    shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also {
                        it.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-r.left.toFloat(), -r.top.toFloat()) })
                    }
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                Canvas(grained).drawRect(0f, 0f, r.width().toFloat(), r.height().toFloat(), gp)
                tgtCanvas.drawBitmap(grained, r.left.toFloat(), r.top.toFloat(), paint)
            } else {
                tgtCanvas.drawBitmap(buffer, r, r, paint)
            }
        }

        /** Fine del tratto: l'acquerello scurisce leggermente i bordi, come il pigmento che si deposita. */
        fun finish(): Rect {
            if (kind == Kind.WATERCOLOR && !dirty.isEmpty) {
                val r = Rect(dirty)
                val rw = r.width(); val rh = r.height()
                val px = IntArray(rw * rh)
                buffer.getPixels(px, 0, rw, r.left, r.top, rw, rh)
                val a = FloatArray(rw * rh) { ((px[it] ushr 24) and 255) / 255f }
                val blur = com.example.fx.FxBlur.boxF(a, rw, rh, max(2, (p.size / 8f).roundToInt()))
                for (i in px.indices) {
                    val edge = (a[i] - blur[i]).coerceAtLeast(0f) * 1.6f
                    val na = (a[i] * 0.85f + edge).coerceIn(0f, 1f)
                    px[i] = ((na * 255f).roundToInt() shl 24) or (px[i] and 0xFFFFFF)
                }
                buffer.setPixels(px, 0, rw, r.left, r.top, rw, rh)
                pending.set(r)
            }
            render()
            return Rect(dirty)
        }

        private fun withAlpha(c: Int, a: Int) = (a.coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)
        private fun mixToWhite(c: Int, t: Float): Int {
            val r = ((c shr 16) and 255); val g = ((c shr 8) and 255); val b = c and 255
            return Color.rgb((r + (255 - r) * t).roundToInt(), (g + (255 - g) * t).roundToInt(), (b + (255 - b) * t).roundToInt())
        }
    }
}

/**
 * Dal punto toccato sulla tela al pixel del bitmap del livello, tenendo conto di posizione, dimensione
 * e rotazione del livello e della risoluzione reale del bitmap (che può essere diversa dalle misure del livello).
 */
object BrushGeometry {
    data class Mapping(val x: Float, val y: Float, val pxPerUnit: Float)

    fun toBitmap(canvasX: Float, canvasY: Float, layerX: Float, layerY: Float, layerW: Float, layerH: Float, rotation: Float, bmpW: Int, bmpH: Int): Mapping {
        val box = TransformGeometry.box(layerX, layerY, layerW, layerH, rotation)
        val l = TransformGeometry.toLocal(box, canvasX, canvasY)
        val u = l.x / layerW + 0.5f
        val v = l.y / layerH + 0.5f
        val sx = bmpW / layerW.coerceAtLeast(1f)
        val sy = bmpH / layerH.coerceAtLeast(1f)
        return Mapping(u * bmpW, v * bmpH, (sx + sy) / 2f)
    }

}
