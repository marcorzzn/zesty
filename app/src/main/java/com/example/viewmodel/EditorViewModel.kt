package com.example.viewmodel

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.ExportEngine
import com.example.engine.ExportFormat
import com.example.engine.PixelEngine
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

data class EditorUiState(
    val canvasW: Int = 800,
    val canvasH: Int = 800,
    val bgColor: Int = Color.parseColor("#08080C"),
    val fitRatioLabel: String = "1:1",
    val fitBgMode: Int = 0, // 0=solid, 1=pattern, 2=blur
    val borderOuter: Float = 0f,
    val borderInner: Float = 0f,
    val borderRadius: Float = 0f,
    val borderColor: Int = Color.WHITE,
    val layers: List<EditorLayer> = emptyList(),
    val activeLayerId: String? = null,
    val compositeBitmap: Bitmap? = null,
    // Viewport zoom / pan / rotate
    val viewScale: Float = 1f,
    val viewOffsetX: Float = 0f,
    val viewOffsetY: Float = 0f,
    val viewRotation: Float = 0f,
    // Tool state
    val activeBottomTab: String = "tools",
    val currentTool: String? = null,
    val toolGridOpen: Boolean = false,
    val layersPanelOpen: Boolean = false,
    val colorPickerOpen: Boolean = false,
    val colorPickerTarget: String = "fill", // "fill", "stroke", "brush", "bg", "border", "grad2"
    val eyedropperActive: Boolean = false,
    val exportModalOpen: Boolean = false,
    val newProjectModalOpen: Boolean = false,
    val confirmLocalizeFxKey: String? = null,
    val customFonts: List<String> = emptyList(),
    val renameLayerModalId: String? = null,
    // History
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    // Crop state
    val cropSubTab: CropSubTab = CropSubTab.CROP,
    val cropRatio: String = "free",
    val cropRect: RectF = RectF(0.1f, 0.1f, 0.9f, 0.9f), // normalized 0..1
    val cropRotation: Float = 0f,
    val perspectiveCorners: List<NormPoint> = listOf(
        NormPoint(0.08f, 0.08f),
        NormPoint(0.92f, 0.08f),
        NormPoint(0.92f, 0.92f),
        NormPoint(0.08f, 0.92f)
    ),
    // Free crop / Cutout / Dispersion / Clone
    val cutoutMode: CutoutMode = CutoutMode.LASSO,
    val lassoPoints: List<PointF> = emptyList(), // in canvas coordinates
    val cutoutShapeKind: ShapeKind = ShapeKind.CIRCLE,
    val dispersionStretch: Float = 65f,
    val dispersionSize: Float = 14f,
    val dispersionDirection: Float = 25f,
    val dispersionFade: Float = 50f,
    val cloneSourcePoint: PointF? = null,
    val cloneOffset: PointF? = null,
    // Brush & Mask & Selective Paint (§23) state
    val brushStyle: BrushStyle = BrushStyle.STANDARD,
    val brushSize: Float = 28f,
    val brushOpacity: Float = 100f,
    val brushHardness: Float = 65f, // 0..100 radial gradient falloff
    val brushColor: Int = Color.parseColor("#E0289A"),
    val selectiveTargetFxKey: String? = null, // null = layer.maskBitmap, non-null = layer.effects[key].localMask
    val showMaskHeatmap: Boolean = true,
    val returnToolAfterSelective: String? = null,
    // Warp state
    val warpMode: WarpMode = WarpMode.PUSH,
    val warpRadius: Float = 95f,
    val warpStrength: Float = 55f,
    // Curves state
    val activeCurveChannel: String = "RGB", // RGB, R, G, B
    // Effects active tab
    val selectedFxKey: String = "blur",
    // Submenu tabs for Shapes & Text
    val shapeSubTab: String = "Forme",
    val textSubTab: String = "Font",
    // Collage state
    val collagePresetCount: Int = 2,
    val collageSplitRatio: Float = 0.5f,
    val collageSpacing: Float = 10f,
    val collageCornerRadius: Float = 16f,
    val collageSlots: List<CollageSlot> = emptyList(),
    val activeCollageSlotId: Int = 0,
    // 170 Filters UI state
    val filterCategory: String = "Popolari",
    val filterThumbnails: Map<String, Bitmap> = emptyMap(),
    // Toast notification
    val toastMessage: String? = null
) {
    val activeLayer: EditorLayer?
        get() = layers.find { it.id == activeLayerId }
}

class EditorViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<HistorySnapshot>()
    private val redoStack = ArrayDeque<HistorySnapshot>()
    private val maxHistory = 12 // Memory-safe cap on mobile devices (Bug 2 fix)

    private var renderJob: Job? = null
    private var warpBackupBitmap: Bitmap? = null
    private var lastBrushPoint: PointF? = null
    private var strokeHasPushedHistory: Boolean = false // Bug 3 fix: pushHistory only on first paint move
    private val dispersionSelection = mutableListOf<PointF>()

    init {
        createNewProject(width = 800, height = 800, bgColor = Color.TRANSPARENT, loadDemo = false)
    }

    fun openNewProjectModal(open: Boolean) {
        _state.value = _state.value.copy(newProjectModalOpen = open)
    }

    fun createNewProject(width: Int, height: Int, bgColor: Int, loadDemo: Boolean = false) {
        val safeW = width.coerceIn(64, 4096)
        val safeH = height.coerceIn(64, 4096)
        val bmp = if (loadDemo) {
            PixelEngine.createDemoBitmap(safeW, safeH)
        } else {
            Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        }
        val baseLayer = EditorLayer(
            id = "layer_base_${System.currentTimeMillis()}",
            type = if (loadDemo) LayerType.IMAGE else LayerType.RASTER,
            name = if (loadDemo) "Scena Demo" else "Livello 1",
            x = 0f,
            y = 0f,
            w = safeW.toFloat(),
            h = safeH.toFloat(),
            bitmap = bmp
        )
        val defaultSlots = buildCollageSlots(2, 0.5f, listOf(bmp))
        _state.value = EditorUiState(
            canvasW = safeW,
            canvasH = safeH,
            bgColor = bgColor,
            customFonts = _state.value.customFonts,
            layers = listOf(baseLayer),
            activeLayerId = baseLayer.id,
            collageSlots = defaultSlots,
            newProjectModalOpen = false,
            toastMessage = if (loadDemo) "Scena demo caricata (${safeW}×${safeH})" else "Nuova tela pronta (${safeW}×${safeH})"
        )
        undoStack.clear()
        redoStack.clear()
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun initDemoProject() {
        createNewProject(width = 800, height = 800, bgColor = Color.parseColor("#08080C"), loadDemo = true)
    }

    // ============ HISTORY (UNDO / REDO) ============
    fun pushHistorySnapshot() {
        val s = _state.value
        val snap = HistorySnapshot(
            canvasW = s.canvasW,
            canvasH = s.canvasH,
            bgColor = s.bgColor,
            fitRatioLabel = s.fitRatioLabel,
            fitBgMode = s.fitBgMode,
            borderOuter = s.borderOuter,
            borderInner = s.borderInner,
            borderRadius = s.borderRadius,
            borderColor = s.borderColor,
            layers = s.layers.map { it.deepCopy() },
            activeLayerId = s.activeLayerId
        )
        if (undoStack.size >= maxHistory) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snap)
        redoStack.clear()
        updateHistoryFlags()
    }

    fun undo() {
        if (undoStack.size <= 1) {
            showToast("Nessuna azione precedente da annullare")
            return
        }
        val current = undoStack.removeLast()
        redoStack.addLast(current)
        val prev = undoStack.last()
        restoreSnapshot(prev)
        showToast("Annullato (${undoStack.size - 1} step disponibili)")
    }

    fun redo() {
        if (redoStack.isEmpty()) {
            showToast("Nessuna azione da ripristinare")
            return
        }
        val next = redoStack.removeLast()
        undoStack.addLast(next)
        restoreSnapshot(next)
        showToast("Ripristinato")
    }

    /**
     * Replay feature: animates step-by-step through the undo history up to current state.
     */
    fun replayHistory() {
        if (undoStack.size <= 1) {
            showToast("Modifica l'immagine per vedere il Replay dei passaggi")
            return
        }
        val snapshots = undoStack.toList()
        viewModelScope.launch {
            showToast("Replay di ${snapshots.size} passaggi in corso...")
            for (snap in snapshots) {
                restoreSnapshot(snap)
                kotlinx.coroutines.delay(350)
            }
            showToast("Replay completato")
        }
    }

    private fun restoreSnapshot(snap: HistorySnapshot) {
        _state.value = _state.value.copy(
            canvasW = snap.canvasW,
            canvasH = snap.canvasH,
            bgColor = snap.bgColor,
            fitRatioLabel = snap.fitRatioLabel,
            fitBgMode = snap.fitBgMode,
            borderOuter = snap.borderOuter,
            borderInner = snap.borderInner,
            borderRadius = snap.borderRadius,
            borderColor = snap.borderColor,
            layers = snap.layers.map { it.deepCopy() },
            activeLayerId = snap.activeLayerId
        )
        updateHistoryFlags()
        rebuildCompositeImmediate()
    }

    private fun updateHistoryFlags() {
        _state.value = _state.value.copy(
            canUndo = undoStack.size > 1,
            canRedo = redoStack.isNotEmpty()
        )
    }

    // ============ COMPOSITE RENDER PIPELINE ============
    fun rebuildCompositeImmediate() {
        val s = _state.value
        val bmp = PixelEngine.renderComposite(
            canvasW = s.canvasW,
            canvasH = s.canvasH,
            bgColor = s.bgColor,
            fitBgMode = s.fitBgMode,
            borderOuter = s.borderOuter,
            borderInner = s.borderInner,
            borderRadius = s.borderRadius,
            borderColor = s.borderColor,
            layers = s.layers
        )
        _state.value = _state.value.copy(compositeBitmap = bmp)
    }

    fun scheduleCompositeRebuild() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val s = _state.value
            val bmp = PixelEngine.renderComposite(
                canvasW = s.canvasW,
                canvasH = s.canvasH,
                bgColor = s.bgColor,
                fitBgMode = s.fitBgMode,
                borderOuter = s.borderOuter,
                borderInner = s.borderInner,
                borderRadius = s.borderRadius,
                borderColor = s.borderColor,
                layers = s.layers
            )
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(compositeBitmap = bmp)
            }
        }
    }

    // ============ TOOL NAVIGATION ============
    fun selectBottomTool(id: String) {
        _state.value = _state.value.copy(activeBottomTab = id)
        when (id) {
            "tools" -> {
                _state.value = _state.value.copy(
                    toolGridOpen = !_state.value.toolGridOpen,
                    currentTool = null
                )
            }
            "replay" -> {
                _state.value = _state.value.copy(toolGridOpen = false)
                replayHistory()
            }
            else -> {
                openTool(id)
            }
        }
    }

    fun openTool(toolId: String) {
        val s = _state.value
        // Map synonyms from bottom bar & grid popup to functional tool editors
        val normalized = when (toolId) {
            "draw" -> "brush"
            "retouch", "movement", "aiimprove" -> "warp"
            "remove", "aireplace" -> "clone"
            "removebg", "selection" -> "freecrop"
            "maskform" -> "shapecrop"
            "frame" -> "border"
            "extend", "aiexpand" -> "fit"
            "blur", "tiltshift" -> "effects"
            else -> toolId
        }

        // Auto-create layer or select existing layer of that type (Bug E fix)
        when (normalized) {
            "shapes", "sticker", "callout" -> {
                val kind = when (toolId) {
                    "callout" -> ShapeKind.CALLOUT
                    "sticker" -> ShapeKind.STAR
                    else -> ShapeKind.CIRCLE
                }
                val active = s.activeLayer
                if (active == null || active.type != LayerType.SHAPE) {
                    val existing = s.layers.lastOrNull { it.type == LayerType.SHAPE }
                    if (existing != null && toolId == "shapes") {
                        _state.value = _state.value.copy(activeLayerId = existing.id)
                    } else {
                        addShapeLayer(kind)
                    }
                }
            }
            "text" -> {
                val active = s.activeLayer
                if (active == null || active.type != LayerType.TEXT) {
                    val existing = s.layers.lastOrNull { it.type == LayerType.TEXT }
                    if (existing != null) {
                        _state.value = _state.value.copy(activeLayerId = existing.id)
                    } else {
                        addTextLayer()
                    }
                }
            }
            "pen" -> {
                val active = s.activeLayer
                if (active == null || active.type != LayerType.PATH) {
                    val existing = s.layers.lastOrNull { it.type == LayerType.PATH }
                    if (existing != null) {
                        _state.value = _state.value.copy(activeLayerId = existing.id)
                    } else {
                        addVectorPathLayer()
                    }
                }
            }
            "warp" -> {
                ensureActiveLayerRasterized()
                warpBackupBitmap = _state.value.activeLayer?.bitmap?.copy(Bitmap.Config.ARGB_8888, true)
            }
            "brush", "clone", "dispersion", "freecrop" -> {
                ensureActiveLayerRasterized()
                dispersionSelection.clear()
                _state.value = _state.value.copy(lassoPoints = emptyList())
            }
            "mask" -> {
                ensureActiveLayerHasMask()
            }
            "effects" -> {
                if (toolId == "blur" || toolId == "tiltshift") {
                    updateLayerEffect("blur", EffectConfig(enabled = true, intensity = 35f, param1 = 40f))
                }
            }
        }

        _state.value = _state.value.copy(
            toolGridOpen = false,
            currentTool = normalized,
            cropRotation = 0f,
            cropRect = RectF(0.1f, 0.1f, 0.9f, 0.9f),
            perspectiveCorners = listOf(
                NormPoint(0.08f, 0.08f),
                NormPoint(0.92f, 0.08f),
                NormPoint(0.92f, 0.92f),
                NormPoint(0.08f, 0.92f)
            )
        )
    }

    fun closeCurrentTool(confirm: Boolean) {
        val tool = _state.value.currentTool
        if (confirm) {
            when (tool) {
                "crop", "rotate" -> applyCropOrPerspective()
                "dispersion" -> applyConfirmedDispersion()
                "freecrop" -> applyCutoutMask()
                "shapecrop" -> applyShapeCropMask()
                "collage" -> bakeCollageToLayer()
            }
            pushHistorySnapshot()
            showToast("Modifiche confermate")
        } else {
            // Revert to latest undo snapshot if cancelled
            undoStack.lastOrNull()?.let { restoreSnapshot(it) }
        }
        _state.value = _state.value.copy(
            currentTool = null,
            eyedropperActive = false,
            lassoPoints = emptyList()
        )
    }

    // ============ LAYER MANAGER ============
    fun toggleLayersPanel() {
        _state.value = _state.value.copy(layersPanelOpen = !_state.value.layersPanelOpen)
    }

    fun selectLayer(id: String) {
        _state.value = _state.value.copy(activeLayerId = id)
    }

    fun toggleLayerMultiSelect(id: String) {
        val updated = _state.value.layers.map {
            if (it.id == id) it.copy(selectedForMulti = !it.selectedForMulti) else it
        }
        _state.value = _state.value.copy(layers = updated)
    }

    fun addEmptyRasterLayer() {
        val s = _state.value
        val bmp = Bitmap.createBitmap(s.canvasW, s.canvasH, Bitmap.Config.ARGB_8888)
        val newLayer = EditorLayer(
            id = "layer_${System.currentTimeMillis()}",
            type = LayerType.RASTER,
            name = "Livello ${s.layers.size + 1}",
            x = 0f,
            y = 0f,
            w = s.canvasW.toFloat(),
            h = s.canvasH.toFloat(),
            bitmap = bmp
        )
        _state.value = s.copy(
            layers = s.layers + newLayer,
            activeLayerId = newLayer.id
        )
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun addShapeLayer(kind: ShapeKind = ShapeKind.CIRCLE) {
        val s = _state.value
        val size = min(s.canvasW, s.canvasH) * 0.42f
        val newLayer = EditorLayer(
            id = "shape_${System.currentTimeMillis()}",
            type = LayerType.SHAPE,
            name = "Forma ${kind.label}",
            x = (s.canvasW - size) / 2f,
            y = (s.canvasH - size) / 2f,
            w = size,
            h = size,
            shapeKind = kind,
            fillColor = Color.parseColor("#8B5CF6"),
            strokeColor = Color.WHITE,
            strokeWidth = 4f
        )
        _state.value = s.copy(
            layers = s.layers + newLayer,
            activeLayerId = newLayer.id
        )
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun addTextLayer() {
        val s = _state.value
        val w = s.canvasW * 0.7f
        val h = 160f
        val newLayer = EditorLayer(
            id = "text_${System.currentTimeMillis()}",
            type = LayerType.TEXT,
            name = "Testo ${s.layers.count { it.type == LayerType.TEXT } + 1}",
            x = (s.canvasW - w) / 2f,
            y = (s.canvasH - h) / 2f,
            w = w,
            h = h,
            text = "Picsart Studio",
            fontFamily = "Inter",
            fontSize = 52f,
            fillColor = Color.WHITE,
            strokeColor = Color.parseColor("#E0289A"),
            strokeWidth = 2f
        )
        _state.value = s.copy(
            layers = s.layers + newLayer,
            activeLayerId = newLayer.id
        )
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun addVectorPathLayer() {
        val s = _state.value
        val newLayer = EditorLayer(
            id = "path_${System.currentTimeMillis()}",
            type = LayerType.PATH,
            name = "Tracciato Vettoriale",
            x = s.canvasW * 0.1f,
            y = s.canvasH * 0.1f,
            w = s.canvasW * 0.8f,
            h = s.canvasH * 0.8f,
            fillColor = Color.argb(90, 224, 40, 154),
            strokeColor = Color.parseColor("#FF3DAD"),
            strokeWidth = 5f,
            pathPoints = listOf(
                NormPoint(0.2f, 0.7f),
                NormPoint(0.5f, 0.2f),
                NormPoint(0.8f, 0.7f)
            ),
            pathClosed = true
        )
        _state.value = s.copy(
            layers = s.layers + newLayer,
            activeLayerId = newLayer.id
        )
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun importImageFromUri(context: Context, uri: Uri, forCollageSlot: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: return@runCatching
                val raw = BitmapFactory.decodeStream(input)
                input.close()
                if (raw == null) return@runCatching

                // Memory safeguard: downsample if >4096px on longest side
                val maxSide = max(raw.width, raw.height)
                val safeBmp = if (maxSide > 4096) {
                    val ratio = 4096f / maxSide
                    Bitmap.createScaledBitmap(
                        raw,
                        (raw.width * ratio).roundToInt().coerceAtLeast(1),
                        (raw.height * ratio).roundToInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    raw.copy(Bitmap.Config.ARGB_8888, true)
                }

                withContext(Dispatchers.Main) {
                    val s = _state.value
                    if (forCollageSlot || s.currentTool == "collage") {
                        val updatedSlots = s.collageSlots.map { slot ->
                            if (slot.id == s.activeCollageSlotId) slot.copy(bitmap = safeBmp) else slot
                        }
                        _state.value = s.copy(collageSlots = updatedSlots)
                        showToast("Foto caricata nello slot collage #${s.activeCollageSlotId + 1}")
                    } else {
                        // Scale proportionally to fit nicely on canvas (max 640px side)
                        val scale = min(s.canvasW * 0.8f / safeBmp.width, s.canvasH * 0.8f / safeBmp.height)
                        val lw = (safeBmp.width * scale).coerceAtLeast(80f)
                        val lh = (safeBmp.height * scale).coerceAtLeast(80f)
                        val newLayer = EditorLayer(
                            id = "img_${System.currentTimeMillis()}",
                            type = LayerType.IMAGE,
                            name = "Foto Importata",
                            x = (s.canvasW - lw) / 2f,
                            y = (s.canvasH - lh) / 2f,
                            w = lw,
                            h = lh,
                            bitmap = safeBmp
                        )
                        _state.value = s.copy(
                            layers = s.layers + newLayer,
                            activeLayerId = newLayer.id
                        )
                        pushHistorySnapshot()
                        rebuildCompositeImmediate()
                        showToast("Immagine importata (${safeBmp.width}×${safeBmp.height})")
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    showToast("Impossibile caricare l'immagine selezionata")
                }
            }
        }
    }

    fun toggleLayerVisibility(id: String) {
        updateLayerById(id) { it.copy(visible = !it.visible) }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun toggleLayerLock(id: String) {
        updateLayerById(id) { it.copy(locked = !it.locked) }
        pushHistorySnapshot()
    }

    fun duplicateLayer(id: String) {
        val s = _state.value
        val orig = s.layers.find { it.id == id } ?: return
        val copy = orig.deepCopy().copy(
            id = "dup_${System.currentTimeMillis()}",
            name = "${orig.name} copia",
            x = orig.x + 20f,
            y = orig.y + 20f
        )
        _state.value = s.copy(
            layers = s.layers + copy,
            activeLayerId = copy.id
        )
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast("Livello duplicato (+20, +20)")
    }

    fun deleteLayer(id: String) {
        val s = _state.value
        if (s.layers.size <= 1) {
            showToast("Deve rimanere almeno un livello")
            return
        }
        val filtered = s.layers.filterNot { it.id == id }
        val nextActive = if (s.activeLayerId == id) filtered.lastOrNull()?.id else s.activeLayerId
        _state.value = s.copy(layers = filtered, activeLayerId = nextActive)
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast("Livello eliminato")
    }

    fun moveLayerOrder(id: String, up: Boolean) {
        val list = _state.value.layers.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx == -1) return
        val targetIdx = if (up) idx + 1 else idx - 1
        if (targetIdx !in list.indices) return
        val item = list.removeAt(idx)
        list.add(targetIdx, item)
        _state.value = _state.value.copy(layers = list)
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun openRenameLayerModal(id: String?) {
        _state.value = _state.value.copy(renameLayerModalId = id)
    }

    fun renameLayer(id: String, newName: String) {
        if (newName.isNotBlank()) {
            updateLayerById(id) { it.copy(name = newName.trim()) }
            pushHistorySnapshot()
        }
        _state.value = _state.value.copy(renameLayerModalId = null)
    }

    fun reflectActiveLayer(horizontal: Boolean) {
        val active = _state.value.activeLayer ?: return
        if (active.locked) {
            showToast("Livello bloccato")
            return
        }
        ensureActiveLayerRasterized()
        val updatedActive = _state.value.activeLayer ?: return
        val bmp = updatedActive.bitmap ?: return
        val m = Matrix().apply {
            if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f)
        }
        val flipped = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        updateActiveLayer { it.copy(bitmap = flipped) }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast(if (horizontal) "Riflesso orizzontale applicato" else "Riflesso verticale applicato")
    }

    // ============ TRANSFORM & GESTURES ON LAYER / VIEW ============
    fun transformActiveLayer(dx: Float, dy: Float, dScale: Float = 1f, dRot: Float = 0f) {
        val active = _state.value.activeLayer ?: return
        if (active.locked) return
        updateActiveLayer { l ->
            val nw = (l.w * dScale).coerceIn(24f, 4096f)
            val nh = (l.h * dScale).coerceIn(24f, 4096f)
            val cx = l.x + l.w / 2f + dx
            val cy = l.y + l.h / 2f + dy
            l.copy(
                x = cx - nw / 2f,
                y = cy - nh / 2f,
                w = nw,
                h = nh,
                rotation = (l.rotation + dRot) % 360f
            )
        }
        scheduleCompositeRebuild()
    }

    fun deleteActiveLayer() {
        val activeId = _state.value.activeLayerId ?: return
        deleteLayer(activeId)
    }

    fun resizeActiveLayerHandle(handleIndex: Int, dx: Float, dy: Float) {
        val active = _state.value.activeLayer ?: return
        if (active.locked) return
        updateActiveLayer { l ->
            var nx = l.x
            var ny = l.y
            var nw = l.w
            var nh = l.h
            val ratio = (l.w / l.h).coerceIn(0.02f, 50f)

            when (handleIndex) {
                0 -> { // Top-Left (Proportional corner)
                    val delta = if (abs(dx) > abs(dy * ratio)) -dx else -dy * ratio
                    nw = (l.w + delta).coerceAtLeast(32f)
                    nh = nw / ratio
                    nx = l.x + (l.w - nw)
                    ny = l.y + (l.h - nh)
                }
                1 -> { // Top-Center (Free height)
                    ny += dy
                    nh = (nh - dy).coerceAtLeast(32f)
                }
                2 -> { // Top-Right (Proportional corner)
                    val delta = if (abs(dx) > abs(dy * ratio)) dx else -dy * ratio
                    nw = (l.w + delta).coerceAtLeast(32f)
                    nh = nw / ratio
                    ny = l.y + (l.h - nh)
                }
                3 -> { // Right-Center (Free width)
                    nw = (nw + dx).coerceAtLeast(32f)
                }
                4 -> { // Bottom-Right (Proportional corner)
                    val delta = if (abs(dx) > abs(dy * ratio)) dx else dy * ratio
                    nw = (l.w + delta).coerceAtLeast(32f)
                    nh = nw / ratio
                }
                5 -> { // Bottom-Center (Free height)
                    nh = (nh + dy).coerceAtLeast(32f)
                }
                6 -> { // Bottom-Left (Proportional corner)
                    val delta = if (abs(dx) > abs(dy * ratio)) -dx else dy * ratio
                    nw = (l.w + delta).coerceAtLeast(32f)
                    nh = nw / ratio
                    nx = l.x + (l.w - nw)
                }
                7 -> { // Left-Center (Free width)
                    nx += dx
                    nw = (nw - dx).coerceAtLeast(32f)
                }
            }
            l.copy(
                x = nx,
                y = ny,
                w = nw.coerceAtLeast(32f),
                h = nh.coerceAtLeast(32f)
            )
        }
        scheduleCompositeRebuild()
    }

    fun rotateActiveLayerToAngle(angleDeg: Float) {
        val active = _state.value.activeLayer ?: return
        if (active.locked) return
        updateActiveLayer { it.copy(rotation = (angleDeg + 360f) % 360f) }
        scheduleCompositeRebuild()
    }

    fun updateViewportGesture(panX: Float, panY: Float, zoomFactor: Float, rotDelta: Float) {
        val s = _state.value
        _state.value = s.copy(
            viewScale = (s.viewScale * zoomFactor).coerceIn(0.35f, 5f),
            viewOffsetX = s.viewOffsetX + panX,
            viewOffsetY = s.viewOffsetY + panY,
            viewRotation = (s.viewRotation + rotDelta) % 360f
        )
    }

    fun resetViewportZoom() {
        _state.value = _state.value.copy(
            viewScale = 1f,
            viewOffsetX = 0f,
            viewOffsetY = 0f,
            viewRotation = 0f
        )
        showToast("Vista adattata allo schermo")
    }

    // ============ PROPERTIES: OPACITY & BLEND MODES ============
    fun updateActiveLayerOpacity(opacity: Float) {
        updateActiveLayer { it.copy(opacity = opacity.coerceIn(0f, 1f)) }
        scheduleCompositeRebuild()
    }

    fun updateActiveLayerBlend(blend: BlendOption) {
        updateActiveLayer { it.copy(blend = blend) }
        rebuildCompositeImmediate()
    }

    // ============ 1. CROP, ROTATE & 3D PERSPECTIVE ============
    fun setCropSubTab(tab: CropSubTab) {
        _state.value = _state.value.copy(cropSubTab = tab)
    }

    fun setCropRatio(ratioKey: String) {
        val s = _state.value
        val cw = s.canvasW.toFloat()
        val ch = s.canvasH.toFloat()
        val targetAspect: Float? = when (ratioKey) {
            "square", "1:1" -> 1f
            "3:4" -> 3f / 4f
            "4:5", "post" -> 4f / 5f
            "9:16", "story" -> 9f / 16f
            "4:3" -> 4f / 3f
            "16:9", "fb" -> 16f / 9f
            else -> null
        }
        val rect = if (targetAspect == null) {
            RectF(0.1f, 0.1f, 0.9f, 0.9f)
        } else {
            val maxW = cw * 0.82f
            val maxH = ch * 0.82f
            var rw = maxW
            var rh = rw / targetAspect
            if (rh > maxH) {
                rh = maxH
                rw = rh * targetAspect
            }
            val left = (cw - rw) / (2f * cw)
            val top = (ch - rh) / (2f * ch)
            RectF(left, top, left + rw / cw, top + rh / ch)
        }
        _state.value = s.copy(cropRatio = ratioKey, cropRect = rect)
    }

    fun setCropRotation(deg: Float) {
        _state.value = _state.value.copy(cropRotation = deg.coerceIn(-180f, 180f))
    }

    fun rotate90Step(clockwise: Boolean) {
        val next = (_state.value.cropRotation + if (clockwise) 90f else -90f) % 360f
        _state.value = _state.value.copy(cropRotation = next)
    }

    fun updateCropCornerDrag(cornerIndex: Int, normX: Float, normY: Float) {
        val s = _state.value
        if (s.cropSubTab == CropSubTab.PERSPECTIVE) {
            val pts = s.perspectiveCorners.toMutableList()
            if (cornerIndex in pts.indices) {
                pts[cornerIndex] = NormPoint(normX.coerceIn(0.02f, 0.98f), normY.coerceIn(0.02f, 0.98f))
                _state.value = s.copy(perspectiveCorners = pts)
            }
        } else {
            val r = RectF(s.cropRect)
            when (cornerIndex) {
                0 -> {
                    r.left = normX.coerceIn(0.02f, r.right - 0.1f)
                    r.top = normY.coerceIn(0.02f, r.bottom - 0.1f)
                }
                1 -> {
                    r.right = normX.coerceIn(r.left + 0.1f, 0.98f)
                    r.top = normY.coerceIn(0.02f, r.bottom - 0.1f)
                }
                2 -> {
                    r.right = normX.coerceIn(r.left + 0.1f, 0.98f)
                    r.bottom = normY.coerceIn(r.top + 0.1f, 0.98f)
                }
                3 -> {
                    r.left = normX.coerceIn(0.02f, r.right - 0.1f)
                    r.bottom = normY.coerceIn(r.top + 0.1f, 0.98f)
                }
            }
            _state.value = s.copy(cropRect = r)
        }
    }

    private fun applyCropOrPerspective() {
        val s = _state.value
        if (s.cropSubTab == CropSubTab.PERSPECTIVE) {
            ensureActiveLayerRasterized()
            val active = _state.value.activeLayer ?: return
            val bmp = active.bitmap ?: return
            val warped = PixelEngine.applyPerspectiveHomography(bmp, s.perspectiveCorners)
            updateActiveLayer { it.copy(bitmap = warped) }
            rebuildCompositeImmediate()
            return
        }

        // Apply rotation if non-zero to base/all layers
        if (abs(s.cropRotation) > 0.05f) {
            val composite = s.compositeBitmap ?: PixelEngine.renderComposite(
                s.canvasW, s.canvasH, s.bgColor, s.fitBgMode,
                s.borderOuter, s.borderInner, s.borderRadius, s.borderColor, s.layers
            )
            val rotBmp = Bitmap.createBitmap(s.canvasW, s.canvasH, Bitmap.Config.ARGB_8888)
            val rc = Canvas(rotBmp)
            rc.translate(s.canvasW / 2f, s.canvasH / 2f)
            rc.rotate(s.cropRotation)
            rc.translate(-s.canvasW / 2f, -s.canvasH / 2f)
            rc.drawBitmap(composite, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

            // Crop to cropRect
            val cropX = (s.cropRect.left * s.canvasW).roundToInt().coerceIn(0, s.canvasW - 16)
            val cropY = (s.cropRect.top * s.canvasH).roundToInt().coerceIn(0, s.canvasH - 16)
            val cropW = ((s.cropRect.width()) * s.canvasW).roundToInt().coerceIn(16, s.canvasW - cropX)
            val cropH = ((s.cropRect.height()) * s.canvasH).roundToInt().coerceIn(16, s.canvasH - cropY)
            val cropped = Bitmap.createBitmap(rotBmp, cropX, cropY, cropW, cropH)

            val newBase = EditorLayer(
                id = "cropped_${System.currentTimeMillis()}",
                type = LayerType.IMAGE,
                name = "Immagine Ritagliata",
                x = 0f,
                y = 0f,
                w = cropW.toFloat(),
                h = cropH.toFloat(),
                bitmap = cropped
            )
            _state.value = s.copy(
                canvasW = cropW,
                canvasH = cropH,
                layers = listOf(newBase),
                activeLayerId = newBase.id,
                cropRotation = 0f
            )
            rebuildCompositeImmediate()
        } else {
            val cropX = (s.cropRect.left * s.canvasW).roundToInt().coerceIn(0, s.canvasW - 16)
            val cropY = (s.cropRect.top * s.canvasH).roundToInt().coerceIn(0, s.canvasH - 16)
            val cropW = ((s.cropRect.width()) * s.canvasW).roundToInt().coerceIn(16, s.canvasW - cropX)
            val cropH = ((s.cropRect.height()) * s.canvasH).roundToInt().coerceIn(16, s.canvasH - cropY)

            val shiftedLayers = s.layers.map { l ->
                l.copy(x = l.x - cropX, y = l.y - cropY)
            }
            _state.value = s.copy(
                canvasW = cropW,
                canvasH = cropH,
                layers = shiftedLayers
            )
            rebuildCompositeImmediate()
        }
    }

    // ============ 2. FREE CROP, SHAPE CROP, DISPERSION, CLONE ============
    fun setCutoutMode(mode: CutoutMode) {
        _state.value = _state.value.copy(cutoutMode = mode, lassoPoints = emptyList())
    }

    fun setCutoutShapeKind(kind: ShapeKind) {
        _state.value = _state.value.copy(cutoutShapeKind = kind)
    }

    private fun applyCutoutMask() {
        val s = _state.value
        val active = s.activeLayer ?: return
        val pts = s.lassoPoints
        if (pts.size < 3 && s.cutoutMode != CutoutMode.SHAPE) return

        val lw = active.w.toInt().coerceAtLeast(4)
        val lh = active.h.toInt().coerceAtLeast(4)
        val mask = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        val mc = Canvas(mask)
        val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = if (s.cutoutMode == CutoutMode.BRUSH) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = s.brushSize * 1.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (s.cutoutMode == CutoutMode.SHAPE) {
            val shapePath = PixelEngine.buildShapePath(lw.toFloat(), lh.toFloat(), active.copy(shapeKind = s.cutoutShapeKind))
            mc.drawPath(shapePath, mp)
        } else {
            val path = Path()
            pts.forEachIndexed { idx, pt ->
                val lx = pt.x - active.x
                val ly = pt.y - active.y
                if (idx == 0) path.moveTo(lx, ly) else path.lineTo(lx, ly)
            }
            if (s.cutoutMode == CutoutMode.LASSO) path.close()
            mc.drawPath(path, mp)
        }

        updateActiveLayer { it.copy(maskBitmap = mask, name = "${it.name} (Ritaglio)") }
        rebuildCompositeImmediate()
    }

    private fun applyShapeCropMask() {
        val s = _state.value
        val active = s.activeLayer ?: return
        val lw = active.w.toInt().coerceAtLeast(4)
        val lh = active.h.toInt().coerceAtLeast(4)
        val mask = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        val mc = Canvas(mask)
        val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val path = PixelEngine.buildShapePath(lw.toFloat(), lh.toFloat(), active.copy(shapeKind = s.cutoutShapeKind))
        mc.drawPath(path, mp)
        updateActiveLayer { it.copy(maskBitmap = mask) }
        rebuildCompositeImmediate()
    }

    fun updateDispersionParams(stretch: Float, size: Float, dir: Float, fade: Float) {
        _state.value = _state.value.copy(
            dispersionStretch = stretch,
            dispersionSize = size,
            dispersionDirection = dir,
            dispersionFade = fade
        )
    }

    private fun applyConfirmedDispersion() {
        val s = _state.value
        val active = s.activeLayer ?: return
        val bmp = active.bitmap ?: return
        val pts = if (dispersionSelection.isNotEmpty()) {
            dispersionSelection.map { PointF(it.x - active.x, it.y - active.y) }
        } else {
            // Default right-side scatter if user tapped confirm directly
            List(35) { i ->
                PointF(bmp.width * 0.65f, (bmp.height * (0.2f + 0.6f * (i / 35f))))
            }
        }
        val dispersed = PixelEngine.applyDispersion(
            src = bmp,
            selectionPoints = pts,
            brushRadius = s.brushSize.coerceAtLeast(24f),
            stretch = s.dispersionStretch,
            particleSize = s.dispersionSize,
            directionDeg = s.dispersionDirection,
            fade = s.dispersionFade
        )
        updateActiveLayer { it.copy(bitmap = dispersed) }
        rebuildCompositeImmediate()
    }

    // ============ 3. WARP / LIQUIFY ============
    fun updateWarpParams(mode: WarpMode, radius: Float, strength: Float) {
        _state.value = _state.value.copy(
            warpMode = mode,
            warpRadius = radius,
            warpStrength = strength
        )
    }

    // ============ 4. ADJUST (REGOLA) ============
    fun updateActiveLayerAdjust(transform: (AdjustParams) -> AdjustParams) {
        updateActiveLayer { it.copy(adjust = transform(it.adjust)) }
        scheduleCompositeRebuild()
    }

    // ============ 5. RGB CURVES ============
    fun setCurveChannel(ch: String) {
        _state.value = _state.value.copy(activeCurveChannel = ch)
    }

    fun updateCurvePoints(channel: String, points: List<CurvePoint>) {
        val sorted = points.sortedBy { it.x }
        updateActiveLayer { l ->
            val c = when (channel) {
                "R" -> l.curves.copy(r = sorted)
                "G" -> l.curves.copy(g = sorted)
                "B" -> l.curves.copy(b = sorted)
                else -> l.curves.copy(rgb = sorted)
            }
            l.copy(curves = c)
        }
        scheduleCompositeRebuild()
    }

    fun resetActiveCurveChannel() {
        val defaultPts = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        updateCurvePoints(_state.value.activeCurveChannel, defaultPts)
    }

    // ============ 7. EFFECTS (FX) ============
    // ============ 170 FILTER FX ENGINE ============
    fun selectEffectFilter(filterId: String) {
        val active = _state.value.activeLayer ?: return
        val newId = if (active.activeFilterId == filterId) null else filterId
        updateActiveLayer { it.copy(activeFilterId = newId) }
        scheduleCompositeRebuild()
    }

    fun setEffectFade(fade: Float) {
        val active = _state.value.activeLayer ?: return
        if (active.activeFilterId == null) return
        updateActiveLayer { it.copy(activeFilterFade = fade) }
        scheduleCompositeRebuild()
    }

    fun setEffectBlendMode(blendMode: String) {
        val active = _state.value.activeLayer ?: return
        if (active.activeFilterId == null) return
        updateActiveLayer { it.copy(activeFilterBlend = blendMode) }
        scheduleCompositeRebuild()
    }

    fun setEffectCategory(category: String) {
        _state.value = _state.value.copy(filterCategory = category)
        generateFilterThumbnails()
    }

    fun applyCurrentEffectPermanently() {
        val active = _state.value.activeLayer ?: return
        val filterId = active.activeFilterId ?: return
        val bmp = active.bitmap ?: return
        val filtered = PixelEngine.applyEffectFilter(bmp, filterId, active.activeFilterFade, active.activeFilterBlend)
        updateActiveLayer {
            it.copy(
                bitmap = filtered,
                activeFilterId = null,
                activeFilterFade = 0f,
                activeFilterBlend = "Normal"
            )
        }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast("Filtro applicato")
    }

    fun cancelCurrentEffect() {
        updateActiveLayer {
            it.copy(
                activeFilterId = null,
                activeFilterFade = 0f,
                activeFilterBlend = "Normal"
            )
        }
        scheduleCompositeRebuild()
    }

    fun generateFilterThumbnails() {
        val active = _state.value.activeLayer ?: return
        val srcBmp = active.bitmap ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val thumbSize = 64
                val scaled = Bitmap.createScaledBitmap(srcBmp, thumbSize, thumbSize, true)
                val filters = PixelEngine.getFiltersByCategory(_state.value.filterCategory)
                val map = mutableMapOf<String, Bitmap>()
                for (f in filters) {
                    val res = PixelEngine.applyEffectFilter(scaled, f.id, 0f, "Normal")
                    map[f.id] = res
                }
                scaled.recycle()
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(filterThumbnails = map)
                }
            } catch (e: Exception) {
                // Ignore any thumbnail generation exception
            }
        }
    }

    fun selectFxKey(key: String) {
        _state.value = _state.value.copy(selectedFxKey = key)
        val active = _state.value.activeLayer ?: return
        val current = active.effects[key]
        if (current == null || !current.enabled) {
            updateLayerEffect(key, (current ?: EffectConfig()).copy(enabled = true))
        }
    }

    fun updateLayerEffect(key: String, config: EffectConfig) {
        updateActiveLayer { l ->
            val map = l.effects.toMutableMap()
            map[key] = config
            l.copy(effects = map)
        }
        scheduleCompositeRebuild()
    }

    fun removeLayerEffect(key: String) {
        updateActiveLayer { l ->
            val map = l.effects.toMutableMap()
            map.remove(key)
            l.copy(effects = map)
        }
        rebuildCompositeImmediate()
    }

    // ============ 8 & 9. SHAPES & TEXT CUSTOMIZATION ============
    fun setShapeSubTab(tab: String) {
        _state.value = _state.value.copy(shapeSubTab = tab)
    }

    fun setTextSubTab(tab: String) {
        _state.value = _state.value.copy(textSubTab = tab)
    }

    fun updateActiveLayerDirect(transform: (EditorLayer) -> EditorLayer) {
        updateActiveLayer(transform)
        scheduleCompositeRebuild()
    }

    // ============ 10. VECTOR PEN TOOL ============
    fun clearVectorPathPoints() {
        updateActiveLayer { it.copy(pathPoints = emptyList(), pathClosed = false) }
        rebuildCompositeImmediate()
    }

    fun closeVectorPath() {
        updateActiveLayer { it.copy(pathClosed = !it.pathClosed) }
        rebuildCompositeImmediate()
    }

    // ============ 11. BOOLEAN OPERATIONS ============
    fun applyBooleanOperation(mode: PorterDuff.Mode, opName: String) {
        val s = _state.value
        val candidates = s.layers.filter { it.selectedForMulti }.ifEmpty {
            s.layers.takeLast(2)
        }
        if (candidates.size < 2) {
            showToast("Aggiungi almeno 2 livelli per eseguire l'operazione booleana")
            return
        }
        val bottom = candidates[candidates.size - 2]
        val top = candidates.last()
        val resultBmp = PixelEngine.performBooleanOp(s.canvasW, s.canvasH, bottom, top, mode)
        val boolLayer = EditorLayer(
            id = "bool_${System.currentTimeMillis()}",
            type = LayerType.RASTER,
            name = "Booleano ($opName)",
            x = 0f,
            y = 0f,
            w = s.canvasW.toFloat(),
            h = s.canvasH.toFloat(),
            bitmap = resultBmp
        )
        val remaining = s.layers.filterNot { it.id == bottom.id || it.id == top.id }
        _state.value = s.copy(
            layers = remaining + boolLayer,
            activeLayerId = boolLayer.id
        )
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast("Operazione booleana '$opName' completata")
    }

    // ============ 12. ALIGN & DISTRIBUTE ============
    fun alignLayers(alignType: String) {
        val s = _state.value
        val selected = s.layers.filter { it.selectedForMulti }
        if (selected.size <= 1) {
            // Align active layer relative to canvas
            val active = s.activeLayer ?: return
            updateActiveLayer { l ->
                when (alignType) {
                    "left" -> l.copy(x = 0f)
                    "centerH" -> l.copy(x = (s.canvasW - l.w) / 2f)
                    "right" -> l.copy(x = s.canvasW - l.w)
                    "top" -> l.copy(y = 0f)
                    "centerV" -> l.copy(y = (s.canvasH - l.h) / 2f)
                    "bottom" -> l.copy(y = s.canvasH - l.h)
                    else -> l
                }
            }
        } else {
            val minX = selected.minOf { it.x }
            val maxX = selected.maxOf { it.x + it.w }
            val minY = selected.minOf { it.y }
            val maxY = selected.maxOf { it.y + it.h }
            val ids = selected.map { it.id }.toSet()

            val updated = s.layers.map { l ->
                if (l.id !in ids) l else when (alignType) {
                    "left" -> l.copy(x = minX)
                    "centerH" -> l.copy(x = (minX + maxX - l.w) / 2f)
                    "right" -> l.copy(x = maxX - l.w)
                    "top" -> l.copy(y = minY)
                    "centerV" -> l.copy(y = (minY + maxY - l.h) / 2f)
                    "bottom" -> l.copy(y = maxY - l.h)
                    else -> l
                }
            }
            _state.value = s.copy(layers = updated)
        }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun distributeLayers(horizontal: Boolean) {
        val s = _state.value
        val targets = s.layers.filter { it.selectedForMulti }.ifEmpty { s.layers }
        if (targets.size < 3) {
            showToast("Servono almeno 3 livelli per distribuire uniformemente")
            return
        }
        val sorted = if (horizontal) targets.sortedBy { it.x } else targets.sortedBy { it.y }
        val minPos = if (horizontal) sorted.first().x else sorted.first().y
        val maxPos = if (horizontal) sorted.last().x else sorted.last().y
        val step = (maxPos - minPos) / (sorted.size - 1).coerceAtLeast(1)
        val posMap = sorted.mapIndexed { idx, l -> l.id to (minPos + idx * step) }.toMap()

        val updated = s.layers.map { l ->
            val newCoord = posMap[l.id] ?: return@map l
            if (horizontal) l.copy(x = newCoord) else l.copy(y = newCoord)
        }
        _state.value = s.copy(layers = updated)
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast(if (horizontal) "Distribuiti orizzontalmente" else "Distribuiti verticalmente")
    }

    // ============ 13, 14 & 23. MASKS, BRUSHES & UNIVERSAL SELECTIVE PAINT (§23) ============
    fun updateBrushSettings(
        style: BrushStyle? = null,
        size: Float? = null,
        opacity: Float? = null,
        hardness: Float? = null,
        color: Int? = null
    ) {
        val s = _state.value
        _state.value = s.copy(
            brushStyle = style ?: s.brushStyle,
            brushSize = size ?: s.brushSize,
            brushOpacity = opacity ?: s.brushOpacity,
            brushHardness = hardness ?: s.brushHardness,
            brushColor = color ?: s.brushColor
        )
    }

    /**
     * Imports any downloaded .ttf / .otf font file from Uri, registers it in PixelEngine.customTypefaces,
     * and applies it immediately to the active text layer.
     */
    fun importCustomFontFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val baseName = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringBeforeLast('.')
                    ?.replace(Regex("[^a-zA-Z0-9_ -]"), "")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "CustomFont_${System.currentTimeMillis() % 1000}"
                val curFonts = _state.value.customFonts
                val fileName = if (baseName in curFonts || PixelEngine.customTypefaces.containsKey(baseName)) {
                    "${baseName}_${curFonts.size + 1}"
                } else {
                    baseName
                }
                val tempFile = java.io.File(context.cacheDir, "font_${System.currentTimeMillis()}.ttf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val tf = Typeface.createFromFile(tempFile)
                PixelEngine.customTypefaces[fileName] = tf
                withContext(Dispatchers.Main) {
                    val updatedFonts = curFonts + fileName
                    _state.value = _state.value.copy(
                        customFonts = updatedFonts,
                        toastMessage = "Font '$fileName' caricato e applicato"
                    )
                    updateActiveLayer { it.copy(fontFamily = fileName) }
                    pushHistorySnapshot()
                    rebuildCompositeImmediate()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    showToast("File font non valido (.ttf / .otf richiesto)")
                }
            }
        }
    }

    /**
     * UX confirmation before converting a global FX into a localized FX (when localMask == null).
     */
    fun requestLocalizeFx(fxKey: String) {
        val active = _state.value.activeLayer ?: return
        val curCfg = active.effects[fxKey]
        if (curCfg?.localMask == null) {
            _state.value = _state.value.copy(confirmLocalizeFxKey = fxKey)
        } else {
            openSelectivePaintMode(fxKey = fxKey, initBlackMask = false)
        }
    }

    fun confirmLocalizeFx() {
        val fxKey = _state.value.confirmLocalizeFxKey ?: return
        _state.value = _state.value.copy(confirmLocalizeFxKey = null)
        openSelectivePaintMode(fxKey = fxKey, initBlackMask = true)
    }

    fun dismissLocalizeFxModal() {
        _state.value = _state.value.copy(confirmLocalizeFxKey = null)
    }

    /**
     * Opens §23 Universal Selective Paint mode for either a specific Effect (`fxKey != null`)
     * or the active layer's visibility (`fxKey == null`).
     */
    fun openSelectivePaintMode(fxKey: String?, initBlackMask: Boolean = true) {
        val s = _state.value
        val active = s.activeLayer ?: return
        val lw = active.w.toInt().coerceAtLeast(4)
        val lh = active.h.toInt().coerceAtLeast(4)

        if (fxKey != null) {
            val curCfg = active.effects[fxKey] ?: EffectConfig(enabled = true)
            val mask = curCfg.localMask ?: Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (initBlackMask) Color.TRANSPARENT else Color.WHITE)
            }
            updateLayerEffect(fxKey, curCfg.copy(enabled = true, localMask = mask, localizing = true))
        } else {
            val mask = active.maskBitmap ?: Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (initBlackMask) Color.TRANSPARENT else Color.WHITE)
            }
            updateActiveLayer { it.copy(maskBitmap = mask, editingMask = true) }
        }

        _state.value = _state.value.copy(
            returnToolAfterSelective = s.currentTool,
            currentTool = "selective_mask",
            selectiveTargetFxKey = fxKey,
            brushStyle = BrushStyle.STANDARD,
            showMaskHeatmap = true,
            toastMessage = if (fxKey != null)
                "Pittura Selettiva FX ($fxKey): dipingi dove applicare l'effetto"
            else
                "Localizza Visibilità Livello: dipingi con pennello o gomma"
        )
        rebuildCompositeImmediate()
    }

    fun exitSelectivePaintMode() {
        val s = _state.value
        val prevTool = s.returnToolAfterSelective ?: "effects"
        _state.value = s.copy(
            currentTool = prevTool,
            selectiveTargetFxKey = null
        )
        rebuildCompositeImmediate()
    }

    fun toggleSelectiveHeatmap() {
        _state.value = _state.value.copy(showMaskHeatmap = !_state.value.showMaskHeatmap)
    }

    fun fillSelectiveMask(fillWhite: Boolean) {
        val s = _state.value
        val active = s.activeLayer ?: return
        val lw = active.w.toInt().coerceAtLeast(4)
        val lh = active.h.toInt().coerceAtLeast(4)
        val mask = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888).apply {
            eraseColor(if (fillWhite) Color.WHITE else Color.TRANSPARENT)
        }
        val fxKey = s.selectiveTargetFxKey
        if (fxKey != null) {
            val cfg = active.effects[fxKey] ?: return
            updateLayerEffect(fxKey, cfg.copy(localMask = mask))
        } else {
            updateActiveLayer { it.copy(maskBitmap = mask) }
        }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun invertSelectiveMask() {
        val s = _state.value
        val active = s.activeLayer ?: return
        val fxKey = s.selectiveTargetFxKey
        if (fxKey != null) {
            val cfg = active.effects[fxKey] ?: return
            updateLayerEffect(fxKey, cfg.copy(localInverted = !cfg.localInverted))
        } else {
            invertActiveLayerMask()
        }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun removeEffectLocalization(fxKey: String) {
        val active = _state.value.activeLayer ?: return
        val cfg = active.effects[fxKey] ?: return
        updateLayerEffect(fxKey, cfg.copy(localMask = null, localInverted = false, localizing = false))
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast("Localizzazione rimossa: effetto applicato ovunque")
    }

    fun ensureActiveLayerHasMask() {
        val active = _state.value.activeLayer ?: return
        if (active.maskBitmap == null) {
            val m = Bitmap.createBitmap(active.w.toInt().coerceAtLeast(4), active.h.toInt().coerceAtLeast(4), Bitmap.Config.ARGB_8888)
            m.eraseColor(Color.WHITE) // White = fully visible
            updateActiveLayer { it.copy(maskBitmap = m, editingMask = true) }
            rebuildCompositeImmediate()
        } else {
            updateActiveLayer { it.copy(editingMask = true) }
        }
    }

    fun invertActiveLayerMask() {
        ensureActiveLayerHasMask()
        updateActiveLayer { it.copy(maskInverted = !it.maskInverted) }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun toggleEditingMaskTarget(editMask: Boolean) {
        if (editMask) ensureActiveLayerHasMask()
        updateActiveLayer { it.copy(editingMask = editMask) }
    }

    fun applyShapeToActiveMask(shapeKind: ShapeKind) {
        val active = _state.value.activeLayer ?: return
        val lw = active.w.toInt().coerceAtLeast(4)
        val lh = active.h.toInt().coerceAtLeast(4)
        val mask = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        val mc = Canvas(mask)
        val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val path = PixelEngine.buildShapePath(lw.toFloat(), lh.toFloat(), active.copy(shapeKind = shapeKind))
        mc.drawPath(path, mp)
        updateActiveLayer { it.copy(maskBitmap = mask) }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    // ============ CANVAS POINTER INTERACTION (BRUSH, CLONE, WARP, PEN, EYEDROPPER) ============
    fun onCanvasPointerDown(canvasX: Float, canvasY: Float) {
        val s = _state.value
        if (s.eyedropperActive) {
            sampleColorAt(canvasX, canvasY)
            return
        }
        // Bug 3 fix: reset strokeHasPushedHistory on pointerdown; push happens once on first paint stroke
        strokeHasPushedHistory = false

        when (s.currentTool) {
            "pen" -> {
                val active = s.activeLayer ?: return
                val nx = ((canvasX - active.x) / active.w.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val ny = ((canvasY - active.y) / active.h.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val existingIdx = active.pathPoints.indexOfFirst {
                    hypot((it.x - nx) * active.w, (it.y - ny) * active.h) < 28f
                }
                if (existingIdx == -1) {
                    pushHistorySnapshot()
                    updateActiveLayer { it.copy(pathPoints = it.pathPoints + NormPoint(nx, ny)) }
                    rebuildCompositeImmediate()
                }
            }
            "clone" -> {
                if (s.cloneSourcePoint == null) {
                    _state.value = s.copy(
                        cloneSourcePoint = PointF(canvasX, canvasY),
                        cloneOffset = null,
                        toastMessage = "Sorgente clona fissata! Ora trascina per dipingere"
                    )
                } else if (s.cloneOffset == null) {
                    val src = s.cloneSourcePoint
                    _state.value = s.copy(
                        cloneOffset = PointF(src.x - canvasX, src.y - canvasY)
                    )
                    paintCloneAt(canvasX, canvasY)
                } else {
                    paintCloneAt(canvasX, canvasY)
                }
            }
            "freecrop" -> {
                _state.value = s.copy(lassoPoints = listOf(PointF(canvasX, canvasY)))
            }
            "dispersion" -> {
                dispersionSelection.add(PointF(canvasX, canvasY))
                _state.value = s.copy(lassoPoints = dispersionSelection.toList())
            }
            "brush", "mask", "selective_mask" -> {
                lastBrushPoint = PointF(canvasX, canvasY)
                paintBrushStroke(canvasX, canvasY, canvasX, canvasY)
            }
            "warp" -> {
                lastBrushPoint = PointF(canvasX, canvasY)
                if (s.warpMode != WarpMode.PUSH) {
                    performWarpAt(canvasX, canvasY, 0f, 0f)
                }
            }
        }
    }

    fun onCanvasPointerMove(canvasX: Float, canvasY: Float) {
        val s = _state.value
        if (s.eyedropperActive) {
            sampleColorAt(canvasX, canvasY)
            return
        }

        // Bug 3 fix: pushHistorySnapshot ONCE on first pointermove of a painting gesture
        if (!strokeHasPushedHistory && s.currentTool in listOf("brush", "mask", "selective_mask", "warp", "clone")) {
            strokeHasPushedHistory = true
            pushHistorySnapshot()
        }

        when (s.currentTool) {
            "pen" -> {
                val active = s.activeLayer ?: return
                val nx = ((canvasX - active.x) / active.w.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val ny = ((canvasY - active.y) / active.h.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val idx = active.pathPoints.indexOfFirst {
                    hypot((it.x - nx) * active.w, (it.y - ny) * active.h) < 42f
                }
                if (idx != -1) {
                    val pts = active.pathPoints.toMutableList()
                    pts[idx] = NormPoint(nx, ny)
                    updateActiveLayer { it.copy(pathPoints = pts) }
                    scheduleCompositeRebuild()
                }
            }
            "clone" -> {
                if (s.cloneOffset != null) {
                    paintCloneAt(canvasX, canvasY)
                }
            }
            "freecrop" -> {
                _state.value = s.copy(lassoPoints = s.lassoPoints + PointF(canvasX, canvasY))
            }
            "dispersion" -> {
                dispersionSelection.add(PointF(canvasX, canvasY))
                _state.value = s.copy(lassoPoints = dispersionSelection.toList())
            }
            "brush", "mask", "selective_mask" -> {
                val prev = lastBrushPoint ?: PointF(canvasX, canvasY)
                paintBrushStroke(prev.x, prev.y, canvasX, canvasY)
                lastBrushPoint = PointF(canvasX, canvasY)
            }
            "warp" -> {
                val prev = lastBrushPoint ?: PointF(canvasX, canvasY)
                performWarpAt(canvasX, canvasY, canvasX - prev.x, canvasY - prev.y)
                lastBrushPoint = PointF(canvasX, canvasY)
            }
        }
    }

    fun onCanvasPointerUp() {
        val s = _state.value
        lastBrushPoint = null
        if (s.currentTool in listOf("brush", "mask", "selective_mask", "warp", "clone", "pen")) {
            rebuildCompositeImmediate()
        }
    }

    fun resetCloneSource() {
        _state.value = _state.value.copy(
            cloneSourcePoint = null,
            cloneOffset = null,
            toastMessage = "Tocca un punto sul canvas per scegliere la nuova sorgente Clona"
        )
    }

    private fun paintBrushStroke(x0: Float, y0: Float, x1: Float, y1: Float) {
        val s = _state.value
        val active = s.activeLayer ?: return
        if (active.locked) return

        val isSelectiveFx = s.currentTool == "selective_mask" && s.selectiveTargetFxKey != null
        val editingLayerMask = (s.currentTool == "selective_mask" && s.selectiveTargetFxKey == null) ||
                s.currentTool == "mask" || active.editingMask

        if (editingLayerMask) {
            ensureActiveLayerHasMask()
        } else if (!isSelectiveFx) {
            ensureActiveLayerRasterized()
        }

        val currentLayer = _state.value.activeLayer ?: return
        val targetBmp: Bitmap = when {
            isSelectiveFx -> {
                val fxKey = s.selectiveTargetFxKey!!
                val cfg = currentLayer.effects[fxKey] ?: EffectConfig(enabled = true)
                (cfg.localMask ?: Bitmap.createBitmap(
                    currentLayer.w.toInt().coerceAtLeast(4),
                    currentLayer.h.toInt().coerceAtLeast(4),
                    Bitmap.Config.ARGB_8888
                )).copy(Bitmap.Config.ARGB_8888, true)
            }
            editingLayerMask -> currentLayer.maskBitmap?.copy(Bitmap.Config.ARGB_8888, true) ?: return
            else -> currentLayer.bitmap?.copy(Bitmap.Config.ARGB_8888, true) ?: return
        }

        val lx0 = x0 - currentLayer.x
        val ly0 = y0 - currentLayer.y
        val lx1 = x1 - currentLayer.x
        val ly1 = y1 - currentLayer.y

        val c = Canvas(targetBmp)
        val radius = (s.brushSize / 2f).coerceIn(1f, 120f)
        val alphaInt = (s.brushOpacity / 100f * 255).toInt().coerceIn(5, 255)

        if (isSelectiveFx || editingLayerMask) {
            // Paint with radial gradient falloff determined by brushHardness (0..100%)
            val dist = hypot(lx1 - lx0, ly1 - ly0)
            val steps = (dist / (radius * 0.35f).coerceAtLeast(2f)).toInt().coerceAtLeast(1)
            val hardStop = (s.brushHardness / 100f).coerceIn(0.05f, 0.98f)
            val dabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (s.brushStyle == BrushStyle.ERASER) {
                dabPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val px = lx0 + (lx1 - lx0) * t
                val py = ly0 + (ly1 - ly0) * t
                // Bug B fix: when hardness > 90%, draw solid circle without radial gradient
                if (s.brushHardness > 90f) {
                    dabPaint.shader = null
                    dabPaint.color = Color.argb(alphaInt, 255, 255, 255)
                    c.drawCircle(px, py, radius, dabPaint)
                } else {
                    dabPaint.shader = RadialGradient(
                        px, py, radius.coerceAtLeast(2f),
                        intArrayOf(
                            Color.argb(alphaInt, 255, 255, 255),
                            Color.argb(alphaInt, 255, 255, 255),
                            Color.argb(0, 255, 255, 255)
                        ),
                        floatArrayOf(0f, hardStop, 1f),
                        Shader.TileMode.CLAMP
                    )
                    c.drawCircle(px, py, radius, dabPaint)
                }
            }
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s.brushSize.coerceIn(1f, 200f)
                strokeJoin = Paint.Join.ROUND
                strokeCap = if (s.brushStyle == BrushStyle.CHISEL) Paint.Cap.SQUARE else Paint.Cap.ROUND
            }
            when (s.brushStyle) {
                BrushStyle.ERASER -> {
                    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    c.drawLine(lx0, ly0, lx1, ly1, p)
                }
                BrushStyle.CHISEL -> {
                    p.color = s.brushColor
                    p.alpha = (s.brushOpacity * 0.5f / 100f * 255).toInt().coerceIn(5, 255)
                    c.drawLine(lx0, ly0, lx1, ly1, p)
                }
                BrushStyle.NEON -> {
                    p.color = s.brushColor
                    p.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
                    c.drawLine(lx0, ly0, lx1, ly1, p)
                    p.maskFilter = null
                    p.color = Color.WHITE
                    p.strokeWidth = (s.brushSize * 0.35f).coerceAtLeast(2f)
                    c.drawLine(lx0, ly0, lx1, ly1, p)
                }
                BrushStyle.SPRAY, BrushStyle.GLITTER -> {
                    p.style = Paint.Style.FILL
                    p.color = s.brushColor
                    p.alpha = alphaInt
                    val dots = if (s.brushStyle == BrushStyle.GLITTER) 14 else 28
                    val rad = s.brushSize * 0.6f
                    for (i in 0 until dots) {
                        val a = Math.random() * Math.PI * 2
                        val r = Math.random() * rad
                        val dx = (cos(a) * r).toFloat()
                        val dy = (sin(a) * r).toFloat()
                        val dotR = if (s.brushStyle == BrushStyle.GLITTER) (1.5f + Math.random().toFloat() * 3.5f) else 1.8f
                        c.drawCircle(lx1 + dx, ly1 + dy, dotR, p)
                    }
                }
                BrushStyle.STANDARD -> {
                    p.color = s.brushColor
                    p.alpha = alphaInt
                    c.drawLine(lx0, ly0, lx1, ly1, p)
                }
            }
        }

        when {
            isSelectiveFx -> {
                val fxKey = s.selectiveTargetFxKey!!
                val cfg = currentLayer.effects[fxKey] ?: EffectConfig(enabled = true)
                updateLayerEffect(fxKey, cfg.copy(localMask = targetBmp))
            }
            editingLayerMask -> updateActiveLayer { it.copy(maskBitmap = targetBmp) }
            else -> updateActiveLayer { it.copy(bitmap = targetBmp) }
        }
        scheduleCompositeRebuild()
    }

    private fun paintCloneAt(canvasX: Float, canvasY: Float) {
        val s = _state.value
        val offset = s.cloneOffset ?: return
        ensureActiveLayerRasterized()
        val active = _state.value.activeLayer ?: return
        val bmp = active.bitmap?.copy(Bitmap.Config.ARGB_8888, true) ?: return

        val dstX = canvasX - active.x
        val dstY = canvasY - active.y
        val srcX = dstX + offset.x
        val srcY = dstY + offset.y
        val rad = (s.brushSize * 0.6f).coerceIn(8f, 120f)

        val c = Canvas(bmp)
        val path = Path().apply { addCircle(dstX, dstY, rad, Path.Direction.CW) }
        c.save()
        c.clipPath(path)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.alpha = (s.brushOpacity / 100f * 255).toInt().coerceIn(20, 255)
        c.drawBitmap(active.bitmap, dstX - srcX, dstY - srcY, p)
        c.restore()

        updateActiveLayer { it.copy(bitmap = bmp) }
        scheduleCompositeRebuild()
    }

    private fun performWarpAt(canvasX: Float, canvasY: Float, dx: Float, dy: Float) {
        val s = _state.value
        val active = s.activeLayer ?: return
        val bmp = active.bitmap ?: return
        val lx = canvasX - active.x
        val ly = canvasY - active.y
        val warped = PixelEngine.applyWarpStroke(
            target = bmp,
            originalForReset = warpBackupBitmap,
            mode = s.warpMode,
            cx = lx,
            cy = ly,
            dx = dx,
            dy = dy,
            radius = s.warpRadius,
            strength = (s.warpStrength / 100f).coerceIn(0.05f, 1f)
        )
        updateActiveLayer { it.copy(bitmap = warped) }
        scheduleCompositeRebuild()
    }

    private fun ensureActiveLayerRasterized() {
        val s = _state.value
        val active = s.activeLayer ?: return
        if (active.type == LayerType.RASTER || active.type == LayerType.IMAGE) {
            if (active.bitmap != null) return
        }
        // Bug 7 fix: notify user and push history snapshot before rasterizing a vector/text/shape layer
        pushHistorySnapshot()
        showToast("Livello '${active.name}' convertito in raster per la modifica pixel")
        val rendered = PixelEngine.renderSingleLayerBitmap(active, s.layers)
        updateActiveLayer {
            it.copy(
                type = LayerType.RASTER,
                bitmap = rendered
            )
        }
    }

    // ============ 15. COLOR PICKER & EYEDROPPER ============
    fun openColorPicker(target: String) {
        _state.value = _state.value.copy(colorPickerOpen = true, colorPickerTarget = target)
    }

    fun closeColorPicker() {
        _state.value = _state.value.copy(colorPickerOpen = false)
    }

    fun toggleEyedropper() {
        _state.value = _state.value.copy(
            eyedropperActive = !_state.value.eyedropperActive,
            colorPickerOpen = false,
            toastMessage = "Tocca qualsiasi punto del canvas per campionare il colore"
        )
    }

    private fun sampleColorAt(canvasX: Float, canvasY: Float) {
        val bmp = _state.value.compositeBitmap ?: return
        val px = canvasX.roundToInt().coerceIn(0, bmp.width - 1)
        val py = canvasY.roundToInt().coerceIn(0, bmp.height - 1)
        val picked = bmp.getPixel(px, py)
        applyPickedColor(picked)
        _state.value = _state.value.copy(
            eyedropperActive = false,
            toastMessage = String.format("Colore campionato: #%06X", 0xFFFFFF and picked)
        )
    }

    fun applyPickedColor(color: Int) {
        val s = _state.value
        when (s.colorPickerTarget) {
            "fill" -> updateActiveLayer { it.copy(fillColor = color) }
            "grad2" -> updateActiveLayer { it.copy(gradColor2 = color, useGradient = true) }
            "stroke" -> updateActiveLayer { it.copy(strokeColor = color) }
            "brush" -> _state.value = s.copy(brushColor = color)
            "bg" -> _state.value = s.copy(bgColor = color)
            "border" -> _state.value = s.copy(borderColor = color)
        }
        rebuildCompositeImmediate()
    }

    // ============ 17. FIT & BORDER FRAME ============
    fun applyCanvasFitRatio(ratioLabel: String) {
        val s = _state.value
        val baseSize = 800
        val (nw, nh) = when (ratioLabel) {
            "1:1" -> baseSize to baseSize
            "4:5" -> baseSize to 1000
            "3:4" -> baseSize to 1066
            "16:9" -> 1024 to 576
            "9:16" -> 576 to 1024
            else -> baseSize to baseSize
        }
        val dx = (nw - s.canvasW) / 2f
        val dy = (nh - s.canvasH) / 2f
        val shifted = s.layers.map { it.copy(x = it.x + dx, y = it.y + dy) }
        _state.value = s.copy(
            canvasW = nw,
            canvasH = nh,
            fitRatioLabel = ratioLabel,
            layers = shifted
        )
        rebuildCompositeImmediate()
    }

    fun updateFitBgMode(mode: Int, bgColor: Int? = null) {
        _state.value = _state.value.copy(
            fitBgMode = mode,
            bgColor = bgColor ?: _state.value.bgColor
        )
        rebuildCompositeImmediate()
    }

    fun updateBorderFrame(outer: Float, inner: Float, radius: Float) {
        _state.value = _state.value.copy(
            borderOuter = outer,
            borderInner = inner,
            borderRadius = radius
        )
        scheduleCompositeRebuild()
    }

    // ============ 18. RESIZE CANVAS ============
    fun resizeCanvasDimensions(newW: Int, newH: Int) {
        val s = _state.value
        val w = newW.coerceIn(64, 4096)
        val h = newH.coerceIn(64, 4096)
        val sx = w.toFloat() / s.canvasW
        val sy = h.toFloat() / s.canvasH
        val scaledLayers = s.layers.map { l ->
            l.copy(x = l.x * sx, y = l.y * sy, w = l.w * sx, h = l.h * sy)
        }
        _state.value = s.copy(canvasW = w, canvasH = h, layers = scaledLayers)
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast("Canvas ridimensionato a ${w}×${h}px")
    }

    // ============ 21. COLLAGE MULTI-SLOT ============
    fun updateCollageLayout(presetCount: Int? = null, splitRatio: Float? = null, spacing: Float? = null, radius: Float? = null) {
        val s = _state.value
        val count = (presetCount ?: s.collagePresetCount).coerceIn(2, 6)
        val split = (splitRatio ?: s.collageSplitRatio).coerceIn(0.2f, 0.8f)
        val existingBitmaps = s.collageSlots.mapNotNull { it.bitmap }
            .ifEmpty { listOfNotNull(s.activeLayer?.bitmap) }
        val slots = buildCollageSlots(count, split, existingBitmaps)
        _state.value = s.copy(
            collagePresetCount = count,
            collageSplitRatio = split,
            collageSpacing = spacing ?: s.collageSpacing,
            collageCornerRadius = radius ?: s.collageCornerRadius,
            collageSlots = slots
        )
    }

    fun selectCollageSlot(id: Int) {
        _state.value = _state.value.copy(activeCollageSlotId = id)
    }

    private fun buildCollageSlots(count: Int, split: Float, bitmaps: List<Bitmap>): List<CollageSlot> {
        val rawCoords = when (count) {
            2 -> listOf(
                RectF(0f, 0f, split, 1f),
                RectF(split, 0f, 1f, 1f)
            )
            3 -> listOf(
                RectF(0f, 0f, split, 1f),
                RectF(split, 0f, 1f, 0.5f),
                RectF(split, 0.5f, 1f, 1f)
            )
            4 -> listOf(
                RectF(0f, 0f, split, split),
                RectF(split, 0f, 1f, split),
                RectF(0f, split, split, 1f),
                RectF(split, split, 1f, 1f)
            )
            6 -> listOf(
                RectF(0f, 0f, 0.5f, 0.33f),
                RectF(0.5f, 0f, 1f, 0.33f),
                RectF(0f, 0.33f, 0.5f, 0.66f),
                RectF(0.5f, 0.33f, 1f, 0.66f),
                RectF(0f, 0.66f, 0.5f, 1f),
                RectF(0.5f, 0.66f, 1f, 1f)
            )
            else -> listOf(
                RectF(0f, 0f, split, 1f),
                RectF(split, 0f, 1f, 1f)
            )
        }
        return rawCoords.mapIndexed { idx, r ->
            val bmp = bitmaps.getOrNull(idx % bitmaps.size.coerceAtLeast(1))
            CollageSlot(idx, r.left, r.top, r.right, r.bottom, bmp)
        }
    }

    private fun bakeCollageToLayer() {
        val s = _state.value
        val out = Bitmap.createBitmap(s.canvasW, s.canvasH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(s.bgColor)
        val pad = s.collageSpacing
        val rad = s.collageCornerRadius
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (slot in s.collageSlots) {
            val rect = RectF(
                slot.left * s.canvasW + pad,
                slot.top * s.canvasH + pad,
                slot.right * s.canvasW - pad,
                slot.bottom * s.canvasH - pad
            )
            if (rect.width() <= 4f || rect.height() <= 4f) continue
            c.save()
            val clip = Path().apply { addRoundRect(rect, rad, rad, Path.Direction.CW) }
            c.clipPath(clip)
            val bmp = slot.bitmap
            if (bmp != null) {
                // Center-crop scale
                val scale = max(rect.width() / bmp.width, rect.height() / bmp.height)
                val sw = bmp.width * scale
                val sh = bmp.height * scale
                val dx = rect.centerX() - sw / 2f
                val dy = rect.centerY() - sh / 2f
                c.drawBitmap(bmp, null, RectF(dx, dy, dx + sw, dy + sh), p)
            } else {
                p.color = Color.parseColor("#26262E")
                c.drawRect(rect, p)
            }
            c.restore()
        }

        val collageLayer = EditorLayer(
            id = "collage_${System.currentTimeMillis()}",
            type = LayerType.IMAGE,
            name = "Collage (${s.collageSlots.size} foto)",
            x = 0f,
            y = 0f,
            w = s.canvasW.toFloat(),
            h = s.canvasH.toFloat(),
            bitmap = out
        )
        _state.value = s.copy(
            layers = s.layers + collageLayer,
            activeLayerId = collageLayer.id
        )
        rebuildCompositeImmediate()
    }

    // ============ 18. EXPORT & SHARE ============
    fun openExportModal(open: Boolean) {
        _state.value = _state.value.copy(exportModalOpen = open)
    }

    fun performExport(
        context: Context,
        format: ExportFormat,
        jpgQuality: Int,
        scale: Float
    ) {
        val s = _state.value
        val composite = s.compositeBitmap ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val res = ExportEngine.exportToDevice(
                context = context,
                composite = composite,
                canvasW = s.canvasW,
                canvasH = s.canvasH,
                bgColor = s.bgColor,
                layers = s.layers,
                format = format,
                jpgQuality = jpgQuality,
                scale = scale
            )
            withContext(Dispatchers.Main) {
                res.onSuccess { (name, bytes) ->
                    val kb = (bytes / 1024).coerceAtLeast(1)
                    _state.value = _state.value.copy(
                        exportModalOpen = false,
                        toastMessage = "Esportato: $name (${kb} KB)"
                    )
                }.onFailure { err ->
                    showToast("Errore esportazione: ${err.localizedMessage ?: "riprova"}")
                }
            }
        }
    }

    fun shareImage(context: Context) {
        val bmp = _state.value.compositeBitmap ?: return
        ExportEngine.shareCurrentImage(context, bmp)
    }

    // ============ HELPERS ============
    fun showToast(msg: String) {
        _state.value = _state.value.copy(toastMessage = msg)
    }

    fun dismissToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    private fun updateActiveLayer(transform: (EditorLayer) -> EditorLayer) {
        val id = _state.value.activeLayerId ?: return
        updateLayerById(id, transform)
    }

    private fun updateLayerById(id: String, transform: (EditorLayer) -> EditorLayer) {
        val updated = _state.value.layers.map {
            if (it.id == id) transform(it) else it
        }
        _state.value = _state.value.copy(layers = updated)
    }
}
