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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.CropSubTab
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgBlack)
            .onSizeChanged {
                boxW = it.width.toFloat()
                boxH = it.height.toFloat()
            }
            .testTag("editor_canvas_stage")
            .pointerInput(
                state.currentTool,
                state.eyedropperActive,
                state.cropSubTab,
                state.activeLayerId,
                stageW,
                stageH,
                state.viewScale,
                state.viewOffsetX,
                state.viewOffsetY
            ) {
                awaitEachGesture {
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
                        vm.onCanvasPointerDown(startCanvas.x, startCanvas.y)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                // 2-finger pinch zoom/pan even while brush tool active
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val rot = event.calculateRotation()
                                vm.updateViewportGesture(pan.x, pan.y, zoom, rot)
                            } else {
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    val cPt = toCanvasPt(change.position)
                                    vm.onCanvasPointerMove(cPt.x, cPt.y)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        vm.onCanvasPointerUp()
                        return@awaitEachGesture
                    }

                    // 4. Default Layer Move / 8-Handle Resize / Rotation Handle / 2-Finger Viewport Zoom
                    val active = state.activeLayer
                    var hitHandle = -1 // 0..7 = resize handles, 8 = top rotation knob, 9 = inside layer drag
                    if (active != null && !active.locked) {
                        val handles = getLayerHandlePoints(active.x, active.y, active.w, active.h)
                        val rotKnob = Offset(active.x + active.w / 2f, active.y - 36f)
                        if (hypot(startCanvas.x - rotKnob.x, startCanvas.y - rotKnob.y) < 38f * scaleToCanvasX) {
                            hitHandle = 8
                        } else {
                            val idx = handles.indices.minByOrNull { i ->
                                hypot(startCanvas.x - handles[i].x, startCanvas.y - handles[i].y)
                            } ?: -1
                            if (idx != -1 && hypot(startCanvas.x - handles[idx].x, startCanvas.y - handles[idx].y) < 34f * scaleToCanvasX) {
                                hitHandle = idx
                            } else if (
                                startCanvas.x in active.x..(active.x + active.w) &&
                                startCanvas.y in active.y..(active.y + active.h)
                            ) {
                                hitHandle = 9
                            }
                        }
                    }

                    var prevCanvas = startCanvas
                    var modifiedLayer = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val rot = event.calculateRotation()
                            if (hitHandle == 9 && active != null) {
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
                                val curCanvas = toCanvasPt(change.position)
                                val dx = curCanvas.x - prevCanvas.x
                                val dy = curCanvas.y - prevCanvas.y
                                when (hitHandle) {
                                    in 0..7 -> {
                                        vm.resizeActiveLayerHandle(hitHandle, dx, dy)
                                        modifiedLayer = true
                                    }
                                    8 -> {
                                        if (active != null) {
                                            val lcx = active.x + active.w / 2f
                                            val lcy = active.y + active.h / 2f
                                            val angle = Math.toDegrees(
                                                atan2((curCanvas.y - lcy).toDouble(), (curCanvas.x - lcx).toDouble())
                                            ).toFloat() + 90f
                                            vm.rotateActiveLayerToAngle(angle)
                                            modifiedLayer = true
                                        }
                                    }
                                    9 -> {
                                        vm.transformActiveLayer(dx, dy)
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
                    color = Color(0xFF1A1A22),
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
                                    color = Color(0xFF262632),
                                    topLeft = Offset(cx, cy),
                                    size = Size(cw, ch)
                                )
                            }
                        }
                    }
                }

                // Draw composite raster bitmap
                state.compositeBitmap?.let { bmp ->
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

                // §23 Selective Mask semi-transparent red heatmap overlay (alpha 0.4)
                if (state.currentTool == "selective_mask" && state.showMaskHeatmap) {
                    val activeLayer = state.activeLayer
                    if (activeLayer != null) {
                        val maskBmp = if (state.selectiveTargetFxKey != null) {
                            activeLayer.effects[state.selectiveTargetFxKey]?.localMask
                        } else {
                            activeLayer.maskBitmap
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
                        val pts = state.perspectiveCorners.map {
                            Offset(left + it.x * stageW, top + it.y * stageH)
                        }
                        if (pts.size == 4) {
                            val quad = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                lineTo(pts[1].x, pts[1].y)
                                lineTo(pts[2].x, pts[2].y)
                                lineTo(pts[3].x, pts[3].y)
                                close()
                            }
                            drawPath(quad, ActivePurple, style = Stroke(width = 2.5.dp.toPx()))
                            pts.forEach { pt ->
                                drawCircle(Color.White, radius = 7.dp.toPx(), center = pt)
                                drawCircle(ActivePurple, radius = 5.dp.toPx(), center = pt)
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

                // Active Layer Bounding Box + 8 Resize Handles + Top Rotation Knob
                if (active != null && state.currentTool !in listOf("crop", "rotate", "collage") && state.layers.size > 1) {
                    val lx = left + (active.x / state.canvasW) * stageW
                    val ly = top + (active.y / state.canvasH) * stageH
                    val lw = (active.w / state.canvasW) * stageW
                    val lh = (active.h / state.canvasH) * stageH

                    rotate(active.rotation, pivot = Offset(lx + lw / 2f, ly + lh / 2f)) {
                        drawRect(
                            color = ActivePurple.copy(alpha = 0.85f),
                            topLeft = Offset(lx, ly),
                            size = Size(lw, lh),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // Rotation stem & knob
                        val knobCenter = Offset(lx + lw / 2f, ly - 22.dp.toPx())
                        drawLine(ActivePurple, Offset(lx + lw / 2f, ly), knobCenter, 1.5.dp.toPx())
                        drawCircle(Color.White, radius = 6.dp.toPx(), center = knobCenter)
                        drawCircle(AccentPink, radius = 4.dp.toPx(), center = knobCenter)

                        // 8 Resize Handles
                        val handles = getLayerHandlePoints(lx, ly, lw, lh)
                        handles.forEach { hPt ->
                            drawCircle(Color.White, radius = 5.dp.toPx(), center = hPt)
                            drawCircle(ActivePurple, radius = 3.5.dp.toPx(), center = hPt)
                        }
                    }
                }
            }
        }
    }
}

private fun getLayerHandlePoints(x: Float, y: Float, w: Float, h: Float): List<Offset> = listOf(
    Offset(x, y),                   // 0: TL
    Offset(x + w / 2f, y),          // 1: TC
    Offset(x + w, y),               // 2: TR
    Offset(x + w, y + h / 2f),      // 3: RC
    Offset(x + w, y + h),           // 4: BR
    Offset(x + w / 2f, y + h),      // 5: BC
    Offset(x, y + h),               // 6: BL
    Offset(x, y + h / 2f)           // 7: LC
)
