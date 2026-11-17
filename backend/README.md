# NGBCL backend

Spring Boot 3.5.15 · Java 21 · PostgreSQL 16 · Flyway · Spring Security (JWT access token + rotating httpOnly refresh cookie, Argon2id) · STOMP WebSockets · springdoc OpenAPI.

```bash
docker compose -f ../docker-compose.yml up -d db
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
| `tournament` | Current edition, public + admin views | M0 → M1 |
| `audit` | Same-transaction audit log + query API | M0 |
| `realtime` | STOMP config, auth interceptor, after-commit publisher | M0 |
| `files`, `registration`, `player`, `team`, `auction`, `competition`, `scoring`, `standings`, `content`, `reporting`, `notification` | see `documentation/architecture-proposal.md` | M1–M7 |

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
