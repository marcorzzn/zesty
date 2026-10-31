package com.example.ui

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.ExportFormat
import com.example.engine.PixelEngine
import com.example.model.BlendOption
import com.example.model.EditorLayer
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import kotlin.math.*

// ============ 20. LAYERS PANEL (DRAWER) ============
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayersSidePanel(state: EditorUiState, vm: EditorViewModel) {
    AnimatedVisibility(
        visible = state.layersPanelOpen,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = Modifier.fillMaxHeight()
    ) {
        val active = state.activeLayer
        Column(
            modifier = Modifier
                .width(265.dp)
                .fillMaxHeight()
                .background(PanelDark.copy(alpha = 0.98f))
                .border(1.dp, BorderCol)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("layers_panel")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Livelli (${state.layers.size})", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { vm.addEmptyRasterLayer() },
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .testTag("add_layer_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi livello", tint = TextWhite, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { vm.toggleLayersPanel() },
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi livelli", tint = TextWhite, modifier = Modifier.size(16.dp))
                    }
                }
            }

            HorizontalDivider(color = BorderCol)

            // Active Layer Opacity + 16 Blend Modes + Mask Target toggle
            if (active != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Opacità: ${(active.opacity * 100).roundToInt()}%", color = TextDim, fontSize = 11.sp)
                        Text("Fusione: ${active.blend.label}", color = AccentPinkHi, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = active.opacity,
                        onValueChange = { vm.updateActiveLayerOpacity(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = AccentPinkHi, activeTrackColor = AccentPink),
                        modifier = Modifier.height(26.dp)
                    )

                    // 16 Native Blend Modes horizontal selector
                    BlendModeStrip(active.blend) { vm.updateActiveLayerBlend(it) }

                    // Edit target: Layer vs Mask + Quick actions
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SmallChipButton(
                            text = if (active.editingMask) "Target: Maschera" else "Target: Livello",
                            active = active.editingMask,
                            modifier = Modifier.weight(1f)
                        ) { vm.toggleEditingMaskTarget(!active.editingMask) }

                        IconButton(
                            onClick = { vm.duplicateLayer(active.id) },
                            modifier = Modifier.size(30.dp).background(Panel2, RoundedCornerShape(6.dp))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplica", tint = TextWhite, modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = { vm.deleteLayer(active.id) },
                            modifier = Modifier.size(30.dp).background(Panel2, RoundedCornerShape(6.dp))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = Color(0xFFFF5C8A), modifier = Modifier.size(15.dp))
                        }
                    }

                    // §23 Universal Layer Localization quick button
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmallChipButton(
                            text = if (active.maskBitmap != null) "Modifica Maschera Locale" else "Localizza Visibilità",
                            active = active.maskBitmap != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            vm.toggleLayersPanel()
                            vm.openSelectivePaintMode(fxKey = null, initBlackMask = false)
                        }
                    }
                }
                HorizontalDivider(color = BorderCol)
            }

            // Layers list (top-most first visually)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.layers.reversed(), key = { it.id }) { layer ->
                    LayerRowItem(
                        layer = layer,
                        isActive = layer.id == state.activeLayerId,
                        onSelect = { vm.selectLayer(layer.id) },
                        onLongPressRename = { vm.openRenameLayerModal(layer.id) },
                        onToggleVis = { vm.toggleLayerVisibility(layer.id) },
                        onToggleLock = { vm.toggleLayerLock(layer.id) },
                        onMoveUp = { vm.moveLayerOrder(layer.id, true) },
                        onMoveDown = { vm.moveLayerOrder(layer.id, false) },
                        onToggleMulti = { vm.toggleLayerMultiSelect(layer.id) }
                    )
                }
            }

            Text(
                text = "Tocco prolungato sul nome per rinominare • Spunta per selez. multipla",
                color = TextMute,
                fontSize = 9.sp,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayerRowItem(
    layer: EditorLayer,
    isActive: Boolean,
    onSelect: () -> Unit,
    onLongPressRename: () -> Unit,
    onToggleVis: () -> Unit,
    onToggleLock: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleMulti: () -> Unit
) {
    val thumbBmp = remember(layer) {
        PixelEngine.renderSingleLayerBitmap(layer)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) ActivePurple.copy(alpha = 0.2f) else Panel2)
            .border(
                width = 1.dp,
                color = if (isActive) ActivePurple else BorderCol,
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongPressRename
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Checkbox(
            checked = layer.selectedForMulti,
            onCheckedChange = { onToggleMulti() },
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(checkedColor = AccentPink)
        )

        // Real-time 34x34 thumbnail
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BgBlack)
                .border(1.dp, BorderCol, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = thumbBmp.asImageBitmap(),
                contentDescription = layer.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = layer.name,
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = "${layer.blend.label} • ${(layer.opacity * 100).roundToInt()}%",
                color = TextDim,
                fontSize = 9.sp,
                maxLines = 1
            )
        }

        // Up/Down reorder
        Column {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Sposta su",
                tint = TextDim,
                modifier = Modifier.size(15.dp).clickable { onMoveUp() }
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Sposta giù",
                tint = TextDim,
                modifier = Modifier.size(15.dp).clickable { onMoveDown() }
            )
        }

        // Lock button
        IconButton(onClick = onToggleLock, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Blocca livello",
                tint = if (layer.locked) AccentPinkHi else TextDim,
                modifier = Modifier.size(14.dp)
            )
        }

        // Eye visibility button
        IconButton(onClick = onToggleVis, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Visibilità livello",
                tint = if (layer.visible) TextWhite else TextMute,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ============ 15. HSV / RGB / HEX COLOR PICKER MODAL ============
@Composable
fun ColorPickerModal(state: EditorUiState, vm: EditorViewModel) {
    if (!state.colorPickerOpen) return

    val initialColor = when (state.colorPickerTarget) {
        "fill" -> state.activeLayer?.fillColor ?: AndroidColor.parseColor("#8B5CF6")
        "grad2" -> state.activeLayer?.gradColor2 ?: AndroidColor.parseColor("#E0289A")
        "stroke" -> state.activeLayer?.strokeColor ?: AndroidColor.WHITE
        "brush" -> state.brushColor
        "bg" -> state.bgColor
        else -> state.borderColor
    }

    var r by remember(initialColor) { mutableIntStateOf(AndroidColor.red(initialColor)) }
    var g by remember(initialColor) { mutableIntStateOf(AndroidColor.green(initialColor)) }
    var b by remember(initialColor) { mutableIntStateOf(AndroidColor.blue(initialColor)) }
    var hexText by remember(r, g, b) {
        mutableStateOf(String.format("#%02X%02X%02X", r, g, b))
    }

    Dialog(onDismissRequest = { vm.closeColorPicker() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PanelDark)
                .border(1.dp, BorderCol, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Selettore Colore (HSV / RGB / HEX)", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // 200x200 HSV Color Wheel
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val dist = (hypot(dx, dy) / cx).coerceIn(0f, 1f)
                            val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
                            val cInt = AndroidColor.HSVToColor(floatArrayOf(angle, dist, 1f))
                            r = AndroidColor.red(cInt)
                            g = AndroidColor.green(cInt)
                            b = AndroidColor.blue(cInt)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sweep = Brush.sweepGradient(
                        listOf(
                            Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                    )
                    drawCircle(sweep)
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color.Transparent)
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // RGB Sliders (0..255)
            CustomSliderRow("Rosso (R)", r.toFloat(), 0f..255f, { r = it.roundToInt() })
            CustomSliderRow("Verde (G)", g.toFloat(), 0f..255f, { g = it.roundToInt() })
            CustomSliderRow("Blu (B)", b.toFloat(), 0f..255f, { b = it.roundToInt() })

            // Preview + HEX input + Eyedropper
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(r, g, b))
                        .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { txt ->
                        hexText = txt
                        runCatching {
                            val parsed = AndroidColor.parseColor(if (txt.startsWith("#")) txt else "#$txt")
                            r = AndroidColor.red(parsed)
                            g = AndroidColor.green(parsed)
                            b = AndroidColor.blue(parsed)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentPink,
                        unfocusedBorderColor = BorderCol
                    )
                )
                SmallChipButton("Contagocce") { vm.toggleEyedropper() }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { vm.closeColorPicker() },
                    colors = ButtonDefaults.buttonColors(containerColor = Panel2),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annulla", color = TextWhite)
                }
                Button(
                    onClick = {
                        vm.applyPickedColor(AndroidColor.rgb(r, g, b))
                        vm.closeColorPicker()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Applica", color = TextWhite)
                }
            }
        }
    }
}

