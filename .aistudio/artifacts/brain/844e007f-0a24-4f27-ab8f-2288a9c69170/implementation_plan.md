# Phase 2.6 — Secure Admin Login & Embedded Dashboard System Plan

## Summary
Implementation plan for the complete administrator authentication system, PBKDF2 password hashing with per-user cryptographic salts, atomic first-admin registration, HttpOnly/Secure/SameSite cookie sessions, CSRF token validation, and an embedded responsive glassmorphism Single Page Application (SPA) dashboard served directly from Cloudflare Worker without separate hosting.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Dashboard Hosting**: Embedded SPA dashboard served directly by the Cloudflare Worker at `/` and `/admin` with modern glassmorphism styling, zero external static hosting dependencies.
> - **Password Hashing**: PBKDF2 with 100,000 rounds of SHA-256 and unique 16-byte random salt per administrator in D1 (`admin_users` table).
> - **Dual Authentication Layer**:
>   - *Dashboard requests*: Secure `HttpOnly; Secure; SameSite=Strict` cookie (`umr_session`) with CSRF header check.
>   - *API & Mobile requests*: `X-Master-API-Key` header with constant-time SHA-256 hash lookup in `api_keys`.
>   - Both mechanisms share the same underlying controllers and data layer.
> - **Zero Exposure Guarantee**: Internal API keys and passwords are never sent to browser storage (`localStorage`/`sessionStorage`), code, or logs.

---

## 1. Architectural Model & Session Flow

```
┌────────────────────────────────────────────────────────┐
│               Admin Web Browser (Desktop/Mobile)       │
└──────────────────────────┬─────────────────────────────┘
                           │ 1. GET / (Serves Glassmorphism Dashboard)
                           │ 2. POST /v1/auth/login (email + password)
┌──────────────────────────▼─────────────────────────────┐
│             Cloudflare Worker (Auth Layer)             │
│ ┌────────────────────────────────────────────────────┐ │
│ │ Session & Cookie Manager (HttpOnly; Secure; Strict)│ │
│ ├────────────────────────────────────────────────────┤ │
│ │ PBKDF2 Password Verifier (100k rounds + Salt)      │ │
│ ├────────────────────────────────────────────────────┤ │
│ │ Dual-Auth Bridge: Resolves Session Cookie or Key   │ │
│ └────────────────────────────────────────────────────┘ │
└────────────┬─────────────────────────────┬─────────────┘
             │ SQL Queries                 │ Session Lookups
┌────────────▼─────────────┐ ┌─────────────▼─────────────┐
│       Cloudflare D1      │ │      Cloudflare KV        │
│    (admin_users,         │ │    (Sliding window rate   │
│     admin_sessions)      │ │     limits & sessions)    │
└──────────────────────────┘ └───────────────────────────┘
```

---

## 2. D1 Schema Migration (`backend/migrations/0002_admin_auth.sql`)

1. `admin_users`:
   - `id TEXT PRIMARY KEY`
   - `email TEXT NOT NULL UNIQUE`
   - `password_hash TEXT NOT NULL`
   - `password_salt TEXT NOT NULL`
   - `name TEXT NOT NULL`
   - `is_owner INTEGER DEFAULT 0`
   - `created_at TEXT NOT NULL DEFAULT (datetime('now'))`
2. `admin_sessions`:
   - `id TEXT PRIMARY KEY` (cryptographically random 32-byte token)
   - `user_id TEXT NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE`
   - `csrf_token TEXT NOT NULL`
   - `expires_at TEXT NOT NULL`
   - `created_at TEXT NOT NULL DEFAULT (datetime('now'))`

---

## 3. Endpoints & UI Views

| Endpoint | Method | Purpose |
|---|---|---|
| `/` | `GET` | Serves the full glassmorphism admin dashboard SPA HTML/CSS/JS |
| `/v1/auth/setup` | `POST` | First-time atomic setup (only succeeds if `admin_users` table is empty) |
| `/v1/auth/setup-status` | `GET` | Returns whether initial owner setup is required (`has_admin: boolean`) |
| `/v1/auth/login` | `POST` | Authenticates email/password, issues `umr_session` cookie + CSRF token |
| `/v1/auth/logout` | `POST` | Invalidates session in D1 and clears cookie |
| `/v1/auth/me` | `GET` | Returns authenticated user profile and CSRF token |

---

## 4. Security Hardening & Rate Limiting

- **Brute Force Protection**: Failed login tracking per IP / email in KV with exponential lockout after 5 consecutive failures.
- **CSRF Token Guard**: State-changing requests (`POST`, `PUT`, `DELETE`, `PATCH`) using cookie authentication must provide matching `X-CSRF-Token` header.
- **Atomic Single-Owner Setup**:
  ```sql
  INSERT INTO admin_users (id, email, password_hash, password_salt, name, is_owner)
  SELECT ?, ?, ?, ?, ?, 1
  WHERE (SELECT count(*) FROM admin_users) = 0;
  ```
  Ensures only one owner can ever be initialized through the setup flow.

---

## 5. Verification Roadmap

1. Implement password hashing & session token utilities (`backend/src/crypto/auth.ts`).
2. Add migration `0002_admin_auth.sql`.
3. Integrate dual authentication (Cookie + `X-Master-API-Key`) into `backend/src/index.ts`.
4. Embed the enterprise glassmorphism Dashboard SPA into the Worker.
5. Create automated test suite in `backend/tests/admin_auth.test.mjs` verifying:
   - Atomic setup
   - Successful login/logout
   - Wrong password rejection
   - Session expiration
   - CSRF validation
   - Existing API key backwards compatibility
6. Verify Android app compilation and unit tests.
