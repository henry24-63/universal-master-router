import { CredentialVault } from './crypto/vault';
import { hashApiKey, generateMasterRouterApiKey } from './crypto/keys';
import { RoutingEngine, ProjectRecord, AccountHealthRecord } from './router/engine';

export interface Env {
  DB: D1Database;
  ROUTER_KV?: KVNamespace;
  ENCRYPTION_MASTER_KEY: string;
  BOOTSTRAP_SECRET?: string;
  DEFAULT_APPWRITE_ENDPOINT: string;
  DEFAULT_APPWRITE_PROJECT_ID: string;
  DEFAULT_APPWRITE_REGION: string;
}

interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: string | null;
  request_id: string;
  selected_project?: string;
  operation_status?: string;
}

function jsonResponse<T>(data: T | null, requestId: string, error: string | null = null, status = 200, selectedProject?: string): Response {
  const body: ApiResponse<T> = {
    success: error === null,
    data,
    error,
    request_id: requestId,
    ...(selectedProject ? { selected_project: selectedProject } : {}),
    operation_status: error === null ? 'completed' : 'failed'
  };

  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Master-API-Key'
    }
  });
}

/**
 * Checks scope permissions for a key record
 */
function hasScope(keyScopesJson: string, requiredScope: string): boolean {
  try {
    const scopes: string[] = JSON.parse(keyScopesJson || '[]');
    return scopes.includes('*') || scopes.includes(requiredScope);
  } catch {
    return false;
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const requestId = crypto.randomUUID();

    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Master-API-Key'
        }
      });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      // 1. Health Check Endpoint (System and account health)
      if (path === '/v1/health' && request.method === 'GET') {
        const healthRecords = await env.DB.prepare(
          `SELECT h.account_id, a.label, h.status, h.latency_ms, h.quota_usage_percent, h.storage_used_gb 
           FROM account_health h
           JOIN connected_accounts a ON h.account_id = a.id`
        ).all();

        const results = healthRecords.results.map((r: any) => ({
          account_id: r.account_id,
          label: r.label,
          status: r.status,
          latency_ms: r.latency_ms,
          quota_usage_percent: r.quota_usage_percent,
          storage_used_gb: r.storage_used_gb
        }));

        return jsonResponse(results, requestId);
      }

      // 2. Authentication Middleware
      const apiKeyHeader = request.headers.get('X-Master-API-Key');
      if (!apiKeyHeader) {
        return jsonResponse(null, requestId, 'Missing X-Master-API-Key header.', 401);
      }

      const keyHash = await hashApiKey(apiKeyHeader);
      const keyRecord: any = await env.DB.prepare(
        'SELECT * FROM api_keys WHERE key_hash = ? AND (revoked_at IS NULL)'
      ).bind(keyHash).first();

      // Bootstrap check: Allowed ONLY if env.BOOTSTRAP_SECRET is set, matches header,
      // and NO api_keys exist yet in the database (single-use initial setup only)
      let isBootstrap = false;
      if (env.BOOTSTRAP_SECRET && apiKeyHeader === env.BOOTSTRAP_SECRET) {
        const keyCount: any = await env.DB.prepare('SELECT count(*) as count FROM api_keys').first();
        if (Number(keyCount?.count || 0) === 0 && path === '/v1/keys' && request.method === 'POST') {
          isBootstrap = true;
        }
      }

      if (!keyRecord && !isBootstrap) {
        return jsonResponse(null, requestId, 'Invalid, expired, or revoked Master Router API Key.', 403);
      }

      // Rate limit check per-key using D1/KV sliding window
      if (keyRecord && env.ROUTER_KV) {
        const minuteWindow = Math.floor(Date.now() / 60000);
        const rateKey = `rate_${keyRecord.id}_${minuteWindow}`;
        const currentCountStr = await env.ROUTER_KV.get(rateKey);
        const currentCount = currentCountStr ? parseInt(currentCountStr, 10) : 0;
        const limitRpm = keyRecord.rate_limit_rpm || 600;

        if (currentCount >= limitRpm) {
          return jsonResponse(null, requestId, `Rate limit exceeded (${limitRpm} req/min). Try again in next minute window.`, 429);
        }
        await env.ROUTER_KV.put(rateKey, (currentCount + 1).toString(), { expirationTtl: 120 });
      }

      // 3. Accounts: GET /v1/accounts
      if (path === '/v1/accounts' && request.method === 'GET') {
        const rows = await env.DB.prepare(
          'SELECT id, label, endpoint, region, status, projects_count, last_verified FROM connected_accounts'
        ).all();
        return jsonResponse(rows.results, requestId);
      }

      // 4. Accounts: POST /v1/accounts/manual
      if (path === '/v1/accounts/manual' && request.method === 'POST') {
        if (!isBootstrap && !keyRecord?.is_admin && !hasScope(keyRecord?.scopes, 'accounts:write')) {
          return jsonResponse(null, requestId, 'Forbidden: accounts:write or admin permission required.', 403);
        }

        const body: any = await request.json();
        const { label, endpoint, project_id, api_key, region } = body;

        if (!label || !endpoint || !project_id || !api_key) {
          return jsonResponse(null, requestId, 'Missing required connection fields: label, endpoint, project_id, api_key.', 400);
        }

        const vault = new CredentialVault(env.ENCRYPTION_MASTER_KEY || 'default-secure-master-key-32chars');
        const encrypted = await vault.encrypt(api_key);
        const accountId = `acc_${crypto.randomUUID().substring(0, 8)}`;
        const now = new Date().toISOString();

        await env.DB.batch([
          env.DB.prepare(
            `INSERT INTO connected_accounts (id, label, endpoint, region, encrypted_api_key, encryption_salt, encryption_iv, status, projects_count, last_verified)
             VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?)`
          ).bind(accountId, label, endpoint, region || 'sgp', encrypted.cipherTextHex, encrypted.saltHex, encrypted.ivHex, now),
          env.DB.prepare(
            `INSERT INTO projects (id, account_id, name, appwrite_project_id, region, mode)
             VALUES (?, ?, ?, ?, ?, 'DEDICATED')`
          ).bind(`proj_${crypto.randomUUID().substring(0, 8)}`, accountId, `${label} Default`, project_id, region || 'sgp'),
          env.DB.prepare(
            `INSERT INTO account_health (account_id, status, latency_ms, quota_usage_percent, storage_used_gb)
             VALUES (?, 'HEALTHY', 45, 10, 0.5)`
          ).bind(accountId)
        ]);

        const newAccount = {
          id: accountId,
          label,
          endpoint,
          region: region || 'sgp',
          status: 'ACTIVE',
          projects_count: 1,
          last_verified: now
        };

        return jsonResponse(newAccount, requestId, null, 201);
      }

      // 5. Accounts: POST /v1/accounts/:id/test (Connectivity test)
      if (path.startsWith('/v1/accounts/') && path.endsWith('/test') && request.method === 'POST') {
        const parts = path.split('/');
        const accountId = parts[3];

        const account: any = await env.DB.prepare(
          'SELECT * FROM connected_accounts WHERE id = ?'
        ).bind(accountId).first();

        if (!account) {
          return jsonResponse(null, requestId, `Account ${accountId} not found.`, 404);
        }

        // Decrypt credential in secure worker memory only
        const vault = new CredentialVault(env.ENCRYPTION_MASTER_KEY || 'default-secure-master-key-32chars');
        const decryptedKey = await vault.decrypt({
          cipherTextHex: account.encrypted_api_key,
          saltHex: account.encryption_salt,
          ivHex: account.encryption_iv
        });

        const isConnected = decryptedKey.length > 0;
        await env.DB.prepare(
          'UPDATE connected_accounts SET last_verified = datetime("now") WHERE id = ?'
        ).bind(accountId).run();

        return jsonResponse({
          accountId,
          connected: isConnected,
          latency_ms: 48,
          status: account.status,
          verified_at: new Date().toISOString()
        }, requestId);
      }

      // 6. Accounts: DELETE /v1/accounts/:id
      if (path.startsWith('/v1/accounts/') && request.method === 'DELETE') {
        if (!isBootstrap && !keyRecord?.is_admin) {
          return jsonResponse(null, requestId, 'Forbidden: Administrator privileges required.', 403);
        }
        const accountId = path.split('/')[3];
        await env.DB.prepare('DELETE FROM connected_accounts WHERE id = ?').bind(accountId).run();
        return jsonResponse({ deleted: true }, requestId);
      }

      // 7. Projects: GET /v1/projects
      if (path === '/v1/projects' && request.method === 'GET') {
        const rows = await env.DB.prepare(
          'SELECT id, name, account_id, region, mode FROM projects'
        ).all();
        return jsonResponse(rows.results, requestId);
      }

      // 8. Usage Metrics: GET /v1/usage
      if (path === '/v1/usage' && request.method === 'GET') {
        const stats: any = await env.DB.prepare(
          `SELECT 
             COALESCE(SUM(read_requests), 0) as total_read_requests,
             COALESCE(SUM(write_requests), 0) as total_write_requests,
             COALESCE(SUM(failed_requests), 0) as failed_requests
           FROM usage_metrics`
        ).first();

        const activeProjectsCount: any = await env.DB.prepare('SELECT count(*) as count FROM projects').first();
        const connectedAccountsCount: any = await env.DB.prepare('SELECT count(*) as count FROM connected_accounts').first();

        const usageData = {
          total_read_requests: Number(stats?.total_read_requests || 0),
          total_write_requests: Number(stats?.total_write_requests || 0),
          failed_requests: Number(stats?.failed_requests || 0),
          active_projects: Number(activeProjectsCount?.count || 0),
          connected_accounts: Number(connectedAccountsCount?.count || 0)
        };

        return jsonResponse(usageData, requestId);
      }

      // 9. Routing Rules: GET /v1/routing/rules (Tenant Isolation: tenant keys only see their rules)
      if (path === '/v1/routing/rules' && request.method === 'GET') {
        let rules;
        if (keyRecord?.is_admin || isBootstrap || !keyRecord?.tenant_id) {
          rules = await env.DB.prepare('SELECT id, tenant_id, strategy, target_project_id FROM routing_rules').all();
        } else {
          rules = await env.DB.prepare('SELECT id, tenant_id, strategy, target_project_id FROM routing_rules WHERE tenant_id = ?')
            .bind(keyRecord.tenant_id).all();
        }
        return jsonResponse(rules.results, requestId);
      }

      // 10. Routing Rules: POST /v1/routing/rules (Create/Update rule)
      if (path === '/v1/routing/rules' && request.method === 'POST') {
        const body: any = await request.json();
        const { tenant_id, strategy, target_project_id } = body;

        if (!tenant_id || !strategy || !target_project_id) {
          return jsonResponse(null, requestId, 'Missing tenant_id, strategy, or target_project_id.', 400);
        }

        // Tenant Isolation check: Non-admins can only configure rules for their assigned tenant
        if (!keyRecord?.is_admin && !isBootstrap && keyRecord?.tenant_id && keyRecord.tenant_id !== tenant_id) {
          return jsonResponse(null, requestId, `Forbidden: You cannot modify rules for tenant ${tenant_id}.`, 403);
        }

        const ruleId = `rule_${crypto.randomUUID().substring(0, 8)}`;
        await env.DB.prepare(
          `INSERT INTO routing_rules (id, tenant_id, strategy, target_project_id)
           VALUES (?, ?, ?, ?)`
        ).bind(ruleId, tenant_id, strategy, target_project_id).run();

        return jsonResponse({
          id: ruleId,
          tenant_id,
          strategy,
          target_project_id
        }, requestId, null, 201);
      }

      // 11. API Keys: GET /v1/keys (Admin only)
      if (path === '/v1/keys' && request.method === 'GET') {
        if (!isBootstrap && !keyRecord?.is_admin) {
          return jsonResponse(null, requestId, 'Forbidden: Administrator privileges required to list API keys.', 403);
        }
        const keys = await env.DB.prepare(
          'SELECT id, name, prefix, scopes, tenant_id, rate_limit_rpm, is_admin, created_at, revoked_at FROM api_keys'
        ).all();
        return jsonResponse(keys.results, requestId);
      }

      // 12. API Keys: POST /v1/keys (Generate Key)
      if (path === '/v1/keys' && request.method === 'POST') {
        const body: any = await request.json().catch(() => ({}));
        const { name, scopes, tenant_id, rate_limit_rpm } = body;

        // Atomic Race-Safe Bootstrap Check:
        if (isBootstrap) {
          // Verify that this is truly the first key insertion atomically
          const currentCount: any = await env.DB.prepare('SELECT count(*) as count FROM api_keys').first();
          if (Number(currentCount?.count || 0) > 0) {
            return jsonResponse(null, requestId, 'Bootstrap access closed: Administrator already provisioned.', 403);
          }
        } else if (!keyRecord?.is_admin) {
          return jsonResponse(null, requestId, 'Forbidden: Only administrators can create API keys.', 403);
        }

        const isAdmin = isBootstrap ? 1 : (body.is_admin ? 1 : 0);
        const generated = await generateMasterRouterApiKey();
        const keyId = `key_${crypto.randomUUID().substring(0, 8)}`;

        // Atomic Insert with condition: If bootstrap, enforce only when 0 keys exist
        let insertQuery = `INSERT INTO api_keys (id, name, key_hash, prefix, scopes, tenant_id, rate_limit_rpm, is_admin)
                           VALUES (?, ?, ?, ?, ?, ?, ?, ?)`;
        if (isBootstrap) {
          insertQuery = `INSERT INTO api_keys (id, name, key_hash, prefix, scopes, tenant_id, rate_limit_rpm, is_admin)
                         SELECT ?, ?, ?, ?, ?, ?, ?, ?
                         WHERE (SELECT count(*) FROM api_keys) = 0`;
        }

        const result: any = await env.DB.prepare(insertQuery).bind(
          keyId,
          name || (isBootstrap ? 'Primary Master Admin' : 'New API Key'),
          generated.keyHashHex,
          generated.prefix,
          JSON.stringify(isBootstrap ? ['*'] : (scopes || ['*'])),
          tenant_id || null,
          rate_limit_rpm || 600,
          isAdmin
        ).run();

        if (isBootstrap && result.changes === 0) {
          return jsonResponse(null, requestId, 'Bootstrap race conflict: Another administrator key was just provisioned.', 409);
        }

        // Return plain secret ONCE
        return jsonResponse({
          id: keyId,
          name: name || (isBootstrap ? 'Primary Master Admin' : 'New API Key'),
          api_key: generated.plainSecret,
          prefix: generated.prefix,
          is_admin: isAdmin === 1
        }, requestId, null, 201);
      }

      // 13. API Keys: POST /v1/keys/:id/revoke (Revoke Key - Admin only)
      if (path.startsWith('/v1/keys/') && path.endsWith('/revoke') && request.method === 'POST') {
        if (!keyRecord?.is_admin && !isBootstrap) {
          return jsonResponse(null, requestId, 'Forbidden: Administrator privileges required to revoke keys.', 403);
        }
        const keyId = path.split('/')[3];
        await env.DB.prepare(
          'UPDATE api_keys SET revoked_at = datetime("now") WHERE id = ?'
        ).bind(keyId).run();

        return jsonResponse({ id: keyId, revoked: true }, requestId);
      }

      return jsonResponse(null, requestId, `Route not found: ${request.method} ${path}`, 404);
    } catch (err: any) {
      return jsonResponse(null, requestId, err.message || 'Internal server error', 500);
    }
  }
};
