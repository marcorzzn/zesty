package com.example.engine

import android.graphics.*
import android.os.Build
import com.example.model.*
import com.example.fx.FxEngine
import kotlin.math.*
import kotlin.random.Random

object PixelEngine {

    /**
     * Generates the procedural demo image (stylized horse + stitched ball on dark textured fabric)
     * identical to the HTML mockup's `createDemoImage()`.
     */
    fun createDemoBitmap(width: Int = 800, height: Int = 800): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Radial dark background
        val radGrad = RadialGradient(
            width * 0.5f, height * 0.5f, width * 0.75f,
            intArrayOf(Color.parseColor("#2A2A35"), Color.parseColor("#050508")),
            floatArrayOf(0.15f, 1f),
            Shader.TileMode.CLAMP
        )
        p.shader = radGrad
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null

        // Subtle fabric noise
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
        val rng = Random(42)
        for (i in pixels.indices) {
            val px = pixels[i]
            val n = rng.nextInt(-18, 19)
            val r = (Color.red(px) + n).coerceIn(0, 255)
            val g = (Color.green(px) + n).coerceIn(0, 255)
            val b = (Color.blue(px) + n).coerceIn(0, 255)
            pixels[i] = Color.argb(255, r, g, b)
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        // Diagonal fabric sheen
        val diagGrad = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.argb(128, 0, 0, 0), Color.argb(10, 255, 255, 255), Color.argb(128, 0, 0, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        p.shader = diagGrad
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null

        // Ball dark outline shadow
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#1A1A1F")
        c.drawCircle(400f, 600f, 140f, p)

        // White/cream ball
        p.color = Color.parseColor("#F5F2EA")
        c.drawCircle(400f, 600f, 130f, p)

        // Ball stitches
        p.style = Paint.Style.STROKE
        p.color = Color.parseColor("#2A2A30")
        p.strokeWidth = 5f
        p.strokeCap = Paint.Cap.ROUND
        c.drawCircle(400f, 600f, 130f, p)

        val stitch = Path().apply {
            moveTo(400f, 470f); lineTo(400f, 530f)
            moveTo(340f, 540f); quadTo(400f, 590f, 460f, 540f)
            moveTo(340f, 660f); quadTo(400f, 610f, 460f, 660f)
            moveTo(270f, 600f); lineTo(340f, 600f)
            moveTo(460f, 600f); lineTo(530f, 600f)
            moveTo(320f, 520f); quadTo(360f, 570f, 360f, 630f)
            moveTo(480f, 520f); quadTo(440f, 570f, 440f, 630f)
        }
        c.drawPath(stitch, p)

        // Stylized horse silhouette
        val horse = Path().apply {
            moveTo(430f, 220f)
            quadTo(470f, 200f, 490f, 230f)
            quadTo(500f, 260f, 480f, 300f)
            quadTo(460f, 320f, 430f, 320f)
            quadTo(400f, 300f, 400f, 280f)
            quadTo(380f, 260f, 360f, 240f)
            quadTo(390f, 240f, 410f, 260f)
            quadTo(400f, 220f, 380f, 200f)
            quadTo(420f, 200f, 440f, 210f)
            quadTo(380f, 350f, 350f, 400f)
            quadTo(300f, 420f, 270f, 460f)
            quadTo(320f, 450f, 340f, 470f)
            quadTo(380f, 500f, 400f, 500f)
            quadTo(430f, 490f, 460f, 460f)
            quadTo(470f, 420f, 500f, 400f)
            quadTo(510f, 380f, 500f, 360f)
            quadTo(490f, 380f, 480f, 400f)
            quadTo(480f, 450f, 460f, 500f)
            quadTo(450f, 400f, 440f, 340f)
            quadTo(445f, 310f, 445f, 300f)
            close()
        }
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#F5F2EA")
        c.drawPath(horse, p)

        p.style = Paint.Style.STROKE
        p.color = Color.parseColor("#1A1A1F")
        p.strokeWidth = 8f
        p.strokeJoin = Paint.Join.ROUND
        c.drawPath(horse, p)

        return bmp
    }

    /**
     * Renders the complete multi-layer composition from bottom to top into a Bitmap.
     */
    fun renderComposite(
        canvasW: Int,
        canvasH: Int,
        bgColor: Int,
        fitBgMode: Int,
        borderOuter: Float,
        borderInner: Float,
        borderRadius: Float,
        borderColor: Int,
        layers: List<EditorLayer>,
        scale: Float = previewScale(canvasW, canvasH)
    ): Bitmap {
        val w = canvasW.coerceAtLeast(1)
        val h = canvasH.coerceAtLeast(1)
        // durante i gesti la tela si compone a ~INTERACTIVE_SIDE px (circa la risoluzione dello schermo)
        val sc = scale.coerceIn(0.05f, 1f)
        val ow = if (sc >= 1f) w else (w * sc).roundToInt().coerceAtLeast(1)
        val oh = if (sc >= 1f) h else (h * sc).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (ow != w || oh != h) canvas.scale(ow / w.toFloat(), oh / h.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 1. Base background
        when (fitBgMode) {
            1 -> { // Checker/Diagonal Pattern
                paint.style = Paint.Style.FILL
                paint.color = bgColor
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.color = Color.argb(40, 255, 255, 255)
                val step = 32f
                var y = 0f
                var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) 0f else step
                    while (x < w) {
                        canvas.drawRect(x, y, (x + step).coerceAtMost(w.toFloat()), (y + step).coerceAtMost(h.toFloat()), paint)
                        x += step * 2
                    }
                    y += step
                    row++
                }
            }
            2 -> { // Blurred first image layer background
                paint.style = Paint.Style.FILL
                paint.color = bgColor
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                val firstBmp = layers.firstOrNull { it.bitmap != null }?.bitmap
                if (firstBmp != null) {
                    val blurred = boxBlur(Bitmap.createScaledBitmap(firstBmp, (w / 4).coerceAtLeast(16), (h / 4).coerceAtLeast(16), true), 6)
                    val dst = RectF(0f, 0f, w.toFloat(), h.toFloat())
                    canvas.drawBitmap(blurred, null, dst, paint)
                    paint.color = Color.argb(90, 0, 0, 0)
                    canvas.drawRect(dst, paint)
                }
            }
            else -> {
                if (Color.alpha(bgColor) > 0) {
                    paint.style = Paint.Style.FILL
                    paint.color = bgColor
                    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                }
            }
        }

        // 2. Render each visible layer bottom -> top
        for (layer in layers) {
            if (!layer.visible) continue
            val layerBmp = renderSingleLayerBitmap(layer, layers, sc)
            val layerRect = RectF(0f, 0f, layer.w.toInt().coerceAtLeast(4).toFloat(), layer.h.toInt().coerceAtLeast(4).toFloat())
            val saveCount = canvas.save()

            val cx = layer.x + layer.w / 2f
            val cy = layer.y + layer.h / 2f
            canvas.translate(cx, cy)
            canvas.rotate(layer.rotation)
            canvas.translate(-layer.w / 2f, -layer.h / 2f)

            val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            layerPaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
            applyBlendMode(layerPaint, layer.blend)

            if (layer.shadowBlur > 0f) {
                // Draw drop shadow silhouette
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                shadowPaint.color = layer.shadowColor
                shadowPaint.maskFilter = BlurMaskFilter(layer.shadowBlur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                canvas.save()
                canvas.translate(layer.shadowOffsetX, layer.shadowOffsetY)
                canvas.drawBitmap(layerBmp.extractAlpha(), null, layerRect, shadowPaint)
                canvas.restore()
            }

            canvas.drawBitmap(layerBmp, null, layerRect, layerPaint)
            canvas.restoreToCount(saveCount)
        }

        // 3. Apply outer & inner border frame if enabled
        if (borderOuter > 0f || borderInner > 0f) {
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (borderOuter > 0f) {
                framePaint.style = Paint.Style.STROKE
                framePaint.strokeWidth = borderOuter * 2f
                framePaint.color = borderColor
                val r = borderRadius
                canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, framePaint)
            }
            if (borderInner > 0f) {
                framePaint.style = Paint.Style.STROKE
                framePaint.strokeWidth = borderInner
                framePaint.color = Color.WHITE
                val inset = borderOuter + borderInner * 1.5f
                val r = (borderRadius - inset * 0.5f).coerceAtLeast(0f)
                canvas.drawRoundRect(
                    RectF(inset, inset, w - inset, h - inset),
                    r, r, framePaint
                )
            }
        }

        return out
    }

