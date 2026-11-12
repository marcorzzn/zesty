package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.fx.FxEngine
import com.example.ui.ZestyEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.EditorViewModel

/**
 * Zesty - Activity Principale Nativa
 * Autore: Marco Razzano
 * 
 * Esegue l'editor grafico professionale ZestyEditorScreen in Jetpack Compose nativo,
 * con gestione dello stato multi-livello in EditorViewModel e rendering grafico hardware.
 */
class MainActivity : ComponentActivity() {

    private val editorViewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Catalogo effetti condiviso con la web app (assets/www/assets/fx)
        FxEngine.load(applicationContext)
        // modelli di scontorno sul telefono (si caricano solo al primo uso)
        com.example.engine.AiSegmenter.attach(applicationContext)
        // pacchetti IA scaricati al primo uso (registro in assets/ai/registry.json)
        com.example.engine.AiPacks.attach(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                ZestyEditorScreen(vm = editorViewModel)
            }
        }
    }
}
