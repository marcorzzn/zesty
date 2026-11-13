package com.example.fx

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/**
 * Zesty FX — motore che esegue le pipeline del catalogo.
 *
 * Chiavi comuni a ogni step (oltre a quelle dell'operazione):
 *   "op"      nome dell'operazione
 *   "blend"   metodo di fusione del risultato sull'immagine di ingresso (default "normal")
 *   "opacity" 0..1 (o espressione) quanto applicare lo step
 *   "mask"    { "type": "radial" | "linear" | "luma", ... } limita lo step a una zona
 *   "if"      espressione: lo step viene eseguito solo se il valore è diverso da 0
 *
 * L'operazione speciale "layer" esegue una sotto-pipeline ("steps") sulla copia dell'immagine
 * e la fonde sopra: permette effetti composti (bagliore Orton, HDR, neon...).
 */
object FxEngine {

    @Volatile
    var catalog: FxCatalog = FxCatalog.EMPTY
        private set

    /** Nei test: le eccezioni delle operazioni non vengono nascoste. */
    @Volatile
    var strict: Boolean = false

    /** Imposta un catalogo già costruito (test, web bridge, cataloghi aggiuntivi). */
    fun setCatalog(c: FxCatalog) { catalog = c }

    private val ops: Map<String, FxOp> by lazy {
        FxOpsColor.ops + FxOpsSpatial.ops + FxOpsDistort.ops + FxOpsPalette.ops + FxOpsArt.ops
    }

    /** Elenco delle operazioni disponibili (utile per documentazione e validazione del catalogo). */
    val operationNames: Set<String> get() = ops.keys + "layer"

    /** Carica il catalogo dagli asset dell'APK (copiati da public/assets/fx durante la build). */
    fun load(context: Context) {
        if (catalog.effects.isNotEmpty()) return
        val am = context.assets
        fun read(path: String): String? = runCatching {
            am.open("www/assets/fx/$path").bufferedReader().use { it.readText() }
        }.getOrNull()
        val index = read("index.json") ?: return
        catalog = runCatching { FxCatalog.parse(index, ::read) }.getOrDefault(FxCatalog.EMPTY)
    }

    /**
     * Applica un effetto del catalogo a una Bitmap.
     * @param strength 0..1 quanto effetto applicare (1 = pieno)
     * @param blend metodo di fusione finale dell'effetto sull'originale
     */
    fun apply(src: Bitmap, effectId: String, values: Map<String, Float>, strength: Float = 1f, blend: String = "normal"): Bitmap {
        val fx = catalog.get(effectId) ?: return src
        val w = src.width; val h = src.height
        val img = FxImage(w, h).also { src.getPixels(it.px, 0, w, 0, 0, w, h) }
        val res = applyToImage(img, fx, values, strength, blend)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(res.px, 0, w, 0, 0, w, h)
        return out
    }

    fun applyToImage(img: FxImage, fx: FxEffect, values: Map<String, Float>, strength: Float, blend: String): FxImage {
        val vars = HashMap<String, Double>()
        for ((k, v) in fx.defaultValues()) vars[k] = v.toDouble()
        for ((k, v) in values) vars[k] = v.toDouble()
        val seed = (vars["seed"] ?: 0.0).toInt() * 9973 + fx.id.hashCode()
        val result = runSteps(img, fx.steps, vars, seed)
        val s = strength.coerceIn(0f, 1f)
        val mode = FxBlend.normalize(blend)
        if (mode == "normal") {
            if (s >= 0.999f) return result
            return FxImage(img.w, img.h, IntArray(img.size) { Px.mix(img.px[it], result.px[it], s) })
        }
        return FxBlend.image(mode, img, result, s)
    }

    private fun runSteps(input: FxImage, steps: JSONArray, vars: Map<String, Double>, seed: Int): FxImage {
        var cur = input
        for (i in 0 until steps.length()) {
            val st = steps.optJSONObject(i) ?: continue
            cur = runStep(cur, st, vars, seed + i * 101)
        }
        return cur
    }

