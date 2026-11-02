package org.organiza.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.organiza.domain.DomainName
import org.organiza.domain.Parsed

class HostsFileTest {
    private fun domains(vararg raw: String) = raw.map { (DomainName.parse(it) as Parsed.Valid).value }.toSet()

    private val original = "127.0.0.1\tlocalhost\n127.0.1.1\tlaptop\n\n# my own entry\n10.0.0.5 nas.home\n"

    @Test
    fun `adds a marked block with common prefixes for ipv4 and ipv6`() {
        val text = HostsFile.render(original, domains("reddit.com"))
        assertTrue(text.startsWith(original))
        for (host in listOf("reddit.com", "www.reddit.com", "m.reddit.com")) {
            assertTrue("0.0.0.0 $host\n" in text, host)
            assertTrue(":: $host\n" in text, host)
        }
        assertTrue(HostsFile.BEGIN in text && HostsFile.END in text)
    }

    @Test
    fun `does not prefix a domain that already has www`() {
        val text = HostsFile.render(original, domains("www.example.com"))
        assertTrue("0.0.0.0 www.www.example.com" !in text)
    }

    @Test
    fun `rendering again replaces the block instead of adding another`() {
        val once = HostsFile.render(original, domains("reddit.com"))
        val twice = HostsFile.render(once, domains("x.com"))
        assertEquals(1, Regex(Regex.escape(HostsFile.BEGIN)).findAll(twice).count())
        assertTrue("reddit.com" !in twice)
        assertTrue("0.0.0.0 x.com" in twice)
    }

    @Test
    fun `an empty list removes the block and restores the file exactly`() {
        val blocked = HostsFile.render(original, domains("reddit.com"))
        assertEquals(original, HostsFile.render(blocked, emptySet()))
        assertEquals(original, HostsFile.render(original, emptySet()))
    }

    @Test
    fun `a file without a final newline keeps its last line intact`() {
        val text = HostsFile.render("127.0.0.1 localhost", domains("x.com"))
        assertTrue(text.startsWith("127.0.0.1 localhost\n"))
        assertEquals("127.0.0.1 localhost\n", HostsFile.render(text, emptySet()))
    }

    @Test
    fun `a damaged block with no end marker is removed to the end of the file`() {
        val damaged = original + HostsFile.BEGIN + "\n0.0.0.0 reddit.com\n"
        assertEquals(original, HostsFile.render(damaged, emptySet()))
    }

    @Test
    fun `output is sorted so rewrites are stable`() {
        assertEquals(HostsFile.render(original, domains("b.com", "a.com")), HostsFile.render(original, domains("a.com", "b.com")))
    }
}
