package com.example

import com.example.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'editor deve nascere senza errori: all'avvio crea un progetto e chiede già una composizione
 * della tela in sottofondo (un campo dichiarato dopo `init` aveva fatto chiudere l'app all'avvio).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorStartupTest {

    @Test
    fun editorStartsWithAProject() {
        val vm = EditorViewModel()
        val s = vm.state.value
        assertEquals(1, s.layers.size)
        assertTrue(s.newProjectModalOpen)
    }

    @Test
    fun newProjectAndRebuildDoNotCrash() {
        val vm = EditorViewModel()
        vm.createNewProject(800, 600, 0xFFFFFFFF.toInt())
        vm.rebuildCompositeImmediate()
        vm.scheduleCompositeRebuild()
        assertEquals(800, vm.state.value.canvasW)
    }
}
