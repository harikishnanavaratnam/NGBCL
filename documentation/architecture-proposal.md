# NGBCL 1.0 Platform — Architecture Proposal

**Status:** Approved 17 Nov 2026 (with the decisions below) · M0 delivered
**Inputs reviewed:** `documentation/badminton_tournament_management_srs.md` (functional source of truth), `documentation/NGBCL 1.0 … UI UX Design Brief.md`, `design/ngbcl-design/` (Claude Design export: `ngbcl-ds.css`, 109 screen boards, assets, screenshots), `design/NGBCL 1.0 — Platform Wireframes.html`, hero video and brand files at the repo root.

> Approved. Decisions from review are recorded in §0.5 and override anything below that conflicts.

## 0.5 Decisions log (17 Nov 2026)

| # | Topic | Decision |
| --- | --- | --- |
| D1 | Visual source of truth | Follow the **export** (Barlow Condensed + DM Sans, charcoal `#101318`, lime `#D8FF42`, gold `#C9A45C`, dark admin). Adjust only where the export is inconsistent with itself. |
| D2 | Accounts | **Only staff sign in** (Super Admin, Tournament Admin, Registration Manager, Auctioneer, Match Scorer — all created by an admin). Players and teams never have accounts. **Team Manager portal dropped** for edition 1. |
| D3 | Tournament specifics (§14) | Number of teams, squad size, budgets, increments, match categories, format, scoring rules, tie-breaks, result approval (1- or 2-step), public fields, dates — **all admin-configurable**; nothing hard-coded. Seeded with sensible defaults in M1/M4/M5. |
| D4 | Hero video | YONEX marks **removed** from the clip (racket stencil, shorts and chest logos inpainted; floor boards cropped). New `hero.webm` 170 KB / `hero.mp4` 478 KB, 7.5 s seamless loop (0.5 s crossfade), no audio. Mobile uses a cleaned portrait still. |
| D5 | Logo | Vectorised from the source JPEG: `ngbcl-logo(-on-dark).svg`, wordmark, shuttle mark, favicon set, 2000 px PNGs. |
| D6 | Organiser logo | Mighty Sharks Battalion logo from `images.jpg` (447 px, raster) — used in footer and auction display. A larger/vector original would be better when available. |
| D7 | Sponsorship proposal link | **Hidden** until a PDF is supplied. |
| D8 | Source media | Moved to `design/source-assets/` (raw video, square still, logo sources). |
| D9 | Versions | Next.js **16.4** (Cache Components, `proxy.ts`), React 19.3; **Spring Boot 3.5.15** (actively patched 3.5 line, Spring Security 6.5) on Java 21. Upgrade to Boot 4.x is a contained follow-up once the build runs in CI. |
| D10 | Fonts | Self-hosted woff2 (OFL, from Fontsource) via `next/font/local` — no build-time dependency on Google Fonts. |


---

## 0. What the inspection found (read first)

### 0.1 Design export vs. brief — the export wins for visuals

The export moved away from the brief in a few places. I will follow the **export** (as instructed) unless you say otherwise:

| Topic | Brief said | Export actually uses (will implement) |
| --- | --- | --- |
| Fonts | Archivo / Inter / JetBrains Mono / Instrument Serif | **Barlow Condensed** (display, scores, italic 800 uppercase) + **DM Sans** (UI, body, tabular refs). Noto Sans Tamil / Sinhala fallbacks |
| Base colour | Navy `#0B1B3A` night theme | Charcoal **`#101318`** / `#191F27` / surfaces `#222A34`, `#2B3440` |
| Live / CTA | Volt `#D4F23A` | Lime **`#D8FF42`** |
| Gold | `#C99A2E` | **`#C9A45C`** / `#D6B877` |
| Admin "Day" theme | Warm paper light theme | **Dark everywhere** (admin is also dark) |
| Status colours | Day-theme tints | Dark-theme `--ok / --warn / --err / --info` + tints |

The `.tbc` dashed-gold placeholder (for unconfirmed official data such as dates, venue, team count) is part of the design and will become a real component driven by "not yet configured" API values.

### 0.2 Export packaging issues (handled in M0)

- Screens reference `./support.js` (not included) and `/_blob/<hash>` URLs (11 unique hashes) instead of `assets/…`. They aren't directly runnable; I'll map each blob to its asset by context and use the screenshots + the 7 MB standalone wireframe HTML as the visual reference.
- `ngbcl-ds.css` is clean, token-based and will be **ported verbatim** as the global design-system layer (not rewritten into a utility framework).

### 0.3 Scope the design adds beyond the SRS (included in this plan)

Sponsors (tiers + wall + title lockup), Organising Committee page + admin, prize pool podium (Rs. 600,000) and third-place playoff, Jaffna eligibility declaration + home area on player registration, team entry fee (info only) + "Fee received" admin toggle, Facebook follow/share, Branding/Registration/Standings settings pages, Team Manager portal (behind a feature flag), auctioneer "Restored session" and scorer "Offline" states.

### 0.4 Hero video check

