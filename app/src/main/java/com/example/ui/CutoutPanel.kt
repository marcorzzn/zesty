package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import kotlin.math.roundToInt

/**
 * Scontorna: un solo strumento per isolare il soggetto e rendere trasparente lo sfondo.
 * Seleziona (bacchetta, pennello, lazo, poligono, forme) · Automatico (soggetto, sfondo uniforme, chiave colore)
 * · Rifinisci (bordo, espandi/contrai, buchi, isolotti, alone, ritaglio al soggetto).
 * Tutto lavora sulla maschera del livello: ogni passo si vede subito ed è annullabile.
 */
@Composable
fun CutoutStudioPanel(state: EditorUiState, vm: EditorViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        PxSegmented(
            options = listOf(
                PxOption("Seleziona a mano", CutIcons.Select),
                PxOption("Togli lo sfondo in automatico", CutIcons.RemoveBackground),
                PxOption("Rifinisci il bordo", CutIcons.RefineHair)
            ),
            selected = page,
            onSelect = { page = it }
        )
        if (state.cutoutBusy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Px.Gutter, vertical = 2.dp).height(2.dp),
                color = Px.Accent, trackColor = DividerCol
            )
        }
        when (page) {
            0 -> SelectivePaintToolPanel(state, vm)
            1 -> CutoutAutoPage(state, vm)
            else -> CutoutRefinePage(state, vm)
        }
    }
}

@Composable
private fun CutoutAutoPage(state: EditorUiState, vm: EditorViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        PxCollectionRow {
            PxChip("Soggetto", selected = false, icon = CutIcons.AutoSubject) { vm.cutoutAutoSubject() }
            PxChip("Sfondo uniforme", selected = false, icon = CutIcons.UniformBackground) { vm.cutoutRemoveUniformBackground() }
            PxChip("Anche i buchi", selected = state.cutoutHoles, icon = CutIcons.InnerHoles) { vm.updateCutoutSettings(holes = !state.cutoutHoles) }
        }
        PxHint("Soggetto: IA sul telefono per persone, animali e oggetti. Togli prima il superfluo per più dettaglio")
        PxCollectionRow {
            PxChip("Bianco", selected = false, swatch = Color.White) { vm.cutoutRemoveColor(0xFFFFFF) }
            PxChip("Nero", selected = false, swatch = Color.Black) { vm.cutoutRemoveColor(0x000000) }
            PxChip("Verde", selected = false, swatch = Color(0xFF00B140)) { vm.cutoutRemoveColor(0x00B140) }
            PxChip("Blu", selected = false, swatch = Color(0xFF0047BB)) { vm.cutoutRemoveColor(0x0047BB) }
        }
        PxSlider("Tolleranza", state.wandTolerance, 2f..60f, { vm.updateWandSettings(tolerance = it) })
        PxSlider("Bordo morbido", state.wandSoftness, 0f..100f, { vm.updateWandSettings(softness = it) }, "%")
    }
}

@Composable
private fun CutoutRefinePage(state: EditorUiState, vm: EditorViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        PxCollectionRow {
            PxChip("Capelli e bordo", selected = false, icon = CutIcons.RefineHair) { vm.cutoutRefineEdge() }
            PxChip("Togli alone", selected = false, icon = CutIcons.Dehalo) { vm.cutoutDecontaminate() }
            PxChip("Sfuma", selected = false, icon = CutIcons.Feather) { vm.featherWholeMask(3f) }
        }
        PxSlider("Raggio bordo", state.cutoutRefineRadius, 1f..30f, { vm.updateCutoutSettings(refineRadius = it) }, "px")
        PxCollectionRow {
            PxChip("Riempi buchi", selected = false, icon = CutIcons.FillHoles) { vm.cutoutFillHoles() }
            PxChip("Togli isolotti", selected = false, icon = CutIcons.Islands) { vm.cutoutRemoveIslands(onlyLargest = false) }
            PxChip("Solo il soggetto", selected = false, icon = CutIcons.MainSubject) { vm.cutoutRemoveIslands(onlyLargest = true) }
            PxChip("Ritaglia", selected = false, icon = CutIcons.TrimToSubject) { vm.cutoutTrimToSubject() }
        }
        PxCollectionRow {
            PxChip("Espandi / contrai", selected = false, icon = CutIcons.GrowShrink) { vm.cutoutGrowShrink() }
        }
        PxSlider("Espandi", state.cutoutGrow, -20f..20f, { vm.updateCutoutSettings(grow = it.roundToInt().toFloat()) }, "px")
    }
}

/** Impostazioni della bacchetta, condivise da Scontorna e dalle zone dei filtri. */
@Composable
fun WandSettings(state: EditorUiState, vm: EditorViewModel) {
    PxSlider("Tolleranza", state.wandTolerance, 2f..60f, { vm.updateWandSettings(tolerance = it) })
    PxSlider("Bordo morbido", state.wandSoftness, 0f..100f, { vm.updateWandSettings(softness = it) }, "%")
    PxCollectionRow {
        PxChip("Solo l'area toccata", selected = state.wandContiguous, icon = CutIcons.Contiguous) { vm.updateWandSettings(contiguous = !state.wandContiguous) }
        PxChip("Segue le sfumature", selected = state.wandGradual, icon = CutIcons.Gradual) { vm.updateWandSettings(gradual = !state.wandGradual) }
    }
    PxHint(if (state.maskSubtract) "Tocca un colore per toglierlo; più tolleranza = prende più colori simili" else "Tocca un colore per rimetterlo; più tolleranza = prende più colori simili")
}
