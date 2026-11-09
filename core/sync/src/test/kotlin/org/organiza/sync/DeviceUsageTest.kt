package org.organiza.sync

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.organiza.ai.UrlConnectionTransport
import org.organiza.testing.FakeArenaRelay
import org.organiza.testing.FakeClock
import org.organiza.testing.FakeSecretStore

class DeviceUsageTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val relay = FakeArenaRelay()

    @Test
    fun `devices that share a code add up each other's minutes by app name`() {
        val link = DeviceLink.create()
        val phone = DeviceUsage(link, "phone", relay)
        val laptop = DeviceUsage(assertNotNull(DeviceLink.fromCode(link.code)), "laptop", relay)
        val desktop = DeviceUsage(link, "desktop", relay)

        phone.post(today, mapOf("Discord" to 40L, "Maps" to 5L))
        laptop.post(today, mapOf("discord " to 30L))
        // A later report from the same device replaces its earlier one; another day never counts.
        phone.post(today, mapOf("Discord" to 50L))
        laptop.post(today.minusDays(1), mapOf("Discord" to 500L))
        // A device a day ahead, in another time zone, keeps today's report too.
        laptop.post(today.plusDays(1), mapOf("Discord" to 7L))
        desktop.pull()

        assertEquals(80, desktop.minutes("DISCORD", today))
        assertEquals(7, desktop.minutes("Discord", today.plusDays(1)))
        assertEquals(0, desktop.minutes("Maps", today))
        assertEquals(2, desktop.devices(today))
        // A device never counts its own reports: the phone sees the laptop's 30, not its own 50.
        phone.pull()
        assertEquals(30, phone.minutes("Discord", today))
    }

    @Test
    fun `the relay sees one padded size and no app names`() {
        val link = DeviceLink.create()
        val device = DeviceUsage(link, "phone", relay)
        device.post(today, mapOf("Discord" to 40L))
        device.post(today, (1..60).associate { "Some long app name number $it" to it.toLong() })
        val stored = relay.stored.getValue(link.mailboxId)
        assertEquals(1, stored.map { it.size % 1024 }.toSet().size)
        stored.forEach { assertFalse("Discord" in String(it, Charsets.ISO_8859_1)) }
        // Even a long list stays under the relay's 8 KB message limit.
        assertTrue(stored.all { it.size <= 8192 })
    }

    @Test
    fun `another link's reports and malformed codes are refused`() {
        DeviceUsage(DeviceLink.create(), "stranger", relay).post(today, mapOf("Discord" to 99L))
        val mine = DeviceLink.create()
        relay.append(mine.mailboxId, relay.stored.values.first().first())
        val device = DeviceUsage(mine, "me", relay)
        device.pull()
        assertEquals(0, device.minutes("Discord", today))
        assertNull(DeviceLink.fromCode("organiza-devices:short"))
        assertNull(DeviceLink.fromCode(mine.code.replace("organiza-devices:", "organiza-invite:")))
    }

    @Test
    fun `two devices share minutes through a self-hosted supabase without changing its schema`() {
        LocalSupabase().use { backend ->
            val config = BackendConfig.Supabase(BackendKind.SELF_HOSTED, backend.url, backend.anonKey)
            val clock = FakeClock(Instant.parse("2026-11-09T12:00:00Z"))
            val transport = UrlConnectionTransport()
            fun device(name: String): DeviceUsage {
                val auth = SupabaseAuth(transport, config, FakeSecretStore(), clock)
                auth.signUp("me@example.org", "correct horse 1")
                auth.signIn("me@example.org", "correct horse 1")
                val sync = SupabaseSync(transport, config, auth)
                return DeviceUsage(LINK, name, sync.relayFor(checkNotNull(sync.defaultRelayUrl())))
            }
            val phone = device("phone")
            val laptop = device("laptop")
            phone.post(today, mapOf("Discord" to Duration.ofMinutes(45).toMinutes()))
            laptop.pull()
            assertEquals(45, laptop.minutes("Discord", today))
            backend.messages().forEach { (_, bytes) -> assertFalse("Discord" in String(bytes, Charsets.ISO_8859_1)) }
        }
    }

    private companion object {
        val LINK = DeviceLink.create()
    }
}
