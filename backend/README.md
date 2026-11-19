# NGBCL backend

Spring Boot 3.5.15 · Java 21 · PostgreSQL 16 · Flyway · Spring Security (JWT access token + rotating httpOnly refresh cookie, Argon2id) · STOMP WebSockets · springdoc OpenAPI.

```bash
docker compose -f ../docker-compose.yml up -d        # PostgreSQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- Swagger UI: http://localhost:8080/swagger-ui.html · OpenAPI JSON: `/v3/api-docs`
- Health: `/actuator/health`

## Modules (`lk.ngbcl.*`)

| Package | Responsibility | Milestone |
| --- | --- | --- |
| `shared` | UUIDv7 ids, base entities, problem+json errors, paging | M0 |
| `security` | JWT issue/verify, permissions, CORS, cookies, login rate limit | M0 |
| `identity` | Staff users, roles, auth endpoints, `/api/me`, user admin, bootstrap admin | M0 |
| `tournament` | Current edition, settings (general, registration windows, public info, branding), phase, status lifecycle | M0 → M1 |
| `files` | Presigned uploads to R2, real-byte checks (type, size, dimensions, ratio), human verification, re-encoded public copies, abandoned-upload cleanup | M1 |
| `player.category` | Skill categories: create, edit, reorder, activate/deactivate, public list | M1 |
| `audit` | Same-transaction audit log + query API | M0 |
| `realtime` | STOMP config, auth interceptor, after-commit publisher | M0 |
| `auction` | Auction rules, queue, live engine (bids, undo, sold/unsold/withdrawn, re-auction rounds), budgets, rosters, audited corrections, public display state | M4 |
| `competition` | Match categories, fixture template, courts, format, group draw, fixture generation (round robin, knockout with byes, 3rd place), scheduling with clash checks, line-ups, publishing frozen public copies | M5 |
| `registration`, `player`, `team`, `scoring`, `standings`, `content`, `reporting`, `notification` | see `documentation/architecture-proposal.md` | M1–M7 |

## Environment

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `NGBCL_JWT_SECRET` | HS256 signing key, ≥ 32 bytes (**required** outside the `local` profile) |
| `NGBCL_ALLOWED_ORIGINS` | Comma-separated frontend origins (CORS, cookie-endpoint Origin check, WebSocket) |
| `NGBCL_COOKIE_DOMAIN` | Parent domain (e.g. `.ngbcl.lk`) so the web app sees the session hint cookie |
| `NGBCL_COOKIE_SECURE` | `true` in every HTTPS environment |
| `NGBCL_IP_HASH_SALT` | Salt for hashing client IPs in audit/refresh records |
| `NGBCL_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` / `_NAME` | Creates the first Super Admin on an empty DB |
| `NGBCL_STORAGE_ENDPOINT` | S3 endpoint — R2: `https://<account-id>.r2.cloudflarestorage.com` (empty = uploads disabled) |
| `NGBCL_STORAGE_ACCESS_KEY` / `_SECRET_KEY` | R2 API token (Object Read & Write, limited to the two buckets) |
| `NGBCL_STORAGE_PRIVATE_BUCKET` / `_PUBLIC_BUCKET` | Default `ngbcl-private` / `ngbcl-public` |
| `NGBCL_STORAGE_PUBLIC_BASE_URL` | URL serving the public bucket (custom domain or `https://pub-….r2.dev`) |
| `NGBCL_STORAGE_PATH_STYLE` | `true` for MinIO, `false` for R2 |
| `NGBCL_TURNSTILE_SECRET` / `NGBCL_TURNSTILE_SITE_KEY` | Cloudflare Turnstile for the public forms. Empty secret = bot check off (a warning is logged). Test keys that always pass: site `1x00000000000000000000AA`, secret `1x0000000000000000000000000000000AA` |
| `NGBCL_SITE_REVALIDATE_URL` / `_SECRET` | Public website refresh after content changes, e.g. `https://ngbcl.lk/api/revalidate` + a random secret (same value as the web app's `REVALIDATE_SECRET`). Empty = pages refresh within a few minutes |
| `NGBCL_REG_SESSIONS_PER_IP` | Registration sessions one IP may start per hour (default 30) |

## Cloudflare R2 setup (M1)

1. R2 → **Create bucket** `ngbcl-private` (no public access) and `ngbcl-public`.
2. `ngbcl-public` → Settings → **Public access**: connect a custom domain (e.g. `cdn.ngbcl.lk`) or enable the r2.dev URL. Use it as `NGBCL_STORAGE_PUBLIC_BASE_URL`.
3. `ngbcl-private` → Settings → **CORS policy** (browsers upload straight to it):
   ```json
   [{ "AllowedOrigins": ["https://ngbcl.lk", "http://localhost:3000"],
      "AllowedMethods": ["PUT", "GET"], "AllowedHeaders": ["content-type"], "MaxAgeSeconds": 3600 }]
   ```
4. R2 → **Manage API tokens** → Object Read & Write, scoped to both buckets → copy the access key, secret and the account S3 endpoint.
5. Frontend: `NEXT_PUBLIC_STORAGE_ORIGIN` = the S3 endpoint origin, `NEXT_PUBLIC_CDN_URL` = the public base URL (both feed the Content-Security-Policy).

Upload flow: `POST /api/files/uploads` → browser `PUT`s to the presigned URL → `POST /api/files/{id}/complete`
(server reads the object back and checks real type, size, dimensions and ratio) → verify → publish
(re-encoded JPEG/PNG copy in the public bucket, EXIF/GPS stripped). Abandoned uploads are removed hourly.

## Local uploads

With the `local` profile uploads work out of the box: when no R2 endpoint is set, files are kept in
`backend/.dev-storage/` (git-ignored) and served by the API under `/dev-storage/**` through short-lived signed
links. This mode is development-only and never activates when a real endpoint is configured.

To test against real R2 instead, create two **dev** buckets in
R2 (`ngbcl-dev-private`, `ngbcl-dev-public`) following the steps above, then copy `local-secrets.example.yml`
to `local-secrets.yml` (git-ignored) and fill it in. In `frontend/.env.local` set
`NEXT_PUBLIC_STORAGE_ORIGIN=https://<ACCOUNT_ID>.r2.cloudflarestorage.com` and `NEXT_PUBLIC_CDN_URL=https://pub-<ID>.r2.dev`.

## Auction engine (M4)

- PostgreSQL is the single source of truth. Every auction command locks the edition's `auctions` row
  (`SELECT … FOR UPDATE`), so commands run one at a time and `state_version` rises by exactly one per change.
- Bids are append-only and idempotent (`Idempotency-Key` header); undo marks a bid `VOIDED`.
- A sale is one transaction: transaction row + squad entry + budget deduction + lot closed. DB CHECKs keep
  every purse balanced and non-negative; partial unique indexes allow one open lot and one active team per player.
- After commit the public state is broadcast on `/topic/public/auction`; the console refetches its staff snapshot.
- Defaults (all editable in Settings › Auction): budget 1,000,000 · squad 8 · base price 10,000 · increment 5,000.

## Competition (M5)

- New editions are seeded with MS, WS, MD, WD, XD, a 5-match template and three courts — all editable.
- Generation replaces draft ties only; once anything is published, ties are edited one by one.
- Scheduling places a tie's matches back to back (match minutes + break) and refuses a court, team or player
  in two places at once (FIX-009). Line-ups must come from the team's squad (FIX-008), follow the category's
  gender rule and the per-tie match limit.
- Publishing stores a JSON copy of each tie; `/api/public/fixtures` serves only those copies, so later edits stay
  private until republished (`changedSincePublish`).
