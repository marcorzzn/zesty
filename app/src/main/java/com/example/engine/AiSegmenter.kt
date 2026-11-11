package com.example.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

/**
 * Modelli di scontorno eseguiti sul telefono (ONNX Runtime, nessuna rete):
 * - IS-Net general-use: qualunque soggetto (oggetti, animali, persone), 1024×1024;
 * - MODNet: persone, con opacità fine sui capelli.
 * I modelli si caricano alla prima richiesta e restano in memoria. Se il telefono non li supporta
 * (es. processore a 32 bit) [available] è false e Scontorna usa solo il motore classico di Zesty.
 * Licenze in assets/licenses.
 */
object AiSegmenter {
    private const val ISNET = "models/isnet_general.onnx"
    private const val MODNET = "models/modnet_portrait.onnx"

    @Volatile private var app: Context? = null
    private var env: OrtEnvironment? = null
    private var isnet: OrtSession? = null
    private var modnet: OrtSession? = null
    @Volatile private var failed = false

    fun attach(context: Context) { app = context.applicationContext }

    val available: Boolean get() = app != null && !failed

    @Synchronized
    private fun session(path: String, current: OrtSession?, keep: (OrtSession) -> Unit): OrtSession? {
        if (current != null) return current
        val ctx = app ?: return null
        return try {
            val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
            val bytes = ctx.assets.open(path).use { it.readBytes() }
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            e.createSession(bytes, opts).also(keep)
        } catch (t: Throwable) {
            // UnsatisfiedLinkError su processori non supportati, OutOfMemory su telefoni molto vecchi
            failed = true
            null
        }
    }

    private fun run(s: OrtSession, input: FloatArray, w: Int, h: Int): FloatArray {
        val e = env!!
        OnnxTensor.createTensor(e, FloatBuffer.wrap(input), longArrayOf(1, 3, h.toLong(), w.toLong())).use { t ->
            s.run(mapOf(s.inputNames.first() to t), setOf("output")).use { res ->
                val out = res.get(0) as OnnxTensor
                val fb = out.floatBuffer
                return FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
    }

    /** Probabilità di "soggetto" per ogni pixel (w×h), o null se il modello non è disponibile. */
    fun salientObject(px: IntArray, w: Int, h: Int): FloatArray? {
        val s = session(ISNET, isnet) { isnet = it } ?: return null
        val side = 1024
        val input = AiPrep.toChw(px, w, h, side, side, mean = 128f, std = 256f, scale = 1f)
        val out = run(s, input, side, side)
        AiPrep.normalizeMinMax(out)
        return SubjectEngine.upscale(out, side, side, w, h)
    }

    /** Opacità della persona (w×h, capelli inclusi), o null se il modello non è disponibile. */
    fun portraitMatte(px: IntArray, w: Int, h: Int): FloatArray? {
        val s = session(MODNET, modnet) { modnet = it } ?: return null
        val (tw, th) = AiPrep.modnetSize(w, h)
        val input = AiPrep.toChw(px, w, h, tw, th, mean = 0.5f, std = 0.5f, scale = 1f / 255f)
        val out = run(s, input, tw, th)
        return SubjectEngine.upscale(out, tw, th, w, h)
    }
}
