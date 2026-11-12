package com.example.engine

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.random.Random

/** Scaricatore dei pacchetti IA con un piccolo server HTTP locale (niente rete vera). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModelStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val data = Random(1).nextBytes(300_000)
    private val sha = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private lateinit var server: HttpServer
    private var base = ""
    private val rangesSeen = mutableListOf<String?>()
    private var honourRange = true

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/model.bin") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            rangesSeen += range
            if (range != null && honourRange) {
                val from = range.removePrefix("bytes=").substringBefore("-").toInt()
                val body = data.copyOfRange(from, data.size)
                ex.responseHeaders.add("Content-Range", "bytes $from-${data.size - 1}/${data.size}")
                ex.sendResponseHeaders(206, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            } else {
                ex.sendResponseHeaders(200, data.size.toLong())
                ex.responseBody.use { it.write(data) }
            }
        }
        server.createContext("/missing.bin") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        server.createContext("/moved.bin") { ex ->
            ex.responseHeaders.add("Location", "/model.bin")
            ex.sendResponseHeaders(302, -1); ex.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After fun stop() { server.stop(0) }

    private fun entry(vararg paths: String, hash: String = sha) = ModelRegistry.Entry(
        id = "test", file = "model.bin", urls = paths.map { base + it }, sha256 = hash, bytes = data.size.toLong(),
        title = "Prova", purpose = "", license = "MIT", source = ""
    )

    @Test fun downloadsAndVerifies() {
        val store = ModelStore(tmp.newFolder())
        val e = entry("/model.bin")
        assertFalse(store.isReady(e))
        var lastProgress = 0L
        val f = store.download(e, progress = { d, _ -> lastProgress = d })
        assertArrayEquals(data, f.readBytes())
        assertTrue(store.isReady(e))
        assertEquals(data.size.toLong(), lastProgress)
    }

    @Test fun resumesAnInterruptedDownload() {
        val dir = tmp.newFolder()
        java.io.File(dir, "model.bin.part").writeBytes(data.copyOfRange(0, 120_000))
        val f = ModelStore(dir).download(entry("/model.bin"))
        assertArrayEquals(data, f.readBytes())
        assertEquals("bytes=120000-", rangesSeen.last())
    }

    @Test fun restartsWhenTheServerIgnoresTheRange() {
        honourRange = false
        val dir = tmp.newFolder()
        java.io.File(dir, "model.bin.part").writeBytes(data.copyOfRange(0, 50_000))
        val f = ModelStore(dir).download(entry("/model.bin"))
        assertArrayEquals(data, f.readBytes())
    }

    @Test fun rejectsAFileWithTheWrongFingerprint() {
        val store = ModelStore(tmp.newFolder())
        val e = entry("/model.bin", hash = "0".repeat(64))
        try { store.download(e); fail("doveva rifiutare il file") } catch (_: ModelStore.DownloadException) {}
        assertFalse(store.isReady(e))
        assertFalse(store.fileOf(e).exists())
    }

    @Test fun triesTheNextAddressAndFollowsRedirects() {
        val f = ModelStore(tmp.newFolder()).download(entry("/missing.bin", "/moved.bin"))
        assertArrayEquals(data, f.readBytes())
    }

    @Test fun cancellingKeepsThePartialFile() {
        val dir = tmp.newFolder()
        val store = ModelStore(dir)
        var seen = 0L
        try {
            store.download(entry("/model.bin"), progress = { d, _ -> seen = d }, cancelled = { seen > 100_000 })
            fail("doveva interrompersi")
        } catch (_: ModelStore.CancelledException) {}
        assertTrue(java.io.File(dir, "model.bin.part").length() > 0)
        // il secondo tentativo riprende e completa
        assertArrayEquals(data, store.download(entry("/model.bin")).readBytes())
    }

    @Test fun parsesTheRegistry() {
        val json = """{"models":[{"id":"ddcolor","file":"ddcolor.onnx","urls":["https://a/x"],"sha256":"ABC","bytes":1048576,
            "title":"Colore automatico","purpose":"colorizza","license":"Apache-2.0","source":"https://github.com/piddnad/DDColor"}]}"""
        val e = ModelRegistry.parse(json).single()
        assertEquals("abc", e.sha256)
        assertEquals(1, e.megabytes)
        assertEquals(listOf("https://a/x"), e.urls)
    }
}
