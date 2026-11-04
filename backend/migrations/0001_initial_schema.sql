-- Universal Appwrite Master Router
-- Cloudflare D1 Database Schema Migration: 0001_initial_schema.sql

-- 1. Master Router API Keys (Stored as SHA-256 hashes, never plaintext)
CREATE TABLE IF NOT EXISTS api_keys (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    key_hash TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    scopes TEXT NOT NULL, -- JSON array of string scopes e.g. ["*"] or ["accounts:read", "database:write"]
    tenant_id TEXT,
    rate_limit_rpm INTEGER DEFAULT 600,
    is_admin INTEGER DEFAULT 0,
    expires_at TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    revoked_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_api_keys_prefix ON api_keys(prefix);

-- 2. Connected Appwrite Accounts
CREATE TABLE IF NOT EXISTS connected_accounts (
    id TEXT PRIMARY KEY,
    label TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    region TEXT NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    encryption_salt TEXT NOT NULL,
    encryption_iv TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, FROZEN, ERROR
    projects_count INTEGER DEFAULT 0,
    last_verified TEXT NOT NULL DEFAULT (datetime('now')),
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_connected_accounts_status ON connected_accounts(status);

-- 3. Projects Nodes
CREATE TABLE IF NOT EXISTS projects (
    id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    name TEXT NOT NULL,
    appwrite_project_id TEXT NOT NULL,
    region TEXT NOT NULL,
    mode TEXT NOT NULL DEFAULT 'DEDICATED', -- DEDICATED, SHARED_POOL
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY(account_id) REFERENCES connected_accounts(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_projects_account_id ON projects(account_id);
CREATE INDEX IF NOT EXISTS idx_projects_mode ON projects(mode);

-- 4. Tenants
CREATE TABLE IF NOT EXISTS tenants (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    default_strategy TEXT NOT NULL DEFAULT 'SHARED_POOL', -- DEDICATED, SHARED_POOL, HYBRID
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 5. Routing Rules
CREATE TABLE IF NOT EXISTS routing_rules (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    strategy TEXT NOT NULL, -- DEDICATED, SHARED_POOL, HYBRID
    target_project_id TEXT NOT NULL,
    priority INTEGER DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY(target_project_id) REFERENCES projects(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_routing_rules_tenant ON routing_rules(tenant_id);

-- 6. Account Health & Quotas
CREATE TABLE IF NOT EXISTS account_health (
    account_id TEXT PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'HEALTHY', -- HEALTHY, WARNING, CRITICAL
    latency_ms INTEGER DEFAULT 50,
    quota_usage_percent INTEGER DEFAULT 0,
    storage_used_gb REAL DEFAULT 0.0,
    last_checked_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY(account_id) REFERENCES connected_accounts(id) ON DELETE CASCADE
);

-- 7. Aggregated Usage Metrics
CREATE TABLE IF NOT EXISTS usage_metrics (
    id TEXT PRIMARY KEY,
    account_id TEXT,
    project_id TEXT,
    read_requests INTEGER DEFAULT 0,
    write_requests INTEGER DEFAULT 0,
    failed_requests INTEGER DEFAULT 0,
    period_date TEXT NOT NULL DEFAULT (date('now'))
);
CREATE INDEX IF NOT EXISTS idx_usage_metrics_date ON usage_metrics(period_date);

-- 8. Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id TEXT PRIMARY KEY,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    target TEXT NOT NULL,
    tenant_id TEXT,
    project_id TEXT,
    status TEXT NOT NULL,
    request_id TEXT NOT NULL,
    details TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);

-- 9. Request and Routing History
CREATE TABLE IF NOT EXISTS request_history (
    id TEXT PRIMARY KEY,
    request_id TEXT NOT NULL,
    tenant_id TEXT,
    operation TEXT NOT NULL, -- READ, WRITE
    selected_project_id TEXT NOT NULL,
    status TEXT NOT NULL, -- COMPLETED, FAILED, BLOCKED
    duration_ms INTEGER DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_request_history_req ON request_history(request_id);

-- Seed Default Tenant and Initial Project Record for Universal SGP
INSERT OR IGNORE INTO tenants (id, name, default_strategy) 
VALUES ('default_tenant', 'Default Tenant', 'SHARED_POOL');
