# Phase 2.11 — Implementation Plan: Universal Appwrite Master Router Real Backend & End-to-End Integration

## 1. Overview & Core Objectives

This phase transforms the **Universal Appwrite Master Router** into a production-grade control plane and multi-tenant router with **100% real Appwrite upstream communications**, eliminating all mock fallbacks, hardcoded metrics, and placeholder UI handlers across both the Cloudflare Worker backend, the Web SPA Dashboard, and the Native Android App.

### Verified User Selections
- **Proxy Architecture**: `/v1/proxy/*` transparent routing with `X-Appwrite-Project` and `X-Master-API-Key` headers.
- **Database Browser Mode**: Full Read & Write support with document creation, editing, and deletion across both Web and Android.

---

## 2. Architecture & File Structure

```
backend/
├── src/
│   ├── appwrite/
│   │   └── client.ts         [NEW] Centralized Appwrite REST Client (SSRF safe, AES decrypted key)
│   ├── crypto/
│   │   ├── auth.ts           PBKDF2 authentication & session tokens
│   │   ├── keys.ts           SHA-256 API key hashing & generation
│   │   └── vault.ts          AES-256-GCM secret vault
│   ├── router/
│   │   └── engine.ts         Routing decision engine (DEDICATED, SHARED_POOL, HYBRID)
│   ├── ui/
│   │   └── dashboard.ts      Glassmorphism Web SPA (Modal handlers, Database Browser tab)
│   └── index.ts              Cloudflare Worker main entry point & API routes
├── migrations/
│   ├── 0001_initial_schema.sql
│   └── 0002_admin_auth.sql
└── tests/
    ├── admin_auth.test.mjs
    ├── api.test.mjs
    ├── appwrite_client.test.mjs [NEW]
    ├── router.test.mjs
    └── vault.test.mjs

app/src/main/java/com/example/
├── data/
│   ├── api/MasterRouterApi.kt [Updated Retrofit endpoints for DB & Proxy]
│   ├── model/MasterRouterModels.kt [Updated DTOs for Database Browser CRUD]
│   ├── repository/MasterRouterRepository.kt [Mock fallbacks removed; real errors propagated]
│   └── security/SecureStorageManager.kt [Keystore-backed token storage]
└── ui/
    ├── screens/
    │   ├── AccountsScreen.kt [Real Add/Test Account actions]
    │   ├── ApiKeysScreen.kt [Real Key generation & revocation]
    │   ├── DatabaseBrowserScreen.kt [Real Account -> DB -> Collection -> Document CRUD UI]
    │   ├── RoutingRulesScreen.kt [Real Rule creation & deletion]
    │   └── SettingsScreen.kt [Worker URL & Admin logout]
    └── viewmodel/MasterRouterViewModel.kt
```

---

## 3. Detailed Implementation Steps

### Step 1: Centralized Appwrite Upstream HTTP Client (`backend/src/appwrite/client.ts`)
- **SSRF Protection**: Sanitize all incoming Appwrite endpoint URLs. Strictly enforce `https://` protocol and reject `localhost`, `127.0.0.1`, private RFC 1918 IPv4 ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local, or cloud metadata endpoints (`169.254.169.254`).
- **AES Decryption**: Decrypt stored API keys using `CredentialVault(env.ENCRYPTION_MASTER_KEY)`.
- **Request Execution**: Execute `fetch()` with `X-Appwrite-Project`, `X-Appwrite-Key`, and `Content-Type: application/json`.
- **AbortController & Latency Measurement**: Timeout requests after 10 seconds and return exact network latency in milliseconds.
- **Appwrite Methods**:
  - `verifyConnection(endpoint, projectId, apiKey)`: Tests connection via `/health/time` or `/projects`.
  - `listDatabases(endpoint, projectId, apiKey)`: Returns list of databases.
  - `listCollections(endpoint, projectId, apiKey, dbId)`: Returns collections for database.
  - `listDocuments(endpoint, projectId, apiKey, dbId, colId, limit, offset)`: Returns documents.
  - `createDocument(endpoint, projectId, apiKey, dbId, colId, docId, data)`: Creates new document.
  - `updateDocument(endpoint, projectId, apiKey, dbId, colId, docId, data)`: Updates existing document.
  - `deleteDocument(endpoint, projectId, apiKey, dbId, colId, docId)`: Deletes document.

