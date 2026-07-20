# Deploy Photo Backup Auth Server to Railway

This service holds encrypted Google refresh tokens and device records. Deploy only from a private repository and never commit `.env`, databases, encryption keys, or Google credentials.

## Create the service

1. Push the project to a private GitHub repository.
2. In Railway, create a project from that repository.
3. Set the service **Root Directory** to `auth-server`.
4. Set the build command to `npm ci && npm run build`.
5. Set the start command to `npm start`.
6. Set the health-check path to `/health`.
7. Add a persistent Railway volume mounted at `/data`.
8. Generate a Railway HTTPS domain, such as `https://SERVICE.up.railway.app`.

The process listens on Railway's `PORT` at `0.0.0.0`. Railway terminates TLS and the app trusts one forwarding proxy hop.

## Variables

Configure these Railway variables:

```text
NODE_ENV=production
PUBLIC_BASE_URL=https://SERVICE.up.railway.app
GOOGLE_CLIENT_ID=<Google web OAuth client ID>
GOOGLE_CLIENT_SECRET=<Google web OAuth client secret>
TOKEN_ENCRYPTION_KEY_BASE64=<base64 encoding of exactly 32 random bytes>
DATABASE_PATH=/data/photo-backup.sqlite
PAIRING_TTL_SECONDS=600
PAIRING_POLL_INTERVAL_SECONDS=5
DEVICE_TOKEN_TTL_DAYS=365
TRUST_PROXY=true
```

Railway supplies `PORT`; do not hard-code it. Generate the encryption key locally with `openssl rand -base64 32` and place it only in Railway's secret variables. The key must remain stable: changing or losing it makes stored refresh tokens unreadable. Production derives `GOOGLE_REDIRECT_URI` from `PUBLIC_BASE_URL`; do not set a different callback.

## Google and verification

1. In the existing Google Web OAuth client, add exactly `https://SERVICE.up.railway.app/oauth/google/callback` as an authorized redirect URI.
2. Deploy and open `https://SERVICE.up.railway.app/health`. It should return `{"status":"ok"}` without configuration details.
3. Review Railway logs for startup only; never print variables or tokens.
4. Rebuild the Android app with `PHOTO_BACKUP_AUTH_SERVER_URL=https://SERVICE.up.railway.app`.
5. Install the update and reconnect the Google account to create a credential associated with the deployed server.

## Persistence, backup, and rollback

The `/data` volume and `TOKEN_ENCRYPTION_KEY_BASE64` must survive every redeploy and rollback. Never replace the volume with ephemeral service storage. Before a database migration or platform move, stop writes and take a consistent volume snapshot using Railway's supported backup mechanism. Keep the encryption key in a separate secure backup.

Rolling application code back is safe only when the older version understands the existing SQLite schema. Do not roll back by deleting the volume, and do not restore a database without its matching encryption key. SQLite is appropriate for this personal single-instance service; do not run multiple replicas against the same SQLite file. Move to a managed transactional database before scaling horizontally.
