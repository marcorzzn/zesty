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
import androidx.compose.ui.text.style.TextOverflow
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
/**
 * Pannello dei livelli: isola a destra dentro l'area della tela (non copre le barre in alto e in basso),
 * con gli stessi componenti delle altre schermate. Si chiude con ✕, con Indietro o toccando la tela a vuoto.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayersSidePanel(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.layersPanelOpen,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier.fillMaxHeight()
    ) {
        val active = state.activeLayer
        Column(
            modifier = Modifier
                .width(264.dp)
                .fillMaxHeight()
                .padding(8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(BgBlack)
                .testTag("layers_panel")
        ) {
            PxSectionTitle("Livelli · ${state.layers.size}") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.testTag("add_layer_button")) {
                        PxIconAction(Icons.Default.Add, "Aggiungi livello") { vm.addEmptyRasterLayer() }
                    }
                    PxIconAction(Icons.Default.Close, "Chiudi livelli") { vm.toggleLayersPanel() }
                }
            }

            if (active != null) {
                PxSlider("Opacità", active.opacity * 100f, 0f..100f, { vm.updateActiveLayerOpacity(it / 100f) }, "%")
                BlendModeStrip(active.blend) { vm.updateActiveLayerBlend(it) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PxIconAction(
                        icon = Icons.Default.Masks,
                        description = if (active.editingMask) "Stai modificando la maschera (tocca per il livello)" else "Stai modificando il livello (tocca per la maschera)",
                        active = active.editingMask
                    ) { vm.toggleEditingMaskTarget(!active.editingMask) }
                    PxIconAction(
                        icon = Icons.Default.Gradient,
                        description = if (active.maskBitmap != null) "Modifica la visibilità del livello" else "Mostra il livello solo in una zona",
                        active = active.maskBitmap != null
                    ) {
                        vm.toggleLayersPanel()
                        vm.openSelectivePaintMode(fxKey = null, initBlackMask = false)
                    }
                    Spacer(Modifier.weight(1f))
                    PxIconAction(Icons.Default.ContentCopy, "Duplica livello") { vm.duplicateLayer(active.id) }
                    PxIconAction(Icons.Default.Delete, "Elimina livello") { vm.deleteLayer(active.id) }
                }
                HorizontalDivider(color = DividerCol, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }

            // elenco: il livello più in alto per primo
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
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
            PxHint("Tieni premuto un livello per rinominarlo")
            Spacer(Modifier.height(6.dp))
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
    // miniatura piccola, ricalcolata solo se cambia l'aspetto del livello (non spostandolo o ruotandolo)
    val look = layer.copy(x = 0f, y = 0f, rotation = 0f, selectedForMulti = false, locked = false, visible = true, name = "")
    val thumbBmp = remember(look) {
        PixelEngine.renderSingleLayerBitmap(layer, scale = 96f / max(layer.w, layer.h).coerceAtLeast(1f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Px.Accent.copy(alpha = 0.16f) else Px.Surface)
            .border(1.dp, if (isActive) Px.Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onSelect, onLongClick = onLongPressRename)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StageCheckA)
                .clickable { onToggleMulti() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = thumbBmp.asImageBitmap(),
                contentDescription = layer.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            // selezione multipla: tocco sulla miniatura
            if (layer.selectedForMulti) {
                Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(12.dp).clip(CircleShape).background(Px.Active))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(layer.name, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${layer.blend.label} · ${(layer.opacity * 100).roundToInt()}%",
                color = TextMute, fontSize = 10.sp, maxLines = 1
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Sposta su", tint = TextDim, modifier = Modifier.size(18.dp).clickable { onMoveUp() })
            Icon(Icons.Default.KeyboardArrowDown, "Sposta giù", tint = TextDim, modifier = Modifier.size(18.dp).clickable { onMoveDown() })
        }
        Icon(
            imageVector = if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (layer.locked) "Sblocca livello" else "Blocca livello",
            tint = if (layer.locked) Px.Active else TextDim,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onToggleLock() }.padding(5.dp)
        )
        Icon(
            imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            contentDescription = if (layer.visible) "Nascondi livello" else "Mostra livello",
            tint = if (layer.visible) TextWhite else TextMute,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onToggleVis() }.padding(5.dp)
        )
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
                    Text("Applica", color = OnAccent)
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
                        .background(if (active) AccentPink.copy(alpha = 0.18f) else Panel2)
                        .border(1.dp, if (active) AccentPink else BorderCol, RoundedCornerShape(8.dp))
                        .clickable { format = fmt }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fmt.label, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (active) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentPink, modifier = Modifier.size(16.dp))
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
                    Text("Scarica ${format.ext.uppercase()}", color = OnAccent)
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
                    Text("Salva", color = OnAccent)
                }
            }
        }
    }
}

// ============ NEW PROJECT MODAL (Any dimension, solid color or transparent background, or demo scene) ============
@Composable
fun NewProjectModal(state: EditorUiState, vm: EditorViewModel, onOpenPhoto: () -> Unit = {}) {
    if (!state.newProjectModalOpen) return
    var widthText by remember { mutableStateOf(state.canvasW.toString()) }
    var heightText by remember { mutableStateOf(state.canvasH.toString()) }
    var isTransparent by remember { mutableStateOf(AndroidColor.alpha(state.bgColor) == 0) }
    var customHex by remember {
        mutableStateOf(
            if (AndroidColor.alpha(state.bgColor) == 0) "#121217"
            else String.format("#%06X", 0xFFFFFF and state.bgColor)
        )
    }

    Dialog(onDismissRequest = { vm.openNewProjectModal(false) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PanelDark)
                .border(1.dp, BorderCol, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Nuovo progetto", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            // Strada principale: la foto diventa la tela, con le sue dimensioni
            Button(
                onClick = { vm.openNewProjectModal(false); onOpenPhoto() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = OnAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Apri una foto", color = OnAccent, fontWeight = FontWeight.Bold)
            }
            Text("La tela prende le dimensioni della foto.", color = TextMute, fontSize = 11.sp)
            Text("Oppure crea una tela vuota", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))

            // Quick size presets
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("800×800" to (800 to 800), "1080×1080" to (1080 to 1080), "1080×1350" to (1080 to 1350), "1920×1080" to (1920 to 1080)).forEach { (label, pair) ->
                    SmallChipButton(label) {
                        widthText = pair.first.toString()
                        heightText = pair.second.toString()
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Larghezza (px)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentPink,
                        unfocusedBorderColor = BorderCol
                    )
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Altezza (px)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentPink,
                        unfocusedBorderColor = BorderCol
                    )
                )
            }

            // Background mode: Transparent vs Solid Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallChipButton(
                    text = "Sfondo Trasparente",
                    active = isTransparent,
                    modifier = Modifier.weight(1f)
                ) { isTransparent = true }
                SmallChipButton(
                    text = "Colore Solido",
                    active = !isTransparent,
                    modifier = Modifier.weight(1f)
                ) { isTransparent = false }
            }

            if (!isTransparent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("#FFFFFF", "#000000", "#121217", "#E0289A", "#8B5CF6", "#00F0FF").forEach { swHex ->
                        val swCol = runCatching { AndroidColor.parseColor(swHex) }.getOrDefault(AndroidColor.BLACK)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(swCol))
                                .border(1.dp, Color.White, CircleShape)
                                .clickable { customHex = swHex }
                        )
                    }
                }
                OutlinedTextField(
                    value = customHex,
                    onValueChange = { customHex = it },
                    label = { Text("Colore HEX (#RRGGBB)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentPink,
                        unfocusedBorderColor = BorderCol
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallChipButton(
                    text = "Carica Scena Demo",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val w = widthText.toIntOrNull()?.coerceIn(64, 4096) ?: 800
                    val h = heightText.toIntOrNull()?.coerceIn(64, 4096) ?: 800
                    vm.createNewProject(w, h, AndroidColor.parseColor("#08080C"), loadDemo = true)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { vm.openNewProjectModal(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = Panel2),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annulla", color = TextWhite)
                }
                Button(
                    onClick = {
                        val w = widthText.toIntOrNull()?.coerceIn(64, 4096) ?: 800
                        val h = heightText.toIntOrNull()?.coerceIn(64, 4096) ?: 800
                        val bg = if (isTransparent) {
                            AndroidColor.TRANSPARENT
                        } else {
                            runCatching {
                                AndroidColor.parseColor(if (customHex.startsWith("#")) customHex else "#$customHex")
                            }.getOrDefault(AndroidColor.parseColor("#121217"))
                        }
                        vm.createNewProject(w, h, bg, loadDemo = false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    modifier = Modifier.weight(1f).testTag("create_new_project_button")
                ) {
                    Text("Crea Tela", color = OnAccent)
                }
            }
        }
    }
}

// ============ CONFIRM LOCALIZE FX MODAL (§23 UX confirmation) ============
@Composable
fun ConfirmLocalizeFxModal(state: EditorUiState, vm: EditorViewModel) {
    val fxKey = state.confirmLocalizeFxKey ?: return
    Dialog(onDismissRequest = { vm.dismissLocalizeFxModal() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PanelDark)
                .border(1.dp, BorderCol, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (fxKey == EditorViewModel.FILTER_FX_KEY) "Applica il filtro a una zona" else "Localizza Effetto ($fxKey)",
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "L'effetto resterà solo nella zona che definisci con pennello, forme, lazo o poligono; il resto torna all'originale. Potrai cancellare con la gomma o sottrarre forme. Continuare?",
                color = TextDim,
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { vm.dismissLocalizeFxModal() },
                    colors = ButtonDefaults.buttonColors(containerColor = Panel2),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annulla", color = TextWhite)
                }
                Button(
                    onClick = { vm.confirmLocalizeFx() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    modifier = Modifier.weight(1f).testTag("confirm_localize_fx_button")
                ) {
                    Text("Continua", color = OnAccent)
                }
            }
        }
    }
}

