# Google setup

1. Create or choose a Google Cloud project.
2. Enable **Google Photos Library API**.
3. Configure the OAuth consent screen. For personal use, keep the app in Testing when appropriate.
4. Add your Google account as a test user.
5. Create an OAuth client of type **Web application**.
6. Add the exact authorized redirect URI `https://MY_SERVER/oauth/google/callback`. HTTP is only appropriate for Google's permitted localhost development cases.
7. Copy the client ID and secret to `auth-server/.env` as `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` and set the matching `GOOGLE_REDIRECT_URI`.
8. Generate `TOKEN_ENCRYPTION_KEY_BASE64` with `openssl rand -base64 32`. Store it in the host secret manager; losing it makes stored refresh tokens unreadable.
9. Start or deploy the server and verify `/health` over HTTPS.
10. Set `PHOTO_BACKUP_AUTH_SERVER_URL=https://MY_SERVER` in `android/local.properties`.
11. Rebuild and install the APK, open the app, grant full photo access, and pair from `/connect` on another device.

Do not place any Google secret or encryption key in Android files or source control.
