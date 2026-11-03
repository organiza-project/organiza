package org.organiza.sync

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.io.File
import java.sql.Connection
import java.sql.SQLException
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

/**
 * Runs `supabase/migrations` on a real PostgreSQL, with the parts of Supabase it relies on
 * stubbed: the anon and authenticated roles, their default grants, and `auth.uid()`.
 */
class SupabaseSchemaTest {
    private val sam = UUID.randomUUID()
    private val alex = UUID.randomUUID()
    private val mailbox = "ab".repeat(32)

    private fun connection(role: String, user: UUID?): Connection = postgres.postgresDatabase.connection.also { db ->
        db.createStatement().use { statement ->
            statement.execute("insert into auth.users (id) values ('$sam'), ('$alex') on conflict do nothing")
            statement.execute("set role $role")
            statement.execute("select set_config('request.jwt.claim.sub', '${user ?: ""}', false)")
        }
    }

    private fun Connection.append(box: String, data: ByteArray): Long =
        prepareStatement("select public.arena_append(?, ?)").use { statement ->
            statement.setString(1, box)
            statement.setString(2, Base64.getEncoder().encodeToString(data))
            statement.executeQuery().use { it.next(); it.getLong(1) }
        }

    private fun Connection.fetch(box: String, after: Long): List<Pair<Long, String>> =
        prepareStatement("select cursor, data from public.arena_fetch(?, ?)").use { statement ->
            statement.setString(1, box)
            statement.setLong(2, after)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1) to rows.getString(2)) } }
        }

    @Test
    fun `a signed-in member appends and fetches whole messages`() {
        connection("authenticated", sam).use { db ->
            val sealed = ByteArray(4124) { it.toByte() }
            val first = db.append(mailbox, sealed)
            val second = db.append(mailbox, byteArrayOf(7))
            val rows = db.fetch(mailbox, 0)
            assertEquals(listOf(first, second), rows.map { it.first })
            assertTrue(rows.none { '\n' in it.second })
            assertContentEquals(sealed, Base64.getDecoder().decode(rows[0].second))
            assertEquals(listOf(second), db.fetch(mailbox, first).map { it.first })
        }
    }

    @Test
    fun `another member of the challenge reads the same mailbox`() {
        val box = "cd".repeat(32)
        connection("authenticated", sam).use { it.append(box, byteArrayOf(1)) }
        connection("authenticated", alex).use { db -> assertEquals(1, db.fetch(box, 0).size) }
    }

    @Test
    fun `anonymous callers cannot use the functions`() {
        connection("anon", null).use { db ->
            assertFailsWith<SQLException> { db.append(mailbox, byteArrayOf(1)) }
            assertFailsWith<SQLException> { db.fetch(mailbox, 0) }
        }
    }

    @Test
    fun `a token without a user is refused`() {
        connection("authenticated", null).use { db -> assertFailsWith<SQLException> { db.append(mailbox, byteArrayOf(1)) } }
    }

    @Test
    fun `nobody can read or change the table directly`() {
        listOf("anon" to null, "authenticated" to sam).forEach { (role, user) ->
            connection(role, user).use { db ->
                db.createStatement().use { statement ->
                    assertFailsWith<SQLException> { statement.executeQuery("select * from public.arena_message") }
                    assertFailsWith<SQLException> { statement.executeUpdate("delete from public.arena_message") }
                }
            }
        }
    }

    @Test
    fun `malformed mailboxes and oversized messages are refused`() {
        connection("authenticated", sam).use { db ->
            assertFailsWith<SQLException> { db.append("not-a-mailbox", byteArrayOf(1)) }
            assertFailsWith<SQLException> { db.append(mailbox, ByteArray(8193)) }
        }
    }

    @Test
    fun `each member has a daily quota`() {
        val busy = UUID.randomUUID()
        postgres.postgresDatabase.connection.use { db ->
            db.createStatement().use { statement ->
                statement.execute("insert into auth.users (id) values ('$busy')")
                statement.execute(
                    "insert into public.arena_message (mailbox, data, sender) " +
                        "select '${"ef".repeat(32)}', '\\x01', '$busy' from generate_series(1, 500)",
                )
            }
        }
        connection("authenticated", busy).use { db -> assertFailsWith<SQLException> { db.append(mailbox, byteArrayOf(1)) } }
    }

    companion object {
        private lateinit var postgres: EmbeddedPostgres

        @BeforeAll
        @JvmStatic
        fun start() {
            postgres = EmbeddedPostgres.start()
            postgres.postgresDatabase.connection.use { db ->
                db.createStatement().use { statement ->
                    statement.execute(SupabaseStub.SQL)
                    migrations().forEach { statement.execute(it.readText()) }
                }
            }
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            postgres.close()
        }

        private fun migrations(): List<File> = SupabaseStub.migrations()

    }
}
