/**
 * Web Crypto API AES-256-GCM Credential Vault
 * Derives encryption keys using PBKDF2 with 100,000 rounds and a random 16-byte salt.
 * Ensures credentials stored in D1 are never readable without the Cloudflare Worker secret.
 */

export interface EncryptedPayload {
  cipherTextHex: string;
  saltHex: string;
  ivHex: string;
}

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

export class CredentialVault {
  private masterKeySecret: string;

  constructor(masterKeySecret: string) {
    if (!masterKeySecret || masterKeySecret.length < 16) {
      throw new Error('ENCRYPTION_MASTER_KEY must be configured and at least 16 characters.');
    }
    this.masterKeySecret = masterKeySecret;
  }

  private async deriveKey(salt: Uint8Array): Promise<CryptoKey> {
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

  async encrypt(plainText: string): Promise<EncryptedPayload> {
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const key = await this.deriveKey(salt);

    const enc = new TextEncoder();
    const cipherBuffer = await crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv: iv
      },
      key,
      enc.encode(plainText)
    );

    return {
      cipherTextHex: buf2hex(cipherBuffer),
      saltHex: buf2hex(salt.buffer),
      ivHex: buf2hex(iv.buffer)
    };
  }

  async decrypt(payload: EncryptedPayload): Promise<string> {
    const salt = hex2buf(payload.saltHex);
    const iv = hex2buf(payload.ivHex);
    const cipherBytes = hex2buf(payload.cipherTextHex);
    const key = await this.deriveKey(salt);

    const decryptedBuffer = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: iv
      },
      key,
      cipherBytes
    );

    const dec = new TextDecoder();
    return dec.decode(decryptedBuffer);
  }
}
