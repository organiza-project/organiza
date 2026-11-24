-- Organiza backend: the relay forgets old messages in every mailbox (ADR-0034).
-- A mailbox nobody posts to again, after an unlink or an uninstall, is cleaned too. The index keeps
-- the delete to the few rows that have just turned fifteen days old.

create index arena_message_created on public.arena_message (created_at);

create or replace function public.arena_append(p_mailbox text, p_data text)
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
    delete from public.arena_message m where m.created_at < now() - interval '15 days';
    insert into public.arena_message (mailbox, data, sender)
    values (p_mailbox, decode(p_data, 'base64'), v_sender)
    returning cursor into v_cursor;
    return v_cursor;
end;
$$;
