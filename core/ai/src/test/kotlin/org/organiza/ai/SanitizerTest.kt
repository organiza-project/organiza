package org.organiza.ai

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SanitizerTest {
    private val sanitizer = Sanitizer()

    private fun clean(raw: String) = sanitizer.clean(raw).value

    @Test
    fun `plain goals pass through`() {
        assertEquals("Finish the thesis chapter before lunch", clean("Finish the thesis chapter before lunch"))
    }

    @Test
    fun `urls are removed entirely`() {
        assertEquals("read [URL] today", clean("read https://example.com/private?token=abc123 today"))
        assertEquals("see [URL]", clean("see www.reddit.com/r/all"))
        assertEquals("open [URL] now", clean("open reddit.com/r/all now"))
    }

    @Test
    fun `bare domains and package names are removed`() {
        assertEquals("stop using [URL]", clean("stop using tiktok.com"))
        assertEquals("too long on [URL]", clean("too long on com.instagram.android"))
    }

    @Test
    fun `ordinary sentence punctuation is not treated as a domain`() {
        assertEquals("Done.Next task", clean("Done.Next task"))
        assertEquals("version 2.5 is out", clean("version 2.5 is out"))
    }

    @Test
    fun `emails are removed before domains`() {
        assertEquals("write to [EMAIL]", clean("write to jane.doe+x@mail.example.org"))
    }

    @Test
    fun `ip addresses are removed`() {
        assertEquals("server [IP] is down", clean("server 192.168.1.20 is down"))
        assertEquals("host [IP]", clean("host 2001:0db8:85a3:0000:0000:8a2e:0370:7334"))
    }

    @Test
    fun `phone numbers in common shapes are removed`() {
        assertEquals("call [PHONE]", clean("call +33 6 12 34 56 78"))
        assertEquals("call [PHONE]", clean("call (415) 555-2671"))
        assertEquals("call [PHONE]", clean("call 415-555-2671"))
    }

    @Test
    fun `long digit runs are removed but short numbers stay`() {
        assertEquals("card [NUMBER] paid", clean("card 4111 1111 1111 1111 paid"))
        assertEquals("id [NUMBER]", clean("id 123456789"))
        assertEquals("read 30 pages by 2026", clean("read 30 pages by 2026"))
    }

    @Test
    fun `handles are removed`() {
        assertEquals("message [HANDLE] later", clean("message @jane_doe later"))
    }

    @Test
    fun `names are caught from context`() {
        assertEquals("Lunch with [NAME]", clean("Lunch with Sarah Connor"))
        assertEquals("call [NAME] back", clean("call John back"))
        assertEquals("see [NAME] at noon", clean("see Dr. Patel at noon"))
        assertEquals("help my wife [NAME]", clean("help my wife Fatima"))
    }

    @Test
    fun `names after common prepositions are caught`() {
        assertEquals("Prep for [NAME], then rest", clean("Prep for Sarah Connor, then rest"))
        assertEquals("a gift for [NAME]", clean("a gift for John"))
        assertEquals("notes from [NAME]", clean("notes from Priya"))
    }

    @Test
    fun `two capitalized words in a row are treated as a name`() {
        assertEquals("[NAME] called twice", clean("Sarah Connor called twice"))
        assertEquals("meeting [NAME] today", clean("meeting Jean-Luc Picard today"))
    }

    @Test
    fun `a timetable keeps its times, and its subjects can come back on the device`() {
        val names = mutableListOf<String>()
        val text = sanitizer.clean("Monday 08.00 09.30 11.00 Linear Algebra\nTuesday 10.00-11.30 Computer Science", names).value
        assertEquals("Monday 08.00 09.30 11.00 [NAME1] Tuesday 10.00-11.30 [NAME2]", text)
        assertEquals(listOf("Linear Algebra", "Computer Science"), names)
        // Without a list to keep them, a pair is a name as before; a dotted phone number is still one.
        assertEquals("[NAME] called twice", clean("Sarah Connor called twice"))
        assertEquals("call [PHONE]", clean("call 0612.345.678"))
        // A lone number that looks like a time, such as a room, stays what it was.
        assertEquals("Room 2.14", clean("Room 2.14"))
    }

    @Test
    fun `calendar words are not names`() {
        assertEquals("plan for Monday", clean("plan for Monday"))
        assertEquals("Monday Morning review", clean("Monday Morning review"))
        assertEquals("due from March", clean("due from March"))
    }

    @Test
    fun `context words before ordinary lowercase text change nothing`() {
        assertEquals("work with focus", clean("work with focus"))
        assertEquals("call the bank", clean("call the bank"))
    }

    @Test
    fun `private terms are removed whole word and case insensitive`() {
        val custom = Sanitizer(privateTerms = setOf("Acme Corp", "Radwan"))
        assertEquals("report for [PRIVATE] from [PRIVATE]", custom.clean("report for acme corp from RADWAN").value)
        assertEquals("Radwanski stays", custom.clean("Radwanski stays").value)
    }

    @Test
    fun `private terms with regex characters are safe`() {
        val custom = Sanitizer(privateTerms = setOf("a.b(c"))
        assertEquals("x [PRIVATE] y", custom.clean("x a.b(c y").value)
    }

    @Test
    fun `long input is truncated`() {
        assertEquals(Sanitizer.MAX_CHARS, clean("word ".repeat(5000)).length)
    }

    @Test
    fun `control characters and newlines are flattened`() {
        assertEquals("a b c", clean("a\nb\u0000\tc"))
    }

    @Test
    fun `cleaning twice gives the same result`() {
        val raw = "Lunch with Sarah Connor, call +33 6 12 34 56 78, https://a.example/b me@a.example"
        assertEquals(clean(raw), clean(clean(raw)))
    }

    @Test
    fun `a realistic note leaks nothing`() {
        val output = clean(
            "Ask Dr. Patel about results, email jane.doe@clinic.example, 415-555-2671, " +
                "portal https://clinic.example/patient/88231 and my husband Omar",
        )
        listOf("Patel", "jane", "clinic", "415", "88231", "Omar").forEach { assertFalse(it in output, it) }
    }

    @Test
    fun `reports what was removed`() {
        val result = sanitizer.clean("call John at 415-555-2671")
        assertTrue(result.removedAnything)
        assertFalse(sanitizer.clean("plain text").removedAnything)
    }

    @Test
    fun `random input never throws and leaves no raw url`() {
        val random = Random(11)
        val alphabet = "abc ABC.:/@+-()0123456789\n#\\[]"
        repeat(20_000) {
            val raw = String(CharArray(random.nextInt(0, 80)) { alphabet[random.nextInt(alphabet.length)] })
            assertFalse("://x." in clean("$raw http://x.example $raw"))
        }
    }
}
