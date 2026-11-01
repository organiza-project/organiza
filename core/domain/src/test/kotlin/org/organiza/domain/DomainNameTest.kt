package org.organiza.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainNameTest {
    private fun valid(raw: String) = (DomainName.parse(raw) as Parsed.Valid).value

    @Test
    fun `normalises case and a trailing dot`() {
        assertEquals("www.reddit.com", valid("WWW.Reddit.com.").value)
    }

    @Test
    fun `rejects blank input`() {
        assertEquals(Parsed.Invalid(InvalidReason.BLANK), DomainName.parse(""))
    }

    @Test
    fun `rejects a single label`() {
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), DomainName.parse("localhost"))
    }

    @Test
    fun `rejects urls`() {
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), DomainName.parse("https://reddit.com/r/all"))
    }

    @Test
    fun `rejects labels with edge hyphens`() {
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), DomainName.parse("-bad.com"))
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), DomainName.parse("bad-.com"))
    }

    @Test
    fun `rejects ip addresses`() {
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), DomainName.parse("127.0.0.1"))
    }

    @Test
    fun `rejects labels over 63 characters`() {
        assertEquals(Parsed.Invalid(InvalidReason.TOO_LONG), DomainName.parse("a".repeat(64) + ".com"))
    }

    @Test
    fun `rejects names over 253 characters`() {
        val long = List(26) { "a".repeat(9) }.joinToString(".") + ".com"
        assertEquals(Parsed.Invalid(InvalidReason.TOO_LONG), DomainName.parse(long))
    }

    @Test
    fun `matches itself and subdomains but not lookalikes`() {
        val rule = valid("reddit.com")
        assertEquals(true, rule.covers(valid("reddit.com")))
        assertEquals(true, rule.covers(valid("old.reddit.com")))
        assertEquals(false, rule.covers(valid("notreddit.com")))
        assertEquals(false, valid("old.reddit.com").covers(rule))
    }
}
