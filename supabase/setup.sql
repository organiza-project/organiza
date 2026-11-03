-- Organiza backend: full reset and setup, for the Supabase SQL Editor (ADR-0015).
--
-- Paste this whole file into SQL Editor and run it once. It is safe to run again.
--
-- PART 1 removes everything in the public schema: tables, views, functions, triggers that
-- call those functions (for example a handle_new_user trigger on auth.users), sequences and
-- types. Objects that belong to extensions are left alone, and so are event-trigger functions
-- such as rls_auto_enable, which turns row-level security on for new tables. Accounts in auth.users are kept;
-- see the commented line at the end of part 1 to delete them too.
-- PART 2 is supabase/migrations/20261102120000_arena_relay.sql, unchanged. A test checks
-- that the two stay identical.

-- ===== PART 1: purge the public schema =====
do $$
declare
    item record;
begin
    for item in
        select format('drop materialized view if exists public.%I cascade', matviewname) as command
        from pg_matviews where schemaname = 'public'
        union all
        select format('drop view if exists public.%I cascade', viewname)
        from pg_views where schemaname = 'public'
        union all
        select format('drop table if exists public.%I cascade', tablename)
        from pg_tables where schemaname = 'public'
    loop
        execute item.command;
    end loop;

    for item in
        select format('drop %s if exists public.%I(%s) cascade',
                      case p.prokind when 'p' then 'procedure' when 'a' then 'aggregate' else 'function' end,
                      p.proname, pg_get_function_identity_arguments(p.oid)) as command
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and not exists (select 1 from pg_depend d where d.objid = p.oid and d.deptype = 'e')
          -- Event-trigger functions such as rls_auto_enable, behind Supabase's ensure_rls, stay.
          and p.prorettype <> 'event_trigger'::regtype
    loop
        execute item.command;
    end loop;

    for item in
        select format('drop sequence if exists public.%I cascade', sequencename) as command
        from pg_sequences where schemaname = 'public'
    loop
        execute item.command;
    end loop;

    for item in
        select format('drop type if exists public.%I cascade', t.typname) as command
        from pg_type t
        join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public'
          and t.typtype in ('e', 'd', 'c', 'r')
          and (t.typrelid = 0 or (select c.relkind from pg_class c where c.oid = t.typrelid) = 'c')
          and not exists (select 1 from pg_depend d where d.objid = t.oid and d.deptype = 'e')
    loop
        execute item.command;
    end loop;
end;
$$;

-- To delete every account as well, uncomment the next line. This cannot be undone.
-- delete from auth.users;

-- ===== PART 2: the Organiza schema =====
-- Organiza backend: the Arena relay on Supabase (ADR-0015, ADR-0011).
-- The table holds the same opaque, padded ciphertext as the blind relay, under the mailbox ID
-- derived from a challenge secret. Clients reach it only through the two functions below.

create table public.arena_message (
    cursor     bigint generated always as identity primary key,
    mailbox    text not null check (mailbox ~ '^[0-9a-f]{64}$'),
    data       bytea not null check (octet_length(data) between 1 and 8192),
    sender     uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now()
);

create index arena_message_mailbox_cursor on public.arena_message (mailbox, cursor);
create index arena_message_sender_created on public.arena_message (sender, created_at);

-- Row-level security with no policies denies every direct read and write. Supabase grants new
-- tables to anon and authenticated by default, so those grants are taken back as well.
alter table public.arena_message enable row level security;
revoke all on table public.arena_message from public, anon, authenticated;

create function public.arena_append(p_mailbox text, p_data text)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sender uuid := auth.uid();
    v_cursor bigint;
begin
    if v_sender is null then
        raise exception 'sign in to use the relay' using errcode = '28000';
    end if;
    if (select count(*) from public.arena_message m
        where m.sender = v_sender and m.created_at > now() - interval '1 day') >= 500 then
        raise exception 'daily relay limit reached' using errcode = '54000';
    end if;
    insert into public.arena_message (mailbox, data, sender)
    values (p_mailbox, decode(p_data, 'base64'), v_sender)
    returning cursor into v_cursor;
    return v_cursor;
end;
$$;

-- Postgres wraps base64 output every 76 characters, so the line breaks are removed.
create function public.arena_fetch(p_mailbox text, p_after bigint)
returns table (cursor bigint, data text)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'sign in to use the relay' using errcode = '28000';
    end if;
    return query
        select m.cursor, translate(encode(m.data, 'base64'), E'\n', '')
        from public.arena_message m
        where m.mailbox = p_mailbox and m.cursor > p_after
        order by m.cursor
        limit 200;
end;
$$;

revoke all on function public.arena_append(text, text) from public, anon;
revoke all on function public.arena_fetch(text, bigint) from public, anon;
grant execute on function public.arena_append(text, text) to authenticated;
grant execute on function public.arena_fetch(text, bigint) to authenticated;

-- Tell the Data API about the new functions now rather than on its next refresh.
notify pgrst, 'reload schema';
