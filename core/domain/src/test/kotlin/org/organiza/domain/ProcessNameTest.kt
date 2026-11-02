package org.organiza.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessNameTest {
    @Test
    fun `accepts executable names and flatpak ids`() {
        for (name in listOf("firefox", "steam", "Discord", "code-oss", "com.discordapp.Discord", "python3.12", "g++")) {
            assertEquals(name, (ProcessName.parse(name) as Parsed.Valid).value.value, name)
        }
    }

    @Test
    fun `rejects paths spaces and blanks`() {
        assertEquals(Parsed.Invalid(InvalidReason.BLANK), ProcessName.parse(" "))
        for (name in listOf("/usr/bin/firefox", "my app", "../x", "-flag", "a;b")) {
            assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), ProcessName.parse(name), name)
        }
        assertEquals(Parsed.Invalid(InvalidReason.TOO_LONG), ProcessName.parse("x".repeat(256)))
    }
}
