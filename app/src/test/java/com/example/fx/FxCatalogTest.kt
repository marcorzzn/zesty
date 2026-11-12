package com.example.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Controllo del catalogo dopo il riordino (docs/EFFETTI.md): ogni effetto si applica senza errori,
 * cambia davvero l'immagine e non la svuota. Scrive anche un provino degli effetti per categoria
 * in app/build/fx-provini/ per guardarli.
 */
@RunWith(RobolectricTestRunner::class)
class FxCatalogTest {

    private fun catalog(): FxCatalog {
        val dir = listOf(File("../public/assets/fx"), File("public/assets/fx")).first { File(it, "index.json").exists() }
        return FxCatalog.parse(File(dir, "index.json").readText()) { File(dir, it).takeIf { f -> f.exists() }?.readText() }
    }

    /** Scena di prova "fotografica": cielo, prato, persona con pelle e maglia, oggetti colorati, ombre. */
    private fun scene(w: Int, h: Int): FxImage {
        val img = FxImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val t = y.toFloat() / h
            var r: Float; var g: Float; var b: Float
            if (t < 0.55f) { r = 120f + 110f * t; g = 170f + 60f * t; b = 235f - 40f * t } // cielo
            else { r = 60f - 20f * t; g = 130f - 30f * t; b = 50f } // prato
            val dx = (x - w * 0.5f) / (w * 0.09f); val dy = (y - h * 0.38f) / (h * 0.12f)
            if (dx * dx + dy * dy < 1f) { r = 225f - dx * 25f; g = 172f - dx * 20f; b = 140f - dx * 15f } // volto, luce da sinistra
            if (y > h * 0.5f && abs(x - w * 0.5f) < w * (0.1f + (y - h * 0.5f) / h * 0.3f)) { r = 180f; g = 30f; b = 45f } // maglia rossa
            if (hypot(x - w * 0.2f, y - h * 0.75f) < w * 0.07f) { r = 250f; g = 200f; b = 30f } // oggetto giallo
            if (x > w * 0.78f && x < w * 0.9f && y > h * 0.45f && y < h * 0.85f) { r = 30f; g = 70f; b = 170f } // oggetto blu
            val n = (FxRand.r01(x, y, 3) - 0.5f) * 14f
            img.px[y * w + x] = Px.argb(255, (r + n).toInt().coerceIn(0, 255), (g + n).toInt().coerceIn(0, 255), (b + n).toInt().coerceIn(0, 255))
        }
        return img
    }

    private fun meanDiff(a: FxImage, b: FxImage): Double {
        var s = 0.0
        for (i in a.px.indices) {
            val p = a.px[i]; val q = b.px[i]
            s += abs(Px.r(p) - Px.r(q)) + abs(Px.g(p) - Px.g(q)) + abs(Px.b(p) - Px.b(q))
        }
        return s / (a.px.size * 3)
    }

    private fun spread(img: FxImage): Double {
        var mn = 255; var mx = 0
        for (p in img.px) { val l = (Px.r(p) + Px.g(p) + Px.b(p)) / 3; if (l < mn) mn = l; if (l > mx) mx = l }
        return (mx - mn).toDouble()
    }

    @Test
    fun everyEffectWorks() {
        val cat = catalog()
        assertEquals("16 categorie", 16, cat.categories.size)
        val src = scene(160, 120)
        val failures = mutableListOf<String>()
        for (fx in cat.effects) {
            val out = try {
                FxEngine.applyToImage(src.copy(), fx, emptyMap(), 1f, "normal")
            } catch (t: Throwable) { failures += "${fx.id}: errore ${t.message}"; continue }
            if (out.w != src.w || out.h != src.h) failures += "${fx.id}: dimensioni cambiate"
            // effetti delicati che sulla scena sintetica cambiano poco: stelle solo sul buio, levigatura solo sulla pelle vera
            if (meanDiff(src, out) < 0.8 && fx.id !in setOf("atm_starry", "port_skin")) failures += "${fx.id}: non cambia l'immagine"
            if (spread(out) < 8) failures += "${fx.id}: immagine piatta"
        }
        assertTrue("Effetti con problemi:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun idsAreUniqueAndGroupsOrdered() {
        val cat = catalog()
        assertEquals(cat.effects.size, cat.effects.map { it.id }.toSet().size)
        assertTrue(cat.effects.size >= 367)
        assertTrue(cat.inCategory("cinema").any { it.group == "I grandi autori" })
    }

    /** Provini: una tavola per categoria, ogni effetto su 160×120 con il nome nel file indice. */
    @Test
    fun contactSheets() {
        val cat = catalog()
        val src = scene(160, 120)
        val dir = File("build/fx-provini").apply { mkdirs() }
        for (c in cat.categories) {
            val list = cat.inCategory(c.id)
            val cols = 6; val rows = (list.size + cols - 1) / cols
            val sheet = BufferedImage(cols * 164, rows * 124, BufferedImage.TYPE_INT_RGB)
            val names = StringBuilder()
            list.forEachIndexed { i, fx ->
                val out = runCatching { FxEngine.applyToImage(src.copy(), fx, emptyMap(), 1f, "normal") }.getOrNull() ?: return@forEachIndexed
                val ox = (i % cols) * 164 + 2; val oy = (i / cols) * 124 + 2
                for (y in 0 until 120) for (x in 0 until 160) sheet.setRGB(ox + x, oy + y, out.px[y * 160 + x] and 0xFFFFFF)
                names.appendLine("${i + 1}. ${fx.label} [${fx.group}] (${fx.id})")
            }
            ImageIO.write(sheet, "png", File(dir, "${c.id}.png"))
            File(dir, "${c.id}.txt").writeText(names.toString())
        }
    }
}

class FxSampleTest {
    @org.junit.Test
    fun bilinearDoesNotMixChannels() {
        // due pixel vicini: blu pieno e rosso pieno; a metà strada deve venire un viola, senza verde né sconfinamenti
        val img = FxImage(2, 1, intArrayOf(Px.argb(255, 0, 0, 255), Px.argb(255, 255, 0, 0)))
        val c = FxSample.bilinear(img, 0.5f, 0f)
        org.junit.Assert.assertEquals(255, (c ushr 24) and 0xFF)
        org.junit.Assert.assertEquals(128f, Px.r(c).toFloat(), 1f)
        org.junit.Assert.assertEquals(0, Px.g(c))
        org.junit.Assert.assertEquals(128f, Px.b(c).toFloat(), 1f)
        // valori casuali: ogni canale resta tra i due estremi
        val r = java.util.Random(4)
        repeat(2000) {
            val a = r.nextInt() or 0xFF000000.toInt(); val b = r.nextInt() or 0xFF000000.toInt()
            val m = FxSample.bilinear(FxImage(2, 1, intArrayOf(a, b)), r.nextFloat(), 0f)
            for (sh in intArrayOf(16, 8, 0)) {
                val x = (a ushr sh) and 0xFF; val y = (b ushr sh) and 0xFF; val v = (m ushr sh) and 0xFF
                org.junit.Assert.assertTrue(v >= minOf(x, y) - 1 && v <= maxOf(x, y) + 1)
            }
        }
    }
}
