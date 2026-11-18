# NGBCL 1.0 — Tournament Platform

Official platform for the **Next Gen Badminton Champions League 1.0**, presented by Mighty Sharks Battalion, Jaffna.
*Rise. Rally. Rule the Court.*

| Folder | What it is |
| --- | --- |
| `frontend/` | Next.js 16 + React 19 + TypeScript — public site, admin portal, auctioneer console, auction display, scorer console |
| `backend/` | Spring Boot 3.5 (Java 21) modular monolith — REST + STOMP WebSockets, PostgreSQL, Flyway, Spring Security |
| `design/` | Claude Design export (source of truth for visuals), source media in `design/source-assets/` |
| `documentation/` | SRS (source of truth for functionality), UI/UX brief, architecture proposal |

## Prerequisites

- Node.js 22 LTS (npm comes with it)
- JDK 21 (Temurin recommended) — Maven is downloaded by `./mvnw`
- Docker Desktop (PostgreSQL for local dev and for backend integration tests)

## Run it locally

```bash
# 1) Database
docker compose up -d db

# 2) API  → http://localhost:8080  (Swagger UI: /swagger-ui.html)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local        # Windows: mvnw.cmd ...
#   first run creates the Super Admin: admin@ngbcl.local / ChangeMe-Local-123 (local profile only)

# 3) Web app → http://localhost:3000
cd frontend
npm install
npm run dev
```

Useful pages: `/` (home), `/register/player` and `/register/team` (public forms — open the windows in Settings → Registration first), `/login` (staff), `/admin`, `/ds` (living design system), `/display/auction` (big screen).

## Checks

```bash
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
cd backend  && ./mvnw verify      # needs Docker for Testcontainers
# End-to-end (API on :8080 with the local profile, web built and started on :3000)
cd frontend && npx playwright install chromium && npm run e2e
```

## Ground rules

- **Server is the source of truth.** Every permission and business rule is enforced in the API; the UI only hides what the API would refuse anyway.
- **Commands over REST, broadcasts over WebSocket.** Clients never send bids or score points over the socket.
- **Public endpoints live under `/api/public/**`** and return dedicated `Public*` DTOs — never contact details or internal notes (an ArchUnit test enforces this).
- **Only staff sign in.** Players and teams register without accounts; Team Manager logins are not used in edition 1.
- **Visual changes start in `frontend/src/design-system/styles/ngbcl-ds.css`** (verbatim port of the export). Don't restyle with a generic UI kit.
- Secrets come from environment variables only (`NGBCL_JWT_SECRET`, DB credentials, R2 keys). See `backend/src/main/resources/application.yml` and `frontend/.env.example`.
