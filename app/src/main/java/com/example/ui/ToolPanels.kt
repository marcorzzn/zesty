package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.PixelEngine
import com.example.model.*
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import kotlin.math.hypot
import kotlin.math.roundToInt

// Design tokens from the HTML mockup
val BgBlack = Color(0xFF000000)
val PanelDark = Color(0xFF0D0D10)
val Panel2 = Color(0xFF16161A)
val Panel3 = Color(0xFF1C1C22)
val BorderCol = Color(0xFF26262E)
val TextWhite = Color(0xFFFFFFFF)
val TextDim = Color(0xFF9A9AA5)
val TextMute = Color(0xFF5C5C66)
val AccentPink = Color(0xFFE0289A)
val AccentPinkHi = Color(0xFFFF3DAD)
val ActivePurple = Color(0xFF8B5CF6)

@Composable
fun CustomSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    suffix: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.width(84.dp)
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = AccentPinkHi,
                activeTrackColor = AccentPink,
                inactiveTrackColor = BorderCol
            )
        )
        Text(
            text = "${value.roundToInt()}$suffix",
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(42.dp)
        )
    }
}

// ============ 1. CROP / ROTATE / 3D PERSPECTIVE PANEL ============
@Composable
fun CropToolPanel(state: EditorUiState, vm: EditorViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgBlack)
    ) {
        // Rotation / Perspective top controls
        if (state.cropSubTab != CropSubTab.PERSPECTIVE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f°", state.cropRotation),
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reset rotazione",
                        tint = TextDim,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { vm.setCropRotation(0f) }
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("-45°", "-30°", "-15°", "0°", "15°", "30°", "45°").forEach { tick ->
                        Text(tick, color = TextMute, fontSize = 9.sp)
                    }
                }
                Slider(
                    value = state.cropRotation.coerceIn(-45f, 45f),
                    onValueChange = { vm.setCropRotation(it) },
                    valueRange = -45f..45f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPinkHi,
                        activeTrackColor = AccentPink,
                        inactiveTrackColor = BorderCol
                    ),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )

                if (state.cropSubTab == CropSubTab.ROTATE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SmallChipButton("Ruota -90°") { vm.rotate90Step(false) }
                        SmallChipButton("Ruota +90°") { vm.rotate90Step(true) }
                        SmallChipButton("Specchia H") { vm.reflectActiveLayer(true) }
                        SmallChipButton("Specchia V") { vm.reflectActiveLayer(false) }
                    }
                }
            }
        } else {
            Text(
                text = "Trascina i 4 angoli viola sul canvas per la Prospettiva 3D (Omografia)",
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // Aspect Ratio row
        if (state.cropSubTab == CropSubTab.CROP) {
            val ratios = listOf(
                "free" to "Libero",
                "square" to "1:1",
                "3:4" to "3:4",
                "4:5" to "4:5 Post",
                "9:16" to "9:16 Story",
                "4:3" to "4:3",
                "16:9" to "16:9 FB"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ratios.forEach { (key, label) ->
                    val active = state.cropRatio == key
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) ActivePurple else Color.White.copy(alpha = 0.05f))
                            .clickable { vm.setCropRatio(key) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp, 14.dp)
                                .border(1.5.dp, if (active) TextWhite else TextDim, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 10.sp, color = if (active) TextWhite else TextDim)
                    }
                }
            }
        }

        // Bottom Crop Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0D))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CropSubTab.entries.forEach { tab ->
                val label = when (tab) {
                    CropSubTab.CROP -> "RITAGLIA"
                    CropSubTab.ROTATE -> "RUOTA"
                    CropSubTab.PERSPECTIVE -> "PROSPETTIVA"
                }
                val active = state.cropSubTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) BorderCol else Color.White.copy(alpha = 0.03f))
                        .clickable { vm.setCropSubTab(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) TextWhite else TextMute
                    )
                }
            }
        }
    }
}

