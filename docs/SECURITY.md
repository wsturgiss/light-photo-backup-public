# Security

## Model

Assets are local photos, the device credential, Google refresh/access tokens, and the server encryption key. Threats include lost phones, malicious local apps, network interception, pairing-code guessing, server/database theft, logs, and dependency compromise.

The server alone holds the Google web-client secret and refresh token. Refresh tokens are encrypted with AES-256-GCM using a separate 32-byte environment key. The phone holds a 256-bit random device credential encrypted by a non-exportable Android Keystore AES-GCM key; the server stores only its SHA-256 hash. Access tokens are short-lived and cached only in memory. Pairing codes expire, attempts are bounded, OAuth uses state plus PKCE, connected credentials are delivered once, API input is validated, rate limits and secure headers are enabled, CORS is not enabled, and production requires HTTPS.

The only Google scope is `photoslibrary.appendonly`; no client secret is in the APK because a public mobile binary cannot protect one. Sensitive fields are redacted and photo bytes, full URIs, credentials, codes, and response bodies are not logged. Disconnect deletes server token material and hashed device access, attempts Google revocation, and clears the phone store.

Limitations: a compromised running server with access to both database and environment can decrypt refresh tokens; a compromised unlocked phone may invoke its credential; SQLite lacks managed-database availability/auditing; rate limits are process-local; backups and key rotation are operator responsibilities. Use host patching, least privilege, encrypted disks, secret management, monitoring, and a managed database for higher assurance.
