# Server deployment

Use any small HTTPS-capable Node host, VM, container platform, or personal server with persistent storage. Install production dependencies, run `npm run build`, then `npm start` with `NODE_ENV=production`. Put the service behind a maintained TLS reverse proxy or a platform HTTPS endpoint; set `PUBLIC_BASE_URL` and `GOOGLE_REDIRECT_URI` to the public HTTPS origin. Set `TRUST_PROXY=true` only when a trusted proxy terminates TLS.

Inject `.env` values through the host secret manager, restrict filesystem/database permissions, back up the encryption key separately, monitor disk and errors without logging request credentials, and firewall the database. Production refuses HTTP and missing encryption keys. Never publish the development fallback configuration.

SQLite is suitable for local/personal, single-instance use—not a claim of production-grade availability. For multiple instances or stronger durability, replace `Store` with PostgreSQL or a managed transactional database while keeping its interface, uniqueness constraints, encryption, and one-time credential transaction.