// ============ 2. FREE CROP / SHAPE CROP / DISPERSION / CLONE PANELS ============
@Composable
fun CutoutToolPanel(state: EditorUiState, vm: EditorViewModel, isShapeCropOnly: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        if (!isShapeCropOnly) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CutoutMode.entries.forEach { m ->
                    val active = state.cutoutMode == m
                    SmallChipButton(
                        text = m.label,
                        active = active,
                        modifier = Modifier.weight(1f)
                    ) { vm.setCutoutMode(m) }
                }
            }
        }

        if (isShapeCropOnly || state.cutoutMode == CutoutMode.SHAPE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShapeKind.entries.forEach { kind ->
                    val active = state.cutoutShapeKind == kind
                    SmallChipButton(
                        text = kind.label,
                        active = active
                    ) { vm.setCutoutShapeKind(kind) }
                }
            }
        } else {
            CustomSliderRow(
                label = "Dimensione",
                value = state.brushSize,
                valueRange = 5f..120f,
                onValueChange = { vm.updateBrushSettings(size = it) },
                suffix = "px"
            )
            Text(
                text = "Traccia sul canvas l'area da ritagliare e premi la spunta in alto a destra",
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun DispersionToolPanel(state: EditorUiState, vm: EditorViewModel) {
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        Text(
            text = "Dipingi sull'area da frammentare e regola i parametri:",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )
        CustomSliderRow("Espansione", state.dispersionStretch, 10f..180f, {
            vm.updateDispersionParams(it, state.dispersionSize, state.dispersionDirection, state.dispersionFade)
        })
        CustomSliderRow("Frammenti", state.dispersionSize, 4f..40f, {
            vm.updateDispersionParams(state.dispersionStretch, it, state.dispersionDirection, state.dispersionFade)
        }, "px")
        CustomSliderRow("Direzione", state.dispersionDirection, 0f..360f, {
            vm.updateDispersionParams(state.dispersionStretch, state.dispersionSize, it, state.dispersionFade)
        }, "°")
        CustomSliderRow("Sfuma", state.dispersionFade, 0f..100f, {
            vm.updateDispersionParams(state.dispersionStretch, state.dispersionSize, state.dispersionDirection, it)
        }, "%")
    }
}

@Composable
fun CloneToolPanel(state: EditorUiState, vm: EditorViewModel) {
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.cloneSourcePoint == null)
                    "1° Tocco sul canvas = Fissa Sorgente"
                else
                    "Sorgente fissata (${state.cloneSourcePoint.x.toInt()}, ${state.cloneSourcePoint.y.toInt()}) — Trascina per clonare",
                color = TextWhite,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            SmallChipButton("Cambia Sorgente") { vm.resetCloneSource() }
        }
        CustomSliderRow("Raggio", state.brushSize, 10f..140f, { vm.updateBrushSettings(size = it) }, "px")
        CustomSliderRow("Opacità", state.brushOpacity, 10f..100f, { vm.updateBrushSettings(opacity = it) }, "%")
    }
}

// ============ 3. WARP / FLUIDIFICA PANEL ============
@Composable
fun WarpToolPanel(state: EditorUiState, vm: EditorViewModel) {
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WarpMode.entries.forEach { mode ->
                SmallChipButton(
                    text = mode.label,
                    active = state.warpMode == mode
                ) {
                    vm.updateWarpParams(mode, state.warpRadius, state.warpStrength)
                }
            }
        }
        CustomSliderRow("Dimensione", state.warpRadius, 25f..220f, {
            vm.updateWarpParams(state.warpMode, it, state.warpStrength)
        }, "px")
        CustomSliderRow("Potenza", state.warpStrength, 10f..100f, {
            vm.updateWarpParams(state.warpMode, state.warpRadius, it)
        }, "%")
    }
}

// ============ 4. ADJUST (REGOLA) PANEL ============
@Composable
fun AdjustToolPanel(state: EditorUiState, vm: EditorViewModel) {
    val adj = state.activeLayer?.adjust ?: AdjustParams()
    var subParam by remember { mutableStateOf("Luminosità") }
    val params = listOf(
        "Luminosità", "Contrasto", "Saturazione", "Tonalità",
        "Temperatura", "Luci", "Ombre", "Chiarezza", "Esposizione"
    )

    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        when (subParam) {
            "Luminosità" -> CustomSliderRow("Luminosità", adj.brightness, -100f..100f, { v -> vm.updateActiveLayerAdjust { it.copy(brightness = v) } })
            "Contrasto" -> CustomSliderRow("Contrasto", adj.contrast, -100f..100f, { v -> vm.updateActiveLayerAdjust { it.copy(contrast = v) } })
            "Saturazione" -> CustomSliderRow("Saturazione", adj.saturation, -100f..100f, { v -> vm.updateActiveLayerAdjust { it.copy(saturation = v) } })
            "Tonalità" -> CustomSliderRow("Tonalità", adj.hue, 0f..360f, { v -> vm.updateActiveLayerAdjust { it.copy(hue = v) } }, "°")
            "Temperatura" -> CustomSliderRow("Temperatura", adj.temperature, -100f..100f, { v -> vm.updateActiveLayerAdjust { it.copy(temperature = v) } })
            "Luci" -> CustomSliderRow("Luci", adj.highlights, -100f..100f, { v -> vm.updateActiveLayerAdjust { it.copy(highlights = v) } })
            "Ombre" -> CustomSliderRow("Ombre", adj.shadows, -100f..100f, { v -> vm.updateActiveLayerAdjust { it.copy(shadows = v) } })
            "Chiarezza" -> CustomSliderRow("Chiarezza", adj.clarity, -100f..100f, { v -> vm.updateActiveLayerAdjust { it.copy(clarity = v) } })
            "Esposizione" -> CustomSliderRow("Esposizione", adj.exposure, -100f..100f, { v -> vm.updateActiveLayerAdjust { it.copy(exposure = v) } })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            params.forEach { name ->
                SmallChipButton(
                    text = name,
                    active = subParam == name
                ) { subParam = name }
            }
            SmallChipButton("Reset Tutto") {
                vm.updateActiveLayerAdjust { AdjustParams() }
            }
        }
    }
}

