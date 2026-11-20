import com.example.juke.services.StreamCache
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StreamCacheTest {
    @Test fun playbackAndPrefetchShareOneCompleteDownload() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        val bytes = ByteArray(8192) { it.toByte() }
        server.createContext("/audio") { exchange ->
            calls.incrementAndGet()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val url = "http://127.0.0.1:${server.address.port}/audio?${java.util.UUID.randomUUID()}"
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val readers = List(4) {
                pool.submit(Callable { start.await(); StreamCache.fetch(url).readBytes() })
            }
            start.countDown()
            readers.forEach { assertContentEquals(bytes, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
        } finally {
            pool.shutdownNow()
            server.stop(0)
            StreamCache.fileFor(url).delete()
        }
    }
}
