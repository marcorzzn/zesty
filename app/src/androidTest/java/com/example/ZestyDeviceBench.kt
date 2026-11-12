package com.example

import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.engine.AiSegmenter
import com.example.engine.CutoutEngine
import com.example.engine.SubjectEngine
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Misure di riferimento sul telefono (fase 0): tempi e memoria di IA e motore di Scontorna,
 * su una scena sintetica sempre uguale. Risultati nel log con etichetta "ZestyBench".
 * Avvio: gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ZestyDeviceBench
 */
@RunWith(AndroidJUnit4::class)
class ZestyDeviceBench {

    private fun log(msg: String) {
        Log.i("ZestyBench", msg)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "ZestyBench: $msg\n") })
    }

    private inline fun <T> timed(label: String, block: () -> T): T? {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            block().also { log("%-34s %6d ms   heap Java %4d MB  nativo %4d MB".format(label, SystemClock.elapsedRealtime() - t0, usedJavaMb(), Debug.getNativeHeapAllocatedSize() / 1048576)) }
        } catch (t: Throwable) {
            log("%-34s ERRORE %s".format(label, t.toString().take(120)))
            null
        }
    }

    private fun usedJavaMb(): Long { val r = Runtime.getRuntime(); return (r.totalMemory() - r.freeMemory()) / 1048576 }

    /** Scena: sfondo a righe con rumore + "persona" (testa e busto) al centro. */
    private fun scene(w: Int, h: Int): IntArray {
        val rnd = Random(42)
        val fx = w / 2f; val fy = h * 0.32f; val d = w * 0.08f
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val hx = (x - fx) / (1.2f * d); val hy = (y - fy) / (1.6f * d)
            val head = hx * hx + hy * hy < 1f
            val body = y > fy + 1.5f * d && kotlin.math.abs(x - fx) < d * (1f + 2.2f * ((y - fy - 1.5f * d) / (3f * d)).coerceIn(0f, 1f))
            val (r, g, b) = when {
                head -> Triple(228, 175, 140)
                body -> Triple(150, 30, 50)
                else -> when (((x + y / 2) / (w / 40 + 1)) % 4) { 0 -> Triple(40, 90, 160); 1 -> Triple(200, 200, 190); 2 -> Triple(60, 130, 60); else -> Triple(120, 70, 40) }
            }
            val n = rnd.nextInt(-12, 12)
            (0xFF shl 24) or ((r + n).coerceIn(0, 255) shl 16) or ((g + n).coerceIn(0, 255) shl 8) or (b + n).coerceIn(0, 255)
        }
    }

    private fun faces(px: IntArray, w: Int, h: Int): List<SubjectEngine.Face> {
        val fw = w and 1.inv(); val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val rgb = Bitmap.createScaledBitmap(bmp, fw, h, true).copy(Bitmap.Config.RGB_565, false)
        val found = arrayOfNulls<android.media.FaceDetector.Face>(4)
        val n = android.media.FaceDetector(fw, h, 4).findFaces(rgb, found)
        return (0 until n).mapNotNull { found[it] }.map { val p = android.graphics.PointF(); it.getMidPoint(p); SubjectEngine.Face(p.x, p.y, it.eyesDistance()) }
    }

    private fun runSize(w: Int, h: Int) {
        log("===== $w x $h (${w * h / 1_000_000.0} MP) =====")
        System.gc()
        val px = scene(w, h)
        val fakeFace = listOf(SubjectEngine.Face(w / 2f, h * 0.32f, w * 0.08f / 2.5f))
        timed("Volti (rilevatore Android)") { faces(px, w, h) }
        val sal = timed("IS-Net (con caricamento)") { AiSegmenter.salientObject(px, w, h) }
        timed("IS-Net (già caricato)") { AiSegmenter.salientObject(px, w, h) }
        val por = timed("MODNet (con caricamento)") { AiSegmenter.portraitMatte(px, w, h) }
        timed("MODNet (già caricato)") { AiSegmenter.portraitMatte(px, w, h) }
        if (sal != null) timed("Fusione (taglio + rifinitura)") { SubjectEngine.fuse(px, w, h, sal, por, fakeFace) }
        timed("Soggetto classico (senza IA)") { SubjectEngine.subject(px, w, h, fakeFace) }
        val lab = timed("Conversione CIELAB") { CutoutEngine.toLab(px, w, h) }
        if (lab != null) timed("Bacchetta (tocco)") { CutoutEngine.wand(lab, 5, 5, 18f, 0.35f, true, true) }
        timed("Rifinisci bordo r=6") { CutoutEngine.refineEdge(px, FloatArray(w * h) { if (it % w < w / 2) 1f else 0f }, w, h, 6) }
    }

    @Test
    fun baseline() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AiSegmenter.attach(ctx)
        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
        log("Limite heap Java ${Runtime.getRuntime().maxMemory() / 1048576} MB, memoryClass ${am.memoryClass} MB, largeMemoryClass ${am.largeMemoryClass} MB, core ${Runtime.getRuntime().availableProcessors()}")
        runSize(1200, 1600)  // anteprima tipica
        runSize(3000, 4000)  // foto da 12 MP a piena risoluzione
        log("IA disponibile: ${AiSegmenter.available}")
    }

    /** Fase 1: confronto delle impostazioni del motore IA (tempo e memoria). */
    @Test
    fun aiOptions() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AiSegmenter.attach(ctx)
        val w = 1536; val h = 2048 // tela massima dell'editor
        val px = scene(w, h)
        val configs = listOf(
            AiSegmenter.Config(xnnpack = false, threads = 4),
            AiSegmenter.Config(xnnpack = false, threads = 6),
            AiSegmenter.Config(xnnpack = false, threads = 8),
            AiSegmenter.Config(xnnpack = false, threads = 4, isnetSide = 768)
        )
        for (c in configs) {
            AiSegmenter.config = c
            System.gc()
            log("--- $c")
            timed("IS-Net 1° (con caricamento)") { AiSegmenter.salientObject(px, w, h) }
            timed("IS-Net 2°") { AiSegmenter.salientObject(px, w, h) }
            timed("MODNet 1°") { AiSegmenter.portraitMatte(px, w, h) }
            timed("MODNet 2°") { AiSegmenter.portraitMatte(px, w, h) }
            AiSegmenter.release()
            System.gc()
            log("dopo release: nativo ${Debug.getNativeHeapAllocatedSize() / 1048576} MB")
        }
    }

    /** Fase 1: tempi delle singole fasi della fusione (sulla tela massima). */
    @Test
    fun fusionStages() {
        val w = 1536; val h = 2048
        val px = scene(w, h)
        val ai = FloatArray(w * h) { i -> val x = i % w; val y = i / w; val dx = (x - w / 2f) / (w * 0.3f); val dy = (y - h * 0.55f) / (h * 0.4f); ((1.1f - kotlin.math.sqrt(dx * dx + dy * dy)) / 0.2f).coerceIn(0f, 1f) }
        val face = listOf(SubjectEngine.Face(w / 2f, h * 0.32f, w * 0.08f / 2.5f))
        SubjectEngine.trace = { stage, ms -> log("%-34s %6d ms".format(stage, ms)) }
        repeat(2) { timed("Fusione completa") { SubjectEngine.fuse(px, w, h, ai, ai, face) } }
        SubjectEngine.trace = null
        timed("Soggetto classico") { SubjectEngine.subject(px, w, h, face) }
        timed("Rifinisci bordo r=6") { CutoutEngine.refineEdge(px, ai, w, h, 6) }
        timed("Togli isolotti") { CutoutEngine.removeIslands(ai, w, h, 0.02f) }
    }

    /** "Soggetto" dall'inizio alla fine, come nell'app: volti in parallelo a IS-Net, poi MODNet e fusione. */
    @Test
    fun endToEnd() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AiSegmenter.attach(ctx)
        AiSegmenter.release()
        val w = 1536; val h = 2048
        val px = scene(w, h)
        val face = listOf(SubjectEngine.Face(w / 2f, h * 0.32f, w * 0.08f / 2.5f))
        repeat(2) { round ->
            timed("Soggetto completo (giro ${round + 1})") {
                val t = kotlin.concurrent.thread { faces(px, w, h) }
                val sal = AiSegmenter.salientObject(px, w, h)!!
                t.join()
                val por = AiSegmenter.portraitMatte(px, w, h)
                SubjectEngine.fuse(px, w, h, sal, por, face)
            }
        }
        AiSegmenter.release(); System.gc()
        log("dopo release: nativo ${Debug.getNativeHeapAllocatedSize() / 1048576} MB")
    }
}
