package com.example.fx

import org.junit.Test

/** Misura su JVM pura (senza Robolectric) delle primitive più usate, ripetute per escludere il riscaldamento. */
class FxMicroBenchTest {
    private fun sample(w: Int, h: Int) = FxImage(w, h).also { img ->
        for (i in img.px.indices) img.px[i] = Px.argb(255, i % 251, (i / 7) % 253, (i / 13) % 241)
    }

    private inline fun timeIt(label: String, n: Int = 6, block: () -> Unit) {
        var best = Long.MAX_VALUE
        repeat(n) { val t = System.nanoTime(); block(); best = minOf(best, System.nanoTime() - t) }
        println("%-28s %6.1f ms (migliore di $n)".format(label, best / 1e6))
    }

    @Test
    fun primitives() {
        org.junit.Assume.assumeTrue(System.getenv("ZESTY_BENCH") != null)
        val img = sample(1080, 1350)
        println("JVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version") + " args=" +
                java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments.joinToString(" "))
        timeIt("taratura: somma 2,9M int") {
            var s = 0L
            val p = img.px
            for (k in 0 until 2) for (i in p.indices) s += p[i] and 0xFF
            if (s == 42L) println()
        }
        timeIt("box r=8") { FxBlur.box(img.px, img.w, img.h, 8) }
        timeIt("gaussian sigma=13") { FxBlur.gaussian(img.px, img.w, img.h, 13f) }
        val f = FloatArray(img.size) { (it % 255).toFloat() }
        timeIt("boxF r=8") { FxBlur.boxF(f, img.w, img.h, 8) }
        timeIt("blend overlay") { FxBlend.image("overlay", img, img, 0.7f) }
        timeIt("blend color") { FxBlend.image("color", img, img, 0.7f) }
        timeIt("bilinear 1x") {
            var acc = 0
            for (y in 0 until img.h) for (x in 0 until img.w) acc += FxSample.bilinear(img, x + 0.3f, y + 0.6f)
            if (acc == 42) println()
        }
    }
}