// ============ 5. RGB CURVES PANEL ============
@Composable
fun CurvesToolPanel(state: EditorUiState, vm: EditorViewModel) {
    val curves = state.activeLayer?.curves ?: CurvesData()
    val ch = state.activeCurveChannel
    val pts = when (ch) {
        "R" -> curves.r
        "G" -> curves.g
        "B" -> curves.b
        else -> curves.rgb
    }
    val strokeColor = when (ch) {
        "R" -> Color(0xFFFF4D6D)
        "G" -> Color(0xFF2EC4B6)
        "B" -> Color(0xFF3A86FF)
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgBlack)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("RGB", "R", "G", "B").forEach { cName ->
                    SmallChipButton(
                        text = cName,
                        active = ch == cName
                    ) { vm.setCurveChannel(cName) }
                }
            }
            SmallChipButton("Reset Curva") { vm.resetActiveCurveChannel() }
        }

        Spacer(Modifier.height(8.dp))

        // Interactive 2D Cartesian Curve Graph
        Box(
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Panel2)
                .border(1.dp, BorderCol, RoundedCornerShape(8.dp))
                .pointerInput(ch, pts) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val nx = (offset.x / size.width).coerceIn(0f, 1f)
                            val ny = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            // Remove nearest interior point
                            if (pts.size > 2) {
                                val idx = pts.indices.minByOrNull { i ->
                                    hypot(pts[i].x - nx, pts[i].y - ny)
                                } ?: -1
                                if (idx > 0 && idx < pts.lastIndex) {
                                    val next = pts.toMutableList().apply { removeAt(idx) }
                                    vm.updateCurvePoints(ch, next)
                                }
                            }
                        },
                        onTap = { offset ->
                            val nx = (offset.x / size.width).coerceIn(0.01f, 0.99f)
                            val ny = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            val next = (pts + CurvePoint(nx, ny)).sortedBy { it.x }
                            vm.updateCurvePoints(ch, next)
                        }
                    )
                }
                .pointerInput(ch, pts) {
                    var dragIdx = -1
                    detectDragGestures(
                        onDragStart = { offset ->
                            val nx = (offset.x / size.width).coerceIn(0f, 1f)
                            val ny = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            dragIdx = pts.indices.minByOrNull { i ->
                                hypot(pts[i].x - nx, pts[i].y - ny)
                            } ?: -1
                        },
                        onDrag = { change, _ ->
                            if (dragIdx in pts.indices) {
                                val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                val ny = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                val next = pts.toMutableList()
                                val fixedX = when (dragIdx) {
                                    0 -> 0f
                                    pts.lastIndex -> 1f
                                    else -> nx
                                }
                                next[dragIdx] = CurvePoint(fixedX, ny)
                                vm.updateCurvePoints(ch, next)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Grid lines
                for (i in 1..3) {
                    val p = i / 4f
                    drawLine(BorderCol, Offset(w * p, 0f), Offset(w * p, h), 1f)
                    drawLine(BorderCol, Offset(0f, h * p), Offset(w, h * p), 1f)
                }
                // Diagonal reference
                drawLine(TextMute, Offset(0f, h), Offset(w, 0f), 1f)

                // Draw LUT curve
                val lut = PixelEngine.buildCurveLut(pts)
                val path = Path()
                for (i in 0..255) {
                    val px = (i / 255f) * w
                    val py = h - (lut[i] / 255f) * h
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, strokeColor, style = Stroke(width = 3f))

                // Control points
                pts.forEach { pt ->
                    val cx = pt.x * w
                    val cy = (1f - pt.y) * h
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(AccentPink, radius = 4.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }
        Text(
            text = "Tocca per aggiungere un punto • Trascina per curvare • Doppio tocco per rimuovere",
            color = TextMute,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ============ 7. EFFECTS (FX) PANEL ============
@Composable
fun EffectsToolPanel(state: EditorUiState, vm: EditorViewModel) {
    val fxList = listOf(
        "blur" to "Sfocatura",
        "bw" to "B&N",
        "sepia" to "Seppia",
        "invert" to "Inverti",
        "glitch" to "Glitch RGB",
        "neon" to "Neon Glow",
        "vignette" to "Vignetta",
        "grain" to "Grana Film"
    )
    val active = state.activeLayer
    val key = state.selectedFxKey
    val cfg = active?.effects?.get(key) ?: EffectConfig()

    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        // Horizontal FX tiles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fxList.forEach { (fxKey, label) ->
                val isSelected = key == fxKey
                val isEnabled = active?.effects?.get(fxKey)?.enabled == true
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Panel3 else Panel2)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AccentPink else if (isEnabled) ActivePurple else BorderCol,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { vm.selectFxKey(fxKey) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (fxKey) {
                                    "blur" -> Color(0xFF3A3A45)
                                    "bw" -> Color(0xFF888888)
                                    "sepia" -> Color(0xFFA88A5F)
                                    "invert" -> Color(0xFF4361EE)
                                    "glitch" -> Color(0xFFFF2E93)
                                    "neon" -> Color(0xFF00F0FF)
                                    "vignette" -> Color(0xFF2B2D42)
                                    else -> Color(0xFF6C757D)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isEnabled) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = 9.sp, color = TextWhite, maxLines = 1)
                }
            }
        }

        // Active FX Parameter Sliders + Toggle + Selective Localization (§23) + Remove (X)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = cfg.enabled,
                onCheckedChange = { vm.updateLayerEffect(key, cfg.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = AccentPinkHi, checkedTrackColor = AccentPink)
            )
            SmallChipButton(
                text = if (cfg.localMask != null) "Localizza FX (Attivo)" else "Localizza FX",
                active = cfg.localMask != null
            ) {
                vm.requestLocalizeFx(key)
            }
            if (cfg.localMask != null) {
                SmallChipButton("Applica ovunque") {
                    vm.removeEffectLocalization(key)
                }
            }
            SmallChipButton("Rimuovi FX (X)") { vm.removeLayerEffect(key) }
        }

        CustomSliderRow("Intensità", cfg.intensity, 0f..100f, {
            vm.updateLayerEffect(key, cfg.copy(enabled = true, intensity = it))
        }, "%")
        CustomSliderRow("Raggio / Scala", cfg.param1, 1f..100f, {
            vm.updateLayerEffect(key, cfg.copy(enabled = true, param1 = it))
        })
        CustomSliderRow("Diffusione", cfg.param2, 0f..100f, {
            vm.updateLayerEffect(key, cfg.copy(enabled = true, param2 = it))
        })
    }
}

