package com.example.fx

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Solo per indagare: rende un effetto un passo alla volta (ZESTY_DEBUG_FX=id). */
@RunWith(RobolectricTestRunner::class)
class FxDebugSteps {
    @Test fun steps() {
        val id = System.getenv("ZESTY_DEBUG_FX") ?: return
        val dir = listOf(File("../public/assets/fx"), File("public/assets/fx")).first { File(it, "index.json").exists() }
        val cat = FxCatalog.parse(File(dir, "index.json").readText()) { File(dir, it).takeIf { f -> f.exists() }?.readText() }
        val fx = cat.get(id)!!
        val w = 160; val h = 120
        val img = FxImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val n = ((FxRand.r01(x, y, 3) - 0.5f) * 14f).toInt()
            img.px[y * w + x] = if (y < 66) Px.argb(255, 120 + y / 2 + n, 175 + n, 230 - y / 3 + n) else Px.argb(255, 50 + n, 110 + n, 50 + n)
        }
        val n = fx.steps.length()
        val sheet = BufferedImage((n + 1) * 164, 124, BufferedImage.TYPE_INT_RGB)
        for (k in 0..n) {
            val arr = org.json.JSONArray(); for (i in 0 until k) arr.put(fx.steps.get(i))
            val out = FxEngine.applyToImage(img.copy(), fx.copy(steps = arr), emptyMap(), 1f, "normal")
            for (y in 0 until h) for (x in 0 until w) sheet.setRGB(k * 164 + 2 + x, 2 + y, out.px[y * w + x] and 0xFFFFFF)
        }
        ImageIO.write(sheet, "png", File("build/fx-debug-$id.png"))
    }
}
