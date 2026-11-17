# Setting up a Supabase backend

Organiza works fully offline. A backend lets your devices share usage, so daily limits
count all of them: it holds accounts and encrypted, padded messages, and nothing else
(ADR-0015, ADR-0028). These steps set up a project on supabase.com; to run the backend on your own
machine with Docker or Portainer, see [SELF_HOSTING.md](SELF_HOSTING.md).

## 1. Run the schema

1. Open your project in the Supabase dashboard, then **SQL Editor** > **New query**.
2. Paste the whole of [`supabase/setup.sql`](../supabase/setup.sql) and select **Run**.
   It should end with "Success. No rows returned".

`setup.sql` first **purges the `public` schema**: every table, view, function, sequence and type in
it, and any trigger those functions back, such as a `handle_new_user` trigger on `auth.users`. It
keeps extension objects, Supabase's `ensure_rls` safeguard, and every account in `auth.users`. It
then creates the schema. It is safe to run again. If you only want the schema, run the files in
`supabase/migrations/` in order instead, or `supabase db push` with the CLI.

To check it worked, run:

```sql
select proname from pg_proc where proname in ('arena_append', 'arena_fetch', 'delete_account');
```

All three names should come back.

### What the schema contains

| Object | Purpose |
|--------|---------|
| `arena_message` | Opaque ciphertext, one row per message, under a mailbox ID derived from the device link code |
| `arena_append(mailbox, data)` | Adds a message. Needs a signed-in user. At most 500 messages per user per day |
| `arena_fetch(mailbox, after)` | Returns up to 200 messages after a cursor. Needs a signed-in user |
| `delete_account()` | Deletes the caller's account and, by cascade, every message they sent (ADR-0020) |

Row-level security is on for `arena_message`, with **no policies**, and every direct grant to `anon`
and `authenticated` is revoked. Clients cannot list, read, change or delete rows. They can only
call the three functions, and reading a mailbox needs its 64-character ID, which only your linked
devices can derive.

There are deliberately no `profiles`, device or usage tables. Device names, apps and minutes live
inside the encrypted messages, so the server never learns which devices are yours or how you use
them (ADR-0015, ADR-0028). The names keep their `arena_` prefix from an earlier feature (ADR-0029).

## 2. Configure authentication

In **Authentication**:

| Setting | Value | Why |
|---------|-------|-----|
| Email provider | **On** | The app signs in with email and password |
| Confirm email | Your choice | **Off** is simplest for dogfooding: sign-up signs straight in. **On** makes the app say "Check your email", and the person signs in after tapping the link |
| Minimum password length | 8 or more | A short password gets "Choose a longer password" in the app |
| Anonymous sign-ins | **Off** | Not used. Anonymous users would count as signed in and could use the relay quota |
| Phone, OAuth and SSO providers | **Off** | Not used |
| Site URL | Any page you control, for example the project's GitHub page | Only used as the landing page after an email confirmation link |
| Redirect URLs | None | The app uses no deep links and no OAuth |
| JWT settings | Defaults | The app refreshes the one-hour access token itself, and rotated refresh tokens are kept |

With **Confirm email** on, the built-in email sender has a very low hourly limit. Turn it off, or set
up custom SMTP under **Authentication** > **Emails**, before inviting several people.

Under **Project Settings** > **Data API**, the Data API must be enabled with `public` among the
exposed schemas. Both are the defaults.

## 3. Give the app the project

Under **Project Settings** > **API Keys**, copy the project URL and the **publishable** key
(`sb_publishable_...`). The legacy `anon` key also works. Never use the `service_role` or a secret
key: it bypasses row-level security for every user, and the app refuses it.

- **Self-hosted:** in the app, choose **My own Supabase** in setup or in the Vault, and enter both.
- **Hosted build:** build the APK with the project baked in. The values stay out of the repository:

  ```bash
  ./gradlew :app:android:assembleRelease \
    -Porganiza.supabaseUrl=https://YOUR-PROJECT.supabase.co \
    -Porganiza.supabaseAnonKey=sb_publishable_YOUR_KEY
  ```

## What the backend can see

The account's email address, a password hash, mailbox IDs, message sizes and timing, and network
addresses. It cannot see habits, sessions, blocked apps or sites, overrides, notes, AI keys, or
usage.

## Checking sync end to end

`DeviceUsageTest` (in `core/sync`) has two devices share their minutes through a local stand-in for
Supabase built from `supabase/migrations`, over real HTTP; `./gradlew test` always runs it. To check
your own project, sign in on two devices, turn on **Usage across devices** on one, paste its device
code on the other, and open **Metrics** after a few minutes: both devices' time shows there.

