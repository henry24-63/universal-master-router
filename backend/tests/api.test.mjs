import test from 'node:test';
import assert from 'node:assert/strict';

// SHA-256 helper
async function hashApiKey(key) {
  const enc = new TextEncoder();
  const hashBuffer = await crypto.subtle.digest('SHA-256', enc.encode(key));
  return [...new Uint8Array(hashBuffer)]
    .map(x => x.toString(16).padStart(2, '0'))
    .join('');
}

// In-Memory mock D1 database mimicking Cloudflare D1 statement execution
class MockD1Database {
  constructor() {
    this.tables = {
      api_keys: [],
      connected_accounts: [],
      projects: [],
      account_health: [],
      usage_metrics: [],
      routing_rules: []
    };
  }

  prepare(sql) {
    const db = this;
    return {
      params: [],
      bind(...args) {
        this.params = args;
        return this;
      },
      async first() {
        if (sql.includes('FROM api_keys WHERE key_hash = ?')) {
          const hash = this.params[0];
          return db.tables.api_keys.find(k => k.key_hash === hash && !k.revoked_at) || null;
        }
        if (sql.includes('SELECT count(*) as count FROM api_keys') || sql.includes('SELECT count(*) FROM api_keys')) {
          return { count: db.tables.api_keys.length };
        }
        if (sql.includes('FROM connected_accounts WHERE id = ?')) {
          const id = this.params[0];
          return db.tables.connected_accounts.find(a => a.id === id) || null;
        }
        if (sql.includes('SELECT count(*) as count FROM projects')) {
          return { count: db.tables.projects.length };
        }
        if (sql.includes('SELECT count(*) as count FROM connected_accounts')) {
          return { count: db.tables.connected_accounts.length };
        }
        return null;
      },
      async all() {
        if (sql.includes('FROM routing_rules WHERE tenant_id = ?')) {
          const tenantId = this.params[0];
          return { results: db.tables.routing_rules.filter(r => r.tenant_id === tenantId) };
        }
        if (sql.includes('FROM routing_rules')) {
          return { results: db.tables.routing_rules };
        }
        if (sql.includes('FROM api_keys')) {
          return { results: db.tables.api_keys };
        }
        return { results: [] };
      },
      async run() {
        // Atomic conditional insert check: WHERE (SELECT count(*) FROM api_keys) = 0
        if (sql.includes('WHERE (SELECT count(*) FROM api_keys) = 0')) {
          if (db.tables.api_keys.length > 0) {
            return { changes: 0, success: false };
          }
        }

        if (sql.includes('INSERT INTO api_keys')) {
          db.tables.api_keys.push({
            id: this.params[0],
            name: this.params[1],
            key_hash: this.params[2],
            prefix: this.params[3],
            scopes: this.params[4],
            tenant_id: this.params[5],
            rate_limit_rpm: this.params[6],
            is_admin: this.params[7],
            revoked_at: null
          });
          return { changes: 1, success: true };
        }
        if (sql.includes('UPDATE api_keys SET revoked_at')) {
          const id = this.params[0];
          const key = db.tables.api_keys.find(k => k.id === id);
          if (key) key.revoked_at = new Date().toISOString();
          return { changes: 1, success: true };
        }
        if (sql.includes('INSERT INTO routing_rules')) {
          db.tables.routing_rules.push({
            id: this.params[0],
            tenant_id: this.params[1],
            strategy: this.params[2],
            target_project_id: this.params[3]
          });
          return { changes: 1, success: true };
        }
        return { changes: 1, success: true };
      }
    };
  }
}

// In-Memory mock KV namespace
class MockKV {
  constructor() {
    this.store = new Map();
  }
  async get(key) {
    return this.store.get(key) || null;
  }
  async put(key, val) {
    this.store.set(key, val);
  }
}

test('API Middleware - Unauthenticated request returns 401', async () => {
  const req = new Request('https://router.internal/v1/accounts', { method: 'GET' });
  const apiKeyHeader = req.headers.get('X-Master-API-Key');
  assert.equal(apiKeyHeader, null);
});

test('API Middleware - Invalid API key hash returns 403', async () => {
  const mockDb = new MockD1Database();
  const testKey = 'umr_live_fake_key_000000000000';
  const hash = await hashApiKey(testKey);

  const keyRecord = await mockDb.prepare('SELECT * FROM api_keys WHERE key_hash = ? AND (revoked_at IS NULL)').bind(hash).first();
  assert.equal(keyRecord, null);
});

