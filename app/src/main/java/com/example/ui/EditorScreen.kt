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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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

@Composable
fun ZestyEditorScreen(vm: EditorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var pickingForCollage by remember { mutableStateOf(false) }
    var pickingAsNewProject by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.importImageFromUri(context, uri, forCollageSlot = pickingForCollage, asNewProject = pickingAsNewProject)
        }
    }

    // Custom Font (.ttf / .otf) Document Picker
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importCustomFontFromUri(context, uri)
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
            delay(1800)
            vm.dismissToast()
        }
    }

    // Pannello parametri dello strumento attivo: lo stesso contenuto in basso (telefono) o a destra (tablet/web)
    val toolPanel: @Composable () -> Unit = {
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
            "colorize" -> ColorizePanel(state, vm)
            "shapes", "sticker", "callout" -> ShapesToolPanel(state, vm)
            "text" -> TextToolPanel(
                state = state,
                vm = vm,
                onPickFontFile = {
                    fontPickerLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "*/*"))
                }
            )
            "pen" -> PenAndBooleanPanel(state, vm)
            "brush" -> BrushToolPanel(state, vm, isMaskMode = false)
            "mask" -> BrushToolPanel(state, vm, isMaskMode = true)
            "selective_mask" -> if (state.cutoutStudio) CutoutStudioPanel(state, vm) else SelectivePaintToolPanel(state, vm)
            "fit" -> FitAndBorderPanel(state, vm, isBorderTab = false)
            "border" -> FitAndBorderPanel(state, vm, isBorderTab = true)
            "resize" -> ResizeCanvasPanel(state, vm)
            "split" -> SplitToolPanel(state, vm)
            "collage" -> CollageToolPanel(
                state = state,
                vm = vm,
                onPickPhotoForSlot = {
                    pickingForCollage = true
                    pickingAsNewProject = false
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    }

    // Azioni che non aprono un pannello
    val onToolClick: (String) -> Unit = { id ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (id) {
            "addphoto" -> {
                pickingForCollage = false
                pickingAsNewProject = false
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            "reflect" -> vm.reflectActiveLayer(horizontal = true)
            "export" -> vm.openExportModal(true)
            "share" -> vm.shareImage(context)
            else -> vm.selectBottomTool(id)
        }
    }
    val familyOf: (String?) -> String? = { tool -> TOOL_FAMILIES.firstOrNull { f -> f.tools.any { it.id == tool } }?.id }
    val selectedFamily = TOOL_FAMILIES.firstOrNull { it.id == state.activeBottomTab }?.id ?: familyOf(state.currentTool)
    val onFamily: (String) -> Unit = { id ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.selectFamily(if (state.activeBottomTab == id && state.currentTool == null) null else id)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StageSurround)
    ) {
        // Disposizione B (telefono verticale) o C (≥ 600 dp: tablet, orizzontale, web) — classi di finestra Material 3
        val expanded = maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Barra superiore: progetto (normale) o strumento (✕ · annulla · ripeti · azioni · ✓)
            if (state.currentTool == null) {
                MainTopBar(
                    state = state,
                    onResetDemo = { vm.openNewProjectModal(true) },
                    onUndo = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.undo() },
                    onRedo = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.redo() },
                    onFitZoom = { vm.resetViewportZoom() },
                    onLayers = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.toggleLayersPanel() }
                )
            } else {
                val inZone = state.currentTool == "selective_mask"
                ToolModeTopBar(
                    state = state,
                    // nella zona: ✕ scarta le modifiche alla zona, ✓ le tiene; entrambi tornano allo strumento di partenza
                    onCancel = { if (inZone) vm.discardSelectiveChanges() else vm.closeCurrentTool(confirm = false) },
                    onUndo = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.undo() },
                    onRedo = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.redo() },
                    onConfirm = {
                        when {
                            inZone -> vm.exitSelectivePaintMode()
                            state.currentTool == "split" -> vm.exportSplit(context)
                            else -> vm.closeCurrentTool(confirm = true)
                        }
                    },
                    actions = { ToolTopBarActions(state, vm) }
                )
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (expanded) {
                    FamilyRail(
                        selectedFamily = selectedFamily,
                        activeTool = state.currentTool,
                        onFamily = onFamily,
                        onTool = onToolClick
                    )
                }

                // 2. Tela
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    InteractiveCanvasStage(state = state, vm = vm, modifier = Modifier.fillMaxSize())
                    // pannello livelli: isola a destra dentro l'area della tela, tra la barra in alto e quella in basso
                    LayersSidePanel(state = state, vm = vm, modifier = Modifier.align(Alignment.CenterEnd))
                }

                // 3C. Pannello parametri a destra (tablet/web): isola staccata dai bordi, accanto alla foto
                if (expanded && state.currentTool != null) {
                    Box(
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BgBlack)
                            .verticalScroll(rememberScrollState())
                    ) { toolPanel() }
                }
            }

            // 3B. Telefono: isola sotto la foto (non la copre mai). Quando si allarga, la tela si restringe
            // e la foto viene riadattata per intero nello spazio rimasto.
            if (!expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(BgBlack)
                        .animateContentSize()
                ) {
                    if (state.currentTool != null) {
                        toolPanel()
                    } else {
                        FamilyToolsRow(familyId = selectedFamily, onTool = onToolClick)
                        FamilyBar(selectedFamily = selectedFamily, onFamily = onFamily)
                    }
                }
            }
        }


        // 5. MODALS (Color Picker, Export, Rename Layer, New Project, Confirm Localize FX)
        ColorPickerModal(state = state, vm = vm)
        ExportDialogModal(state = state, vm = vm)
        AiPackDialog(state = state, vm = vm)
        RenameLayerModal(state = state, vm = vm)
        NewProjectModal(state = state, vm = vm, onOpenPhoto = {
            pickingForCollage = false
            pickingAsNewProject = true
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        ConfirmLocalizeFxModal(state = state, vm = vm)

        // 6. Avviso discreto: una riga, nessun pulsante, sparisce da solo (non interrompe il lavoro)
        val toastMsg = state.toastMessage
        androidx.compose.animation.AnimatedVisibility(
            visible = toastMsg != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp)
        ) {
            Text(
                text = toastMsg ?: "",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Panel3)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("toast_card")
            )
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
    onLayers: () -> Unit
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
            icon = Icons.Default.NoteAdd,
            contentDescription = "Nuovo progetto",
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
        Text(
            text = "${state.canvasW}×${state.canvasH} · ${(state.viewScale * 100).toInt()}%",
            color = TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(17.dp))
                .background(BgBlack)
                .clickable(onClick = onFitZoom)
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("zoom_indicator")
        )
        Spacer(Modifier.weight(1f))
        TopBarCircleButton(
            icon = Icons.Default.Layers,
            contentDescription = "Livelli",
            tag = "layers_fab",
            onClick = onLayers
        )
    }
}

@Composable
private fun ToolModeTopBar(
    state: EditorUiState,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onConfirm: () -> Unit,
    actions: @Composable RowScope.() -> Unit
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
        }
        Spacer(Modifier.weight(1f))
        // azioni contestuali dello strumento (max 2 icone + menu ⋮)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
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
                tint = OnAccent,
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
            .background(if (enabled) BgBlack else BgBlack.copy(alpha = 0.5f))
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

