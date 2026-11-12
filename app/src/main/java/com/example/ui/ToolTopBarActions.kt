package com.example.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import com.example.model.BrushStyle
import com.example.model.MaskTool
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel

/**
 * Azioni contestuali della barra superiore (docs/UI_PRINCIPLES.md §2):
 * al massimo 2 icone, il resto nel menu ⋮. Chiudi/Annulla/Ripeti/Conferma sono fissi.
 */
@Composable
fun RowScope.ToolTopBarActions(state: EditorUiState, vm: EditorViewModel) {
    when (state.currentTool) {
        "effects" -> {
            val active = state.activeLayer
            val hasFilter = active?.activeFilterId != null
            val hasZone = active?.activeFilterMask != null
            PxIconAction(
                icon = Icons.Default.HighlightAlt,
                description = if (hasZone) "Modifica la zona del filtro" else "Applica il filtro a una zona",
                active = hasZone,
                enabled = hasFilter
            ) { vm.requestLocalizeFx(EditorViewModel.FILTER_FX_KEY) }
            if (hasFilter) {
                PxOverflowMenu(buildList {
                    add(PxMenuItem("Ripristina parametri", Icons.Default.RestartAlt) { vm.resetEffectParams() })
                    if (hasZone) add(PxMenuItem("Applica a tutta la foto", Icons.Default.CropFree) { vm.removeEffectLocalization(EditorViewModel.FILTER_FX_KEY) })
                    add(PxMenuItem("Rendi definitivo", Icons.Default.Done) { vm.applyCurrentEffectPermanently() })
                    add(PxMenuItem("Rimuovi filtro", Icons.Default.DeleteOutline) { vm.cancelCurrentEffect() })
                })
            }
        }
        "selective_mask" -> {
            val cut = state.cutoutStudio
            PxIconAction(
                icon = when {
                    cut && state.maskSubtract -> CutIcons.ModeRemove
                    cut -> CutIcons.ModeRestore
                    state.maskSubtract -> Icons.Default.RemoveCircleOutline
                    else -> Icons.Default.AddCircleOutline
                },
                description = when {
                    cut && state.maskSubtract -> "Modalità togli (tocca per rimettere)"
                    cut -> "Modalità rimetti (tocca per togliere)"
                    state.maskSubtract -> "Modalità togli zona (tocca per aggiungere)"
                    else -> "Modalità aggiungi zona (tocca per togliere)"
                },
                active = state.maskSubtract
            ) { vm.setMaskSubtract(!state.maskSubtract) }
            if (cut) {
                PxIconAction(
                    icon = Icons.Default.Wallpaper,
                    description = "Sfondo di prova: " + listOf("scacchi", "scuro", "bianco", "lime")[state.cutoutBackdrop],
                    active = state.cutoutBackdrop != 0
                ) { vm.setCutoutBackdrop(state.cutoutBackdrop + 1) }
            } else {
                PxIconAction(
                    icon = if (state.showMaskHeatmap) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    description = "Evidenzia la zona",
                    active = state.showMaskHeatmap
                ) { vm.toggleSelectiveHeatmap() }
            }
            PxOverflowMenu(buildList {
                if (cut) {
                    add(PxMenuItem("Evidenzia la parte tolta", Icons.Outlined.Visibility, checked = state.showMaskHeatmap) { vm.toggleSelectiveHeatmap() })
                    add(PxMenuItem("Ritaglia al soggetto", Icons.Default.CropFree) { vm.cutoutTrimToSubject() })
                }
                if (state.maskTool == MaskTool.POLYGON && state.maskPolyPoints.isNotEmpty()) {
                    add(PxMenuItem("Togli ultimo punto", Icons.Default.Backspace) { vm.removeLastMaskPoint() })
                }
                if (state.maskShapeDraft != null || state.maskPolyPoints.isNotEmpty()) {
                    add(PxMenuItem("Scarta contorno", Icons.Default.Clear) { vm.clearMaskDraft() })
                }
                add(PxMenuItem("Inverti zona", Icons.Default.Flip, checked = vm.isSelectiveInverted()) { vm.invertSelectiveMask() })
                add(PxMenuItem("Sfuma i bordi", Icons.Default.BlurOn) { vm.featherWholeMask(8f) })
                add(PxMenuItem(if (cut) "Rimetti tutto" else "Tutta la foto", Icons.Default.SelectAll) { vm.fillSelectiveMask(true) })
                add(PxMenuItem(if (cut) "Togli tutto" else "Svuota zona", Icons.Default.Deselect) { vm.fillSelectiveMask(false) })
            })
        }
        "brush" -> {
            val erasing = state.brushKind == com.example.engine.BrushEngine.Kind.ERASER
            PxIconAction(Icons.Default.AutoFixOff, if (erasing) "Gomma attiva (tocca per tornare al pennello)" else "Gomma", active = erasing) {
                vm.updatePaintBrush(kind = if (erasing) com.example.engine.BrushEngine.Kind.ROUND else com.example.engine.BrushEngine.Kind.ERASER)
            }
        }
        "mask" -> {
            val erasing = state.brushStyle == BrushStyle.ERASER
            PxIconAction(Icons.Default.AutoFixOff, if (erasing) "Gomma attiva" else "Gomma", active = erasing) {
                vm.updateBrushSettings(style = if (erasing) BrushStyle.STANDARD else BrushStyle.ERASER)
            }
        }
    }
}
