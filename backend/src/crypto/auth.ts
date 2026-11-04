/**
 * Admin Authentication & Password Utilities
 * PBKDF2 password hashing with 100,000 rounds of SHA-256 and unique 16-byte random salt.
 */

function buf2hex(buffer: ArrayBuffer): string {
  return [...new Uint8Array(buffer)]
    .map(x => x.toString(16).padStart(2, '0'))
    .join('');
}

function hex2buf(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16);
  }
  return bytes;
}

export interface PasswordHashResult {
  hashHex: string;
  saltHex: string;
}

export async function hashPassword(password: string, providedSaltHex?: string): Promise<PasswordHashResult> {
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

export async function verifyPassword(password: string, expectedHashHex: string, saltHex: string): Promise<boolean> {
  const result = await hashPassword(password, saltHex);
  return result.hashHex === expectedHashHex;
}

export function generateSessionId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return buf2hex(bytes.buffer);
}

export function generateCsrfToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  return buf2hex(bytes.buffer);
}