    private fun runStep(input: FxImage, st: JSONObject, vars: Map<String, Double>, seed: Int): FxImage {
        val args = FxArgs(st, vars)
        if (st.has("if") && !args.bool("if", true)) return input
        val opName = st.optString("op")
        val ctx = FxContext(seed) { img, steps -> runSteps(img, steps, vars, seed * 31 + 7) }
        val produced: FxImage = if (opName == "layer") {
            val sub = st.optJSONArray("steps") ?: return input
            ctx.runSteps(input, sub)
        } else {
            val op = ops[opName] ?: if (strict) error("Operazione sconosciuta: $opName") else return input
            if (strict) op.run(input, args, ctx)
            else runCatching { op.run(input, args, ctx) }.getOrElse { return input }
        }
        val opacity = args.num("opacity", 1f).coerceIn(0f, 1f)
        val blend = FxBlend.normalize(args.str("blend", "normal"))
        val maskObj = st.optJSONObject("mask")
        if (opacity >= 0.999f && blend == "normal" && maskObj == null) return produced
        val mask = maskObj?.let { buildMask(input, FxArgs(it, vars)) }
        val out = IntArray(input.size)
        if (blend == "normal") {
            // la fusione normale porta anche l'alpha (necessario per distorsioni e pattern trasparenti)
            val ip = input.px; val pp = produced.px
            for (i in out.indices) out[i] = Px.mix(ip[i], pp[i], if (mask != null) mask[i] * opacity else opacity)
        } else {
            FxBlend.blendInto(blend, input.px, produced.px, mask, opacity, out)
        }
        return FxImage(input.w, input.h, out)
    }

    /** Maschere procedurali 0..1 per localizzare uno step. */
    private fun buildMask(img: FxImage, a: FxArgs): FloatArray {
        val type = a.str("type", "radial")
        val invert = a.bool("invert", false)
        val w = img.w; val h = img.h
        val out = FloatArray(img.size)
        when (type) {
            "linear" -> { // fascia (tilt-shift): position = centro, width = ampiezza, feather = sfumatura
                val pos = a.num("position", 0.5f); val width = a.num("width", 0.25f); val feather = a.num("feather", 0.2f).coerceAtLeast(0.001f)
                val ang = Math.toRadians(a.num("angle", 0f).toDouble())
                val nx = -sin(ang).toFloat(); val ny = cos(ang).toFloat()
                for (i in out.indices) {
                    val u = (i % w).toFloat() / w - 0.5f; val v = (i / w).toFloat() / h - pos
                    val d = abs(u * nx + v * ny)
                    out[i] = Px.smooth(width / 2f, width / 2f + feather, d)
                }
            }
            "luma" -> { // zone tonali: from..to in 0..100
                val from = a.num("from", 50f) / 100f * 255f; val to = a.num("to", 100f) / 100f * 255f
                val soft = a.num("softness", 10f) / 100f * 255f
                for (i in out.indices) {
                    val l = Px.luma(img.px[i])
                    out[i] = Px.smooth(from - soft, from + soft, l) * (1f - Px.smooth(to - soft, to + soft, l))
                }
            }
            else -> { // radial: cx, cy, radius (0..1 del lato corto), feather
                val cx = a.num("cx", 0.5f) * w; val cy = a.num("cy", 0.5f) * h
                val r = a.num("radius", 0.3f) * min(w, h); val feather = (a.num("feather", 0.25f) * min(w, h)).coerceAtLeast(1f)
                for (i in out.indices) {
                    val dx = (i % w) - cx; val dy = (i / w) - cy
                    out[i] = 1f - Px.smooth(r, r + feather, sqrt(dx * dx + dy * dy))
                }
            }
        }
        if (invert) for (i in out.indices) out[i] = 1f - out[i]
        return out
    }
}
