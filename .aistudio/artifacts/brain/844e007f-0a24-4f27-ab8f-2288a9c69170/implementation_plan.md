# Phase 2 — Cloudflare Production Deployment Preparation Plan

## Summary
Preparation, configuration audit, and dual deployment manual (Wrangler CLI and Cloudflare Dashboard) for deploying the Universal Appwrite Master Router backend to the global `*.workers.dev` domain with Cloudflare D1 and KV.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Deployment Domain**: Cloudflare Global Workers dev domain (`https://universal-master-router.<subdomain>.workers.dev`).
> - **Setup Guide Format**: Comprehensive dual-path instructions containing both **Wrangler CLI terminal commands** and **Cloudflare Web Dashboard click-through steps**.
> - **Secrets Policy**: Master encryption key (`ENCRYPTION_MASTER_KEY`) and server-side credentials must be stored strictly in Cloudflare Worker Secrets—never in `wrangler.toml`, Git, client code, or chat logs.

---

## 1. Inspection & Configuration Audit

- **Worker Name**: `universal-master-router`
- **Main Entry Point**: `src/index.ts`
- **Compatibility Date**: `2024-09-23`
- **Database Binding**: `DB` bound to `universal_master_router_db` (Currently contains placeholder `d1-placeholder-id`)
- **KV Binding**: `ROUTER_KV` bound to rate limiter namespace (Currently contains placeholder `kv-placeholder-id`)
- **Worker Code Binding Verification**: `env.DB` and `env.ROUTER_KV` in `backend/src/index.ts` match `wrangler.toml` exactly.
- **Migration Schema Safety**: `backend/migrations/0001_initial_schema.sql` uses `CREATE TABLE IF NOT EXISTS` and `INSERT OR IGNORE`, ensuring safe, idempotent migrations without data loss.

---

## 2. D1 Database & KV Namespace Setup

```
┌────────────────────────────────────────────────────────┐
│                   Cloudflare Cloud Edge                │
├──────────────────────────┬─────────────────────────────┤
│ 1. D1 Relational DB      │ universal_master_router_db  │
│    (Binding: DB)         │ (9 tables, indexes, rules)  │
├──────────────────────────┼─────────────────────────────┤
│ 2. KV Namespace          │ ROUTER_KV                   │
│    (Binding: ROUTER_KV)  │ (Sliding window rate limit) │
├──────────────────────────┼─────────────────────────────┤
│ 3. Worker Secrets        │ ENCRYPTION_MASTER_KEY       │
│    (AES-256-GCM Master)  │ (32+ char cryptographic key)│
└──────────────────────────┴─────────────────────────────┘
```

---

## 3. Production Readiness Checks

- **CORS Handling**: `Access-Control-Allow-Origin: *`, `OPTIONS` preflight, allowed headers include `X-Master-API-Key`, `Content-Type`, `Authorization`.
- **Authentication**: Strict validation of `X-Master-API-Key` using SHA-256 hash comparison against `api_keys` table with scope enforcement.
- **Rate Limiting**: Sliding window KV tracking per minute window (`rate_{keyId}_{minuteWindow}`).
- **Health Endpoint**: `GET /v1/health` reports status, latency, and quota percentages without exposing server secrets, passwords, or salts.
- **Zero Exposure**: Decrypted keys exist solely within temporary Worker memory for active API verification and are never persisted or returned in API responses.

---

## 4. Execution Roadmap

1. Update `backend/package.json` to provide dedicated build, test, and typecheck scripts.
2. Verify TypeScript type checking on backend code.
3. Prepare the definitive deployment guide detailing exact Cloudflare Dashboard steps, Wrangler terminal commands, placeholder replacements, secret generation, and end-to-end verification tests.