// ============ 8 & 9. SHAPES & TEXT PANELS ============
@Composable
fun ShapesToolPanel(state: EditorUiState, vm: EditorViewModel) {
    val active = state.activeLayer ?: return
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack)) {
        when (state.shapeSubTab) {
            "Forme" -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallChipButton("+ Nuova Forma") {
                        vm.addShapeLayer(ShapeKind.CIRCLE)
                    }
                    ShapeKind.entries.forEach { kind ->
                        val isSel = active.shapeKind == kind
                        SmallChipButton(
                            text = kind.label,
                            active = isSel
                        ) {
                            vm.updateActiveLayerDirect { it.copy(shapeKind = kind, name = "Forma ${kind.label}") }
                        }
                    }
                }
                if (active.shapeKind == ShapeKind.RECT) {
                    CustomSliderRow("Raggio Angoli", active.cornerRadius, 0f..200f, { r ->
                        vm.updateActiveLayerDirect { it.copy(cornerRadius = r) }
                    }, "px")
                } else if (active.shapeKind == ShapeKind.POLYGON) {
                    CustomSliderRow("Lati Poligono", active.polygonSides.toFloat(), 3f..12f, { s ->
                        vm.updateActiveLayerDirect { it.copy(polygonSides = s.roundToInt()) }
                    })
                } else if (active.shapeKind == ShapeKind.STAR) {
                    CustomSliderRow("Punte Stella", active.starPoints.toFloat(), 3f..24f, { p ->
                        vm.updateActiveLayerDirect { it.copy(starPoints = p.roundToInt()) }
                    })
                    CustomSliderRow("Raggio Interno", active.starInnerRatio * 100f, 10f..90f, { r ->
                        vm.updateActiveLayerDirect { it.copy(starInnerRatio = r / 100f) }
                    }, "%")
                }
            }
            "Colore" -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallChipButton("Colore Riempimento") { vm.openColorPicker("fill") }
                    SmallChipButton("Contagocce") { vm.toggleEyedropper() }
                    SmallChipButton(
                        text = if (active.useGradient) "Gradiente: ON" else "Gradiente: OFF",
                        active = active.useGradient
                    ) {
                        vm.updateActiveLayerDirect { it.copy(useGradient = !it.useGradient) }
                    }
                    if (active.useGradient) {
                        SmallChipButton("2° Colore") { vm.openColorPicker("grad2") }
                    }
                }
                if (active.useGradient) {
                    CustomSliderRow("Angolo Grad.", active.gradAngle, 0f..360f, { a ->
                        vm.updateActiveLayerDirect { it.copy(gradAngle = a) }
                    }, "°")
                }
            }
            "Bordo" -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallChipButton("Colore Tratto") { vm.openColorPicker("stroke") }
                }
                CustomSliderRow("Spessore", active.strokeWidth, 0f..40f, { sw ->
                    vm.updateActiveLayerDirect { it.copy(strokeWidth = sw) }
                }, "px")
            }
            "Ombra" -> {
                CustomSliderRow("Sfocatura", active.shadowBlur, 0f..50f, { sb ->
                    vm.updateActiveLayerDirect { it.copy(shadowBlur = sb) }
                }, "px")
                CustomSliderRow("Offset X", active.shadowOffsetX, -50f..50f, { ox ->
                    vm.updateActiveLayerDirect { it.copy(shadowOffsetX = ox) }
                }, "px")
                CustomSliderRow("Offset Y", active.shadowOffsetY, -50f..50f, { oy ->
                    vm.updateActiveLayerDirect { it.copy(shadowOffsetY = oy) }
                }, "px")
            }
            "Opacità" -> {
                CustomSliderRow("Opacità", active.opacity * 100f, 0f..100f, { op ->
                    vm.updateActiveLayerOpacity(op / 100f)
                }, "%")
                BlendModeStrip(active.blend) { vm.updateActiveLayerBlend(it) }
            }
        }

        // Submenu strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Forme", "Colore", "Bordo", "Ombra", "Opacità").forEach { tab ->
                SmallChipButton(
                    text = tab,
                    active = state.shapeSubTab == tab
                ) { vm.setShapeSubTab(tab) }
            }
        }
    }
}

