package org.organiza.sync

import java.io.File

/** What a Supabase project provides before any migration runs, for tests on plain PostgreSQL. */
object SupabaseStub {
    val SQL = """
        create role anon nologin;
        create role authenticated nologin;
        grant usage on schema public to anon, authenticated;
        alter default privileges in schema public grant all on tables to anon, authenticated;
        alter default privileges in schema public grant all on functions to anon, authenticated;
        create schema auth;
        grant usage on schema auth to anon, authenticated;
        create table auth.users (id uuid primary key);
        create function auth.uid() returns uuid language sql stable as
            ${'$'}${'$'} select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid ${'$'}${'$'};
        grant execute on function auth.uid() to anon, authenticated;
    """.trimIndent()

    private val directory = File(checkNotNull(System.getProperty("organiza.supabaseMigrations")))

    fun migrations(): List<File> = directory.listFiles { file -> file.name.endsWith(".sql") }.orEmpty().sortedBy { it.name }

    /** The one-paste reset and setup script for the SQL Editor. */
    val setup: File get() = File(directory.parentFile, "setup.sql")
}
