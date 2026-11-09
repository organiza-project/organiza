#!/usr/bin/env bash
# Writes .env with a new database password, JWT secret and the anon key the app takes.
# Usage: ./make-keys.sh https://organiza.example.com   (needs openssl; never overwrites .env)
set -euo pipefail
cd "$(dirname "$0")"
url="${1:?give the https address the app will use, like https://organiza.example.com}"
[ -e .env ] && { echo ".env exists; delete it first to make new keys" >&2; exit 1; }
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
password=$(openssl rand -hex 24)
secret=$(openssl rand -hex 32)
now=$(date +%s)
header=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64url)
payload=$(printf '{"role":"anon","iss":"supabase","iat":%d,"exp":%d}' "$now" $((now + 10 * 365 * 24 * 3600)) | b64url)
signature=$(printf '%s' "$header.$payload" | openssl dgst -sha256 -hmac "$secret" -binary | b64url)
sed -e "s|^PUBLIC_URL=.*|PUBLIC_URL=${url%/}|" -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$password|" \
    -e "s|^JWT_SECRET=.*|JWT_SECRET=$secret|" -e "s|^ANON_KEY=.*|ANON_KEY=$header.$payload.$signature|" .env.example > .env
chmod 600 .env
echo "Wrote .env. In Organiza, choose My own Supabase and enter:"
echo "  URL: ${url%/}"
echo "  Key: $header.$payload.$signature"
