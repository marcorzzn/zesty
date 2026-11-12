package com.example.engine

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Pacchetti IA scaricati al primo uso.
 *
 * - Il **registro** (assets/models/registry.json, condiviso con la webapp) elenca per ogni modello
 *   indirizzi, peso, impronta SHA-256 e licenza.
 * - Lo **scaricatore** riprende i download interrotti, prova gli indirizzi in ordine e accetta il file
 *   solo se l'impronta coincide: un file alterato o incompleto non viene mai usato.
 * - I file restano nella memoria privata dell'app: dopo la prima volta funziona senza rete.
 *
 * Solo Java/Kotlin standard: verificabile con test JVM.
 */
object ModelRegistry {
    data class Entry(
        val id: String,
        val file: String,
        val urls: List<String>,
        val sha256: String,
        val bytes: Long,
        val title: String,
        val purpose: String,
        val license: String,
        val source: String
    ) {
        val megabytes: Int get() = ((bytes + 524_288) / 1_048_576).toInt()
    }

    fun parse(json: String): List<Entry> {
        val arr = JSONObject(json).getJSONArray("models")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val urls = o.getJSONArray("urls")
            Entry(
                id = o.getString("id"),
                file = o.getString("file"),
                urls = (0 until urls.length()).map { urls.getString(it) },
                sha256 = o.getString("sha256").lowercase(),
                bytes = o.getLong("bytes"),
                title = o.getString("title"),
                purpose = o.optString("purpose"),
                license = o.getString("license"),
                source = o.optString("source")
            )
        }
    }
}

class ModelStore(private val dir: File) {

    class DownloadException(msg: String, cause: Throwable? = null) : IOException(msg, cause)

    init { dir.mkdirs() }

    fun fileOf(e: ModelRegistry.Entry) = File(dir, e.file)
    private fun partOf(e: ModelRegistry.Entry) = File(dir, e.file + ".part")
    private fun okOf(e: ModelRegistry.Entry) = File(dir, e.file + ".ok")

    /** Pronto all'uso: scaricato per intero e con l'impronta verificata (segnata in un file .ok). */
    fun isReady(e: ModelRegistry.Entry): Boolean {
        val f = fileOf(e); val ok = okOf(e)
        return f.isFile && f.length() == e.bytes && ok.isFile && ok.readText().trim() == e.sha256
    }

    /**
     * Scarica (o completa) il modello. [progress] riceve byte scaricati e totali;
     * [cancelled] permette di interrompere: il file parziale resta e il prossimo tentativo riprende da lì.
     */
    fun download(e: ModelRegistry.Entry, progress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }): File {
        if (isReady(e)) return fileOf(e)
        var last: Throwable? = null
        for (url in e.urls) {
            try {
                fetch(url, e, progress, cancelled)
                val part = partOf(e)
                val sha = sha256(part)
                if (sha != e.sha256) {
                    part.delete() // file diverso da quello atteso: si riparte da zero con l'indirizzo successivo
                    throw DownloadException("impronta diversa da quella attesa")
                }
                val f = fileOf(e)
                f.delete()
                if (!part.renameTo(f)) throw DownloadException("impossibile salvare il modello")
                okOf(e).writeText(e.sha256)
                return f
            } catch (c: CancelledException) {
                throw c
            } catch (t: Throwable) {
                last = t
            }
        }
        throw DownloadException("download non riuscito: ${last?.message ?: "nessun indirizzo disponibile"}", last)
    }

    class CancelledException : IOException("download interrotto")

    private fun fetch(url: String, e: ModelRegistry.Entry, progress: (Long, Long) -> Unit, cancelled: () -> Boolean) {
        val part = partOf(e)
        var have = if (part.isFile) part.length() else 0L
        if (have > e.bytes) { part.delete(); have = 0L }
        if (have == e.bytes) return
        var target = URL(url)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) { // segue i reindirizzamenti anche tra http e https (es. Hugging Face → CDN)
            conn = (target.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Zesty")
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 8) {
                val loc = conn.getHeaderField("Location") ?: throw DownloadException("reindirizzamento senza destinazione")
                conn.disconnect()
                target = URL(target, loc)
                redirects++
                continue
            }
            break
        }
        val code = conn.responseCode
        val append = when (code) {
            206 -> true
            200 -> { have = 0L; false } // il server non riprende: si ricomincia
            416 -> { conn.disconnect(); part.delete(); throw DownloadException("intervallo non valido") }
            else -> { conn.disconnect(); throw DownloadException("risposta HTTP $code") }
        }
        conn.inputStream.use { input ->
            FileOutputStream(part, append).use { out ->
                val buf = ByteArray(64 * 1024)
                var done = have
                progress(done, e.bytes)
                while (true) {
                    if (cancelled()) throw CancelledException()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done > e.bytes) throw DownloadException("file più grande del previsto")
                    progress(done, e.bytes)
                }
            }
        }
        conn.disconnect()
        if (part.length() != e.bytes) throw DownloadException("download incompleto (${part.length()} di ${e.bytes} byte)")
    }

    /** Spazio occupato dai modelli scaricati. */
    fun usedBytes(): Long = dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun delete(e: ModelRegistry.Entry) {
        fileOf(e).delete(); partOf(e).delete(); okOf(e).delete()
    }

    companion object {
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(f).use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
