package org.organiza.arena

import java.io.IOException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.organiza.testing.FakeHttpTransport

class HttpArenaRelayTest {
    private val transport = FakeHttpTransport()
    private val relay = HttpArenaRelay(transport, "https://relay.example/")

    @Test
    fun `append posts raw bytes to the mailbox`() {
        transport.respond(200, """{"cursor":1}""")
        relay.append("abc", byteArrayOf(1, 2))
        assertEquals("https://relay.example/v1/mailbox/abc/append", transport.requests.single().url)
    }

    @Test
    fun `fetch sends the cursor and decodes messages`() {
        val data = Base64.getEncoder().encodeToString(byteArrayOf(9))
        transport.respond(200, """{"messages":[{"cursor":5,"data":"$data"}]}""")
        val messages = relay.fetch("abc", 4)
        assertEquals(5, messages.single().cursor)
        assertEquals(listOf<Byte>(9), messages.single().data.toList())
        assertTrue("\"after\":4" in transport.requests.single().body)
    }

    @Test
    fun `failures and junk replies are io errors`() {
        transport.respond(500, "")
        assertFailsWith<IOException> { relay.append("abc", byteArrayOf(1)) }
        assertFailsWith<IOException> { relay.fetch("abc", 0) }
        transport.respond(200, "not json")
        assertFailsWith<IOException> { relay.fetch("abc", 0) }
    }

    @Test
    fun `no credentials or identifying headers are sent`() {
        transport.respond(200, """{"cursor":1}""")
        relay.append("abc", byteArrayOf(1))
        assertEquals(setOf("Content-Type"), transport.requests.single().headers.keys)
    }
}
