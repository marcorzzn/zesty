package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.PicsartEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.EditorViewModel

/**
 * Pixart - Activity Principale Nativa
 * Autore: Marco Razzano
 * 
 * Esegue l'editor grafico professionale PicsartEditorScreen in Jetpack Compose nativo,
 * con gestione dello stato multi-livello in EditorViewModel e rendering grafico hardware.
 */
class MainActivity : ComponentActivity() {

    private val editorViewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                PicsartEditorScreen(vm = editorViewModel)
            }
        }
    }
}
