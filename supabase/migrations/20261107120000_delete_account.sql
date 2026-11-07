-- Organiza backend: deleting an account from the app (ADR-0020).
-- Removes the caller's auth identity, and with it, by the foreign key's cascade, every relay
-- message they sent. Nobody can delete another account: the function only ever uses auth.uid().

create function public.delete_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
begin
    if v_user is null then
        raise exception 'sign in to delete the account' using errcode = '28000';
    end if;
    delete from auth.users where id = v_user;
end;
$$;

revoke all on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;
