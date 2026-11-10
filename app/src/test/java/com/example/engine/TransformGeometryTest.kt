package com.example.engine

import com.example.engine.TransformGeometry.Handle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransformGeometryTest {

    private fun corner(b: TransformGeometry.Box, h: Handle) = TransformGeometry.handles(b, 10f, 10f).getValue(h)

    @Test
    fun cornerResizeKeepsOppositeCornerFixedEvenWhenRotated() {
        val start = TransformGeometry.Box(200f, 200f, 100f, 60f, rotation = 30f)
        val anchorBefore = corner(start, Handle.TL)
        val br = corner(start, Handle.BR)
        val out = TransformGeometry.resize(start, Handle.BR, br.x + 40f, br.y + 25f, keepRatio = true, fromCenter = false, minSize = 16f)
        val anchorAfter = corner(out, Handle.TL)
        assertEquals(anchorBefore.x, anchorAfter.x, 0.05f)
        assertEquals(anchorBefore.y, anchorAfter.y, 0.05f)
        assertEquals(100f / 60f, out.w / out.h, 0.001f) // proporzioni mantenute
    }

    @Test
    fun sideResizeChangesOneAxisOnly() {
        val start = TransformGeometry.Box(100f, 100f, 80f, 40f, 0f)
        val out = TransformGeometry.resize(start, Handle.R, 160f, 999f, keepRatio = true, fromCenter = false, minSize = 16f)
        assertEquals(100f, out.w, 0.01f)   // da x=60 (bordo sinistro fermo) a 160
        assertEquals(40f, out.h, 0.01f)
        assertEquals(60f, out.x, 0.01f)
    }

    @Test
    fun resizeDoesNotDriftWhenCalledRepeatedlyFromStart() {
        val start = TransformGeometry.Box(100f, 100f, 80f, 80f, 15f)
        val target = corner(start, Handle.TR).let { android.graphics.PointF(it.x + 30f, it.y - 10f) }
        val a = TransformGeometry.resize(start, Handle.TR, target.x, target.y, true, false, 16f)
        val b = TransformGeometry.resize(start, Handle.TR, target.x, target.y, true, false, 16f)
        assertEquals(a, b)
    }

    @Test
    fun minimumSizeIsRespected() {
        val start = TransformGeometry.Box(100f, 100f, 80f, 80f, 0f)
        val out = TransformGeometry.resize(start, Handle.BR, 0f, 0f, keepRatio = false, fromCenter = false, minSize = 16f)
        assertEquals(16f, out.w, 0.01f)
        assertEquals(16f, out.h, 0.01f)
    }

    @Test
    fun rotationSnapsNear45Degrees() {
        val start = TransformGeometry.Box(0f, 0f, 50f, 50f, 0f)
        val startAngle = TransformGeometry.angleOf(start, 100f, 0f) // 0°
        val p = Math.toRadians(43.0)
        val out = TransformGeometry.rotate(start, startAngle, (100 * Math.cos(p)).toFloat(), (100 * Math.sin(p)).toFloat())
        assertEquals(45f, out.rotation, 0.001f)
    }

    @Test
    fun handlesWinOverBodyAndSidesCanBeHidden() {
        val b = TransformGeometry.Box(100f, 100f, 40f, 40f, 0f)
        assertEquals(Handle.BR, TransformGeometry.hit(b, 121f, 121f, 8f, 10f, 10f, showSides = true))
        assertEquals(Handle.BODY, TransformGeometry.hit(b, 100f, 100f, 8f, 10f, 10f, showSides = true))
        // lato destro nascosto: il tocco sul lato cade nel corpo
        assertEquals(Handle.BODY, TransformGeometry.hit(b, 119f, 100f, 8f, 10f, 10f, showSides = false))
        assertEquals(null, TransformGeometry.hit(b, 300f, 300f, 8f, 10f, 10f, showSides = true))
    }
}
