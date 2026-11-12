package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.AiPacks
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import kotlin.math.roundToInt

/**
 * Richiesta di download di un pacchetto IA (peso, licenza, avviso rete a consumo) e avanzamento.
 * Niente parte senza il consenso: il download avviene una volta sola, poi funziona senza rete.
 */
@Composable
fun AiPackDialog(state: EditorUiState, vm: EditorViewModel) {
    val promptId = state.aiPackPrompt
    val progress = state.aiPackProgress
    if (promptId == null && progress == null) return
    Dialog(onDismissRequest = { if (progress == null) vm.dismissPack() }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Px.SurfaceHi).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (progress != null) {
                Text("Scaricamento del modello…", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Px.Accent, trackColor = DividerCol
                )
                Text("${(progress * 100).roundToInt()}% · si riprende da qui se lo interrompi", color = TextDim, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.cancelPackDownload() }) { Text("Interrompi", color = TextDim) }
                }
            } else {
                val e = AiPacks.entry(promptId!!) ?: return@Column
                Text(e.title, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Per ${e.purpose.lowercase()} serve un modello di intelligenza artificiale da ${e.megabytes} MB. " +
                        "Si scarica una sola volta, poi funziona anche senza rete. Le tue foto non lasciano mai il telefono.",
                    color = TextDim, fontSize = 13.sp
                )
                Text("Licenza ${e.license} · ${e.source.removePrefix("https://")}", color = TextMute, fontSize = 11.sp)
                if (AiPacks.isMetered()) {
                    Text("Sei su rete dati mobile: il download consumerà circa ${e.megabytes} MB del tuo piano.", color = Px.Active, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.dismissPack() }) { Text("Non ora", color = TextDim) }
                    TextButton(onClick = { vm.acceptPack() }) { Text("Scarica", color = Px.Accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Colorizza: colore automatico (DDColor) su un livello "Colore (IA)"; intensità e saturazione regolabili. */
@Composable
fun ColorizePanel(state: EditorUiState, vm: EditorViewModel) {
    Column(Modifier.fillMaxWidth().background(BgBlack).padding(vertical = 6.dp)) {
        if (state.colorizeBusy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Px.Gutter, vertical = 4.dp).height(2.dp),
                color = Px.Accent, trackColor = DividerCol
            )
            PxHint("L'IA sta stimando i colori della foto…")
        }
        val layer = state.layers.firstOrNull { it.id == state.colorizeLayerId }
        if (layer != null) {
            PxSlider("Intensità", layer.opacity * 100f, 0f..100f, { vm.setColorizeIntensity(it) }, "%")
            PxSlider("Vivacità", state.colorizeSaturation, 0f..200f, { vm.setColorizeLook(saturation = it) }, "%")
            PxSlider("Profondità", state.colorizeDepth, 0f..100f, { vm.setColorizeLook(depth = it) }, "%")
            PxSlider("Calore", state.colorizeWarmth, -100f..100f, { vm.setColorizeLook(warmth = it) })
            PxSlider("Tinta", state.colorizeHue, -60f..60f, { vm.setColorizeLook(hue = it) }, "°")
            PxSlider("Precisione bordi", state.colorizeEdges, 0f..100f, { vm.setColorizeLook(edges = it) }, "%")
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PxIconAction(Icons.Default.Refresh, "Ricalcola i colori") { vm.startColorize() }
                PxIconAction(Icons.Default.Brush, "Correggi una zona con il pennello in modo Colore") { vm.openColorFixBrush() }
                Text(
                    "Un colore sbagliato? Tinta per tutta la foto, pennello per una zona",
                    color = TextMute, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp)
                )
            }
        } else if (!state.colorizeBusy) {
            PxHint("Tocca ↻ per colorare la foto selezionata")
            Row(Modifier.padding(horizontal = 12.dp)) { PxIconAction(Icons.Default.Refresh, "Colora la foto") { vm.startColorize() } }
        }
        PxHint("I colori sono una stima dell'IA, non quelli originali della foto")
    }
}
