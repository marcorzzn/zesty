package com.example.fx

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lavoro a strisce orizzontali su tutti i core. Ogni striscia scrive solo le sue righe, quindi il risultato
 * è identico a quello sequenziale. Per le operazioni pesanti (pennellate) che altrimenti userebbero un core solo.
 */
object FxParallel {
    private val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

    private val pool: ExecutorService by lazy {
        Executors.newFixedThreadPool(cores) { r -> Thread(r, "zesty-fx").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    }

    /** Esegue [block] su strisce [ya, yb) che coprono 0..h. */
    fun rows(h: Int, block: (Int, Int) -> Unit) {
        val parts = if (h < 64) 1 else min(cores * 2, h / 16)
        if (parts <= 1 || cores == 1) { block(0, h); return }
        val tasks = (0 until parts).map { k ->
            val ya = h * k / parts; val yb = h * (k + 1) / parts
            Callable { block(ya, yb) }
        }
        // invokeAll attende tutte le strisce; un errore in una striscia viene rilanciato qui
        for (f in pool.invokeAll(tasks)) f.get()
    }

    private fun min(a: Int, b: Int) = if (a < b) a else b
}
