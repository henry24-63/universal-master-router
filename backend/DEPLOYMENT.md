# Universal Appwrite Master Router — Production Deployment Manual

This manual provides the step-by-step production deployment procedure for the **Cloudflare Worker Backend** (`universal-master-router`) and synchronization with the **Native Android App**.

---

## 1. Exact Files That Need Editing Before Deploying

The only file that requires user-specific Cloudflare resource IDs is:

`backend/wrangler.toml`

Specifically, replace the two placeholder strings:
1. `database_id = "d1-placeholder-id"` &rarr; replace with your actual D1 UUID from Step 2.
2. `id = "kv-placeholder-id"` &rarr; replace with your actual KV Namespace ID from Step 3.

---

## 2. Cloudflare Dashboard & CLI Setup (Step-by-Step)

### STEP A: Login to Cloudflare
In your local terminal:
```bash
npx wrangler login
```
*(Or login via web browser at [dash.cloudflare.com](https://dash.cloudflare.com)).*

---

### STEP B: Create the D1 Database
#### Option 1: Via Wrangler CLI
```bash
npx wrangler d1 create universal_master_router_db
```
The output will display:
```toml
[[d1_databases]]
binding = "DB"
database_name = "universal_master_router_db"
database_id = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```
Copy that `database_id` value and paste it into `backend/wrangler.toml`.

#### Option 2: Via Cloudflare Web Dashboard
1. Open **Cloudflare Dashboard** &rarr; select **Storage & Databases** &rarr; **D1 SQL Database**.
2. Click **Create database** &rarr; Name it `universal_master_router_db`.
3. In the database overview, copy the **Database ID** (UUID).
4. Paste it into `backend/wrangler.toml` under `database_id`.

---

### STEP C: Apply D1 Database Migrations
Execute the migration script to create all 9 tables, indexes, and constraints:
```bash
npx wrangler d1 execute universal_master_router_db --file=backend/migrations/0001_initial_schema.sql --remote
```
*(Verification: Run `npx wrangler d1 execute universal_master_router_db --command="SELECT name FROM sqlite_master WHERE type='table';" --remote` to confirm all 9 tables exist).*

---

### STEP D: Create the KV Namespace for Rate Limiting
#### Option 1: Via Wrangler CLI
```bash
npx wrangler kv:namespace create "ROUTER_KV"
```
The output will display:
```toml
[[kv_namespaces]]
binding = "ROUTER_KV"
id = "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy"
```
Copy that `id` value and paste it into `backend/wrangler.toml`.

#### Option 2: Via Cloudflare Web Dashboard
1. Open **Cloudflare Dashboard** &rarr; **Storage & Databases** &rarr; **KV**.
2. Click **Create a namespace** &rarr; Name it `ROUTER_KV`.
3. Copy the **Namespace ID** and paste it into `backend/wrangler.toml` under `id`.

---

### STEP E: Configure Worker Secrets
Do **NOT** store encryption secrets in `.env`, `wrangler.toml`, or Git. Set them directly in Cloudflare Worker Secrets:

Generate a secure 32+ character encryption master key:
```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

Configure the secret in Cloudflare:
```bash
cd backend
npx wrangler secret put ENCRYPTION_MASTER_KEY
# When prompted, paste the generated 64-character hex string
```

*(Optional: If your custom Appwrite server requires an administrative project-level master key for background worker routines):*
```bash
npx wrangler secret put APPWRITE_MASTER_API_KEY
# Enter your server-side Appwrite API Key when prompted
```

---

### STEP F: Deploy the Worker
From the `backend/` directory:
```bash
npx wrangler deploy
```
Wrangler will output your live URL:
```
Published universal-master-router (X.XX sec)
  https://universal-master-router.<YOUR-SUBDOMAIN>.workers.dev
```

---

## 3. End-to-End Verification & Health Testing

### Test 1: Verify Health Endpoint
```bash
curl -s https://universal-master-router.<YOUR-SUBDOMAIN>.workers.dev/v1/health | jq .
```
Expected response:
```json
{
  "success": true,
  "data": [],
  "error": null,
  "request_id": "...",
  "operation_status": "completed"
}
```

### Test 2: Generate Initial Master Router API Key
Generate your first admin key using the bootstrap secret:
```bash
curl -X POST https://universal-master-router.<YOUR-SUBDOMAIN>.workers.dev/v1/keys \
  -H "X-Master-API-Key: bootstrap-admin-key" \
  -H "Content-Type: application/json" \
  -d '{"name": "Admin Mobile Client", "scopes": ["*"], "rate_limit_rpm": 600}' | jq .
```
Save the returned `api_key` (`umr_live_...`). This secret is only displayed once and stored securely as a SHA-256 hash in Cloudflare D1.

### Test 3: Connect the Native Android App
1. Open the **Master Router** app on your Android device or emulator.
2. Tap the **Settings** navigation tab.
3. In **Master Router API Endpoint**, enter:
   `https://universal-master-router.<YOUR-SUBDOMAIN>.workers.dev`
4. In **Master Router API Key**, enter the generated `umr_live_...` key.
5. Tap **Save & Synchronize**.
6. The app will immediately authenticate and pull live data from Cloudflare D1.

---

## 4. Phase 2.11 — API Endpoints & Transparent Router Proxy

### A. Database Browser REST Endpoints
These endpoints provide cross-account administrative access to upstream Appwrite databases:
- `GET /v1/databases?account_id={accountId}` — List databases for connected account.
- `GET /v1/databases/{dbId}/collections?account_id={accountId}` — List collections inside a database.
- `GET /v1/databases/{dbId}/collections/{colId}/documents?account_id={accountId}` — List documents in a collection.
- `POST /v1/databases/{dbId}/collections/{colId}/documents?account_id={accountId}` — Create a document with body `{"document_id": "...", "data": {...}}`.
- `PUT /v1/databases/{dbId}/collections/{colId}/documents/{docId}?account_id={accountId}` — Update a document with body `{"data": {...}}`.
- `DELETE /v1/databases/{dbId}/collections/{colId}/documents/{docId}?account_id={accountId}` — Delete a document.

### B. Transparent Proxy Router (`/v1/proxy/*`)
Client SDKs and mobile applications route Appwrite SDK calls through `/v1/proxy/*`:
- Target tenant resolved via `X-Appwrite-Project` header or `X-Tenant-ID` header.
- Routing strategy evaluated via `RoutingEngine` (DIRECT, SHARED_POOL, LEAST_LATENCY, FAILOVER).
- Requests dispatched securely to upstream Appwrite node using encrypted Master Key with SSRF protection.
- Metering recorded asynchronously into D1 `usage_metrics`.

