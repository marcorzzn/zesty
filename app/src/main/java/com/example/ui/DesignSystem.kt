package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Zesty — componenti di base dell'interfaccia (vedi docs/UI_PRINCIPLES.md).
 * Misure e colori sono qui, una volta sola: i pannelli non usano valori sparsi.
 */
object Px {
    val Gutter = 16.dp
    val Gap = 8.dp
    val GroupGap = 12.dp
    val ControlH = 36.dp
    val Touch = 44.dp
    val IconBar = 20.dp
    val IconControl = 18.dp
    val Accent = AccentPink      // arancio
    val Active = AccentPinkHi    // limone
    val Handle = ActivePurple    // lime
    val OnAccentColor = OnAccent
    val Surface = Panel2
    val SurfaceHi = Panel3
}

/** Nome dello strumento mostrato con la pressione lunga (le icone non hanno scritte sotto). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PxTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip(containerColor = Px.SurfaceHi, contentColor = TextWhite) { Text(label, fontSize = 12.sp) } },
        state = rememberTooltipState()
    ) { content() }
}

/** Titolo di sezione del pannello. */
@Composable
fun PxSectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Px.Gutter, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Suggerimento di una riga. */
@Composable
fun PxHint(text: String) {
    Text(
        text, color = TextMute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Px.Gutter, vertical = 2.dp)
    )
}

/** Chip selezionabile, solo per collezioni in una riga scorrevole. */
@Composable
fun PxChip(text: String, selected: Boolean, icon: ImageVector? = null, swatch: Color? = null, onClick: () -> Unit) {
    val fg = if (selected) OnAccent else TextDim
    Row(
        modifier = Modifier
            .height(if (icon != null) 36.dp else 32.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Px.Accent else Px.Surface)
            .clickable(onClick = onClick)
            .padding(start = if (icon != null || swatch != null) 8.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // l'icona mostra il risultato dell'azione, il testo breve la conferma
        if (icon != null) Icon(icon, contentDescription = null, tint = if (selected) OnAccent else TextWhite, modifier = Modifier.size(20.dp))
        if (swatch != null) Box(Modifier.size(16.dp).clip(CircleShape).background(swatch).border(1.dp, TextMute, CircleShape))
        Text(text, color = fg, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

/** Riga scorrevole per collezioni, con i margini standard. */
@Composable
fun PxCollectionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Px.Gutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Px.Gap),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Variante senza margine sinistro, per collezioni a destra di un'etichetta. */
@Composable
fun PxCollectionRowCompact(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = Px.Gutter),
        horizontalArrangement = Arrangement.spacedBy(Px.Gap),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

data class PxOption(val label: String, val icon: ImageVector? = null)

/** Selettore segmentato a larghezza piena (2–5 opzioni): non esce mai dai margini. */
@Composable
fun PxSegmented(options: List<PxOption>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Px.Gutter, vertical = 4.dp)
            .height(if (options.any { it.icon != null }) Px.Touch else Px.ControlH)
            .clip(RoundedCornerShape(10.dp))
            .background(Px.Surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) Px.Accent else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (o.icon != null) {
                    // solo icona: il nome è nella descrizione accessibile e nel suggerimento a pressione lunga
                    PxTooltip(o.label) { Icon(o.icon, contentDescription = o.label, tint = if (sel) OnAccent else TextDim, modifier = Modifier.size(22.dp)) }
                } else {
                    Text(o.label, color = if (sel) OnAccent else TextDim, fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Cursore con etichetta e valore, misure standard. */
@Composable
fun PxSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    suffix: String = "",
    decimals: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Px.Gutter).height(Px.ControlH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(92.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Px.Active, activeTrackColor = Px.Accent, inactiveTrackColor = BorderCol)
        )
        Text(
            text = (if (decimals) "%.2f".format(value).trimEnd('0').trimEnd('.', ',') else value.roundToInt().toString()) + suffix,
            color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.width(44.dp).padding(start = 6.dp)
        )
    }
}

/** Scelta da un elenco lungo (es. metodi di fusione) in una sola riga: etichetta + menu. */
@Composable
fun PxDropdownRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Px.Gutter).height(Px.ControlH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDim, fontSize = 11.sp, modifier = Modifier.width(92.dp))
        Box {
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Px.Surface)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(options.getOrElse(selected) { "" }, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Scegli", tint = TextDim, modifier = Modifier.size(Px.IconControl))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Px.SurfaceHi) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(
                        text = { Text(o, color = if (i == selected) Px.Accent else TextWhite, fontSize = 13.sp) },
                        onClick = { open = false; onSelect(i) }
                    )
                }
            }
        }
    }
}

/** Azione a icona per la barra superiore; `active` la colora d'accento. */
@Composable
fun PxIconAction(icon: ImageVector, description: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    PxTooltip(description) { PxIconActionInner(icon, description, active, enabled, onClick) }
}

@Composable
private fun PxIconActionInner(icon: ImageVector, description: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) Px.Accent else BgBlack)
    ) {
        Icon(icon, contentDescription = description, tint = if (!enabled) TextMute else if (active) OnAccent else TextWhite, modifier = Modifier.size(Px.IconBar))
    }
}

data class PxMenuItem(val label: String, val icon: ImageVector? = null, val checked: Boolean? = null, val onClick: () -> Unit)

/** Menu ⋮ per le azioni che non entrano nella barra. */
@Composable
fun PxOverflowMenu(items: List<PxMenuItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        PxIconAction(Icons.Default.MoreVert, "Altre azioni") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Px.SurfaceHi) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, color = TextWhite, fontSize = 13.sp) },
                    leadingIcon = item.icon?.let { ic -> { Icon(ic, contentDescription = null, tint = if (item.checked == true) Px.Accent else TextDim, modifier = Modifier.size(Px.IconControl)) } },
                    trailingIcon = item.checked?.let { c -> { if (c) Box(Modifier.size(8.dp).clip(CircleShape).background(Px.Accent)) } },
                    onClick = { open = false; item.onClick() }
                )
            }
        }
    }
}

/** Contenitore del pannello inferiore: altezza massima e scorrimento verticale se serve. */
@Composable
fun PxPanel(maxHeight: androidx.compose.ui.unit.Dp = 300.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgBlack)
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}
