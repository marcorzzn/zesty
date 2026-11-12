package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.engine.MaskGeometry
import com.example.engine.TransformGeometry
import com.example.model.CropSubTab
import com.example.model.MaskTool
import com.example.model.LayerType
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import kotlin.math.*

@Composable
fun InteractiveCanvasStage(
    state: EditorUiState,
    vm: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var boxW by remember { mutableFloatStateOf(800f) }
    var boxH by remember { mutableFloatStateOf(800f) }

    val canvasAspect = state.canvasW.toFloat() / state.canvasH.coerceAtLeast(1)
    val availW = (boxW - 24f).coerceAtLeast(100f)
    val availH = (boxH - 24f).coerceAtLeast(100f)
    val stageW: Float
    val stageH: Float
    if (availW / canvasAspect <= availH) {
        stageW = availW
        stageH = availW / canvasAspect
    } else {
        stageH = availH
        stageW = availH * canvasAspect
    }

    val stageLeft = (boxW - stageW) / 2f + state.viewOffsetX
    val stageTop = (boxH - stageH) / 2f + state.viewOffsetY
    val scaleToCanvasX = state.canvasW / stageW.coerceAtLeast(1f)
    val scaleToCanvasY = state.canvasH / stageH.coerceAtLeast(1f)
    // il gestore dei tocchi non viene ricreato a ogni modifica: legge sempre lo stato più recente
    val latestState = rememberUpdatedState(state)
    // Lente d'ingrandimento (posizione del dito sullo schermo) per contorni precisi
    var loupe by remember { mutableStateOf<Offset?>(null) }

    // Prospettiva: angoli animati in coordinate del livello (0..1)
    val pc = state.perspectiveCorners
    val cornerSpec: AnimationSpec<Offset> = if (state.perspDragging) snap() else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    val pa0 by animateOffsetAsState(Offset(pc[0].x, pc[0].y), cornerSpec, label = "p0")
    val pa1 by animateOffsetAsState(Offset(pc[1].x, pc[1].y), cornerSpec, label = "p1")
    val pa2 by animateOffsetAsState(Offset(pc[2].x, pc[2].y), cornerSpec, label = "p2")
    val pa3 by animateOffsetAsState(Offset(pc[3].x, pc[3].y), cornerSpec, label = "p3")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StageSurround)
            .onSizeChanged {
                boxW = it.width.toFloat()
                boxH = it.height.toFloat()
            }
            .testTag("editor_canvas_stage")
            .pointerInput(
                state.currentTool,
                state.eyedropperActive,
                state.cropSubTab,
                stageW,
                stageH,
                state.viewScale,
                state.viewOffsetX,
                state.viewOffsetY
            ) {
                awaitEachGesture {
                    @Suppress("NAME_SHADOWING") val state = latestState.value
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val screenPt = down.position

                    // Convert screen point into canvas (0..canvasW, 0..canvasH)
                    fun toCanvasPt(pt: Offset): Offset {
                        val cx = boxW / 2f + state.viewOffsetX
                        val cy = boxH / 2f + state.viewOffsetY
                        val rx = (pt.x - cx) / state.viewScale
                        val ry = (pt.y - cy) / state.viewScale
                        val localX = rx + stageW / 2f
                        val localY = ry + stageH / 2f
                        return Offset(localX * scaleToCanvasX, localY * scaleToCanvasY)
                    }

                    val startCanvas = toCanvasPt(screenPt)
                    val tool = state.currentTool

                    // 1. Check if in Crop / Perspective corner drag mode
                    if ((tool == "crop" || tool == "rotate") && state.cropSubTab == CropSubTab.PERSPECTIVE) {
                        val act = state.activeLayer
                        if (act != null) {
                            val t2c = scaleToCanvasX / state.viewScale.coerceAtLeast(0.05f)
                            val pts = state.perspectiveCorners.map { Offset(act.x + it.x * act.w, act.y + it.y * act.h) }
                            val hit = pts.indices.minByOrNull { hypot(pts[it].x - startCanvas.x, pts[it].y - startCanvas.y) }
                                ?.takeIf { hypot(pts[it].x - startCanvas.x, pts[it].y - startCanvas.y) < HANDLE_TOUCH_DP.dp.toPx() * t2c * 1.4f } ?: -1
                            if (hit != -1) {
                                vm.setPerspectiveDragging(true)
                                do {
                                    val event = awaitPointerEvent()
                                    val cur = event.changes.firstOrNull() ?: break
                                    if (cur.pressed) {
                                        val cPt = toCanvasPt(cur.position)
                                        vm.perspectiveCornerDrag(hit, cPt.x, cPt.y)
                                        cur.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                                vm.setPerspectiveDragging(false)
                                return@awaitEachGesture
                            }
                        }
                        // fuori dalle maniglie: panoramica e zoom come sempre
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                vm.updateViewportGesture(event.calculatePan().x, event.calculatePan().y, event.calculateZoom(), 0f)
                            } else {
                                val ch = event.changes.firstOrNull() ?: break
                                if (ch.pressed) { val d = ch.position - ch.previousPosition; vm.updateViewportGesture(d.x, d.y, 1f, 0f) }
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }

                    if (tool == "crop" || tool == "rotate") {
                        val normX = startCanvas.x / state.canvasW
                        val normY = startCanvas.y / state.canvasH
                        val corners = if (state.cropSubTab == CropSubTab.PERSPECTIVE) {
                            state.perspectiveCorners.map { Offset(it.x, it.y) }
                        } else {
                            listOf(
                                Offset(state.cropRect.left, state.cropRect.top),
                                Offset(state.cropRect.right, state.cropRect.top),
                                Offset(state.cropRect.right, state.cropRect.bottom),
                                Offset(state.cropRect.left, state.cropRect.bottom)
                            )
                        }
                        val hitCorner = corners.indices.minByOrNull { i ->
                            hypot(corners[i].x - normX, corners[i].y - normY)
                        }?.takeIf { i ->
                            hypot(corners[i].x - normX, corners[i].y - normY) < 0.14f
                        } ?: -1

                        do {
                            val event = awaitPointerEvent()
                            val cur = event.changes.firstOrNull() ?: break
                            if (hitCorner != -1) {
                                val cPt = toCanvasPt(cur.position)
                                vm.updateCropCornerDrag(
                                    hitCorner,
                                    cPt.x / state.canvasW,
                                    cPt.y / state.canvasH
                                )
                                cur.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }

                    // 2. Check if in Collage slot tap or separator line drag (Bug 5 fix)
                    if (tool == "collage") {
                        val nx = (startCanvas.x / state.canvasW).coerceIn(0f, 1f)
                        val ny = (startCanvas.y / state.canvasH).coerceIn(0f, 1f)
                        val nearSplit = abs(nx - state.collageSplitRatio) < 0.06f
                        if (nearSplit) {
                            do {
                                val event = awaitPointerEvent()
                                val cur = event.changes.firstOrNull() ?: break
                                if (cur.pressed) {
                                    val cPt = toCanvasPt(cur.position)
                                    val curNx = (cPt.x / state.canvasW).coerceIn(0.2f, 0.8f)
                                    vm.updateCollageLayout(splitRatio = curNx)
                                    cur.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        } else {
                            val slot = state.collageSlots.find {
                                nx in it.left..it.right && ny in it.top..it.bottom
                            }
                            if (slot != null) {
                                vm.selectCollageSlot(slot.id)
                            }
                        }
                        return@awaitEachGesture
                    }

                    // 3. Direct painting/sampling tools (Brush, Mask, Selective Mask, Warp, Clone, FreeCrop, Dispersion, Pen, Eyedropper)
                    val isDirectPaintTool = state.eyedropperActive ||
                            tool in listOf("brush", "mask", "selective_mask", "warp", "clone", "freecrop", "dispersion", "pen")

                    if (isDirectPaintTool) {
                        // unità della tela per pixel dello schermo (maniglie con presa costante a ogni zoom)
                        vm.touchToCanvas = scaleToCanvasX / state.viewScale.coerceAtLeast(0.05f)
                        val useLoupe = tool == "selective_mask" && state.maskTool != MaskTool.WAND
                        if (useLoupe) loupe = screenPt
                        vm.onCanvasPointerDown(startCanvas.x, startCanvas.y)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                loupe = null
                                // 2-finger pinch zoom/pan even while brush tool active
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val rot = event.calculateRotation()
                                vm.updateViewportGesture(pan.x, pan.y, zoom, rot)
                            } else {
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    val cPt = toCanvasPt(change.position)
                                    if (useLoupe) loupe = change.position
                                    vm.onCanvasPointerMove(cPt.x, cPt.y)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        loupe = null
                        vm.onCanvasPointerUp()
                        return@awaitEachGesture
                    }

                    // 4. Sposta / ridimensiona / ruota il livello attivo (TransformGeometry), zoom e panoramica a due dita
                    // unità della tela per pixel di schermo: le maniglie hanno la stessa presa a ogni zoom
                    val t2c = scaleToCanvasX / state.viewScale.coerceAtLeast(0.05f)
                    var active = state.activeLayer
                    var box0 = active?.let { TransformGeometry.box(it.x, it.y, it.w, it.h, it.rotation) }
                    var hit: TransformGeometry.Handle? = null
                    if (active != null && !active.locked && box0 != null && !state.handlesHidden) {
                        val screenMin = min(active.w, active.h) / t2c
                        hit = TransformGeometry.hit(
                            box0, startCanvas.x, startCanvas.y,
                            radius = HANDLE_TOUCH_DP.dp.toPx() * t2c,
                            rotateOffset = ROTATE_OFFSET_DP.dp.toPx() * t2c,
                            deleteOffset = DELETE_OFFSET_DP.dp.toPx() * t2c,
                            showSides = screenMin > SIDE_HANDLES_MIN_DP.dp.toPx()
                        )
                    }
                    if (hit == TransformGeometry.Handle.DELETE) {
                        vm.deleteActiveLayer()
                        return@awaitEachGesture
                    }
                    // fuori dalle maniglie: il livello più in alto sotto il dito (non bloccato) diventa attivo
                    var emptyTouch = false
                    if (hit == null || hit == TransformGeometry.Handle.BODY) {
                        val top = vm.layerAt(startCanvas.x, startCanvas.y)
                        if (top != null) {
                            if (top.id != active?.id || state.handlesHidden) vm.tapLayer(top.id)
                            active = top
                            box0 = TransformGeometry.box(top.x, top.y, top.w, top.h, top.rotation)
                            hit = TransformGeometry.Handle.BODY
                        } else {
                            hit = null
                            emptyTouch = true
                        }
                    }
                    val startBox = box0
                    val handle = hit
                    val touchSlop = viewConfiguration.touchSlop
                    var moved = false
                    val startAngle = startBox?.let { TransformGeometry.angleOf(it, startCanvas.x, startCanvas.y) } ?: 0f
                    val keepRatio = true // angoli proporzionali, lati liberi

                    var prevCanvas = startCanvas
                    var modifiedLayer = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            moved = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val rot = event.calculateRotation()
                            if (handle == TransformGeometry.Handle.BODY && active != null) {
                                vm.transformActiveLayer(
                                    dx = pan.x * scaleToCanvasX / state.viewScale,
                                    dy = pan.y * scaleToCanvasY / state.viewScale,
                                    dScale = zoom,
                                    dRot = rot
                                )
                                modifiedLayer = true
                            } else {
                                vm.updateViewportGesture(pan.x, pan.y, zoom, rot)
                            }
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                if (!moved && (change.position - screenPt).getDistance() > touchSlop) moved = true
                                val curCanvas = toCanvasPt(change.position)
                                val h = handle
                                when {
                                    startBox != null && h != null && (h.isCorner || h.isSide) -> {
                                        vm.setActiveLayerBox(
                                            TransformGeometry.resize(startBox, h, curCanvas.x, curCanvas.y, keepRatio = keepRatio && h.isCorner, fromCenter = false, minSize = 16f)
                                        )
                                        modifiedLayer = true
                                    }
                                    startBox != null && h == TransformGeometry.Handle.ROTATE -> {
                                        vm.setActiveLayerBox(TransformGeometry.rotate(startBox, startAngle, curCanvas.x, curCanvas.y))
                                        modifiedLayer = true
                                    }
                                    h == TransformGeometry.Handle.BODY -> {
                                        vm.transformActiveLayer(curCanvas.x - prevCanvas.x, curCanvas.y - prevCanvas.y)
                                        modifiedLayer = true
                                    }
                                    else -> {
                                        val screenDelta = change.position - change.previousPosition
                                        vm.updateViewportGesture(screenDelta.x, screenDelta.y, 1f, 0f)
                                    }
                                }
                                prevCanvas = curCanvas
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (modifiedLayer) {
                        vm.pushHistorySnapshot()
                    }
                    // tocco a vuoto (senza trascinare): via le maniglie e chiuso il pannello dei livelli
                    if (emptyTouch && !moved) vm.tapEmptyCanvas()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(state.viewOffsetX, state.viewOffsetY)
                scale(state.viewScale, state.viewScale, pivot = center)
                rotate(state.viewRotation, pivot = center)
            }) {
                val left = (size.width - stageW) / 2f
                val top = (size.height - stageH) / 2f

                // Draw alpha checkerboard behind canvas so transparent backgrounds are clearly visible
                drawRect(
                    color = StageSurround,
                    topLeft = Offset(left, top),
                    size = Size(stageW, stageH)
                )
                val checkSize = 18.dp.toPx()
                val cols = (stageW / checkSize).toInt().coerceAtLeast(1)
                val rows = (stageH / checkSize).toInt().coerceAtLeast(1)
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        if ((r + c) % 2 == 0) {
                            val cx = left + c * checkSize
                            val cy = top + r * checkSize
                            val cw = (left + stageW - cx).coerceAtMost(checkSize)
                            val ch = (top + stageH - cy).coerceAtMost(checkSize)
                            if (cw > 0f && ch > 0f) {
                                drawRect(
                                    color = StageCheckA,
                                    topLeft = Offset(cx, cy),
                                    size = Size(cw, ch)
                                )
                            }
                        }
                    }
                }

                // Scontorna: sfondo di prova sotto il soggetto (non entra nell'immagine esportata)
                if (state.currentTool == "selective_mask" && state.cutoutStudio && state.cutoutBackdrop != 0) {
                    val bd = when (state.cutoutBackdrop) { 1 -> Color(0xFF151413); 2 -> Color.White; else -> ActivePurple }
                    drawRect(bd, topLeft = Offset(left, top), size = Size(stageW, stageH))
                }

                // Draw composite raster bitmap
                val perspPreview = (state.currentTool == "crop" || state.currentTool == "rotate") &&
                        state.cropSubTab == CropSubTab.PERSPECTIVE && state.perspSource != null && state.activeLayer != null
                if (perspPreview) {
                    val act = state.activeLayer!!
                    val src = state.perspSource!!
                    state.perspBackdrop?.let { bd ->
                        drawImage(bd.asImageBitmap(), dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(stageW.roundToInt(), stageH.roundToInt()))
                    }
                    val sx = stageW / state.canvasW; val sy = stageH / state.canvasH
                    fun toSt(o: Offset) = Offset(left + (act.x + o.x * act.w) * sx, top + (act.y + o.y * act.h) * sy)
                    val q = listOf(pa0, pa1, pa2, pa3).map { toSt(it) }
                    val m = android.graphics.Matrix()
                    m.setPolyToPoly(
                        floatArrayOf(0f, 0f, src.width.toFloat(), 0f, src.width.toFloat(), src.height.toFloat(), 0f, src.height.toFloat()), 0,
                        floatArrayOf(q[0].x, q[0].y, q[1].x, q[1].y, q[2].x, q[2].y, q[3].x, q[3].y), 0, 4
                    )
                    drawIntoCanvas { c ->
                        c.nativeCanvas.drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
                    }
                }
                if (!perspPreview) state.compositeBitmap?.let { bmp ->
                    if (state.currentTool == "crop" || state.currentTool == "rotate") {
                        rotate(state.cropRotation, pivot = Offset(left + stageW / 2f, top + stageH / 2f)) {
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(stageW.roundToInt(), stageH.roundToInt())
                            )
                        }
                    } else {
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(stageW.roundToInt(), stageH.roundToInt())
                        )
                    }
                }

                drawRect(StageFrame, topLeft = Offset(left, top), size = Size(stageW, stageH), style = Stroke(width = 1.dp.toPx() / state.viewScale.coerceAtLeast(0.05f)))

                // §23 Selective Mask semi-transparent red heatmap overlay (alpha 0.4)
                if (state.currentTool == "selective_mask" && state.showMaskHeatmap) {
                    val activeLayer = state.activeLayer
                    if (activeLayer != null) {
                        val maskBmp = when (state.selectiveTargetFxKey) {
                            null -> activeLayer.maskBitmap
                            EditorViewModel.FILTER_FX_KEY -> activeLayer.activeFilterMask
                            else -> activeLayer.effects[state.selectiveTargetFxKey]?.localMask
                        }
                        if (maskBmp != null) {
                            val lx = left + (activeLayer.x / state.canvasW) * stageW
                            val ly = top + (activeLayer.y / state.canvasH) * stageH
                            val lw = (activeLayer.w / state.canvasW) * stageW
                            val lh = (activeLayer.h / state.canvasH) * stageH
                            rotate(activeLayer.rotation, pivot = Offset(lx + lw / 2f, ly + lh / 2f)) {
                                drawImage(
                                    image = maskBmp.asImageBitmap(),
                                    dstOffset = IntOffset(lx.roundToInt(), ly.roundToInt()),
                                    dstSize = IntSize(lw.roundToInt().coerceAtLeast(1), lh.roundToInt().coerceAtLeast(1)),
                                    alpha = 0.42f,
                                    colorFilter = ColorFilter.tint(Color(0xFFFF1E42), BlendMode.Modulate)
                                )
                            }
                        }
                    }
                }

                // Zona in bozza: forma con maniglie, lazo o poligono modellabile
                if (state.currentTool == "selective_mask") {
                    val toStage = android.graphics.Matrix().apply {
                        postScale(stageW / state.canvasW, stageH / state.canvasH)
                        postTranslate(left, top)
                    }
                    fun st(p: android.graphics.PointF) = Offset(left + p.x / state.canvasW * stageW, top + p.y / state.canvasH * stageH)
                    val outlineW = 2.dp.toPx()
                    state.maskShapeDraft?.let { d ->
                        val path = MaskGeometry.outline(d).apply { transform(toStage) }.asComposePath()
                        drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(width = outlineW * 2f))
                        drawPath(path, AccentPinkHi, style = Stroke(width = outlineW, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))))
                        val hs = MaskGeometry.handles(d, 40f * vm.touchToCanvas).map { st(it) }
                        // riquadro di controllo e collegamento alla maniglia di rotazione
                        for (i in 0 until 4) drawLine(Color.White.copy(alpha = 0.6f), hs[i], hs[(i + 1) % 4], strokeWidth = 1.dp.toPx())
                        val topMid = Offset((hs[0].x + hs[1].x) / 2f, (hs[0].y + hs[1].y) / 2f)
                        drawLine(Color.White.copy(alpha = 0.8f), topMid, hs[4], strokeWidth = 1.5.dp.toPx())
                        hs.forEachIndexed { i, h ->
                            drawCircle(Color(0xCC141417), radius = 8.5.dp.toPx(), center = h) // anello scuro: visibile anche su foto chiare
                            drawCircle(Color.White, radius = 7.dp.toPx(), center = h)
                            drawCircle(if (i == 4) ActivePurple else AccentPink, radius = 4.5.dp.toPx(), center = h)
                        }
                    }
                    val pts = state.maskPolyPoints
                    if (pts.isNotEmpty()) {
                        val sp = pts.map { st(it) }
                        val path = Path().apply {
                            moveTo(sp[0].x, sp[0].y)
                            for (i in 1 until sp.size) lineTo(sp[i].x, sp[i].y)
                            if (sp.size >= 3) close()
                        }
                        if (sp.size >= 3) drawPath(path, AccentPink.copy(alpha = 0.18f))
                        drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(width = outlineW * 2f))
                        drawPath(path, AccentPinkHi, style = Stroke(width = outlineW))
                        // i punti si mostrano quando il contorno è stato chiuso o costruito a mano
                        if (state.maskTool == MaskTool.POLYGON || sp.size <= 64) {
                            sp.forEach { c ->
                                drawCircle(Color(0xCC141417), radius = 7.5.dp.toPx(), center = c) // anello scuro: visibile anche su foto chiare
                                drawCircle(Color.White, radius = 6.dp.toPx(), center = c)
                                drawCircle(ActivePurple, radius = 3.5.dp.toPx(), center = c)
                            }
                        }
                    }
                }

                // Dividi e salva: le parti escluse si scuriscono, ogni pannello ha contorno e numero
                if (state.currentTool == "split") {
                    val rects = com.example.engine.SplitGeometry.rects(state.canvasW, state.canvasH, state.split)
                    val sx = stageW / state.canvasW; val sy = stageH / state.canvasH
                    val zs = state.viewScale.coerceAtLeast(0.05f)
                    val keep = Path().apply {
                        rects.forEach { r -> addRect(androidx.compose.ui.geometry.Rect(left + r.left * sx, top + r.top * sy, left + r.right * sx, top + r.bottom * sy)) }
                    }
                    val all = Path().apply { addRect(androidx.compose.ui.geometry.Rect(left, top, left + stageW, top + stageH)) }
                    drawPath(Path.combine(PathOperation.Difference, all, keep), Color.Black.copy(alpha = 0.6f))
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE; textSize = 13.dp.toPx() / zs; isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    rects.forEachIndexed { i, r ->
                        val l = left + r.left * sx; val t = top + r.top * sy; val rw = r.width() * sx; val rh = r.height() * sy
                        drawRect(Color.Black.copy(alpha = 0.5f), Offset(l, t), Size(rw, rh), style = Stroke(width = 3.dp.toPx() / zs))
                        drawRect(AccentPinkHi, Offset(l, t), Size(rw, rh), style = Stroke(width = 1.5.dp.toPx() / zs))
                        val c = Offset(l + 16.dp.toPx() / zs, t + 16.dp.toPx() / zs)
                        drawCircle(Color(0xE61F1D1A), radius = 11.dp.toPx() / zs, center = c)
                        drawIntoCanvas { it.nativeCanvas.drawText("${i + 1}", c.x, c.y + 4.5.dp.toPx() / zs, paint) }
                    }
                }

                // Collage interactive overlay when collage tool open
                if (state.currentTool == "collage") {
                    drawRect(
                        color = Color(state.bgColor),
                        topLeft = Offset(left, top),
                        size = Size(stageW, stageH)
                    )
                    state.collageSlots.forEach { slot ->
                        val sl = left + slot.left * stageW + 4f
                        val st = top + slot.top * stageH + 4f
                        val sw = (slot.right - slot.left) * stageW - 8f
                        val sh = (slot.bottom - slot.top) * stageH - 8f
                        slot.bitmap?.let { b ->
                            drawImage(
                                image = b.asImageBitmap(),
                                dstOffset = IntOffset(sl.roundToInt(), st.roundToInt()),
                                dstSize = IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1))
                            )
                        }
                        val isSel = slot.id == state.activeCollageSlotId
                        drawRect(
                            color = if (isSel) AccentPinkHi else Color.White.copy(alpha = 0.5f),
                            topLeft = Offset(sl, st),
                            size = Size(sw, sh),
                            style = Stroke(width = if (isSel) 3.dp.toPx() else 1.dp.toPx())
                        )
                    }
                    // Bug 5 fix: visible draggable separator line + grip pill
                    val splitX = left + state.collageSplitRatio * stageW
                    drawLine(
                        color = AccentPinkHi,
                        start = Offset(splitX, top),
                        end = Offset(splitX, top + stageH),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(splitX, top + stageH / 2f)
                    )
                    drawCircle(
                        color = AccentPink,
                        radius = 4.5.dp.toPx(),
                        center = Offset(splitX, top + stageH / 2f)
                    )
                }

                // Crop / Perspective Overlay
                if (state.currentTool == "crop" || state.currentTool == "rotate") {
                    if (state.cropSubTab == CropSubTab.PERSPECTIVE) {
                        val act = state.activeLayer
                        val zs = state.viewScale.coerceAtLeast(0.05f)
                        // griglia fissa: confronto immediato con verticali e orizzontali vere
                        for (i in 1..5) {
                            val fx = left + stageW * i / 6f; val fy = top + stageH * i / 6f
                            drawLine(Color.White.copy(alpha = 0.28f), Offset(fx, top), Offset(fx, top + stageH), strokeWidth = 1.dp.toPx() / zs)
                            drawLine(Color.White.copy(alpha = 0.28f), Offset(left, fy), Offset(left + stageW, fy), strokeWidth = 1.dp.toPx() / zs)
                        }
                        if (act != null) {
                            val sx = stageW / state.canvasW; val sy = stageH / state.canvasH
                            val pts = listOf(pa0, pa1, pa2, pa3).map { Offset(left + (act.x + it.x * act.w) * sx, top + (act.y + it.y * act.h) * sy) }
                            val quad = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                            drawPath(quad, Color.Black.copy(alpha = 0.4f), style = Stroke(width = 3.dp.toPx() / zs))
                            drawPath(quad, ActivePurple, style = Stroke(width = 1.5.dp.toPx() / zs))
                            pts.forEach { pt ->
                                drawCircle(Color(0xCC141417), radius = 9.5.dp.toPx() / zs, center = pt)
                                drawCircle(Color.White, radius = 8.dp.toPx() / zs, center = pt)
                                drawCircle(ActivePurple, radius = 6.dp.toPx() / zs, center = pt)
                            }
                        }
                    } else {
                        val cl = left + state.cropRect.left * stageW
                        val ct = top + state.cropRect.top * stageH
                        val cw = state.cropRect.width() * stageW
                        val ch = state.cropRect.height() * stageH

                        // Dim outside crop
                        drawRect(Color.Black.copy(alpha = 0.55f), Offset(left, top), Size(stageW, ct - top))
                        drawRect(Color.Black.copy(alpha = 0.55f), Offset(left, ct + ch), Size(stageW, (top + stageH) - (ct + ch)))
                        drawRect(Color.Black.copy(alpha = 0.55f), Offset(left, ct), Size(cl - left, ch))
                        drawRect(Color.Black.copy(alpha = 0.55f), Offset(cl + cw, ct), Size((left + stageW) - (cl + cw), ch))

                        // Crop frame + rule of thirds
                        drawRect(Color.White, Offset(cl, ct), Size(cw, ch), style = Stroke(width = 2.dp.toPx()))
                        for (i in 1..2) {
                            val p = i / 3f
                            drawLine(
                                Color.White.copy(alpha = 0.35f),
                                Offset(cl + cw * p, ct),
                                Offset(cl + cw * p, ct + ch),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                Color.White.copy(alpha = 0.35f),
                                Offset(cl, ct + ch * p),
                                Offset(cl + cw, ct + ch * p),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // 4 Corner handles
                        listOf(
                            Offset(cl, ct),
                            Offset(cl + cw, ct),
                            Offset(cl + cw, ct + ch),
                            Offset(cl, ct + ch)
                        ).forEach { corner ->
                            drawCircle(Color(0xCC141417), radius = 7.5.dp.toPx(), center = corner) // anello scuro: visibile anche su foto chiare
                            drawCircle(Color.White, radius = 6.dp.toPx(), center = corner)
                            drawCircle(ActivePurple, radius = 4.dp.toPx(), center = corner)
                        }
                    }
                }

                // Lasso / Selection / Dispersion overlay path
                if (state.lassoPoints.size >= 2) {
                    val lassoPath = Path()
                    state.lassoPoints.forEachIndexed { idx, pt ->
                        val sx = left + (pt.x / state.canvasW) * stageW
                        val sy = top + (pt.y / state.canvasH) * stageH
                        if (idx == 0) lassoPath.moveTo(sx, sy) else lassoPath.lineTo(sx, sy)
                    }
                    drawPath(
                        path = lassoPath,
                        color = AccentPinkHi,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                        )
                    )
                }

                // Clone source crosshair
                state.cloneSourcePoint?.let { srcPt ->
                    if (state.currentTool == "clone") {
                        val sx = left + (srcPt.x / state.canvasW) * stageW
                        val sy = top + (srcPt.y / state.canvasH) * stageH
                        drawCircle(AccentPinkHi, radius = 10.dp.toPx(), center = Offset(sx, sy), style = Stroke(2.dp.toPx()))
                        drawLine(AccentPinkHi, Offset(sx - 14.dp.toPx(), sy), Offset(sx + 14.dp.toPx(), sy), 2.dp.toPx())
                        drawLine(AccentPinkHi, Offset(sx, sy - 14.dp.toPx()), Offset(sx, sy + 14.dp.toPx()), 2.dp.toPx())
                    }
                }

                // Vector Pen vertices overlay
                val active = state.activeLayer
                if (state.currentTool == "pen" && active != null && active.type == LayerType.PATH) {
                    active.pathPoints.forEach { np ->
                        val vx = left + ((active.x + np.x * active.w) / state.canvasW) * stageW
                        val vy = top + ((active.y + np.y * active.h) / state.canvasH) * stageH
                        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(vx, vy))
                        drawCircle(AccentPink, radius = 4.dp.toPx(), center = Offset(vx, vy))
                    }
                }

                // Active Layer Bounding Box + Quick Delete 'X' + 8 Resize Handles + Rotation Knob with Icon
                if (active != null && !state.handlesHidden && state.currentTool !in listOf("crop", "rotate", "collage") &&
                    (state.layers.size > 1 || active.type != LayerType.RASTER || active.bitmap != null)) {
                    // Riquadro, maniglie e rotazione: dimensioni costanti sullo schermo (divise per lo zoom)
                    val zs = state.viewScale.coerceAtLeast(0.05f)
                    val toStage = { p: android.graphics.PointF -> Offset(left + p.x / state.canvasW * stageW, top + p.y / state.canvasH * stageH) }
                    val c2s = stageW / state.canvasW // pixel di tela-a-schermo prima dello zoom
                    val t2c = 1f / (c2s * zs)
                    val box = TransformGeometry.box(active.x, active.y, active.w, active.h, active.rotation)
                    val hs = TransformGeometry.handles(box, ROTATE_OFFSET_DP.dp.toPx() * t2c, DELETE_OFFSET_DP.dp.toPx() * t2c)
                    val showSides = min(active.w, active.h) / t2c > SIDE_HANDLES_MIN_DP.dp.toPx()
                    val corners = listOf(TransformGeometry.Handle.TL, TransformGeometry.Handle.TR, TransformGeometry.Handle.BR, TransformGeometry.Handle.BL).map { toStage(hs.getValue(it)) }
                    val outline = Path().apply { moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    drawPath(outline, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 3.dp.toPx() / zs))
                    drawPath(outline, ActivePurple, style = Stroke(width = 1.5.dp.toPx() / zs))

                    // rotazione
                    val topMid = Offset((corners[0].x + corners[1].x) / 2f, (corners[0].y + corners[1].y) / 2f)
                    val knob = toStage(hs.getValue(TransformGeometry.Handle.ROTATE))
                    drawLine(ActivePurple, topMid, knob, 1.5.dp.toPx() / zs)
                    drawCircle(Color(0xCC141417), radius = 11.5.dp.toPx() / zs, center = knob) // anello scuro: visibile anche su foto chiare
                    drawCircle(Color.White, radius = 10.dp.toPx() / zs, center = knob)
                    drawCircle(ActivePurple, radius = 8.dp.toPx() / zs, center = knob)
                    drawArc(
                        color = Color.White, startAngle = 40f, sweepAngle = 280f, useCenter = false,
                        topLeft = Offset(knob.x - 4.dp.toPx() / zs, knob.y - 4.dp.toPx() / zs),
                        size = Size(8.dp.toPx() / zs, 8.dp.toPx() / zs), style = Stroke(1.5.dp.toPx() / zs)
                    )

                    // angoli (proporzionali) e lati (un asse)
                    hs.forEach { (h, p) ->
                        val c = toStage(p)
                        when {
                            h.isCorner -> {
                                drawCircle(Color(0xCC141417), radius = 8.5.dp.toPx() / zs, center = c) // anello scuro: visibile anche su foto chiare
                                drawCircle(Color.White, radius = 7.dp.toPx() / zs, center = c)
                                drawCircle(ActivePurple, radius = 5.dp.toPx() / zs, center = c)
                            }
                            h.isSide && showSides -> {
                                drawCircle(Color(0xCC141417), radius = 7.0.dp.toPx() / zs, center = c) // anello scuro: visibile anche su foto chiare
                                drawCircle(Color.White, radius = 5.5.dp.toPx() / zs, center = c)
                                drawCircle(ActivePurple, radius = 3.5.dp.toPx() / zs, center = c)
                            }
                        }
                    }

                    // eliminazione rapida, fuori dall'angolo in alto a sinistra
                    val del = toStage(hs.getValue(TransformGeometry.Handle.DELETE))
                    drawCircle(Color(0xFFE53935), radius = 10.dp.toPx() / zs, center = del)
                    val xArm = 3.5.dp.toPx() / zs
                    drawLine(Color.White, Offset(del.x - xArm, del.y - xArm), Offset(del.x + xArm, del.y + xArm), 1.8.dp.toPx() / zs)
                    drawLine(Color.White, Offset(del.x + xArm, del.y - xArm), Offset(del.x - xArm, del.y + xArm), 1.8.dp.toPx() / zs)
                }
            }

            // Lente: mostra ingrandita (2,5× la vista) la zona sotto il dito, nell'angolo opposto
            val lp = loupe
            val comp = state.compositeBitmap
            if (lp != null && comp != null && state.currentTool == "selective_mask") {
                val r = 56.dp.toPx()
                val margin = 10.dp.toPx()
                val center = Offset(if (lp.x < size.width / 2f) size.width - r - margin else r + margin, r + margin)
                val ccx = ((lp.x - (size.width / 2f + state.viewOffsetX)) / state.viewScale + stageW / 2f) * scaleToCanvasX
                val ccy = ((lp.y - (size.height / 2f + state.viewOffsetY)) / state.viewScale + stageH / 2f) * scaleToCanvasY
                val screenPerCanvas = stageW * state.viewScale / state.canvasW.coerceAtLeast(1)
                val k = screenPerCanvas * 2.5f          // pixel della lente per unità della tela
                val half = r / k                        // metà del lato visibile, in unità della tela
                val bx = comp.width.toFloat() / state.canvasW.coerceAtLeast(1)
                val by = comp.height.toFloat() / state.canvasH.coerceAtLeast(1)
                val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                drawCircle(Color.Black.copy(alpha = 0.55f), radius = r + 3.dp.toPx(), center = center)
                clipPath(circle) {
                    drawRect(StageCheckA, topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r))
                    if (state.cutoutStudio && state.cutoutBackdrop != 0) {
                        drawRect(when (state.cutoutBackdrop) { 1 -> Color(0xFF151413); 2 -> Color.White; else -> ActivePurple },
                            topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r))
                    }
                    drawIntoCanvas { c ->
                        val src = android.graphics.RectF((ccx - half) * bx, (ccy - half) * by, (ccx + half) * bx, (ccy + half) * by)
                        val dst = android.graphics.RectF(center.x - r, center.y - r, center.x + r, center.y + r)
                        val m = android.graphics.Matrix().apply { setRectToRect(src, dst, android.graphics.Matrix.ScaleToFit.FILL) }
                        c.nativeCanvas.drawBitmap(comp, m, null) // senza filtro: si vedono i pixel veri
                    }
                    // contorno in bozza e dimensione del pennello dentro la lente
                    fun toL(p: android.graphics.PointF) = Offset(center.x + (p.x - ccx) * k, center.y + (p.y - ccy) * k)
                    val pts = state.maskPolyPoints
                    if (pts.size >= 2) {
                        val pl = Path().apply { moveTo(toL(pts[0]).x, toL(pts[0]).y); for (i in 1 until pts.size) { val q = toL(pts[i]); lineTo(q.x, q.y) } }
                        drawPath(pl, Color.Black.copy(alpha = 0.6f), style = Stroke(width = 3.dp.toPx()))
                        drawPath(pl, AccentPinkHi, style = Stroke(width = 1.5.dp.toPx()))
                    }
                    if (state.maskTool == MaskTool.BRUSH) {
                        drawCircle(Color.White.copy(alpha = 0.8f), radius = (state.brushSize / 2f * k).coerceAtMost(r), center = center, style = Stroke(width = 1.dp.toPx()))
                    }
                }
                drawCircle(StageFrame, radius = r, center = center, style = Stroke(width = 1.5.dp.toPx()))
                val arm = 7.dp.toPx()
                drawLine(Color.Black.copy(alpha = 0.7f), Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), 3.dp.toPx())
                drawLine(Color.Black.copy(alpha = 0.7f), Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), 3.dp.toPx())
                drawLine(AccentPinkHi, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), 1.2.dp.toPx())
                drawLine(AccentPinkHi, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), 1.2.dp.toPx())
            }
        }
    }
}

// Misure delle maniglie in dp di schermo (docs/UI_PRINCIPLES.md: area di tocco ≥ 44 dp di diametro)
private const val HANDLE_TOUCH_DP = 24f
private const val ROTATE_OFFSET_DP = 32f
private const val DELETE_OFFSET_DP = 30f
private const val SIDE_HANDLES_MIN_DP = 96f
