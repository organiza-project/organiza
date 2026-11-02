package org.organiza.enforcement

import kotlin.test.Test
import kotlin.test.assertEquals

class BlockListTest {
    private val list = BlockList(setOf(pkg("com.instagram.android")), setOf(domain("reddit.com")))

    @Test
    fun `blocks listed packages only`() {
        assertEquals(true, list.blocks(pkg("com.instagram.android")))
        assertEquals(false, list.blocks(pkg("com.example.notes")))
    }

    @Test
    fun `blocks domains and their subdomains`() {
        assertEquals(true, list.blocks(domain("old.reddit.com")))
        assertEquals(false, list.blocks(domain("notreddit.com")))
    }

    @Test
    fun `empty list blocks nothing`() {
        assertEquals(false, BlockList.EMPTY.blocks(pkg("com.instagram.android")))
    }

    @Test
    fun `blocks listed processes`() {
        val steam = (org.organiza.domain.ProcessName.parse("steam") as org.organiza.domain.Parsed.Valid).value
        val withProcess = list.copy(processes = setOf(steam))
        assertEquals(true, withProcess.blocks(steam))
        assertEquals(false, list.blocks(steam))
    }
}
