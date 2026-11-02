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
