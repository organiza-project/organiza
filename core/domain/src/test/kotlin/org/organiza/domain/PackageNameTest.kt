package org.organiza.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PackageNameTest {
    @Test
    fun `accepts a real package name`() {
        val parsed = PackageName.parse("com.instagram.android")
        assertEquals("com.instagram.android", (parsed as Parsed.Valid).value.value)
    }

    @Test
    fun `allows underscores and digits after the first character of a segment`() {
        assertEquals(true, PackageName.parse("com.foo_bar.app2") is Parsed.Valid)
    }

    @Test
    fun `rejects blank input`() {
        assertEquals(Parsed.Invalid(InvalidReason.BLANK), PackageName.parse("  "))
    }

    @Test
    fun `rejects a single segment`() {
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), PackageName.parse("instagram"))
    }

    @Test
    fun `rejects segments that start with a digit`() {
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), PackageName.parse("com.1foo.app"))
    }

    @Test
    fun `rejects empty segments`() {
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), PackageName.parse("com..app"))
    }
}
