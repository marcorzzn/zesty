package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import kotlin.math.roundToInt

/** Proporzioni pronte per i pannelli (social e stampa) più la scelta libera. */
private val SPLIT_RATIOS = listOf(
    "1:1" to (1f to 1f), "4:5" to (4f to 5f), "3:4" to (3f to 4f), "2:3" to (2f to 3f),
    "9:16" to (9f to 16f), "16:9" to (16f to 9f), "3:2" to (3f to 2f)
)

/**
 * Esporta → Dividi e salva. Anteprima dei pannelli sulla tela, ✓ salva ogni pannello come file.
 * Griglia (colonne × righe) oppure N pannelli di proporzione qualsiasi in fila o in colonna.
 */
@Composable
fun SplitToolPanel(state: EditorUiState, vm: EditorViewModel) {
    val s = state.split
    val count = vm.splitRects().size
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        PxSectionTitle("Dividi e salva · $count file")
        PxSegmented(
            options = listOf(PxOption("Griglia", Icons.Default.GridOn), PxOption("Proporzione", Icons.Default.ViewColumn)),
            selected = s.mode,
            onSelect = { m -> vm.updateSplit { it.copy(mode = m) } }
        )
        if (s.mode == 0) {
            PxSlider("Colonne", s.cols.toFloat(), 1f..20f, { v -> vm.updateSplit { it.copy(cols = v.roundToInt()) } })
            PxSlider("Righe", s.rows.toFloat(), 1f..20f, { v -> vm.updateSplit { it.copy(rows = v.roundToInt()) } })
        } else {
            PxCollectionRow {
                SPLIT_RATIOS.forEach { (label, r) ->
                    PxChip(label, selected = s.ratioW == r.first && s.ratioH == r.second) {
                        vm.updateSplit { it.copy(ratioW = r.first, ratioH = r.second) }
                    }
                }
            }
            // proporzione libera: due valori qualsiasi
            PxSlider("Larghezza", s.ratioW, 1f..21f, { v -> vm.updateSplit { it.copy(ratioW = (v * 2).roundToInt() / 2f) } }, decimals = true)
            PxSlider("Altezza", s.ratioH, 1f..21f, { v -> vm.updateSplit { it.copy(ratioH = (v * 2).roundToInt() / 2f) } }, decimals = true)
            PxSlider("Pannelli", s.count.toFloat(), 1f..20f, { v -> vm.updateSplit { it.copy(count = v.roundToInt()) } })
            PxSegmented(
                options = listOf(PxOption("In fila"), PxOption("In colonna")),
                selected = if (s.vertical) 1 else 0,
                onSelect = { i -> vm.updateSplit { it.copy(vertical = i == 1) } }
            )
            PxSlider("Posizione", s.position * 100f, 0f..100f, { v -> vm.updateSplit { it.copy(position = v / 100f) } }, "%")
        }
        PxSlider("Spazio", s.gap.toFloat(), 0f..60f, { v -> vm.updateSplit { it.copy(gap = v.roundToInt()) } }, "px")
        PxSegmented(
            options = listOf(PxOption("PNG"), PxOption("JPG")),
            selected = if (state.splitJpg) 1 else 0,
            onSelect = { vm.setSplitJpg(it == 1) }
        )
        PxHint("✓ salva ogni pannello come file separato in Immagini/Zesty")
    }
}
