# NGBCL frontend

Next.js 16 (App Router, Cache Components) · React 19 · TypeScript · `motion` (Framer Motion) · TanStack Query · react-hook-form + zod · STOMP.

```bash
npm install
cp .env.example .env.local   # already present for local dev
npm run dev                   # http://localhost:3000
```

## Structure

```
src/
  app/                 routes — (public), (auth)/login, admin, console/auction, display/auction, score, ds
  design-system/       styles/ngbcl-ds.css (verbatim export), fonts (self-hosted), tokens,
                       primitives/, data/, sport/, live/, motion/
  features/            screen-level composition per domain (home, admin-shell, public-shell, display, auth…)
  lib/                 api (typed fetch + problem+json errors), auth (in-memory access token,
                       refresh cookie), realtime (STOMP hub + versioned topics), format (money, dates, names)
  proxy.ts             coarse route gate for /admin, /console, /score (UX only — the API enforces auth)
```

## Public pages and caching

Public pages read the API through tagged `'use cache'` fetchers (`src/lib/api/public.ts`) and refresh every few
minutes. For instant updates after an admin change, set the same random value as `REVALIDATE_SECRET` here and
`NGBCL_SITE_REVALIDATE_SECRET` on the API, with `NGBCL_SITE_REVALIDATE_URL=https://<site>/api/revalidate`.

## Auction screens (M4)

- `/admin/system/settings/auction` rules · `/admin/auction/setup` queue order and per-player base prices ·
  `/admin/auction/results` squads, purses and corrections.
- `/console/auction` auctioneer console: keys `1`–`9` pick a team, `Enter` places the bid. Commands go over REST
  and answer with a fresh snapshot; realtime messages only trigger refetches.
- `/display/auction` 1920×1080 projector view (scaled to any screen); `/auction` public live page and results.

## Competition screens (M5)

- `/admin/competition/format` rules, courts, seeding + snake group draw, generate · `/admin/competition/fixtures`
  list / bracket / cross-table with the tie editor (schedule, knockout teams, line-ups, scorer) and publish dialog ·
  `/admin/competition/schedule` drag-and-drop board · match categories in Settings · public `/fixtures`.

## Conventions

- Every data view handles **loading → empty → error → content**; mutations end in a toast or inline field errors from the server.
- Money: `Money` component (`LKR` label in admin, `Rs.` on public pages, compact only on the big display).
- Status: `StatusChip status="PENDING"` — never pick status colours by hand.
- Animations use `design-system/motion/presets.ts` durations and respect reduced motion via `MotionProvider`.
- API types: `npm run gen:api` (with the backend running) regenerates `src/lib/api/schema.d.ts` from OpenAPI.

Read `AGENTS.md` — this Next.js version has breaking changes (e.g. `middleware` → `proxy`, async `params`).
