package org.organiza.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class BackendConfigTest {
    private fun check(url: String, key: String = ANON_KEY, insecure: Boolean = false) =
        BackendConfig.supabase(BackendKind.SELF_HOSTED, url, key, allowInsecure = insecure)

    @Test
    fun `an https project with an anon key is accepted and tidied`() {
        assertEquals(
            BackendCheck.Valid(BackendConfig.Supabase(BackendKind.SELF_HOSTED, "https://abc.supabase.co", ANON_KEY)),
            check("  https://abc.supabase.co/  ", "  $ANON_KEY "),
        )
    }

    @Test
    fun `a self-hosted instance can live under a path`() {
        assertEquals(
            "https://example.org/supabase",
            (check("https://example.org/supabase/") as BackendCheck.Valid).config.url,
        )
    }

    @Test
    fun `plain http is refused, except on loopback in debug builds`() {
        assertEquals(BackendCheck.Invalid(BackendProblem.INSECURE_URL), check("http://abc.supabase.co"))
        assertEquals(BackendCheck.Invalid(BackendProblem.INSECURE_URL), check("http://10.0.2.2:54321"))
        assertEquals(BackendCheck.Invalid(BackendProblem.INSECURE_URL), check("http://example.org", insecure = true))
        assertEquals("http://10.0.2.2:54321", (check("http://10.0.2.2:54321", insecure = true) as BackendCheck.Valid).config.url)
        assertEquals("http://127.0.0.1:54321", (check("http://127.0.0.1:54321", insecure = true) as BackendCheck.Valid).config.url)
    }

    @Test
    fun `malformed addresses are refused`() {
        listOf("", "abc.supabase.co", "https://", "https://abc.supabase.co?x=1", "https://abc.supabase.co#a", "https://a b.co")
            .forEach { assertEquals(BackendCheck.Invalid(BackendProblem.MALFORMED_URL), check(it), it) }
    }

    @Test
    fun `a service role key is refused, because it bypasses row-level security`() {
        assertEquals(BackendCheck.Invalid(BackendProblem.SECRET_KEY), check("https://abc.supabase.co", jwt("""{"role":"service_role"}""")))
        assertEquals(BackendCheck.Invalid(BackendProblem.SECRET_KEY), check("https://abc.supabase.co", "sb_secret_abcdefghijklmnop"))
    }

    @Test
    fun `a publishable key is accepted`() {
        assertEquals(true, check("https://abc.supabase.co", "sb_publishable_abcdefghijklmnop") is BackendCheck.Valid)
    }

    @Test
    fun `anything else is not an anon key`() {
        listOf("", "hello", jwt("""{"role":"authenticated"}"""), "a.b.c", jwt("not json"), "sb_publishable_")
            .forEach { assertEquals(BackendCheck.Invalid(BackendProblem.MALFORMED_KEY), check("https://abc.supabase.co", it), it) }
    }

    @Test
    fun `the anon key never prints`() {
        val config = (check("https://abc.supabase.co") as BackendCheck.Valid).config
        assertEquals(false, config.toString().contains(ANON_KEY))
    }
}
