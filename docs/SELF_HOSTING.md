# Self-hosting the Organiza backend

Organiza works fully offline. A backend adds an account, Arena challenges with friends, and daily
limits that count all your devices. Whatever you choose, the backend only ever holds accounts and
**encrypted, padded messages** it cannot read (ADR-0015, ADR-0028). This guide runs that backend
on your own machine in about five minutes, with Docker or Portainer.

The app does not care who runs it: **Organiza hosted** and **your own** are the same Supabase API
at a different address. You give the app a URL and an anon key, and that is the whole difference.

## What you run

[`supabase/self-host/docker-compose.yml`](../supabase/self-host/docker-compose.yml) starts only
what the app uses, not the full Supabase stack:

| Service | Image | Job |
|---------|-------|-----|
| `db` | `supabase/postgres` | The database: accounts and the relay's ciphertext |
| `db-init` | same | Runs once at each start to set the passwords Auth and PostgREST log in with |
| `auth` | `supabase/gotrue` | Email and password accounts at `/auth/v1` |
| `rest` | `postgrest/postgrest` | The three relay functions at `/rest/v1/rpc` |
| `gateway` | `caddy` | One address for both, with automatic HTTPS for a domain |

You need Docker with Compose v2, and a machine that your phone and computers can reach. The app
only accepts `https://` addresses, so either give the stack a domain (Caddy gets a Let's Encrypt
certificate) or put it behind the reverse proxy you already run.

## 1. Make the keys

```bash
git clone https://github.com/organiza-project/organiza.git
cd organiza/supabase/self-host
./make-keys.sh https://organiza.example.com
```

`make-keys.sh` writes `.env` with a new database password, a JWT secret, and the **anon key** for
the app, then prints the URL and key to enter. It needs `openssl` and never overwrites an existing
`.env`. Keep `.env` private: `JWT_SECRET` signs every login. The anon key is public by design.

Then pick how the address is served, in `.env`:

- **A domain pointing at this machine, ports 80 and 443 open:** set
  `SITE_ADDRESS=organiza.example.com`, `HTTP_PORT=80` and `HTTPS_PORT=443`. Caddy fetches and
  renews the certificate.
- **Behind your own reverse proxy** (Nginx Proxy Manager, Traefik, Caddy): keep
  `SITE_ADDRESS=:80` and forward your HTTPS host to port `8000` of this machine.

## 2. Start it

### With Docker

```bash
docker compose up -d
docker compose ps        # wait until auth shows "healthy"
```

### With Portainer

1. **Stacks** > **Add stack**, name it `organiza`.
2. **Web editor**: paste the whole of `docker-compose.yml`. It needs no other files.
3. **Environment variables** > **Load variables from .env file**: choose the `.env` from step 1.
4. **Deploy the stack**, and wait until the `auth` container is healthy.

## 3. Create the schema

Run [`supabase/setup.sql`](../supabase/setup.sql) once, from the repository root on the Docker host:

```bash
docker exec -i organiza-db-1 psql -U supabase_admin -d postgres -v ON_ERROR_STOP=1 < supabase/setup.sql
docker restart organiza-rest-1
```

Restarting `rest` makes PostgREST see the new functions. `setup.sql` is the exact schema the
hosted project runs, and safe to run again: it first clears the `public` schema, keeps every
account, then recreates the relay table and its three functions. In Portainer the containers carry
the same names; run the commands on the host, or paste `setup.sql` into the `db` container's
console after starting `psql -U supabase_admin -d postgres` there.

To check it worked:

```bash
curl -s https://organiza.example.com/auth/v1/health
```

It should answer with GoTrue's name and version.

## 4. Connect the app

On every device: **Profile** > **Where Arena syncs** (or the setup screens) > **My own Supabase**,
then enter the URL and the anon key that `make-keys.sh` printed. Create an account on the first
device and sign in with it on the others.

To count limits across devices, turn on **Usage across devices** under **Account** on one device,
**Copy device code**, and paste it on each other device. The code is the key to your usage
reports: share it like a password, only with yourself.

## Keeping it yours

- **Close sign-up** once your accounts exist, if the address is public: set `DISABLE_SIGNUP=true`
  in `.env` and run `docker compose up -d` again. Otherwise anyone could create an account and use
  your relay quota (500 messages per account per day).
- **Email confirmation** is off, since the stack has no mail server: sign-up signs straight in. To
  confirm addresses, add GoTrue's `GOTRUE_SMTP_*` settings to the `auth` service and set
  `AUTOCONFIRM=false`.
- **Backups:** the `db-data` volume holds everything. It is only accounts and ciphertext; losing it
  loses no habit, session or usage data, which stay on the devices.
- **Updates:** the image versions are pinned in the compose file. Change them deliberately, and run
  `setup.sql` again only when it changes.

## What the backend can see

| It sees | It never sees |
|---------|---------------|
| Account emails and password hashes | Habits, sessions, apps, sites, calendar titles |
| When a signed-in account sends or reads a message | Who is in which challenge, or how anyone is doing |
| Opaque mailbox IDs and padded ciphertext | Usage minutes, app names, or how many apps a report holds |

The schema and its guarantees are described in [`supabase-setup.md`](supabase-setup.md), which
also covers creating a project on supabase.com instead.
