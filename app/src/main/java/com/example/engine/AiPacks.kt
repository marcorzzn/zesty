package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import java.io.File

/**
 * Pacchetti IA sul telefono: registro dagli asset (ai/registry.json) e file nella memoria privata dell'app.
 * Vedi [ModelStore] per il download verificato.
 */
object AiPacks {
    @Volatile private var app: Context? = null
    @Volatile private var entries: List<ModelRegistry.Entry> = emptyList()
    @Volatile private var store: ModelStore? = null

    fun attach(context: Context) {
        val c = context.applicationContext
        app = c
        entries = runCatching { ModelRegistry.parse(c.assets.open("ai/registry.json").bufferedReader().use { it.readText() }) }
            .getOrDefault(emptyList())
        store = ModelStore(File(c.filesDir, "ai"))
    }

    fun entry(id: String): ModelRegistry.Entry? = entries.firstOrNull { it.id == id }

    fun isReady(id: String): Boolean = entry(id)?.let { store?.isReady(it) } ?: false

    fun file(id: String): File? = entry(id)?.let { e -> store?.takeIf { it.isReady(e) }?.fileOf(e) }

    fun download(id: String, progress: (Long, Long) -> Unit, cancelled: () -> Boolean): File {
        val e = entry(id) ?: throw ModelStore.DownloadException("pacchetto sconosciuto: $id")
        val s = store ?: throw ModelStore.DownloadException("pacchetti non inizializzati")
        return s.download(e, progress, cancelled)
    }

    /** Rete a consumo (dati mobili): prima di scaricare si avvisa. */
    fun isMetered(): Boolean = runCatching {
        (app?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered ?: false
    }.getOrDefault(false)
}
