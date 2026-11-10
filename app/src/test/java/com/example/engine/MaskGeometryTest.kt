package com.example.engine

import android.graphics.PointF
import com.example.model.MaskShapeDraft
import com.example.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaskGeometryTest {

    @Test
    fun handlesFollowRotation() {
        val d = MaskShapeDraft(ShapeKind.RECT, cx = 100f, cy = 100f, w = 40f, h = 20f, rotation = 90f)
        val hs = MaskGeometry.handles(d, knobOffset = 10f)
        // angolo alto-sinistra (-20,-10) ruotato di 90° -> (10,-20) rispetto al centro
        assertEquals(110f, hs[0].x, 0.01f)
        assertEquals(80f, hs[0].y, 0.01f)
        // maniglia di rotazione: locale (0, -h/2 - 10) = (0, -20), ruotata di 90° -> (20, 0)
        assertEquals(120f, hs[4].x, 0.01f)
        assertEquals(100f, hs[4].y, 0.01f)
        assertEquals(5, hs.size)
    }

    @Test
    fun localRoundTripAndContains() {
        val d = MaskShapeDraft(ShapeKind.CIRCLE, 50f, 60f, 30f, 10f, rotation = 33f)
        val p = MaskGeometry.local(d, 7f, -3f)
        val back = MaskGeometry.toLocal(d, p.x, p.y)
        assertEquals(7f, back.x, 0.01f)
        assertEquals(-3f, back.y, 0.01f)
        assertTrue(MaskGeometry.contains(d, 50f, 60f))
        assertFalse(MaskGeometry.contains(d, 90f, 60f))
    }

    @Test
    fun polygonEditingHelpers() {
        val square = listOf(PointF(0f, 0f), PointF(100f, 0f), PointF(100f, 100f), PointF(0f, 100f))
        assertEquals(2, MaskGeometry.nearestPoint(square, 98f, 103f, 10f))
        assertEquals(-1, MaskGeometry.nearestPoint(square, 50f, 50f, 10f))
        // vicino al lato destro (fra punto 1 e 2) -> si inserisce dopo l'indice 1
        assertEquals(1, MaskGeometry.nearestSegment(square, 103f, 50f, 10f))
        // il lato di chiusura (3 -> 0)
        assertEquals(3, MaskGeometry.nearestSegment(square, -2f, 40f, 10f))
    }

    @Test
    fun lassoSimplification() {
        val dense = (0 until 200).map { PointF(it.toFloat(), 0f) }
        val simple = MaskGeometry.simplify(dense, 10f)
        assertTrue(simple.size in 19..21)
    }
}
