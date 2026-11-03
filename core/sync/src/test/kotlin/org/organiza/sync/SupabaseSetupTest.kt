package org.organiza.sync

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs supabase/setup.sql over a project that already holds tables from earlier experiments. */
class SupabaseSetupTest {
    private fun Connection.names(sql: String): List<String> =
        createStatement().use { it.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } } }

    @Test
    fun `setup clears earlier experiments and installs the relay, and can run twice`() {
        EmbeddedPostgres.start().use { postgres ->
            postgres.postgresDatabase.connection.use { db ->
                db.createStatement().use { statement ->
                    statement.execute(SupabaseStub.SQL)
                    statement.execute(LEFTOVERS)
                    statement.execute(SupabaseStub.setup.readText())
                    statement.execute(SupabaseStub.setup.readText())
                }
                assertEquals(listOf("arena_message"), db.names("select tablename from pg_tables where schemaname = 'public'"))
                assertEquals(emptyList(), db.names("select viewname from pg_views where schemaname = 'public'"))
                assertEquals(
                    listOf("arena_append", "arena_fetch", "rls_auto_enable"),
                    db.names("select p.proname from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' order by 1"),
                )
                assertEquals(emptyList(), db.names("select tgname from pg_trigger where tgrelid = 'auth.users'::regclass and not tgisinternal"))
                assertEquals(emptyList(), db.names("select typname from pg_type t join pg_namespace n on n.oid = t.typnamespace where n.nspname = 'public' and t.typtype = 'e'"))
                assertEquals(listOf("1"), db.names("select count(*) from auth.users"), "accounts are kept")
                assertEquals(listOf("ensure_rls"), db.names("select evtname from pg_event_trigger"), "the RLS safeguard is kept")
                val user = db.names("select id from auth.users").single()
                db.createStatement().use { statement ->
                    statement.execute("set role authenticated")
                    statement.execute("select set_config('request.jwt.claim.sub', '$user', false)")
                }
                assertEquals(listOf("1"), db.names("select public.arena_append('${"ab".repeat(32)}', 'AQID')"))
            }
        }
    }

    @Test
    fun `the setup script carries the migration unchanged`() {
        val setup = SupabaseStub.setup.readText()
        SupabaseStub.migrations().forEach { assertTrue(it.readText() in setup, "${it.name} differs from setup.sql") }
    }

    private companion object {
        // The kind of thing a Supabase quickstart leaves behind, including a trigger on auth.users.
        val LEFTOVERS = """
            insert into auth.users (id) values ('${UUID.randomUUID()}');
            create type public.mood as enum ('good', 'bad');
            create table public.profiles (id uuid primary key references auth.users (id), name text, mood public.mood);
            create table public.challenges (id bigserial primary key, title text);
            create view public.challenge_titles as select title from public.challenges;
            create sequence public.stray_numbers;
            create function public.handle_new_user() returns trigger language plpgsql security definer as
                ${'$'}${'$'} begin insert into public.profiles (id) values (new.id); return new; end ${'$'}${'$'};
            create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();
            alter table public.profiles enable row level security;
            create policy "own profile" on public.profiles for select using (auth.uid() = id);
            create function public.rls_auto_enable() returns event_trigger language plpgsql as ${'$'}${'$'} begin end ${'$'}${'$'};
            create event trigger ensure_rls on ddl_command_end execute function public.rls_auto_enable();
        """.trimIndent()
    }
}
