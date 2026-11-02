package org.organiza.linux

import kotlin.test.Test
import kotlin.test.assertEquals

class CliArgsTest {
    @Test
    fun `parses each command`() {
        assertEquals(CliCommand.Send(Request.Status), CliArgs.parse(listOf("status")))
        assertEquals(CliCommand.Send(Request.Stop), CliArgs.parse(listOf("stop")))
        assertEquals(
            CliCommand.Send(Request.Start(30, listOf("steam", "discord"), listOf("reddit.com"), strict = true)),
            CliArgs.parse(listOf("start", "--minutes", "30", "--block", "steam,discord", "--site", "reddit.com", "--strict")),
        )
        assertEquals(CliCommand.ImportFile("/tmp/a.ics"), CliArgs.parse(listOf("import", "/tmp/a.ics")))
        assertEquals(CliCommand.RunDaemon, CliArgs.parse(listOf("daemon")))
        assertEquals(
            CliCommand.Send(Request.Rule("deepwork", listOf("steam"), listOf("x.com"), strict = false)),
            CliArgs.parse(listOf("rule", "--tag", "deepwork", "--block", "steam", "--site", "x.com")),
        )
    }

    @Test
    fun `bad input prints help instead of guessing`() {
        for (args in listOf(emptyList(), listOf("start", "--minutes"), listOf("start", "--minutes", "x"), listOf("launch"), listOf("import"), listOf("rule", "--block", "steam"))) {
            assertEquals(CliCommand.Help, CliArgs.parse(args), args.toString())
        }
    }
}
