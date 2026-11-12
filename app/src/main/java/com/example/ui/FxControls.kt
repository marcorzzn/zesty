package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fx.FxEngine
import com.example.fx.FxParam
import com.example.fx.FxParamType
import kotlin.math.roundToInt

/** Palette rapida per i parametri colore dei filtri. */
private val FX_SWATCHES = listOf(
    0x000000, 0xFFFFFF, 0x1A1446, 0x3D8BFF, 0x05D9E8, 0x06D6A0, 0x7AE582, 0xFFD23F,
    0xFF9800, 0xFF6A3D, 0xE4003A, 0xFF3D7F, 0xB14AED, 0x5A1A6A, 0x8A6A48, 0xF6EAD6
)

/**
 * Controlli generati dai parametri di un filtro del catalogo (cursori, scelte, interruttori, colori).
 * Un nuovo filtro JSON ottiene la sua interfaccia senza scrivere codice.
 * Il "Ripristina" sta nel menu ⋮ della barra superiore.
 */
@Composable
fun FxParamControls(
    effectId: String?,
    overrides: Map<String, Float>,
    onChange: (String, Float) -> Unit
) {
    val fx = effectId?.let { FxEngine.catalog.get(it) } ?: return
    val values = fx.defaultValues() + overrides
    fx.params.filterNot { it.hidden }.forEach { p ->
        val v = values[p.id] ?: p.default
        when (p.type) {
            FxParamType.RANGE -> PxSlider(
                label = p.label,
                value = v,
                range = p.min..p.max,
                onChange = { raw ->
                    val snapped = if (p.step > 0f) (Math.round((raw - p.min) / p.step) * p.step + p.min) else raw
                    onChange(p.id, snapped.coerceIn(p.min, p.max))
                },
                decimals = p.step < 1f
            )
            FxParamType.CHOICE -> FxOptionRow(p.label, p.options, v.roundToInt()) { onChange(p.id, it.toFloat()) }
            FxParamType.TOGGLE -> FxOptionRow(p.label, listOf("No", "Sì"), if (v != 0f) 1 else 0) { onChange(p.id, it.toFloat()) }
            FxParamType.COLOR -> FxColorRow(p, v.toInt()) { onChange(p.id, it.toFloat()) }
        }
    }
}

/** Poche opzioni: chip in fila con l'etichetta a sinistra (collezione, può scorrere). */
@Composable
private fun FxOptionRow(label: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = Px.Gutter).height(Px.ControlH), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextDim, fontSize = 11.sp, modifier = Modifier.width(92.dp))
        Box(Modifier.weight(1f)) {
            PxCollectionRowCompact { options.forEachIndexed { i, o -> PxChip(o, selected = i == selected) { onChange(i) } } }
        }
    }
}

@Composable
private fun FxColorRow(p: FxParam, rgb: Int, onChange: (Int) -> Unit) {
    val swatches = (listOf(p.defaultColor and 0xFFFFFF) + FX_SWATCHES).distinct()
    Row(modifier = Modifier.fillMaxWidth().padding(start = Px.Gutter).height(Px.ControlH), verticalAlignment = Alignment.CenterVertically) {
        Text(p.label, color = TextDim, fontSize = 11.sp, modifier = Modifier.width(92.dp))
        Box(Modifier.weight(1f)) {
            PxCollectionRowCompact {
                swatches.forEach { c ->
                    val sel = c == rgb
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF000000.toInt() or c))
                            .border(if (sel) 2.dp else 1.dp, if (sel) Px.Accent else BorderCol, CircleShape)
                            .clickable { onChange(c) }
                    )
                }
            }
        }
    }
}