| Check | Result |
| --- | --- |
| Raw source (`0f17…1794907786.mp4`) | 1280×720, 8.0 s, H.264 + **AAC audio**, 2.3 MB. Not for shipping (audio, YONEX floor boards visible). |
| Processed `assets/hero.mp4` / `hero.webm` | 1280×592, 7.5 s, **audio stripped**, 600 KB / 190 KB. Well under the 3 MB budget. ✅ |
| Poster frame `hero-poster.jpg` | 1280×592, matches first frame. ✅ |
| Loop seam | First vs last frame differ by ~1/255 on average — loops cleanly. ✅ |
| YONEX floor boards | Removed by the crop to 592 px height. ✅ |
| **YONEX racket stencil** | Still clearly visible on the strings around 3–4 s (when the racket swings into frame), plus a tiny logo on the shorts. ⚠️ |
| Framing | Design uses `object-fit: cover; object-position: 72% 30%`, which keeps the player in frame on desktop. ✅ |
| Resolution | 1280 px wide is upscaled on 1440 px+ / retina screens (slightly soft under the dark overlay). The source is only 720p, so re-encoding at 1920 wouldn't add detail. Acceptable; a higher-res source would be better. |
| Mobile | The mobile home board has no video. Plan: no video under 768 px or on `Save-Data`/reduced motion → still image. The square still `89c0…1794907425.jpeg` (1024×1024, full jump + court) is a better mobile/OG source than the 2.16:1 poster. It also shows the YONEX stencil + floor board, so it needs the same treatment. |
| Logo files | `logo-dark.png` / `logo-wordmark.png` are transparent RGBA but only 601 px wide — fine for the header, too small for the 1920×1080 auction display. The WhatsApp logo is JPEG on white. |