// ============ 18. EXPORT MODAL (PNG, JPG, SVG, PDF + SCALE 0.25x..4x) ============
@Composable
fun ExportDialogModal(state: EditorUiState, vm: EditorViewModel) {
    if (!state.exportModalOpen) return
    val context = LocalContext.current
    var format by remember { mutableStateOf(ExportFormat.PNG) }
    var jpgQuality by remember { mutableFloatStateOf(90f) }
    var scale by remember { mutableFloatStateOf(1f) }

    Dialog(onDismissRequest = { vm.openExportModal(false) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PanelDark)
                .border(1.dp, BorderCol, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text("Esporta Immagine", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            ExportFormat.entries.forEach { fmt ->
                val active = format == fmt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) ActivePurple.copy(alpha = 0.25f) else Panel2)
                        .border(1.dp, if (active) ActivePurple else BorderCol, RoundedCornerShape(8.dp))
                        .clickable { format = fmt }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fmt.label, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (active) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentPinkHi, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (format == ExportFormat.JPG || format == ExportFormat.PDF) {
                Spacer(Modifier.height(6.dp))
                CustomSliderRow("Qualità JPG", jpgQuality, 10f..100f, { jpgQuality = it }, "%")
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Scala Esportazione: ${String.format(java.util.Locale.US, "%.2fx", scale)} (${(state.canvasW * scale).roundToInt()}×${(state.canvasH * scale).roundToInt()} px)",
                color = TextDim,
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.25f, 0.5f, 1f, 2f, 4f).forEach { sVal ->
                    SmallChipButton(
                        text = "${sVal}x",
                        active = abs(scale - sVal) < 0.01f
                    ) { scale = sVal }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { vm.openExportModal(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = Panel2),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annulla", color = TextWhite)
                }
                Button(
                    onClick = {
                        vm.performExport(context, format, jpgQuality.roundToInt(), scale)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    modifier = Modifier.weight(1f).testTag("confirm_export_button")
                ) {
                    Text("Scarica ${format.ext.uppercase()}", color = TextWhite)
                }
            }
        }
    }
}

// ============ RENAME LAYER MODAL ============
@Composable
fun RenameLayerModal(state: EditorUiState, vm: EditorViewModel) {
    val targetId = state.renameLayerModalId ?: return
    val layer = state.layers.find { it.id == targetId } ?: return
    var name by remember(layer) { mutableStateOf(layer.name) }

    Dialog(onDismissRequest = { vm.openRenameLayerModal(null) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PanelDark)
                .border(1.dp, BorderCol, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Text("Rinomina Livello", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = BorderCol
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.openRenameLayerModal(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = Panel2),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annulla", color = TextWhite)
                }
                Button(
                    onClick = { vm.renameLayer(targetId, name) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Salva", color = TextWhite)
                }
            }
        }
    }
}
