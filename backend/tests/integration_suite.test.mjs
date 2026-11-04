import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.ts';

async function hashApiKey(key) {
  const enc = new TextEncoder();
  const hashBuffer = await crypto.subtle.digest('SHA-256', enc.encode(key));
  return [...new Uint8Array(hashBuffer)]
    .map(x => x.toString(16).padStart(2, '0'))
    .join('');
}

class MockD1Database {
  constructor() {
    this.tables = {
      admin_users: [],
      admin_sessions: [],
      api_keys: [],
      connected_accounts: [],
      projects: [],
      usage_metrics: []
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
        if (sql.includes('FROM admin_sessions')) {
          const sessId = this.params[0];
          const session = db.tables.admin_sessions.find(s => s.id === sessId);
          if (!session) return null;
          const user = db.tables.admin_users.find(u => u.id === session.user_id);
          if (!user) return null;
          return {
            id: user.id,
            email: user.email,
            name: user.name,
            is_owner: user.is_owner,
            csrf_token: session.csrf_token,
            expires_at: session.expires_at
          };
        }
        if (sql.includes('FROM api_keys WHERE key_hash = ?')) {
          const hash = this.params[0];
          return db.tables.api_keys.find(k => k.key_hash === hash && !k.revoked_at) || null;
        }
        if (sql.includes('FROM connected_accounts WHERE id = ?')) {
          const id = this.params[0];
          return db.tables.connected_accounts.find(a => a.id === id) || null;
        }
        if (sql.includes('FROM connected_accounts LIMIT 1') || sql.includes('FROM connected_accounts')) {
          return db.tables.connected_accounts[0] || null;
        }
        return null;
      },
      async all() {
        if (sql.includes('FROM connected_accounts')) {
          return { results: db.tables.connected_accounts };
        }
        return { results: [] };
      },
      async run() {
        if (sql.includes('INSERT INTO usage_metrics')) {
          db.tables.usage_metrics.push({
            id: this.params[0],
            account_id: this.params[1],
            read_requests: this.params[2],
            write_requests: this.params[3]
          });
          return { changes: 1, success: true };
        }
        return { changes: 1, success: true };
      }
    };
  }
}

function createEnv(db) {
  return {
    DB: db,
    ROUTER_KV: {
      get: async () => null,
      put: async () => {},
      delete: async () => {}
    },
    ENCRYPTION_KEY: 'test-secret-key-32-chars-length!!'
  };
}

function setupAdminSession(db) {
  db.tables.admin_users.push({
    id: 'user_admin_1',
    email: 'admin@universal.internal',
    name: 'Administrator',
    password_hash: 'hash',
    password_salt: 'salt',
    is_owner: 1
  });

  db.tables.admin_sessions.push({
    id: 'sess_admin_1',
    user_id: 'user_admin_1',
    csrf_token: 'csrf_test_token_123',
    expires_at: '2099-01-01T00:00:00Z'
  });
}

function getAuthHeaders() {
  return {
    'Content-Type': 'application/json',
    'Cookie': 'umr_session=sess_admin_1',
    'X-CSRF-Token': 'csrf_test_token_123'
  };
}

test('Integration Suite - Reject request missing API Key', async () => {
  const db = new MockD1Database();
  setupAdminSession(db);
  const env = createEnv(db);

  const req = new Request('http://localhost/v1/test/integration-suite', {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({})
  });

  const res = await worker.fetch(req, env);
  assert.equal(res.status, 400);
  const data = await res.json();
  assert.equal(data.success, false);
  assert.match(data.error, /API Key is required/);
});

test('Integration Suite - Reject invalid Master API Key in Stage 1', async () => {
  const db = new MockD1Database();
  setupAdminSession(db);
  const env = createEnv(db);

  const req = new Request('http://localhost/v1/test/integration-suite', {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ api_key: 'umr_invalid_key_12345' })
  });

  const res = await worker.fetch(req, env);
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.success, true);
  assert.equal(data.data.overall_status, 'FAILED');
  assert.equal(data.data.stages[0].stage_name, '1. Master API Key Authentication');
  assert.equal(data.data.stages[0].status, 'FAILED');
  assert.equal(data.data.stages[0].http_status, 401);
});

test('Integration Suite - Stage 1 succeeds with valid API Key, gracefully handles missing account', async () => {
  const db = new MockD1Database();
  setupAdminSession(db);

  const apiKey = 'umr_valid_test_key_12345';
  const keyHash = await hashApiKey(apiKey);

  db.tables.api_keys.push({
    id: 'key_1',
    name: 'Test Key',
    key_hash: keyHash,
    prefix: 'umr_vali',
    scopes: '["*"]',
    tenant_id: 'default',
    rate_limit_rpm: 600,
    is_admin: 1,
    revoked_at: null
  });

  const env = createEnv(db);

  const req = new Request('http://localhost/v1/test/integration-suite', {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ api_key: apiKey, confirm_write: false })
  });

  const res = await worker.fetch(req, env);
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.success, true);
  assert.equal(data.data.stages[0].stage_name, '1. Master API Key Authentication');
  assert.equal(data.data.stages[0].status, 'PASSED');
  assert.equal(data.data.stages[0].http_status, 200);

  // Stage 2 fails because no Appwrite accounts exist
  assert.equal(data.data.stages[1].stage_name, '2. Appwrite Connection & Health');
  assert.equal(data.data.stages[1].status, 'FAILED');
  assert.match(data.data.stages[1].message, /No connected Appwrite account/);
});