**Needed from you for the hero/brand:**
1. Decision on the racket YONEX stencil: accept it, or regenerate/blur that segment (I can mask it with ffmpeg if you prefer — it's a moving object, so a regenerated clip is cleaner).
2. A **vector (SVG) or ≥2000 px transparent PNG** of the NGBCL logo, and the shuttle mark alone for the favicon.
3. **Mighty Sharks Battalion logo** (footer, auction display).
4. **Sponsorship proposal PDF** (the "Become a sponsor" link) — or confirm we hide that link for now.
5. OK to move the three root-level files into `design/source-assets/` and add a `.gitignore` (raw media shouldn't sit at repo root).

---

## 1. Architecture proposal

### 1.1 Shape

```
                    Cloudflare (DNS, TLS, WAF, Turnstile)
                 ┌──────────────┴───────────────┐
        app.ngbcl.lk (Next.js)            api.ngbcl.lk (Spring Boot)
   SSR/ISR public pages, client apps  ──REST /api/** ──►  modular monolith
   (admin, consoles, display, scorer) ◄─STOMP/WS /ws ──   ├── PostgreSQL 16 (Flyway)
                 │                                         └── Cloudflare R2 (S3 API)
                 └──── images/video from cdn.ngbcl.lk  ◄── public R2 bucket (verified assets only)
```

- **Frontend (Next.js, TypeScript):** presentation only. No business rule decides anything on the client; the UI shows *sending → accepted / rejected* from the server.
- **Backend (Spring Boot, Java 21):** a single deployable **modular monolith** (SRS §10). Module boundaries are package-level and checked by Spring Modulith tests so they can't leak.
- **Database:** PostgreSQL is the single source of truth, including live auction state. No in-memory auction state that could be lost on restart (supports the "Restored session" screen and §9.2 recovery).
- **Real-time:** **commands over REST, broadcasts over WebSocket.** Bids, score points and lot actions are REST calls (auth, validation, idempotency, error bodies are simpler and testable). After the DB transaction commits, the backend publishes a versioned event on a STOMP topic. Every event carries a `stateVersion`; a client that sees a gap refetches the snapshot.
- **Files:** presigned direct-to-R2 uploads into a **private** bucket; on verification + approval the asset is copied to a **public** bucket served by a custom domain. Private data never has a public URL.

### 1.2 Key technical decisions

| Concern | Decision | Why |
| --- | --- | --- |
| Auth | Spring Security, short-lived **access JWT** (15 min, held in memory) + **rotating refresh token in an httpOnly, Secure, SameSite=Strict cookie**. Argon2id password hashing. | No tokens in localStorage; stateless API; NFR-004. |
| Authorisation | Roles → fine-grained **permissions** (e.g. `REGISTRATION_REVIEW`, `AUCTION_CONTROL`, `MATCH_SCORE`, `RESULT_CONFIRM`, `AUDIT_VIEW`). `@PreAuthorize` on every service method, plus scope evaluators: `@matchAccess.isAssignedScorer(#matchId)`, `@teamScope.owns(#teamId)`. | SRS §2.1, §12 — enforced server-side; the frontend only hides what the API would refuse anyway. |
| Public API privacy | Separate **public DTOs** (never entity serialisation) under `/api/public/**`; an ArchUnit test fails the build if a public DTO has fields like `email`, `phone`, `address`, `remarks`. | PUB-008, NFR-014. |
| Bid concurrency | One transaction per bid: `SELECT … FOR UPDATE` on the `auction_players` row → validate against latest accepted bid, increment, team remaining budget, squad space → insert bid → bump `state_version`. Client sends an **idempotency key**. Partial unique indexes as a final guard. | AUC-025–031, NFR-007. Lock is per lot, so contention is tiny. |
| Sale atomicity | `SOLD` = one transaction: transaction row + `team_players` allocation + `team_budgets` update + lot close + audit row. Budget columns have `CHECK (remaining >= 0)`. | AUC-031, "succeed or fail together". |
| Scoring | Append-only **score events** (point, undo, game-end, retire, walkover) with a client event id; games/match state are derived by a pure Java **rules engine** (`pointsToWin`, `winBy`, `cap`, `gamesToWin`). Offline scorer queue replays events idempotently. | MAT-002–006, offline tolerance, full point log for confirmation/corrections. |
| Results | `IN_PROGRESS → SUBMITTED (provisional) → CONFIRMED`, with configurable one-step or two-step approval. Confirmed fixture results trigger standings + stats recalculation (small data → full recompute per stage, cached in tables). | MAT-007–009, STA-007–008. |
| Audit | `AuditService` + `@Audited` aspect writing actor, action, entity, before/after JSON diff, reason, timestamp — in the **same transaction** as the change. | REP-005/006, AUC-034. |
| Public forms | Cloudflare **Turnstile** + Bucket4j rate limits; a Turnstile-verified submission gets a short-lived **upload token** scoped to one draft registration. | NFR-006, §7.2. |
| Image checks | Apache Tika (magic-byte sniffing) + TwelveMonkeys ImageIO (JPEG/PNG/WebP decode, dimension check) + Thumbnailator (resized derivatives). | §7.3 "validate actual content". |
| API contract | springdoc-openapi → `openapi.json` → **openapi-typescript** generates frontend types; `openapi-fetch` typed client. | One contract, no drift. |
| Time | Store UTC (`timestamptz`), display in tournament time zone (`Asia/Colombo`). Money as `bigint` LKR (whole rupees) with currency on the tournament. | §6.4, brief money rules. |
| Notifications | `NotificationPort` with an outbox table; email adapter (SMTP) when you pick a provider, log adapter until then. | PLY-009 is "Should"; channel unconfirmed (§14). |
| Scaling | One backend instance for edition 1 (simple broker). If we ever run 2+, switch the STOMP simple broker to a relay (RabbitMQ/Redis) — no code change in modules. | Avoid premature infra. |

### 1.3 Frontend rendering strategy

| Surface | Rendering | Notes |
| --- | --- | --- |
| Public site (`/`, `/teams`, `/players`, `/fixtures`, `/standings`, `/news`, `/committee`) | Server Components + ISR (revalidate on publish via a backend webhook) | Fast mobile loads (NFR-013); live strip hydrates and subscribes to WS. |
| `/live/[matchId]`, `/auction` | SSR snapshot + WS | Snapshot first paint, then live. |
| Registration wizards | Client components | react-hook-form + zod mirroring backend validation (backend still re-validates). |
| `/admin/**` | Client app behind `middleware.ts` session gate | Role-filtered sidebar from `/api/me` permissions. |
| `/console/auction` | Client, full-screen, keyboard shortcuts | 1280 px laptop minimum. |
| `/display/auction` | Client, fixed **1920×1080 canvas scaled to fit** (letterboxed) | No auth; read-only topics. |
| `/score/**` | Client, tablet-landscape first, phone-portrait supported | IndexedDB offline queue. |

**Motion:** the `motion` library (Framer Motion) for the bid-change count-up and lime flash (320 ms ease-out-expo), SOLD reveal (900 ms), rejected-bid shake, drawer/panel transitions (200 ms), score point tick, page transitions on public pages. All gated by a single `MotionProvider` that honours `prefers-reduced-motion`. Durations/easing come from the DS tokens (`--ease: cubic-bezier(.16,1,.3,1)`).

**Styling:** `ngbcl-ds.css` imported globally (tokens + primitive classes exactly as exported) + **CSS Modules** per component for layout. Fonts via `next/font/google` (Barlow Condensed, DM Sans). No Tailwind/shadcn — that would pull the look toward a generic dashboard.

---

## 2. Frontend folder structure

```
frontend/
├─ src/
│  ├─ app/
│  │  ├─ (public)/                      # Night shell: live strip, top bar, footer
│  │  │  ├─ page.tsx                    # Home (phase-aware hero, prize podium, crest wall…)
│  │  │  ├─ teams/ · teams/[slug]/
│  │  │  ├─ players/ · players/[slug]/
│  │  │  ├─ auction/                    # results + link to live display
│  │  │  ├─ fixtures/ · live/[matchId]/
│  │  │  ├─ standings/ · stats/ · stats/players/[slug]/ · stats/h2h/
│  │  │  ├─ news/ · news/[slug]/
│  │  │  └─ committee/
│  │  ├─ (register)/register/
│  │  │  ├─ player/ (4-step wizard) · player/confirmation/
│  │  │  └─ team/   (3-step wizard) · team/confirmation/
│  │  ├─ (auth)/login/
│  │  ├─ admin/                         # sidebar shell, mirrors brief §6 groups
│  │  │  ├─ page.tsx                    # Overview (phase-aware dashboard)
│  │  │  ├─ registrations/{players,teams,duplicates}/
│  │  │  ├─ directory/{players,players/[id],teams,teams/[id],categories}/
│  │  │  ├─ auction/{setup,results,roster,corrections}/
│  │  │  ├─ competition/{format,fixtures,schedule,bracket,match-categories,scoring-rules}/
│  │  │  ├─ results/{confirmations,standings,statistics}/
│  │  │  ├─ content/{announcements,sponsors,committee,branding}/
│  │  │  └─ system/{users,audit,reports,settings}/
│  │  ├─ console/auction/
│  │  ├─ display/auction/
│  │  ├─ score/ · score/[matchId]/ · score/history/
│  │  ├─ team/                          # Team Manager portal (feature-flagged)
│  │  ├─ ds/                            # internal living style guide (DS foundations/components)
│  │  ├─ layout.tsx · not-found.tsx · error.tsx · global-error.tsx
│  ├─ design-system/
│  │  ├─ styles/ngbcl-ds.css            # verbatim port of the export
│  │  ├─ tokens.ts · fonts.ts
│  │  ├─ primitives/   Button, Input, PhoneInput(+94), Select, DateInput, Textarea, Toggle,
│  │  │                RadioCard, Checkbox, StatusChip, Tabs, Segmented, Breadcrumb,
│  │  │                Pagination, Tooltip, Toast, Modal, ConfirmDialog(2-step), Drawer(480/720),
│  │  │                CourtDivider, Tbc, MediaSlot, Eyebrow, Money, Score, Mono
│  │  ├─ data/         DataTable (sticky, sortable, column chooser, bulk bar, 25/50/100),
│  │  │                FilterBar, AuditTimeline, StatBlock, EmptyState, ErrorState, Skeletons
│  │  ├─ sport/        PlayerCard(public/admin/lot/roster), CrestTile(S/M/L/XL), BudgetMeter,
│  │  │                SquadSlots, BidLadder, LotStatusBar, TieCard, MatchScoreboard,
│  │  │                GameScoreTiles, StandingsTable, Bracket, CrossTable, ImageUploader(crop 4:5/1:1),
│  │  │                PrizePodium, SponsorWall, CrestWall, DecoCorners, GoldOrbit
│  │  ├─ live/         ConnectionPill, LiveDot, LiveStrip, PendingValue(sending/accepted/rejected),
│  │  │                UndoToast, ProvisionalBanner
│  │  └─ motion/       presets.ts, MotionProvider, CountUp, BidFlash, SoldReveal, Shake
│  ├─ features/                         # screen-level composition + hooks, one folder per domain
│  │  ├─ home/ (HeroVideo, PhaseCta, Countdown…)  ├─ registration/  ├─ review/
│  │  ├─ directory/  ├─ auction/ (console, display, useAuctionChannel, reducer)
│  │  ├─ roster/  ├─ fixtures/  ├─ scoring/ (scorer machine, offline queue)
│  │  ├─ results/  ├─ standings/  ├─ content/  ├─ system/  └─ team-portal/
│  ├─ lib/
│  │  ├─ api/  schema.d.ts (generated), client.ts, query-keys.ts, errors.ts (problem+json)
│  │  ├─ auth/ session.ts, permissions.ts (UI gating only), guards.tsx
│  │  ├─ realtime/ stomp-client.ts, useTopic.ts, version-gap → refetch
│  │  ├─ upload/ presign → PUT → complete, progress
│  │  └─ format/ money (Rs./LKR), dates (Asia/Colombo), names (first + SURNAME)
│  └─ middleware.ts
├─ public/brand/  logos, favicon, hero.webm/mp4, hero-poster.jpg, hero-mobile.jpg
├─ tests/ (Vitest + Testing Library) · e2e/ (Playwright)
└─ package.json · next.config.ts · tsconfig.json · .env.example
```

Every data screen uses one pattern: `Skeleton` while loading → `EmptyState` (court-line art + one action) → `ErrorState` (message + retry) → content; mutations end in a toast (success) or inline field errors from the server's problem+json.

---

## 3. Backend module structure

```
backend/  (Maven wrapper, Java 21, Spring Boot)
└─ src/main/java/lk/ngbcl/
   ├─ NgbclApplication.java
   ├─ shared/        BaseEntity (UUIDv7, audit timestamps, @Version), Money, PageResponse,
   │                 GlobalExceptionHandler (RFC 9457 problem+json), BusinessRuleException(code),
   │                 TournamentContext, Clock
   ├─ security/      SecurityConfig, JwtService, RefreshTokenService, Permission enum,
   │                 MatchAccessEvaluator, TeamScopeEvaluator, RateLimitFilter, TurnstileVerifier
   ├─ identity/      users, roles, permissions, auth (login/refresh/logout), /me
   ├─ tournament/    tournaments, settings, registration windows, publication & feature flags, phase
   ├─ files/         R2 config, presign, upload completion + verification, publish-to-public,
   │                 derivatives, cleanup job (@Scheduled)
   ├─ registration/  player & team registrations, reference generator, duplicate detection,
   │                 review workflow (approve/reject/note/link), public submission endpoints
   ├─ player/        players, skill categories, withdraw/deactivate
   ├─ team/          teams, admin create, fee received, team managers
   ├─ auction/       auction + settings (+ snapshot), lot queue, bidding engine, lot lifecycle,
   │                 sale/unsold/withdraw, undo, budgets, roster, allocation corrections
   ├─ competition/   stages/format, groups & seeding, fixture templates, fixture generation
   │                 (round robin circle method, knockout + 3rd-place), courts, scheduling
   │                 conflict checker, tie editor, publish (diff)
   ├─ scoring/       scoring rules engine, score events, game/match derivation, submit,
   │                 confirm/return, exceptional outcomes, result corrections
   ├─ standings/     standings calculator, tie-break chain, player statistics, h2h
   ├─ content/       announcements, sponsors, committee members, branding
   ├─ audit/         AuditService, @Audited aspect, audit log queries
   ├─ reporting/     registration summary, auction spend, rosters, fixtures/standings (CSV/PDF)
   ├─ notification/  NotificationPort, outbox, email + log adapters
   └─ realtime/      STOMP config, CONNECT auth interceptor, topic publishers,
                     @TransactionalEventListener(AFTER_COMMIT) bridges
```

Inside each domain module: `web/` (controllers, request/response DTOs, public vs admin controllers split), `service/` (transactional use cases, `@PreAuthorize`), `domain/` (entities, enums, rule objects), `repo/` (Spring Data JPA + native queries where needed). Modules talk via services or domain events, never another module's repository.

Resources: `db/migration/V1__…sql` (Flyway), `application.yml` + profiles (`local`, `test`, `prod`), seed data (roles, permissions, default scoring rules, team colour slots).

Tests: JUnit 5 + **Testcontainers PostgreSQL**; auction concurrency test (N threads bidding on one lot → exactly one valid ladder, budgets never negative); rules-engine property tests (deuce, cap at 30); Spring Modulith boundary test; ArchUnit privacy test.

---

## 4. Database entity proposal

PostgreSQL 16, UUID (v7) keys, `timestamptz`, soft states instead of deletes. ★ = beyond SRS §6.1 (from the brief/design or needed for correctness).

### 4.1 Identity & tournament

| Table | Key columns / constraints |
| --- | --- |
| `users` | email (unique, citext), password_hash, display_name, status, last_login_at |
| `roles`, `permissions`★, `role_permissions`★, `user_roles` | `user_roles(user_id, role_id, tournament_id NULL)` — role can be tournament-scoped |
| `refresh_tokens`★ | user_id, token_hash, family_id, expires_at, revoked_at (rotation + reuse detection) |
| `tournaments` | name, edition ("1.0"), slug, description, starts_on, ends_on, venue, status (DRAFT/UPCOMING/ACTIVE/COMPLETED/ARCHIVED), timezone, currency, logo/banner asset ids |
| `tournament_settings`★ | reg windows (player/team open/close), category_required, result_approval_mode (ONE_STEP/TWO_STEP), publication flags (show_prices, show_budgets), feature flags (team_manager_portal, exceptional_outcomes), duplicate match fields |
| `reference_sequences`★ | (tournament_id, kind) → next_value; drives `PLY-2026-00142` / `TEM-2026-0007` |

### 4.2 Files

| `file_assets` | as SRS §6.3 + `bucket` (PRIVATE/PUBLIC), `public_object_key`, width, height, `verification_status` (PENDING/VERIFIED/REJECTED), `upload_token_id`, `derivatives jsonb` |
| `upload_tokens`★ | scope (PLAYER_PHOTO/TEAM_LOGO), draft_ref, expires_at, used_at, ip_hash |

### 4.3 Players, teams, registrations

| Table | Key columns / constraints |
| --- | --- |
| `players` | full_name, date_of_birth, gender, email, phone, address, profile_photo_asset_id, slug |
| `player_categories` | tournament_id, name, description, is_active, display_order — unique(tournament_id, name) |
| `player_registrations` | tournament_id, player_id NULL, registration_number (unique), submitted fields (typed columns), home_area★, jaffna_declared★, experience, emergency_name/phone, consent_version + consent_at★, photo_asset_id, skill_category_id NULL, status (PENDING/APPROVED/REJECTED/WITHDRAWN), reviewed_by/at, rejection_reason, admin_remarks, `extra jsonb` (optional future fields only) |
| `registration_duplicates`★ | registration_id, candidate_registration_id / candidate_player_id, matched_on[], resolution (LINKED/NEW_PROFILE/NOT_DUPLICATE/OPEN), resolved_by/at |
| `registration_notes`★ | internal notes per registration (never public) |
| `tournament_players`★ | (tournament_id, player_id) unique; status (APPROVED/IN_POOL/SOLD/UNSOLD/WITHDRAWN/INACTIVE), public flag — the per-edition player state used by directory + auction eligibility |
| `team_registrations` | as SRS + description, status, review fields |
| `teams` | as SRS + slug, short_code (3), colour_slot (1–10), description, fee_received★ + fee_received_at/by — unique(tournament_id, lower(name)), unique(tournament_id, short_code) |
| `team_managers`★ | user_id, team_id |

### 4.4 Auction

| Table | Key columns / constraints |
| --- | --- |
| `auctions` | tournament_id (unique per edition), status (DRAFT/READY/LIVE/PAUSED/COMPLETED), current_lot_id, **state_version bigint**, started_at, settings_snapshot jsonb (AUC-007) |
| `auction_settings` | initial_team_budget, max_squad_size, min_squad_size★, default_base_price, min_bid_increment, allow_reauction, allow_manual_adjustment — CHECKs > 0 |
| `team_budgets` | auction_id, team_id (unique pair), initial_budget, spent_amount, remaining_budget, squad_count — CHECK remaining ≥ 0, squad_count ≤ max |
| `auction_players` | auction_id, tournament_player_id (unique pair), base_price, lot_state (QUEUED/OPEN/GOING_ONCE/GOING_TWICE/SOLD/UNSOLD/WITHDRAWN), round (1 = main, 2+ = re-auction), auction_order, current_bid_id, final_price, winning_team_id, started_at, closed_at, @version |
| `auction_bids` | auction_player_id, team_id, bid_amount, bid_number, placed_by, idempotency_key (unique), status (ACCEPTED/OUTBID/VOIDED/REJECTED), rejection_code, created_at — append-only |
| `auction_transactions` | auction_player_id, team_id, player_id, winning_bid_id, final_amount, transaction_status (SOLD/UNSOLD/WITHDRAWN/REVERSED), confirmed_by/at — partial unique: one SOLD per auction_player |
| `team_players` | tournament_id, team_id, player_id, source (AUCTION/ADMIN), purchase_price, transaction_id, active — **partial unique (tournament_id, player_id) WHERE active** (BR-012, ROS-006) |
| `budget_adjustments`★ / `allocation_corrections`★ | amount or from/to team, reason, actor, linked audit id (ROS-005) |

### 4.5 Competition & scoring

| Table | Key columns / constraints |
| --- | --- |
| `stages`★ | tournament_id, type (ROUND_ROBIN/KNOCKOUT/GROUPS), order, qualifiers_per_group |
| `groups`★, `group_teams`★ | name; seed |
| `courts`★ | tournament_id, name, venue |
| `match_categories` | tournament_id, code (MS/WS/MD/WD/XD…), name, players_per_side (1/2), gender_rule, scoring_rules_id |
| `scoring_rules`★ | points_to_win (21), win_by (2), cap (30), games_to_win (2), interval_at (11) |
| `fixture_templates`★ + `fixture_template_items`★ | ordered rubbers per tie |
| `team_fixtures` | as SRS + stage_id, group_id, round_name, court_id, status (DRAFT/SCHEDULED/LIVE/PROVISIONAL/CONFIRMED), published, bracket links (`winner_to_fixture_id`, `loser_to_fixture_id` for 3rd place, slot) — CHECK team_a ≠ team_b |
| `matches` | as SRS + scorer_user_id★, result_status (IN_PROGRESS/SUBMITTED/CONFIRMED/RETURNED), outcome (NORMAL/WALKOVER/RETIRED), first_server_side |
| `match_players` | match_id, team_id, player_id, side (A/B), position — trigger/service check: player ∈ team roster, count = players_per_side |
| `match_games` | match_id, game_number (unique pair), team_a_score, team_b_score, winner_side, completed_at — CHECK scores ≥ 0 |
| `score_events` (= SRS `score_history`) | match_id, seq, client_event_id (unique), type, side, game_number, recorded_by, recorded_at — append-only |
| `result_reviews`★ / `result_corrections`★ | submitted/confirmed/returned by+at, reason; correction before/after |
| `standings_rules`★ | points win/draw/loss, tie-break order (ordered list), qualification line |
| `team_standings` | stage/group, team, P/W/L, match W–L, game W–L, point diff, pts, form, position, basis (CONFIRMED), computed_at |
| `player_statistics` | tournament, player, matches played/won, games won/lost, points for/against |

### 4.6 Content, audit, ops

`announcements` (title, slug, body, pinned, status, published_at), `sponsors`★ (name, tier, logo, url, active, order), `committee_members`★ (name, role, photo, order), `audit_logs` (actor_id, actor_role, action, entity_type, entity_id, tournament_id, before/after jsonb, reason, ip, at — insert-only, no UPDATE grant), `notification_outbox`★.

---

## 5. API module proposal

REST under `/api`, JSON, problem+json errors with stable `code`s (e.g. `BID_EXCEEDS_BUDGET`, `BID_BELOW_MIN_INCREMENT`, `SQUAD_FULL`, `LOT_NOT_OPEN`, `PLAYER_NOT_IN_TEAM`, `SCHEDULE_CONFLICT`). Pagination `?page&size&sort`, filters as query params. Tournament is resolved from the active edition (no edition in URL until 2.0, per brief).

| Group | Endpoints (abridged) | Access |
| --- | --- | --- |
| **Auth** | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `GET /me` | public / any user |
| **Public** | `GET /public/tournament` (phase, dates, prize pool, settings that are public), `/public/teams`, `/public/teams/{slug}`, `/public/players?team&category&gender`, `/public/players/{slug}`, `/public/auction/results`, `/public/auction/state`, `/public/fixtures`, `/public/matches/{id}`, `/public/standings`, `/public/stats/*`, `/public/news`, `/public/sponsors`, `/public/committee` | anonymous, cached |
| **Public registration** | `POST /public/registrations/session` (Turnstile → upload token), `POST /public/uploads/presign`, `POST /public/uploads/{id}/complete`, `POST /public/player-registrations`, `POST /public/team-registrations` | anonymous, rate-limited |
| **Files (admin)** | `POST /files/presign`, `POST /files/{id}/complete`, `POST /files/{id}/verify`, `GET /files/{id}/view-url` (short-lived) | staff |
| **Registrations** | `GET /admin/registrations/players|teams`, `GET …/{id}`, `POST …/{id}/approve` (link/new profile), `/reject` (reason required), `/notes`, `PATCH …/{id}` (audited edit), `GET /admin/registrations/duplicates`, `POST …/duplicates/{id}/resolve`, `POST …/bulk-approve` | REGISTRATION_REVIEW |
| **Directory** | `/admin/players` (+ withdraw/deactivate), `/admin/teams` (create, edit, activate, fee-received), `/admin/categories` (CRUD, reorder, toggle) | TOURNAMENT_ADMIN / REGISTRATION_REVIEW (read) |
| **Tournament & settings** | `/admin/tournaments`, `/admin/settings/{registration,branding,auction,match-categories,scoring,standings}` | TOURNAMENT_ADMIN |
| **Auction admin** | `PUT /admin/auction/settings`, `POST /admin/auction/init-budgets`, `GET /admin/auction/budgets`, `POST /admin/auction/corrections/*` | AUCTION_ADMIN |
| **Auction console** | `GET /auction/console` (snapshot), `POST /auction/start|pause|resume|end`, `POST /auction/lots/{id}/open`, `PUT /auction/queue/order`, `POST /auction/lots/{id}/bids` (idempotency key), `POST /auction/lots/{id}/undo-bid`, `/going-once`, `/going-twice`, `/sell`, `/unsold`, `/withdraw` | AUCTION_CONTROL |
| **Competition** | `/admin/stages`, `/admin/groups`, `POST /admin/fixtures/generate`, `/admin/fixtures` CRUD, `/admin/fixtures/{id}/matches` (tie editor), `GET /admin/fixtures/conflicts`, `POST /admin/fixtures/publish` (`?dryRun=true` → diff), `/admin/courts` | TOURNAMENT_ADMIN |
| **Scoring** | `GET /score/my-matches`, `POST /matches/{id}/start`, `POST /matches/{id}/events` (batch, idempotent — supports offline replay), `PUT /matches/{id}/games` (game-by-game entry), `POST /matches/{id}/submit` | MATCH_SCORE + assigned |
| **Results** | `GET /admin/results/pending`, `POST /admin/results/{matchId}/confirm`, `/return`, `POST /admin/results/{matchId}/corrections` | RESULT_CONFIRM |
| **Standings & stats** | `GET /admin/standings?includeProvisional`, `POST /admin/standings/recalculate` | TOURNAMENT_ADMIN |
| **Content** | `/admin/announcements`, `/admin/sponsors`, `/admin/committee` | CONTENT_EDIT |
| **System** | `/admin/users`, `/admin/roles`, `/admin/audit?entity&actor&from&to`, `/admin/reports/{type}?format=csv` | SUPER_ADMIN / AUDIT_VIEW |
| ~~**Team portal**~~ | — | Dropped for edition 1 (D2) |

**WebSocket (STOMP at `/ws`)** — broadcast only:

| Topic | Payload | Who |
| --- | --- | --- |
| `/topic/auction` | `AuctionState` (lot, state, highest bid, leading team, budgets rail, stateVersion) | public |
| `/topic/auction/events` | `BID_ACCEPTED`, `LOT_SOLD`, `LOT_UNSOLD`, `PAUSED`… (drives display animations) | public |
| `/topic/matches/{id}` | live score delta + version | public if fixture published |
| `/topic/live` | live-strip summary (what's live now) | public |
| `/user/queue/notices` | console/scorer-specific notices | authenticated |

---

## 6. Development milestones

Each milestone ends with a demo against the listed SRS acceptance criteria (§13) and is merged only with tests green.

| # | Milestone | Scope | Exit criteria |
| --- | --- | --- | --- |
| **M0** | Foundations | Monorepo, docker-compose (Postgres + MinIO as local R2), CI (lint, typecheck, test, build), Spring Boot skeleton + Flyway V1 (identity, tournament, audit, files), auth + permissions + `/me`, OpenAPI → TS codegen, **DS port** (`ngbcl-ds.css`, fonts, primitives, motion presets, `/ds` style guide matching DSFoundations/DSComponents boards), app shells (public, admin sidebar, console, display, scorer), asset tidy-up | Login works; role-filtered admin shell; `/ds` visually matches export screenshots |
| **M1** | Tournament config & files | Tournament + settings screens (Registration, Branding, Categories), R2 presign/verify/publish pipeline, image uploader with crop, cleanup job | Admin configures windows/categories; upload → verified asset round trip |
| **M2** | Registration & review | Player wizard (4 steps + confirm, not-open/closed states, desktop + mobile), team wizard (3 steps), Turnstile + rate limits, reference numbers, duplicate detection, inbox + review drawer (verify photo, approve/reject/note, J/K/A/R), bulk approve, admin player/team directories, admin team creation, audit trail | AC 1–6, 15, 16 (approval part) |
| **M3** | Public site | Home (hero video, phase CTAs, countdown, prize podium, owner block, crest wall, sponsor wall, news), teams, players, player page, news, committee, sponsors/committee admin, ISR revalidation | Pages match Main/Mobile/Pub-* boards; no private fields in any public response (automated test) |
| **M4** | Auction | Settings + snapshot, budget init, queue + reorder, console (all states: open, rejected bid, SOLD confirm, paused, restored), bidding engine, public display (intro, lot, new bid, sold, unsold, paused, ended), public auction results, roster & budgets, correction flow, concurrency/load test | AC 7–10; 50 concurrent bidders test passes with zero inconsistencies |
| **M5** | Competition | Format wizard, groups & seeding, fixture template, generation (RR + KO + 3rd place), list / schedule board (drag, conflicts) / bracket / cross-table, tie editor (roster-only pickers, 1-vs-2 players, scorer assignment), publish with diff | AC 11; FIX-008/009 enforced by API |
| **M6** | Scoring & results | Scorer app (my matches, pre-match, live, deuce/cap, game end, manual entry, submit, offline queue), rules engine, live public match page, results-to-confirm queue, return-to-scorer, corrections | AC 12–13 |
| **M7** | Standings, stats, reports | Standings (groups, qualification line, provisional banner, tie-break popover), leaders, player stats, H2H, fixture results, admin standings + recalc, reports/CSV, announcements, Team Manager portal (flag) | AC 14; STA-001–008 |
| **M8** | Hardening & launch | Security review (authz matrix test per endpoint), a11y pass (focus rings, contrast, reduced motion), performance budget on mobile, backup + **restore drill**, auction/scoring recovery runbook, monitoring, prod deploy, UAT | SRS Phase 5; NFR-003/009/015 |

M2 and M3 can overlap; M4 is the riskiest and should be done well before auction day — tell me the auction date and I'll plan backwards from it.

---

## 7. Dependencies & setup requirements

### 7.1 Tooling

| Tool | Version |
| --- | --- |
| Node.js | 22 LTS (your machine has 22.x ✅) + **pnpm** 9+ |
| Java | **JDK 21** LTS (Temurin) — Maven Wrapper committed, no global Maven needed |
| Docker Desktop | for local Postgres + MinIO and Testcontainers |
| PostgreSQL | 16 (local via Docker; managed in prod) |
| Git | repo already initialised (`main`) |

### 7.2 Frontend packages

`next`, `react`, `react-dom`, `typescript` · `motion` (Framer Motion) · `@tanstack/react-query` · `react-hook-form` + `zod` + `@hookform/resolvers` · `@stomp/stompjs` · `openapi-fetch` + `openapi-typescript` (dev) · `@dnd-kit/core` + `@dnd-kit/sortable` (queue, seeding, schedule board) · `react-easy-crop` (4:5 / 1:1 crop) · `date-fns` + `date-fns-tz` · `idb-keyval` (scorer offline queue) · `@marsidev/react-turnstile` · `clsx` · dev: `eslint`, `prettier`, `vitest`, `@testing-library/react`, `@playwright/test`. Charts: hand-built SVG components (bars + one line, per brief) — no chart library needed.

### 7.3 Backend dependencies

Spring Boot starters: `web`, `validation`, `security`, `oauth2-resource-server` (JWT), `data-jpa`, `websocket`, `actuator` · `flyway-core` + `flyway-database-postgresql` · `postgresql` · `springdoc-openapi-starter-webmvc-ui` · `spring-modulith-starter-core` · AWS SDK v2 `s3` (R2 endpoint, presigner) · `apache-tika-core` · TwelveMonkeys `imageio-webp`/`imageio-jpeg` · `thumbnailator` · `bucket4j` · `argon2` (via spring-security-crypto + bouncycastle) · `mapstruct` · `lombok` (optional — your call) · tests: `spring-boot-testcontainers`, `testcontainers-postgresql`, `archunit`, `spring-modulith-starter-test`.

### 7.4 Accounts, secrets and environment

| Item | Needed by | Notes |
| --- | --- | --- |
| Cloudflare account: **2 R2 buckets** (`ngbcl-private`, `ngbcl-public`), R2 API token (access key + secret), account ID | M1 | MinIO locally until then |
| Custom domain for public assets (e.g. `cdn.<domain>`) | M3 | bound to the public bucket |
| Cloudflare **Turnstile** site key + secret | M2 | free |
| Domain(s): app + api | M8 (earlier for staging) | |
| Hosting choice: frontend (Vercel or container) + backend container with WebSocket support (Railway / Fly.io / Render / a VPS) + managed Postgres with automated backups | M8 (staging by M3) | tell me if you have a preference / budget |
| Email provider (SMTP: Resend, SES, Brevo…) | optional, M2 | for "registration received" (PLY-009) |
| `.env.example` for both apps; secrets never committed | M0 | |

### 7.5 Decisions I need (non-blocking for M0 unless marked)

1. **Approve this architecture** (or tell me what to change). ← blocks M0
2. Visual source of truth = the **export's** fonts/colours/dark admin (§0.1)? ← blocks M0 DS port
3. Hero/brand items in §0.4 (YONEX stencil, vector logo, Sharks logo, proposal PDF, moving root files).
4. SRS §14 defaults to seed (all configurable, needed by M4/M5): number of teams, max squad size, initial purse / base price / min increment (LKR), match categories per tie, format, scoring rules (21/2/30/best of 3?), tie-break order, one-step vs two-step result approval, whether Team Manager logins are on, publicly visible player fields.
5. Auction date and tournament dates (to schedule M4 safely).
