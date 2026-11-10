package com.example.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Esegue ogni effetto del catalogo su un'immagine sintetica e verifica che:
 * - il catalogo si carichi e ogni operazione esista
 * - nessun effetto generi eccezioni (con valori predefiniti, minimi e massimi)
 * - le dimensioni restino invariate e il risultato non sia vuoto
 */
@RunWith(RobolectricTestRunner::class)
class FxCatalogTest {

    private fun fxDir(): File {
        val candidates = listOf(File("../public/assets/fx"), File("public/assets/fx"))
        return candidates.first { File(it, "index.json").exists() }
    }

    private fun loadCatalog(): FxCatalog {
        val dir = fxDir()
        return FxCatalog.parse(File(dir, "index.json").readText()) { path ->
            File(dir, path).takeIf { it.exists() }?.readText()
        }
    }

    /** Gradiente colorato con un cerchio e un'area trasparente: copre colori, bordi e alpha. */
    private fun sampleImage(w: Int = 48, h: Int = 36): FxImage {
        val img = FxImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val r = x * 255 / (w - 1); val g = y * 255 / (h - 1); val b = 255 - r
            val inCircle = (x - w / 2) * (x - w / 2) + (y - h / 2) * (y - h / 2) < (h / 4) * (h / 4)
            val a = if (x < 3) 0 else 255
            img.px[y * w + x] = if (inCircle) Px.argb(a, 240, 200, 170) else Px.argb(a, r, g, b)
        }
        return img
    }

    @Test
    fun catalogLoadsAllCategories() {
        val cat = loadCatalog()
        assertTrue("catalogo vuoto", cat.effects.size >= 250)
        val catIds = cat.categories.map { it.id }.toSet()
        cat.effects.forEach { assertTrue("${it.id}: categoria ${it.category} sconosciuta", it.category in catIds) }
        assertEquals("id duplicati", cat.effects.size, cat.effects.map { it.id }.toSet().size)
    }

    @Test
    fun everyEffectRunsWithDefaultMinAndMaxValues() {
        val cat = loadCatalog()
        FxEngine.setCatalog(cat)
        FxEngine.strict = true
        val src = sampleImage()
        val failures = mutableListOf<String>()
        for (fx in cat.effects) {
            val variants = listOf(
                emptyMap(),
                fx.params.filter { it.type == FxParamType.RANGE }.associate { it.id to it.min },
                fx.params.filter { it.type == FxParamType.RANGE }.associate { it.id to it.max }
            )
            for (values in variants) {
                try {
                    val out = FxEngine.applyToImage(src.copy(), fx, values, 1f, "normal")
                    if (out.w != src.w || out.h != src.h) failures += "${fx.id}: dimensioni cambiate"
                } catch (t: Throwable) {
                    failures += "${fx.id} $values: ${t::class.simpleName} ${t.message}"
                }
            }
        }
        FxEngine.strict = false
        assertTrue("Effetti con errori:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun expressionsAndColors() {
        val vars = mapOf("amount" to 50.0, "bars" to 10.0)
        assertEquals(20.0, FxExpr.eval("\$amount * 0.4", vars), 1e-9)
        assertEquals(-20.0, FxExpr.eval("-\$amount * 0.4", vars), 1e-9)
        assertEquals(0.8, FxExpr.eval("1 - \$bars / 50", vars), 1e-9)
        assertEquals(30.0, FxExpr.eval("clamp(\$amount, 0, 30)", vars), 1e-9)
        assertEquals(0xFFFF6A3D.toInt(), FxColor.parse("#ff6a3d"))
        assertEquals(0x80FF3D3D.toInt(), FxColor.parse("#ff3d3d80"))
    }

    @Test
    fun strengthZeroKeepsOriginal() {
        val cat = loadCatalog()
        FxEngine.setCatalog(cat)
        val src = sampleImage()
        val fx = cat.get("bw_classic")!!
        val out = FxEngine.applyToImage(src.copy(), fx, emptyMap(), 0f, "normal")
        assertTrue(out.px.contentEquals(src.px))
    }
}
