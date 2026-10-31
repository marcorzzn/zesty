package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BrushStyle
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import kotlinx.coroutines.delay

data class ToolNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

private val BOTTOM_TOOLS = listOf(
    ToolNavItem("tools", "Strumenti", Icons.Default.Build),
    ToolNavItem("effects", "Effetti", Icons.Default.AutoAwesome),
    ToolNavItem("retouch", "Fluidifica", Icons.Default.WaterDrop),
    ToolNavItem("removebg", "Ritaglio Lib.", Icons.Default.ContentCut),
    ToolNavItem("sticker", "Adesivo", Icons.Default.Star),
    ToolNavItem("crop", "Ritaglia", Icons.Default.Crop),
    ToolNavItem("text", "Testo", Icons.Default.Title),
    ToolNavItem("addphoto", "Aggiungi Foto", Icons.Default.AddPhotoAlternate),
    ToolNavItem("remove", "Clona", Icons.Default.Healing),
    ToolNavItem("replay", "Replay", Icons.Default.Replay),
    ToolNavItem("fit", "Adatta", Icons.Default.FitScreen),
    ToolNavItem("brush", "Pennelli", Icons.Default.Brush),
    ToolNavItem("border", "Bordo", Icons.Default.FilterFrames),
    ToolNavItem("shapes", "Forme", Icons.Default.Category),
    ToolNavItem("mask", "Maschera", Icons.Default.Gradient),
    ToolNavItem("draw", "Disegna", Icons.Default.Edit),
    ToolNavItem("pen", "Penna & Bool", Icons.Default.Polyline),
    ToolNavItem("reflect", "Riflesso", Icons.Default.Flip),
    ToolNavItem("maskform", "Masch. Forma", Icons.Default.Interests),
    ToolNavItem("collage", "Collage", Icons.Default.GridView),
    ToolNavItem("callout", "Chiamata", Icons.Default.ChatBubbleOutline)
)

private val GRID_TOOLS = listOf(
    ToolNavItem("crop", "Ritaglia", Icons.Default.Crop),
    ToolNavItem("freecrop", "Libero", Icons.Default.ContentCut),
    ToolNavItem("shapecrop", "Ritaglia Forma", Icons.Default.Interests),
    ToolNavItem("dispersion", "Dispersione", Icons.Default.Grain),
    ToolNavItem("clone", "Clona", Icons.Default.Healing),
    ToolNavItem("warp", "Fluidifica", Icons.Default.WaterDrop),
    ToolNavItem("fit", "Estendi / Fit", Icons.Default.FitScreen),
    ToolNavItem("pen", "Penna & Bool", Icons.Default.Polyline),
    ToolNavItem("mask", "Maschera", Icons.Default.Gradient),
    ToolNavItem("curve", "Curva RGB", Icons.Default.Timeline),
    ToolNavItem("adjust", "Regola", Icons.Default.Tune),
    ToolNavItem("effects", "Sfocatura / FX", Icons.Default.BlurOn),
    ToolNavItem("collage", "Collage", Icons.Default.GridView),
    ToolNavItem("resize", "Ridimensiona", Icons.Default.AspectRatio),
    ToolNavItem("rotate", "Ruota & 3D", Icons.Default.RotateRight),
    ToolNavItem("border", "Cornice", Icons.Default.FilterFrames)
)

