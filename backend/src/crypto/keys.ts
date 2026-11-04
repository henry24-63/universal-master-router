/**
 * Master Router API Key Generation and Verification Utility
 * Generates cryptographically secure keys (e.g. umr_live_...) and hashes them with SHA-256.
 */

export interface GeneratedKey {
  plainSecret: string;
  keyHashHex: string;
  prefix: string;
}

function buf2hex(buffer: ArrayBuffer): string {
  return [...new Uint8Array(buffer)]
    .map(x => x.toString(16).padStart(2, '0'))
    .join('');
}

export async function hashApiKey(key: string): Promise<string> {
  const enc = new TextEncoder();
  const hashBuffer = await crypto.subtle.digest('SHA-256', enc.encode(key));
  return buf2hex(hashBuffer);
}

export async function generateMasterRouterApiKey(): Promise<GeneratedKey> {
  const randomBytes = crypto.getRandomValues(new Uint8Array(24));
  const rawKey = buf2hex(randomBytes.buffer);
  const plainSecret = `umr_live_${rawKey}`;
  const prefix = plainSecret.substring(0, 16);
  const keyHashHex = await hashApiKey(plainSecret);

  return {
    plainSecret,
    keyHashHex,
    prefix
  };
}
