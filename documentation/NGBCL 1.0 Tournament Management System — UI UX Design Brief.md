# NGBCL 1.0 Tournament Management System — UI/UX Design Brief

Nov 17, 2026 · @Someone

## 1. Overview

The product is the official platform for the **Next Gen Badminton Champions League 1.0 (NGBCL 1.0)**, presented by Mighty Sharks Battalion in Jaffna. It has five surfaces on one design system: a public site, an admin portal, a full-screen auctioneer console, a big-screen auction display and a tablet-first scorer console. Together they cover 11 interfaces. The look extends the brand that already exists — the navy-and-gold NGBCL logo, the sponsorship proposal, the launch poster and the arena background video — so the app, print and social all read as one league.

### Tournament facts from the existing assets

| Item | Value | Source |
| --- | --- | --- |
| Name | Next Gen Badminton Champions League 1.0 (NGBCL 1.0), inaugural edition | Logo, poster, proposal |
| Organiser | Mighty Sharks Battalion, Jaffna, Sri Lanka (also runs the NGCL cricket tournament) | Proposal p.1–2 |
| Format | Team-owner franchise league; owners build squads through the auction | Proposal p.2, p.4 |
| Eligibility | Jaffna players only | Poster, proposal p.4 |
| Prize money | Champions Rs. 300,000 · Runners-up Rs. 200,000 · Third Rs. 100,000 (pool Rs. 600,000) | Poster, proposal p.4 |
| Team entry fee | Rs. 150,000 per team | Poster, proposal p.4 |
| Currency | LKR, shown as "Rs. 300,000" on public pages and "LKR" in admin tables | Poster, proposal |
| Tagline | Rise. Rally. Rule the Court. | Poster, proposal |
| Sponsor tiers | Title LKR 300K (one only), Platinum 200K, Gold 100K, Silver 50K, Bronze 30K, Official Media Partner | Proposal p.6–7 |
| Social channel | Mighty Sharks Battalion Facebook page ([link](https://www.facebook.com/share/1Ea6AVLhXd/?mibextid=wwXIfr)) | You |
| Still to confirm | Dates, venue, number of teams, players, matches | Proposal p.4 (TBC) |

### What the assets add beyond the SRS

The SRS doesn't cover these, but the brand and proposal promise them publicly. The brief designs them as small additions.

- **Sponsors:** a title-sponsor lockup ("NGBCL 1.0 presented by \[Brand\]"), a tiered sponsor wall, sponsor logos on the auction display ticker, and named awards such as "Player of the Match presented by \[Brand\]". Admin needs a simple Sponsors list under Content (name, tier, logo, link, active).
- **Prize pool and third place:** the public home and standings show the Rs. 600,000 pool. A third-place prize means the knockout bracket needs a third-place playoff tie.
- **Jaffna eligibility:** player registration adds an eligibility declaration ("I am a Jaffna player") and a home area field, and the admin review drawer shows it.
- **Entry fee:** team registration shows the Rs. 150,000 fee as information only, because online payment is out of scope (§16). Admin gets a "Fee received" toggle on each team.
- **Facebook:** a Follow link in the header and footer, plus share buttons on results, SOLD moments and announcements.

### What the SRS asks the UI to carry

- **Three levels of competition.** A team fixture holds several individual matches (singles or doubles), and each match holds games. Every results screen must show all three levels without confusion (SRS §4.9, §17).
- **Trust and auditability.** Auction sales, budget changes, approvals and score corrections are audited (AUC-034, MAT-009, REP-005). The UI must make status, actor and time visible, and confirm irreversible actions.
- **Live state.** The auction and scores update in near real time (AUC-033, §8.3). The server decides what is valid, so the UI must show *pending*, *accepted* and *rejected* states clearly.
- **Provisional vs confirmed.** Only confirmed results count, and public standings must say which is which (MAT-008, STA-008).
- **Privacy by default.** Public pages never show contact details, addresses or internal notes (PUB-008, §9.1).
- **Configurability.** Categories, match types, scoring rules, budgets and squad size are all admin-defined. Layouts must not assume a fixed count of teams, categories or matches.

### Users and their primary surface

| Role | Primary surface | Device | Core job |
| --- | --- | --- | --- |
| Public visitor | Public website, auction display | Mobile first, also desktop | Follow teams, players, auction, live scores, standings |
| Applicant (player / team) | Registration forms | Mobile first | Apply without an account, upload photo or logo, get a reference |
| Super / Tournament Administrator | Admin portal | Desktop | Configure the tournament, teams, auction, fixtures, users, reports |
| Registration Manager | Admin portal: review queue | Desktop | Approve, reject, resolve duplicates, verify images |
| Auctioneer | Auctioneer console | Laptop or large display | Run lots, record bids, close SOLD / UNSOLD / WITHDRAWN |
| Team Manager (optional) | Team portal | Desktop and mobile | View own roster, budget, fixtures |
| Match Scorer / Official | Scorer console | Tablet, phone | Enter scores point by point or game by game, submit results |

### Design principles

1. **The court is the grid.** Layout rules, dividers and section frames borrow the proportions and line weights of a badminton court (13.4 × 6.1 m, about 2.2 : 1).
2. **Scores and money are heroes.** Numbers use tabular figures and a condensed display face, sized for glanceability from 5 m (auction) or 1 m (scorer tablet).
3. **State before decoration.** Every record shows its status chip in the same place. Live, provisional and confirmed are never ambiguous.
4. **Dense where it's work, cinematic where it's a show.** Admin screens are calm and information-dense. The auction display and public match pages are broadcast-style.
5. **Irreversible means deliberate.** SOLD, confirm result, approve and corrections use a two-step confirm with a summary of consequences.
6. **No generic dashboard tropes.** No rows of four identical KPI cards with sparklines, purple gradients or glass card soup.

### SRS open items that change the UI (§14)

- Number of teams (4–10): grids, the standings table and the auction team rail must flex across this range.
- Currency and auction values: the money format is a design token (e.g. `LKR 1,250,000` or `1.25M pts`).
- Format (round robin, knockout or groups): the fixtures screen needs a table view and a bracket view.
- Result approval: the scorer flow designs both a one-step and a two-step (scorer → official) path.
- Team Manager login: a portal is designed but hidden behind a feature flag.
- Public contact fields: player and team cards ship with a public-fields-only variant.

## 2. Brand identity and visual direction

The app adopts the **navy-and-gold NGBCL logo** as the master identity and adds one energy colour, **volt lime**. Volt comes straight from the poster's lime banners and from the light bars and jersey trim in the background video. The working concept is **"Night Court"**: a dark, floodlit arena where navy is the night, gold is the trophy, and volt is the light that marks what is live.

### How the four existing assets fit together

| Asset | What it establishes | How the app uses it |
| --- | --- | --- |
| NGBCL logo (navy + gold) | Master identity: heavy italic wordmark, gold orbit swoosh, navy shuttlecock | Header, favicon (the shuttle alone), auction display, loading states. Navy and gold become the core palette |
| Sponsorship proposal | Premium navy-gold print system, Art Deco gold corner frames, trophy podium, sponsor tiers | Gold corner frames become the app's card-corner device; tier names and colours drive the sponsor wall |
| Launch poster (neon) | Hype energy: lime price banners, magenta and cyan streaks, italic slanted panels | Lime becomes the live and CTA colour. Magenta and cyan stay in social posts and the countdown hero only, never in app UI |
| Background video | Dark arena, jump smash, lime light bars, crowd in soft focus | Home hero background loop; its lime bars confirm volt as the live colour |

The poster carries an older neon version of the logo. Use the navy-and-gold logo everywhere in the product, and keep the neon version for social posts if you like it there.

### Positioning

- **Personality:** competitive, proud of Jaffna, premium, trustworthy. A local league staged like a televised tour stop.
- **Tone of voice:** short, confident, sports-desk copy, built around the tagline **Rise. Rally. Rule the Court.** Write "Sold to Nallur Smashers for Rs. 120,000", not "Transaction completed successfully".
- **Edition framing:** "1.0" is part of the name. Use it as a seal (`NGBCL 1.0`) so later seasons slot in as 2.0 and 3.0 (TRN-006).
- **Presenter line:** "Mighty Sharks Battalion presents" sits above the wordmark on hero, display and print, as in the proposal. The Sharks logo sits in the footer and on the auction display.

### Signature visual devices

| Device | What it is | Where it is used |
| --- | --- | --- |
| Gold orbit | The swoosh from the logo, a thin gold elliptical arc | Hero backgrounds, around the current-bid number, around the champion's crest |
| Deco corners | The proposal's gold stepped corner brackets, at 1–2 px | Card corners on sponsor tiles, prize cards, the SOLD card and the trophy section |
| Light bars | Short volt-lime bars of light, as in the video | Live indicators, section dividers on Night screens, the bid-increase flash |
| Court lines | 1 px white rules at court proportions | Standings qualification line, empty states, section frames |
| Italic slant | A 10–12° slant on panels and display type, from the logo and poster | Price banners, lot status bar, tie-score badges. Never on body text or tables |
| Scoreboard numerals | Condensed tabular digits | Scores, bids, budgets, prize money, countdowns |
| Team colour bands | One team colour as a 4 px edge band | Team cards, roster headers, fixture cards, standings rows |

### Photography and motion

- **Background video:** loop it muted behind the home hero with a navy gradient overlay (navy at 70% at the bottom to 20% at the top) so text stays readable. Details are in section 7.1.
- **Player photos:** a fixed 4 : 5 portrait frame on a navy backdrop, cut out where possible, with a navy-and-gold duotone fallback for low-quality uploads.
- **Action imagery:** dark arena shots with lime or gold light, matching the video. No generic stock photos.
- **Team logos:** always on a neutral crest tile so mismatched uploads look consistent.

### What we avoid

- The poster's full neon treatment (magenta and cyan glows) inside the app. It is right for a social post, but it is tiring on screens people use for hours.
- Purple-to-blue gradients, glassmorphism stacks and glows on everything.
- Rows of four identical KPI cards with sparklines as the default dashboard.
- Rounded, bubbly UI. Corners stay at 2–10 px for a precise, athletic feel.
- Emoji or clip-art trophies in the product UI. The real trophy imagery is reserved for the prize section and the champion moment.

## 3. Colour palette

The palette is built on the logo's navy and gold, sampled from the logo file. The video and poster add volt lime. Two themes share these tokens. **Night** (navy) is used for the public site, auction display and scorer console. **Day** (warm paper) is used for admin work screens. Volt always means "live or current" and gold always means "money, prizes and winning". Contrast ratios are measured against the stated background.

### Core brand

| Token | Name | HEX | Use | Contrast |
| --- | --- | --- | --- | --- |
| `brand.night` | Arena Night | #05080F | Auction display base, video overlay, deepest backgrounds | — |
| `brand.navy` | NGBCL Navy | #0B1B3A | Logo navy. Night theme background, Day-theme primary buttons, headings on Day | White 17.0 : 1 |
| `brand.navy-raised` | Midnight | #12264F | Raised cards and header bands on Night | White 14.8 : 1 |
| `brand.gold` | Champion Gold | #C99A2E | Logo gold. Prize money, SOLD, sponsor tiers, deco corners, the 1.0 seal | 6.6 : 1 on Navy; Navy text on Gold 6.6 : 1 |
| `brand.gold-light` | Trophy Gold | #E2B84A | Gold text at small sizes on dark | 9.1 : 1 on Navy |
| `brand.gold-ink` | Gold Ink | #8A6510 | Gold text on white or paper (Day) | about 5 : 1 on white |
| `brand.volt` | Volt Lime | #D4F23A | Live, current lot, leading bid, primary CTA on Night | 13.4 : 1 on Navy; Navy text on Volt |
| `brand.line` | Line White | #F4F6FA | Text and court lines on Night | 16.8 : 1 on Navy |
| `brand.smash` | Smash Red | #E5484D | Live dot, error icons on Night (text only at 18 px or larger) | 4.4 : 1 on Navy |

### Marketing-only accents (from the poster)

| Token | HEX | Allowed use |
| --- | --- | --- |
| `promo.magenta` | #E5348C | Social graphics, the registration countdown hero, the SOLD burst particles |
| `promo.cyan` | #22D3EE | Social graphics, the registration countdown hero |

These two never appear in admin, scorer, tables or status chips.

### Neutrals (Day theme and shared)

| Token | HEX | Use |
| --- | --- | --- |
| `neutral.0` | #FFFFFF | Cards, inputs on Day |
| `neutral.25` | #FAF8F3 | Day page background (warm paper that suits the gold) |
| `neutral.50` | #F2F3F6 | Table zebra, hover |
| `neutral.100` | #E4E6EC | Borders, dividers |
| `neutral.300` | #BCC3D0 | Disabled borders, placeholder icons |
| `neutral.500` | #7D879A | Tertiary text |
| `neutral.600` | #5A6478 | Secondary text on Day (5.6 : 1) |
| `neutral.900` | #0B1B3A | Primary text on Day = NGBCL Navy (16.1 : 1) |
| `night.muted` | #A9B4CC | Secondary text on Night (8.2 : 1) |

### Status (one meaning each, used for chips, borders and icons)

| Status | Text / icon HEX (Day) | Tint background HEX | Applies to |
| --- | --- | --- | --- |
| Pending | #9A5C00 | #FFF3DC | Registrations awaiting review, fee not received |
| Approved / Confirmed | #1F7A4D | #E2F4EA | Approved registrations, confirmed results, verified files |
| Rejected / Error | #C62F35 | #FDE7E8 | Rejected applications, invalid bids, failed uploads |
| Live | #0B1B3A on #D4F23A, plus a #E5484D dot | #D4F23A | Live auction lot, live match, current game |
| Provisional | #2563C9 | #E3ECFB | Submitted but not yet confirmed results (STA-008) |
| Sold | #0B1B3A on #C99A2E | #F6ECD3 | Auction lot sold |
| Unsold / Withdrawn | #5A6478 | #F2F3F6 | Lot closed without sale, withdrawn player |
| Draft / Archived | #7D879A | #F2F3F6 | Tournament, fixture or announcement drafts |

Status never relies on colour alone. Every chip carries a word, and live states add a pulsing dot.

### Sponsor tier colours

| Tier | Accent HEX | Treatment |
| --- | --- | --- |
| Title | #C99A2E | Full gold frame with deco corners, largest logo |
| Platinum | #D9DEE7 | Silver-white frame |
| Gold | #E2B84A | Gold rule |
| Silver | #A9B4CC | Grey rule |
| Bronze | #B8733A | Bronze rule |

### Team palette (assigned per team, max 10)

Each team gets one colour, used only as a 4 px edge band, a crest-tile ring and a chart series colour. It is never used as a large fill, so team logos stay the hero. The set avoids colours that clash with navy, gold or volt.

| Slot | HEX | Slot | HEX |
| --- | --- | --- | --- |
| T1 Ember | #F05A28 | T6 Rose | #E0457B |
| T2 Lagoon | #1BA6B5 | T7 Forest | #2E9E5B |
| T3 Royal | #2F6FED | T8 Sky | #38BDF8 |
| T4 Plum | #A23B72 | T9 Copper | #B8673A |
| T5 Violet | #8B5CF6 | T10 Slate | #64748B |

### Ratios of use

- **Night screens:** about 80% Navy and Arena Night, 15% Line White text and rules, under 5% Volt and Gold.
- **Day screens:** about 85% paper and white, 10% neutrals, 5% Navy and status colours. Volt appears only as the live indicator, and gold only for money and sold states.

## 4. Typography

The system uses three families, all free on Google Fonts. **Archivo** (with its width axis) is the athletic display face. **Inter** handles UI and body text. **JetBrains Mono** sets references and audit data. A single italic serif, **Instrument Serif**, appears only in the public site's editorial moments.

| Role | Family and settings | Why |
| --- | --- | --- |
| Display / headlines | Archivo, width 125 (Expanded), weight 800, uppercase, tracking −1% | Wide, heavy, broadcast feel; distinct from default SaaS faces |
| Scoreboard numerals | Archivo, width 75 (Condensed), weight 700, `tabular-nums`, `lining-nums` | Fits big numbers in tight tiles; digits don't jitter as scores change |
| UI and body | Inter, weights 400 / 500 / 600, `tnum` on tables | Highly legible at 12–16 px in dense admin tables |
| Data / references | JetBrains Mono 500 | Registration refs (`PLY-2026-00142`), bid numbers, audit timestamps |
| Editorial accent | Instrument Serif Italic | One or two words per hero ("Experience the *rally*"), pull quotes. Never in admin |

**Matching the existing logo:** the NGBCL wordmark is a heavy, wide italic sans, and the poster's banners are slanted. Set public display headlines in **Archivo Expanded 800 Italic** so headings sit naturally next to the logo. Admin page titles stay upright Inter. The proposal's condensed headings map to Archivo Condensed, so print and screen match.

### Type scale

The scale is based on 16 px with a 1.25 ratio. Large-display sizes for the auction screen are in viewport units so they hold up on any projector.

| Token | Size / line height | Family | Example use |
| --- | --- | --- | --- |
| `display.xxl` | clamp(64px, 9vw, 160px) / 0.9 | Archivo Expanded 800 | Public hero, auction SOLD headline |
| `display.xl` | 56 / 1.0 | Archivo Expanded 800 | Page heroes, team name on team page |
| `display.l` | 40 / 1.05 | Archivo Expanded 700 | Section titles on public site |
| `score.xxl` | 18vh / 0.85 | Archivo Condensed 700 | Current bid on auction display |
| `score.xl` | 96 / 0.9 | Archivo Condensed 700 | Scorer console live points |
| `score.l` | 48 / 1.0 | Archivo Condensed 700 | Fixture tie score (3–2), budgets |
| `heading.1` | 28 / 1.2 | Inter 600 | Admin page titles |
| `heading.2` | 22 / 1.25 | Inter 600 | Card and panel titles |
| `heading.3` | 18 / 1.3 | Inter 600 | Subsections, drawer titles |
| `body.l` | 18 / 1.55 | Inter 400 | Public site long text |
| `body.m` | 15 / 1.5 | Inter 400 | Default UI text |
| `body.s` | 13 / 1.45 | Inter 400 / 500 | Table cells, helper text |
| `label` | 11 / 1.2, +8% tracking, uppercase | Inter 600 | Column headers, chips, eyebrow labels ("ROUND 2 · COURT 1") |
| `mono.s` | 13 / 1.4 | JetBrains Mono 500 | References, timestamps |

### Rules

- Money is always tabular and right-aligned in tables, with the currency as a small `label` prefix: LKR **1,250,000**. Use compact formatting (1.25M) only on the big auction display.
- Scores show the leader in Line White or Ink and the trailing side at 60% opacity. Never use colour alone.
- Uppercase is only for Archivo display and `label`. Body text and buttons stay sentence case.
- Player names: first name in `body`, surname in the heavier weight on cards and the auction display ("Tharshan **SIVAKUMAR**").
- Support for Sinhala and Tamil names in the future: pair with Noto Sans Sinhala and Noto Sans Tamil as fallbacks in the font stack.

## 5. Component design system

The system has about 35 components across five layers: tokens, primitives, data display, sport-specific components and live components. The sport-specific layer is what keeps it from looking like a generic dashboard: player cards, crest tiles, the bid ladder, the scoreboard and the tie card.

### Foundations (tokens)

| Token group | Values |
| --- | --- |
| Spacing | 4 px base: 4, 8, 12, 16, 24, 32, 48, 64, 96 |
| Radius | `r.xs` 2 (chips, court-line tags), `r.s` 6 (inputs, buttons), `r.m` 10 (cards, drawers), `r.full` (avatars, live dot only) |
| Borders | 1 px `neutral.100` on Day; 1 px Line White at 12% on Night; 2 px Volt for focus and current item |
| Elevation | Day: borders, no shadows. Night: one shadow for overlays only (0 16px 40px rgba(0,0,0,.45)) |
| Grid | Admin: 12 columns, 24 px gutter, 240 px sidebar, content max 1440. Public: 12 columns, 1200 max. Auction display: 16 : 9 fixed canvas |
| Breakpoints | 360, 768, 1024, 1280, 1600, 1920 (display) |
| Motion | 120 ms (hover), 200 ms (panels), 320 ms ease-out-expo (bid change), 900 ms (SOLD reveal). Respect `prefers-reduced-motion` |
| Focus | 2 px Volt ring, 2 px offset, on every interactive element |

### Primitives

- **Buttons:** Primary (NGBCL Navy on Day, Volt with Navy text on Night), Secondary (outline), Ghost, Destructive (Smash Red outline, filled only in a confirm dialog). Sizes: 32, 40, 48. Scorer and auctioneer use an XL size of 64–72 px for touch.
- **Inputs:** text, select, date, phone with +94 prefix, textarea, toggle, radio cards (for skill category). Labels sit above the field, helper text sits below, and an error shows an icon plus a message.
- **Status chip:** a `label` in uppercase with a 2 px radius, built from the status table in section 3. Live chips add a pulsing dot.
- **Court-line divider:** a 1 px rule with service-box corner ticks, used to separate major sections.
- **Tabs, segmented control, breadcrumb, pagination, tooltip, toast, modal, drawer (right, 480 / 720 px).**

### Data display

- **Data table:** sticky header, 44 px rows (compact 36), sortable columns, column chooser, row selection with a bulk action bar, inline status chip column, and a right-aligned numeric column. It always has filters plus a search box, and pagination of 25, 50 or 100 (NFR-012).
- **Review drawer:** the left side shows the applicant's submitted data. The right side shows photo verification, duplicate matches and actions (Approve, Reject with a required reason, Add note). It also has an audit trail at the foot.
- **Audit timeline:** a vertical list of actor avatar, action, field diff (old → new), and a timestamp in mono.
- **Stat block:** a large number, a label and an optional delta. Use it in context-specific groups, not as a fixed row of four.
- **Empty state:** a court-line illustration with one sentence and one action.

### Sport-specific components

| Component | Anatomy | Variants |
| --- | --- | --- |
| Player card | 4 : 5 photo, name (surname heavy), skill category tag, team colour band, base or sold price | Public, Admin (with status and reference), Auction lot (large), Roster row (compact) |
| Team crest tile | Logo on a neutral square, team-colour ring, name, short code (3 letters) | S 32, M 56, L 96, XL 200 for the auction display |
| Budget meter | Horizontal bar: spent (Gold), remaining (Line or Ink), max-bid marker; text "Remaining 2.4M of 10M" | Inline, team card, auction rail |
| Squad slots | A row of N slots (N = max squad size), filled ones show a player avatar | Auction rail, team page |
| Bid ladder | Vertical list of recent bids: team crest, amount, time; the newest at the top in Volt | Console, display |
| Lot status bar | Lot number, player, base price, state: Ready → Open → Going once → Going twice → SOLD / UNSOLD / WITHDRAWN | Console, display |
| Tie card (team fixture) | Two crests, tie score (3–2), round and court; expands into rubbers: category, players, games (21–18, 19–21, 21–15) | Compact, expanded, live |
| Match scoreboard | Players per side, service indicator (shuttle icon), current game points, game history tiles | Public live, scorer, embedded |
| Game score tiles | One tile per game: winner in bold, loser at 60% | Inline |
| Standings table | Pos, crest, team, P, W, L, matches W–L, games W–L, points diff, Pts, form (last 5) | Full, compact (mobile), group |
| Bracket | Knockout tree with tie cards as nodes | Round robin uses a cross-table matrix instead |
| Image uploader | Drag and drop, then crop to 4 : 5 (photo) or 1 : 1 (logo), check type and size, upload progress, verified badge | Public, admin |

### Live components

- **Connection indicator:** a small pill showing Live, Reconnecting or Offline. It sits in the header of every real-time screen.
- **Optimistic vs confirmed states:** a bid or score you enter shows "Sending…" (dimmed), then turns Accepted (solid, with a Volt flash) or Rejected (Smash Red shake plus the server's reason, e.g. "Exceeds remaining budget"). The server is the source of truth (§8.3).
- **Undo window:** for score entry, offer a 5-second undo toast on each point. Corrections after confirmation open an audited correction flow.

## 6. Navigation structure

Every role lands on exactly one home surface after sign-in. The public site and the two full-screen displays need no account. Only the admin portal uses a full sidebar, and the consoles keep navigation minimal so nothing interrupts live work.

&#91;embedded content: navigation map · 7 entry points, 2 access zones\]

The admin portal publishes content to the public site. The scorer console feeds live scores to it, and the auctioneer console drives the auction display in real time.

### Navigation patterns per surface

| Surface | Pattern | Desktop | Mobile / tablet |
| --- | --- | --- | --- |
| Public website | Top bar: wordmark, 8 links, Register CTA, Live pill when anything is live | Sticky top bar, live strip under it | Hamburger opens a full-screen sheet; a sticky bottom live strip |
| Registration | Stepper only; logo links home with a "leave form?" guard | Stepper on the left, summary on the right | Stepper as "Step 2 of 4" with a progress arc |
| Admin portal | Left sidebar of 8 groups, top bar with tournament switcher and search, breadcrumbs | 240 px sidebar, collapsible to 64 px icons | Admin is desktop-first; under 1024 px the sidebar becomes a drawer, tables scroll sideways |
| Auctioneer console | No sidebar; a header with session controls and an "Exit console" link | Full-screen, three columns | Works on a 1280 px laptop; not designed for phone |
| Auction display | None | Fixed 1920 × 1080 canvas, scales to fit | Same canvas, letterboxed |
| Scorer console | Bottom tab bar: My matches, Live, History | Tablet landscape | Phone portrait, the live screen stacks the two sides |
| Team portal | Top tabs: Team, Roster, Budget, Fixtures | Top tabs | Scrollable tabs |

### Admin sidebar (role-filtered)

1. **Overview** — lifecycle dashboard
2. **Registrations** — Players, Teams, Duplicates
3. **Directory** — Players, Teams, Skill categories
4. **Auction** — Setup, Open console, Results and budgets
5. **Competition** — Format, Fixtures, Schedule board, Match categories, Scoring rules
6. **Results** — Confirmations, Standings, Statistics
7. **Content** — Announcements, Sponsors, Branding
8. **System** — Users and roles, Audit log, Reports, Settings

A Registration Manager sees only Overview, Registrations and Directory. An Auctioneer signing in goes straight to the console. A Scorer goes straight to My matches.

### Route conventions

- Public: `/`, `/teams/:slug`, `/players/:slug`, `/auction`, `/fixtures`, `/live/:matchId`, `/standings`, `/news/:slug`, `/register/player`, `/register/team`
- Displays and consoles: `/display/auction`, `/console/auction`, `/score`, `/score/:matchId`
- Admin: `/admin/...` mirroring the sidebar groups. The edition is a scope in the top bar, not in the URL, until edition 2.

## 7. Page-by-page UI requirements

Each interface lists its users, layout, required elements, states and responsive behaviour, with SRS references. "Must" items from the SRS are mandatory in the prototype; "Should" items are designed but can be stubbed.

### 7.1 Public tournament website

**Users:** public visitors. **Theme:** Night. **Refs:** PUB-001–008, ROS-003, MAT-010, STA-008.

- **Home:** a full-bleed floodlit hero with the tournament wordmark, the `NGBCL 1.0` seal, dates and venue. It has a phase-aware primary CTA that changes over the lifecycle:
  - Registration open: "Register as a player" and "Enter a team", plus a countdown to the closing date.
  - Auction day: "Watch the auction live".
  - Tournament running: "Live now" strip with in-progress ties.
  - Completed: champion banner.
- **Home sections:** a live strip (only when something is live), next fixtures, a standings snapshot (top 4), teams crest wall, latest announcements, and venue and schedule.
- **Teams:** a crest wall grid. Each **team page** has a header band in the team colour, crest XL, roster (player cards with sold price if published), budget summary if published, fixtures and results.
- **Players:** a filterable directory of approved players only (filter by team, category, gender), with a 4 : 5 card grid. The **player page** shows photo, name, category, team, auction price and statistics. It shows no contact details.
- **Auction results:** a sold list sortable by price, a team spend summary, and a link to the live display while the auction runs.
- **Fixtures, live scores and standings:** covered in 7.9–7.11 (public views).
- **News:** an announcement list and detail page, with pinned items.
- **States:** pre-launch ("Registrations open on…"), registration closed, no data yet (empty states with court-line art), and a provisional-results banner.
- **Responsive:** mobile first. Bottom-sheet nav on mobile, sticky live strip, crest wall in 3 columns on mobile and 6 on desktop.

**NGBCL-specific home and site content**

- **Video hero:** the background video plays muted and looped behind the hero. A navy gradient overlay keeps text readable. On top: "Mighty Sharks Battalion presents", the NGBCL logo, "Next Gen Badminton Champions League 1.0", the tagline, dates and venue, and the phase CTAs. Add a "Jaffna players only" badge next to Register.
- **Prize pool section:** a podium layout taken from proposal page 4. Champions Rs. 300,000 in the centre in gold, Runners-up Rs. 200,000 in silver, Third Rs. 100,000 in bronze, and "Total prize pool Rs. 600,000" beneath. After the final, the podium fills with the winning teams' crests.
- **For team owners:** a short block with "Own a team", the Rs. 150,000 entry fee, what owners get (auction purse, squad), and the "Enter a team" CTA.
- **Sponsors:** the title-sponsor lockup under the hero ("NGBCL 1.0 presented by \[Brand\]" once sold), then a sponsor wall grouped by tier (Title, Platinum, Gold, Silver, Bronze, Media Partner) using the tier colours in section 3. Before sponsors sign, show "Become a sponsor" with a link to the proposal PDF.
- **Organiser and social:** a footer with the Mighty Sharks Battalion logo, the Facebook page link, and a short line on NGCL cricket. Share buttons on results, SOLD moments and announcements.

**Background video handling**

- The current file is 1280 × 720, 8 seconds, H.264 with audio. Export a 1920 × 1080 version if possible, strip the audio, and encode WebM plus MP4 at under 3 MB each. Add a still poster frame (the jump-smash frame) for the first paint.
- Make the loop seamless with a short crossfade from the last second into the first.
- Pause it when it scrolls off screen or when `prefers-reduced-motion` is set. On mobile data-saver or slow connections, show the still frame instead.
- The arena boards in the video show **YONEX** branding. Unless Yonex becomes a sponsor, blur or regenerate those boards. A real brand logo on the hero suggests a sponsorship that doesn't exist and could conflict with your actual sponsors.

### 7.2 Player registration

**Users:** applicants with no account. **Theme:** Night header, Day form body for readability. **Refs:** PLY-001–013, BR-005, BR-007, §7.2–7.3, §9.1.

- **Layout:** a four-step stepper with a gold orbit-arc progress line, and a sticky summary panel on desktop.
  1. Personal details: full name, date of birth, gender, email, phone (+94 prefix), address.
  2. Photo: uploader with 4 : 5 crop guide, live checks (JPEG, PNG or WebP; max size; minimum 600 × 750 px), and an example of a good and a bad photo.
  3. Playing profile: skill category as radio cards (shown only if active; marked optional unless configured as required), experience text (300 characters), and emergency contact if configured.
  4. Review and consent: a read-only summary with edit links per step, privacy notice, declaration checkbox, and anti-spam check.
- **Confirmation page:** a large registration reference in mono (e.g. `PLY-2026-00142`) with a copy button, status "Pending review", what happens next as a three-step timeline, and "Received" notice if notifications are enabled.
- **Validation:** inline on blur, a summary of errors at the top on submit, and the form keeps entered data if an upload fails.
- **Duplicate handling:** the form never tells the applicant "duplicate found". It submits normally, and the duplicate flag appears only in admin (PLY-010–011).
- **States:** registration not yet open, closed, an upload error, a network error with retry, and a submission success.
- **Responsive:** single column under 768 px, a large tap target for the uploader, and the camera can be used for the photo on mobile.

**NGBCL eligibility:** step 1 adds a home area field (a Jaffna district area list plus "Other"). Step 4 adds a required declaration: "I confirm I am eligible to play as a Jaffna player in NGBCL 1.0." The registration intro states "Jaffna players only" before the applicant starts, so nobody fills the form in vain.

### 7.3 Team registration

**Users:** applicants with no account. **Refs:** TEM-001–005, TEM-011, BR-006.

- **Layout:** the same stepper pattern, in three steps.
  1. Team identity: team name with a live uniqueness check ("Name available" or "Already taken"), short description, and a 1 : 1 logo uploader with a preview on light and dark backgrounds and on a crest tile.
  2. Contact person: name, email, phone. Show a note that these details are private.
  3. Review and consent.
- **Confirmation page:** reference `TEM-2026-0007`, a crest preview, and the next steps.
- **Detail:** show a live preview of how the team card will look on the public site as the applicant types.

**Entry fee notice:** step 1 shows an information panel "Team entry fee: Rs. 150,000. The organisers will contact you about payment after review." No online payment, as it is out of scope (§16). Admin team records get a "Fee received" toggle with the date and who recorded it.

### 7.4 Admin dashboard

**Users:** Super Admin, Tournament Admin, Registration Manager. **Theme:** Day. **Refs:** §3.1, REP-001–006, TRN-001–006.

- **Shell:** a 240 px left sidebar (role-filtered), top bar with a tournament switcher (`NGBCL 1.0`, ready for future editions), global search (players, teams, references), notifications and user menu.
- **Home is a phase tracker, not a KPI wall.** A horizontal "tournament lifecycle" rail across the top shows Setup → Registration → Review → Auction → Fixtures → Matches → Completed, with the current phase in Volt and a next-action button.
- **Below the rail, show context panels that change by phase:**
  - Registration: a pending queue count with an oldest-waiting age, approvals per day, a duplicates-to-resolve count, and images awaiting verification.
  - Auction: teams ready versus budgets set, eligible players in the auction pool, and an "Open auctioneer console" link.
  - Matches: today's schedule by court, matches awaiting confirmation, and recent corrections.
- **Always visible:** a recent activity feed (audit log excerpt), announcements quick-post, and a configuration checklist (categories, auction settings, scoring rules, format) with completion ticks.
- **Tournament settings pages:** general (name, edition, dates, venue, status), branding (logo, banner, team palette), registration windows and required fields, skill categories (drag to reorder, activate or deactivate), auction settings (with a lock icon once bidding starts and an audited change dialog, AUC-007–008), match categories, scoring rules, standings points and tie-break order (drag to reorder), users and roles, audit log, and reports.

### 7.5 Player and team management

**Users:** Registration Manager, Tournament Admin. **Refs:** PLY-020–028, TEM-006–013, CAT-001–006, BR-019–020.

- **Registrations inbox:** tabs for Players and Teams, with status filters (Pending, Approved, Rejected, All) and saved views ("Needs photo verification", "Possible duplicates"). Table columns: reference, photo thumbnail, name, category, submitted, age in queue, flags (duplicate, image unverified), status.
- **Review drawer (720 px):** submitted data on the left; the photo with zoom, a "Verify image" toggle and a crop adjust on the right. A duplicate panel compares the matching records side by side and offers "Link to existing player", "Create new profile" or "Not a duplicate". Approve is disabled until the photo is verified; Reject requires a reason. An internal note field and the audit history sit at the foot. Keyboard shortcuts: J / K to move, A to approve, R to reject.
- **Bulk actions:** approve the selected rows that are already verified, export, and assign to a reviewer.
- **Player directory (admin):** all players with tournament status (Approved, In auction pool, Sold to X, Withdrawn, Inactive). The profile page shows tabs: Profile, Registration history, Auction, Matches, Audit. Withdraw and deactivate are offered instead of delete, with an explanation that history is kept.
- **Team directory (admin):** a crest grid or table view with a source badge (Public or Admin-created), and a "Create team" drawer for direct creation (TEM-009–010). The team page shows budget, roster, fixtures and audit.
- **Skill categories:** a sortable list with name, description, active toggle, and player count. Deactivated categories show as "Hidden from new registrations".

### 7.6 Live auctioneer console

**Users:** Auctioneer (and an admin observer). **Theme:** Night, desktop or laptop at 1440–1920, touch-friendly. **Refs:** AUC-020–035, §4.7 transaction rules, NFR-007.

- **Layout:** three columns, with nothing scrolling during a lot.
  - **Left (320 px), the lot queue:** upcoming eligible players in auction order, with search, a filter by category, drag to reorder, and a "Re-auction pool" tab for unsold players (if AUC-005 is enabled). Ineligible players never appear (PLY-028).
  - **Centre, the current lot:** a large player card (photo, name, category, base price), the lot status bar, the current highest bid in `score.xl` with the leading team crest, and the bid ladder underneath.
  - **Right (360 px), the team rail:** one row per team with crest, remaining budget meter, squad slots filled versus max, and the maximum allowable bid. Teams that cannot legally bid (budget or squad full) are greyed out with the reason.
- **Bid entry:** tap a team on the rail, then choose the amount. The amount picker shows quick buttons (+1 increment, +2, +5) and a custom field. The next valid minimum is pre-filled. Keyboard: number keys 1–9 select teams, Enter places the bid.
- **Server feedback:** each bid shows Sending, then Accepted or Rejected with the reason ("Below minimum increment of 50,000", "Exceeds remaining budget", "Squad full").
- **Closing a lot:** three large buttons: SOLD (Champion Gold, enabled only with a valid leading bid), UNSOLD, WITHDRAWN. SOLD opens a confirm sheet: "Sell Tharshan SIVAKUMAR to Nallur Smashers for Rs. 120,000? Remaining budget becomes Rs. 380,000. Squad 5 / 8." with Confirm sale and Cancel.
- **Session controls:** Start, Pause (sends a "Paused" screen to the public display), Resume, an undo-last-bid action (audited) and "Next player". Also a connection indicator and a "Display preview" thumbnail of what the public screen is showing.
- **Recovery:** after a reload or disconnect, the console restores the current lot and bid history from the server, with a banner "Restored session — last action 14:32:05" (§9.2).

### 7.7 Public live auction display

**Users:** audience in the hall (projector or LED wall), people watching online. **Theme:** Night, 16 : 9 fixed canvas at 1920 × 1080, legible from 10 m. **Refs:** AUC-022–023, AUC-032–033.

- **Layout:**
  - Left 45%: the player portrait large, with a net-mesh backdrop and the feather-fan frame, plus the name in `display.xl`, category and base price.
  - Right 55%: "CURRENT BID" label, the amount in `score.xxl` in Volt, and the leading team crest XL with the team name. Below that, the last 5 bids as a mini ladder.
  - Bottom ticker: a strip of all teams with crest, remaining budget and squad count, updating live.
  - Top bar: tournament wordmark, `LIVE AUCTION`, lot number of total (Lot 23 / 64), and a live dot.
- **States, each a designed scene:**
  - Waiting or intro: wordmark, "Auction starts at 7:00 PM", and team crest parade.
  - Lot open: as above.
  - New bid: the amount ticks up with a 320 ms counter roll and a Volt flash on the leading crest.
  - SOLD: full-screen reveal with the team colour wash, "SOLD" in `display.xxl`, crest, price in Champion Gold, and a feather-fan burst lasting about 4 s.
  - UNSOLD: a quiet grey card, "Unsold", lasting 2 s.
  - Paused: "Short break" with standings of spend.
  - Ended: a summary of top 5 buys, each team's spend and squad.
- **Rules:** no pointer UI, no scrollbars, auto-reconnect silently. Show only public profile fields.

### 7.8 Team management and player roster

**Users:** Tournament Admin, Team Manager (optional, own team only), public (published view). **Refs:** ROS-001–006, REP-002–003, AUC-031.

- **Team header:** a team-colour band, crest L, name, short code, source badge, contact (admin only), and status.
- **Budget panel:** initial, spent and remaining in `score.l`, a budget meter, average price per player, and the most expensive buy.
- **Roster:** a squad-slot strip (e.g. 6 / 8) above a table or a card grid toggle. Columns: photo, name, category, gender, purchase price, acquired (lot #, time), status. Highlight gaps against match-category needs, e.g. "No women players yet — needed for WS and XD" (once match categories are known).
- **Allocation correction (admin):** a guarded flow. Select the player, choose an action (move to team or release to pool), enter a reason, see a budget impact preview for both teams, then confirm. It writes an audit entry (ROS-005).
- **All-teams overview:** a comparison table of teams with spend, remaining, squad count, and category mix, which can be exported (REP-002–003).
- **Team Manager portal:** the same pages in read-only mode, restricted to one team, plus their fixtures.

### 7.9 Fixture management

**Users:** Tournament Admin. **Refs:** FIX-001–010, BR-014–017.

- **Format setup wizard:** choose format (Round robin, Knockout, Groups then knockout), then groups and seeding by drag and drop, then the fixture template (the ordered list of rubbers per tie, e.g. MS, WS, MD, WD, XD), then schedule settings (start date, courts, slot length, rest time).
- **Fixture views:**
  - A list or table grouped by round, with status chips.
  - A schedule board: courts as columns, time as rows, and tie cards as draggable blocks. Conflicts show in red, e.g. a player in two overlapping matches (FIX-009), with the reason on hover.
  - A bracket view for knockout rounds, including a third-place playoff because there is a third-place prize, and a cross-table for round robin.
- **Tie editor (drawer):** teams (must be two distinct teams), round, group, date and time, court, and status. Below that, the list of rubbers: category, then a player picker for each side that only lists that team's roster players (FIX-008) and enforces 1 or 2 players by category (BR-015). Add a scorer assignment per match.
- **Publish control:** draft fixtures are visible only to admins. A "Publish schedule" action shows a diff of what changes publicly (FIX-010).

### 7.10 Match scoring interface

**Users:** Match Scorer / Official. **Theme:** Night, tablet landscape first, phone portrait supported. **Refs:** MAT-001–012, §8.1.

- **My matches:** a list of assigned matches grouped by Now, Next and Done, each with court, time, category, and players.
- **Pre-match check:** confirm players per side (photos to verify identity), choose first server, choose sides, then Start.
- **Live scoring screen (landscape):**
  - The screen is split into two big halves, one per side. Each shows player names, the team crest and the current points in `score.xl`. Tap anywhere on a half to award a rally point.
  - A shuttle icon shows the server. Game history tiles sit at the top (e.g. G1 21–18), along with the configured rules ("21 pts, win by 2, cap 30, best of 3").
  - Bottom bar: Undo last point, Interval, Retirement or walkover (if enabled, MAT-012), and Save and exit (saves as in progress, MAT-006).
- **Validation:** the app follows the configured rules. At 20–20 it shows "Deuce — win by 2" and at 29–29 "Next point wins". Game end is detected automatically with a confirm "Game 1 to Side A, 21–18?"
- **Alternative entry:** a "Game-by-game" mode lets the scorer type final scores per game for matches scored on paper, with validation errors explained inline.
- **Submit:** a summary of games and the winner, then "Submit result". If two-step approval is enabled, the status becomes Provisional and the result goes to the official or admin confirmation queue.
- **Admin side:** a "Results to confirm" queue with the score sheet, the score history (point log), and Confirm or Return to scorer. Corrections after confirmation require a reason and recalculate standings (MAT-009, STA-007).
- **Offline tolerance:** if the connection drops, keep a local queue with a visible "3 points not synced" badge, and sync with the server on reconnect.

### 7.11 Tournament standings and statistics

**Users:** public, admin. **Refs:** STA-001–008, REP-004.

- **Standings:** one table per group (or overall). Columns: position, crest and team, played, won, lost, match W–L, game W–L, points difference, points, and form (last 5 as W or L tiles). The qualification line is a dashed court line with "Qualifies" (top N).
- **Provisional marking:** a row affected by an unconfirmed result shows a Provisional chip, and a page banner reads "Includes 2 provisional results" (STA-008). A tie-break explainer is in a popover.
- **Fixture results:** tie cards by round, expandable to rubbers and games.
- **Player statistics:** a leaderboard with tabs (matches won, win %, games won, points scored) and filters by category and team. The player stat page shows a match log.
- **Team statistics:** a head-to-head matrix, and match-category win rates per team.
- **Charts:** use the dataviz rules: bars for comparisons across teams, a line only for points over rounds. Use team colours as series colours. No pie charts.
- **Admin variant:** an export to CSV or PDF, a "Recalculate" action with the audit entry, and a toggle to see confirmed-only versus including provisional.

## 8. Recommended visual references

Borrow one specific idea from each reference, not its whole look. These are names to search for in a browser or on Dribbble or Behance. They were not opened or linked while writing this brief.

| Reference | Borrow this | For screen |
| --- | --- | --- |
| BWF World Tour broadcast graphics | Scorebug layout, server indicator, game-history tiles | Scorer console, public live score |
| IPL / WPL player auction broadcast | Lot card, bid amount staging, team purse ticker, SOLD reveal | Auction display, auctioneer console |
| Wimbledon and Roland-Garros live scores sites | Calm, premium live-score pages with one strong brand colour | Public live scores, fixtures |
| Premier League website (club pages, tables) | Club colour bands, crest-led tables, form guide tiles | Team pages, standings |
| Formula 1 live timing | Dense real-time data in dark UI, tabular numerals, status colours | Auctioneer team rail, scorer list |
| Nike and Adidas campaign sites | Wide heavy display type, full-bleed action photography | Public hero, registration header |
| Linear | Keyboard-first tables, quiet borders, fast review flows | Registrations inbox, review drawer |
| Stripe Dashboard | Money formatting, audit trails, settings layouts | Budgets, audit log, settings |
| FIFA World Cup Panini sticker / trading cards | Portrait card framing with name bar and team band | Player card |
| Apple Sports app | Compact live score cards, clear live / final states | Mobile live strip, tie cards |

### Moodboard keywords

Floodlit indoor court, green synthetic mat, white court lines, shuttle in motion blur, net mesh macro, feather detail, scoreboard LED, broadcast lower third, team crest wall.

### Anti-references (do not resemble)

- Generic admin templates (AdminLTE, Material dashboard kits) with KPI tiles and donut charts.
- AI-generated "futuristic sports" art with neon glows and fake holograms.
- Tournament-bracket SaaS tools with default blue Bootstrap styling.

## 9. Prompts for the initial Claude Design prototype

Run the master prompt first to set up the design system, then one prompt per screen group. Paste the master prompt at the top of every new session so each screen uses the same tokens. Replace `[TOURNAMENT NAME]`, the currency and the team names once the §14 decisions are made.

### 9.1 Master prompt (design system)

```text
You are designing the official web app for "NGBCL 1.0 - Next Gen Badminton Champions League", the inaugural edition of a team-owner franchise badminton league in Jaffna, Sri Lanka, presented by Mighty Sharks Battalion. Tagline: "Rise. Rally. Rule the Court." Players are bought by team owners in a live auction. Prize money: Champions Rs. 300,000, Runners-up Rs. 200,000, Third Rs. 100,000. Team entry fee Rs. 150,000. Jaffna players only. Build the design system first as one reference page, before any screens. I will attach the NGBCL logo (navy wordmark, gold orbit swoosh, navy shuttlecock) - match it.

CONCEPT "Night Court": a dark floodlit arena. Navy is the night, gold is the trophy, volt-lime light marks what is live. Visual devices: a thin gold elliptical "orbit" arc from the logo; Art Deco gold stepped corner brackets (from the sponsorship proposal) on premium cards; short volt-lime light bars as live indicators and dividers; thin white court lines; a 10-12 degree italic slant on price banners and display type only; condensed scoreboard numerals.

THEMES: Night (public site, auction display, scorer console) on navy; Day (admin portal) on warm paper.

COLOURS: Arena Night #05080F, NGBCL Navy #0B1B3A, Midnight #12264F (raised), Champion Gold #C99A2E (prize money, SOLD, sponsors), Trophy Gold #E2B84A (small gold text on dark), Gold Ink #8A6510 (gold text on light), Volt Lime #D4F23A (live, current, primary CTA on dark; navy text on it), Line White #F4F6FA, Smash Red #E5484D (live dot, errors). Day: page #FAF8F3, surface #FFFFFF, border #E4E6EC, secondary text #5A6478, ink #0B1B3A. Night secondary text #A9B4CC. Status chips: Pending #9A5C00 on #FFF3DC, Approved #1F7A4D on #E2F4EA, Rejected #C62F35 on #FDE7E8, Provisional #2563C9 on #E3ECFB, Sold #0B1B3A on #C99A2E, Unsold #5A6478 on #F2F3F6. Team colours (4px edge bands and rings only): #F05A28, #1BA6B5, #2F6FED, #A23B72, #8B5CF6, #E0457B, #2E9E5B, #38BDF8. Magenta #E5348C and cyan #22D3EE are marketing-only; do not use them in the app UI.

TYPE (Google Fonts): Archivo width 125 weight 800 ITALIC uppercase for public display headlines (matches the logo wordmark); Archivo width 75 weight 700 with tabular numerals for scores, bids, budgets and prize money; Inter 400/500/600 for UI; JetBrains Mono for reference numbers and timestamps. Scale: 160/56/40 display, 96/48 score, 28/22/18 headings, 15 body, 13 small, 11 uppercase label. Money format: "Rs. 300,000" on public screens, "LKR" prefix in admin tables.

SHAPE: radius 2px chips, 6px inputs and buttons, 10px cards. Borders, not shadows, on Day. 4px spacing grid. 2px Volt focus ring.

SHOW on the reference page: logo lockups ("Mighty Sharks Battalion presents" above the NGBCL logo), colour swatches with hex, type scale, buttons (primary, secondary, ghost, destructive; 32/40/48 and XL 64px touch), inputs, status chips, live connection pill, player card (4:5 photo on navy, surname heavy, category tag, team band, price), team crest tile (S/M/L/XL), budget meter, squad slots, bid ladder, lot status bar, tie card (team fixture with expandable rubbers and game scores), match scoreboard with server indicator, standings row with form tiles, data table row, review drawer, image uploader with crop guide, prize podium card (gold/silver/bronze), sponsor tile per tier (Title, Platinum, Gold, Silver, Bronze, Media Partner), empty state with court-line illustration.

AVOID: the full neon poster look (magenta/cyan glows) inside the app, purple or blue gradients, glassmorphism, rows of four identical KPI cards with sparklines, donut charts, emoji, bubbly rounded shapes, stock office photos. It must not look like a generic AI or SaaS dashboard. Use realistic Jaffna Tamil sample names (e.g. Tharshan Sivakumar, Nilaxan Rajendran, Kavya Thevarajah), team names like "Nallur Smashers", "Point Pedro Strikers", "Chunnakam Chargers", and Rs. amounts in a realistic league range.
```

### 9.2 Public website, player and team registration

```text
Using the Night Court design system, design the public website at 1440px and 390px mobile.
1) Home: hero with my attached background video (dark arena, jump smash; muted loop under a navy gradient overlay), "Mighty Sharks Battalion presents", the NGBCL logo, "Next Gen Badminton Champions League 1.0", "Rise. Rally. Rule the Court.", dates and venue, a "Jaffna players only" badge, and phase-aware CTAs ("Register as a player", "Enter a team", countdown to registration close). Sections: live strip (hidden when nothing is live), next fixtures as tie cards, standings top 4, team crest wall, latest news, venue, a prize podium (Champions Rs. 300,000 centre in gold, Runners-up Rs. 200,000 silver, Third Rs. 100,000 bronze, "Total prize pool Rs. 600,000"), a "Own a team" block with the Rs. 150,000 entry fee, a sponsor wall by tier with a title-sponsor lockup "NGBCL 1.0 presented by [Brand]", and a footer with the Mighty Sharks Battalion logo and Facebook link.
2) Teams grid and a team page: team-colour header band, XL crest, roster as player cards with sold price, budget summary, fixtures.
3) Players directory (filters: team, category, gender) and a player page with stats. Never show contact details.
4) Player registration: a 4-step stepper with a gold orbit-arc progress line - Personal details, Photo (4:5 crop guide, good/bad examples, format and size checks), Playing profile (skill category as optional radio cards, experience), Review and consent. Then a confirmation page with the reference "PLY-2026-00142" in mono, a copy button, a "Pending review" chip and a 3-step what-happens-next timeline. Show inline validation errors and an upload error state.
5) Team registration: 3 steps - team name with a live "available/taken" check and a 1:1 logo uploader previewed on a crest tile, contact person (marked private), review. Show a live preview of the public team card. Confirmation with "TEM-2026-0007".
```

### 9.3 Admin dashboard, registrations and directories

```text
Using the Night Court design system in the Day theme, design the admin portal at 1440px.
Shell: 240px left sidebar grouped as Overview; Registrations (Players, Teams, Duplicates); Directory (Players, Teams, Skill categories); Auction (Setup, Console, Results); Competition (Format, Fixtures, Schedule board, Scoring); Results (Confirmations, Standings, Statistics); Content (Announcements, Sponsors, Branding); System (Users and roles, Audit log, Reports, Settings). Top bar: tournament switcher "NGBCL 1.0", global search, notifications, user menu.
1) Dashboard: NOT a KPI wall. A horizontal tournament lifecycle rail (Setup > Registration > Review > Auction > Fixtures > Matches > Completed) with the current phase in Volt and a next-action button. Below it, phase-specific panels: pending queue with oldest waiting age, duplicates to resolve, images to verify; a setup checklist; a recent activity feed from the audit log.
2) Registrations inbox: Players/Teams tabs, status filters, saved views, a dense table (reference, photo, name, category, submitted, age in queue, flags, status chip), and a bulk action bar.
3) Review drawer (720px): submitted data left; photo with zoom and a "Verify image" toggle right; a duplicate comparison panel (Link to existing / Create new / Not a duplicate); Approve disabled until the photo is verified; Reject requires a reason; internal note; audit trail. Show keyboard hints J/K/A/R.
4) Player profile (admin) with tabs Profile, Registration history, Auction, Matches, Audit; Withdraw instead of Delete. Team directory with source badge (Public / Admin-created) and a Create team drawer. Skill categories: sortable list with active toggles.
```

### 9.4 Auctioneer console and public auction display

```text
Using the Night Court design system (Night theme), design two linked real-time screens.
A) Auctioneer console at 1600x900, no scrolling during a lot. Left 320px: lot queue of eligible players with search, category filter, drag to reorder, and a re-auction tab. Centre: large current player card, lot status bar (Ready > Open > Going once > Going twice), current highest bid in huge condensed numerals with the leading team crest, bid ladder. Right 360px: team rail - one row per team with crest, budget meter, squad slots (e.g. 5/8) and max allowable bid; teams that can't bid are greyed with a reason. Bid entry: pick a team, quick buttons +1/+2/+5 increments, custom amount, next valid minimum pre-filled. Show bid states: Sending, Accepted (Volt flash), Rejected (red, with reason "Exceeds remaining budget"). Big close buttons: SOLD (Champion Gold), UNSOLD, WITHDRAWN. Show the SOLD confirm sheet: "Sell Tharshan SIVAKUMAR to Nallur Smashers for Rs. 120,000? Remaining budget becomes Rs. 380,000. Squad 6/8." Header: Start/Pause/Resume, connection pill, a public display preview thumbnail.
B) Public auction display, fixed 1920x1080, readable from 10m, no pointer UI. Left 45%: player portrait with net-mesh backdrop and feather-fan frame, name, category, base price. Right 55%: "CURRENT BID" in Volt at about 18vh, leading team crest XL, last 5 bids. Bottom ticker: every team's crest, remaining budget and squad count. Top bar: NGBCL logo, "Mighty Sharks Battalion presents", LIVE AUCTION, Lot 23/64, live dot. Also design these scenes: Intro/waiting, SOLD reveal (team colour wash, giant "SOLD", price in Champion Gold, feather burst), Unsold, Paused, and an end-of-auction summary.
```

### 9.5 Team roster, fixtures, scoring and standings

```text
Using the Night Court design system, design these screens.
1) Team roster (Day, 1440px): team-colour header band, crest, source badge; budget panel (initial / spent / remaining in condensed numerals, budget meter, average price); squad-slot strip; roster table with photo, name, category, gender, price, lot # and time; a gap warning "No women players yet - needed for WS and XD". An allocation correction drawer with reason and a budget impact preview for both teams. An all-teams comparison table.
2) Fixture management (Day): a format wizard (Round robin / Knockout / Groups then knockout; groups and seeding by drag; fixture template of ordered rubbers MS, WS, MD, WD, XD; courts and slot length). A schedule board with courts as columns and time as rows, tie cards draggable, a conflict shown in red with its reason. A tie editor drawer where player pickers list only that team's roster and enforce 1 or 2 players per side. A "Publish schedule" diff dialog. A bracket view.
3) Scorer console (Night, tablet 1180x820 landscape plus 390px phone): My matches (Now / Next / Done); pre-match check (confirm players, first server); live scoring split into two huge tap halves with points at 96px, shuttle server icon, game tiles (G1 21-18), rules line "21 pts, win by 2, cap 30, best of 3", bottom bar Undo, Interval, Retire/Walkover, Save and exit. Show the deuce state at 20-20, an auto game-end confirm, a "3 points not synced" offline badge, a game-by-game manual entry mode, and the submit summary with a Provisional status.
4) Standings and statistics (Night public, 1440px and 390px): a group table (Pos, crest, team, P, W, L, match W-L, game W-L, points diff, Pts, form tiles) with a dashed court-line qualification cut and a "Includes 2 provisional results" banner; fixture results as expandable tie cards; a player leaderboard with tabs; a team head-to-head matrix. Bar charts in team colours, no pie charts.
```

### 9.6 Review checklist for each generated screen

- Does it use only the palette tokens, with Volt only for live or current items?
- Are scores and money in tabular condensed numerals, and right-aligned in tables?
- Does every record show a worded status chip?
- Are irreversible actions (sell, approve, confirm, correct) behind a confirm with consequences?
- Are private fields absent from every public screen?
- Does it still work with 4 teams and with 10 teams?
- Could this screen belong to any generic SaaS product? If yes, add the court-line framing, crest tiles or scoreboard treatment.
