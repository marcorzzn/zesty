package com.example.fx

import org.json.JSONArray
import org.json.JSONObject

/**
 * Zesty FX — modello dati del catalogo effetti.
 *
 * Il catalogo è definito in JSON (public/assets/fx/) con un formato originale di Zesty,
 * condiviso fra l'app Android e la web app. Ogni effetto è una sequenza di "step":
 * ogni step invoca un'operazione primitiva (vedi FxOps) con argomenti che possono essere
 * numeri, colori "#rrggbb", oppure espressioni che usano i parametri dell'effetto ("$amount * 0.5").
 *
 * Per aggiungere un effetto basta aggiungere una voce JSON: nessuna modifica al codice.
 */

data class FxCategory(val id: String, val label: String)

enum class FxParamType { RANGE, COLOR, CHOICE, TOGGLE }

data class FxParam(
    val id: String,
    val label: String,
    val type: FxParamType,
    val min: Float = 0f,
    val max: Float = 100f,
    val default: Float = 0f,
    val step: Float = 1f,
    val defaultColor: Int = 0xFFFFFFFF.toInt(),
    val options: List<String> = emptyList()
)

data class FxEffect(
    val id: String,
    val label: String,
    val category: String,
    val params: List<FxParam>,
    val steps: JSONArray,
    val tags: List<String> = emptyList()
) {
    /**
     * Valori predefiniti dei parametri. I colori sono salvati come numero RGB 0..16777215
     * (rappresentabile esattamente in Float), l'alpha è sempre pieno.
     */
    fun defaultValues(): Map<String, Float> = params.associate { p ->
        p.id to when (p.type) {
            FxParamType.COLOR -> (p.defaultColor and 0xFFFFFF).toFloat()
            else -> p.default
        }
    }
}

class FxCatalog(
    val categories: List<FxCategory>,
    val effects: List<FxEffect>
) {
    private val byId = effects.associateBy { it.id }
    fun get(id: String): FxEffect? = byId[id]
    fun inCategory(categoryId: String): List<FxEffect> = effects.filter { it.category == categoryId }

    companion object {
        val EMPTY = FxCatalog(emptyList(), emptyList())

        /** Costruisce il catalogo dall'indice e dai file di categoria già letti come testo. */
        fun parse(indexJson: String, readFile: (String) -> String?): FxCatalog {
            val index = JSONObject(indexJson)
            val cats = mutableListOf<FxCategory>()
            val effects = mutableListOf<FxEffect>()
            val catArr = index.optJSONArray("categories") ?: JSONArray()
            for (i in 0 until catArr.length()) {
                val c = catArr.getJSONObject(i)
                cats += FxCategory(c.getString("id"), c.optString("label", c.getString("id")))
            }
            val files = index.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val text = readFile(files.getString(i)) ?: continue
                val root = JSONObject(text)
                val arr = root.optJSONArray("effects") ?: continue
                val defaultCat = root.optString("category", "")
                for (j in 0 until arr.length()) {
                    runCatching { effects += parseEffect(arr.getJSONObject(j), defaultCat) }
                }
            }
            return FxCatalog(cats, effects)
        }

        private fun parseEffect(o: JSONObject, defaultCat: String): FxEffect {
            val params = mutableListOf<FxParam>()
            val pa = o.optJSONArray("params") ?: JSONArray()
            for (i in 0 until pa.length()) {
                val p = pa.getJSONObject(i)
                val type = when (p.optString("type", "range")) {
                    "color" -> FxParamType.COLOR
                    "choice" -> FxParamType.CHOICE
                    "toggle" -> FxParamType.TOGGLE
                    else -> FxParamType.RANGE
                }
                val opts = p.optJSONArray("options")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
                params += FxParam(
                    id = p.getString("id"),
                    label = p.optString("label", p.getString("id")),
                    type = type,
                    min = p.optDouble("min", 0.0).toFloat(),
                    max = p.optDouble("max", if (type == FxParamType.CHOICE) (opts.size - 1).coerceAtLeast(0).toDouble() else 100.0).toFloat(),
                    default = if (type == FxParamType.COLOR) 0f else p.optDouble("default", 0.0).toFloat(),
                    step = p.optDouble("step", 1.0).toFloat(),
                    defaultColor = if (type == FxParamType.COLOR) FxColor.parse(p.optString("default", "#ffffff")) else 0,
                    options = opts
                )
            }
            val tags = o.optJSONArray("tags")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            return FxEffect(
                id = o.getString("id"),
                label = o.optString("label", o.getString("id")),
                category = o.optString("category", defaultCat),
                params = params,
                steps = o.getJSONArray("steps"),
                tags = tags
            )
        }
    }
}