@Composable
fun PicsartEditorScreen(vm: EditorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingForCollage by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.importImageFromUri(context, uri, forCollageSlot = pickingForCollage)
        }
    }

    // Handle system back when inside a tool or panel
    BackHandler(enabled = state.currentTool != null || state.toolGridOpen || state.layersPanelOpen) {
        when {
            state.layersPanelOpen -> vm.toggleLayersPanel()
            state.toolGridOpen -> vm.selectBottomTool("tools")
            state.currentTool != null -> vm.closeCurrentTool(confirm = false)
        }
    }

    // Auto-dismiss toast after 3.2s
    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(3200)
            vm.dismissToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR (Normal vs Tool Mode)
            if (state.currentTool == null) {
                MainTopBar(
                    state = state,
                    onResetDemo = { vm.initDemoProject() },
                    onUndo = { vm.undo() },
                    onRedo = { vm.redo() },
                    onFitZoom = { vm.resetViewportZoom() },
                    onExport = { vm.openExportModal(true) },
                    onShare = { vm.shareImage(context) }
                )
            } else {
                ToolModeTopBar(
                    state = state,
                    onCancel = { vm.closeCurrentTool(confirm = false) },
                    onUndo = { vm.undo() },
                    onRedo = { vm.redo() },
                    onToggleEraser = {
                        val next = if (state.brushStyle == BrushStyle.ERASER) BrushStyle.STANDARD else BrushStyle.ERASER
                        vm.updateBrushSettings(style = next)
                        vm.showToast(if (next == BrushStyle.ERASER) "Gomma attiva" else "Pennello attivo")
                    },
                    onConfirm = { vm.closeCurrentTool(confirm = true) }
                )
            }

            // 2. INTERACTIVE RASTER CANVAS AREA
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                InteractiveCanvasStage(
                    state = state,
                    vm = vm,
                    modifier = Modifier.fillMaxSize()
                )

                // Resolution & Zoom Pill Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { vm.resetViewportZoom() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${state.canvasW}×${state.canvasH} • ${(state.viewScale * 100).toInt()}%",
                        color = TextWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Layers FAB (top-right, matches HTML mockup .layers-fab)
                if (state.currentTool == null) {
                    IconButton(
                        onClick = { vm.toggleLayersPanel() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp, top = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .testTag("layers_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Livelli",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Magenta Import Photo FAB (bottom-right, matches HTML mockup .fab)
                if (state.currentTool == null) {
                    FloatingActionButton(
                        onClick = {
                            pickingForCollage = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        containerColor = AccentPink,
                        contentColor = TextWhite,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 14.dp)
                            .size(48.dp)
                            .testTag("import_photo_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Importa foto dalla galleria",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 4x4 TOOL GRID POPUP (.tool-grid)
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.toolGridOpen && state.currentTool == null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(PanelDark)
                            .border(1.dp, BorderCol, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .padding(12.dp)
                            .testTag("tool_grid_popup")
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(GRID_TOOLS, key = { it.id }) { item ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { vm.openTool(item.id) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp)
                                        .testTag("grid_tool_${item.id}"),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = TextDim,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = item.label,
                                        color = TextDim,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. ACTIVE TOOL PANEL OR BOTTOM SCROLLABLE 21-TOOL BAR
            if (state.currentTool != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgBlack)
                        .navigationBarsPadding()
                ) {
                    when (state.currentTool) {
                        "crop", "rotate" -> CropToolPanel(state, vm)
                        "freecrop" -> CutoutToolPanel(state, vm, isShapeCropOnly = false)
                        "shapecrop" -> CutoutToolPanel(state, vm, isShapeCropOnly = true)
                        "dispersion" -> DispersionToolPanel(state, vm)
                        "clone" -> CloneToolPanel(state, vm)
                        "warp" -> WarpToolPanel(state, vm)
                        "adjust" -> AdjustToolPanel(state, vm)
                        "curve" -> CurvesToolPanel(state, vm)
                        "effects" -> EffectsToolPanel(state, vm)
                        "shapes", "sticker", "callout" -> ShapesToolPanel(state, vm)
                        "text" -> TextToolPanel(state, vm)
                        "pen" -> PenAndBooleanPanel(state, vm)
                        "brush" -> BrushToolPanel(state, vm, isMaskMode = false)
                        "mask" -> BrushToolPanel(state, vm, isMaskMode = true)
                        "selective_mask" -> SelectivePaintToolPanel(state, vm)
                        "fit" -> FitAndBorderPanel(state, vm, isBorderTab = false)
                        "border" -> FitAndBorderPanel(state, vm, isBorderTab = true)
                        "resize" -> ResizeCanvasPanel(state, vm)
                        "collage" -> CollageToolPanel(
                            state = state,
                            vm = vm,
                            onPickPhotoForSlot = {
                                pickingForCollage = true
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }
            } else {
                BottomScrollableToolbar(
                    activeTab = state.activeBottomTab,
                    onToolClick = { id ->
                        when (id) {
                            "addphoto" -> {
                                pickingForCollage = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            "reflect" -> vm.reflectActiveLayer(horizontal = true)
                            else -> vm.selectBottomTool(id)
                        }
                    }
                )
            }
        }

        // 4. LAYERS SIDE PANEL DRAWER
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            LayersSidePanel(state = state, vm = vm)
        }

        // 5. MODALS (Color Picker, Export, Rename Layer)
        ColorPickerModal(state = state, vm = vm)
        ExportDialogModal(state = state, vm = vm)
        RenameLayerModal(state = state, vm = vm)

        // 6. TOAST CARD (Matches HTML mockup .toast)
        val toastMsg = state.toastMessage
        if (toastMsg != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp)
                    .testTag("toast_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = toastMsg,
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = "Capito!",
                        color = ActivePurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { vm.dismissToast() }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTopBar(
    state: EditorUiState,
    onResetDemo: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFitZoom: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TopBarCircleButton(
            icon = Icons.Default.Refresh,
            contentDescription = "Nuovo progetto demo",
            tag = "reset_demo_button",
            onClick = onResetDemo
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TopBarCircleButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Annulla",
                enabled = state.canUndo,
                tag = "undo_button",
                onClick = onUndo
            )
            TopBarCircleButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Ripeti",
                enabled = state.canRedo,
                tag = "redo_button",
                onClick = onRedo
            )
            TopBarCircleButton(
                icon = Icons.Default.CenterFocusStrong,
                contentDescription = "Adatta vista",
                tag = "fit_view_button",
                onClick = onFitZoom
            )
        }
        Spacer(Modifier.weight(1f))
        TopBarCircleButton(
            icon = Icons.Default.Download,
            contentDescription = "Esporta immagine",
            tag = "export_button",
            onClick = onExport
        )
        TopBarCircleButton(
            icon = Icons.Default.Share,
            contentDescription = "Condividi",
            tag = "share_button",
            onClick = onShare
        )
    }
}

@Composable
private fun ToolModeTopBar(
    state: EditorUiState,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleEraser: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TopBarCircleButton(
            icon = Icons.Default.Close,
            contentDescription = "Annulla strumento",
            tag = "tool_cancel_button",
            onClick = onCancel
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TopBarCircleButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Annulla",
                enabled = state.canUndo,
                tag = "tool_undo_button",
                onClick = onUndo
            )
            TopBarCircleButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Ripeti",
                enabled = state.canRedo,
                tag = "tool_redo_button",
                onClick = onRedo
            )
            TopBarCircleButton(
                icon = Icons.Default.AutoFixOff,
                contentDescription = "Gomma rapida",
                tag = "tool_eraser_button",
                onClick = onToggleEraser
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onConfirm,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AccentPink)
                .testTag("tool_confirm_button")
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Conferma modifiche",
                tint = TextWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TopBarCircleButton(
    icon: ImageVector,
    contentDescription: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.09f else 0.03f))
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) TextWhite else TextMute,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BottomScrollableToolbar(
    activeTab: String,
    onToolClick: (String) -> Unit
) {
    // Notice: Zero PRO disabled badges — every single tool is unlocked and functional
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgBlack)
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("bottom_toolbar"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BOTTOM_TOOLS.forEach { item ->
            val isSelected = activeTab == item.id
            Column(
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToolClick(item.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("btool_${item.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (isSelected) ActivePurple else TextDim,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = item.label,
                    color = if (isSelected) ActivePurple else TextDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