test('Integration Suite - Respect confirm_write=false flag for write operations', async () => {
  const db = new MockD1Database();
  setupAdminSession(db);

  const apiKey = 'umr_write_test_key_98765';
  const keyHash = await hashApiKey(apiKey);

  db.tables.api_keys.push({
    id: 'key_2',
    name: 'Write Test Key',
    key_hash: keyHash,
    prefix: 'umr_writ',
    scopes: '["*"]',
    tenant_id: 'default',
    rate_limit_rpm: 600,
    is_admin: 1,
    revoked_at: null
  });

  db.tables.connected_accounts.push({
    id: 'acc_test_1',
    label: 'Test Account',
    endpoint: 'https://sgp.cloud.appwrite.io/v1',
    project_id: 'proj_test',
    api_key_ciphertext: 'encrypted',
    region: 'sgp',
    status: 'ACTIVE'
  });

  const env = createEnv(db);

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    const urlStr = url.toString();
    if (urlStr.includes('/locale') || urlStr.includes('/health')) {
      return new Response(JSON.stringify({ status: 'pass' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (urlStr.includes('/databases') && !urlStr.includes('/collections')) {
      return new Response(JSON.stringify({ databases: [{ $id: 'db_1', name: 'Database 1' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (urlStr.includes('/collections') && !urlStr.includes('/documents')) {
      return new Response(JSON.stringify({ collections: [{ $id: 'col_1', name: 'Collection 1' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (urlStr.includes('/documents')) {
      return new Response(JSON.stringify({ documents: [{ $id: 'doc_1', title: 'Hello' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    return new Response(JSON.stringify({ message: 'OK' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };

  try {
    const req = new Request('http://localhost/v1/test/integration-suite', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ api_key: apiKey, confirm_write: false })
    });

    const res = await worker.fetch(req, env);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.success, true);
    
    // Stages 1-5 pass
    assert.equal(data.data.stages[0].status, 'PASSED');
    assert.equal(data.data.stages[1].status, 'PASSED');
    assert.equal(data.data.stages[2].status, 'PASSED');
    assert.equal(data.data.stages[3].status, 'PASSED');
    assert.equal(data.data.stages[4].status, 'PASSED');

    // Write stages 6-8 are NOT TESTED
    assert.equal(data.data.stages[5].stage_name, '6. Document Creation');
    assert.equal(data.data.stages[5].status, 'NOT TESTED');
    assert.match(data.data.stages[5].message, /confirm_write/);

    assert.equal(data.data.stages[6].stage_name, '7. Document Update');
    assert.equal(data.data.stages[6].status, 'NOT TESTED');

    assert.equal(data.data.stages[7].stage_name, '8. Document Deletion');
    assert.equal(data.data.stages[7].status, 'NOT TESTED');

    // Stage 9 (Usage Metering) passes
    assert.equal(data.data.stages[8].stage_name, '9. Usage Metering Verification');
    assert.equal(data.data.stages[8].status, 'PASSED');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('Integration Suite - Full execution with confirm_write=true creates, updates, and deletes test doc', async () => {
  const db = new MockD1Database();
  setupAdminSession(db);

  const apiKey = 'umr_full_write_key_77777';
  const keyHash = await hashApiKey(apiKey);

  db.tables.api_keys.push({
    id: 'key_3',
    name: 'Full Write Key',
    key_hash: keyHash,
    prefix: 'umr_full',
    scopes: '["*"]',
    tenant_id: 'default',
    rate_limit_rpm: 600,
    is_admin: 1,
    revoked_at: null
  });

  db.tables.connected_accounts.push({
    id: 'acc_test_2',
    label: 'Write Test Account',
    endpoint: 'https://sgp.cloud.appwrite.io/v1',
    project_id: 'proj_write_test',
    api_key_ciphertext: 'encrypted',
    region: 'sgp',
    status: 'ACTIVE'
  });

  const env = createEnv(db);

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    const urlStr = url.toString();
    const method = (options?.method || 'GET').toUpperCase();

    if (urlStr.includes('/locale') || urlStr.includes('/health')) {
      return new Response(JSON.stringify({ status: 'pass' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (urlStr.includes('/databases') && !urlStr.includes('/collections')) {
      return new Response(JSON.stringify({ databases: [{ $id: 'db_1', name: 'Database 1' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (urlStr.includes('/collections') && !urlStr.includes('/documents')) {
      return new Response(JSON.stringify({ collections: [{ $id: 'col_1', name: 'Collection 1' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (urlStr.includes('/documents')) {
      if (method === 'POST') {
        return new Response(JSON.stringify({ $id: 'test_doc_123', status: 'created' }), { status: 201, headers: { 'Content-Type': 'application/json' } });
      }
      if (method === 'PATCH') {
        return new Response(JSON.stringify({ $id: 'test_doc_123', status: 'VERIFIED_PASSED' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (method === 'DELETE') {
        return new Response(JSON.stringify({ success: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ documents: [{ $id: 'doc_1', title: 'Hello' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    return new Response(JSON.stringify({ message: 'OK' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };

  try {
    const req = new Request('http://localhost/v1/test/integration-suite', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ api_key: apiKey, confirm_write: true })
    });

    const res = await worker.fetch(req, env);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.success, true);
    assert.equal(data.data.overall_status, 'PASSED');

    // All 9 stages should be PASSED
    for (let i = 0; i < 9; i++) {
      assert.equal(data.data.stages[i].status, 'PASSED', `Stage ${i + 1} (${data.data.stages[i].stage_name}) should be PASSED`);
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});