@Composable
fun TextToolPanel(state: EditorUiState, vm: EditorViewModel, onPickFontFile: () -> Unit = {}) {
    val active = state.activeLayer ?: return
    val baseFonts = listOf("Inter", "Arial", "Georgia", "Times", "Verdana", "Courier", "Impact")
    val fonts = state.customFonts + baseFonts.filterNot { it in state.customFonts }

    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(top = 8.dp)) {
        when (state.textSubTab) {
            "Font" -> {
                OutlinedTextField(
                    value = active.text,
                    onValueChange = { txt -> vm.updateActiveLayerDirect { it.copy(text = txt) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .heightIn(min = 56.dp, max = 90.dp)
                        .testTag("text_layer_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentPink,
                        unfocusedBorderColor = BorderCol,
                        focusedContainerColor = Panel2,
                        unfocusedContainerColor = Panel2
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallChipButton("+ Carica Font (.ttf/.otf)", active = true) {
                        onPickFontFile()
                    }
                    SmallChipButton("+ Nuovo Testo") {
                        vm.addTextLayer()
                    }
                    fonts.forEach { f ->
                        val isSel = active.fontFamily == f
                        SmallChipButton(
                            text = f,
                            active = isSel
                        ) { vm.updateActiveLayerDirect { it.copy(fontFamily = f) } }
                    }
                }
                CustomSliderRow("Dimensione", active.fontSize, 8f..200f, { fs ->
                    vm.updateActiveLayerDirect { it.copy(fontSize = fs) }
                }, "px")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallChipButton("Sinistra", active = active.textAlign == 0) {
                        vm.updateActiveLayerDirect { it.copy(textAlign = 0) }
                    }
                    SmallChipButton("Centro", active = active.textAlign == 1) {
                        vm.updateActiveLayerDirect { it.copy(textAlign = 1) }
                    }
                    SmallChipButton("Destra", active = active.textAlign == 2) {
                        vm.updateActiveLayerDirect { it.copy(textAlign = 2) }
                    }
                }
            }
            "Colore" -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallChipButton("Colore Testo") { vm.openColorPicker("fill") }
                    SmallChipButton("Contagocce") { vm.toggleEyedropper() }
                    SmallChipButton(
                        text = if (active.useGradient) "Gradiente ON" else "Gradiente OFF",
                        active = active.useGradient
                    ) { vm.updateActiveLayerDirect { it.copy(useGradient = !it.useGradient) } }
                    if (active.useGradient) {
                        SmallChipButton("2° Colore") { vm.openColorPicker("grad2") }
                    }
                }
                if (active.useGradient) {
                    CustomSliderRow("Angolo Grad.", active.gradAngle, 0f..360f, { a ->
                        vm.updateActiveLayerDirect { it.copy(gradAngle = a) }
                    }, "°")
                }
            }
            "Tratto" -> {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    SmallChipButton("Colore Tratto") { vm.openColorPicker("stroke") }
                }
                CustomSliderRow("Spessore", active.strokeWidth, 0f..40f, { sw ->
                    vm.updateActiveLayerDirect { it.copy(strokeWidth = sw) }
                }, "px")
            }
            "Ombra" -> {
                CustomSliderRow("Sfocatura", active.shadowBlur, 0f..50f, { sb ->
                    vm.updateActiveLayerDirect { it.copy(shadowBlur = sb) }
                }, "px")
                CustomSliderRow("Offset X", active.shadowOffsetX, -40f..40f, { ox ->
                    vm.updateActiveLayerDirect { it.copy(shadowOffsetX = ox) }
                }, "px")
                CustomSliderRow("Offset Y", active.shadowOffsetY, -40f..40f, { oy ->
                    vm.updateActiveLayerDirect { it.copy(shadowOffsetY = oy) }
                }, "px")
            }
            "Piega" -> {
                CustomSliderRow("Arco (Bend)", active.textBend, -100f..100f, { b ->
                    vm.updateActiveLayerDirect { it.copy(textBend = b) }
                }, "%")
                val pathLayers = state.layers.filter { it.type == LayerType.PATH }
                if (pathLayers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmallChipButton(
                            text = "Testo su Tracciato",
                            active = active.textOnPathLayerId != null
                        ) {
                            val nextId = if (active.textOnPathLayerId == null) pathLayers.first().id else null
                            vm.updateActiveLayerDirect { it.copy(textOnPathLayerId = nextId) }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Font", "Colore", "Tratto", "Ombra", "Piega").forEach { tab ->
                SmallChipButton(
                    text = tab,
                    active = state.textSubTab == tab
                ) { vm.setTextSubTab(tab) }
            }
        }
    }
}

