# Deploying NGBCL

```
visitors ──► Vercel (Next.js website, *.vercel.app)
               │  /api/*  is proxied (same origin → first-party sign-in cookies)
               ▼
            Oracle Cloud Always Free VM (Ubuntu 24.04 ARM)
              Caddy (HTTPS, Let's Encrypt)  ──►  Spring Boot API  ──►  PostgreSQL 16
              ▲ wss://API_DOMAIN/ws  (live auction + scores connect straight here)
            Cloudflare R2: uploads (private/public buckets) + encrypted nightly backups
            healthchecks.io: backup + server health alerts · UptimeRobot: site/API uptime
```

Everything secret lives in `deploy/.env` on the server and in Vercel's environment settings — never in git, never
in chat. Times below are Sri Lanka time unless marked UTC.

## 1. Oracle Cloud VM

1. Create an **Ampere A1** instance (Always Free allows up to 4 OCPU / 24 GB in total): image **Ubuntu 24.04**,
   2 OCPU / 12 GB is plenty. Add your **public** SSH key only. Use a **reserved public IP** so the address never changes.
2. VCN → Security List → add ingress rules for TCP **80** and **443** (and UDP 443) from `0.0.0.0/0`. SSH (22) is
   already open; restrict it to your own IP if you can.
3. SSH in and run the one-time setup:
   ```bash
   git clone https://github.com/<you>/NGBCL.git ~/ngbcl
   bash ~/ngbcl/deploy/scripts/server-setup.sh      # docker, firewall ports, auto security updates, fail2ban
   exit                                             # log back in so the docker group applies
   ```

## 2. Names and accounts

- **DuckDNS**: create `ngbcl-api` (→ `ngbcl-api.duckdns.org`) pointing at the reserved IP. Copy your token.
- **R2 backups**: create a private bucket `ngbcl-backups` and an API token with *Object Read & Write* on that bucket
  only. Don't reuse the uploads token.
- **healthchecks.io** (free): create two checks — *ngbcl backup* (period 1 day, grace 2 h) and *ngbcl server*
  (period 10 min, grace 20 min). Copy both ping URLs. Email alerts are on by default.
- **UptimeRobot** (free): HTTP(s) monitors every 5 min for `https://<site>/` and
  `https://ngbcl-api.duckdns.org/actuator/health` (keyword `UP`).

## 3. Configure

```bash
cd ~/ngbcl/deploy
cp .env.example .env && chmod 600 .env
nano .env        # fill every value; secrets: openssl rand -base64 48
```

Keep `BACKUP_PASSPHRASE` in your password manager as well — backups can't be restored without it.

## 4. Start

```bash
cd ~/ngbcl/deploy
docker compose -f docker-compose.prod.yml up -d --build      # first build takes a few minutes
docker compose -f docker-compose.prod.yml ps                 # api → healthy
curl https://ngbcl-api.duckdns.org/actuator/health           # {"status":"UP"}
crontab -e                                                   # paste deploy/crontab.example
```

Sign in once with the bootstrap admin, change its password, create real staff accounts, then **delete the two
`NGBCL_BOOTSTRAP_ADMIN_*` lines** from `.env` and run `scripts/deploy.sh`.

Run the first backup by hand and check it arrives: `scripts/backup.sh && scripts/restore.sh --list`.

## 5. Vercel (website)

1. Import the GitHub repo → **Root directory** `frontend` → framework Next.js.
2. Environment variables (Production):

   | Name | Value |
   | --- | --- |
   | `API_PROXY_TARGET` | `https://ngbcl-api.duckdns.org` |
   | `NEXT_PUBLIC_WS_URL` | `wss://ngbcl-api.duckdns.org/ws` |
   | `NEXT_PUBLIC_SITE_URL` | `https://<project>.vercel.app` |
   | `NEXT_PUBLIC_STORAGE_ORIGIN` | `https://<account-id>.r2.cloudflarestorage.com` |
   | `NEXT_PUBLIC_CDN_URL` | the public bucket URL (same as `NGBCL_STORAGE_PUBLIC_BASE_URL`) |
   | `REVALIDATE_SECRET` | same value as `NGBCL_SITE_REVALIDATE_SECRET` on the API |

   Leave `NEXT_PUBLIC_API_URL` unset — with `API_PROXY_TARGET` the site calls its own `/api/*`.
3. Deploy, then put the final site URL into the API's `NGBCL_ALLOWED_ORIGINS`, `SITE_URL` and
   `NGBCL_SITE_REVALIDATE_URL`, and into the **R2 CORS policy** of `ngbcl-private` (see backend/README.md), then
   `scripts/deploy.sh`.

Note: Vercel's free Hobby plan is meant for non-commercial use. A community tournament with sponsors may count as
commercial — check Vercel's current terms and move to Pro if needed.

## Updating

Merge to `main` (CI green), then on the server: `~/ngbcl/deploy/scripts/deploy.sh`. The API restarts in about a
minute; **don't deploy during the auction or while matches are being scored**. Vercel deploys the website on its
own when `main` changes.

Database migrations (Flyway) run automatically on start. Take a manual backup first for any release with a new
`V*__*.sql` file: `scripts/backup.sh`.

## Restoring a backup

```bash
cd ~/ngbcl/deploy
scripts/restore.sh --list
scripts/restore.sh daily/ngbcl-20261120T211500Z.sql.gz.enc   # asks you to type RESTORE
```

Practise this once on a spare VM (or locally with Docker) before launch so you know it works.

## Useful commands

```bash
cd ~/ngbcl/deploy
docker compose -f docker-compose.prod.yml logs -f --tail=200 api     # API logs
docker compose -f docker-compose.prod.yml exec db psql -U ngbcl       # SQL shell (careful)
docker compose -f docker-compose.prod.yml restart api
df -h / && free -m
```

## If something breaks

| Symptom | Check |
| --- | --- |
| Website up, "Can't reach the server" | `curl https://ngbcl-api.duckdns.org/actuator/health`; API logs; `docker compose ps` |
| Certificate errors | DuckDNS points at the VM? Ports 80/443 open in both the security list and iptables? `docker compose logs caddy` |
| Staff can't stay signed in | `NGBCL_ALLOWED_ORIGINS` = exact site origin; `API_PROXY_TARGET` set on Vercel; redeploy Vercel after env changes |
| Live auction/scores don't update | `NEXT_PUBLIC_WS_URL` correct (wss://…/ws); site origin in `NGBCL_ALLOWED_ORIGINS` |
| Uploads fail | R2 CORS includes the site origin; `NEXT_PUBLIC_STORAGE_ORIGIN` set |
| healthchecks.io "backup failed" email | `~/ngbcl-backup.log` |
