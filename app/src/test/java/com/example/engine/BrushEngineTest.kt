package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.BlendOption
import com.example.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrushEngineTest {

    @Test
    fun mappingFollowsScaleAndRotation() {
        // livello 100×50 in (200,100), bitmap 400×200 (2× le misure del livello)
        val m = BrushGeometry.toBitmap(250f, 125f, 200f, 100f, 100f, 50f, 0f, 400, 200)
        assertEquals(200f, m.x, 0.01f); assertEquals(100f, m.y, 0.01f); assertEquals(4f, m.pxPerUnit, 0.01f)
        // ruotato di 90°: un punto 10 unità a destra del centro sulla tela è 10 unità "sopra" nel livello
        val r = BrushGeometry.toBitmap(260f, 125f, 200f, 100f, 100f, 50f, 90f, 100, 50)
        assertEquals(50f, r.x, 0.01f); assertEquals(15f, r.y, 0.01f)
    }

    @Test
    fun opacityIsUniformAlongTheStroke() {
        val bmp = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888)
        val s = BrushEngine.Stroke(bmp, BrushEngine.Kind.ROUND, BrushEngine.Params(size = 20f, opacity = 50f, hardness = 100f, spacing = 5f, smoothing = 0f, color = Color.RED))
        var x = 20f
        while (x <= 280f) { s.addPoint(x, 50f); s.render(); x += 3f }
        s.finish()
        val alphas = (40..260 step 5).map { Color.alpha(bmp.getPixel(it, 50)) }
        val min = alphas.min(); val max = alphas.max()
        assertTrue("alpha tra $min e $max", max - min <= 3)
        assertEquals(128f, alphas.average().toFloat(), 4f)
        assertEquals(0, Color.alpha(bmp.getPixel(150, 5))) // fuori dal tratto resta trasparente
    }

    @Test
    fun eraserRemovesPixels() {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val s = BrushEngine.Stroke(bmp, BrushEngine.Kind.ERASER, BrushEngine.Params(size = 20f, opacity = 100f, hardness = 100f, smoothing = 0f))
        s.addPoint(10f, 50f); s.addPoint(90f, 50f); s.finish()
        assertTrue(Color.alpha(bmp.getPixel(50, 50)) < 10)
        assertEquals(255, Color.alpha(bmp.getPixel(50, 5)))
    }

    @Test
    fun colorBrushPaintsOnItsOwnLayerAndLeavesThePhotoAlone() {
        val vm = EditorViewModel()
        vm.createNewProject(400, 300, Color.WHITE)
        val photoBefore = vm.state.value.layers.first().bitmap?.getPixel(200, 150)
        vm.openTool("brush")
        vm.updatePaintBrush(kind = BrushEngine.Kind.ROUND, blend = BlendOption.COLOR, color = Color.RED)
        vm.onCanvasPointerDown(100f, 150f)
        vm.onCanvasPointerMove(200f, 150f)
        vm.onCanvasPointerMove(300f, 150f)
        vm.onCanvasPointerUp()
        val layers = vm.state.value.layers
        val paint = layers.last()
        assertTrue(paint.isPaintLayer)
        assertEquals(BlendOption.COLOR, paint.blend)
        assertTrue(Color.alpha(paint.bitmap!!.getPixel(200, 150)) > 200)
        assertEquals(photoBefore, layers.first().bitmap?.getPixel(200, 150))
        assertTrue(abs(Color.red(paint.bitmap!!.getPixel(200, 150)) - 255) < 3)
    }

    @Test
    fun strokeChangesTheBitmapGenerationWhileDrawing() {
        // la cache dei livelli riconosce le modifiche dal "generationId": deve cambiare a ogni tratto
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val g0 = bmp.generationId
        val s = BrushEngine.Stroke(bmp, BrushEngine.Kind.ROUND, BrushEngine.Params(size = 10f, smoothing = 0f, color = Color.BLACK))
        s.addPoint(20f, 20f); s.render()
        val g1 = bmp.generationId
        s.addPoint(80f, 80f); s.render()
        assertTrue(g1 != g0)
        assertTrue(bmp.generationId != g1)
    }
}