    // Custom fonts loaded at runtime from .ttf/.otf files
    val customTypefaces = mutableMapOf<String, Typeface>()

    // Per-layer composition LRU cache to avoid redundant CPU pixel passes during slider drags (Bug C fix)
    private val layerCompCache = android.util.LruCache<Int, Bitmap>(18)

    // Risultato del filtro prima della zona: mentre si modifica la zona si ricalcola solo la fusione (anteprima in diretta)
    private val filterResultCache = android.util.LruCache<Int, Bitmap>(4)

    /**
     * Anteprima interattiva: mentre si trascina un cursore il filtro si calcola su una copia con lato lungo
     * di [PREVIEW_MAX_SIDE] px e si ingrandisce; appena l'interazione si ferma si ricalcola a piena risoluzione.
     * Le misure del motore sono proporzionali alla foto, quindi l'aspetto non cambia.
     */
    @Volatile var interactivePreview: Boolean = false
    /** Lato a cui si calcola l'effetto durante i gesti (gli effetti pesanti costano col numero di pixel). */
    const val PREVIEW_MAX_SIDE = 720
    /** Lato lungo della tela durante i gesti: circa la risoluzione dello schermo. */
    const val INTERACTIVE_SIDE = 1080

    fun previewScale(canvasW: Int, canvasH: Int): Float {
        if (!interactivePreview) return 1f
        val m = max(canvasW, canvasH)
        return if (m <= INTERACTIVE_SIDE) 1f else INTERACTIVE_SIDE.toFloat() / m
    }

    private fun applyFilterForPreview(bmp: Bitmap, layer: EditorLayer): Bitmap {
        val id = layer.activeFilterId ?: return bmp
        val maxSide = max(bmp.width, bmp.height)
        if (!interactivePreview || maxSide <= PREVIEW_MAX_SIDE) {
            return applyEffectFilter(bmp, id, layer.activeFilterFade, layer.activeFilterBlend, layer.activeFilterParams)
        }
        val k = PREVIEW_MAX_SIDE.toFloat() / maxSide
        val small = Bitmap.createScaledBitmap(bmp, (bmp.width * k).roundToInt().coerceAtLeast(1), (bmp.height * k).roundToInt().coerceAtLeast(1), true)
        val fx = applyEffectFilter(small, id, layer.activeFilterFade, layer.activeFilterBlend, layer.activeFilterParams)
        return Bitmap.createScaledBitmap(fx, bmp.width, bmp.height, true)
    }

    private fun computeLayerCacheKey(layer: EditorLayer, allLayers: List<EditorLayer>): Int {
        // spostare, ruotare, cambiare opacità o fusione non cambia i pixel del livello: niente ricalcolo degli effetti
        var result = layer.copy(
            x = 0f, y = 0f, rotation = 0f, opacity = 1f, blend = com.example.model.BlendOption.NORMAL,
            visible = true, locked = false, selectedForMulti = false, editingMask = false, name = "",
            shadowBlur = 0f, shadowOffsetX = 0f, shadowOffsetY = 0f, shadowColor = 0
        ).hashCode()
        result = 31 * result + (layer.bitmap?.generationId ?: 0)
        result = 31 * result + (layer.maskBitmap?.generationId ?: 0)
        result = 31 * result + (layer.activeFilterMask?.generationId ?: 0)
        for ((_, cfg) in layer.effects) {
            result = 31 * result + (cfg.localMask?.generationId ?: 0)
        }
        if (layer.type == LayerType.TEXT && layer.textOnPathLayerId != null) {
            val pathL = allLayers.find { it.id == layer.textOnPathLayerId }
            result = 31 * result + (pathL?.hashCode() ?: 0)
        }
        return result
    }