// ============ 10, 11, 12. VECTOR PEN, BOOLEAN & ALIGN PANELS ============
@Composable
fun PenAndBooleanPanel(state: EditorUiState, vm: EditorViewModel) {
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        Text(
            text = "Penna Vettoriale: Tocca il canvas per aggiungere vertici, trascina i punti per curvare",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallChipButton("Chiudi/Apri Tracciato") { vm.closeVectorPath() }
            SmallChipButton("Svuota Punti") { vm.clearVectorPathPoints() }
            SmallChipButton("Colore Riemp.") { vm.openColorPicker("fill") }
            SmallChipButton("Colore Tratto") { vm.openColorPicker("stroke") }
        }
        HorizontalDivider(color = BorderCol, modifier = Modifier.padding(vertical = 6.dp))
        Text(
            text = "Operazioni Booleane (su ultimi 2 livelli o selezionati):",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallChipButton("Unione") { vm.applyBooleanOperation(android.graphics.PorterDuff.Mode.SRC_OVER, "Unione") }
            SmallChipButton("Sottrazione") { vm.applyBooleanOperation(android.graphics.PorterDuff.Mode.DST_OUT, "Sottrazione") }
            SmallChipButton("Intersezione") { vm.applyBooleanOperation(android.graphics.PorterDuff.Mode.DST_IN, "Intersezione") }
            SmallChipButton("Esclusione XOR") { vm.applyBooleanOperation(android.graphics.PorterDuff.Mode.XOR, "XOR") }
        }
        HorizontalDivider(color = BorderCol, modifier = Modifier.padding(vertical = 6.dp))
        Text(
            text = "Allinea & Distribuisci:",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallChipButton("Sinistra") { vm.alignLayers("left") }
            SmallChipButton("Centro H") { vm.alignLayers("centerH") }
            SmallChipButton("Destra") { vm.alignLayers("right") }
            SmallChipButton("Alto") { vm.alignLayers("top") }
            SmallChipButton("Centro V") { vm.alignLayers("centerV") }
            SmallChipButton("Basso") { vm.alignLayers("bottom") }
            SmallChipButton("Distribuisci H") { vm.distributeLayers(true) }
            SmallChipButton("Distribuisci V") { vm.distributeLayers(false) }
        }
    }
}

