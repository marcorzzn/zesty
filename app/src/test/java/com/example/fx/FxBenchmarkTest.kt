package com.example.fx

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Misura il tempo di ogni effetto su una foto 1080×1350 (JVM: i valori assoluti differiscono da un
 * telefono, l'ordine dei più lenti no). Scrive la classifica in build/fx-benchmark.txt.
 */
@RunWith(RobolectricTestRunner::class)
class FxBenchmarkTest {

    @Test
    fun timeAllEffects() {
        // si esegue solo su richiesta: ZESTY_BENCH=1 ./gradlew testDebugUnitTest --tests "*FxBenchmarkTest"
        org.junit.Assume.assumeTrue(System.getenv("ZESTY_BENCH") != null)
        val dir = listOf(File("../public/assets/fx"), File("public/assets/fx")).first { File(it, "index.json").exists() }
        val cat = FxCatalog.parse(File(dir, "index.json").readText()) { File(dir, it).takeIf { f -> f.exists() }?.readText() }
        val w = 1080; val h = 1350
        val img = FxImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val r = (x * 255 / w); val g = (y * 255 / h); val b = ((x + y) * 127 / (w + h)) + 64
            img.px[y * w + x] = Px.argb(255, r, g, (b + FxRand.r01(x, y, 1) * 40).toInt().coerceAtMost(255))
        }
        // riscaldamento della JVM
        repeat(3) { FxEngine.applyToImage(img.copy(), cat.get("bw_classic")!!, emptyMap(), 1f, "normal") }
        val times = cat.effects.map { fx ->
            val t0 = System.nanoTime()
            FxEngine.applyToImage(img.copy(), fx, emptyMap(), 1f, "normal")
            fx.id to (System.nanoTime() - t0) / 1_000_000
        }.sortedByDescending { it.second }
        val total = times.sumOf { it.second }
        val report = buildString {
            appendLine("Effetti: ${times.size}, foto ${w}x$h, totale ${total} ms, mediana ${times[times.size / 2].second} ms")
            appendLine("Oltre 150 ms: ${times.count { it.second > 150 }}")
            times.take(40).forEach { (id, ms) -> appendLine("%6d ms  %s".format(ms, id)) }
            appendLine("--- campioni ---")
            listOf("warm","bw_classic","light_vignette","tex_grain","blur_gauss","sharpen","clarity","denoise","draw_oil","blur_motion","glitch_classic","light_glow").forEach { id -> times.find { it.first == id }?.let { appendLine("%6d ms  %s".format(it.second, id)) } }
            appendLine("--- i più veloci ---")
            times.takeLast(15).forEach { (id, ms) -> appendLine("%6d ms  %s".format(ms, id)) }
        }
        File("build").mkdirs()
        File("build/fx-benchmark.txt").writeText(report)
        println(report)
    }
}