    private fun applyBlendMode(paint: Paint, blend: BlendOption) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = when (blend) {
                BlendOption.NORMAL -> android.graphics.BlendMode.SRC_OVER
                BlendOption.MULTIPLY -> android.graphics.BlendMode.MULTIPLY
                BlendOption.SCREEN -> android.graphics.BlendMode.SCREEN
                BlendOption.OVERLAY -> android.graphics.BlendMode.OVERLAY
                BlendOption.DARKEN -> android.graphics.BlendMode.DARKEN
                BlendOption.LIGHTEN -> android.graphics.BlendMode.LIGHTEN
                BlendOption.COLOR_DODGE -> android.graphics.BlendMode.COLOR_DODGE
                BlendOption.COLOR_BURN -> android.graphics.BlendMode.COLOR_BURN
                BlendOption.HARD_LIGHT -> android.graphics.BlendMode.HARD_LIGHT
                BlendOption.SOFT_LIGHT -> android.graphics.BlendMode.SOFT_LIGHT
                BlendOption.DIFFERENCE -> android.graphics.BlendMode.DIFFERENCE
                BlendOption.EXCLUSION -> android.graphics.BlendMode.EXCLUSION
                BlendOption.HUE -> android.graphics.BlendMode.HUE
                BlendOption.SATURATION -> android.graphics.BlendMode.SATURATION
                BlendOption.COLOR -> android.graphics.BlendMode.COLOR
                BlendOption.LUMINOSITY -> android.graphics.BlendMode.LUMINOSITY
            }
            paint.blendMode = bm
        } else if (blend.porterDuff != null) {
            paint.xfermode = PorterDuffXfermode(blend.porterDuff)
        }
    }

    /**
     * Renders a single layer's content at its bounding box (w x h), then applies mask, adjustments, curves, and FX
     * supporting both global FX (Path A) and per-effect selective localMask (Path B), with LRU caching (Bug C fix).
     */
    /**
     * @param scale risoluzione di rendering rispetto alle misure del livello (1 = piena; <1 anteprima durante i gesti).
     * Il contenuto si disegna sempre in unità del livello: cambia solo la densità dei pixel.
     */
    fun renderSingleLayerBitmap(layer: EditorLayer, allLayers: List<EditorLayer> = emptyList(), scale: Float = 1f): Bitmap {
        val lw = layer.w.toInt().coerceAtLeast(4)
        val lh = layer.h.toInt().coerceAtLeast(4)
        val sc = scale.coerceIn(0.05f, 1f)
        val rw = if (sc >= 1f) lw else (lw * sc).roundToInt().coerceAtLeast(4)
        val rh = if (sc >= 1f) lh else (lh * sc).roundToInt().coerceAtLeast(4)
        // la scala fa parte della chiave: un'anteprima non viene mai riusata come risultato a piena qualità
        val cacheKey = 31 * computeLayerCacheKey(layer, allLayers) + rw
        layerCompCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled && cached.width == rw && cached.height == rh) {
                return cached
            }
        }
        val rawBmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
        val c = Canvas(rawBmp)
        if (rw != lw || rh != lh) c.scale(rw / lw.toFloat(), rh / lh.toFloat())
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        when (layer.type) {
            LayerType.RASTER, LayerType.IMAGE -> {
                layer.bitmap?.let { src ->
                    c.drawBitmap(src, null, RectF(0f, 0f, lw.toFloat(), lh.toFloat()), p)
                }
            }
            LayerType.SHAPE -> {
                drawShapeContent(c, lw.toFloat(), lh.toFloat(), layer)
            }
            LayerType.TEXT -> {
                drawTextContent(c, lw.toFloat(), lh.toFloat(), layer, allLayers)
            }
            LayerType.PATH -> {
                drawVectorPathContent(c, lw.toFloat(), lh.toFloat(), layer)
            }
        }

        // Apply base adjustments and curves first
        var bmp = if (!layer.adjust.isIdentity || !layer.curves.isIdentity) {
            applyPixelPipeline(rawBmp, layer.adjust, layer.curves, emptyMap())
        } else {
            rawBmp
        }

        // Apply enabled Effects (global or with selective localMask)
        val enabledEffects = layer.effects.filterValues { it.enabled }
        if (enabledEffects.isNotEmpty()) {
            val hasLocalMask = enabledEffects.values.any { it.localMask != null }
            if (!hasLocalMask) {
                // Path A: Global effects
                bmp = applyPixelPipeline(bmp, AdjustParams(), CurvesData(), enabledEffects)
            } else {
                // Path B: Each effect can be global or selectively masked with localMask
                for ((fxKey, cfg) in enabledEffects) {
                    val fxOnly = applyPixelPipeline(bmp, AdjustParams(), CurvesData(), mapOf(fxKey to cfg))
                    val lMask = cfg.localMask
                    if (lMask == null) {
                        bmp = fxOnly
                    } else {
                        // Combine original bmp + masked fxOnly
                        val fxMasked = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                        val fmc = Canvas(fxMasked)
                        fmc.drawBitmap(fxOnly, 0f, 0f, null)
                        val mp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        mp.xfermode = PorterDuffXfermode(
                            if (cfg.localInverted) PorterDuff.Mode.DST_OUT else PorterDuff.Mode.DST_IN
                        )
                        fmc.drawBitmap(lMask, null, RectF(0f, 0f, rw.toFloat(), rh.toFloat()), mp)

                        // Cut out masked area from original and draw fxMasked on top
                        val combined = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                        val cc = Canvas(combined)
                        cc.drawBitmap(bmp, 0f, 0f, null)
                        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        cutPaint.xfermode = PorterDuffXfermode(
                            if (cfg.localInverted) PorterDuff.Mode.DST_IN else PorterDuff.Mode.DST_OUT
                        )
                        cc.drawBitmap(lMask, null, RectF(0f, 0f, rw.toFloat(), rh.toFloat()), cutPaint)
                        cc.drawBitmap(fxMasked, 0f, 0f, null)
                        bmp = combined
                    }
                }
            }
        }

        // Apply 170 Filter FX if active
        if (layer.activeFilterId != null) {
            val preview = interactivePreview
            val filterKey = 31 * (31 * computeLayerCacheKey(layer.copy(activeFilterMask = null, activeFilterMaskInverted = false), allLayers) + rw) + (if (preview) 1 else 0)
            val filtered = filterResultCache.get(filterKey)?.takeIf { !it.isRecycled && it.width == bmp.width && it.height == bmp.height }
                ?: applyFilterForPreview(bmp, layer).also { filterResultCache.put(filterKey, it) }
            // Zona del filtro: pennello, gomma, forme, lazo o poligono (§ maschera selettiva)
            bmp = layer.activeFilterMask?.let { MaskGeometry.blendThroughMask(bmp, filtered, it, layer.activeFilterMaskInverted) } ?: filtered
        }

        // Apply layer visibility mask if present (independent and multiplicative with effect localMask)
        layer.maskBitmap?.let { mask ->
            val masked = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
            val mc = Canvas(masked)
            mc.drawBitmap(bmp, 0f, 0f, null)
            val mp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            mp.xfermode = PorterDuffXfermode(
                if (layer.maskInverted) PorterDuff.Mode.DST_OUT else PorterDuff.Mode.DST_IN
            )
            mc.drawBitmap(mask, null, RectF(0f, 0f, rw.toFloat(), rh.toFloat()), mp)
            bmp = masked
        }

        layerCompCache.put(cacheKey, bmp)
        return bmp
    }

    fun buildShapePath(w: Float, h: Float, layer: EditorLayer): Path {
        val path = Path()
        val pad = (layer.strokeWidth / 2f).coerceAtLeast(2f)
        val left = pad
        val top = pad
        val right = (w - pad).coerceAtLeast(left + 2f)
        val bottom = (h - pad).coerceAtLeast(top + 2f)
        val cx = w / 2f
        val cy = h / 2f
        val rx = (right - left) / 2f
        val ry = (bottom - top) / 2f

        when (layer.shapeKind) {
            ShapeKind.RECT -> {
                val r = layer.cornerRadius.coerceIn(0f, min(rx, ry))
                path.addRoundRect(RectF(left, top, right, bottom), r, r, Path.Direction.CW)
            }
            ShapeKind.CIRCLE -> {
                path.addOval(RectF(left, top, right, bottom), Path.Direction.CW)
            }
            ShapeKind.TRIANGLE -> {
                path.moveTo(cx, top)
                path.lineTo(right, bottom)
                path.lineTo(left, bottom)
                path.close()
            }
            ShapeKind.POLYGON -> {
                val sides = layer.polygonSides.coerceIn(3, 12)
                for (i in 0 until sides) {
                    val angle = (i.toDouble() / sides) * Math.PI * 2.0 - Math.PI / 2.0
                    val px = cx + (cos(angle) * rx).toFloat()
                    val py = cy + (sin(angle) * ry).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            ShapeKind.STAR -> {
                val pts = layer.starPoints.coerceIn(3, 24)
                val innerRatio = layer.starInnerRatio.coerceIn(0.1f, 0.9f)
                val total = pts * 2
                for (i in 0 until total) {
                    val angle = (i.toDouble() / total) * Math.PI * 2.0 - Math.PI / 2.0
                    val factor = if (i % 2 == 0) 1f else innerRatio
                    val px = cx + (cos(angle) * rx * factor).toFloat()
                    val py = cy + (sin(angle) * ry * factor).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            ShapeKind.HEART -> {
                val s = min(rx, ry)
                path.moveTo(cx, cy + s * 0.75f)
                path.cubicTo(cx - s * 1.25f, cy - s * 0.25f, cx - s * 0.45f, cy - s * 1.1f, cx, cy - s * 0.35f)
                path.cubicTo(cx + s * 0.45f, cy - s * 1.1f, cx + s * 1.25f, cy - s * 0.25f, cx, cy + s * 0.75f)
                path.close()
            }
            ShapeKind.LINE -> {
                path.moveTo(left, cy)
                path.lineTo(right, cy)
            }
            ShapeKind.CALLOUT -> {
                val bubbleBottom = bottom - ry * 0.35f
                val r = 20f
                path.addRoundRect(RectF(left, top, right, bubbleBottom), r, r, Path.Direction.CW)
                path.moveTo(cx - rx * 0.2f, bubbleBottom)
                path.lineTo(cx - rx * 0.4f, bottom)
                path.lineTo(cx + rx * 0.1f, bubbleBottom)
                path.close()
            }
        }
        return path
    }

    private fun drawShapeContent(c: Canvas, w: Float, h: Float, layer: EditorLayer) {
        val path = buildShapePath(w, h, layer)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        if (layer.shapeKind != ShapeKind.LINE) {
            p.style = Paint.Style.FILL
            if (layer.useGradient) {
                val rad = Math.toRadians(layer.gradAngle.toDouble())
                val dx = cos(rad).toFloat() * w * 0.5f
                val dy = sin(rad).toFloat() * h * 0.5f
                p.shader = LinearGradient(
                    w / 2f - dx, h / 2f - dy, w / 2f + dx, h / 2f + dy,
                    layer.fillColor, layer.gradColor2, Shader.TileMode.CLAMP
                )
            } else {
                p.color = layer.fillColor
            }
            c.drawPath(path, p)
            p.shader = null
        }

        if (layer.strokeWidth > 0f || layer.shapeKind == ShapeKind.LINE) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = layer.strokeWidth.coerceAtLeast(if (layer.shapeKind == ShapeKind.LINE) 4f else 0f)
            p.color = if (layer.shapeKind == ShapeKind.LINE) layer.fillColor else layer.strokeColor
            p.strokeJoin = Paint.Join.ROUND
            p.strokeCap = Paint.Cap.ROUND
            c.drawPath(path, p)
        }
    }

    private fun drawVectorPathContent(c: Canvas, w: Float, h: Float, layer: EditorLayer) {
        if (layer.pathPoints.isEmpty()) return
        val path = Path()
        layer.pathPoints.forEachIndexed { idx, pt ->
            val px = pt.x * w
            val py = pt.y * h
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (layer.pathClosed && layer.pathPoints.size >= 3) {
            path.close()
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (layer.pathClosed && Color.alpha(layer.fillColor) > 0) {
            p.style = Paint.Style.FILL
            p.color = layer.fillColor
            c.drawPath(path, p)
        }
        if (layer.strokeWidth > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = layer.strokeWidth
            p.color = layer.strokeColor
            p.strokeJoin = Paint.Join.ROUND
            p.strokeCap = Paint.Cap.ROUND
            c.drawPath(path, p)
        }
    }

    private fun drawTextContent(c: Canvas, w: Float, h: Float, layer: EditorLayer, allLayers: List<EditorLayer>) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        val customTf = customTypefaces[layer.fontFamily]
        val tf = if (customTf != null) {
            Typeface.create(customTf, if (layer.fontWeightBold) Typeface.BOLD else Typeface.NORMAL)
        } else {
            when (layer.fontFamily.lowercase()) {
                "georgia", "times", "serif" -> Typeface.create(Typeface.SERIF, if (layer.fontWeightBold) Typeface.BOLD else Typeface.NORMAL)
                "courier", "monospace" -> Typeface.create(Typeface.MONOSPACE, if (layer.fontWeightBold) Typeface.BOLD else Typeface.NORMAL)
                "impact" -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                else -> Typeface.create(Typeface.SANS_SERIF, if (layer.fontWeightBold) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        tp.typeface = tf
        tp.textSize = layer.fontSize.coerceIn(8f, 200f)

        if (layer.useGradient) {
            val rad = Math.toRadians(layer.gradAngle.toDouble())
            val dx = cos(rad).toFloat() * w * 0.5f
            val dy = sin(rad).toFloat() * h * 0.5f
            tp.shader = LinearGradient(
                w / 2f - dx, h / 2f - dy, w / 2f + dx, h / 2f + dy,
                layer.fillColor, layer.gradColor2, Shader.TileMode.CLAMP
            )
        } else {
            tp.color = layer.fillColor
        }

        val strokePaint = Paint(tp).apply {
            shader = null
            style = Paint.Style.STROKE
            strokeWidth = layer.strokeWidth
            color = layer.strokeColor
            strokeJoin = Paint.Join.ROUND
        }

        // Check if bound to a vector path layer (Bug D fix: map pathLayer coordinates into text layer local space)
        val targetPathLayer = layer.textOnPathLayerId?.let { pid -> allLayers.find { it.id == pid && it.pathPoints.size >= 2 } }
        if (targetPathLayer != null) {
            val vPath = Path()
            targetPathLayer.pathPoints.forEachIndexed { idx, pt ->
                val px = (pt.x * targetPathLayer.w) + targetPathLayer.x - layer.x
                val py = (pt.y * targetPathLayer.h) + targetPathLayer.y - layer.y
                if (idx == 0) vPath.moveTo(px, py) else vPath.lineTo(px, py)
            }
            val cleanText = layer.text.replace("\n", " ")
            if (layer.strokeWidth > 0f) {
                c.drawTextOnPath(cleanText, vPath, 0f, layer.fontSize * 0.35f, strokePaint)
            }
            c.drawTextOnPath(cleanText, vPath, 0f, layer.fontSize * 0.35f, tp)
            return
        }

        // Check if text has Bend (arc distortion -100..100)
        if (abs(layer.textBend) > 1f) {
            val cleanText = layer.text.replace("\n", " ")
            if (cleanText.isEmpty()) return
            val bendNorm = (layer.textBend / 100f).coerceIn(-1f, 1f)
            val totalWidth = tp.measureText(cleanText).coerceAtLeast(1f)
            val sweepAngle = bendNorm * 180f // degrees
            val sweepRad = Math.toRadians(abs(sweepAngle).toDouble()).toFloat().coerceAtLeast(0.05f)
            val radius = totalWidth / sweepRad
            val centerY = if (bendNorm > 0) h * 0.5f + radius * 0.65f else h * 0.5f - radius * 0.65f
            val startAngle = if (bendNorm > 0) -Math.PI.toFloat() / 2f - sweepRad / 2f else Math.PI.toFloat() / 2f + sweepRad / 2f

            var accWidth = 0f
            for (ch in cleanText) {
                val cw = tp.measureText(ch.toString())
                val charCenter = accWidth + cw / 2f
                val frac = charCenter / totalWidth
                val angle = if (bendNorm > 0) startAngle + frac * sweepRad else startAngle - frac * sweepRad
                val px = w / 2f + cos(angle) * radius
                val py = centerY + sin(angle) * radius
                val rotDeg = Math.toDegrees(angle.toDouble()).toFloat() + if (bendNorm > 0) 90f else -90f

                c.save()
                c.translate(px, py)
                c.rotate(rotDeg)
                if (layer.strokeWidth > 0f) {
                    c.drawText(ch.toString(), -cw / 2f, 0f, strokePaint)
                }
                c.drawText(ch.toString(), -cw / 2f, 0f, tp)
                c.restore()
                accWidth += cw
            }
            return
        }

        // Standard multi-line text rendering
        val lines = layer.text.split("\n")
        val lineStep = layer.fontSize * layer.lineHeight
        val totalH = lines.size * lineStep
        var y = (h - totalH) / 2f + layer.fontSize * 0.85f

        for (line in lines) {
            val lw = tp.measureText(line)
            val x = when (layer.textAlign) {
                0 -> 16f
                2 -> (w - lw - 16f).coerceAtLeast(8f)
                else -> (w - lw) / 2f
            }
            if (layer.strokeWidth > 0f) {
                c.drawText(line, x, y, strokePaint)
            }
            c.drawText(line, x, y, tp)
            y += lineStep
        }
    }

    /**
     * Applies Adjust parameters, RGB Curves LUT, and Effects FX to a Bitmap.
     */
    fun applyPixelPipeline(
        src: Bitmap,
        adjust: AdjustParams,
        curves: CurvesData,
        effects: Map<String, EffectConfig>
    ): Bitmap {
        var working = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = working.width
        val h = working.height

        // 1. Blur FX if enabled
        val blurFx = effects["blur"]
        if (blurFx != null && blurFx.enabled && blurFx.intensity > 0f) {
            val radius = ((blurFx.intensity / 100f) * 20f).toInt().coerceIn(1, 25)
            working = boxBlur(working, radius)
        }

        // 2. Build LUTs for Curves + Tonal adjustments
        val hasCurves = !curves.isIdentity
        val lutRGB = if (hasCurves) buildCurveLut(curves.rgb) else null
        val lutR = if (hasCurves) buildCurveLut(curves.r) else null
        val lutG = if (hasCurves) buildCurveLut(curves.g) else null
        val lutB = if (hasCurves) buildCurveLut(curves.b) else null

        val hasAdjust = !adjust.isIdentity
        val bwFx = effects["bw"]?.takeIf { it.enabled }
        val sepiaFx = effects["sepia"]?.takeIf { it.enabled }
        val invertFx = effects["invert"]?.takeIf { it.enabled }
        val vignetteFx = effects["vignette"]?.takeIf { it.enabled }
        val grainFx = effects["grain"]?.takeIf { it.enabled }
        val glitchFx = effects["glitch"]?.takeIf { it.enabled }
        val neonFx = effects["neon"]?.takeIf { it.enabled }

        if (hasAdjust || hasCurves || bwFx != null || sepiaFx != null || invertFx != null || vignetteFx != null || grainFx != null) {
            val pixels = IntArray(w * h)
            working.getPixels(pixels, 0, w, 0, 0, w, h)

            val brightFactor = 1f + (adjust.brightness / 100f) + (adjust.exposure / 100f) * 0.8f
            val contrastVal = (adjust.contrast / 100f) * 128f
            val contrastFactor = (259f * (contrastVal + 255f)) / (255f * (259f - contrastVal).coerceAtLeast(1f))
            val satFactor = 1f + (adjust.saturation / 100f)
            val tempShift = (adjust.temperature / 100f) * 45f
            val highGain = (adjust.highlights / 100f) * 55f
            val shadGain = (adjust.shadows / 100f) * 55f
            val clarityFactor = (adjust.clarity / 100f) * 0.45f
            val hueShiftDeg = adjust.hue

            val bwMix = (bwFx?.intensity ?: 0f) / 100f
            val sepiaMix = (sepiaFx?.intensity ?: 0f) / 100f
            val invertMix = (invertFx?.intensity ?: 0f) / 100f
            val vigStrength = (vignetteFx?.intensity ?: 0f) / 100f
            val vigRadius = ((vignetteFx?.param1 ?: 50f) / 100f).coerceIn(0.1f, 1f)
            val grainAmp = ((grainFx?.intensity ?: 0f) / 100f) * 65f
            val rng = Random(1337)

            val cx = w * 0.5f
            val cy = h * 0.5f
            val maxDist = hypot(cx, cy).coerceAtLeast(1f)

            for (i in pixels.indices) {
                val px = pixels[i]
                val a = Color.alpha(px)
                if (a == 0) continue

                var r = Color.red(px).toFloat()
                var g = Color.green(px).toFloat()
                var b = Color.blue(px).toFloat()

                if (hasAdjust) {
                    // Brightness & Exposure
                    r *= brightFactor
                    g *= brightFactor
                    b *= brightFactor

                    // Contrast + Clarity midtone punch
                    val cf = contrastFactor * (1f + clarityFactor)
                    r = cf * (r - 128f) + 128f
                    g = cf * (g - 128f) + 128f
                    b = cf * (b - 128f) + 128f

                    // Temperature
                    r += tempShift
                    b -= tempShift

                    // Shadows & Highlights
                    val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    if (shadGain != 0f) {
                        val sw = (1f - lum).coerceIn(0f, 1f).pow(2)
                        r += shadGain * sw
                        g += shadGain * sw
                        b += shadGain * sw
                    }
                    if (highGain != 0f) {
                        val hw = lum.coerceIn(0f, 1f).pow(2)
                        r += highGain * hw
                        g += highGain * hw
                        b += highGain * hw
                    }

                    // Saturation
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b
                    r = luma + (r - luma) * satFactor
                    g = luma + (g - luma) * satFactor
                    b = luma + (b - luma) * satFactor

                    // Hue rotation if non-zero
                    if (hueShiftDeg > 0.5f) {
                        val hsv = FloatArray(3)
                        Color.RGBToHSV(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255), hsv)
                        hsv[0] = (hsv[0] + hueShiftDeg) % 360f
                        val shifted = Color.HSVToColor(hsv)
                        r = Color.red(shifted).toFloat()
                        g = Color.green(shifted).toFloat()
                        b = Color.blue(shifted).toFloat()
                    }
                }

                // Curves LUT
                if (hasCurves && lutRGB != null && lutR != null && lutG != null && lutB != null) {
                    val ri = r.toInt().coerceIn(0, 255)
                    val gi = g.toInt().coerceIn(0, 255)
                    val bi = b.toInt().coerceIn(0, 255)
                    r = lutRGB[lutR[ri]].toFloat()
                    g = lutRGB[lutG[gi]].toFloat()
                    b = lutRGB[lutB[bi]].toFloat()
                }

                // B&W FX
                if (bwMix > 0f) {
                    val gray = 0.299f * r + 0.587f * g + 0.114f * b
                    r = r * (1f - bwMix) + gray * bwMix
                    g = g * (1f - bwMix) + gray * bwMix
                    b = b * (1f - bwMix) + gray * bwMix
                }

                // Sepia FX
                if (sepiaMix > 0f) {
                    val sr = (r * 0.393f + g * 0.769f + b * 0.189f).coerceAtMost(255f)
                    val sg = (r * 0.349f + g * 0.686f + b * 0.168f).coerceAtMost(255f)
                    val sb = (r * 0.272f + g * 0.534f + b * 0.131f).coerceAtMost(255f)
                    r = r * (1f - sepiaMix) + sr * sepiaMix
                    g = g * (1f - sepiaMix) + sg * sepiaMix
                    b = b * (1f - sepiaMix) + sb * sepiaMix
                }

                // Invert FX
                if (invertMix > 0f) {
                    r = r * (1f - invertMix) + (255f - r) * invertMix
                    g = g * (1f - invertMix) + (255f - g) * invertMix
                    b = b * (1f - invertMix) + (255f - b) * invertMix
                }

                // Vignette FX
                if (vigStrength > 0f) {
                    val pxX = (i % w).toFloat()
                    val pxY = (i / w).toFloat()
                    val dist = hypot(pxX - cx, pxY - cy) / maxDist
                    if (dist > vigRadius * 0.5f) {
                        val factor = ((dist - vigRadius * 0.5f) / (1f - vigRadius * 0.5f).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                        val dark = 1f - factor * factor * vigStrength
                        r *= dark
                        g *= dark
                        b *= dark
                    }
                }

                // Grain FX
                if (grainAmp > 0f) {
                    val n = (rng.nextFloat() - 0.5f) * 2f * grainAmp
                    r += n
                    g += n
                    b += n
                }

                pixels[i] = Color.argb(
                    a,
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
            working.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        // 3. Glitch FX (RGB channel shift + slice displacement)
        if (glitchFx != null) {
            working = applyGlitchEffect(working, glitchFx)
        }

        // 4. Neon Glow FX
        if (neonFx != null) {
            working = applyNeonEffect(working, neonFx)
        }

        return working
    }

    /**
     * Generates a 256-entry Look-Up Table from sorted control points using piecewise linear/smooth interpolation.
     */
    fun buildCurveLut(points: List<CurvePoint>): IntArray {
        val lut = IntArray(256)
        val sorted = points.sortedBy { it.x }
        if (sorted.isEmpty()) {
            for (i in 0..255) lut[i] = i
            return lut
        }
        for (i in 0..255) {
            val x = i / 255f
            val y = when {
                x <= sorted.first().x -> sorted.first().y
                x >= sorted.last().x -> sorted.last().y
                else -> {
                    var idx = 0
                    while (idx < sorted.size - 1 && sorted[idx + 1].x < x) {
                        idx++
                    }
                    val p0 = sorted[idx]
                    val p1 = sorted[idx + 1]
                    val span = (p1.x - p0.x).coerceAtLeast(0.0001f)
                    val t = (x - p0.x) / span
                    // Smoothstep blend for natural curve transition
                    val st = t * t * (3f - 2f * t)
                    p0.y + (p1.y - p0.y) * st
                }
            }
            lut[i] = (y * 255f).roundToInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun applyGlitchEffect(src: Bitmap, cfg: EffectConfig): Bitmap {
        val w = src.width
        val h = src.height
        val inPx = IntArray(w * h)
        val outPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)

        val shift = ((cfg.intensity / 100f) * 28f).toInt().coerceAtLeast(2)
        val slices = ((cfg.param1 / 100f) * 16f).toInt().coerceAtLeast(2)
        val rng = Random(99)
        val sliceBounds = Array(slices) {
            val y0 = rng.nextInt(0, h)
            val sh = rng.nextInt(6, (h / 8).coerceAtLeast(10))
            val offset = rng.nextInt(-shift * 2, shift * 2 + 1)
            Triple(y0, (y0 + sh).coerceAtMost(h - 1), offset)
        }

        for (y in 0 until h) {
            var sliceOff = 0
            for (s in sliceBounds) {
                if (y in s.first..s.second) {
                    sliceOff = s.third
                    break
                }
            }
            for (x in 0 until w) {
                val baseIdx = y * w + (x + sliceOff).coerceIn(0, w - 1)
                val rIdx = y * w + (x + sliceOff + shift).coerceIn(0, w - 1)
                val bIdx = y * w + (x + sliceOff - shift).coerceIn(0, w - 1)
                val a = Color.alpha(inPx[baseIdx])
                val r = Color.red(inPx[rIdx])
                val g = Color.green(inPx[baseIdx])
                val b = Color.blue(inPx[bIdx])
                outPx[y * w + x] = Color.argb(a, r, g, b)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    private fun applyNeonEffect(src: Bitmap, cfg: EffectConfig): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(src, 0f, 0f, null)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = ((cfg.param1 / 100f) * 14f).coerceAtLeast(2f)
        p.color = cfg.colorHex
        p.maskFilter = BlurMaskFilter(((cfg.intensity / 100f) * 25f).coerceAtLeast(2f), BlurMaskFilter.Blur.NORMAL)
        val pad = 12f
        c.drawRoundRect(RectF(pad, pad, w - pad, h - pad), 18f, 18f, p)
        return out
    }

    /**
     * Fast separable box blur.
     */
    fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val r = radius.coerceIn(1, 35)
        val div = 2 * r + 1

        // Horizontal pass
        for (y in 0 until h) {
            var rSum = 0; var gSum = 0; var bSum = 0; var aSum = 0
            val rowOffset = y * w
            for (i in -r..r) {
                val p = pix[rowOffset + i.coerceIn(0, w - 1)]
                aSum += Color.alpha(p)
                rSum += Color.red(p)
                gSum += Color.green(p)
                bSum += Color.blue(p)
            }
            for (x in 0 until w) {
                tmp[rowOffset + x] = Color.argb(aSum / div, rSum / div, gSum / div, bSum / div)
                val pOut = pix[rowOffset + (x - r).coerceIn(0, w - 1)]
                val pIn = pix[rowOffset + (x + r + 1).coerceIn(0, w - 1)]
                aSum += Color.alpha(pIn) - Color.alpha(pOut)
                rSum += Color.red(pIn) - Color.red(pOut)
                gSum += Color.green(pIn) - Color.green(pOut)
                bSum += Color.blue(pIn) - Color.blue(pOut)
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0; var aSum = 0
            for (i in -r..r) {
                val p = tmp[i.coerceIn(0, h - 1) * w + x]
                aSum += Color.alpha(p)
                rSum += Color.red(p)
                gSum += Color.green(p)
                bSum += Color.blue(p)
            }
            for (y in 0 until h) {
                pix[y * w + x] = Color.argb(aSum / div, rSum / div, gSum / div, bSum / div)
                val pOut = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                val pIn = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                aSum += Color.alpha(pIn) - Color.alpha(pOut)
                rSum += Color.red(pIn) - Color.red(pOut)
                gSum += Color.green(pIn) - Color.green(pOut)
                bSum += Color.blue(pIn) - Color.blue(pOut)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pix, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * 3D Perspective Homography transformation using 4 draggable corners (normalized 0..1)
     * Solves the 8x8 linear system for inverse projective mapping and samples pixel-by-pixel with bilinear interpolation.
     */
    /**
     * Distorsione prospettica: gli angoli del bitmap vanno nei punti [corners] (normalizzati 0..1, anche fuori).
     * È la stessa trasformazione dell'anteprima (Matrix.setPolyToPoly), calcolata in modo esatto pixel per pixel.
     * Le zone fuori dalla foto diventano trasparenti.
     */
    fun applyPerspectiveDistort(src: Bitmap, corners: List<NormPoint>): Bitmap {
        if (corners.size < 4) return src
        val w = src.width; val h = src.height
        val quad = floatArrayOf(
            corners[0].x * w, corners[0].y * h, corners[1].x * w, corners[1].y * h,
            corners[2].x * w, corners[2].y * h, corners[3].x * w, corners[3].y * h
        )
        val rect = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        val hm = solveHomography8x8(quad, rect) // dal quadrilatero di arrivo al rettangolo di partenza
        val inPx = IntArray(w * h); val outPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            val yf = y + 0.5f
            for (x in 0 until w) {
                val xf = x + 0.5f
                val d = hm[6] * xf + hm[7] * yf + 1f
                if (abs(d) < 1e-6f) continue
                val u = (hm[0] * xf + hm[1] * yf + hm[2]) / d - 0.5f
                val v = (hm[3] * xf + hm[4] * yf + hm[5]) / d - 0.5f
                if (u < -0.5f || v < -0.5f || u > w - 0.5f || v > h - 0.5f) continue
                outPx[y * w + x] = sampleBilinear(inPx, w, h, u, v)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    fun applyPerspectiveHomography(src: Bitmap, corners: List<NormPoint>): Bitmap {
        if (corners.size < 4) return src
        val w = src.width
        val h = src.height
        // Map destination corners (0,0), (w,0), (w,h), (0,h) -> source quad corners
        val dstPts = floatArrayOf(
            0f, 0f,
            w.toFloat(), 0f,
            w.toFloat(), h.toFloat(),
            0f, h.toFloat()
        )
        val srcPts = floatArrayOf(
            corners[0].x * w, corners[0].y * h,
            corners[1].x * w, corners[1].y * h,
            corners[2].x * w, corners[2].y * h,
            corners[3].x * w, corners[3].y * h
        )

        val hMat = solveHomography8x8(dstPts, srcPts)
        val inPx = IntArray(w * h)
        val outPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            val yf = y.toFloat()
            for (x in 0 until w) {
                val xf = x.toFloat()
                val denom = hMat[6] * xf + hMat[7] * yf + 1f
                if (abs(denom) < 1e-6f) continue
                val u = (hMat[0] * xf + hMat[1] * yf + hMat[2]) / denom
                val v = (hMat[3] * xf + hMat[4] * yf + hMat[5]) / denom
                outPx[y * w + x] = sampleBilinear(inPx, w, h, u, v)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Solves the classic 8x8 linear system via Gaussian elimination with partial pivoting
     * to compute projective homography coefficients (a, b, c, d, e, f, g, h).
     */
    private fun solveHomography8x8(src: FloatArray, dst: FloatArray): FloatArray {
        val a = Array(8) { FloatArray(9) }
        for (i in 0 until 4) {
            val x = src[i * 2]
            val y = src[i * 2 + 1]
            val u = dst[i * 2]
            val v = dst[i * 2 + 1]
            val r1 = i * 2
            val r2 = i * 2 + 1
            a[r1][0] = x; a[r1][1] = y; a[r1][2] = 1f
            a[r1][3] = 0f; a[r1][4] = 0f; a[r1][5] = 0f
            a[r1][6] = -x * u; a[r1][7] = -y * u; a[r1][8] = u

            a[r2][0] = 0f; a[r2][1] = 0f; a[r2][2] = 0f
            a[r2][3] = x; a[r2][4] = y; a[r2][5] = 1f
            a[r2][6] = -x * v; a[r2][7] = -y * v; a[r2][8] = v
        }

        // Gaussian elimination
        for (col in 0 until 8) {
            var pivot = col
            for (row in col + 1 until 8) {
                if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
            }
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val div = a[col][col]
            if (abs(div) < 1e-8f) continue
            for (j in col..8) a[col][j] /= div
            for (row in 0 until 8) {
                if (row != col) {
                    val factor = a[row][col]
                    for (j in col..8) a[row][j] -= factor * a[col][j]
                }
            }
        }
        return FloatArray(8) { a[it][8] }
    }

    /**
     * Bilinear pixel sampling at floating-point coordinates (u, v).
     */
    fun sampleBilinear(pixels: IntArray, w: Int, h: Int, u: Float, v: Float): Int {
        if (u < 0f || u >= w - 1 || v < 0f || v >= h - 1) {
            val xi = u.roundToInt().coerceIn(0, w - 1)
            val yi = v.roundToInt().coerceIn(0, h - 1)
            return if (u < -1f || u > w || v < -1f || v > h) Color.TRANSPARENT else pixels[yi * w + xi]
        }
        val x0 = u.toInt()
        val y0 = v.toInt()
        val x1 = (x0 + 1).coerceAtMost(w - 1)
        val y1 = (y0 + 1).coerceAtMost(h - 1)
        val dx = u - x0
        val dy = v - y0

        val c00 = pixels[y0 * w + x0]
        val c10 = pixels[y0 * w + x1]
        val c01 = pixels[y1 * w + x0]
        val c11 = pixels[y1 * w + x1]

        val w00 = (1f - dx) * (1f - dy)
        val w10 = dx * (1f - dy)
        val w01 = (1f - dx) * dy
        val w11 = dx * dy

        val a = (Color.alpha(c00) * w00 + Color.alpha(c10) * w10 + Color.alpha(c01) * w01 + Color.alpha(c11) * w11).toInt().coerceIn(0, 255)
        val r = (Color.red(c00) * w00 + Color.red(c10) * w10 + Color.red(c01) * w01 + Color.red(c11) * w11).toInt().coerceIn(0, 255)
        val g = (Color.green(c00) * w00 + Color.green(c10) * w10 + Color.green(c01) * w01 + Color.green(c11) * w11).toInt().coerceIn(0, 255)
        val b = (Color.blue(c00) * w00 + Color.blue(c10) * w10 + Color.blue(c01) * w01 + Color.blue(c11) * w11).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    /**
     * Liquify / Warp local deformation around (cx, cy) with bilinear sampling.
     */
    fun applyWarpStroke(
        target: Bitmap,
        originalForReset: Bitmap?,
        mode: WarpMode,
        cx: Float,
        cy: Float,
        dx: Float,
        dy: Float,
        radius: Float,
        strength: Float // 0..1
    ): Bitmap {
        val w = target.width
        val h = target.height
        val r = radius.coerceAtLeast(10f)
        val rSq = r * r

        val xMin = (cx - r).toInt().coerceIn(0, w - 1)
        val xMax = (cx + r).toInt().coerceIn(0, w - 1)
        val yMin = (cy - r).toInt().coerceIn(0, h - 1)
        val yMax = (cy + r).toInt().coerceIn(0, h - 1)

        val srcPixels = IntArray(w * h)
        target.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val outPixels = srcPixels.copyOf()
        val resetPixels = if (mode == WarpMode.RESET && originalForReset != null &&
            originalForReset.width == w && originalForReset.height == h
        ) {
            IntArray(w * h).also { originalForReset.getPixels(it, 0, w, 0, 0, w, h) }
        } else null

        for (y in yMin..yMax) {
            val ry = y - cy
            for (x in xMin..xMax) {
                val rx = x - cx
                val distSq = rx * rx + ry * ry
                if (distSq >= rSq) continue
                val dist = sqrt(distSq)
                val falloff = (1f - dist / r).pow(2) * strength

                if (mode == WarpMode.RESET && resetPixels != null) {
                    val cur = srcPixels[y * w + x]
                    val orig = resetPixels[y * w + x]
                    val nr = (Color.red(cur) * (1f - falloff) + Color.red(orig) * falloff).toInt()
                    val ng = (Color.green(cur) * (1f - falloff) + Color.green(orig) * falloff).toInt()
                    val nb = (Color.blue(cur) * (1f - falloff) + Color.blue(orig) * falloff).toInt()
                    val na = (Color.alpha(cur) * (1f - falloff) + Color.alpha(orig) * falloff).toInt()
                    outPixels[y * w + x] = Color.argb(na, nr, ng, nb)
                    continue
                }

                var u = x.toFloat()
                var v = y.toFloat()
                when (mode) {
                    WarpMode.PUSH -> {
                        u = x - dx * falloff
                        v = y - dy * falloff
                    }
                    WarpMode.TWIRL_CW, WarpMode.TWIRL_CCW -> {
                        val sign = if (mode == WarpMode.TWIRL_CW) 1f else -1f
                        val angle = falloff * 0.9f * sign
                        val cosA = cos(angle)
                        val sinA = sin(angle)
                        u = cx + rx * cosA - ry * sinA
                        v = cy + rx * sinA + ry * cosA
                    }
                    WarpMode.PINCH -> {
                        val scale = 1f + falloff * 0.55f
                        u = cx + rx * scale
                        v = cy + ry * scale
                    }
                    WarpMode.BULGE -> {
                        val scale = (1f - falloff * 0.45f).coerceAtLeast(0.15f)
                        u = cx + rx * scale
                        v = cy + ry * scale
                    }
                    WarpMode.RESET -> {}
                }
                outPixels[y * w + x] = sampleBilinear(srcPixels, w, h, u, v)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Applies pixel dispersion / particle scatter effect to painted mask regions.
     */
    fun applyDispersion(
        src: Bitmap,
        selectionPoints: List<PointF>,
        brushRadius: Float,
        stretch: Float,       // 10..200
        particleSize: Float,  // 4..40
        directionDeg: Float,  // 0..360
        fade: Float           // 0..100
    ): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        if (selectionPoints.isEmpty()) return out

        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = Math.toRadians(directionDeg.toDouble())
        val dirX = cos(rad).toFloat()
        val dirY = sin(rad).toFloat()
        val rng = Random(777)

        for (pt in selectionPoints) {
            val count = (brushRadius / particleSize.coerceAtLeast(4f) * 3f).toInt().coerceIn(3, 18)
            for (i in 0 until count) {
                val ox = (rng.nextFloat() - 0.5f) * brushRadius * 1.6f
                val oy = (rng.nextFloat() - 0.5f) * brushRadius * 1.6f
                val sx = (pt.x + ox).toInt().coerceIn(0, w - 1)
                val sy = (pt.y + oy).toInt().coerceIn(0, h - 1)
                val color = src.getPixel(sx, sy)
                if (Color.alpha(color) < 20) continue

                val dist = rng.nextFloat() * stretch * 1.8f
                val jitterAngle = rad + (rng.nextFloat() - 0.5f) * 0.45f
                val dx = cos(jitterAngle).toFloat() * dist
                val dy = sin(jitterAngle).toFloat() * dist

                // Carve small gap at origin
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                val pSize = (particleSize * (0.6f + rng.nextFloat() * 0.7f)).coerceAtLeast(3f)
                c.drawRect(sx - pSize * 0.3f, sy - pSize * 0.3f, sx + pSize * 0.3f, sy + pSize * 0.3f, p)

                // Draw scattered fragment along direction
                p.xfermode = null
                p.color = color
                val alphaFactor = 1f - (dist / (stretch * 1.8f + 1f)) * (fade / 100f)
                p.alpha = (alphaFactor.coerceIn(0.15f, 1f) * 255).toInt()

                c.save()
                c.translate(sx + dx, sy + dy)
                c.rotate(rng.nextFloat() * 60f - 30f)
                val path = Path().apply {
                    moveTo(0f, -pSize * 0.6f)
                    lineTo(pSize * 0.6f, pSize * 0.5f)
                    lineTo(-pSize * 0.5f, pSize * 0.4f)
                    close()
                }
                c.drawPath(path, p)
                c.restore()
            }
        }
        return out
    }

    /**
     * Boolean operations on 2+ layers (Union, Subtract, Intersect, Exclude XOR).
     * Produces a rasterized bitmap in canvas coordinates.
     */
    fun performBooleanOp(
        canvasW: Int,
        canvasH: Int,
        bottomLayer: EditorLayer,
        topLayer: EditorLayer,
        mode: PorterDuff.Mode
    ): Bitmap {
        val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)

        val b1 = renderLayerInCanvasSpace(canvasW, canvasH, bottomLayer)
        val b2 = renderLayerInCanvasSpace(canvasW, canvasH, topLayer)

        c.drawBitmap(b1, 0f, 0f, null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(mode)
        c.drawBitmap(b2, 0f, 0f, p)
        return out
    }

    private fun renderLayerInCanvasSpace(canvasW: Int, canvasH: Int, layer: EditorLayer): Bitmap {
        val bmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val single = renderSingleLayerBitmap(layer)
        val cx = layer.x + layer.w / 2f
        val cy = layer.y + layer.h / 2f
        c.save()
        c.translate(cx, cy)
        c.rotate(layer.rotation)
        c.translate(-layer.w / 2f, -layer.h / 2f)
        c.drawBitmap(single, 0f, 0f, null)
        c.restore()
        return bmp
    }

    // ============ FILTRI: catalogo Zesty FX ============
    // I filtri sono definiti in public/assets/fx (formato JSON originale, condiviso con la web app)
    // ed eseguiti da com.example.fx.FxEngine. Per aggiungere un filtro basta aggiungere una voce JSON.

    /** Categorie da mostrare nel pannello: "Popolari", "Tutti" e quelle del catalogo. */
    fun getFilterCategories(): List<Pair<String, String>> =
        listOf("Popolari" to "Popolari", "Tutti" to "Tutti") +
            FxEngine.catalog.categories.map { it.id to it.label }

    private val POPULAR_FILTERS = listOf(
        "poster_vintage", "frame_instant", "enhance", "hdr_natural", "bw_classic", "bw_noir", "film_warm", "film_instant", "film_lomo",
        "vintage_classic", "cine_teal_orange", "cine_golden_hour", "color_splash", "duotone",
        "pop_grid4", "pop_halftone", "draw_pencil", "draw_cartoon", "draw_watercolor", "blur_tilt_shift",
        "glitch_vhs", "light_leak_warm", "light_bokeh", "atm_snow", "port_skin"
    )

    fun getFiltersByCategory(category: String): List<EffectFilter> {
        val cat = FxEngine.catalog
        val list = when (category) {
            "Tutti", "All" -> cat.effects
            "Popolari" -> POPULAR_FILTERS.mapNotNull { cat.get(it) }
            else -> cat.inCategory(category)
        }
        return list.map { EffectFilter(id = it.id, name = it.id, label = it.label, category = it.category, algorithm = "zesty-fx") }
    }

    /** Valori dei parametri del filtro: quelli salvati nel livello sopra i predefiniti. */
    fun filterParamValues(filterId: String, overrides: Map<String, Float>): Map<String, Float> =
        (FxEngine.catalog.get(filterId)?.defaultValues() ?: emptyMap()) + overrides

    /**
     * Applica un filtro del catalogo con dissolvenza (0 = effetto pieno, 100 = originale)
     * e metodo di fusione finale.
     */
    fun applyEffectFilter(
        src: Bitmap,
        filterId: String,
        fade: Float = 0f,
        blendMode: String = "Normal",
        params: Map<String, Float> = emptyMap()
    ): Bitmap {
        val strength = (100f - fade.coerceIn(0f, 100f)) / 100f
        return FxEngine.apply(src, filterId, params, strength, blendMode)
    }

    private fun applyColorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        c.drawBitmap(src, 0f, 0f, p)
        return out
    }

    private fun blendBitmaps(base: Bitmap, top: Bitmap, mode: PorterDuff.Mode, alphaWeight: Float): Bitmap {
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(base, 0f, 0f, null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (alphaWeight.coerceIn(0f, 1f) * 255f).toInt()
            xfermode = PorterDuffXfermode(mode)
        }
        c.drawBitmap(top, 0f, 0f, p)
        return out
    }

    private fun applyVignette(src: Bitmap, strength: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val cx = src.width / 2f
        val cy = src.height / 2f
        val radius = hypot(cx, cy)
        val grad = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((strength * 220).toInt().coerceIn(0, 255), 0, 0, 0)),
            floatArrayOf(0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = grad
        }
        c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), p)
        return out
    }

    private fun applyPopArt(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        for (i in pixels.indices) {
            val px = pixels[i]
            val a = Color.alpha(px)
            if (a == 0) continue
            val r = (Color.red(px) / 64) * 85
            val g = (Color.green(px) / 64) * 85
            val b = (Color.blue(px) / 64) * 85
            pixels[i] = Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }
        out.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        return out
    }

    private fun applyGlitch(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val offset = (src.width * 0.02f).toInt().coerceAtLeast(4)
        for (y in 0 until src.height) {
            val lineShift = if (y % 16 in 0..4) offset * 2 else offset
            for (x in 0 until src.width) {
                val idx = y * src.width + x
                val origPx = pixels[idx]
                val a = Color.alpha(origPx)
                if (a == 0) continue
                val rIdx = (y * src.width + (x - lineShift).coerceIn(0, src.width - 1))
                val bIdx = (y * src.width + (x + lineShift).coerceIn(0, src.width - 1))
                val r = Color.red(pixels[rIdx])
                val g = Color.green(origPx)
                val b = Color.blue(pixels[bIdx])
                pixels[idx] = Color.argb(a, r, g, b)
            }
        }
        out.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        return out
    }

}
