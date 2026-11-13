package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SplitGeometryTest {

    @Test
    fun gridCoversThePhotoWithoutOverlap() {
        val r = SplitGeometry.rects(900, 600, SplitGeometry.Settings(mode = 0, cols = 3, rows = 2))
        assertEquals(6, r.size)
        assertEquals(900 * 600, r.sumOf { it.width() * it.height() })
        assertEquals(300, r[0].width()); assertEquals(300, r[0].height())
    }

    @Test
    fun upToTwentyColumnsRowsAndPanels() {
        val g = SplitGeometry.rects(4000, 3000, SplitGeometry.Settings(mode = 0, cols = 20, rows = 20))
        assertEquals(400, g.size)
        assertEquals(4000 * 3000, g.sumOf { it.width() * it.height() })
        assertEquals(200, g[0].width()); assertEquals(150, g[0].height())
        val p = SplitGeometry.rects(20000, 1000, SplitGeometry.Settings(mode = 1, ratioW = 1f, ratioH = 1f, count = 20))
        assertEquals(20, p.size)
        assertEquals(20000, p.last().right)
    }

    @Test
    fun gapIsRemovedBetweenPanels() {
        val r = SplitGeometry.rects(1000, 500, SplitGeometry.Settings(mode = 0, cols = 2, rows = 1, gap = 20))
        assertEquals(490, r[0].width())
        assertEquals(510, r[1].left)
        assertEquals(1000, r[1].right)
    }

    @Test
    fun ratioPanelsKeepTheirProportionAndFit() {
        // tre pannelli 4:5 in fila su una foto 3000×2000: altezza piena, larghezza 1600 → non entrano, si riducono
        val r = SplitGeometry.rects(3000, 2000, SplitGeometry.Settings(mode = 1, ratioW = 4f, ratioH = 5f, count = 3))
        assertEquals(3, r.size)
        r.forEach { assertEquals(0.8f, it.width().toFloat() / it.height(), 0.01f) }
        assertTrue(r.last().right <= 3000)
        assertEquals(3000, r.last().right) // occupa tutta la larghezza
    }

    @Test
    fun verticalStripStacksPanels() {
        val r = SplitGeometry.rects(1080, 3000, SplitGeometry.Settings(mode = 1, ratioW = 1f, ratioH = 1f, count = 2, vertical = true, position = 0f))
        assertEquals(2, r.size)
        assertEquals(1080, r[0].width()); assertEquals(1080, r[0].height())
        assertEquals(1080, r[1].top)
    }
}
