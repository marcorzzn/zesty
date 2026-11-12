package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Architettura delle informazioni di Zesty (docs/PIANO_INTEGRAZIONE.md §3):
 * 7 famiglie, ogni strumento in un solo posto. Uguale su telefono, tablet e web; cambia solo la posizione.
 * Solo icone: il nome è nella descrizione accessibile e nel suggerimento a pressione lunga.
 */
data class ToolNavItem(val id: String, val label: String, val icon: ImageVector)

data class ToolFamily(val id: String, val label: String, val icon: ImageVector, val tools: List<ToolNavItem>)

val TOOL_FAMILIES = listOf(
    ToolFamily("regola", "Regola", Icons.Default.Tune, listOf(
        ToolNavItem("adjust", "Regolazioni", Icons.Default.Tune),
        ToolNavItem("curve", "Curve", Icons.Default.ShowChart)
    )),
    ToolFamily("effetti", "Effetti", Icons.Default.AutoAwesome, listOf(
        ToolNavItem("effects", "Filtri ed effetti", Icons.Default.AutoAwesome)
    )),
    ToolFamily("ritaglia", "Ritaglia", Icons.Default.Crop, listOf(
        ToolNavItem("crop", "Ritaglia", Icons.Default.Crop),
        ToolNavItem("rotate", "Ruota e prospettiva", Icons.Default.RotateRight),
        ToolNavItem("resize", "Dimensione tela", Icons.Default.AspectRatio),
        ToolNavItem("fit", "Estendi", Icons.Default.FitScreen),
        ToolNavItem("border", "Cornice", Icons.Default.FilterFrames),
        ToolNavItem("reflect", "Rifletti", Icons.Default.Flip)
    )),
    ToolFamily("scontorna", "Scontorna", Icons.Default.ContentCut, listOf(
        // un solo strumento: bacchetta, lazo, pennello, forme, sfondo automatico e rifinitura
        // (i vecchi "Ritaglio libero", "Ritaglio a forma" e "Maschera" confluiscono qui; il codice resta)
        ToolNavItem("cutout", "Scontorna", Icons.Default.ContentCut)
    )),
    // foto vecchie: colore, riparazioni, toni (Colorizza per primo; gli altri strumenti arrivano a seguire)
    ToolFamily("restauro", "Restauro", Icons.Default.Palette, listOf(
        ToolNavItem("colorize", "Colorizza", Icons.Default.Palette)
    )),
    ToolFamily("ritocca", "Ritocca", Icons.Default.Healing, listOf(
        ToolNavItem("erase", "Cancella oggetti", Icons.Default.AutoFixOff),
        ToolNavItem("clone", "Clona", Icons.Default.Healing),
        ToolNavItem("warp", "Fluidifica", Icons.Default.WaterDrop),
        ToolNavItem("brush", "Pennelli", Icons.Default.Brush),
        ToolNavItem("dispersion", "Dispersione", Icons.Default.Grain)
    )),
    ToolFamily("aggiungi", "Aggiungi", Icons.Default.AddCircleOutline, listOf(
        ToolNavItem("addphoto", "Foto", Icons.Default.AddPhotoAlternate),
        ToolNavItem("text", "Testo", Icons.Default.Title),
        ToolNavItem("shapes", "Forme", Icons.Default.Category),
        ToolNavItem("sticker", "Adesivi", Icons.Default.Star),
        ToolNavItem("callout", "Fumetti", Icons.Default.ChatBubbleOutline),
        ToolNavItem("pen", "Penna e booleane", Icons.Default.Polyline),
        ToolNavItem("collage", "Collage", Icons.Default.GridView)
    )),
    ToolFamily("esporta", "Esporta", Icons.Default.IosShare, listOf(
        ToolNavItem("export", "Esporta", Icons.Default.Download),
        ToolNavItem("share", "Condividi", Icons.Default.Share),
        ToolNavItem("split", "Dividi e salva", Icons.Default.GridOn),
        ToolNavItem("replay", "Riproduci modifiche", Icons.Default.Replay)
    ))
)

@Composable
private fun NavIcon(item: ImageVector, label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    PxTooltip(label) {
        Box(
            modifier = Modifier
                .size(Px.Touch)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) Px.Accent.copy(alpha = 0.18f) else Color.Transparent)
                .clickable(onClick = onClick)
                .testTag(tag),
            contentAlignment = Alignment.Center
        ) {
            Icon(item, contentDescription = label, tint = if (selected) Px.Active else TextDim, modifier = Modifier.size(22.dp))
        }
    }
}

/** Telefono: barra fissa con le 7 famiglie (non scorre: 7 × 44 dp entrano in ogni telefono). */
@Composable
fun FamilyBar(selectedFamily: String?, onFamily: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("bottom_toolbar"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TOOL_FAMILIES.forEach { f -> NavIcon(f.icon, f.label, f.id == selectedFamily, "family_${f.id}") { onFamily(f.id) } }
    }
}

/** Telefono: strumenti della famiglia scelta, sopra la barra delle famiglie. */
@Composable
fun FamilyToolsRow(familyId: String?, onTool: (String) -> Unit) {
    val fam = TOOL_FAMILIES.find { it.id == familyId } ?: return
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Px.Gutter, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Px.Gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fam.tools.forEach { t -> NavIcon(t.icon, t.label, false, "tool_${t.id}") { onTool(t.id) } }
        }
        HorizontalDivider(color = DividerCol, thickness = 0.5.dp)
    }
}

/** Tablet, orizzontale e web: colonna a sinistra con famiglie e, accanto, gli strumenti della famiglia. */
@Composable
fun FamilyRail(selectedFamily: String?, activeTool: String?, onFamily: (String) -> Unit, onTool: (String) -> Unit) {
    Row(Modifier.fillMaxHeight().padding(8.dp).clip(RoundedCornerShape(18.dp)).background(BgBlack)) {
        Column(
            modifier = Modifier.fillMaxHeight().width(60.dp).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TOOL_FAMILIES.forEach { f -> NavIcon(f.icon, f.label, f.id == selectedFamily, "family_${f.id}") { onFamily(f.id) } }
        }
        val fam = TOOL_FAMILIES.find { it.id == selectedFamily }
        if (fam != null) {
            Column(
                modifier = Modifier.fillMaxHeight().width(60.dp).background(Panel2).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fam.tools.forEach { t -> NavIcon(t.icon, t.label, t.id == activeTool, "tool_${t.id}") { onTool(t.id) } }
            }
        }
    }
}
