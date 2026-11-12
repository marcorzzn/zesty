package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel

/**
 * Cancella oggetti: in alto il pulsante principale, sotto gli strumenti per segnare la zona
 * (pennello, lazo, poligono, forme, bacchetta). La zona da togliere è in rosso sulla foto.
 */
@Composable
fun EraseStudioPanel(state: EditorUiState, vm: EditorViewModel) {
    Column(Modifier.fillMaxWidth().background(BgBlack).padding(top = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Px.Gutter, vertical = 4.dp)
                .height(Px.Touch)
                .clip(RoundedCornerShape(14.dp))
                .background(if (state.eraseBusy) Px.Surface else Px.Accent)
                .clickable(enabled = !state.eraseBusy) { vm.eraseNow() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = if (state.eraseBusy) TextDim else OnAccent, modifier = Modifier.size(20.dp))
            Text(
                if (state.eraseBusy) "Ricostruisco lo sfondo…" else "Cancella",
                color = if (state.eraseBusy) TextDim else OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (state.eraseBusy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Px.Gutter, vertical = 2.dp).height(2.dp),
                color = Px.Accent, trackColor = DividerCol
            )
        }
        PxHint("Copri tutto l'oggetto, anche l'ombra, con un piccolo margine: il risultato è più pulito")
        SelectivePaintToolPanel(state, vm)
    }
}
