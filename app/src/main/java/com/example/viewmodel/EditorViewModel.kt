package com.example.viewmodel

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.CutoutEngine
import com.example.engine.SubjectEngine
import com.example.engine.AiSegmenter
import com.example.engine.ExportEngine
import com.example.engine.ExportFormat
import com.example.engine.MaskGeometry
import com.example.engine.PixelEngine
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    // tocco a vuoto sulla tela: maniglie nascoste (il livello resta attivo per gli strumenti aperti)
    val handlesHidden: Boolean = false,
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
    // Prospettiva (distorsione): angoli del livello attivo normalizzati sul suo riquadro (0..1, possono uscire)
    val perspectiveCorners: List<NormPoint> = listOf(NormPoint(0f, 0f), NormPoint(1f, 0f), NormPoint(1f, 1f), NormPoint(0f, 1f)),
    val perspVertical: Float = 0f,     // -100..100: allarga/stringe il lato alto (raddrizza le verticali)
    val perspHorizontal: Float = 0f,   // -100..100: allarga/stringe il lato destro
    val perspOffsets: List<NormPoint> = List(4) { NormPoint(0f, 0f) }, // spostamenti manuali degli angoli
    val perspDragging: Boolean = false,
    // Esporta → Dividi e salva
    val split: com.example.engine.SplitGeometry.Settings = com.example.engine.SplitGeometry.Settings(),
    val splitJpg: Boolean = false, // durante il trascinamento niente animazione (segue il dito)
    val perspSource: Bitmap? = null,   // livello attivo già elaborato, per l'anteprima
    val perspBackdrop: Bitmap? = null, // gli altri livelli, sotto l'anteprima
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
    // Pennelli a colore (BrushEngine)
    val brushKind: com.example.engine.BrushEngine.Kind = com.example.engine.BrushEngine.Kind.ROUND,
    val brushFlow: Float = 100f,
    val brushSpacing: Float = 8f,
    val brushScatter: Float = 0f,
    val brushSmoothing: Float = 30f,
    val brushBlend: BlendOption = BlendOption.NORMAL, // "Colore" colora mantenendo luci e ombre
    val recentColors: List<Int> = emptyList(),
    // Pacchetti IA: richiesta di download in attesa di conferma, avanzamento (0..1) durante il download
    val aiPackPrompt: String? = null,
    val aiPackProgress: Float? = null,
    // Colorizza
    val colorizeBusy: Boolean = false,
    val colorizeSaturation: Float = 120f,
    val colorizeWarmth: Float = 0f,   // -100..100
    val colorizeHue: Float = 0f,      // gradi
    val colorizeDepth: Float = 30f,   // 0..100
    val colorizeEdges: Float = 50f,   // 0..100
    val colorizeLayerId: String? = null,
    // Cancella oggetti: la zona da togliere si segna con gli strumenti delle zone, poi LaMa ricostruisce
    val eraseStudio: Boolean = false,
    val eraseBusy: Boolean = false,
    val selectiveTargetFxKey: String? = null, // null = layer.maskBitmap, non-null = layer.effects[key].localMask
    val showMaskHeatmap: Boolean = true,
    // Strumenti zona: pennello, gomma, forme con maniglie, lazo, poligono modellabile
    val maskTool: MaskTool = MaskTool.BRUSH,
    val maskShapeKind: ShapeKind = ShapeKind.CIRCLE,
    val maskShapeDraft: MaskShapeDraft? = null,
    val maskPolyPoints: List<PointF> = emptyList(), // tela; chiuso automaticamente
    val maskSubtract: Boolean = false, // modalità − : il pennello cancella, forme/lazo/poligono tolgono zona
    val maskFeather: Float = 6f, // sfumatura bordo forme/lazo (unita' tela)
    val returnToolAfterSelective: String? = null,
    // Scontorna: bacchetta, sfondo automatico e rifinitura sulla maschera del livello
    val cutoutStudio: Boolean = false,
    val wandTolerance: Float = 18f,   // ΔE CIELAB
    val wandSoftness: Float = 35f,    // % della tolleranza
    val wandContiguous: Boolean = true,
    val wandGradual: Boolean = true,
    val cutoutHoles: Boolean = false, // sfondo: toglie anche le zone racchiuse dal soggetto
    val cutoutRefineRadius: Float = 6f,
    val cutoutGrow: Float = 0f,       // px: + espandi, - contrai
    val cutoutBackdrop: Int = 0,      // anteprima: 0 scacchi, 1 scuro, 2 bianco, 3 lime
    val cutoutBusy: Boolean = false,
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

    /**
     * Tela composta, pubblicata a parte dallo stato dell'editor: un nuovo fotogramma (decine al secondo
     * durante i cursori) fa ridisegnare solo la tela, non pannelli e barre.
     * [display] è la copia già caricata sulla GPU, solo per disegnarla.
     */
    data class CompositeFrame(val bitmap: Bitmap? = null, val display: Bitmap? = null)
    private val _composite = MutableStateFlow(CompositeFrame())
    val composite: StateFlow<CompositeFrame> = _composite.asStateFlow()
    // versione richiesta e versione pubblicata: la tela è aggiornata solo se coincidono
    @Volatile private var compositeRequested = 0L
    @Volatile private var compositePublished = -1L

    private val undoStack = ArrayDeque<HistorySnapshot>()
    private val redoStack = ArrayDeque<HistorySnapshot>()
    private val maxHistory = 12 // Memory-safe cap on mobile devices (Bug 2 fix)

    private var renderJob: Job? = null
    // dichiarati prima di init: all'avvio si chiede già una composizione della tela in sottofondo
    private val renderLock = Any()
    private var renderRunning = false
    private var renderPending = false
    private var warpBackupBitmap: Bitmap? = null
    private var lastBrushPoint: PointF? = null
    private var strokeHasPushedHistory: Boolean = false // Bug 3 fix: pushHistory only on first paint move
    private val dispersionSelection = mutableListOf<PointF>()

    /** Unita' della tela per pixel dello schermo: rende costante la presa delle maniglie a ogni zoom. */
    var touchToCanvas: Float = 1f

    private enum class MaskDrag { NONE, CREATE, MOVE, CORNER, ROTATE, POINT, LASSO }
    private var maskDrag = MaskDrag.NONE
    private var maskDragIndex = -1
    private var maskDragAnchor: PointF? = null
    private var maskDragLast: PointF? = null
    private var maskStrokeDirty = false
    /** Zona confermata su cui si sovrappone in anteprima la forma o il contorno in bozza. */
    private var maskBase: Bitmap? = null
    /** Zona all'ingresso nello strumento: ✕ la ripristina. */
    private var maskOnEnter: Bitmap? = null

    companion object {
        /** Chiave della maschera del filtro del catalogo (separata dagli FX classici). */
        const val FILTER_FX_KEY = "filter"
        /** Zona da cancellare (Cancella oggetti): non è un effetto, vive solo mentre lo strumento è aperto. */
        const val ERASE_KEY = "erase"
        /** LaMa con pesi compressi: ONNX Runtime li riporta in virgola mobile al caricamento. */
        val LAMA_SESSION = mapOf("session.disable_quant_qdq" to "1")
    }

    init {
        createNewProject(width = 1080, height = 1080, bgColor = Color.TRANSPARENT, loadDemo = false)
        // all'avvio si sceglie: aprire una foto (la tela prende le sue dimensioni) o una tela vuota
        _state.value = _state.value.copy(newProjectModalOpen = true, toastMessage = null)
    }

    /** Progetto appena creato e mai modificato: una sola tela vuota, nessuna azione in cronologia. */
    private fun isPristineProject(): Boolean {
        val s = _state.value
        return undoStack.size <= 1 && s.layers.size == 1 && s.layers[0].type == LayerType.RASTER
    }

    /**
     * La foto diventa il progetto: tela = dimensioni della foto, la foto è il livello di base
     * (nessun livello trasparente di dimensione diversa sotto).
     */
    private fun startProjectFromPhoto(original: Bitmap) {
        // tela di lavoro: stesse proporzioni della foto, lato lungo al massimo 2048 px per restare fluidi
        val maxSide = max(original.width, original.height)
        val photo = if (maxSide > 2048) {
            val k = 2048f / maxSide
            Bitmap.createScaledBitmap(original, (original.width * k).roundToInt().coerceAtLeast(1), (original.height * k).roundToInt().coerceAtLeast(1), true)
        } else original
        val w = photo.width; val h = photo.height
        val base = EditorLayer(
            id = "layer_base_${System.currentTimeMillis()}",
            type = LayerType.IMAGE,
            name = "Foto",
            x = 0f, y = 0f, w = w.toFloat(), h = h.toFloat(),
            bitmap = photo
        )
        _state.value = EditorUiState(
            canvasW = w,
            canvasH = h,
            bgColor = Color.TRANSPARENT,
            customFonts = _state.value.customFonts,
            layers = listOf(base),
            activeLayerId = base.id,
            collageSlots = buildCollageSlots(2, 0.5f, listOf(photo)),
            newProjectModalOpen = false
        )
        undoStack.clear()
        redoStack.clear()
        pushHistorySnapshot()
        rebuildCompositeImmediate()
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
    /** Annulla/Ripeti lavorano su stati confermati: una bozza di zona in corso viene lasciata cadere. */
    private fun dropMaskDraft() {
        maskBase = null
        if (_state.value.maskShapeDraft != null || _state.value.maskPolyPoints.isNotEmpty()) {
            _state.value = _state.value.copy(maskShapeDraft = null, maskPolyPoints = emptyList())
        }
    }

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
        dropMaskDraft()
        if (undoStack.size <= 1) {
            showToast("Nessuna azione precedente da annullare")
            return
        }
        val current = undoStack.removeLast()
        redoStack.addLast(current)
        val prev = undoStack.last()
        restoreSnapshot(prev)
    }

    fun redo() {
        dropMaskDraft()
        if (redoStack.isEmpty()) {
            showToast("Nessuna azione da ripristinare")
            return
        }
        val next = redoStack.removeLast()
        undoStack.addLast(next)
        restoreSnapshot(next)
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
    /**
     * Copia della tela per lo schermo, già caricata sulla GPU (Android 8+). Il caricamento avviene qui,
     * in sottofondo, invece che durante il disegno del fotogramma (misurato: "caricamenti lenti" sulla GPU).
     * La tela normale resta per salvare, campionare colori e calcolare.
     */
    private fun gpuCopy(bmp: Bitmap): Bitmap? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            runCatching { bmp.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        else { bmp.prepareToDraw(); null }

    /**
     * La tela a piena risoluzione: se quella mostrata è ancora l'anteprima di un gesto (più piccola),
     * la si ricalcola subito. Da usare per salvare, condividere, ritagliare e campionare colori.
     */
    private fun fullComposite(): Bitmap {
        val s = _state.value
        val cur = _composite.value.bitmap
        if (cur != null && compositePublished == compositeRequested &&
            cur.width == s.canvasW.coerceAtLeast(1) && cur.height == s.canvasH.coerceAtLeast(1)) return cur
        // tela non aggiornata (calcolo in corso) o in anteprima: si calcola subito a piena risoluzione
        val ver = compositeRequested
        val bmp = PixelEngine.renderComposite(
            s.canvasW, s.canvasH, s.bgColor, s.fitBgMode,
            s.borderOuter, s.borderInner, s.borderRadius, s.borderColor, s.layers, scale = 1f
        )
        bmp.prepareToDraw()
        _composite.value = CompositeFrame(bmp, null)
        compositePublished = ver
        return bmp
    }

    /**
     * Ricomposizione dopo un'azione (elimina, fusione, conferma...). Non blocca più lo schermo:
     * si calcola in sottofondo come durante i cursori; chi ha bisogno della tela piena usa [fullComposite].
     */
    fun rebuildCompositeImmediate() = scheduleCompositeRebuild()


    /**
     * Anteprima in diretta: un rendering in corso non viene annullato (a ogni movimento del dito
     * sarebbe lavoro perso e la foto non si aggiornerebbe mai): si segna che serve un nuovo giro
     * e lo si esegue con l'ultimo stato appena il precedente è pronto.
     */
    fun scheduleCompositeRebuild() {
        compositeRequested++
        synchronized(renderLock) {
            if (renderRunning) { renderPending = true; return }
            renderRunning = true
            renderPending = false
        }
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                while (true) {
                    val ver = compositeRequested
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
                    val display = gpuCopy(bmp)
                    withContext(Dispatchers.Main) {
                        _composite.value = CompositeFrame(bmp, display)
                        compositePublished = ver
                    }
                    synchronized(renderLock) {
                        if (!renderPending) { renderRunning = false; return@launch }
                        renderPending = false
                    }
                }
            } finally {
                synchronized(renderLock) { renderRunning = false }
            }
        }
    }

    // ============ TOOL NAVIGATION ============
    /** Famiglia di strumenti mostrata (null = nessuna aperta). */
    fun selectFamily(id: String?) {
        _state.value = _state.value.copy(activeBottomTab = id ?: "", toolGridOpen = false)
    }

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
            "removebg", "selection", "freecrop", "shapecrop" -> "cutout"
            "maskform" -> "shapecrop"
            "frame" -> "border"
            "extend", "aiexpand" -> "fit"
            "blur", "tiltshift" -> "effects"
            else -> toolId
        }

        if (normalized == "cutout") { openCutoutStudio(); return }
        if (normalized == "erase") { openEraseStudio(); return }
        if (normalized == "colorize") {
            _state.value = _state.value.copy(toolGridOpen = false, currentTool = "colorize", colorizeLayerId = null)
            colorizeAb = null
            startColorize()
            return
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
            "brush" -> {
                _state.value = _state.value.copy(lassoPoints = emptyList(), handlesHidden = true)
            }
            "clone", "dispersion", "freecrop" -> {
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
            perspectiveCorners = listOf(NormPoint(0f, 0f), NormPoint(1f, 0f), NormPoint(1f, 1f), NormPoint(0f, 1f)),
            perspVertical = 0f,
            perspHorizontal = 0f,
            perspOffsets = List(4) { NormPoint(0f, 0f) },
            perspSource = null,
            perspBackdrop = null
        )
        if ((normalized == "crop" || normalized == "rotate") && _state.value.cropSubTab == CropSubTab.PERSPECTIVE) preparePerspectivePreview()
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
        _state.value = _state.value.copy(activeLayerId = id, handlesHidden = false)
    }

    /**
     * Livello visibile e non bloccato sotto il punto (il più in alto). Nei livelli immagine conta solo
     * la parte piena: toccando una zona trasparente (es. attorno a un soggetto scontornato) si passa sotto.
     */
    fun layerAt(x: Float, y: Float): EditorLayer? {
        for (l in _state.value.layers.asReversed()) {
            if (!l.visible || l.locked) continue
            val b = com.example.engine.TransformGeometry.box(l.x, l.y, l.w, l.h, l.rotation)
            val p = com.example.engine.TransformGeometry.toLocal(b, x, y)
            if (abs(p.x) > l.w / 2f || abs(p.y) > l.h / 2f) continue
            val u = (p.x / l.w + 0.5f).coerceIn(0f, 0.9999f)
            val v = (p.y / l.h + 0.5f).coerceIn(0f, 0.9999f)
            val bmp = l.bitmap
            if (bmp != null && (l.type == LayerType.IMAGE || l.type == LayerType.RASTER)) {
                val a = (bmp.getPixel((u * bmp.width).toInt(), (v * bmp.height).toInt()) ushr 24) and 255
                if (a < 16) continue
            }
            val m = l.maskBitmap
            if (m != null) {
                val a = (m.getPixel((u * m.width).toInt(), (v * m.height).toInt()) ushr 24) and 255
                if ((if (l.maskInverted) 255 - a else a) < 16) continue
            }
            return l
        }
        return null
    }

    /** Tocco a vuoto sulla tela: spariscono le maniglie e si chiude il pannello dei livelli. */
    fun tapEmptyCanvas() {
        _state.value = _state.value.copy(handlesHidden = true, layersPanelOpen = false)
    }

    /** Tocco su un livello: diventa attivo con le maniglie visibili. */
    fun tapLayer(id: String) {
        _state.value = _state.value.copy(activeLayerId = id, handlesHidden = false)
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
            text = "Zesty",
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

    fun importImageFromUri(context: Context, uri: Uri, forCollageSlot: Boolean = false, asNewProject: Boolean = false) {
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
                    if (!forCollageSlot && s.currentTool != "collage" && (asNewProject || isPristineProject())) {
                        startProjectFromPhoto(safeBmp)
                        return@withContext
                    }
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
        interactiveEffectChange() // durante il gesto anteprima leggera, poi piena qualità
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

    /** Imposta posizione, dimensione e rotazione del livello attivo (gesti con TransformGeometry). */
    fun setActiveLayerBox(b: com.example.engine.TransformGeometry.Box) {
        val active = _state.value.activeLayer ?: return
        if (active.locked) return
        interactiveEffectChange() // anche con un effetto attivo le maniglie restano fluide
        updateActiveLayer { it.copy(x = b.x, y = b.y, w = b.w.coerceIn(16f, 8192f), h = b.h.coerceIn(16f, 8192f), rotation = b.rotation) }
        scheduleCompositeRebuild()
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
        if (tab == CropSubTab.PERSPECTIVE) preparePerspectivePreview()
    }

    // ============ PROSPETTIVA: anteprima in diretta ============
    /** Livello attivo elaborato e resto della composizione, calcolati una volta: poi l'anteprima è solo GPU. */
    private fun preparePerspectivePreview() {
        val s = _state.value
        val active = s.activeLayer ?: return
        val source = PixelEngine.renderSingleLayerBitmap(active, s.layers)
        val others = s.layers.map { if (it.id == active.id) it.copy(visible = false) else it }
        val backdrop = PixelEngine.renderComposite(
            s.canvasW, s.canvasH, s.bgColor, s.fitBgMode, s.borderOuter, s.borderInner, s.borderRadius, s.borderColor, others, scale = 1f
        )
        _state.value = _state.value.copy(perspSource = source, perspBackdrop = backdrop)
    }

    private fun perspectiveCornersFrom(v: Float, h: Float, off: List<NormPoint>): List<NormPoint> {
        val kv = v / 100f * 0.35f; val kh = h / 100f * 0.35f
        val base = listOf(NormPoint(-kv, 0f), NormPoint(1f + kv, -kh), NormPoint(1f, 1f + kh), NormPoint(0f, 1f))
        return base.mapIndexed { i, b -> NormPoint(b.x + off[i].x, b.y + off[i].y) }
    }

    /** Raddrizza con i cursori: gli angoli si spostano con un'animazione morbida. */
    fun setPerspectiveKeystone(vertical: Float? = null, horizontal: Float? = null) {
        val s = _state.value
        val v = vertical ?: s.perspVertical; val h = horizontal ?: s.perspHorizontal
        _state.value = s.copy(perspVertical = v, perspHorizontal = h, perspectiveCorners = perspectiveCornersFrom(v, h, s.perspOffsets))
    }

    fun setPerspectiveDragging(dragging: Boolean) {
        _state.value = _state.value.copy(perspDragging = dragging)
    }

    /** Trascina un angolo (coordinate della tela): diventa uno spostamento manuale sopra i cursori. */
    fun perspectiveCornerDrag(index: Int, canvasX: Float, canvasY: Float) {
        val s = _state.value
        val active = s.activeLayer ?: return
        val nx = ((canvasX - active.x) / active.w.coerceAtLeast(1f)).coerceIn(-0.6f, 1.6f)
        val ny = ((canvasY - active.y) / active.h.coerceAtLeast(1f)).coerceIn(-0.6f, 1.6f)
        val base = perspectiveCornersFrom(s.perspVertical, s.perspHorizontal, List(4) { NormPoint(0f, 0f) })
        val off = s.perspOffsets.toMutableList()
        off[index] = NormPoint(nx - base[index].x, ny - base[index].y)
        _state.value = s.copy(perspOffsets = off, perspectiveCorners = perspectiveCornersFrom(s.perspVertical, s.perspHorizontal, off))
    }

    fun resetPerspective() {
        _state.value = _state.value.copy(
            perspVertical = 0f, perspHorizontal = 0f, perspOffsets = List(4) { NormPoint(0f, 0f) },
            perspectiveCorners = listOf(NormPoint(0f, 0f), NormPoint(1f, 0f), NormPoint(1f, 1f), NormPoint(0f, 1f))
        )
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
            val warped = PixelEngine.applyPerspectiveDistort(bmp, s.perspectiveCorners)
            updateActiveLayer { it.copy(bitmap = warped) }
            _state.value = _state.value.copy(perspSource = null, perspBackdrop = null)
            rebuildCompositeImmediate()
            return
        }

        // Apply rotation if non-zero to base/all layers
        if (abs(s.cropRotation) > 0.05f) {
            val composite = fullComposite()
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
        interactiveEffectChange()
        updateActiveLayer { it.copy(adjust = transform(it.adjust)) }
        scheduleCompositeRebuild()
    }

    // ============ 5. RGB CURVES ============
    fun setCurveChannel(ch: String) {
        _state.value = _state.value.copy(activeCurveChannel = ch)
    }

    fun updateCurvePoints(channel: String, points: List<CurvePoint>) {
        interactiveEffectChange()
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
        // cambiando filtro si ripartono dai valori predefiniti del catalogo
        updateActiveLayer { it.copy(activeFilterId = newId, activeFilterParams = emptyMap()) }
        scheduleCompositeRebuild()
    }

    /** Modifica un parametro del filtro attivo (cursore, colore, scelta o interruttore). */
    private var settleJob: Job? = null

    /**
     * Durante un trascinamento il filtro si mostra in anteprima ridotta (fluida); 250 ms dopo l'ultimo
     * movimento si ricalcola a piena risoluzione.
     */
    private fun interactiveEffectChange() {
        PixelEngine.interactivePreview = true
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            PixelEngine.interactivePreview = false
            scheduleCompositeRebuild()
        }
    }

    /** L'effetto attivo ha le bande cinema accese: il trascinamento sulla foto sposta l'inquadratura fra le bande. */
    fun bandsActive(): Boolean {
        val l = _state.value.activeLayer ?: return false
        val id = l.activeFilterId ?: return false
        val fx = com.example.fx.FxEngine.catalog.get(id) ?: return false
        if (fx.params.none { it.id == "boff" }) return false
        return (PixelEngine.filterParamValues(id, l.activeFilterParams)["bands"] ?: 0f) > 0.5f
    }

    /** Trascinamento verticale (unità della tela): il dito "porta" la foto, la finestra fra le bande segue. */
    fun dragBandsOffset(dyCanvas: Float) {
        val l = _state.value.activeLayer ?: return
        val id = l.activeFilterId ?: return
        val cur = PixelEngine.filterParamValues(id, l.activeFilterParams)["boff"] ?: 0f
        setEffectParam("boff", (cur - dyCanvas / (l.h * 0.5f).coerceAtLeast(1f) * 100f).coerceIn(-100f, 100f))
    }

    fun setEffectParam(paramId: String, value: Float) {
        interactiveEffectChange()
        val active = _state.value.activeLayer ?: return
        if (active.activeFilterId == null) return
        updateActiveLayer { it.copy(activeFilterParams = it.activeFilterParams + (paramId to value)) }
        scheduleCompositeRebuild()
    }

    /** Riporta i parametri del filtro attivo ai valori predefiniti. */
    fun resetEffectParams() {
        val active = _state.value.activeLayer ?: return
        if (active.activeFilterId == null) return
        updateActiveLayer { it.copy(activeFilterParams = emptyMap()) }
        scheduleCompositeRebuild()
    }

    fun setEffectFade(fade: Float) {
        interactiveEffectChange()
        val active = _state.value.activeLayer ?: return
        if (active.activeFilterId == null) return
        updateActiveLayer { it.copy(activeFilterFade = fade) }
        scheduleCompositeRebuild()
    }

    fun setEffectBlendMode(blendMode: String) {
        interactiveEffectChange()
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
        val full = PixelEngine.applyEffectFilter(bmp, filterId, active.activeFilterFade, active.activeFilterBlend, active.activeFilterParams)
        val filtered = active.activeFilterMask?.let { MaskGeometry.blendThroughMask(bmp, full, it, active.activeFilterMaskInverted) } ?: full
        updateActiveLayer {
            it.copy(
                bitmap = filtered,
                activeFilterId = null,
                activeFilterFade = 0f,
                activeFilterBlend = "Normal",
                activeFilterParams = emptyMap(),
                activeFilterMask = null,
                activeFilterMaskInverted = false
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
                activeFilterBlend = "Normal",
                activeFilterParams = emptyMap(),
                activeFilterMask = null,
                activeFilterMaskInverted = false
            )
        }
        scheduleCompositeRebuild()
    }

    fun generateFilterThumbnails() {
        val active = _state.value.activeLayer ?: return
        val srcBmp = active.bitmap ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // ritaglio quadrato centrale: tutte le miniature uguali e nessuna deformazione della foto
                val thumbSize = 160
                val side = minOf(srcBmp.width, srcBmp.height)
                val square = Bitmap.createBitmap(srcBmp, (srcBmp.width - side) / 2, (srcBmp.height - side) / 2, side, side)
                val scaled = Bitmap.createScaledBitmap(square, thumbSize, thumbSize, true)
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
        interactiveEffectChange()
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
        if (fxKey == FILTER_FX_KEY && active.activeFilterId == null) {
            showToast("Scegli prima un filtro")
            return
        }
        if (selectiveMaskOf(active, fxKey) == null) {
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

    // ---------- accesso uniforme alle maschere: livello (null), filtro del catalogo, FX classici ----------
    private fun selectiveMaskOf(layer: EditorLayer, fxKey: String?): Bitmap? = when (fxKey) {
        null -> layer.maskBitmap
        FILTER_FX_KEY -> layer.activeFilterMask
        else -> layer.effects[fxKey]?.localMask
    }

    private fun selectiveInvertedOf(layer: EditorLayer, fxKey: String?): Boolean = when (fxKey) {
        null -> layer.maskInverted
        FILTER_FX_KEY -> layer.activeFilterMaskInverted
        else -> layer.effects[fxKey]?.localInverted == true
    }

    private fun setSelectiveMask(fxKey: String?, mask: Bitmap?) {
        when (fxKey) {
            null -> updateActiveLayer { it.copy(maskBitmap = mask) }
            FILTER_FX_KEY -> updateActiveLayer { it.copy(activeFilterMask = mask) }
            else -> {
                val cfg = _state.value.activeLayer?.effects?.get(fxKey) ?: EffectConfig(enabled = true)
                updateLayerEffect(fxKey, cfg.copy(enabled = true, localMask = mask, localizing = mask != null))
            }
        }
    }

    fun isSelectiveInverted(): Boolean {
        val s = _state.value
        return s.activeLayer?.let { selectiveInvertedOf(it, s.selectiveTargetFxKey) } ?: false
    }

    /** Maschera della zona in modifica, creata vuota se non esiste. */
    private fun currentSelectiveMaskOrNew(): Bitmap? {
        val s = _state.value
        val active = s.activeLayer ?: return null
        return selectiveMaskOf(active, s.selectiveTargetFxKey) ?: Bitmap.createBitmap(
            active.w.toInt().coerceAtLeast(4), active.h.toInt().coerceAtLeast(4), Bitmap.Config.ARGB_8888
        )
    }

    /**
     * Opens §23 Universal Selective Paint mode for an Effect (`fxKey != null`, incluso il filtro del catalogo)
     * or the active layer's visibility (`fxKey == null`).
     */
    fun openSelectivePaintMode(fxKey: String?, initBlackMask: Boolean = true) {
        val s = _state.value
        val active = s.activeLayer ?: return
        val lw = active.w.toInt().coerceAtLeast(4)
        val lh = active.h.toInt().coerceAtLeast(4)

        val mask = selectiveMaskOf(active, fxKey) ?: Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888).apply {
            eraseColor(if (initBlackMask) Color.TRANSPARENT else Color.WHITE)
        }
        setSelectiveMask(fxKey, mask)
        if (fxKey == null) updateActiveLayer { it.copy(editingMask = true) }
        maskOnEnter = mask.copy(Bitmap.Config.ARGB_8888, false)
        maskBase = null

        _state.value = _state.value.copy(
            returnToolAfterSelective = s.currentTool,
            currentTool = "selective_mask",
            selectiveTargetFxKey = fxKey,
            brushStyle = BrushStyle.STANDARD,
            maskTool = MaskTool.BRUSH,
            maskSubtract = false,
            maskShapeDraft = null,
            maskPolyPoints = emptyList(),
            // con l'anteprima in diretta l'evidenziazione rossa coprirebbe il risultato: parte spenta
            showMaskHeatmap = false,
            toastMessage = when (fxKey) {
                null -> "Visibilità livello: dipingi, cancella o usa forme, lazo e poligono"
                FILTER_FX_KEY -> "Zona del filtro: dipingi, cancella o usa forme, lazo e poligono"
                else -> "Zona dell'effetto ($fxKey): dipingi, cancella o usa forme"
            }
        )
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    /** ✓: conferma l'eventuale bozza e torna allo strumento di partenza. */
    fun exitSelectivePaintMode() {
        commitMaskDraft()
        maskOnEnter = null
        val s = _state.value
        val prevTool = if (s.cutoutStudio || s.eraseStudio) null else (s.returnToolAfterSelective ?: "effects")
        if (s.eraseStudio) {
            // la zona di cancellazione non resta nel livello
            updateActiveLayer { it.copy(effects = it.effects - ERASE_KEY) }
            _state.value = _state.value.copy(eraseStudio = false, eraseBusy = false)
        }
        if (s.cutoutStudio) releaseAiAnalysis() // la memoria dell'IA torna libera all'uscita
        _state.value = s.copy(
            currentTool = prevTool,
            cutoutStudio = false,
            cutoutBusy = false,
            selectiveTargetFxKey = null,
            maskShapeDraft = null,
            maskPolyPoints = emptyList()
        )
        rebuildCompositeImmediate()
    }

    /** ✕: scarta tutte le modifiche alla zona fatte da quando si è entrati nello strumento. */
    fun discardSelectiveChanges() {
        maskBase = null
        setSelectiveMask(_state.value.selectiveTargetFxKey, maskOnEnter)
        _state.value = _state.value.copy(maskShapeDraft = null, maskPolyPoints = emptyList())
        pushHistorySnapshot()
        exitSelectivePaintMode()
    }

    fun toggleSelectiveHeatmap() {
        _state.value = _state.value.copy(showMaskHeatmap = !_state.value.showMaskHeatmap)
    }

    fun fillSelectiveMask(fillWhite: Boolean) {
        commitMaskDraft()
        val s = _state.value
        val active = s.activeLayer ?: return
        val lw = active.w.toInt().coerceAtLeast(4)
        val lh = active.h.toInt().coerceAtLeast(4)
        val mask = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888).apply {
            eraseColor(if (fillWhite) Color.WHITE else Color.TRANSPARENT)
        }
        setSelectiveMask(s.selectiveTargetFxKey, mask)
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun invertSelectiveMask() {
        commitMaskDraft()
        val s = _state.value
        val active = s.activeLayer ?: return
        when (val fxKey = s.selectiveTargetFxKey) {
            null -> invertActiveLayerMask()
            FILTER_FX_KEY -> updateActiveLayer { it.copy(activeFilterMaskInverted = !it.activeFilterMaskInverted) }
            else -> {
                val cfg = active.effects[fxKey] ?: return
                updateLayerEffect(fxKey, cfg.copy(localInverted = !cfg.localInverted))
            }
        }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    fun removeEffectLocalization(fxKey: String) {
        val active = _state.value.activeLayer ?: return
        if (fxKey == FILTER_FX_KEY) {
            updateActiveLayer { it.copy(activeFilterMask = null, activeFilterMaskInverted = false) }
        } else {
            val cfg = active.effects[fxKey] ?: return
            updateLayerEffect(fxKey, cfg.copy(localMask = null, localInverted = false, localizing = false))
        }
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast("Localizzazione rimossa: effetto applicato ovunque")
    }

    // ============ ZONA: FORME, LAZO, POLIGONO MODELLABILE (anteprima in diretta) ============
    fun setMaskTool(tool: MaskTool) {
        commitMaskDraft()
        val s = _state.value
        _state.value = s.copy(
            maskTool = tool,
            brushStyle = if (tool == MaskTool.BRUSH) (if (s.maskSubtract) BrushStyle.ERASER else BrushStyle.STANDARD) else s.brushStyle,
            maskShapeDraft = null,
            maskPolyPoints = emptyList()
        )
        if (tool == MaskTool.SHAPE) setMaskShapeKind(s.maskShapeKind)
    }

    /** Modalità ＋/−: vale per pennello (gomma), forme, lazo e poligono; la bozza si aggiorna subito. */
    fun setMaskSubtract(subtract: Boolean) {
        val s = _state.value
        _state.value = s.copy(
            maskSubtract = subtract,
            brushStyle = if (s.maskTool == MaskTool.BRUSH) (if (subtract) BrushStyle.ERASER else BrushStyle.STANDARD) else s.brushStyle
        )
        refreshMaskDraftPreview()
    }

    fun setMaskShapeKind(kind: ShapeKind) {
        val s = _state.value
        val active = s.activeLayer ?: return
        if (s.maskShapeDraft == null) beginMaskDraft()
        val draft = s.maskShapeDraft?.copy(kind = kind) ?: run {
            // forma predefinita al centro del livello: si vede subito applicata
            val side = min(active.w, active.h) * 0.45f
            MaskShapeDraft(kind, active.x + active.w / 2f, active.y + active.h / 2f, side, side)
        }
        _state.value = _state.value.copy(maskShapeKind = kind, maskTool = MaskTool.SHAPE, maskShapeDraft = draft)
        refreshMaskDraftPreview()
    }

    fun setMaskFeather(v: Float) {
        _state.value = _state.value.copy(maskFeather = v)
        refreshMaskDraftPreview()
    }

    /** Memorizza la zona confermata prima di iniziare una bozza. */
    private fun beginMaskDraft() {
        if (maskBase == null) maskBase = currentSelectiveMaskOrNew()?.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun draftPath(s: EditorUiState): android.graphics.Path? {
        val d = s.maskShapeDraft
        return when {
            s.maskTool == MaskTool.SHAPE && d != null && d.w >= 2f && d.h >= 2f -> MaskGeometry.outline(d)
            s.maskTool != MaskTool.SHAPE && s.maskPolyPoints.size >= 3 -> MaskGeometry.polygonPath(s.maskPolyPoints)
            else -> null
        }
    }

    /** Zona mostrata = zona confermata + bozza (aggiunta o tolta); aggiorna subito la foto. */
    private fun refreshMaskDraftPreview() {
        val s = _state.value
        if (wandSeed != null) { refreshWandPreview(); return }
        val base = maskBase ?: return
        val active = s.activeLayer ?: return
        val path = draftPath(s)
        val shown = if (path != null) MaskGeometry.paint(base, path, active, s.maskSubtract, s.maskFeather) else base
        setSelectiveMask(s.selectiveTargetFxKey, shown)
        scheduleCompositeRebuild()
    }

    /** Rende definitiva la bozza (già visibile in anteprima) e la salva nella cronologia. */
    fun commitMaskDraft() {
        val s = _state.value
        if (wandSeed != null) {
            // se l'ultimo calcolo in sottofondo non riflette ancora le impostazioni attuali, si completa ora
            if (!wandFresh) computeWandNow()
            cutoutGen++ // i calcoli ancora in corso non devono più toccare la zona
            wandSeed = null
            maskBase = null
            wandDirty = false
            pushHistorySnapshot()
            return
        }
        if (maskBase == null) return
        val hadDraft = draftPath(s) != null
        refreshMaskDraftPreview()
        maskBase = null
        _state.value = _state.value.copy(maskShapeDraft = null, maskPolyPoints = emptyList())
        if (hadDraft) pushHistorySnapshot()
    }

    /** Scarta solo la bozza corrente: la zona torna com'era prima della forma o del contorno. */
    fun clearMaskDraft() {
        if (wandSeed != null) { wandSeed = null; cutoutGen++; wandDirty = false; wandFresh = true }
        val base = maskBase
        _state.value = _state.value.copy(maskShapeDraft = null, maskPolyPoints = emptyList())
        if (base != null) {
            setSelectiveMask(_state.value.selectiveTargetFxKey, base)
            maskBase = null
            scheduleCompositeRebuild()
        }
    }

    fun removeLastMaskPoint() {
        val pts = _state.value.maskPolyPoints
        if (pts.isNotEmpty()) {
            _state.value = _state.value.copy(maskPolyPoints = pts.dropLast(1))
            refreshMaskDraftPreview()
        }
    }

    /** Sfuma i bordi di tutta la zona già definita. */
    fun featherWholeMask(radius: Float) {
        commitMaskDraft()
        val s = _state.value
        val active = s.activeLayer ?: return
        val mask = selectiveMaskOf(active, s.selectiveTargetFxKey) ?: return
        setSelectiveMask(s.selectiveTargetFxKey, MaskGeometry.blurMask(mask, radius))
        pushHistorySnapshot()
        rebuildCompositeImmediate()
    }

    // ============ SCONTORNA: bacchetta, sfondo automatico, rifinitura (CutoutEngine) ============
    /** Pixel del livello alla risoluzione della maschera, già convertiti in CIELAB (in cache). */
    private class CutoutSource(val key: Long, val px: IntArray, val lab: CutoutEngine.Lab)
    @Volatile private var cutoutSourceCache: CutoutSource? = null
    private var cutoutJob: Job? = null
    private var cutoutGen = 0
    private var wandSeed: Point? = null // coordinate della maschera
    private var wandRunning = false // un solo calcolo alla volta
    private var wandDirty = false   // impostazioni cambiate durante il calcolo: si ricalcola alla fine
    private var wandFresh = true    // la zona mostrata corrisponde alle impostazioni attuali

    private fun cutoutSource(layer: EditorLayer, layers: List<EditorLayer>, mw: Int, mh: Int): CutoutSource {
        val bmp = layer.bitmap ?: PixelEngine.renderSingleLayerBitmap(layer, layers)
        val key = (System.identityHashCode(bmp).toLong() shl 32) xor (bmp.generationId.toLong() shl 20) xor (mw.toLong() * 31 + mh)
        cutoutSourceCache?.takeIf { it.key == key }?.let { return it }
        val scaled = if (bmp.width == mw && bmp.height == mh) bmp else Bitmap.createScaledBitmap(bmp, mw, mh, true)
        val px = IntArray(mw * mh)
        scaled.getPixels(px, 0, mw, 0, 0, mw, mh)
        return CutoutSource(key, px, CutoutEngine.toLab(px, mw, mh)).also { cutoutSourceCache = it }
    }

    private fun maskToAlpha(mask: Bitmap): FloatArray {
        val w = mask.width; val h = mask.height
        val px = IntArray(w * h)
        mask.getPixels(px, 0, w, 0, 0, w, h)
        return FloatArray(w * h) { ((px[it] ushr 24) and 255) / 255f }
    }

    private fun alphaToMask(a: FloatArray, w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { i -> ((a[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or 0xFFFFFF }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) }
    }

    /** Apre Scontorna: lavora sulla visibilità del livello, con tutti gli strumenti della zona più la bacchetta. */
    fun openCutoutStudio() {
        val s0 = _state.value
        if (s0.activeLayer == null) { showToast("Aggiungi prima una foto"); return }
        ensureActiveLayerRasterized()
        val active = _state.value.activeLayer ?: return
        if (active.maskInverted && active.maskBitmap != null) {
            // la maschera invertita diventa normale: ciò che si vede resta uguale
            val a = maskToAlpha(active.maskBitmap)
            for (i in a.indices) a[i] = 1f - a[i]
            updateActiveLayer { it.copy(maskBitmap = alphaToMask(a, active.maskBitmap.width, active.maskBitmap.height), maskInverted = false) }
        }
        openSelectivePaintMode(fxKey = null, initBlackMask = false)
        _state.value = _state.value.copy(
            cutoutStudio = true,
            returnToolAfterSelective = null,
            maskTool = MaskTool.WAND,
            maskSubtract = true,
            showMaskHeatmap = false,
            toastMessage = "Tocca lo sfondo per toglierlo. Con ＋ lo rimetti"
        )
        prefetchAiAnalysis()
    }

    fun setCutoutBackdrop(v: Int) { _state.value = _state.value.copy(cutoutBackdrop = v.mod(4)) }

    fun updateWandSettings(tolerance: Float? = null, softness: Float? = null, contiguous: Boolean? = null, gradual: Boolean? = null) {
        val s = _state.value
        _state.value = s.copy(
            wandTolerance = tolerance ?: s.wandTolerance,
            wandSoftness = softness ?: s.wandSoftness,
            wandContiguous = contiguous ?: s.wandContiguous,
            wandGradual = gradual ?: s.wandGradual
        )
        if (wandSeed != null) refreshWandPreview()
    }

    fun updateCutoutSettings(holes: Boolean? = null, refineRadius: Float? = null, grow: Float? = null) {
        val s = _state.value
        _state.value = s.copy(
            cutoutHoles = holes ?: s.cutoutHoles,
            cutoutRefineRadius = refineRadius ?: s.cutoutRefineRadius,
            cutoutGrow = grow ?: s.cutoutGrow
        )
    }

    /** Tocco con la bacchetta: la selezione resta in bozza e si aggiorna se cambi tolleranza o modalità. */
    private fun wandTap(x: Float, y: Float) {
        commitMaskDraft() // il tocco precedente entra nella zona
        val active = _state.value.activeLayer ?: return
        val mask = currentSelectiveMaskOrNew() ?: return
        val mx = ((x - active.x) / active.w.coerceAtLeast(1f) * mask.width).toInt()
        val my = ((y - active.y) / active.h.coerceAtLeast(1f) * mask.height).toInt()
        if (mx !in 0 until mask.width || my !in 0 until mask.height) return
        beginMaskDraft()
        wandSeed = Point(mx, my)
        refreshWandPreview()
    }

    private fun wandResult(s: EditorUiState, base: Bitmap, seed: Point): Bitmap? {
        val active = s.activeLayer ?: return null
        val w = base.width; val h = base.height
        val src = cutoutSource(active, s.layers, w, h)
        val sel = CutoutEngine.wand(src.lab, seed.x, seed.y, s.wandTolerance, s.wandSoftness / 100f, s.wandContiguous, s.wandGradual)
        return alphaToMask(CutoutEngine.combine(maskToAlpha(base), sel, s.maskSubtract), w, h)
    }

    /** Calcolo immediato (alla conferma), quando quello in sottofondo non è aggiornato. */
    private fun computeWandNow() {
        val base = maskBase ?: return
        val seed = wandSeed ?: return
        val s = _state.value
        runCatching { wandResult(s, base, seed) }.getOrNull()?.let { setSelectiveMask(s.selectiveTargetFxKey, it); scheduleCompositeRebuild() }
        wandFresh = true
    }

    /** Anteprima della bacchetta in sottofondo: mentre trascini un cursore resta un solo calcolo, con l'ultimo valore. */
    private fun refreshWandPreview() {
        if (maskBase == null || wandSeed == null) return
        wandFresh = false
        if (wandRunning) { wandDirty = true; return }
        wandRunning = true
        val s = _state.value
        val base = maskBase!!
        val seed = wandSeed!!
        val gen = cutoutGen
        cutoutJob = viewModelScope.launch(Dispatchers.Default) {
            val shown = runCatching { wandResult(s, base, seed) }.getOrNull()
            withContext(Dispatchers.Main) {
                wandRunning = false
                if (gen == cutoutGen && shown != null) {
                    setSelectiveMask(s.selectiveTargetFxKey, shown)
                    scheduleCompositeRebuild()
                    if (!wandDirty) wandFresh = true
                }
                if (wandDirty) { wandDirty = false; refreshWandPreview() }
            }
        }
    }

    /** Esegue un'operazione su tutta la zona in sottofondo, poi la salva nella cronologia. */
    private fun runCutoutOp(done: String, op: suspend (CutoutSource, FloatArray, Int, Int) -> FloatArray) {
        commitMaskDraft()
        val s = _state.value
        if (s.cutoutBusy) return
        val active = s.activeLayer ?: return
        val key = s.selectiveTargetFxKey
        val mask = currentSelectiveMaskOrNew() ?: return
        _state.value = s.copy(cutoutBusy = true)
        viewModelScope.launch(Dispatchers.Default) {
            cutoutJob?.join()
            val res = runCatching {
                val w = mask.width; val h = mask.height
                alphaToMask(op(cutoutSource(active, s.layers, w, h), maskToAlpha(mask), w, h), w, h)
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(cutoutBusy = false)
                res.onSuccess {
                    setSelectiveMask(key, it)
                    pushHistorySnapshot()
                    rebuildCompositeImmediate()
                    showToast(done)
                }.onFailure { showToast("Operazione non riuscita: ${it.message ?: "memoria insufficiente"}") }
            }
        }
    }

    /**
     * Soggetto automatico: isola persona o oggetto anche su sfondi qualsiasi.
     * I volti (rilevatore di Android, sul telefono, senza rete) indicano dove stanno testa e busto;
     * ciò che è già stato tolto resta sfondo, così si può ripetere dopo qualche ritocco.
     */
    fun cutoutAutoSubject() {
        runCutoutOp("Soggetto isolato: ritocca con bacchetta o pennello se serve") { src, a, w, h ->
            aiPrefetch?.join() // l'analisi anticipata, se in corso, viene riusata
            subjectInArea(src.px, w, h, a)
        }
    }

    /**
     * Area di interesse: se una parte è già stata tolta, l'IA guarda solo dove resta qualcosa
     * (più pixel sul soggetto = bordo più preciso, utile per oggetti piccoli).
     */
    private fun subjectInArea(px: IntArray, w: Int, h: Int, keep: FloatArray): FloatArray {
        val b = CutoutEngine.bounds(keep, w, h, 0.5f) ?: return FloatArray(w * h)
        val bw = b[2] - b[0]; val bh = b[3] - b[1]
        if (bw.toLong() * bh > 0.7 * w.toLong() * h) return mixedSubject(px, w, h, keep)
        val mx = (bw * 0.06f).toInt() + 4; val my = (bh * 0.06f).toInt() + 4
        val l = (b[0] - mx).coerceAtLeast(0); val t = (b[1] - my).coerceAtLeast(0)
        val r = (b[2] + mx).coerceAtMost(w); val btm = (b[3] + my).coerceAtMost(h)
        val cw = r - l; val ch = btm - t
        val cpx = IntArray(cw * ch); val ck = FloatArray(cw * ch)
        for (y in 0 until ch) {
            System.arraycopy(px, (y + t) * w + l, cpx, y * cw, cw)
            System.arraycopy(keep, (y + t) * w + l, ck, y * cw, cw)
        }
        val part = mixedSubject(cpx, cw, ch, ck)
        val out = FloatArray(w * h)
        for (y in 0 until ch) System.arraycopy(part, y * cw, out, (y + t) * w + l, cw)
        return out
    }

    /** Il mix: IS-Net (qualunque soggetto) + MODNet (persone) + taglio su grafo e rifinitura di Zesty. */
    private fun mixedSubject(px: IntArray, w: Int, h: Int, keep: FloatArray): FloatArray {
        val a = aiAnalysis?.takeIf { it.px === px } ?: analyze(px, w, h)
        val salient = a.salient ?: return SubjectEngine.subject(px, w, h, a.faces, keep) // senza IA: solo il motore di Zesty
        return SubjectEngine.fuse(px, w, h, salient, a.portrait, a.faces, keep)
    }

    /** Risultati dell'IA per una foto: si calcolano una volta e si riusano finché la foto non cambia. */
    private class AiAnalysis(val px: IntArray, val faces: List<SubjectEngine.Face>, val salient: FloatArray?, val portrait: FloatArray?)
    @Volatile private var aiAnalysis: AiAnalysis? = null
    private var aiPrefetch: Job? = null

    /** Volti e IS-Net in parallelo (il rilevatore usa un core libero), poi MODNet solo se ci sono persone. */
    private fun analyze(px: IntArray, w: Int, h: Int): AiAnalysis {
        var faces: List<SubjectEngine.Face> = emptyList()
        val faceThread = kotlin.concurrent.thread(name = "zesty-volti") {
            faces = runCatching { detectFaces(px, w, h) }.getOrDefault(emptyList())
        }
        val salient = if (AiSegmenter.available) runCatching { AiSegmenter.salientObject(px, w, h) }.getOrNull() else null
        faceThread.join()
        val portrait = if (salient != null && faces.isNotEmpty()) runCatching { AiSegmenter.portraitMatte(px, w, h) }.getOrNull() else null
        return AiAnalysis(px, faces, salient, portrait).also { aiAnalysis = it }
    }

    /** All'apertura di Scontorna l'IA inizia a guardare la foto: al tocco di "Soggetto" resta solo la fusione. */
    private fun prefetchAiAnalysis() {
        if (!AiSegmenter.available) return
        val s = _state.value
        val active = s.activeLayer ?: return
        val mask = currentSelectiveMaskOrNew() ?: return
        val w = mask.width; val h = mask.height
        aiPrefetch?.cancel()
        aiPrefetch = viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val src = cutoutSource(active, s.layers, w, h)
                if (aiAnalysis?.px !== src.px) analyze(src.px, w, h)
            }
            // uscito da Scontorna mentre l'IA lavorava: niente risultati né modelli lasciati in memoria
            if (!isActive) { aiAnalysis = null; AiSegmenter.release() }
        }
    }

    private fun releaseAiAnalysis() {
        aiPrefetch?.cancel(); aiPrefetch = null
        aiAnalysis = null
        viewModelScope.launch(Dispatchers.Default) { AiSegmenter.release() }
    }

    private fun detectFaces(px: IntArray, w: Int, h: Int): List<SubjectEngine.Face> {
        val sc = minOf(1f, 720f / maxOf(w, h))
        val fw = ((w * sc).toInt() and 1.inv()).coerceAtLeast(2) // larghezza pari, richiesta dal rilevatore
        val fh = (h * sc).toInt().coerceAtLeast(2)
        val src = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createScaledBitmap(src, fw, fh, true)
        val rgb = scaled.copy(Bitmap.Config.RGB_565, false)
        val found = arrayOfNulls<android.media.FaceDetector.Face>(6)
        val count = android.media.FaceDetector(fw, fh, found.size).findFaces(rgb, found)
        val sx = w.toFloat() / fw; val sy = h.toFloat() / fh
        val mid = android.graphics.PointF()
        return (0 until count).mapNotNull { i ->
            val f = found[i] ?: return@mapNotNull null
            if (f.confidence() < 0.3f) return@mapNotNull null
            f.getMidPoint(mid)
            SubjectEngine.Face(mid.x * sx, mid.y * sy, f.eyesDistance() * (sx + sy) / 2f)
        }
    }

    /** Toglie lo sfondo uniforme (colori dominanti del bordo della foto). */
    fun cutoutRemoveUniformBackground() {
        val s = _state.value
        runCutoutOp("Sfondo tolto") { src, a, _, _ ->
            CutoutEngine.combine(a, CutoutEngine.uniformBackground(src.lab, s.wandTolerance, s.wandSoftness / 100f, s.cutoutHoles), subtract = true)
        }
    }

    /** Chiave colore: toglie in tutta la foto il colore indicato (nero, bianco, verde, blu). */
    fun cutoutRemoveColor(rgb: Int) {
        val s = _state.value
        val key = CutoutEngine.rgbToLab((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
        runCutoutOp("Colore tolto") { src, a, _, _ ->
            CutoutEngine.combine(a, CutoutEngine.colorKey(src.lab, key, s.wandTolerance, s.wandSoftness / 100f), subtract = true)
        }
    }

    fun cutoutRefineEdge() {
        val r = _state.value.cutoutRefineRadius.roundToInt().coerceAtLeast(1)
        runCutoutOp("Bordo rifinito") { src, a, w, h -> CutoutEngine.refineEdge(src.px, a, w, h, r) }
    }

    fun cutoutGrowShrink() {
        val r = _state.value.cutoutGrow.roundToInt()
        if (r == 0) { showToast("Scegli quanti pixel espandere o contrarre"); return }
        runCutoutOp(if (r > 0) "Zona espansa di $r px" else "Zona contratta di ${-r} px") { _, a, w, h -> CutoutEngine.grow(a, w, h, r) }
    }

    fun cutoutFillHoles() = runCutoutOp("Buchi riempiti") { _, a, w, h -> CutoutEngine.fillHoles(a, w, h) }

    fun cutoutRemoveIslands(onlyLargest: Boolean) =
        runCutoutOp(if (onlyLargest) "Tenuto solo il soggetto principale" else "Isolotti tolti") { _, a, w, h ->
            CutoutEngine.removeIslands(a, w, h, if (onlyLargest) 1f else 0.02f)
        }

    /** Togli alone: corregge il colore dei pixel di bordo (modifica i pixel del livello, annullabile). */
    fun cutoutDecontaminate() {
        commitMaskDraft()
        val s = _state.value
        if (s.cutoutBusy || s.selectiveTargetFxKey != null) return
        val active = s.activeLayer ?: return
        val bmp = active.bitmap ?: return
        val mask = active.maskBitmap ?: return
        _state.value = s.copy(cutoutBusy = true)
        viewModelScope.launch(Dispatchers.Default) {
            val res = runCatching {
                val w = bmp.width; val h = bmp.height
                val m = if (mask.width == w && mask.height == h) mask else Bitmap.createScaledBitmap(mask, w, h, true)
                val px = IntArray(w * h)
                bmp.getPixels(px, 0, w, 0, 0, w, h)
                val radius = (4f * w / mask.width.coerceAtLeast(1)).roundToInt().coerceIn(2, 24)
                val out = CutoutEngine.decontaminate(px, maskToAlpha(m), w, h, radius)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(out, 0, w, 0, 0, w, h) }
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(cutoutBusy = false)
                res.onSuccess { nb ->
                    updateActiveLayer { it.copy(bitmap = nb) }
                    pushHistorySnapshot()
                    rebuildCompositeImmediate()
                    showToast("Alone tolto dai bordi")
                }.onFailure { showToast("Operazione non riuscita") }
            }
        }
    }

    /** Ritaglia il livello attorno al soggetto; se è l'unico livello, anche la tela (PNG senza margini vuoti). */
    fun cutoutTrimToSubject() {
        commitMaskDraft()
        val s = _state.value
        val active = s.activeLayer ?: return
        val mask = active.maskBitmap ?: run { showToast("Togli prima lo sfondo"); return }
        val bmp = active.bitmap ?: return
        val mw = mask.width; val mh = mask.height
        val b = CutoutEngine.bounds(maskToAlpha(mask), mw, mh) ?: run { showToast("La zona visibile è vuota"); return }
        if (b[0] == 0 && b[1] == 0 && b[2] == mw && b[3] == mh) { showToast("Il soggetto occupa già tutto il livello"); return }
        val bx = bmp.width.toFloat() / mw; val by = bmp.height.toFloat() / mh
        val cl = (b[0] * bx).toInt(); val ct = (b[1] * by).toInt()
        val cw = ((b[2] - b[0]) * bx).roundToInt().coerceIn(1, bmp.width - cl)
        val ch = ((b[3] - b[1]) * by).roundToInt().coerceIn(1, bmp.height - ct)
        val newBmp = Bitmap.createBitmap(bmp, cl, ct, cw, ch).copy(Bitmap.Config.ARGB_8888, true)
        val newMask = Bitmap.createBitmap(mask, b[0], b[1], b[2] - b[0], b[3] - b[1]).copy(Bitmap.Config.ARGB_8888, true)
        val sx = active.w / mw; val sy = active.h / mh
        var nx = active.x + b[0] * sx; var ny = active.y + b[1] * sy
        val nw = (b[2] - b[0]) * sx; val nh = (b[3] - b[1]) * sy
        val single = s.layers.size == 1
        if (single) { nx = 0f; ny = 0f }
        updateActiveLayer { it.copy(bitmap = newBmp, maskBitmap = newMask, x = nx, y = ny, w = nw, h = nh) }
        if (single) _state.value = _state.value.copy(canvasW = nw.roundToInt().coerceAtLeast(1), canvasH = nh.roundToInt().coerceAtLeast(1))
        maskOnEnter = newMask.copy(Bitmap.Config.ARGB_8888, false)
        pushHistorySnapshot()
        rebuildCompositeImmediate()
        showToast(if (single) "Foto ritagliata attorno al soggetto" else "Livello ritagliato attorno al soggetto")
    }

    private fun maskHitRadius() = 30f * touchToCanvas

    /** Tocchi per forme, lazo e poligono. Restituisce false se il tocco va al pennello o alla gomma. */
    private fun maskPointerDown(x: Float, y: Float): Boolean {
        val s = _state.value
        val r = maskHitRadius()
        maskDragLast = PointF(x, y)
        when (s.maskTool) {
            MaskTool.WAND -> { wandTap(x, y); return true }
            MaskTool.BRUSH -> return false
            MaskTool.SHAPE -> {
                val d = s.maskShapeDraft
                if (d != null) {
                    val hs = MaskGeometry.handles(d, 40f * touchToCanvas)
                    val hit = MaskGeometry.nearestPoint(hs, x, y, r)
                    when {
                        hit == 4 -> { maskDrag = MaskDrag.ROTATE; return true }
                        hit in 0..3 -> { maskDrag = MaskDrag.CORNER; maskDragIndex = hit; return true }
                        MaskGeometry.contains(d, x, y) -> { maskDrag = MaskDrag.MOVE; return true }
                    }
                }
                // una nuova forma conferma quella precedente (già visibile in anteprima)
                if (d != null) commitMaskDraft()
                maskDrag = MaskDrag.CREATE
                maskDragAnchor = PointF(x, y)
                beginMaskDraft()
                _state.value = _state.value.copy(maskShapeDraft = MaskShapeDraft(s.maskShapeKind, x, y, 1f, 1f))
            }
            MaskTool.LASSO, MaskTool.POLYGON -> {
                val pts = s.maskPolyPoints
                val hit = MaskGeometry.nearestPoint(pts, x, y, r)
                if (hit != -1) { maskDrag = MaskDrag.POINT; maskDragIndex = hit; return true }
                if (s.maskTool == MaskTool.LASSO) {
                    if (pts.size >= 3) commitMaskDraft()
                    maskDrag = MaskDrag.LASSO
                    beginMaskDraft()
                    _state.value = _state.value.copy(maskPolyPoints = listOf(PointF(x, y)))
                } else {
                    // inserisce sul lato più vicino, altrimenti aggiunge in coda
                    val seg = if (pts.size >= 3) MaskGeometry.nearestSegment(pts, x, y, r) else -1
                    if (pts.isEmpty()) beginMaskDraft()
                    val newPts = pts.toMutableList()
                    val idx = if (seg != -1) seg + 1 else newPts.size
                    newPts.add(idx, PointF(x, y))
                    maskDrag = MaskDrag.POINT; maskDragIndex = idx
                    _state.value = s.copy(maskPolyPoints = newPts)
                }
            }
        }
        return true
    }

    private fun maskPointerMove(x: Float, y: Float) {
        val s = _state.value
        val last = maskDragLast ?: PointF(x, y)
        when (maskDrag) {
            MaskDrag.CREATE -> {
                val a = maskDragAnchor
                val d = s.maskShapeDraft
                if (a != null && d != null) {
                    _state.value = s.copy(maskShapeDraft = d.copy(
                        cx = (a.x + x) / 2f, cy = (a.y + y) / 2f,
                        w = abs(x - a.x).coerceAtLeast(1f), h = abs(y - a.y).coerceAtLeast(1f), rotation = 0f
                    ))
                }
            }
            MaskDrag.MOVE -> s.maskShapeDraft?.let { d ->
                _state.value = s.copy(maskShapeDraft = d.copy(cx = d.cx + x - last.x, cy = d.cy + y - last.y))
            }
            MaskDrag.CORNER -> s.maskShapeDraft?.let { d ->
                val l = MaskGeometry.toLocal(d, x, y)
                _state.value = s.copy(maskShapeDraft = d.copy(w = (abs(l.x) * 2f).coerceAtLeast(8f), h = (abs(l.y) * 2f).coerceAtLeast(8f)))
            }
            MaskDrag.ROTATE -> s.maskShapeDraft?.let { d ->
                val ang = Math.toDegrees(atan2((y - d.cy).toDouble(), (x - d.cx).toDouble())).toFloat() + 90f
                _state.value = s.copy(maskShapeDraft = d.copy(rotation = ang))
            }
            MaskDrag.POINT -> {
                val pts = s.maskPolyPoints.toMutableList()
                if (maskDragIndex in pts.indices) {
                    pts[maskDragIndex] = PointF(x, y)
                    _state.value = s.copy(maskPolyPoints = pts)
                }
            }
            MaskDrag.LASSO -> {
                val pts = s.maskPolyPoints
                if (pts.isEmpty() || hypot(pts.last().x - x, pts.last().y - y) > 2f * touchToCanvas) {
                    _state.value = s.copy(maskPolyPoints = pts + PointF(x, y))
                }
            }
            MaskDrag.NONE -> {}
        }
        maskDragLast = PointF(x, y)
        refreshMaskDraftPreview()
    }

    private fun maskPointerUp() {
        val s = _state.value
        if (maskDrag == MaskDrag.LASSO) {
            val simplified = MaskGeometry.simplify(s.maskPolyPoints, 8f * touchToCanvas)
            _state.value = s.copy(maskPolyPoints = if (simplified.size >= 3) simplified else emptyList())
        }
        if (maskDrag == MaskDrag.CREATE) {
            val d = s.maskShapeDraft
            if (d != null && (d.w < 12f * touchToCanvas || d.h < 12f * touchToCanvas)) {
                // tocco senza trascinare: forma di dimensione predefinita centrata nel punto
                val side = (s.activeLayer?.let { min(it.w, it.h) } ?: 400f) * 0.35f
                _state.value = s.copy(maskShapeDraft = d.copy(w = side, h = side))
            }
        }
        maskDrag = MaskDrag.NONE
        maskDragIndex = -1
        maskDragAnchor = null
        maskDragLast = null
        refreshMaskDraftPreview()
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
            "selective_mask" -> {
                if (!maskPointerDown(canvasX, canvasY)) {
                    lastBrushPoint = PointF(canvasX, canvasY)
                    maskStrokeDirty = true
                    paintBrushStroke(canvasX, canvasY, canvasX, canvasY)
                }
            }
            "brush" -> brushDown(canvasX, canvasY)
            "mask" -> {
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
        // la zona selettiva salva la cronologia a fine tratto (vedi onCanvasPointerUp)
        if (!strokeHasPushedHistory && s.currentTool in listOf("mask", "warp", "clone")) {
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
            "selective_mask" -> {
                if (maskDrag != MaskDrag.NONE) {
                    maskPointerMove(canvasX, canvasY)
                } else if (s.maskTool != MaskTool.WAND) {
                    val prev = lastBrushPoint ?: PointF(canvasX, canvasY)
                    paintBrushStroke(prev.x, prev.y, canvasX, canvasY)
                    lastBrushPoint = PointF(canvasX, canvasY)
                    maskStrokeDirty = true
                }
            }
            "brush" -> brushMove(canvasX, canvasY)
            "mask" -> {
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
        if (s.currentTool == "brush" && !s.eyedropperActive) { brushUp(); return }
        if (s.currentTool == "selective_mask") {
            if (maskDrag != MaskDrag.NONE) maskPointerUp()
            if (maskStrokeDirty) { maskStrokeDirty = false; pushHistorySnapshot() }
        }
        if (s.currentTool in listOf("brush", "mask", "selective_mask", "warp", "clone", "pen")) {
            rebuildCompositeImmediate()
        }
    }

    // ============ PENNELLI A COLORE (BrushEngine) ============
    private var brushStroke: com.example.engine.BrushEngine.Stroke? = null
    private var brushLayerId: String? = null

    /**
     * Livello su cui dipingere. Pennelli a colore: un livello "Pennello" con il modo colore scelto
     * (si riusa quello attivo o il più alto con lo stesso modo, altrimenti se ne crea uno). Gomma e sfumino
     * lavorano sul livello attivo, qualunque sia.
     */
    private fun brushTargetLayer(): EditorLayer? {
        val s = _state.value
        val kind = s.brushKind
        if (kind == com.example.engine.BrushEngine.Kind.ERASER || kind == com.example.engine.BrushEngine.Kind.SMUDGE) {
            val a = s.activeLayer ?: return null
            if (a.locked) { showToast("Il livello è bloccato"); return null }
            ensureActiveLayerRasterized()
            return _state.value.activeLayer
        }
        val a = s.activeLayer
        if (a != null && a.isPaintLayer && a.blend == s.brushBlend && !a.locked && a.visible) return a
        s.layers.lastOrNull { it.isPaintLayer && it.blend == s.brushBlend && !it.locked && it.visible }?.let {
            _state.value = s.copy(activeLayerId = it.id)
            return it
        }
        val name = if (s.brushBlend == BlendOption.NORMAL) "Pennello" else "Pennello · ${s.brushBlend.label}"
        val layer = EditorLayer(
            id = "paint_${System.currentTimeMillis()}",
            type = LayerType.RASTER,
            name = name,
            w = s.canvasW.toFloat(), h = s.canvasH.toFloat(),
            blend = s.brushBlend,
            isPaintLayer = true,
            bitmap = Bitmap.createBitmap(s.canvasW.coerceAtLeast(1), s.canvasH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        )
        _state.value = s.copy(layers = s.layers + layer, activeLayerId = layer.id)
        return layer
    }

    private fun brushMap(l: EditorLayer, bmp: Bitmap, x: Float, y: Float) =
        com.example.engine.BrushGeometry.toBitmap(x, y, l.x, l.y, l.w, l.h, l.rotation, bmp.width, bmp.height)

    private fun brushDown(x: Float, y: Float) {
        val s = _state.value
        val layer = brushTargetLayer() ?: return
        var bmp = layer.bitmap ?: return
        if (!bmp.isMutable) {
            bmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
            updateActiveLayer { it.copy(bitmap = bmp) }
        }
        val m = brushMap(layer, bmp, x, y)
        val k = m.pxPerUnit
        val params = com.example.engine.BrushEngine.Params(
            size = (s.brushSize * k).coerceAtLeast(1f),
            opacity = s.brushOpacity,
            flow = s.brushFlow,
            hardness = s.brushHardness,
            spacing = s.brushSpacing,
            scatter = s.brushScatter,
            smoothing = s.brushSmoothing,
            color = s.brushColor
        )
        brushLayerId = layer.id
        brushStroke = com.example.engine.BrushEngine.Stroke(bmp, s.brushKind, params, seed = (System.nanoTime() and 0xFFFF).toInt()).also {
            it.addPoint(m.x, m.y)
            it.render()
        }
        interactiveEffectChange()
        scheduleCompositeRebuild()
    }

    private fun brushMove(x: Float, y: Float) {
        val stroke = brushStroke ?: return
        val layer = _state.value.layers.firstOrNull { it.id == brushLayerId } ?: return
        val bmp = layer.bitmap ?: return
        val m = brushMap(layer, bmp, x, y)
        stroke.addPoint(m.x, m.y)
        stroke.render()
        interactiveEffectChange()
        scheduleCompositeRebuild()
    }

    private fun brushUp() {
        val stroke = brushStroke ?: return
        brushStroke = null
        stroke.finish()
        val id = brushLayerId ?: return
        brushLayerId = null
        val s = _state.value
        // nuova istanza del bitmap: la cronologia e le miniature vedono il tratto concluso
        _state.value = s.copy(
            layers = s.layers.map { if (it.id == id) it.copy(bitmap = it.bitmap?.copy(Bitmap.Config.ARGB_8888, true)) else it },
            recentColors = if (stroke.kind == com.example.engine.BrushEngine.Kind.ERASER || stroke.kind == com.example.engine.BrushEngine.Kind.SMUDGE) s.recentColors
                else (listOf(s.brushColor) + s.recentColors.filter { it != s.brushColor }).take(10)
        )
        pushHistorySnapshot()
        scheduleCompositeRebuild()
    }

    /** Impostazioni dei pennelli a colore. Scegliendo un tipo si caricano i suoi valori consigliati. */
    fun updatePaintBrush(
        kind: com.example.engine.BrushEngine.Kind? = null,
        size: Float? = null, opacity: Float? = null, flow: Float? = null, hardness: Float? = null,
        spacing: Float? = null, scatter: Float? = null, smoothing: Float? = null,
        blend: BlendOption? = null, color: Int? = null
    ) {
        var s = _state.value
        if (kind != null && kind != s.brushKind) {
            val p = kind.preset
            s = s.copy(brushKind = kind, brushSize = p.size, brushOpacity = p.opacity, brushFlow = p.flow,
                brushHardness = p.hardness, brushSpacing = p.spacing, brushScatter = p.scatter)
        }
        _state.value = s.copy(
            brushSize = size ?: s.brushSize,
            brushOpacity = opacity ?: s.brushOpacity,
            brushFlow = flow ?: s.brushFlow,
            brushHardness = hardness ?: s.brushHardness,
            brushSpacing = spacing ?: s.brushSpacing,
            brushScatter = scatter ?: s.brushScatter,
            brushSmoothing = smoothing ?: s.brushSmoothing,
            brushBlend = blend ?: s.brushBlend,
            brushColor = color ?: s.brushColor
        )
    }

    /** Contagocce dei pennelli: il colore toccato sulla foto diventa quello del pennello. */
    fun pickBrushColorFromCanvas() {
        _state.value = _state.value.copy(
            eyedropperActive = true,
            colorPickerTarget = "brush",
            colorPickerOpen = false,
            toastMessage = "Tocca la foto per prendere il colore"
        )
    }

    // ============ PACCHETTI IA (scaricati al primo uso) ============
    private var packAction: ((java.io.File) -> Unit)? = null
    @Volatile private var packCancel = false

    /** Esegue [action] con il file del modello; se non c'è ancora, chiede prima il permesso di scaricarlo. */
    fun requirePack(id: String, action: (java.io.File) -> Unit) {
        val f = com.example.engine.AiPacks.file(id)
        if (f != null) { action(f); return }
        if (com.example.engine.AiPacks.entry(id) == null) { showToast("Funzione non disponibile in questa versione"); return }
        packAction = action
        _state.value = _state.value.copy(aiPackPrompt = id)
    }

    fun acceptPack() {
        val id = _state.value.aiPackPrompt ?: return
        val action = packAction
        packCancel = false
        _state.value = _state.value.copy(aiPackPrompt = null, aiPackProgress = 0f)
        viewModelScope.launch(Dispatchers.IO) {
            var lastPct = -1
            val res = runCatching {
                com.example.engine.AiPacks.download(id, progress = { d, t ->
                    val pct = if (t > 0) (d * 100 / t).toInt() else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        _state.value = _state.value.copy(aiPackProgress = pct / 100f)
                    }
                }, cancelled = { packCancel })
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(aiPackProgress = null)
                res.onSuccess { f -> packAction = null; action?.invoke(f) }
                    .onFailure {
                        packAction = null
                        if (it is com.example.engine.ModelStore.CancelledException) showToast("Download sospeso: riprenderà da dove si è fermato")
                        else showToast("Download non riuscito: ${it.message ?: "controlla la connessione"}")
                    }
            }
        }
    }

    fun dismissPack() { packAction = null; _state.value = _state.value.copy(aiPackPrompt = null) }
    fun cancelPackDownload() { packCancel = true }

    // ============ CANCELLA OGGETTI (LaMa) ============
    /** Apre Cancella oggetti: si dipinge (o si cerchia) ciò che va tolto, in rosso sulla foto. */
    fun openEraseStudio() {
        val s0 = _state.value
        val a = s0.activeLayer?.takeIf { it.type == LayerType.IMAGE || it.type == LayerType.RASTER }
            ?: s0.layers.firstOrNull { (it.type == LayerType.IMAGE || it.type == LayerType.RASTER) && it.bitmap != null }
            ?: run { showToast("Apri prima una foto"); return }
        if (a.id != s0.activeLayerId) _state.value = s0.copy(activeLayerId = a.id)
        ensureActiveLayerRasterized()
        openSelectivePaintMode(ERASE_KEY, initBlackMask = true)
        _state.value = _state.value.copy(
            eraseStudio = true,
            returnToolAfterSelective = null,
            maskTool = MaskTool.BRUSH,
            maskSubtract = false,
            showMaskHeatmap = true, // la zona da togliere si vede in rosso
            brushSize = 40f, brushHardness = 70f, brushOpacity = 100f,
            toastMessage = "Dipingi su ciò che vuoi togliere, poi tocca Cancella"
        )
        // se il modello c'è già, si prepara mentre l'utente segna la zona: al tocco di Cancella resta solo il calcolo
        com.example.engine.AiPacks.file("lama")?.let { f ->
            viewModelScope.launch(Dispatchers.Default) { runCatching { com.example.engine.AiSegmenter.prepare(f, LAMA_SESSION) } }
        }
    }

    /** Cancella la zona segnata: LaMa ricostruisce lo sfondo (pacchetto scaricato al primo uso). */
    fun eraseNow() {
        commitMaskDraft()
        val s = _state.value
        if (s.eraseBusy) return
        val layer = s.activeLayer ?: return
        val mask = layer.effects[ERASE_KEY]?.localMask ?: return
        requirePack("lama") { file -> runErase(file, layer.id, mask) }
    }

    private fun runErase(file: java.io.File, layerId: String, maskBmp: Bitmap) {
        val layer = _state.value.layers.firstOrNull { it.id == layerId } ?: return
        val bmp = layer.bitmap ?: return
        _state.value = _state.value.copy(eraseBusy = true)
        viewModelScope.launch(Dispatchers.Default) {
            val res = runCatching {
                val w = bmp.width; val h = bmp.height
                val m = if (maskBmp.width == w && maskBmp.height == h) maskBmp else Bitmap.createScaledBitmap(maskBmp, w, h, true)
                val alpha = maskToAlpha(m)
                val crop = com.example.engine.InpaintEngine.crop(alpha, w, h) ?: return@runCatching null
                val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
                val (image, maskIn) = com.example.engine.InpaintEngine.inputs(px, alpha, w, crop)
                val side = com.example.engine.InpaintEngine.SIDE.toLong()
                val out = com.example.engine.AiSegmenter.runFileInputs(file, listOf(
                    "image" to (image to longArrayOf(1, 3, side, side)),
                    "mask" to (maskIn to longArrayOf(1, 1, side, side))
                ), LAMA_SESSION)
                val newPx = com.example.engine.InpaintEngine.blend(px, alpha, w, h, crop, out)
                Bitmap.createBitmap(newPx, w, h, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(eraseBusy = false)
                res.onSuccess { done ->
                    if (done == null) { showToast("Dipingi prima su ciò che vuoi togliere"); return@onSuccess }
                    val lw = layer.w.toInt().coerceAtLeast(4); val lh = layer.h.toInt().coerceAtLeast(4)
                    val empty = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
                    _state.value = _state.value.copy(layers = _state.value.layers.map {
                        if (it.id == layerId) it.copy(bitmap = done, effects = it.effects + (ERASE_KEY to (it.effects[ERASE_KEY] ?: EffectConfig()).copy(localMask = empty))) else it
                    })
                    maskOnEnter = empty
                    pushHistorySnapshot()
                    scheduleCompositeRebuild()
                    showToast("Fatto: segna altro da togliere oppure conferma")
                }.onFailure { showToast("Cancellazione non riuscita: ${it.message ?: "errore del modello"}") }
            }
        }
    }

    // ============ COLORIZZA (DDColor) ============
    private var colorizeAb: FloatArray? = null
    private var colorizeDirty = false
    private var colorizeSourceId: String? = null

    /** Colora automaticamente il livello foto attivo: il risultato è un livello "Colore (IA)" con modo Colore. */
    fun startColorize() {
        val s = _state.value
        val src = s.activeLayer?.takeIf { it.bitmap != null && !it.isPaintLayer && (it.type == LayerType.IMAGE || it.type == LayerType.RASTER) }
            ?: s.layers.firstOrNull { it.bitmap != null && (it.type == LayerType.IMAGE || it.type == LayerType.RASTER) && !it.isPaintLayer }
            ?: run { showToast("Apri prima una foto"); return }
        colorizeSourceId = src.id
        requirePack("ddcolor_tiny") { file -> runColorize(file, recompute = true) }
    }

    private fun runColorize(file: java.io.File?, recompute: Boolean) {
        val s = _state.value
        val src = s.layers.firstOrNull { it.id == colorizeSourceId } ?: return
        val bmp0 = src.bitmap ?: return
        if (s.colorizeBusy) { colorizeDirty = true; return }
        _state.value = s.copy(colorizeBusy = true)
        val look = com.example.engine.ColorizeEngine.Look(
            saturation = s.colorizeSaturation / 100f, warmth = s.colorizeWarmth / 100f, hue = s.colorizeHue,
            depth = s.colorizeDepth / 100f, edges = s.colorizeEdges / 100f
        )
        colorizeDirty = false
        viewModelScope.launch(Dispatchers.Default) {
            val res = runCatching {
                // si lavora al massimo a 2048 px: il colore stimato è a 512 px, la luminosità resta quella vera
                val k = minOf(1f, 2048f / maxOf(bmp0.width, bmp0.height))
                val bmp = if (k < 1f) Bitmap.createScaledBitmap(bmp0, (bmp0.width * k).roundToInt(), (bmp0.height * k).roundToInt(), true) else bmp0
                val w = bmp.width; val h = bmp.height
                val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
                val ab = if (recompute || colorizeAb == null) {
                    val input = com.example.engine.ColorizeEngine.modelInput(com.example.engine.ColorizeEngine.lightness(px), w, h)
                    com.example.engine.AiSegmenter.runFile(file ?: com.example.engine.AiPacks.file("ddcolor_tiny")!!, input,
                        com.example.engine.ColorizeEngine.SIDE, com.example.engine.ColorizeEngine.SIDE).also { colorizeAb = it }
                } else colorizeAb!!
                val out = com.example.engine.ColorizeEngine.compose(px, w, h, ab, look)
                Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(colorizeBusy = false)
                res.onSuccess { colored -> placeColorLayer(src, colored) }
                    .onFailure { showToast("Colorizzazione non riuscita: ${it.message ?: "errore del modello"}") }
                // un cursore mosso durante il calcolo: si riapplica con l'ultimo valore
                if (colorizeDirty) runColorize(null, recompute = false)
            }
        }
    }

    /** Il colore va su un livello sopra la foto, con modo "Colore": la foto resta intatta, l'intensità è l'opacità. */
    private fun placeColorLayer(src: EditorLayer, colored: Bitmap) {
        val s = _state.value
        val existing = s.layers.firstOrNull { it.id == s.colorizeLayerId }
        val layers = if (existing != null) {
            s.layers.map { if (it.id == existing.id) it.copy(bitmap = colored, x = src.x, y = src.y, w = src.w, h = src.h, rotation = src.rotation) else it }
        } else {
            val layer = EditorLayer(
                id = "colorize_${System.currentTimeMillis()}", type = LayerType.RASTER, name = "Colore (IA)",
                x = src.x, y = src.y, w = src.w, h = src.h, rotation = src.rotation,
                blend = BlendOption.NORMAL, bitmap = colored
            )
            val idx = s.layers.indexOfFirst { it.id == src.id }
            s.layers.toMutableList().apply { add(idx + 1, layer) }.also { _state.value = s.copy(colorizeLayerId = layer.id) }
        }
        _state.value = _state.value.copy(layers = layers, handlesHidden = true)
        scheduleCompositeRebuild()
    }

    fun setColorizeIntensity(v: Float) {
        val id = _state.value.colorizeLayerId ?: return
        interactiveEffectChange()
        _state.value = _state.value.copy(layers = _state.value.layers.map { if (it.id == id) it.copy(opacity = (v / 100f).coerceIn(0f, 1f)) else it })
        scheduleCompositeRebuild()
    }

    fun setColorizeSaturation(v: Float) = setColorizeLook(saturation = v)

    /** Regolazioni del colore (vivacità, calore, tinta, profondità, bordi): istantanee, senza ricalcolare l'IA. */
    fun setColorizeLook(saturation: Float? = null, warmth: Float? = null, hue: Float? = null, depth: Float? = null, edges: Float? = null) {
        val s = _state.value
        _state.value = s.copy(
            colorizeSaturation = saturation ?: s.colorizeSaturation, colorizeWarmth = warmth ?: s.colorizeWarmth,
            colorizeHue = hue ?: s.colorizeHue, colorizeDepth = depth ?: s.colorizeDepth, colorizeEdges = edges ?: s.colorizeEdges
        )
        if (colorizeAb != null) runColorize(null, recompute = false)
    }

    /** Correggi una zona: Pennelli in modo "Colore" (ridipinge il colore mantenendo luci e ombre). */
    fun openColorFixBrush() {
        closeCurrentTool(confirm = true)
        updatePaintBrush(kind = com.example.engine.BrushEngine.Kind.AIRBRUSH, blend = BlendOption.COLOR)
        updatePaintBrush(flow = 35f, size = 60f)
        openTool("brush")
        showToast("Scegli il colore giusto e dipingi la zona sbagliata")
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
                (selectiveMaskOf(currentLayer, s.selectiveTargetFxKey) ?: Bitmap.createBitmap(
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
            isSelectiveFx -> setSelectiveMask(s.selectiveTargetFxKey, targetBmp)
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
        val bmp = fullComposite()
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
            "brush" -> _state.value = s.copy(brushColor = color, eyedropperActive = false)
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
        val composite = fullComposite()
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

    // ============ ESPORTA: DIVIDI E SALVA ============
    fun updateSplit(transform: (com.example.engine.SplitGeometry.Settings) -> com.example.engine.SplitGeometry.Settings) {
        _state.value = _state.value.copy(split = transform(_state.value.split))
    }

    fun setSplitJpg(jpg: Boolean) {
        _state.value = _state.value.copy(splitJpg = jpg)
    }

    fun splitRects(): List<android.graphics.Rect> {
        val s = _state.value
        return com.example.engine.SplitGeometry.rects(s.canvasW, s.canvasH, s.split)
    }

    /** ✓ dello strumento Dividi: salva ogni pannello come file separato. */
    fun exportSplit(context: Context) {
        val s = _state.value
        val composite = fullComposite()
        val rects = splitRects()
        if (rects.isEmpty()) { showToast("Nessun pannello da salvare"); return }
        val format = if (s.splitJpg) ExportFormat.JPG else ExportFormat.PNG
        viewModelScope.launch(Dispatchers.IO) {
            val res = ExportEngine.exportPieces(context, composite, rects, format, 92)
            withContext(Dispatchers.Main) {
                res.onSuccess { n ->
                    showToast("Salvati $n file in Immagini/Zesty")
                    _state.value = _state.value.copy(currentTool = null)
                }.onFailure { showToast("Salvataggio non riuscito") }
            }
        }
    }

    fun shareImage(context: Context) {
        val bmp = fullComposite()
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