// ============ 13 & 14. BRUSH & MASK PANELS ============
@Composable
fun BrushToolPanel(state: EditorUiState, vm: EditorViewModel, isMaskMode: Boolean = false) {
    val active = state.activeLayer
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        if (isMaskMode && active != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallChipButton(
                    text = "Mostra (Bianco)",
                    active = state.brushStyle != BrushStyle.ERASER
                ) { vm.updateBrushSettings(style = BrushStyle.STANDARD) }
                SmallChipButton(
                    text = "Nascondi (Nero)",
                    active = state.brushStyle == BrushStyle.ERASER
                ) { vm.updateBrushSettings(style = BrushStyle.ERASER) }
                SmallChipButton(
                    text = if (active.maskInverted) "Invertita: ON" else "Inverti Maschera",
                    active = active.maskInverted
                ) { vm.invertActiveLayerMask() }
                ShapeKind.entries.take(4).forEach { sk ->
                    SmallChipButton("Maschera ${sk.label}") { vm.applyShapeToActiveMask(sk) }
                }
            }
        } else {
            // Brush style tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrushStyle.entries.forEach { style ->
                    val isSel = state.brushStyle == style
                    SmallChipButton(
                        text = style.label,
                        active = isSel
                    ) { vm.updateBrushSettings(style = style) }
                }
            }
        }

        CustomSliderRow("Dimensione", state.brushSize, 1f..200f, {
            vm.updateBrushSettings(size = it)
        }, "px")
        CustomSliderRow("Opacità", state.brushOpacity, 5f..100f, {
            vm.updateBrushSettings(opacity = it)
        }, "%")

        if (!isMaskMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(state.brushColor))
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { vm.openColorPicker("brush") }
                )
                SmallChipButton("Scegli Colore HSV/RGB") { vm.openColorPicker("brush") }
                SmallChipButton("Contagocce") { vm.toggleEyedropper() }
            }
        }
    }
}

// ============ 17 & 21. FIT, BORDER, RESIZE & COLLAGE PANELS ============
@Composable
fun FitAndBorderPanel(state: EditorUiState, vm: EditorViewModel, isBorderTab: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        if (!isBorderTab) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("1:1", "4:5", "3:4", "16:9", "9:16").forEach { r ->
                    SmallChipButton(
                        text = "Ratio $r",
                        active = state.fitRatioLabel == r
                    ) { vm.applyCanvasFitRatio(r) }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallChipButton("Sfondo Solido", active = state.fitBgMode == 0) { vm.updateFitBgMode(0) }
                SmallChipButton("Pattern Scacchi", active = state.fitBgMode == 1) { vm.updateFitBgMode(1) }
                SmallChipButton("Sfondo Sfocato", active = state.fitBgMode == 2) { vm.updateFitBgMode(2) }
                SmallChipButton("Colore Sfondo") { vm.openColorPicker("bg") }
                SmallChipButton("Reset Zoom Vista") { vm.resetViewportZoom() }
            }
        }

        CustomSliderRow("Bordo Esterno", state.borderOuter, 0f..60f, {
            vm.updateBorderFrame(it, state.borderInner, state.borderRadius)
        }, "px")
        CustomSliderRow("Margine Interno", state.borderInner, 0f..40f, {
            vm.updateBorderFrame(state.borderOuter, it, state.borderRadius)
        }, "px")
        CustomSliderRow("Raggio Cornice", state.borderRadius, 0f..120f, {
            vm.updateBorderFrame(state.borderOuter, state.borderInner, it)
        }, "px")
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            SmallChipButton("Colore Cornice") { vm.openColorPicker("border") }
        }
    }
}