### Step 2: Real Appwrite Account Connection & Health Testing (`backend/src/index.ts`)
- **`POST /v1/accounts/manual`**: Validates credentials via `AppwriteClient.verifyConnection()` before inserting into D1 `connected_accounts`. Rejects invalid API keys with HTTP 400.
- **`POST /v1/accounts/:id/test`**: Executes real HTTPS health probe against Appwrite, measures true ping latency, and updates `account_health` and `connected_accounts.last_verified` in D1.

### Step 3: Database Browser Endpoints & Proxy Router
- **Database Browser REST API**:
  - `GET /v1/databases?account_id={id}`
  - `GET /v1/databases/:dbId/collections?account_id={id}`
  - `GET /v1/databases/:dbId/collections/:colId/documents?account_id={id}`
  - `POST /v1/databases/:dbId/collections/:colId/documents?account_id={id}`
  - `PUT /v1/databases/:dbId/collections/:colId/documents/:docId?account_id={id}`
  - `DELETE /v1/databases/:dbId/collections/:colId/documents/:docId?account_id={id}`
- **Transparent Router Proxy (`/v1/proxy/*`)**:
  - Validates `X-Master-API-Key` or session cookie.
  - Resolves tenant and applies `RoutingEngine` strategy (`DEDICATED`, `SHARED_POOL`, `HYBRID`) to pick target account.
  - Transparently forwards HTTP request and body to `{targetAccount.endpoint}/{path}` with decrypted `X-Appwrite-Key`.
  - Records request metrics into `usage_metrics` (read vs write classification).

### Step 4: Web SPA Dashboard Modal Handlers & Database Browser (`backend/src/ui/dashboard.ts`)
- **Connect Account Modal**: Implement `openAddAccountModal()`, `closeAddAccountModal()`, and `handleConnectAccount(e)` submitting to `POST /v1/accounts/manual`.
- **New Routing Rule Modal**: Implement `openAddRuleModal()`, `closeAddRuleModal()`, and `handleCreateRule(e)` with dynamic project selection from `GET /v1/projects`.
- **Web Database Browser Tab**: Add interactive account/database/collection/document inspector with document editing and creation modals.

### Step 5: Eliminate Android Repository Mock Fallbacks & Upgrade App UI
- **`MasterRouterRepository.kt`**: Remove `getMockAccounts()`, `getMockHealth()`, `getMockUsage()`, `getMockProjects()`, `getMockRoutingRules()`, `getMockKeys()`. Propagate real network and HTTP error messages.
- **`DatabaseBrowserScreen.kt`**: Implement 4-tier navigation:
  1. Select Connected Account.
  2. Select Database.
  3. Select Collection.
  4. View/Create/Edit/Delete Documents with JSON property editor.

### Step 6: Real Usage Metering & Health Probing
- Middleware logs every routed proxy request into D1 `usage_metrics` (`read_requests`, `write_requests`, `failed_requests`).
- `GET /v1/usage` sums real traffic from D1.

---

## 4. Verification & Testing Strategy

1. **Backend Test Suite (`npm --prefix backend test`)**:
   - `appwrite_client.test.mjs`: Tests SSRF protection, URL normalization, and mock server HTTP calls.
   - `api.test.mjs`: Tests real database CRUD, account verification, and `/v1/proxy/*` routing.
   - Total expected passing tests: $\ge 30$.
2. **Android App Compilation & Tests**:
   - `compile_applet`: Clean Android compilation.
   - `gradle :app:testDebugUnitTest`: Execution of all Robolectric and unit tests.

---

## 5. Security Invariants
- **No Plaintext API Keys**: Appwrite API keys are encrypted at rest with AES-256-GCM. Decrypted keys never leave the Worker environment.
- **SSRF Defense**: Strict URL validation prevents SSRF attacks to internal infrastructure.
- **Tenant Isolation**: Routing rules enforce strict tenant boundaries.
