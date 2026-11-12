package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.engine.BrushEngine.Kind
import com.example.model.BlendOption
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel

/** Icona di ogni tipo di pennello. */
private fun Kind.icon(): ImageVector = when (this) {
    Kind.ROUND -> Icons.Default.Brush
    Kind.PENCIL -> Icons.Default.Create
    Kind.MARKER -> Icons.Default.Highlight
    Kind.AIRBRUSH -> Icons.Default.Air
    Kind.WATERCOLOR -> Icons.Default.WaterDrop
    Kind.CHALK -> Icons.Default.Gesture
    Kind.NEON -> Icons.Default.FlashOn
    Kind.SPRAY -> Icons.Default.Grain
    Kind.GLITTER -> Icons.Default.AutoAwesome
    Kind.SMUDGE -> Icons.Default.TouchApp
    Kind.ERASER -> Icons.Default.CleaningServices
}

/** Modi colore offerti per i pennelli, con cosa fanno davvero. */
private val paintBlends = listOf(
    BlendOption.NORMAL to "Copre: colore pieno sopra la foto",
    BlendOption.COLOR to "Colora la foto mantenendo luci, ombre e dettagli (ideale per il bianco e nero)",
    BlendOption.MULTIPLY to "Scurisce come un pennarello trasparente",
    BlendOption.OVERLAY to "Tinge e aumenta il contrasto",
    BlendOption.SOFT_LIGHT to "Tinge in modo delicato",
    BlendOption.SCREEN to "Schiarisce, come una luce colorata",
    BlendOption.HUE to "Cambia solo la tinta, non la saturazione"
)

/** Tavolozza rapida: colori base e qualche tono pelle. */
private val quickPalette = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF7A7A7A, 0xFFE53935, 0xFFFF8F00, 0xFFFFD600, 0xFF7CB342,
    0xFF00897B, 0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41,
    0xFFF1C9A5, 0xFFD9A07B, 0xFFA86B4A, 0xFF6B3E26
).map { it.toInt() }

@Composable
private fun Swatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) Px.Active else TextMute, CircleShape)
            .clickable(onClick = onClick)
    )
}

/**
 * Pannello dei pennelli a colore. Tre pagine (icone): Pennello · Colore · Regolazioni.
 * I tratti a colore vanno su un livello "Pennello" (la foto resta intatta); gomma e sfumino sul livello attivo.
 */
@Composable
fun PaintBrushPanel(state: EditorUiState, vm: EditorViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 6.dp)) {
        PxSegmented(
            options = listOf(
                PxOption("Tipo di pennello", Icons.Default.Brush),
                PxOption("Colore e modo colore", Icons.Default.Palette),
                PxOption("Regolazioni del tratto", Icons.Default.Gesture)
            ),
            selected = page,
            onSelect = { page = it }
        )
        when (page) {
            0 -> {
                PxCollectionRow {
                    Kind.entries.forEach { k ->
                        PxChip(k.label, selected = state.brushKind == k, icon = k.icon()) { vm.updatePaintBrush(kind = k) }
                    }
                }
                PxSlider("Dimensione", state.brushSize, 1f..300f, { vm.updatePaintBrush(size = it) }, "px")
                PxSlider("Opacità", state.brushOpacity, 1f..100f, { vm.updatePaintBrush(opacity = it) }, "%")
                PxHint(
                    when (state.brushKind) {
                        Kind.ERASER -> "Cancella dal livello selezionato"
                        Kind.SMUDGE -> "Trascina i colori del livello selezionato, come con un dito"
                        else -> "I tratti vanno su un livello \"Pennello\": la foto resta intatta"
                    }
                )
            }
            1 -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Px.Gutter, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(state.brushColor))
                            .border(2.dp, TextWhite, CircleShape).clickable { vm.openColorPicker("brush") }
                    )
                    PxIconAction(Icons.Default.Palette, "Scegli il colore (ruota, RGB, HEX)") { vm.openColorPicker("brush") }
                    PxIconAction(Icons.Default.Colorize, "Prendi il colore dalla foto", active = state.eyedropperActive) { vm.pickBrushColorFromCanvas() }
                    // colori usati di recente
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.recentColors.forEach { c -> Swatch(c, c == state.brushColor) { vm.updatePaintBrush(color = c) } }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Px.Gutter, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPalette.forEach { c -> Swatch(c, c == state.brushColor) { vm.updatePaintBrush(color = c) } }
                }
                PxCollectionRow {
                    paintBlends.forEach { (b, _) ->
                        PxChip(b.label, selected = state.brushBlend == b) { vm.updatePaintBrush(blend = b) }
                    }
                }
                PxHint(paintBlends.firstOrNull { it.first == state.brushBlend }?.second ?: "")
            }
            else -> {
                PxSlider("Flusso", state.brushFlow, 1f..100f, { vm.updatePaintBrush(flow = it) }, "%")
                PxSlider("Durezza", state.brushHardness, 0f..100f, { vm.updatePaintBrush(hardness = it) }, "%")
                PxSlider("Spaziatura", state.brushSpacing, 1f..100f, { vm.updatePaintBrush(spacing = it) }, "%")
                PxSlider("Dispersione", state.brushScatter, 0f..100f, { vm.updatePaintBrush(scatter = it) }, "%")
                PxSlider("Stabilizzatore", state.brushSmoothing, 0f..95f, { vm.updatePaintBrush(smoothing = it) }, "%")
                PxHint("Flusso basso: il colore si accumula ripassando. Stabilizzatore: linee più morbide")
            }
        }
    }
}
