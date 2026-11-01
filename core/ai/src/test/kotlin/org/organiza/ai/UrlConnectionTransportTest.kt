package org.organiza.ai

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UrlConnectionTransportTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val transport = UrlConnectionTransport()
    private var receivedBody = ""
    private var receivedAuth: String? = null
    private var redirectFollowed = false

    private val url get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/ok") { exchange ->
            receivedBody = exchange.requestBody.readBytes().decodeToString()
            receivedAuth = exchange.requestHeaders.getFirst("Authorization")
            val reply = "fine".toByteArray()
            exchange.sendResponseHeaders(200, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        server.createContext("/limited") { exchange ->
            val reply = "slow down".toByteArray()
            exchange.sendResponseHeaders(429, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "$url/elsewhere")
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        server.createContext("/elsewhere") { exchange ->
            redirectFollowed = true
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun `posts the body and headers`() {
        val reply = transport.post("$url/ok", mapOf("Authorization" to "Bearer k"), "hello")
        assertEquals(200, reply.status)
        assertEquals("fine", reply.body)
        assertEquals("hello", receivedBody)
        assertEquals("Bearer k", receivedAuth)
    }

    @Test
    fun `error bodies are readable`() {
        val reply = transport.post("$url/limited", emptyMap(), "x")
        assertEquals(429, reply.status)
        assertEquals("slow down", reply.body)
    }

    @Test
    fun `redirects are never followed`() {
        assertEquals(307, transport.post("$url/redirect", mapOf("Authorization" to "Bearer k"), "x").status)
        assertEquals(false, redirectFollowed)
    }

    @Test
    fun `an unreachable server throws io exception`() {
        val closedPort = "http://127.0.0.1:1"
        assertFailsWith<IOException> { transport.post("$closedPort/x", emptyMap(), "x") }
    }
}