/** Utilità colore condivise. */
object FxColor {
    fun parse(s: String): Int {
        val h = s.trim().removePrefix("#")
        return when (h.length) {
            3 -> {
                val r = h[0].digitToInt(16) * 17
                val g = h[1].digitToInt(16) * 17
                val b = h[2].digitToInt(16) * 17
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            6 -> (0xFF shl 24) or h.toLong(16).toInt()
            8 -> { // #RRGGBBAA, come in CSS
                val v = h.toLong(16)
                (((v and 0xFF) shl 24) or (v ushr 8)).toInt()
            }
            else -> 0xFFFFFFFF.toInt()
        }
    }
}

/**
 * Valutatore minimale di espressioni aritmetiche: numeri, $variabili, + - * /, parentesi,
 * e le funzioni min(a,b), max(a,b), abs(a), clamp(v,lo,hi).
 * Lo stesso linguaggio è implementato nella web app (fx-engine.js) per avere risultati identici.
 */
object FxExpr {
    fun eval(src: String, vars: Map<String, Double>): Double = Parser(src, vars).parseTop()

    private class Parser(val s: String, val vars: Map<String, Double>) {
        var i = 0
        fun parseTop(): Double { val v = expr(); skip(); return v }
        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun expr(): Double {
            var v = term()
            while (true) {
                skip()
                if (i >= s.length) return v
                when (s[i]) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
        }
        fun term(): Double {
            var v = unary()
            while (true) {
                skip()
                if (i >= s.length) return v
                when (s[i]) {
                    '*' -> { i++; v *= unary() }
                    '/' -> { i++; val d = unary(); v = if (d == 0.0) 0.0 else v / d }
                    else -> return v
                }
            }
        }
        fun unary(): Double {
            skip()
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return atom()
        }
        fun atom(): Double {
            skip()
            if (i >= s.length) return 0.0
            val c = s[i]
            if (c == '(') { i++; val v = expr(); skip(); if (i < s.length && s[i] == ')') i++; return v }
            if (c == '$') {
                i++
                val st = i
                while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                return vars[s.substring(st, i)] ?: 0.0
            }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(st, i).toDoubleOrNull() ?: 0.0
            }
            if (c.isLetter()) {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(st, i)
                skip()
                val args = mutableListOf<Double>()
                if (i < s.length && s[i] == '(') {
                    i++
                    while (true) {
                        skip()
                        if (i < s.length && s[i] == ')') { i++; break }
                        args += expr()
                        skip()
                        if (i < s.length && s[i] == ',') { i++; continue }
                        if (i < s.length && s[i] == ')') { i++; break }
                        break
                    }
                }
                return when (name) {
                    "min" -> if (args.size >= 2) minOf(args[0], args[1]) else args.firstOrNull() ?: 0.0
                    "max" -> if (args.size >= 2) maxOf(args[0], args[1]) else args.firstOrNull() ?: 0.0
                    "abs" -> kotlin.math.abs(args.firstOrNull() ?: 0.0)
                    "clamp" -> if (args.size >= 3) args[0].coerceIn(args[1], args[2]) else args.firstOrNull() ?: 0.0
                    // colore da tre canali 0..255 (arrotondati e limitati): "rgb(246 - $aged * 0.14, 238, 218)"
                    "rgb" -> if (args.size >= 3) {
                        val r = Math.round(args[0]).coerceIn(0, 255); val g = Math.round(args[1]).coerceIn(0, 255); val b = Math.round(args[2]).coerceIn(0, 255)
                        ((r shl 16) or (g shl 8) or b).toDouble()
                    } else 0.0
                    else -> 0.0
                }
            }
            i++
            return 0.0
        }
    }
}
