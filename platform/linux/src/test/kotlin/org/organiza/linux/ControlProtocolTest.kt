package org.organiza.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlProtocolTest {
    @Test
    fun `requests round trip`() {
        val start = Request.Start(minutes = 45, processes = listOf("steam"), domains = listOf("reddit.com"), strict = true)
        val rule = Request.Rule(tag = "deepwork", processes = listOf("steam"), domains = listOf("x.com"), strict = false)
        for (request in listOf(Request.Status, Request.Stop, start, rule, Request.Import("BEGIN:VCALENDAR"))) {
            assertEquals(request, ControlProtocol.parseRequest(ControlProtocol.encode(request)))
        }
    }

    @Test
    fun `replies round trip`() {
        val reply = Reply(ok = true, message = "Session started", status = listOf("Blocking 1 process"))
        assertEquals(reply, ControlProtocol.parseReply(ControlProtocol.encode(reply)))
    }

    @Test
    fun `junk and unknown commands are rejected not thrown`() {
        for (line in listOf("", "not json", "{}", """{"command":"format-disk"}""", """{"command":"start","minutes":"x"}""", "[1,2]")) {
            assertEquals(null, ControlProtocol.parseRequest(line), line)
        }
    }

    @Test
    fun `one request is one line`() {
        assertTrue('\n' !in ControlProtocol.encode(Request.Import("a\nb\r\nc")))
    }
}