@Composable
fun ResizeCanvasPanel(state: EditorUiState, vm: EditorViewModel) {
    val presets = listOf(
        "800×800 (Quadrato)" to (800 to 800),
        "1080×1080 (IG Post)" to (1080 to 1080),
        "1080×1350 (Portrait 4:5)" to (1080 to 1350),
        "1080×1920 (Story 9:16)" to (1080 to 1920),
        "1920×1080 (Full HD 16:9)" to (1920 to 1080)
    )
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(12.dp)) {
        Text(
            text = "Dimensioni attuali: ${state.canvasW} × ${state.canvasH} px",
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (label, dim) ->
                SmallChipButton(
                    text = label,
                    active = state.canvasW == dim.first && state.canvasH == dim.second
                ) { vm.resizeCanvasDimensions(dim.first, dim.second) }
            }
        }
    }
}

@Composable
fun CollageToolPanel(
    state: EditorUiState,
    vm: EditorViewModel,
    onPickPhotoForSlot: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(2, 3, 4, 6).forEach { count ->
                SmallChipButton(
                    text = "Griglia $count Slot",
                    active = state.collagePresetCount == count
                ) { vm.updateCollageLayout(presetCount = count) }
            }
            SmallChipButton("Carica Foto nello Slot #${state.activeCollageSlotId + 1}") {
                onPickPhotoForSlot()
            }
        }
        CustomSliderRow("Separatore", state.collageSplitRatio * 100f, 20f..80f, {
            vm.updateCollageLayout(splitRatio = it / 100f)
        }, "%")
        CustomSliderRow("Spaziatura", state.collageSpacing, 0f..36f, {
            vm.updateCollageLayout(spacing = it)
        }, "px")
        CustomSliderRow("Arrotondamento", state.collageCornerRadius, 0f..60f, {
            vm.updateCollageLayout(radius = it)
        }, "px")
    }
}

@Composable
fun BlendModeStrip(selected: BlendOption, onSelect: (BlendOption) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BlendOption.entries.forEach { bm ->
            SmallChipButton(
                text = bm.label,
                active = bm == selected
            ) { onSelect(bm) }
        }
    }
}

@Composable
fun SelectivePaintToolPanel(state: EditorUiState, vm: EditorViewModel) {
    val fxKey = state.selectiveTargetFxKey
    val active = state.activeLayer
    val isInverted = if (fxKey != null) {
        active?.effects?.get(fxKey)?.localInverted == true
    } else {
        active?.maskInverted == true
    }

    Column(modifier = Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallChipButton(
                text = "Pennello (+)",
                active = state.brushStyle != BrushStyle.ERASER
            ) { vm.updateBrushSettings(style = BrushStyle.STANDARD) }
            SmallChipButton(
                text = "Gomma (-)",
                active = state.brushStyle == BrushStyle.ERASER
            ) { vm.updateBrushSettings(style = BrushStyle.ERASER) }
            SmallChipButton(
                text = if (state.showMaskHeatmap) "Overlay Rosso: ON" else "Overlay Rosso: OFF",
                active = state.showMaskHeatmap
            ) { vm.toggleSelectiveHeatmap() }
            SmallChipButton(
                text = if (isInverted) "Inverti: ON" else "Inverti",
                active = isInverted
            ) { vm.invertSelectiveMask() }
            SmallChipButton("Applica a Tutto") { vm.fillSelectiveMask(true) }
            SmallChipButton("Cancella Tutto") { vm.fillSelectiveMask(false) }
            SmallChipButton("Esci (X)") { vm.exitSelectivePaintMode() }
        }

        CustomSliderRow("Dimensione", state.brushSize, 1f..200f, {
            vm.updateBrushSettings(size = it)
        }, "px")
        CustomSliderRow("Opacità", state.brushOpacity, 5f..100f, {
            vm.updateBrushSettings(opacity = it)
        }, "%")
        CustomSliderRow("Durezza Bordo", state.brushHardness, 5f..100f, {
            vm.updateBrushSettings(hardness = it)
        }, "%")
    }
}

@Composable
fun SmallChipButton(
    text: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) ActivePurple else Panel2)
            .border(1.dp, if (active) ActivePurple else BorderCol, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) TextWhite else TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
