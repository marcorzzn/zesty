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

    /** Fase 1b: costo di un movimento di cursore nell'editor (ricomposizione completa della tela). */
    @Test
    fun editorPipeline() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        com.example.fx.FxEngine.load(ctx)
        val cat = com.example.fx.FxEngine.catalog
        log("Catalogo effetti: ${cat.effects.size} effetti; bw_classic presente: ${cat.get("bw_classic") != null}")
        val w = 1536; val h = 2048
        val bmp = Bitmap.createBitmap(scene(w, h), w, h, Bitmap.Config.ARGB_8888)
        val base = com.example.model.EditorLayer(id = "foto", type = com.example.model.LayerType.IMAGE, name = "foto", w = w.toFloat(), h = h.toFloat(), bitmap = bmp)
        val text = com.example.model.EditorLayer(id = "testo", type = com.example.model.LayerType.TEXT, name = "testo", x = 100f, y = 200f, w = 900f, h = 300f)
        fun compose(layers: List<com.example.model.EditorLayer>) =
            com.example.engine.PixelEngine.renderComposite(w, h, 0xFF000000.toInt(), 0, 0f, 0f, 0f, 0, layers)
        fun series(label: String, n: Int = 8, layerAt: (Int) -> List<com.example.model.EditorLayer>) {
            compose(layerAt(-1))
            val times = LongArray(n) { k -> val t0 = SystemClock.elapsedRealtime(); compose(layerAt(k)); SystemClock.elapsedRealtime() - t0 }
            log("%-40s mediana %4d ms  (min %d, max %d)".format(label, times.sorted()[n / 2], times.minOrNull(), times.maxOrNull()))
        }
        series("Nessun cambiamento (tutto in cache)") { listOf(base, text) }
        com.example.engine.PixelEngine.interactivePreview = true
        series("Luminosità durante il trascinamento") { k -> listOf(base.copy(adjust = com.example.model.AdjustParams(brightness = 10f + k)), text) }
        com.example.engine.PixelEngine.interactivePreview = false
        series("Luminosità a piena risoluzione", 3) { k -> listOf(base.copy(adjust = com.example.model.AdjustParams(brightness = 50f + k)), text) }
        for (fx in listOf("bw_classic", "film_warm", "blur_gauss", "light_glow", "draw_pencil", "tex_grain")) {
            com.example.engine.PixelEngine.interactivePreview = true
            series("$fx durante il trascinamento") { k -> listOf(base.copy(activeFilterId = fx, activeFilterFade = k.toFloat()), text) }
            com.example.engine.PixelEngine.interactivePreview = false
            series("$fx a piena risoluzione", 3) { k -> listOf(base.copy(activeFilterId = fx, activeFilterFade = 50f + k), text) }
        }
        // costo della sola tela vuota e del caricamento sulla GPU non misurabili qui: solo la parte CPU
        val t0 = SystemClock.elapsedRealtime()
        repeat(10) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
        log("Nuova bitmap 1536×2048 (media)            %4d ms".format((SystemClock.elapsedRealtime() - t0) / 10))
    }

    /** Effetti di pittura e tecniche nuove: tempo all'anteprima (720×960) e a piena risoluzione (1536×2048). */
    @Test
    fun paintEffects() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        com.example.fx.FxEngine.load(ctx)
        val cat = com.example.fx.FxEngine.catalog
        for ((w, h) in listOf(720 to 960, 1536 to 2048)) {
            val src = com.example.fx.FxImage(w, h, scene(w, h))
            for (id in listOf("art_impressionism", "art_swirl", "art_oil_brush", "art_pastel_strokes", "comic_speed", "poster_torn", "film_aerochrome")) {
                val fx = cat.get(id) ?: run { log("$id mancante"); continue }
                com.example.fx.FxEngine.applyToImage(src.copy(), fx, emptyMap(), 1f, "normal") // riscaldamento
                val t0 = SystemClock.elapsedRealtime()
                com.example.fx.FxEngine.applyToImage(src.copy(), fx, emptyMap(), 1f, "normal")
                log("%-22s %4d×%-4d %5d ms".format(id, w, h, SystemClock.elapsedRealtime() - t0))
            }
        }
    }

    /** Colorizza sul telefono: download verificato del pacchetto DDColor (se manca) e tempi del colore. */
    @Test
    fun colorize() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AiSegmenter.attach(ctx)
        com.example.engine.AiPacks.attach(ctx)
        var lastPct = -10L
        val file = timed("Pacchetto DDColor (scarica o trova)") {
            com.example.engine.AiPacks.download("ddcolor_tiny", progress = { d, t ->
                val pct = d * 100 / t
                if (pct >= lastPct + 10) { lastPct = pct; log("  download $pct%") }
            }, cancelled = { false })
        } ?: return
        log("File: ${file.length() / 1048576} MB, pronto: ${com.example.engine.AiPacks.isReady("ddcolor_tiny")}")
        val w = 1536; val h = 2048
        val px = scene(w, h)
        val lum = timed("Luminosità") { com.example.engine.ColorizeEngine.lightness(px) }!!
        val input = com.example.engine.ColorizeEngine.modelInput(lum, w, h)
        val ab = timed("DDColor 1° (con caricamento)") { AiSegmenter.runFile(file, input, 512, 512) }
        timed("DDColor 2°") { AiSegmenter.runFile(file, input, 512, 512) }
        if (ab != null) {
            var mx = 0f; for (v in ab) mx = maxOf(mx, kotlin.math.abs(v))
            log("Uscita: ${ab.size} valori, |ab| massimo ${"%.1f".format(mx)}")
            timed("Ricomposizione a 1536×2048") { com.example.engine.ColorizeEngine.compose(px, w, h, ab, com.example.engine.ColorizeEngine.Look()) }
        }
        AiSegmenter.release()
    }

    /** Cancella oggetti sul telefono: pacchetto LaMa, un oggetto rosso su prato e cielo viene tolto. */
    @Test
    fun erase() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AiSegmenter.attach(ctx)
        com.example.engine.AiPacks.attach(ctx)
        val file = timed("Pacchetto LaMa (scarica o trova)") {
            com.example.engine.AiPacks.download("lama", progress = { _, _ -> }, cancelled = { false })
        } ?: return
        val w = 1536; val h = 2048
        // cielo sopra, prato sotto, un quadrato rosso a cavallo dell'orizzonte da togliere
        val px = IntArray(w * h) { i -> val y = i / w; if (y < h / 2) 0xFF7FB2E5.toInt() else 0xFF3C7A32.toInt() }
        val mask = FloatArray(w * h)
        for (y in 900 until 1150) for (x in 600 until 900) { px[y * w + x] = 0xFFD02020.toInt(); mask[y * w + x] = 1f }
        val crop = com.example.engine.InpaintEngine.crop(mask, w, h)!!
        val (image, m) = com.example.engine.InpaintEngine.inputs(px, mask, w, crop)
        var out: FloatArray? = null
        timed("Preparazione anticipata del modello") { AiSegmenter.prepare(file, mapOf("session.disable_quant_qdq" to "1")) }
        for (full in listOf(false)) repeat(2) { k ->
            out = timed("LaMa ${if (full) "ottimizzazione piena" else "ottimizzazione base"} ${k + 1}°") {
                AiSegmenter.runFileInputs(file, listOf("image" to (image to longArrayOf(1, 3, 512, 512)), "mask" to (m to longArrayOf(1, 1, 512, 512))),
                    mapOf("session.disable_quant_qdq" to "1"), fullOptimization = full)
            }
        }
        val o = out ?: return
        val res = timed("Ricomposizione") { com.example.engine.InpaintEngine.blend(px, mask, w, h, crop, o) }!!
        fun red(p: Int) = (p shr 16) and 255
        val sky = res[950 * w + 750]; val grass = res[1100 * w + 750]
        log("Uscita min %.0f max %.0f; cielo ricostruito #%06X, prato ricostruito #%06X (prima #D02020)".format(o.min(), o.max(), sky and 0xFFFFFF, grass and 0xFFFFFF))
        log("Rosso rimasto: cielo ${red(sky)}, prato ${red(grass)} (sotto 160 = oggetto tolto)")
        AiSegmenter.release()
    }
}
