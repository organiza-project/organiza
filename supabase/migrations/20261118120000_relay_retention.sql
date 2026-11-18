-- Organiza backend: the relay forgets old messages (ADR-0034).
-- Device usage reports matter for two weeks at most. Each append first deletes its own mailbox's
-- messages older than fifteen days, so the table stays small and a new device's first read is short.

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
    delete from public.arena_message m
    where m.mailbox = p_mailbox and m.created_at < now() - interval '15 days';
    insert into public.arena_message (mailbox, data, sender)
    values (p_mailbox, decode(p_data, 'base64'), v_sender)
    returning cursor into v_cursor;
    return v_cursor;
end;
$$;
