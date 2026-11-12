package com.example.fx

import org.json.JSONArray
import org.json.JSONObject

/**
 * Accesso tipizzato agli argomenti di uno step. Ogni valore può essere:
 * - un numero (12.5), un booleano, una stringa
 * - un colore "#rrggbb" oppure "$param" che punta a un parametro colore
 * - un'espressione "$amount * 0.4 + 10"
 */
class FxArgs(val o: JSONObject, val vars: Map<String, Double>) {

    fun has(k: String) = o.has(k)

    fun num(k: String, def: Float): Float {
        if (!o.has(k)) return def
        return when (val v = o.get(k)) {
            is Number -> v.toFloat()
            is Boolean -> if (v) 1f else 0f
            is String -> FxExpr.eval(v, vars).toFloat()
            // scelta numerica da un parametro: { "pick": "$bands", "from": [0, 2.39, 1.85, 1.37] }
            is JSONObject -> {
                val from = v.optJSONArray("from") ?: return def
                if (from.length() == 0) return def
                val idx = Math.round(numOf(v.opt("pick"))).coerceIn(0, from.length() - 1)
                from.optDouble(idx, def.toDouble()).toFloat()
            }
            else -> def
        }
    }

    fun int(k: String, def: Int): Int = Math.round(num(k, def.toFloat()))

    fun bool(k: String, def: Boolean): Boolean {
        if (!o.has(k)) return def
        return when (val v = o.get(k)) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> FxExpr.eval(v, vars) != 0.0
            else -> def
        }
    }

    /**
     * Stringa letterale, oppure scelta guidata da un parametro:
     * { "pick": "$mode", "from": ["left", "right", "top"] } restituisce la voce all'indice indicato.
     */
    fun str(k: String, def: String): String {
        if (!o.has(k)) return def
        val v = o.get(k)
        if (v is JSONObject) {
            val from = v.optJSONArray("from") ?: return def
            if (from.length() == 0) return def
            val idx = Math.round(numOf(v.opt("pick"))).coerceIn(0, from.length() - 1)
            return from.optString(idx, def)
        }
        return v.toString()
    }

    /** Sceglie una voce da una lista: accetta il nome o un'espressione che dà l'indice. */
    fun choice(k: String, options: List<String>, def: String): String {
        if (!o.has(k)) return def
        val v = o.get(k)
        if (v is String && v in options) return v
        val idx = when (v) {
            is Number -> v.toInt()
            is String -> FxExpr.eval(v, vars).toInt()
            else -> -1
        }
        return options.getOrElse(idx) { def }
    }

    fun color(k: String, def: Int): Int = if (!o.has(k)) def else colorOf(o.get(k), def)

    fun colorOf(v: Any?, def: Int): Int = when (v) {
        is String -> when {
            v.startsWith("#") -> FxColor.parse(v)
            else -> 0xFF000000.toInt() or (FxExpr.eval(v, vars).toLong().toInt() and 0xFFFFFF)
        }
        is Number -> 0xFF000000.toInt() or (v.toInt() and 0xFFFFFF)
        // colore scelto da un parametro: { "pick": "$mat", "from": ["#8a8a86", "#6a5f52"] }
        is JSONObject -> v.optJSONArray("from")?.takeIf { it.length() > 0 }?.let { from ->
            colorOf(from.get(Math.round(numOf(v.opt("pick"))).coerceIn(0, from.length() - 1)), def)
        } ?: def
        else -> def
    }

    fun colors(k: String): List<Int> {
        val a = o.optJSONArray(k) ?: return emptyList()
        return List(a.length()) { colorOf(a.get(it), 0xFF000000.toInt()) }
    }

    /** Lista di [posizione 0..1, colore]. */
    fun stops(k: String): List<Pair<Float, Int>> {
        val a = o.optJSONArray(k) ?: return emptyList()
        val out = mutableListOf<Pair<Float, Int>>()
        for (i in 0 until a.length()) {
            val s = a.optJSONArray(i) ?: continue
            out += numOf(s.get(0)) to colorOf(s.get(1), 0xFF000000.toInt())
        }
        return out.sortedBy { it.first }
    }

    /** Lista di punti [x, y] (per le curve). */
    fun points(k: String): List<Pair<Float, Float>> {
        val a = o.optJSONArray(k) ?: return emptyList()
        val out = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until a.length()) {
            val p = a.optJSONArray(i) ?: continue
            out += numOf(p.get(0)) to numOf(p.get(1))
        }
        return out.sortedBy { it.first }
    }

    fun nums(k: String): List<Float> {
        val a = o.optJSONArray(k) ?: return emptyList()
        return List(a.length()) { numOf(a.get(it)) }
    }

    fun numOf(v: Any?): Float = when (v) {
        is Number -> v.toFloat()
        is String -> FxExpr.eval(v, vars).toFloat()
        is Boolean -> if (v) 1f else 0f
        else -> 0f
    }

    fun arr(k: String): JSONArray? = o.optJSONArray(k)
    fun obj(k: String): JSONObject? = o.optJSONObject(k)
}
