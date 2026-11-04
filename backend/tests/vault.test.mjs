import test from 'node:test';
import assert from 'node:assert/strict';

// Helper Web Crypto vault implementation for pure ESM test execution
function buf2hex(buffer) {
  return [...new Uint8Array(buffer)]
    .map(x => x.toString(16).padStart(2, '0'))
    .join('');
}

function hex2buf(hex) {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16);
  }
  return bytes;
}

class CredentialVault {
  constructor(masterKeySecret) {
    if (!masterKeySecret || masterKeySecret.length < 16) {
      throw new Error('ENCRYPTION_MASTER_KEY must be configured and at least 16 characters.');
    }
    this.masterKeySecret = masterKeySecret;
  }

  async deriveKey(salt) {
    const enc = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
      'raw',
      enc.encode(this.masterKeySecret),
      'PBKDF2',
      false,
      ['deriveKey']
    );

    return crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt: salt,
        iterations: 100000,
        hash: 'SHA-256'
      },
      keyMaterial,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt']
    );
  }

  async encrypt(plainText) {
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const key = await this.deriveKey(salt);

    const enc = new TextEncoder();
    const cipherBuffer = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: iv },
      key,
      enc.encode(plainText)
    );

    return {
      cipherTextHex: buf2hex(cipherBuffer),
      saltHex: buf2hex(salt.buffer),
      ivHex: buf2hex(iv.buffer)
    };
  }

  async decrypt(payload) {
    const salt = hex2buf(payload.saltHex);
    const iv = hex2buf(payload.ivHex);
    const cipherBytes = hex2buf(payload.cipherTextHex);
    const key = await this.deriveKey(salt);

    const decryptedBuffer = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: iv },
      key,
      cipherBytes
    );

    const dec = new TextDecoder();
    return dec.decode(decryptedBuffer);
  }
}

test('CredentialVault - Encryption & Decryption roundtrip', async () => {
  const secret = 'test-master-secret-key-32-characters-long';
  const vault = new CredentialVault(secret);
  const plainSecret = 'appwrite_api_key_secret_998877665544';

  const encrypted = await vault.encrypt(plainSecret);
  assert.notEqual(encrypted.cipherTextHex, plainSecret);
  assert.equal(encrypted.saltHex.length, 32); // 16 bytes = 32 hex chars
  assert.equal(encrypted.ivHex.length, 24);   // 12 bytes = 24 hex chars

  const decrypted = await vault.decrypt(encrypted);
  assert.equal(decrypted, plainSecret);
});

test('CredentialVault - Tampered ciphertext fails authentication check', async () => {
  const secret = 'test-master-secret-key-32-characters-long';
  const vault = new CredentialVault(secret);
  const plainSecret = 'sensitive_key_val';

  const encrypted = await vault.encrypt(plainSecret);
  // Tamper with the last character of ciphertext
  const tamperedHex = encrypted.cipherTextHex.slice(0, -2) + (encrypted.cipherTextHex.endsWith('0') ? '1' : '0');
  const tamperedPayload = { ...encrypted, cipherTextHex: tamperedHex };

  await assert.rejects(
    async () => await vault.decrypt(tamperedPayload),
    /operation failed|tag mismatch|ciphertext/i
  );
});

test('CredentialVault - Wrong master key fails authentication check', async () => {
  const vault1 = new CredentialVault('master-secret-key-number-one-12345');
  const vault2 = new CredentialVault('master-secret-key-number-two-67890');

  const encrypted = await vault1.encrypt('secret_value_data');

  await assert.rejects(
    async () => await vault2.decrypt(encrypted),
    /operation failed|tag mismatch|ciphertext/i
  );
});

test('CredentialVault - Enforces minimum 16-character secret requirement', () => {
  assert.throws(
    () => new CredentialVault('short'),
    /at least 16 characters/
  );
});