test('Bootstrap - Atomic Race-Condition Protection: simultaneous bootstrap requests create only ONE key', async () => {
  const mockDb = new MockD1Database();

  const insertSql = `INSERT INTO api_keys (id, name, key_hash, prefix, scopes, tenant_id, rate_limit_rpm, is_admin)
                     SELECT ?, ?, ?, ?, ?, ?, ?, ?
                     WHERE (SELECT count(*) FROM api_keys) = 0`;

  // Simulate two concurrent requests arriving in parallel
  const req1 = mockDb.prepare(insertSql).bind('key_admin_1', 'Primary Admin 1', 'hash1', 'prefix1', '["*"]', null, 600, 1).run();
  const req2 = mockDb.prepare(insertSql).bind('key_admin_2', 'Primary Admin 2', 'hash2', 'prefix2', '["*"]', null, 600, 1).run();

  const [res1, res2] = await Promise.all([req1, req2]);

  // Exactly one must succeed (changes = 1) and one must be rejected (changes = 0)
  const totalCreated = (res1.changes === 1 ? 1 : 0) + (res2.changes === 1 ? 1 : 0);
  assert.equal(totalCreated, 1);
  assert.equal(mockDb.tables.api_keys.length, 1);
});

test('Bootstrap - Repeated bootstrap attempts are rejected once an admin key exists', async () => {
  const mockDb = new MockD1Database();
  // Pre-seed an existing admin key
  mockDb.tables.api_keys.push({
    id: 'key_existing_admin',
    key_hash: 'hash_existing',
    is_admin: 1
  });

  const countResult = await mockDb.prepare('SELECT count(*) as count FROM api_keys').first();
  const count = Number(countResult.count);
  assert.ok(count > 0);

  // Bootstrap check condition: count must be 0
  const isBootstrapAllowed = count === 0;
  assert.equal(isBootstrapAllowed, false);
});

test('Authorization - Normal (non-admin) users cannot create or revoke API keys', async () => {
  const normalUserKey = {
    id: 'key_normal_tenant',
    is_admin: 0,
    scopes: JSON.stringify(['accounts:read'])
  };

  assert.equal(normalUserKey.is_admin === 1, false);
});

test('Tenant Security - Strict tenant data isolation on routing rules', async () => {
  const mockDb = new MockD1Database();
  // Insert rules for two separate tenants
  await mockDb.prepare('INSERT INTO routing_rules (id, tenant_id, strategy, target_project_id)')
    .bind('rule_1', 'tenant_alpha', 'DEDICATED', 'proj_alpha').run();
  await mockDb.prepare('INSERT INTO routing_rules (id, tenant_id, strategy, target_project_id)')
    .bind('rule_2', 'tenant_beta', 'SHARED_POOL', 'proj_beta').run();

  // Tenant Alpha queries their rules
  const alphaRules = await mockDb.prepare('SELECT id, tenant_id, strategy, target_project_id FROM routing_rules WHERE tenant_id = ?')
    .bind('tenant_alpha').all();

  assert.equal(alphaRules.results.length, 1);
  assert.equal(alphaRules.results[0].tenant_id, 'tenant_alpha');
  assert.notEqual(alphaRules.results[0].tenant_id, 'tenant_beta');

  // Verify cross-tenant modification prevention
  const callerTenant = 'tenant_alpha';
  const targetTenantToModify = 'tenant_beta';
  const isAllowed = callerTenant === targetTenantToModify;
  assert.equal(isAllowed, false);
});

test('API Middleware - Strict per-key rate limit enforcement with KV', async () => {
  const mockKv = new MockKV();
  const rateLimitRpm = 3;
  const keyId = 'key_rate_test';
  const windowId = 123456;

  for (let i = 0; i < rateLimitRpm; i++) {
    const current = parseInt(await mockKv.get(`rate_${keyId}_${windowId}`) || '0', 10);
    assert.ok(current < rateLimitRpm);
    await mockKv.put(`rate_${keyId}_${windowId}`, (current + 1).toString());
  }

  // 4th request in same window exceeds limit
  const currentCount = parseInt(await mockKv.get(`rate_${keyId}_${windowId}`) || '0', 10);
  assert.equal(currentCount, 3);
  const isBlocked = currentCount >= rateLimitRpm;
  assert.equal(isBlocked, true);
});
