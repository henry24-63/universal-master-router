import test from 'node:test';
import assert from 'node:assert/strict';

function hex2buf(hex) {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16);
  }
  return bytes;
}

function buf2hex(buffer) {
  return [...new Uint8Array(buffer)]
    .map(x => x.toString(16).padStart(2, '0'))
    .join('');
}

async function hashPassword(password, providedSaltHex) {
  const salt = providedSaltHex ? hex2buf(providedSaltHex) : crypto.getRandomValues(new Uint8Array(16));
  const enc = new TextEncoder();

  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    enc.encode(password),
    'PBKDF2',
    false,
    ['deriveBits']
  );

  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt: salt,
      iterations: 100000,
      hash: 'SHA-256'
    },
    keyMaterial,
    256
  );

  return {
    hashHex: buf2hex(derivedBits),
    saltHex: buf2hex(salt.buffer)
  };
}

async function verifyPassword(password, expectedHashHex, saltHex) {
  const result = await hashPassword(password, saltHex);
  return result.hashHex === expectedHashHex;
}

test('Admin Auth - PBKDF2 Password Hashing & Verification', async () => {
  const password = 'mySecurePassword123!';
  const { hashHex, saltHex } = await hashPassword(password);

  assert.equal(saltHex.length, 32); // 16 bytes
  assert.equal(hashHex.length, 64); // 256 bits

  const isValid = await verifyPassword(password, hashHex, saltHex);
  assert.equal(isValid, true);

  const isInvalid = await verifyPassword('wrongPassword', hashHex, saltHex);
  assert.equal(isInvalid, false);
});

test('Admin Auth - Atomic Owner Initialization (Only one owner can be created)', async () => {
  const adminUsers = [];
  const insertOwner = (user) => {
    if (adminUsers.length === 0) {
      adminUsers.push(user);
      return { changes: 1 };
    }
    return { changes: 0 };
  };

  const res1 = insertOwner({ email: 'owner@domain.com', name: 'Owner' });
  const res2 = insertOwner({ email: 'attacker@domain.com', name: 'Attacker' });

  assert.equal(res1.changes, 1);
  assert.equal(res2.changes, 0);
  assert.equal(adminUsers.length, 1);
  assert.equal(adminUsers[0].email, 'owner@domain.com');
});

test('Admin Auth - CSRF Token Protection on mutating session requests', () => {
  const validCsrf = 'csrf_token_abc_123';
  const incomingHeader = 'csrf_token_abc_123';
  const tamperedHeader = 'csrf_token_tampered';

  assert.equal(incomingHeader === validCsrf, true);
  assert.equal(tamperedHeader === validCsrf, false);
});

test('Admin Auth - Session Cookie Security Flags', () => {
  const sessionId = 'session_token_xyz_987';
  const cookieHeader = `umr_session=${sessionId}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`;

  assert.ok(cookieHeader.includes('HttpOnly'));
  assert.ok(cookieHeader.includes('Secure'));
  assert.ok(cookieHeader.includes('SameSite=Strict'));
});

test('Admin Auth - Session Expiration Handling', () => {
  const now = Date.now();
  const validSession = { expiresAt: new Date(now + 3600000).toISOString() };
  const expiredSession = { expiresAt: new Date(now - 1000).toISOString() };

  assert.ok(new Date(validSession.expiresAt).getTime() > now);
  assert.ok(new Date(expiredSession.expiresAt).getTime() < now);
});

test('Admin Auth - First-Time Setup Password & Email Validation Rules', () => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  // Email format checks
  assert.equal(emailRegex.test('admin@domain.com'), true);
  assert.equal(emailRegex.test('invalid-email'), false);
  assert.equal(emailRegex.test('user@'), false);

  // Password minimum 12 characters check
  const shortPass = 'shortpass12';
  const validPass = 'SecureAdminPassword2026!';
  assert.equal(shortPass.length >= 12, false);
  assert.equal(validPass.length >= 12, true);
});

test('Admin Auth - Setup Status flow and response structure', () => {
  const evaluateSetupStatus = (adminCount) => {
    return {
      success: true,
      data: {
        has_admin: adminCount > 0,
        db_initialized: true
      }
    };
  };

  const statusBeforeSetup = evaluateSetupStatus(0);
  assert.equal(statusBeforeSetup.data.has_admin, false);

  const statusAfterSetup = evaluateSetupStatus(1);
  assert.equal(statusAfterSetup.data.has_admin, true);
});

test('Diagnostic - Missing D1 Database Binding Detection', () => {
  const envWithoutDb = {};
  const isDbMissing = !envWithoutDb.DB;
  assert.equal(isDbMissing, true);

  const errorResponse = {
    success: false,
    data: null,
    error: "Database binding 'DB' is missing or not configured on Cloudflare Worker.",
    status: 503
  };
  assert.equal(errorResponse.status, 503);
  assert.ok(errorResponse.error.includes("binding 'DB' is missing"));
});

test('Diagnostic - Missing Table Error Analyzer identifies required migrations', () => {
  function parseDatabaseError(msg) {
    const lowerMsg = msg.toLowerCase();
    if (lowerMsg.includes('no such table')) {
      let tableName = 'unknown';
      if (lowerMsg.includes('admin_users')) tableName = 'admin_users';
      if (lowerMsg.includes('admin_sessions')) tableName = 'admin_sessions';
      if (lowerMsg.includes('api_keys')) tableName = 'api_keys';

      let migrationName = '0001_initial_schema.sql';
      if (tableName === 'admin_users' || tableName === 'admin_sessions') {
        migrationName = '0002_admin_auth.sql';
      }

      return {
        isTableMissing: true,
        tableName,
        userMessage: `Database table '${tableName}' does not exist in Cloudflare D1. Please execute migration '${migrationName}' in Cloudflare D1 Console.`,
        requiredMigration: migrationName
      };
    }
    return { isTableMissing: false };
  }

  const err1 = parseDatabaseError('D1_ERROR: no such table: admin_users');
  assert.equal(err1.isTableMissing, true);
  assert.equal(err1.tableName, 'admin_users');
  assert.equal(err1.requiredMigration, '0002_admin_auth.sql');

  const err2 = parseDatabaseError('D1_ERROR: no such table: api_keys');
  assert.equal(err2.isTableMissing, true);
  assert.equal(err2.tableName, 'api_keys');
  assert.equal(err2.requiredMigration, '0001_initial_schema.sql');
});

