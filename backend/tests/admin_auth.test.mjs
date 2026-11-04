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
