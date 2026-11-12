package com.example.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

/**
 * Modelli di scontorno eseguiti sul telefono (ONNX Runtime, nessuna rete):
 * - IS-Net general-use: qualunque soggetto (oggetti, animali, persone);
 * - MODNet: persone, con opacità fine sui capelli.
 *
 * Memoria: niente riserva interna di ONNX Runtime (che non restituisce mai la memoria) e modelli
 * chiusi con [release] dopo l'uso; ricaricarli costa ~0,1–0,2 s.
 * Se il telefono non li supporta [available] è false e Scontorna usa solo il motore classico di Zesty.
 * Licenze in assets/licenses.
 */
object AiSegmenter {
    private const val ISNET = "models/isnet_general.onnx"
    private const val MODNET = "models/modnet_portrait.onnx"

    /** Impostazioni del motore (regolabili per le misure sul telefono). */
    data class Config(
        val xnnpack: Boolean = false, // misurato sul telefono: con IS-Net 3× più lento e 550 MB non restituiti
        val threads: Int = 4,
        val arena: Boolean = false,
        val isnetSide: Int = 1024
    )

    @Volatile var config = Config()
        set(v) { field = v; release() }

    @Volatile private var app: Context? = null
    private var env: OrtEnvironment? = null
    private var isnet: OrtSession? = null
    private var modnet: OrtSession? = null
    @Volatile private var failed = false

    fun attach(context: Context) { app = context.applicationContext }

    val available: Boolean get() = app != null && !failed

    private fun options(c: Config) = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setCPUArenaAllocator(c.arena)
        setMemoryPatternOptimization(c.arena)
        if (c.xnnpack) {
            // XNNPACK usa i propri thread: quelli di ONNX Runtime restano 1 (indicazione della documentazione)
            addXnnpack(mapOf("intra_op_num_threads" to c.threads.toString()))
            setIntraOpNumThreads(1)
            addConfigEntry("session.intra_op.allow_spinning", "0")
        } else {
            setIntraOpNumThreads(c.threads)
        }
    }

    @Synchronized
    private fun session(path: String, current: OrtSession?, keep: (OrtSession) -> Unit): OrtSession? {
        if (current != null) return current
        val ctx = app ?: return null
        return try {
            val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
            val bytes = ctx.assets.open(path).use { it.readBytes() }
            val s = try {
                e.createSession(bytes, options(config))
            } catch (x: Exception) {
                // acceleratore non disponibile su questo telefono: processore normale
                e.createSession(bytes, options(config.copy(xnnpack = false)))
            }
            s.also(keep)
        } catch (t: Throwable) {
            // UnsatisfiedLinkError su processori non supportati, OutOfMemory su telefoni molto vecchi
            failed = true
            null
        }
    }

    /** Chiude i modelli e restituisce la loro memoria. */
    @Synchronized
    fun release() {
        runCatching { isnet?.close() }; isnet = null
        runCatching { modnet?.close() }; modnet = null
        runCatching { downloaded?.close() }; downloaded = null; downloadedPath = null
    }

    // modello scaricato (pacchetti IA): uno alla volta, aperto dal file senza copiarlo in memoria
    private var downloaded: OrtSession? = null
    private var downloadedPath: String? = null

    /**
     * Prepara (o riusa) la sessione di un modello scaricato. Chiamata in anticipo, all'apertura dello strumento,
     * toglie dall'attesa dell'utente la parte lenta (decine di secondi sui modelli grandi).
     */
    @Synchronized
    fun prepare(file: java.io.File, entries: Map<String, String> = emptyMap(), fullOptimization: Boolean = false): OrtSession {
        val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val key = file.absolutePath + entries.toString() + fullOptimization
        if (downloadedPath != key || downloaded == null) {
            runCatching { downloaded?.close() }
            downloaded = e.createSession(file.absolutePath, options(config.copy(xnnpack = false)).apply {
                setOptimizationLevel(if (fullOptimization) OrtSession.SessionOptions.OptLevel.ALL_OPT else OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                entries.forEach { (k, v) -> addConfigEntry(k, v) }
            })
            downloadedPath = key
        }
        return downloaded!!
    }

    /**
     * Esegue un modello scaricato con più ingressi (nome → valori e forma) e restituisce la prima uscita.
     * [entries] = voci di sessione (es. "session.disable_quant_qdq" per i modelli con pesi compressi).
     */
    @Synchronized
    fun runFileInputs(file: java.io.File, inputs: List<Pair<String, Pair<FloatArray, LongArray>>>, entries: Map<String, String> = emptyMap(),
                      fullOptimization: Boolean = false): FloatArray {
        val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val s = prepare(file, entries, fullOptimization)
        val tensors = inputs.map { (name, v) -> name to OnnxTensor.createTensor(e, FloatBuffer.wrap(v.first), v.second) }
        try {
            s.run(tensors.toMap()).use { res ->
                val out = res.get(0) as OnnxTensor
                val fb = out.floatBuffer
                return FloatArray(fb.remaining()).also { fb.get(it) }
            }
        } finally { tensors.forEach { it.second.close() } }
    }

    /**
     * Esegue un modello scaricato ([file] dal ModelStore) con un ingresso 1×3×h×w e restituisce la prima uscita.
     * Se manca l'acceleratore o il telefono non supporta un'operazione, l'errore risale al chiamante.
     */
    @Synchronized
    fun runFile(file: java.io.File, input: FloatArray, w: Int, h: Int): FloatArray {
        val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
        if (downloadedPath != file.absolutePath) {
            runCatching { downloaded?.close() }
            // ottimizzazione di base: le fusioni avanzate creano operazioni a mezza precisione (es. Gelu fp16)
            // che il processore del telefono non ha; le operazioni standard invece sì
            downloaded = e.createSession(file.absolutePath, options(config.copy(xnnpack = false)).apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            })
            downloadedPath = file.absolutePath
        }
        val s = downloaded!!
        OnnxTensor.createTensor(e, FloatBuffer.wrap(input), longArrayOf(1, 3, h.toLong(), w.toLong())).use { t ->
            s.run(mapOf(s.inputNames.first() to t)).use { res ->
                val out = res.get(0) as OnnxTensor
                val fb = out.floatBuffer
                return FloatArray(fb.remaining()).also { fb.get(it) }
            }
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
    @Synchronized // un calcolo alla volta; release() aspetta che finisca prima di chiudere il modello
    fun salientObject(px: IntArray, w: Int, h: Int): FloatArray? {
        val s = session(ISNET, isnet) { isnet = it } ?: return null
        val side = config.isnetSide
        val input = AiPrep.toChw(px, w, h, side, side, mean = 128f, std = 256f, scale = 1f)
        val out = run(s, input, side, side)
        AiPrep.normalizeMinMax(out)
        return SubjectEngine.upscale(out, side, side, w, h)
    }

    /** Opacità della persona (w×h, capelli inclusi), o null se il modello non è disponibile. */
    @Synchronized
    fun portraitMatte(px: IntArray, w: Int, h: Int): FloatArray? {
        val s = session(MODNET, modnet) { modnet = it } ?: return null
        val (tw, th) = AiPrep.modnetSize(w, h)
        val input = AiPrep.toChw(px, w, h, tw, th, mean = 0.5f, std = 0.5f, scale = 1f / 255f)
        val out = run(s, input, tw, th)
        return SubjectEngine.upscale(out, tw, th, w, h)
    }
}
