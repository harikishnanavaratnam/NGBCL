# Launch checklist

Tick each line before opening registrations, and the match-day list before each event day.

## Security

- [ ] Every secret in `deploy/.env` is freshly generated (`openssl rand -base64 48`); file is `chmod 600`.
- [ ] Bootstrap admin password changed; `NGBCL_BOOTSTRAP_ADMIN_*` removed from `.env`.
- [ ] Each staff member has their own account with the smallest role that works (no shared logins).
- [ ] `NGBCL_API_DOCS_ENABLED=false` — `https://<api>/swagger-ui.html` returns 404.
- [ ] Turnstile keys are the real ones (not the always-pass test keys).
- [ ] R2: `ngbcl-private` and `ngbcl-backups` have public access **off**; CORS lists only the real site origin.
- [ ] The R2 token on the server for backups is scoped to `ngbcl-backups` only.
- [ ] SSH is key-only; port 22 limited to your IP in the security list if possible.
- [ ] Site settings: player prices / team budgets public or not — decide before the auction.
- [ ] Privacy notice and consent text reviewed by the organisers.

## Reliability

- [ ] `scripts/backup.sh` ran and the file shows in `scripts/restore.sh --list`.
- [ ] A restore was practised once on a spare database.
- [ ] `BACKUP_PASSPHRASE` is stored outside the server.
- [ ] healthchecks.io: both checks green and email alerts reach you (use "Send test notification").
- [ ] UptimeRobot: site and API monitors green; alert contact verified.
- [ ] Cron installed (`crontab -l` shows backup, health check, DuckDNS).

## Functional run-through (staging data, then wipe)

- [ ] Player and team registration on a phone (Turnstile, photo upload, confirmation).
- [ ] Review, approve, verify photo; the player appears on the public site.
- [ ] Auction: console bids, undo, sold/unsold, display screen on the projector, public page updates live.
- [ ] Fixtures generated, scheduled, published; public fixtures page correct.
- [ ] Scoring on the actual tablets over the venue Wi-Fi: start, points, undo, submit; confirm in admin.
- [ ] Standings update; knockout places confirmed and published.
- [ ] Each report downloads and opens in Excel.

## Match day

- [ ] Tablets charged, signed in as scorers, screen lock off, browser zoom 100%.
- [ ] Venue Wi-Fi tested from every court; a phone hotspot ready as backup.
- [ ] Paper scoresheets printed (scorers can enter game-by-game later).
- [ ] No deploys scheduled; last night's backup succeeded.
- [ ] One official signed in to Results › Confirmations for the whole day.
